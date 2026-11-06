package com.reelometro.app.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.reelometro.app.ui.calibration.CalibrationRoute
import com.reelometro.app.ui.day.DayRoute
import com.reelometro.app.ui.onboarding.OnboardingRoute
import com.reelometro.app.ui.onboarding.ServiceSetupRoute
import com.reelometro.app.ui.passport.CelebrationHost
import com.reelometro.app.ui.passport.PassportRoute
import com.reelometro.app.ui.settings.SettingsRoute
import com.reelometro.app.ui.stats.StatsRoute
import com.reelometro.app.ui.support.SupportPromptHost
import com.reelometro.app.ui.theme.LocalReducedMotion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.today.TodayRoute
import com.reelometro.app.ui.tour.LocalTourTargets
import com.reelometro.app.ui.tour.TourHost
import com.reelometro.app.ui.tour.TourTargets
import com.reelometro.app.ui.wrapped.StoryRoute
import com.reelometro.app.ui.wrapped.WrappedHubRoute
import kotlinx.coroutines.flow.Flow

/** Alto de la barra de pestañas sin el inset del sistema. */
val TabBarHeight = 64.dp

/**
 * Armazón de la app: NavHost con las pestañas (Hoy · Estadísticas · Resumen · Pasaporte) y las
 * pantallas apiladas (detalle del día, Wrapped, Ajustes, Calibración, Onboarding).
 *
 * - Transiciones con springs; el gesto de atrás predictivo anima las de "pop".
 * - `SharedTransitionLayout` envuelve el NavHost: las pantallas usan [reelSharedElement].
 * - Encima de todo: el recorrido guiado ([TourHost]), las celebraciones del Pasaporte ([CelebrationHost]) y
 *   el aviso de apoyo ([SupportPromptHost]).
 *
 * @param passportEnabled modo pasaporte: con `false` no hay cuarta pestaña ni celebraciones.
 * @param tourPending el recorrido guiado está pendiente (onboarding hecho y recorrido sin ver).
 * @param onTourFinished al terminar o saltar el recorrido (se guarda como visto).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppShell(
    startDestination: Any,
    deepLinks: Flow<Any>,
    deepLinksEnabled: Boolean,
    onOnboardingFinished: () -> Unit,
    modifier: Modifier = Modifier,
    passportEnabled: Boolean = true,
    tourPending: Boolean = false,
    onTourFinished: (skipped: Boolean) -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    val entry by navController.currentBackStackEntryAsState()
    // Con el modo pasaporte apagado, su destino no es una pestaña (no se pinta y se vuelve a Hoy).
    val onPassport = entry?.destination?.hasRoute(PassportDest::class) == true
    val tabIndex = if (onPassport && !passportEnabled) -1 else entry?.destination?.tabIndex() ?: -1
    val reduced = LocalReducedMotion.current
    val tourTargets = remember { TourTargets() }

    // Mientras no se haya completado el onboarding, los enlaces se ignoran (salvo el propio onboarding).
    // Con el modo pasaporte apagado, el enlace al Pasaporte tampoco existe.
    val linksEnabled by rememberUpdatedState(deepLinksEnabled)
    val passport by rememberUpdatedState(passportEnabled)
    LaunchedEffect(navController) {
        deepLinks.collect {
            when {
                it == PassportDest && !passport -> Unit
                linksEnabled || it == OnboardingDest -> navController.openDeepLink(it)
            }
        }
    }
    // Si se apaga el modo pasaporte con su pestaña abierta, se vuelve a Hoy.
    LaunchedEffect(passportEnabled, onPassport) {
        if (!passportEnabled && onPassport) navController.navigateTab(TodayDest)
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Con el recorrido a la vista, TalkBack solo lee su panel (Compose no tiene modal fuera de Dialog).
    val underTour = if (tourPending && tabIndex >= 0) Modifier.clearAndSetSemantics { } else Modifier

    Box(modifier.fillMaxSize().background(ReelTheme.colors.bg)) {
        SharedTransitionLayout(underTour) {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
                LocalBottomBarHeight provides if (tabIndex >= 0) TabBarHeight + navInset else 0.dp,
                LocalTourTargets provides tourTargets,
            ) {
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    enterTransition = { enter(reduced) },
                    exitTransition = { exit(reduced) },
                    popEnterTransition = { popEnter(reduced) },
                    popExitTransition = { popExit(reduced) },
                ) {
                    composable<TodayDest> {
                        Scoped {
                            TodayRoute(
                                onOpenDay = { navController.navigate(DayDest(it)) },
                                onOpenStats = { navController.navigateTab(StatsDest) },
                                onOpenSettings = { navController.navigate(SettingsDest) },
                                onConnectService = { navController.navigate(ServiceSetupDest) { launchSingleTop = true } },
                            )
                        }
                    }
                    composable<StatsDest> {
                        Scoped {
                            StatsRoute(
                                onOpenDay = { navController.navigate(DayDest(it)) },
                                onOpenSettings = { navController.navigate(SettingsDest) },
                            )
                        }
                    }
                    composable<WrappedDest> {
                        Scoped {
                            WrappedHubRoute(
                                onOpenStory = { kind, offset -> navController.navigate(StoryDest(kind, offset)) },
                                onOpenSettings = { navController.navigate(SettingsDest) },
                            )
                        }
                    }
                    composable<PassportDest> {
                        if (passport) Scoped { PassportRoute(onOpenSettings = { navController.navigate(SettingsDest) }) }
                    }
                    composable<DayDest> { backStack ->
                        val day = backStack.toRoute<DayDest>()
                        Scoped { DayRoute(epochDay = day.epochDay, onBack = { navController.popBackStack() }) }
                    }
                    composable<StoryDest>(
                        enterTransition = { storyEnter(reduced) },
                        popExitTransition = { storyExit(reduced) },
                    ) { backStack ->
                        val story = backStack.toRoute<StoryDest>()
                        Scoped {
                            StoryRoute(
                                kind = story.kind,
                                offset = story.offset,
                                onClose = { navController.popBackStack() },
                            )
                        }
                    }
                    composable<SettingsDest> {
                        Scoped {
                            SettingsRoute(
                                onBack = { navController.popBackStack() },
                                onOpenCalibration = { navController.navigate(CalibrationDest) },
                                onReplayOnboarding = { navController.navigate(OnboardingDest) },
                                onOpenServiceSetup = { navController.navigate(ServiceSetupDest) { launchSingleTop = true } },
                                onReplayTour = {
                                    // El recorrido empieza en Hoy: se quita lo que hay encima SIN guardar estado
                                    // (con navigateTab, Ajustes quedaría guardado en la pila de una pestaña y
                                    // volvería a salir al restaurarla, tapando el recorrido).
                                    if (!navController.popBackStack<TodayDest>(inclusive = false)) {
                                        navController.navigateTab(TodayDest)
                                    }
                                },
                            )
                        }
                    }
                    composable<ServiceSetupDest> {
                        Scoped { ServiceSetupRoute(onBack = { navController.popBackStack() }) }
                    }
                    composable<CalibrationDest> {
                        Scoped { CalibrationRoute(onBack = { navController.popBackStack() }) }
                    }
                    composable<OnboardingDest> {
                        Scoped {
                            OnboardingRoute(
                                onFinished = {
                                    onOnboardingFinished()
                                    if (navController.previousBackStackEntry != null) {
                                        // Vuelto a ver desde Ajustes (o por enlace): se vuelve a donde estaba.
                                        navController.popBackStack()
                                    } else {
                                        // Primera vez: Hoy pasa a ser la raíz.
                                        navController.navigate(TodayDest) {
                                            popUpTo(navController.graph.id) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = tabIndex >= 0,
            enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 500f)) { it } + fadeIn(),
            exit = slideOutVertically(spring(dampingRatio = 0.9f, stiffness = 500f)) { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).then(underTour),
        ) {
            AppTabBar(
                selectedIndex = tabIndex.coerceAtLeast(0),
                onSelect = { index -> navController.navigateTab(tabDests(passportEnabled)[index]) },
                passportEnabled = passportEnabled,
            )
        }

        // Celebraciones del Pasaporte pendientes: solo en las pestañas y nunca durante el recorrido.
        CelebrationHost(
            enabled = passportEnabled && tabIndex >= 0 && !tourPending,
            onOpenPassport = { navController.navigateTab(PassportDest) },
        )

        // Aviso de apoyo (GitHub del proyecto): mismas condiciones, y nunca en una sesión con celebraciones.
        SupportPromptHost(
            enabled = tabIndex >= 0 && !tourPending,
            passportEnabled = passportEnabled,
            navReady = entry != null,
        )

        CompositionLocalProvider(LocalTourTargets provides tourTargets) {
            TourHost(
                pending = tourPending,
                currentTab = tabIndex,
                passportEnabled = passportEnabled,
                targets = tourTargets,
                onShowTab = { dest -> navController.navigateTab(dest) },
                onFinished = onTourFinished,
            )
        }
    }
}

/** Índice de la pestaña Pasaporte en la barra. */
private const val PASSPORT_TAB = 3

/** Expone el ámbito de animación del destino a [reelSharedElement]/[reelSharedBounds]. */
@Composable
private fun AnimatedVisibilityScope.Scoped(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedScope provides this, content = content)
}

private fun NavDestination.tabIndex(): Int = when {
    hasRoute(TodayDest::class) -> 0
    hasRoute(StatsDest::class) -> 1
    hasRoute(WrappedDest::class) -> 2
    hasRoute(PassportDest::class) -> PASSPORT_TAB
    else -> -1
}

/** Cambio de pestaña conservando el estado de cada una. */
fun NavHostController.navigateTab(dest: Any) {
    navigate(dest) {
        // Hoy es siempre la raíz de la pila (también tras el onboarding).
        popUpTo<TodayDest> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

fun NavHostController.openDeepLink(dest: Any) {
    when (dest) {
        TodayDest, StatsDest, WrappedDest, PassportDest -> navigateTab(dest)
        else -> navigate(dest) { launchSingleTop = true }
    }
}

// ------------------------------------------------------------------ transiciones

private val slideSpring = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 420f)
private val fadeSpring = spring<Float>(dampingRatio = 1f, stiffness = 600f)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.tabIndex() >= 0 && targetState.destination.tabIndex() >= 0

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabDirection(): Int =
    if (targetState.destination.tabIndex() >= initialState.destination.tabIndex()) 1 else -1

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(reduced: Boolean): EnterTransition = when {
    reduced -> fadeIn(fadeSpring)
    isTabSwitch() -> fadeIn(fadeSpring) + slideInHorizontally(slideSpring) { (it * 0.04f).toInt() * tabDirection() }
    else -> slideInHorizontally(slideSpring) { it } + fadeIn(fadeSpring)
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(reduced: Boolean): ExitTransition = when {
    reduced -> fadeOut(fadeSpring)
    isTabSwitch() -> fadeOut(fadeSpring) + slideOutHorizontally(slideSpring) { -(it * 0.04f).toInt() * tabDirection() }
    else -> slideOutHorizontally(slideSpring) { -it / 4 } + fadeOut(fadeSpring)
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(reduced: Boolean): EnterTransition = when {
    reduced -> fadeIn(fadeSpring)
    isTabSwitch() -> fadeIn(fadeSpring)
    else -> slideInHorizontally(slideSpring) { -it / 4 } + fadeIn(fadeSpring)
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(reduced: Boolean): ExitTransition = when {
    reduced -> fadeOut(fadeSpring)
    isTabSwitch() -> fadeOut(fadeSpring)
    else -> slideOutHorizontally(slideSpring) { it } + fadeOut(fadeSpring)
}

/** El Wrapped sube desde abajo, como una historia. */
private fun storyEnter(reduced: Boolean): EnterTransition =
    if (reduced) fadeIn(fadeSpring) else slideInVertically(slideSpring) { it } + fadeIn(fadeSpring)

private fun storyExit(reduced: Boolean): ExitTransition =
    if (reduced) fadeOut(fadeSpring) else slideOutVertically(slideSpring) { it } + fadeOut(fadeSpring)
