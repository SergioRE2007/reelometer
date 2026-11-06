package com.reelometro.app.stats

import com.reelometro.app.copy.Variants
import kotlin.math.roundToLong

/**
 * «Con ese tiempo podrías…»: el tiempo viendo vídeos cortos traducido a cosas que llevan un
 * número conocido de horas. Cada equivalencia lleva su **base de cálculo** ([Equivalence.basis])
 * para la nota discreta de la interfaz (`dataS`, `textMuted`).
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
        "grado", "🎓", 240 * 25.0, "Base: 240 ECTS × 25 h (Plan Bolonia)", "GRADOS UNIVERSITARIOS",
        EquivalenceGroup.ESTUDIO, absurd = false,
        Wording.Units("sacarte", "grado universitario", "grados universitarios"),
    )
    val MASTER = Equivalence(
        "master", "📜", 60 * 25.0, "Base: 60 ECTS × 25 h (máster de un curso)", "MÁSTERES",
        EquivalenceGroup.ESTUDIO, absurd = false,
        Wording.Units("hacer", "máster", "másteres"),
    )
    val LANGUAGE = Equivalence(
        "idioma", "🌍", 600.0, "Base: FSI, ~600–750 h para idiomas de categoría I", "IDIOMAS",
        EquivalenceGroup.ESTUDIO, absurd = false,
        Wording.Units("aprender", "idioma", "idiomas"),
    )
    val CODING = Equivalence(
        "bootcamp", "💻", 400.0, "Base: bootcamp intensivo de programación, ~400 h", "BOOTCAMPS DE PROGRAMACIÓN",
        EquivalenceGroup.ESTUDIO, absurd = false,
        Wording.Units("hacer", "bootcamp", "bootcamps", tail = "de programación"),
    )

    // ------------------------------------------------------------------ lectura

    val BOOK = Equivalence(
        "libro", "📚", 90_000 / 250.0 / 60, "Base: novela de ~90.000 palabras a 250 palabras/min ≈ 6 h", "LIBROS",
        EquivalenceGroup.LECTURA, absurd = false,
        Wording.Units("leer", "libro", "libros"),
    )
    val QUIJOTE = Equivalence(
        "quijote", "🐴", 380_000 / 250.0 / 60, "Base: las dos partes, ~380.000 palabras a 250 palabras/min ≈ 25 h", "EL QUIJOTE",
        EquivalenceGroup.LECTURA, absurd = false,
        Wording.Whole("leer", "el Quijote entero", "del Quijote"),
    )
    val PROUST = Equivalence(
        "proust", "⏳", 1_270_000 / 250.0 / 60,
        "Base: 7 tomos, ~1.270.000 palabras a 250 palabras/min ≈ 85 h", "EN BUSCA DEL TIEMPO PERDIDO",
        EquivalenceGroup.LECTURA, absurd = false,
        Wording.Whole("leer", "En busca del tiempo perdido", "de En busca del tiempo perdido"),
    )

    // ------------------------------------------------------------------ pantalla (de la grande)

    val GOT = Equivalence(
        "juego-de-tronos", "🐉", 70.0, "Base: 8 temporadas, 73 episodios ≈ 70 h", "JUEGO DE TRONOS",
        EquivalenceGroup.PANTALLA, absurd = false,
        Wording.Whole("ver", "Juego de Tronos entera", "de Juego de Tronos"),
    )
    val HARRY_POTTER = Equivalence(
        "harry-potter", "⚡", 1_179 / 60.0, "Base: 8 películas, 1.179 min ≈ 19 h 40 min", "SAGA DE HARRY POTTER",
        EquivalenceGroup.PANTALLA, absurd = false,
        Wording.Whole("ver", "la saga de Harry Potter entera", "de la saga de Harry Potter"),
    )
    val LOTR = Equivalence(
        "senor-de-los-anillos", "💍", 726 / 60.0, "Base: trilogía en versión extendida, 726 min ≈ 12 h", "EL SEÑOR DE LOS ANILLOS",
        EquivalenceGroup.PANTALLA, absurd = false,
        Wording.Whole("ver", "El Señor de los Anillos en versión extendida", "de El Señor de los Anillos en versión extendida"),
    )
    val MOVIE = Equivalence(
        "pelicula", "🎬", 2.0, "Base: película media ≈ 2 h", "PELÍCULAS",
        EquivalenceGroup.PANTALLA, absurd = false,
        Wording.Units("ver", "película", "películas", feminine = true),
    )
    val FOOTBALL = Equivalence(
        "partido", "⚽", 1.75, "Base: 90 min + 15 de descanso ≈ 1 h 45 min", "PARTIDOS DE FÚTBOL",
        EquivalenceGroup.PANTALLA, absurd = false,
        Wording.Units("ver", "partido", "partidos", tail = "de fútbol"),
    )

    // ------------------------------------------------------------------ deporte y viajes

    val MARATHON = Equivalence(
        "maraton", "🏃", 4.5, "Base: 42,195 km a ritmo popular ≈ 4 h 30 min", "MARATONES",
        EquivalenceGroup.DEPORTE, absurd = false,
        Wording.Units("correr", "maratón", "maratones", feminine = true),
    )
    val CAMINO = Equivalence(
        "camino-de-santiago", "🐚", 780 / 4.0, "Base: Camino Francés, ~780 km a 4 km/h ≈ 195 h andando", "CAMINO DE SANTIAGO",
        EquivalenceGroup.VIAJE, absurd = false,
        Wording.Whole("hacer", "el Camino de Santiago entero", "del Camino de Santiago"),
    )
    val TOLEDO = Equivalence(
        "madrid-toledo", "🥾", 72 / 5.0, "Base: ~72 km a 5 km/h ≈ 14,4 h", "MADRID–TOLEDO A PIE",
        EquivalenceGroup.VIAJE, absurd = false,
        Wording.Whole("ir andando", "de Madrid a Toledo", "del camino de Madrid a Toledo"),
    )
    val AVE = Equivalence(
        "ave", "🚄", 2.5, "Base: AVE directo ≈ 2 h 30 min", "AVE MADRID–BARCELONA",
        EquivalenceGroup.VIAJE, absurd = false,
        Wording.Whole("ir en AVE", "de Madrid a Barcelona", "del trayecto de Madrid a Barcelona"),
    )
    val FLIGHT = Equivalence(
        "vuelo-nueva-york", "✈️", 8.0, "Base: vuelo directo ≈ 8 h", "VUELO MADRID–NUEVA YORK",
        EquivalenceGroup.VIAJE, absurd = false,
        Wording.Whole("volar", "de Madrid a Nueva York", "del vuelo de Madrid a Nueva York"),
    )
    val WALK = Equivalence(
        "km-a-pie", "🚶", 1 / 5.0, "Base: 5 km/h, paso normal", "KILÓMETROS A PIE",
        EquivalenceGroup.VIAJE, absurd = false,
        Wording.Distance("ir andando"),
    )

    // ------------------------------------------------------------------ cocina, música y sofá

    val TORTILLA = Equivalence(
        "tortilla", "🥔", 0.75, "Base: pelar, freír y cuajar ≈ 45 min", "TORTILLAS DE PATATAS",
        EquivalenceGroup.COCINA, absurd = true,
        Wording.Units("hacer", "tortilla", "tortillas", feminine = true, tail = "de patatas"),
    )
    val COCIDO = Equivalence(
        "cocido", "🍲", 3.5, "Base: 3 h 30 min a fuego lento (el remojo no cuenta)", "COCIDOS",
        EquivalenceGroup.COCINA, absurd = true,
        Wording.Units("hacer", "cocido madrileño", "cocidos madrileños"),
    )
    val EGG = Equivalence(
        "huevo-duro", "🥚", 10 / 60.0, "Base: 10 min de cocción", "HUEVOS DUROS",
        EquivalenceGroup.COCINA, absurd = true,
        Wording.Units("cocer", "huevo duro", "huevos duros"),
    )
    val MILK = Equivalence(
        "vaso-de-leche", "🥛", 1 / 60.0, "Base: 1 min a máxima potencia", "VASOS DE LECHE AL MICROONDAS",
        EquivalenceGroup.COCINA, absurd = true,
        Wording.Units("calentar", "vaso", "vasos", tail = "de leche en el microondas"),
    )
    val BOHEMIAN = Equivalence(
        "bohemian-rhapsody", "🎸", (5 * 60 + 55) / 3600.0, "Base: 5 min 55 s, versión de estudio", "BOHEMIAN RHAPSODY",
        EquivalenceGroup.MUSICA, absurd = true,
        Wording.Whole("escuchar", "Bohemian Rhapsody", "de Bohemian Rhapsody"),
    )
    val NAP = Equivalence(
        "siesta", "😴", 0.5, "Base: siesta de 30 min (sin pijama)", "SIESTAS",
        EquivalenceGroup.DESCANSO, absurd = true,
        Wording.Units("echarte", "siesta", "siestas", feminine = true),
    )
    val SLEEP = Equivalence(
        "noche", "🛌", 8.0, "Base: 8 h por noche", "NOCHES DEL TIRÓN",
        EquivalenceGroup.DESCANSO, absurd = false,
        Wording.Units("dormir", "noche", "noches", feminine = true, tail = "del tirón"),
    )
    val WORKDAY = Equivalence(
        "jornada", "💼", 8.0, "Base: jornada completa de 8 h", "JORNADAS LABORALES",
        EquivalenceGroup.TRABAJO, absurd = false,
        Wording.Units("echar", "jornada laboral", "jornadas laborales", feminine = true),
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
        if (ms < 1_000) return "Todavía no hay tiempo que comparar."
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

/** Une cifra y unidad con espacio duro en las notas de base («25 h», «780 km»). */
private fun nbsp(text: String): String = text.replace(Regex("(\\d) "), "$1${Format.NBSP}")

/** Familia temática, para no repetir dos del mismo tipo en un titular. */
enum class EquivalenceGroup { ESTUDIO, LECTURA, PANTALLA, DEPORTE, VIAJE, COCINA, MUSICA, DESCANSO, TRABAJO }

/** Cómo se dice una equivalencia en español. */
sealed interface Wording {
    /**
     * Lo que se cuenta por unidades: «14 libros», «medio grado universitario», «el 40 % de un máster».
     * [tail] es el complemento con preposición, que va detrás del «y medio»:
     * «una tortilla y media **de patatas**», «un vaso y medio **de leche en el microondas**».
     */
    data class Units(
        val verb: String,
        val singular: String,
        val plural: String,
        val feminine: Boolean = false,
        val tail: String = "",
    ) : Wording

    /** Una obra o un trayecto concreto que se repite: «ver Juego de Tronos entera 2 veces». */
    data class Whole(val verb: String, val once: String, val of: String) : Wording

    /** Distancias a pie: «ir andando 5 kilómetros», «ir andando 830 metros». */
    data class Distance(val verb: String) : Wording
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
    basis: String,
    val label: String,
    val group: EquivalenceGroup,
    val absurd: Boolean,
    val wording: Wording,
) {
    val basis: String = nbsp(basis)

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
    val infinitive: String get() = "${verb(equivalence.wording)} $amountText"

    /** Frase completa, con variantes por [seed]: «Con ese tiempo podrías leer 14 libros.». */
    fun sentence(seed: Long): String = Variants.pick(SENTENCES, seed, "eq.sentence")(infinitive)

    /** Valor para una línea de ticket: «14», «0,5», «0,04»; con «×» delante si es una obra o trayecto («×2»). */
    val ticketValue: String
        get() {
            val v = when {
                amount <= 0.0 -> "0"
                reachesOne -> Format.decimal(amount)
                amount >= 0.1 -> Format.decimal(amount, 1)
                amount >= 0.01 -> Format.decimal(amount, 2)
                else -> "<0,01"
            }
            return if (equivalence.wording is Wording.Whole) "×$v" else v
        }

    /** Base de cálculo (atajo a [Equivalence.basis]). */
    val basis: String get() = equivalence.basis

    /** Compatibilidad: la cantidad con mayúscula inicial («14 libros», «Medio grado universitario»). */
    val text: String get() = Format.capitalize(amountText)

    private companion object {
        val SENTENCES: List<(String) -> String> = listOf(
            { "Con ese tiempo podrías $it." },
            { "Te daba para $it." },
            { "Eso da para $it." },
        )

        fun verb(w: Wording): String = when (w) {
            is Wording.Units -> w.verb
            is Wording.Whole -> w.verb
            is Wording.Distance -> w.verb
        }
    }
}

/**
 * Redacción de cantidades como se dicen, no como se calculan: «un libro», «más de 3 másteres»,
 * «2 idiomas y medio», «casi 5 huevos duros», «medio grado», «el 40 % de un máster».
 * Las cifras exactas quedan para el ticket ([EquivalenceValue.ticketValue]).
 */
internal object Amounts {
    private val NB = Format.NBSP

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
        is Wording.Units -> units(u, w)
        is Wording.Whole -> whole(u, w)
        is Wording.Distance -> distance(u)
    }

    private fun units(u: Double, w: Wording.Units): String {
        val un = if (w.feminine) "una" else "un"
        val medio = if (w.feminine) "media" else "medio"
        val tail = if (w.tail.isEmpty()) "" else " ${w.tail}"
        fun count(k: Long) = if (k == 1L) "$un ${w.singular}" else "${Format.integer(k)}$NB${w.plural}"
        return when {
            u <= 0.0 -> "0$NB${w.plural}$tail"
            u >= ONE -> when (val r = read(u)) {
                is Reading.Exactly -> "${count(r.n)}$tail"
                is Reading.MoreThan -> "más de ${count(r.n)}$tail"
                is Reading.AndHalf -> "${count(r.n)} y $medio$tail"
                is Reading.Almost -> "casi ${count(r.n)}$tail"
            }
            u >= 0.85 -> "casi $un ${w.singular}$tail"
            else -> fraction(
                u,
                articleOf = "de $un ${w.singular}$tail",
                bareOf = "de ${w.singular}$tail",
                half = "$medio ${w.singular}$tail",
            )
        }
    }

    private fun whole(u: Double, w: Wording.Whole): String {
        fun times(k: Long) = if (k == 1L) "una vez" else "${Format.integer(k)}${NB}veces"
        return when {
            u <= 0.0 -> "${w.once} 0${NB}veces"
            u >= ONE -> when (val r = read(u)) {
                is Reading.Exactly -> if (r.n == 1L) w.once else "${w.once} ${times(r.n)}"
                is Reading.MoreThan -> "${w.once} más de ${times(r.n)}"
                is Reading.AndHalf -> "${w.once} ${times(r.n)} y media"
                is Reading.Almost -> "${w.once} casi ${times(r.n)}"
            }
            u >= 0.85 -> "casi ${w.once}"
            else -> fraction(u, articleOf = w.of, bareOf = w.of, half = "la mitad ${w.of}")
        }
    }

    private fun distance(u: Double): String {
        fun km(k: Long) = if (k == 1L) "un kilómetro" else "${Format.integer(k)}${NB}kilómetros"
        if (u >= ONE) {
            return when (val r = read(u)) {
                is Reading.Exactly -> km(r.n)
                is Reading.MoreThan -> "más de ${km(r.n)}"
                is Reading.AndHalf -> "${km(r.n)} y medio"
                is Reading.Almost -> "casi ${km(r.n)}"
            }
        }
        val metres = Math.round(u * 100) * 10
        return if (metres < 10) "menos de 10${NB}metros" else "${Format.integer(metres)}${NB}metros"
    }

    /** «el 40 % de un máster», «un cuarto de máster», «medio máster», «tres cuartos de máster». */
    private fun fraction(u: Double, articleOf: String, bareOf: String, half: String): String = when {
        u < 0.001 -> "menos del 0,1$NB% $articleOf"
        u >= 0.23 && u < 0.27 -> "un cuarto $bareOf"
        u >= 0.45 && u < 0.55 -> half
        u >= 0.73 && u < 0.77 -> "tres cuartos $bareOf"
        else -> "el ${Format.percent(u)} $articleOf"
    }
}
