package com.reelometro.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Iconos propios de Reelometer (docs/DESIGN.md §5). Nada de Material Icons.
 *
 * Reglas de la familia:
 *  - Rejilla de 24 dp; formas cuadradas en 4–20, círculos de radio 8,5–9, padding de 2 dp.
 *  - Trazo uniforme de 1,75, remates cuadrados y uniones en inglete. Sin rellenos salvo puntos de 2 dp.
 *  - Rasgo propio, «el corte»: las piezas que se tocarían quedan separadas por un hueco de 1,25 dp,
 *    como la bisagra de una ficha split-flap (bandejas de Compartir y Descargar, tapa de la papelera,
 *    eje de Estadísticas, deslizadores de Ajustes, fichas apiladas…).
 *
 * Se pintan con [ReelIcon]; el color lo pone el tinte (los trazados son negros).
 */
object ReelIcons {

    // ------------------------------------------------------------------ navegación principal

    /** Hoy: una ficha split-flap con su eje (la bisagra asoma por los lados). */
    val Today: ImageVector by lazy {
        reelIcon("Today") {
            rect(5.5f, 3f, 18.5f, 21f)
            line(3f, 12f, 21f, 12f)
        }
    }

    /** Estadísticas: tres columnas sobre un eje separado. */
    val Stats: ImageVector by lazy {
        reelIcon("Stats") {
            line(6.5f, 17.5f, 6.5f, 11f)
            line(12f, 17.5f, 12f, 4f)
            line(17.5f, 17.5f, 17.5f, 8f)
            line(3.5f, 20.5f, 20.5f, 20.5f)
        }
    }

    /** Resumen (Wrapped): fichas apiladas; la delantera lleva el «play» de la historia. */
    val Wrapped: ImageVector by lazy {
        reelIcon("Wrapped") {
            poly(8f, 3.5f, 20.5f, 3.5f, 20.5f, 16f)
            rect(3.5f, 7.5f, 16.5f, 21f)
            polygon(8.25f, 11.25f, 12.25f, 14.25f, 8.25f, 17.25f)
        }
    }

    /**
     * Flap (la pestaña de la mascota): su ficha con los dos ojos y los pies, separados por el corte.
     * Sin bisagra, para no confundirlo con [Today].
     */
    val Flap: ImageVector by lazy {
        reelIcon("Flap") {
            rect(5f, 3f, 19f, 18f)
            dot(9.5f, 9.5f); dot(14.5f, 9.5f)
            line(8.5f, 21f, 10.5f, 21f); line(13.5f, 21f, 15.5f, 21f)
        }
    }

    /** Ajustes: tres deslizadores (no un engranaje). */
    val Settings: ImageVector by lazy {
        reelIcon("Settings") {
            line(4f, 6f, 12f, 6f); line(18f, 6f, 20f, 6f); line(15f, 3.75f, 15f, 8.25f)
            line(4f, 12f, 6f, 12f); line(12f, 12f, 20f, 12f); line(9f, 9.75f, 9f, 14.25f)
            line(4f, 18f, 11f, 18f); line(17f, 18f, 20f, 18f); line(14f, 15.75f, 14f, 20.25f)
        }
    }

    // ------------------------------------------------------------------ acciones

    /** Compartir: flecha que sale de una bandeja abierta. */
    val Share: ImageVector by lazy {
        reelIcon("Share") {
            poly(9f, 10.5f, 5f, 10.5f, 5f, 20.5f, 19f, 20.5f, 19f, 10.5f, 15f, 10.5f)
            line(12f, 3.5f, 12f, 15f)
            poly(8.5f, 7f, 12f, 3.5f, 15.5f, 7f)
        }
    }

    /** Enviar: avión de papel. */
    val Send: ImageVector by lazy {
        reelIcon("Send") {
            poly(*PaperPlane.OUTLINE)
            poly(*PaperPlane.FOLD)
        }
    }

    /** Descargar / exportar: flecha que entra en una bandeja. */
    val Download: ImageVector by lazy {
        reelIcon("Download") {
            line(12f, 3.5f, 12f, 15f)
            poly(7.5f, 10.5f, 12f, 15f, 16.5f, 10.5f)
            poly(4f, 15.5f, 4f, 20.5f, 20f, 20.5f, 20f, 15.5f)
        }
    }

    /** Subir / importar: flecha que sale de la bandeja (la de [Download], al revés). */
    val Upload: ImageVector by lazy {
        reelIcon("Upload") {
            line(12f, 15f, 12f, 3.5f)
            poly(7.5f, 8f, 12f, 3.5f, 16.5f, 8f)
            poly(4f, 15.5f, 4f, 20.5f, 20f, 20.5f, 20f, 15.5f)
        }
    }

    /** Papelera: tapa separada del cuerpo por el corte. */
    val Trash: ImageVector by lazy {
        reelIcon("Trash") {
            line(3.75f, 6.5f, 20.25f, 6.5f)
            poly(9f, 6.5f, 9f, 3.5f, 15f, 3.5f, 15f, 6.5f)
            poly(5.75f, 9.5f, 5.75f, 20.5f, 18.25f, 20.5f, 18.25f, 9.5f)
            line(10f, 12f, 10f, 17.5f)
            line(14f, 12f, 14f, 17.5f)
        }
    }

    /** Copiar: una hoja delante de otra. */
    val Copy: ImageVector by lazy {
        reelIcon("Copy") {
            poly(4f, 15.5f, 4f, 4f, 15.5f, 4f)
            rect(8f, 8f, 20f, 20f)
        }
    }

    /** Abrir fuera de la app (ajustes del sistema, enlaces). */
    val External: ImageVector by lazy {
        reelIcon("External") {
            poly(10.5f, 4.5f, 4.5f, 4.5f, 4.5f, 19.5f, 19.5f, 19.5f, 19.5f, 13.5f)
            line(11f, 13f, 19.5f, 4.5f)
            poly(13.5f, 4.5f, 19.5f, 4.5f, 19.5f, 10.5f)
        }
    }

    val Close: ImageVector by lazy {
        reelIcon("Close") {
            line(5.5f, 5.5f, 18.5f, 18.5f)
            line(18.5f, 5.5f, 5.5f, 18.5f)
        }
    }

    val Check: ImageVector by lazy {
        reelIcon("Check") { poly(4.5f, 12f, 9.5f, 17f, 19.5f, 6.5f) }
    }

    val Plus: ImageVector by lazy {
        reelIcon("Plus") {
            line(12f, 4.5f, 12f, 19.5f)
            line(4.5f, 12f, 19.5f, 12f)
        }
    }

    val Minus: ImageVector by lazy {
        reelIcon("Minus") { line(4.5f, 12f, 19.5f, 12f) }
    }

    val Play: ImageVector by lazy {
        reelIcon("Play") { polygon(6.5f, 4.5f, 18f, 12f, 6.5f, 19.5f) }
    }

    val Pause: ImageVector by lazy {
        reelIcon("Pause") {
            rect(6f, 4.5f, 10f, 19.5f)
            rect(14f, 4.5f, 18f, 19.5f)
        }
    }

    // ------------------------------------------------------------------ flechas y chevrons

    /** Volver (flecha a la izquierda). */
    val Back: ImageVector by lazy {
        reelIcon("Back") {
            line(20f, 12f, 4.5f, 12f)
            poly(11f, 5.5f, 4.5f, 12f, 11f, 18.5f)
        }
    }

    val ArrowLeft: ImageVector get() = Back

    val ArrowRight: ImageVector by lazy {
        reelIcon("ArrowRight") {
            line(4f, 12f, 19.5f, 12f)
            poly(13f, 5.5f, 19.5f, 12f, 13f, 18.5f)
        }
    }

    val ArrowUp: ImageVector by lazy {
        reelIcon("ArrowUp") {
            line(12f, 20f, 12f, 4.5f)
            poly(5.5f, 11f, 12f, 4.5f, 18.5f, 11f)
        }
    }

    val ArrowDown: ImageVector by lazy {
        reelIcon("ArrowDown") {
            line(12f, 4f, 12f, 19.5f)
            poly(5.5f, 13f, 12f, 19.5f, 18.5f, 13f)
        }
    }

    val ChevronLeft: ImageVector by lazy {
        reelIcon("ChevronLeft") { poly(15.25f, 5.5f, 8.75f, 12f, 15.25f, 18.5f) }
    }

    val ChevronRight: ImageVector by lazy {
        reelIcon("ChevronRight") { poly(8.75f, 5.5f, 15.25f, 12f, 8.75f, 18.5f) }
    }

    val ChevronDown: ImageVector by lazy {
        reelIcon("ChevronDown") { poly(5.5f, 8.75f, 12f, 15.25f, 18.5f, 8.75f) }
    }

    val ChevronUp: ImageVector by lazy {
        reelIcon("ChevronUp") { poly(5.5f, 15.25f, 12f, 8.75f, 18.5f, 15.25f) }
    }

    // ------------------------------------------------------------------ objetos

    /** Avisos: campana recta con el badajo separado. */
    val Bell: ImageVector by lazy {
        reelIcon("Bell") {
            moveTo(6f, 17f)
            lineTo(6f, 10.5f)
            arcTo(6f, 18f, 10.5f, clockwise = true)
            lineTo(18f, 17f)
            line(3.75f, 17f, 20.25f, 17f)
            line(10f, 20.25f, 14f, 20.25f)
        }
    }

    val Clock: ImageVector by lazy {
        reelIcon("Clock") {
            circle(12f, 12f, 9f)
            poly(12f, 6.5f, 12f, 12f, 15.5f, 12f)
        }
    }

    /** Objetivo diario: diana. */
    val Target: ImageVector by lazy {
        reelIcon("Target") {
            circle(12f, 12f, 8.75f)
            circle(12f, 12f, 4.25f)
            dot(12f, 12f)
        }
    }

    val Calendar: ImageVector by lazy {
        reelIcon("Calendar") {
            rect(4f, 5.5f, 20f, 20.5f)
            line(4f, 10f, 20f, 10f)
            line(8.5f, 3f, 8.5f, 7f)
            line(15.5f, 3f, 15.5f, 7f)
            dot(8.5f, 15.25f)
        }
    }

    /** Privacidad: escudo recto. */
    val Shield: ImageVector by lazy {
        reelIcon("Shield") {
            polygon(12f, 3f, 19.5f, 5.5f, 19.5f, 12f, 12f, 20.5f, 4.5f, 12f, 4.5f, 5.5f)
        }
    }

    /** Lo que ve el servicio (accesibilidad). */
    val Eye: ImageVector by lazy {
        reelIcon("Eye") {
            lens(3f, 21f, 12f, sagitta = 6f)
            circle(12f, 12f, 3f)
        }
    }

    val Info: ImageVector by lazy {
        reelIcon("Info") {
            circle(12f, 12f, 9f)
            line(12f, 10.75f, 12f, 16.5f)
            dot(12f, 7.75f)
        }
    }

    val Lock: ImageVector by lazy {
        reelIcon("Lock") {
            rect(5f, 10.5f, 19f, 20.5f)
            moveTo(8f, 10.5f)
            lineTo(8f, 7f)
            arcTo(4f, 16f, 7f, clockwise = true)
            lineTo(16f, 10.5f)
            line(12f, 14f, 12f, 16.75f)
        }
    }

    /** Calibración (modo oculto): un escarabajo con la cabeza separada por el corte. */
    val Bug: ImageVector by lazy {
        reelIcon("Bug") {
            // Cabeza: cúpula.
            moveTo(8.75f, 7.75f)
            arcTo(3.25f, 15.25f, 7.75f, clockwise = true)
            close()
            // Cuerpo: tapa recta, fondo redondo y la costura de los élitros.
            moveTo(7f, 11f)
            lineTo(17f, 11f)
            lineTo(17f, 15.5f)
            arcTo(5f, 7f, 15.5f, clockwise = true)
            close()
            line(12f, 11f, 12f, 20f)
            // Patas.
            line(3f, 13.5f, 7f, 13.5f)
            line(17f, 13.5f, 21f, 13.5f)
            line(7f, 17.75f, 3.75f, 20f)
            line(17f, 17.75f, 20.25f, 20f)
        }
    }

    /** Widget de la pantalla de inicio: una pieza ancha sobre dos apps. */
    val Widget: ImageVector by lazy {
        reelIcon("Widget") {
            rect(4f, 4f, 20f, 10.5f)
            rect(4f, 13.5f, 10.5f, 20f)
            rect(13.5f, 13.5f, 20f, 20f)
        }
    }

    /** Qué apps cuento. */
    val Apps: ImageVector by lazy {
        reelIcon("Apps") {
            rect(4f, 4f, 10.5f, 10.5f)
            rect(13.5f, 4f, 20f, 10.5f)
            rect(4f, 13.5f, 10.5f, 20f)
            rect(13.5f, 13.5f, 20f, 20f)
        }
    }

    /** Estado del servicio: pulso. */
    val Pulse: ImageVector by lazy {
        reelIcon("Pulse") { poly(3f, 12f, 7f, 12f, 9.5f, 5.5f, 14.5f, 18.5f, 17f, 12f, 21f, 12f) }
    }

    /** Ticket (resumen, imagen compartible). */
    val Ticket: ImageVector by lazy {
        reelIcon("Ticket") {
            polygon(
                5f, 3f, 19f, 3f, 19f, 20.5f,
                16.667f, 18.5f, 14.333f, 20.5f, 12f, 18.5f, 9.667f, 20.5f, 7.333f, 18.5f, 5f, 20.5f,
            )
            line(8.5f, 7.5f, 15.5f, 7.5f)
            line(8.5f, 11f, 15.5f, 11f)
            line(8.5f, 14.5f, 12.5f, 14.5f)
        }
    }

    // ------------------------------------------------------------------ tema

    val Sun: ImageVector by lazy {
        reelIcon("Sun") {
            circle(12f, 12f, 4f)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val c = cos(a).toFloat()
                val s = sin(a).toFloat()
                line(12f + 7f * c, 12f + 7f * s, 12f + 9f * c, 12f + 9f * s)
            }
        }
    }

    val Moon: ImageVector by lazy {
        reelIcon("Moon", miterLimit = 2f) { crescent(12f, 12.5f, 8.5f, 16.5f, 8f, 6.75f) }
    }

    /** Tema automático (sigue al sistema): círculo con su mitad oscura. */
    val Auto: ImageVector by lazy {
        reelIcon("Auto") {
            circle(12f, 12f, 9f)
            moveTo(12f, 7f)
            arcTo(5f, 12f, 17f, clockwise = true)
            close()
        }
    }

    /** Todos los iconos con su nombre (galería y pruebas). */
    val all: List<Pair<String, ImageVector>> by lazy {
        listOf(
            "Today" to Today, "Stats" to Stats, "Wrapped" to Wrapped, "Flap" to Flap, "Settings" to Settings,
            "Share" to Share, "Download" to Download, "Upload" to Upload, "Trash" to Trash, "Copy" to Copy,
            "External" to External, "Close" to Close, "Check" to Check, "Plus" to Plus,
            "Minus" to Minus, "Play" to Play, "Pause" to Pause, "Back" to Back,
            "ArrowRight" to ArrowRight, "ArrowUp" to ArrowUp, "ArrowDown" to ArrowDown,
            "ChevronLeft" to ChevronLeft, "ChevronRight" to ChevronRight, "ChevronDown" to ChevronDown,
            "ChevronUp" to ChevronUp, "Bell" to Bell, "Clock" to Clock, "Target" to Target,
            "Calendar" to Calendar, "Shield" to Shield, "Eye" to Eye, "Info" to Info, "Lock" to Lock,
            "Bug" to Bug, "Widget" to Widget, "Apps" to Apps, "Pulse" to Pulse, "Ticket" to Ticket,
            "Sun" to Sun, "Moon" to Moon, "Auto" to Auto,
        )
    }
}

// ---------------------------------------------------------------------- construcción

/**
 * Geometría del avión de papel en la rejilla de 24 (x, y, x, y…) de [ReelIcons.Send]. Hasta la 1.4.1 la usaba también la
 * insignia de las burbujas del envío rápido; desde la 1.4.2 (burbujas de cristal) ya no llevan insignia.
 */
object PaperPlane {
    /** Contorno cerrado: punta arriba a la derecha, ala izquierda, pliegue y cola abajo. */
    val OUTLINE = floatArrayOf(21f, 3f, 3f, 10.5f, 10.5f, 13.5f, 13.5f, 21f, 21f, 3f)

    /** El pliegue, de la punta al centro. */
    val FOLD = floatArrayOf(21f, 3f, 10.5f, 13.5f)
}

/** Grosor del trazo en la rejilla de 24. */
const val ReelIconStroke = 1.75f

/**
 * Lápiz para dibujar iconos en coordenadas de la rejilla de 24 (y hacia abajo).
 * Todo lo que se traza va a un único path de trazo; los puntos, a un path de relleno aparte.
 */
private class IconPen {
    val stroke = PathBuilder()
    val fill = PathBuilder()
    private var hasFill = false
    val fillUsed get() = hasFill

    fun moveTo(x: Float, y: Float) { stroke.moveTo(x, y) }
    fun lineTo(x: Float, y: Float) { stroke.lineTo(x, y) }
    fun close() { stroke.close() }

    /** Arco circular de radio [r] desde el punto actual hasta ([x], [y]); `clockwise` en pantalla. */
    fun arcTo(r: Float, x: Float, y: Float, clockwise: Boolean, large: Boolean = false) {
        stroke.arcTo(r, r, 0f, large, clockwise, x, y)
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
        stroke.moveTo(x1, y1)
        stroke.lineTo(x2, y2)
    }

    /** Polilínea abierta: x0, y0, x1, y1… */
    fun poly(vararg p: Float) {
        stroke.moveTo(p[0], p[1])
        var i = 2
        while (i < p.size) {
            stroke.lineTo(p[i], p[i + 1])
            i += 2
        }
    }

    /** Polígono cerrado. */
    fun polygon(vararg p: Float) {
        poly(*p)
        stroke.close()
    }

    fun rect(l: Float, t: Float, r: Float, b: Float) = polygon(l, t, r, t, r, b, l, b)

    fun circle(cx: Float, cy: Float, r: Float) {
        stroke.moveTo(cx - r, cy)
        stroke.arcTo(r, r, 0f, false, true, cx + r, cy)
        stroke.arcTo(r, r, 0f, false, true, cx - r, cy)
        stroke.close()
    }

    /** Lente (ojo): dos arcos simétricos entre (x1, y) y (x2, y) con flecha [sagitta]. */
    fun lens(x1: Float, x2: Float, y: Float, sagitta: Float) {
        val half = (x2 - x1) / 2f
        val r = (half * half + sagitta * sagitta) / (2f * sagitta)
        stroke.moveTo(x1, y)
        stroke.arcTo(r, r, 0f, false, true, x2, y)
        stroke.arcTo(r, r, 0f, false, true, x1, y)
        stroke.close()
    }

    /**
     * Media luna: el círculo 1 menos el círculo 2 (que lo muerde). Calcula las intersecciones,
     * así se puede retocar la forma moviendo solo centros y radios.
     */
    fun crescent(c1x: Float, c1y: Float, r1: Float, c2x: Float, c2y: Float, r2: Float) {
        val dx = c2x - c1x
        val dy = c2y - c1y
        val d = hypot(dx, dy)
        val a = (d * d + r1 * r1 - r2 * r2) / (2f * d)
        val h = sqrt((r1 * r1 - a * a).coerceAtLeast(0f))
        val mx = c1x + a * dx / d
        val my = c1y + a * dy / d
        // Perpendicular (−dy, dx): en pantalla queda «a la derecha» del vector c1→c2.
        val px = -dy / d
        val py = dx / d
        val plusX = mx + h * px
        val plusY = my + h * py
        val minusX = mx - h * px
        val minusY = my - h * py
        // El arco interior (la parte del círculo 2 que queda dentro del 1) es el largo solo si el
        // centro 2 está más allá de la cuerda común, vista desde el centro 1.
        val innerLarge = a > d
        stroke.moveTo(minusX, minusY)
        stroke.arcTo(r1, r1, 0f, true, false, plusX, plusY)
        stroke.arcTo(r2, r2, 0f, innerLarge, true, minusX, minusY)
        stroke.close()
    }

    /** Punto cuadrado de 2 dp (único relleno permitido). */
    fun dot(cx: Float, cy: Float, size: Float = 2f) {
        hasFill = true
        val h = size / 2f
        fill.moveTo(cx - h, cy - h)
        fill.lineTo(cx + h, cy - h)
        fill.lineTo(cx + h, cy + h)
        fill.lineTo(cx - h, cy + h)
        fill.close()
    }
}

private fun reelIcon(name: String, miterLimit: Float = 4f, draw: IconPen.() -> Unit): ImageVector {
    val pen = IconPen().apply(draw)
    val builder = ImageVector.Builder(
        name = "Reel.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    builder.addPath(
        pathData = pen.stroke.nodes,
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = ReelIconStroke,
        strokeLineCap = StrokeCap.Square,
        strokeLineJoin = StrokeJoin.Miter,
        strokeLineMiter = miterLimit,
    )
    if (pen.fillUsed) builder.addPath(pathData = pen.fill.nodes, fill = SolidColor(Color.Black))
    return builder.build()
}
