package com.reelometro.app.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.reelometro.app.data.Platform
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.components.charts.CalendarHeatmap
import com.reelometro.app.ui.components.charts.DailyBars
import com.reelometro.app.ui.components.charts.HeatDay
import com.reelometro.app.ui.components.charts.HeatmapMode
import com.reelometro.app.ui.components.charts.HourLine
import com.reelometro.app.ui.components.charts.PlatformSplitBar
import com.reelometro.app.ui.components.charts.RankBars
import com.reelometro.app.ui.components.charts.RankItem
import com.reelometro.app.ui.components.flap.EmptyState
import com.reelometro.app.ui.components.flap.ErrorState
import com.reelometro.app.ui.components.flap.FlapContent
import com.reelometro.app.ui.components.flap.FlapLoader
import com.reelometro.app.ui.components.flap.FlapPad
import com.reelometro.app.ui.components.flap.RollingNumber
import com.reelometro.app.ui.components.flap.SplitFlapCounter
import com.reelometro.app.ui.components.flap.SplitFlapDisplay
import com.reelometro.app.ui.components.flap.SplitFlapText
import com.reelometro.app.ui.theme.LocalReducedMotion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Shapes
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics
import java.time.LocalDate
import java.util.Random
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Secciones de la galería para los componentes animados (marcador split-flap, cifras rodantes,
 * gráficas). GalleryActivity las llama; mantén estas firmas.
 */

/** Marcador split-flap, RollingNumber y FlapLoader con controles para cambiar el valor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CounterGallerySection() {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    var reduced by remember { mutableStateOf(false) }
    val systemReduced = LocalReducedMotion.current

    CompositionLocalProvider(LocalReducedMotion provides (reduced || systemReduced)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.screen)) {
            Spacer(Modifier.height(Space.x6))
            GalleryToggle("SIN ANIMACIONES", reduced) { reduced = it }

            // ------------------------------------------------------------------ el marcador
            GalleryHeading("14.1", "MARCADOR")
            var number by remember { mutableLongStateOf(212L) }
            var mode by remember { mutableIntStateOf(0) } // 0 cifra · 1 en blanco · 2 OK
            val content = when (mode) {
                1 -> FlapContent.Blank
                2 -> FlapContent.Text("OK")
                else -> FlapContent.Number(number)
            }
            Text(if (mode == 1) "SIN SEÑAL" else "VÍDEOS", style = type.label, color = if (mode == 1) colors.signalText else colors.textMuted)
            Spacer(Modifier.height(Space.x2))
            Row(verticalAlignment = Alignment.Top) {
                SplitFlapDisplay(
                    content = content,
                    modifier = Modifier.weight(1f),
                    contentDescription = when (mode) {
                        1 -> "Sin señal"
                        2 -> "OK"
                        else -> "${Format.integer(number)} vídeos hoy"
                    },
                )
                Column(Modifier.padding(start = Space.x3, top = Space.x1).width(Space.x20 + Space.x4)) {
                    Text("OBJETIVO", style = type.label, color = colors.textMuted)
                    Text("150", style = type.data, color = colors.text)
                    Spacer(Modifier.height(Space.x2))
                    val over = number - 150
                    Text(
                        if (over > 0) "+${Format.integer(over)} PASADO" else "${Format.integer(-over)} LIBRES",
                        style = type.label,
                        color = if (over > 0) colors.signalText else colors.textMuted,
                    )
                }
            }
            Spacer(Modifier.height(Space.x4))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                GalleryButton("+1") { number += 1; mode = 0 }
                GalleryButton("+37") { number += 37; mode = 0 }
                GalleryButton("×10") { number = (number * 10).coerceAtMost(9_999_999L); mode = 0 }
                GalleryButton("EN BLANCO") { mode = 1 }
                GalleryButton("OK") { mode = 2 }
                GalleryButton("0") { number = 0; mode = 0 }
            }

            // ------------------------------------------------------------ autoajuste y variantes
            GalleryHeading("14.2", "AUTOAJUSTE")
            Text("12.408 EN TODO EL ANCHO", style = type.label, color = colors.textMuted)
            Spacer(Modifier.height(Space.x2))
            SplitFlapCounter(12_408L, contentDescription = "12.408 vídeos")
            Spacer(Modifier.height(Space.x4))
            Text("1.234.567 EN 200 DP", style = type.label, color = colors.textMuted)
            Spacer(Modifier.height(Space.x2))
            SplitFlapCounter(1_234_567L, modifier = Modifier.width(200.dp))
            Spacer(Modifier.height(Space.x4))
            Text("SIN CEROS · DISPLAY L", style = type.label, color = colors.textMuted)
            Spacer(Modifier.height(Space.x2))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x6)) {
                SplitFlapCounter(7L, style = type.displayL, pad = FlapPad.Blank)
                SplitFlapCounter(7L, style = type.displayL, minDigits = 1)
                SplitFlapText("HOY", style = type.displayL)
            }

            // ------------------------------------------------------------------ cifra rodante
            GalleryHeading("14.3", "CIFRA RODANTE")
            var minutes by remember { mutableLongStateOf(47L) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RollingNumber(
                    minutes,
                    style = type.dataL,
                    format = { "${Format.integer(it)} MIN" },
                    contentDescription = "$minutes minutos",
                )
                Text(" DE PANTALLA · 4 COMPARTIDOS", style = type.data, color = colors.textMuted)
            }
            Spacer(Modifier.height(Space.x3))
            RollingNumber(minutes * 26, style = type.displayL)
            Spacer(Modifier.height(Space.x3))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                GalleryButton("+1") { minutes += 1 }
                GalleryButton("+13") { minutes += 13 }
                GalleryButton("−5") { minutes = max(0L, minutes - 5) }
                GalleryButton("×10") { minutes = (minutes * 10).coerceAtMost(99_999L) }
            }

            // ------------------------------------------------------------------ estados
            GalleryHeading("14.4", "CARGANDO")
            FlapLoader(style = type.displayL)

            GalleryHeading("14.5", "VACÍO Y ERROR")
            EmptyState(
                title = "De momento, ganas tú.",
                body = "Hoy no has visto ningún vídeo. El marcador espera.",
            ) { GalleryButton("VER AYER") { } }
            Spacer(Modifier.height(Space.section))
            ErrorState(
                message = "No he podido leer tus datos.",
                label = "Error",
            ) { GalleryButton("REINTENTAR") { } }
            Spacer(Modifier.height(Space.section))
        }
    }
}

/** DailyBars, HourLine, CalendarHeatmap (semana/mes/año), RankBars y PlatformSplitBar con datos falsos. */
@Composable
fun ChartsGallerySection() {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val today = remember { LocalDate.now() }

    Column(Modifier.fillMaxWidth().padding(horizontal = Space.screen)) {
        // ----------------------------------------------------------------------- cada día
        GalleryHeading("15.1", "CADA DÍA")
        var period by remember { mutableIntStateOf(7) }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            GalleryChip("SEMANA", period == 7) { period = 7 }
            GalleryChip("MES", period == 31) { period = 31 }
            GalleryChip("AÑO", period == 365) { period = 365 }
        }
        Spacer(Modifier.height(Space.x10))
        val series = remember(period) { fakeSeries(period, today) }
        val labels = remember(period) {
            series.dates.mapIndexed { i, d ->
                when (period) {
                    7 -> Format.weekdayInitial(d.dayOfWeek)
                    31 -> d.dayOfMonth.toString()
                    else -> if (d.dayOfMonth == 1 || i == 0) Format.upper(Format.monthShort(d.monthValue)) else ""
                }
            }
        }
        DailyBars(
            values = series.values,
            labels = labels,
            tooltip = { i -> "${Format.integer(series.values[i].roundToInt())} vídeos\n${Format.weekdayShort(series.dates[i].dayOfWeek)} ${Format.dayShort(series.dates[i])}" },
            highlightIndex = if (period == 7) series.values.lastIndex else null,
            average = series.values.average().toFloat(),
        )

        // ---------------------------------------------------------------------- hora a hora
        GalleryHeading("15.2", "TU DÍA, HORA A HORA")
        Spacer(Modifier.height(Space.x8))
        val hours = remember { fakeHours() }
        HourLine(
            values = hours,
            currentHour = 21,
            tooltip = { h -> "${Format.integer(hours[h].roundToInt())} vídeos\n${Format.hourRange(h)}" },
        )
        Spacer(Modifier.height(Space.x3))
        Text("HORA PUNTA 21:00 · 41 VÍDEOS", style = type.label, color = colors.textMuted)
        Spacer(Modifier.height(Space.x8))
        HourLine(
            values = hours.map { it * 0.8f },
            tooltip = { h -> "${Format.integer((hours[h] * 0.8f).roundToInt())} vídeos\n${Format.hourRange(h)}" },
        )

        // ----------------------------------------------------------------------- calendario
        GalleryHeading("15.3", "CALENDARIO")
        val heat = remember { fakeHeat(today) }
        val heatTooltip: (HeatDay) -> String = { d -> "${Format.integer(d.value.roundToInt())} vídeos\n${Format.weekdayShort(d.date.dayOfWeek)} ${Format.dayShort(d.date)}" }
        Text("SEMANA", style = type.label, color = colors.textMuted)
        Spacer(Modifier.height(Space.x6))
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        CalendarHeatmap(heat.filter { !it.date.isBefore(monday) && it.date.isBefore(monday.plusDays(7)) }, HeatmapMode.WEEK, heatTooltip, today = today)
        Spacer(Modifier.height(Space.x8))
        Text(Format.upper(Format.monthYear(today)), style = type.label, color = colors.textMuted)
        Spacer(Modifier.height(Space.x6))
        val firstOfMonth = today.withDayOfMonth(1)
        CalendarHeatmap(heat.filter { !it.date.isBefore(firstOfMonth) && it.date.monthValue == today.monthValue }, HeatmapMode.MONTH, heatTooltip, today = today)
        Spacer(Modifier.height(Space.x8))
        Text("${today.year}", style = type.label, color = colors.textMuted)
        Spacer(Modifier.height(Space.x6))
        CalendarHeatmap(heat.filter { it.date.year == today.year }, HeatmapMode.YEAR, heatTooltip, today = today)

        // ------------------------------------------------------------------------ rankings
        GalleryHeading("15.4", "LO QUE MÁS VES")
        RankBars(
            listOf(
                RankItem("Humor", 120f, "120 · 26 MIN"),
                RankItem("Deporte", 64f, "64 · 14 MIN"),
                RankItem("Cocina", 41f, "41 · 9 MIN"),
                RankItem("Música", 33f, "33 · 7 MIN"),
                RankItem("Animales", 21f, "21 · 5 MIN"),
                RankItem("Noticias y actualidad política internacional", 9f, "9 · 2 MIN"),
            ),
        )
        Spacer(Modifier.height(Space.x8))
        RankBars(
            listOf(
                RankItem("@cocinaconpaco", 31f, "31", colors.platform(Platform.TIKTOK)),
                RankItem("@futbolclips", 22f, "22", colors.platform(Platform.YOUTUBE)),
                RankItem("@gatosdemadrid", 14f, "14", colors.platform(Platform.INSTAGRAM)),
            ),
        )

        // ----------------------------------------------------------------------- plataformas
        GalleryHeading("15.5", "PLATAFORMAS")
        val platforms = listOf(Platform.TIKTOK to 120f, Platform.INSTAGRAM to 64f, Platform.YOUTUBE to 28f)
        PlatformSplitBar(
            segments = platforms.map { (p, v) -> colors.platform(p) to v },
            contentDescription = "TikTok 120, Reels 64, Shorts 28",
        )
        Spacer(Modifier.height(Space.x4))
        platforms.forEach { (p, v) ->
            Row(Modifier.padding(vertical = Space.x1), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(Space.x2).background(colors.platform(p), Shapes.dot))
                Spacer(Modifier.width(Space.x2))
                Text(p.shortName, style = type.data, color = colors.text, modifier = Modifier.weight(1f))
                Text(Format.integer(v.roundToInt()), style = type.data, color = colors.textMuted)
            }
        }
        Spacer(Modifier.height(Space.x4))
        Text("SIN DATOS", style = type.label, color = colors.textMuted)
        Spacer(Modifier.height(Space.x2))
        PlatformSplitBar(segments = emptyList())
        Spacer(Modifier.height(Space.section * 2))
    }
}

// ---------------------------------------------------------------------------- piezas de galería

/** Subapartado dentro de la sección de la galería (14.1, 15.2…): la galería ya pone el `SectionHeader`. */
@Composable
internal fun GalleryHeading(index: String, title: String) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    Spacer(Modifier.height(Space.section))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(index, style = type.label, color = colors.textMuted)
        Spacer(Modifier.width(Space.x3))
        Text(title, style = type.label, color = colors.text)
        Spacer(Modifier.width(Space.x3))
        Box(Modifier.weight(1f).height(Space.hairline).background(colors.line))
    }
    Spacer(Modifier.height(Space.x4))
}

/** Botón provisional de la galería (los definitivos son `ReelButton`). */
@Composable
internal fun GalleryButton(text: String, onClick: () -> Unit) {
    val colors = ReelTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .defaultMinSize(minWidth = Space.touch, minHeight = Space.touch)
            .background(if (pressed) colors.bgSunken else colors.bg, Shapes.button)
            .border(Space.hairline, colors.lineStrong, Shapes.button)
            .clickable(interaction, indication = null, role = Role.Button) {
                haptics.press()
                onClick()
            }
            .padding(horizontal = Space.x4),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = ReelTheme.type.button, color = colors.text)
    }
}

@Composable
private fun GalleryChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = ReelTheme.colors
    Column(
        Modifier
            .defaultMinSize(minHeight = Space.touch)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClick = onClick)
            .padding(end = Space.x3),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, style = ReelTheme.type.labelL, color = if (selected) colors.text else colors.textMuted)
        Spacer(Modifier.height(Space.x1))
        Box(Modifier.width(Space.x8).height(2.dp).background(if (selected) colors.signal else Color.Transparent))
    }
}

@Composable
internal fun GalleryToggle(text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val colors = ReelTheme.colors
    Row(
        Modifier
            .defaultMinSize(minHeight = Space.touch)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Switch) { onChange(!on) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = ReelTheme.type.label, color = colors.textMuted)
        Spacer(Modifier.width(Space.x3))
        Text(if (on) "SÍ" else "NO", style = ReelTheme.type.button, color = if (on) colors.signalText else colors.text)
    }
}

// --------------------------------------------------------------------------------- datos falsos

private class FakeSeries(val dates: List<LocalDate>, val values: List<Float>)

/** [days] días hasta hoy: más los fines de semana, una racha floja y un día sin móvil. */
private fun fakeSeries(days: Int, today: LocalDate): FakeSeries {
    val rnd = Random(days * 31L + 7)
    val dates = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
    val values = dates.mapIndexed { i, d ->
        val weekend = if (d.dayOfWeek.value >= 5) 55f else 0f
        val season = if (days > 31) 40f * sin(2 * PI * (d.dayOfYear - 80) / 365.0).toFloat() else 0f
        val noise = rnd.nextGaussian().toFloat() * 38f
        val v = 150f + weekend + season + noise
        when {
            days >= 31 && i == days / 3 -> 0f
            else -> max(4f, v)
        }.roundToInt().toFloat()
    }
    return FakeSeries(dates, values)
}

/** Un día típico: poco de madrugada, algo al despertar, pico de sobremesa y gran pico nocturno. */
private fun fakeHours(): List<Float> = listOf(
    6f, 2f, 0f, 0f, 0f, 1f, 3f, 9f, 14f, 8f, 5f, 6f,
    9f, 17f, 22f, 13f, 9f, 11f, 15f, 19f, 27f, 41f, 36f, 18f,
)

private fun fakeHeat(today: LocalDate): List<HeatDay> {
    val rnd = Random(2026)
    val start = LocalDate.of(today.year, 1, 1)
    val end = today.withDayOfMonth(1).plusMonths(1).minusDays(1)
    return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { d ->
        if (d.isAfter(today)) {
            HeatDay(d, 0f)
        } else {
            val weekend = if (d.dayOfWeek.value >= 5) 60f else 0f
            val off = rnd.nextInt(17) == 0
            HeatDay(d, if (off) 0f else max(0f, 120f + weekend + rnd.nextGaussian().toFloat() * 55f).roundToInt().toFloat())
        }
    }.toList()
}
