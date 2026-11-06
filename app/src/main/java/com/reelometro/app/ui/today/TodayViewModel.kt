package com.reelometro.app.ui.today

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reelometro.app.ReelometroApp
import com.reelometro.app.data.settings.AppSettings
import com.reelometro.app.detection.ReelAccessibilityService
import com.reelometro.app.stats.AllTime
import com.reelometro.app.stats.DayStats
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * Hoy: combina el día en curso, el total desde el primer registro, los ajustes (divulgación
 * aceptada, apps activas) y el estado del servicio de accesibilidad.
 *
 * Las fuentes llegan como flujos y funciones (no el repositorio entero) para probarlo con datos
 * falsos. El estado del servicio no tiene flujo en el sistema: la pantalla llama a
 * [refreshService] en cada `ON_RESUME` (al volver de los ajustes de accesibilidad, por ejemplo).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val today: Flow<DayStats>,
    private val allTime: Flow<AllTime>,
    private val settings: Flow<AppSettings>,
    private val serviceEnabled: () -> Boolean,
    private val clock: () -> LocalDate = { LocalDate.now() },
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val service = MutableStateFlow(serviceEnabled())
    private val attempt = MutableStateFlow(0)

    val uiState: StateFlow<TodayUiState> = attempt
        .flatMapLatest {
            combine(today, allTime, settings, service) { day, all, prefs, on -> TodayMapper.map(day, all, prefs, on) }
                .onStart { emit(TodayMapper.loading(clock())) }
                .catch { emit(TodayMapper.error(clock())) }
        }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayMapper.loading(clock()))

    /** Vuelve a mirar si el servicio está activado (en cada `ON_RESUME`). */
    fun refreshService() {
        service.value = serviceEnabled()
    }

    /** Reintenta tras un error. */
    fun retry() {
        attempt.value++
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val app = ReelometroApp.from(appContext)
                TodayViewModel(
                    today = app.repository.observeToday(),
                    allTime = app.repository.observeAllTime(),
                    settings = app.settings.settings,
                    serviceEnabled = { ReelAccessibilityService.isEnabled(appContext) },
                )
            }
        }
    }
}
