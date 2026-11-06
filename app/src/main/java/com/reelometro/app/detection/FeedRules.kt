package com.reelometro.app.detection

import com.reelometro.app.data.Platform
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Reglas para decidir si un contenedor que hace scroll es **el feed de vídeos cortos** (un vídeo a
 * pantalla completa; se pasa al siguiente deslizando en vertical) y no el feed normal, los comentarios,
 * un perfil, Explorar, Stories, la portada de YouTube…
 *
 * Las reglas son **datos** ([PlatformRules], una entrada por app en [RULES]) para poder ajustarlas tras
 * calibrar en un móvil real: la pantalla de Calibración muestra, para cada evento, la decisión y la
 * regla que decidió ([Decision.rule]). Guía de ajuste al final del fichero.
 *
 * ⚠️ Es la parte más frágil de la app: TikTok, Instagram y YouTube cambian sus pantallas sin avisar.
 * Los ids y las clases salen de lo que se sabe de versiones públicas recientes de esas apps y **no se
 * han verificado** con esta versión de Reelómetro en un móvil real.
 *
 * Orden de evaluación (la primera regla que decide gana):
 *  1. Pantalla (actividad) excluida, p. ej. LIVE de TikTok.
 *  2. Id «de otra pantalla» (visor de Stories…), aunque el scroll sea horizontal.
 *  3. Clase excluida (texto que se desplaza, webs).
 *  4. Dirección: horizontal o rejilla nunca es el feed.
 *  5. Id excluido (si no coincide ningún id de feed).
 *  6. Tamaño: ancho ≥ [PlatformRules.minWidth] y alto ≥ [PlatformRules.minHeight] de la pantalla.
 *  7. Un vídeo por pantalla: si hay índices, como mucho [PlatformRules.maxVisibleSpan] + 1 visibles.
 *  8. Identificación: id de feed (Instagram, YouTube) o clase de pager (TikTok, ids ofuscados).
 *
 * Además, un contenedor grande que se mueve en vertical y NO es el feed (feed normal, perfil,
 * Explorar…) devuelve [Verdict.OTHER_SCREEN]: el usuario se ha ido a otra pantalla de la app y el
 * vídeo en curso se pausa. Las hojas inferiores (comentarios, compartir) no lo hacen: el vídeo sigue
 * en pantalla detrás.
 */
object FeedRules {

    /** Qué hace el detector con un evento de scroll. */
    enum class Verdict {
        /** Es el feed de vídeos cortos: el [ViewTracker] lo usa para contar. */
        FEED,

        /** Es otra pantalla grande de la misma app (feed normal, perfil, Explorar…): se pausa el vídeo. */
        OTHER_SCREEN,

        /** No dice nada (horizontal, pequeño, hoja de comentarios…): no cambia nada. */
        IGNORED,
    }

    /** Resultado de [evaluate]. [rule] es el texto corto que se ve en Calibración. */
    data class Decision(val verdict: Verdict, val rule: String) {
        val isFeed: Boolean get() = verdict == Verdict.FEED
    }

    /** Cómo rellena el contenedor `fromIndex`/`toIndex` en `TYPE_VIEW_SCROLLED`. */
    enum class IndexStyle {
        /** ViewPager / ViewPager2: from == to == página actual (cambia al soltar el dedo). */
        CURRENT_ITEM,

        /** RecyclerView: primer y último elemento visibles (from != to a mitad de deslizamiento). */
        VISIBLE_RANGE,
    }

    /** Orientación del contenedor, deducida del nodo (CollectionInfo, acciones) o de cómo se ha movido. */
    enum class Orientation { VERTICAL, HORIZONTAL, GRID }

    /** Tipo de ventana de un `TYPE_WINDOW_STATE_CHANGED` (por su `className`). */
    enum class WindowKind {
        /** Una actividad: el usuario ha cambiado de pantalla. */
        ACTIVITY,

        /** Diálogo, hoja, menú o popup encima de la pantalla actual (el vídeo sigue detrás). */
        OVERLAY,

        /** No se sabe (vistas genéricas, cambios de panel). */
        OTHER,
    }

    /** Un evento de scroll, ya sin tipos de Android. */
    data class ScrollSample(
        val platform: Platform,
        /** Id completo del nodo que hace scroll, o null. */
        val viewId: String?,
        /** Clase del evento (la que declara la vista: ViewPager2 se declara "ViewPager"). */
        val className: String?,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val screenWidth: Int,
        val screenHeight: Int,
        val fromIndex: Int = -1,
        val toIndex: Int = -1,
        val itemCount: Int = -1,
        /** Desplazamiento del evento en px (Android 9+), o null si no se conoce. */
        val deltaX: Int? = null,
        val deltaY: Int? = null,
        /** Actividad actual de la app (último `TYPE_WINDOW_STATE_CHANGED`), si se conoce. */
        val screen: String? = null,
        val orientation: Orientation? = null,
    )

    /**
     * Reglas de una app. Los fragmentos de id se comparan con el id **corto** en minúsculas
     * (`clips_viewer_view_pager`); los de clase y actividad, con el nombre completo (distingue mayúsculas).
     */
    data class PlatformRules(
        /** Fragmentos de id del contenedor del feed. Si coincide, gana a [excludedIds] y [exitIds]. */
        val feedIds: List<String>,
        /** Si es true, sin id de feed no se acepta nunca (Instagram, YouTube). */
        val requireFeedId: Boolean,
        /** Fragmentos de id que nunca son el feed. */
        val excludedIds: List<String>,
        /** Fragmentos de id que significan «estás en otra pantalla» aunque se muevan en horizontal. */
        val exitIds: List<String>,
        /** Clases aceptadas cuando no se exige id (TikTok). */
        val feedClasses: List<String>,
        /** Clases que nunca son el feed. */
        val excludedClasses: List<String>,
        /** Fragmentos de la actividad en la que el feed no cuenta (y salir a ella pausa el vídeo). */
        val excludedScreens: List<String>,
        val minWidth: Float = 0.9f,
        val minHeight: Float = 0.75f,
        /** to − from máximo con el vídeo en pantalla (1 = dos páginas a medio deslizar). */
        val maxVisibleSpan: Int = 1,
        /**
         * Segunda señal por contenido ([ShortSignature]): el servicio lee autor y título del vídeo visible
         * tras cada scroll y cada pocos segundos. Cuenta los deslizamientos que no llegan como scroll y une
         * los vídeos contados dos veces. Cuesta una lectura del árbol cada pocos segundos con un vídeo activo.
         */
        val contentCheck: Boolean = false,
    )

    /** Clases cuyo scroll nunca es el feed: texto que se desplaza dentro de una vista, webs. */
    private val DEFAULT_EXCLUDED_CLASSES = listOf("TextView", "EditText", "WebView")

    val RULES: Map<Platform, PlatformRules> = mapOf(
        // En el código de Instagram «clips» = Reels y «reel» = Stories (nombre histórico de las historias).
        Platform.INSTAGRAM to PlatformRules(
            feedIds = listOf(
                "clips_viewer",          // clips_viewer_view_pager / clips_viewer_container: pestaña Reels y
                                         // Reels abiertos desde Explorar, un perfil o un mensaje directo
                "clips_view_pager",
                "clips_video_container",
                "clips_pager",
                "clips_swipe_refresh",   // contenedor «desliza para recargar» del visor
                "root_clips",
            ),
            // Sin id no hay forma fiable de distinguir el visor de Reels del feed normal (que también
            // tiene vídeos a todo lo ancho): mejor no contar que contar mal.
            requireFeedId = true,
            excludedIds = listOf(
                "reels_tray", "tray", "story", "stories", "comment", "explore", "profile", "grid",
                "direct", "thread", "inbox", "search", "carousel", "sheet",
            ),
            // Visor de Stories: pasa de una a otra en horizontal, pero es otra pantalla.
            exitIds = listOf("reel_viewer", "story_viewer", "stories_viewer"),
            feedClasses = emptyList(),
            excludedClasses = DEFAULT_EXCLUDED_CLASSES,
            excludedScreens = emptyList(),
            minHeight = 0.7f,
        ),
        // En YouTube «reel» = Shorts.
        Platform.YOUTUBE to PlatformRules(
            feedIds = listOf(
                "reel_recycler",         // el pager vertical de Shorts (RecyclerView)
                "reel_watch",            // reel_watch_player / reel_watch_fragment…
                "reel_player",           // reel_player_page_container…
                "reel_pager",
                "shorts_pager",
                "shorts_player",
                "shorts_container",
            ),
            requireFeedId = true,        // la portada y los resultados también son listas verticales grandes
            excludedIds = listOf(
                "shelf",                 // estantería horizontal de Shorts en la portada
                "results",               // portada, búsqueda, suscripciones
                "comment", "engagement_panel", "browse", "search", "playlist", "channel", "library",
                "subscription", "pivot_bar", "chip", "related",
            ),
            exitIds = listOf("watch_list"),   // lista bajo el reproductor normal
            feedClasses = emptyList(),
            excludedClasses = DEFAULT_EXCLUDED_CLASSES,
            excludedScreens = emptyList(),
            minHeight = 0.7f,
            // El id ya dice que es el feed de Shorts. Al volver hacia arriba pueden asomar tres páginas a
            // la vez (la vecina precargada toca el borde) y eso no es una lista: no debe pausar el vídeo.
            maxVisibleSpan = 2,
            // Primera prueba en un móvil real (nov 2026): el recuento de Shorts no cuadraba. Además del
            // scroll de reel_recycler se comprueba el contenido (autor + título) del vídeo visible.
            contentCheck = true,
        ),
        // TikTok ofusca los ids (cambian en cada versión): se decide por clase + tamaño + dirección + un
        // solo vídeo visible + actividad. El feed principal (Para ti, Siguiendo, Amigos…) y el detalle
        // abierto desde un perfil, la búsqueda o un enlace son pagers verticales a pantalla completa.
        // Comentarios y compartir son hojas que no llegan a 3/4 de la pantalla; perfiles, búsqueda,
        // Discover, bandeja y tienda son listas o rejillas (varios elementos visibles a la vez).
        Platform.TIKTOK to PlatformRules(
            feedIds = emptyList(),
            requireFeedId = false,
            excludedIds = listOf("comment", "search", "profile", "live", "inbox", "notice", "message", "chat"),
            exitIds = emptyList(),
            // "ViewPager" cubre ViewPager, ViewPager2 (se declara ViewPager) y VerticalViewPager.
            feedClasses = listOf("ViewPager", "VerticalPager", "RecyclerView"),
            excludedClasses = DEFAULT_EXCLUDED_CLASSES,
            // LIVE: también es un pager vertical a pantalla completa, pero no son vídeos cortos.
            excludedScreens = listOf(".live.", "livesdk", "LivePlay", "LiveRoom", "LiveBroadcast", "LiveDummy", "LiveActivity"),
            minHeight = 0.75f,
        ),
    )

    /** Un contenedor de otra pantalla «grande»: a todo lo ancho, al menos medio alto y empezando arriba. */
    private const val OTHER_MIN_HEIGHT = 0.5f
    private const val OTHER_MAX_TOP = 0.2f

    /** Hoja inferior (comentarios, compartir): empieza por debajo del 20 % y llega casi abajo. */
    private const val SHEET_MIN_TOP = 0.2f
    private const val SHEET_MIN_BOTTOM = 0.85f

    /** Pistas de que una ventana es un diálogo/hoja/menú encima de la pantalla (no una pantalla nueva). */
    private val OVERLAY_CLASS_HINTS = listOf("Dialog", "BottomSheet", "Popup", "Sheet", "Menu", "Toast", "Tooltip", "Snackbar", "Drawer")

    fun rulesFor(platform: Platform): PlatformRules = RULES.getValue(platform)

    fun evaluate(s: ScrollSample): Decision {
        if (s.screenWidth <= 0 || s.screenHeight <= 0) return ignored("sin tamaño de pantalla")
        if (s.width <= 0 || s.height <= 0) return ignored("sin nodo (no se pudo leer)")
        val rules = rulesFor(s.platform)
        val id = shortViewId(s.viewId)
        val cls = s.className.orEmpty()
        val dx = s.deltaX ?: 0
        val dy = s.deltaY ?: 0
        val moved = dx != 0 || dy != 0
        val movedVertically = dy != 0 && abs(dy) >= abs(dx)
        val movedHorizontally = dx != 0 && abs(dx) > abs(dy)
        val w = s.width.toFloat() / s.screenWidth
        val h = s.height.toFloat() / s.screenHeight
        val top = s.top.toFloat() / s.screenHeight
        val bottom = (s.top + s.height).toFloat() / s.screenHeight
        val bigList = w >= rules.minWidth && h >= OTHER_MIN_HEIGHT && top <= OTHER_MAX_TOP

        fun other(reason: String) = Decision(Verdict.OTHER_SCREEN, "otra pantalla · $reason")
        fun otherIfBig(reason: String) = if (movedVertically && bigList) other(reason) else ignored(reason)

        // 1. Actividad excluida (LIVE…)
        excludedScreen(s.platform, s.screen)?.let { fragment ->
            val reason = "pantalla excluida ($fragment)"
            return if (moved) other(reason) else ignored(reason)
        }
        val feedId = rules.feedIds.firstOrNull { id.contains(it) }
        // 2. Ids que delatan otra pantalla aunque se muevan en horizontal (visor de Stories…)
        if (feedId == null) {
            rules.exitIds.firstOrNull { id.contains(it) }?.let { fragment ->
                val reason = "id de otra pantalla ($fragment)"
                return if (moved) other(reason) else ignored(reason)
            }
        }
        // 3. Clases que nunca son el feed
        rules.excludedClasses.firstOrNull { cls.contains(it) }?.let { return ignored("clase excluida (${simpleClassName(cls)})") }
        // 4. Dirección
        if (movedHorizontally || s.orientation == Orientation.HORIZONTAL) return ignored("horizontal")
        if (s.orientation == Orientation.GRID) return otherIfBig("rejilla")
        // 5. Ids excluidos
        if (feedId == null) {
            rules.excludedIds.firstOrNull { id.contains(it) }?.let { return otherIfBig("id excluido ($it)") }
        }
        // 6. Tamaño
        if (w < rules.minWidth) return ignored("estrecho (${pct(w)} del ancho)")
        if (h < rules.minHeight) {
            val sheet = top >= SHEET_MIN_TOP && bottom >= SHEET_MIN_BOTTOM
            return otherIfBig(if (sheet) "hoja inferior (${pct(h)} del alto)" else "bajo (${pct(h)} del alto)")
        }
        // 7. Un vídeo por pantalla
        if (s.fromIndex >= 0 && s.toIndex >= s.fromIndex && s.toIndex - s.fromIndex > rules.maxVisibleSpan) {
            return otherIfBig("lista o rejilla (${s.toIndex - s.fromIndex + 1} visibles)")
        }
        // 8. Identificación
        if (feedId != null) return Decision(Verdict.FEED, "id ($feedId)")
        if (rules.requireFeedId) {
            return otherIfBig(if (id.isEmpty()) "sin id (esta app exige id de feed)" else "id sin regla de feed ($id)")
        }
        rules.feedClasses.firstOrNull { cls.contains(it) }?.let { return Decision(Verdict.FEED, "clase (${simpleClassName(cls)})") }
        return otherIfBig("clase no reconocida (${simpleClassName(cls)})")
    }

    /** Fragmento de [PlatformRules.excludedScreens] que coincide con la actividad, o null. */
    fun excludedScreen(platform: Platform, screen: String?): String? {
        if (screen.isNullOrEmpty()) return null
        return rulesFor(platform).excludedScreens.firstOrNull { screen.contains(it) }
    }

    /**
     * Clasifica la ventana de un `TYPE_WINDOW_STATE_CHANGED` por su clase. Las actividades casi siempre
     * acaban en «Activity»; el servicio confirma las demás con el PackageManager.
     */
    fun windowKind(className: String?): WindowKind = when {
        className.isNullOrBlank() -> WindowKind.OTHER
        className.endsWith("Activity") -> WindowKind.ACTIVITY
        OVERLAY_CLASS_HINTS.any { className.contains(it) } -> WindowKind.OVERLAY
        else -> WindowKind.OTHER
    }

    /** ViewPager/ViewPager2 informan de la página actual; RecyclerView, del rango visible. */
    fun indexStyleFor(className: String?): IndexStyle =
        if (className?.contains("Pager") == true) IndexStyle.CURRENT_ITEM else IndexStyle.VISIBLE_RANGE

    private fun ignored(reason: String) = Decision(Verdict.IGNORED, reason)

    private fun pct(fraction: Float) = "${(fraction * 100).roundToInt()} %"
}

/*
 * ─── Guía de ajuste con la pantalla de Calibración ────────────────────────────────────────────────
 *
 * 1. Abre Calibración (Ajustes › Acerca de › 7 toques en la versión) y activa CAPTURAR.
 * 2. En la app a calibrar, haz cada recorrido despacio: 5 deslizamientos en el feed de vídeos cortos,
 *    rebote (deslizar a medias y soltar), volver atrás, abrir y cerrar comentarios, ir al perfil del
 *    creador, al feed normal / portada, a Explorar o la búsqueda, y salir de la app.
 * 3. Vuelve a Calibración y mira los eventos SCROLL:
 *    - En el feed de vídeos cortos debe salir ✓ con «id (…)» o «clase (…)». Si sale ✗:
 *        · «sin id (esta app exige id de feed)» o «id sin regla de feed (xxx)» → añade un fragmento de
 *          ese id a `feedIds` (Instagram/YouTube).
 *        · «clase no reconocida (Xxx)» → añade la clase a `feedClasses` (TikTok).
 *        · «bajo (68 % del alto)» → baja `minHeight` (no por debajo de 0,65: los comentarios rondan 0,6–0,7).
 *        · «lista o rejilla (3 visibles)» con el feed → sube `maxVisibleSpan` solo si el feed muestra
 *          trozos de 3 vídeos a la vez (raro).
 *    - Fuera del feed debe salir ✗. Si algo del feed normal, comentarios o un perfil sale ✓, añade su
 *      id a `excludedIds` (o su actividad a `excludedScreens` si es una pantalla aparte, como LIVE).
 * 4. Mira VIDEO_START / VIDEO_END / VIDEO_DISCARD: cada deslizamiento real = un VIDEO_START; los rebotes
 *    deben acabar en VIDEO_DISCARD o no generar nada. Si un deslizamiento cuenta dos vídeos, comprueba
 *    `fromIndex/toIndex` (CURRENT_ITEM vs VISIBLE_RANGE en [FeedRules.indexStyleFor]).
 * 5. Exporta el registro (COMPARTIR LOG) y guárdalo junto a la versión de cada app (sale en la cabecera)
 *    antes de cambiar reglas: así se puede comparar tras una actualización de TikTok/Instagram/YouTube.
 * 6. Con `contentCheck` (YouTube) mira también los INFO: «cambio de vídeo sin scroll» debe salir solo cuando
 *    deslizaste y el scroll no contó; «vídeo repetido» solo cuando un mismo Short se contó dos veces. Si salen
 *    de más, la lectura del autor/título no es estable (ver ContentExtractor y ShortSignature).
 * 7. Tras cualquier cambio: `./gradlew testDebugUnitTest` (FeedRulesTest documenta los casos conocidos).
 */
