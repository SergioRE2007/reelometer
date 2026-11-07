package com.reelometro.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.reelometro.app.data.Platform

/**
 * Paleta de Reelometer (ver docs/DESIGN.md §1). Un único acento: [signal].
 * Nunca escribas colores literales en pantallas: usa siempre `ReelTheme.colors`.
 */
@Immutable
data class ReelColors(
    val isDark: Boolean,
    /** Fondo de todas las pantallas (negro cálido / papel). */
    val bg: Color,
    /** Superficies puntuales: tooltip, hoja de diálogo, banda de aviso. */
    val bgRaised: Color,
    /** Fichas split-flap, pistas de barras e interruptores, celdas vacías. */
    val bgSunken: Color,
    /** Texto principal y cifras. */
    val text: Color,
    /** Texto secundario (AA ≥ 4,5:1 sobre [bg]). */
    val textMuted: Color,
    /** Solo decoración o texto ≥ 18 sp (≥ 3:1). */
    val textFaint: Color,
    /** Filetes, separadores, rejilla. */
    val line: Color,
    /** Bordes de botón secundario, ejes, pistas activas. */
    val lineStrong: Color,
    /** Acento de relleno (botón primario, banda a sangre, barra destacada). */
    val signal: Color,
    /** Texto/iconos sobre [signal]. */
    val onSignal: Color,
    /** Acento como texto o trazo fino sobre [bg] (AA). */
    val signalText: Color,
    /** Cifras grandes (≥ 24 sp) en acento sobre [bg]. */
    val signalDisplay: Color,
    val tiktok: Color,
    val instagram: Color,
    val youtube: Color,
) {
    /** Marca de color de una plataforma: solo para puntos, filetes y segmentos pequeños. */
    fun platform(platform: Platform): Color = when (platform) {
        Platform.TIKTOK -> tiktok
        Platform.INSTAGRAM -> instagram
        Platform.YOUTUBE -> youtube
    }

    /** Escala de 5 pasos del mapa de calor: 0 = nada … 4 = máximo. */
    val heatSteps: List<Color> = listOf(
        bgSunken,
        signal.copy(alpha = 0.25f).compositeOver(bg),
        signal.copy(alpha = 0.45f).compositeOver(bg),
        signal.copy(alpha = 0.70f).compositeOver(bg),
        signal,
    )

    fun heat(step: Int): Color = heatSteps[step.coerceIn(0, heatSteps.lastIndex)]
}

private val Signal = Color(0xFFFF5A1F)
private val InkOnSignal = Color(0xFF11100E)

val DarkReelColors = ReelColors(
    isDark = true,
    bg = Color(0xFF11100E),
    bgRaised = Color(0xFF1A1815),
    bgSunken = Color(0xFF1F1D1A),
    text = Color(0xFFEDE8DF),
    textMuted = Color(0xFFA39E94),
    textFaint = Color(0xFF6F6A62),
    line = Color(0xFF2E2B27),
    lineStrong = Color(0xFF4A463F),
    signal = Signal,
    onSignal = InkOnSignal,
    signalText = Signal,
    signalDisplay = Signal,
    tiktok = Color(0xFF3FE0E5),
    instagram = Color(0xFFE0559A),
    youtube = Color(0xFFFF3347),
)

val LightReelColors = ReelColors(
    isDark = false,
    bg = Color(0xFFF3EEE5),
    bgRaised = Color(0xFFEAE4D8),
    bgSunken = Color(0xFFE3DCCF),
    text = Color(0xFF151412),
    textMuted = Color(0xFF5F5A52),
    textFaint = Color(0xFF8C857A),
    line = Color(0xFFD6CFC2),
    lineStrong = Color(0xFFB5AD9F),
    signal = Signal,
    onSignal = InkOnSignal,
    signalText = Color(0xFFC23A06),
    signalDisplay = Color(0xFFD9480F),
    tiktok = Color(0xFF0B8A8F),
    instagram = Color(0xFFB8306F),
    youtube = Color(0xFFD41B30),
)
