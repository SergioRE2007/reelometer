package com.reelometro.app.copy

import com.reelometro.app.R
import com.reelometro.app.game.FlapMood
import com.reelometro.app.stats.Format

/**
 * Estados genéricos **cargando / vacío / error** (`FlapLoader`, `EmptyState`, `ErrorState`).
 * Las palabras de este archivo están en `res/values/copy_state.xml`.
 */
object StateCopy {

    /** Etiqueta del `FlapLoader`. */
    val LOADING_LABEL: String get() = str(R.string.state_loading_label)

    /** contentDescription mientras carga. */
    val LOADING_DESCRIPTION: String get() = A11y.LOADING

    /** Fichas en blanco del marcador (símbolos, no palabras: igual en todos los idiomas). */
    const val PLACEHOLDER = "– – –"

    val RETRY: String get() = Buttons.RETRY

    /** Frase opcional bajo el `FlapLoader`. */
    fun loading(seed: Long): String = pick(seed, "state.loading", R.array.state_loading)

    /** Vacío genérico (cuando la pantalla no tiene uno propio). */
    fun empty(seed: Long): String = pick(seed, "state.empty", R.array.state_empty)

    /** Error genérico (va con [RETRY]). */
    fun error(seed: Long): String = pick(seed, "state.error", R.array.state_error)
}

/** Textos del **widget** de Hoy (Glance). El nombre y la descripción del selector están en strings.xml. */
object WidgetCopy {

    val LABEL: String get() = Labels.TODAY

    /** Con el servicio desconectado. */
    val NO_SIGNAL: String get() = Labels.NO_SIGNAL

    /** Dígitos en blanco mientras no hay datos. */
    const val PLACEHOLDER = StateCopy.PLACEHOLDER

    /** «47 MIN». */
    fun time(totalMs: Long): String = Words.durationLabel(totalMs)

    /** contentDescription: «Hoy: 212 vídeos, 47 minutos.». */
    fun description(count: Int, totalMs: Long): String =
        str(R.string.state_widget_description, Words.videos(count), Words.duration(totalMs))

    /** Con objetivo: «Hoy: 212 vídeos, 47 minutos. 62 vídeos por encima del objetivo de 150.». */
    fun description(count: Int, totalMs: Long, goal: Int?): String =
        if (goal == null || goal <= 0) {
            description(count, totalMs)
        } else {
            str(R.string.state_widget_description_goal, description(count, totalMs), TodayCopy.goalStatusDescription(count, goal))
        }

    /** Frente al objetivo, igual que en Hoy: «QUEDAN 38», «EN EL LÍMITE», «+62 PASADO». */
    fun goalStatus(count: Int, goal: Int): String = TodayCopy.goalStatus(count, goal)

    val DESCRIPTION_OFF: String get() = str(R.string.state_widget_description_off)

    /** Con Flap a la vista (tamaño grande, modo pasaporte): «Hoy: 12 vídeos, 9 minutos. Flap: en forma.». */
    fun withFlap(description: String, mood: FlapMood): String =
        str(R.string.state_widget_with_flap, description, PassportCopy.moodLabel(mood).lowercase())
}

/** Textos mínimos de la pantalla oculta de **Calibración** (consola en mono). */
object CalibrationCopy {

    val TITLE: String get() = str(R.string.state_calibration_title)

    /** Titular de la pantalla. */
    val HEADLINE: String get() = str(R.string.state_calibration_headline)

    /** Botones de la consola (TalkBack). */
    val COPY_DESCRIPTION: String get() = str(R.string.state_calibration_copy_description)
    val SHARE_DESCRIPTION: String get() = str(R.string.state_calibration_share_description)
    val CLEAR_DESCRIPTION: String get() = str(R.string.state_calibration_clear_description)

    val INTRO: String get() = str(R.string.state_calibration_intro)

    val SERVICE: String get() = str(R.string.state_calibration_service)
    val CAPTURE: String get() = str(R.string.state_calibration_capture)
    val FILTER_PLATFORM: String get() = str(R.string.state_calibration_filter_platform)
    val FILTER_TYPE: String get() = str(R.string.state_calibration_filter_type)

    /** «TODAS» (plataformas). */
    val ALL_PLATFORMS: String get() = str(R.string.state_calibration_all_platforms)

    /** «TODOS» (tipos de evento). */
    val ALL_TYPES: String get() = str(R.string.state_calibration_all_types)

    val FEED_YES: String get() = str(R.string.state_calibration_feed_yes)
    val FEED_NO: String get() = str(R.string.state_calibration_feed_no)

    /** Decisión de las demás reglas (botón de compartir…). Símbolos: iguales en todos los idiomas. */
    const val MARK_YES = "✓"
    const val MARK_NO = "✗"

    /** Regla que decidió: «[id (clips_viewer)]». */
    fun rule(rule: String): String = "[$rule]"
    val VIDEO_DETECTED: String get() = str(R.string.state_calibration_video_detected)

    val EMPTY: String get() = str(R.string.state_calibration_empty)
    val PAUSED: String get() = str(R.string.state_calibration_paused)

    val COPY: String get() = Buttons.COPY
    val SHARE_LOG: String get() = Buttons.SHARE_LOG
    val CLEAR: String get() = Buttons.CLEAR

    val COPIED: String get() = str(R.string.state_calibration_copied)
    val CLEARED: String get() = str(R.string.state_calibration_cleared)
    val SHARE_TITLE: String get() = str(R.string.state_calibration_share_title)

    /** «128 EVENTOS», «1 EVENTO». */
    fun events(count: Int): String = plural(R.plurals.state_calibration_events, count, Format.integer(count))

    /** Activado en Android pero aún sin conectar (el sistema tarda un momento tras arrancar la app). */
    val SERVICE_WAITING: String get() = str(R.string.state_calibration_service_waiting)

    val LAST_EVENT: String get() = str(R.string.state_calibration_last_event)
    val LAST_VIDEO: String get() = Labels.LAST_VIDEO

    /** Sin dato todavía (símbolo). */
    const val NONE = "—"

    /** Filtro de tipo: grupos de eventos. */
    val TYPE_SCROLL: String get() = str(R.string.state_calibration_type_scroll)
    val TYPE_WINDOW: String get() = str(R.string.state_calibration_type_window)
    val TYPE_VIDEO: String get() = VIDEO_DETECTED
    val TYPE_SHARE: String get() = str(R.string.state_calibration_type_share)

    /** contentDescription del interruptor. */
    val CAPTURE_DESCRIPTION: String get() = str(R.string.state_calibration_capture_description)

    /** Con filtros que no dejan nada. */
    val FILTER_EMPTY: String get() = str(R.string.state_calibration_filter_empty)

    /** Estado vacío: cómo se usa en un móvil de verdad (en el emulador no hay TikTok). */
    val HOW_TO_TITLE: String get() = str(R.string.state_calibration_how_to_title)

    val HOW_TO: List<Step>
        get() = strArray(R.array.state_calibration_how_to).mapIndexed { i, text -> Step(i + 1, text) }

    /** Vídeo que todavía se está viendo (sin duración). */
    val ON_SCREEN: String get() = str(R.string.state_calibration_on_screen)

    /** Lista en vivo, cabecera. */
    val LIVE: String get() = str(R.string.state_calibration_live)

    /** Línea de un vídeo guardado en la base de datos: «VÍDEO #123045». */
    fun videoTitle(shortId: String): String = str(R.string.state_calibration_video_title, shortId)

    /** «1 compartido», «3 compartidos». */
    fun shares(count: Int): String = plural(R.plurals.state_calibration_shares, count, Format.integer(count))
}
