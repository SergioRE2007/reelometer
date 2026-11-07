package com.reelometro.app.ui.components.flap

import androidx.compose.runtime.Immutable
import com.reelometro.app.stats.Format
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * Lógica pura del marcador split-flap (sin Android): qué fichas hay, por qué glifos pasa cada
 * columna al voltear y qué proporciones tiene cada ficha. Probada en FlapModelTest.
 */

/** Qué muestra un marcador split-flap. */
@Immutable
sealed interface FlapContent {
    /** Cifra entera con punto de millar a la española (1.234). Se muestra su valor absoluto. */
    @Immutable
    data class Number(val value: Long) : FlapContent

    /** Fichas en blanco con guion («– – –»): sin dato, servicio desactivado. */
    @Immutable
    data object Blank : FlapContent

    /**
     * Texto en fichas, una letra por ficha (p. ej. «OK»). Se pasa a mayúsculas; los espacios son
     * fichas vacías. Se alinea a la derecha dentro del mínimo de columnas, igual que las cifras.
     */
    @Immutable
    data class Text(val text: String) : FlapContent
}

/** Relleno a la izquierda cuando la cifra tiene menos dígitos que el mínimo de columnas. */
enum class FlapPad {
    /** «007»: ceros, como un marcador mecánico. */
    Zeros,

    /** «  7»: fichas vacías. */
    Blank,
}

/** Desde dónde voltea el marcador la primera vez que aparece. */
enum class FlapEnter {
    /** Desde fichas vacías: todas las columnas voltean (el panel «se despierta»). */
    Blank,

    /** Desde ceros: solo voltean las columnas que no son 0. */
    Zero,

    /** Sin animación de entrada. */
    None,
}

/** Glifos especiales y tambores de volteo. */
internal object FlapGlyphs {
    /** Ficha vacía (no se dibuja glifo). */
    const val EMPTY = ' '

    /** Guion de las fichas en blanco. Se dibuja como una barra centrada en la bisagra. */
    const val DASH = '–'

    const val DIGITS = "0123456789"
    const val LETTERS = "ABCDEFGHIJKLMNÑOPQRSTUVWXYZ"

    /** Tope de volteos por columna en un cambio (los saltos largos se recortan por el principio). */
    const val MAX_FLIPS = 10
}

/** Una posición del marcador, de izquierda a derecha. */
internal sealed interface FlapCell {
    /** Ficha. [position] cuenta desde la derecha (0 = unidades) y es su identidad estable. */
    data class Tile(val position: Int, val glyph: Char) : FlapCell

    /** Punto de millar entre dos fichas. */
    data object Separator : FlapCell
}

internal val List<FlapCell>.tiles: List<FlapCell.Tile> get() = filterIsInstance<FlapCell.Tile>()

/**
 * Reparte [content] en fichas. Las cifras se alinean a la derecha con al menos [minColumns]
 * columnas y llevan punto de millar entre dígitos reales (los ceros de relleno no llevan punto:
 * «0999» → «1.000»).
 */
internal fun flapCells(content: FlapContent, minColumns: Int, pad: FlapPad): List<FlapCell> {
    val cols = minColumns.coerceAtLeast(1)
    return when (content) {
        is FlapContent.Number -> {
            val digits = content.value.toString().removePrefix("-")
            val n = max(digits.length, cols)
            buildList {
                for (pos in n - 1 downTo 0) {
                    val real = pos < digits.length
                    val glyph = when {
                        real -> digits[digits.length - 1 - pos]
                        pad == FlapPad.Zeros -> '0'
                        else -> FlapGlyphs.EMPTY
                    }
                    add(FlapCell.Tile(pos, glyph))
                    if (real && pos > 0 && pos % 3 == 0) add(FlapCell.Separator)
                }
            }
        }

        FlapContent.Blank -> List(cols) { i -> FlapCell.Tile(cols - 1 - i, FlapGlyphs.DASH) }

        is FlapContent.Text -> {
            val text = Format.upper(content.text)
            val n = max(text.length, cols)
            List(n) { i ->
                val pos = n - 1 - i
                val idx = text.length - 1 - pos
                FlapCell.Tile(pos, if (idx >= 0) text[idx] else FlapGlyphs.EMPTY)
            }
        }
    }
}

/**
 * Glifos por los que pasa una columna al ir de [from] a [to], sin incluir [from] y terminando en
 * [to]. Como un tambor real, solo gira hacia delante: 7 → 2 pasa por 8, 9, 0, 1. Desde una ficha
 * vacía, un guion o una letra, las cifras empiezan en 0 (y las letras en A). Hacia un glifo fuera
 * de los tambores (vacío, guion…) se llega en un solo volteo. Con más de [maxFlips] pasos se
 * conservan los últimos, para que la llegada siga siendo consecutiva.
 */
internal fun flapPath(from: Char, to: Char, maxFlips: Int = FlapGlyphs.MAX_FLIPS): List<Char> {
    if (from == to) return emptyList()
    val full = when (to) {
        in FlapGlyphs.DIGITS -> drumPath(FlapGlyphs.DIGITS, from, to)
        in FlapGlyphs.LETTERS -> drumPath(FlapGlyphs.LETTERS, from, to)
        else -> listOf(to)
    }
    val cap = maxFlips.coerceAtLeast(1)
    return if (full.size > cap) full.takeLast(cap) else full
}

private fun drumPath(drum: String, from: Char, to: Char): List<Char> {
    val t = drum.indexOf(to)
    val f = drum.indexOf(from)
    if (f < 0) return drum.substring(0, t + 1).toList()
    val steps = (t - f + drum.length) % drum.length
    return List(steps) { k -> drum[(f + 1 + k) % drum.length] }
}

/**
 * Estado visual de una columna: en reposo ([fraction] = 0) o volteando de [from] a [to]. Es
 * mutable para reutilizar una sola instancia al dibujar (sin asignar memoria por fotograma).
 */
internal data class FlapFrame(
    var from: Char = FlapGlyphs.EMPTY,
    var to: Char = FlapGlyphs.EMPTY,
    var fraction: Float = 0f,
) {
    fun set(from: Char, to: Char, fraction: Float): FlapFrame {
        this.from = from
        this.to = to
        this.fraction = fraction
        return this
    }

    /**
     * Traduce el progreso continuo de una columna ([progress] ∈ [0, seq.lastIndex], un volteo por
     * unidad) a lo que hay que dibujar. Si el muelle de llegada se pasa del final, el exceso se
     * refleja como un rebote de la última hoja (se levanta un poco y vuelve a caer).
     */
    fun resolve(seq: List<Char>, progress: Float): FlapFrame {
        val last = seq.lastIndex
        if (last <= 0) {
            val g = seq.firstOrNull() ?: FlapGlyphs.EMPTY
            return set(g, g, 0f)
        }
        var p = progress
        if (p > last) p = max(last - 1f, 2f * last - p)
        p = p.coerceIn(0f, last.toFloat())
        val k = min(floor(p).toInt(), last)
        val f = p - k
        return if (k >= last || f <= 0f) set(seq[k], seq[k], 0f) else set(seq[k], seq[k + 1], f)
    }
}

/** [FlapFrame.resolve] en una instancia nueva (para pruebas y usos puntuales). */
internal fun flapFrame(seq: List<Char>, progress: Float): FlapFrame = FlapFrame().resolve(seq, progress)

/**
 * Punto desde el que retomar una columna cuando cambia el destino a mitad de animación: si está
 * volteando, se conserva la hoja en movimiento (y su fracción) para que no salte.
 */
internal fun flapRestart(seq: List<Char>, progress: Float): Pair<List<Char>, Float> {
    val last = seq.lastIndex
    if (last <= 0) return listOf(seq.firstOrNull() ?: FlapGlyphs.EMPTY) to 0f
    val p = progress.coerceIn(0f, last.toFloat())
    val k = min(floor(p).toInt(), last)
    val f = p - k
    return if (k >= last || f < 0.001f) listOf(seq[k]) to 0f else listOf(seq[k], seq[k + 1]) to f
}

/** Proporciones de las fichas en «em» (múltiplos del tamaño de letra). */
internal object FlapProportions {
    /** Aire a cada lado del glifo. */
    const val PAD_X = 0.075f

    /** Aire por encima y por debajo de la tinta de las cifras. */
    const val PAD_Y = 0.11f

    /** Hueco entre fichas. */
    const val GAP = 0.045f

    /** Diámetro del punto de millar. */
    const val DOT = 0.07f

    /** Guion de las fichas en blanco: ancho (respecto al ancho del glifo) y grosor. */
    const val DASH_WIDTH = 0.72f
    const val DASH_THICKNESS = 0.075f

    /** Distancia de la cámara para la perspectiva del volteo, en alturas de ficha. */
    const val CAMERA_DISTANCE = 5f

    /** Grosor de la bisagra (se acota además a 1–2 dp). */
    const val HINGE = 0.012f
}

/** Métricas tipográficas de las cifras, en em: avance del glifo más ancho y tinta respecto a la base. */
internal data class FlapGlyphMetrics(
    val advanceEm: Float,
    /** Borde superior de la tinta de las cifras respecto a la línea base (negativo: hacia arriba). */
    val inkTopEm: Float,
    /** Borde inferior (≈ 0; algo positivo por el rebase de las cifras redondas). */
    val inkBottomEm: Float,
) {
    val tileWidthEm: Float get() = advanceEm + 2 * FlapProportions.PAD_X
    val tileHeightEm: Float get() = (inkBottomEm - inkTopEm) + 2 * FlapProportions.PAD_Y
}

/** Ancho total del marcador en em. */
internal fun boardWidthEm(cells: List<FlapCell>, tileWidthEm: Float): Float {
    var tiles = 0
    var separators = 0
    for (c in cells) if (c is FlapCell.Tile) tiles++ else separators++
    if (tiles == 0) return 0f
    return tiles * tileWidthEm + (tiles - 1) * FlapProportions.GAP +
        separators * (FlapProportions.DOT + FlapProportions.GAP)
}

/**
 * Autoajuste: el mayor tamaño de letra (px) que no supera [maxFontPx] y hace que el marcador
 * quepa en el ancho (y alto, si está acotado) disponible. Nunca parte ni corta: encoge.
 */
internal fun fitFontPx(
    maxFontPx: Float,
    widthEm: Float,
    heightEm: Float,
    maxWidthPx: Float = Float.POSITIVE_INFINITY,
    maxHeightPx: Float = Float.POSITIVE_INFINITY,
): Float {
    var fs = maxFontPx
    if (widthEm > 0f && maxWidthPx.isFinite()) fs = min(fs, maxWidthPx / widthEm)
    if (heightEm > 0f && maxHeightPx.isFinite()) fs = min(fs, maxHeightPx / heightEm)
    return fs.coerceAtLeast(0f)
}

/** Cómo se dibuja el separador de miles entre fichas, según el idioma ([Format.groupingSeparator]). */
internal enum class FlapGroupMark {
    /** «1.234» (español, italiano, portugués de Brasil). */
    DOT,

    /** «1,234» (inglés): un punto con rabito. */
    COMMA,

    /** «1 234» (francés): solo el hueco. */
    SPACE,
    ;

    companion object {
        fun of(separator: Char): FlapGroupMark = when {
            separator == ',' -> COMMA
            separator.isWhitespace() || Character.isSpaceChar(separator) -> SPACE
            else -> DOT
        }

        /** La del idioma actual de los textos. */
        val current: FlapGroupMark get() = of(Format.groupingSeparator)
    }
}
