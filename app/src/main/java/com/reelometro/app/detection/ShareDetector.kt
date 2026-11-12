package com.reelometro.app.detection

import com.reelometro.app.data.Platform

/**
 * Detecta la pulsación del botón **compartir** de un vídeo corto a partir de un `TYPE_VIEW_CLICKED`.
 *
 * Solo se cuenta que se ha pulsado (y en qué vídeo); a quién se envía lo lee aparte [RecipientDetector]
 * (Instagram y TikTok, con el ajuste encendido). Este detector no lee nada de la hoja:
 *  - El servicio solo llama a [evaluate] con un vídeo activo en el feed de esa app y si [isArmed].
 *  - Tras un compartido la detección queda en silencio (no se mira ningún clic de esa app) hasta que
 *    el feed vuelve a moverse ([onFeedActivity]): así no se lee nada de la hoja de compartir ni del
 *    destinatario, y además hace de antirrebote (una vez por vídeo).
 *  - De cada clic solo se miran etiquetas del propio botón, sus hijos directos y hasta
 *    [Config.maxAncestors] antepasados pequeños; nada se guarda.
 *
 * Reglas (todas deben cumplirse):
 *  1. El botón está en la columna de acciones de la derecha (centro x ≥ [Config.minCenterX]) y es
 *     pequeño (ancho ≤ [Config.maxWidth] de la pantalla). En las tres apps «compartir» está ahí.
 *  2. No está junto a un campo de texto (el «Enviar» del cuadro de comentarios).
 *  3. Ninguna etiqueta habla de comentarios, mensajes, respuestas o regalos.
 *  4. Alguna etiqueta dice compartir/share (texto, descripción o id con «share»), o «Enviar»/«Send»
 *     como etiqueta corta (Instagram usa el avión de papel).
 */
class ShareDetector(private val config: Config = Config()) {

    data class Config(
        val minCenterX: Float = 0.55f,
        val maxWidth: Float = 0.35f,
        val maxAncestors: Int = 2,
        val maxChildren: Int = 6,
        /** Si el feed no se mueve en este tiempo tras un compartido, se vuelve a escuchar igualmente. */
        val quietMaxMs: Long = 10 * 60_000L,
    )

    data class Result(val isShare: Boolean, val rule: String)

    private val quietSince = HashMap<Platform, Long>()

    /** false mientras dure el silencio tras un compartido: no hay que leer nada del clic. */
    fun isArmed(platform: Platform, now: Long): Boolean {
        val since = quietSince[platform] ?: return true
        if (now - since > config.quietMaxMs) {
            quietSince.remove(platform)
            return true
        }
        return false
    }

    /** El feed se ha movido (el usuario ha vuelto a los vídeos): se puede volver a detectar. */
    fun onFeedActivity(platform: Platform) {
        quietSince.remove(platform)
    }

    fun onShareRecorded(platform: Platform, now: Long) {
        quietSince[platform] = now
    }

    fun evaluate(node: UiNode, screenWidth: Int, screenHeight: Int): Result {
        if (screenWidth <= 0 || screenHeight <= 0) return no("sin tamaño de pantalla")
        val bounds = node.bounds
        if (bounds.isEmpty) return no("sin nodo")
        val maxWidthPx = screenWidth * config.maxWidth
        if (bounds.width > maxWidthPx) return no("demasiado grande para ser un botón")
        if (bounds.centerX < screenWidth * config.minCenterX) return no("fuera de la columna de acciones")
        if (nextToTextField(node)) return no("junto a un campo de texto")

        val sources = buildList {
            add(node)
            node.children.take(config.maxChildren).forEach(::add)
            var ancestor = node.parent
            var level = 0
            while (ancestor != null && level < config.maxAncestors) {
                if (ancestor.bounds.width <= maxWidthPx) add(ancestor)
                ancestor = ancestor.parent
                level++
            }
        }
        val texts = sources.flatMap { listOfNotNull(it.text, it.contentDescription) }
            .map { CategoryClassifier.normalize(it.toString()).trim() }
            .filter { it.isNotEmpty() }
        val ids = sources.map { shortViewId(it.viewId) }.filter { it.isNotEmpty() }

        (texts + ids).firstNotNullOfOrNull { EXCLUDED.find(it)?.value }?.let { return no("excluido ($it)") }
        texts.firstNotNullOfOrNull { STRONG.find(it)?.value }?.let { return yes("texto ($it)") }
        ids.firstOrNull { id -> SHARE_IDS.any { id.contains(it) } }?.let { return yes("id ($it)") }
        texts.firstOrNull { WEAK.containsMatchIn(it) && it.split(' ').size <= 4 }?.let { return yes("texto (enviar)") }
        return no("sin etiqueta de compartir")
    }

    /**
     * ¿Este clic abre una hoja encima del visor? (1.4.1, para esconder las burbujas del envío rápido.) Compartir
     * ([evaluate]) o, en la misma columna de botones y con el mismo tamaño, «Comentar» o «Más opciones». No depende
     * de [isArmed]: un compartir a mano justo después de un envío rápido también abre su hoja.
     *
     * 1.10.0, [platform] TikTok: su botón de comentarios lleva una descripción larga con la cifra («Leer o añadir
     * comentarios. 12,1 mil comentarios»), así que también vale una etiqueta larga que **empiece** así
     * ([LONG_OPENERS]), del botón o de un antepasado pequeño (el clic suele llegar del icono de dentro). En Instagram,
     * igual que antes.
     */
    fun opensSheet(node: UiNode, screenWidth: Int, screenHeight: Int, platform: Platform = Platform.INSTAGRAM): Boolean {
        if (evaluate(node, screenWidth, screenHeight).isShare) return true
        if (screenWidth <= 0 || screenHeight <= 0) return false
        val bounds = node.bounds
        val maxWidthPx = screenWidth * config.maxWidth
        if (bounds.isEmpty || bounds.width > maxWidthPx || bounds.centerX < screenWidth * config.minCenterX) return false
        if (nextToTextField(node)) return false
        val sources = buildList {
            add(node)
            node.children.take(config.maxChildren).forEach(::add)
        }
        if (platform == Platform.TIKTOK) {
            // El clic puede llegar del icono de dentro (pulsable y sin etiqueta): también los antepasados pequeños.
            val near = buildList {
                addAll(sources)
                var ancestor = node.parent
                var level = 0
                while (ancestor != null && level < config.maxAncestors) {
                    if (ancestor.bounds.width <= maxWidthPx) add(ancestor)
                    ancestor = ancestor.parent
                    level++
                }
            }
            val long = near.flatMap { listOfNotNull(it.text, it.contentDescription) }.map { CategoryClassifier.normalize(it.toString()).trim() }
            if (long.any { LONG_OPENERS.containsMatchIn(it) }) return true
        }
        val labels = sources.flatMap { listOfNotNull(it.text, it.contentDescription) }
            .map { CategoryClassifier.normalize(it.toString()).trim() }
            .filter { it.isNotEmpty() && it.split(' ').size <= 4 } +
            sources.map { shortViewId(it.viewId) }.filter { it.isNotEmpty() }
        return labels.any { SHEET_OPENERS.containsMatchIn(it) }
    }

    /**
     * 1.10.5: ¿[node] es un botón pequeño de la columna de acciones de la derecha (las mismas medidas que [opensSheet])? No
     * mira ninguna etiqueta: sirve para que el envío rápido mire si un clic sin etiqueta conocida ha abierto algo encima
     * del visor ([SheetWatch.probeOpened]) sin gastar nada con los toques en el vídeo. No cuenta nada.
     */
    fun inActionColumn(node: UiNode, screenWidth: Int, screenHeight: Int): Boolean {
        if (screenWidth <= 0 || screenHeight <= 0) return false
        val bounds = node.bounds
        return !bounds.isEmpty && bounds.width <= screenWidth * config.maxWidth && bounds.centerX >= screenWidth * config.minCenterX
    }

    /** El botón «Enviar» de un comentario o mensaje está al lado de un campo de texto. */
    private fun nextToTextField(node: UiNode): Boolean {
        var ancestor = node.parent
        repeat(2) {
            val current = ancestor ?: return false
            if (current.children.take(12).any { it.className?.contains("EditText") == true }) return true
            ancestor = current.parent
        }
        return false
    }

    private fun yes(rule: String) = Result(true, rule)
    private fun no(rule: String) = Result(false, rule)

    private companion object {
        /** Sobre texto normalizado (minúsculas, sin tildes). Español, inglés, francés, italiano y portugués. */
        val STRONG = Regex(
            "\\b(?:compartir|comparte|compartelo|compartido|share|shares|sharing|reenviar|forward|" +
                "partager|partage|partages|condividi|condividere|condivisione|compartilhar|compartilhe|partilhar)\\b",
        )
        val WEAK = Regex("\\b(?:enviar|envia|send|envoyer|invia|inviare)\\b")
        val EXCLUDED = Regex(
            "\\b(?:coment\\w*|comment\\w*|mensaje\\w*|message\\w*|messagg\\w*|mensag\\w*|chat\\w*|respond\\w*|respuesta\\w*|" +
                "repl(?:y|ies)|regalo\\w*|gift\\w*)\\b",
        )
        val SHARE_IDS = listOf("share", "reshare", "forward")

        /** Botones del visor que abren una hoja encima: comentarios y más opciones (texto normalizado o id). */
        val SHEET_OPENERS = Regex("(?:\\bcomentar\\b|\\bcomentarios\\b|\\bcomment|\\bmas opciones\\b|\\bmore options\\b|^mas$|^more$|^opciones$|^options$|comment_button|more_button|overflow)")

        /** TikTok 47.7: el botón de comentarios del visor («Leer o añadir comentarios. 12,1 mil comentarios»). */
        val LONG_OPENERS = Regex("^(?:leer o anadir comentarios|read or add comments)\\b")
    }
}
