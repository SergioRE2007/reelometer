package com.reelometro.app.detection

/**
 * **Qué burbujas se ven y en qué estado** (1.4.1, lógica pura sin Android). Lo decide esto y no
 * [ShareDetector.isArmed], que sirve para otra cosa (que el detector de «compartir» no cuente dos veces).
 *
 * Reglas:
 *  - Las burbujas se quedan quietas durante todo el envío rápido y después: no se esconden ni vuelven a salir.
 *  - Cada una enseña su marca (enviando, enviado o fallo) hasta pasar de reel ([onReel]); las demás siguen
 *    normales para mandar el mismo reel a otro fijado.
 *  - Solo se esconden si el usuario abre a mano una hoja encima del visor (compartir, comentarios, más opciones:
 *    [onUserSheetOpened]), con la «X» ([onDismissed]) hasta el siguiente reel, o si sale de Reels (eso lo dice
 *    `onReel` al llamar a [visible]). Un envío rápido que falla y deja la hoja abierta para terminar a mano cuenta
 *    como una hoja abierta, pero después de enseñar el fallo un momento ([FAILED_SHOWN_MS]).
 *  - La hoja se da por cerrada cuando el visor se mueve ([onFeedMoved], pasado [SETTLE_MS] desde que se abrió:
 *    al abrirse también puede llegar un movimiento), al enviar desde ella ([onSheetClosed]) o al cambiar de reel.
 *  - Mientras se envía, siempre se ven (la hoja abierta es la del propio envío).
 */
class BubbleBoard {

    enum class Mark { IDLE, SENDING, SENT, FAILED }

    private var reel: Long? = null
    private val marks = LinkedHashMap<String, Mark>()
    private var sendReel: Long? = null
    private var dismissedReel: Long? = null
    private var dismissed = false

    /** Hay una hoja abierta encima del visor desde [sheetSince]; las burbujas se esconden desde [hideFrom]. */
    private var sheetSince: Long? = null
    private var hideFrom: Long? = null

    val isSending: Boolean get() = marks.values.any { it == Mark.SENDING }

    /** Hay una hoja abierta encima del visor (a mano, o la que deja un fallo). */
    val sheetOpen: Boolean get() = sheetSince != null

    fun markOf(name: String): Mark = marks[name] ?: Mark.IDLE

    /** Las marcas que no son [Mark.IDLE]. */
    val shownMarks: Map<String, Mark> get() = marks.toMap()

    /** El reel visible es [videoId]: si es otro, las marcas (salvo las que aún envían) y la «X» se olvidan. */
    fun onReel(videoId: Long?): Boolean {
        if (videoId == reel) return false
        reel = videoId
        marks.entries.removeAll { it.value != Mark.SENDING }
        dismissed = false
        dismissedReel = null
        sheetSince = null
        hideFrom = null
        return true
    }

    /** El usuario ha abierto a mano una hoja (compartir, comentarios…) encima del visor: se esconden ya. */
    fun onUserSheetOpened(now: Long) {
        sheetSince = now
        hideFrom = now
    }

    /** El visor se ha movido: la hoja que hubiera se ha cerrado (salvo justo al abrirse). */
    fun onFeedMoved(now: Long) {
        val since = sheetSince ?: return
        if (now < since || now - since >= SETTLE_MS) {
            sheetSince = null
            hideFrom = null
        }
    }

    /** Se ha enviado desde la hoja abierta a mano (y se ha cerrado sola). */
    fun onSheetClosed() {
        sheetSince = null
        hideFrom = null
    }

    /** Soltadas en la «X» en el reel [videoId]: fuera hasta el siguiente. */
    fun onDismissed(videoId: Long?) {
        dismissed = true
        dismissedReel = videoId
    }

    /** ¿Se soltaron en la «X» en el reel [videoId]? */
    fun isDismissedOn(videoId: Long?): Boolean = dismissed && dismissedReel == videoId

    /** Empieza (o se suma a) un envío rápido desde el reel [videoId]. */
    fun onSending(names: Collection<String>, videoId: Long?) {
        names.forEach { marks[it] = Mark.SENDING }
        sendReel = videoId
    }

    /** Android ya aceptó «Enviar»: dicen «enviado» sin esperar a la confirmación. */
    fun onProvisionallySent(names: Collection<String>) {
        names.forEach { if (marks[it] == Mark.SENDING) marks[it] = Mark.SENT }
    }

    /**
     * Fin del envío: cada destino de [results] (nombre → enviado) queda con su marca, si se sigue en el reel desde
     * el que se envió. [sheetLeftOpen]: la hoja se queda abierta para terminar a mano; se esconden tras enseñar
     * el fallo [FAILED_SHOWN_MS].
     */
    fun onFinished(results: Map<String, Boolean>, sheetLeftOpen: Boolean, now: Long) {
        val sameReel = sendReel == reel
        for ((name, ok) in results) {
            if (sameReel) {
                marks[name] = if (ok) Mark.SENT else Mark.FAILED
            } else {
                marks.remove(name)
            }
        }
        if (sheetLeftOpen && sameReel) {
            sheetSince = now
            hideFrom = now + FAILED_SHOWN_MS
        }
    }

    /**
     * ¿Se ven ahora? [onReel]: envío rápido encendido, con fijados y un reel de Instagram delante; [videoId]: ese reel.
     * Enviando, siempre.
     */
    fun visible(onReel: Boolean, videoId: Long?, now: Long): Boolean {
        if (isSending) return true
        if (!onReel) return false
        if (dismissed && dismissedReel == videoId) return false
        val hide = hideFrom
        return hide == null || now < hide
    }

    /** Cuándo puede cambiar [visible] sin que llegue ningún evento (para volver a mirar entonces), o `null`. */
    fun nextChangeAt(now: Long): Long? = hideFrom?.takeIf { it > now }

    companion object {
        /** Movimientos del visor justo al abrirse una hoja que no la cierran. */
        const val SETTLE_MS = 500L

        /** Lo que se ve el «!» antes de esconderlas, si la hoja se queda abierta para terminar a mano. */
        const val FAILED_SHOWN_MS = 2_000L
    }
}

/**
 * El dedo sobre una burbuja (1.4.1, puro): **toque** (envía), **arrastre** de la pila (mover el dedo más que
 * `touchSlop` antes de [longPressMs]: mueve todas, como en la 1.4.0) o **mantener y soltar encima** (quieto
 * [longPressMs]: esa burbuja se despega; soltarla sobre otra envía a las dos; en otro sitio, vuelve a la pila).
 * Las coordenadas son de pantalla (px).
 */
class BubbleGesture(private val touchSlop: Int, private val longPressMs: Long) {

    enum class Mode { IDLE, PRESSED, DRAG_STACK, LIFTED }

    sealed interface Outcome {
        /** Toque sin mover: enviar a [index]. */
        data class Tap(val index: Int) : Outcome

        /** Fin del arrastre de la pila (el que mira la «X» y el borde es quien la mueve). */
        data object StackDropped : Outcome

        /** [from] soltada encima de [to]: enviar a las dos. */
        data class Together(val from: Int, val to: Int) : Outcome

        /** [index] soltada fuera de otra burbuja: vuelve a la pila sin enviar. */
        data class Returned(val index: Int) : Outcome

        data object None : Outcome
    }

    var mode = Mode.IDLE
        private set

    /** La burbuja que tiene el dedo. */
    var index = -1
        private set

    /** Con una despegada: la burbuja que tiene debajo (se ilumina), o `null`. */
    var hover: Int? = null
        private set

    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L

    fun down(index: Int, x: Float, y: Float, now: Long) {
        mode = Mode.PRESSED
        this.index = index
        hover = null
        downX = x
        downY = y
        downAt = now
    }

    /** ¿Toca despegarla ya? (el servicio lo pregunta a los [longPressMs]). Si es que sí, pasa a [Mode.LIFTED]. */
    fun longPress(now: Long): Boolean {
        if (mode != Mode.PRESSED || now - downAt < longPressMs) return false
        mode = Mode.LIFTED
        return true
    }

    /**
     * El dedo se mueve a ([x], [y]). [centers]: centro de cada burbuja en la pila; [radius]: a esta distancia
     * del centro ya está encima. Devuelve `true` si ha cambiado algo que se ve (empieza el arrastre de la pila o
     * cambia la burbuja de debajo).
     */
    fun move(x: Float, y: Float, centers: List<Pair<Float, Float>>, radius: Float): Boolean = when (mode) {
        Mode.PRESSED -> if (BubbleLayout.isDrag(x - downX, y - downY, touchSlop)) {
            mode = Mode.DRAG_STACK
            true
        } else {
            false
        }
        Mode.LIFTED -> {
            val under = bubbleUnder(x, y, centers, exclude = index, radius = radius)
            if (under != hover) {
                hover = under
                true
            } else {
                false
            }
        }
        else -> false
    }

    /** El dedo se levanta en ([x], [y]). */
    fun up(x: Float, y: Float, centers: List<Pair<Float, Float>>, radius: Float): Outcome {
        val outcome = when (mode) {
            Mode.PRESSED -> Outcome.Tap(index)
            Mode.DRAG_STACK -> Outcome.StackDropped
            Mode.LIFTED -> bubbleUnder(x, y, centers, exclude = index, radius = radius)?.let { Outcome.Together(index, it) } ?: Outcome.Returned(index)
            Mode.IDLE -> Outcome.None
        }
        reset()
        return outcome
    }

    /** Android cancela el gesto: nunca envía. */
    fun cancel(): Outcome {
        val outcome = when (mode) {
            Mode.DRAG_STACK -> Outcome.StackDropped
            Mode.LIFTED -> Outcome.Returned(index)
            else -> Outcome.None
        }
        reset()
        return outcome
    }

    private fun reset() {
        mode = Mode.IDLE
        hover = null
    }

    companion object {
        /** La burbuja (de [centers], sin contar [exclude]) más cercana a ([x], [y]) a menos de [radius], o `null`. */
        fun bubbleUnder(x: Float, y: Float, centers: List<Pair<Float, Float>>, exclude: Int, radius: Float): Int? =
            centers.withIndex()
                .filter { it.index != exclude }
                .map { (i, c) -> i to (c.first - x) * (c.first - x) + (c.second - y) * (c.second - y) }
                .filter { it.second <= radius * radius }
                .minByOrNull { it.second }
                ?.first
    }
}
