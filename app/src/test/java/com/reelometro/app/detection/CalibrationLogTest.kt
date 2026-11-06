package com.reelometro.app.detection

import com.reelometro.app.data.Platform
import org.junit.After
import org.junit.Assert.assertEquals
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
}
