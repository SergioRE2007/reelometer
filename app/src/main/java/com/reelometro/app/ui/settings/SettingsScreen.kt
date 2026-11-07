package com.reelometro.app.ui.settings

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.reelometro.app.copy.AppLanguage
import com.reelometro.app.copy.A11y
import com.reelometro.app.copy.Buttons
import com.reelometro.app.copy.DisclosureCopy
import com.reelometro.app.copy.SettingsCopy
import com.reelometro.app.copy.SettingsSection
import com.reelometro.app.copy.StateCopy
import com.reelometro.app.copy.Variants
import com.reelometro.app.data.Platform
import com.reelometro.app.data.export.ImportMode
import com.reelometro.app.data.settings.AppSettings
import com.reelometro.app.data.settings.ThemeMode
import com.reelometro.app.detection.AvatarStore
import com.reelometro.app.detection.GlassBubbleArt
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.ReceiptLine
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.ReelButtonStyle
import com.reelometro.app.ui.components.ReelDialog
import com.reelometro.app.ui.components.ReelSwitch
import com.reelometro.app.ui.components.ReelTopBar
import com.reelometro.app.ui.components.SectionHeader
import com.reelometro.app.ui.components.SegmentedTabs
import com.reelometro.app.ui.components.Stepper
import com.reelometro.app.ui.components.SwitchLine
import com.reelometro.app.ui.components.TicketDivider
import com.reelometro.app.ui.components.TicketDividerStyle
import com.reelometro.app.ui.components.flap.ErrorState
import com.reelometro.app.ui.components.flap.FlapLoader
import com.reelometro.app.AppLinks
import com.reelometro.app.ui.icons.ReelIcon
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.components.pressable
import com.reelometro.app.ui.onboarding.InlineNotice
import com.reelometro.app.ui.onboarding.goalStep
import com.reelometro.app.ui.onboarding.revealIn
import com.reelometro.app.ui.onboarding.revealOut
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

/** Acciones de Ajustes (agrupadas para que la firma de la pantalla no crezca sin fin). */
class SettingsActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onGoalChange: (Int) -> Unit,
    val onAddGoal: () -> Unit,
    val onRemoveGoal: () -> Unit,
    val onNotifyDaily: (Boolean) -> Unit,
    val onNotifyMonthly: (Boolean) -> Unit,
    val onNotifyTime: (hour: Int, minute: Int) -> Unit,
    val onRequestNotifications: () -> Unit,
    val onOpenNotificationSettings: () -> Unit,
    val onPlatform: (Platform, Boolean) -> Unit,
    val onTheme: (ThemeMode) -> Unit,
    val onExport: () -> Unit,
    val onDeleteAll: () -> Unit,
    val onOpenServiceSetup: () -> Unit,
    val onOpenAccessibility: () -> Unit,
    val onVersionTap: () -> Unit,
    val onOpenLicenses: () -> Unit,
    val onReplayOnboarding: () -> Unit,
    val onOpenCalibration: () -> Unit,
    val onSeedSampleData: () -> Unit,
    /** Modo pasaporte (01). */
    val onPassport: (Boolean) -> Unit = {},
    /** Aviso en directo al pasar el objetivo (02). */
    val onNotifyGoalLive: (Boolean) -> Unit = {},
    /** VER EL RECORRIDO (08). */
    val onReplayTour: () -> Unit = {},
    /** «A quién envías» en Instagram y TikTok (03). */
    val onRecordRecipients: (Boolean) -> Unit = {},
    /** «Envío rápido» en Instagram y TikTok (03). */
    val onQuickSend: (Boolean) -> Unit = {},
    /** Pestaña INSTAGRAM / TIKTOK del envío rápido (1.10.0). */
    val onQuickSendApp: (Platform) -> Unit = {},
    /** FIJAR / FIJADO de una persona o un grupo del envío rápido en esa app (03). */
    val onQuickSendTarget: (Platform, String) -> Unit = { _, _ -> },
    /** RECOLOCAR: las burbujas del envío rápido de esa app vuelven a su sitio (03). */
    val onQuickSendRelocate: (Platform) -> Unit = {},
    /** ELEGIR FOTO de un fijado de esa app: abre el selector de fotos de Android (1.4.3). */
    val onQuickSendPickPhoto: (Platform, String) -> Unit = { _, _ -> },
    /** QUITAR FOTO de un fijado de esa app: vuelve la recortada de la hoja de compartir (1.4.3). */
    val onQuickSendClearPhoto: (Platform, String) -> Unit = { _, _ -> },
    /** IMPORTAR CSV: abre el selector de archivos (1.6.0). */
    val onImport: () -> Unit = {},
    /** AÑADIR o SUSTITUIR TODO (ya confirmado) en la vista previa de la importación. */
    val onImportConfirm: (ImportMode) -> Unit = {},
    /** CANCELAR en la vista previa. */
    val onImportCancel: () -> Unit = {},
    /** Interruptor «Copia semanal» (05): encenderlo abre el selector de carpetas. */
    val onBackup: (Boolean) -> Unit = {},
    /** COPIAR AHORA (05). */
    val onBackupNow: () -> Unit = {},
    /** CAMBIAR CARPETA (05). */
    val onBackupChangeFolder: () -> Unit = {},
)

/**
 * Ajustes: lista editorial de 8 secciones numeradas (docs/DESIGN.md §9) con un índice arriba que salta a cada
 * una, una barra fija para volver a él y lo menos usado (envío rápido, licencias y herramientas) plegado.
 */
@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(ReelTheme.colors.bg),
    ) {
        when (state) {
            SettingsUiState.Loading -> Column(Modifier.fillMaxSize()) {
                ReelTopBar(title = SettingsCopy.TITLE, onBack = actions.onBack, backDescription = A11y.BACK)
                FlapLoader(
                    style = ReelTheme.type.displayXL,
                    modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.x8),
                    contentDescription = StateCopy.LOADING_DESCRIPTION,
                )
            }
            SettingsUiState.Error -> Column(Modifier.fillMaxSize()) {
                ReelTopBar(title = SettingsCopy.TITLE, onBack = actions.onBack, backDescription = A11y.BACK)
                val seed = remember { Variants.daySeed(LocalDate.now()) }
                ErrorState(
                    message = StateCopy.error(seed),
                    modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.x8),
                    action = { ReelButton(StateCopy.RETRY, onClick = actions.onRetry, style = ReelButtonStyle.Secondary) },
                )
            }
            is SettingsUiState.Content -> SettingsList(state.settings, actions)
        }
        StatusBarScrim()
    }
}

/** Franja del color de fondo bajo la barra de estado: la lista pasa por debajo sin chocar con el reloj. */
@Composable
internal fun StatusBarScrim(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(ReelTheme.colors.bg),
    )
}

@Composable
private fun SettingsList(s: SettingsState, actions: SettingsActions) {
    var timeDialog by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    var replaceDialog by rememberSaveable { mutableStateOf(false) }
    var licensesDialog by rememberSaveable { mutableStateOf(false) }
    var disclosureOpen by rememberSaveable { mutableStateOf(false) }
    var quickSendOpen by rememberSaveable { mutableStateOf(false) }
    var toolsOpen by rememberSaveable { mutableStateOf(false) }
    // La sección donde estabas al volver con ÍNDICE (se marca en el índice).
    var marked by rememberSaveable { mutableStateOf<SettingsPart?>(null) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + Space.x16

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduced = ReelTheme.reducedMotion
    val density = LocalDensity.current
    // Bajo la barra fija (barra de estado + una fila táctil + filete): ahí queda la cabecera de la sección al saltar.
    val barBottomPx = WindowInsets.statusBars.getTop(density) + with(density) { (Space.touch + Space.hairline).roundToPx() }
    val sectionTopPx = with(density) { Space.section.roundToPx() }
    val gapPx = with(density) { Space.x4.roundToPx() }
    val showBar by remember { derivedStateOf { SettingsLayout.showsBar(listState.firstVisibleItemIndex) } }
    val current by remember(barBottomPx) {
        derivedStateOf {
            val info = listState.layoutInfo
            SettingsLayout.currentPart(
                info.visibleItemsInfo.map { it.index to it.offset },
                linePx = SettingsLayout.lineFor(
                    barLinePx = barBottomPx + gapPx - sectionTopPx,
                    viewportPx = info.viewportEndOffset,
                    atEnd = !listState.canScrollForward,
                ),
            )
        }
    }
    fun scrollTo(item: Int, offset: Int) {
        scope.launch {
            if (reduced) listState.scrollToItem(item, offset) else listState.animateScrollToItem(item, offset)
        }
    }
    // La cabecera de la sección, justo debajo de la barra fija (el elemento empieza con su margen de sección).
    val jump: (SettingsPart) -> Unit = { part -> scrollTo(SettingsLayout.itemOf(part), sectionTopPx - barBottomPx - gapPx) }

    // Al desbloquear la calibración (7 toques en la versión), se despliega el bloque donde está su entrada.
    val unlocked = s.calibrationUnlocked
    var wasUnlocked by remember { mutableStateOf(unlocked) }
    LaunchedEffect(unlocked) {
        if (unlocked && !wasUnlocked) toolsOpen = true
        wasUnlocked = unlocked
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = bottom)) {
        item(key = "top") {
            ReelTopBar(title = SettingsCopy.TITLE, onBack = actions.onBack, backDescription = A11y.BACK)
        }
        item(key = "index") {
            SettingsIndex(
                marked = marked,
                busy = s.busy,
                onJump = jump,
                // Exportar e importar desde arriba: la acción y, para ver el resultado, a TUS DATOS.
                onExport = {
                    actions.onExport()
                    jump(SettingsPart.DATA)
                },
                onImport = {
                    actions.onImport()
                    jump(SettingsPart.DATA)
                },
            )
        }
        item(key = SettingsPart.GOAL.key) { Section(SettingsCopy.GOAL) { GoalSection(s, actions) } }
        item(key = SettingsPart.NOTIFICATIONS.key) {
            Section(SettingsCopy.NOTIFICATIONS) { NotificationsSection(s, actions, onPickTime = { timeDialog = true }) }
        }
        item(key = SettingsPart.APPS.key) {
            Section(SettingsCopy.APPS) {
                AppsSection(s, actions, quickSendOpen = quickSendOpen, onToggleQuickSend = { quickSendOpen = !quickSendOpen })
            }
        }
        item(key = SettingsPart.THEME.key) {
            Section(SettingsCopy.THEME) {
                SegmentedTabs(
                    options = THEME_OPTIONS,
                    selectedIndex = THEME_MODES.indexOf(s.themeMode).coerceAtLeast(0),
                    onSelect = { actions.onTheme(THEME_MODES[it]) },
                )
                LanguageLine()
            }
        }
        item(key = SettingsPart.DATA.key) { Section(SettingsCopy.DATA) { DataSection(s, actions, onDelete = { deleteDialog = true }) } }
        item(key = SettingsPart.SERVICE.key) {
            Section(SettingsCopy.SERVICE) {
                ServiceSection(s, actions, disclosureOpen = disclosureOpen, onToggleDisclosure = { disclosureOpen = !disclosureOpen })
            }
        }
        item(key = SettingsPart.PRIVACY.key) {
            Section(SettingsCopy.PRIVACY) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                    SettingsCopy.PRIVACY_PARAGRAPHS.forEach {
                        Text(it, style = ReelTheme.type.body, color = ReelTheme.colors.text)
                    }
                }
            }
        }
        item(key = SettingsPart.ABOUT.key) {
            Section(SettingsCopy.ABOUT) {
                AboutSection(
                    s,
                    onVersionTap = {
                        // Ya desbloqueada, otro toque en la versión enseña dónde está la calibración.
                        if (s.calibrationUnlocked) toolsOpen = true
                        actions.onVersionTap()
                    },
                    actions = actions,
                )
            }
        }
        item(key = "tools") {
            ToolsSection(
                s,
                actions,
                open = toolsOpen,
                onToggle = { toolsOpen = !toolsOpen },
                onLicenses = {
                    actions.onOpenLicenses()
                    licensesDialog = true
                },
            )
        }
    }

    SettingsJumpBar(
        visible = showBar,
        current = current,
        onIndex = {
            marked = current
            scrollTo(SettingsLayout.TOP_ITEM, 0)
        },
    )

    NotifyTimeDialog(
        visible = timeDialog,
        hour = s.notifyHour,
        minute = s.notifyMinute,
        onDismiss = { timeDialog = false },
        onConfirm = { h, m ->
            timeDialog = false
            actions.onNotifyTime(h, m)
        },
    )
    ReelDialog(
        visible = deleteDialog,
        onDismiss = { deleteDialog = false },
        kicker = SettingsCopy.dialogKicker(SettingsCopy.DATA),
        title = SettingsCopy.DELETE_DIALOG_TITLE,
        text = SettingsCopy.deleteDialogBody(s.totals.count.toLong(), s.totals.totalMs, s.backupOn),
        confirmLabel = SettingsCopy.DELETE_CONFIRM,
        dismissLabel = SettingsCopy.DELETE_CANCEL,
        destructive = true,
        onConfirm = {
            deleteDialog = false
            actions.onDeleteAll()
        },
    )
    ImportDialogs(
        s = s,
        actions = actions,
        replaceDialog = replaceDialog,
        onReplaceDialog = { replaceDialog = it },
    )
    LicensesDialog(visible = licensesDialog, licenses = s.licenses, onDismiss = { licensesDialog = false })
}

/** Sección numerada: cabecera `01  OBJETIVO ─────`, la descripción y el contenido. */
@Composable
private fun Section(section: SettingsSection, first: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .padding(top = if (first) Space.x4 else Space.section),
    ) {
        SectionHeader(index = section.index.toIntOrNull(), title = section.title)
        Spacer(Modifier.height(Space.x3))
        Text(section.description, style = ReelTheme.type.bodySmall, color = ReelTheme.colors.textMuted)
        Spacer(Modifier.height(Space.x3))
        content()
    }
}

/** Resultado de una acción, leído por TalkBack al aparecer. */
@Composable
private fun Feedback(text: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = text != null,
        enter = revealIn(ReelTheme.reducedMotion),
        exit = revealOut(ReelTheme.reducedMotion),
        modifier = modifier,
    ) {
        Text(
            text = text.orEmpty(),
            style = ReelTheme.type.bodySmall,
            color = ReelTheme.colors.text,
            modifier = Modifier
                .padding(top = Space.x2)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

// ====================================================================== 04 tema: fila «Idioma»

/**
 * Fila «Idioma» bajo el tema (solo Android 13+, donde cada app puede tener su idioma): a la derecha,
 * el idioma de los textos en su propio idioma; al tocarla, un diálogo de la app para elegirlo (la
 * pantalla de Android mezcla idiomas de otras apps y del teclado, ver [AppLanguage]).
 */
@Composable
private fun LanguageLine() {
    if (!AppLanguage.available) return
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    Spacer(Modifier.height(Space.x4))
    ReceiptLine(
        label = SettingsCopy.languageLabel(),
        value = SettingsCopy.languageValue(),
        onClick = { open = true },
        onClickLabel = SettingsCopy.languageOpen(),
    )
    Text(SettingsCopy.languageHint(), style = ReelTheme.type.bodySmall, color = ReelTheme.colors.textMuted)

    val options = remember(open) { if (open) AppLanguage.options(context, SettingsCopy.languageSystem()) else emptyList() }
    val current = remember(open) { if (open) AppLanguage.selectedTag(context) else null }
    var picked by remember(open) { mutableStateOf(current) }
    ReelDialog(
        visible = open,
        onDismiss = { open = false },
        kicker = SettingsCopy.dialogKicker(SettingsCopy.THEME),
        title = SettingsCopy.languageDialogTitle(),
        confirmLabel = Buttons.DONE,
        dismissLabel = Buttons.CANCEL,
        onConfirm = {
            open = false
            if (picked != current) AppLanguage.set(context, picked)
        },
    ) {
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEach { option ->
                LanguageOptionRow(option.label, selected = option.tag == picked, onSelect = { picked = option.tag })
            }
        }
    }
}

/** Una opción del diálogo de idioma: el nombre y, si es la elegida, la marca en el acento. */
@Composable
private fun LanguageOptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    val colors = ReelTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touch)
            .pressable(onClick = onSelect, role = Role.RadioButton)
            .semantics { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = ReelTheme.type.body,
            color = if (selected) colors.text else colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        if (selected) ReelIcon(ReelIcons.Check, contentDescription = null, tint = colors.signal)
    }
}

// ====================================================================== 01 objetivo

@Composable
private fun GoalSection(s: SettingsState, actions: SettingsActions) {
    val goal = s.goal
    if (goal != null) {
        ReceiptLine(SettingsCopy.GOAL_LINE) {
            Stepper(
                value = goal,
                onValueChange = actions.onGoalChange,
                range = AppSettings.MIN_GOAL..AppSettings.MAX_GOAL,
                step = goalStep(goal),
                decreaseDescription = SettingsCopy.GOAL_DECREASE,
                increaseDescription = SettingsCopy.GOAL_INCREASE,
            )
        }
        Text(
            SettingsCopy.goalRange(AppSettings.MIN_GOAL, AppSettings.MAX_GOAL),
            style = ReelTheme.type.dataS,
            color = ReelTheme.colors.textMuted,
        )
        ReelButton(SettingsCopy.GOAL_REMOVE, onClick = actions.onRemoveGoal, style = ReelButtonStyle.Ghost)
    } else {
        ReceiptLine(SettingsCopy.GOAL_LINE, SettingsCopy.goalValue(null), valueColor = ReelTheme.colors.textMuted)
        ReelButton(SettingsCopy.GOAL_SET, onClick = actions.onAddGoal, style = ReelButtonStyle.Ghost)
    }
    PassportLine(s, actions)
}

/** Modo pasaporte: el juego premia quedarse por debajo, así que va con el objetivo. */
@Composable
private fun PassportLine(s: SettingsState, actions: SettingsActions) {
    Spacer(Modifier.height(Space.x4))
    TicketDivider(style = TicketDividerStyle.Dashed)
    Spacer(Modifier.height(Space.x2))
    SwitchLine(SettingsCopy.PASSPORT_LABEL, checked = s.passportEnabled, onCheckedChange = actions.onPassport)
    Text(SettingsCopy.PASSPORT_DESCRIPTION, style = ReelTheme.type.bodySmall, color = ReelTheme.colors.textMuted)
}

// ====================================================================== 02 avisos

@Composable
private fun NotificationsSection(s: SettingsState, actions: SettingsActions, onPickTime: () -> Unit) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    SwitchLine(SettingsCopy.DAILY_LABEL, checked = s.notifyDaily, onCheckedChange = actions.onNotifyDaily)
    ReceiptLine(
        label = SettingsCopy.TIME_LABEL,
        value = Format.time(s.notifyHour, s.notifyMinute),
        valueColor = if (s.notifyDaily) colors.text else colors.textMuted,
        onClick = onPickTime,
    )
    Text(SettingsCopy.dailyDescription(s.notifyHour, s.notifyMinute), style = type.bodySmall, color = colors.textMuted)
    Spacer(Modifier.height(Space.x3))
    SwitchLine(SettingsCopy.MONTHLY_LABEL, checked = s.notifyMonthly, onCheckedChange = actions.onNotifyMonthly)
    Text(SettingsCopy.MONTHLY_DESCRIPTION, style = type.bodySmall, color = colors.textMuted)
    Spacer(Modifier.height(Space.x3))
    // Sin objetivo, deshabilitado y con la explicación debajo.
    SwitchLine(
        SettingsCopy.GOAL_LIVE_LABEL,
        checked = s.notifyGoalLive && s.goalLiveAvailable,
        onCheckedChange = actions.onNotifyGoalLive,
        enabled = s.goalLiveAvailable,
    )
    Text(SettingsCopy.goalLiveDescription(s.goal), style = type.bodySmall, color = colors.textMuted)
    AnimatedVisibility(
        visible = !s.notificationsAllowed && s.anyNotificationOn,
        enter = revealIn(ReelTheme.reducedMotion),
        exit = revealOut(ReelTheme.reducedMotion),
    ) {
        InlineNotice(
            text = if (s.canRequestNotifications) SettingsCopy.NOTIFICATIONS_ASK else SettingsCopy.NOTIFICATIONS_BLOCKED,
            actionLabel = if (s.canRequestNotifications) SettingsCopy.NOTIFICATIONS_ALLOW else SettingsCopy.NOTIFICATIONS_OPEN,
            onAction = if (s.canRequestNotifications) actions.onRequestNotifications else actions.onOpenNotificationSettings,
            actionIcon = if (s.canRequestNotifications) ReelIcons.Bell else ReelIcons.External,
            modifier = Modifier.padding(top = Space.x5),
        )
    }
}

// ====================================================================== 03 apps

@Composable
private fun AppsSection(s: SettingsState, actions: SettingsActions, quickSendOpen: Boolean, onToggleQuickSend: () -> Unit) {
    s.apps.forEach { app -> AppSwitchLine(app, onToggle = { actions.onPlatform(app.platform, it) }) }
    AnimatedVisibility(
        visible = s.allAppsOff,
        enter = revealIn(ReelTheme.reducedMotion),
        exit = revealOut(ReelTheme.reducedMotion),
    ) {
        Text(
            SettingsCopy.ALL_APPS_OFF,
            style = ReelTheme.type.bodySmall,
            color = ReelTheme.colors.signalText,
            modifier = Modifier.padding(top = Space.x2),
        )
    }
    RecipientsLine(s, actions)
    QuickSendSection(s, actions, open = quickSendOpen, onToggle = onToggleQuickSend)
}

/** «A quién envías» (Instagram y TikTok): va con las apps porque decide qué se lee de ellas. */
@Composable
private fun RecipientsLine(s: SettingsState, actions: SettingsActions) {
    Spacer(Modifier.height(Space.x4))
    TicketDivider(style = TicketDividerStyle.Dashed)
    Spacer(Modifier.height(Space.x2))
    SwitchLine(SettingsCopy.RECIPIENTS_LABEL, checked = s.recordRecipients, onCheckedChange = actions.onRecordRecipients)
    Text(SettingsCopy.RECIPIENTS_DESCRIPTION, style = ReelTheme.type.bodySmall, color = ReelTheme.colors.textMuted)
}

/**
 * «Envío rápido» (Instagram y TikTok), debajo de «A quién envías»: el interruptor (uno para las dos apps) y,
 * encendido, una pestaña por app con su lista para fijar hasta [AppSettings.MAX_QUICK_SEND] personas o grupos de tu
 * historial de envíos en esa app, sus fotos y su RECOLOCAR (1.10.0). Plegado por defecto tras
 * `ENVÍO RÁPIDO · 2 FIJADOS ▸` (los de las dos apps): es lo más largo de Ajustes y casi nunca se toca.
 */
@Composable
private fun QuickSendSection(s: SettingsState, actions: SettingsActions, open: Boolean, onToggle: () -> Unit) {
    Spacer(Modifier.height(Space.x4))
    TicketDivider(style = TicketDividerStyle.Dashed)
    FoldLine(
        text = SettingsCopy.quickSendFold(s.quickSendEnabled && s.quickSendAvailable, s.quickSendPinnedTotal),
        expanded = open,
        onToggle = onToggle,
    )
    AnimatedVisibility(
        visible = open,
        enter = revealIn(ReelTheme.reducedMotion),
        exit = revealOut(ReelTheme.reducedMotion),
    ) {
        Column(Modifier.fillMaxWidth()) { QuickSendContent(s, actions) }
    }
}

@Composable
private fun QuickSendContent(s: SettingsState, actions: SettingsActions) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    // Sin «A quién envías», deshabilitado y con la explicación debajo (como el aviso en directo sin objetivo).
    SwitchLine(
        SettingsCopy.QUICK_SEND_LABEL,
        checked = s.quickSendEnabled && s.quickSendAvailable,
        onCheckedChange = actions.onQuickSend,
        enabled = s.quickSendAvailable,
    )
    Text(
        if (s.quickSendAvailable) SettingsCopy.QUICK_SEND_DESCRIPTION else SettingsCopy.QUICK_SEND_NEEDS_RECIPIENTS,
        style = type.bodySmall,
        color = colors.textMuted,
    )
    if (s.quickSendAvailable) {
        Text(
            SettingsCopy.QUICK_SEND_PAIR_HINT,
            style = type.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.padding(top = Space.x2),
        )
    }
    AnimatedVisibility(
        visible = s.quickSendEnabled && s.quickSendAvailable,
        enter = revealIn(ReelTheme.reducedMotion),
        exit = revealOut(ReelTheme.reducedMotion),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = Space.x3)) {
            // Cada app tiene sus fijados y su sitio: primero la pestaña y, debajo, todo lo de esa app.
            Text(SettingsCopy.QUICK_SEND_APPS_HINT, style = type.bodySmall, color = colors.textMuted)
            Spacer(Modifier.height(Space.x1))
            SegmentedTabs(
                options = s.quickSendApps.map { it.appName },
                selectedIndex = s.quickSendApps.indexOf(s.quickSendApp).coerceAtLeast(0),
                onSelect = { actions.onQuickSendApp(s.quickSendApps[it]) },
                descriptions = s.quickSendApps.map { SettingsCopy.quickSendAppDescription(it.appName) },
            )
            Spacer(Modifier.height(Space.x3))
            QuickSendAppContent(s, actions)
        }
    }
}

/**
 * Lo de la pestaña elegida ([SettingsState.quickSendApp]): su lista (o el vacío de esa app), «ya tienes 3» y
 * RECOLOCAR de sus burbujas. Cada texto dice de qué app es («A quién envías más en TikTok»). Al cambiar de pestaña,
 * el alto cambia con un muelle.
 */
@Composable
private fun QuickSendAppContent(s: SettingsState, actions: SettingsActions) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val app = s.quickSendApp
    Column(Modifier.fillMaxWidth().animateContentSize(Motion.orSnap(ReelTheme.reducedMotion, Motion.standard()))) {
        if (s.quickSendLines.isEmpty()) {
            Text(SettingsCopy.quickSendEmpty(app.appName), style = type.bodySmall, color = colors.text)
        } else {
            Label(SettingsCopy.quickSendListLabel(app.appName))
            Spacer(Modifier.height(Space.x1))
            s.quickSendLines.forEach { line ->
                key(line.platform, line.name) {
                    QuickSendRow(
                        line,
                        enabled = line.pinned || !s.quickSendFull,
                        onToggle = { actions.onQuickSendTarget(line.platform, line.name) },
                        onPickPhoto = { actions.onQuickSendPickPhoto(line.platform, line.name) },
                        onClearPhoto = { actions.onQuickSendClearPhoto(line.platform, line.name) },
                    )
                }
            }
            if (s.quickSendFull) {
                Text(
                    SettingsCopy.quickSendFull(AppSettings.MAX_QUICK_SEND),
                    style = type.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = Space.x2),
                )
            }
        }
        Spacer(Modifier.height(Space.x3))
        ReelButton(
            SettingsCopy.QUICK_SEND_RELOCATE,
            onClick = { actions.onQuickSendRelocate(app) },
            style = ReelButtonStyle.Ghost,
            enabled = s.quickSendMoved,
        )
        Text(
            s.quickSendMessage ?: SettingsCopy.quickSendRelocateHint(app.appName),
            style = type.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * La burbuja de una fila, **igual que sobre los reels**: el mismo dibujo ([GlassBubbleArt]) y el mismo tamaño
 * (círculo de 42 dp en 48 dp), con la foto de perfil ([photo]: la elegida de la galería o la recortada de la hoja de
 * compartir) o las iniciales. El círculo y el cristal son las excepciones de las burbujas en `docs/DESIGN.md`.
 * Decorativa: la fila ya dice a TalkBack el nombre y si está fijado.
 */
@Composable
private fun QuickSendAvatar(line: QuickSendLine, photo: Bitmap?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val art = remember { GlassBubbleArt(GlassBubbleArt.font(context)) }
    Canvas(modifier.size(Space.x12)) {
        val radius = GlassBubbleArt.CIRCLE_DP / GlassBubbleArt.TOUCH_DP * size.minDimension / 2f
        drawIntoCanvas { art.draw(it.nativeCanvas, center.x, center.y, radius, photo, line.initials) }
    }
}

/** La foto de un fijado en Ajustes y si es la elegida de la galería (para enseñar QUITAR FOTO). */
private class RowPhoto(val bitmap: Bitmap?, val chosen: Boolean)

/**
 * `Los del piso ········ FIJADO`: toda la fila fija o quita; con el cupo lleno, las demás quedan apagadas. Fijado,
 * debajo lleva ELEGIR FOTO (de la galería) y, si tiene una elegida, QUITAR FOTO (1.4.3).
 */
@Composable
private fun QuickSendRow(
    line: QuickSendLine,
    enabled: Boolean,
    onToggle: () -> Unit,
    onPickPhoto: () -> Unit,
    onClearPhoto: () -> Unit,
) {
    val context = LocalContext.current
    val version by AvatarStore.changes.collectAsState()
    val photo by produceState(initialValue = RowPhoto(null, false), line.platform, line.name, line.pinned, version) {
        value = if (!line.pinned) {
            RowPhoto(null, false)
        } else {
            withContext(Dispatchers.IO) {
                // La foto de este fijado en su app (la misma persona en Instagram y en TikTok tiene dos).
                val store = AvatarStore(context)
                RowPhoto(store.load(line.name, line.platform), store.hasChosen(line.name, line.platform))
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        QuickSendPinLine(line, photo.bitmap, enabled, onToggle)
        if (line.pinned) {
            // Alineado con el nombre: debajo de la burbuja no va nada.
            Row(Modifier.padding(start = Space.x12 + Space.x3), verticalAlignment = Alignment.CenterVertically) {
                ReelButton(
                    if (photo.chosen) SettingsCopy.QUICK_SEND_PHOTO_CHANGE else SettingsCopy.QUICK_SEND_PHOTO_PICK,
                    onClick = onPickPhoto,
                    style = ReelButtonStyle.Ghost,
                    trailingIcon = null,
                    contentDescription = SettingsCopy.quickSendPhotoPickDescription(line.name, photo.chosen),
                )
                if (photo.chosen) {
                    Spacer(Modifier.width(Space.x5))
                    ReelButton(
                        SettingsCopy.QUICK_SEND_PHOTO_CLEAR,
                        onClick = onClearPhoto,
                        style = ReelButtonStyle.Ghost,
                        trailingIcon = null,
                        contentDescription = SettingsCopy.quickSendPhotoClearDescription(line.name),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSendPinLine(line: QuickSendLine, photo: Bitmap?, enabled: Boolean, onToggle: () -> Unit) {
    val colors = ReelTheme.colors
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touch)
            .toggleable(
                value = line.pinned,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = {
                    haptics.toggle(it)
                    onToggle()
                },
            )
            .semantics(mergeDescendants = true) { contentDescription = SettingsCopy.quickSendPinDescription(line.name, line.pinned) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickSendAvatar(line, photo)
        Spacer(Modifier.width(Space.x3))
        ReceiptLine(label = line.name, modifier = Modifier.weight(1f)) {
            Label(
                if (line.pinned) SettingsCopy.QUICK_SEND_PINNED else SettingsCopy.QUICK_SEND_PIN,
                color = when {
                    line.pinned -> colors.signalText
                    enabled -> colors.text
                    else -> colors.textMuted
                },
                large = true,
                maxLines = 1,
            )
        }
    }
}

/** `● TikTok ······ NO INSTALADA [SÍ ▮]`: toda la fila es el interruptor, como `SwitchLine`. */
@Composable
private fun AppSwitchLine(app: AppLine, onToggle: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    ReceiptLine(
        label = SettingsCopy.appName(app.platform),
        mark = ReelTheme.colors.platform(app.platform),
        modifier = Modifier
            .heightIn(min = Space.touch)
            .toggleable(
                value = app.enabled,
                role = Role.Switch,
                onValueChange = {
                    haptics.toggle(it)
                    onToggle(it)
                },
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!app.installed) {
                Label(SettingsCopy.APP_NOT_INSTALLED, maxLines = 1)
                Spacer(Modifier.width(Space.x3))
            }
            ReelSwitch(checked = app.enabled, onCheckedChange = null)
        }
    }
}

// ====================================================================== 05 tus datos

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataSection(s: SettingsState, actions: SettingsActions, onDelete: () -> Unit) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    ReceiptLine(SettingsCopy.DATA_COUNT_LABEL, Format.integer(s.totals.count))
    ReceiptLine(SettingsCopy.DATA_TIME_LABEL, Format.duration(s.totals.totalMs))
    Spacer(Modifier.height(Space.x4))
    TicketDivider(style = TicketDividerStyle.Dashed)
    Spacer(Modifier.height(Space.x4))
    Text(SettingsCopy.EXPORT_DESCRIPTION, style = type.bodySmall, color = colors.textMuted)
    Spacer(Modifier.height(Space.x2))
    ReelButton(
        SettingsCopy.EXPORT_LABEL,
        onClick = actions.onExport,
        style = ReelButtonStyle.Secondary,
        icon = ReelIcons.Download,
        enabled = !s.busy,
    )
    Spacer(Modifier.height(Space.x5))
    Text(SettingsCopy.IMPORT_DESCRIPTION, style = type.bodySmall, color = colors.textMuted)
    Spacer(Modifier.height(Space.x2))
    ReelButton(
        SettingsCopy.IMPORT_LABEL,
        onClick = actions.onImport,
        style = ReelButtonStyle.Secondary,
        icon = ReelIcons.Upload,
        enabled = !s.busy,
    )
    if (s.importing) {
        Spacer(Modifier.height(Space.x3))
        FlapLoader(style = type.displayL, contentDescription = SettingsCopy.IMPORT_BUSY)
    }
    BackupBlock(s, actions)
    Spacer(Modifier.height(Space.x5))
    Text(SettingsCopy.DELETE_DESCRIPTION, style = type.bodySmall, color = colors.textMuted)
    Spacer(Modifier.height(Space.x2))
    ReelButton(
        SettingsCopy.DELETE_LABEL,
        onClick = onDelete,
        style = ReelButtonStyle.Secondary,
        icon = ReelIcons.Trash,
        enabled = !s.busy,
    )
    Feedback(s.dataMessage)
}

/**
 * Copia semanal del CSV: el interruptor y, encendida, la carpeta (tocarla la cambia), la última copia, el aviso si
 * no se puede escribir y COPIAR AHORA / CAMBIAR CARPETA.
 */
@Composable
private fun BackupBlock(s: SettingsState, actions: SettingsActions) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    Spacer(Modifier.height(Space.x5))
    SwitchLine(SettingsCopy.BACKUP_LABEL, checked = s.backupOn, onCheckedChange = actions.onBackup, enabled = !s.busy)
    Text(SettingsCopy.BACKUP_DESCRIPTION, style = type.bodySmall, color = colors.textMuted)
    AnimatedVisibility(
        visible = s.backupOn,
        enter = revealIn(ReelTheme.reducedMotion),
        exit = revealOut(ReelTheme.reducedMotion),
    ) {
        Column {
            Spacer(Modifier.height(Space.x2))
            ReceiptLine(
                label = SettingsCopy.BACKUP_FOLDER_LABEL,
                value = s.backupFolderName.orEmpty(),
                onClick = if (s.busy) null else actions.onBackupChangeFolder,
                onClickLabel = SettingsCopy.BACKUP_CHANGE_TALKBACK,
            )
            ReceiptLine(
                label = SettingsCopy.BACKUP_LAST_LABEL,
                value = SettingsCopy.backupLast(s.backupLast),
                valueColor = if (s.backupLast == null) colors.textMuted else colors.text,
            )
            s.backupError?.let { error ->
                InlineNotice(
                    text = SettingsCopy.backupError(error),
                    actionLabel = SettingsCopy.BACKUP_CHANGE,
                    onAction = actions.onBackupChangeFolder,
                    actionIcon = ReelIcons.External,
                    modifier = Modifier.padding(top = Space.x3),
                )
            }
            Spacer(Modifier.height(Space.x3))
            ReelButton(
                SettingsCopy.BACKUP_NOW,
                onClick = actions.onBackupNow,
                style = ReelButtonStyle.Secondary,
                icon = ReelIcons.Copy,
                enabled = !s.busy,
            )
            // Con el aviso, CAMBIAR CARPETA ya va en él: no se repite debajo.
            if (s.backupError == null) {
                Spacer(Modifier.height(Space.x2))
                ReelButton(
                    SettingsCopy.BACKUP_CHANGE,
                    onClick = actions.onBackupChangeFolder,
                    style = ReelButtonStyle.Ghost,
                    enabled = !s.busy,
                )
            }
        }
    }
}

/**
 * Vista previa de IMPORTAR CSV (AÑADIR, la opción por defecto; SUSTITUIR TODO dentro, que pide su propia
 * confirmación, igual que BORRAR TODO). Cerrar la confirmación vuelve a la vista previa.
 */
@Composable
private fun ImportDialogs(
    s: SettingsState,
    actions: SettingsActions,
    replaceDialog: Boolean,
    onReplaceDialog: (Boolean) -> Unit,
) {
    val preview = s.importPreview
    ReelDialog(
        visible = preview != null && !replaceDialog,
        onDismiss = actions.onImportCancel,
        kicker = SettingsCopy.dialogKicker(SettingsCopy.DATA),
        title = SettingsCopy.IMPORT_DIALOG_TITLE,
        text = preview?.let(SettingsCopy::importPreview),
        confirmLabel = SettingsCopy.IMPORT_ADD,
        dismissLabel = SettingsCopy.IMPORT_CANCEL,
        confirmIcon = ReelIcons.Upload,
        onConfirm = { actions.onImportConfirm(ImportMode.ADD) },
    ) {
        Text(SettingsCopy.IMPORT_MODES, style = ReelTheme.type.bodySmall, color = ReelTheme.colors.textMuted)
        Spacer(Modifier.height(Space.x3))
        ReelButton(
            SettingsCopy.IMPORT_REPLACE,
            onClick = { onReplaceDialog(true) },
            style = ReelButtonStyle.Secondary,
        )
    }
    ReelDialog(
        visible = preview != null && replaceDialog,
        onDismiss = { onReplaceDialog(false) },
        kicker = SettingsCopy.dialogKicker(SettingsCopy.DATA),
        title = SettingsCopy.IMPORT_REPLACE_TITLE,
        text = SettingsCopy.importReplaceBody(s.totals.count.toLong(), s.totals.totalMs),
        confirmLabel = SettingsCopy.IMPORT_REPLACE_CONFIRM,
        dismissLabel = SettingsCopy.IMPORT_CANCEL,
        destructive = true,
        onConfirm = {
            onReplaceDialog(false)
            actions.onImportConfirm(ImportMode.REPLACE)
        },
    )
}

// ====================================================================== 06 servicio

@Composable
private fun ServiceSection(
    s: SettingsState,
    actions: SettingsActions,
    disclosureOpen: Boolean,
    onToggleDisclosure: () -> Unit,
) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val enabled = s.serviceEnabled
    ReceiptLine(
        label = SettingsCopy.SERVICE_LINE,
        value = SettingsCopy.serviceStatus(enabled),
        valueColor = if (enabled) colors.text else colors.signalText,
    )
    Text(
        text = if (enabled) {
            SettingsCopy.serviceConnected(s.apps.filter { it.enabled }.map { it.platform })
        } else {
            SettingsCopy.SERVICE_DISCONNECTED
        },
        style = type.bodySmall,
        color = colors.textMuted,
    )
    Spacer(Modifier.height(Space.x4))
    if (enabled) {
        ReelButton(
            SettingsCopy.SERVICE_BUTTON,
            onClick = actions.onOpenAccessibility,
            style = ReelButtonStyle.Secondary,
            trailingIcon = ReelIcons.External,
        )
    } else {
        ReelButton(SettingsCopy.SERVICE_CONNECT, onClick = actions.onOpenServiceSetup, trailingIcon = ReelIcons.ArrowRight)
    }
    ReelButton(
        SettingsCopy.SERVICE_DISCLOSURE_LINK,
        onClick = onToggleDisclosure,
        style = ReelButtonStyle.Ghost,
        trailingIcon = if (disclosureOpen) ReelIcons.ChevronUp else ReelIcons.ChevronDown,
    )
    AnimatedVisibility(
        visible = disclosureOpen,
        enter = revealIn(ReelTheme.reducedMotion),
        exit = revealOut(ReelTheme.reducedMotion),
    ) {
        Column(Modifier.fillMaxWidth()) {
            DisclosureCopy.SECTIONS.forEach { section ->
                TicketDivider(style = TicketDividerStyle.Dashed)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.x3)
                        .semantics(mergeDescendants = true) {},
                ) {
                    Label(section.label, color = colors.text)
                    Spacer(Modifier.height(Space.x2))
                    Text(section.text, style = type.bodySmall, color = colors.textMuted)
                }
            }
            TicketDivider(style = TicketDividerStyle.Dashed)
        }
    }
}

// ====================================================================== 08 acerca de

@Composable
private fun AboutSection(s: SettingsState, onVersionTap: () -> Unit, actions: SettingsActions) {
    ReceiptLine(SettingsCopy.VERSION_LINE, s.versionName, onClick = onVersionTap)
    Feedback(s.aboutMessage, Modifier.padding(bottom = Space.x2))
    Spacer(Modifier.height(Space.x3))
    ReelButton(SettingsCopy.REPLAY_ONBOARDING, onClick = actions.onReplayOnboarding, style = ReelButtonStyle.Ghost)
    ReelButton(SettingsCopy.REPLAY_TOUR, onClick = actions.onReplayTour, style = ReelButtonStyle.Ghost)
    if (AppLinks.PROJECT_URL.isNotBlank()) ProjectBlock(AppLinks.PROJECT_URL)
    Spacer(Modifier.height(Space.x8))
    Text(SettingsCopy.SIGNATURE, style = ReelTheme.type.quoteSmall, color = ReelTheme.colors.textMuted)
}

/**
 * Al final y plegado (`LICENCIAS Y HERRAMIENTAS ▸`): las licencias de las fuentes, la calibración (si está
 * desbloqueada) y, en la versión de pruebas, la depuración.
 */
@Composable
private fun ToolsSection(
    s: SettingsState,
    actions: SettingsActions,
    open: Boolean,
    onToggle: () -> Unit,
    onLicenses: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .padding(top = Space.section),
    ) {
        TicketDivider(style = TicketDividerStyle.Dashed)
        FoldLine(SettingsCopy.TOOLS_FOLD, expanded = open, onToggle = onToggle)
        AnimatedVisibility(
            visible = open,
            enter = revealIn(ReelTheme.reducedMotion),
            exit = revealOut(ReelTheme.reducedMotion),
        ) {
            Column(Modifier.fillMaxWidth()) {
                ReceiptLine(SettingsCopy.FONTS_LINE, SettingsCopy.FONTS_VALUE, onClick = onLicenses)
                AnimatedVisibility(
                    visible = s.calibrationUnlocked,
                    enter = revealIn(ReelTheme.reducedMotion),
                    exit = revealOut(ReelTheme.reducedMotion),
                ) {
                    ReelButton(
                        SettingsCopy.CALIBRATION_ENTRY,
                        onClick = actions.onOpenCalibration,
                        style = ReelButtonStyle.Ghost,
                        icon = ReelIcons.Bug,
                    )
                }
                if (s.debugAvailable) DebugSection(s, actions)
            }
        }
    }
}

/** El proyecto en GitHub (donde está el botón de donaciones): se abre en el navegador; la app sigue sin INTERNET. */
@Composable
private fun ProjectBlock(url: String) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    Spacer(Modifier.height(Space.x6))
    Text(SettingsCopy.PROJECT_TEXT, style = ReelTheme.type.body, color = ReelTheme.colors.text)
    Spacer(Modifier.height(Space.x3))
    ReelButton(
        SettingsCopy.PROJECT_BUTTON,
        onClick = {
            failed = !AppLinks.open(context, url)
        },
        style = ReelButtonStyle.Secondary,
        icon = ReelIcons.External,
    )
    Spacer(Modifier.height(Space.x2))
    Text(SettingsCopy.PROJECT_NOTE, style = ReelTheme.type.bodySmall, color = ReelTheme.colors.textMuted)
    Feedback(if (failed) SettingsCopy.PROJECT_NO_BROWSER else null, Modifier.padding(top = Space.x2))
}

// ====================================================================== depuración

@Composable
private fun DebugSection(s: SettingsState, actions: SettingsActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.x8),
    ) {
        SectionHeader(index = null, title = SettingsCopy.DEBUG_TITLE)
        Spacer(Modifier.height(Space.x3))
        Text(SettingsCopy.DEBUG_DESCRIPTION, style = ReelTheme.type.bodySmall, color = ReelTheme.colors.textMuted)
        Spacer(Modifier.height(Space.x3))
        ReelButton(
            SettingsCopy.DEBUG_SEED,
            onClick = actions.onSeedSampleData,
            style = ReelButtonStyle.Secondary,
            icon = ReelIcons.Bug,
            enabled = !s.busy,
        )
        Feedback(s.debugMessage)
    }
}

// ====================================================================== diálogos

/** Selector de hora propio (nada de `TimePicker`): la hora en grande y dos selectores. Minutos de 5 en 5. */
@Composable
private fun NotifyTimeDialog(
    visible: Boolean,
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var h by remember(visible, hour) { mutableStateOf(hour.coerceIn(0, 23)) }
    var m by remember(visible, minute) { mutableStateOf((minute / MINUTE_STEP * MINUTE_STEP).coerceIn(0, LAST_MINUTE)) }
    ReelDialog(
        visible = visible,
        onDismiss = onDismiss,
        kicker = SettingsCopy.dialogKicker(SettingsCopy.NOTIFICATIONS),
        title = SettingsCopy.TIME_DIALOG_TITLE,
        confirmLabel = SettingsCopy.TIME_CONFIRM,
        dismissLabel = SettingsCopy.TIME_CANCEL,
        onConfirm = { onConfirm(h, m) },
    ) {
        Text(
            Format.time(h, m),
            style = ReelTheme.type.displayL,
            color = ReelTheme.colors.text,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(Space.x5))
        Label(SettingsCopy.TIME_HOUR)
        Spacer(Modifier.height(Space.x2))
        Stepper(
            value = h,
            onValueChange = { h = it },
            range = 0..23,
            format = ::twoDigits,
            decreaseDescription = SettingsCopy.TIME_HOUR_DOWN,
            increaseDescription = SettingsCopy.TIME_HOUR_UP,
        )
        Spacer(Modifier.height(Space.x4))
        Label(SettingsCopy.TIME_MINUTE)
        Spacer(Modifier.height(Space.x2))
        Stepper(
            value = m,
            onValueChange = { m = it },
            range = 0..LAST_MINUTE,
            step = MINUTE_STEP,
            format = ::twoDigits,
            decreaseDescription = SettingsCopy.TIME_MINUTE_DOWN,
            increaseDescription = SettingsCopy.TIME_MINUTE_UP,
        )
    }
}

/** Licencias OFL de las tres familias (texto completo de `assets/licenses/`). */
@Composable
private fun LicensesDialog(visible: Boolean, licenses: List<License>?, onDismiss: () -> Unit) {
    ReelDialog(
        visible = visible,
        onDismiss = onDismiss,
        kicker = SettingsCopy.LICENSES_LABEL,
        title = SettingsCopy.LICENSES_TITLE,
        text = SettingsCopy.FONTS_LICENSE,
        confirmLabel = SettingsCopy.LICENSES_CLOSE,
        onConfirm = onDismiss,
        dismissLabel = null,
    ) {
        licenses.orEmpty().forEach { license ->
            TicketDivider(style = TicketDividerStyle.Dashed)
            Spacer(Modifier.height(Space.x3))
            Label(license.name, color = ReelTheme.colors.text)
            Spacer(Modifier.height(Space.x2))
            Text(license.text, style = ReelTheme.type.dataS, color = ReelTheme.colors.textMuted)
            Spacer(Modifier.height(Space.x4))
        }
    }
}

private const val MINUTE_STEP = 5
private const val LAST_MINUTE = 55

private fun twoDigits(value: Int): String = String.format(Locale.ROOT, "%02d", value)

private val THEME_MODES = listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT)
private val THEME_OPTIONS: List<String>
    get() = listOf(SettingsCopy.THEME_SYSTEM, SettingsCopy.THEME_DARK, SettingsCopy.THEME_LIGHT)
