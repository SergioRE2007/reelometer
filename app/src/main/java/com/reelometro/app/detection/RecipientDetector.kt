package com.reelometro.app.detection

import com.reelometro.app.data.Platform

/**
 * «A quién envías» (Instagram y, desde la 1.10.0, TikTok; solo con el ajuste encendido): lee en la hoja de
 * compartir el nombre de la persona o el grupo al que mandas un vídeo.
 *
 * Funciona por sesiones. Una sesión se abre ([start], con el [PlatformProfile] de la app) justo después de que
 * [ShareDetector] detecte «compartir» y se cierra ([stop]) cuando el feed vuelve a moverse, al enviar con el botón
 * de abajo o, como mucho, a los [Config.sessionMaxMs]. Fuera de una sesión no se mira ningún clic.
 *
 * Dentro de la sesión, cada `TYPE_VIEW_CLICKED` de **Instagram** se clasifica así:
 *  1. Campo de texto (buscador, «Escribe un mensaje») o algo dentro del visor de Reels: se ignora.
 *  2. **Enviar** (el propio botón o un hijo directo dice «Enviar»/«Send»/«Enviar por separado»):
 *     - con personas marcadas → se envía a todas y la sesión se cierra (la hoja se cierra sola);
 *     - sin marcadas → es el «Enviar» de una fila (diseño antiguo de la hoja): el nombre de esa fila.
 *  3. **Deshacer** (el «Enviar» de una fila se convierte en «Deshacer»): se anula el último envío a
 *     ese nombre.
 *  4. Otra opción de la hoja (copiar enlace, añadir a la historia, WhatsApp, buscar…): se ignora.
 *  5. Si no, es una persona o grupo: el primer texto con pinta de nombre de lo pulsado (o de su fila)
 *     se marca, o se desmarca si ya lo estaba.
 *
 * En **TikTok** (la hoja va en su propia ventana; ver el KDoc de [PlatformProfile]), [onTikTokClick]:
 *  1. Campo de texto (buscador, «Escribe un mensaje…») o algo del visor ([PlatformProfile.feedIds]): se ignora.
 *  2. **Enviar** (texto exacto o [PlatformProfile.sendIds], `tv_send`): con marcados → enviado a todos y la sesión
 *     se cierra (la hoja de TikTok se cierra al momento); sin marcados → nada (TikTok no tiene «Enviar» por fila).
 *  3. **Emojis de reacción** (celdas de un `GridView` debajo de «Escribe un mensaje…»): se ignoran. No se sabe si
 *     mandan el vídeo con ese emoji, así que no se apuntan.
 *  4. **Opciones** ([PlatformProfile.sheetOptions]: «Compartir» —que es repostear—, WhatsApp, Copiar enlace,
 *     Denunciar…, la lupa, «Cerrar»): se ignoran.
 *  5. **Celda de la fila de contactos** (lo pulsado o un antepasado pulsable con descripción): su descripción es el
 *     nombre **entero** (el texto de debajo puede ir cortado). Se marca o se desmarca. De respaldo, una celda **debajo
 *     de una línea divisoria** de la hoja ([belowDivider]: los contactos van entre la cabecera y la primera línea) es
 *     una opción con una etiqueta que no conocemos y se ignora.
 *  6. **Fila del buscador** (pulsable, con un `CheckBox`): su primer texto es el nombre (un grupo, «Ana, Bea,
 *     Carlos», con su nombre entero). Se marca o se desmarca.
 *    Para «Enviar» y las opciones se mira **solo lo pulsado**, nunca sus hijos: en una fila del buscador, un hijo es el
 *    usuario de esa persona (un usuario «send» o «audio» no es un botón).
 * Los nombres se limpian con [clean] y las reglas de nombres de TikTok ([PlatformProfile.symbolNames]: sin marcas de
 * dirección y valen los que son solo emojis, como «💿»). Un clic sin nodo ([EventUiNode]) solo se mira en TikTok
 * ([acceptsNodelessClicks]); en Instagram se descarta, como siempre.
 *
 * Nunca se lee nada del chat ni del mensaje: solo el nombre que sale en la hoja. Sin Android: se
 * prueba en la JVM con árboles falsos y con volcados reales (`UiDump`).
 */
class RecipientDetector(private val config: Config = Config()) {

    data class Config(
        /** Tras este tiempo sin cerrar, la sesión se da por terminada. */
        val sessionMaxMs: Long = 5 * 60_000L,
        /** Profundidad máxima bajo el nodo pulsado al buscar el nombre. */
        val maxDepth: Int = 3,
        /** Nodos que se miran como mucho por clic (cada hijo puede costar una llamada entre procesos). */
        val maxNodes: Int = 30,
        /** Antepasados que se miran para encontrar la fila de lo pulsado. */
        val maxAncestors: Int = 3,
        /** Una fila no pasa de esta parte de la altura de la pantalla (si no, es la hoja entera). */
        val maxRowHeight: Float = 0.25f,
        /** Antepasados que se miran para saber si el clic es dentro del visor de Reels. */
        val maxFeedAncestors: Int = 12,
        val maxNameLength: Int = 64,
        /** TikTok: antepasados que se miran buscando una línea divisoria encima de lo pulsado ([belowDivider]). */
        val maxSectionAncestors: Int = 6,
    )

    sealed interface Outcome {
        /** Regla que decidió (va al registro de Calibración; nunca lleva el nombre). */
        val rule: String

        data class Ignored(override val rule: String) : Outcome

        /** Marcado o desmarcado en la hoja. [selected] = cuántos quedan marcados. */
        data class Toggled(val name: String, val selected: Int, override val rule: String) : Outcome

        /** Enviado a [names] (en el orden en que se marcaron), desde el vídeo [reelId]. */
        data class Sent(val names: List<String>, val reelId: Long?, val closesSession: Boolean, override val rule: String) : Outcome

        /** «Deshacer» del último envío a [name] hecho desde [since]. */
        data class Undone(val name: String, val since: Long, override val rule: String) : Outcome
    }

    private class Session(val startedAt: Long, val reelId: Long?, val profile: PlatformProfile) {
        val selected = LinkedHashSet<String>()
        var lastSent: String? = null
    }

    private var session: Session? = null

    /**
     * Se acaba de pulsar «compartir» en el vídeo [reelId] de la app de [profile]: empieza a escuchar la hoja.
     * [preselected]: personas ya marcadas en la hoja (el envío rápido que se quedó a medias marcó al grupo fijado).
     */
    fun start(now: Long, reelId: Long?, preselected: List<String> = emptyList(), profile: PlatformProfile = PlatformProfile.INSTAGRAM) {
        session = Session(now, reelId, profile).apply { selected.addAll(preselected) }
    }

    fun stop() {
        session = null
    }

    /** Personas o grupos marcados en la hoja abierta (0 sin hoja). */
    val selectedCount: Int get() = session?.selected?.size ?: 0

    /** La app de la sesión abierta (sus clics son los únicos que se miran), o `null` sin sesión. */
    val platform: Platform? get() = session?.profile?.platform

    /**
     * ¿Vale en la sesión abierta un clic que llega sin nodo ([EventUiNode], con lo que trae el propio evento)? Solo en las
     * apps cuya hoja va en su propia ventana ([PlatformProfile.sheetOwnWindow], TikTok: su «Enviar» cierra la hoja al
     * momento y el clic llega sin nodo). En Instagram no: sin nodo no se sabe si el clic viene del visor, y se descarta
     * como hasta la 1.9.0.
     */
    val acceptsNodelessClicks: Boolean get() = session?.profile?.sheetOwnWindow == true

    fun isOpen(now: Long): Boolean {
        val s = session ?: return false
        if (now - s.startedAt > config.sessionMaxMs) {
            session = null
            return false
        }
        return true
    }

    fun onClick(node: UiNode, screenHeight: Int, now: Long): Outcome {
        if (!isOpen(now)) return Outcome.Ignored("sin hoja de compartir abierta")
        val s = session ?: return Outcome.Ignored("sin hoja de compartir abierta")
        if (node is EventUiNode && !s.profile.sheetOwnWindow) return Outcome.Ignored("clic sin nodo")
        if (s.profile.platform == Platform.TIKTOK) return onTikTokClick(s, node, screenHeight)
        if (node.className?.contains("EditText") == true) return Outcome.Ignored("campo de texto")
        if (insideFeed(node)) return Outcome.Ignored("dentro del visor de Reels, no en la hoja")

        val own = buildList {
            add(node)
            addAll(node.children.take(MAX_OWN_CHILDREN))
        }.flatMap { labelsOf(it) }.map { normalize(it.value) }

        if (own.any { SEND.matches(it) } || isSendId(node)) return send(s, node, screenHeight)
        if (own.any { UNDO.matches(it) }) {
            val name = rowName(node, screenHeight) ?: s.lastSent ?: return Outcome.Ignored("deshacer sin destinatario")
            if (s.lastSent == name) s.lastSent = null
            return Outcome.Undone(name, s.startedAt, "deshacer")
        }

        val below = labelsBelow(node)
        if (below.any { isOption(normalize(it.value)) }) return Outcome.Ignored("otra opción de la hoja")
        val name = nameOf(below) ?: rowName(node, screenHeight) ?: return Outcome.Ignored("sin nombre")
        return toggle(s, name)
    }

    private fun toggle(s: Session, name: String, where: String = ""): Outcome.Toggled =
        if (s.selected.remove(name)) {
            Outcome.Toggled(name, s.selected.size, "desmarcado$where")
        } else {
            s.selected += name
            Outcome.Toggled(name, s.selected.size, "marcado$where")
        }

    // ── TikTok (1.10.0) ──────────────────────────────────────────────────────────────────────────

    /**
     * La hoja de TikTok se ha cerrado (medido en el móvil, TikTok 47.7: su «Enviar» cierra la hoja **sin** un
     * `TYPE_VIEW_CLICKED`, así que el clic en «Enviar» nunca llega). El servicio mira entonces si TikTok enseña abajo el
     * aviso «Enviado a …» ([isSentBanner]) y llama aquí con lo que vio: con el aviso, enviado a los marcados; sin él (se
     * cerró con la «X», con atrás o tocando fuera), no se apunta nada. En los dos casos la sesión se cierra: TikTok olvida lo
     * marcado al cerrar la hoja.
     */
    fun onTikTokSheetClosed(selection: Selection, sentBanner: Boolean): Outcome {
        if (session?.startedAt == selection.startedAt) session = null
        if (!sentBanner) return Outcome.Ignored("hoja cerrada sin enviar")
        return Outcome.Sent(selection.names, selection.reelId, closesSession = true, rule = "aviso de enviado (${selection.names.size})")
    }

    /** Lo marcado en la hoja de una app que cierra la hoja al enviar (TikTok), en el momento de cerrarse. */
    data class Selection(val names: List<String>, val reelId: Long?, val startedAt: Long)

    /** Un aviso «Enviado a …» de TikTok en pantalla: su texto normalizado y su ventana ([UiNode.windowId]; cada aviso, una nueva). */
    data class SentBanner(val text: String, val windowId: Int)

    /**
     * Lo marcado ahora en la hoja de TikTok (para decidir con [onTikTokSheetClosed] cuando se compruebe el aviso), o `null`
     * sin sesión de TikTok o sin nadie marcado. No cierra la sesión: si la hoja sigue abierta, todo sigue igual.
     */
    fun tikTokSelection(now: Long): Selection? {
        if (!isOpen(now)) return null
        val s = session?.takeIf { it.profile.platform == Platform.TIKTOK && it.selected.isNotEmpty() } ?: return null
        return Selection(s.selected.toList(), s.reelId, s.startedAt)
    }

    /** Un clic en la hoja de compartir de TikTok (reglas en el KDoc de la clase). */
    private fun onTikTokClick(s: Session, node: UiNode, screenHeight: Int): Outcome {
        val profile = s.profile
        if (node.className?.contains("EditText") == true) return Outcome.Ignored("campo de texto")
        if (insideFeed(node, profile.feedIds)) return Outcome.Ignored("dentro del visor de vídeos, no en la hoja")

        // Solo lo pulsado: los dos «Enviar» (y las opciones) llevan su etiqueta en el propio nodo. Los hijos de una fila del
        // buscador son su nombre y su usuario, y un usuario «send» o «audio» no es un botón (la fila se lee abajo).
        val own = labelsOf(node).map { normalize(it.value) }
        // Un contacto que se llama «Send» o «Enviar» (su celda, su foto, su nombre o su fila del buscador) nunca es el botón:
        // los dos «Enviar» de verdad no tienen encima ninguna celda ni fila de contacto (segunda pasada de la revisión).
        val inContact = cell(node) != null || searchRow(node, screenHeight) != null
        if (!inContact && (own.any { SEND.matches(it) } || isSendId(node, profile))) {
            if (s.selected.isEmpty()) {
                // La hoja de TikTok se cierra al momento: no queda nada que escuchar.
                session = null
                return Outcome.Ignored("enviar sin destinatario")
            }
            return send(s, node, screenHeight)
        }
        if (inReactionGrid(node)) return Outcome.Ignored("emoji de reacción (no se apunta)")
        if (own.any { isSheetOption(it, profile) }) return Outcome.Ignored("otra opción de la hoja")

        cell(node)?.let { cell ->
            val desc = cell.contentDescription?.toString().orEmpty()
            if (isSheetOption(normalize(desc), profile)) return Outcome.Ignored("otra opción de la hoja")
            // Solo en la hoja: en el buscador, la lista empieza con una vista fina vacía que no separa nada.
            if (belowDivider(cell)) return Outcome.Ignored("debajo de los contactos (opción de la hoja)")
            val name = clean(desc, fromText = true, symbolNames = profile.symbolNames) ?: return Outcome.Ignored("sin nombre")
            return toggle(s, name, " (fila)")
        }
        val row = searchRow(node, screenHeight) ?: return Outcome.Ignored("sin nombre")
        // Solo el primer texto de la fila: si no vale, nunca el segundo («· Amigos», el usuario o «3 miembros»).
        val first = labelsBelow(row).firstOrNull { it.isText }?.value ?: return Outcome.Ignored("sin nombre")
        if (isSheetOption(normalize(first), profile)) return Outcome.Ignored("otra opción de la hoja")
        val name = clean(first, fromText = true, symbolNames = profile.symbolNames) ?: return Outcome.Ignored("sin nombre")
        return toggle(s, name, " (buscador)")
    }

    /**
     * La celda de un contacto de la fila de TikTok: lo pulsado o su primer antepasado **pulsable con descripción**
     * (la foto, el `CheckBox` y el nombre de dentro también son pulsables y no la llevan). La descripción es el nombre.
     */
    private fun cell(node: UiNode): UiNode? {
        var current: UiNode? = node
        repeat(config.maxAncestors + 1) {
            val n = current ?: return null
            if (n.isClickable && !n.contentDescription.isNullOrBlank()) return n
            current = n.parent
        }
        return null
    }

    /**
     * La fila del buscador de TikTok: lo pulsado o un antepasado pulsable, no más alto que una fila, con un `CheckBox`
     * dentro (el de marcar) y algún texto.
     */
    private fun searchRow(node: UiNode, screenHeight: Int): UiNode? {
        var current: UiNode? = node
        repeat(config.maxAncestors + 1) {
            val n = current ?: return null
            val small = screenHeight <= 0 || n.bounds.height <= screenHeight * config.maxRowHeight
            if (n.isClickable && small && hasCheckBox(n, depth = 0) && labelsBelow(n).any { it.isText }) return n
            current = n.parent
        }
        return null
    }

    private fun hasCheckBox(node: UiNode, depth: Int): Boolean =
        node.className?.contains("CheckBox") == true ||
            (depth < MAX_CHECKBOX_DEPTH && node.children.take(MAX_ROW_CHILDREN).any { hasCheckBox(it, depth + 1) })

    /** ¿Es un emoji de reacción? (Celdas de un `GridView`; los contactos van en una fila, nunca en una cuadrícula.) */
    private fun inReactionGrid(node: UiNode): Boolean {
        var current: UiNode? = node
        repeat(MAX_GRID_ANCESTORS + 1) {
            val n = current ?: return false
            if (n.className?.contains("GridView") == true) return true
            current = n.parent
        }
        return false
    }

    /**
     * Regla de posición de la hoja de TikTok: subiendo desde una celda, ¿hay en algún nivel una **línea divisoria** (una
     * vista fina, ancha y sin etiquetas) encima? Los contactos van entre la cabecera y la primera línea; debajo, las
     * opciones de compartir, «Escribe un mensaje…» y los emojis. Por geometría (no por identidad de nodos, que en el
     * móvil se piden de nuevo cada vez). Solo para las celdas de la hoja, no para las filas del buscador (su lista empieza
     * con una vista de 1 px vacía).
     */
    internal fun belowDivider(node: UiNode): Boolean {
        var current = node
        repeat(config.maxSectionAncestors) {
            val parent = current.parent ?: return false
            val top = current.bounds.top
            val divider = parent.children.take(MAX_SIBLINGS).any { sibling ->
                isDivider(sibling, parent) && sibling.bounds.bottom <= top
            }
            if (divider) return true
            current = parent
        }
        return false
    }

    private fun isDivider(node: UiNode, parent: UiNode): Boolean {
        val b = node.bounds
        return !b.isEmpty && b.height <= DIVIDER_MAX_PX && b.width * 2 >= parent.bounds.width &&
            node.text.isNullOrBlank() && node.contentDescription.isNullOrBlank()
    }

    private fun send(s: Session, node: UiNode, screenHeight: Int): Outcome {
        if (s.selected.isNotEmpty()) {
            val names = s.selected.toList()
            session = null
            return Outcome.Sent(names, s.reelId, closesSession = true, rule = "botón enviar (${names.size})")
        }
        val name = rowName(node, screenHeight) ?: return Outcome.Ignored("enviar sin destinatario")
        s.lastSent = name
        return Outcome.Sent(listOf(name), s.reelId, closesSession = false, rule = "enviar de la fila")
    }

    // ── Lectura del árbol ────────────────────────────────────────────────────────────────────────

    private class Label(val value: String, val isText: Boolean)

    private fun labelsOf(node: UiNode): List<Label> = listOfNotNull(
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { Label(it, isText = true) },
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { Label(it, isText = false) },
    )

    /** Etiquetas del nodo y de sus descendientes (en orden de lectura), con límite de nodos y profundidad. */
    private fun labelsBelow(node: UiNode, skip: UiNode? = null): List<Label> {
        val out = mutableListOf<Label>()
        var visited = 0
        var firstLine = -1
        fun visit(n: UiNode, depth: Int) {
            if (visited >= config.maxNodes || n === skip || !n.isVisible) return
            visited++
            val own = labelsOf(n)
            val id = shortViewId(n.viewId)
            val text = own.firstOrNull { it.isText }
            if (text != null && firstLine >= 0 && id.endsWith(SECOND_LINE) && !STATUS.containsMatchIn(normalize(text.value))) {
                // 1.4.4: la celda de la cuadrícula parte los nombres largos en dos líneas («Viaje fin de» / «curso»):
                // son un solo nombre (con solo la primera, el buscador no lo encontraba).
                out[firstLine] = Label(out[firstLine].value + " " + text.value.trim(), isText = true)
                out += own.filter { !it.isText }
                firstLine = -1
                if (depth < config.maxDepth) n.children.forEach { visit(it, depth + 1) }
                return
            }
            firstLine = if (text != null && id.endsWith(FIRST_LINE)) out.size + own.indexOf(text) else -1
            out += own
            if (depth < config.maxDepth) n.children.forEach { visit(it, depth + 1) }
        }
        visit(node, 0)
        return out
    }

    /** El nombre de la fila del nodo: el primer nombre de un antepasado pequeño, sin mirar el propio botón. */
    private fun rowName(node: UiNode, screenHeight: Int): String? {
        var ancestor = node.parent
        repeat(config.maxAncestors) {
            val current = ancestor ?: return null
            if (screenHeight > 0 && current.bounds.height > screenHeight * config.maxRowHeight) return null
            val labels = labelsBelow(current, skip = node)
            if (labels.any { isOption(normalize(it.value)) }) return null
            nameOf(labels)?.let { return it }
            ancestor = current.parent
        }
        return null
    }

    /**
     * ¿El nodo está dentro del visor de vídeos? Por defecto, el de Reels (ids `clips_*`); en otra app, sus
     * [feedIds] ([PlatformProfile.feedIds]: en TikTok, `viewpager`). Lo usa también [QuickSender].
     */
    internal fun insideFeed(node: UiNode, feedIds: List<String> = FEED_IDS): Boolean {
        var current: UiNode? = node
        repeat(config.maxFeedAncestors) {
            val n = current ?: return false
            val id = shortViewId(n.viewId)
            if (feedIds.any { id.contains(it) }) return true
            current = n.parent
        }
        return false
    }

    /** Primero los textos visibles (el nombre de la fila), después las descripciones. */
    private fun nameOf(labels: List<Label>): String? =
        labels.filter { it.isText }.firstNotNullOfOrNull { clean(it.value, fromText = true) }
            ?: labels.filter { !it.isText }.firstNotNullOfOrNull { clean(it.value, fromText = false) }

    /**
     * «Foto del perfil de Ana» → «Ana»; «Ana García, seleccionado» → «Ana García». `null` si no parece
     * un nombre (vacío, largo, sin letras, un botón o un estado como «Activo hace 5 min»).
     *
     * [symbolNames] son **las reglas de nombres de TikTok** ([PlatformProfile.symbolNames], 1.10.0): se quitan las marcas
     * de dirección invisibles con las que TikTok rodea los nombres (U+200E delante; en los grupos, U+2068/U+2069) y vale
     * un nombre hecho solo de emojis («💿»). Sin ellas (Instagram) el nombre sale exactamente como en la 1.9.0, con sus
     * marcas si las lleva: es lo que ya está guardado (estadísticas, fijados y la clave de sus fotos).
     */
    internal fun clean(raw: String, fromText: Boolean, symbolNames: Boolean = false): String? {
        fun norm(text: String) = if (symbolNames) normalize(text) else normalizeKeepingMarks(text)
        var name = (if (symbolNames) stripBidi(raw) else raw).replace(WHITESPACE, " ").trim()
        PREFIXES.firstOrNull { norm(name).startsWith(it) }?.let { name = name.substring(it.length).trim() }
        if (!fromText) name = name.substringBefore(", ").trim()
        while (true) {
            val n = norm(name)
            val suffix = SUFFIXES.firstOrNull { n.endsWith(it) } ?: break
            name = name.dropLast(suffix.length).trim().trimEnd(',').trim()
        }
        if (name.isEmpty() || name.length > config.maxNameLength) return null
        // Contadores («12»), puntos, iconos. Con las reglas de TikTok vale un nombre hecho solo de emojis («💿»): allí hay
        // quien se llama así.
        if (name.none { it.isLetter() } && !(symbolNames && hasSymbol(name))) return null
        val n = norm(name)
        if (SEND.matches(n) || UNDO.matches(n) || isOption(n) || STATUS.containsMatchIn(n)) return null
        return name
    }

    /** `internal` para [QuickSender], que busca en la hoja con la misma normalización y limpieza. */
    internal companion object {
        const val MAX_OWN_CHILDREN = 4

        /** Ids del visor de Reels de Instagram (ver [FeedRules]): un clic ahí no es de la hoja. */
        val FEED_IDS = listOf("clips_viewer", "clips_view_pager", "clips_video_container", "clips_pager", "clips_ufi")

        val WHITESPACE = Regex("\\s+")

        /** Para comparar: sin tildes, mayúsculas ni marcas de dirección (las de TikTok), y con los espacios juntos. */
        fun normalize(text: String): String = normalizeKeepingMarks(stripBidi(text))

        /**
         * La normalización de la 1.9.0, que **no** quita las marcas de dirección: la de los nombres de Instagram que se
         * guardan ([clean] sin las reglas de TikTok) y la clave de sus fijados ([AvatarRules.fileName] y `QuickSendMemory`),
         * para que lo guardado antes de la 1.10.0 siga valiendo aunque el nombre lleve alguna.
         */
        fun normalizeKeepingMarks(text: String): String = CategoryClassifier.normalize(text).replace(WHITESPACE, " ").trim()

        /** Marcas de dirección invisibles (LRM/RLM, U+202A–U+202E, U+2066–U+2069) que TikTok pone en los nombres. */
        private val BIDI: Set<Char> = (listOf(0x200E, 0x200F) + (0x202A..0x202E) + (0x2066..0x2069)).mapTo(HashSet()) { Char(it) }

        /**
         * El aviso «Enviado a …» en un idioma: sus códigos ([codes], como los da `Locale.language`), los prefijos que acepta la
         * regla ([prefixes], seguidos del nombre: con un espacio, o pegado si [joined]) y lo que se pide al atajo de texto
         * ([queries]). `findAccessibilityNodeInfosByText` busca una **subcadena** sin mayúsculas (pero con tildes), así que una
         * consulta vale por todos los prefijos que la contienen («Enviado» por «Enviado a» y «Enviado para»); trae más nodos,
         * pero la regla los filtra, y cada consulta es una llamada entre procesos por ventana.
         */
        internal class BannerLanguage(val codes: List<String>, val prefixes: List<String>, val queries: List<String>, val joined: Boolean = false)

        /**
         * Español e inglés, vistos o conocidos; los demás, supuestos de memoria, sin fuente verificada: si alguno no es así,
         * simplemente no se reconoce. Solo idiomas con un prefijo fijo delante del nombre: no los que ponen el nombre delante
         * (ja, ko) ni los que no lo llevan (tr, ru, uk, hi, th). En chino el nombre va pegado (también se admite con espacio).
         */
        internal val SENT_BANNER_LANGUAGES = listOf(
            BannerLanguage(listOf("es"), listOf("Enviado a", "Enviada a"), listOf("Enviado", "Enviada")),
            BannerLanguage(listOf("en"), listOf("Sent to"), listOf("Sent to")),
            BannerLanguage(listOf("pt"), listOf("Enviado para", "Enviada para"), listOf("Enviado", "Enviada")),
            BannerLanguage(listOf("fr"), listOf("Envoyé à", "Envoyée à"), listOf("Envoyé")),
            BannerLanguage(listOf("de"), listOf("Gesendet an"), listOf("Gesendet an")),
            BannerLanguage(listOf("it"), listOf("Inviato a", "Inviata a"), listOf("Inviat")),
            BannerLanguage(listOf("nl"), listOf("Verzonden naar", "Verstuurd naar"), listOf("Verzonden naar", "Verstuurd naar")),
            BannerLanguage(listOf("pl"), listOf("Wysłano do"), listOf("Wysłano do")),
            BannerLanguage(listOf("ar"), listOf("تم الإرسال إلى"), listOf("تم الإرسال إلى")),
            BannerLanguage(listOf("id", "in"), listOf("Terkirim ke", "Dikirim ke"), listOf("Terkirim ke", "Dikirim ke")),
            BannerLanguage(listOf("ms"), listOf("Dihantar kepada", "Dihantar ke"), listOf("Dihantar ke")),
            BannerLanguage(listOf("vi"), listOf("Đã gửi đến", "Đã gửi tới"), listOf("Đã gửi")),
            BannerLanguage(listOf("zh"), listOf("已发送给", "已傳送給"), listOf("已发送给", "已傳送給"), joined = true),
            BannerLanguage(listOf("fil", "tl"), listOf("Ipinadala kay", "Naipadala sa"), listOf("Ipinadala kay", "Naipadala sa")),
            BannerLanguage(listOf("ro"), listOf("Trimis către"), listOf("Trimis către")),
        )

        /** Los idiomas cuyas consultas se hacen siempre, sea cual sea el del sistema. */
        private val ALWAYS_BANNER_LANGUAGES = listOf("es", "en")

        /**
         * Lo que se pide al atajo de texto para encontrar el aviso con el sistema en [languages] (etiquetas o códigos: «es-ES»,
         * «zh-TW», «fr»): las consultas de esos idiomas y, siempre, las de español e inglés; sin repetir. TikTok sigue el idioma
         * del sistema salvo que se cambie en la app: un aviso en un idioma que no está aquí no se busca (pero si se encuentra
         * por otra consulta, la regla lo acepta).
         */
        fun sentBannerQueriesFor(languages: List<String>): List<String> {
            val codes = (languages.map { it.substringBefore('-').substringBefore('_').lowercase() } + ALWAYS_BANNER_LANGUAGES).distinct()
            return codes.flatMap { code -> SENT_BANNER_LANGUAGES.filter { code in it.codes }.flatMap { it.queries } }.distinct()
        }

        /** Todas las consultas de todos los idiomas: todo aviso que la regla reconoce contiene, tal cual, alguna. */
        val SENT_BANNER_QUERIES: List<String> = SENT_BANNER_LANGUAGES.flatMap { it.queries }.distinct()

        /** La regla, normalizada, con los prefijos de todos los idiomas (no cuesta llamadas entre procesos). */
        val SENT_BANNER = Regex(
            "^(?:(?:" + SENT_BANNER_LANGUAGES.filter { !it.joined }.flatMap { it.prefixes }.joinToString("|") { Regex.escape(normalize(it)) } + ") .+" +
                "|(?:" + SENT_BANNER_LANGUAGES.filter { it.joined }.flatMap { it.prefixes }.joinToString("|") { Regex.escape(normalize(it)) } + ") ?.+)",
        )

        /**
         * Lo más largo que puede ser el aviso (normalizado): «Enviado a» y el nombre o los nombres. Un texto más largo es
         * otra cosa, como el pie de un vídeo que empieza por «Sent to…».
         */
        const val MAX_SENT_BANNER_LENGTH = 100

        /** TikTok: el pie del vídeo (id sin ofuscar en 47.7): nunca es el aviso, aunque empiece por «Sent to…». */
        val CAPTION_IDS = listOf("desc")

        /**
         * ¿Es el aviso «Enviado a …»? Una línea corta sin «#»: el pie de un vídeo («Sent to my bestie… #fyp») puede empezar
         * igual.
         */
        fun isSentBanner(label: CharSequence): Boolean {
            val raw = label.toString()
            if ('#' in raw || '\n' in raw) return false
            val n = normalize(raw)
            return n.length <= MAX_SENT_BANNER_LENGTH && SENT_BANNER.matches(n)
        }

        /**
         * Los avisos «Enviado a …» que se ven en [roots] (normalizados, con su ventana), buscados con el atajo de texto de
         * [finder] con [queries] (el servicio pasa las de los idiomas del sistema, [sentBannerQueriesFor]); nunca el pie del
         * vídeo ([CAPTION_IDS]).
         */
        fun sentBanners(roots: List<UiNode>, finder: UiFinder, queries: List<String> = SENT_BANNER_QUERIES): Set<SentBanner> {
            val out = LinkedHashSet<SentBanner>()
            for (root in roots) {
                for (query in queries) {
                    for (n in finder.byText(root, query)) {
                        if (shortViewId(n.viewId) in CAPTION_IDS) continue
                        listOfNotNull(n.text, n.contentDescription).filter { isSentBanner(it) }
                            .forEach { out += SentBanner(normalize(it.toString()), n.windowId) }
                    }
                }
            }
            return out
        }

        /**
         * Los avisos de [now] que son de **este** cierre de la hoja: los que no son ninguno de [before] (los de envíos anteriores
         * que se veían al empezar la sesión). Un aviso es el mismo si dice lo mismo **en la misma ventana**: medido en el móvil
         * (TikTok 47.7.3, 7 nov 2026), cada aviso sale en una ventana nueva (6437, 6458, 6459) y dura unos 5 s (aún a 4,2 y
         * 4,7 s del envío; ya no a 5,0 y 6,4 s), también con la hoja encima. Así el de un envío anterior que sigue en pantalla
         * nunca vale, por mucho que dure, y uno nuevo a las mismas personas sí, aunque diga lo mismo. Sin ventana conocida
         * ([UiNode.NO_WINDOW]) basta el texto: un reenvío a las mismas personas con el aviso viejo aún a la vista no se apunta
         * (mejor eso que uno inventado). Verificación final de la 1.10.0: comparando solo el texto, el aviso visto al pulsar
         * «Compartir» dejaba sin apuntar cualquier envío a esa persona desde la hoja, por mucho que se tardara.
         */
        fun freshSentBanners(before: Set<SentBanner>, now: Set<SentBanner>): Set<SentBanner> =
            now.filterTo(LinkedHashSet()) { b -> before.none { old -> old.text == b.text && sameWindow(old.windowId, b.windowId) } }

        /**
         * [freshSentBanners] y, además (1.10.1), para cuando la lectura del principio fue **a ciegas** (la sesión empezó con la
         * hoja ya abierta, porque un envío rápido la dejó así: con un diálogo encima el aviso de debajo no se ve):
         *  - nunca vale un aviso de una ventana de [knownWindows] (las de avisos vistos tras envíos ya apuntados:
         *    [SentBannerWindows]); cada aviso sale en una ventana nueva, así que esas ventanas no pueden ser de este envío;
         *  - con [newerThan] (la ventana de la hoja de esta sesión), solo valen avisos de una ventana posterior: los ids de
         *    ventana crecen (medido, TikTok 47.7.3: 6437, 6458, 6459, 6464), y el aviso de este envío sale después de abrirse
         *    la hoja. Un aviso sin ventana conocida ([UiNode.NO_WINDOW]) no se descarta por estas dos reglas.
         */
        fun freshSentBanners(before: Set<SentBanner>, now: Set<SentBanner>, knownWindows: Set<Int>, newerThan: Int?): Set<SentBanner> =
            freshSentBanners(before, now).filterTo(LinkedHashSet()) { b ->
                b.windowId == UiNode.NO_WINDOW || (b.windowId !in knownWindows && (newerThan == null || b.windowId > newerThan))
            }

        /**
         * ¿Es un clic que provoca el envío rápido (no cuenta: lo apunta él al terminar)? Mientras está en curso ([quickBusy]),
         * todos los de su app; acabado, los que lleguen hasta [ignoreUntil] (el último paso más un margen), salvo un clic en el
         * «Compartir» del visor ([viewerShare]) con el envío ya terminado (1.10.1): el envío rápido no pulsa nada al acabar,
         * así que ese es del usuario y abre su sesión de «A quién envías». Por si el evento del «Compartir» que pulsó el propio
         * envío ([ownShareAt], cuándo lo pulsó) llegara con retraso, ese clic sigue siendo propio durante [OWN_SHARE_ECHO_MS].
         */
        fun isOwnQuickClick(quickBusy: Boolean, now: Long, ignoreUntil: Long, viewerShare: Boolean, ownShareAt: Long? = null): Boolean {
            if (quickBusy) return true
            if (now >= ignoreUntil) return false
            val echo = ownShareAt != null && now - ownShareAt < OWN_SHARE_ECHO_MS
            return !viewerShare || echo
        }

        /** Lo que puede tardar en llegar el evento del clic que pulsó el propio envío rápido. */
        const val OWN_SHARE_ECHO_MS = 300L

        private fun sameWindow(a: Int, b: Int): Boolean = a == b || a == UiNode.NO_WINDOW || b == UiNode.NO_WINDOW

        fun stripBidi(text: String): String = if (text.none { it in BIDI }) text else text.filterNot { it in BIDI }

        /** ¿Lleva algún emoji o símbolo («💿», «🦇»)? */
        fun hasSymbol(text: String): Boolean = text.codePoints().anyMatch { Character.getType(it) == Character.OTHER_SYMBOL.toInt() }

        /**
         * El «Enviar» de abajo de la hoja de hoy, por su id (Instagram 454, nov. 2026; `Button` con la descripción
         * «Enviar»). Vale en cualquier idioma; las palabras de [SEND] quedan de respaldo.
         */
        const val SEND_BUTTON_ID = "com.instagram.android:id/direct_send_button_multi_select"

        /** «Crear grupo» (aparece junto a «Enviar por separado» con dos marcados): nunca es «Enviar», lleve el id que lleve. */
        val CREATE_GROUP = Regex(
            "^(?:crear (?:un )?grupo|create (?:a )?group|creer un groupe|crea (?:un )?gruppo|gruppe erstellen|criar (?:um )?grupo|" +
                "crea un grup|groep maken|taldea sortu)$",
        )

        /** ¿Es el «Enviar» de la hoja por su id (y no «Crear grupo»)? */
        fun isSendId(node: UiNode): Boolean = node.viewId == SEND_BUTTON_ID && !isCreateGroup(node)

        /**
         * Lo mismo en la hoja de [profile] (1.10.0): en Instagram, [SEND_BUTTON_ID]; en las demás, sus
         * [PlatformProfile.sendIds] en cualquiera de sus paquetes (TikTok: `tv_send`, el del buscador).
         */
        fun isSendId(node: UiNode, profile: PlatformProfile): Boolean {
            if (profile.platform == Platform.INSTAGRAM) return isSendId(node)
            val id = node.viewId ?: return false
            return profile.sendIds.any { id in profile.fullIds(it) } && !isCreateGroup(node)
        }

        private fun isCreateGroup(node: UiNode): Boolean =
            (listOf(node) + node.children.take(MAX_OWN_CHILDREN))
                .flatMap { listOfNotNull(it.text, it.contentDescription) }
                .any { CREATE_GROUP.matches(normalize(it.toString())) }

        /**
         * ¿[n] (normalizado) es una opción de la hoja de [profile] y no un contacto? Sin los puntos finales de
         * «Escribe un mensaje...».
         */
        fun isSheetOption(n: String, profile: PlatformProfile): Boolean {
            val t = n.trimEnd('.', '…', ':', ' ')
            return isOption(t) || t in profile.sheetOptions
        }

        /** TikTok: hasta dónde se busca el `CheckBox` de una fila del buscador, y cuántos hijos por nivel. */
        private const val MAX_CHECKBOX_DEPTH = 2
        private const val MAX_ROW_CHILDREN = 8

        /** TikTok: antepasados que se miran buscando la cuadrícula de los emojis de reacción. */
        private const val MAX_GRID_ANCESTORS = 3

        /** TikTok: hermanos que se miran por nivel buscando una línea divisoria, y lo más gruesa que puede ser (px). */
        private const val MAX_SIBLINGS = 16
        private const val DIVIDER_MAX_PX = 6

        /**
         * Sobre texto normalizado (minúsculas, sin tildes). Español e inglés, y de respaldo (por si el id cambia) los
         * idiomas más comunes de Instagram: portugués y gallego («enviar»), catalán, francés, italiano, alemán,
         * neerlandés y euskera (1.4.4).
         */
        val SEND = Regex(
            "^(?:enviar|send|enviar por separado|send separately|enviar a todos|send to all|enviar ahora|send now|" +
                "enviar separadamente|envia|envia per separat|envoyer|envoyer separement|invia|invia separatamente|" +
                "senden|separat senden|einzeln senden|verzenden|versturen|afzonderlijk verzenden|bidali|bidali bereizita)$",
        )
        val UNDO = Regex("^(?:deshacer|undo|anular|cancelar envio|desfazer|desfer|annuler|annulla|ruckgangig machen|ongedaan maken|desegin)$")

        /** Estados y subtítulos de una fila, que no son el nombre. */
        val STATUS = Regex(
            "^(?:activ[oa]|active|en linea|online|escribiendo|typing|visto|seen|enviado|sent|seleccionad[oa]|selected)\\b",
        )

        val PREFIXES = listOf(
            "foto del perfil de ", "foto de perfil de ", "imagen del perfil de ", "imagen de perfil de ",
            "profile picture of ", "profile photo of ", "avatar de ", "avatar of ",
        )
        /** Celda de la cuadrícula de hoy: `grid_view_pog_text_view_first_line` y `…_second_line` (1.4.4). */
        const val FIRST_LINE = "_first_line"
        const val SECOND_LINE = "_second_line"

        /** Primero los de «chat» (la celda de hoy dice «Viaje fin de curso Chat sin seleccionar»), que son más largos. */
        val SUFFIXES = listOf(
            " chat sin seleccionar", " chat no seleccionado", " chat seleccionado", " chat not selected", " chat selected",
            " sin seleccionar", " no seleccionado", " no seleccionada", " seleccionado", " seleccionada", " not selected", " selected",
        )

        /** Opciones de la hoja (y botones del visor) que no son personas: coincidencia exacta. */
        val OPTIONS = setOf(
            "copiar enlace", "copy link", "enlace", "link", "anadir a la historia", "anadir a historia",
            "anadir historia", "add to story", "add to your story", "tu historia", "your story", "compartir",
            "share", "compartir en", "share to", "compartir en...", "mas", "more", "mas opciones", "more options",
            "opciones", "options", "buscar", "search", "crear grupo", "create group", "nuevo grupo", "new group",
            "cancelar", "cancel", "cerrar", "close", "listo", "done", "descargar", "download", "guardar", "save",
            "whatsapp", "whatsapp business", "mensajes", "messages", "sms", "facebook", "messenger", "threads",
            "telegram", "gmail", "correo", "correo electronico", "email", "snapchat", "x", "twitter", "signal",
            "discord", "me gusta", "like", "comentar", "comment", "comentarios", "comments", "seguir", "follow",
            "audio", "remix", "silenciar", "mute", "reels", "inicio", "home", "perfil", "profile", "atras", "back",
            "notas", "notes", "codigo qr", "qr code", "escribe un mensaje", "write a message",
        )

        fun isOption(n: String): Boolean = n in OPTIONS || n.startsWith("compartir en ") || n.startsWith("share to ")
    }
}

/**
 * Las ventanas de los avisos «Enviado a …» de TikTok vistos tras cada envío ya apuntado (envío rápido terminado, o un `Sent`
 * de «A quién envías»), 1.10.1. Cada aviso sale en una ventana nueva y la conserva mientras se ve (unos 5 s, también con la
 * hoja encima), así que un aviso de una de estas ventanas nunca es el de un envío posterior ([RecipientDetector.freshSentBanners]).
 * Acotada: solo las de los últimos [keepMs] y, como mucho, las [max] más recientes. Sin Android; el servicio la usa en un solo hilo.
 */
class SentBannerWindows(private val keepMs: Long = KEEP_MS, private val max: Int = MAX) {

    /** Ventana → cuándo se vio por última vez (en el reloj que pase quien llama). */
    private val seen = LinkedHashMap<Int, Long>()

    /** Se han visto los avisos de [windows] en [now]. Las ventanas desconocidas ([UiNode.NO_WINDOW]) no se guardan. */
    fun remember(windows: Collection<Int>, now: Long) {
        for (w in windows) {
            if (w == UiNode.NO_WINDOW) continue
            seen.remove(w)
            seen[w] = now
        }
        prune(now)
    }

    /** Las ventanas recordadas en [now] (las vistas en los últimos [keepMs]). */
    fun known(now: Long): Set<Int> {
        prune(now)
        return seen.keys.toSet()
    }

    private fun prune(now: Long) {
        seen.entries.removeAll { now - it.value > keepMs }
        while (seen.size > max) seen.remove(seen.keys.first())
    }

    companion object {
        /** El aviso dura unos 5 s (medido); el doble, de margen. */
        const val KEEP_MS = 10_000L
        const val MAX = 8
    }
}
