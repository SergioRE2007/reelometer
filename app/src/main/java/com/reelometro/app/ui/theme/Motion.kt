package com.reelometro.app.ui.theme

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Movimiento (docs/DESIGN.md §6). Todo se anima con springs; nada de duraciones lineales.
 */
object Motion {
    /** Pulsaciones, interruptores. */
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.7f, stiffness = 900f)

    /** Indicadores, cambios de layout, transiciones. */
    fun <T> standard(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 380f)

    /** Cifras y gráficas que crecen. */
    fun <T> gentle(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 160f)

    /** Sellos y momentos de recompensa (con moderación). */
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = 500f)

    /** Escalonados (ms). */
    const val STAGGER_BAR_MS = 24L
    const val STAGGER_DIGIT_MS = 40L
    const val STAGGER_LIST_MS = 30L

    /** Devuelve [spec] o un salto instantáneo si el usuario ha pedido reducir animaciones. */
    fun <T> orSnap(reduced: Boolean, spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (reduced) snap() else spec
}

/** `true` si el sistema tiene las animaciones desactivadas (escala 0 o «Quitar animaciones»). */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Lee la preferencia del sistema y se actualiza en vivo si cambia. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
                reduced = scale == 0f
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
