package com.reelometro.app.ui.support

/**
 * Estado guardado del aviso de apoyo (DataStore, `SettingsRepository.supportPrompt`).
 *
 * @param usageDays días distintos en los que se ha abierto la app.
 * @param lastUsageDay último día (epochDay) en que se abrió; un mismo día cuenta una vez.
 * @param timesShown veces que ya ha salido el aviso.
 * @param lastShownDay día (epochDay) en que salió por última vez.
 * @param finished abrió el GitHub o pulsó «No volver a mostrar»: no sale nunca más.
 */
data class SupportPromptState(
    val usageDays: Int = 0,
    val lastUsageDay: Long? = null,
    val timesShown: Int = 0,
    val lastShownDay: Long? = null,
    val finished: Boolean = false,
)

/**
 * Cuándo sale el **aviso de apoyo** (lógica pura, sin Android).
 *
 * - Solo con el onboarding y el recorrido guiado terminados, y con un enlace al que llevar.
 * - La 1.ª vez, cuando se lleva al menos [FIRST_AFTER_USAGE_DAYS] días distintos abriendo la app.
 * - Una 2.ª y última vez si la 1.ª se cerró con «Ahora no» y han pasado [RETRY_AFTER_DAYS] días.
 * - Nunca más tras abrir el GitHub o pulsar «No volver a mostrar» ([SupportPromptState.finished]).
 * - Como mucho una vez al día.
 *
 * Se apunta como enseñado en el momento de salir (si Android mata la app con el aviso abierto, cuenta).
 */
object SupportPrompt {
    const val FIRST_AFTER_USAGE_DAYS = 3
    const val RETRY_AFTER_DAYS = 30
    const val MAX_TIMES = 2

    fun shouldShow(
        state: SupportPromptState,
        today: Long,
        onboardingDone: Boolean,
        tourDone: Boolean,
        hasLink: Boolean,
    ): Boolean {
        if (!onboardingDone || !tourDone || !hasLink) return false
        if (state.finished || state.timesShown >= MAX_TIMES) return false
        if (state.lastShownDay == today) return false
        return when (state.timesShown) {
            0 -> state.usageDays >= FIRST_AFTER_USAGE_DAYS
            else -> {
                // Sin la fecha de la vez anterior no se sabe si ha pasado el plazo: mejor no insistir.
                val last = state.lastShownDay ?: return false
                today - last >= RETRY_AFTER_DAYS
            }
        }
    }

    /**
     * Se ha abierto la app el día [today]: suma un día de uso solo si es posterior al último apuntado (con la fecha
     * hacia atrás, un viaje o el reloj cambiado, el mismo día no cuenta dos veces).
     */
    fun recordUsage(state: SupportPromptState, today: Long): SupportPromptState =
        if (state.lastUsageDay != null && today <= state.lastUsageDay) state else state.copy(usageDays = state.usageDays + 1, lastUsageDay = today)

    /** El aviso acaba de salir el día [today]. */
    fun markShown(state: SupportPromptState, today: Long): SupportPromptState =
        state.copy(timesShown = state.timesShown + 1, lastShownDay = today)

    /** Abrió el GitHub o pulsó «No volver a mostrar». */
    fun finish(state: SupportPromptState): SupportPromptState = state.copy(finished = true)

    /** Gancho de QA: listo para salir en la próxima entrada a una pestaña. */
    fun readyForQa(today: Long): SupportPromptState =
        SupportPromptState(usageDays = FIRST_AFTER_USAGE_DAYS, lastUsageDay = today)
}
