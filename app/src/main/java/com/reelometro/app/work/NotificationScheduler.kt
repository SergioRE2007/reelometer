package com.reelometro.app.work

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.reelometro.app.ReelometroApp
import com.reelometro.app.data.settings.AppSettings
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Programa los avisos con WorkManager: un `OneTimeWorkRequest` único por tipo que, al ejecutarse,
 * deja programado el siguiente (detrás de sí mismo, con APPEND: un REPLACE desde dentro lo cancelaría).
 *
 * Al arrancar y cuando cambian los ajustes **no** se usa REPLACE a ciegas: cancelaría el trabajo que
 * WorkManager está a punto de ejecutar justo cuando ha arrancado el proceso para ejecutarlo. La
 * configuración que decide la hora (hora, interruptor y zona) va como etiqueta en el trabajo y solo
 * se reprograma si cambia, o si cambian el reloj o la zona ([onClockChanged]).
 */
object NotificationScheduler {

    const val DAILY = "notify_daily"
    const val MONTHLY = "notify_monthly"

    /** Trabajo periódico provisional de una versiÃ³n antigua: se cancela al arrancar. */
    private const val LEGACY = "daily_summary"

    /** Día programado (epochDay) que lleva cada trabajo. */
    internal const val KEY_DATE = "date"

    private const val TAG_PREFIX = "cfg:"
    private const val LOG = "Reelometro"

    /** La parte de los ajustes que decide la programación. */
    data class Config(val daily: Boolean, val monthly: Boolean, val time: LocalTime) {
        companion object {
            fun of(s: AppSettings) = Config(s.notifyDaily, s.notifyMonthly, LocalTime.of(s.notifyHour, s.notifyMinute))
        }
    }

    /** Desde `ReelometroApp.onCreate`: observa los ajustes y mantiene los dos avisos al día. */
    fun start(app: ReelometroApp) {
        app.appScope.launch {
            runCatching { WorkManager.getInstance(app).cancelUniqueWork(LEGACY) }
            app.settings.settings
                .map(Config::of)
                .distinctUntilChanged()
                .collect { sync(app, it, force = false) }
        }
    }

    /** El usuario (o la red) ha cambiado la hora o la zona: recalcula los dos avisos. */
    fun onClockChanged(app: ReelometroApp) {
        app.appScope.launch { sync(app, Config.of(app.settings.current()), force = true) }
    }

    /** Deja programados (o cancelados) los dos avisos según [config]. */
    suspend fun sync(context: Context, config: Config, force: Boolean) {
        runCatching {
            val wm = WorkManager.getInstance(context)
            val now = ZonedDateTime.now()
            ensure(wm, DAILY, config.daily, SummaryRules.dailyKey(config.time, now.zone), force) { tag ->
                request<DailyNotificationWorker>(now, SummaryRules.nextDaily(now, config.time), tag)
            }
            ensure(wm, MONTHLY, config.monthly, SummaryRules.monthlyKey(now.zone), force) { tag ->
                request<MonthlyNotificationWorker>(now, SummaryRules.nextMonthly(now), tag)
            }
        }.onFailure { Log.w(LOG, "No se han podido programar los avisos", it) }
    }

    /** Desde el worker del diario, al terminar. [after]: día ya resumido (null = vale hoy). */
    internal fun scheduleNextDaily(context: Context, time: LocalTime, now: ZonedDateTime, after: LocalDate?) {
        val tag = TAG_PREFIX + SummaryRules.dailyKey(time, now.zone)
        WorkManager.getInstance(context).enqueueUniqueWork(
            DAILY,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request<DailyNotificationWorker>(now, SummaryRules.nextDaily(now, time, after), tag),
        )
    }

    /** Desde el worker del mensual, al terminar. */
    internal fun scheduleNextMonthly(context: Context, now: ZonedDateTime, after: LocalDate?) {
        val tag = TAG_PREFIX + SummaryRules.monthlyKey(now.zone)
        WorkManager.getInstance(context).enqueueUniqueWork(
            MONTHLY,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request<MonthlyNotificationWorker>(now, SummaryRules.nextMonthly(now, after), tag),
        )
    }

    private suspend fun ensure(
        wm: WorkManager,
        name: String,
        enabled: Boolean,
        key: String,
        force: Boolean,
        build: (tag: String) -> OneTimeWorkRequest,
    ) {
        if (!enabled) {
            wm.cancelUniqueWork(name)
            return
        }
        val tag = TAG_PREFIX + key
        if (!force) {
            val pending = wm.getWorkInfosForUniqueWorkFlow(name).first()
            if (pending.any { !it.state.isFinished && tag in it.tags }) return
        }
        wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, build(tag))
    }

    private inline fun <reified W : ListenableWorker> request(now: ZonedDateTime, at: ZonedDateTime, tag: String) =
        OneTimeWorkRequestBuilder<W>()
            .setInitialDelay(Duration.between(now, at).coerceAtLeast(Duration.ZERO))
            .setInputData(workDataOf(KEY_DATE to at.toLocalDate().toEpochDay()))
            .addTag(tag)
            .build()
}
