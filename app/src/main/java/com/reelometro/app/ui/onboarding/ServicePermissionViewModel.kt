package com.reelometro.app.ui.onboarding

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelometro.app.ReelometroApp
import com.reelometro.app.data.settings.SettingsRepository
import com.reelometro.app.detection.ExitCategory
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
    /** Activado, pero Android no lo ha arrancado (o su arranque ha fallado): enseña los pasos para arreglarlo. */
    val serviceStalled: Boolean = false,
    /** Texto del fallo del arranque, si lo hubo. */
    val startError: String? = null,
    /** Por qué se cerró la app la última vez (sin funcionar o apagado por el sistema): una línea más con la explicación. */
    val stallReason: ExitCategory? = null,
    /** Apagado, pero no por el usuario: lo quitó el sistema al cerrar la app. Se explica encima de los pasos. */
    val serviceLost: Boolean = false,
    /**
     * En esta visita el servicio estaba apagado por el sistema (aunque ya se haya vuelto a activar): la guía para que
     * no se desconecte sale abierta, también en Android de serie.
     */
    val recoveredFromLoss: Boolean = false,
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
        val stalled: Boolean = false,
        val startError: String? = null,
        val stallReason: ExitCategory? = null,
        val lost: Boolean = false,
        val everLost: Boolean = false,
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
                    serviceStalled = enabled && l.stalled,
                    startError = l.startError.takeIf { enabled && l.stalled },
                    stallReason = l.stallReason.takeIf { (enabled && l.stalled) || (!enabled && l.lost) },
                    serviceLost = !enabled && l.lost,
                    recoveredFromLoss = l.everLost,
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
        viewModelScope.launch { probe.changes().collect { refresh() } }
    }

    /** Relee si el servicio está activo y si hay «Ajuste restringido». Llamar en cada `ON_RESUME`. */
    fun refresh() {
        viewModelScope.launch {
            val probed = withContext(io) {
                val on = probe.isServiceEnabled()
                val stalled = on && probe.isServiceStalled()
                val lost = !on && runCatching { probe.isServiceLost() }.getOrDefault(false)
                Probed(
                    enabled = on,
                    restricted = !on && probe.isRestricted(),
                    stalled = stalled,
                    startError = if (stalled) probe.startError() else null,
                    stallReason = if (stalled || lost) runCatching { probe.stallReason() }.getOrNull() else null,
                    lost = lost,
                )
            }
            local.update { l ->
                val wasWorking = l.enabled?.let { it && !l.stalled }
                val working = probed.enabled && !probed.stalled
                l.copy(
                    enabled = probed.enabled,
                    restricted = probed.restricted,
                    stalled = probed.stalled,
                    startError = probed.startError,
                    stallReason = probed.stallReason,
                    lost = probed.lost,
                    everLost = l.everLost || probed.lost,
                    connectedHere = l.connectedHere || (wasWorking == false && working),
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

    private data class Probed(
        val enabled: Boolean,
        val restricted: Boolean,
        val stalled: Boolean,
        val startError: String?,
        val stallReason: ExitCategory?,
        val lost: Boolean,
    )

    companion object {
        fun create(context: Context): ServicePermissionViewModel {
            val app = ReelometroApp.from(context)
            return ServicePermissionViewModel(app.settings, AndroidServiceProbe(app))
        }
    }
}
