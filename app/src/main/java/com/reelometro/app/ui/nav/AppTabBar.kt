package com.reelometro.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.reelometro.app.copy.A11y
import com.reelometro.app.copy.Labels
import com.reelometro.app.ui.components.ReelTab
import com.reelometro.app.ui.components.ReelTabBar
import com.reelometro.app.ui.icons.ReelIcons

private val Tabs = listOf(
    ReelTab(Labels.TAB_TODAY, ReelIcons.Today, contentDescription = A11y.TAB_TODAY),
    ReelTab(Labels.TAB_STATS, ReelIcons.Stats, contentDescription = A11y.TAB_STATS),
    ReelTab(Labels.TAB_WRAPPED, ReelIcons.Wrapped, contentDescription = A11y.TAB_WRAPPED),
    ReelTab(Labels.TAB_PASSPORT, ReelIcons.Passport, contentDescription = A11y.TAB_PASSPORT),
)

/**
 * Barra de pestañas de la app (Hoy · Estadísticas · Resumen · Pasaporte) con el componente del
 * sistema de diseño. Sin el modo pasaporte, solo las tres primeras.
 */
@Composable
internal fun AppTabBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    passportEnabled: Boolean = true,
) {
    val items = if (passportEnabled) Tabs else Tabs.take(3)
    ReelTabBar(items = items, selectedIndex = selectedIndex.coerceIn(0, items.size - 1), onSelect = onSelect, modifier = modifier)
}
