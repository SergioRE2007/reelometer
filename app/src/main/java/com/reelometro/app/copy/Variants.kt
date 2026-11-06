package com.reelometro.app.copy

import java.time.LocalDate

/**
 * Elección determinista de variantes de texto.
 *
 * Cada situación («hoy, cero vídeos», «racha rota»…) tiene entre 3 y 6 frases. La variante sale
 * de una **semilla** (para textos diarios, el día: [daySeed]) y de una **clave** propia de la
 * situación, de modo que:
 *
 *  - misma semilla y clave → misma frase: no cambia al recomponer ni al volver a la pantalla;
 *  - días consecutivos → nunca la misma frase dos días seguidos (si hay al menos 2 variantes);
 *  - en cada bloque de N días salen las N variantes, en un orden barajado distinto en cada bloque
 *    (no es una rotación fija, pero tampoco hay variantes que no salgan nunca);
 *  - dos situaciones distintas el mismo día no van «sincronizadas» (cada clave baraja a su manera).
 *
 * Es aritmética pura sobre `Long`: da lo mismo en la JVM de los tests que en el móvil.
 */
object Variants {

    /** Semilla recomendada para textos que cambian una vez al día. */
    fun daySeed(date: LocalDate): Long = date.toEpochDay()

    /** Elige una de [options] para la semilla [seed] y la situación [key]. */
    fun <T> pick(options: List<T>, seed: Long, key: String): T {
        require(options.isNotEmpty()) { "No hay variantes para «$key»" }
        return options[index(options.size, seed, key)]
    }

    /** Índice en `[0, size)` que corresponde a [seed] y [key] (ver reglas en la cabecera). */
    fun index(size: Int, seed: Long, key: String): Int {
        require(size > 0) { "size debe ser > 0" }
        if (size == 1) return 0
        val salt = hash(key)
        // Con dos variantes, la única forma de no repetir es alternar.
        if (size == 2) return Math.floorMod(seed + salt, 2L).toInt()
        val n = size.toLong()
        val block = Math.floorDiv(seed, n)
        val position = Math.floorMod(seed, n).toInt()
        val order = shuffled(size, mix(block, salt))
        // Frontera entre bloques: el último del bloque anterior no puede abrir este.
        // (El ajuste solo toca las posiciones 0 y 1, así que el último de cada bloque es estable.)
        val previousLast = shuffled(size, mix(block - 1, salt))[size - 1]
        if (order[0] == previousLast) {
            val tmp = order[0]
            order[0] = order[1]
            order[1] = tmp
        }
        return order[position]
    }

    /** Baraja determinista de `[0, size)` (Fisher–Yates con SplitMix64). */
    internal fun shuffled(size: Int, seed: Long): IntArray {
        val a = IntArray(size) { it }
        var state = seed
        for (i in size - 1 downTo 1) {
            state += GOLDEN
            val j = Math.floorMod(scramble(state), (i + 1).toLong()).toInt()
            val tmp = a[i]
            a[i] = a[j]
            a[j] = tmp
        }
        return a
    }

    /** Número pseudoaleatorio estable en `[0, bound)` para [seed] y [key]. */
    internal fun number(bound: Int, seed: Long, key: String): Int {
        require(bound > 0)
        return Math.floorMod(scramble(mix(seed, hash(key))), bound.toLong()).toInt()
    }

    /** FNV-1a de 64 bits: hash estable de la clave (no depende de la JVM). */
    internal fun hash(key: String): Long {
        var h = FNV_OFFSET
        for (c in key) {
            h = h xor c.code.toLong()
            h *= FNV_PRIME
        }
        return h
    }

    internal fun mix(a: Long, b: Long): Long = scramble(a * GOLDEN + b)

    /** Paso de mezcla de SplitMix64. */
    private fun scramble(x: Long): Long {
        var z = x
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        return z xor (z ushr 31)
    }

    private val GOLDEN = 0x9E3779B97F4A7C15uL.toLong()
    private val MIX_1 = 0xBF58476D1CE4E5B9uL.toLong()
    private val MIX_2 = 0x94D049BB133111EBuL.toLong()
    private val FNV_OFFSET = 0xCBF29CE484222325uL.toLong()
    private const val FNV_PRIME = 0x100000001B3L
}

/**
 * Variante de una situación: elige una frase del `<string-array>` [arrayId] para [seed] y [key]
 * y rellena sus huecos con [args]: `pick(seed, "day.zero", R.array.day_zero)`.
 *
 * Cada idioma puede tener un número distinto de variantes: la elección usa el tamaño del array
 * que se está leyendo, así que lo determinista (misma semilla, misma frase; no repetir dos días
 * seguidos) se cumple en todos. Las claves ([key]) no se cambian: de ellas sale qué frase toca
 * cada día, y en español tiene que seguir saliendo la misma que antes de los recursos.
 *
 * Si una frase cambia por concordancia (singular o plural, con o sin tiempo…), hay un array por
 * caso con la misma [key]: con el mismo tamaño, sale la misma posición en todos.
 */
fun pick(seed: Long, key: String, arrayId: Int, vararg args: Any): String {
    val texts = AppTexts.current
    val chosen = Variants.pick(texts.array(arrayId), seed, key)
    return if (args.isEmpty()) chosen else texts.format(chosen, *args)
}
