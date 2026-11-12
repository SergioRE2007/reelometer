package com.reelometro.app.work

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.reelometro.app.ReelometroApp
import com.reelometro.app.copy.NotificationCopy
import com.reelometro.app.copy.Variants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Avisos que salen del servicio de accesibilidad, sin encuestas periódicas ni trabajos
 * nuevos: tras cada `recordStart`, el servicio llama a [onVideoStarted] y, en el `appScope`,
 * [GoalAlert] decide el aviso en directo al pasar el objetivo (una vez al día; reglas en
 * [GoalAlertRules]). Flap no manda avisos: se ve en su pestaña y en el widget.
 */
object LiveAlerts {

    /** Gancho del servicio tras cada vídeo nuevo ya guardado. No bloquea: todo va en el `appScope`. */
    fun onVideoStarted(context: Context) {
        val app = ReelometroApp.from(context)
        app.appScope.launch {
            runCatching { GoalAlert.onVideo(app) }.onFailure { Log.w(LOG, "Aviso en directo fallido", it) }
        }
    }

    /** BORRAR TODO: olvida qué se ha avisado (el día del aviso en directo). */
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

/**
 * Lo que recuerdan estos avisos: el día del último aviso en directo. No es configuración (no va en
 * DataStore con los ajustes) ni sale del móvil (la app no tiene copia de seguridad).
 */
internal class AlertStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var goalSentOn: LocalDate?
        get() = day(KEY_GOAL_SENT)
        set(value) = putDay(KEY_GOAL_SENT, value)

    fun clear() {
        prefs.edit { this.clear() }
    }

    /**
     * Lo que recordaba el aviso de nivel del antiguo Pasaporte (quitado en 1.12.0): el último nivel
     * avisado y el día en que se miró. Se borra una vez; si ya no está, no escribe nada.
     */
    fun dropLegacyLevel() {
        if (LEGACY_KEYS.any { prefs.contains(it) }) prefs.edit { LEGACY_KEYS.forEach { remove(it) } }
    }

    private fun day(key: String): LocalDate? =
        if (prefs.contains(key)) LocalDate.ofEpochDay(prefs.getLong(key, 0L)) else null

    private fun putDay(key: String, value: LocalDate?) {
        prefs.edit { if (value == null) remove(key) else putLong(key, value.toEpochDay()) }
    }

    private companion object {
        const val FILE = "alerts"
        const val KEY_GOAL_SENT = "goal_live_sent_day"

        /** Claves del aviso de nivel (hasta 1.11.x): solo para borrarlas ([dropLegacyLevel]). */
        val LEGACY_KEYS = listOf("level_notified", "level_checked_day")
    }
}

private const val LOG = "Reelometro"
