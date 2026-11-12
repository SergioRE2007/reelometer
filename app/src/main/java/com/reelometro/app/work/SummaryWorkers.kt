package com.reelometro.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reelometro.app.ReelometroApp
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime

/**
 * Aviso diario: resume el día programado (o «ayer», si llega tarde pasada la medianoche) y deja
 * programado el siguiente. Si llega antes de hora (cambio de zona o de reloj), solo se reprograma.
 */
class DailyNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = ReelometroApp.from(applicationContext)
        val now = ZonedDateTime.now()
        val settings = app.settings.current()
        val time = LocalTime.of(settings.notifyHour, settings.notifyMinute)
        val scheduled = scheduledDate(inputData.getLong(NotificationScheduler.KEY_DATE, NO_DATE)) ?: now.toLocalDate()
        val run = SummaryRules.dailyRun(scheduled, time, now)
        if (run is SummaryRules.Run.Notify) {
            runCatching { SummaryNotifier.daily(app, run.date, run.late, now) }
                .onSuccess { Log.i(LOG, "Aviso diario: $it") }
                .onFailure { Log.w(LOG, "Aviso diario fallido", it) }
        }
        if (!isStopped && app.settings.current().notifyDaily) {
            NotificationScheduler.scheduleNextDaily(app, time, now, after = if (run is SummaryRules.Run.Early) null else scheduled)
        }
        return Result.success()
    }
}

/** Resumen mensual (día 1): resume el mes anterior, abre su Wrapped y deja programado el siguiente. */
class MonthlyNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = ReelometroApp.from(applicationContext)
        val now = ZonedDateTime.now()
        val scheduled = scheduledDate(inputData.getLong(NotificationScheduler.KEY_DATE, NO_DATE))
            ?: YearMonth.from(now).atDay(1)
        val run = SummaryRules.monthlyRun(scheduled, now)
        if (run is SummaryRules.Run.Notify) {
            runCatching { SummaryNotifier.monthly(app, YearMonth.from(run.date), now) }
                .onSuccess { Log.i(LOG, "Aviso mensual: $it") }
                .onFailure { Log.w(LOG, "Aviso mensual fallido", it) }
        }
        if (!isStopped && app.settings.current().notifyMonthly) {
            NotificationScheduler.scheduleNextMonthly(app, now, after = if (run is SummaryRules.Run.Early) null else scheduled)
        }
        return Result.success()
    }
}

private const val LOG = "Reelometro"
private const val NO_DATE = Long.MIN_VALUE

private fun scheduledDate(epochDay: Long): LocalDate? = epochDay.takeIf { it != NO_DATE }?.let(LocalDate::ofEpochDay)
