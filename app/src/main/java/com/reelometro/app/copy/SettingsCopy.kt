package com.reelometro.app.copy

import com.reelometro.app.R
import com.reelometro.app.data.Platform
import com.reelometro.app.data.backup.BackupError
import com.reelometro.app.data.backup.BackupOutcome
import com.reelometro.app.data.backup.BackupRules
import com.reelometro.app.data.export.ImportError
import com.reelometro.app.data.export.ImportPreview
import com.reelometro.app.data.export.ImportResult
import com.reelometro.app.stats.Format
import java.time.LocalDate
import java.time.LocalDateTime

/** Sección numerada de Ajustes: «01  OBJETIVO ─────» + descripción; [short], su nombre en el índice de arriba. */
data class SettingsSection(val index: String, val title: String, val description: String, val short: String = title)

/**
 * Textos de **Ajustes** (lista editorial de 8 secciones numeradas).
 *
 * Las palabras están en `res/values/copy_settings.xml`; aquí solo se decide qué frase toca.
 */
object SettingsCopy {

    val TITLE: String get() = str(R.string.settings_title)

    val GOAL: SettingsSection
        get() = SettingsSection(
            "01",
            str(R.string.settings_section_goal_title),
            str(R.string.settings_section_goal_description),
            INDEX_GOAL,
        )
    val NOTIFICATIONS: SettingsSection
        get() = SettingsSection(
            "02",
            str(R.string.settings_section_notifications_title),
            str(R.string.settings_section_notifications_description),
            INDEX_NOTIFICATIONS,
        )
    val APPS: SettingsSection
        get() = SettingsSection(
            "03",
            str(R.string.settings_section_apps_title),
            str(R.string.settings_section_apps_description),
            INDEX_APPS,
        )
    val THEME: SettingsSection
        get() = SettingsSection(
            "04",
            str(R.string.settings_section_theme_title),
            str(R.string.settings_section_theme_description),
            INDEX_THEME,
        )
    val DATA: SettingsSection
        get() = SettingsSection(
            "05",
            str(R.string.settings_section_data_title),
            str(R.string.settings_section_data_description),
            INDEX_DATA,
        )
    val SERVICE: SettingsSection
        get() = SettingsSection(
            "06",
            str(R.string.settings_section_service_title),
            str(R.string.settings_section_service_description),
            INDEX_SERVICE,
        )
    val PRIVACY: SettingsSection
        get() = SettingsSection(
            "07",
            str(R.string.settings_section_privacy_title),
            str(R.string.settings_section_privacy_description),
            INDEX_PRIVACY,
        )
    val ABOUT: SettingsSection
        get() = SettingsSection(
            "08",
            str(R.string.settings_section_about_title),
            str(R.string.settings_section_about_description),
            INDEX_ABOUT,
        )

    /** Todas, en orden. */
    val SECTIONS: List<SettingsSection> get() = listOf(GOAL, NOTIFICATIONS, APPS, THEME, DATA, SERVICE, PRIVACY, ABOUT)

    /** Antetítulo de un diálogo que sale de una sección: «05 · TUS DATOS». */
    fun dialogKicker(section: SettingsSection): String = str(R.string.settings_dialog_kicker, section.index, section.title)

    // ------------------------------------------------------------------ índice y líneas plegables

    /** Nombres cortos del índice de arriba (se ven en MAYÚSCULAS). */
    val INDEX_GOAL: String get() = str(R.string.settings_index_goal)
    val INDEX_NOTIFICATIONS: String get() = str(R.string.settings_index_notifications)
    val INDEX_APPS: String get() = str(R.string.settings_index_apps)
    val INDEX_THEME: String get() = str(R.string.settings_index_theme)
    val INDEX_DATA: String get() = str(R.string.settings_index_data)
    val INDEX_SERVICE: String get() = str(R.string.settings_index_service)
    val INDEX_PRIVACY: String get() = str(R.string.settings_index_privacy)
    val INDEX_ABOUT: String get() = str(R.string.settings_index_about)

    /** TalkBack de un acceso del índice: «Ir a Tus datos». */
    fun indexGo(section: SettingsSection): String = str(R.string.settings_index_go, section.short)

    /** TalkBack: el acceso de la sección donde estabas antes de volver al índice. */
    val INDEX_HERE: String get() = str(R.string.settings_index_here)

    /** Barra fija al bajar por la lista: la sección en la que estás («05 · TUS DATOS») y el botón ÍNDICE. */
    fun indexCurrent(section: SettingsSection): String = dialogKicker(section)

    val INDEX_BACK: String get() = str(R.string.settings_index_back)
    val INDEX_BACK_DESCRIPTION: String get() = str(R.string.settings_index_back_description)

    /** Línea plegable del envío rápido: «ENVÍO RÁPIDO · APAGADO», «… · NADIE FIJADO», «… · 2 FIJADOS». */
    fun quickSendFold(enabled: Boolean, pinned: Int): String = when {
        !enabled -> str(R.string.settings_quick_send_fold_off)
        pinned <= 0 -> str(R.string.settings_quick_send_fold_none)
        else -> plural(R.plurals.settings_quick_send_fold, pinned, Format.integer(pinned))
    }

    /** Línea plegable del final: licencias, calibración y depuración. */
    val TOOLS_FOLD: String get() = str(R.string.settings_tools_fold)

    /** TalkBack de las líneas plegables. */
    val FOLD_OPEN: String get() = str(R.string.settings_fold_open)
    val FOLD_CLOSED: String get() = str(R.string.settings_fold_closed)
    val FOLD_EXPAND: String get() = str(R.string.settings_fold_expand)
    val FOLD_COLLAPSE: String get() = str(R.string.settings_fold_collapse)

    // ------------------------------------------------------------------ 01 objetivo

    /** «150 AL DÍA» / «SIN OBJETIVO». */
    fun goalValue(goal: Int?): String =
        if (goal == null || goal <= 0) OnboardingCopy.GOAL_NONE else str(R.string.settings_goal_value, Format.integer(goal))

    val GOAL_REMOVE: String get() = Buttons.REMOVE_GOAL

    val GOAL_SET: String get() = Buttons.SET_GOAL

    /** Línea del selector: «Vídeos al día ······ [ − 150 + ]». */
    val GOAL_LINE: String get() = str(R.string.settings_goal_line)

    val GOAL_DECREASE: String get() = OnboardingCopy.GOAL_DECREASE
    val GOAL_INCREASE: String get() = OnboardingCopy.GOAL_INCREASE

    /** Ayuda del selector: «Entre 5 y 2.000 vídeos al día.». */
    fun goalRange(min: Int, max: Int): String = OnboardingCopy.goalRange(min, max)

    /** Interruptor de Flap, bajo el objetivo: el objetivo decide cuánto aguanta Flap. */
    val PASSPORT_LABEL: String get() = str(R.string.settings_passport_label)

    val PASSPORT_DESCRIPTION: String get() = str(R.string.settings_passport_description)

    // ------------------------------------------------------------------ 02 avisos

    val DAILY_LABEL: String get() = str(R.string.settings_daily_label)

    /** «Tu cuenta del día, a las 21:30.». */
    fun dailyDescription(hour: Int, minute: Int): String = str(R.string.settings_daily_description, Words.atTime(hour, minute))

    val TIME_LABEL: String get() = str(R.string.settings_time_label)

    val MONTHLY_LABEL: String get() = str(R.string.settings_monthly_label)

    val MONTHLY_DESCRIPTION: String get() = str(R.string.settings_monthly_description)

    /** Aviso en directo al pasar el objetivo (apagado por defecto). */
    val GOAL_LIVE_LABEL: String get() = str(R.string.settings_goal_live_label)

    /** «Un solo aviso al día, en cuanto pases de 150 vídeos.»; sin objetivo, explica por qué no se puede. */
    fun goalLiveDescription(goal: Int?): String =
        if (goal == null || goal <= 0) {
            GOAL_LIVE_NEEDS_GOAL
        } else {
            str(R.string.settings_goal_live_description, Words.videos(goal))
        }

    val GOAL_LIVE_NEEDS_GOAL: String get() = str(R.string.settings_goal_live_needs_goal)

    /** Selector propio de la hora (hoja `ReelDialog`). */
    val TIME_DIALOG_TITLE: String get() = str(R.string.settings_time_dialog_title)
    val TIME_HOUR: String get() = str(R.string.settings_time_hour)
    val TIME_MINUTE: String get() = str(R.string.settings_time_minute)
    val TIME_CONFIRM: String get() = Buttons.DONE
    val TIME_CANCEL: String get() = Buttons.CANCEL

    /** Accesibilidad de los botones del selector de hora. */
    val TIME_HOUR_DOWN: String get() = str(R.string.settings_time_hour_down)
    val TIME_HOUR_UP: String get() = str(R.string.settings_time_hour_up)
    val TIME_MINUTE_DOWN: String get() = str(R.string.settings_time_minute_down)
    val TIME_MINUTE_UP: String get() = str(R.string.settings_time_minute_up)

    /** TalkBack del selector de hora: «7 horas», «5 minutos» (la cifra con su unidad). */
    fun timeHourValue(h: Int): String = plural(R.plurals.settings_time_hour_value, h, Format.integer(h))
    fun timeMinuteValue(m: Int): String = plural(R.plurals.settings_time_minute_value, m, Format.integer(m))

    /** Pedir el permiso de avisos (Android 13+), antes de que se haya denegado del todo. */
    val NOTIFICATIONS_ASK: String get() = OnboardingCopy.NOTIFY_RATIONALE
    val NOTIFICATIONS_ALLOW: String get() = Buttons.ALLOW_NOTIFICATIONS
    val NOTIFICATIONS_OPEN: String get() = Buttons.OPEN_SETTINGS

    /** Con los avisos bloqueados en Android (va con «ABRIR AJUSTES»). */
    val NOTIFICATIONS_BLOCKED: String get() = str(R.string.settings_notifications_blocked)

    // ------------------------------------------------------------------ 03 apps

    /** Nombre de cada app en la lista: «TikTok», «Instagram Reels», «YouTube Shorts». */
    fun appName(platform: Platform): String = platform.displayName

    val APP_NOT_INSTALLED: String get() = Labels.NOT_INSTALLED

    val ALL_APPS_OFF: String get() = str(R.string.settings_all_apps_off)

    /** Interruptor «A quién envías» (apagado por defecto), bajo la lista de apps. */
    val RECIPIENTS_LABEL: String get() = str(R.string.settings_recipients_label)

    val RECIPIENTS_DESCRIPTION: String get() = str(R.string.settings_recipients_description)

    /** Interruptor «Envío rápido» (apagado por defecto), debajo de «A quién envías». */
    val QUICK_SEND_LABEL: String get() = str(R.string.settings_quick_send_label)

    val QUICK_SEND_DESCRIPTION: String get() = str(R.string.settings_quick_send_description)

    /** El gesto para enviar a dos fijados a la vez (1.4.1). */
    val QUICK_SEND_PAIR_HINT: String get() = str(R.string.settings_quick_send_pair_hint)

    /** Con «A quién envías» apagado, el envío rápido no se puede encender. */
    val QUICK_SEND_NEEDS_RECIPIENTS: String get() = str(R.string.settings_quick_send_needs_recipients)

    /** 1.10.0: encima de las pestañas de cada app (INSTAGRAM, TIKTOK). */
    val QUICK_SEND_APPS_HINT: String get() = str(R.string.settings_quick_send_apps_hint)

    /** TalkBack de la pestaña de [app] («TikTok»: `Platform.appName`). */
    fun quickSendAppDescription(app: String): String = str(R.string.settings_quick_send_app_description, app)

    /** Sin historial de envíos en [app] todavía: no hay nada que fijar. */
    fun quickSendEmpty(app: String): String = str(R.string.settings_quick_send_empty_app, app)

    /** Encima de la lista de personas y grupos de [app] para fijar. */
    fun quickSendListLabel(app: String): String = str(R.string.settings_quick_send_list_label_app, app)

    val QUICK_SEND_PIN: String get() = str(R.string.settings_quick_send_pin)
    val QUICK_SEND_PINNED: String get() = str(R.string.settings_quick_send_pinned)

    /** Devuelve las burbujas de la app elegida a su sitio por defecto. */
    val QUICK_SEND_RELOCATE: String get() = str(R.string.settings_quick_send_relocate)

    fun quickSendRelocateHint(app: String): String = str(R.string.settings_quick_send_relocate_hint_app, app)

    fun quickSendRelocated(app: String): String = str(R.string.settings_quick_send_relocated_app, app)

    /** Ya hay [max] fijados: para fijar otro hay que quitar uno. */
    fun quickSendFull(max: Int): String = plural(R.plurals.settings_quick_send_full, max, Format.integer(max))

    /** Para TalkBack, en cada fila: «Fijar a Ana» / «Quitar a Ana del envío rápido». */
    fun quickSendPinDescription(name: String, pinned: Boolean): String =
        if (pinned) {
            str(R.string.settings_quick_send_unpin_description, name)
        } else {
            str(R.string.settings_quick_send_pin_description, name)
        }

    /** Debajo de cada fijado (1.4.3): su burbuja con una foto de la galería, en vez de la recortada de Instagram. */
    val QUICK_SEND_PHOTO_PICK: String get() = str(R.string.settings_quick_send_photo_pick)

    /** Con una foto elegida: cambiarla por otra de la galería. */
    val QUICK_SEND_PHOTO_CHANGE: String get() = str(R.string.settings_quick_send_photo_change)

    /** Quita la foto elegida: vuelve la recortada de la hoja de compartir (o las iniciales). */
    val QUICK_SEND_PHOTO_CLEAR: String get() = str(R.string.settings_quick_send_photo_clear)

    val QUICK_SEND_PHOTO_SET: String get() = str(R.string.settings_quick_send_photo_set)

    val QUICK_SEND_PHOTO_CLEARED: String get() = str(R.string.settings_quick_send_photo_cleared)

    val QUICK_SEND_PHOTO_FAILED: String get() = str(R.string.settings_quick_send_photo_failed)

    /** Para TalkBack: «Elegir de la galería la foto de la burbuja de Ana». */
    fun quickSendPhotoPickDescription(name: String, chosen: Boolean): String =
        if (chosen) {
            str(R.string.settings_quick_send_photo_change_description, name)
        } else {
            str(R.string.settings_quick_send_photo_pick_description, name)
        }

    /** Para TalkBack: «Quitar la foto elegida de Ana». */
    fun quickSendPhotoClearDescription(name: String): String = str(R.string.settings_quick_send_photo_clear_description, name)

    // ------------------------------------------------------------------ 04 tema e idioma

    val THEME_SYSTEM: String get() = str(R.string.settings_theme_system)
    val THEME_DARK: String get() = str(R.string.settings_theme_dark)
    val THEME_LIGHT: String get() = str(R.string.settings_theme_light)

    /** Fila «Idioma» (Android 13+): abre el diálogo de la app para elegirlo. */
    fun languageLabel(): String = str(R.string.settings_language_label)

    /** Título del diálogo de idioma: «¿En qué idioma?». */
    fun languageDialogTitle(): String = str(R.string.settings_language_dialog_title)

    /** Primera opción del diálogo: «El del móvil». */
    fun languageSystem(): String = str(R.string.settings_language_system)

    /** Valor de la fila: el idioma de los textos, en su propio idioma («Español»). */
    fun languageValue(): String = Format.capitalize(Format.locale.getDisplayLanguage(Format.locale))

    /** Ayuda bajo la fila: «Elige en qué idioma ves la app.». */
    fun languageHint(): String = str(R.string.settings_language_hint)

    /** TalkBack: qué hace tocar la fila. */
    fun languageOpen(): String = str(R.string.settings_language_open)

    // ------------------------------------------------------------------ 05 tus datos

    /** Resumen de lo guardado, en líneas de ticket. */
    val DATA_COUNT_LABEL: String get() = str(R.string.settings_data_count_label)
    val DATA_TIME_LABEL: String get() = str(R.string.settings_data_time_label)

    val EXPORT_LABEL: String get() = Buttons.EXPORT_CSV
    val EXPORT_DESCRIPTION: String get() = str(R.string.settings_export_description)

    /** «Exportado: 12.408 vídeos.». */
    fun exportDone(rows: Int): String = str(R.string.settings_export_done, Words.videos(rows))

    val EXPORT_EMPTY: String get() = str(R.string.settings_export_empty)
    val EXPORT_FAILED: String get() = str(R.string.settings_export_failed)

    /** Nombre propuesto para el CSV: «reelometer-2026-11-03.csv» (igual en todos los idiomas). */
    fun exportFileName(date: LocalDate): String = "reelometer-$date.csv"

    val DELETE_LABEL: String get() = Buttons.DELETE_ALL
    val DELETE_DESCRIPTION: String get() = str(R.string.settings_delete_description)

    val DELETE_DIALOG_TITLE: String get() = str(R.string.settings_delete_dialog_title)

    /** Cuerpo del diálogo de borrado (`ReelDialog`). */
    fun deleteDialogBody(count: Long, totalMs: Long, backupOn: Boolean = false): String {
        val body = if (count <= 0) {
            str(R.string.settings_delete_dialog_body_empty)
        } else {
            plural(R.plurals.settings_delete_dialog_body, count, Words.videos(count), Words.duration(totalMs))
        }
        // Con la copia semanal encendida sí hay copias fuera de la app: se dice que esas no se borran.
        return if (backupOn) body + " " + str(R.string.settings_backup_delete_note) else body
    }

    val DELETE_CONFIRM: String get() = Buttons.DELETE_ALL
    val DELETE_CANCEL: String get() = Buttons.CANCEL
    val DELETE_DONE: String get() = str(R.string.settings_delete_done)
    val DELETE_FAILED: String get() = str(R.string.settings_delete_failed)

    // ------------------------------------------------------------------ importar CSV (1.6.0)

    val IMPORT_LABEL: String get() = Buttons.IMPORT_CSV
    val IMPORT_DESCRIPTION: String get() = str(R.string.settings_import_description)

    /** TalkBack del indicador mientras se lee o se importa. */
    val IMPORT_BUSY: String get() = str(R.string.settings_import_busy)

    val IMPORT_DIALOG_TITLE: String get() = str(R.string.settings_import_dialog_title)
    val IMPORT_ADD: String get() = Buttons.IMPORT_ADD
    val IMPORT_REPLACE: String get() = Buttons.IMPORT_REPLACE

    /** Confirmar en «¿Sustituir todo?»: corto, para que quepa junto al icono con la fuente al 130 %. */
    val IMPORT_REPLACE_CONFIRM: String get() = Buttons.IMPORT_REPLACE_CONFIRM
    val IMPORT_CANCEL: String get() = Buttons.CANCEL
    val IMPORT_MODES: String get() = str(R.string.settings_import_modes)

    /**
     * Vista previa, una frase por línea: «En el archivo hay 1.234 vídeos (3 mar – 4 nov 2026).», «230 ya
     * estaban guardados: no se repiten.», las líneas rotas y que los ajustes no viajan. El bloque PASAPORTE de
     * los CSV de antes de 1.12.0 se ignora al importar: no se menciona.
     */
    fun importPreview(preview: ImportPreview): String = buildList {
        val from = preview.from
        val to = preview.to
        if (preview.videos > 0 && from != null && to != null) {
            add(str(R.string.settings_import_preview, Words.videos(preview.videos), Format.rangeWithYear(from, to)))
        }
        if (preview.alreadyPresent > 0) {
            add(plural(R.plurals.settings_import_preview_present, preview.alreadyPresent, Format.integer(preview.alreadyPresent)))
        }
        if (preview.badLines > 0) {
            add(plural(R.plurals.settings_import_preview_bad, preview.badLines, Format.integer(preview.badLines)))
        }
        add(str(R.string.settings_import_preview_settings))
    }.joinToString("\n")

    val IMPORT_REPLACE_TITLE: String get() = str(R.string.settings_import_replace_title)

    /** Confirmación de SUSTITUIR TODO, con lo que se va a borrar. */
    fun importReplaceBody(count: Long, totalMs: Long): String =
        if (count <= 0) {
            str(R.string.settings_import_replace_body_empty)
        } else {
            plural(R.plurals.settings_import_replace_body, count, Words.videos(count), Words.duration(totalMs))
        }

    /** «Importados: 1.004 vídeos. 230 ya estaban. 3 líneas no se entendían.» */
    fun importDone(result: ImportResult): String = buildList {
        if (result.imported > 0) {
            add(plural(R.plurals.settings_import_done, result.imported, Words.videos(result.imported)))
            if (result.alreadyPresent > 0) {
                add(plural(R.plurals.settings_import_done_present, result.alreadyPresent, Format.integer(result.alreadyPresent)))
            }
        } else {
            add(str(R.string.settings_import_nothing_new))
        }
        if (result.badLines > 0) {
            add(plural(R.plurals.settings_import_done_bad, result.badLines, Format.integer(result.badLines)))
        }
    }.joinToString(" ")

    /** Por qué no se ha podido importar. */
    fun importError(error: ImportError): String = when (error) {
        ImportError.NOT_REELOMETRO -> str(R.string.settings_import_not_csv)
        ImportError.EMPTY -> str(R.string.settings_import_empty)
        ImportError.TOO_LARGE -> str(R.string.settings_import_too_large)
        ImportError.TOO_MANY_BAD -> str(R.string.settings_import_too_broken)
        ImportError.FAILED -> str(R.string.settings_import_failed)
        ImportError.DATES_UNREADABLE -> str(R.string.settings_import_dates)
    }

    // ------------------------------------------------------------------ copia semanal del CSV

    val BACKUP_LABEL: String get() = str(R.string.settings_backup_label)

    /** «Cada semana guardo el mismo CSV de EXPORTAR… Me quedo con las 4 últimas copias…». */
    val BACKUP_DESCRIPTION: String
        get() = plural(R.plurals.settings_backup_description, BackupRules.KEEP, Format.integer(BackupRules.KEEP))

    val BACKUP_FOLDER_LABEL: String get() = str(R.string.settings_backup_folder_label)
    val BACKUP_LAST_LABEL: String get() = str(R.string.settings_backup_last_label)
    val BACKUP_NOW: String get() = str(R.string.settings_backup_now)
    val BACKUP_CHANGE: String get() = str(R.string.settings_backup_change)
    val BACKUP_CHANGE_TALKBACK: String get() = str(R.string.settings_backup_change_talkback)

    /** A la derecha de «Última copia»: «7 nov 2026, 14:05» o «Todavía ninguna». */
    fun backupLast(at: LocalDateTime?): String =
        if (at == null) {
            str(R.string.settings_backup_last_never)
        } else {
            str(R.string.settings_backup_last_value, Format.dayShortWithYear(at.toLocalDate()), Format.time(at.hour, at.minute))
        }

    /** Resultado de COPIAR AHORA (o de elegir carpeta, que hace la primera copia). */
    fun backupResult(outcome: BackupOutcome, folderName: String): String = when (outcome) {
        is BackupOutcome.Done -> str(R.string.settings_backup_done, folderName, Words.videos(outcome.videos))
        BackupOutcome.Empty -> str(R.string.settings_backup_empty)
        is BackupOutcome.Failed -> backupError(outcome.error)
    }

    /** Por qué falló la última copia (aviso fijo bajo la copia semanal). */
    fun backupError(error: BackupError): String = when (error) {
        BackupError.NO_ACCESS -> str(R.string.settings_backup_no_access)
        BackupError.FAILED -> str(R.string.settings_backup_failed)
    }

    /** Android no ha dado permiso para la carpeta recién elegida. */
    val BACKUP_PICK_FAILED: String get() = str(R.string.settings_backup_pick_failed)

    // ------------------------------------------------------------------ 06 servicio

    fun serviceStatus(connected: Boolean): String = if (connected) Labels.CONNECTED else Labels.DISCONNECTED

    /** Línea de estado: «Permiso de accesibilidad ······ CONECTADO». */
    val SERVICE_LINE: String get() = str(R.string.settings_service_line)

    /** Sin el servicio: lleva a la divulgación y la guía. */
    val SERVICE_CONNECT: String get() = Buttons.CONNECT

    /** «Contando en TikTok, Reels y Shorts.»; sin apps activadas, aviso. */
    fun serviceConnected(enabled: List<Platform>): String =
        if (enabled.isEmpty()) {
            ALL_APPS_OFF
        } else {
            str(R.string.settings_service_connected, Words.join(enabled.map { it.shortName }))
        }

    val SERVICE_DISCONNECTED: String get() = str(R.string.settings_service_disconnected)

    val SERVICE_BUTTON: String get() = Buttons.OPEN_ACCESSIBILITY

    /** Enlace para volver a leer la divulgación destacada. */
    val SERVICE_DISCLOSURE_LINK: String get() = str(R.string.settings_service_disclosure_link)

    // ------------------------------------------------------------------ 07 privacidad

    /** Explicación de privacidad, en párrafos cortos. */
    val PRIVACY_PARAGRAPHS: List<String> get() = strArray(R.array.settings_privacy_paragraphs)

    val PRIVACY_POLICY_LINK: String get() = str(R.string.settings_privacy_policy_link)

    // ------------------------------------------------------------------ 08 acerca de

    /** «VERSIÓN 1.0.0». */
    fun versionLabel(versionName: String): String = str(R.string.settings_version_label, versionName)

    /**
     * Cuenta atrás discreta de los 7 toques en la versión: «A 3 toques de la calibración.»,
     * «A un toque de la calibración.». Con 0 o menos, [CALIBRATION_UNLOCKED].
     */
    fun calibrationTapsLeft(left: Int): String =
        if (left <= 0) CALIBRATION_UNLOCKED else plural(R.plurals.settings_calibration_taps_left, left, Format.integer(left))

    val CALIBRATION_UNLOCKED: String get() = str(R.string.settings_calibration_unlocked)
    val CALIBRATION_ALREADY: String get() = str(R.string.settings_calibration_already)
    val CALIBRATION_ENTRY: String get() = str(R.string.settings_calibration_entry)

    /** Línea de la versión (7 toques desbloquean la calibración). */
    val VERSION_LINE: String get() = str(R.string.settings_version_line)

    /** Línea que abre las licencias. */
    val FONTS_LINE: String get() = str(R.string.settings_fonts_line)
    val FONTS_VALUE: String get() = str(R.string.settings_fonts_value)
    val LICENSES_TITLE: String get() = str(R.string.settings_licenses_title)
    val LICENSES_CLOSE: String get() = Buttons.CLOSE

    /** Volver a ver el onboarding. */
    val REPLAY_ONBOARDING: String get() = str(R.string.settings_replay_onboarding)

    /** Volver a ver el recorrido guiado (empieza en Hoy). */
    val REPLAY_TOUR: String get() = str(R.string.settings_replay_tour)
    val SHOW_ALL_TABS: String get() = str(R.string.settings_show_all_tabs)

    val LICENSES_LABEL: String get() = str(R.string.settings_licenses_label)
    val FONTS_LICENSE: String get() = str(R.string.settings_fonts_license)

    val SIGNATURE: String get() = str(R.string.settings_signature)

    /**
     * El proyecto en GitHub (solo si hay enlace en `AppLinks.PROJECT_URL`). Sin hablar de pagos: ver `AppLinks`.
     * [PROJECT_NOTE] y [PROJECT_NO_BROWSER] sirven también para el enlace de la política (07 PRIVACIDAD).
     */
    val PROJECT_TEXT: String get() = str(R.string.settings_project_text)
    val PROJECT_BUTTON: String get() = str(R.string.settings_project_button)
    val PROJECT_NOTE: String get() = str(R.string.settings_project_note)
    val PROJECT_NO_BROWSER: String get() = str(R.string.settings_project_no_browser)

    // ------------------------------------------------------------------ depuración (solo build debug)

    val DEBUG_TITLE: String get() = str(R.string.settings_debug_title)
    val DEBUG_DESCRIPTION: String get() = str(R.string.settings_debug_description)
    val DEBUG_SEED: String get() = str(R.string.settings_debug_seed)
    val DEBUG_FAILED: String get() = str(R.string.settings_debug_failed)

    /** «Listo: 12.408 vídeos de ejemplo.». */
    fun debugSeeded(count: Int): String = str(R.string.settings_debug_seeded, Words.videos(count))
}
