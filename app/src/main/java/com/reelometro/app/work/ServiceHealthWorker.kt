package com.reelometro.app.work

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.reelometro.app.ReelometroApp
import com.reelometro.app.copy.ExitCopy
import com.reelometro.app.copy.NotificationCopy
import com.reelometro.app.detection.ProcessExits
import com.reelometro.app.detection.ServiceHealth
import com.reelometro.app.detection.ServiceJournal
import com.reelometro.app.detection.ServiceJournalRules
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Vigila que el servicio de accesibilidad funcione de verdad: si está activado pero el sistema no lo ha conectado
 * ([ServiceHealth.STALLED]), avisa con una notificación (como mucho una al día) que abre la pantalla de conectar con
 * los pasos para arreglarlo. Si vuelve a funcionar, quita el aviso.
 *
 * También avisa, una vez por cada vez que pasa, si el servicio funcionaba y ha salido **apagado** sin que nadie lo
 * apagara en Ajustes ([ServiceJournalRules.lostBySystem]): lo ha quitado el sistema al forzar la detención de la app.
 *
 * Límite honesto: si el fabricante ha «forzado la detención» de la app, Android tampoco ejecuta este trabajo hasta que
 * se abra la app; entonces lo avisa Hoy. Por eso también se mira nada más arrancar el proceso ([ServiceHealthScheduler]).
 *
 * **Tras reiniciar el móvil.** WorkManager guarda el trabajo periódico en su base de datos y lo vuelve a programar al
 * arrancar con su propio receptor (`RescheduleReceiver`, que trae `RECEIVE_BOOT_COMPLETED` en el manifiesto de la
 * librería: por eso la app no declara el permiso ni un receptor propio). Ese receptor, el widget o el propio servicio
 * de accesibilidad arrancan el proceso, y `ReelometroApp.onCreate` llama a [ServiceHealthScheduler.start]. Si el
 * proceso arranca en los primeros minutos de encendido ([ServiceHealth.BOOT_WINDOW_MS]), el margen para conectar es
 * [ServiceHealth.BOOT_GRACE_MS] (Android conecta la accesibilidad después de desbloquear) y, si aun así no ha conectado,
 * no se avisa a la primera: se vuelve a mirar a los 5 minutos ([ServiceHealthRules.Action.RETRY]) y solo entonces se
 * avisa.
 * En los móviles que bloquean el «Inicio automático» (MIUI/HyperOS, EMUI, ColorOS…) el receptor tampoco se ejecuta
 * hasta abrir la app: un receptor propio estaría bloqueado igual.
 */
class ServiceHealthWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val isRetry = inputData.getBoolean(KEY_RETRY, false)
        runCatching { ServiceHealthScheduler.check(ReelometroApp.from(applicationContext), isRetry) }
            .onFailure { Log.w(LOG, "Revisión del servicio fallida", it) }
        return Result.success()
    }

    internal companion object {
        private const val LOG = "Reelometro"

        /** Dato de entrada de la revisión diferida tras reiniciar: ya es la segunda, se avisa si sigue sin conectar. */
        const val KEY_RETRY = "retry"
    }
}

/** Reglas puras del aviso del servicio (sin Android). */
object ServiceHealthRules {
    enum class Action {
        NOTIFY,

        /** El servicio funcionaba y lo ha apagado el sistema: aviso de «ha dejado de contar» (uno por pérdida). */
        NOTIFY_LOST,
        CANCEL,
        NOTHING,
        RETRY,
    }

    /**
     * @param disclosureAccepted solo se avisa a quien activó el servicio a propósito.
     * @param lastNotified último día con aviso (null = nunca).
     * @param bootRetryPending el proceso arrancó justo tras reiniciar y aún no se ha hecho la segunda revisión: si sigue
     *   sin conectar, [Action.RETRY] (mirar más tarde) en vez de avisar.
     * @param lost apagado, pero no por el usuario ([ServiceJournalRules.lostBySystem]).
     * @param lossNotified de esta pérdida ya se avisó (el aviso se deja como esté: ni se repite ni se quita).
     * @param appVisible la app está en pantalla: lo dice Hoy, así que el aviso de pérdida se deja para más tarde.
     */
    fun action(
        health: ServiceHealth,
        disclosureAccepted: Boolean,
        canNotify: Boolean,
        lastNotified: LocalDate?,
        today: LocalDate,
        bootRetryPending: Boolean = false,
        lost: Boolean = false,
        lossNotified: Boolean = false,
        appVisible: Boolean = false,
    ): Action = when (health) {
        ServiceHealth.OK -> Action.CANCEL
        ServiceHealth.OFF -> when {
            !lost -> Action.CANCEL
            lossNotified || appVisible || !disclosureAccepted || !canNotify -> Action.NOTHING
            else -> Action.NOTIFY_LOST
        }
        ServiceHealth.STARTING -> if (bootRetryPending) Action.RETRY else Action.NOTHING
        ServiceHealth.STALLED -> when {
            !disclosureAccepted || !canNotify -> Action.NOTHING
            bootRetryPending -> Action.RETRY
            lastNotified == today -> Action.NOTHING
            else -> Action.NOTIFY
        }
    }
}

/** Programa la revisión periódica y la del arranque del proceso. */
object ServiceHealthScheduler {
    private const val WORK = "service_health"
    private const val WORK_SOON = "service_health_soon"
    private const val SOON_MINUTES = 5L

    /** Revisión al poco de que Android desenlace el servicio con el interruptor encendido ([checkSoon]). */
    private const val WORK_UNBOUND = "service_health_unbound"
    private const val UNBOUND_MINUTES = 2L

    /** Edad máxima del proceso para aplazar el aviso: más allá no es «recién arrancado al reiniciar». */
    private const val BOOT_RETRY_MAX_AGE_MS = 10 * 60_000L
    /**
     * Cada hora (hasta la 1.12.0, cada 4): si el sistema cierra la app y no vuelve a enlazar el servicio, este trabajo
     * es lo único que arranca el proceso para darse cuenta. Con el servicio bien no cuesta nada (el proceso ya está
     * vivo y solo se lee un ajuste); el margen deja que Android lo agrupe con otros trabajos.
     */
    private const val PERIOD_HOURS = 1L
    private const val FLEX_MINUTES = 20L
    private const val PREFS = "service_health"
    private const val KEY_LAST = "last_notified_day"

    /** Desde `ReelometroApp.onCreate`. */
    fun start(app: ReelometroApp) {
        app.appScope.launch {
            runCatching {
                WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                    WORK,
                    // UPDATE: quien ya tenía programada la revisión de cada 4 horas pasa a la de cada hora.
                    ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<ServiceHealthWorker>(PERIOD_HOURS, TimeUnit.HOURS, FLEX_MINUTES, TimeUnit.MINUTES).build(),
                )
            }
            // Al arrancar el proceso (abrir la app, un widget, un aviso…): el sistema conecta el servicio enseguida si
            // funciona; si pasado el margen no lo ha hecho, se avisa.
            runCatching { check(app) }
        }
    }

    /**
     * Android ha desenlazado el servicio con el interruptor encendido y el proceso sigue vivo: se mira a los pocos
     * minutos si lo ha vuelto a enlazar y, si no, se avisa sin esperar a la revisión periódica.
     */
    fun checkSoon(app: ReelometroApp) {
        runCatching {
            WorkManager.getInstance(app).enqueueUniqueWork(
                WORK_UNBOUND,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ServiceHealthWorker>()
                    .setInitialDelay(UNBOUND_MINUTES, TimeUnit.MINUTES)
                    .setInputData(workDataOf(ServiceHealthWorker.KEY_RETRY to true))
                    .build(),
            )
        }
    }

    /** La app está en pantalla (entonces lo que haya que decir lo dice Hoy). */
    private fun appVisible(app: ReelometroApp): Boolean = runCatching {
        val state = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(state)
        state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }.getOrDefault(false)

    /**
     * @param isRetry es la revisión diferida tras reiniciar: ya no se vuelve a aplazar.
     */
    suspend fun check(app: ReelometroApp, isRetry: Boolean = false) {
        val health = ServiceHealth.settle(app)
        // Funciona: los cierres de hasta ahora quedan vistos y solo se explicarán los que vengan después.
        if (health == ServiceHealth.OK) ProcessExits.markSeen(app)
        // Proceso arrancado justo tras reiniciar y aún joven: Android puede conectar la accesibilidad pasado el margen
        // (desbloqueo lento), así que la primera revisión no avisa y se repite una vez a los 5 minutos.
        val bootRetryPending = !isRetry &&
            ServiceHealth.defaultGraceMs() == ServiceHealth.BOOT_GRACE_MS &&
            ServiceHealth.processAgeMs() < BOOT_RETRY_MAX_AGE_MS
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = ZonedDateTime.now()
        val last = prefs.getLong(KEY_LAST, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)
        // Apagado sin que nadie lo apagara: lo ha quitado el sistema (solo se mira con el servicio apagado).
        val journal = ServiceJournal(app)
        val lostConnection = if (health == ServiceHealth.OFF) {
            journal.read().takeIf { ServiceJournalRules.lostBySystem(enabled = false, it) }?.lastConnection
        } else {
            null
        }
        val action = ServiceHealthRules.action(
            health = health,
            disclosureAccepted = app.settings.current().disclosureAccepted,
            canNotify = Notifications.canNotify(app),
            lastNotified = last,
            today = now.toLocalDate(),
            bootRetryPending = bootRetryPending,
            lost = lostConnection != null,
            lossNotified = lostConnection != null && journal.notifiedLossOf == lostConnection.at,
            appVisible = lostConnection != null && appVisible(app),
        )
        when (action) {
            ServiceHealthRules.Action.NOTIFY_LOST -> {
                val shown = Notifications.show(
                    app,
                    Notifications.Kind.SERVICE,
                    copy = NotificationCopy.serviceLost,
                    public = NotificationCopy.serviceStalledPublic,
                    route = "setup",
                    now = now,
                    expiresAt = now.plusDays(1),
                )
                if (shown) journal.notifiedLossOf = lostConnection?.at
            }
            ServiceHealthRules.Action.NOTIFY -> {
                val shown = Notifications.show(
                    app,
                    Notifications.Kind.SERVICE,
                    // Con el motivo del último cierre nuevo, si Android lo guarda (p. ej. al limpiar recientes).
                    copy = NotificationCopy.serviceStalled(ProcessExits.stallReason(app)?.category?.let(ExitCopy::diagnosis)),
                    public = NotificationCopy.serviceStalledPublic,
                    route = "setup",
                    now = now,
                    expiresAt = now.plusDays(1),
                )
                if (shown) prefs.edit { putLong(KEY_LAST, now.toLocalDate().toEpochDay()) }
            }
            ServiceHealthRules.Action.CANCEL -> Notifications.cancel(app, Notifications.Kind.SERVICE)
            ServiceHealthRules.Action.RETRY -> runCatching {
                WorkManager.getInstance(app).enqueueUniqueWork(
                    WORK_SOON,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<ServiceHealthWorker>()
                        .setInitialDelay(SOON_MINUTES, TimeUnit.MINUTES)
                        .setInputData(workDataOf(ServiceHealthWorker.KEY_RETRY to true))
                        .build(),
                )
            }
            ServiceHealthRules.Action.NOTHING -> Unit
        }
    }
}
