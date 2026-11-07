package com.reelometro.app.detection

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La APK que se publica es la debug: sus ganchos de QA por difusión (el `DebugReceiver` del manifiesto de debug y los avisos
 * `DUMP_SHEET` y `QUICK_SEND` del servicio) solo los puede mandar quien tenga el permiso DUMP (el shell de adb, con
 * `adb shell am broadcast`), nunca otra app instalada. Se comprueba sobre el código: el sistema aplica el permiso al
 * entregar la difusión y eso no se puede probar en la JVM.
 */
class DebugHooksPermissionTest {

    private fun source(path: String): String =
        (listOf(File(path), File("app", path)).firstOrNull { it.isFile } ?: error("No está $path")).readText()

    @Test
    fun `los receptores exportados del manifiesto de debug exigen el permiso DUMP`() {
        val manifest = source("src/debug/AndroidManifest.xml")
        val receivers = Regex("<receiver\\b[^>]*>").findAll(manifest).map { it.value }.toList()
        assertTrue("el manifiesto de debug declara el DebugReceiver", receivers.any { "DebugReceiver" in it })
        receivers.filter { "android:exported=\"true\"" in it }.forEach { receiver ->
            assertTrue(receiver, "android:permission=\"android.permission.DUMP\"" in receiver)
        }
    }

    @Test
    fun `el receptor de depuracion del servicio exige el permiso DUMP`() {
        val service = source("src/main/java/com/reelometro/app/detection/ReelAccessibilityService.kt")
        assertTrue(Regex("DEBUG_HOOKS_PERMISSION\\s*=\\s*android\\.Manifest\\.permission\\.DUMP\\b").containsMatchIn(service))
        val exported = Regex("\\bRECEIVER_EXPORTED\\b").findAll(service).toList()
        assertTrue("el servicio registra su receptor de depuración", exported.isNotEmpty())
        exported.forEach { match ->
            val call = service.substring(service.lastIndexOf("registerReceiver(", match.range.first), match.range.last + 1)
            assertTrue(call, "DEBUG_HOOKS_PERMISSION" in call)
        }
    }
}
