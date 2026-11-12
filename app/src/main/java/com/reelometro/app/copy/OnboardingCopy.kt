package com.reelometro.app.copy

import com.reelometro.app.R
import com.reelometro.app.data.Platform
import com.reelometro.app.detection.CheckFailure
import com.reelometro.app.detection.OemFamily
import com.reelometro.app.detection.OemGuide
import com.reelometro.app.detection.OemScreen
import com.reelometro.app.detection.OemStepKind
import com.reelometro.app.stats.Format

/**
 * **Onboarding** (primera vez, 4 pasos a pantalla completa):
 *  1. Qué hace · 2. Privacidad · 3. Permiso de accesibilidad ([DisclosureCopy] + guía + «Ajuste
 *  restringido») · 4. Avisos y objetivo diario opcional.
 *
 * Las palabras están en `res/values/copy_onboarding.xml`. Los nombres de menús de Android van entre
 * comillas latinas y tal como salen en cada idioma (AOSP/Pixel; los fabricantes a veces los cambian,
 * por eso se dan alternativas).
 */
object OnboardingCopy {

    const val STEPS = 4

    /** «PASO 1 DE 4». */
    fun stepLabel(index: Int, total: Int = STEPS): String =
        str(R.string.onboarding_step_label, Format.integer(index), Format.integer(total))

    /** Para los pasos 1–2. Nunca debe saltarse la divulgación del paso 3. */
    val SKIP: String get() = str(R.string.onboarding_skip)

    // ------------------------------------------------------------------ 1. Qué hace

    val WHAT_TITLE: String get() = str(R.string.onboarding_what_title)

    val WHAT_BODY: String get() = str(R.string.onboarding_what_body)

    /** Hasta dónde cuenta solo el marcador de demostración. */
    const val WHAT_DEMO_COUNT = 212

    /** Tiempo de la demostración: 47 minutos, los de la frase de referencia. */
    const val WHAT_DEMO_MS = 47 * 60_000L

    /** Línea bajo el marcador de demostración: «47 MIN DE PANTALLA». */
    fun demoTime(ms: Long): String = str(R.string.onboarding_demo_time, Words.durationLabel(ms))

    /** contentDescription del marcador de demostración: «Un marcador que cuenta hasta 212 vídeos.». */
    fun demoDescription(count: Int): String = str(R.string.onboarding_demo_description, Words.videos(count))

    // ------------------------------------------------------------------ 2. Privacidad

    val PRIVACY_TITLE: String get() = str(R.string.onboarding_privacy_title)

    val PRIVACY_POINTS: List<LabeledText>
        get() = listOf(
            LabeledText(str(R.string.onboarding_privacy_accounts_label), str(R.string.onboarding_privacy_accounts_text)),
            LabeledText(str(R.string.onboarding_privacy_servers_label), str(R.string.onboarding_privacy_servers_text)),
            LabeledText(str(R.string.onboarding_privacy_ads_label), str(R.string.onboarding_privacy_ads_text)),
            LabeledText(str(R.string.onboarding_privacy_internet_label), str(R.string.onboarding_privacy_internet_text)),
        )

    val PRIVACY_STAMP: String get() = Labels.LOCAL_STAMP

    /** Segunda línea del sello. */
    val PRIVACY_STAMP_SUBTEXT: String get() = str(R.string.onboarding_privacy_stamp_subtext)

    val PRIVACY_FOOTNOTE: String get() = str(R.string.onboarding_privacy_footnote)

    // ------------------------------------------------------------------ 3. Permiso (guía)
    // La divulgación destacada está en DisclosureCopy y va ANTES de esta guía.

    val GUIDE_TITLE: String get() = str(R.string.onboarding_guide_title)

    val GUIDE_STEPS: List<Step>
        get() = listOf(
            Step(1, str(R.string.onboarding_guide_step_1)),
            Step(2, str(R.string.onboarding_guide_step_2)),
            Step(3, str(R.string.onboarding_guide_step_3)),
            Step(4, str(R.string.onboarding_guide_step_4)),
            Step(5, str(R.string.onboarding_guide_step_5)),
        )

    val GUIDE_BUTTON: String get() = Buttons.OPEN_ACCESSIBILITY

    /** Etiqueta mientras se espera a que el servicio se conecte. */
    val GUIDE_WAITING: String get() = str(R.string.onboarding_guide_waiting)

    val GUIDE_CONNECTED_LABEL: String get() = Labels.CONNECTED

    /** Lo que muestra el marcador al conectar (voltea a «OK» + háptico CONFIRM). */
    val GUIDE_CONNECTED_COUNTER: String get() = str(R.string.onboarding_guide_connected_counter)

    /** Al volver con el servicio activo. */
    fun connected(seed: Long): String = pick(seed, "onboarding.connected", R.array.onboarding_connected)

    /** Al volver sin haberlo activado. */
    fun notYet(seed: Long): String = pick(seed, "onboarding.notYet", R.array.onboarding_not_yet)

    /** Bajo el «OK»: qué pasa ahora. */
    val GUIDE_CONNECTED_BODY: String get() = str(R.string.onboarding_guide_connected_body)

    /** Con el servicio ya activo, en la pantalla de conectar (fuera del onboarding). */
    val GUIDE_DONE: String get() = Buttons.DONE

    /** Seguir sin activar el servicio (solo en el onboarding: la divulgación ya se ha visto). */
    val GUIDE_SKIP: String get() = Buttons.NEXT

    /** contentDescription del marcador de estado: conectado o esperando. */
    fun guideStatusDescription(connected: Boolean): String =
        if (connected) str(R.string.onboarding_guide_status_connected) else str(R.string.onboarding_guide_status_waiting)

    /** Enlace a la ayuda de «Ajuste restringido». */
    val RESTRICTED_LINK: String get() = str(R.string.onboarding_restricted_link)

    // ------------------------------------------------------------------ 3b. «Ajuste restringido» (Android 13+)

    val RESTRICTED_TITLE: String get() = str(R.string.onboarding_restricted_title)

    val RESTRICTED_BODY: String get() = str(R.string.onboarding_restricted_body)

    val RESTRICTED_STEPS: List<Step>
        get() = listOf(
            Step(1, str(R.string.onboarding_restricted_step_1)),
            Step(2, str(R.string.onboarding_restricted_step_2)),
            Step(3, str(R.string.onboarding_restricted_step_3)),
            Step(4, str(R.string.onboarding_restricted_step_4)),
        )

    val RESTRICTED_NOTE: String get() = str(R.string.onboarding_restricted_note)

    val RESTRICTED_BUTTON: String get() = Buttons.OPEN_APP_INFO

    // ------------------------------------------------------------------ 3c. Activado, pero sin conectar

    /** Marcador y título cuando el servicio está activado pero el sistema no lo ha conectado. */
    val STALLED_LABEL: String get() = str(R.string.onboarding_stalled_label)

    val STALLED_TITLE: String get() = str(R.string.onboarding_stalled_title)

    val STALLED_BODY: String get() = str(R.string.onboarding_stalled_body)

    /** Va con el texto del fallo del arranque, tal cual (para mandarlo al autor). */
    val STALLED_ERROR_LABEL: String get() = str(R.string.onboarding_stalled_error_label)

    // ------------------------------------------------------------------ 3d. Guía por marca (detection/OemGuide)

    /** «Pasos para Xiaomi, Redmi y POCO». */
    fun oemTitle(family: OemFamily): String = str(
        when (family) {
            OemFamily.XIAOMI -> R.string.onboarding_oem_title_xiaomi
            OemFamily.SAMSUNG -> R.string.onboarding_oem_title_samsung
            OemFamily.HUAWEI -> R.string.onboarding_oem_title_huawei
            OemFamily.HONOR -> R.string.onboarding_oem_title_honor
            OemFamily.COLOROS -> R.string.onboarding_oem_title_coloros
            OemFamily.VIVO -> R.string.onboarding_oem_title_vivo
            OemFamily.STOCK -> R.string.onboarding_oem_title_stock
        },
    )

    /** Los pasos de [OemGuide.steps], numerados. */
    fun oemSteps(family: OemFamily, unusedApps: Boolean = false): List<Step> =
        OemGuide.steps(family, unusedApps).mapIndexed { i, step -> Step(i + 1, oemStepText(family, step.kind)) }

    /** Texto de un paso: el de la marca o, si la marca no lo tiene propio, el genérico de ese tipo. */
    fun oemStepText(family: OemFamily, kind: OemStepKind): String = str(
        when (kind) {
            OemStepKind.AUTOSTART -> when (family) {
                OemFamily.XIAOMI -> R.string.onboarding_oem_xiaomi_autostart
                OemFamily.HUAWEI, OemFamily.HONOR -> R.string.onboarding_oem_huawei_autostart
                OemFamily.SAMSUNG, OemFamily.COLOROS, OemFamily.VIVO, OemFamily.STOCK -> R.string.onboarding_oem_autostart
            }
            OemStepKind.BATTERY -> when (family) {
                OemFamily.XIAOMI -> R.string.onboarding_oem_xiaomi_battery
                OemFamily.COLOROS -> R.string.onboarding_oem_coloros_battery
                OemFamily.VIVO -> R.string.onboarding_oem_vivo_battery
                OemFamily.SAMSUNG, OemFamily.HUAWEI, OemFamily.HONOR, OemFamily.STOCK -> R.string.onboarding_oem_app_battery
            }
            OemStepKind.NEVER_SLEEP -> R.string.onboarding_oem_samsung_sleep
            OemStepKind.LOCK_RECENTS -> when (family) {
                OemFamily.SAMSUNG -> R.string.onboarding_oem_samsung_recents
                OemFamily.COLOROS -> R.string.onboarding_oem_coloros_recents
                OemFamily.HUAWEI, OemFamily.HONOR, OemFamily.VIVO -> R.string.onboarding_oem_recents_swipe
                OemFamily.XIAOMI, OemFamily.STOCK -> R.string.onboarding_oem_xiaomi_recents
            }
            OemStepKind.TOGGLE_SERVICE -> R.string.onboarding_oem_toggle
            OemStepKind.OTHER_MANAGERS -> R.string.onboarding_oem_stock_other
            OemStepKind.UNUSED_APPS -> R.string.onboarding_oem_unused
        },
    )

    /** Botón bajo un paso: el mismo texto que cita el paso («ABRIR INICIO AUTOMÁTICO»). */
    fun oemButton(family: OemFamily, screen: OemScreen): String = when (screen) {
        OemScreen.AUTOSTART -> when (family) {
            OemFamily.HUAWEI, OemFamily.HONOR -> str(R.string.onboarding_oem_button_startup_huawei)
            OemFamily.XIAOMI, OemFamily.SAMSUNG, OemFamily.COLOROS, OemFamily.VIVO, OemFamily.STOCK ->
                str(R.string.onboarding_oem_button_autostart)
        }
        OemScreen.BATTERY_APP -> str(R.string.onboarding_oem_button_battery_saver)
        OemScreen.BATTERY_SETTINGS -> str(R.string.onboarding_oem_button_battery)
        OemScreen.APP_INFO, OemScreen.UNUSED_APPS -> Buttons.OPEN_APP_INFO
        OemScreen.ACCESSIBILITY -> Buttons.OPEN_ACCESSIBILITY
    }

    // ------------------------------------------------------------------ 3d bis. El sistema ha apagado el servicio

    /** El servicio funcionaba y ha salido apagado sin que el usuario lo apagara (`ServiceJournalRules.lostBySystem`). */
    val LOST_TITLE: String get() = str(R.string.onboarding_lost_title)

    /** Explicación; con el motivo del cierre que apunta Android ([ExitCopy.categoryLabel]) detrás, si lo hay. */
    fun lostBody(reason: com.reelometro.app.detection.ExitCategory?): String {
        val body = str(R.string.onboarding_lost_body)
        return if (reason == null) body else str(R.string.onboarding_lost_reason, body, ExitCopy.categoryLabel(reason))
    }

    /** No se ha podido abrir ninguna pantalla (ni la genérica). */
    val OEM_OPEN_FAILED: String get() = str(R.string.onboarding_oem_open_failed)

    /** Enlace plegable con el servicio ya conectado. */
    val OEM_PREVENT_LINK: String get() = str(R.string.onboarding_oem_prevent_link)

    // ------------------------------------------------------------------ 3e. Prueba de un minuto (detection/ServiceCheck)

    val CHECK_LABEL: String get() = str(R.string.onboarding_check_label)

    val CHECK_TITLE: String get() = str(R.string.onboarding_check_title)

    /** «Abre TikTok, Instagram o YouTube, pasa 3 vídeos cortos y vuelve aquí…». */
    fun checkBody(target: Int): String = plural(R.plurals.onboarding_check_body, target, Format.integer(target))

    /** «ABRIR TIKTOK» (la marca no se traduce). */
    fun checkOpenApp(platform: Platform): String = str(R.string.onboarding_check_open_app, platform.appName)

    val CHECK_NONE_INSTALLED: String get() = str(R.string.onboarding_check_none_installed)

    val CHECK_RUNNING_LABEL: String get() = str(R.string.onboarding_check_running_label)

    /** «2 de 3 vídeos». */
    fun checkProgress(videos: Long, target: Int): String =
        plural(R.plurals.onboarding_check_progress, target, Format.integer(videos), Format.integer(target))

    val CHECK_RUNNING_BODY: String get() = str(R.string.onboarding_check_running_body)

    val CHECK_VERIFY: String get() = str(R.string.onboarding_check_verify)

    val CHECK_CANCEL: String get() = str(R.string.onboarding_check_cancel)

    val CHECK_PASSED_LABEL: String get() = str(R.string.onboarding_check_passed_label)

    /** «He contado 3 vídeos. Todo funciona.». */
    fun checkPassed(videos: Long): String = plural(R.plurals.onboarding_check_passed, videos, Format.integer(videos))

    /** Ha contado, pero el proceso se ha reiniciado durante la prueba: va seguido de la guía por marca. */
    val CHECK_PASSED_RESTARTED: String get() = str(R.string.onboarding_check_passed_restarted)

    val CHECK_FAILED_LABEL: String get() = str(R.string.onboarding_check_failed_label)

    /** El diagnóstico de cada fallo. */
    fun checkFailure(reason: CheckFailure): String = str(
        when (reason) {
            CheckFailure.OFF -> R.string.onboarding_check_fail_off
            CheckFailure.KILLED -> R.string.onboarding_check_fail_killed
            CheckFailure.NOT_CONNECTED -> R.string.onboarding_check_fail_not_connected
            CheckFailure.NO_EVENTS -> R.string.onboarding_check_fail_no_events
            CheckFailure.PLATFORMS_OFF -> R.string.onboarding_check_fail_platforms_off
            CheckFailure.NO_VIDEOS -> R.string.onboarding_check_fail_no_videos
        },
    )

    val CHECK_OPEN_CALIBRATION: String get() = str(R.string.onboarding_check_open_calibration)

    val CHECK_OPEN_SETTINGS: String get() = str(R.string.onboarding_check_open_settings)

    val CHECK_RETRY: String get() = str(R.string.onboarding_check_retry)

    // ------------------------------------------------------------------ 4. Avisos y objetivo

    val FINAL_TITLE: String get() = str(R.string.onboarding_final_title)

    val NOTIFY_DAILY_LABEL: String get() = str(R.string.onboarding_notify_daily_label)

    /** «A las 21:30, tu cuenta del día.». */
    fun notifyDaily(hour: Int, minute: Int): String =
        str(R.string.onboarding_notify_daily, Format.capitalize(Words.atTime(hour, minute)))

    val NOTIFY_MONTHLY_LABEL: String get() = str(R.string.onboarding_notify_monthly_label)

    val NOTIFY_MONTHLY: String get() = str(R.string.onboarding_notify_monthly)

    /** Antes de pedir POST_NOTIFICATIONS (Android 13+). */
    val NOTIFY_RATIONALE: String get() = str(R.string.onboarding_notify_rationale)

    val NOTIFY_BUTTON: String get() = Buttons.ALLOW_NOTIFICATIONS

    val NOTIFY_DENIED: String get() = str(R.string.onboarding_notify_denied)

    val GOAL_LABEL: String get() = str(R.string.onboarding_goal_label)

    val GOAL_BODY: String get() = str(R.string.onboarding_goal_body)

    val GOAL_NONE: String get() = str(R.string.onboarding_goal_none)

    val GOAL_OTHER: String get() = str(R.string.onboarding_goal_other)

    /** Botones del selector «OTRO» (TalkBack). */
    val GOAL_DECREASE: String get() = str(R.string.onboarding_goal_decrease)
    val GOAL_INCREASE: String get() = str(R.string.onboarding_goal_increase)

    /** Opción numérica: «150». */
    fun goalOption(goal: Int): String = Format.integer(goal)

    /** Ayuda del campo «OTRO»: «Entre 5 y 2.000 vídeos al día.». */
    fun goalRange(min: Int, max: Int): String = str(R.string.onboarding_goal_range, Format.integer(min), Format.integer(max))

    /** Confirmación de la elección: «Como mucho, 150 al día.» / «Sin objetivo: solo contar.». */
    fun goalChosen(goal: Int?): String =
        if (goal == null || goal <= 0) {
            str(R.string.onboarding_goal_chosen_none)
        } else {
            str(R.string.onboarding_goal_chosen, Format.integer(goal))
        }

    val FINAL_BUTTON: String get() = Buttons.START

    /** Cabeceras de los dos bloques del paso 4. */
    val NOTIFY_SECTION: String get() = str(R.string.onboarding_notify_section)

    /** contentDescription del indicador de pasos: «Paso 2 de 4». */
    fun stepDescription(index: Int, total: Int = STEPS): String =
        str(R.string.onboarding_step_description, Format.integer(index), Format.integer(total))
}
