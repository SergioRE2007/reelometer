package com.reelometro.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.unit.ColorProvider
import com.reelometro.app.MainActivity
import com.reelometro.app.copy.WidgetCopy
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapSprite
import com.reelometro.app.ui.components.flap.FlapCell
import com.reelometro.app.ui.components.flap.FlapGlyphs
import com.reelometro.app.ui.theme.ReelTypography
import kotlin.math.floor

/**
 * Widget «Hoy» (docs/DESIGN.md §9): una ficha (papel en claro, tinta en oscuro) con «HOY», el
 * marcador split-flap de hoy con las fuentes de la app, el tiempo debajo y, con objetivo, su estado
 * y una barra. Sin señal (servicio desactivado), fichas en blanco y «SIN SEÑAL». Toque → Hoy.
 */
class TodayWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = WidgetUpdater.loadFresh(context)
        provideContent {
            val live by WidgetUpdater.data.collectAsState()
            TodayWidgetContent(live ?: initial)
        }
    }

    /** API 35+: vista previa generada para el selector de widgets (ver `WidgetUpdater.start`). */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { TodayWidgetContent(WidgetData.PREVIEW) }
    }
}

/** Receptor del widget (manifiesto). Mantiene programado el refresco de medianoche mientras haya widgets. */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.onWidgetsChanged(context, enabled = true)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdater.onWidgetsChanged(context, enabled = false)
    }
}

/** Ruta de MainActivity que abre el toque. */
private const val ROUTE_TODAY = "today"

/** Esquinas de ficha (docs/DESIGN.md §3: fichas y botones, 2 dp). */
private val TILE_RADIUS = 2.dp

/** Tope de la escala de fuente del sistema para las etiquetas (pintadas como imagen). */
private const val MAX_FONT_SCALE = 1.3f

/** Contenido del widget para el tamaño actual (`LocalSize`). [data] `null`: todavía sin datos. */
@Composable
internal fun TodayWidgetContent(data: WidgetData?) {
    val context = LocalContext.current
    val size = LocalSize.current
    val glyphs = WidgetGlyphs.get(context)
    val model = WidgetModel.of(data)
    val fontScale = context.resources.configuration.fontScale.coerceIn(1f, MAX_FONT_SCALE)
    val type = ReelTypography
    val layout = WidgetLayout.compute(
        width = size.width.value,
        height = size.height.value,
        model = model,
        metrics = glyphs.metrics,
        label = textSpec(type.label.fontSize.value, type.label.fontWeight?.weight, type.label.letterSpacing.value, fontScale),
        dataL = textSpec(type.dataL.fontSize.value, type.dataL.fontWeight?.weight, 0f, fontScale),
        data = textSpec(type.data.fontSize.value, type.data.fontWeight?.weight, 0f, fontScale),
        // Mono 500 también en pequeño: a 13 dp el 400 se queda flojo sobre el papel.
        dataS = textSpec(type.dataS.fontSize.value, type.data.fontWeight?.weight, 0f, fontScale),
        text = glyphs,
        maxFontDp = type.counter.fontSize.value,
    )
    val board = BoardSpec.of(layout.fontDp, glyphs.metrics, glyphs.density)
    val description = model.flap?.takeIf { layout.flapDp > 0f }
        ?.let { WidgetCopy.withFlap(model.description, it) } ?: model.description

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(TILE_RADIUS)
            .background(WidgetColors.bg)
            .clickable(actionStartActivity(MainActivity.intent(context, ROUTE_TODAY)))
            .semantics { contentDescription = description }
            .padding(layout.padding.dp),
    ) {
        when (layout.arrangement) {
            Arrangement.STACKED, Arrangement.SIDE -> Stacked(model, layout, board, glyphs)
            Arrangement.ROW -> Compact(model, layout, board, glyphs)
        }
        // Flap (si está encendido en Ajustes, solo en el tamaño grande): abajo a la derecha, junto al marcador y
        // encima de la barra del objetivo. La maqueta ya ha estrechado el marcador para dejarle sitio;
        // va en una capa aparte para no anidar más el marcador (RemoteViews limita la profundidad).
        val flap = model.flap
        if (flap != null && layout.flapDp > 0f) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(bottom = if (layout.showBar) (WidgetLayout.GAP + WidgetLayout.BAR).dp else 0.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                FlapMark(flap, layout.flapDp, glyphs)
            }
        }
    }
}

private fun textSpec(size: Float, weight: Int?, tracking: Float, fontScale: Float) =
    TextSpec(sizeDp = size * fontScale, weight = weight ?: 400, trackingEm = tracking)

/**
 * «HOY» (y el objetivo) arriba; abajo, el marcador con el tiempo debajo (apilado) o al lado (2×1)
 * y la barra del objetivo. El aire sobrante queda entre la etiqueta y el marcador.
 */
@Composable
private fun Stacked(model: WidgetModel, layout: WidgetLayout, board: BoardSpec, glyphs: WidgetGlyphs) {
    val detail = model.detail?.takeIf { layout.showDetail }
    val detailColor = if (model.detailAlert) WidgetColors.signalText else WidgetColors.text
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextLine(model.label, layout.label, WidgetColors.textMuted, glyphs)
            val status = model.status
            if (layout.showStatus && status != null) {
                Spacer(GlanceModifier.defaultWeight())
                TextLine(status, layout.label, if (model.statusAlert) WidgetColors.signalText else WidgetColors.textMuted, glyphs)
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        if (layout.arrangement == Arrangement.SIDE && detail != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Board(model, board, glyphs)
                Spacer(GlanceModifier.width(WidgetLayout.COLUMN_GAP.dp))
                TextLine(detail, layout.detail, detailColor, glyphs)
            }
        } else {
            Board(model, board, glyphs)
            if (detail != null) {
                Spacer(GlanceModifier.height(WidgetLayout.GAP.dp))
                TextLine(detail, layout.detail, detailColor, glyphs)
            }
        }
        val fraction = model.goalFraction
        if (layout.showBar && fraction != null) {
            Spacer(GlanceModifier.height(WidgetLayout.GAP.dp))
            GoalBar(fraction, model.statusAlert, layout.innerWidth)
        }
    }
}

/** En una fila: el marcador a la izquierda y las etiquetas en una columna estrecha a la derecha. */
@Composable
private fun Compact(model: WidgetModel, layout: WidgetLayout, board: BoardSpec, glyphs: WidgetGlyphs) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Board(model, board, glyphs)
        Spacer(GlanceModifier.defaultWeight())
        Column(horizontalAlignment = Alignment.End) {
            TextLine(model.label, layout.label, WidgetColors.textMuted, glyphs)
            val detail = model.detail
            if (layout.showDetail && detail != null) {
                Spacer(GlanceModifier.height(WidgetLayout.LINE_GAP.dp))
                TextLine(detail, layout.detail, if (model.detailAlert) WidgetColors.signalText else WidgetColors.text, glyphs)
            }
            val status = model.status
            if (layout.showStatus && status != null) {
                Spacer(GlanceModifier.height(WidgetLayout.LINE_GAP.dp))
                TextLine(status, layout.label, if (model.statusAlert) WidgetColors.signalText else WidgetColors.textMuted, glyphs)
            }
        }
    }
}

/** Las fichas y los puntos de millar. Cada celda es un solo hijo de la fila (Glance admite 10). */
@Composable
private fun Board(model: WidgetModel, board: BoardSpec, glyphs: WidgetGlyphs) {
    Row(verticalAlignment = Alignment.Top) {
        model.cells.forEachIndexed { i, cell ->
            val gap = if (i == 0) 0f else board.gap
            when (cell) {
                is FlapCell.Tile -> Box(GlanceModifier.padding(start = gap.dp)) {
                    Tile(cell.glyph, dim = cell.position >= model.dimFrom, board, glyphs)
                }
                FlapCell.Separator -> Image(
                    provider = ImageProvider(glyphs.dot(board)),
                    contentDescription = null,
                    modifier = GlanceModifier.width((gap + board.dotW).dp).height(board.tileH.dp).padding(start = gap.dp),
                    contentScale = ContentScale.FillBounds,
                    colorFilter = ColorFilter.tint(WidgetColors.text),
                )
            }
        }
    }
}

/**
 * Una ficha: fondo `bgSunken` con esquinas de 2 dp, el glifo (máscara teñida), la mitad inferior
 * apenas sombreada y la bisagra en `bg` encima (partida si hay guion, para no leer «=»).
 */
@Composable
private fun Tile(glyph: Char, dim: Boolean, board: BoardSpec, glyphs: WidgetGlyphs) {
    val w = board.tileW
    val h = board.tileH
    val hinge = board.hinge
    Box(GlanceModifier.size(w.dp, h.dp).cornerRadius(TILE_RADIUS).background(WidgetColors.tile)) {
        if (glyph != FlapGlyphs.EMPTY) {
            Image(
                provider = ImageProvider(glyphs.tile(glyph, board)),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(if (dim) WidgetColors.textFaint else WidgetColors.text),
            )
        }
        Column(GlanceModifier.fillMaxSize()) {
            Spacer(GlanceModifier.height((h / 2f - hinge / 2f).dp))
            if (glyph == FlapGlyphs.DASH) {
                val side = ((w - board.dashW) / 2f).coerceAtLeast(0f)
                Row(GlanceModifier.fillMaxWidth().height(hinge.dp)) {
                    Spacer(GlanceModifier.width(side.dp).fillMaxHeight().background(WidgetColors.bg))
                    Spacer(GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(side.dp).fillMaxHeight().background(WidgetColors.bg))
                }
            } else {
                Spacer(GlanceModifier.fillMaxWidth().height(hinge.dp).background(WidgetColors.bg))
            }
            Spacer(GlanceModifier.fillMaxWidth().defaultWeight().background(WidgetColors.tileShade))
        }
    }
}

/**
 * Barra del objetivo: pista `bgSunken` a todo el ancho, relleno en `text` o, si se ha pasado, entera
 * en acento. El relleno parcial se mide con el ancho de la maqueta ([width]); si el launcher estira
 * el widget algo más, la pista crece y el relleno no (error de pocos dp, nunca «lleno» de más).
 */
@Composable
private fun GoalBar(fraction: Float, exceeded: Boolean, width: Float) {
    Row(GlanceModifier.fillMaxWidth().height(WidgetLayout.BAR.dp).background(WidgetColors.tile)) {
        val fill = if (exceeded || fraction >= 1f) GlanceModifier.fillMaxWidth() else GlanceModifier.width((width * fraction).dp)
        if (exceeded || width * fraction >= 1f) {
            Spacer(fill.fillMaxHeight().background(if (exceeded) WidgetColors.signal else WidgetColors.text))
        }
    }
}

/**
 * Flap en pixel art (el mismo dibujo que en la pestaña FLAP): una máscara por color apilada en una
 * caja y teñida con los colores del widget (ficha `bgSunken`, bisagra `bg`, gris, tinta y acento),
 * así cambia sola entre claro y oscuro. Decorativo para TalkBack (el widget entero ya se lee).
 */
@Composable
private fun FlapMark(mood: FlapMood, sizeDp: Float, glyphs: WidgetGlyphs) {
    val pixelPx = floor(sizeDp / FlapSprite.SIZE * glyphs.density).toInt().coerceAtLeast(1)
    val side = (FlapSprite.SIZE * pixelPx / glyphs.density).dp
    val present = FlapSprite.frame(mood).joinToString("")
    Box(GlanceModifier.size(side, side)) {
        for (layer in FLAP_LAYERS) {
            if (layer in present) {
                Image(
                    provider = ImageProvider(glyphs.flap(mood, layer, pixelPx)),
                    contentDescription = null,
                    modifier = GlanceModifier.size(side, side),
                    contentScale = ContentScale.FillBounds,
                    colorFilter = ColorFilter.tint(flapColor(layer)),
                )
            }
        }
    }
}

/** Capas de Flap de abajo arriba: ficha, bisagra, gris, tinta y acento (caracteres de `FlapSprite`). */
private const val FLAP_LAYERS = "shmXo"

private fun flapColor(layer: Char): ColorProvider = when (layer) {
    's' -> WidgetColors.tile
    'h' -> WidgetColors.bg
    'm' -> WidgetColors.textMuted
    'X' -> WidgetColors.text
    else -> WidgetColors.signal
}

/** Una línea de texto pintada con JetBrains Mono (máscara teñida de [color]). */
@Composable
private fun TextLine(text: String, spec: TextSpec, color: ColorProvider, glyphs: WidgetGlyphs) {
    val bitmap = glyphs.text(text, spec)
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        modifier = GlanceModifier.size((bitmap.width / glyphs.density).dp, (bitmap.height / glyphs.density).dp),
        contentScale = ContentScale.FillBounds,
        colorFilter = ColorFilter.tint(color),
    )
}
