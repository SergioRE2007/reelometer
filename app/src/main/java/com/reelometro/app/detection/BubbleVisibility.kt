package com.reelometro.app.detection

/**
 * Cuándo las burbujas del envío rápido tienen algo encima que no es una hoja en otra ventana (1.10.5, lógica pura sin
 * Android; la lee el servicio con [screenWindows] y [feedPanelOpen] y lo apunta en [BubbleBoard]):
 *  - [SystemShade]: la barra de notificaciones o los ajustes rápidos bajados encima de la app.
 *  - [FeedPanel]: un panel (los comentarios de TikTok) que tapa el visor en la **misma ventana** del feed.
 *  - [SheetWatch]: cada cuánto se mira, hasta cuándo, y qué hacer con un clic en el visor.
 */

/** Una ventana de la pantalla tal como la ve el servicio (de `AccessibilityWindowInfo`); tamaño en px. */
data class ScreenWindow(
    val id: Int,
    val kind: Kind,
    val layer: Int,
    val width: Int,
    val height: Int,
    val focused: Boolean,
    val active: Boolean,
) {
    /** `TYPE_APPLICATION` → [APP], `TYPE_SYSTEM` → [SYSTEM]; teclado, capas de accesibilidad, divisor… → [OTHER]. */
    enum class Kind { APP, SYSTEM, OTHER }
}

/** ¿Está bajada la barra de notificaciones (o los ajustes rápidos, o la pantalla de bloqueo) encima de la app? */
object SystemShade {

    /** Lo que tiene que ocupar la ventana del sistema, en fracción del ancho y del alto de la pantalla. */
    const val MIN_FRACTION = 0.5f

    /**
     * `true` si en [windows] hay una ventana del sistema que ocupa al menos [MIN_FRACTION] de ancho y de alto, va por encima
     * de todas las de las apps (o no hay ninguna de app) y tiene el foco o está activa (o ninguna ventana de app tiene el
     * foco). Lo del foco es la salvaguarda: una notificación emergente o una capa del sistema que no se toca no le quitan
     * el foco a la app, así que no esconden nada. Con la barra desplegada, SystemUI se vuelve enfocable (Android 12+: la
     * `NotificationShade`; Android 8–10: la `StatusBar`, ya a pantalla completa). Sin tamaño de pantalla, `false`.
     */
    fun covers(windows: List<ScreenWindow>, screenWidth: Int, screenHeight: Int): Boolean {
        if (screenWidth <= 0 || screenHeight <= 0) return false
        val apps = windows.filter { it.kind == ScreenWindow.Kind.APP }
        val topApp = apps.maxOfOrNull { it.layer }
        val appFocused = apps.any { it.focused }
        return windows.any { w ->
            w.kind == ScreenWindow.Kind.SYSTEM &&
                w.width >= screenWidth * MIN_FRACTION && w.height >= screenHeight * MIN_FRACTION &&
                (topApp == null || w.layer > topApp) &&
                (w.focused || w.active || !appFocused)
        }
    }
}

/**
 * ¿Hay un panel (los comentarios de TikTok, que van en la misma ventana del feed) tapando el visor? Solo con ids y
 * geometría, sin leer ningún texto: vale en cualquier idioma.
 *
 * El visor es el nodo **más profundo** visible, de ancho completo, que cae en la pantalla y cuyo id corto es uno de
 * los del feed ([PlatformProfile.feedIds]; en TikTok 47.7, el `viewpager` de dentro, no el de las pestañas, que envuelve
 * casi toda la interfaz). Hay panel si:
 *  - (a) fuera del visor (ni dentro de él ni uno de sus antepasados) hay un nodo visible de ancho completo
 *    ([FULL_WIDTH]) que empieza entre [MIN_TOP] y [MAX_TOP] del alto, llega abajo ([MIN_BOTTOM]), mide al menos
 *    [MIN_HEIGHT] y tiene algo dentro (una hoja de abajo, aunque aún esté subiendo); o
 *  - (b) el visor está encogido arriba ([SHRUNK_TOP], [SHRUNK_BOTTOM]): por si la app encoge el vídeo al abrirlos (sin
 *    comprobar en el móvil).
 *
 * Con [relaxed] (ya se vio el panel en esta hoja), (a) no exige que llegue abajo y le basta [RELAXED_MIN_HEIGHT]: al escribir un
 * comentario, con el teclado, la hoja se encoge por encima de él (el teclado no es una ventana de la app).
 *
 * Solo se baja a los hijos de los nodos de ancho completo (una hoja de abajo y todos sus antepasados lo son): en el
 * volcado real del feed de TikTok 47.7 son ~100 nodos. Cada `children` puede ser una llamada entre procesos.
 */
object FeedPanel {

    const val MAX_NODES = 300
    const val MAX_DEPTH = 40
    const val FULL_WIDTH = 0.90f
    const val MIN_TOP = 0.10f
    const val MAX_TOP = 0.70f
    const val MIN_BOTTOM = 0.85f
    const val MIN_HEIGHT = 0.25f
    const val RELAXED_MIN_HEIGHT = 0.15f
    const val SHRUNK_TOP = 0.15f
    const val SHRUNK_BOTTOM = 0.60f

    /**
     * `true`: un panel tapa el visor en su misma ventana; `false`: el visor está a la vista y no hay nada así encima;
     * `null`: no se sabe (sin visor en [root], sin tamaño de pantalla o más de [maxNodes] nodos que mirar).
     */
    fun isOpen(
        root: UiNode,
        feedIds: List<String>,
        screenWidth: Int,
        screenHeight: Int,
        maxNodes: Int = MAX_NODES,
        relaxed: Boolean = false,
    ): Boolean? {
        if (screenWidth <= 0 || screenHeight <= 0) return null
        val ids = feedIds.map { it.lowercase() }
        val minWidth = screenWidth * FULL_WIDTH
        val nodes = ArrayList<UiNode>()
        val parents = ArrayList<Int>()
        val depths = ArrayList<Int>()
        val hasChildren = ArrayList<Boolean>()
        // Recorrido iterativo (nodo, índice del padre, profundidad).
        val stack = ArrayDeque<Triple<UiNode, Int, Int>>()
        stack.addLast(Triple(root, -1, 0))
        while (stack.isNotEmpty()) {
            val (node, parent, depth) = stack.removeLast()
            if (nodes.size >= maxNodes) return null
            val index = nodes.size
            nodes += node
            parents += parent
            depths += depth
            if (depth < MAX_DEPTH && onScreenWidth(node.bounds, screenWidth) >= minWidth) {
                val children = node.children
                hasChildren += children.isNotEmpty()
                for (i in children.indices.reversed()) stack.addLast(Triple(children[i], index, depth + 1))
            } else {
                hasChildren += false
            }
        }

        var viewer = -1
        for (i in nodes.indices) {
            val n = nodes[i]
            if (shortViewId(n.viewId) !in ids || !n.isVisible) continue
            if (onScreenWidth(n.bounds, screenWidth) < minWidth || !n.bounds.intersectsScreen(screenWidth, screenHeight)) continue
            if (viewer < 0 || depths[i] > depths[viewer]) viewer = i
        }
        if (viewer < 0) return null

        val v = nodes[viewer].bounds
        if (v.top <= screenHeight * SHRUNK_TOP && v.bottom <= screenHeight * SHRUNK_BOTTOM) return true

        val related = HashSet<Int>()
        var up = viewer
        while (up >= 0) {
            related += up
            up = parents[up]
        }
        for (i in nodes.indices) {
            if (i in related || !hasChildren[i]) continue
            val n = nodes[i]
            val b = n.bounds
            if (!n.isVisible || onScreenWidth(b, screenWidth) < minWidth) continue
            if (b.top < screenHeight * MIN_TOP || b.top > screenHeight * MAX_TOP) continue
            if (relaxed) {
                if (b.height < screenHeight * RELAXED_MIN_HEIGHT || b.bottom <= v.top) continue
            } else if (b.bottom < screenHeight * MIN_BOTTOM || b.height < screenHeight * MIN_HEIGHT) {
                continue
            }
            if (isInside(i, viewer, parents)) continue
            return true
        }
        return false
    }

    /** Lo que se ve de [b] a lo ancho dentro de una pantalla de [screenWidth] px. */
    private fun onScreenWidth(b: NodeBounds, screenWidth: Int): Int = minOf(b.right, screenWidth) - maxOf(b.left, 0)

    private fun isInside(index: Int, ancestor: Int, parents: List<Int>): Boolean {
        var up = parents[index]
        while (up >= 0) {
            if (up == ancestor) return true
            up = parents[up]
        }
        return false
    }
}

/**
 * Cómo se vigila una hoja abierta a mano en las apps cuya hoja va en su propia ventana (TikTok), y qué hacer con un clic
 * en el visor (1.10.5). Antes estaba suelto en el servicio (1.10.1–1.10.2: [SHEET_POLL_MS], [SHEET_WATCH_MS]).
 */
object SheetWatch {

    /** Con una ventana encima del feed, se vuelve a mirar cada [SHEET_POLL_MS] hasta [SHEET_WATCH_MS]. */
    const val SHEET_POLL_MS = 500L
    const val SHEET_WATCH_MS = 8_000L

    /** Con un panel en la ventana del feed (comentarios), cada [PANEL_POLL_MS] hasta [PANEL_WATCH_MS]. */
    const val PANEL_POLL_MS = 1_000L
    const val PANEL_WATCH_MS = 600_000L

    /**
     * Una hoja que no es la de compartir ([BubbleBoard.SheetKind.OTHER]) no se da por cerrada porque no se vea el panel hasta
     * pasado este tiempo desde que se abrió (o hasta haberlo visto): los comentarios pueden tardar en subir o en entrar en el
     * árbol, y cualquier cambio de ventana de TikTok vuelve a lanzar la vigilancia con su primera mirada a los 500 ms.
     */
    const val PANEL_GRACE_MS = 1_500L

    /** El «antes» de [probeOpened] leído más tarde que esto tras el clic puede ser ya el «después» ([probeBefore]). */
    const val PROBE_FRESH_MS = 100L

    /** Un clic en el visor con una hoja abierta: se mira pasado este tiempo si sigue algo encima. */
    const val VERIFY_CLICK_MS = 400L

    /** Un clic en la columna de botones sin etiqueta conocida: se mira pasado este tiempo si ha salido algo. */
    const val PROBE_MS = 700L

    /** Lo que se ve encima del feed: otra ventana de la app ([SheetWindows.sheetAbove]) y un panel en la suya ([FeedPanel]). */
    data class Look(val windowAbove: Boolean?, val panel: Boolean?)

    sealed interface Step {
        /** No queda nada encima: la hoja se ha cerrado. */
        data object Gone : Step

        /** Se deja de mirar (se queda como está hasta que pase otra cosa: mover el visor, otro reel, otro cambio de ventana). */
        data object GiveUp : Step

        /** Volver a mirar dentro de [ms]. */
        data class Wait(val ms: Long) : Step
    }

    /** ¿Se mira [FeedPanel] con una hoja de [kind]? Solo con la que no es la de compartir: la «X» de esa las devuelve siempre. */
    fun usesPanel(kind: BubbleBoard.SheetKind?): Boolean = kind == BubbleBoard.SheetKind.OTHER

    /**
     * Qué hacer tras mirar [look] con una hoja de [kind] abierta hace [sinceOpened] ms, con [waited] ms mirando, [panelSeen]
     * si ya se vio el panel en esta hoja y [unknownIsGone] si un «no se sabe» de las ventanas cuenta como cerrada (tras tocar
     * el visor, como hasta la 1.10.4). Con [kind] que no mira el panel ([usesPanel]), [Look.panel] no cuenta.
     *
     * Con el panel ([usesPanel]): no se da por cerrada sin haberlo visto antes de [PANEL_GRACE_MS]; tras verlo, un «no se
     * sabe» del panel (una lectura que falla) mantiene lo de antes. El tope largo ([PANEL_WATCH_MS]) solo con el panel a la
     * vista o una ventana encima de él; sin saber nada, el corto ([SHEET_WATCH_MS]).
     */
    fun next(
        kind: BubbleBoard.SheetKind?,
        look: Look,
        waited: Long,
        panelSeen: Boolean,
        unknownIsGone: Boolean = false,
        sinceOpened: Long = Long.MAX_VALUE,
    ): Step {
        val panelRule = usesPanel(kind)
        val panel = if (panelRule) look.panel else false
        if (panel == true) return if (waited >= PANEL_WATCH_MS) Step.GiveUp else Step.Wait(PANEL_POLL_MS)
        val early = panelRule && !panelSeen && sinceOpened < PANEL_GRACE_MS
        when (look.windowAbove) {
            false -> when {
                early -> return Step.Wait(SHEET_POLL_MS)
                panelRule && panelSeen && panel == null -> return if (waited >= PANEL_WATCH_MS) Step.GiveUp else Step.Wait(PANEL_POLL_MS)
                else -> return Step.Gone
            }
            null -> if (unknownIsGone && !early) return Step.Gone
            true -> Unit
        }
        val limit = if (look.windowAbove == true && panelSeen) PANEL_WATCH_MS else SHEET_WATCH_MS
        return if (waited >= limit) Step.GiveUp else Step.Wait(SHEET_POLL_MS)
    }

    enum class ClickAction {
        /** Abre una hoja (compartir, comentarios…): fuera. */
        OPENED,

        /** Había una hoja (en la misma ventana, Instagram): tocar el visor la cierra. */
        CLOSED,

        /** Había una hoja (TikTok): se comprueba en un momento si sigue algo encima ([VERIFY_CLICK_MS]). */
        VERIFY,

        /** No había hoja (TikTok): se mira si el clic ha abierto algo que no se reconoce por su etiqueta ([PROBE_MS]). */
        PROBE,

        NONE,
    }

    /** Un clic dentro del visor: [opensSheet] si se reconoce que abre una hoja, [sheetOpen] si ya había una. */
    fun onViewerClick(opensSheet: Boolean, sheetOpen: Boolean, sheetOwnWindow: Boolean): ClickAction = when {
        opensSheet -> ClickAction.OPENED
        !sheetOwnWindow -> if (sheetOpen) ClickAction.CLOSED else ClickAction.NONE
        sheetOpen -> ClickAction.VERIFY
        else -> ClickAction.PROBE
    }

    /**
     * ¿Merece la pena mirar si un clic en el visor ha abierto algo ([ClickAction.PROBE])? En la columna de botones
     * ([inActionColumn]) o en la franja de abajo con algo que no ocupa medio visor: el adelanto de un comentario sobre la
     * descripción, la barra «Añadir comentario…» de la vista de detalle. Un toque en el vídeo (un nodo alto) no gasta nada.
     */
    fun worthProbing(inActionColumn: Boolean, bounds: NodeBounds, screenWidth: Int, screenHeight: Int): Boolean {
        if (inActionColumn) return true
        if (screenWidth <= 0 || screenHeight <= 0 || bounds.isEmpty) return false
        return bounds.height <= screenHeight * PROBE_MAX_HEIGHT && bounds.centerY >= screenHeight * PROBE_MIN_CENTER_Y
    }

    const val PROBE_MAX_HEIGHT = 0.25f
    const val PROBE_MIN_CENTER_Y = 0.50f

    /**
     * El «antes» de [probeOpened]: [read] si se leyó a tiempo ([readLate] ms tras el clic, hasta [PROBE_FRESH_MS]) o si no hay
     * otra cosa; si se leyó tarde (el hilo de lectura estaba ocupado) y hay [baseline] (lo último visto sin hoja en este reel),
     * esa: para entonces lo que abrió el clic puede estar ya ahí y el antes y el después saldrían iguales.
     */
    fun probeBefore(read: Look, readLate: Long, baseline: Look?): Look =
        if (readLate > PROBE_FRESH_MS && baseline != null) baseline else read

    /**
     * Tras un clic sin etiqueta conocida: ¿ha salido algo que antes no estaba? Se compara con lo de antes del clic para que
     * algo que siempre encaje por error en [FeedPanel] nunca esconda nada.
     */
    fun probeOpened(before: Look, after: Look): Boolean =
        (before.windowAbove == false && after.windowAbove == true) || (before.panel == false && after.panel == true)
}
