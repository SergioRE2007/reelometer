package com.reelometro.app.copy

import com.reelometro.app.R
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
