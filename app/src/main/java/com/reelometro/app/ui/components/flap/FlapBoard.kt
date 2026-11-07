package com.reelometro.app.ui.components.flap

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.resolveAsTypeface
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withMatrix
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelColors
import com.reelometro.app.ui.theme.Shapes
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Motor del marcador split-flap: estado de cada columna, muelles del volteo y dibujo en un único
 * nodo (layout + drawWithCache). El progreso de las animaciones solo se lee en la fase de dibujo:
 * mientras voltea no se recompone nada.
 */

/** Muelles del volteo. */
internal object FlapSprings {
    /** Hojas intermedias: rápidas y sin rebote (≈ 85 ms por hoja, unas 12 por segundo). */
    val rapid: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 6000f, visibilityThreshold = 0.01f)

    /** Última hoja: cae y rebota un poco al asentarse (el exceso del muelle se dibuja como rebote). */
    val landing: SpringSpec<Float> = Motion.snappy()

    /** Cargando: volteo pausado. */
    val loader: SpringSpec<Float> = Motion.standard()

    const val LOADER_STAGGER_MS = 110L
    const val LOADER_PAUSE_MS = 520L
}

/** Una columna del marcador: la secuencia de glifos por la que pasa y su progreso (un volteo por unidad). */
@Stable
internal class FlapColumn(initial: Char) {
    var seq: List<Char> by mutableStateOf(listOf(initial))
        private set
    var progress: Float by mutableFloatStateOf(0f)
        private set

    /** Está a mitad de una hoja (no en reposo). */
    val midFlip: Boolean get() = seq.size > 1 && progress > 0f && progress < seq.lastIndex

    fun snap(glyph: Char) {
        seq = listOf(glyph)
        progress = 0f
    }

    /** Prepara el volteo hacia [glyph] desde lo que se ve ahora. `false` si no hay nada que voltear. */
    fun retarget(glyph: Char, maxFlips: Int): Boolean {
        val (base, start) = flapRestart(seq, progress)
        val path = flapPath(base.last(), glyph, maxFlips)
        if (path.isEmpty() && base.size == 1) {
            if (seq.size != 1 || progress != 0f) snap(base[0])
            return false
        }
        seq = base + path
        progress = start
        return true
    }

    /** Recorre la secuencia hoja a hoja; la última con el muelle de llegada. */
    suspend fun run() {
        val last = seq.lastIndex
        var k = floor(progress).toInt()
        while (k < last) {
            val spec = if (k + 1 == last) FlapSprings.landing else FlapSprings.rapid
            animate(progress, (k + 1).toFloat(), animationSpec = spec) { v, _ -> progress = v }
            k++
        }
        snap(seq.last())
    }

    /** Una hoja del glifo actual sobre sí mismo (estado cargando). */
    suspend fun flipInPlace() {
        val g = seq.last()
        seq = listOf(g, g)
        progress = 0f
        animate(0f, 1f, animationSpec = FlapSprings.loader) { v, _ -> progress = v }
        snap(g)
    }
}

/** Columnas de un marcador, por posición desde la derecha. */
@Stable
internal class FlapBoardState {
    private val columns = HashMap<Int, FlapColumn>()
    private var synced = false

    fun column(position: Int): FlapColumn? = columns[position]

    /**
     * Crea las columnas que falten (la primera vez, según [enter]; después, desde ficha vacía) y
     * quita las que sobran. Idempotente: se llama al cambiar las fichas, antes de dibujar.
     */
    fun sync(cells: List<FlapCell>, enter: FlapEnter) {
        val tiles = cells.tiles
        val keep = HashSet<Int>(tiles.size * 2)
        for (t in tiles) {
            keep += t.position
            if (t.position !in columns) {
                val start = if (synced) FlapGlyphs.EMPTY else when (enter) {
                    FlapEnter.Blank -> FlapGlyphs.EMPTY
                    FlapEnter.Zero -> if (t.glyph in FlapGlyphs.DIGITS) '0' else FlapGlyphs.EMPTY
                    FlapEnter.None -> t.glyph
                }
                columns[t.position] = FlapColumn(start)
            }
        }
        columns.keys.retainAll(keep)
        synced = true
    }

    /**
     * Voltea cada columna hasta su glifo, escalonadas de izquierda a derecha entre las que cambian.
     * Con [reduced] el cambio es instantáneo. Llama a [onSettled] una vez, cuando todas han caído.
     */
    suspend fun animateTo(cells: List<FlapCell>, reduced: Boolean, maxFlips: Int, onSettled: () -> Unit) {
        val tiles = cells.tiles
        if (reduced) {
            for (t in tiles) columns[t.position]?.snap(t.glyph)
            return
        }
        var moved = false
        coroutineScope {
            var order = 0
            for (t in tiles) {
                val col = columns[t.position] ?: continue
                val wasMoving = col.midFlip
                if (!col.retarget(t.glyph, maxFlips)) continue
                moved = true
                // Una hoja que ya estaba en el aire sigue sin esperar; el resto, escalonado.
                val wait = if (wasMoving) 0L else order++ * Motion.STAGGER_DIGIT_MS
                launch {
                    if (wait > 0) delay(wait)
                    col.run()
                }
            }
        }
        if (moved) onSettled()
    }

    /** Bucle de «cargando»: cada ficha voltea sobre sí misma, en ola, hasta que se cancela. */
    suspend fun loop(cells: List<FlapCell>) = coroutineScope {
        cells.tiles.forEachIndexed { i, t ->
            val col = columns[t.position] ?: return@forEachIndexed
            launch {
                delay(i * FlapSprings.LOADER_STAGGER_MS)
                while (isActive) {
                    col.flipInPlace()
                    delay(FlapSprings.LOADER_PAUSE_MS)
                }
            }
        }
    }
}

/**
 * Color del sombreado: tinta casi negra en ambos temas (el fondo en oscuro, el texto en claro). Sobre
 * papel la tinta contrasta más, así que en claro va atenuada para que la sombra pese lo mismo.
 */
internal fun flapShade(colors: ReelColors): Color = if (colors.isDark) colors.bg else colors.text.copy(alpha = 0.7f)

/** Mide la tinta y el avance de los glifos con la misma Typeface (y ejes variables) que dibuja Compose. */
internal class FlapFontMeasurer(typeface: Typeface, letterSpacingEm: Float, features: String?) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = REF
        letterSpacing = letterSpacingEm
        if (features != null) fontFeatureSettings = features
    }
    private val advances = HashMap<Char, Float>()

    val metrics: FlapGlyphMetrics = run {
        val r = Rect()
        paint.getTextBounds(FlapGlyphs.DIGITS, 0, FlapGlyphs.DIGITS.length, r)
        val advance = FlapGlyphs.DIGITS.maxOf { paint.measureText(it.toString()) } / REF
        FlapGlyphMetrics(advanceEm = advance, inkTopEm = r.top / REF, inkBottomEm = r.bottom / REF)
    }

    fun advanceEm(c: Char): Float = when (c) {
        FlapGlyphs.EMPTY, FlapGlyphs.DASH -> metrics.advanceEm
        in FlapGlyphs.DIGITS -> metrics.advanceEm
        else -> advances.getOrPut(c) { paint.measureText(c.toString()) / REF }
    }

    private companion object {
        const val REF = 512f
    }
}

/** [FlapFontMeasurer] para [style], con la Typeface que resuelve Compose (ejes variables incluidos). */
@Composable
internal fun rememberFlapFontMeasurer(style: TextStyle): FlapFontMeasurer {
    val resolver = LocalFontFamilyResolver.current
    val typeface by remember(resolver, style.fontFamily, style.fontWeight, style.fontStyle, style.fontSynthesis) {
        resolver.resolveAsTypeface(
            fontFamily = style.fontFamily,
            fontWeight = style.fontWeight ?: FontWeight.Normal,
            fontStyle = style.fontStyle ?: FontStyle.Normal,
            fontSynthesis = style.fontSynthesis ?: FontSynthesis.All,
        )
    }
    val letterSpacingEm = if (style.letterSpacing.isEm) style.letterSpacing.value else 0f
    return remember(typeface, letterSpacingEm, style.fontFeatureSettings) {
        FlapFontMeasurer(typeface, letterSpacingEm, style.fontFeatureSettings)
    }
}

/** Proyección en perspectiva de media ficha girando sobre la bisagra (rotationX con cámara). */
internal class FlapProjector {
    private val matrix = Matrix()
    private val values = FloatArray(9)

    /**
     * [angle] en radianes desde la posición plana. [topHalf]: la hoja superior cae hacia el
     * espectador; si no, la inferior se posa. [distance] es la distancia de la cámara en px.
     */
    fun matrix(angle: Float, topHalf: Boolean, pivotX: Float, pivotY: Float, distance: Float): Matrix {
        val s = sin(angle)
        values[0] = 1f; values[1] = 0f; values[2] = 0f
        values[3] = 0f; values[4] = cos(angle); values[5] = 0f
        values[6] = 0f; values[7] = (if (topHalf) s else -s) / distance; values[8] = 1f
        matrix.setValues(values)
        matrix.preTranslate(-pivotX, -pivotY)
        matrix.postTranslate(pivotX, pivotY)
        return matrix
    }
}

/** Sombra estática de la mitad inferior (las hojas de abajo quedan apenas más oscuras). */
private const val STATIC_SHADE = 0.07f

/** Sombra máxima de la hoja que cae / de la que se posa / de la mitad inferior durante el volteo. */
private const val FALLING_SHADE = 0.55f
private const val LANDING_SHADE = 0.30f
private const val CAST_SHADE = 0.45f

private val DefaultFontSize = 132.sp

/**
 * Dibuja un marcador de fichas. Ocupa el ancho de su contenido al mayor tamaño que quepa
 * (hasta `style.fontSize`, ya escalado por el tamaño de fuente del sistema).
 */
@Composable
internal fun FlapBoard(
    cells: List<FlapCell>,
    state: FlapBoardState,
    style: TextStyle,
    glyphColor: Color,
    tileColor: Color,
    hingeColor: Color,
    shadeColor: Color,
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    val fontMeasurer = rememberFlapFontMeasurer(style)
    val metrics = fontMeasurer.metrics
    val advanceEm = remember(cells, fontMeasurer) {
        maxOf(metrics.advanceEm, cells.tiles.maxOfOrNull { fontMeasurer.advanceEm(it.glyph) } ?: 0f)
    }
    val tileWidthEm = advanceEm + 2 * FlapProportions.PAD_X
    val tileHeightEm = metrics.tileHeightEm
    val widthEm = remember(cells, tileWidthEm) { boardWidthEm(cells, tileWidthEm) }
    val textMeasurer = rememberTextMeasurer()
    val fontPx = remember { mutableFloatStateOf(0f) }
    val projector = remember { FlapProjector() }

    Spacer(
        modifier
            .layout { measurable, constraints ->
                val maxFont = (if (style.fontSize.isSpecified) style.fontSize else DefaultFontSize).toPx()
                val fs = fitFontPx(
                    maxFontPx = maxFont,
                    widthEm = widthEm,
                    heightEm = tileHeightEm,
                    maxWidthPx = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.POSITIVE_INFINITY,
                    maxHeightPx = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else Float.POSITIVE_INFINITY,
                )
                fontPx.floatValue = fs
                val w = ceil(fs * widthEm).toInt().coerceIn(constraints.minWidth, constraints.maxWidth)
                val h = ceil(fs * tileHeightEm).toInt().coerceIn(constraints.minHeight, constraints.maxHeight)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { placeable.place(0, 0) }
            }
            .drawWithCache {
                val fs = fontPx.floatValue
                if (fs <= 0f || cells.isEmpty()) return@drawWithCache onDrawBehind { }

                val tileW = fs * tileWidthEm
                val tileH = fs * tileHeightEm
                val gap = fs * FlapProportions.GAP
                val dot = fs * FlapProportions.DOT
                val contentW = fs * widthEm
                val x0 = alignment.align(contentW.roundToInt(), size.width.roundToInt(), layoutDirection).toFloat()
                val top = ((size.height - tileH) / 2f).coerceAtLeast(0f)
                val bottom = top + tileH
                val baseline = top + fs * FlapProportions.PAD_Y - fs * metrics.inkTopEm
                val hingeY = top + tileH / 2f
                val hinge = (fs * FlapProportions.HINGE).coerceIn(1.dp.toPx(), 2.dp.toPx())
                val radius = Shapes.tile.topStart.toPx(Size(tileW, tileH), this).let { CornerRadius(it, it) }
                val maxGlyphW = tileW - fs * FlapProportions.PAD_X
                val dashW = fs * advanceEm * FlapProportions.DASH_WIDTH
                val dashT = fs * FlapProportions.DASH_THICKNESS
                val camera = tileH * FlapProportions.CAMERA_DISTANCE
                val castShade = Brush.verticalGradient(
                    0f to shadeColor,
                    1f to shadeColor.copy(alpha = 0f),
                    startY = hingeY,
                    endY = bottom,
                )

                val lefts = FloatArray(cells.size)
                var cursor = x0
                cells.forEachIndexed { i, c ->
                    lefts[i] = cursor
                    cursor += if (c is FlapCell.Tile) tileW + gap else dot + gap
                }

                val glyphStyle = style.copy(
                    color = glyphColor,
                    fontSize = (fs / density).sp,
                    lineHeight = TextUnit.Unspecified,
                    lineHeightStyle = null,
                )
                // El tamaño ya está en px reales: se mide sin volver a aplicar la escala de fuente.
                val glyphDensity = Density(density, 1f)
                val layouts = HashMap<Char, TextLayoutResult>()
                fun layoutOf(c: Char): TextLayoutResult = layouts.getOrPut(c) {
                    textMeasurer.measure(c.toString(), glyphStyle, density = glyphDensity)
                }

                fun DrawScope.glyph(c: Char, cx: Float) {
                    when (c) {
                        FlapGlyphs.EMPTY -> Unit
                        FlapGlyphs.DASH -> drawRect(
                            glyphColor,
                            topLeft = Offset(cx - dashW / 2f, hingeY - dashT / 2f),
                            size = Size(dashW, dashT),
                        )
                        else -> {
                            val l = layoutOf(c)
                            val topLeft = Offset(cx - l.size.width / 2f, baseline - l.firstBaseline)
                            // Letras de paso más anchas que la ficha (M, W…): se encogen para caber.
                            val fit = maxGlyphW / l.size.width
                            if (fit < 1f) {
                                scale(fit, Offset(cx, hingeY)) { drawText(l, topLeft = topLeft) }
                            } else {
                                drawText(l, topLeft = topLeft)
                            }
                        }
                    }
                }

                fun DrawScope.tile(left: Float, color: Color = tileColor, alpha: Float = 1f) {
                    drawRoundRect(color, Offset(left, top), Size(tileW, tileH), radius, alpha = alpha)
                }

                fun DrawScope.upperHalf(left: Float, c: Char, shade: Float) {
                    clipRect(left, top, left + tileW, hingeY) {
                        tile(left)
                        glyph(c, left + tileW / 2f)
                        if (shade > 0f) tile(left, shadeColor, shade)
                    }
                }

                fun DrawScope.lowerHalf(left: Float, c: Char, shade: Float, cast: Float = 0f) {
                    clipRect(left, hingeY, left + tileW, bottom) {
                        tile(left)
                        glyph(c, left + tileW / 2f)
                        tile(left, shadeColor, STATIC_SHADE)
                        if (shade > 0f) tile(left, shadeColor, shade)
                        if (cast > 0f) {
                            drawRoundRect(castShade, Offset(left, top), Size(tileW, tileH), radius, alpha = cast)
                        }
                    }
                }

                fun DrawScope.projected(angle: Float, topHalf: Boolean, cx: Float, block: DrawScope.() -> Unit) {
                    val m = projector.matrix(angle, topHalf, cx, hingeY, camera)
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.withMatrix(m) { this@projected.block() }
                    }
                }

                val frame = FlapFrame()
                val groupMark = FlapGroupMark.current

                onDrawBehind {
                    for (i in cells.indices) {
                        val left = lefts[i]
                        when (val cell = cells[i]) {
                            // Separador de miles del idioma: «.» (es, it, pt-BR), «,» (en) o solo el hueco (fr).
                            FlapCell.Separator -> when (groupMark) {
                                FlapGroupMark.SPACE -> Unit
                                FlapGroupMark.DOT -> drawCircle(
                                    glyphColor,
                                    radius = dot / 2f,
                                    center = Offset(left + dot / 2f, baseline - dot / 2f),
                                )
                                FlapGroupMark.COMMA -> {
                                    drawCircle(glyphColor, radius = dot / 2f, center = Offset(left + dot / 2f, baseline - dot / 2f))
                                    drawLine(
                                        glyphColor,
                                        start = Offset(left + dot * 0.85f, baseline - dot * 0.35f),
                                        end = Offset(left + dot * 0.2f, baseline + dot * 0.9f),
                                        strokeWidth = dot * 0.45f,
                                        cap = StrokeCap.Round,
                                    )
                                }
                            }

                            is FlapCell.Tile -> {
                                val col = state.column(cell.position)
                                if (col == null) frame.set(cell.glyph, cell.glyph, 0f) else frame.resolve(col.seq, col.progress)
                                val cx = left + tileW / 2f
                                val f = frame.fraction
                                if (f <= 0f) {
                                    upperHalf(left, frame.from, 0f)
                                    lowerHalf(left, frame.from, 0f)
                                } else {
                                    // Detrás: arriba ya asoma el glifo nuevo; abajo sigue el viejo, en sombra.
                                    upperHalf(left, frame.to, 0f)
                                    lowerHalf(left, frame.from, 0f, cast = CAST_SHADE * sin(PI.toFloat() * f))
                                    if (f < 0.5f) {
                                        val angle = f * PI.toFloat()
                                        projected(angle, topHalf = true, cx) {
                                            upperHalf(left, frame.from, FALLING_SHADE * sin(angle))
                                        }
                                    } else {
                                        val angle = (1f - f) * PI.toFloat()
                                        projected(angle, topHalf = false, cx) {
                                            lowerHalf(left, frame.to, LANDING_SHADE * sin(angle))
                                        }
                                    }
                                }
                                if (frame.from == FlapGlyphs.DASH || frame.to == FlapGlyphs.DASH) {
                                    // La bisagra no corta el guion: partido en dos barras se leería «=».
                                    val gapL = cx - dashW / 2f
                                    val gapR = cx + dashW / 2f
                                    drawRect(hingeColor, Offset(left, hingeY - hinge / 2f), Size(gapL - left, hinge))
                                    drawRect(hingeColor, Offset(gapR, hingeY - hinge / 2f), Size(left + tileW - gapR, hinge))
                                } else {
                                    drawRect(hingeColor, Offset(left, hingeY - hinge / 2f), Size(tileW, hinge))
                                }
                            }
                        }
                    }
                }
            },
    )
}
