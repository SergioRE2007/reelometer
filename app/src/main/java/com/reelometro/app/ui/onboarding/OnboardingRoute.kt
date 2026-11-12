package com.reelometro.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reelometro.app.copy.OnboardingCopy
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.ReelButtonStyle
import com.reelometro.app.ui.components.flap.FlapLoader
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space

/** Grosor de los segmentos del indicador de pasos (como la barra de progreso de las historias). */
private val ProgressThickness = 2.dp

/**
 * Onboarding (primera vez y «Ver la introducción» desde Ajustes): 4 pasos a pantalla completa.
 * Atrás vuelve al paso anterior; en el primero, sale como siempre.
 */
@Composable
fun OnboardingRoute(onFinished: () -> Unit) {
    val context = LocalContext.current
    val vm: OnboardingViewModel = viewModel { OnboardingViewModel.create(context) }
    val permissionVm: ServicePermissionViewModel = viewModel { ServicePermissionViewModel.create(context) }
    val state by vm.state.collectAsStateWithLifecycle()
    val permission by permissionVm.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionVm.refresh()
        vm.refreshNotifications()
    }
    val openAccessibility = {
        permissionVm.onOpenedSettings()
        SystemScreens.openAccessibility(context)
        Unit
    }
    // Sin enlaces a Calibración ni Ajustes: durante el onboarding los enlaces internos se ignoran.
    val help = rememberConnectionHelp(linksToScreens = false, onOpenAccessibility = openAccessibility)
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.onNotificationPermission(it)
    }

    val ready = state as? OnboardingUiState.Ready
    BackHandler(enabled = (ready?.onboarding?.step ?: 0) > 0) { vm.back() }

    OnboardingScreen(
        state = state,
        permission = permission,
        callbacks = OnboardingCallbacks(
            onNext = vm::next,
            onSkip = vm::skipToPermission,
            onAccept = {
                permissionVm.accept()
                SystemScreens.openAccessibility(context)
            },
            onDecline = {
                permissionVm.decline()
                vm.next()
            },
            onOpenAccessibility = openAccessibility,
            onOpenAppInfo = { SystemScreens.openAppInfo(context) },
            onToggleRestrictedHelp = permissionVm::toggleRestrictedHelp,
            onNotifyDaily = vm::setNotifyDaily,
            onNotifyMonthly = vm::setNotifyMonthly,
            onRequestNotifications = {
                // El botón solo aparece desde Android 13 (needsNotificationPermission); la guarda lo hace explícito.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onChooseGoal = vm::chooseGoal,
            onChooseCustomGoal = vm::chooseCustomGoal,
            onCustomGoal = vm::setCustomGoal,
            onStart = { vm.finish(onFinished) },
            help = help,
        ),
    )
}

/** Acciones del onboarding (agrupadas para que la firma de la pantalla no crezca sin fin). */
class OnboardingCallbacks(
    val onNext: () -> Unit,
    val onSkip: () -> Unit,
    val onAccept: () -> Unit,
    val onDecline: () -> Unit,
    val onOpenAccessibility: () -> Unit,
    val onOpenAppInfo: () -> Unit,
    val onToggleRestrictedHelp: () -> Unit,
    val onNotifyDaily: (Boolean) -> Unit,
    val onNotifyMonthly: (Boolean) -> Unit,
    val onRequestNotifications: () -> Unit,
    val onChooseGoal: (GoalChoice) -> Unit,
    val onChooseCustomGoal: () -> Unit,
    val onCustomGoal: (Int) -> Unit,
    val onStart: () -> Unit,
    /** Guía por marca y prueba de un minuto del paso 3 (null: sin ellas). */
    val help: ConnectionHelp? = null,
)

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    permission: ServicePermissionUiState,
    callbacks: OnboardingCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = ReelTheme.colors
    Box(
        modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        when (state) {
            OnboardingUiState.Loading -> Loading()
            is OnboardingUiState.Ready -> Steps(state.onboarding, permission, callbacks)
        }
    }
}

@Composable
private fun Loading() {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(Space.screen),
        contentAlignment = Alignment.CenterStart,
    ) {
        FlapLoader(style = ReelTheme.type.displayXL)
    }
}

@Composable
private fun Steps(state: OnboardingState, permission: ServicePermissionUiState, callbacks: OnboardingCallbacks) {
    val reduced = ReelTheme.reducedMotion
    Column(Modifier.fillMaxSize()) {
        StepHeader(
            step = state.step,
            onSkip = if (state.step < OnboardingViewModel.PERMISSION_STEP) callbacks.onSkip else null,
        )
        AnimatedContent(
            targetState = state.step,
            transitionSpec = { stepTransition(state.direction, reduced) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "onboardingStep",
        ) { step ->
            when (step) {
                0 -> WhatStep(onNext = callbacks.onNext)
                1 -> PrivacyStep(onNext = callbacks.onNext)
                2 -> when (permission) {
                    ServicePermissionUiState.Loading -> Loading()
                    is ServicePermissionUiState.Ready -> ServicePermissionStep(
                        state = permission.permission,
                        place = PermissionPlace.Onboarding,
                        onAccept = callbacks.onAccept,
                        onDecline = callbacks.onDecline,
                        onOpenAccessibility = callbacks.onOpenAccessibility,
                        onOpenAppInfo = callbacks.onOpenAppInfo,
                        onToggleRestrictedHelp = callbacks.onToggleRestrictedHelp,
                        onContinue = callbacks.onNext,
                        oem = callbacks.help?.oem,
                        onOpenOem = callbacks.help?.onOpenOem ?: {},
                        check = callbacks.help?.check,
                        checkActions = callbacks.help?.checkActions,
                    )
                }
                else -> FinalStep(
                    state = state,
                    onNotifyDaily = callbacks.onNotifyDaily,
                    onNotifyMonthly = callbacks.onNotifyMonthly,
                    onRequestNotifications = callbacks.onRequestNotifications,
                    onChooseGoal = callbacks.onChooseGoal,
                    onChooseCustomGoal = callbacks.onChooseCustomGoal,
                    onCustomGoal = callbacks.onCustomGoal,
                    onStart = callbacks.onStart,
                )
            }
        }
    }
}

/** Siguiente paso: entra desde la derecha; atrás, desde la izquierda. Con «reducir animaciones», fundido. */
private fun stepTransition(direction: Int, reduced: Boolean): ContentTransform =
    if (reduced) {
        fadeIn(Motion.standard()) togetherWith fadeOut(Motion.standard())
    } else {
        (slideInHorizontally(Motion.standard()) { it / 4 * direction } + fadeIn(Motion.standard())) togetherWith
            (slideOutHorizontally(Motion.standard()) { -it / 4 * direction } + fadeOut(Motion.standard()))
    }

/** «PASO 2 DE 4 ······ SALTAR» y debajo cuatro segmentos: hechos en tinta, el actual en acento. */
@Composable
private fun StepHeader(step: Int, onSkip: (() -> Unit)?) {
    val colors = ReelTheme.colors
    val reduced = ReelTheme.reducedMotion
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Space.screen),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Space.touch),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(
                OnboardingCopy.stepLabel(step + 1),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = OnboardingCopy.stepDescription(step + 1) },
            )
            if (onSkip != null) {
                ReelButton(OnboardingCopy.SKIP, onClick = onSkip, style = ReelButtonStyle.Ghost, trailingIcon = null)
            }
        }
        Spacer(Modifier.height(Space.x1))
        Row(
            Modifier
                .fillMaxWidth()
                .height(ProgressThickness)
                .clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(Space.x1),
        ) {
            repeat(OnboardingCopy.STEPS) { index ->
                val color by animateColorAsState(
                    targetValue = when {
                        index == step -> colors.signal
                        index < step -> colors.text
                        else -> colors.line
                    },
                    animationSpec = Motion.orSnap(reduced, Motion.standard()),
                    label = "stepSegment",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .drawBehind { drawRect(color) },
                )
            }
        }
    }
}
