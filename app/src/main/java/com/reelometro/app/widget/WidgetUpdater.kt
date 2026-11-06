package com.reelometro.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.reelometro.app.ReelometroApp
import com.reelometro.app.detection.ReelAccessibilityService
import com.reelometro.app.game.FlapMood
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Punto único para pedir que se refresque el widget de «Hoy».
 *
 * [requestUpdate] se puede llamar muy a menudo (el servicio lo hace en cada vídeo): como mucho una
 * actualización cada [MIN_INTERVAL_MS], la primera al momento y siempre una final con lo último; sin
 * widgets en la pantalla de inicio no se lee nada. Además se refresca solo a medianoche (un trabajo
 * único de WorkManager), al cambiar la hora o la zona y al activar o desactivar el servicio.
 *
 * Los datos se publican en [data]: la composición viva del widget lo observa (con la sesión de
 * Glance abierta, `update()` no vuelve a ejecutar `provideGlance`).
 */
object WidgetUpdater {

    /** Intervalo mínimo entre actualizaciones pedidas con [requestUpdate]. */
    internal const val MIN_INTERVAL_MS = 20_000L

    private const val MIDNIGHT_WORK = "widget_midnight"
    private const val PREFS = "widget"
    private const val KEY_PREVIEWS = "previews_for_install"
    private const val LOG = "Reelometro"

    /** Lecturas más recientes que esto se reutilizan (evita leer dos veces al abrir una sesión). */
    private const val FRESH_MS = 2_000L

    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val consuming = AtomicBoolean(false)
    private val observing = AtomicBoolean(false)
    private val _data = MutableStateFlow<WidgetData?>(null)

    @Volatile
    private var loadedAt = 0L

    /** Lo último leído para el widget (`null` hasta la primera lectura). */
    internal val data: StateFlow<WidgetData?> = _data.asStateFlow()

    /** Pide refrescar el widget. Barato y seguro desde cualquier hilo. */
    fun requestUpdate(context: Context) {
        val app = ReelometroApp.from(context)
        if (consuming.compareAndSet(false, true)) {
            app.appScope.launch {
                throttle(requests, MIN_INTERVAL_MS) {
                    try {
                        refreshNow(app)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(LOG, "Widget: no se ha podido repintar", e)
                    }
                }
            }
        }
        requests.trySend(Unit)
    }

    /**
     * Desde `ReelometroApp.onCreate`: si hay widgets, repinta (el proceso pudo morir con datos viejos)
     * y deja programada la medianoche; vigila el interruptor del servicio; publica las vistas previas.
     */
    internal fun start(app: ReelometroApp) {
        if (observing.compareAndSet(false, true)) {
            runCatching {
                app.contentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                    false,
                    object : ContentObserver(null) {
                        override fun onChange(selfChange: Boolean) = requestUpdate(app)
                    },
                )
            }
            // Al encender o apagar el modo pasaporte, Flap aparece o desaparece del widget.
            app.appScope.launch {
                runCatching {
                    app.settings.settings.map { it.passportEnabled }.distinctUntilChanged().drop(1).collect { requestUpdate(app) }
                }
            }
        }
        app.appScope.launch {
            runCatching {
                if (hasWidgets(app)) {
                    scheduleMidnight(app, ExistingWorkPolicy.KEEP)
                    requestUpdate(app)
                }
                publishPreviews(app)
            }.onFailure { Log.w(LOG, "Widget: arranque fallido", it) }
        }
    }

    /** Ha cambiado la hora, la zona o el día: repinta y recoloca la medianoche. */
    internal fun onClockChanged(app: ReelometroApp, reschedule: Boolean) {
        app.appScope.launch {
            runCatching {
                if (reschedule && hasWidgets(app)) scheduleMidnight(app, ExistingWorkPolicy.REPLACE)
                requestUpdate(app)
            }
        }
    }

    /**
     * Desde el receptor: el primer widget se ha añadido ([enabled]) o se ha quitado el último. Se
     * decide por el evento y no preguntando a Glance, cuyo registro de receptores aún no está escrito
     * cuando llega el primer `onEnabled`.
     */
    internal fun onWidgetsChanged(context: Context, enabled: Boolean) {
        val app = ReelometroApp.from(context)
        app.appScope.launch {
            runCatching {
                if (enabled) {
                    scheduleMidnight(app, ExistingWorkPolicy.KEEP)
                } else {
                    WorkManager.getInstance(app).cancelUniqueWork(MIDNIGHT_WORK)
                }
            }
        }
    }

    /** Lee los datos y repinta todos los widgets ya, sin límite. Devuelve cuántos hay. */
    suspend fun refreshNow(context: Context): Int {
        val count = widgetCount(context)
        if (count == 0) return 0
        load(context)
        TodayWidget().updateAll(context)
        return count
    }

    /** Para `provideGlance`: lo último si se acaba de leer; si no, lee ahora (y lo publica). */
    internal suspend fun loadFresh(context: Context): WidgetData? {
        val last = _data.value
        if (last != null && SystemClock.elapsedRealtime() - loadedAt < FRESH_MS) return last
        return runCatching { load(context) }
            .onFailure { Log.w(LOG, "Widget: no se han podido leer los datos", it) }
            .getOrNull() ?: last
    }

    private suspend fun load(context: Context): WidgetData {
        val app = ReelometroApp.from(context)
        val day = app.repository.today()
        val data = WidgetData(
            date = day.date,
            count = day.totals.count,
            totalMs = day.totals.totalMs,
            goal = day.goal?.goal,
            serviceOn = ReelAccessibilityService.isEnabled(context),
            flap = flapMood(app, day.date, day.goal?.goal, day.totals.count),
        )
        loadedAt = SystemClock.elapsedRealtime()
        _data.value = data
        return data
    }

    /**
     * Estado de Flap para el tamaño grande: `null` con el modo pasaporte apagado (ni se calcula) o
     * si el pasaporte falla (el widget sale igual, sin Flap).
     *
     * El pasaporte entero solo se recalcula si cambia algo que puede cambiar a Flap: el día, el
     * objetivo, que hoy cruce su referencia (hoy solo empeora) o que el recuento baje (datos borrados).
     * Lo demás reutiliza [flapCache].
     */
    private suspend fun flapMood(app: ReelometroApp, date: LocalDate, goal: Int?, count: Int): FlapMood? = try {
        if (!app.settings.current().passportEnabled) {
            flapCache = null
            null
        } else {
            val cached = flapCache
            if (
                cached != null && cached.date == date && cached.goal == goal &&
                count >= cached.count && cached.over(count) == cached.wasOver
            ) {
                cached.mood
            } else {
                val flap = app.passport.snapshot().flap
                flapCache = FlapCache(date, goal, count, flap.reference, wasOver = flap.reference?.let { count > it }, mood = flap.mood)
                flap.mood
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(LOG, "Widget: no se ha podido leer el estado de Flap", e)
        null
    }

    @Volatile
    private var flapCache: FlapCache? = null

    /** Último Flap calculado para el widget y lo que lo decidía. */
    private class FlapCache(
        val date: LocalDate,
        val goal: Int?,
        /** Recuento con el que se calculó: si baja (datos borrados o sembrados), se recalcula. */
        val count: Int,
        val reference: Int?,
        val wasOver: Boolean?,
        val mood: FlapMood,
    ) {
        fun over(count: Int): Boolean? = reference?.let { count > it }
    }

    private fun hasWidgets(context: Context): Boolean = widgetCount(context) > 0

    /** Widgets fijados, según el sistema (fiable desde el primer momento, a diferencia de `getGlanceIds`). */
    private fun widgetCount(context: Context): Int =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, TodayWidgetReceiver::class.java)).size

    internal fun scheduleMidnight(context: Context, policy: ExistingWorkPolicy) {
        val now = ZonedDateTime.now()
        val request = OneTimeWorkRequestBuilder<WidgetMidnightWorker>()
            .setInitialDelay(Duration.between(now, WidgetSchedule.nextRefresh(now)))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(MIDNIGHT_WORK, policy, request)
    }

    /**
     * API 35+: vistas previas generadas (`providePreview`) para el selector de widgets. El sistema
     * limita cuántas veces se pueden publicar, así que solo se hace una vez por instalación o
     * actualización (y se reintenta en el siguiente arranque si se ha rechazado).
     */
    private suspend fun publishPreviews(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        if (prefs.getLong(KEY_PREVIEWS, 0L) == installed) return
        val result = GlanceAppWidgetManager(context).setWidgetPreviews(TodayWidgetReceiver::class)
        if (result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) {
            prefs.edit { putLong(KEY_PREVIEWS, installed) }
        }
    }
}

/** Refresca el widget al empezar el día y deja programado el de mañana (detrás de sí: APPEND). */
class WidgetMidnightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val widgets = runCatching { WidgetUpdater.refreshNow(applicationContext) }.getOrDefault(1)
        if (widgets > 0 && !isStopped) WidgetUpdater.scheduleMidnight(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}
