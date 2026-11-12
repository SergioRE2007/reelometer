package com.reelometro.app.detection

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat

/**
 * Lo que Android dice de la app y puede explicar que el servicio deje de contar tras un rato sin usar el móvil:
 * restricciones de batería, grupo de uso, hibernación de apps sin usar. Va en la cabecera del registro de Calibración
 * para que baste con compartirlo (sin cable ni adb). Cada dato es `null` si esa versión de Android no lo da o si
 * falla al leerlo. Solo habla de esta app: no hace falta ningún permiso ni sale nada del usuario.
 */
data class SystemState(
    /** `UsageStatsManager.getAppStandbyBucket` (Android 9+): 10 activa … 40 rara, 45 restringida. */
    val standbyBucket: Int? = null,
    /** Exenta de la optimización de batería («Sin restricciones»). */
    val batteryUnrestricted: Boolean? = null,
    /** Uso en segundo plano restringido por el usuario o por el sistema (Android 9+). */
    val backgroundRestricted: Boolean? = null,
    /** Ahorro de batería del móvil encendido ahora mismo. */
    val powerSave: Boolean? = null,
    /** El usuario ha apagado «Pausar actividad de la app si no se usa» para esta app (Android 11+). */
    val hibernationExempt: Boolean? = null,
    val notificationsAllowed: Boolean? = null,
    /** Tiempo que lleva vivo el proceso. */
    val processAgeMs: Long? = null,
) {
    companion object {
        /** Nombre de Android de cada grupo de uso (no se traduce: es el que sale en `adb` y en la documentación). */
        fun bucketName(bucket: Int): String = when (bucket) {
            5 -> "EXEMPTED"
            10 -> "ACTIVE"
            20 -> "WORKING_SET"
            30 -> "FREQUENT"
            40 -> "RARE"
            45 -> "RESTRICTED"
            50 -> "NEVER"
            else -> "?"
        }

        /** ¿Puede Android pausar la app por no usarla? Solo existe en Android 11+ (antes, `null`). */
        fun hibernationExempt(context: Context): Boolean? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { context.packageManager.isAutoRevokeWhitelisted }.getOrNull()
            } else {
                null
            }

        /** Lee el estado. Nunca lanza. Hace llamadas al sistema: fuera del hilo principal. */
        fun read(context: Context): SystemState {
            val app = context.applicationContext
            val power = runCatching { app.getSystemService(PowerManager::class.java) }.getOrNull()
            val activity = runCatching { app.getSystemService(ActivityManager::class.java) }.getOrNull()
            val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            return SystemState(
                standbyBucket = if (modern) {
                    runCatching { app.getSystemService(UsageStatsManager::class.java)?.appStandbyBucket }.getOrNull()
                } else {
                    null
                },
                batteryUnrestricted = runCatching { power?.isIgnoringBatteryOptimizations(app.packageName) }.getOrNull(),
                backgroundRestricted = if (modern) runCatching { activity?.isBackgroundRestricted }.getOrNull() else null,
                powerSave = runCatching { power?.isPowerSaveMode }.getOrNull(),
                hibernationExempt = hibernationExempt(app),
                notificationsAllowed = runCatching { NotificationManagerCompat.from(app).areNotificationsEnabled() }.getOrNull(),
                processAgeMs = runCatching { ServiceHealth.processAgeMs() }.getOrNull(),
            )
        }
    }
}
