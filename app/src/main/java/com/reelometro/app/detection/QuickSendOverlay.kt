package com.reelometro.app.detection

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import com.reelometro.app.R
import com.reelometro.app.copy.QuickSendCopy
import com.reelometro.app.ui.theme.DarkReelColors
import com.reelometro.app.ui.theme.Space
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Las **burbujas de cristal** del envío rápido sobre los reels de Instagram (1.4.2): la foto de perfil (o las
 * iniciales) de cada fijado en un círculo de 42 dp con un anillo de cristal, un reflejo y una sombra suave
 * ([GlassBubbleArt], el mismo dibujo que la vista previa de Ajustes y la galería). Zona táctil de 48 dp. Sin texto
 * visible: el nombre va en la descripción para TalkBack. El círculo y el cristal son excepciones deliberadas de
 * `docs/DESIGN.md`: es un avatar sobre Instagram, no una tarjeta de la app.
 *
 * Ventanas `TYPE_ACCESSIBILITY_OVERLAY` del propio servicio (sin permiso de superposición), con Views porque
 * viven fuera de cualquier actividad. La de las burbujas mide solo lo que ocupan (incluso al arrastrarlas: se
 * mueve la ventana); el resto de la pantalla sigue siendo de Instagram. Sin desenfoque real (ver [GlassBubbleArt]).
 *  - **Tocar** envía (se hunde un poco y vibra). Enviando, un brillo gira por el anillo; enviado, una marca de
 *    visto pequeña encima de la foto y la burbuja rebota; si falla, «!» y una sacudida corta.
 *  - **Arrastrar** (más de `scaledTouchSlop`) mueve todas juntas y nunca envía. Al soltar se quedan **donde se
 *    sueltan** (sin imán al borde; solo se corrige lo justo para no salirse ni quedar bajo las barras del sistema),
 *    en **fila** si se sueltan en la franja de abajo y en columna en el resto ([BubbleLayout.drop]), y se avisa de
 *    la posición ([Listener.onMoved], en fracción de la pantalla).
 *  - Mientras se arrastra, abajo en el centro sale una zona «X»: soltarlas ahí las esconde ([Listener.onDismissed]).
 *  - **Mantener pulsada** una (≈ `longPressTimeout`, sin mover el dedo) la despega: crece con un muelle y su cristal
 *    se aclara, y vibra; las demás se quedan. Arrastrada encima de otra, esa crece un poco, su cristal se aclara y
 *    sale un «+» con un tic; al soltar se envía a las dos ([Listener.onPair]). Soltada en otro sitio, vuelve a la
 *    pila sin enviar. Sin zona «X».
 *    Lo decide [BubbleGesture] (puro); la despegada va en una ventana propia que no recibe toques.
 *  - Las marcas (enviando, enviado, fallo) las pone el servicio con [setStates] según [BubbleBoard].
 *
 * Solo desde el hilo principal.
 */
internal class QuickSendOverlay(
    private val service: AccessibilityService,
    private val listener: Listener,
) {

    interface Listener {
        fun onTap(name: String)

        /** [first] soltada encima de [second] (o la acción de TalkBack «Enviar a X y a B»): enviar a las dos. */
        fun onPair(first: String, second: String)

        /** Soltadas en otro sitio: guardar la posición. */
        fun onMoved(spot: BubbleSpot)

        /**
         * Soltadas en la «X» (o la acción de TalkBack «Esconder»): fuera hasta el siguiente reel. `false` si ahora
         * no se pueden esconder (enviando): entonces vuelven a su sitio.
         */
        fun onDismissed(): Boolean
    }

    enum class State { IDLE, SENDING, SENT, FAILED }

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop
    private val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val initialsFont: Typeface? = runCatching { ResourcesCompat.getFont(service, R.font.bricolage_grotesque) }.getOrNull()
    private val monoFont: Typeface? = runCatching { ResourcesCompat.getFont(service, R.font.jetbrains_mono) }.getOrNull()

    private var container: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var shownTargets: List<String> = emptyList()
    private var shownSpot: BubbleSpot? = null
    private var screenW = 0
    private var screenH = 0

    /** Columna o fila: sale de la altura de [shownSpot] ([BubbleLayout.orientationFor]). */
    private var orientation = BubbleOrientation.COLUMN

    /** Lo que la pila no pisa (barras del sistema), medido al ponerla. */
    private var safe = SafeArea(0, 0, 0, 0)
    private val bubbles = LinkedHashMap<String, BubbleView>()
    private val states = HashMap<String, State>()
    private var moveAnimator: ValueAnimator? = null

    private var zone: DismissZoneView? = null

    /** La burbuja despegada (mantener pulsada) y su ventana, que no recibe toques. */
    private var ghost: BubbleView? = null
    private var ghostParams: WindowManager.LayoutParams? = null
    private var ghostAnimator: ValueAnimator? = null
    private var lifted: BubbleView? = null
    private var hot: BubbleView? = null
    private var question: View? = null

    var isDragging = false
        private set

    val isShown: Boolean get() = container != null
    val isAsking: Boolean get() = question != null

    /**
     * Pone (o deja) las burbujas de [targets] en [spot]. Si ya estaban iguales, solo actualiza las fotos.
     * Mientras se arrastran no se mueven de donde está el dedo.
     */
    fun show(targets: List<String>, avatars: Map<String, Bitmap?>, spot: BubbleSpot, screenWidth: Int, screenHeight: Int) {
        if (container != null && targets == shownTargets && screenWidth == screenW && screenHeight == screenH) {
            targets.forEach { name -> bubbles[name]?.avatar = avatars[name] }
            if (spot != shownSpot && !isDragging && moveAnimator == null) place(spot)
            return
        }
        hide()
        if (targets.isEmpty() || windowManager == null || screenWidth <= 0 || screenHeight <= 0) return
        screenW = screenWidth
        screenH = screenHeight
        safe = BubbleLayout.safeArea(screenW, screenH, systemInsets(), px(Space.x1))
        val pad = px(Space.x1) // sitio para la sombra (y el ×1,08 de la de debajo) sin que la corte la ventana
        val layout = LinearLayout(service).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(pad, pad, pad, pad)
        }
        targets.forEachIndexed { i, name ->
            val bubble = BubbleView(service, name, px(VIEW_DP)).apply { avatar = avatars[name] }
            layout.addView(bubble, LinearLayout.LayoutParams(px(VIEW_DP), px(VIEW_DP)))
            bubbles[name] = bubble
            bindTouch(bubble, i)
            // TalkBack: una acción por cada otro fijado («Enviar a Ana y a Los del piso»).
            targets.filter { it != name }.forEach { other ->
                ViewCompat.addAccessibilityAction(bubble, QuickSendCopy.pairAction(name, other)) { _, _ ->
                    listener.onPair(name, other)
                    true
                }
            }
            render(name, animate = false)
        }
        orientation = BubbleLayout.orientationFor(BubbleLayout.sanitize(spot).y)
        layOut(layout, orientation)
        val (x, y) = windowXY(spot, targets.size, orientation)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
            title = QuickSendCopy.WINDOW_TITLE
        }
        try {
            windowManager.addView(layout, lp)
            container = layout
            params = lp
            shownTargets = targets
            shownSpot = spot
        } catch (e: RuntimeException) {
            bubbles.values.forEach { it.stopAnimations() }
            bubbles.clear()
        }
    }

    /**
     * Antes de recortar una foto de perfil: si la pila tapa [box] (coordenadas de pantalla), se vuelve invisible
     * para que no salga en la captura. `true` si se ha escondido (llama a [showAfterCapture] al acabar).
     */
    fun hideForCapture(box: NodeBounds): Boolean {
        val view = container ?: return false
        val lp = params ?: return false
        val (w, h) = stackSize(shownTargets.size, orientation)
        val overlaps = lp.x < box.right && box.left < lp.x + w && lp.y < box.bottom && box.top < lp.y + h
        // Con el dedo encima (una despegada o la zona «X» a la vista), puede pasar por la foto: fuera todo.
        val moving = ghost != null || zone != null || isDragging
        if (!overlaps && !moving) return false
        view.visibility = View.INVISIBLE
        ghost?.visibility = View.INVISIBLE
        zone?.visibility = View.INVISIBLE
        return true
    }

    fun showAfterCapture() {
        container?.visibility = View.VISIBLE
        ghost?.visibility = View.VISIBLE
        zone?.visibility = View.VISIBLE
    }

    fun hide() {
        // Pulsaciones largas pendientes de la pila que se quita (Android ya cancela el gesto; por si acaso).
        handler.removeCallbacksAndMessages(null)
        moveAnimator?.cancel()
        moveAnimator = null
        hideZone()
        dropGhost()
        bubbles.values.forEach { it.stopAnimations() }
        val view = container
        if (view != null) runCatching { windowManager?.removeViewImmediate(view) }
        container = null
        params = null
        isDragging = false
        shownTargets = emptyList()
        shownSpot = null
        bubbles.clear()
    }

    fun setAvatar(name: String, bitmap: Bitmap?) {
        bubbles[name]?.avatar = bitmap
    }

    fun setState(name: String, state: State) {
        val previous = states[name] ?: State.IDLE
        if (previous == state) return
        if (state == State.IDLE) states.remove(name) else states[name] = state
        render(name, animate = true)
    }

    /** Las marcas de [BubbleBoard]: cada burbuja pasa a la suya (con animación si cambia); las que no salen, normales. */
    fun setStates(marks: Map<String, State>) {
        bubbles.keys.toList().forEach { name -> setState(name, marks[name] ?: State.IDLE) }
        states.keys.filter { it !in bubbles }.forEach { states.remove(it) }
    }

    /** Todas vuelven a su insignia normal. */
    fun resetStates() {
        if (states.isEmpty()) return
        val names = states.keys.toList()
        states.clear()
        names.forEach { render(it, animate = false) }
    }

    // ── Pregunta tras esconderlas 3 veces ────────────────────────────────────────────────────────

    /** «¿Apago el envío rápido?», una sola vez. Se quita al contestar o con [hideQuestion]. */
    fun showQuestion(onTurnOff: () -> Unit, onKeep: () -> Unit) {
        if (question != null || windowManager == null) return
        val colors = DarkReelColors
        val panel = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(Space.x4), px(Space.x4), px(Space.x4), px(Space.x4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 0f
                setColor(colors.bg.toArgb())
                setStroke(px(Space.hairline).coerceAtLeast(1), colors.signal.toArgb())
            }
        }
        panel.addView(
            TextView(service).apply {
                text = QuickSendCopy.ASK_TITLE
                typeface = initialsFont ?: Typeface.DEFAULT_BOLD
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(colors.text.toArgb())
            },
        )
        panel.addView(
            TextView(service).apply {
                text = QuickSendCopy.ASK_BODY
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(colors.textMuted.toArgb())
                setPadding(0, px(Space.x2), 0, px(Space.x3))
            },
        )
        val buttons = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(label: String, filled: Boolean, onClick: () -> Unit) = TextView(service).apply {
            text = label
            typeface = monoFont ?: Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            minHeight = px(Space.touch)
            setPadding(px(Space.x4), 0, px(Space.x4), 0)
            setTextColor((if (filled) colors.onSignal else colors.text).toArgb())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 2.dp.value * density
                setColor((if (filled) colors.signal else colors.bg).toArgb())
                setStroke(px(Space.hairline).coerceAtLeast(1), (if (filled) colors.signal else colors.lineStrong).toArgb())
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                hideQuestion()
                onClick()
            }
        }
        buttons.addView(button(QuickSendCopy.ASK_TURN_OFF, filled = true, onTurnOff))
        buttons.addView(
            button(QuickSendCopy.ASK_KEEP, filled = false, onKeep),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = px(Space.x2) },
        )
        panel.addView(buttons)
        val lp = WindowManager.LayoutParams(
            (screenW - px(Space.x4) * 2).takeIf { it > 0 } ?: WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = px(QUESTION_BOTTOM_DP)
            title = QuickSendCopy.WINDOW_TITLE
        }
        try {
            windowManager.addView(panel, lp)
            question = panel
            panel.alpha = 0f
            panel.animate().alpha(1f).setDuration(FADE_MS).start()
        } catch (e: RuntimeException) {
            question = null
        }
    }

    fun hideQuestion() {
        val view = question ?: return
        question = null
        runCatching { windowManager?.removeViewImmediate(view) }
    }

    // ── Arrastre ─────────────────────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility") // El toque sin arrastre llama a performClick (y TalkBack usa el clic).
    private fun bindTouch(bubble: BubbleView, index: Int) {
        var startX = 0
        var startY = 0
        var overZone = false
        val gesture = BubbleGesture(touchSlop, longPressMs)
        val longPress = Runnable {
            // El mismo reloj que MotionEvent.eventTime.
            if (gesture.longPress(android.os.SystemClock.uptimeMillis())) lift(bubble)
        }
        bubble.setOnClickListener { view ->
            if (states[bubble.name] == State.SENDING) return@setOnClickListener
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            listener.onTap(bubble.name)
        }
        ViewCompat.addAccessibilityAction(bubble, QuickSendCopy.HIDE_ACTION) { _, _ -> listener.onDismissed() }
        bubble.setOnTouchListener { view, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Si aún iba hacia su sitio, termina de llegar (un toque no la deja a medio camino).
                    moveAnimator?.end()
                    moveAnimator = null
                    gesture.down(index, event.rawX, event.rawY, event.eventTime)
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    isDragging = false
                    overZone = false
                    bubble.press(true)
                    handler.removeCallbacks(longPress)
                    handler.postDelayed(longPress, longPressMs)
                }
                MotionEvent.ACTION_MOVE -> {
                    val changed = gesture.move(event.rawX, event.rawY, centers(), hitRadius())
                    when (gesture.mode) {
                        BubbleGesture.Mode.DRAG_STACK -> {
                            if (changed) {
                                handler.removeCallbacks(longPress)
                                isDragging = true
                                bubble.press(false)
                                showZone()
                            }
                            moveStack(event, startX, startY)
                            val (cx, cy) = stackCenter()
                            val over = BubbleLayout.inDismissZone(cx, cy, screenW, screenH, px(ZONE_HIT_DP))
                            if (over != overZone) {
                                overZone = over
                                zone?.setHot(over)
                                if (over) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                        BubbleGesture.Mode.LIFTED -> {
                            moveGhost(event.rawX, event.rawY)
                            if (changed) setHot(gesture.hover?.let { i -> bubbles.values.elementAtOrNull(i) })
                        }
                        else -> Unit
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    bubble.press(false)
                    val outcome = if (event.actionMasked == MotionEvent.ACTION_UP) {
                        gesture.up(event.rawX, event.rawY, centers(), hitRadius())
                    } else {
                        gesture.cancel()
                    }
                    when (outcome) {
                        is BubbleGesture.Outcome.Tap -> view.performClick()
                        BubbleGesture.Outcome.StackDropped -> {
                            isDragging = false
                            hideZone()
                            if (overZone && event.actionMasked == MotionEvent.ACTION_UP && listener.onDismissed()) {
                                // Escondidas: el servicio quita la ventana.
                            } else if (overZone && event.actionMasked == MotionEvent.ACTION_UP) {
                                animateTo(shownSpot ?: BubbleLayout.DEFAULT) // ahora no se pueden esconder: vuelven
                            } else {
                                dropStack()
                            }
                        }
                        is BubbleGesture.Outcome.Together -> {
                            val target = bubbles.values.elementAtOrNull(outcome.to)
                            landGhost(target)
                            if (target != null) listener.onPair(bubble.name, target.name)
                        }
                        is BubbleGesture.Outcome.Returned -> landGhost(null)
                        BubbleGesture.Outcome.None -> isDragging = false
                    }
                }
            }
            true
        }
    }

    /**
     * Pila soltada fuera de la «X»: se queda donde está (corregida solo lo justo) y, si a esa altura toca la otra
     * orientación, gira alrededor de su centro (cada burbuja con un pequeño muelle) antes de asentarse.
     */
    private fun dropStack() {
        val (cx, cy) = stackCenter()
        val spot = BubbleLayout.drop(cx, cy, screenW, screenH, safe, dismissRadius = px(ZONE_HIT_DP)) { stackSize(shownTargets.size, it) }
        shownSpot = spot
        val next = BubbleLayout.orientationFor(spot.y)
        val view = container
        if (next != orientation && view != null) {
            orientation = next
            layOut(view, next)
            val (w, h) = stackSize(shownTargets.size, next)
            moveWindow((cx - w / 2f).roundToInt(), (cy - h / 2f).roundToInt())
            bubbles.values.forEach { it.pop() }
        }
        animateTo(spot)
        listener.onMoved(spot)
    }

    /** Columna o fila, con [Space.x1] entre burbujas. */
    private fun layOut(layout: LinearLayout, o: BubbleOrientation) {
        layout.orientation = if (o == BubbleOrientation.ROW) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        for (i in 0 until layout.childCount) {
            val lp = layout.getChildAt(i).layoutParams as LinearLayout.LayoutParams
            val gap = if (i > 0) px(Space.x1) else 0
            lp.topMargin = if (o == BubbleOrientation.COLUMN) gap else 0
            lp.marginStart = if (o == BubbleOrientation.ROW) gap else 0
            layout.getChildAt(i).layoutParams = lp
        }
    }

    /** Mueve la pila entera con el dedo (arrastre normal). */
    private fun moveStack(event: MotionEvent, startX: Int, startY: Int) {
        val dx = event.rawX - downRawX
        val dy = event.rawY - downRawY
        moveWindow(startX + dx.roundToInt(), startY + dy.roundToInt())
    }

    private var downRawX = 0f
    private var downRawY = 0f

    /** Centro de cada burbuja de la pila, en px de pantalla (para saber sobre cuál cae la despegada). */
    private fun centers(): List<Pair<Float, Float>> {
        val lp = params ?: return emptyList()
        return bubbles.values.map { b -> lp.x + b.left + b.width / 2f to lp.y + b.top + b.height / 2f }
    }

    private fun hitRadius(): Float = px(VIEW_DP) * HIT_FRACTION

    // ── Mantener y soltar encima ─────────────────────────────────────────────────────────────────

    /** Despega [bubble]: una copia en su propia ventana crece con un muelle; la de la pila queda como hueco. */
    private fun lift(bubble: BubbleView) {
        val lp = params ?: return
        // Solo una burbuja de la pila que está a la vista (no la de una pila ya quitada).
        if (windowManager == null || ghost != null || bubbles[bubble.name] !== bubble || !bubble.isAttachedToWindow) return
        isDragging = true
        bubble.press(false)
        // Su ventana es algo más grande que la burbuja para que al crecer (×1,15) no se corte la sombra.
        val size = px(GHOST_DP)
        val copy = BubbleView(service, bubble.name, size, lifted = true).apply {
            avatar = bubble.avatar
            show(states[bubble.name] ?: State.IDLE, animate = false)
        }
        val glp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lp.x + bubble.left - ghostInset()
            y = lp.y + bubble.top - ghostInset()
            title = QuickSendCopy.WINDOW_TITLE
        }
        try {
            windowManager.addView(copy, glp)
        } catch (e: RuntimeException) {
            isDragging = false
            return
        }
        ghost = copy
        ghostParams = glp
        lifted = bubble
        bubble.alpha = LIFTED_HOLE_ALPHA
        copy.animate().scaleX(LIFT_SCALE).scaleY(LIFT_SCALE).setDuration(SNAP_MS).setInterpolator(Spring(damping = 0.5f)).start()
        copy.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** La despegada sigue al dedo (centrada en él). */
    private fun moveGhost(rawX: Float, rawY: Float) {
        val view = ghost ?: return
        val glp = ghostParams ?: return
        val half = px(GHOST_DP) / 2
        glp.x = (rawX.roundToInt() - half).coerceIn(0, (screenW - half * 2).coerceAtLeast(0))
        glp.y = (rawY.roundToInt() - half).coerceIn(0, (screenH - half * 2).coerceAtLeast(0))
        runCatching { windowManager?.updateViewLayout(view, glp) }
    }

    /** Lo que sobresale la ventana de la despegada por cada lado respecto a una burbuja de la pila. */
    private fun ghostInset(): Int = (px(GHOST_DP) - px(VIEW_DP)) / 2

    /** La burbuja de debajo se ilumina (crece, cristal más claro y «+») con un tic; la anterior se apaga. */
    private fun setHot(bubble: BubbleView?) {
        if (hot === bubble) return
        hot?.setHot(false)
        hot = bubble
        bubble?.setHot(true)
        if (bubble != null) bubble.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Suelta la despegada: encima de [target] (va hacia ella) o de vuelta a su hueco, con un muelle. */
    private fun landGhost(target: BubbleView?) {
        val view = ghost
        val glp = ghostParams
        val home = lifted
        val lp = params
        if (view == null || glp == null || home == null || lp == null) {
            dropGhost()
            return
        }
        val toX = lp.x + (target ?: home).left - ghostInset()
        val toY = lp.y + (target ?: home).top - ghostInset()
        val fromX = glp.x
        val fromY = glp.y
        ghostAnimator?.cancel()
        view.animate().scaleX(if (target != null) 0.6f else 1f).scaleY(if (target != null) 0.6f else 1f).setDuration(SNAP_MS)
            .setInterpolator(Spring(damping = 0.62f)).start()
        ghostAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (target != null) PRESS_MS * 2 else SNAP_MS
            interpolator = Spring(damping = 0.62f)
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                glp.x = fromX + ((toX - fromX) * t).roundToInt()
                glp.y = fromY + ((toY - fromY) * t).roundToInt()
                runCatching { windowManager?.updateViewLayout(view, glp) }
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (ghostAnimator === animation) dropGhost()
                    }
                },
            )
            start()
        }
    }

    /** Quita la despegada sin animación y deja la pila como estaba. */
    private fun dropGhost() {
        ghostAnimator?.cancel()
        ghostAnimator = null
        ghost?.let { view ->
            view.stopAnimations()
            runCatching { windowManager?.removeViewImmediate(view) }
        }
        ghost = null
        ghostParams = null
        lifted?.alpha = 1f
        lifted = null
        hot?.setHot(false)
        hot = null
        isDragging = false
    }

    private fun stackCenter(): Pair<Float, Float> {
        val lp = params ?: return 0f to 0f
        val (w, h) = stackSize(shownTargets.size, orientation)
        return lp.x + w / 2f to lp.y + h / 2f
    }

    private fun moveWindow(x: Int, y: Int) {
        val lp = params ?: return
        val view = container ?: return
        val (w, h) = stackSize(shownTargets.size, orientation)
        lp.x = x.coerceIn(0, (screenW - w).coerceAtLeast(0))
        lp.y = y.coerceIn(0, (screenH - h).coerceAtLeast(0))
        runCatching { windowManager?.updateViewLayout(view, lp) }
    }

    /** Otra posición guardada (p. ej. RECOLOCAR): salta ahí, en la orientación que le toca. */
    private fun place(spot: BubbleSpot) {
        shownSpot = spot
        val next = BubbleLayout.orientationFor(BubbleLayout.sanitize(spot).y)
        val view = container
        if (next != orientation && view != null) {
            orientation = next
            layOut(view, next)
        }
        val (x, y) = windowXY(spot, shownTargets.size, orientation)
        moveWindow(x, y)
    }

    /** A su sitio con un muelle (con las animaciones del sistema apagadas, salta). */
    private fun animateTo(spot: BubbleSpot) {
        val lp = params ?: return
        val (toX, toY) = windowXY(spot, shownTargets.size, orientation)
        val fromX = lp.x
        val fromY = lp.y
        moveAnimator?.cancel()
        moveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SNAP_MS
            interpolator = Spring(damping = 0.62f)
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                moveWindow(fromX + ((toX - fromX) * t).roundToInt(), fromY + ((toY - fromY) * t).roundToInt())
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (moveAnimator === animation) moveAnimator = null
                    }
                },
            )
            start()
        }
    }

    private fun windowXY(spot: BubbleSpot, count: Int, o: BubbleOrientation): Pair<Int, Int> {
        val (w, h) = stackSize(count, o)
        return BubbleLayout.windowXY(spot, w, h, screenW, screenH, safe)
    }

    /** Ancho y alto de la ventana de la pila (con el margen de [Space.x1] alrededor para la sombra). */
    private fun stackSize(count: Int, o: BubbleOrientation): Pair<Int, Int> {
        val (w, h) = BubbleLayout.stackSize(count, px(VIEW_DP), px(Space.x1), o)
        val pad = if (count > 0) px(Space.x1) * 2 else 0
        return w + pad to h + pad
    }

    /**
     * Las barras del sistema y el recorte de la cámara, en px, aunque ahora estén escondidas (Instagram esconde la
     * de estado en los reels). `null` si no se pueden saber (Android 10 o menos): entonces van márgenes en fracción.
     */
    private fun systemInsets(): SafeArea? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val wm = windowManager ?: return null
        return runCatching {
            val insets = wm.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            SafeArea(insets.left, insets.top, insets.right, insets.bottom)
        }.getOrNull()?.takeIf { it.top > 0 || it.bottom > 0 || it.left > 0 || it.right > 0 }
    }

    private fun showZone() {
        if (zone != null || windowManager == null) return
        val view = DismissZoneView(service)
        val size = px(ZONE_DP)
        val (cx, cy) = BubbleLayout.dismissCenter(screenW, screenH)
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = cx - size / 2
            y = cy - size / 2
            title = QuickSendCopy.ZONE_TITLE
        }
        try {
            windowManager.addView(view, lp)
            zone = view
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(FADE_MS).start()
        } catch (e: RuntimeException) {
            zone = null
        }
    }

    private fun hideZone() {
        val view = zone ?: return
        zone = null
        runCatching { windowManager?.removeViewImmediate(view) }
    }

    // ── Estados ──────────────────────────────────────────────────────────────────────────────────

    private fun render(name: String, animate: Boolean) {
        val bubble = bubbles[name] ?: return
        val state = states[name] ?: State.IDLE
        bubble.show(state, animate)
        bubble.contentDescription = when (state) {
            State.IDLE -> QuickSendCopy.bubbleDescription(name)
            State.SENDING -> QuickSendCopy.sendingDescription(name)
            State.SENT -> QuickSendCopy.sentDescription(name)
            State.FAILED -> QuickSendCopy.failedDescription(name)
        }
        if (animate && state == State.SENT) {
            val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            bubble.performHapticFeedback(confirm)
        }
    }

    private fun px(dp: Dp): Int = (dp.value * density + 0.5f).toInt()

    private fun px(dp: Float): Int = (dp * density + 0.5f).toInt()

    // ── Vistas ───────────────────────────────────────────────────────────────────────────────────

    /**
     * Una burbuja de cristal de [side] px de lado (la zona táctil), con el círculo de [GlassBubbleArt.CIRCLE_DP]
     * en el centro. El dibujo es [GlassBubbleArt]; aquí van los muelles y las animaciones. [lifted]: la despegada.
     */
    private inner class BubbleView(context: Context, val name: String, private val side: Int, private val lifted: Boolean = false) : View(context) {

        var avatar: Bitmap? = null
            set(value) {
                if (field === value) return
                field = value
                invalidate()
            }

        private var state = State.IDLE

        /** Otra burbuja despegada está encima: crece, cristal más claro y «+» (al soltar, se envía a las dos). */
        private var hotNow = false
        private var spin = 0f
        private var spinner: ValueAnimator? = null
        private val initials = AvatarRules.initials(name)
        private val art = GlassBubbleArt(initialsFont)
        private val radius = px(GlassBubbleArt.CIRCLE_DP) / 2f

        fun setHot(value: Boolean) {
            if (hotNow == value) return
            hotNow = value
            animate().scaleX(if (value) HOT_SCALE else 1f).scaleY(if (value) HOT_SCALE else 1f)
                .setDuration(PRESS_MS).setInterpolator(Spring(damping = 0.55f)).start()
            invalidate()
        }

        init {
            isClickable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = QuickSendCopy.bubbleDescription(name)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(side, side)
        }

        fun show(next: State, animate: Boolean) {
            val previous = state
            state = next
            if (next == State.SENDING) startSpin() else stopSpin()
            if (animate && next == State.SENT && previous != State.SENT) bounce()
            if (animate && next == State.FAILED) shake()
            invalidate()
        }

        fun press(down: Boolean) {
            animate().scaleX(if (down) PRESSED_SCALE else 1f).scaleY(if (down) PRESSED_SCALE else 1f)
                .setDuration(if (down) PRESS_MS else SNAP_MS)
                .setInterpolator(if (down) null else Spring(damping = 0.5f))
                .start()
        }

        /** Al cambiar de columna a fila (o al revés): encoge un poco y vuelve con un muelle. */
        fun pop() {
            scaleX = POP_SCALE
            scaleY = POP_SCALE
            animate().scaleX(1f).scaleY(1f).setDuration(SNAP_MS).setInterpolator(Spring(damping = 0.5f)).start()
        }

        fun stopAnimations() {
            stopSpin()
            animate().cancel()
        }

        private fun startSpin() {
            if (spinner != null) return
            spinner = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = SPIN_MS
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    spin = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        private fun stopSpin() {
            spinner?.cancel()
            spinner = null
        }

        private fun bounce() {
            scaleX = 1f
            scaleY = 1f
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = SNAP_MS
                addUpdateListener {
                    val t = it.animatedFraction
                    // Crece un poco y vuelve con un muelle.
                    val s = 1f + BOUNCE * sin(Math.PI * t).toFloat() * exp(-2.2f * t)
                    scaleX = s
                    scaleY = s
                }
                start()
            }
        }

        private fun shake() {
            val amp = px(SHAKE_DP).toFloat()
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = SHAKE_MS
                addUpdateListener {
                    val t = it.animatedFraction
                    translationX = (amp * sin(t * Math.PI * 6).toFloat() * (1f - t))
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            translationX = 0f
                        }
                    },
                )
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            val mark = when (state) {
                State.IDLE -> GlassBubbleArt.Mark.NONE
                State.SENDING -> GlassBubbleArt.Mark.SENDING
                State.SENT -> GlassBubbleArt.Mark.SENT
                State.FAILED -> GlassBubbleArt.Mark.FAILED
            }
            art.draw(canvas, width / 2f, height / 2f, radius, avatar, initials, mark, spin, hot = hotNow, lifted = lifted)
        }
    }

    /** La zona «X» de abajo: un círculo con una equis; encendida cuando la pila está encima. */
    private inner class DismissZoneView(context: Context) : View(context) {
        private var hot = false
        private val colors = DarkReelColors
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = px(Space.hairline).coerceAtLeast(1).toFloat() * 2
        }

        init {
            contentDescription = QuickSendCopy.ZONE_TITLE
        }

        fun setHot(value: Boolean) {
            if (hot == value) return
            hot = value
            animate().scaleX(if (value) ZONE_HOT_SCALE else 1f).scaleY(if (value) ZONE_HOT_SCALE else 1f)
                .setDuration(PRESS_MS).setInterpolator(Spring(damping = 0.55f)).start()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val r = width / 2f
            val inner = r / ZONE_HOT_SCALE
            fill.color = (if (hot) colors.signal else colors.bg).toArgb()
            canvas.drawCircle(r, r, inner - stroke.strokeWidth, fill)
            stroke.color = (if (hot) colors.signal else colors.lineStrong).toArgb()
            canvas.drawCircle(r, r, inner - stroke.strokeWidth, stroke)
            stroke.color = (if (hot) colors.onSignal else colors.text).toArgb()
            val a = inner * 0.32f
            canvas.drawLine(r - a, r - a, r + a, r + a, stroke)
            canvas.drawLine(r - a, r + a, r + a, r - a, stroke)
        }
    }

    /**
     * Muelle amortiguado (como los `spring` de `ui/theme/Motion`): llega a 1 pasándose un poco y se asienta.
     * [damping] < 1 rebota; con 0,6 apenas se nota.
     */
    private class Spring(private val damping: Float, private val stiffness: Float = 11f) : TimeInterpolator {
        override fun getInterpolation(input: Float): Float {
            if (input >= 1f) return 1f
            val wd = stiffness * sqrt(1 - damping * damping)
            val decay = exp(-damping * stiffness * input)
            return 1f - decay * (cos(wd * input) + (damping * stiffness / wd) * sin(wd * input))
        }
    }

    private companion object {
        /** Lado de cada burbuja = su zona táctil (48 dp); el círculo de cristal mide 42 dp ([GlassBubbleArt]). */
        const val VIEW_DP = GlassBubbleArt.TOUCH_DP

        /** Ventana de la despegada: cabe crecida (×1,15) con su sombra. */
        const val GHOST_DP = 60f

        const val ZONE_DP = 72f
        const val ZONE_HIT_DP = 72f
        const val ZONE_HOT_SCALE = 1.18f
        const val QUESTION_BOTTOM_DP = 96f

        const val PRESSED_SCALE = 0.9f
        const val BOUNCE = 0.22f
        const val SHAKE_DP = 6f

        /** Al girar de columna a fila (o al revés), cada burbuja sale de este tamaño y vuelve con un muelle. */
        const val POP_SCALE = 0.86f

        const val PRESS_MS = 90L
        const val SNAP_MS = 420L
        const val SHAKE_MS = 320L
        const val SPIN_MS = 900L

        /** La despegada crece así; su hueco en la pila se queda con esta opacidad. */
        const val LIFT_SCALE = 1.15f
        const val LIFTED_HOLE_ALPHA = 0.3f

        /** La de debajo, iluminada: un poco más grande (el cristal más claro y grueso lo pone [GlassBubbleArt]). */
        const val HOT_SCALE = 1.08f

        /** A esta parte del lado de una burbuja desde su centro, la despegada ya está encima. */
        const val HIT_FRACTION = 0.75f
        const val FADE_MS = 150L
    }
}
