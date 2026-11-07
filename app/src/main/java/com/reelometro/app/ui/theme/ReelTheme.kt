package com.reelometro.app.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalReelColors = staticCompositionLocalOf { DarkReelColors }
private val LocalReelType = staticCompositionLocalOf { ReelTypography }

/**
 * Tema de Reelometer. Material3 queda debajo solo como base invisible (ripple, selección de texto…),
 * con un esquema derivado de nuestros tokens para que nunca asome el morado por defecto.
 */
@Composable
fun ReelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkReelColors else LightReelColors
    val type = ReelTypography
    val reducedMotion = rememberReducedMotion()

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.signal, onPrimary = colors.onSignal,
            secondary = colors.text, onSecondary = colors.bg,
            tertiary = colors.signalText, onTertiary = colors.bg,
            background = colors.bg, onBackground = colors.text,
            surface = colors.bg, onSurface = colors.text,
            surfaceVariant = colors.bgSunken, onSurfaceVariant = colors.textMuted,
            surfaceContainerLowest = colors.bg, surfaceContainerLow = colors.bgRaised,
            surfaceContainer = colors.bgRaised, surfaceContainerHigh = colors.bgRaised,
            surfaceContainerHighest = colors.bgSunken,
            outline = colors.lineStrong, outlineVariant = colors.line,
            error = colors.signalText, onError = colors.bg,
            inverseSurface = colors.text, inverseOnSurface = colors.bg, inversePrimary = colors.signal,
            scrim = colors.bg,
        )
    } else {
        lightColorScheme(
            primary = colors.signal, onPrimary = colors.onSignal,
            secondary = colors.text, onSecondary = colors.bg,
            tertiary = colors.signalText, onTertiary = colors.bg,
            background = colors.bg, onBackground = colors.text,
            surface = colors.bg, onSurface = colors.text,
            surfaceVariant = colors.bgSunken, onSurfaceVariant = colors.textMuted,
            surfaceContainerLowest = colors.bg, surfaceContainerLow = colors.bgRaised,
            surfaceContainer = colors.bgRaised, surfaceContainerHigh = colors.bgRaised,
            surfaceContainerHighest = colors.bgSunken,
            outline = colors.lineStrong, outlineVariant = colors.line,
            error = colors.signalText, onError = colors.bg,
            inverseSurface = colors.text, inverseOnSurface = colors.bg, inversePrimary = colors.signal,
            scrim = colors.text,
        )
    }

    val typography = Typography(
        displayLarge = type.displayL, displayMedium = type.displayM, displaySmall = type.headline,
        headlineLarge = type.headline, headlineMedium = type.headline, headlineSmall = type.title,
        titleLarge = type.title, titleMedium = type.title, titleSmall = type.labelL,
        bodyLarge = type.body, bodyMedium = type.body, bodySmall = type.bodySmall,
        labelLarge = type.button, labelMedium = type.labelL, labelSmall = type.label,
    )

    val selection = TextSelectionColors(
        handleColor = colors.signal,
        backgroundColor = colors.signal.copy(alpha = 0.35f),
    )

    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(
            LocalReelColors provides colors,
            LocalReelType provides type,
            LocalReducedMotion provides reducedMotion,
            LocalContentColor provides colors.text,
            LocalTextStyle provides type.body,
            LocalTextSelectionColors provides selection,
            // Sin ripple de Material: todo lo pulsable se hunde (ver Indication.kt).
            LocalIndication provides ReelIndication,
            content = content,
        )
    }
}

/** Acceso a los tokens: `ReelTheme.colors.signal`, `ReelTheme.type.counter`. */
object ReelTheme {
    val colors: ReelColors
        @Composable @ReadOnlyComposable get() = LocalReelColors.current

    val type: ReelType
        @Composable @ReadOnlyComposable get() = LocalReelType.current

    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}
