package com.reelometro.app.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.reelometro.app.ReelometroApp
import com.reelometro.app.data.backup.BackupError
import com.reelometro.app.data.backup.BackupOutcome
import com.reelometro.app.data.backup.BackupRules
import com.reelometro.app.data.backup.CsvBackup
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Copia semanal del CSV en la carpeta elegida ([CsvBackup]). Apunta el resultado en los ajustes: la fecha si sale
 * bien, el error si no (Ajustes lo enseña). Sin permiso no reintenta (hace falta otra carpeta); con otro fallo,
 * lo reintenta un par de veces y, si no, a la semana siguiente.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = ReelometroApp.from(applicationContext)
        val folder = app.settings.current().backupFolder ?: return Result.success()
        val outcome = CsvBackup.write(app, folder)
        runCatching { app.settings.recordBackup(folder, outcome, System.currentTimeMillis()) }
        Log.i(LOG, "Copia semanal: $outcome")
        val retry = outcome is BackupOutcome.Failed && outcome.error == BackupError.FAILED && runAttemptCount < MAX_RETRIES
        return if (retry) Result.retry() else Result.success()
    }

    private companion object {
        const val LOG = "Reelometro"
        const val MAX_RETRIES = 2
    }
}

/**
 * Programa la copia semanal: un `PeriodicWorkRequest` único mientras haya carpeta. Con KEEP, arrancar la app o
 * cambiar de carpeta no mueve el día de la copia (el worker lee la carpeta de ese momento). Al encenderla, Ajustes
 * ya hace la primera copia; por eso la periódica empieza a contar desde ahí.
 */
object BackupScheduler {

    const val WORK = "csv_backup_weekly"
    private const val LOG = "Reelometro"

    /** Desde `ReelometroApp.onCreate`: observa los ajustes y programa o cancela la copia. */
    fun start(app: ReelometroApp) {
        app.appScope.launch {
            app.settings.settings
                .map { it.backupFolder != null }
                .distinctUntilChanged()
                .collect { on -> sync(app, on) }
        }
    }

    private fun sync(context: Context, on: Boolean) {
        runCatching {
            val wm = WorkManager.getInstance(context)
            if (!on) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<BackupWorker>(BackupRules.INTERVAL_DAYS, TimeUnit.DAYS)
                .setInitialDelay(BackupRules.INTERVAL_DAYS, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }.onFailure { Log.w(LOG, "No se ha podido programar la copia semanal", it) }
    }
}
