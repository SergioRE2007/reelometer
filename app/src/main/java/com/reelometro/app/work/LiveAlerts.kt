package com.reelometro.app.work

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.reelometro.app.ReelometroApp
import com.reelometro.app.copy.NotificationCopy
import com.reelometro.app.copy.Variants
import com.reelometro.app.game.TravelerLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Avisos que salen del servicio de accesibilidad, sin encuestas periódicas ni trabajos
 * nuevos: tras cada `recordStart`, el servicio llama a [onVideoStarted] y, en el `appScope`:
 * - [GoalAlert]: aviso en directo al pasar el objetivo (una vez al día; reglas en [GoalAlertRules]).
 * - [LevelAlert]: con el primer vídeo del día, si el modo pasaporte está encendido, mira si hay
 *   nivel nuevo (reglas en [LevelAlertRules]). También lo mira el aviso diario.
 */
object LiveAlerts {

    /** Gancho del servicio tras cada vídeo nuevo ya guardado. No bloquea: todo va en el `appScope`. */
    fun onVideoStarted(context: Context) {
        val app = ReelometroApp.from(context)
        app.appScope.launch {
            runCatching { GoalAlert.onVideo(app) }.onFailure { Log.w(LOG, "Aviso en directo fallido", it) }
            runCatching { LevelAlert.onVideo(app) }.onFailure { Log.w(LOG, "Aviso de nivel fallido", it) }
        }
    }

    /** BORRAR TODO: olvida qué se ha avisado (el día del aviso en directo y el último nivel). */
    fun clear(context: Context) = AlertStore(context).clear()
}

/** Aviso en directo al pasar el objetivo. */
object GoalAlert {

    /** Ruta que abre Hoy. */
    private const val ROUTE_TODAY = "today"

    /**
     * Espera antes de contar: si el vídeo era un rebote (menos de `ViewTracker.MIN_DWELL_MS` en
     * pantalla), para entonces ya se ha descartado y no cuenta para pasar el objetivo.
     */
    private const val SETTLE_MS = 1_500L

    /** Dos vídeos seguidos no pueden mandar dos avisos. */
    private val mutex = Mutex()

    internal suspend fun onVideo(context: Context) {
        val app = ReelometroApp.from(context)
        val settings = app.settings.current()
        val today = LocalDate.now()
        // Lo barato primero (apagado por defecto): sin objetivo, apagado o ya enviado, ni se cuenta ni se espera.
        if (GoalAlertRules.precheck(settings.notifyGoalLive, settings.dailyGoal, today, AlertStore(app).goalSentOn) != null) return
        delay(SETTLE_MS)
        val result = check(app)
        // Por debajo del objetivo, cada vídeo acaba en «omitido»: solo se apunta lo que se publica.
        if (!result.startsWith(SKIPPED)) Log.i(LOG, "Aviso en directo: $result")
    }

    /**
     * Decide y, si toca, publica el aviso con el recuento de hoy (`ReelRepository.countOn`, barato).
     * [force] (QA, `NOTIFY_GOAL`) salta el ajuste y el «ya enviado hoy», nunca el objetivo ni el
     * permiso de avisos; no lo marca como enviado. Devuelve una línea para Logcat.
     */
    suspend fun check(context: Context, now: ZonedDateTime = ZonedDateTime.now(), force: Boolean = false): String =
        mutex.withLock {
            val app = ReelometroApp.from(context)
            val settings = app.settings.current()
            val store = AlertStore(app)
            val today = now.toLocalDate()
            val goal = settings.dailyGoal
            GoalAlertRules.precheck(settings.notifyGoalLive, goal, today, store.goalSentOn, force)
                ?.let { return@withLock "omitido ($it)" }
            val count = app.repository.countOn(today)
            GoalAlertRules.decide(settings.notifyGoalLive, goal, count, today, store.goalSentOn, force)
                ?.let { return@withLock "omitido ($it: $count de ${goal ?: 0})" }
            if (goal == null) return@withLock "omitido (${GoalAlertRules.Skip.NO_GOAL})"
            if (!Notifications.canNotify(app)) return@withLock "omitido (${SummaryRules.Skip.NO_PERMISSION})"

            val copy = NotificationCopy.goalPassed(count, goal, Variants.daySeed(today))
            val shown = Notifications.show(
                app, Notifications.Kind.GOAL_LIVE, copy, NotificationCopy.goalPassedPublic, ROUTE_TODAY, now,
                GoalAlertRules.expiry(now),
            )
            if (shown && !force) store.goalSentOn = today
            if (shown) "publicado «${copy.title}» · ${copy.text} → $ROUTE_TODAY" else "omitido (${SummaryRules.Skip.NO_PERMISSION})"
        }

    private const val SKIPPED = "omitido"
}

/** Aviso de nivel nuevo del Pasaporte (el único aviso del modo pasaporte). */
object LevelAlert {

    /** Ruta que abre la pestaña Pasaporte. */
    private const val ROUTE_PASSPORT = "passport"

    private val mutex = Mutex()

    /** Con el primer vídeo de cada día (el nivel cambia al cerrarse los días). */
    internal suspend fun onVideo(context: Context) {
        val app = ReelometroApp.from(context)
        val settings = app.settings.current()
        val today = LocalDate.now()
        val store = AlertStore(app)
        if (!LevelAlertRules.shouldCheckOnVideo(settings.passportEnabled, today, store.levelCheckedOn)) return
        store.levelCheckedOn = today
        Log.i(LOG, "Aviso de nivel: ${check(app)}")
    }

    /**
     * Mira el nivel del pasaporte y avisa si supera al último avisado. Nunca con el modo pasaporte
     * apagado (ni a la fuerza). [force] (QA, `NOTIFY_LEVEL`) avisa del nivel actual aunque no sea
     * nuevo, sin guardarlo. Devuelve una línea para Logcat.
     */
    suspend fun check(context: Context, now: ZonedDateTime = ZonedDateTime.now(), force: Boolean = false): String =
        mutex.withLock {
            val app = ReelometroApp.from(context)
            if (!app.settings.current().passportEnabled) return@withLock "omitido (modo pasaporte apagado)"
            val level = app.passport.snapshot().level.level
            val store = AlertStore(app)
            if (force) return@withLock publish(app, level, now)
            when (val outcome = LevelAlertRules.decide(true, level, store.levelNotified)) {
                is LevelAlertRules.Outcome.Notify -> {
                    // Se guarda aunque no haya permiso: un «nivel nuevo» que llegara días después ya no sería nuevo.
                    store.levelNotified = outcome.level
                    publish(app, outcome.level, now)
                }
                is LevelAlertRules.Outcome.Baseline -> {
                    store.levelNotified = outcome.level
                    "base: ${outcome.level}"
                }
                LevelAlertRules.Outcome.Keep -> "sin cambios (${level.name})"
            }
        }

    private fun publish(context: Context, level: TravelerLevel, now: ZonedDateTime): String {
        val copy = NotificationCopy.levelUp(level, Variants.daySeed(now.toLocalDate()))
        val shown = Notifications.show(
            context, Notifications.Kind.LEVEL, copy, NotificationCopy.levelUpPublic, ROUTE_PASSPORT, now,
            LevelAlertRules.expiry(now),
        )
        return if (shown) "publicado «${copy.title}» · ${copy.text} → $ROUTE_PASSPORT" else "omitido (${SummaryRules.Skip.NO_PERMISSION})"
    }

    /** Al apagar el modo pasaporte: fuera el aviso de nivel si seguía en la cortina. */
    fun onPassportDisabled(context: Context) = Notifications.cancel(context, Notifications.Kind.LEVEL)

    /**
     * Al encenderlo otra vez: el próximo chequeo vuelve a tomar la base sin avisar, para no anunciar
     * un nivel que se subió con el modo oculto.
     */
    fun onPassportEnabled(context: Context) {
        AlertStore(context).levelNotified = null
    }
}

/**
 * Lo que recuerdan estos avisos: el día del último aviso en directo, el último nivel avisado y el
 * último día en que se miró el nivel. No es configuración (no va en DataStore con los ajustes) ni
 * sale del móvil (la app no tiene copia de seguridad).
 */
internal class AlertStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var goalSentOn: LocalDate?
        get() = day(KEY_GOAL_SENT)
        set(value) = putDay(KEY_GOAL_SENT, value)

    var levelCheckedOn: LocalDate?
        get() = day(KEY_LEVEL_CHECKED)
        set(value) = putDay(KEY_LEVEL_CHECKED, value)

    var levelNotified: TravelerLevel?
        get() = LevelAlertRules.parse(prefs.getString(KEY_LEVEL, null))
        set(value) {
            prefs.edit { if (value == null) remove(KEY_LEVEL) else putString(KEY_LEVEL, value.name) }
        }

    fun clear() {
        prefs.edit { this.clear() }
    }

    private fun day(key: String): LocalDate? =
        if (prefs.contains(key)) LocalDate.ofEpochDay(prefs.getLong(key, 0L)) else null

    private fun putDay(key: String, value: LocalDate?) {
        prefs.edit { if (value == null) remove(key) else putLong(key, value.toEpochDay()) }
    }

    private companion object {
        const val FILE = "alerts"
        const val KEY_GOAL_SENT = "goal_live_sent_day"
        const val KEY_LEVEL = "level_notified"
        const val KEY_LEVEL_CHECKED = "level_checked_day"
    }
}

private const val LOG = "Reelometro"
