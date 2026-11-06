package com.reelometro.app.stats

import com.reelometro.app.R
import com.reelometro.app.copy.Variants
import com.reelometro.app.copy.pick
import com.reelometro.app.copy.plural
import com.reelometro.app.copy.str
import kotlin.math.roundToLong

/**
 * «Con ese tiempo podrías…»: el tiempo viendo vídeos cortos traducido a cosas que llevan un
 * número conocido de horas. Cada equivalencia lleva su **base de cálculo** ([Equivalence.basis])
 * para la nota discreta de la interfaz (`dataS`, `textMuted`).
 *
 * Los cálculos (horas por unidad, cómo se lee una cantidad) son los mismos en todos los idiomas;
 * las palabras salen de `res/values/copy_equivalences.xml`, con una frase entera por caso
 * («más de 3 libros», «un libro y medio», «el 40 % de un libro»…).
 *
 * Uso típico:
 * ```
 * val picks = Equivalences.pick(totalMs, count = 3, seed = epochDay)   // las más llamativas
 * picks[0].infinitive   // «sacarte medio grado universitario»
 * picks[0].basis        // «Base: 240 ECTS × 25 h (Plan Bolonia)»
 * Equivalences.headline(totalMs, seed)   // «Con ese tiempo podrías leer 14 libros.»
 * ```
 */
object Equivalences {

    // ------------------------------------------------------------------ estudio

    val DEGREE = Equivalence(
        "grado", "🎓", 240 * 25.0, R.string.eq_degree_basis, R.string.eq_degree_label,
        EquivalenceGroup.ESTUDIO, absurd = false,
        Wording.Units(
            R.string.eq_degree_do,
            Counted(R.plurals.eq_degree_n, R.plurals.eq_degree_more, R.plurals.eq_degree_and_half, R.plurals.eq_degree_almost),
            Parts(R.string.eq_degree_percent, R.string.eq_degree_less, R.string.eq_degree_quarter, R.string.eq_degree_half, R.string.eq_degree_three_quarters),
        ),
    )
    val MASTER = Equivalence(
        "master", "📜", 60 * 25.0, R.string.eq_master_basis, R.string.eq_master_label,
        EquivalenceGroup.ESTUDIO, absurd = false,
        Wording.Units(
            R.string.eq_master_do,
            Counted(R.plurals.eq_master_n, R.plurals.eq_master_more, R.plurals.eq_master_and_half, R.plurals.eq_master_almost),
            Parts(R.string.eq_master_percent, R.string.eq_master_less, R.string.eq_master_quarter, R.string.eq_master_half, R.string.eq_master_three_quarters),
        ),
    )
    val LANGUAGE = Equivalence(
        "idioma", "🌍", 600.0, R.string.eq_language_basis, R.string.eq_language_label,
        EquivalenceGroup.ESTUDIO, absurd = false,
        Wording.Units(
            R.string.eq_language_do,
            Counted(R.plurals.eq_language_n, R.plurals.eq_language_more, R.plurals.eq_language_and_half, R.plurals.eq_language_almost),
            Parts(R.string.eq_language_percent, R.string.eq_language_less, R.string.eq_language_quarter, R.string.eq_language_half, R.string.eq_language_three_quarters),
        ),
    )
    val CODING = Equivalence(
        "bootcamp", "💻", 400.0, R.string.eq_coding_basis, R.string.eq_coding_label,
        EquivalenceGroup.ESTUDIO, absurd = false,
        Wording.Units(
            R.string.eq_coding_do,
            Counted(R.plurals.eq_coding_n, R.plurals.eq_coding_more, R.plurals.eq_coding_and_half, R.plurals.eq_coding_almost),
            Parts(R.string.eq_coding_percent, R.string.eq_coding_less, R.string.eq_coding_quarter, R.string.eq_coding_half, R.string.eq_coding_three_quarters),
        ),
    )
    // ------------------------------------------------------------------ lectura

    val BOOK = Equivalence(
        "libro", "📚", 90_000 / 250.0 / 60, R.string.eq_book_basis, R.string.eq_book_label,
        EquivalenceGroup.LECTURA, absurd = false,
        Wording.Units(
            R.string.eq_book_do,
            Counted(R.plurals.eq_book_n, R.plurals.eq_book_more, R.plurals.eq_book_and_half, R.plurals.eq_book_almost),
            Parts(R.string.eq_book_percent, R.string.eq_book_less, R.string.eq_book_quarter, R.string.eq_book_half, R.string.eq_book_three_quarters),
        ),
    )
    val QUIJOTE = Equivalence(
        "quijote", "🐴", 380_000 / 250.0 / 60, R.string.eq_quijote_basis, R.string.eq_quijote_label,
        EquivalenceGroup.LECTURA, absurd = false,
        Wording.Whole(
            R.string.eq_quijote_do,
            Counted(R.plurals.eq_quijote_n, R.plurals.eq_quijote_more, R.plurals.eq_quijote_and_half, R.plurals.eq_quijote_almost),
            Parts(R.string.eq_quijote_percent, R.string.eq_quijote_less, R.string.eq_quijote_quarter, R.string.eq_quijote_half, R.string.eq_quijote_three_quarters),
        ),
    )
    val PROUST = Equivalence(
        "proust", "⏳", 1_270_000 / 250.0 / 60, R.string.eq_proust_basis, R.string.eq_proust_label,
        EquivalenceGroup.LECTURA, absurd = false,
        Wording.Whole(
            R.string.eq_proust_do,
            Counted(R.plurals.eq_proust_n, R.plurals.eq_proust_more, R.plurals.eq_proust_and_half, R.plurals.eq_proust_almost),
            Parts(R.string.eq_proust_percent, R.string.eq_proust_less, R.string.eq_proust_quarter, R.string.eq_proust_half, R.string.eq_proust_three_quarters),
        ),
    )
    // ------------------------------------------------------------------ pantalla (de la grande)

    val GOT = Equivalence(
        "juego-de-tronos", "🐉", 70.0, R.string.eq_got_basis, R.string.eq_got_label,
        EquivalenceGroup.PANTALLA, absurd = false,
        Wording.Whole(
            R.string.eq_got_do,
            Counted(R.plurals.eq_got_n, R.plurals.eq_got_more, R.plurals.eq_got_and_half, R.plurals.eq_got_almost),
            Parts(R.string.eq_got_percent, R.string.eq_got_less, R.string.eq_got_quarter, R.string.eq_got_half, R.string.eq_got_three_quarters),
        ),
    )
    val HARRY_POTTER = Equivalence(
        "harry-potter", "⚡", 1_179 / 60.0, R.string.eq_harry_potter_basis, R.string.eq_harry_potter_label,
        EquivalenceGroup.PANTALLA, absurd = false,
        Wording.Whole(
            R.string.eq_harry_potter_do,
            Counted(R.plurals.eq_harry_potter_n, R.plurals.eq_harry_potter_more, R.plurals.eq_harry_potter_and_half, R.plurals.eq_harry_potter_almost),
            Parts(R.string.eq_harry_potter_percent, R.string.eq_harry_potter_less, R.string.eq_harry_potter_quarter, R.string.eq_harry_potter_half, R.string.eq_harry_potter_three_quarters),
        ),
    )
    val LOTR = Equivalence(
        "senor-de-los-anillos", "💍", 726 / 60.0, R.string.eq_lotr_basis, R.string.eq_lotr_label,
        EquivalenceGroup.PANTALLA, absurd = false,
        Wording.Whole(
            R.string.eq_lotr_do,
            Counted(R.plurals.eq_lotr_n, R.plurals.eq_lotr_more, R.plurals.eq_lotr_and_half, R.plurals.eq_lotr_almost),
            Parts(R.string.eq_lotr_percent, R.string.eq_lotr_less, R.string.eq_lotr_quarter, R.string.eq_lotr_half, R.string.eq_lotr_three_quarters),
        ),
    )
    val MOVIE = Equivalence(
        "pelicula", "🎬", 2.0, R.string.eq_movie_basis, R.string.eq_movie_label,
        EquivalenceGroup.PANTALLA, absurd = false,
        Wording.Units(
            R.string.eq_movie_do,
            Counted(R.plurals.eq_movie_n, R.plurals.eq_movie_more, R.plurals.eq_movie_and_half, R.plurals.eq_movie_almost),
            Parts(R.string.eq_movie_percent, R.string.eq_movie_less, R.string.eq_movie_quarter, R.string.eq_movie_half, R.string.eq_movie_three_quarters),
        ),
    )
    val FOOTBALL = Equivalence(
        "partido", "⚽", 1.75, R.string.eq_football_basis, R.string.eq_football_label,
        EquivalenceGroup.PANTALLA, absurd = false,
        Wording.Units(
            R.string.eq_football_do,
            Counted(R.plurals.eq_football_n, R.plurals.eq_football_more, R.plurals.eq_football_and_half, R.plurals.eq_football_almost),
            Parts(R.string.eq_football_percent, R.string.eq_football_less, R.string.eq_football_quarter, R.string.eq_football_half, R.string.eq_football_three_quarters),
        ),
    )
    // ------------------------------------------------------------------ deporte y viajes

    val MARATHON = Equivalence(
        "maraton", "🏃", 4.5, R.string.eq_marathon_basis, R.string.eq_marathon_label,
        EquivalenceGroup.DEPORTE, absurd = false,
        Wording.Units(
            R.string.eq_marathon_do,
            Counted(R.plurals.eq_marathon_n, R.plurals.eq_marathon_more, R.plurals.eq_marathon_and_half, R.plurals.eq_marathon_almost),
            Parts(R.string.eq_marathon_percent, R.string.eq_marathon_less, R.string.eq_marathon_quarter, R.string.eq_marathon_half, R.string.eq_marathon_three_quarters),
        ),
    )
    val CAMINO = Equivalence(
        "camino-de-santiago", "🐚", 780 / 4.0, R.string.eq_camino_basis, R.string.eq_camino_label,
        EquivalenceGroup.VIAJE, absurd = false,
        Wording.Whole(
            R.string.eq_camino_do,
            Counted(R.plurals.eq_camino_n, R.plurals.eq_camino_more, R.plurals.eq_camino_and_half, R.plurals.eq_camino_almost),
            Parts(R.string.eq_camino_percent, R.string.eq_camino_less, R.string.eq_camino_quarter, R.string.eq_camino_half, R.string.eq_camino_three_quarters),
        ),
    )
    val TOLEDO = Equivalence(
        "madrid-toledo", "🥾", 72 / 5.0, R.string.eq_toledo_basis, R.string.eq_toledo_label,
        EquivalenceGroup.VIAJE, absurd = false,
        Wording.Whole(
            R.string.eq_toledo_do,
            Counted(R.plurals.eq_toledo_n, R.plurals.eq_toledo_more, R.plurals.eq_toledo_and_half, R.plurals.eq_toledo_almost),
            Parts(R.string.eq_toledo_percent, R.string.eq_toledo_less, R.string.eq_toledo_quarter, R.string.eq_toledo_half, R.string.eq_toledo_three_quarters),
        ),
    )
    val AVE = Equivalence(
        "ave", "🚄", 2.5, R.string.eq_ave_basis, R.string.eq_ave_label,
        EquivalenceGroup.VIAJE, absurd = false,
        Wording.Whole(
            R.string.eq_ave_do,
            Counted(R.plurals.eq_ave_n, R.plurals.eq_ave_more, R.plurals.eq_ave_and_half, R.plurals.eq_ave_almost),
            Parts(R.string.eq_ave_percent, R.string.eq_ave_less, R.string.eq_ave_quarter, R.string.eq_ave_half, R.string.eq_ave_three_quarters),
        ),
    )
    val FLIGHT = Equivalence(
        "vuelo-nueva-york", "✈️", 8.0, R.string.eq_flight_basis, R.string.eq_flight_label,
        EquivalenceGroup.VIAJE, absurd = false,
        Wording.Whole(
            R.string.eq_flight_do,
            Counted(R.plurals.eq_flight_n, R.plurals.eq_flight_more, R.plurals.eq_flight_and_half, R.plurals.eq_flight_almost),
            Parts(R.string.eq_flight_percent, R.string.eq_flight_less, R.string.eq_flight_quarter, R.string.eq_flight_half, R.string.eq_flight_three_quarters),
        ),
    )
    val WALK = Equivalence(
        "km-a-pie", "🚶", 1 / 5.0, R.string.eq_walk_basis, R.string.eq_walk_label,
        EquivalenceGroup.VIAJE, absurd = false,
        Wording.Distance(
            R.string.eq_walk_do,
            Counted(R.plurals.eq_walk_n, R.plurals.eq_walk_more, R.plurals.eq_walk_and_half, R.plurals.eq_walk_almost),
            R.plurals.eq_walk_metres, R.plurals.eq_walk_under_metres,
        ),
    )
    // ------------------------------------------------------------------ cocina, música y sofá

    val TORTILLA = Equivalence(
        "tortilla", "🥔", 0.75, R.string.eq_tortilla_basis, R.string.eq_tortilla_label,
        EquivalenceGroup.COCINA, absurd = true,
        Wording.Units(
            R.string.eq_tortilla_do,
            Counted(R.plurals.eq_tortilla_n, R.plurals.eq_tortilla_more, R.plurals.eq_tortilla_and_half, R.plurals.eq_tortilla_almost),
            Parts(R.string.eq_tortilla_percent, R.string.eq_tortilla_less, R.string.eq_tortilla_quarter, R.string.eq_tortilla_half, R.string.eq_tortilla_three_quarters),
        ),
    )
    val COCIDO = Equivalence(
        "cocido", "🍲", 3.5, R.string.eq_cocido_basis, R.string.eq_cocido_label,
        EquivalenceGroup.COCINA, absurd = true,
        Wording.Units(
            R.string.eq_cocido_do,
            Counted(R.plurals.eq_cocido_n, R.plurals.eq_cocido_more, R.plurals.eq_cocido_and_half, R.plurals.eq_cocido_almost),
            Parts(R.string.eq_cocido_percent, R.string.eq_cocido_less, R.string.eq_cocido_quarter, R.string.eq_cocido_half, R.string.eq_cocido_three_quarters),
        ),
    )
    val EGG = Equivalence(
        "huevo-duro", "🥚", 10 / 60.0, R.string.eq_egg_basis, R.string.eq_egg_label,
        EquivalenceGroup.COCINA, absurd = true,
        Wording.Units(
            R.string.eq_egg_do,
            Counted(R.plurals.eq_egg_n, R.plurals.eq_egg_more, R.plurals.eq_egg_and_half, R.plurals.eq_egg_almost),
            Parts(R.string.eq_egg_percent, R.string.eq_egg_less, R.string.eq_egg_quarter, R.string.eq_egg_half, R.string.eq_egg_three_quarters),
        ),
    )
    val MILK = Equivalence(
        "vaso-de-leche", "🥛", 1 / 60.0, R.string.eq_milk_basis, R.string.eq_milk_label,
        EquivalenceGroup.COCINA, absurd = true,
        Wording.Units(
            R.string.eq_milk_do,
            Counted(R.plurals.eq_milk_n, R.plurals.eq_milk_more, R.plurals.eq_milk_and_half, R.plurals.eq_milk_almost),
            Parts(R.string.eq_milk_percent, R.string.eq_milk_less, R.string.eq_milk_quarter, R.string.eq_milk_half, R.string.eq_milk_three_quarters),
        ),
    )
    val BOHEMIAN = Equivalence(
        "bohemian-rhapsody", "🎸", (5 * 60 + 55) / 3600.0, R.string.eq_bohemian_basis, R.string.eq_bohemian_label,
        EquivalenceGroup.MUSICA, absurd = true,
        Wording.Whole(
            R.string.eq_bohemian_do,
            Counted(R.plurals.eq_bohemian_n, R.plurals.eq_bohemian_more, R.plurals.eq_bohemian_and_half, R.plurals.eq_bohemian_almost),
            Parts(R.string.eq_bohemian_percent, R.string.eq_bohemian_less, R.string.eq_bohemian_quarter, R.string.eq_bohemian_half, R.string.eq_bohemian_three_quarters),
        ),
    )
    val NAP = Equivalence(
        "siesta", "😴", 0.5, R.string.eq_nap_basis, R.string.eq_nap_label,
        EquivalenceGroup.DESCANSO, absurd = true,
        Wording.Units(
            R.string.eq_nap_do,
            Counted(R.plurals.eq_nap_n, R.plurals.eq_nap_more, R.plurals.eq_nap_and_half, R.plurals.eq_nap_almost),
            Parts(R.string.eq_nap_percent, R.string.eq_nap_less, R.string.eq_nap_quarter, R.string.eq_nap_half, R.string.eq_nap_three_quarters),
        ),
    )
    val SLEEP = Equivalence(
        "noche", "🛌", 8.0, R.string.eq_sleep_basis, R.string.eq_sleep_label,
        EquivalenceGroup.DESCANSO, absurd = false,
        Wording.Units(
            R.string.eq_sleep_do,
            Counted(R.plurals.eq_sleep_n, R.plurals.eq_sleep_more, R.plurals.eq_sleep_and_half, R.plurals.eq_sleep_almost),
            Parts(R.string.eq_sleep_percent, R.string.eq_sleep_less, R.string.eq_sleep_quarter, R.string.eq_sleep_half, R.string.eq_sleep_three_quarters),
        ),
    )
    val WORKDAY = Equivalence(
        "jornada", "💼", 8.0, R.string.eq_workday_basis, R.string.eq_workday_label,
        EquivalenceGroup.TRABAJO, absurd = false,
        Wording.Units(
            R.string.eq_workday_do,
            Counted(R.plurals.eq_workday_n, R.plurals.eq_workday_more, R.plurals.eq_workday_and_half, R.plurals.eq_workday_almost),
            Parts(R.string.eq_workday_percent, R.string.eq_workday_less, R.string.eq_workday_quarter, R.string.eq_workday_half, R.string.eq_workday_three_quarters),
        ),
    )

    /** Todas, de más a menos horas por unidad. */
    val ALL: List<Equivalence> = listOf(
        DEGREE, MASTER, LANGUAGE, CODING, CAMINO, PROUST, GOT, QUIJOTE, HARRY_POTTER, TOLEDO, LOTR,
        FLIGHT, SLEEP, WORKDAY, BOOK, MARATHON, COCIDO, AVE, MOVIE, FOOTBALL, TORTILLA, NAP, WALK, EGG,
        BOHEMIAN, MILK,
    ).sortedByDescending { it.hoursEach }

    // ------------------------------------------------------------------ cálculo

    /** Todas las equivalencias para [hours] horas, en el orden de [ALL]. */
    fun forHours(hours: Double): List<EquivalenceValue> = ALL.map { it.value(hours) }

    /** Todas las equivalencias para [ms] milisegundos, en el orden de [ALL]. */
    fun forDuration(ms: Long): List<EquivalenceValue> = forHours(Format.hours(ms.coerceAtLeast(0)))

    /**
     * Las [count] equivalencias más llamativas para [ms], deterministas por [seed]:
     *
     *  1. Se prefieren las que llegan al menos a una unidad («un máster», «14 libros»).
     *  2. Variedad: como mucho una por familia ([EquivalenceGroup]).
     *  3. Primero las «grandes» (más horas por unidad); si se piden 3 o más, la última plaza es para
     *     una de andar por casa (tortillas, siestas…) como contrapunto: grande, grande, absurda.
     *  4. Si no hay bastantes que lleguen a 1, se completa con las fracciones más cercanas a 1
     *     («medio grado», «el 40 % de un máster»).
     *
     * Con `count = 1` la elección rota (por semilla) entre las tres más grandes que llegan a 1.
     * Siempre devuelve [count] elementos si [ms] > 0; vacío si [ms] ≤ 0.
     */
    fun pick(ms: Long, count: Int = 3, seed: Long = 0): List<EquivalenceValue> {
        if (count <= 0 || ms <= 0) return emptyList()
        val values = forDuration(ms)
        val eligible = values.filter { it.reachesOne }
        val biggestPerGroup = eligible.distinctBy { it.equivalence.group } // ya vienen de más a menos horas

        val chosen = mutableListOf<EquivalenceValue>()
        if (count == 1) {
            biggestPerGroup.take(3).takeIf { it.isNotEmpty() }?.let { chosen += Variants.pick(it, seed, "eq.headline") }
        } else {
            val absurd = eligible.filter { it.equivalence.absurd && it.amount <= MAX_READABLE_UNITS }
            val bigSlots = if (absurd.isNotEmpty()) count - 1 else count
            val serious = biggestPerGroup.filter { !it.equivalence.absurd }
            chosen += choose(serious.take(bigSlots + 1), bigSlots, seed)
            if (absurd.isNotEmpty()) {
                val usedGroups = chosen.map { it.equivalence.group }.toSet()
                val pool = absurd.filter { it.equivalence.group !in usedGroups }.ifEmpty { absurd }
                chosen += Variants.pick(pool, seed, "eq.absurd")
            }
        }

        if (chosen.size < count) {
            val usedGroups = chosen.mapTo(mutableSetOf()) { it.equivalence.group }
            val fill = eligible.filter { it !in chosen } + values.filter { !it.reachesOne }.sortedByDescending { it.amount }
            for (v in fill) {
                if (chosen.size >= count) break
                if (v.equivalence.group !in usedGroups) {
                    chosen += v
                    usedGroups += v.equivalence.group
                }
            }
            for (v in fill) {
                if (chosen.size >= count) break
                if (v !in chosen) chosen += v
            }
        }

        // Presentación: primero las que llegan a 1 (las serias de más a menos horas, la absurda al final);
        // después, las fracciones de la más cercana a 1 a la más lejana.
        return chosen.take(count).sortedWith(
            compareByDescending<EquivalenceValue> { it.reachesOne }
                .thenBy { it.reachesOne && it.equivalence.absurd }
                .thenByDescending { if (it.reachesOne) it.equivalence.hoursEach else it.amount },
        )
    }

    /**
     * Frase de titular para [ms]: «Con ese tiempo podrías sacarte medio grado universitario.».
     * Determinista por [seed]. Con menos de un segundo: «Todavía no hay tiempo que comparar.».
     */
    fun headline(ms: Long, seed: Long): String {
        if (ms < 1_000) return str(R.string.eq_headline_none)
        return pick(ms, 1, seed).first().sentence(seed)
    }

    /** Compatibilidad con la versión anterior (horas en vez de milisegundos, sin semilla). */
    @Deprecated("Usa headline(ms, seed)", ReplaceWith("headline((hours * 3_600_000).toLong(), seed = 0)"))
    fun headline(hours: Double): String = headline((hours * 3_600_000).roundToLong(), 0)

    /** Cada deslizamiento de dedo recorre unos 12 cm de pantalla. */
    fun swipeMetres(count: Int): Double = count * 0.12

    /** Por encima de esto, las absurdas dejan de tener gracia («300.000 vasos de leche»). */
    private const val MAX_READABLE_UNITS = 10_000.0

    /** Elige [k] de [pool] por semilla, conservando su orden. */
    private fun choose(pool: List<EquivalenceValue>, k: Int, seed: Long): List<EquivalenceValue> {
        if (pool.size <= k) return pool
        val order = Variants.shuffled(pool.size, Variants.mix(seed, Variants.hash("eq.list")))
        return order.take(k).sorted().map { pool[it] }
    }
}

/**
 * Une cifra y unidad con espacio duro en las notas de base («25 h», «780 km»). Los recursos ya lo
 * traen; esto solo cubre una traducción que lo haya olvidado.
 */
private fun nbsp(text: String): String = text.replace(Regex("(\\d) "), "$1${Format.NBSP}")

/** Familia temática, para no repetir dos del mismo tipo en un titular. */
enum class EquivalenceGroup { ESTUDIO, LECTURA, PANTALLA, DEPORTE, VIAJE, COCINA, MUSICA, DESCANSO, TRABAJO }

/**
 * Cantidades que llegan al menos a una unidad, cada una un `<plurals>` con la cifra en `%1$s`:
 * [exactly] «un libro», «14 libros» (y «0 libros»); [moreThan] «más de 3 libros»;
 * [andHalf] «3 libros y medio»; [almost] «casi 4 libros» (y, con 1, «casi un libro»).
 */
class Counted(val exactly: Int, val moreThan: Int, val andHalf: Int, val almost: Int)

/**
 * Fracciones de una unidad (`<string>`): [percent] «el 40 % de un máster» y [less] «menos del
 * 0,1 % de un máster» (`%1$s` es el porcentaje), [quarter] «un cuarto de máster», [half] «medio
 * máster», [threeQuarters] «tres cuartos de máster».
 */
class Parts(val percent: Int, val less: Int, val quarter: Int, val half: Int, val threeQuarters: Int)

/**
 * Cómo se dice una equivalencia: ids de `copy_equivalences.xml`. [infinitiveId] es el verbo con
 * la cantidad en `%1$s` («leer %1$s»).
 */
sealed interface Wording {
    val infinitiveId: Int

    /**
     * Lo que se cuenta por unidades: «14 libros», «medio grado universitario», «el 40 % de un máster»,
     * «una tortilla y media de patatas».
     */
    data class Units(override val infinitiveId: Int, val counted: Counted, val parts: Parts) : Wording

    /** Una obra o un trayecto concreto que se repite: «ver Juego de Tronos entera 2 veces». */
    data class Whole(override val infinitiveId: Int, val counted: Counted, val parts: Parts) : Wording

    /**
     * Distancias a pie: «ir andando 5 kilómetros» ([km]), «ir andando 830 metros» ([metresId]),
     * «ir andando menos de 10 metros» ([underMetresId]); los dos últimos, `<plurals>` con la cifra.
     */
    data class Distance(override val infinitiveId: Int, val km: Counted, val metresId: Int, val underMetresId: Int) : Wording
}

/**
 * Algo que lleva [hoursEach] horas.
 *
 * @property emoji solo para la imagen compartible (nunca en la interfaz).
 * @property basis base de cálculo para la nota discreta: «Base: 240 ECTS × 25 h (Plan Bolonia)».
 * @property label etiqueta corta en mayúsculas para líneas de ticket: «LIBROS», «JUEGO DE TRONOS».
 * @property absurd de andar por casa (tortillas, siestas…): el contrapunto de las listas.
 */
class Equivalence(
    val id: String,
    val emoji: String,
    val hoursEach: Double,
    private val basisId: Int,
    private val labelId: Int,
    val group: EquivalenceGroup,
    val absurd: Boolean,
    val wording: Wording,
) {
    val basis: String get() = nbsp(str(basisId))

    val label: String get() = str(labelId)

    fun value(hours: Double): EquivalenceValue = EquivalenceValue(this, hours / hoursEach)

    fun forDuration(ms: Long): EquivalenceValue = value(Format.hours(ms.coerceAtLeast(0)))

    override fun toString(): String = id
}

/**
 * Una equivalencia con su cantidad. [amount] son unidades (2.0 = «2 libros», 0.5 = «medio libro»).
 */
data class EquivalenceValue(val equivalence: Equivalence, val amount: Double) {

    /** Llega al menos a una unidad tal y como se lee (0,95 ya es «un libro»). */
    val reachesOne: Boolean get() = amount >= Amounts.ONE

    /**
     * La cantidad en palabras, sin verbo: «14 libros», «un libro y medio», «más de 3 másteres»,
     * «casi 5 huevos duros», «medio grado universitario», «el 40 % de un máster», «casi un idioma»,
     * «Juego de Tronos entera 2 veces», «una tortilla y media de patatas», «830 metros».
     */
    val amountText: String get() = Amounts.text(amount, equivalence.wording)

    /** Con su verbo, para encadenar en frases: «leer 14 libros», «sacarte medio grado universitario». */
    val infinitive: String get() = str(equivalence.wording.infinitiveId, amountText)

    /** Frase completa, con variantes por [seed]: «Con ese tiempo podrías leer 14 libros.». */
    fun sentence(seed: Long): String = pick(seed, "eq.sentence", R.array.eq_sentence, infinitive)

    /** Valor para una línea de ticket: «14», «0,5», «0,04»; con «×» delante si es una obra o trayecto («×2»). */
    val ticketValue: String
        get() {
            val v = when {
                amount <= 0.0 -> Format.integer(0)
                reachesOne -> Format.decimal(amount)
                amount >= 0.1 -> Format.decimal(amount, 1)
                amount >= 0.01 -> Format.decimal(amount, 2)
                else -> "<${Format.decimal(0.01, 2)}"
            }
            return if (equivalence.wording is Wording.Whole) "×$v" else v
        }

    /** Base de cálculo (atajo a [Equivalence.basis]). */
    val basis: String get() = equivalence.basis

    /** Compatibilidad: la cantidad con mayúscula inicial («14 libros», «Medio grado universitario»). */
    val text: String get() = Format.capitalize(amountText)
}

/**
 * Redacción de cantidades como se dicen, no como se calculan: «un libro», «más de 3 másteres»,
 * «2 idiomas y medio», «casi 5 huevos duros», «medio grado», «el 40 % de un máster».
 * Las cifras exactas quedan para el ticket ([EquivalenceValue.ticketValue]).
 */
internal object Amounts {
    /** Desde aquí ya se lee «una unidad» (0,95 libros = «un libro»). */
    const val ONE = 0.95

    /** Cómo se lee una cantidad ≥ [ONE]. */
    sealed interface Reading {
        val n: Long

        /** «3 libros» */
        data class Exactly(override val n: Long) : Reading

        /** «más de 3 libros» (3,1–3,3) */
        data class MoreThan(override val n: Long) : Reading

        /** «3 libros y medio» (3,4–3,6) */
        data class AndHalf(override val n: Long) : Reading

        /** «casi 4 libros» (3,7–3,9) */
        data class Almost(override val n: Long) : Reading
    }

    /** A partir de 10, entero redondeado; por debajo, «más de», «y medio» o «casi» según la parte decimal. */
    fun read(u: Double): Reading {
        if (u >= 10) return Reading.Exactly(Math.round(u))
        val whole = Math.floor(u).toLong()
        val frac = u - whole
        return when {
            frac < 0.1 -> Reading.Exactly(whole)
            frac > 0.9 -> Reading.Exactly(whole + 1)
            frac < 0.4 -> Reading.MoreThan(whole)
            frac <= 0.6 -> Reading.AndHalf(whole)
            else -> Reading.Almost(whole + 1)
        }
    }

    fun text(u: Double, w: Wording): String = when (w) {
        is Wording.Units -> counted(u, w.counted, w.parts)
        is Wording.Whole -> counted(u, w.counted, w.parts)
        is Wording.Distance -> distance(u, w)
    }

    /** Unidades, obras y trayectos: la frase de cada caso sale de [c] (≥ 1) o de [p] (fracciones). */
    private fun counted(u: Double, c: Counted, p: Parts): String = when {
        u <= 0.0 -> count(c.exactly, 0)
        u >= ONE -> reading(read(u), c)
        u >= 0.85 -> count(c.almost, 1)
        else -> fraction(u, p)
    }

    private fun reading(r: Reading, c: Counted): String = when (r) {
        is Reading.Exactly -> count(c.exactly, r.n)
        is Reading.MoreThan -> count(c.moreThan, r.n)
        is Reading.AndHalf -> count(c.andHalf, r.n)
        is Reading.Almost -> count(c.almost, r.n)
    }

    /** `<plurals>` [id] para [n], con la cifra formateada en `%1$s`. */
    private fun count(id: Int, n: Long): String = plural(id, n, Format.integer(n))

    private fun distance(u: Double, w: Wording.Distance): String {
        if (u >= ONE) return reading(read(u), w.km)
        val metres = Math.round(u * 100) * 10
        return if (metres < 10) count(w.underMetresId, 10) else count(w.metresId, metres)
    }

    /** «el 40 % de un máster», «un cuarto de máster», «medio máster», «tres cuartos de máster». */
    private fun fraction(u: Double, p: Parts): String = when {
        u < 0.001 -> str(p.less, Format.percent(0.001))
        u >= 0.23 && u < 0.27 -> str(p.quarter)
        u >= 0.45 && u < 0.55 -> str(p.half)
        u >= 0.73 && u < 0.77 -> str(p.threeQuarters)
        else -> str(p.percent, Format.percent(u))
    }
}
