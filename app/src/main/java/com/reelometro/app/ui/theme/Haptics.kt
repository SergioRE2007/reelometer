package com.reelometro.app.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Vibraciones con significado (docs/DESIGN.md §6). Usa la API del sistema
 * (respeta el ajuste de «respuesta táctil» del usuario) con alternativas por versión.
 */
@Stable
class Haptics(private val view: View) {
    /** Pulsación de botón. */
    fun press() = perform(HapticFeedbackConstants.VIRTUAL_KEY)

    /** Recorrer barras / celdas / diapositivas: un tic suave. */
    fun tick() = perform(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        else HapticFeedbackConstants.CLOCK_TICK,
    )

    /** El marcador se asienta en su valor. */
    fun settle() = perform(HapticFeedbackConstants.CLOCK_TICK)

    /** Se activa una pulsación larga (tooltip, pausa). */
    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)

    fun toggle(on: Boolean) = perform(
        when {
            Build.VERSION.SDK_INT >= 34 && on -> HapticFeedbackConstants.TOGGLE_ON
            Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.TOGGLE_OFF
            else -> HapticFeedbackConstants.CONTEXT_CLICK
        },
    )

    /** Algo salió bien (servicio conectado, exportación hecha). */
    fun confirm() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
    )

    /** Acción destructiva confirmada o rechazo. */
    fun reject() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
    )

    private fun perform(constant: Int) {
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
