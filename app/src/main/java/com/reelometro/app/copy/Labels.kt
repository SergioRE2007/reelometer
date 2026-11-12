package com.reelometro.app.copy

import com.reelometro.app.R
import com.reelometro.app.stats.Format

/**
 * Etiquetas cortas en MAYÚSCULAS (estilo `label`/`labelL`, siempre con tracking). Úsalas tal cual:
 * ya vienen en mayúsculas y con tildes («DÍA», «VÍDEOS»). Las palabras están en
 * `res/values/copy_labels.xml`.
 */
object Labels {
    val VIDEOS: String get() = str(R.string.label_videos)
    val VIDEO: String get() = str(R.string.label_video)
    val TIME: String get() = str(R.string.label_time)
    val TODAY: String get() = str(R.string.label_today)
    val YESTERDAY: String get() = str(R.string.label_yesterday)
    val GOAL: String get() = str(R.string.label_goal)
    val NO_SIGNAL: String get() = str(R.string.label_no_signal)
    val PEAK_HOUR: String get() = str(R.string.label_peak_hour)
    val WEAK_DAY: String get() = str(R.string.label_weak_day)
    val WORST_DAY: String get() = str(R.string.label_worst_day)
    val STREAK: String get() = str(R.string.label_streak)
    val CURRENT_STREAK: String get() = str(R.string.label_current_streak)
    val BEST_STREAK: String get() = str(R.string.label_best_streak)
    val PER_VIDEO: String get() = str(R.string.label_per_video)
    val SHARES: String get() = str(R.string.label_shares)
    val DAILY_AVERAGE: String get() = str(R.string.label_daily_average)
    val ACTIVE_DAYS: String get() = str(R.string.label_active_days)
    val FIRST_VIDEO: String get() = str(R.string.label_first_video)
    val LAST_VIDEO: String get() = str(R.string.label_last_video)
    val PLATFORMS: String get() = str(R.string.label_platforms)
    val CATEGORIES: String get() = str(R.string.label_categories)
    val CREATORS: String get() = str(R.string.label_creators)
    val TOP_CREATOR: String get() = str(R.string.label_top_creator)
    val TOP_CATEGORY: String get() = str(R.string.label_top_category)
    val EQUIVALENCES: String get() = str(R.string.label_equivalences)
    val SINCE_START: String get() = str(R.string.label_since_start)
    val CONNECTED: String get() = str(R.string.label_connected)
    val DISCONNECTED: String get() = str(R.string.label_disconnected)
    val NOT_INSTALLED: String get() = str(R.string.label_not_installed)

    /** «100 % LOCAL». */
    val LOCAL_STAMP: String get() = str(R.string.label_local_stamp, Format.percent(1.0))
    val WEEK: String get() = str(R.string.label_week)
    val MONTH: String get() = str(R.string.label_month)
    val YEAR: String get() = str(R.string.label_year)
    val ALL: String get() = str(R.string.label_all)
    val YES: String get() = str(R.string.label_yes)
    val NO: String get() = str(R.string.label_no)

    // Pestañas de la barra inferior.
    val TAB_TODAY: String get() = str(R.string.label_tab_today)
    val TAB_STATS: String get() = str(R.string.label_tab_stats)
    val TAB_WRAPPED: String get() = str(R.string.label_tab_wrapped)
    /** La pestaña de Flap (antes «Pasaporte»; la clave se queda). */
    val TAB_PASSPORT: String get() = str(R.string.label_tab_passport)
}

/** Textos de botón (estilo `button`, MAYÚSCULAS). Verbos directos. */
object Buttons {
    val CONNECT: String get() = str(R.string.button_connect)
    val RETRY: String get() = str(R.string.button_retry)
    val SHARE: String get() = str(R.string.button_share)
    val SAVE: String get() = str(R.string.button_save)
    val NEXT: String get() = str(R.string.button_next)
    val START: String get() = str(R.string.button_start)
    val ACCEPT_AND_ENABLE: String get() = str(R.string.button_accept_and_enable)
    val NOT_NOW: String get() = str(R.string.button_not_now)
    val OPEN_SETTINGS: String get() = str(R.string.button_open_settings)
    val OPEN_ACCESSIBILITY: String get() = str(R.string.button_open_accessibility)
    val OPEN_APP_INFO: String get() = str(R.string.button_open_app_info)
    val ALLOW_NOTIFICATIONS: String get() = str(R.string.button_allow_notifications)
    val CANCEL: String get() = str(R.string.button_cancel)
    val CLOSE: String get() = str(R.string.button_close)
    val DELETE_ALL: String get() = str(R.string.button_delete_all)
    val EXPORT_CSV: String get() = str(R.string.button_export_csv)
    val IMPORT_CSV: String get() = str(R.string.button_import_csv)
    val IMPORT_ADD: String get() = str(R.string.button_import_add)
    val IMPORT_REPLACE: String get() = str(R.string.button_import_replace)
    val IMPORT_REPLACE_CONFIRM: String get() = str(R.string.button_import_replace_confirm)
    val SEE_SUMMARY: String get() = str(R.string.button_see_summary)
    val SEE_PERMISSION: String get() = str(R.string.button_see_permission)
    val REMOVE_GOAL: String get() = str(R.string.button_remove_goal)
    val SET_GOAL: String get() = str(R.string.button_set_goal)
    val DONE: String get() = str(R.string.button_done)
    val BACK: String get() = str(R.string.button_back)
    val COPY: String get() = str(R.string.button_copy)
    val SHARE_LOG: String get() = str(R.string.button_share_log)
    val CLEAR: String get() = str(R.string.button_clear)
}

/**
 * Descripciones para TalkBack (`contentDescription`) de iconos y acciones. En frase normal,
 * diciendo qué hace el botón.
 */
object A11y {
    val SETTINGS: String get() = str(R.string.a11y_settings)

    /** Pestañas, en minúsculas para que TalkBack las lea bien. */
    val TAB_TODAY: String get() = str(R.string.a11y_tab_today)
    val TAB_STATS: String get() = str(R.string.a11y_tab_stats)
    val TAB_WRAPPED: String get() = str(R.string.a11y_tab_wrapped)
    val TAB_PASSPORT: String get() = str(R.string.a11y_tab_passport)
    val SHARE_SUMMARY: String get() = str(R.string.a11y_share_summary)
    val SAVE_IMAGE: String get() = str(R.string.a11y_save_image)
    val BACK: String get() = str(R.string.a11y_back)
    val CLOSE: String get() = str(R.string.a11y_close)
    val PREVIOUS_PERIOD: String get() = str(R.string.a11y_previous_period)
    val NEXT_PERIOD: String get() = str(R.string.a11y_next_period)
    val PAUSE: String get() = str(R.string.a11y_pause)
    val RESUME: String get() = str(R.string.a11y_resume)
    val OPEN_DAY: String get() = str(R.string.a11y_open_day)
    val LOADING: String get() = str(R.string.a11y_loading)
    val DECREASE: String get() = str(R.string.a11y_decrease)
    val INCREASE: String get() = str(R.string.a11y_increase)

    /** «Diapositiva 3 de 10». */
    fun slide(index: Int, total: Int): String = str(R.string.a11y_slide, Format.integer(index), Format.integer(total))

    val NEXT_SLIDE: String get() = str(R.string.a11y_next_slide)
    val PREVIOUS_SLIDE: String get() = str(R.string.a11y_previous_slide)
    val OPEN_SUMMARY: String get() = str(R.string.a11y_open_summary)
}
