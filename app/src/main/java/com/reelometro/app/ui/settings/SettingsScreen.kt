package com.reelometro.app.ui.settings

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.reelometro.app.copy.A11y
import com.reelometro.app.copy.DisclosureCopy
import com.reelometro.app.copy.SettingsCopy
import com.reelometro.app.copy.SettingsSection
import com.reelometro.app.copy.StateCopy
import com.reelometro.app.copy.Variants
import com.reelometro.app.data.Platform
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
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.onboarding.InlineNotice
import com.reelometro.app.ui.onboarding.goalStep
import com.reelometro.app.ui.onboarding.revealIn
import com.reelometro.app.ui.onboarding.revealOut
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics
import kotlinx.coroutines.Dispatchers
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
    /** «A quién envías» en Instagram (03). */
    val onRecordRecipients: (Boolean) -> Unit = {},
    /** «Envío rápido» en Instagram (03). */
    val onQuickSend: (Boolean) -> Unit = {},
    /** FIJAR / FIJADO de una persona o un grupo del envío rápido (03). */
    val onQuickSendTarget: (String) -> Unit = {},
    /** RECOLOCAR: las burbujas del envío rápido vuelven a su sitio (03). */
    val onQuickSendRelocate: () -> Unit = {},
    /** ELEGIR FOTO de un fijado: abre el selector de fotos de Android (1.4.3). */
    val onQuickSendPickPhoto: (String) -> Unit = {},
    /** QUITAR FOTO de un fijado: vuelve la recortada de la hoja de compartir (1.4.3). */
    val onQuickSendClearPhoto: (String) -> Unit = {},
)

/** Ajustes: lista editorial de 8 secciones numeradas (docs/DESIGN.md §9). */
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
    var licensesDialog by rememberSaveable { mutableStateOf(false) }
    var disclosureOpen by rememberSaveable { mutableStateOf(false) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + Space.x16

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom)) {
        item(key = "top") {
            ReelTopBar(title = SettingsCopy.TITLE, onBack = actions.onBack, backDescription = A11y.BACK)
        }
        item(key = "goal") { Section(SettingsCopy.GOAL, first = true) { GoalSection(s, actions) } }
        item(key = "notifications") {
            Section(SettingsCopy.NOTIFICATIONS) { NotificationsSection(s, actions, onPickTime = { timeDialog = true }) }
        }
        item(key = "apps") { Section(SettingsCopy.APPS) { AppsSection(s, actions) } }
        item(key = "theme") {
            Section(SettingsCopy.THEME) {
                SegmentedTabs(
                    options = THEME_OPTIONS,
                    selectedIndex = THEME_MODES.indexOf(s.themeMode).coerceAtLeast(0),
                    onSelect = { actions.onTheme(THEME_MODES[it]) },
                )
            }
        }
        item(key = "data") { Section(SettingsCopy.DATA) { DataSection(s, actions, onDelete = { deleteDialog = true }) } }
        item(key = "service") {
            Section(SettingsCopy.SERVICE) {
                ServiceSection(s, actions, disclosureOpen = disclosureOpen, onToggleDisclosure = { disclosureOpen = !disclosureOpen })
            }
        }
        item(key = "privacy") {
            Section(SettingsCopy.PRIVACY) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                    SettingsCopy.PRIVACY_PARAGRAPHS.forEach {
                        Text(it, style = ReelTheme.type.body, color = ReelTheme.colors.text)
                    }
                }
            }
        }
        item(key = "about") {
            Section(SettingsCopy.ABOUT) {
                AboutSection(
                    s,
                    actions,
                    onLicenses = {
                        actions.onOpenLicenses()
                        licensesDialog = true
                    },
                )
            }
        }
        if (s.debugAvailable) {
            item(key = "debug") { DebugSection(s, actions) }
        }
    }

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
        kicker = "${SettingsCopy.DATA.index} · ${SettingsCopy.DATA.title}",
        title = SettingsCopy.DELETE_DIALOG_TITLE,
        text = SettingsCopy.deleteDialogBody(s.totals.count.toLong(), s.totals.totalMs),
        confirmLabel = SettingsCopy.DELETE_CONFIRM,
        dismissLabel = SettingsCopy.DELETE_CANCEL,
        destructive = true,
        onConfirm = {
            deleteDialog = false
            actions.onDeleteAll()
        },
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
private fun AppsSection(s: SettingsState, actions: SettingsActions) {
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
    QuickSendSection(s, actions)
}

/** «A quién envías» (solo Instagram): va con las apps porque decide qué se lee de una de ellas. */
@Composable
private fun RecipientsLine(s: SettingsState, actions: SettingsActions) {
    Spacer(Modifier.height(Space.x4))
    TicketDivider(style = TicketDividerStyle.Dashed)
    Spacer(Modifier.height(Space.x2))
    SwitchLine(SettingsCopy.RECIPIENTS_LABEL, checked = s.recordRecipients, onCheckedChange = actions.onRecordRecipients)
    Text(SettingsCopy.RECIPIENTS_DESCRIPTION, style = ReelTheme.type.bodySmall, color = ReelTheme.colors.textMuted)
}

/**
 * «Envío rápido» (solo Instagram), debajo de «A quién envías»: el interruptor y, encendido, la lista para
 * fijar hasta [AppSettings.MAX_QUICK_SEND] personas o grupos de tu historial de envíos.
 */
@Composable
private fun QuickSendSection(s: SettingsState, actions: SettingsActions) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    Spacer(Modifier.height(Space.x4))
    TicketDivider(style = TicketDividerStyle.Dashed)
    Spacer(Modifier.height(Space.x2))
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
            if (s.quickSendLines.isEmpty()) {
                Text(SettingsCopy.QUICK_SEND_EMPTY, style = type.bodySmall, color = colors.text)
            } else {
                Label(SettingsCopy.QUICK_SEND_LIST_LABEL)
                Spacer(Modifier.height(Space.x1))
                s.quickSendLines.forEach { line ->
                    QuickSendRow(
                        line,
                        enabled = line.pinned || !s.quickSendFull,
                        onToggle = { actions.onQuickSendTarget(line.name) },
                        onPickPhoto = { actions.onQuickSendPickPhoto(line.name) },
                        onClearPhoto = { actions.onQuickSendClearPhoto(line.name) },
                    )
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
                onClick = actions.onQuickSendRelocate,
                style = ReelButtonStyle.Ghost,
                enabled = s.quickSendMoved,
            )
            Text(
                s.quickSendMessage ?: SettingsCopy.QUICK_SEND_RELOCATE_HINT,
                style = type.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
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
    val photo by produceState(initialValue = RowPhoto(null, false), line.name, line.pinned, version) {
        value = if (!line.pinned) {
            RowPhoto(null, false)
        } else {
            withContext(Dispatchers.IO) {
                val store = AvatarStore(context)
                RowPhoto(store.load(line.name), store.hasChosen(line.name))
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
private fun AboutSection(s: SettingsState, actions: SettingsActions, onLicenses: () -> Unit) {
    ReceiptLine(SettingsCopy.VERSION_LINE, s.versionName, onClick = actions.onVersionTap)
    Feedback(s.aboutMessage, Modifier.padding(bottom = Space.x2))
    ReceiptLine(SettingsCopy.FONTS_LINE, SettingsCopy.FONTS_VALUE, onClick = onLicenses)
    Spacer(Modifier.height(Space.x3))
    ReelButton(SettingsCopy.REPLAY_ONBOARDING, onClick = actions.onReplayOnboarding, style = ReelButtonStyle.Ghost)
    ReelButton(SettingsCopy.REPLAY_TOUR, onClick = actions.onReplayTour, style = ReelButtonStyle.Ghost)
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
    if (AppLinks.PROJECT_URL.isNotBlank()) ProjectBlock(AppLinks.PROJECT_URL)
    Spacer(Modifier.height(Space.x8))
    Text(SettingsCopy.SIGNATURE, style = ReelTheme.type.quoteSmall, color = ReelTheme.colors.textMuted)
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
            .padding(horizontal = Space.screen)
            .padding(top = Space.section),
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
        kicker = "${SettingsCopy.NOTIFICATIONS.index} · ${SettingsCopy.NOTIFICATIONS.title}",
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
private val THEME_OPTIONS = listOf(SettingsCopy.THEME_SYSTEM, SettingsCopy.THEME_DARK, SettingsCopy.THEME_LIGHT)
