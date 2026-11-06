package com.reelometro.app.ui.passport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import com.reelometro.app.copy.TicketLine
import com.reelometro.app.ui.theme.DarkReelColors
import com.reelometro.app.ui.theme.LightReelColors
import com.reelometro.app.ui.wrapped.TicketBarcode
import com.reelometro.app.ui.wrapped.TicketFiles
import com.reelometro.app.ui.wrapped.TicketFonts
import com.reelometro.app.ui.wrapped.TicketModel
import com.reelometro.app.ui.wrapped.TicketRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Ticket del nivel nuevo (1080 × 1920, `android.graphics`), con el mismo papel que el ticket del
 * Wrapped (docs/DESIGN.md §9): papel hueso sobre negro cálido, dientes arriba y abajo, cabecera
 * REELÓMETRO, la ruta ORIGEN → DESTINO con códigos de tres letras, el nivel enorme, millas y
 * sellos (como sellitos de tinta), código de barras con un trazo por sello y el sello rotado
 * «EMBARQUE CONFIRMADO» en `signal`. Medidas en píxeles de la imagen (retícula de 4 px).
 */
internal object LevelTicketRenderer {

    const val WIDTH = TicketRenderer.WIDTH
    const val HEIGHT = TicketRenderer.HEIGHT

    /** Pinta [data] en un bitmap nuevo de 1080 × 1920. Llamar fuera del hilo principal. */
    fun render(context: Context, data: LevelTicketData): Bitmap {
        val bitmap = createBitmap(WIDTH, HEIGHT)
        draw(Canvas(bitmap), data, TicketFonts.load(context))
        return bitmap
    }

    fun draw(canvas: Canvas, data: LevelTicketData, fonts: TicketFonts) {
        canvas.drawColor(C.BG)
        val ticket = LevelLayout(data, fonts)
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

    /** Medidas de la imagen (px), las mismas que el ticket del Wrapped salvo lo propio del nivel. */
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
        const val CODE_SIZE = 168f
        const val LEVEL_MAX = 320f
        const val LINE_SIZE = 32f
        const val LINE_H = 64f
        const val TOTAL_SIZE = 48f
        const val TOTAL_H = 80f
        const val FOOTER_SIZE = 48f
        const val CAPTION_SIZE = 24f
        const val BARCODE_H = 120f
        const val GUARD_EXTRA = 16f
        const val BAR_MODULE_MIN = 3f
        const val BARCODE_SHARE = 0.56f

        const val RULE = 3f
        const val DASH = 12f
        const val DASH_GAP = 10f
        const val NOTCH_R = 20f
        const val DOT_R = 2.5f
        const val DOT_PITCH = 14f
        const val LEADER_GAP = 16f
        const val LEADER_MIN = 48f
        const val ARROW_HEAD = 20f

        const val MINI_COLUMNS = 3
        const val MINI_GAP = 24f
        const val MINI_H = 112f
        const val MINI_SIZE = 24f
        const val MINI_OUTER = 4f
        const val MINI_INNER = 2f
        const val MINI_INSET = 8f
        const val MINI_ROTATION = 3f

        const val STAMP_SIZE = 36f
        const val STAMP_DATE_SIZE = 20f
        const val STAMP_PAD_X = 32f
        const val STAMP_PAD_Y = 26f
        const val STAMP_OUTER = 6f
        const val STAMP_INNER = 2.5f
        const val STAMP_INSET = 12f
        const val STAMP_ROTATION = -11f
    }

    /** Colores: la imagen usa siempre la paleta del marcador (papel = `text` oscuro; tinta = `text` claro). */
    private object C {
        val BG = DarkReelColors.bg.toArgb()
        val PAPER = DarkReelColors.text.toArgb()
        val INK = LightReelColors.text.toArgb()
        val INK_MUTED = LightReelColors.textMuted.toArgb()
        val RULE = LightReelColors.lineStrong.toArgb()
        val STAMP = DarkReelColors.signal.copy(alpha = 0.92f).toArgb()
        val FOOT = DarkReelColors.textMuted.toArgb()
    }

    /** El ticket entero medido de arriba abajo; luego se pinta con [draw]. */
    private class LevelLayout(private val data: LevelTicketData, private val fonts: TicketFonts) {
        private val ops = ArrayList<(Canvas) -> Unit>()
        private val notches = ArrayList<Float>()
        private var y = M.DEPTH + M.PAD
        private var stampX = 0f
        private var stampY = 0f
        val height: Float

        private val center = M.PAPER_W / 2f
        private val left = M.PAD
        private val right = M.PAPER_W - M.PAD

        init {
            header()
            gap(44f); dashed(); gap(44f)
            route()
            gap(48f)
            level()
            gap(48f); perforation(); gap(40f)
            data.lines.forEach { receipt(it, fonts.mono(M.LINE_SIZE, C.INK, 500), fonts.mono(M.LINE_SIZE, C.INK, 600), M.LINE_H) }
            if (data.stamps.isNotEmpty()) {
                gap(16f); dashed(); gap(40f)
                label(data.stampsLabel)
                gap(24f)
                miniStamps()
            }
            gap(24f); doubleRule(); gap(4f)
            val total = fonts.mono(M.TOTAL_SIZE, C.INK, 700, tracking = 0.04f)
            receipt(data.total, total, total, M.TOTAL_H)
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

        private fun gap(px: Float) {
            y += px
        }

        private fun header() {
            centeredLine(data.brand, fonts.display(M.BRAND_SIZE, C.INK, align = Paint.Align.CENTER, tracking = 0.02f))
            gap(24f)
            centeredLine(data.title, fonts.mono(M.SMALL_SIZE, C.INK_MUTED, 600, tracking = 0.24f, align = Paint.Align.CENTER))
            gap(16f)
            centeredLine(data.dateLine, fonts.mono(M.SMALL_SIZE, C.INK, 500, tracking = 0.16f, align = Paint.Align.CENTER))
        }

        /** ORIGEN ─────▶ DESTINO, con los códigos enormes a cada lado. */
        private fun route() {
            val labelPaint = fonts.mono(M.LABEL_SIZE, C.INK_MUTED, 600, tracking = 0.2f)
            val labelRight = fonts.mono(M.LABEL_SIZE, C.INK_MUTED, 600, tracking = 0.2f, align = Paint.Align.RIGHT)
            val labelBounds = bounds("H", labelPaint)
            val labelBaseline = y - labelBounds.top
            val origin = data.originLabel
            val destination = data.destinationLabel
            ops += { c ->
                c.drawText(origin, left, labelBaseline, labelPaint)
                c.drawText(destination, right, labelBaseline, labelRight)
            }
            y += labelBounds.height()
            gap(20f)

            val code = fonts.display(M.CODE_SIZE, C.INK)
            val codeRight = fonts.display(M.CODE_SIZE, C.INK, align = Paint.Align.RIGHT)
            val codeBounds = bounds("H", code)
            val baseline = y - codeBounds.top
            val from = data.originCode
            val to = data.destinationCode
            val fromW = code.measureText(from)
            val toW = codeRight.measureText(to)
            val arrowY = baseline - codeBounds.height() / 2f
            val arrowFrom = left + fromW + 32f
            val arrowTo = right - toW - 32f
            val ink = fill(C.INK)
            ops += { c ->
                c.drawText(from, left, baseline, code)
                c.drawText(to, right, baseline, codeRight)
                if (arrowTo - arrowFrom > M.ARROW_HEAD * 2) {
                    // Línea discontinua (la ruta) y punta de flecha.
                    var x = arrowFrom
                    while (x < arrowTo - M.ARROW_HEAD) {
                        c.drawRect(x, arrowY - M.RULE / 2f, min(x + M.DASH, arrowTo - M.ARROW_HEAD), arrowY + M.RULE / 2f, ink)
                        x += M.DASH + M.DASH_GAP
                    }
                    val head = Path().apply {
                        moveTo(arrowTo, arrowY)
                        lineTo(arrowTo - M.ARROW_HEAD, arrowY - M.ARROW_HEAD * 0.6f)
                        lineTo(arrowTo - M.ARROW_HEAD, arrowY + M.ARROW_HEAD * 0.6f)
                        close()
                    }
                    c.drawPath(head, ink)
                }
            }
            y += codeBounds.height()
        }

        /** «NIVEL» y el nombre enorme, ajustado al ancho. */
        private fun level() {
            label(data.levelLabel)
            gap(24f)
            val paint = fonts.display(M.LEVEL_MAX, C.INK)
            val width = paint.measureText(data.levelName)
            if (width > M.CONTENT_W) paint.textSize = M.LEVEL_MAX * M.CONTENT_W / width
            val bounds = bounds(data.levelName, paint)
            val baseline = y - bounds.top
            val text = data.levelName
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

        /** `etiqueta ········ valor`, con el valor recortado si no cabe. */
        private fun receipt(line: TicketLine, labelPaint: TextPaint, valuePaint: TextPaint, lineHeight: Float) {
            val cap = bounds("H", labelPaint).height()
            val baseline = y + (lineHeight + cap) / 2f
            val label = TextUtils.ellipsize(line.label, labelPaint, M.CONTENT_W * 0.62f, TextUtils.TruncateAt.END).toString()
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
                var x = ceil(dotsFrom / M.DOT_PITCH) * M.DOT_PITCH
                while (x <= dotsTo) {
                    c.drawCircle(x, baseline - M.DOT_R, M.DOT_R, dot)
                    x += M.DOT_PITCH
                }
            }
            y += lineHeight
        }

        /** Los sellos conseguidos como sellitos de tinta con doble borde, ligeramente girados. */
        private fun miniStamps() {
            val cols = M.MINI_COLUMNS
            val w = (M.CONTENT_W - M.MINI_GAP * (cols - 1)) / cols
            val text = fonts.mono(M.MINI_SIZE, C.INK, 700, tracking = 0.06f, align = Paint.Align.CENTER)
            val cap = bounds("H", text).height()
            val outer = stroke(C.INK, M.MINI_OUTER)
            val inner = stroke(C.INK, M.MINI_INNER)
            val rows = (data.stamps.size + cols - 1) / cols
            data.stamps.forEachIndexed { i, name ->
                val col = i % cols
                val row = i / cols
                val boxLeft = left + col * (w + M.MINI_GAP)
                val boxTop = y + row * (M.MINI_H + M.MINI_GAP)
                val box = RectF(boxLeft, boxTop, boxLeft + w, boxTop + M.MINI_H)
                val lines = TicketModel.balancedLines(name).map {
                    TextUtils.ellipsize(it, text, w - 2 * (M.MINI_INSET + 12f), TextUtils.TruncateAt.END).toString()
                }
                val rotation = if (i % 2 == 0) -M.MINI_ROTATION else M.MINI_ROTATION
                ops += { c ->
                    c.withRotation(rotation, box.centerX(), box.centerY()) {
                        c.drawRect(box, outer)
                        c.drawRect(RectF(box.left + M.MINI_INSET, box.top + M.MINI_INSET, box.right - M.MINI_INSET, box.bottom - M.MINI_INSET), inner)
                        val lineH = cap * 1.7f
                        var baseline = box.centerY() - lineH * (lines.size - 1) / 2f + cap / 2f
                        lines.forEach { line ->
                            c.drawText(line, box.centerX(), baseline, text)
                            baseline += lineH
                        }
                    }
                }
            }
            y += rows * M.MINI_H + (rows - 1) * M.MINI_GAP
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

        /** Código de barras (un trazo por sello) con su pie; a la derecha queda el hueco del sello. */
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

        /** «EMBARQUE CONFIRMADO» en dos líneas, doble borde, rotado y algo transparente (tinta). */
        private fun stamp(canvas: Canvas) {
            val words = TicketModel.balancedLines(data.stamp)
            val text = fonts.mono(M.STAMP_SIZE, C.STAMP, 700, tracking = 0.08f, align = Paint.Align.CENTER)
            val date = fonts.mono(M.STAMP_DATE_SIZE, C.STAMP, 600, tracking = 0.2f, align = Paint.Align.CENTER)
            val lineH = bounds("H", text).height() * 1.6f
            val dateH = bounds("H", date).height()
            val textW = max(words.maxOf { text.measureText(it) }, date.measureText(data.stampDate))
            val boxW = textW + 2 * M.STAMP_PAD_X
            val boxH = lineH * words.size + 16f + dateH + 2 * M.STAMP_PAD_Y
            val cx = min(stampX, M.PAPER_W - M.PAD / 2f - boxW / 2f)
            val cy = stampY
            val outer = stroke(C.STAMP, M.STAMP_OUTER)
            val inner = stroke(C.STAMP, M.STAMP_INNER)
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

        private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeJoin = Paint.Join.MITER
        }
    }
}

/** Pinta el ticket de nivel, lo deja en la caché y abre el selector del sistema para compartirlo. */
internal object LevelTicketSharer {

    /** `true` si se abrió el selector. Llamar desde el hilo principal (pinta y escribe fuera de él). */
    suspend fun share(context: Context, data: LevelTicketData): Boolean {
        val app = context.applicationContext
        val uri = runCatching {
            withContext(Dispatchers.Default) {
                val bitmap = LevelTicketRenderer.render(app, data)
                val png = TicketRenderer.encodePng(bitmap)
                bitmap.recycle()
                val file = withContext(Dispatchers.IO) { TicketFiles.write(app, png, data.fileName) }
                FileProvider.getUriForFile(app, TicketFiles.authority(app), file)
            }
        }.getOrNull() ?: return false
        return runCatching { context.startActivity(TicketFiles.shareIntent(uri, data.shareText)) }.isSuccess
    }
}
