package com.reelometro.app.widget

import com.reelometro.app.game.FlapSprite
import com.reelometro.app.ui.components.flap.FlapGlyphMetrics
import com.reelometro.app.ui.components.flap.FlapProportions
import com.reelometro.app.ui.components.flap.boardWidthEm
import com.reelometro.app.ui.components.flap.fitFontPx
import com.reelometro.app.ui.theme.Space
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** Una línea de texto del widget (JetBrains Mono pintada como máscara). Tamaño en dp. */
internal data class TextSpec(val sizeDp: Float, val weight: Int, val trackingEm: Float)

/** Mide texto en dp (lo implementa [WidgetGlyphs] con la fuente real; en los tests, uno falso). */
internal interface TextMetrics {
    fun width(text: String, spec: TextSpec): Float

    fun lineHeight(spec: TextSpec): Float
}

/** Cómo se colocan las piezas del widget. */
internal enum class Arrangement {
    /** «HOY» (y el objetivo) arriba; el marcador, el tiempo y la barra del objetivo debajo (2×2, 4×2). */
    STACKED,

    /** «HOY» (y el objetivo) arriba; el marcador con el tiempo al lado; la barra debajo (2×1). */
    SIDE,

    /** Una fila: el marcador y una columna estrecha de etiquetas a la derecha (4×1, muy bajos). */
    ROW,
}

/**
 * Maqueta de un tamaño del widget (lógica pura, probada en WidgetModelTest).
 *
 * @property fontDp tamaño de letra de las cifras; las fichas salen de él (como en `FlapBoard`).
 * @property detail estilo de la línea del tiempo (o «SIN SEÑAL»).
 * @property flapDp lado de Flap (a la derecha del marcador, abajo); `0` = sin Flap.
 */
internal data class WidgetLayout(
    val arrangement: Arrangement,
    val padding: Float,
    val innerWidth: Float,
    val fontDp: Float,
    val label: TextSpec,
    val detail: TextSpec,
    val showDetail: Boolean,
    val showStatus: Boolean,
    val showBar: Boolean,
    val flapDp: Float = 0f,
) {
    companion object {
        /** Separación entre líneas y bloques. */
        val GAP: Float = Space.x2.value

        /** Separación entre las líneas de la columna (maqueta en fila). */
        val LINE_GAP: Float = Space.x1.value

        /** Separación mínima entre el marcador y lo que tiene al lado. */
        val COLUMN_GAP: Float = Space.x3.value

        /** Grosor de la barra del objetivo. */
        val BAR: Float = Space.x1.value

        /** Por debajo de este lado, margen de 12 dp en vez de 16. */
        private const val ROOMY = 120f

        /** Cifras más pequeñas que esto no se leen: solo entonces se sacrifica el tiempo. */
        private const val MIN_FONT = 16f

        /** Con objetivo, gana una maqueta con su barra si su marcador llega a este tanto del mayor. */
        private const val GOAL_PREFER = 0.8f

        /** A igualdad aproximada (este tanto del mayor), gana la primera en orden de preferencia. */
        private const val PREFER = 0.9f

        /**
         * Flap solo cabe en el tamaño grande (4×2, 352 × 208): en los otros cinco quitaría sitio al
         * marcador. Por debajo de esto, nunca.
         */
        private const val FLAP_MIN_WIDTH = 300f
        private const val FLAP_MIN_HEIGHT = 180f

        /** Lado de cada píxel de Flap en dp, de mayor a menor (16 × 4 = 64 dp; 16 × 3 = 48 dp). */
        private val FLAP_PIXEL_DP = floatArrayOf(4f, 3f)

        /** Flap no puede encoger el marcador por debajo de este tanto de su tamaño sin Flap. */
        private const val FLAP_KEEP = 0.75f

        /**
         * Elige la maqueta: entre las que enseñan el tiempo, la de marcador más grande; con objetivo,
         * mejor una que enseñe su barra si el marcador no baja de un 80 % del mayor; y a igualdad
         * aproximada (10 %), la primera en este orden: apilada con el tiempo grande, apilada, al lado,
         * en fila. Estilos de texto ya con la escala de fuente: [label] para etiquetas,
         * [dataL]/[data]/[dataS] para el tiempo según el sitio; [maxFontDp], el tope de las cifras.
         */
        fun compute(
            width: Float,
            height: Float,
            model: WidgetModel,
            metrics: FlapGlyphMetrics,
            label: TextSpec,
            dataL: TextSpec,
            data: TextSpec,
            dataS: TextSpec,
            text: TextMetrics,
            maxFontDp: Float,
        ): WidgetLayout {
            val padding = (if (min(width, height) >= ROOMY) Space.x4 else Space.x3).value
            val innerW = (width - 2 * padding).coerceAtLeast(0f)
            val innerH = (height - 2 * padding).coerceAtLeast(0f)
            val widthEm = boardWidthEm(model.cells, metrics.tileWidthEm)
            val heightEm = metrics.tileHeightEm
            fun fit(w: Float, h: Float) = fitFontPx(maxFontDp, widthEm, heightEm, w.coerceAtLeast(0f), h.coerceAtLeast(0f))

            val detail = model.detail
            val status = model.status
            val goal = model.goalFraction != null
            val top = text.lineHeight(label) + GAP
            val bar = if (goal) GAP + BAR else 0f
            val statusFits = status != null && text.width(model.label, label) + GAP + text.width(status, label) <= innerW
            fun layout(arrangement: Arrangement, font: Float, spec: TextSpec, detailShown: Boolean, statusShown: Boolean, barShown: Boolean) =
                WidgetLayout(arrangement, padding, innerW, font, label, spec, detailShown, statusShown, barShown)

            // En orden de preferencia (a igualdad de marcador, gana la primera).
            val candidates = buildList {
                for (spec in listOf(dataL, data)) {
                    val rows = top + (if (detail != null) GAP + text.lineHeight(spec) else 0f) + bar
                    add(layout(Arrangement.STACKED, fit(innerW, innerH - rows), spec, detail != null, statusFits, goal))
                }
                if (detail != null) {
                    var font = fit(innerW - text.width(detail, data) - COLUMN_GAP, innerH - top - bar)
                    if (font * heightEm < text.lineHeight(data)) font = 0f // el tiempo no cabe al lado
                    add(layout(Arrangement.SIDE, font, data, true, statusFits, goal))
                }
                val lines = listOfNotNull(model.label to label, detail?.let { it to dataS }, status?.let { it to label })
                for (n in lines.size downTo 1) {
                    val used = lines.take(n)
                    val columnH = used.sumOf { (_, spec) -> text.lineHeight(spec).toDouble() }.toFloat() + (n - 1) * LINE_GAP
                    if (columnH > innerH && n > 1) continue
                    val columnW = used.maxOf { (t, spec) -> text.width(t, spec) }
                    add(layout(Arrangement.ROW, fit(innerW - columnW - COLUMN_GAP, innerH), dataS, detail != null && n >= 2, status != null && n >= 3, false))
                }
            }
            val pool = candidates.filter { (it.showDetail || detail == null) && it.fontDp >= MIN_FONT }.ifEmpty { candidates }
            val best = pool.maxOf { it.fontDp }
            val choice = pool.filter { it.showBar && it.fontDp >= best * GOAL_PREFER }.ifEmpty { pool }
            val bestChoice = choice.maxOf { it.fontDp }
            val chosen = choice.first { it.fontDp >= bestChoice * PREFER }

            // Flap (modo pasaporte): a la derecha del marcador y el tiempo, abajo, solo en el tamaño
            // grande y apilado, y solo si el marcador no pierde más de un 25 % (con 4–5 cifras, Flap
            // encoge el marcador; con más, no cabe y no sale).
            if (model.flap == null || width < FLAP_MIN_WIDTH || height < FLAP_MIN_HEIGHT || chosen.arrangement != Arrangement.STACKED) {
                return chosen
            }
            val rows = top + (if (detail != null) GAP + text.lineHeight(chosen.detail) else 0f) + (if (chosen.showBar) bar else 0f)
            for (pixel in FLAP_PIXEL_DP) {
                val flap = FlapSprite.SIZE * pixel
                if (flap > innerH - top - (if (chosen.showBar) bar else 0f)) continue
                // El tiempo va debajo del marcador, a la altura de Flap: tampoco puede pisarlo.
                if (detail != null && text.width(detail, chosen.detail) > innerW - flap - COLUMN_GAP) continue
                val font = min(fit(innerW - flap - COLUMN_GAP, innerH - rows), chosen.fontDp)
                if (font >= MIN_FONT && font >= chosen.fontDp * FLAP_KEEP) return chosen.copy(fontDp = font, flapDp = flap)
            }
            return chosen
        }
    }
}

/**
 * Medidas de un marcador del widget en **px reales** (los bitmaps se pintan a ese tamaño exacto,
 * así no se escalan) y su equivalente en dp para la maqueta. Proporciones de `FlapProportions`.
 */
internal data class BoardSpec(
    val fontPx: Float,
    val tileWPx: Int,
    val tileHPx: Int,
    val dotWPx: Int,
    val gapPx: Int,
    /** Línea base de las cifras desde el borde superior de la ficha. */
    val baselinePx: Float,
    /** Ancho del guion de las fichas en blanco. */
    val dashWPx: Float,
    val density: Float,
) {
    val tileW: Float get() = tileWPx / density
    val tileH: Float get() = tileHPx / density
    val dotW: Float get() = dotWPx / density
    val gap: Float get() = gapPx / density
    val dashW: Float get() = dashWPx / density

    /** Bisagra en `bg` sobre el glifo (como en la app: proporcional y entre 1 y 2 dp). */
    val hinge: Float get() = (fontPx * FlapProportions.HINGE / density).coerceIn(1f, 2f)

    companion object {
        fun of(fontDp: Float, metrics: FlapGlyphMetrics, density: Float): BoardSpec {
            val fs = fontDp * density
            return BoardSpec(
                fontPx = fs,
                tileWPx = floor(fs * metrics.tileWidthEm).toInt().coerceAtLeast(1),
                tileHPx = floor(fs * metrics.tileHeightEm).toInt().coerceAtLeast(1),
                dotWPx = ceil(fs * FlapProportions.DOT).toInt().coerceAtLeast(1),
                gapPx = (fs * FlapProportions.GAP).roundToInt().coerceAtLeast(1),
                baselinePx = fs * FlapProportions.PAD_Y - fs * metrics.inkTopEm,
                dashWPx = fs * metrics.advanceEm * FlapProportions.DASH_WIDTH,
                density = density,
            )
        }
    }
}
