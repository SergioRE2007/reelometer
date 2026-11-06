package com.reelometro.app.ui.onboarding

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Process

/**
 * «Ajuste restringido» (Android 13+): Android no deja activar el servicio de accesibilidad de una app
 * instalada desde un archivo (navegador, gestor de archivos…) hasta que el usuario toca «Permitir
 * ajustes restringidos» en la información de la app. Las apps de Google Play (y las instaladas por
 * adb) no lo sufren.
 *
 * [isRestricted] es la decisión pura (con tests); [read] reúne los datos de Android sin lanzar nunca.
 */
object RestrictedSettings {

    /** AppOp (oculto) en el que Android guarda si el usuario ha permitido los ajustes restringidos. */
    const val APP_OP = "android:access_restricted_settings"

    /**
     * ¿Va a necesitar el usuario «Permitir ajustes restringidos»?
     *
     * - Antes de Android 13 no existe → `false`.
     * - Si el AppOp responde: `MODE_ERRORED`/`MODE_IGNORED` = bloqueado; `MODE_ALLOWED` = permitido (o
     *   nunca restringido). Cualquier otro modo no decide y se mira el origen.
     * - Sin AppOp (fabricantes que lo quitan, versiones futuras): instalada desde un archivo local o
     *   descargado ([PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE] / `PACKAGE_SOURCE_DOWNLOADED_FILE`).
     *
     * @param appOpMode resultado de `unsafeCheckOpNoThrow(APP_OP…)`, o `null` si no se pudo leer.
     * @param packageSource `InstallSourceInfo.packageSource`, o `null` si no se pudo leer.
     */
    @SuppressLint("InlinedApi") // Guarda en la primera rama (`sdkInt < TIRAMISU`), que lint no ve.
    fun isRestricted(sdkInt: Int, appOpMode: Int?, packageSource: Int?): Boolean = when {
        sdkInt < Build.VERSION_CODES.TIRAMISU -> false
        appOpMode == AppOpsManager.MODE_ALLOWED -> false
        appOpMode == AppOpsManager.MODE_ERRORED || appOpMode == AppOpsManager.MODE_IGNORED -> true
        else -> packageSource == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE ||
            packageSource == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
    }

    /** Lee el AppOp y el origen de la instalación de esta app. Nunca lanza. */
    fun read(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val mode = runCatching {
            context.getSystemService(AppOpsManager::class.java)
                ?.unsafeCheckOpNoThrow(APP_OP, Process.myUid(), context.packageName)
        }.getOrNull()
        val source = runCatching {
            context.packageManager.getInstallSourceInfo(context.packageName).packageSource
        }.getOrNull()
        return isRestricted(Build.VERSION.SDK_INT, mode, source)
    }
}
