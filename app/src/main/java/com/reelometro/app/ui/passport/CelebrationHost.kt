package com.reelometro.app.ui.passport

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelometro.app.ReelometroApp
import com.reelometro.app.copy.PassportCopy
import com.reelometro.app.game.GameEvent
import com.reelometro.app.game.PassportSource
import com.reelometro.app.game.PassportState
import com.reelometro.app.ui.components.Celebration
import com.reelometro.app.ui.components.CelebrationAction
import com.reelometro.app.ui.components.CelebrationKind
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.flap.FlapEnter
import com.reelometro.app.ui.components.flap.SplitFlapText
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Celebraciones pendientes del Pasaporte (sello nuevo, nivel nuevo, reto cumplido) la próxima vez
 * que se abre la app, una sola vez cada una, con el componente común `ui/components/Celebration`.
 * Lo pinta `AppShell` sobre las pestañas.
 *
 * Con [enabled] observa `PassportSource.observe().pending` y enseña la primera que no se haya
 * cerrado; al cerrarla (SEGUIR, atrás, tocar fuera o VER EL PASAPORTE) la marca con
 * `markCelebrated` y pasa a la siguiente. El nivel nuevo ofrece COMPARTIR TICKET (imagen
 * 1080 × 1920 con el estilo del ticket del Wrapped) y registra `LEVEL_TICKET_SHARED`. Sin
 * [enabled] no observa nada y, si había una en pantalla, se va sin marcarla.
 *
 * @param enabled modo pasaporte activo y en una pestaña (ni onboarding, ni historia, ni recorrido).
 * @param onOpenPassport abre la pestaña Pasaporte (p. ej. «VER EL PASAPORTE» tras un sello).
 */
@Composable
fun CelebrationHost(
    enabled: Boolean,
    onOpenPassport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val passport: PassportSource = remember(context) { ReelometroApp.from(context).passport }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    val source: Flow<PassportState?> = remember(passport, enabled) {
        if (enabled) passport.observe().catch { } else flowOf(null)
    }
    val passportState by source.collectAsStateWithLifecycle(initialValue = null)
    // Cerradas en esta sesión: no vuelven aunque el origen tarde en quitarlas de `pending`.
    var dismissed by remember { mutableStateOf(emptySet<String>()) }

    val snapshot = passportState
    val current = snapshot?.let { CelebrationModel.next(it.pending, dismissed) }
    val ui = remember(current, snapshot?.today) {
        if (current != null && snapshot != null) CelebrationModel.of(current, snapshot) else null
    }
    // La última que se vio, para que pueda salir con su animación.
    var last by remember { mutableStateOf<CelebrationUi?>(null) }
    LaunchedEffect(ui) { if (ui != null) last = ui }
    val shown = ui ?: last ?: return

    val dismiss: () -> Unit = {
        val key = shown.key
        dismissed = dismissed + key
        scope.launch { runCatching { passport.markCelebrated(listOf(key)) } }
    }
    val level = shown.level
    val action = if (shown.type == CelebrationType.LEVEL && level != null) {
        CelebrationAction(
            label = shown.actionLabel,
            contentDescription = shown.actionDescription,
            onClick = {
                scope.launch {
                    val state = snapshot ?: runCatching { passport.snapshot() }.getOrNull()
                    val shared = state != null && LevelTicketSharer.share(context, LevelTicketModel.build(state, level))
                    if (shared) {
                        haptics.confirm()
                        runCatching { passport.recordEvent(GameEvent.LEVEL_TICKET_SHARED) }
                    } else {
                        haptics.reject()
                    }
                }
            },
        )
    } else {
        CelebrationAction(
            label = shown.actionLabel,
            onClick = {
                dismiss()
                onOpenPassport()
            },
        )
    }

    Celebration(
        visible = enabled && ui != null,
        kind = when (shown.type) {
            CelebrationType.STAMP -> CelebrationKind.STAMP
            CelebrationType.LEVEL -> CelebrationKind.LEVEL
            CelebrationType.CHALLENGE -> CelebrationKind.CHALLENGE
        },
        label = shown.label,
        title = shown.title,
        onDismiss = dismiss,
        modifier = modifier,
        caption = shown.caption,
        action = action,
        dismissLabel = PassportCopy.CONTINUE,
        art = { CelebrationArt(shown) },
    )
}

/** El protagonista de cada celebración: el sello, el código del nivel en el panel o la cifra. */
@Composable
private fun CelebrationArt(ui: CelebrationUi) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    when (ui.type) {
        CelebrationType.STAMP -> {
            val stamp = ui.stamp
            if (stamp != null) PassportStamp(stamp = stamp, tilt = false, modifier = Modifier.size(FlapDefaults.Size))
        }
        CelebrationType.LEVEL -> SplitFlapText(
            text = ui.levelCode.orEmpty(),
            style = type.displayXL,
            enter = FlapEnter.Blank,
            contentDescription = ui.title,
        )
        CelebrationType.CHALLENGE -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(ui.figure.orEmpty(), style = type.displayXL, color = colors.text)
            Spacer(Modifier.height(Space.x2))
            Label(ui.figureCaption.orEmpty())
        }
    }
}
