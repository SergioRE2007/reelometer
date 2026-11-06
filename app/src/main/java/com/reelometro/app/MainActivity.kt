package com.reelometro.app

import android.app.UiModeManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelometro.app.data.settings.ThemeMode
import com.reelometro.app.ui.nav.AppShell
import com.reelometro.app.ui.nav.DeepLinks
import com.reelometro.app.ui.nav.OnboardingDest
import com.reelometro.app.ui.nav.TodayDest
import com.reelometro.app.ui.theme.DarkReelColors
import com.reelometro.app.ui.theme.LightReelColors
import com.reelometro.app.ui.theme.ReelTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Única actividad. Mantiene el splash hasta tener los ajustes (tema y si hay que enseñar el
 * onboarding) y abre la pantalla pedida en el extra [EXTRA_ROUTE] (notificaciones, widget, QA):
 * `today | stats | wrapped | passport | settings | calibration | onboarding | setup | day:<epochDay> | story:<KIND>:<offset>`.
 */
class MainActivity : ComponentActivity() {

    @Volatile
    private var ready = false
    private val deepLinks = Channel<Any>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !ready }

        val app = ReelometroApp.from(this)
        if (savedInstanceState == null) {
            DeepLinks.parse(intent?.getStringExtra(EXTRA_ROUTE))?.let { deepLinks.trySend(it) }
        }

        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@setContent
            SideEffect { ready = true }

            val dark = when (s.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            LaunchedEffect(s.themeMode) { applyAppNightMode(s.themeMode) }
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                // Con el tema forzado distinto al del sistema, el fondo del tema XML no coincide:
                // se ve en transiciones de ventana y al redimensionar.
                window.setBackgroundDrawable((if (dark) DarkReelColors else LightReelColors).bg.toArgb().toDrawable())
                onDispose { }
            }

            // El destino inicial solo se decide una vez (cambiarlo recrearía el grafo).
            val start = remember { if (s.onboardingDone) TodayDest else OnboardingDest }
            val scope = rememberCoroutineScope()
            ReelTheme(darkTheme = dark) {
                AppShell(
                    startDestination = start,
                    deepLinks = remember { deepLinks.receiveAsFlow() },
                    deepLinksEnabled = s.onboardingDone,
                    onOnboardingFinished = { scope.launch { app.settings.setOnboardingDone(true) } },
                    passportEnabled = s.passportEnabled,
                    tourPending = s.onboardingDone && !s.tourDone,
                    onTourFinished = { scope.launch { app.settings.setTourDone(true) } },
                )
            }
        }
    }

    /**
     * Android 12+: modo noche propio de la app, para que el splash y los recursos sigan el tema
     * elegido en Ajustes aunque no coincida con el del sistema. La actividad declara
     * `configChanges="uiMode"`: el cambio no la recrea, Compose solo se recompone.
     */
    private fun applyAppNightMode(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val target = when (mode) {
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
        }
        runCatching { getSystemService(UiModeManager::class.java)?.setApplicationNightMode(target) }
    }

    /** Apunta el día de uso para el aviso de apoyo (un mismo día cuenta una vez; lo decide el repositorio). */
    override fun onStart() {
        super.onStart()
        val app = ReelometroApp.from(this)
        val today = LocalDate.now().toEpochDay()
        app.appScope.launch { runCatching { app.settings.recordUsageDay(today) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLinks.parse(intent.getStringExtra(EXTRA_ROUTE))?.let { deepLinks.trySend(it) }
    }

    companion object {
        const val EXTRA_ROUTE = "route"

        /** Intent para abrir una ruta concreta de la app (notificaciones, widget). */
        fun intent(context: android.content.Context, route: String? = null): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (route != null) putExtra(EXTRA_ROUTE, route)
            }
    }
}
