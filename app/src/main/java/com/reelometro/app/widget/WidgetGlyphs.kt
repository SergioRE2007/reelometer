package com.reelometro.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import com.reelometro.app.R
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapSprite
import com.reelometro.app.ui.components.flap.FlapGroupMark
import com.reelometro.app.ui.components.flap.FlapGlyphMetrics
import com.reelometro.app.ui.components.flap.FlapGlyphs
import com.reelometro.app.ui.components.flap.FlapProportions
import com.reelometro.app.ui.theme.ReelTypography
import kotlin.math.ceil

/**
 * Pinta los glifos del widget con las fuentes de la app como **máscaras** (bitmaps ALPHA_8): el
 * sistema las tiñe con `ColorFilter.tint(ColorProvider(día, noche))`, así que un mismo bitmap vale
 * para el tema claro y el oscuro y cambia de color solo. Hace falta porque RemoteViews infla las
 * vistas con un contexto restringido que ignora las fuentes propias (`@font/...`).
 *
 * Cifras: Bricolage Grotesque 800 · wdth 75 · opsz 96 (el `counter` de docs/DESIGN.md §2), con la
 * misma colocación que el marcador de la app (`FlapBoard`). Etiquetas: JetBrains Mono con su peso y
 * tracking. Los bitmaps se guardan en una caché LRU (los mismos objetos se reutilizan entre tamaños
 * y RemoteViews no los duplica).
 */
internal class WidgetGlyphs private constructor(context: Context) : TextMetrics {

    val density: Float = context.resources.displayMetrics.density

    private val counter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font(context, R.font.bricolage_grotesque)
        fontVariationSettings = COUNTER_AXES
        fontFeatureSettings = "tnum"
        letterSpacing = ReelTypography.counter.letterSpacing.value
        textAlign = Paint.Align.CENTER
    }
    private val monoTypeface = font(context, R.font.jetbrains_mono)
    private val mono = HashMap<Int, Paint>()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Píxeles de Flap: sin suavizado, bordes duros. */
    private val pixel = Paint()
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    /** Métricas de las cifras en em, medidas igual que `FlapFontMeasurer`. */
    val metrics: FlapGlyphMetrics = run {
        counter.textSize = REF
        val r = Rect()
        counter.getTextBounds(FlapGlyphs.DIGITS, 0, FlapGlyphs.DIGITS.length, r)
        val advance = FlapGlyphs.DIGITS.maxOf { counter.measureText(it.toString()) } / REF
        FlapGlyphMetrics(advanceEm = advance, inkTopEm = r.top / REF, inkBottomEm = r.bottom / REF)
    }

    /** Máscara de una ficha entera de [board] con [glyph] en su sitio (cifra, guion o nada). */
    @Synchronized
    fun tile(glyph: Char, board: BoardSpec): Bitmap = cached("t$glyph|${board.fontPx}|${board.tileWPx}x${board.tileHPx}") {
        mask(board.tileWPx, board.tileHPx).also { bmp ->
            val canvas = Canvas(bmp)
            val cx = board.tileWPx / 2f
            when (glyph) {
                FlapGlyphs.EMPTY -> Unit
                FlapGlyphs.DASH -> {
                    val t = board.fontPx * FlapProportions.DASH_THICKNESS
                    val y = board.tileHPx / 2f
                    canvas.drawRect(cx - board.dashWPx / 2f, y - t / 2f, cx + board.dashWPx / 2f, y + t / 2f, fill)
                }
                else -> {
                    counter.textSize = board.fontPx
                    canvas.drawText(glyph.toString(), cx, board.baselinePx, counter)
                }
            }
        }
    }

    /** Máscara del punto de millar: un círculo apoyado en la línea base, del alto de una ficha. */
    @Synchronized
    fun dot(board: BoardSpec, mark: FlapGroupMark = FlapGroupMark.current): Bitmap =
        cached("d|${mark.name}|${board.fontPx}|${board.dotWPx}x${board.tileHPx}") {
            mask(board.dotWPx, board.tileHPx).also { bmp ->
                // Separador de miles del idioma: punto, coma (punto con rabito) o nada (francés: solo el hueco).
                val r = board.fontPx * FlapProportions.DOT / 2f
                val canvas = Canvas(bmp)
                if (mark != FlapGroupMark.SPACE) canvas.drawCircle(board.dotWPx / 2f, board.baselinePx - r, r, fill)
                if (mark == FlapGroupMark.COMMA) {
                    val d = r * 2f
                    val tail = android.graphics.Paint(fill).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = d * 0.45f
                        strokeCap = android.graphics.Paint.Cap.ROUND
                    }
                    canvas.drawLine(board.dotWPx / 2f + d * 0.35f, board.baselinePx - d * 0.35f, board.dotWPx / 2f - d * 0.3f, board.baselinePx + d * 0.9f, tail)
                }
            }
        }

    /**
     * Máscara de una capa de Flap: los píxeles de [FlapSprite.frame] ([mood], ojos abiertos) que son
     * [layer] (un carácter de [FlapSprite.PIXELS]), cada uno de [pixelPx] × [pixelPx] px enteros
     * para que el pixel art salga nítido. El widget apila una capa por color y tiñe cada una.
     */
    @Synchronized
    fun flap(mood: FlapMood, layer: Char, pixelPx: Int): Bitmap = cached("f$layer|${mood.name}|$pixelPx") {
        val side = FlapSprite.SIZE * pixelPx
        mask(side, side).also { bmp ->
            val canvas = Canvas(bmp)
            FlapSprite.frame(mood).forEachIndexed { y, row ->
                row.forEachIndexed { x, c ->
                    if (c == layer) {
                        canvas.drawRect(
                            (x * pixelPx).toFloat(),
                            (y * pixelPx).toFloat(),
                            ((x + 1) * pixelPx).toFloat(),
                            ((y + 1) * pixelPx).toFloat(),
                            pixel,
                        )
                    }
                }
            }
        }
    }

    /** Máscara de una línea de texto en JetBrains Mono ([spec]), del alto de su línea. */
    @Synchronized
    fun text(text: String, spec: TextSpec): Bitmap = cached("x|$spec|$text") {
        val paint = mono(spec)
        val fm = paint.fontMetrics
        mask(ceil(paint.measureText(text)).toInt(), ceil(fm.descent - fm.ascent).toInt()).also { bmp ->
            Canvas(bmp).drawText(text, 0f, -fm.ascent, paint)
        }
    }

    @Synchronized
    override fun width(text: String, spec: TextSpec): Float = ceil(mono(spec).measureText(text)) / density

    @Synchronized
    override fun lineHeight(spec: TextSpec): Float = mono(spec).fontMetrics.let { ceil(it.descent - it.ascent) } / density

    private fun mono(spec: TextSpec): Paint = mono.getOrPut(spec.weight) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = monoTypeface
            fontVariationSettings = "'wght' ${spec.weight}"
        }
    }.apply {
        textSize = spec.sizeDp * density
        letterSpacing = spec.trackingEm
    }

    private inline fun cached(key: String, create: () -> Bitmap): Bitmap =
        cache.get(key) ?: create().also { cache.put(key, it) }

    private fun mask(w: Int, h: Int): Bitmap = createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ALPHA_8)

    companion object {
        /** Ejes del `counter` (ui/theme/Type.kt). */
        private const val COUNTER_AXES = "'wght' 800, 'wdth' 75, 'opsz' 96"
        private const val REF = 512f
        private const val CACHE_BYTES = 4 * 1024 * 1024

        @Volatile
        private var instance: WidgetGlyphs? = null

        /** Instancia compartida (se rehace si cambia la densidad de pantalla). */
        fun get(context: Context): WidgetGlyphs {
            val density = context.resources.displayMetrics.density
            instance?.takeIf { it.density == density }?.let { return it }
            return synchronized(this) {
                instance?.takeIf { it.density == density } ?: WidgetGlyphs(context.applicationContext).also { instance = it }
            }
        }

        private fun font(context: Context, id: Int): Typeface =
            runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: Typeface.DEFAULT
    }
}
