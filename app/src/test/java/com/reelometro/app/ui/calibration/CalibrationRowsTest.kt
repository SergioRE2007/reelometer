package com.reelometro.app.ui.calibration

import com.reelometro.app.data.Category
import com.reelometro.app.data.Platform
import com.reelometro.app.data.RecentVideo
import com.reelometro.app.detection.CalibrationEvent
import com.reelometro.app.detection.CalibrationEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class CalibrationRowsTest {

    private val zone = ZoneOffset.UTC

    private fun event(time: Long, type: CalibrationEventType = CalibrationEventType.SCROLL, platform: Platform? = Platform.TIKTOK) =
        CalibrationEvent(time = time, type = type, platform = platform, decision = type == CalibrationEventType.SCROLL)

    private fun video(id: Long, startedAt: Long, platform: Platform = Platform.TIKTOK) =
        RecentVideo(id, platform, startedAt, durationMs = 12_300, creator = "pepito", caption = null, category = Category.HUMOR, shares = 1)

    @Test
    fun `eventos y vídeos guardados se mezclan del más reciente al más antiguo`() {
        val events = listOf(event(5_000), event(3_000, CalibrationEventType.VIDEO_START))
        val rows = CalibrationRows.build(
            events, listOf(1, 2), listOf(video(7, 1_000), video(8, 4_000)), since = 0,
            platform = null, type = TypeFilter.ALL, zone = zone,
        )
        assertEquals(listOf(5_000L, 4_000L, 3_000L, 1_000L), rows.map { it.time })
        assertEquals(listOf(1L, -8L, 2L, -7L), rows.map { it.key })
        assertEquals(listOf(false, true, true, true), rows.map { it.video })
    }

    @Test
    fun `los vídeos anteriores a la sesión no salen y los filtros se aplican`() {
        val events = listOf(
            event(5_000, platform = Platform.INSTAGRAM),
            event(4_500, CalibrationEventType.WINDOW, platform = Platform.TIKTOK),
            event(4_000, CalibrationEventType.VIDEO_END),
        )
        val videos = listOf(video(1, 4_200), video(2, 1_000))
        val keys = listOf(1L, 2L, 3L)
        val since = 2_000L

        val all = CalibrationRows.build(events, keys, videos, since, null, TypeFilter.ALL, zone)
        assertEquals(listOf(5_000L, 4_500L, 4_200L, 4_000L), all.map { it.time })

        val tiktok = CalibrationRows.build(events, keys, videos, since, Platform.TIKTOK, TypeFilter.ALL, zone)
        assertTrue(tiktok.all { it.platform == Platform.TIKTOK })

        val onlyVideos = CalibrationRows.build(events, keys, videos, since, null, TypeFilter.VIDEO, zone)
        assertEquals(listOf(4_200L, 4_000L), onlyVideos.map { it.time })

        val windows = CalibrationRows.build(events, keys, videos, since, null, TypeFilter.WINDOW, zone)
        assertEquals(listOf(CalibrationEventType.WINDOW), windows.map { it.type })
    }

    @Test
    fun `cada evento conserva su clave mientras sigue en el búfer`() {
        val keys = EventKeys()
        val a = event(1_000)
        val b = event(2_000)
        val first = keys.assign(listOf(b, a))
        val c = event(3_000)
        val second = keys.assign(listOf(c, b, a))
        assertEquals(first, second.drop(1))
        assertEquals(3, second.toSet().size)
        // Dos eventos idénticos (mismo milisegundo) no comparten clave.
        val twin = event(3_000)
        assertEquals(2, keys.assign(listOf(twin, c)).toSet().size)
    }

    @Test
    fun `los detalles técnicos caben en una línea legible`() {
        val e = CalibrationEvent(
            time = 0,
            type = CalibrationEventType.SCROLL,
            platform = Platform.INSTAGRAM,
            viewId = "com.instagram.android:id/clips_viewer_view_pager",
            className = "androidx.viewpager2.widget.ViewPager2",
            fromIndex = 3,
            toIndex = 4,
            itemCount = 120,
            scrollDeltaY = 1_840,
            nodeWidth = 1_080,
            nodeHeight = 2_208,
            screenWidth = 1_080,
            screenHeight = 2_400,
            decision = true,
            rule = "id (clips_viewer)",
        )
        val details = CalibrationRows.details(e).replace(' ', ' ')
        assertEquals(
            "id=clips_viewer_view_pager · clase=ViewPager2 · idx 3→4 de 120 · dy=1840 · nodo 1080×2208 (92 %) · pantalla 1080×2400",
            details,
        )
        val row = CalibrationRows.eventRow(e, key = 9, platform = Platform.INSTAGRAM, zone = zone)
        assertEquals("SCROLL · Reels", row.title)
        assertEquals("00:00:00.000", row.timeText)
    }
}
