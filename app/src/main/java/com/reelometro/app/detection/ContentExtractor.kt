package com.reelometro.app.detection

import com.reelometro.app.data.Platform

/**
 * Lee de la pantalla el **creador**, la **descripción** y los **hashtags** del vídeo que se está viendo.
 * Es «best effort»: si la app no expone el texto, devuelve nulls.
 *
 * Solo mira la página visible del feed: dentro del contenedor del feed elige la página que ocupa la
 * pantalla (no las vecinas precargadas) y nunca entra en comentarios, hojas inferiores ni campos de
 * texto. Recorre como mucho [MAX_NODES] nodos.
 */
object ContentExtractor {

    data class VideoInfo(val creator: String?, val caption: String?, val hashtags: List<String>) {
        val isEmpty: Boolean get() = creator == null && caption == null && hashtags.isEmpty()
    }

    const val MAX_CAPTION = 300
    const val MAX_NODES = 300
    private const val MAX_DEPTH = 30
    private const val MAX_HASHTAGS = 15

    private data class TextItem(val text: String, val viewId: String, val fromDescription: Boolean, val bounds: NodeBounds)

    private val WHITESPACE = Regex("\\s+")
    private val HANDLE = Regex("^@([\\p{L}\\p{N}._]{2,30})$")

    /** Nombre de usuario de Instagram tal como se ve en Reels (sin @): minúsculas, cifras, punto y guion bajo. */
    private val INSTAGRAM_USERNAME = Regex("^[a-z0-9._]{2,30}$")
    private val HASHTAG = Regex("#([\\p{L}\\p{N}_]{2,40})")
    private val NUMBER_LIKE = Regex("^[\\d.,\\s]+\\s?(k|m|mil|mill\\.?|m\\.|b|mln)?$", RegexOption.IGNORE_CASE)
    private val MORE_SUFFIX = Regex("\\s*(…|\\.{3})\\s*(ver )?(más|more)$", RegexOption.IGNORE_CASE)

    /** Descripciones del avatar del creador (español e inglés). El grupo 1 es el nombre. */
    private val PROFILE_DESCRIPTIONS = listOf(
        "^(?:foto|imagen) (?:del|de) perfil de (.+)$",
        "^profile (?:picture|photo|image) of (.+)$",
        "^(.+?)'s? profile (?:picture|photo|image)$",
        "^(?:ir al|ver el|abrir el|ir a|ver) (?:perfil|canal) de (.+)$",
        "^go to (.+?)'s? (?:profile|channel)$",
        "^go to channel:? (.+)$",
        "^(?:view|visit|open) (.+?)'s? (?:profile|channel)$",
        "^(?:avatar|perfil|canal) de (.+)$",
        "^(.+?)'s? (?:avatar|channel)$",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    /** Ids (cortos) de nodos con el nombre del creador. Los de TikTok están ofuscados: no aparecen. */
    private val CREATOR_IDS = listOf(
        "username", "user_name", "author", "owner", "creator", "channel_name", "channel_title",
        "profile_name", "nickname", "reel_channel",
    )

    /** Ids (cortos) de nodos con la descripción o el título del vídeo. */
    private val CAPTION_IDS = listOf("caption", "description", "desc", "title", "subtitle", "text_content", "headline")

    /** Subárboles que nunca son del vídeo: comentarios, hojas, teclado. */
    private val SKIP_SUBTREE_IDS = listOf("comment", "bottom_sheet", "keyboard", "composer")

    /**
     * Palabras de botones y etiquetas de la interfaz (texto normalizado, palabras completas): no son ni el
     * creador ni la descripción. «playa» no cae por «play» ni «Mad Max» por «ad».
     */
    private val UI_LABEL = Regex(
        "\\b(?:me gusta|likes?|coment\\w*|comments?|compart\\w*|share|seguir|follow\\w*|siguiendo|suscri\\w*|" +
            "subscri\\w*|remix|guardar|save|enviar|send|mas opciones|more options|sonido|sound|audio|perfil|" +
            "profile|anadir|add|buscar|search|silenciar|mute|pausa|pause|reproducir|play|patrocinado|" +
            "sponsored|publicidad|visitas|views|reproducciones)\\b",
    )

    /** Hashtags genéricos que no dicen nada del contenido. */
    private val GENERIC_HASHTAGS = setOf(
        "fyp", "foryou", "foryoupage", "parati", "paratii", "fy", "fypage", "viral", "viralvideo", "trending",
        "tiktok", "reels", "reel", "shorts", "short", "youtubeshorts", "instagram", "explore", "explorepage",
        "xyzbca", "capcut", "foryourpage",
    )

    /**
     * @param root si [rootIsFeed], el contenedor del feed (se lee solo su página visible); si no, la raíz
     *   de la ventana (se lee todo lo visible salvo comentarios/hojas).
     */
    fun extract(platform: Platform, root: UiNode, screenWidth: Int, screenHeight: Int, rootIsFeed: Boolean): VideoInfo {
        if (screenWidth <= 0 || screenHeight <= 0) return VideoInfo(null, null, emptyList())
        val scope = if (rootIsFeed) visiblePage(root, screenWidth, screenHeight) else root
        val items = collect(scope, screenWidth, screenHeight)
        val creator = findCreator(platform, items, screenWidth, screenHeight)
        val caption = findCaption(items, creator)
        val hashtags = findHashtags(items)
        return VideoInfo(creator, caption, hashtags)
    }

    /**
     * La página del feed que ocupa la pantalla. Un ViewPager2 tiene un RecyclerView interno entre el
     * contenedor y las páginas: se baja un nivel.
     */
    fun visiblePage(container: UiNode, screenWidth: Int, screenHeight: Int): UiNode {
        var pager = container
        val only = pager.children.singleOrNull()
        if (only != null && only.className?.contains("RecyclerView") == true) pager = only
        return pager.children
            .filter { it.isVisible && it.bounds.intersectsScreen(screenWidth, screenHeight) }
            .maxByOrNull { it.bounds.visibleArea(screenWidth, screenHeight) }
            ?: pager
    }

    private fun collect(scope: UiNode, screenWidth: Int, screenHeight: Int): List<TextItem> {
        val out = ArrayList<TextItem>()
        val queue = ArrayDeque<Pair<UiNode, Int>>()
        queue.add(scope to 0)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++
            if (!node.isVisible || !node.bounds.intersectsScreen(screenWidth, screenHeight)) continue
            val id = shortViewId(node.viewId)
            if (SKIP_SUBTREE_IDS.any { id.contains(it) }) continue
            if (node.className?.contains("EditText") == true) continue
            node.text?.let(::clean)?.let { out += TextItem(it, id, fromDescription = false, node.bounds) }
            node.contentDescription?.let(::clean)?.let { out += TextItem(it, id, fromDescription = true, node.bounds) }
            if (depth < MAX_DEPTH) node.children.forEach { queue.add(it to depth + 1) }
        }
        return out
    }

    private fun clean(raw: CharSequence): String? = raw.toString().replace(WHITESPACE, " ").trim().ifEmpty { null }

    // ── Creador ──────────────────────────────────────────────────────────────────────────────────

    private fun findCreator(platform: Platform, items: List<TextItem>, screenWidth: Int, screenHeight: Int): String? {
        // 1. Descripción del avatar: «Foto del perfil de X», «Go to X's channel»…
        for (item in items) {
            if (!item.fromDescription) continue
            val name = PROFILE_DESCRIPTIONS.firstNotNullOfOrNull { it.find(item.text)?.groupValues?.get(1) }
            normalizeCreator(name)?.let { return it }
        }
        // 2. Nodo con id de autor (Instagram, YouTube)
        for (item in items) {
            if (item.fromDescription || CREATOR_IDS.none { item.viewId.contains(it) }) continue
            normalizeCreator(item.text)?.let { return it }
        }
        // 3. «@handle» suelto (TikTok, YouTube Shorts)
        val handles = items.filter { HANDLE.matches(it.text) }
        val handle = handles.firstOrNull { !it.fromDescription } ?: handles.firstOrNull()
        if (handle != null) return normalizeCreator(handle.text)
        // 4. Instagram enseña el usuario sin @, abajo a la izquierda (junto al avatar y «Seguir»).
        if (platform == Platform.INSTAGRAM) {
            items.firstOrNull { item ->
                !item.fromDescription && INSTAGRAM_USERNAME.matches(item.text) && item.text.any { it.isLetter() } &&
                    item.bounds.left < screenWidth / 2 && item.bounds.centerY > screenHeight * 0.55
            }?.let { return normalizeCreator(it.text) }
        }
        return null
    }

    private fun normalizeCreator(raw: String?): String? {
        if (raw == null) return null
        val name = raw.trim()
            .substringBefore(" · ").substringBefore(" • ").substringBefore(", ")
            .removePrefix("@").trim()
            .removeSuffix(".").trim()
        if (name.length !in 2..40 || '#' in name || name.count { it == ' ' } > 3) return null
        if (NUMBER_LIKE.matches(name)) return null
        if (UI_LABEL.matches(CategoryClassifier.normalize(name))) return null
        return name
    }

    // ── Descripción ──────────────────────────────────────────────────────────────────────────────

    private fun findCaption(items: List<TextItem>, creator: String?): String? =
        items.filter { isCaptionCandidate(it, creator) }
            .maxByOrNull(::captionScore)
            ?.text
            ?.replace(MORE_SUFFIX, "")
            ?.trim()
            ?.take(MAX_CAPTION)
            ?.trim()
            ?.ifEmpty { null }

    private fun isCaptionCandidate(item: TextItem, creator: String?): Boolean {
        val text = item.text
        if (text.length < 12 && '#' !in text) return false
        if (NUMBER_LIKE.matches(text) || HANDLE.matches(text)) return false
        if (text.startsWith("♬") || text.startsWith("♪") || text.startsWith("♫")) return false
        if (creator != null && text.removePrefix("@").equals(creator, ignoreCase = true)) return false
        if (PROFILE_DESCRIPTIONS.any { it.matches(text) }) return false
        val normalized = CategoryClassifier.normalize(text)
        if (normalized.startsWith("sonido original") || normalized.startsWith("audio original") ||
            normalized.startsWith("original sound") || normalized.startsWith("original audio")
        ) return false
        // Las descripciones de botones («Me gusta, 12 K», «Compartir») no son la descripción del vídeo.
        if (item.fromDescription && '#' !in text && UI_LABEL.containsMatchIn(normalized)) return false
        return true
    }

    private fun captionScore(item: TextItem): Int {
        var score = item.text.length.coerceAtMost(200)
        if ('#' in item.text) score += 80
        if (CAPTION_IDS.any { item.viewId.contains(it) }) score += 120
        if (item.fromDescription) score /= 2
        return score
    }

    // ── Hashtags ─────────────────────────────────────────────────────────────────────────────────

    private fun findHashtags(items: List<TextItem>): List<String> =
        items.asSequence()
            .flatMap { item -> HASHTAG.findAll(item.text).map { it.groupValues[1].lowercase() } }
            .filter { it !in GENERIC_HASHTAGS }
            .distinct()
            .take(MAX_HASHTAGS)
            .toList()
}
