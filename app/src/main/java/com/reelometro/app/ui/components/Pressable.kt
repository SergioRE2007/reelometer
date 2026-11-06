package com.reelometro.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.reelometro.app.ui.theme.ReelHapticIndication
import com.reelometro.app.ui.theme.ReelIndication

/**
 * Hace pulsable cualquier cosa con el tacto de la app (docs/DESIGN.md §4 y §6): mientras el dedo
 * está encima, el elemento **se hunde** (escala 0,96 y baja 1 dp, spring `snappy`) y al completar
 * la pulsación vibra con `Haptics.press()`. Sin ripple de Material.
 *
 * Ponlo **antes** del fondo y del borde en la cadena para que también se hundan:
 * `Modifier.pressable(onClick).background(…).padding(…)`.
 *
 * @param haptic vibración al soltar (desactívala si `onClick` hace la suya, p. ej. `reject()`).
 * @param role rol para TalkBack (por defecto, botón).
 * @param onClickLabel verbo que TalkBack lee tras «toca dos veces para…».
 */
fun Modifier.pressable(
    onClick: () -> Unit,
    haptic: Boolean = true,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    interactionSource: MutableInteractionSource? = null,
): Modifier = clickable(
    interactionSource = interactionSource,
    indication = if (haptic) ReelHapticIndication else ReelIndication,
    enabled = enabled,
    onClickLabel = onClickLabel,
    role = role,
    onClick = onClick,
)

/**
 * Solo el efecto de hundirse, para gestos propios (p. ej. repetición con pulsación larga):
 * emite `PressInteraction` en [interactionSource] y este modificador dibuja la respuesta.
 */
fun Modifier.sinkOnPress(interactionSource: InteractionSource, haptic: Boolean = false): Modifier =
    indication(interactionSource, if (haptic) ReelHapticIndication else ReelIndication)
