package com.reelometro.app.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.reelometro.app.R
import com.reelometro.app.ui.theme.DarkReelColors

/**
 * El dibujo de una **burbuja de cristal** del envío rápido (1.4.2), compartido por las de verdad
 * ([QuickSendOverlay]), la vista previa de Ajustes › Envío rápido y la galería de debug: así las tres son iguales.
 *
 * La foto de perfil (o las iniciales sobre un cristal oscuro translúcido) en un círculo con un **anillo de cristal**
 * (hueso con alfa baja), un **reflejo** arriba y una **sombra** suave alrededor. Por fuera, nada opaco. Sin aro
 * del acento ni insignia: los estados van encima de la foto, pequeños y sin texto.
 *  - enviando: un brillo que gira por el anillo ([spin], en grados);
 *  - enviado: una marca de visto en un círculo pequeño del acento (el único color);
 *  - fallo: «!» en un círculo pequeño claro;
 *  - la de debajo al despegar otra ([hot]): anillo más claro y grueso y «+» claro; la despegada ([lifted]):
 *    anillo más claro. El tamaño (×1,08 y ×1,15) lo pone quien la enseña, con un muelle.
 *
 * Reflejo y sombra son degradados sutiles: excepción documentada en `docs/DESIGN.md` (estas burbujas van sobre
 * Instagram, no dentro de la app). Sin desenfoque real de lo de detrás (ver «Burbujas de cristal» en
 * `docs/DESIGN.md`). Colores de [DarkReelColors] (tokens), con alfa.
 *
 * Todo se mide con el radio del círculo: dibujada a cualquier tamaño, es la misma burbuja. Sin asignaciones en
 * [draw] salvo cuando cambian el tamaño o la foto. Solo desde el hilo principal.
 */
internal class GlassBubbleArt(initialsFont: Typeface?) {

    enum class Mark { NONE, SENDING, SENT, FAILED }

    private val colors = DarkReelColors
    private val glass = colors.text.toArgb() // hueso, casi blanco
    private val ink = colors.bg.toArgb()

    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = initialsFont ?: Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        color = glass
    }
    private val arc = RectF()
    private val shine = RectF()
    private val photoMatrix = Matrix()

    // Lo que depende del tamaño y del sitio: se rehace solo si cambian.
    private var builtFor = Float.NaN
    private var builtX = Float.NaN
    private var builtY = Float.NaN
    private val check = Path()
    private val bang = Path()
    private val plus = Path()
    private var bangDotY = 0f

    private var shaderFor: Bitmap? = null
    private var photoShader: BitmapShader? = null

    /**
     * Dibuja la burbuja con el centro en ([cx], [cy]) y el círculo de radio [radius] px. La sombra sale hasta
     * [SHADOW_REACH] veces el radio: deja ese sitio alrededor.
     */
    fun draw(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        photo: Bitmap?,
        initials: String,
        mark: Mark = Mark.NONE,
        spin: Float = 0f,
        hot: Boolean = false,
        lifted: Boolean = false,
    ) {
        if (radius <= 0f) return
        build(cx, cy, radius)
        val ring = radius * RING
        // Sombra suave: un halo oscuro que se apaga hacia fuera (un poco más abajo, como si flotase).
        canvas.drawCircle(cx, cy + radius * SHADOW_DROP, radius * SHADOW_REACH, shadowPaint)

        // Foto o iniciales, recortadas en círculo.
        val image = photo?.takeUnless { it.isRecycled }
        if (image != null) {
            val shader = photoShader?.takeIf { shaderFor === image }
                ?: BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also {
                    photoShader = it
                    shaderFor = image
                }
            val scale = radius * 2 / minOf(image.width, image.height)
            photoMatrix.setScale(scale, scale)
            photoMatrix.postTranslate(cx - image.width * scale / 2f, cy - image.height * scale / 2f)
            shader.setLocalMatrix(photoMatrix)
            photoPaint.shader = shader
            canvas.drawCircle(cx, cy, radius, photoPaint)
        } else {
            fillPaint.color = withAlpha(ink, INITIALS_FILL)
            canvas.drawCircle(cx, cy, radius, fillPaint)
            // Con una marca en el centro («+», visto, «!»), las iniciales asomarían por los lados: fuera.
            if (!hot && mark != Mark.SENT && mark != Mark.FAILED) {
                textPaint.textSize = radius * 0.78f
                canvas.drawText(initials, cx, cy - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
            }
        }

        // Reflejo arriba: una elipse clara que se apaga hacia abajo, dentro del círculo.
        canvas.drawOval(shine, shinePaint)

        // Anillo de cristal.
        val ringAlpha = when {
            hot -> RING_HOT
            lifted -> RING_LIFTED
            else -> RING_IDLE
        }
        val width = if (hot) ring * HOT_RING else ring
        ringPaint.strokeWidth = width
        ringPaint.color = withAlpha(glass, ringAlpha)
        arc.set(cx - radius + width / 2, cy - radius + width / 2, cx + radius - width / 2, cy + radius - width / 2)
        canvas.drawOval(arc, ringPaint)

        // Estados, encima de la foto.
        if (mark == Mark.SENDING) {
            // Un carril oscuro translúcido bajo el brillo: se ve igual sobre fotos claras y oscuras.
            ringPaint.strokeWidth = ring * SPIN_WIDTH
            arc.inset(ring * (SPIN_WIDTH - 1) / 2, ring * (SPIN_WIDTH - 1) / 2)
            ringPaint.color = withAlpha(ink, 0.45f)
            canvas.drawOval(arc, ringPaint)
            ringPaint.color = glass
            canvas.drawArc(arc, spin - 90f, SPIN_SWEEP, false, ringPaint)
        }
        val chip = radius * CHIP
        when {
            hot -> {
                fillPaint.color = withAlpha(ink, 0.6f)
                canvas.drawCircle(cx, cy, chip, fillPaint)
                glyphPaint.color = glass
                glyphPaint.strokeWidth = chip * 0.22f
                canvas.drawPath(plus, glyphPaint)
            }
            mark == Mark.SENT -> {
                fillPaint.color = withAlpha(ink, SCRIM)
                canvas.drawCircle(cx, cy, radius, fillPaint)
                fillPaint.color = colors.signal.toArgb()
                canvas.drawCircle(cx, cy, chip, fillPaint)
                glyphPaint.color = colors.onSignal.toArgb()
                glyphPaint.strokeWidth = chip * 0.22f
                canvas.drawPath(check, glyphPaint)
            }
            mark == Mark.FAILED -> {
                fillPaint.color = withAlpha(ink, SCRIM)
                canvas.drawCircle(cx, cy, radius, fillPaint)
                fillPaint.color = withAlpha(glass, 0.94f)
                canvas.drawCircle(cx, cy, chip, fillPaint)
                glyphPaint.color = ink
                glyphPaint.strokeWidth = chip * 0.22f
                canvas.drawPath(bang, glyphPaint)
                fillPaint.color = ink
                canvas.drawCircle(cx, bangDotY, glyphPaint.strokeWidth * 0.62f, fillPaint)
            }
        }
    }

    private fun build(cx: Float, cy: Float, radius: Float) {
        if (radius == builtFor && cx == builtX && cy == builtY) return
        builtFor = radius
        builtX = cx
        builtY = cy
        shadowPaint.shader = RadialGradient(
            cx, cy + radius * SHADOW_DROP, radius * SHADOW_REACH,
            intArrayOf(withAlpha(ink, SHADOW), withAlpha(ink, SHADOW), withAlpha(ink, 0f)),
            floatArrayOf(0f, SHADOW_FROM / SHADOW_REACH, 1f),
            Shader.TileMode.CLAMP,
        )
        shine.set(cx - radius * 0.66f, cy - radius * 0.92f, cx + radius * 0.66f, cy - radius * 0.08f)
        shinePaint.shader = LinearGradient(
            0f, shine.top, 0f, shine.bottom,
            withAlpha(glass, SHINE), withAlpha(glass, 0f),
            Shader.TileMode.CLAMP,
        )
        val chip = radius * CHIP
        glyph(check, cx, cy, chip, floatArrayOf(5f, 12.5f, 10f, 17f, 19f, 7.5f))
        glyph(bang, cx, cy, chip, floatArrayOf(12f, 5.5f, 12f, 13f))
        glyph(plus, cx, cy, chip, floatArrayOf(12f, 6f, 12f, 18f), floatArrayOf(6f, 12f, 18f, 12f))
        bangDotY = cy - chip * 1.25f / 2f + 18f * chip * 1.25f / 24f
    }

    /** Un trazo de la rejilla de 24 (como `ui/icons`) en un círculo de radio [chip] centrado en ([cx], [cy]). */
    private fun glyph(path: Path, cx: Float, cy: Float, chip: Float, vararg polylines: FloatArray) {
        path.reset()
        val side = chip * 1.25f
        val s = side / 24f
        val ox = cx - side / 2f
        val oy = cy - side / 2f
        polylines.forEach { p ->
            path.moveTo(ox + p[0] * s, oy + p[1] * s)
            var i = 2
            while (i < p.size) {
                path.lineTo(ox + p[i] * s, oy + p[i + 1] * s)
                i += 2
            }
        }
    }

    companion object {
        /** El círculo de las burbujas sobre los reels, y el lado de su zona táctil (accesibilidad: 48 dp). */
        const val CIRCLE_DP = 42f
        const val TOUCH_DP = 48f

        /** Hasta dónde llega la sombra, en veces el radio (y cuánto baja). */
        const val SHADOW_REACH = 1.13f
        private const val SHADOW_FROM = 0.92f
        private const val SHADOW_DROP = 0.05f
        private const val SHADOW = 0.32f

        /** Grosor del anillo en parte del radio (≈ 1,5 dp a 42 dp) y su alfa en cada caso. */
        private const val RING = 0.075f
        private const val RING_IDLE = 0.42f
        private const val RING_LIFTED = 0.66f
        private const val RING_HOT = 0.85f
        private const val HOT_RING = 2f

        private const val SHINE = 0.34f
        private const val INITIALS_FILL = 0.62f
        private const val SCRIM = 0.3f

        /** El círculo pequeño de las marcas (visto, «!», «+»), en parte del radio (≈ 18 dp de lado). */
        private const val CHIP = 0.43f
        private const val SPIN_SWEEP = 110f

        /** Enviando: el carril y el brillo, este número de veces el grosor del anillo. */
        private const val SPIN_WIDTH = 2f

        fun font(context: Context): Typeface? = runCatching { ResourcesCompat.getFont(context, R.font.bricolage_grotesque) }.getOrNull()

        private fun withAlpha(color: Int, alpha: Float): Int =
            Color.argb((alpha.coerceIn(0f, 1f) * 255 + 0.5f).toInt(), Color.red(color), Color.green(color), Color.blue(color))
    }
}
