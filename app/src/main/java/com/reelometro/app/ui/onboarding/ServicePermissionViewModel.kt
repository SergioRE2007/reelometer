package com.reelometro.app.ui.onboarding

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelometro.app.ReelometroApp
import com.reelometro.app.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo que pinta el paso del permiso (paso 3 del onboarding y pantalla de conectar). */
@Immutable
data class ServicePermissionState(
    /** Se ha pulsado ACEPTAR Y ACTIVAR alguna vez: ya no se enseña la divulgación, sino la guía. */
    val disclosureAccepted: Boolean,
    /** Servicio activado en Ajustes › Accesibilidad. */
    val serviceEnabled: Boolean,
    /** Android 13+ con «Ajuste restringido» detectado. */
    val restricted: Boolean,
    /** Ayuda de «Ajuste restringido» desplegada (siempre, si está detectado). */
    val restrictedHelpVisible: Boolean,
    /** Ha ido a Ajustes y ha vuelto sin activarlo. */
    val returnedWithoutService: Boolean,
    /** Ha pulsado AHORA NO. */
    val declined: Boolean,
    /** El servicio se ha activado mientras esta pantalla estaba abierta (para el volteo a «OK» + háptico). */
    val connectedHere: Boolean,
)

sealed interface ServicePermissionUiState {
    data object Loading : ServicePermissionUiState

    data class Ready(val permission: ServicePermissionState) : ServicePermissionUiState
}

/**
 * Divulgación destacada + guía + «Ajuste restringido». Lo comparten el onboarding y la pantalla de
 * conectar. La aceptación solo se guarda al pulsar ACEPTAR ([accept]); atrás o AHORA NO no cuentan.
 * El estado del servicio se relee en cada `ON_RESUME` ([refresh]).
 */
class ServicePermissionViewModel(
    private val settings: SettingsRepository,
    private val probe: ServiceProbe,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private data class Local(
        val enabled: Boolean? = null,
        val restricted: Boolean = false,
        val helpOpen: Boolean = false,
        val openedSettings: Boolean = false,
        val declined: Boolean = false,
        val connectedHere: Boolean = false,
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<ServicePermissionUiState> =
        combine(settings.settings, local) { s, l ->
            val enabled = l.enabled ?: return@combine ServicePermissionUiState.Loading
            ServicePermissionUiState.Ready(
                ServicePermissionState(
                    disclosureAccepted = s.disclosureAccepted,
                    serviceEnabled = enabled,
                    restricted = l.restricted && !enabled,
                    restrictedHelpVisible = !enabled && (l.restricted || l.helpOpen),
                    returnedWithoutService = l.openedSettings && !enabled,
                    declined = l.declined,
                    connectedHere = l.connectedHere,
                ),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServicePermissionUiState.Loading)

    init {
        refresh()
    }

    /** Relee si el servicio está activo y si hay «Ajuste restringido». Llamar en cada `ON_RESUME`. */
    fun refresh() {
        viewModelScope.launch {
            val (enabled, restricted) = withContext(io) {
                val on = probe.isServiceEnabled()
                on to (!on && probe.isRestricted())
            }
            local.update { l ->
                l.copy(
                    enabled = enabled,
                    restricted = restricted,
                    connectedHere = l.connectedHere || (l.enabled == false && enabled),
                )
            }
        }
    }

    /** ACEPTAR Y ACTIVAR: guarda la aceptación. Quien llama abre Ajustes › Accesibilidad. */
    fun accept() {
        local.update { it.copy(openedSettings = true, declined = false) }
        viewModelScope.launch { settings.setDisclosureAccepted(true) }
    }

    /** ABRIR ACCESIBILIDAD desde la guía. */
    fun onOpenedSettings() {
        local.update { it.copy(openedSettings = true) }
    }

    /** AHORA NO: no se guarda nada ni se abre nada. */
    fun decline() {
        local.update { it.copy(declined = true) }
    }

    fun toggleRestrictedHelp() {
        local.update { it.copy(helpOpen = !it.helpOpen) }
    }

    companion object {
        fun create(context: Context): ServicePermissionViewModel {
            val app = ReelometroApp.from(context)
            return ServicePermissionViewModel(app.settings, AndroidServiceProbe(app))
        }
    }
}
