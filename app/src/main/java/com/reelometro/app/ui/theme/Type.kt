package com.reelometro.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.reelometro.app.R

/*
 * Tipografía (docs/DESIGN.md §2). Tres familias OFL incluidas en res/font:
 *  - Bricolage Grotesque (variable opsz/wdth/wght): display y texto.
 *  - JetBrains Mono (variable wght): datos, etiquetas, botones.
 *  - Instrument Serif Italic: apartes editoriales.
 *
 * Cada combinación de ejes es una FontFamily propia (Compose aplica los ejes en API 26+).
 */

private fun bricolage(weight: Int, width: Float, opsz: Float, fontWeight: FontWeight) = Font(
    R.font.bricolage_grotesque,
    weight = fontWeight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(width),
        FontVariation.Setting("opsz", opsz),
    ),
)

private fun mono(weight: Int, fontWeight: FontWeight) = Font(
    R.font.jetbrains_mono,
    weight = fontWeight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Cifras del marcador: condensada y muy pesada. */
val CounterFamily = FontFamily(bricolage(800, 75f, 96f, FontWeight.ExtraBold))
val DisplayXLFamily = CounterFamily
val DisplayLFamily = FontFamily(bricolage(800, 80f, 72f, FontWeight.ExtraBold))
val DisplayMFamily = FontFamily(bricolage(700, 85f, 48f, FontWeight.Bold))
val HeadlineFamily = FontFamily(bricolage(700, 90f, 32f, FontWeight.Bold))
val TitleFamily = FontFamily(bricolage(600, 100f, 20f, FontWeight.SemiBold))

/** Texto corrido (opsz pequeño) con pesos normal / semibold / bold. */
val TextFamily = FontFamily(
    bricolage(400, 100f, 14f, FontWeight.Normal),
    bricolage(600, 100f, 14f, FontWeight.SemiBold),
    bricolage(700, 100f, 14f, FontWeight.Bold),
)

val MonoFamily = FontFamily(
    mono(400, FontWeight.Normal),
    mono(500, FontWeight.Medium),
    mono(600, FontWeight.SemiBold),
    mono(700, FontWeight.Bold),
)

val SerifFamily = FontFamily(
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
)

/** Interlineado ajustado sin relleno extra arriba/abajo (cifras y titulares). */
private val Tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun style(
    family: FontFamily,
    size: Int,
    lineHeight: Double,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0,
    italic: Boolean = false,
    tight: Boolean = false,
    tabular: Boolean = false,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = tracking.em,
    fontFeatureSettings = if (tabular) "tnum" else null,
    lineHeightStyle = if (tight) Tight else LineHeightStyle.Default,
    platformStyle = NoFontPadding,
)

/**
 * Escala tipográfica. Las etiquetas (`label`, `labelL`, `button`) se escriben en MAYÚSCULAS
 * (usa `Format.upper(...)` para respetar las reglas del español).
 */
@Immutable
data class ReelType(
    val counter: TextStyle = style(CounterFamily, 132, 1.0, FontWeight.ExtraBold, -0.02, tight = true, tabular = true),
    val displayXL: TextStyle = style(DisplayXLFamily, 96, 0.95, FontWeight.ExtraBold, -0.02, tight = true, tabular = true),
    val displayL: TextStyle = style(DisplayLFamily, 64, 1.0, FontWeight.ExtraBold, -0.01, tight = true, tabular = true),
    val displayM: TextStyle = style(DisplayMFamily, 44, 1.05, FontWeight.Bold, -0.01, tight = true),
    val headline: TextStyle = style(HeadlineFamily, 30, 1.1, FontWeight.Bold),
    val title: TextStyle = style(TitleFamily, 20, 1.25, FontWeight.SemiBold),
    val body: TextStyle = style(TextFamily, 16, 1.45),
    val bodySmall: TextStyle = style(TextFamily, 14, 1.4),
    val quote: TextStyle = style(SerifFamily, 28, 1.1, italic = true),
    val quoteSmall: TextStyle = style(SerifFamily, 20, 1.2, italic = true),
    val dataL: TextStyle = style(MonoFamily, 22, 1.25, FontWeight.Medium),
    val data: TextStyle = style(MonoFamily, 15, 1.35, FontWeight.Medium),
    val dataS: TextStyle = style(MonoFamily, 13, 1.35, FontWeight.Normal),
    val label: TextStyle = style(MonoFamily, 11, 1.3, FontWeight.SemiBold, 0.12),
    val labelL: TextStyle = style(MonoFamily, 13, 1.3, FontWeight.SemiBold, 0.10),
    val button: TextStyle = style(MonoFamily, 14, 1.15, FontWeight.Bold, 0.08),
)

val ReelTypography = ReelType()
