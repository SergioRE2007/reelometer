package com.reelometro.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelometro.app.ReelometroApp
import com.reelometro.app.copy.OnboardingCopy
import com.reelometro.app.data.settings.AppSettings
import com.reelometro.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Objetivo elegido en el paso 4. */
sealed interface GoalChoice {
    /** «Sin objetivo: solo contar.». */
    data object None : GoalChoice

    /** Una de [AppSettings.SUGGESTED_GOALS]. */
    data class Preset(val value: Int) : GoalChoice

    /** «OTRO», con su selector. */
    data class Custom(val value: Int) : GoalChoice

    val goal: Int?
        get() = when (this) {
            None -> null
            is Preset -> value
            is Custom -> value
        }
}

/** Estado de los avisos del sistema (interfaz para poder probar sin Android). */
interface NotificationAccess {
    /** Android 13+: hace falta pedir `POST_NOTIFICATIONS`. */
    val needsRuntimePermission: Boolean

    /** Los avisos de la app pueden mostrarse (permiso concedido y no bloqueados). */
    fun areAllowed(): Boolean
}

class AndroidNotificationAccess(context: Context) : NotificationAccess {
    private val app = context.applicationContext

    override val needsRuntimePermission: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun areAllowed(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(app).areNotificationsEnabled()
    }
}

@Immutable
data class OnboardingState(
    /** 0 … [OnboardingCopy.STEPS] − 1. */
    val step: Int,
    /** Sentido del último cambio de paso (+1 adelante, −1 atrás) para la transición. */
    val direction: Int,
    val notifyDaily: Boolean,
    val notifyMonthly: Boolean,
    val notifyHour: Int,
    val notifyMinute: Int,
    val notificationsAllowed: Boolean,
    val needsNotificationPermission: Boolean,
    /** Ha dicho que no al permiso de avisos en este onboarding. */
    val notificationsDenied: Boolean,
    val goal: GoalChoice,
) {
    val isLast: Boolean get() = step == OnboardingCopy.STEPS - 1
}

sealed interface OnboardingUiState {
    data object Loading : OnboardingUiState

    data class Ready(val onboarding: OnboardingState) : OnboardingUiState
}

/**
 * Onboarding de 4 pasos. El paso actual vive aquí (sobrevive a la rotación); el permiso del paso 3
 * lo lleva [ServicePermissionViewModel]. Avisos y objetivo se guardan al pulsar EMPEZAR ([finish]),
 * de una vez: si se abandona a medias, el onboarding vuelve a salir y no queda nada a medio guardar.
 */
class OnboardingViewModel(
    private val settings: SettingsRepository,
    private val notifications: NotificationAccess,
) : ViewModel() {

    private val local = MutableStateFlow<OnboardingState?>(null)

    val state: StateFlow<OnboardingUiState> = local
        .map { it?.let(OnboardingUiState::Ready) ?: OnboardingUiState.Loading }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState.Loading)

    init {
        viewModelScope.launch {
            val s = settings.current()
            local.value = OnboardingState(
                step = 0,
                direction = 1,
                notifyDaily = s.notifyDaily,
                notifyMonthly = s.notifyMonthly,
                notifyHour = s.notifyHour,
                notifyMinute = s.notifyMinute,
                notificationsAllowed = notifications.areAllowed(),
                needsNotificationPermission = notifications.needsRuntimePermission,
                notificationsDenied = false,
                goal = choiceFor(s.dailyGoal),
            )
        }
    }

    fun next() = edit { if (it.isLast) it else it.copy(step = it.step + 1, direction = 1) }

    fun back() = edit { if (it.step == 0) it else it.copy(step = it.step - 1, direction = -1) }

    /** SALTAR (pasos 1–2): directo al permiso; la divulgación nunca se salta. */
    fun skipToPermission() = edit { if (it.step >= PERMISSION_STEP) it else it.copy(step = PERMISSION_STEP, direction = 1) }

    fun setNotifyDaily(on: Boolean) = edit { it.copy(notifyDaily = on) }

    fun setNotifyMonthly(on: Boolean) = edit { it.copy(notifyMonthly = on) }

    /** Respuesta al diálogo de `POST_NOTIFICATIONS`. Si dice que no, los avisos se quedan apagados. */
    fun onNotificationPermission(granted: Boolean) = edit {
        if (granted) {
            it.copy(notificationsAllowed = notifications.areAllowed(), notificationsDenied = false)
        } else {
            it.copy(notificationsAllowed = false, notificationsDenied = true, notifyDaily = false, notifyMonthly = false)
        }
    }

    /** Al volver a la app (el usuario puede haber cambiado los avisos en Ajustes de Android). */
    fun refreshNotifications() = edit {
        val allowed = notifications.areAllowed()
        it.copy(notificationsAllowed = allowed, notificationsDenied = it.notificationsDenied && !allowed)
    }

    fun chooseGoal(choice: GoalChoice) = edit { it.copy(goal = choice) }

    /** «OTRO»: arranca en el objetivo que hubiera o en [DEFAULT_CUSTOM_GOAL]. */
    fun chooseCustomGoal() = edit {
        if (it.goal is GoalChoice.Custom) it else it.copy(goal = GoalChoice.Custom(it.goal.goal ?: DEFAULT_CUSTOM_GOAL))
    }

    fun setCustomGoal(value: Int) = edit {
        it.copy(goal = GoalChoice.Custom(value.coerceIn(AppSettings.MIN_GOAL, AppSettings.MAX_GOAL)))
    }

    /** EMPEZAR: guarda avisos y objetivo y, cuando está escrito, llama a [onSaved]. */
    fun finish(onSaved: () -> Unit) {
        val s = local.value ?: return
        viewModelScope.launch {
            settings.setNotifyDaily(s.notifyDaily)
            settings.setNotifyMonthly(s.notifyMonthly)
            settings.setDailyGoal(s.goal.goal)
            onSaved()
        }
    }

    private fun edit(block: (OnboardingState) -> OnboardingState) {
        local.update { current -> current?.let(block) }
    }

    companion object {
        /** Índice del paso del permiso (el 3.º). */
        const val PERMISSION_STEP = 2

        /** Punto de partida del selector «OTRO». */
        const val DEFAULT_CUSTOM_GOAL = 120

        fun choiceFor(goal: Int?): GoalChoice = when {
            goal == null || goal <= 0 -> GoalChoice.None
            goal in AppSettings.SUGGESTED_GOALS -> GoalChoice.Preset(goal)
            else -> GoalChoice.Custom(goal)
        }

        fun create(context: Context): OnboardingViewModel {
            val app = ReelometroApp.from(context)
            return OnboardingViewModel(app.settings, AndroidNotificationAccess(app))
        }
    }
}
