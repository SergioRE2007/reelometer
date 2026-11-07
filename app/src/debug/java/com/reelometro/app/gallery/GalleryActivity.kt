package com.reelometro.app.gallery

import android.graphics.drawable.Animatable
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.reelometro.app.R
import com.reelometro.app.data.Platform
import com.reelometro.app.detection.GlassBubbleArt
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.components.Band
import com.reelometro.app.ui.components.Barcode
import com.reelometro.app.ui.components.JaggedSide
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.MarkStyle
import com.reelometro.app.ui.components.Notice
import com.reelometro.app.ui.components.PeriodNavigator
import com.reelometro.app.ui.components.PlatformMark
import com.reelometro.app.ui.components.ReceiptLine
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.ReelButtonStyle
import com.reelometro.app.ui.components.ReelDialog
import com.reelometro.app.ui.components.ReelIconButton
import com.reelometro.app.ui.components.ReelSwitch
import com.reelometro.app.ui.components.ReelTab
import com.reelometro.app.ui.components.ReelTabBar
import com.reelometro.app.ui.components.ReelTopBar
import com.reelometro.app.ui.components.SectionHeader
import com.reelometro.app.ui.components.SegmentedTabs
import com.reelometro.app.ui.components.Stamp
import com.reelometro.app.ui.components.Stepper
import com.reelometro.app.ui.components.SwitchLine
import com.reelometro.app.ui.components.TicketDivider
import com.reelometro.app.ui.components.TicketDividerStyle
import com.reelometro.app.ui.components.jaggedEdge
import com.reelometro.app.ui.icons.ReelIcon
import com.reelometro.app.ui.icons.ReelIconStroke
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.theme.ReelColors
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Shapes
import com.reelometro.app.ui.theme.Space
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/**
 * Galería del sistema de diseño (solo debug): tokens, iconos y todos los componentes con datos
 * falsos, con interruptor claro/oscuro propio.
 *
 * QA por adb:
 *   adb shell am start -S -n com.reelometro.app/.gallery.GalleryActivity --ei section 6 --ez dark false
 * `section` = índice de la sección (ver [sections]); `dark` fuerza el tema al abrir.
 */
class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startSection = intent.getIntExtra(EXTRA_SECTION, 0)
        val forcedDark = if (intent.hasExtra(EXTRA_DARK)) intent.getBooleanExtra(EXTRA_DARK, true) else null
        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            var dark by rememberSaveable { mutableStateOf(forcedDark ?: systemDark) }
            LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            ReelTheme(darkTheme = dark) {
                GalleryScreen(dark = dark, onToggleDark = { dark = !dark }, startSection = startSection)
            }
        }
    }

    companion object {
        const val EXTRA_SECTION = "section"
        const val EXTRA_DARK = "dark"
    }
}

// ====================================================================== estructura

private class GallerySection(val key: String, val content: @Composable () -> Unit)

/** Primer índice de cada grupo de la barra inferior (Tokens · Piezas · Movimiento). */
private val GroupStarts = intArrayOf(0, 6, 13)

private val sections: List<GallerySection> = listOf(
    GallerySection("color") { ColorSection(1) },
    GallerySection("type") { TypeSection(2) },
    GallerySection("axes") { AxesSection(3) },
    GallerySection("space") { SpaceSection(4) },
    GallerySection("icons") { IconsSection(5) },
    GallerySection("iconsLarge") { IconsLargeSection(6) },
    GallerySection("buttons") { ButtonsSection(7) },
    GallerySection("controls") { ControlsSection(8) },
    GallerySection("navigation") { NavigationSection(9) },
    GallerySection("ticket") { TicketSection(10) },
    GallerySection("blocks") { BlocksSection(11) },
    GallerySection("dialogs") { DialogsSection(12) },
    GallerySection("appIcon") { AppIconSection(13) },
    GallerySection("counter") {
        Section(14, "Marcador", trailing = "GalleryMotion") {}
        CounterGallerySection()
    },
    GallerySection("charts") {
        Section(15, "Gráficas", trailing = "GalleryMotion") {}
        ChartsGallerySection()
    },
    GallerySection("bubbles") { BubblesSection(16) },
)

@Composable
private fun GalleryScreen(dark: Boolean, onToggleDark: () -> Unit, startSection: Int) {
    val colors = ReelTheme.colors
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startSection.coerceIn(0, sections.lastIndex))
    val scope = rememberCoroutineScope()
    val group by remember {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            GroupStarts.indexOfLast { first >= it }.coerceAtLeast(0)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        ReelTopBar(
            kicker = "Galería · debug",
            title = "Sistema",
            divider = true,
            actions = {
                ReelIconButton(
                    icon = if (dark) ReelIcons.Sun else ReelIcons.Moon,
                    contentDescription = if (dark) "Pasar a tema claro" else "Pasar a tema oscuro",
                    onClick = onToggleDark,
                )
            },
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Space.section),
        ) {
            items(sections, key = { it.key }) { it.content() }
        }
        ReelTabBar(
            items = listOf(
                ReelTab("Hoy", ReelIcons.Today),
                ReelTab("Estadísticas", ReelIcons.Stats),
                ReelTab("Resumen", ReelIcons.Wrapped),
            ),
            selectedIndex = group,
            onSelect = { scope.launch { listState.animateScrollToItem(GroupStarts[it]) } },
        )
    }
}

/** Sección con su cabecera numerada y el margen lateral de pantalla. */
@Composable
private fun Section(
    index: Int,
    title: String,
    trailing: String? = null,
    padded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = Space.section)) {
        SectionHeader(index, title, Modifier.padding(horizontal = Space.screen), trailing = trailing)
        Spacer(Modifier.height(Space.x4))
        Column(if (padded) Modifier.padding(horizontal = Space.screen) else Modifier, content = content)
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = ReelTheme.type.dataS, color = ReelTheme.colors.textMuted)
}

// ====================================================================== 01 color

private fun hex(color: Color): String = String.format(Locale.ROOT, "#%06X", color.toArgb() and 0xFFFFFF)

@Composable
private fun ColorSection(index: Int) {
    val c = ReelTheme.colors
    val tokens = listOf(
        "bg" to c.bg, "bgRaised" to c.bgRaised, "bgSunken" to c.bgSunken,
        "text" to c.text, "textMuted" to c.textMuted, "textFaint" to c.textFaint,
        "line" to c.line, "lineStrong" to c.lineStrong, "signal" to c.signal,
        "onSignal" to c.onSignal, "signalText" to c.signalText, "signalDisplay" to c.signalDisplay,
        "tiktok" to c.tiktok, "instagram" to c.instagram, "youtube" to c.youtube,
    )
    Section(index, "Color", trailing = if (c.isDark) "Oscuro" else "Claro") {
        tokens.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                row.forEach { (name, color) -> Swatch(name, color, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(Space.x4))
        }
        Label("Mapa de calor · 5 pasos")
        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            c.heatSteps.forEach { Box(Modifier.size(Space.x8).background(it)) }
        }
    }
}

@Composable
private fun Swatch(name: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(Space.x12)
                .background(color)
                .border(Space.hairline, ReelTheme.colors.line),
        )
        Spacer(Modifier.height(Space.x1))
        Text(name, style = ReelTheme.type.dataS, color = ReelTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(hex(color), style = ReelTheme.type.dataS, color = ReelTheme.colors.textMuted)
    }
}

// ====================================================================== 02 tipografía

@Composable
private fun TypeSection(index: Int) {
    val t = ReelTheme.type
    val samples: List<Triple<String, TextStyle, String>> = listOf(
        Triple("counter · 132", t.counter, "212"),
        Triple("displayXL · 96", t.displayXL, "1.204"),
        Triple("displayL · 64", t.displayL, "47 min"),
        Triple("displayM · 44", t.displayM, "Estadísticas"),
        Triple("headline · 30", t.headline, "Tu octubre, contado."),
        Triple("title · 20", t.title, "@creadora_de_recetas"),
        Triple("body · 16", t.body, "Hoy llevas 212. Eso son 47 minutos que nadie te va a devolver."),
        Triple("bodySmall · 14", t.bodySmall, "Solo lee la pantalla de TikTok, Instagram y YouTube."),
        Triple("quote · 28", t.quote, "«Eso son 47 minutos que nadie te va a devolver.»"),
        Triple("quoteSmall · 20", t.quoteSmall, "Tres temporadas de una serie de las buenas."),
        Triple("dataL · 22", t.dataL, "47 MIN · 4 COMPARTIDOS"),
        Triple("data · 15", t.data, "120 · 26 MIN"),
        Triple("dataS · 13", t.dataS, "Base: 240 ECTS × 25 h (Plan Bolonia)"),
        Triple("label · 11", t.label, Format.upper("Vídeos · hora punta")),
        Triple("labelL · 13", t.labelL, Format.upper("Semana  mes  año")),
        Triple("button · 14", t.button, Format.upper("Compartir")),
    )
    Section(index, "Tipografía", trailing = "16 estilos") {
        samples.forEach { (name, style, sample) ->
            Label(name)
            Spacer(Modifier.height(Space.x1))
            Text(sample, style = style, color = ReelTheme.colors.text)
            Spacer(Modifier.height(Space.x5))
        }
    }
}

// ====================================================================== 03 ejes variables

private fun bricolage(weight: Int, width: Float, opsz: Float) = FontFamily(
    Font(
        R.font.bricolage_grotesque,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.width(width),
            FontVariation.Setting("opsz", opsz),
        ),
    ),
)

/** Comprueba en pantalla que se aplican los ejes de Bricolage (si salen iguales, no se aplican). */
@Composable
private fun AxesSection(index: Int) {
    val base = ReelTheme.type.displayL
    val rows = listOf(
        "wght 800 · wdth 75 (marcador)" to bricolage(800, 75f, 96f),
        "wght 800 · wdth 100" to bricolage(800, 100f, 96f),
        "wght 400 · wdth 100 · opsz 14" to bricolage(400, 100f, 14f),
        "wght 200 · wdth 75" to bricolage(200, 75f, 96f),
    )
    Section(index, "Ejes variables", trailing = "Bricolage") {
        rows.forEach { (name, family) ->
            Label(name)
            Text("212 0123", style = base.copy(fontFamily = family, fontWeight = null), color = ReelTheme.colors.text)
            Spacer(Modifier.height(Space.x3))
        }
        Caption("Si las cuatro líneas se ven iguales, Compose no está aplicando los ejes.")
    }
}

// ====================================================================== 04 espaciado y formas

@Composable
private fun SpaceSection(index: Int) {
    val tokens = listOf(
        "x1" to Space.x1, "x2" to Space.x2, "x3" to Space.x3, "x4" to Space.x4, "x5" to Space.x5,
        "x6" to Space.x6, "x8" to Space.x8, "x10" to Space.x10, "x12" to Space.x12, "x16" to Space.x16,
        "x20" to Space.x20,
    )
    Section(index, "Espaciado", trailing = "Retícula 4 dp") {
        tokens.forEach { (name, value) ->
            Row(Modifier.height(Space.x6), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = ReelTheme.type.dataS, color = ReelTheme.colors.textMuted, modifier = Modifier.width(Space.x10))
                Box(Modifier.width(value).height(Space.x2).background(ReelTheme.colors.signal))
                Spacer(Modifier.width(Space.x3))
                Text("${value.value.toInt()} dp", style = ReelTheme.type.dataS, color = ReelTheme.colors.text)
            }
        }
        Spacer(Modifier.height(Space.x4))
        Label("Formas: bloque 0 · ficha y botón 2 · punto")
        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x4), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Space.x12).background(ReelTheme.colors.bgSunken, Shapes.block))
            Box(Modifier.size(Space.x10, Space.x12).background(ReelTheme.colors.bgSunken, Shapes.tile))
            Box(Modifier.size(Space.x2).background(ReelTheme.colors.signal, Shapes.dot))
        }
    }
}

// ====================================================================== 05 iconos

@Composable
private fun IconsSection(index: Int) {
    Section(index, "Iconos", trailing = "${ReelIcons.all.size} · 24 dp") {
        ReelIcons.all.chunked(5).forEach { row ->
            Row {
                row.forEach { (name, icon) -> IconCell(name, icon, Modifier.weight(1f)) }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun IconCell(name: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(modifier.height(Space.x16 + Space.x2), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(Space.x3))
        ReelIcon(icon, contentDescription = name, tint = ReelTheme.colors.text)
        Spacer(Modifier.height(Space.x2))
        Text(
            name,
            style = ReelTheme.type.label.copy(fontSize = 9.sp, letterSpacing = 0.sp),
            color = ReelTheme.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** QA de geometría: cada icono a 3× sobre sus guías (cuadrado 4–20, círculo r 9, margen de 2 dp). */
@Composable
private fun IconsLargeSection(index: Int) {
    val colors = ReelTheme.colors
    Section(index, "Iconos a 3×", trailing = "Trazo $ReelIconStroke") {
        ReelIcons.all.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                row.forEach { (name, icon) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(72.dp)) {
                            Canvas(Modifier.size(72.dp)) {
                                val u = size.width / 24f
                                val guide = colors.signal.copy(alpha = 0.35f)
                                val hair = Stroke(width = 1f)
                                drawRect(colors.bgSunken)
                                drawRect(guide, topLeft = Offset(2 * u, 2 * u), size = Size(20 * u, 20 * u), style = hair)
                                drawRect(guide, topLeft = Offset(4 * u, 4 * u), size = Size(16 * u, 16 * u), style = hair)
                                drawCircle(guide, radius = 9 * u, style = hair)
                                drawLine(guide, Offset(12 * u, 0f), Offset(12 * u, size.height), strokeWidth = 1f)
                                drawLine(guide, Offset(0f, 12 * u), Offset(size.width, 12 * u), strokeWidth = 1f)
                            }
                            Image(
                                painter = rememberVectorPainter(icon),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(colors.text),
                                modifier = Modifier.size(72.dp),
                            )
                        }
                        Text(name, style = ReelTheme.type.dataS.copy(fontSize = 10.sp), color = colors.textMuted, maxLines = 1)
                        Spacer(Modifier.height(Space.x2))
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ====================================================================== 07 botones

@Composable
private fun ButtonsSection(index: Int) {
    Section(index, "Botones", trailing = "48 dp") {
        ReelButton("Conectar", onClick = {}, modifier = Modifier.fillMaxWidth(), trailingIcon = ReelIcons.ArrowRight)
        Spacer(Modifier.height(Space.x3))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
            ReelButton("Compartir", onClick = {}, icon = ReelIcons.Share)
            ReelButton("Exportar", onClick = {}, style = ReelButtonStyle.Secondary, icon = ReelIcons.Download)
        }
        Spacer(Modifier.height(Space.x2))
        ReelButton("Ver el detalle", onClick = {}, style = ReelButtonStyle.Ghost)
        Spacer(Modifier.height(Space.x4))
        Label("Desactivados")
        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x3), verticalAlignment = Alignment.CenterVertically) {
            ReelButton("Guardar", onClick = {}, enabled = false)
            ReelButton("Copiar", onClick = {}, style = ReelButtonStyle.Secondary, enabled = false)
            ReelButton("Más", onClick = {}, style = ReelButtonStyle.Ghost, enabled = false)
        }
        Spacer(Modifier.height(Space.x4))
        Label("Botones de icono")
        Row(Modifier.padding(start = 0.dp), verticalAlignment = Alignment.CenterVertically) {
            ReelIconButton(ReelIcons.Settings, "Abrir ajustes", onClick = {})
            ReelIconButton(ReelIcons.Share, "Compartir", onClick = {})
            ReelIconButton(ReelIcons.Close, "Cerrar", onClick = {})
            ReelIconButton(ReelIcons.Trash, "Borrar", onClick = {}, tint = ReelTheme.colors.signalText)
            ReelIconButton(ReelIcons.ChevronRight, "Siguiente", onClick = {}, enabled = false)
        }
    }
}

// ====================================================================== 08 controles

@Composable
private fun ControlsSection(index: Int) {
    var daily by remember { mutableStateOf(true) }
    var monthly by remember { mutableStateOf(false) }
    var goal by remember { mutableIntStateOf(150) }
    Section(index, "Controles", trailing = "Switch · Stepper") {
        SwitchLine("Aviso diario", checked = daily, onCheckedChange = { daily = it })
        SwitchLine("Resumen mensual", checked = monthly, onCheckedChange = { monthly = it })
        SwitchLine("Calibración", checked = true, onCheckedChange = {}, enabled = false)
        ReceiptLine("Objetivo") { Stepper(value = goal, onValueChange = { goal = it }, range = 0..500, step = 10) }
        Spacer(Modifier.height(Space.x4))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x4), verticalAlignment = Alignment.CenterVertically) {
            ReelSwitch(daily, onCheckedChange = { daily = it }, contentDescription = "Aviso diario")
            ReelSwitch(!daily, onCheckedChange = { daily = !it }, contentDescription = "Al revés")
            ReelSwitch(false, onCheckedChange = null, enabled = false)
        }
        Spacer(Modifier.height(Space.x5))
        Label("Objetivo diario")
        Spacer(Modifier.height(Space.x2))
        Stepper(value = goal, onValueChange = { goal = it }, range = 0..500, step = 10)
        Spacer(Modifier.height(Space.x3))
        Caption("Mantén pulsado + o − para repetir (acelera y vibra en cada paso).")
    }
}

// ====================================================================== 09 navegación

@Composable
private fun NavigationSection(index: Int) {
    var tab by remember { mutableIntStateOf(0) }
    var offset by remember { mutableIntStateOf(0) }
    val today = LocalDate.of(2026, 11, 3)
    val label = when (tab) {
        0 -> {
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong()).plusWeeks(offset.toLong())
            Format.range(monday, monday.plusDays(6))
        }
        1 -> Format.monthYear(today.plusMonths(offset.toLong()))
        2 -> (today.year + offset).toString()
        else -> "Desde el 3 mar 2026"
    }
    Section(index, "Navegación", trailing = "Tabs · periodo · cabeceras", padded = false) {
        Column(Modifier.padding(horizontal = Space.screen)) {
            SegmentedTabs(
                options = listOf("Semana", "Mes", "Año", "Siempre"),
                selectedIndex = tab,
                onSelect = {
                    tab = it
                    offset = 0
                },
            )
        }
        Spacer(Modifier.height(Space.x2))
        PeriodNavigator(
            label = label,
            onPrevious = { offset-- },
            onNext = { offset++ },
            canGoPrevious = tab != 3 && offset > -24,
            canGoNext = tab != 3 && offset < 0,
            modifier = Modifier.padding(horizontal = Space.x2),
        )
        Spacer(Modifier.height(Space.x6))
        Column(Modifier.padding(horizontal = Space.screen)) { Label("ReelTopBar") }
        ReelTopBar(
            kicker = "Hoy · martes 3 nov",
            divider = true,
            windowInsets = WindowInsets(0),
            actions = { ReelIconButton(ReelIcons.Settings, "Abrir ajustes", onClick = {}) },
        )
        Spacer(Modifier.height(Space.x3))
        ReelTopBar(
            title = "Estadísticas",
            windowInsets = WindowInsets(0),
            actions = { ReelIconButton(ReelIcons.Settings, "Abrir ajustes", onClick = {}) },
        )
        Spacer(Modifier.height(Space.x3))
        ReelTopBar(
            title = "Ajustes",
            kicker = "08 · Acerca de",
            onBack = {},
            titleStyle = ReelTheme.type.headline,
            windowInsets = WindowInsets(0),
        )
    }
}

// ====================================================================== 10 ticket

private val FakeDays: List<Float> = List(35) { i -> (((i * 37 + 11) % 23) + 4 + if (i % 9 == 4) 18 else 0).toFloat() }

@Composable
private fun TicketSection(index: Int) {
    val c = ReelTheme.colors
    Section(index, "Ticket", trailing = "3", padded = false) {
        Column(Modifier.padding(horizontal = Space.screen)) {
            ReceiptLine("TikTok", "120 · 26 MIN", mark = c.platform(Platform.TIKTOK))
            ReceiptLine("Reels", "64 · 15 MIN", mark = c.platform(Platform.INSTAGRAM))
            ReceiptLine("Shorts", "28 · 6 MIN", mark = c.platform(Platform.YOUTUBE))
            ReceiptLine("Total", "212", highlight = true)
            ReceiptLine("Hora punta", "23:00 · 41", valueColor = c.signalText)
            ReceiptLine("Una etiqueta larga que necesita dos líneas para caber", "1.204")
            ReceiptLine("Exportar CSV", onClick = {}) { ReelIcon(ReelIcons.ArrowRight, contentDescription = null) }
            Spacer(Modifier.height(Space.x4))
            Label("Dashed")
            Spacer(Modifier.height(Space.x3))
            TicketDivider()
            Spacer(Modifier.height(Space.x4))
        }
        Column(Modifier.padding(horizontal = Space.screen)) { Label("Perforated · a sangre") }
        Spacer(Modifier.height(Space.x2))
        TicketDivider(style = TicketDividerStyle.Perforated)
        Spacer(Modifier.height(Space.x6))
        // Un trozo de ticket: papel bgRaised con el borde inferior dentado.
        Column(
            Modifier
                .padding(horizontal = Space.screen)
                .fillMaxWidth()
                .jaggedEdge(c.bgRaised, JaggedSide.Bottom)
                .padding(start = Space.x5, end = Space.x5, top = Space.x5, bottom = Space.x6),
        ) {
            Label("Reelometer · ticket nº 0212")
            Spacer(Modifier.height(Space.x2))
            ReceiptLine("Vídeos", "212")
            ReceiptLine("Tiempo", "47 MIN")
            Spacer(Modifier.height(Space.x3))
            Barcode(values = FakeDays, caption = "000212 047 2026", height = 64.dp)
        }
        Spacer(Modifier.height(Space.x6))
        Column(Modifier.padding(horizontal = Space.screen)) {
            Label("Barcode · 120 días (agrupa) · altura por valor")
            Spacer(Modifier.height(Space.x2))
            Barcode(values = List(120) { FakeDays[it % FakeDays.size] * (1 + it % 3) }, heightByValue = true, height = 40.dp)
        }
    }
}

// ====================================================================== 11 bloques

@Composable
private fun BlocksSection(index: Int) {
    val c = ReelTheme.colors
    var stampKey by remember { mutableIntStateOf(0) }
    Section(index, "Bloques", trailing = "Band · Stamp · Notice", padded = false) {
        Band(text = "12.408 vídeos · 61 h", kicker = "Desde el 3 mar", onClick = {})
        Spacer(Modifier.height(Space.x8))
        Column(Modifier.padding(horizontal = Space.screen)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                key(stampKey) { Stamp("100 % local", subtext = "Sin servidores", hapticOnLand = true) }
                Spacer(Modifier.weight(1f))
                ReelButton("Otra vez", onClick = { stampKey++ }, style = ReelButtonStyle.Ghost)
            }
            Spacer(Modifier.height(Space.x8))
            Notice(
                title = "Sin señal",
                text = "Sin el permiso de accesibilidad no veo nada. Que, por otra parte, es lo que esperas de una app así.",
                actionLabel = "Conectar",
                onAction = {},
            )
            Spacer(Modifier.height(Space.x8))
            Label("Label")
            Spacer(Modifier.height(Space.x1))
            Label("Hora punta", color = c.text, large = true)
            Spacer(Modifier.height(Space.x5))
            Label("PlatformMark · punto y filete")
            Spacer(Modifier.height(Space.x2))
            Platform.entries.forEach { p ->
                Row(Modifier.height(Space.x8), verticalAlignment = Alignment.CenterVertically) {
                    PlatformMark(p)
                    Spacer(Modifier.width(Space.x2))
                    Text(p.shortName, style = ReelTheme.type.body, color = c.text, modifier = Modifier.width(Space.x20))
                    PlatformMark(p, style = MarkStyle.Rule)
                }
            }
        }
    }
}

// ====================================================================== 12 diálogos

@Composable
private fun DialogsSection(index: Int) {
    var plain by remember { mutableStateOf(false) }
    var destructive by remember { mutableStateOf(false) }
    Section(index, "Diálogos", trailing = "ReelDialog") {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
            ReelButton("Normal", onClick = { plain = true }, style = ReelButtonStyle.Secondary)
            ReelButton("Destructivo", onClick = { destructive = true }, style = ReelButtonStyle.Secondary, icon = ReelIcons.Trash)
        }
    }
    ReelDialog(
        visible = plain,
        onDismiss = { plain = false },
        kicker = "02 · Avisos",
        title = "¿Te aviso cada noche?",
        text = "A las 22:00, una línea con lo de hoy. Nada más.",
        confirmLabel = "Activar",
        onConfirm = { plain = false },
        dismissLabel = "Ahora no",
    )
    ReelDialog(
        visible = destructive,
        onDismiss = { destructive = false },
        kicker = "Irreversible",
        title = "¿Borrar todos tus datos?",
        text = "212 días de registro desaparecen de este móvil. No hay copia en ningún otro sitio.",
        confirmLabel = "Borrar",
        onConfirm = { destructive = false },
        destructive = true,
    )
}

// ====================================================================== 13 icono de la app

@Composable
private fun AppIconSection(index: Int) {
    val c = ReelTheme.colors
    var replay by remember { mutableIntStateOf(0) }
    Section(index, "Icono de la app", trailing = "Adaptativo") {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x4), verticalAlignment = Alignment.CenterVertically) {
            AdaptivePreview(CircleShape, 72.dp)
            AdaptivePreview(RoundedCornerShape(30), 72.dp)
            AdaptivePreview(RoundedCornerShape(8), 48.dp)
            MonochromePreview(c, 72.dp)
        }
        Spacer(Modifier.height(Space.x4))
        Label("Capas a 108 dp · zona segura 66 dp")
        Spacer(Modifier.height(Space.x2))
        Box(Modifier.size(162.dp)) {
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val u = size.width / 108f
                drawCircle(c.signal.copy(alpha = 0.6f), radius = 33 * u, style = Stroke(1f))
                drawCircle(c.textMuted.copy(alpha = 0.6f), radius = 36 * u, style = Stroke(1f))
            }
        }
        Spacer(Modifier.height(Space.x4))
        Label("Notificación · 24 y 48 dp")
        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x4), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_notification), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(c.text))
            Image(painterResource(R.drawable.ic_notification), null, Modifier.size(48.dp), colorFilter = ColorFilter.tint(c.text))
        }
        Spacer(Modifier.height(Space.x5))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Splash animado (colores del sistema)", modifier = Modifier.weight(1f))
            ReelButton("Repetir", onClick = { replay++ }, style = ReelButtonStyle.Ghost)
        }
        Spacer(Modifier.height(Space.x2))
        SplashPreview(replay)
    }
}

@Composable
private fun AdaptivePreview(shape: androidx.compose.ui.graphics.Shape, size: Dp) {
    // El lanzador enseña los 72 dp centrales de los 108: se pintan las capas a 1,5× y se recorta.
    Box(Modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        Box(Modifier.requiredSize(size * 1.5f)) {
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun MonochromePreview(c: ReelColors, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(c.bgSunken), contentAlignment = Alignment.Center) {
        Image(
            painterResource(R.drawable.ic_launcher_monochrome),
            null,
            Modifier.requiredSize(size * 1.5f),
            colorFilter = ColorFilter.tint(c.signalText),
        )
    }
}

@Composable
private fun SplashPreview(replay: Int) {
    val context = LocalContext.current
    val bg = remember { Color(ContextCompat.getColor(context, R.color.reel_bg)) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(bg)
            .border(Space.hairline, ReelTheme.colors.line),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ImageView(it).apply { setImageResource(R.drawable.splash_flap) } },
            modifier = Modifier.size(192.dp),
            update = { view ->
                if (replay > 0) {
                    (view.drawable as? Animatable)?.let { it.stop(); it.start() }
                }
            },
        )
    }
}

// ====================================================================== 16 burbujas del envío rápido

/**
 * Las burbujas de cristal del envío rápido (1.4.2) en todos sus estados y en columna y fila, con el mismo dibujo
 * que las de verdad ([GlassBubbleArt]), sobre un fondo a franjas claras, oscuras y de color como un fotograma de
 * reel (el cristal solo se juzga encima de algo). Abrir con `--ei section 15`.
 */
@Composable
private fun BubblesSection(index: Int) {
    val context = LocalContext.current
    val art = remember { GlassBubbleArt(GlassBubbleArt.font(context)) }
    val c = ReelTheme.colors
    val photo = remember { fakeAvatar(c.instagram.toArgb(), c.text.toArgb(), c.bg.toArgb()) }
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "spin",
    )
    Section(index, "Burbujas", trailing = "Envío rápido · 42 dp") {
        val states = listOf(
            Triple("Reposo · foto", BubbleSample(photo = photo), 1f),
            Triple("Reposo · iniciales", BubbleSample(), 1f),
            Triple("Enviando", BubbleSample(photo = photo, mark = GlassBubbleArt.Mark.SENDING), 1f),
            Triple("Enviado", BubbleSample(photo = photo, mark = GlassBubbleArt.Mark.SENT), 1f),
            Triple("Fallo", BubbleSample(mark = GlassBubbleArt.Mark.FAILED), 1f),
            Triple("Despegada ×1,15", BubbleSample(photo = photo, lifted = true), 1.15f),
            Triple("La de debajo ×1,08", BubbleSample(hot = true), 1.08f),
        )
        states.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                row.forEach { (label, sample, scale) ->
                    Column(Modifier.width(80.dp)) {
                        BubbleBackdrop(Modifier.size(80.dp)) { GlassBubble(art, sample, spin, scale) }
                        Spacer(Modifier.height(Space.x1))
                        Caption(label)
                    }
                }
            }
            Spacer(Modifier.height(Space.x4))
        }
        Caption("Pila en columna y en fila (franja de abajo, encima del nombre del reel)")
        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically) {
            BubbleBackdrop(Modifier.width(80.dp).height(184.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                    GlassBubble(art, BubbleSample(photo = photo), spin)
                    GlassBubble(art, BubbleSample(initials = "LP", mark = GlassBubbleArt.Mark.SENT), spin)
                    GlassBubble(art, BubbleSample(initials = "M"), spin)
                }
            }
            BubbleBackdrop(Modifier.weight(1f).height(184.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                    GlassBubble(art, BubbleSample(photo = photo, mark = GlassBubbleArt.Mark.SENDING), spin)
                    GlassBubble(art, BubbleSample(initials = "LP"), spin)
                    GlassBubble(art, BubbleSample(initials = "M"), spin)
                }
            }
        }
    }
}

private class BubbleSample(
    val photo: android.graphics.Bitmap? = null,
    val initials: String = "A",
    val mark: GlassBubbleArt.Mark = GlassBubbleArt.Mark.NONE,
    val hot: Boolean = false,
    val lifted: Boolean = false,
)

/** Una burbuja de 48 dp (círculo de 42) dibujada con [GlassBubbleArt], como en el overlay. */
@Composable
private fun GlassBubble(art: GlassBubbleArt, sample: BubbleSample, spin: Float, scale: Float = 1f) {
    Canvas(
        Modifier
            .size(Space.x12)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        val radius = GlassBubbleArt.CIRCLE_DP / GlassBubbleArt.TOUCH_DP * size.minDimension / 2f
        drawIntoCanvas {
            art.draw(it.nativeCanvas, center.x, center.y, radius, sample.photo, sample.initials, sample.mark, spin, sample.hot, sample.lifted)
        }
    }
}

/** Fondo de prueba como un fotograma de reel: franjas claras, oscuras y de color. */
@Composable
private fun BubbleBackdrop(modifier: Modifier, content: @Composable () -> Unit) {
    val c = ReelTheme.colors
    val stripes = listOf(c.text, Color(0xFF8A8F98), c.instagram, Color(0xFF1B2A3A), c.tiktok, Color.Black)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height / stripes.size
            stripes.forEachIndexed { i, color -> drawRect(color, Offset(0f, i * h), Size(size.width, h + 1f)) }
        }
        content()
    }
}

/** Una «foto de perfil» de mentira: fondo de color, cabeza y hombros. */
private fun fakeAvatar(background: Int, figure: Int, shade: Int): android.graphics.Bitmap {
    val side = 128
    val bitmap = android.graphics.Bitmap.createBitmap(side, side, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    canvas.drawColor(background)
    paint.color = shade
    canvas.drawCircle(side * 0.5f, side * 1.02f, side * 0.42f, paint)
    paint.color = figure
    canvas.drawCircle(side * 0.5f, side * 0.40f, side * 0.20f, paint)
    canvas.drawCircle(side * 0.5f, side * 1.06f, side * 0.38f, paint)
    return bitmap
}
