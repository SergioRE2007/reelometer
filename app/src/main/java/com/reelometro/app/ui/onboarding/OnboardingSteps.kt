package com.reelometro.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.reelometro.app.copy.Buttons
import com.reelometro.app.copy.Labels
import com.reelometro.app.copy.OnboardingCopy
import com.reelometro.app.data.Platform
import com.reelometro.app.data.settings.AppSettings
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.PlatformMark
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.SectionHeader
import com.reelometro.app.ui.components.Stamp
import com.reelometro.app.ui.components.Stepper
import com.reelometro.app.ui.components.SwitchLine
import com.reelometro.app.ui.components.TicketDivider
import com.reelometro.app.ui.components.TicketDividerStyle
import com.reelometro.app.ui.components.flap.SplitFlapCounter
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import kotlinx.coroutines.delay

/** Recorrido del marcador de demostración: arranca despacio y acelera, como una tarde de vídeos. */
private val DemoSequence = longArrayOf(1, 2, 4, 7, 12, 19, 28, 40, 55, 73, 94, 118, 145, 172, 196, 212)
private const val DEMO_START_MS = 600L
private const val DEMO_TICK_MS = 160L

// ====================================================================== 1. Qué hace

@Composable
internal fun WhatStep(onNext: () -> Unit) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val reduced = ReelTheme.reducedMotion
    var value by remember { mutableLongStateOf(if (reduced) OnboardingCopy.WHAT_DEMO_COUNT.toLong() else 0L) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        delay(DEMO_START_MS)
        for (v in DemoSequence) {
            value = v
            delay(DEMO_TICK_MS)
        }
    }

    StepScaffold(
        footer = {
            ReelButton(Buttons.NEXT, onClick = onNext, trailingIcon = ReelIcons.ArrowRight, modifier = Modifier.fillMaxWidth())
        },
    ) {
        Spacer(Modifier.weight(0.5f))
        Label(Labels.VIDEOS)
        Spacer(Modifier.height(Space.x2))
        SplitFlapCounter(
            value = value,
            style = type.counter,
            haptic = false,
            contentDescription = OnboardingCopy.demoDescription(OnboardingCopy.WHAT_DEMO_COUNT),
        )
        Spacer(Modifier.height(Space.x3))
        Text(OnboardingCopy.demoTime(OnboardingCopy.WHAT_DEMO_MS), style = type.dataL, color = colors.textMuted)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(Space.x10))
        Text(OnboardingCopy.WHAT_TITLE, style = type.displayM, color = colors.text, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Space.x4))
        Text(OnboardingCopy.WHAT_BODY, style = type.body, color = colors.textMuted)
        Spacer(Modifier.height(Space.x6))
        PlatformLegend()
    }
}

/** «● TikTok  ● Instagram Reels  ● YouTube Shorts»: los colores de plataforma solo como marcas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlatformLegend() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.x5),
        verticalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Platform.entries.forEach { platform ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformMark(platform)
                Spacer(Modifier.width(Space.x2))
                Text(platform.displayName, style = ReelTheme.type.dataS, color = ReelTheme.colors.textMuted)
            }
        }
    }
}

// ====================================================================== 2. Privacidad

@Composable
internal fun PrivacyStep(onNext: () -> Unit) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    StepScaffold(
        footer = {
            ReelButton(Buttons.NEXT, onClick = onNext, trailingIcon = ReelIcons.ArrowRight, modifier = Modifier.fillMaxWidth())
        },
    ) {
        Spacer(Modifier.weight(1f))
        Stamp(
            text = OnboardingCopy.PRIVACY_STAMP,
            subtext = OnboardingCopy.PRIVACY_STAMP_SUBTEXT,
            hapticOnLand = true,
            modifier = Modifier.align(Alignment.End).padding(end = Space.x2),
        )
        Spacer(Modifier.height(Space.x6))
        Text(OnboardingCopy.PRIVACY_TITLE, style = type.displayM, color = colors.text, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Space.x8))
        OnboardingCopy.PRIVACY_POINTS.forEach { point ->
            TicketDivider(style = TicketDividerStyle.Dashed)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.x4)
                    .semantics(mergeDescendants = true) {},
            ) {
                Label(point.label, color = colors.text, modifier = Modifier.weight(0.38f).padding(top = Space.x1))
                Spacer(Modifier.width(Space.x3))
                Text(point.text, style = type.bodySmall, color = colors.textMuted, modifier = Modifier.weight(0.62f))
            }
        }
        TicketDivider(style = TicketDividerStyle.Dashed)
        Spacer(Modifier.height(Space.x6))
        Text(OnboardingCopy.PRIVACY_FOOTNOTE, style = type.quoteSmall, color = colors.textMuted)
        Spacer(Modifier.weight(1f))
    }
}

// ====================================================================== 4. Avisos y objetivo

@Composable
internal fun FinalStep(
    state: OnboardingState,
    onNotifyDaily: (Boolean) -> Unit,
    onNotifyMonthly: (Boolean) -> Unit,
    onRequestNotifications: () -> Unit,
    onChooseGoal: (GoalChoice) -> Unit,
    onChooseCustomGoal: () -> Unit,
    onCustomGoal: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    StepScaffold(
        footer = {
            ReelButton(OnboardingCopy.FINAL_BUTTON, onClick = onStart, trailingIcon = ReelIcons.ArrowRight, modifier = Modifier.fillMaxWidth())
        },
    ) {
        Text(OnboardingCopy.FINAL_TITLE, style = type.displayM, color = colors.text, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Space.x10))

        SectionHeader(index = 1, title = OnboardingCopy.NOTIFY_SECTION)
        Spacer(Modifier.height(Space.x2))
        SwitchLine(OnboardingCopy.NOTIFY_DAILY_LABEL, checked = state.notifyDaily, onCheckedChange = onNotifyDaily)
        Text(OnboardingCopy.notifyDaily(state.notifyHour, state.notifyMinute), style = type.bodySmall, color = colors.textMuted)
        Spacer(Modifier.height(Space.x2))
        SwitchLine(OnboardingCopy.NOTIFY_MONTHLY_LABEL, checked = state.notifyMonthly, onCheckedChange = onNotifyMonthly)
        Text(OnboardingCopy.NOTIFY_MONTHLY, style = type.bodySmall, color = colors.textMuted)
        val wantsNotifications = state.notifyDaily || state.notifyMonthly
        AnimatedVisibility(
            visible = state.needsNotificationPermission && !state.notificationsAllowed && (wantsNotifications || state.notificationsDenied),
            enter = revealIn(ReelTheme.reducedMotion),
            exit = revealOut(ReelTheme.reducedMotion),
        ) {
            InlineNotice(
                text = if (state.notificationsDenied) OnboardingCopy.NOTIFY_DENIED else OnboardingCopy.NOTIFY_RATIONALE,
                actionLabel = if (state.notificationsDenied) null else OnboardingCopy.NOTIFY_BUTTON,
                onAction = onRequestNotifications,
                actionIcon = ReelIcons.Bell,
                modifier = Modifier.padding(top = Space.x5),
            )
        }

        Spacer(Modifier.height(Space.x10))
        SectionHeader(index = 2, title = OnboardingCopy.GOAL_LABEL)
        Spacer(Modifier.height(Space.x3))
        Text(OnboardingCopy.GOAL_BODY, style = type.bodySmall, color = colors.textMuted)
        Spacer(Modifier.height(Space.x4))
        GoalOptions(state.goal, onChooseGoal, onChooseCustomGoal)
        val custom = state.goal as? GoalChoice.Custom
        AnimatedVisibility(
            visible = custom != null,
            enter = revealIn(ReelTheme.reducedMotion),
            exit = revealOut(ReelTheme.reducedMotion),
        ) {
            val value = custom?.value ?: OnboardingViewModel.DEFAULT_CUSTOM_GOAL
            Column(Modifier.fillMaxWidth().padding(top = Space.x4)) {
                Stepper(
                    value = value,
                    onValueChange = onCustomGoal,
                    range = AppSettings.MIN_GOAL..AppSettings.MAX_GOAL,
                    step = goalStep(value),
                    decreaseDescription = OnboardingCopy.GOAL_DECREASE,
                    increaseDescription = OnboardingCopy.GOAL_INCREASE,
                )
                Spacer(Modifier.height(Space.x2))
                Text(
                    OnboardingCopy.goalRange(AppSettings.MIN_GOAL, AppSettings.MAX_GOAL),
                    style = type.dataS,
                    color = colors.textMuted,
                )
            }
        }
        Spacer(Modifier.height(Space.x5))
        Text(OnboardingCopy.goalChosen(state.goal.goal), style = type.quoteSmall, color = colors.text)
    }
}

/** SIN OBJETIVO · 30 · 60 · 100 · 150 · 200 · OTRO, en fichas que se reparten en varias líneas si hace falta. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalOptions(goal: GoalChoice, onChoose: (GoalChoice) -> Unit, onCustom: () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
        verticalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        ChoiceChip(OnboardingCopy.GOAL_NONE, selected = goal == GoalChoice.None, onClick = { onChoose(GoalChoice.None) })
        AppSettings.SUGGESTED_GOALS.forEach { option ->
            ChoiceChip(
                text = OnboardingCopy.goalOption(option),
                selected = goal == GoalChoice.Preset(option),
                onClick = { onChoose(GoalChoice.Preset(option)) },
            )
        }
        ChoiceChip(OnboardingCopy.GOAL_OTHER, selected = goal is GoalChoice.Custom, onClick = onCustom)
    }
}
