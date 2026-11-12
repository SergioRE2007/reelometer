package com.reelometro.app.detection

import android.os.Build

/**
 * Guía por fabricante para que el servicio de accesibilidad no se quede «activado, pero sin funcionar»: qué pasos
 * enseñar y qué pantallas de ajustes intentar abrir en cada marca. Lógica pura (sin Android) para poder probarla; la
 * pantalla convierte cada [IntentSpec] en un `Intent` y prueba los candidatos en orden (`SystemScreens.openOem`).
 *
 * Los nombres de componentes de cada marca son los conocidos de sus ajustes y cambian entre versiones: por eso cada
 * lista acaba siempre en pantallas genéricas de Android («Información de la aplicación», batería, Ajustes).
 *
 * No se lee la versión de la ROM (`ro.miui.ui.version.name` y similares): exigiría API oculta por reflexión y no
 * cambia los pasos (los de Xiaomi cubren MIUI y HyperOS).
 */
enum class OemFamily { XIAOMI, SAMSUNG, HUAWEI, HONOR, COLOROS, VIVO, STOCK }

/** Fabricante y marca del móvil (se inyecta para poder probar la regla sin `Build`). */
data class DeviceInfo(val manufacturer: String, val brand: String, val sdk: Int) {
    companion object {
        fun current(): DeviceInfo = DeviceInfo(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            sdk = Build.VERSION.SDK_INT,
        )
    }
}

/** Pantalla de ajustes a la que lleva un paso. */
enum class OemScreen { AUTOSTART, BATTERY_APP, BATTERY_SETTINGS, APP_INFO, ACCESSIBILITY, UNUSED_APPS }

/** Qué pide un paso (decide su texto). */
enum class OemStepKind { AUTOSTART, BATTERY, NEVER_SLEEP, LOCK_RECENTS, TOGGLE_SERVICE, OTHER_MANAGERS, UNUSED_APPS }

/** Un paso de la guía; [screen] null = solo texto (no hay pantalla que abrir, p. ej. bloquear en recientes). */
data class OemStep(val kind: OemStepKind, val screen: OemScreen?)

/**
 * Descripción de un `Intent` sin Android. [packageUri]: datos `package:<app>` (pantallas de una app concreta).
 * [pkg] y [cls] null = intent genérico del sistema (por acción).
 */
data class IntentSpec(
    val action: String? = null,
    val pkg: String? = null,
    val cls: String? = null,
    val packageUri: Boolean = false,
    val extras: Map<String, String> = emptyMap(),
) {
    /** Pantalla propia de una marca (no genérica de Android). */
    val isOem: Boolean get() = pkg != null
}

object OemGuide {

    // Acciones de android.provider.Settings / android.content.Intent, como texto para que la regla sea JVM pura.
    const val ACTION_APP_DETAILS = "android.settings.APPLICATION_DETAILS_SETTINGS"
    const val ACTION_APPLICATION_SETTINGS = "android.settings.APPLICATION_SETTINGS"
    const val ACTION_IGNORE_BATTERY_OPTIMIZATION = "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"
    const val ACTION_POWER_USAGE_SUMMARY = "android.intent.action.POWER_USAGE_SUMMARY"
    const val ACTION_ACCESSIBILITY = "android.settings.ACCESSIBILITY_SETTINGS"
    const val ACTION_SETTINGS = "android.settings.SETTINGS"

    /** `Intent.ACTION_AUTO_REVOKE_PERMISSIONS` (Android 11+): la ficha de la app con «Pausar actividad si no se usa». */
    const val ACTION_AUTO_REVOKE = "android.intent.action.AUTO_REVOKE_PERMISSIONS"

    private const val MIUI_SECURITY = "com.miui.securitycenter"
    private const val MIUI_POWER = "com.miui.powerkeeper"
    private const val HUAWEI_MANAGER = "com.huawei.systemmanager"
    private const val HONOR_MANAGER = "com.hihonor.systemmanager"
    private const val COLOROS_SAFE = "com.coloros.safecenter"
    private const val OPPO_SAFE = "com.oppo.safe"
    private const val ONEPLUS_SECURITY = "com.oneplus.security"
    private const val VIVO_PERMISSIONS = "com.vivo.permissionmanager"
    private const val IQOO_SECURE = "com.iqoo.secure"
    private const val SAMSUNG_LOOL = "com.samsung.android.lool"
    private const val SAMSUNG_SM = "com.samsung.android.sm"

    /** Paquetes de ajustes de marca que se intentan abrir: deben estar en `<queries>` del manifiesto (lo comprueba un test). */
    val OEM_PACKAGES: Set<String> = setOf(
        MIUI_SECURITY, MIUI_POWER, HUAWEI_MANAGER, HONOR_MANAGER, COLOROS_SAFE, OPPO_SAFE, ONEPLUS_SECURITY,
        VIVO_PERMISSIONS, IQOO_SECURE, SAMSUNG_LOOL, SAMSUNG_SM,
    )

    /** Familia de ajustes del móvil a partir del fabricante y la marca (sin distinguir mayúsculas). */
    fun family(info: DeviceInfo): OemFamily {
        val names = listOf(info.manufacturer, info.brand).map { it.trim().lowercase(java.util.Locale.ROOT) }
        fun any(vararg keys: String) = names.any { it in keys }
        return when {
            any("xiaomi", "redmi", "poco") -> OemFamily.XIAOMI
            any("samsung") -> OemFamily.SAMSUNG
            // Honor antes que Huawei: los Honor antiguos dicen «HUAWEI» de fabricante y «HONOR» de marca.
            any("honor") -> OemFamily.HONOR
            any("huawei") -> OemFamily.HUAWEI
            any("oppo", "realme", "oneplus") -> OemFamily.COLOROS
            any("vivo", "iqoo") -> OemFamily.VIVO
            else -> OemFamily.STOCK
        }
    }

    /**
     * Pasos en orden para la familia: lo que más suele fallar primero; apagar y encender el servicio, al final.
     *
     * @param unusedApps Android puede pausar la app por no usarla (Android 11+ y el usuario no la ha eximido): se
     *   añade, justo antes del último, el paso de apagar «Pausar actividad de la app si no se usa». Con el servicio
     *   activado Android de serie no la pausa, pero si el sistema lo apaga la app queda expuesta; es una red, no el
     *   arreglo principal, y por eso va detrás de los de la marca.
     */
    fun steps(family: OemFamily, unusedApps: Boolean = false): List<OemStep> {
        val steps = brandSteps(family)
        if (!unusedApps) return steps
        return steps.dropLast(1) + OemStep(OemStepKind.UNUSED_APPS, OemScreen.UNUSED_APPS) + steps.last()
    }

    private fun brandSteps(family: OemFamily): List<OemStep> = when (family) {
        OemFamily.XIAOMI -> listOf(
            OemStep(OemStepKind.AUTOSTART, OemScreen.AUTOSTART),
            OemStep(OemStepKind.BATTERY, OemScreen.BATTERY_APP),
            OemStep(OemStepKind.LOCK_RECENTS, null),
            toggle,
        )
        OemFamily.SAMSUNG -> listOf(
            OemStep(OemStepKind.BATTERY, OemScreen.APP_INFO),
            OemStep(OemStepKind.NEVER_SLEEP, OemScreen.BATTERY_SETTINGS),
            OemStep(OemStepKind.LOCK_RECENTS, null),
            toggle,
        )
        OemFamily.HUAWEI, OemFamily.HONOR -> listOf(
            OemStep(OemStepKind.AUTOSTART, OemScreen.AUTOSTART),
            OemStep(OemStepKind.LOCK_RECENTS, null),
            toggle,
        )
        OemFamily.COLOROS -> listOf(
            OemStep(OemStepKind.AUTOSTART, OemScreen.AUTOSTART),
            OemStep(OemStepKind.BATTERY, OemScreen.APP_INFO),
            OemStep(OemStepKind.LOCK_RECENTS, null),
            toggle,
        )
        OemFamily.VIVO -> listOf(
            OemStep(OemStepKind.AUTOSTART, OemScreen.AUTOSTART),
            OemStep(OemStepKind.BATTERY, OemScreen.BATTERY_SETTINGS),
            OemStep(OemStepKind.LOCK_RECENTS, null),
            toggle,
        )
        OemFamily.STOCK -> listOf(
            OemStep(OemStepKind.BATTERY, OemScreen.APP_INFO),
            OemStep(OemStepKind.OTHER_MANAGERS, null),
            toggle,
        )
    }

    private val toggle = OemStep(OemStepKind.TOGGLE_SERVICE, OemScreen.ACCESSIBILITY)

    /**
     * Intents a probar, en orden, para abrir [screen] en [family]. Siempre acaban en pantallas genéricas de Android
     * (la última, sin paquete). Nunca `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (exige un permiso aparte).
     */
    fun candidates(screen: OemScreen, family: OemFamily, appPackage: String, appLabel: String): List<IntentSpec> {
        val appDetails = IntentSpec(action = ACTION_APP_DETAILS, packageUri = true)
        val settings = IntentSpec(action = ACTION_SETTINGS)
        val batteryGeneric = listOf(appDetails, IntentSpec(action = ACTION_IGNORE_BATTERY_OPTIMIZATION), settings)
        return when (screen) {
            OemScreen.AUTOSTART -> autostart(family) + listOf(appDetails, settings)
            OemScreen.BATTERY_APP -> batteryApp(family, appPackage, appLabel) + batteryGeneric
            OemScreen.BATTERY_SETTINGS ->
                batterySettings(family) + IntentSpec(action = ACTION_POWER_USAGE_SUMMARY) + batteryGeneric
            OemScreen.APP_INFO -> listOf(appDetails, IntentSpec(action = ACTION_APPLICATION_SETTINGS), settings)
            OemScreen.ACCESSIBILITY -> listOf(IntentSpec(action = ACTION_ACCESSIBILITY), settings)
            // La acción no existe antes de Android 11: entonces se cae a la ficha de la app (el paso no se enseña ahí).
            OemScreen.UNUSED_APPS -> listOf(IntentSpec(action = ACTION_AUTO_REVOKE, packageUri = true), appDetails, settings)
        }
    }

    private fun component(pkg: String, cls: String, extras: Map<String, String> = emptyMap()) =
        IntentSpec(pkg = pkg, cls = cls, extras = extras)

    private val huaweiAutostart = listOf(
        component(HUAWEI_MANAGER, "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        component(HUAWEI_MANAGER, "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        component(HUAWEI_MANAGER, "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
    )

    private fun autostart(family: OemFamily): List<IntentSpec> = when (family) {
        OemFamily.XIAOMI -> listOf(
            component(MIUI_SECURITY, "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        OemFamily.HUAWEI -> huaweiAutostart
        OemFamily.HONOR -> listOf(
            component(HONOR_MANAGER, "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ) + huaweiAutostart
        OemFamily.COLOROS -> listOf(
            component(COLOROS_SAFE, "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            component(COLOROS_SAFE, "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            component(OPPO_SAFE, "com.oppo.safe.permission.startup.StartupAppListActivity"),
            component(ONEPLUS_SECURITY, "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        )
        OemFamily.VIVO -> listOf(
            component(VIVO_PERMISSIONS, "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            component(IQOO_SECURE, "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            component(IQOO_SECURE, "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        )
        OemFamily.SAMSUNG, OemFamily.STOCK -> emptyList()
    }

    private fun batteryApp(family: OemFamily, appPackage: String, appLabel: String): List<IntentSpec> = when (family) {
        OemFamily.XIAOMI -> listOf(
            component(
                MIUI_POWER,
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
                extras = mapOf("package_name" to appPackage, "package_label" to appLabel),
            ),
        )
        else -> emptyList()
    }

    private fun batterySettings(family: OemFamily): List<IntentSpec> = when (family) {
        OemFamily.SAMSUNG -> listOf(
            component(SAMSUNG_LOOL, "com.samsung.android.sm.battery.ui.BatteryActivity"),
            component(SAMSUNG_SM, "com.samsung.android.sm.ui.battery.BatteryActivity"),
        )
        OemFamily.VIVO -> listOf(
            component(IQOO_SECURE, "com.iqoo.secure.ui.phoneoptimize.SoftwareManagerActivity"),
        )
        else -> emptyList()
    }
}
