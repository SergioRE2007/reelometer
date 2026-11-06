package com.reelometro.app.copy

/**
 * Etiquetas cortas en MAYÚSCULAS (estilo `label`/`labelL`, siempre con tracking). Úsalas tal cual:
 * ya vienen en mayúsculas y con tildes («DÍA», «VÍDEOS»).
 */
object Labels {
    const val VIDEOS = "VÍDEOS"
    const val VIDEO = "VÍDEO"
    const val TIME = "TIEMPO"
    const val TODAY = "HOY"
    const val YESTERDAY = "AYER"
    const val GOAL = "OBJETIVO"
    const val NO_SIGNAL = "SIN SEÑAL"
    const val PEAK_HOUR = "HORA PUNTA"
    const val WEAK_DAY = "DÍA DÉBIL"
    const val WORST_DAY = "PEOR DÍA"
    const val STREAK = "RACHA"
    const val CURRENT_STREAK = "RACHA ACTUAL"
    const val BEST_STREAK = "MEJOR RACHA"
    const val PER_VIDEO = "POR VÍDEO"
    const val SHARES = "COMPARTIDOS"
    const val DAILY_AVERAGE = "MEDIA DIARIA"
    const val ACTIVE_DAYS = "DÍAS ACTIVOS"
    const val FIRST_VIDEO = "PRIMER VÍDEO"
    const val LAST_VIDEO = "ÚLTIMO VÍDEO"
    const val PLATFORMS = "PLATAFORMAS"
    const val CATEGORIES = "CATEGORÍAS"
    const val CREATORS = "CREADORES"
    const val TOP_CREATOR = "CREADOR N.º 1"
    const val TOP_CATEGORY = "CATEGORÍA N.º 1"
    const val EQUIVALENCES = "CON ESE TIEMPO"
    const val SINCE_START = "DESDE EL PRINCIPIO"
    const val CONNECTED = "CONECTADO"
    const val DISCONNECTED = "DESCONECTADO"
    const val NOT_INSTALLED = "NO INSTALADA"
    const val LOCAL_STAMP = "100 % LOCAL"
    const val WEEK = "SEMANA"
    const val MONTH = "MES"
    const val YEAR = "AÑO"
    const val ALL = "SIEMPRE"
    const val YES = "SÍ"
    const val NO = "NO"

    // Pestañas de la barra inferior.
    const val TAB_TODAY = "HOY"
    const val TAB_STATS = "ESTADÍSTICAS"
    const val TAB_WRAPPED = "RESUMEN"
    const val TAB_PASSPORT = "PASAPORTE"
}

/** Textos de botón (estilo `button`, MAYÚSCULAS). Verbos directos. */
object Buttons {
    const val CONNECT = "CONECTAR"
    const val RETRY = "REINTENTAR"
    const val SHARE = "COMPARTIR"
    const val SAVE = "GUARDAR"
    const val NEXT = "SIGUIENTE"
    const val START = "EMPEZAR"
    const val ACCEPT_AND_ENABLE = "ACEPTAR Y ACTIVAR"
    const val NOT_NOW = "AHORA NO"
    const val OPEN_SETTINGS = "ABRIR AJUSTES"
    const val OPEN_ACCESSIBILITY = "ABRIR ACCESIBILIDAD"
    const val OPEN_APP_INFO = "ABRIR INFORMACIÓN DE LA APP"
    const val ALLOW_NOTIFICATIONS = "PERMITIR AVISOS"
    const val CANCEL = "CANCELAR"
    const val CLOSE = "CERRAR"
    const val DELETE_ALL = "BORRAR TODO"
    const val EXPORT_CSV = "EXPORTAR CSV"
    const val SEE_SUMMARY = "VER RESUMEN"
    const val SEE_PERMISSION = "VER EL PERMISO"
    const val REMOVE_GOAL = "QUITAR OBJETIVO"
    const val SET_GOAL = "PONER OBJETIVO"
    const val DONE = "HECHO"
    const val BACK = "ATRÁS"
    const val COPY = "COPIAR"
    const val SHARE_LOG = "COMPARTIR LOG"
    const val CLEAR = "LIMPIAR"
}

/**
 * Descripciones para TalkBack (`contentDescription`) de iconos y acciones. En frase normal,
 * diciendo qué hace el botón.
 */
object A11y {
    const val SETTINGS = "Ajustes"

    /** Pestañas, en minúsculas para que TalkBack las lea bien. */
    const val TAB_TODAY = "Hoy"
    const val TAB_STATS = "Estadísticas"
    const val TAB_WRAPPED = "Resumen"
    const val TAB_PASSPORT = "Pasaporte"
    const val SHARE_SUMMARY = "Compartir resumen"
    const val SAVE_IMAGE = "Guardar imagen"
    const val BACK = "Atrás"
    const val CLOSE = "Cerrar"
    const val PREVIOUS_PERIOD = "Periodo anterior"
    const val NEXT_PERIOD = "Periodo siguiente"
    const val PAUSE = "Pausar"
    const val RESUME = "Reanudar"
    const val OPEN_DAY = "Abrir el detalle del día"
    const val LOADING = "Cargando"
    const val DECREASE = "Menos"
    const val INCREASE = "Más"

    /** «Diapositiva 3 de 10». */
    fun slide(index: Int, total: Int): String = "Diapositiva $index de $total"

    const val NEXT_SLIDE = "Diapositiva siguiente"
    const val PREVIOUS_SLIDE = "Diapositiva anterior"
    const val OPEN_SUMMARY = "Ver el resumen"
}
