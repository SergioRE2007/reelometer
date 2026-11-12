package com.reelometro.app.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.reelometro.app.copy.FlapCopy
import com.reelometro.app.game.FlapFakes
import com.reelometro.app.game.FlapFrame
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapSprite
import com.reelometro.app.ui.passport.FlapDefaults
import com.reelometro.app.ui.passport.FlapMapper
import com.reelometro.app.ui.passport.FlapScreen
import com.reelometro.app.ui.passport.FlapStill
import com.reelometro.app.ui.passport.FlapUiState
import com.reelometro.app.ui.passport.drawFlap
import com.reelometro.app.ui.theme.LocalReducedMotion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space

/*
 * Sección de la galería para la pestaña Flap: el arte de los cinco estados (ojos abiertos y
 * parpadeo) y la pestaña entera con `FlapFakes` (recién instalada y los cinco estados, con y sin
 * objetivo), con botones para ver el volteo al empeorar y el salto al revivir. GalleryActivity la
 * llama; mantén esta firma.
 */

/** Altura de la ventana donde se pinta la pestaña entera (tiene su propio scroll). */
private val ScreenHeight = 760.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlapGallerySection() {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    var reduced by remember { mutableStateOf(false) }
    val systemReduced = LocalReducedMotion.current

    // 0 = recién instalada; 1–5 = cada estado, del mejor al peor.
    var selected by remember { mutableIntStateOf(1) }
    var withGoal by remember { mutableStateOf(true) }
    var previous by remember { mutableStateOf<FlapMood?>(null) }
    val mood = if (selected == 0) FlapMood.EN_FORMA else FlapMood.entries[selected - 1]
    val fake = when {
        selected == 0 -> FlapFakes.fresh()
        else -> FlapFakes.sample(mood, goal = if (withGoal) 40 else null, previousMood = previous)
    }
    val state: FlapUiState = FlapMapper.map(fake)

    CompositionLocalProvider(LocalReducedMotion provides (reduced || systemReduced)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.screen)) {
            Spacer(Modifier.height(Space.x6))
            GalleryToggle("SIN ANIMACIONES", reduced) { reduced = it }

            // ------------------------------------------------------------------ el arte
            GalleryHeading("17.1", "ARTE · ABIERTO Y PARPADEO")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                FlapMood.entries.forEach { m ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FlapStill(m, size = Space.x12)
                        Spacer(Modifier.height(Space.x1))
                        GalleryFrame(m, FlapFrame.BLINK)
                        Spacer(Modifier.height(Space.x1))
                        Text(FlapCopy.moodLabel(m), style = type.dataS, color = colors.textMuted)
                    }
                }
            }

            // ------------------------------------------------------------------ la pestaña
            GalleryHeading("17.2", "PESTAÑA")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                (0..FlapMood.entries.size).forEach { i ->
                    val m = if (i == 0) FlapMood.EN_FORMA else FlapMood.entries[i - 1]
                    Column(
                        Modifier
                            .border(Space.hairline, if (i == selected) colors.signal else colors.line)
                            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                                selected = i
                                previous = null
                            }
                            .padding(Space.x1),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FlapStill(m, size = FlapDefaults.WeekSize)
                        Text(if (i == 0) "NUEVA" else "$i", style = type.dataS, color = colors.textMuted)
                    }
                }
            }
            Spacer(Modifier.height(Space.x3))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                GalleryToggle("OBJETIVO 40", withGoal) { withGoal = it }
                if (selected > 0 && mood != FlapMood.KO) GalleryButton("REVIVIR DESDE KO") { previous = FlapMood.KO }
                if (selected > 0 && mood != FlapMood.EN_FORMA) GalleryButton("EMPEORAR DESDE EN FORMA") { previous = FlapMood.EN_FORMA }
            }
            Spacer(Modifier.height(Space.x4))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(ScreenHeight)
                .border(Space.hairline, colors.line),
        ) {
            FlapScreen(
                state = state,
                onRetry = {},
                onMoodSeen = { seen -> if (seen == mood) previous = null },
                onOpenSettings = { withGoal = true },
            )
        }
    }
}

/** Un fotograma concreto del arte (`FlapStill` solo pinta los ojos abiertos). */
@Composable
private fun GalleryFrame(mood: FlapMood, frame: FlapFrame) {
    val colors = ReelTheme.colors
    Canvas(Modifier.size(Space.x12)) { drawFlap(FlapSprite.frame(mood, frame), colors) }
}
