package com.reelometro.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.reelometro.app.copy.SettingsCopy
import com.reelometro.app.copy.SettingsSection
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.pressable
import com.reelometro.app.ui.icons.ReelIcon
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space

/** Las 8 secciones numeradas de Ajustes, en orden (cada una es un elemento de la lista). */
internal enum class SettingsPart(val key: String) {
    GOAL("goal"),
    NOTIFICATIONS("notifications"),
    APPS("apps"),
    THEME("theme"),
    DATA("data"),
    SERVICE("service"),
    PRIVACY("privacy"),
    ABOUT("about"),
    ;

    val section: SettingsSection
        get() = when (this) {
            GOAL -> SettingsCopy.GOAL
            NOTIFICATIONS -> SettingsCopy.NOTIFICATIONS
            APPS -> SettingsCopy.APPS
            THEME -> SettingsCopy.THEME
            DATA -> SettingsCopy.DATA
            SERVICE -> SettingsCopy.SERVICE
            PRIVACY -> SettingsCopy.PRIVACY
            ABOUT -> SettingsCopy.ABOUT
        }
}

/**
 * Orden de los elementos de la lista de Ajustes: barra de arriba, índice, las 8 secciones y, al final,
 * «Licencias y herramientas» (plegado). El índice salta a [itemOf]; la barra fija dice en qué sección estás.
 */
internal object SettingsLayout {
    const val TOP_ITEM = 0
    const val INDEX_ITEM = 1
    const val FIRST_SECTION_ITEM = 2
    val TOOLS_ITEM: Int get() = FIRST_SECTION_ITEM + SettingsPart.entries.size

    fun itemOf(part: SettingsPart): Int = FIRST_SECTION_ITEM + part.ordinal

    /** La sección de un elemento; lo de después de ACERCA DE (las herramientas) cuenta como ACERCA DE. */
    fun partOf(item: Int): SettingsPart? = when {
        item < FIRST_SECTION_ITEM -> null
        item >= TOOLS_ITEM -> SettingsPart.ABOUT
        else -> SettingsPart.entries[item - FIRST_SECTION_ITEM]
    }

    /** La barra fija sale cuando el índice ya no se ve. */
    fun showsBar(firstVisibleItem: Int): Boolean = firstVisibleItem > INDEX_ITEM

    /**
     * Sección en la que estás: la del último elemento visible que empieza a [linePx] o más arriba (la línea queda
     * bajo la barra fija). [visible]: índice del elemento y su posición, en orden, como `visibleItemsInfo`.
     */
    /**
     * Línea que decide la sección actual: bajo la barra fija ([barLinePx]) o, al final de la lista (las últimas
     * secciones no pueden subir hasta la barra), a media pantalla.
     */
    fun lineFor(barLinePx: Int, viewportPx: Int, atEnd: Boolean): Int =
        if (atEnd) maxOf(barLinePx, viewportPx / 2) else barLinePx

    fun currentPart(visible: List<Pair<Int, Int>>, linePx: Int): SettingsPart? {
        val item = visible.lastOrNull { it.second <= linePx } ?: visible.firstOrNull() ?: return null
        return partOf(item.first)
    }
}

/** Grosor de la marca de «aquí estabas» (como el subrayado de `SegmentedTabs`). */
private val MarkThickness = Space.hairline * 2

/**
 * Índice de Ajustes, bajo la barra de arriba: las 8 secciones en dos columnas (`01 OBJETIVO`, `02 AVISOS`…),
 * separadas por filetes, y una última fila con EXPORTAR CSV e IMPORTAR CSV. [marked] lleva el número en el acento
 * y un subrayado: la sección donde estabas antes de volver con ÍNDICE.
 */
@Composable
internal fun SettingsIndex(
    marked: SettingsPart?,
    busy: Boolean,
    onJump: (SettingsPart) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .padding(top = Space.x2),
    ) {
        SettingsPart.entries.chunked(2).forEach { row ->
            IndexRow {
                row.forEach { part ->
                    IndexCell(part.section, marked = part == marked, onClick = { onJump(part) }, modifier = Modifier.weight(1f))
                }
            }
        }
        IndexRow {
            IndexAction(SettingsCopy.EXPORT_LABEL, ReelIcons.Download, enabled = !busy, onClick = onExport, modifier = Modifier.weight(1f))
            IndexAction(SettingsCopy.IMPORT_LABEL, ReelIcons.Upload, enabled = !busy, onClick = onImport, modifier = Modifier.weight(1f))
        }
        Hairline()
    }
}

@Composable
private fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Space.hairline)
            .background(ReelTheme.colors.line),
    )
}

@Composable
private fun IndexRow(content: @Composable RowScope.() -> Unit) {
    Hairline()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), content = content)
}

@Composable
private fun IndexCell(section: SettingsSection, marked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    Box(
        modifier
            .fillMaxHeight()
            .heightIn(min = Space.touch)
            .pressable(onClick)
            .semantics {
                contentDescription = SettingsCopy.indexGo(section)
                if (marked) stateDescription = SettingsCopy.INDEX_HERE
            },
    ) {
        Row(
            Modifier
                .padding(vertical = Space.x3)
                .padding(end = Space.x3)
                .clearAndSetSemantics {},
        ) {
            Text(section.index, style = type.label, color = if (marked) colors.signalText else colors.textMuted)
            Spacer(Modifier.width(Space.x2))
            Label(section.short, color = colors.text, maxLines = 2)
        }
        if (marked) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(end = Space.x3)
                    .height(MarkThickness)
                    .background(colors.signal),
            )
        }
    }
}

@Composable
private fun IndexAction(text: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (enabled) ReelTheme.colors.text else ReelTheme.colors.textMuted
    Row(
        modifier
            .fillMaxHeight()
            .heightIn(min = Space.touch)
            .pressable(onClick, enabled = enabled)
            .padding(vertical = Space.x3)
            .padding(end = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReelIcon(icon, contentDescription = null, tint = color, size = Space.x4)
        Spacer(Modifier.width(Space.x2))
        Label(text, color = color, maxLines = 2)
    }
}

/**
 * Barra fija arriba mientras bajas por Ajustes (cuando el índice ya no se ve): la sección en la que estás
 * («05 · TUS DATOS») y ÍNDICE ↑, que vuelve arriba y deja marcada esa sección. Pasa por encima de la lista.
 */
@Composable
internal fun SettingsJumpBar(visible: Boolean, current: SettingsPart?, onIndex: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ReelTheme.colors
    val reduced = ReelTheme.reducedMotion
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.orSnap(reduced, Motion.standard())) +
            slideInVertically(Motion.orSnap(reduced, Motion.standard())) { -it / 2 },
        exit = fadeOut(Motion.orSnap(reduced, Motion.standard())) +
            slideOutVertically(Motion.orSnap(reduced, Motion.standard())) { -it / 2 },
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Space.touch)
                    .padding(horizontal = Space.screen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label(
                    current?.let { SettingsCopy.indexCurrent(it.section) }.orEmpty(),
                    color = colors.text,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    Modifier
                        .heightIn(min = Space.touch)
                        .pressable(onIndex)
                        .semantics { contentDescription = SettingsCopy.INDEX_BACK_DESCRIPTION }
                        .padding(start = Space.x4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label(SettingsCopy.INDEX_BACK, color = colors.text, maxLines = 1, modifier = Modifier.clearAndSetSemantics {})
                    Spacer(Modifier.width(Space.x1))
                    ReelIcon(ReelIcons.ChevronUp, contentDescription = null, tint = colors.text, size = Space.x4)
                }
            }
            Hairline()
        }
    }
}

/**
 * Línea que pliega y despliega un bloque: `ENVÍO RÁPIDO · 2 FIJADOS ▸`. La flecha gira con un spring
 * (de ▸ a ▾); para TalkBack es un botón con su estado («plegado»/«desplegado»).
 */
@Composable
internal fun FoldLine(text: String, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ReelTheme.colors
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else FOLDED_ROTATION,
        animationSpec = Motion.orSnap(ReelTheme.reducedMotion, Motion.standard()),
        label = "fold",
    )
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Space.touch)
            .pressable(onToggle, onClickLabel = if (expanded) SettingsCopy.FOLD_COLLAPSE else SettingsCopy.FOLD_EXPAND)
            .semantics { stateDescription = if (expanded) SettingsCopy.FOLD_OPEN else SettingsCopy.FOLD_CLOSED },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(text, color = colors.text, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Space.x3))
        ReelIcon(
            ReelIcons.ChevronDown,
            contentDescription = null,
            tint = colors.text,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
    }
}

/** ChevronDown girado: apunta a la derecha (▸) mientras está plegado. */
private const val FOLDED_ROTATION = -90f
