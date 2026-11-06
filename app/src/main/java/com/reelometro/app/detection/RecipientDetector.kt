package com.reelometro.app.detection

/**
 * «A quién envías» (solo Instagram y solo con el ajuste encendido): lee en la hoja de compartir el
 * nombre de la persona o el grupo al que mandas un vídeo.
 *
 * Funciona por sesiones. Una sesión se abre ([start]) justo después de que [ShareDetector] detecte
 * «compartir» y se cierra ([stop]) cuando el feed vuelve a moverse, al enviar con el botón de abajo
 * o, como mucho, a los [Config.sessionMaxMs]. Fuera de una sesión no se mira ningún clic.
 *
 * Dentro de la sesión, cada `TYPE_VIEW_CLICKED` de Instagram se clasifica así:
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
 * Nunca se lee nada del chat ni del mensaje: solo el nombre que sale en la hoja. Sin Android: se
 * prueba en la JVM con árboles falsos.
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

    private class Session(val startedAt: Long, val reelId: Long?) {
        val selected = LinkedHashSet<String>()
        var lastSent: String? = null
    }

    private var session: Session? = null

    /**
     * Se acaba de pulsar «compartir» en el vídeo [reelId]: empieza a escuchar la hoja. [preselected]:
     * personas ya marcadas en la hoja (el envío rápido que se quedó a medias marcó al grupo fijado).
     */
    fun start(now: Long, reelId: Long?, preselected: List<String> = emptyList()) {
        session = Session(now, reelId).apply { selected.addAll(preselected) }
    }

    fun stop() {
        session = null
    }

    /** Personas o grupos marcados en la hoja abierta (0 sin hoja). */
    val selectedCount: Int get() = session?.selected?.size ?: 0

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
        return if (s.selected.remove(name)) {
            Outcome.Toggled(name, s.selected.size, "desmarcado")
        } else {
            s.selected += name
            Outcome.Toggled(name, s.selected.size, "marcado")
        }
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

    /** ¿El nodo está dentro del visor de Reels (ids `clips_*`)? Lo usa también [QuickSender]. */
    internal fun insideFeed(node: UiNode): Boolean {
        var current: UiNode? = node
        repeat(config.maxFeedAncestors) {
            val n = current ?: return false
            val id = shortViewId(n.viewId)
            if (FEED_IDS.any { id.contains(it) }) return true
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
     */
    internal fun clean(raw: String, fromText: Boolean): String? {
        var name = raw.replace(WHITESPACE, " ").trim()
        PREFIXES.firstOrNull { normalize(name).startsWith(it) }?.let { name = name.substring(it.length).trim() }
        if (!fromText) name = name.substringBefore(", ").trim()
        while (true) {
            val n = normalize(name)
            val suffix = SUFFIXES.firstOrNull { n.endsWith(it) } ?: break
            name = name.dropLast(suffix.length).trim().trimEnd(',').trim()
        }
        if (name.isEmpty() || name.length > config.maxNameLength) return null
        if (name.none { it.isLetter() }) return null // contadores («12»), puntos, iconos
        val n = normalize(name)
        if (SEND.matches(n) || UNDO.matches(n) || isOption(n) || STATUS.containsMatchIn(n)) return null
        return name
    }

    /** `internal` para [QuickSender], que busca en la hoja con la misma normalización y limpieza. */
    internal companion object {
        const val MAX_OWN_CHILDREN = 4

        /** Ids del visor de Reels de Instagram (ver [FeedRules]): un clic ahí no es de la hoja. */
        val FEED_IDS = listOf("clips_viewer", "clips_view_pager", "clips_video_container", "clips_pager", "clips_ufi")

        val WHITESPACE = Regex("\\s+")

        fun normalize(text: String): String = CategoryClassifier.normalize(text).replace(WHITESPACE, " ").trim()

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
        fun isSendId(node: UiNode): Boolean =
            node.viewId == SEND_BUTTON_ID &&
                (listOf(node) + node.children.take(MAX_OWN_CHILDREN))
                    .flatMap { listOfNotNull(it.text, it.contentDescription) }
                    .none { CREATE_GROUP.matches(normalize(it.toString())) }

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
