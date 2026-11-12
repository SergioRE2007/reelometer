package com.reelometro.app.detection

import com.reelometro.app.data.Platform
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset

class CalibrationLogTest {

    @Before
    fun setUp() {
        CalibrationLog.enabled.value = false
        CalibrationLog.clear()
        CalibrationLog.environment = null
    }

    @After
    fun tearDown() = setUp()

    @Test
    fun `desactivado no guarda nada ni construye el evento`() {
        CalibrationLog.log { error("no debería evaluarse") }
        assertTrue(CalibrationLog.events.value.isEmpty())
    }

    @Test
    fun `guarda los mas recientes primero y como mucho la capacidad`() {
        CalibrationLog.enabled.value = true
        repeat(CalibrationLog.CAPACITY + 20) { i ->
            CalibrationLog.log { CalibrationEvent(i.toLong(), CalibrationEventType.SCROLL, platform = Platform.TIKTOK) }
        }
        val events = CalibrationLog.events.value
        assertEquals(CalibrationLog.CAPACITY, events.size)
        assertEquals(CalibrationLog.CAPACITY + 19L, events.first().time)
        CalibrationLog.clear()
        assertTrue(CalibrationLog.events.value.isEmpty())
    }

    @Test
    fun `exporta texto plano en orden cronologico con la decision y la regla`() {
        CalibrationLog.enabled.value = true
        CalibrationLog.environment = "Pixel 8 · Android 16"
        CalibrationLog.log {
            CalibrationEvent(
                1_000, CalibrationEventType.SCROLL, "com.zhiliaoapp.musically", Platform.TIKTOK,
                viewId = "com.zhiliaoapp.musically:id/e3k", className = "androidx.viewpager.widget.ViewPager",
                fromIndex = 3, toIndex = 3, scrollDeltaY = 900, nodeWidth = 1080, nodeHeight = 2240,
                screenWidth = 1080, screenHeight = 2400, decision = true, rule = "clase (ViewPager)",
            )
        }
        CalibrationLog.log { CalibrationEvent(2_000, CalibrationEventType.VIDEO_START, platform = Platform.TIKTOK, videoId = 7) }
        val text = CalibrationLog.exportText()
        assertTrue(text.contains("Pixel 8"))
        assertTrue(text.indexOf("SCROLL") < text.indexOf("VIDEO_START"))
        val line = CalibrationLog.events.value.last().toLine(ZoneOffset.UTC)
        assertTrue(line.startsWith("00:00:01.000 SCROLL TikTok ✓ [clase (ViewPager)]"))
        assertTrue(line.contains("id=e3k") && line.contains("idx=3→3") && line.contains("dy=900"))
    }

    @Test
    fun `las lineas de rendimiento van en la cabecera, despues del servicio y antes de los eventos`() {
        CalibrationLog.enabled.value = true
        CalibrationLog.log { CalibrationEvent(1_000, CalibrationEventType.SCROLL, platform = Platform.TIKTOK) }
        val text = CalibrationLog.exportText(
            fallbackEnvironment = "Pixel 8",
            service = ServiceHealth.OK,
            performance = listOf("RENDIMIENTO uno", "RENDIMIENTO dos"),
        )
        val service = text.indexOf("Servicio: OK")
        val first = text.indexOf("RENDIMIENTO uno")
        val second = text.indexOf("RENDIMIENTO dos")
        val events = text.indexOf("1 eventos (del más antiguo")
        assertTrue(text, service in 0 until first && first < second && second < events)
    }

    @Test
    fun `un grupo que se llama como una palabra del registro no toca la cabecera ni las claves`() {
        CalibrationLog.enabled.value = true
        CalibrationLog.environment = "Pixel 8 · clase media"
        CalibrationLog.log {
            CalibrationEvent(
                1_000, CalibrationEventType.SCROLL, platform = Platform.TIKTOK, className = "androidx.recyclerview.widget.RecyclerView",
                rule = "Clase nueva", note = "destino Clase · clic",
            )
        }
        val text = CalibrationLog.exportText(performance = listOf("RENDIMIENTO clic 3"), privateNames = listOf("Clase", "clic"))
        assertTrue(text, "clase=RecyclerView" in text)
        assertTrue(text, "Pixel 8 · clase media" in text)
        assertTrue(text, "RENDIMIENTO clic 3" in text)
        assertTrue(text, "[[#1] nueva]" in text)
        assertTrue(text, "· destino [#1] · [#2]" in text)
    }

    @Test
    fun `al exportar cambia los nombres privados por un token`() {
        CalibrationLog.enabled.value = true
        CalibrationLog.environment = "Pixel 8"
        CalibrationLog.log {
            CalibrationEvent(1_000, CalibrationEventType.SHARE, platform = Platform.INSTAGRAM, note = "enviado a Marta · destino 1")
        }
        val text = CalibrationLog.exportText(privateNames = listOf("Marta"))
        assertTrue(text, "[#1]" in text)
        assertFalse(text, "Marta" in text)
        assertTrue("La lista de la pantalla no se anonimiza", CalibrationLog.events.value.single().note!!.contains("Marta"))
        assertTrue(CalibrationLog.exportText(privateNames = listOf("Marta")) { "<$it>" }.contains("enviado a <1>"))
    }

    @Test
    fun `sin parametros nuevos la exportacion es la de siempre`() {
        CalibrationLog.enabled.value = true
        CalibrationLog.environment = "Pixel 8 · Android 16"
        CalibrationLog.log { CalibrationEvent(2_000, CalibrationEventType.VIDEO_START, platform = Platform.TIKTOK, videoId = 7, note = "Marta") }
        val text = CalibrationLog.exportText()
        assertEquals(text, CalibrationLog.exportText(performance = emptyList(), privateNames = emptyList()))
        val lines = text.lines()
        assertEquals("Reelometer · registro de calibración", lines[0])
        assertEquals("Pixel 8 · Android 16", lines[1])
        assertTrue(text, lines.any { it.startsWith("1 eventos") } && "Marta" in text && "\n\n\n" !in text)
    }

    @Test
    fun `el estado del servicio publica eventos como mucho una vez por segundo`() {
        val base = 9_000_000_000L
        ServiceStatus.onEvent(base)
        ServiceStatus.onEvent(base + 400)
        assertEquals(base, ServiceStatus.lastEventAt.value)
        ServiceStatus.onEvent(base + 1_000)
        assertEquals(base + 1_000, ServiceStatus.lastEventAt.value)
        ServiceStatus.onVideo(base + 1_200)
        assertEquals(base + 1_200, ServiceStatus.lastVideoAt.value)
    }

    @Test
    fun `el estado del sistema va en la cabecera, detras de los cierres y antes de los eventos, y sin nombres`() {
        CalibrationLog.enabled.value = true
        CalibrationLog.log { CalibrationEvent(1_000, CalibrationEventType.SCROLL, platform = Platform.TIKTOK) }
        val text = CalibrationLog.exportText(
            performance = listOf("RENDIMIENTO uno"),
            exits = listOf("CIERRE uno"),
            system = listOf("Estado del sistema:", "Conexiones del servicio: 10 nov 14:32 (Marta)"),
            privateNames = listOf("Marta"),
        )
        val exits = text.indexOf("CIERRE uno")
        val system = text.indexOf("Estado del sistema:")
        val events = text.indexOf("1 eventos (del más antiguo")
        assertTrue(text, text.indexOf("RENDIMIENTO uno") in 0 until exits && exits < system && system < events)
        assertFalse(text, "Marta" in text)
    }

    @Test
    fun `sin rendimiento ni cierres, el estado del sistema sigue separado de la cabecera`() {
        val text = CalibrationLog.exportText(fallbackEnvironment = "Pixel 8", system = listOf("Estado del sistema:"))
        assertTrue(text, "\n\nEstado del sistema:\n\n0 eventos" in text)
    }

    @Test
    fun `los avisos de poca memoria se cuentan, menos el de esconder la interfaz`() {
        val before = ServiceStatus.memoryWarnings.value.count
        ServiceStatus.onTrimMemory(20, 1_000) // TRIM_MEMORY_UI_HIDDEN: no dice nada de la memoria
        assertEquals(before, ServiceStatus.memoryWarnings.value.count)
        ServiceStatus.onTrimMemory(40, 2_000)
        val after = ServiceStatus.memoryWarnings.value
        assertEquals(before + 1, after.count)
        assertEquals(40, after.lastLevel)
        assertEquals(2_000L, after.lastAt)
    }
}
