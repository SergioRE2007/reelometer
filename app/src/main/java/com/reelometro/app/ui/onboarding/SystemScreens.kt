package com.reelometro.app.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.reelometro.app.detection.ReelAccessibilityService

/**
 * Pantallas de Ajustes de Android que abre la app. Si un fabricante no tiene la pantalla concreta,
 * se cae a una más general en vez de fallar. Devuelven `false` si no se pudo abrir nada.
 */
internal object SystemScreens {

    /** Ajustes › Accesibilidad. Solo después de aceptar la divulgación destacada. */
    fun openAccessibility(context: Context): Boolean =
        start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) ||
            start(context, Intent(Settings.ACTION_SETTINGS))

    /** «Información de la aplicación» de Reelómetro (menú ⋮ › «Permitir ajustes restringidos»). */
    fun openAppInfo(context: Context): Boolean =
        start(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        ) || start(context, Intent(Settings.ACTION_APPLICATION_SETTINGS))

    /** Ajustes de notificaciones de la app (si el permiso ya no se puede pedir con el diálogo). */
    fun openNotificationSettings(context: Context): Boolean =
        start(
            context,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        ) || openAppInfo(context)

    private fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/** Estado del servicio de accesibilidad visto desde las pantallas (interfaz para poder probar los ViewModels). */
interface ServiceProbe {
    /** Activado en Ajustes › Accesibilidad. */
    fun isServiceEnabled(): Boolean

    /** Hará falta «Permitir ajustes restringidos» ([RestrictedSettings]). */
    fun isRestricted(): Boolean
}

/** [ServiceProbe] real. Usa el contexto de la aplicación (no retiene la actividad). */
class AndroidServiceProbe(context: Context) : ServiceProbe {
    private val app = context.applicationContext

    override fun isServiceEnabled(): Boolean = ReelAccessibilityService.isEnabled(app)

    override fun isRestricted(): Boolean = RestrictedSettings.read(app)
}
