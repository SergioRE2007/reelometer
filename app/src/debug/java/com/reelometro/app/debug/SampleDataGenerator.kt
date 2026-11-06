package com.reelometro.app.debug

import com.reelometro.app.data.Category
import com.reelometro.app.data.LocalStamp
import com.reelometro.app.data.Platform
import com.reelometro.app.data.ReelView
import com.reelometro.app.data.ShareEvent
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Genera un historial de uso **realista y reproducible** (misma semilla + mismo «ahora» + misma
 * zona = mismos datos) para QA y capturas. Solo existe en debug.
 *
 * - Desde hace `weeks` semanas hasta **ahora mismo**: nada en el futuro y hoy a medias.
 * - Sesiones agrupadas: transporte por la mañana, café, comida, tarde y una noche fuerte (21–01 h)
 *   que cruza la medianoche, más miradas sueltas durante el día; los fines de semana hay más y
 *   empiezan más tarde.
 * - Plataforma por sesión con pesos TikTok 50 %, Reels 30 %, Shorts 20 % (a veces se cambia a mitad).
 * - Duraciones log-normales (mediana ~15 s; 5–60 s casi siempre).
 * - 40 creadores recurrentes inventados (nombres claramente ficticios, [NAMED_CREATORS]) con
 *   popularidad tipo Zipf dentro de cada plataforma, que se llevan ~35 % de los vídeos (el nº 1,
 *   ~4 %); ~40 % son de una cola larga de cuentas de paso (también inventadas) y ~25 % sin creador
 *   legible. La categoría va ligada al creador, con «Sin clasificar» realista (~30 %).
 * - ~2 % de vídeos compartidos (alguno sin vídeo asociado).
 * - Tendencia al alza entre semanas, una semana más tranquila, un día «pico» memorable reciente y
 *   un día casi sin uso.
 */
internal class SampleDataGenerator(
    private val seed: Long = DEFAULT_SEED,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    class Result(val views: List<ReelView>, val shares: List<ShareEvent>)

    private class Creator(val handle: String, val category: Category, val platform: Platform)

    /** Una franja de uso: centro (minutos desde la medianoche del día; puede pasar de 1440), dispersión y peso. */
    private class Slot(val centerMinute: Int, val spreadMinutes: Int, val share: Double)

    fun generate(weeks: Int, now: Instant): Result {
        val random = Random(seed)
        val nowMs = now.toEpochMilli()
        val today = now.atZone(zone).toLocalDate()
        val dayCount = weeks.coerceIn(1, MAX_WEEKS) * 7
        val firstDay = today.minusDays(dayCount - 1L)

        // Popularidad tipo Zipf dentro de cada plataforma (el orden lo decide la semilla).
        val namedByPlatform = Platform.entries.associateWith { p -> CREATORS.filter { it.platform == p }.shuffled(random) }
        val namedWeights = namedByPlatform.mapValues { (_, list) ->
            DoubleArray(list.size) { 1.0 / (it + 1.0).pow(ZIPF_EXPONENT) }
        }
        val tail = longTail(random)
        val tailByPlatform = Platform.entries.associateWith { p -> tail.filter { it.platform == p } }

        // Días especiales (no hoy ni ayer): el pico, un fin de semana de la segunda mitad (memorable y reciente).
        val pastDays = (0 until dayCount - 2).map { firstDay.plusDays(it.toLong()) }
        val peakDay = pastDays.drop(pastDays.size / 2)
            .filter { it.dayOfWeek == DayOfWeek.SUNDAY || it.dayOfWeek == DayOfWeek.SATURDAY }
            .ifEmpty { pastDays }.randomOrNull(random)
        val quietDay = pastDays
            .filter { it != peakDay && it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            .ifEmpty { pastDays.filter { it != peakDay } }.randomOrNull(random)
        val calmWeek = if (weeks >= 4) random.nextInt(1, weeks - 1) else -1

        val views = ArrayList<ReelView>(dayCount * 220)
        val shares = ArrayList<ShareEvent>(dayCount * 5)
        var seq = 0L
        // Nunca hay dos vídeos a la vez: una sesión que empezaría antes de que acabe la anterior la continúa.
        var busyUntil = Long.MIN_VALUE

        fun pickCreator(platform: Platform): Creator? {
            val r = random.nextDouble()
            return when {
                r < NO_CREATOR -> null
                r < NO_CREATOR + NAMED_SHARE -> {
                    val list = namedByPlatform.getValue(platform)
                    weighted(random, namedWeights.getValue(platform))?.let { list[it] }
                }
                else -> tailByPlatform.getValue(platform).randomOrNull(random)
            }
        }

        /** Una sesión de [videos] seguidos desde [startMinute] (minutos desde la medianoche de [date]). */
        fun session(date: LocalDate, startMinute: Double, videos: Int) {
            val planned = date.atStartOfDay(zone).toInstant().toEpochMilli() + (startMinute * 60_000).toLong()
            var t = if (planned > busyUntil) planned else busyUntil + 1_000 + random.nextLong(20_000)
            var platform = pickPlatform(random)
            for (v in 0 until videos) {
                if (random.nextDouble() < SWITCH_PLATFORM) platform = pickPlatform(random)
                val duration = duration(random, platform)
                if (t + duration > nowMs) return
                val id = t * 1000 + (seq++ % 1000)
                val creator = pickCreator(platform)
                val category = category(random, creator)
                val stamp = LocalStamp.of(t, zone)
                views += ReelView(
                    id = id,
                    platform = platform.name,
                    startedAt = t,
                    durationMs = duration,
                    creator = creator?.handle,
                    caption = caption(random, category, creator),
                    category = category.name,
                    day = stamp.day,
                    minuteOfDay = stamp.minuteOfDay,
                )
                if (random.nextDouble() < SHARE_PROBABILITY) {
                    val at = t + (duration * (0.3 + 0.6 * random.nextDouble())).toLong()
                    val shareStamp = LocalStamp.of(at, zone)
                    shares += ShareEvent(
                        platform = platform.name,
                        at = at,
                        day = shareStamp.day,
                        minuteOfDay = shareStamp.minuteOfDay,
                        reelId = if (random.nextDouble() < UNLINKED_SHARE) null else id,
                    )
                }
                busyUntil = t + duration
                t += duration + 250 + random.nextLong(1_500)
            }
        }

        for (i in 0 until dayCount) {
            val date = firstDay.plusDays(i.toLong())
            val week = i / 7
            val progress = if (dayCount <= 7) 1.0 else i.toDouble() / (dayCount - 1)
            var intensity = BASE_PER_DAY * weekdayFactor(date.dayOfWeek) * (0.8 + 0.45 * progress) *
                logNormal(random, 0.0, 0.18)
            if (week == calmWeek) intensity *= 0.72
            if (date == peakDay) intensity *= 2.6
            if (date == quietDay) intensity *= 0.12

            val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
            val planned = mutableListOf<Pair<Double, Int>>() // (minuto de inicio, vídeos)
            for (slot in if (weekend) WEEKEND_SLOTS else WEEKDAY_SLOTS) {
                // Las franjas que pasan de 24 h son la madrugada: se registran en el día siguiente.
                val videos = (intensity * slot.share * logNormal(random, 0.0, 0.3)).roundToInt()
                if (videos > 0) planned += (slot.centerMinute + gaussian(random) * slot.spreadMinutes) to videos
            }
            // Miradas sueltas entre las 9 y las 21 h («solo un momento»).
            repeat(2 + random.nextInt(3)) {
                val videos = ((2 + random.nextInt(9)) * intensity / BASE_PER_DAY).roundToInt()
                if (videos > 0) planned += (9 * 60 + random.nextDouble() * 12 * 60) to videos
            }
            planned.sortedBy { it.first }.forEach { (start, videos) -> session(date, start, videos) }
        }
        views.sortBy { it.startedAt }
        shares.sortBy { it.at }
        return Result(views, shares)
    }

    /** Cuentas de paso: muchas, cada una vista pocas veces (nombres combinados, claramente inventados). */
    private fun longTail(random: Random): List<Creator> {
        val handles = TAIL_FIRST.flatMap { a -> TAIL_SECOND.map { b -> "${a}_de_$b" } }.shuffled(random)
        return handles.take(TAIL_SIZE).map { base ->
            val handle = if (random.nextBoolean()) base else "${base}_${10 + random.nextInt(90)}"
            val category = if (random.nextDouble() < 0.25) Category.OTROS else KNOWN_CATEGORIES.random(random)
            Creator(handle, category, pickPlatform(random))
        }
    }

    private fun pickPlatform(random: Random): Platform {
        val r = random.nextDouble()
        return when {
            r < 0.5 -> Platform.TIKTOK
            r < 0.8 -> Platform.INSTAGRAM
            else -> Platform.YOUTUBE
        }
    }

    /** Un índice de [weights] con probabilidad proporcional a su peso; `null` si no hay ninguno. */
    private fun weighted(random: Random, weights: DoubleArray): Int? {
        if (weights.isEmpty()) return null
        var r = random.nextDouble() * weights.sum()
        for (i in weights.indices) {
            r -= weights[i]
            if (r <= 0) return i
        }
        return weights.lastIndex
    }

    private fun duration(random: Random, platform: Platform): Long {
        val medianSeconds = when (platform) {
            Platform.TIKTOK -> 16.0
            Platform.INSTAGRAM -> 13.0
            Platform.YOUTUBE -> 14.0
        }
        val seconds = logNormal(random, ln(medianSeconds), 0.7).coerceIn(2.0, 150.0)
        return (seconds * 1000).toLong()
    }

    private fun category(random: Random, creator: Creator?): Category {
        val r = random.nextDouble()
        return if (creator != null) {
            when {
                r < 0.8 -> creator.category
                r < 0.92 -> Category.OTROS
                else -> KNOWN_CATEGORIES.random(random)
            }
        } else {
            if (r < 0.45) Category.OTROS else KNOWN_CATEGORIES.random(random)
        }
    }

    private fun caption(random: Random, category: Category, creator: Creator?): String? {
        if (creator == null && random.nextDouble() < 0.6) return null
        val options = CAPTIONS[category] ?: CAPTIONS.getValue(Category.OTROS)
        return options.random(random)
    }

    private fun weekdayFactor(day: DayOfWeek): Double = when (day) {
        DayOfWeek.MONDAY -> 0.9
        DayOfWeek.TUESDAY -> 0.85
        DayOfWeek.WEDNESDAY -> 0.95
        DayOfWeek.THURSDAY -> 1.0
        DayOfWeek.FRIDAY -> 1.15
        DayOfWeek.SATURDAY -> 1.35
        DayOfWeek.SUNDAY -> 1.3
    }

    private fun logNormal(random: Random, mu: Double, sigma: Double): Double = exp(mu + sigma * gaussian(random))

    /** Normal estándar (Box–Muller). */
    private fun gaussian(random: Random): Double {
        val u1 = max(random.nextDouble(), 1e-12)
        val u2 = random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
    }

    companion object {
        const val DEFAULT_SEED = 20_261_103L
        const val MAX_WEEKS = 104

        private const val BASE_PER_DAY = 170.0
        private const val ZIPF_EXPONENT = 0.9
        private const val NO_CREATOR = 0.25
        private const val NAMED_SHARE = 0.35
        private const val TAIL_SIZE = 200
        private const val SWITCH_PLATFORM = 0.004
        private const val SHARE_PROBABILITY = 0.02
        private const val UNLINKED_SHARE = 0.1

        private val WEEKDAY_SLOTS = listOf(
            Slot(centerMinute = 8 * 60, spreadMinutes = 20, share = 0.14), // transporte
            Slot(11 * 60, 40, 0.05), // café
            Slot(14 * 60 + 30, 30, 0.16), // comida
            Slot(18 * 60 + 15, 50, 0.12), // tarde
            Slot(22 * 60 + 15, 45, 0.38), // noche fuerte
            Slot(24 * 60 + 25, 20, 0.12), // madrugada (ya es el día siguiente)
        )
        private val WEEKEND_SLOTS = listOf(
            Slot(10 * 60 + 30, 40, 0.18), // en la cama
            Slot(13 * 60 + 30, 50, 0.13),
            Slot(17 * 60 + 30, 60, 0.2), // sofá
            Slot(23 * 60, 45, 0.33),
            Slot(25 * 60, 30, 0.16),
        )

        private val KNOWN_CATEGORIES = Category.entries.filter { it != Category.OTROS }

        /** Nombres inventados: no corresponden a cuentas reales. */
        private val CREATORS = listOf(
            Creator("chistes_de_ascensor", Category.HUMOR, Platform.TIKTOK),
            Creator("la_tia_que_opina", Category.HUMOR, Platform.INSTAGRAM),
            Creator("memes_de_lunes_ficticios", Category.HUMOR, Platform.TIKTOK),
            Creator("risas_en_diferido", Category.HUMOR, Platform.YOUTUBE),
            Creator("la_cocina_de_nadie", Category.COMIDA, Platform.TIKTOK),
            Creator("tortilla_sin_debate", Category.COMIDA, Platform.INSTAGRAM),
            Creator("el_microondas_valiente", Category.COMIDA, Platform.YOUTUBE),
            Creator("michi_filosofo", Category.ANIMALES, Platform.TIKTOK),
            Creator("perrete_con_agenda", Category.ANIMALES, Platform.INSTAGRAM),
            Creator("pesas_y_pesadillas", Category.FITNESS, Platform.INSTAGRAM),
            Creator("abdominales_de_mentira", Category.FITNESS, Platform.TIKTOK),
            Creator("jugadas_del_barrio", Category.DEPORTE, Platform.YOUTUBE),
            Creator("el_var_de_mi_salon", Category.DEPORTE, Platform.TIKTOK),
            Creator("acordes_en_la_ducha", Category.MUSICA, Platform.TIKTOK),
            Creator("karaoke_sin_permiso", Category.MUSICA, Platform.INSTAGRAM),
            Creator("bailando_en_el_pasillo", Category.BAILE, Platform.TIKTOK),
            Creator("coreos_de_cocina", Category.BAILE, Platform.TIKTOK),
            Creator("partidas_a_medianoche", Category.GAMING, Platform.YOUTUBE),
            Creator("el_jefe_final_soy_yo", Category.GAMING, Platform.TIKTOK),
            Creator("datos_que_no_pediste", Category.APRENDIZAJE, Platform.YOUTUBE),
            Creator("historia_en_un_minuto_inventada", Category.APRENDIZAJE, Platform.TIKTOK),
            Creator("la_profe_de_mates_ficticia", Category.APRENDIZAJE, Platform.INSTAGRAM),
            Creator("gafas_de_pasta_tech", Category.TECNOLOGIA, Platform.YOUTUBE),
            Creator("trucos_de_movil_imaginarios", Category.TECNOLOGIA, Platform.TIKTOK),
            Creator("rizos_con_criterio", Category.BELLEZA, Platform.INSTAGRAM),
            Creator("armario_capsula_infinito", Category.BELLEZA, Platform.INSTAGRAM),
            Creator("mochila_y_bocata", Category.VIAJES, Platform.INSTAGRAM),
            Creator("trenes_que_no_salen", Category.VIAJES, Platform.YOUTUBE),
            Creator("motores_y_magdalenas", Category.MOTOR, Platform.YOUTUBE),
            Creator("el_taller_imaginario", Category.MOTOR, Platform.TIKTOK),
            Creator("finanzas_de_bolsillo_roto", Category.DINERO, Platform.TIKTOK),
            Creator("ahorro_con_drama", Category.DINERO, Platform.INSTAGRAM),
            Creator("noticias_en_bata", Category.NOTICIAS, Platform.TIKTOK),
            Creator("resumen_del_dia_de_mentira", Category.NOTICIAS, Platform.YOUTUBE),
            Creator("consejos_de_mi_ex", Category.RELACIONES, Platform.TIKTOK),
            Creator("citas_desastrosas_inventadas", Category.RELACIONES, Platform.INSTAGRAM),
            Creator("cosas_que_pasan_en_mi_portal", Category.OTROS, Platform.TIKTOK),
            Creator("videos_sin_contexto_ficticios", Category.OTROS, Platform.YOUTUBE),
            Creator("el_vecino_del_quinto_b", Category.OTROS, Platform.INSTAGRAM),
            Creator("aleatorio_total_404", Category.OTROS, Platform.TIKTOK),
        )

        /** Los 40 creadores recurrentes (para los tests). */
        val NAMED_CREATORS: List<String> = CREATORS.map { it.handle }

        private val TAIL_FIRST = listOf(
            "diario", "rincon", "club", "cuaderno", "planeta", "taller",
            "mundo", "archivo", "radio", "museo", "academia", "agencia",
        )
        private val TAIL_SECOND = listOf(
            "tostadas", "nubes", "calcetines", "tortugas", "persianas", "lunes", "bocadillos",
            "farolas", "sobremesas", "paraguas", "croquetas", "siestas", "pinguinos", "macetas",
            "chanclas", "ascensores", "botijos", "mandarinas", "grapadoras", "tuppers",
        )

        private val CAPTIONS: Map<Category, List<String>> = mapOf(
            Category.HUMOR to listOf(
                "Cuando tu madre dice «ahora vemos» #humor #risas",
                "Nadie: … Yo a las 3 de la mañana: #comedia",
                "Parte 3 de cosas que solo pasan en mi casa #humor",
            ),
            Category.COMIDA to listOf(
                "Tortilla en 5 minutos, sin discusiones #receta #cocina",
                "Pasta cremosa con lo que hay en la nevera #recetasfaciles",
                "Probando el bocadillo más caro del barrio #comida",
            ),
            Category.ANIMALES to listOf(
                "Mi gato juzgando mis decisiones #gatos #mascotas",
                "Cuando dices «paseo» en voz alta #perros",
            ),
            Category.FITNESS to listOf(
                "Rutina de 10 minutos sin material #fitness #gym",
                "Día de pierna, día de sufrir #entrenamiento",
            ),
            Category.DEPORTE to listOf(
                "Este gol no lo marca ni el mejor del mundo #futbol",
                "Resumen de la jornada en 30 segundos #deporte",
            ),
            Category.MUSICA to listOf(
                "Versión acústica en el baño, perdón a los vecinos #musica",
                "La canción que no te vas a quitar de la cabeza #cancion",
            ),
            Category.BAILE to listOf(
                "Aprende esta coreo en 15 segundos #baile #dance",
                "Bailando hasta que se cansen los vecinos #tendencia #baile",
            ),
            Category.GAMING to listOf(
                "El jefe final más injusto de la historia #gaming #videojuegos",
                "Partida a las 2 de la mañana, como debe ser #gamer",
            ),
            Category.APRENDIZAJE to listOf(
                "Tres datos de historia que no te contaron en clase #aprende #historia",
                "Truco de mates para calcular propinas #aprendeentiktok",
                "Por qué el cielo es azul, en un minuto #ciencia",
            ),
            Category.TECNOLOGIA to listOf(
                "Ajuste oculto del móvil que deberías conocer #tecnologia #android",
                "Probando el cargador más raro del mercado #tech",
            ),
            Category.BELLEZA to listOf(
                "Rutina de rizos en 5 pasos #pelo #belleza",
                "Tres looks con la misma chaqueta #moda #outfit",
            ),
            Category.VIAJES to listOf(
                "Pueblo precioso a una hora de la ciudad #viajes #escapada",
                "Cómo hacer la maleta para 10 días en una mochila #viajar",
            ),
            Category.MOTOR to listOf(
                "Restaurando un coche de los noventa #coches #motor",
                "Este ruido del motor no es normal #mecanica",
            ),
            Category.DINERO to listOf(
                "Cómo ahorrar 100 euros al mes sin dejar de vivir #ahorro #finanzas",
                "Lo que nadie te cuenta de los préstamos #dinero",
            ),
            Category.NOTICIAS to listOf(
                "Lo que ha pasado hoy, en un minuto #noticias #actualidad",
                "Resumen de la semana en el Congreso #politica",
            ),
            Category.RELACIONES to listOf(
                "Señales de que le gustas (o no) #pareja #amor",
                "La peor primera cita del mundo #citas",
            ),
            Category.OTROS to listOf(
                "No me lo puedo creer",
                "Parte 2",
                "Esto pasó ayer en mi calle",
                "Esperad al final",
                "Sin palabras",
            ),
        )
    }
}
