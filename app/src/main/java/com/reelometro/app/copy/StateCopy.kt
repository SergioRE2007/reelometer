package com.reelometro.app.copy

import com.reelometro.app.game.FlapMood

/** Estados genéricos **cargando / vacío / error** (`FlapLoader`, `EmptyState`, `ErrorState`). */
object StateCopy {

    /** Etiqueta del `FlapLoader`. */
    const val LOADING_LABEL = "CONTANDO"

    /** contentDescription mientras carga. */
    const val LOADING_DESCRIPTION = A11y.LOADING

    /** Fichas en blanco del marcador. */
    const val PLACEHOLDER = "– – –"

    const val RETRY = Buttons.RETRY

    /** Frase opcional bajo el `FlapLoader`. */
    fun loading(seed: Long): String = pick(
        seed, "state.loading",
        "Haciendo cuentas.",
        "Sumando.",
        "Contando, que es lo mío.",
    )

    /** Vacío genérico (cuando la pantalla no tiene uno propio). */
    fun empty(seed: Long): String = pick(
        seed, "state.empty",
        "Nada por aquí. De momento.",
        "Todavía no hay nada que contar.",
        "Aquí no hay nada. Que conste en acta.",
    )

    /** Error genérico (va con [RETRY]). */
    fun error(seed: Long): String = pick(
        seed, "state.error",
        "Algo ha fallado al hacer las cuentas.",
        "No he podido cargar esto. A la segunda suele ir.",
        "Error al leer los datos. Vuelve a intentarlo.",
    )
}

/** Textos del **widget** de Hoy (Glance). El nombre y la descripción del selector están en strings.xml. */
object WidgetCopy {

    const val LABEL = Labels.TODAY

    /** Con el servicio desconectado. */
    const val NO_SIGNAL = Labels.NO_SIGNAL

    /** Dígitos en blanco mientras no hay datos. */
    const val PLACEHOLDER = StateCopy.PLACEHOLDER

    /** «47 MIN». */
    fun time(totalMs: Long): String = Words.durationLabel(totalMs)

    /** contentDescription: «Hoy: 212 vídeos, 47 minutos.». */
    fun description(count: Int, totalMs: Long): String = "Hoy: ${Words.videos(count)}, ${Words.duration(totalMs)}."

    /** Con objetivo: «Hoy: 212 vídeos, 47 minutos. 62 vídeos por encima del objetivo de 150.». */
    fun description(count: Int, totalMs: Long, goal: Int?): String =
        if (goal == null || goal <= 0) {
            description(count, totalMs)
        } else {
            "${description(count, totalMs)} ${TodayCopy.goalStatusDescription(count, goal)}."
        }

    /** Frente al objetivo, igual que en Hoy: «QUEDAN 38», «EN EL LÍMITE», «+62 PASADO». */
    fun goalStatus(count: Int, goal: Int): String = TodayCopy.goalStatus(count, goal)

    const val DESCRIPTION_OFF = "Reelómetro, sin señal: el servicio está desconectado."

    /** Con Flap a la vista (tamaño grande, modo pasaporte): «Hoy: 12 vídeos, 9 minutos. Flap: en forma.». */
    fun withFlap(description: String, mood: FlapMood): String =
        "$description Flap: ${PassportCopy.moodLabel(mood).lowercase()}."
}

/** Textos mínimos de la pantalla oculta de **Calibración** (consola en mono). */
object CalibrationCopy {

    const val TITLE = "CALIBRACIÓN"

    /** Titular de la pantalla. */
    const val HEADLINE = "Calibración"

    /** Botones de la consola (TalkBack). */
    const val COPY_DESCRIPTION = "Copiar el log al portapapeles"
    const val SHARE_DESCRIPTION = "Compartir el log de calibración"
    const val CLEAR_DESCRIPTION = "Vaciar el log"

    const val INTRO =
        "Herramienta para ajustar la detección. Lo que se captura aquí se queda en este móvil y se borra al limpiar."

    const val SERVICE = "SERVICIO"
    const val CAPTURE = "CAPTURAR"
    const val FILTER_PLATFORM = "PLATAFORMA"
    const val FILTER_TYPE = "TIPO"

    /** «TODAS» (plataformas). */
    const val ALL_PLATFORMS = "TODAS"

    /** «TODOS» (tipos de evento). */
    const val ALL_TYPES = "TODOS"

    const val FEED_YES = "FEED ✓"
    const val FEED_NO = "FEED ✗"

    /** Decisión de las demás reglas (botón de compartir…). */
    const val MARK_YES = "✓"
    const val MARK_NO = "✗"

    /** Regla que decidió: «[id (clips_viewer)]». */
    fun rule(rule: String): String = "[$rule]"
    const val VIDEO_DETECTED = "VÍDEO"

    const val EMPTY = "Sin eventos. Abre una de las apps y desliza."
    const val PAUSED = "Captura en pausa. Activa CAPTURAR para ver eventos."

    const val COPY = Buttons.COPY
    const val SHARE_LOG = Buttons.SHARE_LOG
    const val CLEAR = Buttons.CLEAR

    const val COPIED = "Log copiado."
    const val CLEARED = "Log vacío."
    const val SHARE_TITLE = "Compartir log de calibración"

    /** «128 EVENTOS», «1 EVENTO». */
    fun events(count: Int): String = Words.upper(Words.count(count, "evento", "eventos"))

    /** Activado en Android pero aún sin conectar (el sistema tarda un momento tras arrancar la app). */
    const val SERVICE_WAITING = "ACTIVADO, SIN CONECTAR"

    const val LAST_EVENT = "ÚLTIMO EVENTO"
    const val LAST_VIDEO = Labels.LAST_VIDEO

    /** Sin dato todavía. */
    const val NONE = "—"

    /** Filtro de tipo: grupos de eventos. */
    const val TYPE_SCROLL = "SCROLL"
    const val TYPE_WINDOW = "VENTANA"
    const val TYPE_VIDEO = VIDEO_DETECTED
    const val TYPE_SHARE = "COMPARTIR"

    /** contentDescription del interruptor. */
    const val CAPTURE_DESCRIPTION = "Capturar eventos"

    /** Con filtros que no dejan nada. */
    const val FILTER_EMPTY = "Ningún evento con estos filtros."

    /** Estado vacío: cómo se usa en un móvil de verdad (en el emulador no hay TikTok). */
    const val HOW_TO_TITLE = "Cómo se usa"

    val HOW_TO: List<Step> = listOf(
        Step(1, "Deja esta pantalla abierta, con CAPTURAR activado, y sal con el botón de inicio."),
        Step(
            2,
            "Abre TikTok, Instagram o YouTube y haz el recorrido despacio: cinco vídeos seguidos, un rebote, " +
                "los comentarios, un perfil, el feed normal y salir.",
        ),
        Step(3, "Vuelve aquí. En el feed de vídeos cortos, cada deslizamiento debe salir con FEED ✓ y un VIDEO_START."),
        Step(4, "Fuera del feed, FEED ✗. Si algo no cuadra, comparte el log: la guía de ajuste está al final de FeedRules."),
    )

    /** Vídeo que todavía se está viendo (sin duración). */
    const val ON_SCREEN = "en pantalla"

    /** Lista en vivo, cabecera. */
    const val LIVE = "EN VIVO"

    /** Línea de un vídeo guardado en la base de datos: «VÍDEO #123045». */
    fun videoTitle(shortId: String): String = "$VIDEO_DETECTED $shortId"

    /** «1 compartido», «3 compartidos». */
    fun shares(count: Int): String = Words.count(count, "compartido", "compartidos")
}
