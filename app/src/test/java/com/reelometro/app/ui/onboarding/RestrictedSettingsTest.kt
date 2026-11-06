package com.reelometro.app.ui.onboarding

import android.app.AppOpsManager
import android.content.pm.PackageInstaller
import com.reelometro.app.ui.onboarding.RestrictedSettings.isRestricted
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestrictedSettingsTest {

    private val localFile = PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE
    private val downloaded = PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
    private val store = PackageInstaller.PACKAGE_SOURCE_STORE
    private val other = PackageInstaller.PACKAGE_SOURCE_OTHER

    @Test
    fun `antes de Android 13 no existe el ajuste restringido`() {
        assertFalse(isRestricted(26, AppOpsManager.MODE_ERRORED, localFile))
        assertFalse(isRestricted(32, AppOpsManager.MODE_ERRORED, downloaded))
    }

    @Test
    fun `si el AppOp responde, manda él`() {
        assertTrue("bloqueado aunque venga de una tienda", isRestricted(33, AppOpsManager.MODE_ERRORED, store))
        assertTrue(isRestricted(34, AppOpsManager.MODE_IGNORED, null))
        assertFalse("ya permitido por el usuario", isRestricted(33, AppOpsManager.MODE_ALLOWED, localFile))
        assertFalse(isRestricted(35, AppOpsManager.MODE_ALLOWED, downloaded))
    }

    @Test
    fun `sin AppOp, o con un modo que no decide, cuenta el origen de la instalación`() {
        assertTrue(isRestricted(33, null, localFile))
        assertTrue(isRestricted(35, null, downloaded))
        assertTrue(isRestricted(36, AppOpsManager.MODE_DEFAULT, localFile))
        assertFalse("Google Play u otra tienda", isRestricted(33, null, store))
        assertFalse("adb u origen desconocido", isRestricted(33, null, other))
        assertFalse("sin ningún dato no se asusta a nadie", isRestricted(34, null, null))
    }
}
