package com.reelometro.app.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guía por marca: qué familia es cada móvil, qué pasos se enseñan y qué pantallas se intentan abrir. */
class OemGuideTest {

    private fun family(manufacturer: String, brand: String = manufacturer) =
        OemGuide.family(DeviceInfo(manufacturer, brand, 34))

    @Test
    fun `detecta la familia por fabricante o marca sin distinguir mayúsculas`() {
        assertEquals(OemFamily.XIAOMI, family("Xiaomi"))
        assertEquals(OemFamily.XIAOMI, family("Redmi"))
        assertEquals(OemFamily.XIAOMI, family("POCO"))
        assertEquals(OemFamily.XIAOMI, family("Xiaomi", "Redmi"))
        assertEquals(OemFamily.SAMSUNG, family("samsung"))
        assertEquals(OemFamily.HUAWEI, family("HUAWEI"))
        assertEquals(OemFamily.HONOR, family("HONOR"))
        assertEquals("Honor antiguo: fabricante Huawei, marca Honor", OemFamily.HONOR, family("HUAWEI", "HONOR"))
        assertEquals(OemFamily.COLOROS, family("OPPO"))
        assertEquals(OemFamily.COLOROS, family("realme"))
        assertEquals(OemFamily.COLOROS, family("OnePlus"))
        assertEquals(OemFamily.VIVO, family("vivo"))
        assertEquals(OemFamily.VIVO, family("vivo", "iQOO"))
        assertEquals(OemFamily.STOCK, family("Google"))
        assertEquals(OemFamily.STOCK, family("motorola"))
        assertEquals(OemFamily.STOCK, family(""))
        assertEquals(OemFamily.STOCK, family(" "))
    }

    @Test
    fun `cada familia acaba en apagar y encender el servicio y tiene un paso de batería o inicio automático`() {
        for (f in OemFamily.entries) {
            val steps = OemGuide.steps(f)
            assertEquals(f.name, OemStepKind.TOGGLE_SERVICE, steps.last().kind)
            assertEquals(f.name, OemScreen.ACCESSIBILITY, steps.last().screen)
            val kinds = steps.map { it.kind }.toSet()
            if (f == OemFamily.HUAWEI || f == OemFamily.HONOR) {
                assertTrue(f.name, OemStepKind.AUTOSTART in kinds)
            } else {
                assertTrue(f.name, OemStepKind.AUTOSTART in kinds || OemStepKind.BATTERY in kinds)
            }
            steps.filter { it.kind == OemStepKind.LOCK_RECENTS || it.kind == OemStepKind.OTHER_MANAGERS }
                .forEach { assertNull("${f.name}: ${it.kind} es solo texto", it.screen) }
        }
    }

    @Test
    fun `los candidatos acaban siempre en una pantalla genérica de Android`() {
        val appInfoScreens = setOf(
            OemScreen.BATTERY_APP, OemScreen.BATTERY_SETTINGS, OemScreen.APP_INFO, OemScreen.AUTOSTART, OemScreen.UNUSED_APPS,
        )
        for (f in OemFamily.entries) for (s in OemScreen.entries) {
            val list = OemGuide.candidates(s, f, "com.reelometro.app", "Reelometer")
            assertTrue("$f/$s", list.isNotEmpty())
            assertNull("$f/$s acaba en genérico", list.last().pkg)
            assertTrue("$f/$s: los genéricos van por acción", list.filter { !it.isOem }.all { it.cls == null && it.action != null })
            if (s in appInfoScreens) {
                assertTrue(
                    "$f/$s incluye «Información de la aplicación»",
                    list.any { it.action == OemGuide.ACTION_APP_DETAILS && it.packageUri },
                )
            }
            assertTrue("nunca pide el permiso de ignorar la optimización", list.none { "REQUEST_IGNORE" in (it.action ?: "") })
        }
    }

    @Test
    fun `Xiaomi abre primero su gestor de inicio automático y el ahorro de batería de la app`() {
        val autostart = OemGuide.candidates(OemScreen.AUTOSTART, OemFamily.XIAOMI, "pkg", "App")
        assertEquals("com.miui.securitycenter", autostart.first().pkg)
        assertTrue(autostart.first().isOem)
        val battery = OemGuide.candidates(OemScreen.BATTERY_APP, OemFamily.XIAOMI, "pkg", "App").first()
        assertEquals("com.miui.powerkeeper", battery.pkg)
        assertEquals(mapOf("package_name" to "pkg", "package_label" to "App"), battery.extras)
    }

    @Test
    fun `Android de serie no intenta componentes de marca`() {
        for (s in OemScreen.entries) {
            assertTrue(s.name, OemGuide.candidates(s, OemFamily.STOCK, "pkg", "App").none { it.isOem })
        }
    }

    @Test
    fun `todos los paquetes de marca están declarados en OEM_PACKAGES`() {
        val used = OemFamily.entries.flatMap { f ->
            OemScreen.entries.flatMap { s -> OemGuide.candidates(s, f, "pkg", "App").mapNotNull { it.pkg } }
        }.toSet()
        assertEquals("ni de menos ni de más", OemGuide.OEM_PACKAGES, used)
    }

    @Test
    fun `los paquetes de marca están en queries del manifiesto (sin ellos resolveActivity da null)`() {
        val file = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.exists() }
        val queries = Regex("<queries>(.*?)</queries>", RegexOption.DOT_MATCHES_ALL).find(file.readText())!!.groupValues[1]
        for (pkg in OemGuide.OEM_PACKAGES) {
            assertTrue("$pkg en <queries>", "<package android:name=\"$pkg\" />" in queries)
        }
    }

    @Test
    fun `si Android puede pausar la app por no usarla, se añade ese paso justo antes de apagar y encender`() {
        for (f in OemFamily.entries) {
            val plain = OemGuide.steps(f)
            assertTrue(f.name, plain.none { it.kind == OemStepKind.UNUSED_APPS })
            val steps = OemGuide.steps(f, unusedApps = true)
            assertEquals(f.name, plain.size + 1, steps.size)
            assertEquals("${f.name}: los de la marca, en el mismo orden y delante", plain.dropLast(1), steps.dropLast(2))
            assertEquals(f.name, OemStep(OemStepKind.UNUSED_APPS, OemScreen.UNUSED_APPS), steps[steps.size - 2])
            assertEquals(f.name, OemStepKind.TOGGLE_SERVICE, steps.last().kind)
        }
    }

    @Test
    fun `el paso de apps sin usar abre la ficha de la app por la accion de Android y, si no existe, la de siempre`() {
        val list = OemGuide.candidates(OemScreen.UNUSED_APPS, OemFamily.XIAOMI, "pkg", "App")
        assertEquals(OemGuide.ACTION_AUTO_REVOKE, list.first().action)
        assertTrue("lleva el paquete de la app", list.first().packageUri)
        assertEquals(OemGuide.ACTION_APP_DETAILS, list[1].action)
        assertTrue(list.none { it.isOem })
    }
}
