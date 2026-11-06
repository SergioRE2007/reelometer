package com.reelometro.app.detection

import com.reelometro.app.data.Category
import com.reelometro.app.data.Platform
import com.reelometro.app.detection.FeedRules.IndexStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewTrackerTest {

    private class FakeRecorder : ViewTracker.Recorder {
        val started = mutableListOf<Pair<Long, Long>>() // id, startedAt
        val finished = mutableMapOf<Long, Long>()
        val discarded = mutableListOf<Long>()
        val resumed = mutableListOf<Long>()
        override fun videoStarted(id: Long, platform: Platform, startedAt: Long) { started += id to startedAt }
        override fun videoFinished(id: Long, durationMs: Long) { finished[id] = durationMs }
        override fun videoDiscarded(id: Long) { discarded += id }
        override fun videoResumed(id: Long) { resumed += id }

        /** Vídeos que cuentan de verdad (empezados y no descartados). */
        val kept: List<Long> get() = started.map { it.first }.filter { it !in discarded }
    }

    private fun scroll(t: ViewTracker, from: Int, to: Int, now: Long, key: String = "feed") =
        t.onFeedScroll(Platform.TIKTOK, key, from, to, null, 2000, now)

    /** ViewPager: from == to == página actual. */
    private fun pager(
        t: ViewTracker, index: Int, now: Long, dy: Int? = 300, key: String = "pager", count: Int = -1,
        platform: Platform = Platform.TIKTOK,
    ) = t.onFeedScroll(ViewTracker.FeedScroll(platform, key, index, index, count, dy, 2000, IndexStyle.CURRENT_ITEM, now))

    /** RecyclerView: rango visible. */
    private fun recycler(t: ViewTracker, from: Int, to: Int, now: Long, dy: Int?, key: String = "recycler") =
        t.onFeedScroll(ViewTracker.FeedScroll(Platform.YOUTUBE, key, from, to, -1, dy, 2000, IndexStyle.VISIBLE_RANGE, now))

    // ── Casos de siempre ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `cada pagina asentada cuenta un video y a mitad de swipe no`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        t.onAppForeground(Platform.TIKTOK, 0)

        scroll(t, 0, 0, 1_000)       // primer vídeo
        scroll(t, 0, 1, 10_000)      // deslizando
        scroll(t, 0, 1, 10_100)
        scroll(t, 1, 1, 10_300)      // asentado en el 2º
        scroll(t, 1, 2, 25_000)
        scroll(t, 2, 2, 25_300)      // 3º

        assertEquals(3, rec.started.size)
        assertEquals(2, rec.finished.size)
        assertEquals(9_300L, rec.finished[rec.started[0].first])
        assertEquals(15_000L, rec.finished[rec.started[1].first])
    }

    @Test
    fun `volver al mismo video tras un swipe a medias no cuenta`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        scroll(t, 3, 3, 1_000)
        val before = rec.started.size
        scroll(t, 3, 4, 2_000)
        scroll(t, 3, 3, 2_200)
        assertEquals(before, rec.started.size)
    }

    @Test
    fun `el primer swipe de la sesion registra tambien el video que ya estaba en pantalla`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        t.onAppForeground(Platform.TIKTOK, 50_000)
        scroll(t, 1, 1, 70_000)
        assertEquals(2, rec.started.size)
        assertEquals(50_000L, rec.started[0].second)
        assertEquals(20_000L, rec.finished[rec.started[0].first])
    }

    @Test
    fun `salir de la app cierra el video y limita la duracion`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        scroll(t, 0, 0, 0)
        t.onLeave(60 * 60_000L)
        assertEquals(ViewTracker.MAX_VIDEO_MS, rec.finished[rec.started[0].first])
    }

    @Test
    fun `sin indices usa el desplazamiento acumulado`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        // Dos swipes de una pantalla (2000 px) en 4 eventos cada uno
        var now = 0L
        repeat(2) {
            repeat(4) { t.onFeedScroll(Platform.YOUTUBE, "shorts", -1, -1, 500, 2000, now); now += 50 }
            now += 10_000
        }
        // 1 "vídeo previo" + 2 swipes
        assertEquals(3, rec.started.size)
    }

    @Test
    fun `clasifica por hashtags`() {
        assertEquals(Category.COMIDA, CategoryClassifier.classify("La mejor tortilla", listOf("recetasfaciles", "cocina")))
        assertEquals(Category.DEPORTE, CategoryClassifier.classify("Golazo de Messi 🔥 #fútbol", listOf("fútbol")))
        assertEquals(Category.OTROS, CategoryClassifier.classify(null, emptyList()))
    }

    // ── Rebotes, permanencia mínima y vuelta atrás ───────────────────────────────────────────────

    @Test
    fun `un rebote de menos de 600 ms se descarta y el anterior se reanuda sin contarlo otra vez`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        t.onAppForeground(Platform.TIKTOK, 0)
        pager(t, 5, 1_000)                 // empieza a arrastrar el vídeo 5 (en pantalla desde 0)
        pager(t, 6, 10_000, dy = 900)      // suelta: vídeo 6
        pager(t, 5, 10_400, dy = -900)     // a los 400 ms vuelve al 5
        val (a, _) = rec.started[0]
        val b = rec.started[1].first
        assertEquals(listOf(b), rec.discarded)
        assertEquals(listOf(a), rec.resumed)
        assertEquals(listOf(a), rec.kept)

        pager(t, 6, 20_000, dy = 900)
        assertEquals(19_600L, rec.finished[a])     // 10 s + 9,6 s, un solo registro
        assertEquals(3, rec.started.size)          // el 6 se vuelve a contar: el de antes se descartó
    }

    @Test
    fun `volver a un video ya visto reanuda su registro`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        recycler(t, 0, 0, 1_000, dy = 0)
        recycler(t, 0, 1, 5_000, dy = 600)
        recycler(t, 1, 1, 5_200, dy = 1_400)
        recycler(t, 0, 1, 15_000, dy = -600)
        recycler(t, 0, 0, 15_200, dy = -1_400)     // vuelve al primero
        recycler(t, 0, 1, 20_000, dy = 600)
        recycler(t, 1, 1, 20_200, dy = 1_400)      // y otra vez al segundo
        assertEquals(2, rec.started.size)
        assertEquals(2, rec.resumed.size)
        // 1,0 → 5,2 s (hasta asentarse en el 2º) + 15,2 → 20,2 s (de nuevo hasta asentarse) = 4,2 s + 5,0 s
        assertEquals(9_200L, rec.finished[rec.started[0].first])
    }

    @Test
    fun `un fling que salta varias paginas solo cuenta la pagina final`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        recycler(t, 2, 2, 1_000, dy = 0)
        recycler(t, 2, 3, 9_000, dy = 800)
        recycler(t, 3, 4, 9_100, dy = 2_000)
        recycler(t, 4, 5, 9_200, dy = 2_000)
        recycler(t, 5, 5, 9_300, dy = 600)
        assertEquals(2, rec.started.size)
        assertEquals(8_300L, rec.finished[rec.started[0].first])
    }

    // ── Primer vídeo de cada feed ────────────────────────────────────────────────────────────────

    @Test
    fun `viewpager - el primer evento al arrastrar es el video en pantalla, no uno nuevo`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        t.onAppForeground(Platform.TIKTOK, 0)
        pager(t, 5, 30_000, dy = 200)
        pager(t, 5, 30_100, dy = 400)
        pager(t, 6, 30_200, dy = 900)
        assertEquals(2, rec.started.size)
        assertEquals(0L, rec.started[0].second)
        assertEquals(30_200L, rec.finished[rec.started[0].first])
    }

    @Test
    fun `recyclerview - primer evento a mitad de deslizamiento cuenta el que se va`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        t.onAppForeground(Platform.YOUTUBE, 0)
        recycler(t, 3, 4, 20_000, dy = 500)
        recycler(t, 4, 4, 20_300, dy = 800)
        assertEquals(2, rec.started.size)
        assertEquals(20_300L, rec.finished[rec.started[0].first])
    }

    @Test
    fun `recyclerview - primer evento ya asentado tras moverse cuenta tambien el anterior`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        t.onAppForeground(Platform.YOUTUBE, 0)
        recycler(t, 4, 4, 20_000, dy = 2_000)
        assertEquals(2, rec.started.size)
        assertEquals(20_000L, rec.finished[rec.started[0].first])
    }

    @Test
    fun `el feed que se acaba de dibujar sin movimiento empieza ahora`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        t.onAppForeground(Platform.YOUTUBE, 0)
        recycler(t, 0, 0, 5_000, dy = 0)
        assertEquals(1, rec.started.size)
        assertEquals(5_000L, rec.started[0].second)
    }

    @Test
    fun `el inicio estimado del video en pantalla es como mucho un minuto antes`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        t.onAppForeground(Platform.TIKTOK, 0)
        pager(t, 5, 600_000)
        assertEquals(600_000L - ViewTracker.MAX_PREVIOUS_MS, rec.started[0].second)
    }

    // ── Índices que cambian sin que el usuario se mueva ──────────────────────────────────────────

    @Test
    fun `indices que cambian sin movimiento no cuentan un video nuevo`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        recycler(t, 0, 0, 1_000, dy = 0)
        recycler(t, 0, 1, 5_000, dy = 600)
        recycler(t, 1, 1, 5_200, dy = 1_400)
        recycler(t, 4, 4, 9_000, dy = 0)          // la app insertó elementos delante
        assertEquals(2, rec.started.size)
        recycler(t, 4, 5, 15_000, dy = 500)
        recycler(t, 5, 5, 15_200, dy = 1_500)
        assertEquals(3, rec.started.size)
        assertEquals(10_000L, rec.finished[rec.started[1].first])
    }

    @Test
    fun `un salto programatico con cambio de datos no cuenta`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        pager(t, 3, 1_000, count = 10)
        pager(t, 8, 2_000, dy = 10_000, count = 12)
        assertEquals(1, rec.started.size)
    }

    @Test
    fun `si baja el numero de elementos no se reanuda un registro viejo`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        pager(t, 3, 1_000, count = 20)
        pager(t, 4, 10_000, count = 20)
        pager(t, 4, 15_000, dy = 0, count = 5)     // la app recargó el feed
        pager(t, 3, 20_000, dy = -900, count = 5)
        assertTrue(rec.resumed.isEmpty())
        assertEquals(3, rec.started.size)
    }

    // ── Salir y volver ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `volver a la app al mismo video lo reanuda en vez de contarlo otra vez`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        t.onAppForeground(Platform.TIKTOK, 0)
        pager(t, 5, 1_000)
        pager(t, 6, 10_000)
        t.onLeave(30_000)
        val b = rec.started[1].first
        assertEquals(20_000L, rec.finished[b])
        assertNull(t.currentVideoId)

        t.onAppForeground(Platform.TIKTOK, 60_000)
        pager(t, 6, 70_000)                        // sigue en el 6 y empieza a arrastrar
        pager(t, 7, 71_000)
        assertEquals(listOf(b), rec.resumed)
        assertEquals(31_000L, rec.finished[b])     // 20 s + 11 s desde que volvió
        assertEquals(3, rec.started.size)
    }

    @Test
    fun `salir del feed a otra pantalla pausa el video`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        pager(t, 5, 0)
        t.onLeaveFeed(Platform.TIKTOK, 10_000)
        assertFalse(t.isActive)
        assertEquals(10_000L, rec.finished[rec.started[0].first])
    }

    @Test
    fun `cambiar de pantalla de la app pausa el video`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        pager(t, 5, 0)
        t.onScreenChanged(Platform.TIKTOK, 8_000)
        assertFalse(t.isActive)
        assertEquals(8_000L, rec.finished[rec.started[0].first])
    }

    @Test
    fun `cambiar de feed cierra el anterior y cuenta el que se ve en el nuevo`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        pager(t, 3, 1_000, key = "para-ti")
        pager(t, 0, 20_000, key = "siguiendo")     // primer evento en otro feed: es el vídeo en pantalla
        pager(t, 1, 30_000, key = "siguiendo")
        assertEquals(3, rec.started.size)
        assertTrue(rec.discarded.isEmpty())
        assertEquals(19_000L, rec.finished[rec.started[0].first])
        assertEquals(10_000L, rec.finished[rec.started[1].first])
    }

    @Test
    fun `cambiar de plataforma cierra el video de la otra`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        pager(t, 1, 1_000)
        t.onAppForeground(Platform.INSTAGRAM, 20_000)
        assertNull(t.currentVideoId)
        assertEquals(19_000L, rec.finished[rec.started[0].first])
    }

    @Test
    fun `desactivar la plataforma cierra su video y olvida el feed`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        pager(t, 5, 1_000)
        t.onPlatformDisabled(Platform.TIKTOK, 11_000)
        assertFalse(t.isActive)
        assertEquals(10_000L, rec.finished[rec.started[0].first])
        pager(t, 5, 20_000)
        assertTrue(rec.resumed.isEmpty())
        assertEquals(2, rec.started.size)
    }

    @Test
    fun `la duracion maxima se aplica en directo`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        pager(t, 5, 0)
        t.tick(60_000)
        assertTrue(t.isActive)
        t.tick(ViewTracker.MAX_VIDEO_MS + 1)
        assertFalse(t.isActive)
        assertEquals(ViewTracker.MAX_VIDEO_MS, rec.finished[rec.started[0].first])
    }

    // ── Sin índices ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `un deslizamiento parcial que rebota no cuenta sin indices`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        t.onAppForeground(Platform.YOUTUBE, 0)
        listOf(400, 400, -400, -400).forEachIndexed { i, dy ->
            t.onFeedScroll(Platform.YOUTUBE, "shorts", -1, -1, dy, 2000, 10_000L + i * 100)
        }
        assertEquals(1, rec.started.size)
        assertTrue(t.isActive)
    }

    @Test
    fun `sin indices ni desplazamiento cuenta por rafagas`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        t.onAppForeground(Platform.TIKTOK, 0)
        listOf(1_000L, 1_100L, 1_200L, 5_000L, 5_100L).forEach { now ->
            t.onFeedScroll(Platform.TIKTOK, "rafagas", -1, -1, null, 2000, now)
        }
        assertEquals(3, rec.kept.size) // el que había + 2 ráfagas
    }

    @Test
    fun `los ids son unicos aunque coincida el milisegundo`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        repeat(50) { i -> pager(t, i, 1_000) }
        assertEquals(rec.started.size, rec.started.map { it.first }.toSet().size)
    }

    // ── Rango que no se cierra y segunda señal por contenido (YouTube Shorts) ─────────────────────

    @Test
    fun `un rango que nunca llega a from igual a to cuenta por desplazamiento`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        t.onAppForeground(Platform.YOUTUBE, 0)
        // En reposo la página siguiente asoma: siempre from = n, to = n + 1.
        recycler(t, 0, 1, 1_000, dy = 0)              // vídeo 0 (ya en pantalla)
        var now = 10_000L
        for (n in 0 until 3) {
            repeat(4) { recycler(t, n, n + 1, now, dy = 500); now += 100 }   // 4 × 500 = una página
            now += 8_000
        }
        assertEquals(4, rec.kept.size)
        assertTrue(rec.discarded.isEmpty())
    }

    @Test
    fun `con asiento normal el desplazamiento no cuenta dos veces`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        recycler(t, 0, 0, 1_000, dy = 0)
        recycler(t, 0, 1, 5_000, dy = 900)
        recycler(t, 0, 1, 5_100, dy = 1_000)    // 1.900 px: casi una página (dentro de la tolerancia)
        recycler(t, 1, 1, 5_200, dy = 100)      // asiento: mismo índice, nada nuevo
        recycler(t, 1, 2, 15_000, dy = 1_000)
        recycler(t, 2, 2, 15_100, dy = 1_000)
        assertEquals(3, rec.kept.size)
    }

    @Test
    fun `un rebote con el rango abierto no cuenta`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        recycler(t, 0, 0, 1_000, dy = 0)
        recycler(t, 0, 1, 5_000, dy = 800)
        recycler(t, 0, 1, 5_100, dy = -800)
        recycler(t, 0, 0, 5_200, dy = 0)
        assertEquals(1, rec.kept.size)
    }

    @Test
    fun `un cambio de contenido sin scroll cuenta el video perdido y el siguiente swipe real tambien`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        recycler(t, 0, 0, 1_000, dy = 0)
        assertTrue(t.onContentChanged(Platform.YOUTUBE, 9_000))       // swipe sin evento: vídeo 1
        assertEquals(8_000L, rec.finished[rec.started[0].first])
        recycler(t, 1, 2, 20_000, dy = 1_000)                          // swipe real al 2
        recycler(t, 2, 2, 20_100, dy = 1_000)
        assertEquals(3, rec.kept.size)
        // Otra plataforma o sin vídeo activo: no hace nada.
        assertFalse(t.onContentChanged(Platform.TIKTOK, 21_000))
        t.onLeave(22_000)
        assertFalse(t.onContentChanged(Platform.YOUTUBE, 23_000))
    }

    @Test
    fun `volver por contenido al video anterior lo reanuda`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        recycler(t, 0, 0, 1_000, dy = 0)
        recycler(t, 0, 1, 5_000, dy = 1_000)
        recycler(t, 1, 1, 5_100, dy = 1_000)
        assertFalse(t.onContentChanged(Platform.YOUTUBE, 9_000, step = -1))
        assertEquals(2, rec.kept.size)
        assertEquals(listOf(rec.started[0].first), rec.resumed)
    }

    @Test
    fun `sin indices un cambio por contenido mueve tambien la posicion`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        t.onAppForeground(Platform.YOUTUBE, 0)
        var now = 1_000L
        repeat(4) { t.onFeedScroll(Platform.YOUTUBE, "shorts", -1, -1, 500, 2000, now); now += 50 }   // 0 → 1
        assertEquals(2, rec.kept.size)
        t.onContentChanged(Platform.YOUTUBE, 10_000)                                                    // 1 → 2 sin scroll
        now = 20_000L
        repeat(4) { t.onFeedScroll(Platform.YOUTUBE, "shorts", -1, -1, 500, 2000, now); now += 50 }   // 2 → 3
        assertEquals(4, rec.kept.size)
    }

    @Test
    fun `un video contado dos veces se une al anterior`() {
        val rec = FakeRecorder()
        val t = ViewTracker(rec)
        recycler(t, 3, 3, 1_000, dy = 0, key = "a")
        val first = rec.started[0].first
        // La app rehace el contenedor (otro nodo): el mismo vídeo llega como feed nuevo.
        recycler(t, 0, 0, 6_000, dy = 0, key = "b")
        assertEquals(2, rec.started.size)
        val second = rec.started[1].first
        assertTrue(t.mergeActiveInto(first, 7_000))
        assertEquals(listOf(second), rec.discarded)
        assertEquals(listOf(first), rec.resumed)
        assertEquals(first, t.currentVideoId)
        t.onLeave(16_000)
        assertEquals(15_000L, rec.finished[first])   // 5 s + 10 s, un solo registro
        assertEquals(listOf(first), rec.kept)
        // Ya no está activo ni existe: no se puede unir otra vez.
        assertFalse(t.mergeActiveInto(second, 17_000))
    }
}
