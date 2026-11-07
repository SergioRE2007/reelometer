package com.reelometro.app.detection

import com.reelometro.app.data.Platform
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recorrido real de YouTube Shorts (YouTube 21.44, Pixel 10a, 6 nov 2026), repetido evento a evento como lo
 * hace el servicio: [FeedRules.evaluate] y, si es el feed, [ViewTracker.onFeedScroll]. El usuario pasó unos 26
 * Shorts hacia delante (el desplazamiento de `reel_recycler` suma ≈ 26,2 páginas) y la app contó 6: en cada
 * deslizamiento también hacen scroll `reel_watch_player` y `reel_player_underlay` (piezas de la página, que se
 * mueven al revés), y el detector creía saltar entre tres feeds.
 */
class ShortsReplayTest {

    private class Counter : ViewTracker.Recorder {
        val started = LinkedHashSet<Long>()

        override fun videoStarted(id: Long, platform: Platform, startedAt: Long) {
            started += id
        }

        override fun videoFinished(id: Long, durationMs: Long) = Unit

        override fun videoDiscarded(id: Long) {
            started -= id
        }
    }

    private data class Row(val t: Long, val id: String, val cls: String, val dy: Int, val w: Int, val h: Int, val sw: Int, val sh: Int)

    private fun rows(file: String = FIRST): List<Row> =
        requireNotNull(javaClass.getResourceAsStream("/calibration/$file")).bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val f = line.split(';')
                Row(f[0].toLong(), f[1], f[2], f[3].toInt(), f[4].toInt(), f[5].toInt(), f[6].toInt(), f[7].toInt())
            }

    /**
     * Repite [file] como el servicio. La clave del feed lleva una «generación» que sube al salir de la app o pasar
     * por otra pantalla: en el móvil, YouTube redibuja el feed como otro nodo al volver (otra instancia, otra clave).
     */
    private fun replay(file: String = FIRST): Int {
        val counter = Counter()
        val tracker = ViewTracker(counter)
        val base = 1_800_000_000_000L
        val rules = FeedRules.rulesFor(Platform.YOUTUBE)
        var generation = 0
        val rows = rows(file)
        for (r in rows) {
            val now = base + r.t
            when (r.id) {
                "@leave" -> {
                    tracker.onLeave(now)
                    generation++
                    continue
                }
                "@leavefeed" -> {
                    tracker.onLeaveFeed(Platform.YOUTUBE, now)
                    generation++
                    continue
                }
            }
            val viewId = r.id.takeIf { it.isNotEmpty() }?.let { "com.google.android.youtube:id/$it" }
            val decision = FeedRules.evaluate(
                FeedRules.ScrollSample(
                    Platform.YOUTUBE, viewId, r.cls, 0, 0, r.w, r.h, r.sw, r.sh,
                    deltaY = r.dy, deltaX = 0, orientation = FeedRules.Orientation.VERTICAL,
                ),
            )
            when (decision.verdict) {
                FeedRules.Verdict.FEED -> tracker.onFeedScroll(
                    ViewTracker.FeedScroll(
                        platform = Platform.YOUTUBE,
                        feedKey = "YOUTUBE|${viewId ?: r.cls}|$generation",
                        deltaY = r.dy,
                        pageHeight = r.h,
                        indexStyle = FeedRules.indexStyleFor(r.cls),
                        now = now,
                        settleTolerance = rules.settleTolerance,
                        resumeOnReturn = rules.resumeOnReturn,
                    ),
                )
                FeedRules.Verdict.OTHER_SCREEN -> {
                    tracker.onLeaveFeed(Platform.YOUTUBE, now)
                    generation++
                }
                FeedRules.Verdict.IGNORED -> Unit
            }
        }
        tracker.onLeave(base + rows.last().t + 2_000)
        return counter.started.size
    }

    @Test
    fun `las piezas de la página no son el feed`() {
        for (id in listOf("reel_watch_player", "reel_player_underlay")) {
            val d = FeedRules.evaluate(
                FeedRules.ScrollSample(
                    Platform.YOUTUBE, "com.google.android.youtube:id/$id", "ViewGroup", 0, 0, 1080, 2083, 1080, 2424,
                    deltaY = -1029, deltaX = 0, orientation = FeedRules.Orientation.VERTICAL,
                ),
            )
            assertTrue("$id → $d", d.verdict == FeedRules.Verdict.IGNORED)
        }
    }

    @Test
    fun `el recorrido real cuenta los Shorts que se vieron`() {
        val counted = replay()
        // 27 Shorts en pantalla (el de entrada y 26 deslizamientos). 8 de la ronda rápida estuvieron 310–590 ms y se
        // descartan como rebote (< 600 ms, la misma regla que en TikTok e Instagram): quedan 19. Antes: 3 (6 en el
        // móvil, con la comprobación de contenido, que aquí no se simula).
        assertTrue("contados $counted", counted in 18..21)
    }

    @Test
    fun `volver atrás reanuda, y salir y volver enseguida no cuenta el mismo Short otra vez`() {
        // Segundo recorrido real: 4 Shorts, 2 hacia atrás (se reanudan), botón de inicio del móvil y vuelta a los
        // 1,5 s al mismo Short (antes contaba 1 más), portada de YouTube y un Short tocado allí: 5 en pantalla, 1 de
        // 0,36 s (rebote). Total: 4 + 4 = 8.
        assertTrue("contados ${replay(BACK)}", replay(BACK) == 8)
    }

    private companion object {
        const val FIRST = "shorts-yt-21.44.csv"
        const val BACK = "shorts-yt-21.44-vuelta.csv"
    }
}
