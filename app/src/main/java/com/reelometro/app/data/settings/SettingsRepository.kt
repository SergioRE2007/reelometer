package com.reelometro.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.reelometro.app.data.Platform
import com.reelometro.app.data.backup.BackupError
import com.reelometro.app.data.backup.BackupOutcome
import com.reelometro.app.detection.BubbleLayout
import com.reelometro.app.detection.BubbleSpot
import com.reelometro.app.ui.support.SupportPrompt
import com.reelometro.app.ui.support.SupportPromptState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Preferencias del usuario. Todo vive en el móvil (DataStore), igual que los datos de uso. */
data class AppSettings(
    /** El onboarding se ha completado al menos una vez. */
    val onboardingDone: Boolean = false,
    /** El usuario ha aceptado la divulgación destacada del servicio de accesibilidad (política de Google Play). */
    val disclosureAccepted: Boolean = false,
    /** Objetivo diario en número de vídeos ("como mucho N al día"). `null` = sin objetivo. */
    val dailyGoal: Int? = null,
    val notifyDaily: Boolean = true,
    val notifyMonthly: Boolean = true,
    /** Hora local del aviso diario. */
    val notifyHour: Int = 21,
    val notifyMinute: Int = 30,
    /** Plataformas que se cuentan. Las demás se ignoran por completo en el servicio. */
    val enabledPlatforms: Set<Platform> = Platform.entries.toSet(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Pantalla de calibración desbloqueada (7 toques en la versión, en Ajustes). */
    val calibrationUnlocked: Boolean = false,
    /**
     * Flap, la mascota (modo Flap): su pestaña FLAP y Flap en el widget grande. Apagado, no se ve nada.
     * Se sigue llamando `passportEnabled` (y su clave de DataStore, `passport_enabled`) desde cuando era
     * el Pasaporte: así se respeta lo que cada uno eligió.
     */
    val passportEnabled: Boolean = true,
    /** Aviso en directo, una vez al día, en el momento en que el recuento pasa del objetivo. */
    val notifyGoalLive: Boolean = false,
    /** El recorrido guiado de la primera vez ya se vio o se saltó («VER EL RECORRIDO» lo repite). */
    val tourDone: Boolean = false,
    /**
     * «A quién envías» (Instagram y TikTok): con `true`, el servicio lee en la hoja de compartir el nombre
     * de la persona o el grupo al que mandas un vídeo y lo guarda en el móvil para las estadísticas.
     * Apagado por defecto: la divulgación y la política lo presentan como algo que eliges tú.
     */
    val recordRecipients: Boolean = false,
    /**
     * «Envío rápido» (Instagram y TikTok): con `true` y algún fijado, el servicio pone sobre los vídeos de esa app
     * un botón por cada nombre fijado en ella ([quickSendTargets] en Instagram, [quickSendTargetsTikTok] en TikTok)
     * y, al tocarlo, pulsa por ti «Compartir», ese nombre y «Enviar» (si no lo ve, lo busca en el buscador de la
     * hoja; en TikTok, pulsando antes su lupa). Un único interruptor para las dos apps. Apagado por defecto.
     */
    val quickSendEnabled: Boolean = false,
    /** Personas o grupos fijados para el envío rápido, en el orden en que se fijaron (como mucho [MAX_QUICK_SEND]). */
    val quickSendTargets: List<String> = emptyList(),
    /**
     * Dónde dejó el usuario las burbujas del envío rápido (el centro de la pila, `x` e `y` en fracción de la
     * pantalla, para que valga al girar o al cambiar de móvil). `null` = donde van por defecto (RECOLOCAR vuelve
     * ahí). Lo guardado por la 1.4.0–1.4.1 (borde + altura) se lee convertido ([BubbleLayout.fromLegacy]).
     */
    val quickSendBubble: BubbleSpot? = null,
    /**
     * 1.10.0, envío rápido también en TikTok: sus fijados (los contactos de TikTok no son los de Instagram), en orden y
     * como mucho [MAX_QUICK_SEND]. [quickSendTargets] y [quickSendBubble] siguen siendo los de **Instagram** (las mismas
     * claves de siempre: nada que migrar). Para leer por app, [quickSendTargets] (función) y [quickSendBubble] (función).
     */
    val quickSendTargetsTikTok: List<String> = emptyList(),
    /** Dónde dejó el usuario las burbujas sobre TikTok; `null` = donde van por defecto en TikTok. */
    val quickSendBubbleTikTok: BubbleSpot? = null,
    /**
     * Copia semanal del CSV: la carpeta (URI de `ACTION_OPEN_DOCUMENT_TREE`, con permiso persistente). `null` = apagada.
     */
    val backupFolder: String? = null,
    /** Nombre de esa carpeta para enseñarlo («Download»). */
    val backupFolderName: String? = null,
    /** Última copia escrita (epoch ms). */
    val backupLastMs: Long? = null,
    /** Por qué falló la última copia (se borra con la siguiente que salga bien o al cambiar de carpeta). */
    val backupError: BackupError? = null,
    // Descubrimiento progresivo (pestañas que aparecen con los días; por defecto, todo a la vista).
    val discovery: DiscoveryState = DiscoveryState.ALL_VISIBLE,
) {
    fun isEnabled(platform: Platform): Boolean = platform in enabledPlatforms

    /** Fijados del envío rápido en [platform] (vacío en las apps sin envío rápido, como YouTube). */
    fun quickSendTargets(platform: Platform): List<String> = when (platform) {
        Platform.INSTAGRAM -> quickSendTargets
        Platform.TIKTOK -> quickSendTargetsTikTok
        else -> emptyList()
    }

    /** Dónde dejó las burbujas en [platform] (`null` = por defecto en esa app). */
    fun quickSendBubble(platform: Platform): BubbleSpot? = when (platform) {
        Platform.INSTAGRAM -> quickSendBubble
        Platform.TIKTOK -> quickSendBubbleTikTok
        else -> null
    }

    /** Todos los fijados, por app (para no borrar las fotos de los de otra app al quitar uno). */
    fun allQuickSendTargets(): Map<Platform, List<String>> = QUICK_SEND_PLATFORMS.associateWith { quickSendTargets(it) }

    companion object {
        /** Apps con envío rápido (y con «A quién envías»), en el orden en que salen en Ajustes. */
        val QUICK_SEND_PLATFORMS: List<Platform> = listOf(Platform.INSTAGRAM, Platform.TIKTOK)

        const val MIN_GOAL = 5
        const val MAX_GOAL = 2_000
        val SUGGESTED_GOALS = listOf(30, 60, 100, 150, 200)

        /** Fijados como mucho para el envío rápido (una burbuja por cada uno sobre el reel). */
        const val MAX_QUICK_SEND = 3
    }
}

class SettingsRepository internal constructor(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { context.applicationContext.preferencesDataStoreFile("settings") },
        ),
    )

    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::toSettings)
        .distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    /**
     * Estado del aviso de apoyo (`ui/support/SupportPrompt`). Va aparte de [settings]: apuntar un día de uso
     * no debe recomponer la app entera.
     */
    val supportPrompt: Flow<SupportPromptState> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::toSupportPrompt)
        .distinctUntilChanged()

    /** La app se ha abierto el día [epochDay] (un mismo día cuenta una vez). */
    suspend fun recordUsageDay(epochDay: Long) = editSupport { SupportPrompt.recordUsage(it, epochDay) }

    /** El aviso de apoyo acaba de salir el día [epochDay]. */
    suspend fun markSupportPromptShown(epochDay: Long) = editSupport { SupportPrompt.markShown(it, epochDay) }

    /** Abrió el GitHub o pulsó «No volver a mostrar»: el aviso de apoyo no sale nunca más. */
    suspend fun finishSupportPrompt() = editSupport { SupportPrompt.finish(it) }

    /** Solo para los ganchos de QA: deja el estado del aviso de apoyo tal cual. */
    suspend fun setSupportPrompt(state: SupportPromptState) = editSupport { state }

    /** Lee, cambia y guarda el estado del aviso en una sola transacción. */
    private suspend fun editSupport(change: (SupportPromptState) -> SupportPromptState) = edit { prefs ->
        val next = change(toSupportPrompt(prefs))
        prefs[SUPPORT_USAGE_DAYS] = next.usageDays.coerceAtLeast(0)
        val lastUsage = next.lastUsageDay
        if (lastUsage == null) prefs.remove(SUPPORT_LAST_USAGE_DAY) else prefs[SUPPORT_LAST_USAGE_DAY] = lastUsage
        prefs[SUPPORT_TIMES_SHOWN] = next.timesShown.coerceAtLeast(0)
        val lastShown = next.lastShownDay
        if (lastShown == null) prefs.remove(SUPPORT_LAST_SHOWN_DAY) else prefs[SUPPORT_LAST_SHOWN_DAY] = lastShown
        prefs[SUPPORT_FINISHED] = next.finished
    }

    private fun toSupportPrompt(p: Preferences) = SupportPromptState(
        usageDays = p[SUPPORT_USAGE_DAYS] ?: 0,
        lastUsageDay = p[SUPPORT_LAST_USAGE_DAY],
        timesShown = p[SUPPORT_TIMES_SHOWN] ?: 0,
        lastShownDay = p[SUPPORT_LAST_SHOWN_DAY],
        finished = p[SUPPORT_FINISHED] ?: false,
    )

    suspend fun setOnboardingDone(done: Boolean) = edit { it[ONBOARDING_DONE] = done }

    suspend fun setDisclosureAccepted(accepted: Boolean) = edit { it[DISCLOSURE_ACCEPTED] = accepted }

    /** `null` o un valor <= 0 quita el objetivo. */
    suspend fun setDailyGoal(goal: Int?) = edit {
        if (goal == null || goal <= 0) it.remove(DAILY_GOAL)
        else it[DAILY_GOAL] = goal.coerceIn(AppSettings.MIN_GOAL, AppSettings.MAX_GOAL)
    }

    suspend fun setNotifyDaily(enabled: Boolean) = edit { it[NOTIFY_DAILY] = enabled }

    suspend fun setNotifyMonthly(enabled: Boolean) = edit { it[NOTIFY_MONTHLY] = enabled }

    suspend fun setNotifyTime(hour: Int, minute: Int) = edit {
        it[NOTIFY_HOUR] = hour.coerceIn(0, 23)
        it[NOTIFY_MINUTE] = minute.coerceIn(0, 59)
    }

    suspend fun setPlatformEnabled(platform: Platform, enabled: Boolean) = edit { prefs ->
        val current = prefs[ENABLED_PLATFORMS] ?: Platform.entries.mapTo(mutableSetOf()) { it.name }
        prefs[ENABLED_PLATFORMS] = if (enabled) current + platform.name else current - platform.name
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[THEME_MODE] = mode.name }

    suspend fun setCalibrationUnlocked(unlocked: Boolean) = edit { it[CALIBRATION_UNLOCKED] = unlocked }

    suspend fun setPassportEnabled(enabled: Boolean) = edit { it[PASSPORT_ENABLED] = enabled }

    suspend fun setNotifyGoalLive(enabled: Boolean) = edit { it[NOTIFY_GOAL_LIVE] = enabled }

    suspend fun setTourDone(done: Boolean) = edit { it[TOUR_DONE] = done }

    suspend fun setRecordRecipients(enabled: Boolean) = edit { it[RECORD_RECIPIENTS] = enabled }

    suspend fun setQuickSendEnabled(enabled: Boolean) = edit { it[QUICK_SEND_ENABLED] = enabled }

    /**
     * Fijados del envío rápido, en orden. Se recortan, se quitan vacíos y repetidos y se guardan como
     * mucho [AppSettings.MAX_QUICK_SEND]. Van unidos por `\n` (un conjunto no guardaría el orden).
     */
    suspend fun setQuickSendTargets(targets: List<String>) = setQuickSendTargets(Platform.INSTAGRAM, targets)

    /** Lo mismo en [platform] (Instagram o TikTok; en las demás no hace nada). */
    suspend fun setQuickSendTargets(platform: Platform, targets: List<String>) {
        val key = targetsKey(platform) ?: return
        edit { prefs ->
            val clean = cleanTargets(targets)
            if (clean.isEmpty()) prefs.remove(key) else prefs[key] = clean.joinToString("\n")
        }
    }

    /** Burbujas del envío rápido soltadas en [spot] (la posición de antes, si quedaba, ya no hace falta). */
    suspend fun setQuickSendBubble(spot: BubbleSpot) = setQuickSendBubble(Platform.INSTAGRAM, spot)

    /** Lo mismo sobre [platform]: cada app guarda su posición (1.10.0). */
    suspend fun setQuickSendBubble(platform: Platform, spot: BubbleSpot) {
        val (keyX, keyY) = bubbleKeys(platform) ?: return
        edit {
            val clean = BubbleLayout.sanitize(spot, BubbleLayout.defaultFor(platform))
            it[keyX] = clean.x
            it[keyY] = clean.y
            if (platform == Platform.INSTAGRAM) {
                it.remove(LEGACY_BUBBLE_LEFT)
                it.remove(LEGACY_BUBBLE_Y)
            }
        }
    }

    /** RECOLOCAR: las burbujas vuelven a su sitio por defecto. */
    suspend fun resetQuickSendBubble() = resetQuickSendBubble(Platform.INSTAGRAM)

    /** RECOLOCAR en [platform]: solo las de esa app vuelven a su sitio por defecto. */
    suspend fun resetQuickSendBubble(platform: Platform) {
        val (keyX, keyY) = bubbleKeys(platform) ?: return
        edit {
            it.remove(keyX)
            it.remove(keyY)
            if (platform == Platform.INSTAGRAM) {
                it.remove(LEGACY_BUBBLE_LEFT)
                it.remove(LEGACY_BUBBLE_Y)
            }
        }
    }

    /** Clave de los fijados de [platform]: Instagram, la de siempre; TikTok, una nueva (1.10.0). */
    private fun targetsKey(platform: Platform): Preferences.Key<String>? = when (platform) {
        Platform.INSTAGRAM -> QUICK_SEND_TARGETS
        Platform.TIKTOK -> QUICK_SEND_TARGETS_TIKTOK
        else -> null
    }

    private fun bubbleKeys(platform: Platform): Pair<Preferences.Key<Float>, Preferences.Key<Float>>? = when (platform) {
        Platform.INSTAGRAM -> QUICK_SEND_BUBBLE_X to QUICK_SEND_BUBBLE_CENTER_Y
        Platform.TIKTOK -> QUICK_SEND_BUBBLE_X_TIKTOK to QUICK_SEND_BUBBLE_CENTER_Y_TIKTOK
        else -> null
    }

    /**
     * Copia semanal en [folder] (ya con su permiso persistente), que se ve como [name]; `null` la apaga. Al cambiar
     * de carpeta se olvidan el error y la fecha de la última copia (eran de la otra).
     */
    suspend fun setBackupFolder(folder: String?, name: String?) = edit {
        val previous = it[BACKUP_FOLDER]
        if (folder.isNullOrBlank()) {
            it.remove(BACKUP_FOLDER)
            it.remove(BACKUP_FOLDER_NAME)
        } else {
            it[BACKUP_FOLDER] = folder
            if (name.isNullOrBlank()) it.remove(BACKUP_FOLDER_NAME) else it[BACKUP_FOLDER_NAME] = name
        }
        if (previous != folder) {
            it.remove(BACKUP_LAST_MS)
            it.remove(BACKUP_ERROR)
        }
    }

    /**
     * Apunta el resultado de una copia hecha en [folder] a las [nowMs]: si salió bien, su fecha (y sin error); si
     * falló, el error. Si mientras tanto se ha cambiado o apagado la copia, no apunta nada (era de otra carpeta).
     */
    suspend fun recordBackup(folder: String, outcome: BackupOutcome, nowMs: Long) = edit {
        if (it[BACKUP_FOLDER] != folder) return@edit
        when (outcome) {
            is BackupOutcome.Done -> {
                it[BACKUP_LAST_MS] = nowMs
                it.remove(BACKUP_ERROR)
            }
            is BackupOutcome.Failed -> it[BACKUP_ERROR] = outcome.error.name
            BackupOutcome.Empty -> it.remove(BACKUP_ERROR)
        }
    }

    /**
     * La posición de las burbujas: la nueva (`x`, `y` del centro) o, si solo está la de la 1.4.0–1.4.1 (borde y
     * altura), esa convertida a la equivalente. No se pierde nada: la de antes se borra al guardar la nueva.
     */
    private fun bubbleSpot(p: Preferences): BubbleSpot? {
        val x = p[QUICK_SEND_BUBBLE_X]
        val y = p[QUICK_SEND_BUBBLE_CENTER_Y]
        if (x != null && y != null) return BubbleLayout.sanitize(BubbleSpot(x, y))
        val legacyY = p[LEGACY_BUBBLE_Y]?.takeIf { it.isFinite() } ?: return null
        return BubbleLayout.fromLegacy(left = p[LEGACY_BUBBLE_LEFT] ?: false, centerY = legacyY)
    }

    /** La posición sobre TikTok (1.10.0; no hay formato antiguo que convertir). */
    private fun tikTokBubbleSpot(p: Preferences): BubbleSpot? {
        val x = p[QUICK_SEND_BUBBLE_X_TIKTOK] ?: return null
        val y = p[QUICK_SEND_BUBBLE_CENTER_Y_TIKTOK] ?: return null
        return BubbleLayout.sanitize(BubbleSpot(x, y), BubbleLayout.DEFAULT_TIKTOK)
    }

    /** Vuelve a los valores por defecto (no toca los datos de uso). */
    suspend fun reset() = edit { it.clear() }

    // Descubrimiento progresivo ------------------------------------------------------------

    /**
     * Decide una sola vez si las pestañas se descubren poco a poco (solo en una instalación nueva).
     * Idempotente: MainActivity la llama en cada arranque. Tras [reset], se vuelve a decidir.
     */
    suspend fun decideDiscovery() = edit {
        DiscoveryPrefs.decide(
            it,
            onboardingDone = it[ONBOARDING_DONE] == true,
            tourDone = it[TOUR_DONE] == true,
            disclosureAccepted = it[DISCLOSURE_ACCEPTED] == true,
        )
    }

    /** Lee, cambia y guarda el estado del descubrimiento en una sola transacción (no escribe si no cambia). */
    suspend fun updateDiscovery(change: (DiscoveryState) -> DiscoveryState) = edit { DiscoveryPrefs.update(it, change) }

    /**
     * FIJAR / FIJADO de una fila, en una sola transacción de DataStore (dos toques seguidos no se pisan): quita
     * [name] si estaba o lo añade al final si queda sitio. Con el cupo lleno no hace nada. Devuelve los
     * fijados que quedan (para borrar la foto de quien ya no lo está).
     */
    suspend fun toggleQuickSendTarget(name: String): List<String> = toggleQuickSendTarget(Platform.INSTAGRAM, name)

    /** Lo mismo en la lista de [platform]; devuelve los fijados que quedan **en esa app**. */
    suspend fun toggleQuickSendTarget(platform: Platform, name: String): List<String> {
        val storeKey = targetsKey(platform) ?: return emptyList()
        var result: List<String> = emptyList()
        edit { prefs ->
            val pinned = prefs[storeKey]?.let { cleanTargets(it.split('\n')) }.orEmpty()
            val key = name.replace('\n', ' ').trim()
            val next = when {
                key.isEmpty() -> pinned
                key in pinned -> pinned - key
                pinned.size < AppSettings.MAX_QUICK_SEND -> pinned + key
                else -> pinned
            }
            if (next.isEmpty()) prefs.remove(storeKey) else prefs[storeKey] = next.joinToString("\n")
            result = next
        }
        return result
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    private fun toSettings(p: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            onboardingDone = p[ONBOARDING_DONE] ?: defaults.onboardingDone,
            disclosureAccepted = p[DISCLOSURE_ACCEPTED] ?: defaults.disclosureAccepted,
            dailyGoal = p[DAILY_GOAL]?.takeIf { it > 0 },
            notifyDaily = p[NOTIFY_DAILY] ?: defaults.notifyDaily,
            notifyMonthly = p[NOTIFY_MONTHLY] ?: defaults.notifyMonthly,
            notifyHour = p[NOTIFY_HOUR] ?: defaults.notifyHour,
            notifyMinute = p[NOTIFY_MINUTE] ?: defaults.notifyMinute,
            enabledPlatforms = p[ENABLED_PLATFORMS]
                ?.mapNotNullTo(mutableSetOf()) { Platform.fromName(it) }
                ?: defaults.enabledPlatforms,
            themeMode = p[THEME_MODE]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: defaults.themeMode,
            calibrationUnlocked = p[CALIBRATION_UNLOCKED] ?: defaults.calibrationUnlocked,
            passportEnabled = p[PASSPORT_ENABLED] ?: defaults.passportEnabled,
            notifyGoalLive = p[NOTIFY_GOAL_LIVE] ?: defaults.notifyGoalLive,
            tourDone = p[TOUR_DONE] ?: defaults.tourDone,
            recordRecipients = p[RECORD_RECIPIENTS] ?: defaults.recordRecipients,
            quickSendEnabled = p[QUICK_SEND_ENABLED] ?: defaults.quickSendEnabled,
            quickSendTargets = p[QUICK_SEND_TARGETS]?.let { cleanTargets(it.split('\n')) } ?: defaults.quickSendTargets,
            quickSendBubble = bubbleSpot(p),
            quickSendTargetsTikTok = p[QUICK_SEND_TARGETS_TIKTOK]?.let { cleanTargets(it.split('\n')) } ?: defaults.quickSendTargetsTikTok,
            quickSendBubbleTikTok = tikTokBubbleSpot(p),
            backupFolder = p[BACKUP_FOLDER]?.takeIf { it.isNotBlank() },
            backupFolderName = p[BACKUP_FOLDER_NAME],
            backupLastMs = p[BACKUP_LAST_MS],
            backupError = p[BACKUP_ERROR]?.let { name -> BackupError.entries.firstOrNull { it.name == name } },
            // Descubrimiento progresivo
            discovery = DiscoveryPrefs.read(p),
        )
    }

    private fun cleanTargets(targets: List<String>): List<String> =
        targets.map { it.replace('\n', ' ').trim() }.filter { it.isNotEmpty() }.distinct().take(AppSettings.MAX_QUICK_SEND)

    private companion object {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val DISCLOSURE_ACCEPTED = booleanPreferencesKey("disclosure_accepted")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val NOTIFY_DAILY = booleanPreferencesKey("notify_daily")
        val NOTIFY_MONTHLY = booleanPreferencesKey("notify_monthly")
        val NOTIFY_HOUR = intPreferencesKey("notify_hour")
        val NOTIFY_MINUTE = intPreferencesKey("notify_minute")
        val ENABLED_PLATFORMS = stringSetPreferencesKey("enabled_platforms")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CALIBRATION_UNLOCKED = booleanPreferencesKey("calibration_unlocked")
        val PASSPORT_ENABLED = booleanPreferencesKey("passport_enabled")
        val NOTIFY_GOAL_LIVE = booleanPreferencesKey("notify_goal_live")
        val TOUR_DONE = booleanPreferencesKey("tour_done")
        val RECORD_RECIPIENTS = booleanPreferencesKey("record_recipients")
        val QUICK_SEND_ENABLED = booleanPreferencesKey("quick_send_enabled")
        val QUICK_SEND_TARGETS = stringPreferencesKey("quick_send_targets")
        val QUICK_SEND_BUBBLE_X = floatPreferencesKey("quick_send_bubble_cx")
        val QUICK_SEND_BUBBLE_CENTER_Y = floatPreferencesKey("quick_send_bubble_cy")

        /** 1.10.0: lo mismo en TikTok (Instagram sigue en las claves de arriba). */
        val QUICK_SEND_TARGETS_TIKTOK = stringPreferencesKey("quick_send_targets_tiktok")
        val QUICK_SEND_BUBBLE_X_TIKTOK = floatPreferencesKey("quick_send_bubble_cx_tiktok")
        val QUICK_SEND_BUBBLE_CENTER_Y_TIKTOK = floatPreferencesKey("quick_send_bubble_cy_tiktok")

        /** Copia semanal del CSV. */
        val BACKUP_FOLDER = stringPreferencesKey("backup_folder")
        val BACKUP_FOLDER_NAME = stringPreferencesKey("backup_folder_name")
        val BACKUP_LAST_MS = longPreferencesKey("backup_last_ms")
        val BACKUP_ERROR = stringPreferencesKey("backup_error")

        /** Aviso de apoyo ([SupportPromptState]). */
        val SUPPORT_USAGE_DAYS = intPreferencesKey("support_usage_days")
        val SUPPORT_LAST_USAGE_DAY = longPreferencesKey("support_last_usage_day")
        val SUPPORT_TIMES_SHOWN = intPreferencesKey("support_times_shown")
        val SUPPORT_LAST_SHOWN_DAY = longPreferencesKey("support_last_shown_day")
        val SUPPORT_FINISHED = booleanPreferencesKey("support_finished")

        /** 1.4.0–1.4.1: borde (izquierdo o no) y altura del centro. Solo se leen para convertirlas. */
        val LEGACY_BUBBLE_LEFT = booleanPreferencesKey("quick_send_bubble_left")
        val LEGACY_BUBBLE_Y = floatPreferencesKey("quick_send_bubble_y")
    }
}
