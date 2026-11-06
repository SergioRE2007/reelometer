package com.reelometro.app.copy

import com.reelometro.app.data.Platform
import com.reelometro.app.stats.Format
import java.time.LocalDate

/** Sección numerada de Ajustes: «01  OBJETIVO ─────» + descripción. */
data class SettingsSection(val index: String, val title: String, val description: String)

/** Textos de **Ajustes** (lista editorial de 8 secciones numeradas). */
object SettingsCopy {

    const val TITLE = "Ajustes"

    val GOAL = SettingsSection(
        "01", "OBJETIVO",
        "Un máximo de vídeos al día. Sirve para el «te quedan» de Hoy y para las rachas.",
    )
    val NOTIFICATIONS = SettingsSection(
        "02", "AVISOS",
        "Un resumen al día, a la hora que elijas, y otro cada mes.",
    )
    val APPS = SettingsSection(
        "03", "QUÉ APPS CUENTO",
        "Lo que apagues aquí deja de contarse al momento. Lo ya contado se queda.",
    )
    val THEME = SettingsSection(
        "04", "TEMA",
        "El marcador luce más a oscuras, pero tú mandas.",
    )
    val DATA = SettingsSection(
        "05", "TUS DATOS",
        "Todo lo contado vive en este móvil. Puedes llevártelo o borrarlo.",
    )
    val SERVICE = SettingsSection(
        "06", "SERVICIO",
        "El permiso de accesibilidad es lo que me deja contar. Aquí ves si está activo.",
    )
    val PRIVACY = SettingsSection(
        "07", "PRIVACIDAD",
        "Qué pasa con tus datos, sin letra pequeña.",
    )
    val ABOUT = SettingsSection(
        "08", "ACERCA DE",
        "Reelómetro no tiene relación con TikTok, Instagram ni YouTube. Solo cuenta lo que ves en ellas.",
    )

    /** Todas, en orden. */
    val SECTIONS: List<SettingsSection> = listOf(GOAL, NOTIFICATIONS, APPS, THEME, DATA, SERVICE, PRIVACY, ABOUT)

    // ------------------------------------------------------------------ 01 objetivo

    /** «150 AL DÍA» / «SIN OBJETIVO». */
    fun goalValue(goal: Int?): String =
        if (goal == null || goal <= 0) OnboardingCopy.GOAL_NONE else "${Format.integer(goal)} AL DÍA"

    const val GOAL_REMOVE = Buttons.REMOVE_GOAL

    const val GOAL_SET = Buttons.SET_GOAL

    /** Línea del selector: «Vídeos al día ······ [ − 150 + ]». */
    const val GOAL_LINE = "Vídeos al día"

    const val GOAL_DECREASE = OnboardingCopy.GOAL_DECREASE
    const val GOAL_INCREASE = OnboardingCopy.GOAL_INCREASE

    /** Ayuda del selector: «Entre 5 y 2.000 vídeos al día.». */
    fun goalRange(min: Int, max: Int): String = OnboardingCopy.goalRange(min, max)

    /** Interruptor del modo Pasaporte, bajo el objetivo: el juego premia quedarse por debajo. */
    const val PASSPORT_LABEL = "Modo pasaporte"

    const val PASSPORT_DESCRIPTION =
        "Una pestaña más con Flap, niveles y sellos que se ganan viendo menos. Si lo apagas, desaparece " +
            "del todo: la pestaña, su aviso y Flap en el widget."

    // ------------------------------------------------------------------ 02 avisos

    const val DAILY_LABEL = "Resumen diario"

    /** «Tu cuenta del día, a las 21:30.». */
    fun dailyDescription(hour: Int, minute: Int): String = "Tu cuenta del día, ${Words.atTime(hour, minute)}."

    const val TIME_LABEL = "Hora del aviso"

    const val MONTHLY_LABEL = "Resumen mensual"

    const val MONTHLY_DESCRIPTION = "El día 1, el mes anterior contado, con su resumen."

    /** Aviso en directo al pasar el objetivo (apagado por defecto). */
    const val GOAL_LIVE_LABEL = "Aviso al pasar el objetivo"

    /** «Un solo aviso al día, en cuanto pases de 150 vídeos.»; sin objetivo, explica por qué no se puede. */
    fun goalLiveDescription(goal: Int?): String =
        if (goal == null || goal <= 0) {
            GOAL_LIVE_NEEDS_GOAL
        } else {
            "Un solo aviso al día, en cuanto pases de ${Words.videos(goal)}."
        }

    const val GOAL_LIVE_NEEDS_GOAL = "Necesita un objetivo. Ponlo arriba y te aviso en cuanto lo pases."

    /** Selector propio de la hora (hoja `ReelDialog`). */
    const val TIME_DIALOG_TITLE = "¿A qué hora te aviso?"
    const val TIME_HOUR = "HORA"
    const val TIME_MINUTE = "MINUTOS"
    const val TIME_CONFIRM = Buttons.DONE
    const val TIME_CANCEL = Buttons.CANCEL

    /** Accesibilidad de los botones del selector de hora. */
    const val TIME_HOUR_DOWN = "Una hora menos"
    const val TIME_HOUR_UP = "Una hora más"
    const val TIME_MINUTE_DOWN = "Cinco minutos menos"
    const val TIME_MINUTE_UP = "Cinco minutos más"

    /** Pedir el permiso de avisos (Android 13+), antes de que se haya denegado del todo. */
    const val NOTIFICATIONS_ASK = OnboardingCopy.NOTIFY_RATIONALE
    const val NOTIFICATIONS_ALLOW = Buttons.ALLOW_NOTIFICATIONS
    const val NOTIFICATIONS_OPEN = Buttons.OPEN_SETTINGS

    /** Con los avisos bloqueados en Android (va con «ABRIR AJUSTES»). */
    const val NOTIFICATIONS_BLOCKED =
        "Android tiene los avisos de Reelómetro desactivados. Actívalos en los ajustes del sistema para recibirlos."

    // ------------------------------------------------------------------ 03 apps

    /** Nombre de cada app en la lista: «TikTok», «Instagram Reels», «YouTube Shorts». */
    fun appName(platform: Platform): String = platform.displayName

    const val APP_NOT_INSTALLED = Labels.NOT_INSTALLED

    const val ALL_APPS_OFF = "Has apagado todas. Así no hay nada que contar."

    /** Interruptor «A quién envías» (apagado por defecto), bajo la lista de apps. */
    const val RECIPIENTS_LABEL = "A quién envías"

    const val RECIPIENTS_DESCRIPTION =
        "Solo en Instagram. Al compartir un vídeo, apunto el nombre de la persona o el grupo que eliges y " +
            "en Estadísticas ves a quién envías más. No leo el chat ni lo que escribes, y se queda en este móvil."

    /** Interruptor «Envío rápido» (apagado por defecto), debajo de «A quién envías». */
    const val QUICK_SEND_LABEL = "Envío rápido"

    const val QUICK_SEND_DESCRIPTION =
        "Solo en Instagram. Mientras ves reels, sale una burbuja por cada persona o grupo que fijes. Al tocarla, " +
            "pulso por ti «Compartir», ese nombre y «Enviar»; si no sale a la vista, escribo su nombre en el " +
            "buscador de la hoja. Nada más. Si algo no cuadra, paro y terminas tú. Para enseñar su foto en la " +
            "burbuja, la recorto de la hoja de compartir cuando sale a la vista, o eliges tú una de tu galería; " +
            "se queda en este móvil. Arrástralas a donde quieras (abajo, encima del nombre del reel, se ponen en fila) o suéltalas en la " +
            "«X» para esconderlas hasta el siguiente reel."

    /** El gesto para enviar a dos fijados a la vez (1.4.1). */
    const val QUICK_SEND_PAIR_HINT =
        "Para mandarlo a dos a la vez, mantén pulsada una burbuja hasta que se despegue y suéltala encima de la otra. " +
            "Si tocas otra mientras envía, la sumo a la misma hoja."

    /** Con «A quién envías» apagado, el envío rápido no se puede encender. */
    const val QUICK_SEND_NEEDS_RECIPIENTS =
        "Para usarlo, enciende antes «A quién envías»: el envío rápido busca en la hoja el nombre que fijas."

    /** Sin historial de envíos todavía: no hay nada que fijar. */
    const val QUICK_SEND_EMPTY =
        "Aún no hay a quién fijar. Comparte un reel a mano con «A quién envías» encendido: aquí saldrán las " +
            "personas y los grupos a los que envías."

    /** Encima de la lista de personas y grupos para fijar. */
    const val QUICK_SEND_LIST_LABEL = "A quién envías más"

    const val QUICK_SEND_PIN = "FIJAR"
    const val QUICK_SEND_PINNED = "FIJADO"

    /** Devuelve las burbujas a su sitio: borde derecho, a media altura. */
    const val QUICK_SEND_RELOCATE = "RECOLOCAR"

    const val QUICK_SEND_RELOCATE_HINT = "Las burbujas vuelven al borde derecho, a media altura."

    const val QUICK_SEND_RELOCATED = "Hecho. Las verás en su sitio en el próximo reel."

    /** Ya hay [max] fijados: para fijar otro hay que quitar uno. */
    fun quickSendFull(max: Int): String = "Ya tienes ${Format.integer(max)} fijados. Quita uno para fijar otro."

    /** Para TalkBack, en cada fila: «Fijar a Ana» / «Quitar a Ana del envío rápido». */
    fun quickSendPinDescription(name: String, pinned: Boolean): String =
        if (pinned) "Quitar a $name del envío rápido" else "Fijar a $name en el envío rápido"

    /** Debajo de cada fijado (1.4.3): su burbuja con una foto de la galería, en vez de la recortada de Instagram. */
    const val QUICK_SEND_PHOTO_PICK = "ELEGIR FOTO"

    /** Con una foto elegida: cambiarla por otra de la galería. */
    const val QUICK_SEND_PHOTO_CHANGE = "CAMBIAR FOTO"

    /** Quita la foto elegida: vuelve la recortada de la hoja de compartir (o las iniciales). */
    const val QUICK_SEND_PHOTO_CLEAR = "QUITAR FOTO"

    const val QUICK_SEND_PHOTO_SET = "Foto puesta. La verás en su burbuja; solo está en este móvil."

    const val QUICK_SEND_PHOTO_CLEARED = "Foto quitada. Vuelve la automática o, si no hay, las iniciales."

    const val QUICK_SEND_PHOTO_FAILED = "No he podido abrir esa foto. Prueba con otra."

    /** Para TalkBack: «Elegir de la galería la foto de la burbuja de Ana». */
    fun quickSendPhotoPickDescription(name: String, chosen: Boolean): String =
        if (chosen) "Cambiar la foto de la burbuja de $name por otra de la galería" else "Elegir de la galería la foto de la burbuja de $name"

    /** Para TalkBack: «Quitar la foto elegida de Ana». */
    fun quickSendPhotoClearDescription(name: String): String =
        "Quitar la foto elegida para $name y volver a la automática"

    // ------------------------------------------------------------------ 04 tema

    const val THEME_SYSTEM = "SISTEMA"
    const val THEME_DARK = "OSCURO"
    const val THEME_LIGHT = "CLARO"

    // ------------------------------------------------------------------ 05 tus datos

    /** Resumen de lo guardado, en líneas de ticket. */
    const val DATA_COUNT_LABEL = "Vídeos guardados"
    const val DATA_TIME_LABEL = "Tiempo guardado"

    const val EXPORT_LABEL = Buttons.EXPORT_CSV
    const val EXPORT_DESCRIPTION = "Todo tu historial en un archivo que abre cualquier hoja de cálculo."

    /** «Exportado: 12.408 vídeos.». */
    fun exportDone(rows: Int): String = "Exportado: ${Words.videos(rows)}."

    const val EXPORT_EMPTY = "No hay nada que exportar todavía."
    const val EXPORT_FAILED = "No se ha podido exportar. Prueba otra vez o elige otra carpeta."

    /** Nombre propuesto para el CSV: «reelometro-2026-11-03.csv». */
    fun exportFileName(date: LocalDate): String = "reelometro-$date.csv"

    const val DELETE_LABEL = Buttons.DELETE_ALL
    const val DELETE_DESCRIPTION = "Borra todos los vídeos contados. No hay papelera."

    const val DELETE_DIALOG_TITLE = "¿Borrar todo?"

    /** Cuerpo del diálogo de borrado (`ReelDialog`). */
    fun deleteDialogBody(count: Long, totalMs: Long): String =
        if (count <= 0) {
            "No hay vídeos que borrar. Tus ajustes se quedan como están."
        } else {
            "Se borrarán ${Words.videos(count)} y ${Words.duration(totalMs)} de historial. No se puede deshacer, " +
                "y no hay copia en ningún otro sitio, porque no hay ningún otro sitio. Tus ajustes se quedan como están."
        }

    const val DELETE_CONFIRM = Buttons.DELETE_ALL
    const val DELETE_CANCEL = Buttons.CANCEL
    const val DELETE_DONE = "Borrado. Marcador a cero."
    const val DELETE_FAILED = "No se ha podido borrar. Vuelve a intentarlo."

    // ------------------------------------------------------------------ 06 servicio

    fun serviceStatus(connected: Boolean): String = if (connected) Labels.CONNECTED else Labels.DISCONNECTED

    /** Línea de estado: «Permiso de accesibilidad ······ CONECTADO». */
    const val SERVICE_LINE = "Permiso de accesibilidad"

    /** Sin el servicio: lleva a la divulgación y la guía. */
    const val SERVICE_CONNECT = Buttons.CONNECT

    /** «Contando en TikTok, Reels y Shorts.»; sin apps activadas, aviso. */
    fun serviceConnected(enabled: List<Platform>): String =
        if (enabled.isEmpty()) ALL_APPS_OFF else "Contando en ${Words.join(enabled.map { it.shortName })}."

    const val SERVICE_DISCONNECTED = "Sin el servicio no cuento nada. Actívalo en Accesibilidad."

    const val SERVICE_BUTTON = Buttons.OPEN_ACCESSIBILITY

    /** Enlace para volver a leer la divulgación destacada. */
    const val SERVICE_DISCLOSURE_LINK = "QUÉ LEE Y PARA QUÉ"

    // ------------------------------------------------------------------ 07 privacidad

    /** Explicación de privacidad, en párrafos cortos. */
    val PRIVACY_PARAGRAPHS: List<String> = listOf(
        "Reelómetro cuenta lo que ves en TikTok, Instagram y YouTube con el servicio de accesibilidad de " +
            "Android. De cada vídeo guarda la app, la hora, cuánto lo viste, el creador y la descripción.",
        "Todo se guarda en este móvil. No hay cuentas, ni servidores, ni anuncios, ni analítica. La app no " +
            "tiene permiso para usar internet.",
        "Tus datos solo salen de aquí si los sacas tú: al exportar el CSV o al compartir tu ticket.",
        "Si desactivas el servicio, deja de contar. Si borras los datos o desinstalas la app, desaparecen.",
    )

    const val PRIVACY_POLICY_LINK = "POLÍTICA DE PRIVACIDAD COMPLETA"

    // ------------------------------------------------------------------ 08 acerca de

    /** «VERSIÓN 1.0.0». */
    fun versionLabel(versionName: String): String = "VERSIÓN $versionName"

    /**
     * Cuenta atrás discreta de los 7 toques en la versión: «A 3 toques de la calibración.»,
     * «A un toque de la calibración.». Con 0 o menos, [CALIBRATION_UNLOCKED].
     */
    fun calibrationTapsLeft(left: Int): String = when {
        left <= 0 -> CALIBRATION_UNLOCKED
        left == 1 -> "A un toque de la calibración."
        else -> "A ${Format.integer(left)} toques de la calibración."
    }

    const val CALIBRATION_UNLOCKED = "Calibración desbloqueada. Está al final de Ajustes."
    const val CALIBRATION_ALREADY = "La calibración ya estaba desbloqueada."
    const val CALIBRATION_ENTRY = "CALIBRACIÓN"

    /** Línea de la versión (7 toques desbloquean la calibración). */
    const val VERSION_LINE = "Versión"

    /** Línea que abre las licencias. */
    const val FONTS_LINE = "Tipografías"
    const val FONTS_VALUE = "SIL OFL"
    const val LICENSES_TITLE = "Licencias"
    const val LICENSES_CLOSE = Buttons.CLOSE

    /** Volver a ver el onboarding. */
    const val REPLAY_ONBOARDING = "VER LA INTRODUCCIÓN"

    /** Volver a ver el recorrido guiado (empieza en Hoy). */
    const val REPLAY_TOUR = "VER EL RECORRIDO"

    const val LICENSES_LABEL = "LICENCIAS"
    const val FONTS_LICENSE =
        "Tipografías: Bricolage Grotesque, JetBrains Mono e Instrument Serif, con licencia SIL Open Font License 1.1."

    const val SIGNATURE = "Hecho a ratos de quince segundos."

    /** El proyecto en GitHub (solo si hay enlace en `AppLinks.PROJECT_URL`). Sin hablar de pagos: ver `AppLinks`. */
    const val PROJECT_TEXT = "Reelómetro es gratis, no tiene anuncios y lo hace una sola persona. El proyecto está en GitHub."
    const val PROJECT_BUTTON = "VER EN GITHUB"
    const val PROJECT_NOTE = "Se abre en tu navegador. La app sigue sin internet."
    const val PROJECT_NO_BROWSER = "No hay ningún navegador para abrir el enlace."

    // ------------------------------------------------------------------ depuración (solo build debug)

    const val DEBUG_TITLE = "DEPURACIÓN"
    const val DEBUG_DESCRIPTION = "Solo en la versión de pruebas. Sustituye tus datos por un historial inventado de diez semanas."
    const val DEBUG_SEED = "GENERAR DATOS DE EJEMPLO"
    const val DEBUG_FAILED = "No se han podido generar los datos de ejemplo."

    /** «Listo: 12.408 vídeos de ejemplo.». */
    fun debugSeeded(count: Int): String = "Listo: ${Words.videos(count)} de ejemplo."
}
