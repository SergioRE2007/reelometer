package com.reelometro.app.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.reelometro.app.MainActivity
import com.reelometro.app.R
import com.reelometro.app.data.Platform
import com.reelometro.app.detection.ExitCategory
import com.reelometro.app.detection.IntentSpec
import com.reelometro.app.detection.OemFamily
import com.reelometro.app.detection.OemGuide
import com.reelometro.app.detection.OemScreen
import com.reelometro.app.detection.ProcessExits
import com.reelometro.app.detection.ReelAccessibilityService
import com.reelometro.app.detection.ServiceHealth
import com.reelometro.app.detection.ServiceJournal
import com.reelometro.app.detection.ServiceJournalRules
import com.reelometro.app.detection.ServiceStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Pantallas de Ajustes de Android que abre la app. Si un fabricante no tiene la pantalla concreta,
 * se cae a una más general en vez de fallar. Devuelven `false` si no se pudo abrir nada.
 */
internal object SystemScreens {

    /** Ajustes › Accesibilidad. Solo después de aceptar la divulgación destacada. */
    fun openAccessibility(context: Context): Boolean =
        start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) ||
            start(context, Intent(Settings.ACTION_SETTINGS))

    /** «Información de la aplicación» de Reelometer (menú ⋮ › «Permitir ajustes restringidos»). */
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

    /** Abre la app de vídeos para la prueba de un minuto (el paquete que esté instalado). */
    fun openApp(context: Context, platform: Platform): Boolean = platform.packages.any { pkg ->
        val launch = try {
            context.packageManager.getLaunchIntentForPackage(pkg)
        } catch (_: RuntimeException) {
            null
        }
        launch != null && start(context, launch)
    }

    /** Otra pantalla de Reelometer por su ruta de enlace («calibration», «settings»; ver `DeepLinks`). */
    fun openRoute(context: Context, route: String): Boolean = start(context, MainActivity.intent(context, route))

    /** Cómo ha ido abrir una pantalla de la guía por marca. */
    enum class OpenResult {
        /** Se ha abierto la pantalla propia de la marca. */
        EXACT,

        /** Solo una genérica de Android («Información de la aplicación», batería, Ajustes). */
        FALLBACK,

        /** Nada: la pantalla avisa y sugiere el buscador de Ajustes. */
        NONE,
    }

    /**
     * Abre [screen] para [family] probando en orden [OemGuide.candidates]: se salta lo que no existe en el móvil
     * (`resolveActivity` en las de marca, que necesita los paquetes en `<queries>`) y lo que falla al abrir (algunas ROM lanzan
     * `SecurityException` con actividades no exportadas).
     */
    fun openOem(context: Context, screen: OemScreen, family: OemFamily): OpenResult {
        val label = context.getString(R.string.app_name)
        for (spec in OemGuide.candidates(screen, family, context.packageName, label)) {
            val intent = spec.toIntent(context.packageName)
            // Solo las pantallas de marca se comprueban antes: las genéricas de Android (Ajustes, Información de la
            // aplicación…) pueden no resolverse por la visibilidad de paquetes aun existiendo, y se lanzan directamente.
            if (spec.isOem && context.packageManager.resolveActivity(intent, 0) == null) continue
            if (start(context, intent)) return if (spec.isOem) OpenResult.EXACT else OpenResult.FALLBACK
        }
        return OpenResult.NONE
    }

    private fun IntentSpec.toIntent(appPackage: String): Intent {
        val intent = if (action != null) Intent(action) else Intent()
        if (pkg != null && cls != null) intent.component = ComponentName(pkg, cls)
        if (packageUri) intent.data = Uri.fromParts("package", appPackage, null)
        extras.forEach { (k, v) -> intent.putExtra(k, v) }
        return intent
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: RuntimeException) {
        // SecurityException (actividad de marca no exportada), IllegalArgumentException… : se prueba la siguiente.
        false
    }
}

/** Estado del servicio de accesibilidad visto desde las pantallas (interfaz para poder probar los ViewModels). */
interface ServiceProbe {
    /** Activado en Ajustes › Accesibilidad. */
    fun isServiceEnabled(): Boolean

    /** Hará falta «Permitir ajustes restringidos» ([RestrictedSettings]). */
    fun isRestricted(): Boolean

    /** Activado, pero el sistema no lo ha conectado o su arranque ha fallado ([ServiceHealth.STALLED]). */
    fun isServiceStalled(): Boolean = false

    /** Texto del fallo del arranque, si lo hubo ([ServiceStatus.startError]). */
    fun startError(): String? = null

    /** Por qué se cerró la app la última vez, si Android lo guarda ([ProcessExits.stallReason]). */
    fun stallReason(): ExitCategory? = null

    /**
     * El servicio funcionaba y está apagado sin que el usuario lo apagara: lo quitó el sistema
     * ([ServiceJournalRules.lostBySystem]). Solo tiene sentido con el servicio apagado.
     */
    fun isServiceLost(): Boolean = false

    /** Cuándo volver a mirar sin esperar a `ON_RESUME` ([ServiceHealth.changes]). */
    fun changes(): Flow<Unit> = emptyFlow()
}

/** [ServiceProbe] real. Usa el contexto de la aplicación (no retiene la actividad). */
class AndroidServiceProbe(context: Context) : ServiceProbe {
    private val app = context.applicationContext

    override fun isServiceEnabled(): Boolean = ReelAccessibilityService.isEnabled(app)

    override fun isRestricted(): Boolean = RestrictedSettings.read(app)

    override fun isServiceStalled(): Boolean = ServiceHealth.current(app) == ServiceHealth.STALLED

    override fun startError(): String? = ServiceStatus.startError.value

    override fun stallReason(): ExitCategory? = ProcessExits.stallReason(app)?.category

    override fun isServiceLost(): Boolean = ServiceJournalRules.lostBySystem(enabled = false, ServiceJournal(app).read())

    override fun changes(): Flow<Unit> = ServiceHealth.changes()
}
