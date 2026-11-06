package com.reelometro.app.ui.wrapped

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.FontRes
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import com.reelometro.app.R
import com.reelometro.app.copy.TicketLine
import com.reelometro.app.ui.theme.DarkReelColors
import com.reelometro.app.ui.theme.LightReelColors
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * La imagen compartible: «ticket de tu tiempo» (docs/DESIGN.md §9), 1080 × 1920 px, pintada con
 * `android.graphics` (no Compose) para poder generarla fuera del hilo principal. Papel hueso
 * sobre negro cálido, dientes arriba y abajo, cabecera REELÓMETRO, cifra enorme, líneas de ticket
 * con emojis (el único sitio donde se permiten), total, código de barras de un trazo por día,
 * sello rotado en `signal` y pie de marca.
 *
 * Las medidas son píxeles de la imagen final (retícula de 4 px). Los colores salen de los tokens
 * (papel = `text` oscuro; tinta = `text` claro): la imagen usa siempre la paleta del marcador.
 */
internal object TicketRenderer {

    const val WIDTH = 1080
    const val HEIGHT = 1920

    /** Pinta [data] en un bitmap nuevo de 1080 × 1920. Llamar fuera del hilo principal. */
    fun render(context: Context, data: TicketData): Bitmap {
        val bitmap = createBitmap(WIDTH, HEIGHT)
        draw(Canvas(bitmap), data, TicketFonts.load(context))
        return bitmap
    }

    /** PNG sin pérdida, listo para compartir o guardar. */
    fun encodePng(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }

    internal fun draw(canvas: Canvas, data: TicketData, fonts: TicketFonts) {
        canvas.drawColor(C.BG)
        val ticket = TicketLayout(data, fonts)
        val available = HEIGHT - M.TOP_MIN - M.FOOTER_ZONE
        val scale = min(1f, available / ticket.height)
        val top = M.TOP_MIN + (available - ticket.height * scale) / 2f

        canvas.withTranslation(M.PAPER_X, top) {
            if (scale < 1f) canvas.scale(scale, scale, M.PAPER_W / 2f, 0f)
            ticket.draw(canvas)
        }

        val foot = fonts.mono(M.FOOT_SIZE, C.FOOT, 600, tracking = 0.24f, align = Paint.Align.CENTER)
        canvas.drawText(data.brandFooter, WIDTH / 2f, HEIGHT - M.FOOT_BASELINE_FROM_BOTTOM, foot)
    }

    // ------------------------------------------------------------------ medidas y colores

    /** Medidas de la imagen (px). */
    private object M {
        const val TOP_MIN = 72f
        const val FOOTER_ZONE = 176f
        const val FOOT_BASELINE_FROM_BOTTOM = 92f
        const val FOOT_SIZE = 26f

        const val PAPER_X = 88f
        const val PAPER_W = WIDTH - 2 * PAPER_X
        const val PAD = 64f
        const val CONTENT_W = PAPER_W - 2 * PAD
        const val TOOTH = 28f
        const val DEPTH = 14f

        const val BRAND_SIZE = 112f
        const val SMALL_SIZE = 28f
        const val LABEL_SIZE = 26f
        const val COUNT_MAX = 380f
        const val LINE_SIZE = 32f
        const val LINE_H = 64f
        const val TOTAL_SIZE = 48f
        const val TOTAL_H = 80f
        const val FOOTER_SIZE = 48f
        const val CAPTION_SIZE = 24f
        const val BARCODE_H = 120f
        const val GUARD_EXTRA = 16f
        const val BAR_MODULE_MIN = 3f
        /** Parte del ancho útil para el código de barras; el resto es del sello. */
        const val BARCODE_SHARE = 0.56f

        const val RULE = 3f
        const val DASH = 12f
        const val DASH_GAP = 10f
        const val NOTCH_R = 20f
        const val DOT_R = 2.5f
        const val DOT_PITCH = 14f
        const val LEADER_GAP = 16f
        const val LEADER_MIN = 48f

        const val STAMP_SIZE = 36f
        const val STAMP_DATE_SIZE = 20f
        const val STAMP_PAD_X = 32f
        const val STAMP_PAD_Y = 26f
        const val STAMP_OUTER = 6f
        const val STAMP_INNER = 2.5f
        const val STAMP_INSET = 12f
        const val STAMP_ROTATION = -11f
    }

    /** Colores (tokens del marcador). */
    private object C {
        val BG = DarkReelColors.bg.toArgb()
        val PAPER = DarkReelColors.text.toArgb()
        val INK = LightReelColors.text.toArgb()
        val INK_MUTED = LightReelColors.textMuted.toArgb()
        val RULE = LightReelColors.lineStrong.toArgb()
        val STAMP = DarkReelColors.signal.copy(alpha = 0.92f).toArgb()
        val FOOT = DarkReelColors.textMuted.toArgb()
    }

    // ------------------------------------------------------------------ maquetación del papel

    /**
     * Calcula la posición de cada bloque (coordenadas del papel: x desde su borde izquierdo,
     * y desde su borde superior) y luego lo pinta. Así el papel mide justo lo que lleva.
     */
    private class TicketLayout(private val data: TicketData, private val fonts: TicketFonts) {
        private val ops = ArrayList<(Canvas) -> Unit>()
        private val notches = ArrayList<Float>()
        private var y = M.DEPTH + M.PAD

        /** Centro del hueco del sello: a la derecha del código de barras (no tapa ningún dato). */
        private var stampX = 0f
        private var stampY = 0f
        val height: Float

        private val center = M.PAPER_W / 2f
        private val left = M.PAD
        private val right = M.PAPER_W - M.PAD

        init {
            header()
            gap(44f); dashed(); gap(44f)
            count()
            gap(48f); perforation(); gap(40f)
            lines(data.lines)
            if (data.equivalences.isNotEmpty()) {
                gap(16f); dashed(); gap(40f)
                label(data.equivalencesLabel)
                gap(12f)
                lines(data.equivalences)
            }
            gap(24f); doubleRule(); gap(4f)
            totalLine(data.total)
            gap(4f); doubleRule(); gap(48f)
            footer()
            gap(48f)
            barcode()
            y += M.PAD + M.DEPTH
            height = y
        }

        fun draw(canvas: Canvas) {
            paper(canvas)
            ops.forEach { it(canvas) }
            stamp(canvas)
        }

        // -------------------------------------------------------------- bloques

        private fun gap(px: Float) {
            y += px
        }

        private fun header() {
            val brand = fonts.display(M.BRAND_SIZE, C.INK, align = Paint.Align.CENTER, tracking = 0.02f)
            centeredLine(data.brand, brand)
            gap(24f)
            centeredLine(data.title, fonts.mono(M.SMALL_SIZE, C.INK_MUTED, 600, tracking = 0.24f, align = Paint.Align.CENTER))
            gap(16f)
            centeredLine(data.period, fonts.mono(M.SMALL_SIZE, C.INK, 500, tracking = 0.16f, align = Paint.Align.CENTER))
        }

        private fun count() {
            label(data.countLabel)
            gap(24f)
            val paint = fonts.display(M.COUNT_MAX, C.INK)
            val width = paint.measureText(data.count)
            if (width > M.CONTENT_W) paint.textSize = M.COUNT_MAX * M.CONTENT_W / width
            val bounds = bounds(data.count, paint)
            val baseline = y - bounds.top
            val text = data.count
            // Alineada a la izquierda como el resto de cifras de la app (el glifo, no su caja, cae en el margen).
            val x = left - bounds.left
            ops += { it.drawText(text, x, baseline, paint) }
            y += bounds.height()
        }

        private fun label(text: String) {
            val paint = fonts.mono(M.LABEL_SIZE, C.INK_MUTED, 600, tracking = 0.2f)
            val bounds = bounds(text, paint)
            val baseline = y - bounds.top
            ops += { it.drawText(text, left, baseline, paint) }
            y += bounds.height()
        }

        private fun centeredLine(text: String, paint: TextPaint) {
            val bounds = bounds(text, paint)
            val baseline = y - bounds.top
            ops += { it.drawText(text, center, baseline, paint) }
            y += bounds.height()
        }

        private fun lines(items: List<TicketLine>) {
            val labelPaint = fonts.mono(M.LINE_SIZE, C.INK, 500)
            val valuePaint = fonts.mono(M.LINE_SIZE, C.INK, 600)
            items.forEach { receipt(it, labelPaint, valuePaint, M.LINE_H) }
        }

        private fun totalLine(line: TicketLine) {
            val paint = fonts.mono(M.TOTAL_SIZE, C.INK, 700, tracking = 0.04f)
            receipt(line, paint, paint, M.TOTAL_H)
        }

        /** `etiqueta ········ valor`, con el valor recortado («…») si no cabe. */
        private fun receipt(line: TicketLine, labelPaint: TextPaint, valuePaint: TextPaint, lineHeight: Float) {
            val cap = bounds("H", labelPaint).height()
            val baseline = y + (lineHeight + cap) / 2f
            val maxLabel = M.CONTENT_W * 0.62f
            val label = TextUtils.ellipsize(line.label, labelPaint, maxLabel, TextUtils.TruncateAt.END).toString()
            val labelW = labelPaint.measureText(label)
            val maxValue = M.CONTENT_W - labelW - M.LEADER_MIN - 2 * M.LEADER_GAP
            val value = TextUtils.ellipsize(line.value, valuePaint, max(0f, maxValue), TextUtils.TruncateAt.END).toString()
            val valueW = valuePaint.measureText(value)
            val dotsFrom = left + labelW + M.LEADER_GAP
            val dotsTo = right - valueW - M.LEADER_GAP
            val dot = fill(C.RULE)
            ops += { c ->
                c.drawText(label, left, baseline, labelPaint)
                c.drawText(value, right - valueW, baseline, valuePaint)
                var x = (kotlin.math.ceil(dotsFrom / M.DOT_PITCH) * M.DOT_PITCH)
                while (x <= dotsTo) {
                    c.drawCircle(x, baseline - M.DOT_R, M.DOT_R, dot)
                    x += M.DOT_PITCH
                }
            }
            y += lineHeight
        }

        private fun dashed() {
            val at = y
            val paint = fill(C.RULE)
            ops += { c ->
                var x = left
                while (x < right) {
                    c.drawRect(x, at, min(x + M.DASH, right), at + M.RULE, paint)
                    x += M.DASH + M.DASH_GAP
                }
            }
            y += M.RULE
        }

        /** Línea de corte: discontinua de borde a borde con muescas semicirculares en los lados. */
        private fun perforation() {
            val at = y
            notches += at + M.RULE / 2f
            val paint = fill(C.RULE)
            ops += { c ->
                var x = M.NOTCH_R + M.DASH_GAP
                val end = M.PAPER_W - M.NOTCH_R - M.DASH_GAP
                while (x < end) {
                    c.drawRect(x, at, min(x + M.DASH, end), at + M.RULE, paint)
                    x += M.DASH + M.DASH_GAP
                }
            }
            y += M.RULE
        }

        private fun doubleRule() {
            val at = y
            val paint = fill(C.INK)
            ops += { c ->
                c.drawRect(left, at, right, at + M.RULE, paint)
                c.drawRect(left, at + M.RULE * 3, right, at + M.RULE * 4, paint)
            }
            y += M.RULE * 4
        }

        private fun footer() {
            val paint = fonts.serif(M.FOOTER_SIZE, C.INK)
            val layout = StaticLayout.Builder
                .obtain(data.footer, 0, data.footer.length, paint, M.CONTENT_W.roundToInt())
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build()
            val at = y
            ops += { c ->
                c.withTranslation(left, at) { layout.draw(c) }
            }
            y += layout.height
        }

        /** Código de barras a la izquierda (un trazo por día) con su pie; a la derecha queda el hueco del sello. */
        private fun barcode() {
            val width = M.CONTENT_W * M.BARCODE_SHARE
            val bars = TicketBarcode.encode(data.barcode, width, M.BAR_MODULE_MIN)
            val at = y
            val ink = fill(C.INK)
            ops += { c ->
                bars.forEach { b ->
                    val bottom = at + M.BARCODE_H + if (b.guard) M.GUARD_EXTRA else 0f
                    c.drawRect(left + b.x, at, left + b.x + b.width, bottom, ink)
                }
            }
            y += M.BARCODE_H + M.GUARD_EXTRA + 12f
            val caption = fonts.mono(M.CAPTION_SIZE, C.INK_MUTED, 400, tracking = 0.12f)
            // Que el pie no pase del ancho del código (con fechas largas encoge).
            val captionW = caption.measureText(data.barcodeCaption)
            if (captionW > width) caption.textSize *= width / captionW
            val bounds = bounds(data.barcodeCaption, caption)
            val baseline = y - bounds.top
            val text = data.barcodeCaption
            ops += { it.drawText(text, left, baseline, caption) }
            y += bounds.height()
            stampX = left + width + (M.CONTENT_W - width) / 2f
            stampY = (at + y) / 2f
        }

        // -------------------------------------------------------------- papel y sello

        /** Papel con dientes arriba y abajo y las muescas de las líneas de corte. */
        private fun paper(canvas: Canvas) {
            val w = M.PAPER_W
            val h = height
            val n = max(1, (w / M.TOOTH).roundToInt())
            val tw = w / n
            val path = Path().apply {
                moveTo(0f, M.DEPTH)
                for (k in 0 until n) {
                    lineTo(k * tw + tw / 2f, 0f)
                    lineTo((k + 1) * tw, M.DEPTH)
                }
                lineTo(w, h - M.DEPTH)
                for (k in n - 1 downTo 0) {
                    lineTo(k * tw + tw / 2f, h)
                    lineTo(k * tw, h - M.DEPTH)
                }
                close()
            }
            canvas.drawPath(path, fill(C.PAPER))
            val cut = fill(C.BG)
            notches.forEach { cy ->
                canvas.drawCircle(0f, cy, M.NOTCH_R, cut)
                canvas.drawCircle(w, cy, M.NOTCH_R, cut)
            }
        }

        /** «PAGADO CON TU TIEMPO» en dos líneas, doble borde, rotado y algo transparente (tinta). */
        private fun stamp(canvas: Canvas) {
            val words = TicketModel.balancedLines(data.stamp)
            val text = fonts.mono(M.STAMP_SIZE, C.STAMP, 700, tracking = 0.08f, align = Paint.Align.CENTER)
            val date = fonts.mono(M.STAMP_DATE_SIZE, C.STAMP, 600, tracking = 0.2f, align = Paint.Align.CENTER)
            val lineH = bounds("H", text).height() * 1.6f
            val dateH = bounds("H", date).height()
            val textW = max(words.maxOf { text.measureText(it) }, date.measureText(data.stampDate))
            val boxW = textW + 2 * M.STAMP_PAD_X
            val boxH = lineH * words.size + 16f + dateH + 2 * M.STAMP_PAD_Y
            // En su hueco, sin salirse del papel aunque gire.
            val cx = min(stampX, M.PAPER_W - M.PAD / 2f - boxW / 2f)
            val cy = stampY
            val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = M.STAMP_OUTER
                color = C.STAMP
                strokeJoin = Paint.Join.MITER
            }
            val inner = Paint(outer).apply { strokeWidth = M.STAMP_INNER }
            canvas.withRotation(M.STAMP_ROTATION, cx, cy) {
                val box = RectF(cx - boxW / 2f, cy - boxH / 2f, cx + boxW / 2f, cy + boxH / 2f)
                canvas.drawRect(box, outer)
                canvas.drawRect(
                    RectF(box.left + M.STAMP_INSET, box.top + M.STAMP_INSET, box.right - M.STAMP_INSET, box.bottom - M.STAMP_INSET),
                    inner,
                )
                val cap = bounds("H", text).height()
                var baseline = box.top + M.STAMP_PAD_Y + (lineH + cap) / 2f
                words.forEach {
                    canvas.drawText(it, cx, baseline, text)
                    baseline += lineH
                }
                canvas.drawText(data.stampDate, cx, box.bottom - M.STAMP_PAD_Y, date)
            }
        }

        private fun bounds(text: String, paint: Paint): Rect = Rect().also { paint.getTextBounds(text, 0, text.length, it) }

        private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
    }
}

/**
 * Las tres familias de la app como `Typeface` de `android.graphics`, con sus ejes variables.
 * Si una fuente no se puede cargar (p. ej. en tests), se usa la del sistema equivalente.
 */
internal class TicketFonts(
    private val bricolage: Typeface,
    private val jetbrains: Typeface,
    private val instrument: Typeface,
) {
    /** Bricolage condensada y pesada (cifras y cabecera): `'wght' 800, 'wdth' 75, 'opsz' 96`. */
    fun display(size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT, tracking: Float = -0.02f): TextPaint =
        paint(bricolage, size, color, "'wght' 800, 'wdth' 75, 'opsz' 96", tracking, align)

    /** JetBrains Mono con su peso (`'wght'`). */
    fun mono(size: Float, color: Int, weight: Int, tracking: Float = 0f, align: Paint.Align = Paint.Align.LEFT): TextPaint =
        paint(jetbrains, size, color, "'wght' $weight", tracking, align)

    /** Instrument Serif Italic (la frase). */
    fun serif(size: Float, color: Int): TextPaint = paint(instrument, size, color, null, 0f, Paint.Align.LEFT)

    private fun paint(face: Typeface, size: Float, color: Int, axes: String?, tracking: Float, align: Paint.Align) =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = face
            textSize = size
            this.color = color
            letterSpacing = tracking
            textAlign = align
            if (axes != null) runCatching { setFontVariationSettings(axes) }
        }

    companion object {
        fun load(context: Context): TicketFonts = TicketFonts(
            bricolage = font(context, R.font.bricolage_grotesque) ?: Typeface.DEFAULT_BOLD,
            jetbrains = font(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE,
            instrument = font(context, R.font.instrument_serif_italic) ?: Typeface.create(Typeface.SERIF, Typeface.ITALIC),
        )

        private fun font(context: Context, @FontRes id: Int): Typeface? = runCatching { ResourcesCompat.getFont(context, id) }.getOrNull()
    }
}
