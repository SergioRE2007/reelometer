package com.reelometro.app.detection

import com.reelometro.app.data.Platform

import kotlin.math.roundToInt

/**
 * Dónde va la pila de **burbujas** del envío rápido: el **centro** de la pila en fracción de la pantalla ([x] 0 a
 * la izquierda, 1 a la derecha; [y] 0 arriba, 1 abajo), para que valga al girar el móvil o al cambiar de móvil.
 * La orientación no se guarda: sale de [y] ([BubbleLayout.orientationFor]). Se guarda en DataStore
 * (`SettingsRepository.setQuickSendBubble`).
 */
data class BubbleSpot(val x: Float, val y: Float)

/** La pila en columna (una debajo de otra) o en fila (una al lado de otra, encima del nombre del vídeo). */
enum class BubbleOrientation { COLUMN, ROW }

/**
 * Márgenes en px desde cada borde de la pantalla que la pila no pisa (barra de estado, de navegación, recorte de
 * la cámara y un poco de aire).
 */
data class SafeArea(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Cuentas puras de las burbujas: posición libre, orientación, arrastre, zona «X» y cuándo preguntar si se apaga. */
object BubbleLayout {

    /**
     * Por defecto, junto al borde derecho y un poco por encima de la mitad: la columna de botones de Instagram (me
     * gusta, comentar, compartir…) empieza hacia el 55 % y la descripción va abajo. Es lo mismo que en la 1.4.1
     * (pegadas al borde derecho con el centro al 40 %).
     *
     * 1.10.5: `x = 1` (el borde derecho seguro, que [clamp] corrige con el ancho de la pila) y no 0,92 del ancho: en un
     * móvil en vertical queda igual (±3 px), pero en horizontal o en una tablet 0,92 dejaba la pila a ~70 dp del borde.
     */
    val DEFAULT = BubbleSpot(x = 1f, y = 0.40f)

    /**
     * 1.10.0, sobre TikTok: en la columna de la derecha la foto del creador empieza hacia el 46 % de la altura (y los
     * botones siguen hasta el 92 %), así que la pila va más arriba, con el centro al 30 %, bajo las pestañas de arriba
     * (acaban hacia el 12 %, con la lupa a la derecha). Lo demás vale igual sobre TikTok (medido con el volcado de
     * TikTok 47.7 en `BubbleLayoutTest`): una fila soltada al principio de su franja ([ROW_FROM_Y]) queda encima del
     * nombre del creador y de la descripción (abajo a la izquierda, desde el 84 %), y la zona «X» solo se ve mientras
     * se arrastra (soltar ahí no toca nada de TikTok).
     */
    val DEFAULT_TIKTOK = BubbleSpot(x = DEFAULT.x, y = 0.30f)

    /** El sitio por defecto de las burbujas en [platform] (donde las deja RECOLOCAR). */
    fun defaultFor(platform: Platform): BubbleSpot = if (platform == Platform.TIKTOK) DEFAULT_TIKTOK else DEFAULT

    /**
     * Sin los márgenes del sistema a mano, la pila no pasa de esta franja (ni bajo la barra de estado ni encima de
     * la de navegación).
     */
    const val MIN_Y = 0.08f
    const val MAX_Y = 0.92f

    /**
     * Soltada con el centro en la franja de abajo (de aquí hacia abajo, encima del nombre del vídeo, en Instagram y en
     * TikTok), la pila se pone en **fila**; en el resto, en **columna**.
     */
    const val ROW_FROM_Y = 0.72f

    /** Lo más abajo que queda una posición antigua al convertirla: justo por encima de la franja de la fila. */
    const val LEGACY_MAX_Y = ROW_FROM_Y - 0.01f

    /** La zona «X» de esconder: su centro, en fracción de la altura (abajo del todo, centrada). */
    const val DISMISS_CENTER_Y = 0.88f

    /** Esconder la burbuja estas veces seguidas (sin enviar entre medias) hace preguntar si se apaga. */
    const val ASK_AFTER_DISMISSES = 3

    /** Fila en la franja de abajo, columna en el resto. Un valor roto, columna. */
    fun orientationFor(y: Float): BubbleOrientation =
        if (y.isFinite() && y >= ROW_FROM_Y) BubbleOrientation.ROW else BubbleOrientation.COLUMN

    /**
     * La posición guardada de antes (borde + altura del centro) en la posición libre equivalente, siempre en
     * columna, como era: nadie la ve pasar a fila sin haberla tocado. Era «pegadas al borde izquierdo o derecho»
     * (1.4.0–1.4.1): `x` = 0 o 1, que [clamp] deja en el borde seguro en cualquier pantalla.
     */
    fun fromLegacy(left: Boolean, centerY: Float): BubbleSpot =
        sanitize(BubbleSpot(x = if (left) 0f else 1f, y = centerY)).let { s ->
            // Era una columna: sigue siéndolo aunque estuviera en la franja de abajo (nadie la ve cambiar sin tocarla).
            s.copy(y = s.y.coerceAtMost(LEGACY_MAX_Y))
        }

    /**
     * Fracciones entre 0 y 1; lo que no es un número vuelve al de [fallback] (el sitio por defecto de la app:
     * [defaultFor]; sin decirlo, el de Instagram).
     */
    fun sanitize(spot: BubbleSpot, fallback: BubbleSpot = DEFAULT): BubbleSpot = BubbleSpot(
        x = spot.x.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: fallback.x,
        y = spot.y.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: fallback.y,
    )

    /**
     * Lo que la pila no pisa: los márgenes del sistema ([insets], en px) más [edge] px de aire. Sin ellos, la
     * franja [MIN_Y]–[MAX_Y] en vertical y [edge] a los lados.
     *
     * 1.10.5: sin márgenes (Android 8–10) y en horizontal, la barra de navegación va en un lado: [landscapeSide] px más a
     * izquierda y derecha (a los dos, para no tener que saber hacia dónde se ha girado).
     */
    fun safeArea(screenWidth: Int, screenHeight: Int, insets: SafeArea?, edge: Int, landscapeSide: Int = 0): SafeArea {
        if (insets == null) {
            val side = edge + if (screenWidth > screenHeight) landscapeSide.coerceAtLeast(0) else 0
            return SafeArea(
                left = side,
                top = (screenHeight * MIN_Y).roundToInt(),
                right = side,
                bottom = (screenHeight * (1f - MAX_Y)).roundToInt(),
            )
        }
        return SafeArea(insets.left + edge, insets.top + edge, insets.right + edge, insets.bottom + edge)
    }

    /**
     * [spot] corregido lo justo para que una pila de [stackWidth]×[stackHeight] px quepa entera dentro de [safe]. Si
     * no cabe en un eje, se centra en lo que queda de ese eje.
     */
    fun clamp(spot: BubbleSpot, stackWidth: Int, stackHeight: Int, screenWidth: Int, screenHeight: Int, safe: SafeArea): BubbleSpot {
        val clean = sanitize(spot)
        if (screenWidth <= 0 || screenHeight <= 0) return clean
        return BubbleSpot(
            x = clampAxis(clean.x, stackWidth, screenWidth, safe.left, safe.right),
            y = clampAxis(clean.y, stackHeight, screenHeight, safe.top, safe.bottom),
        )
    }

    private fun clampAxis(fraction: Float, stack: Int, screen: Int, before: Int, after: Int): Float {
        val half = stack / 2f
        val min = (before + half) / screen
        val max = (screen - after - half) / screen
        return if (min > max) ((min + max) / 2f).coerceIn(0f, 1f) else fraction.coerceIn(min, max)
    }

    /**
     * Al soltar la pila con el centro en ([centerX], [centerY]) px: se queda **ahí** (sin imán al borde), con la
     * orientación que toca a esa altura y corregida solo para no salirse de [safe]. [size] da el ancho y el alto
     * de la pila en cada orientación. La posición devuelta, guardada y vuelta a leer, da la misma orientación
     * (y la misma corrección).
     *
     * 1.10.5, [edgeSnap] (px): si el centro queda a esa distancia o menos del sitio más a la izquierda o más a la derecha
     * que cabe, se guarda `x` = 0 o 1 ([snapToEdge]): soltada contra el borde, sigue pegada al girar o en otra pantalla.
     * Con `null`, la fracción tal cual.
     */
    fun drop(
        centerX: Float,
        centerY: Float,
        screenWidth: Int,
        screenHeight: Int,
        safe: SafeArea,
        dismissRadius: Int = 0,
        edgeSnap: Int? = null,
        size: (BubbleOrientation) -> Pair<Int, Int>,
    ): BubbleSpot {
        if (screenWidth <= 0 || screenHeight <= 0) return DEFAULT
        var spot = sanitize(BubbleSpot(centerX / screenWidth, centerY / screenHeight))
        var orientation = orientationFor(spot.y)
        for (attempt in 0 until 2) {
            val (w, h) = size(orientation)
            spot = clamp(spot, w, h, screenWidth, screenHeight, safe)
            val next = orientationFor(spot.y)
            if (next == orientation) break
            orientation = next
        }
        val (w, h) = size(orientation)
        val placed = awayFromDismiss(spot, w, screenWidth, screenHeight, safe, h, dismissRadius)
        return snapToEdge(placed, w, screenWidth, safe, edgeSnap)
    }

    /**
     * [spot] con `x` = 0 o 1 si su centro está a [tolerance] px o menos del sitio más a la izquierda o más a la derecha en el
     * que cabe una pila de [stackWidth] px dentro de [safe] (lo que [clamp] deja con 0 o 1). Sin [tolerance], o si la pila no
     * cabe, tal cual.
     */
    internal fun snapToEdge(spot: BubbleSpot, stackWidth: Int, screenWidth: Int, safe: SafeArea, tolerance: Int?): BubbleSpot {
        if (tolerance == null || tolerance < 0 || screenWidth <= 0 || !spot.x.isFinite()) return spot
        val half = stackWidth / 2f
        val min = safe.left + half
        val max = screenWidth - safe.right - half
        if (min > max) return spot
        val center = spot.x * screenWidth
        return when {
            center - min <= tolerance -> spot.copy(x = 0f)
            max - center <= tolerance -> spot.copy(x = 1f)
            else -> spot
        }
    }

    /**
     * Si al corregir la pila ha caído encima de la zona «X» (soltada justo debajo, y subida por la barra de
     * navegación), se aparta a un lado de la «X», hacia el que estaba: nunca se guarda encima de ella.
     */
    private fun awayFromDismiss(spot: BubbleSpot, w: Int, screenWidth: Int, screenHeight: Int, safe: SafeArea, h: Int, radius: Int): BubbleSpot {
        if (radius <= 0 || !inDismissZone(spot.x * screenWidth, spot.y * screenHeight, screenWidth, screenHeight, radius)) return spot
        val (cx, _) = dismissCenter(screenWidth, screenHeight)
        val side = if (spot.x * screenWidth < cx) -1 else 1
        val x = (cx + side * (radius + w / 2f + 1f)) / screenWidth
        return clamp(spot.copy(x = x), w, h, screenWidth, screenHeight, safe)
    }

    /** Esquina de arriba a la izquierda de la ventana (px) para [spot], ya dentro de [safe]. */
    fun windowXY(spot: BubbleSpot, stackWidth: Int, stackHeight: Int, screenWidth: Int, screenHeight: Int, safe: SafeArea): Pair<Int, Int> {
        val clamped = clamp(spot, stackWidth, stackHeight, screenWidth, screenHeight, safe)
        val x = (clamped.x * screenWidth - stackWidth / 2f).roundToInt().coerceIn(0, (screenWidth - stackWidth).coerceAtLeast(0))
        val y = (clamped.y * screenHeight - stackHeight / 2f).roundToInt().coerceIn(0, (screenHeight - stackHeight).coerceAtLeast(0))
        return x to y
    }

    /** Ancho y alto de una pila de [count] burbujas de [bubble] px separadas [gap] px, en [orientation]. */
    fun stackSize(count: Int, bubble: Int, gap: Int, orientation: BubbleOrientation): Pair<Int, Int> {
        if (count <= 0) return 0 to 0
        val long = count * bubble + (count - 1) * gap
        return if (orientation == BubbleOrientation.ROW) long to bubble else bubble to long
    }

    /** Un dedo que se ha movido más que [touchSlop] px ya arrastra: al soltar, no envía. */
    fun isDrag(dx: Float, dy: Float, touchSlop: Int): Boolean = dx * dx + dy * dy > touchSlop.toFloat() * touchSlop

    /** Centro de la zona «X» en px. */
    fun dismissCenter(screenWidth: Int, screenHeight: Int): Pair<Int, Int> =
        screenWidth / 2 to (screenHeight * DISMISS_CENTER_Y).roundToInt()

    /** ¿El centro de la pila ([x], [y]) está sobre la zona «X»? [radius] en px. */
    fun inDismissZone(x: Float, y: Float, screenWidth: Int, screenHeight: Int, radius: Int): Boolean {
        val (cx, cy) = dismissCenter(screenWidth, screenHeight)
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy <= radius.toFloat() * radius
    }
}

/**
 * Cuántas veces seguidas se ha escondido la burbuja en la zona «X» y si ya se preguntó (solo se pregunta una
 * vez en la vida). Se guarda en las preferencias del servicio.
 */
data class DismissStreak(val count: Int = 0, val asked: Boolean = false) {

    /** Una vez más a la «X». `second` = toca preguntar ahora si se apaga el envío rápido. */
    fun dismissed(): Pair<DismissStreak, Boolean> {
        val next = copy(count = count + 1)
        return if (!asked && next.count >= BubbleLayout.ASK_AFTER_DISMISSES) next.copy(count = 0, asked = true) to true else next to false
    }

    /** Se ha usado una burbuja: la racha vuelve a cero. */
    fun used(): DismissStreak = if (count == 0) this else copy(count = 0)
}
