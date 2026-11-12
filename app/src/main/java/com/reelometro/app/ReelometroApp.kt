package com.reelometro.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import com.reelometro.app.copy.AppLanguage
import com.reelometro.app.copy.AppTexts
import com.reelometro.app.copy.ResourceTexts
import com.reelometro.app.data.AppDatabase
import com.reelometro.app.data.ReelRepository
import com.reelometro.app.data.game.FlapRepository
import com.reelometro.app.data.settings.SettingsRepository
import com.reelometro.app.detection.ServiceStatus
import com.reelometro.app.widget.WidgetUpdater
import com.reelometro.app.work.BackupScheduler
import com.reelometro.app.work.NotificationScheduler
import com.reelometro.app.work.Notifications
import com.reelometro.app.work.ServiceHealthScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Contenedor de dependencias de la app (sin framework de DI: la app es pequeña).
 * Úsalo con `ReelometroApp.from(context)`.
 */
class ReelometroApp : Application() {

    /** Ámbito de la app para trabajo que debe sobrevivir a una pantalla (escrituras, widget…). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    /**
     * El objetivo diario de los ajustes alimenta rachas y progreso. Lo visto de Flap se borra con
     * BORRAR TODO (proveedor perezoso: no se crea hasta que hace falta).
     */
    val repository: ReelRepository by lazy {
        ReelRepository(database, dailyGoal = settings.settings.map { it.dailyGoal }, userData = { listOf(flap) })
    }

    /**
     * Flap, la mascota que enferma con cada vídeo (pestaña y widget), sobre los datos de uso y el
     * objetivo. Es un `FlapSource`; además, `clearAll()` para BORRAR TODO y `GAME_RESET`.
     */
    val flap: FlapRepository by lazy { FlapRepository(this, database, settings) }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Cambió el idioma del sistema (o el de la app desde Android) con el proceso vivo.
        AppLanguage.refresh(this)
    }

    /**
     * Aviso de poca memoria del sistema. Solo se apunta (sale en el registro de Calibración, junto a los cierres): en
     * este proceso no hay cachés grandes que soltar. Las fotos de los fijados son unas pocas miniaturas, la caché de
     * glifos del widget tiene tope y lo que más pesa (la interfaz) ya lo recorta Android al esconderse.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        ServiceStatus.onTrimMemory(level, System.currentTimeMillis())
    }

    override fun onCreate() {
        super.onCreate()
        // Antes que nada: todos los textos (pantallas, avisos, widget) salen de los recursos.
        AppTexts.install(ResourceTexts(this))
        // Todo fuera del hilo principal: el arranque no espera a canales, WorkManager ni widgets.
        appScope.launch { runCatching { Notifications.createChannels(this@ReelometroApp) } }
        // La memoria del pasaporte de antes de Flap (fechas de sellos) ya no sirve: fuera, si sigue ahí.
        appScope.launch { runCatching { flap.dropLegacy() } }
        NotificationScheduler.start(this)
        BackupScheduler.start(this)
        ServiceHealthScheduler.start(this)
        WidgetUpdater.start(this)
        listenToClock()
    }

    /**
     * Cambios de reloj, de zona y de día mientras el proceso vive (el servicio de accesibilidad lo
     * mantiene casi siempre): avisos y medianoche del widget se recolocan; el widget se repinta.
     * Si el proceso estaba muerto, al arrancar se comparan la hora y la zona con lo programado.
     */
    private fun listenToClock() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val moved = intent.action != Intent.ACTION_DATE_CHANGED
                if (moved) NotificationScheduler.onClockChanged(this@ReelometroApp)
                WidgetUpdater.onClockChanged(this@ReelometroApp, reschedule = moved)
            }
        }
        runCatching { ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
    }

    companion object {
        fun from(context: Context): ReelometroApp = context.applicationContext as ReelometroApp
    }
}
