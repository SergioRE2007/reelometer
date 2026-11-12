package com.reelometro.app.detection

import android.view.accessibility.AccessibilityEvent
import com.reelometro.app.data.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceMetricsTest {

    private var now = 1_000_000L
    private val metrics = ServiceMetrics(clockMs = { now })

    private val scroll = AccessibilityEvent.TYPE_VIEW_SCROLLED
    private val window = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    private val click = AccessibilityEvent.TYPE_VIEW_CLICKED
    private val content = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

    @Test
    fun `cuenta por plataforma y tipo de evento`() {
        repeat(3) { metrics.onEvent(Platform.TIKTOK, scroll, 1_000) }
        metrics.onEvent(Platform.TIKTOK, window, 1_000)
        metrics.onEvent(Platform.INSTAGRAM, click, 1_000)
        repeat(2) { metrics.onEvent(Platform.INSTAGRAM, content, 100) }
        metrics.onEvent(Platform.INSTAGRAM, AccessibilityEvent.TYPE_VIEW_FOCUSED, 100)
        val s = metrics.snapshot()
        assertEquals(ServiceMetrics.KindCounts(scroll = 3, window = 1, click = 0, content = 0, other = 0), s.events[Platform.TIKTOK])
        assertEquals(ServiceMetrics.KindCounts(scroll = 0, window = 0, click = 1, content = 2, other = 1), s.events[Platform.INSTAGRAM])
        assertNull("Sin eventos de YouTube no sale", s.events[Platform.YOUTUBE])
        assertEquals(2L, s.contentChanged)
        assertEquals(8L, s.handled)
        assertEquals(0L, s.unknownEvents)
    }

    @Test
    fun `un paquete sin plataforma va aparte`() {
        metrics.onEvent(null, scroll, 10)
        metrics.onEvent(null, content, 10)
        val s = metrics.snapshot()
        assertEquals(2L, s.unknownEvents)
        assertEquals(1L, s.contentChanged)
        assertTrue(s.events.isEmpty())
    }

    @Test
    fun `media, maximo y eventos lentos`() {
        metrics.onEvent(Platform.TIKTOK, scroll, 1_000_000) // 1 ms
        metrics.onEvent(Platform.TIKTOK, scroll, 3_000_000) // 3 ms
        metrics.onEvent(Platform.TIKTOK, scroll, 20_000_000) // 20 ms: lento
        metrics.onEvent(Platform.TIKTOK, scroll, ServiceMetrics.SLOW_EVENT_NANOS) // justo en el límite: no cuenta
        metrics.onEvent(Platform.TIKTOK, scroll, -5) // reloj raro: cuenta como 0
        val s = metrics.snapshot()
        assertEquals(5L, s.handled)
        assertEquals(1L, s.slow)
        assertEquals(20.0, s.maxEventMs, 1e-9)
        assertEquals(40.0 / 5, s.avgEventMs, 1e-9)
    }

    @Test
    fun `retraso de entrega - el mayor, los de dos segundos o mas y la hora del ultimo`() {
        var wall = 5_000L
        val m = ServiceMetrics(clockMs = { now }, wallMs = { wall })
        m.onEvent(Platform.TIKTOK, scroll, 1_000_000, lagMs = 12)
        m.onEvent(Platform.TIKTOK, scroll, 1_000_000, lagMs = ServiceMetrics.LAG_SLOW_MS - 1)
        m.onEvent(Platform.TIKTOK, scroll, 1_000_000) // sin hora en el evento: 0
        var s = m.snapshot()
        assertEquals(ServiceMetrics.LAG_SLOW_MS - 1, s.lagMaxMs)
        assertEquals(0L, s.lagSlow)
        assertNull("nunca ha llegado uno tarde de verdad", s.lagLastAt)

        // El proceso estuvo congelado: el evento entra minutos después de que la app lo mandara.
        wall = 9_000L
        m.onEvent(Platform.INSTAGRAM, window, 1_000_000, lagMs = 95_000)
        wall = 9_500L
        m.onEvent(Platform.INSTAGRAM, window, 1_000_000, lagMs = ServiceMetrics.LAG_SLOW_MS)
        wall = 20_000L
        m.onEvent(Platform.INSTAGRAM, window, 1_000_000, lagMs = 3)
        s = m.snapshot()
        assertEquals(95_000L, s.lagMaxMs)
        assertEquals(2L, s.lagSlow)
        assertEquals("la hora del último que llegó tarde, no la del último evento", 9_500L, s.lagLastAt)

        m.reset()
        s = m.snapshot()
        assertEquals(0L, s.lagMaxMs)
        assertEquals(0L, s.lagSlow)
        assertNull(s.lagLastAt)
    }

    @Test
    fun `eventos por minuto con el reloj falso`() {
        // 121 eventos, uno por segundo: 120 s con actividad.
        repeat(121) {
            metrics.onEvent(Platform.YOUTUBE, scroll, 1)
            now += 1_000
        }
        val s = metrics.snapshot()
        assertEquals(121_000L, s.elapsedMs)
        assertEquals(120_000L, s.activeMs)
        assertEquals(60.0, s.perMinute(120), 1e-9)
        assertTrue(s.rateKnown)
    }

    @Test
    fun `las pausas de mas de un minuto no diluyen el ritmo`() {
        repeat(11) {
            metrics.onEvent(Platform.TIKTOK, scroll, 1)
            now += 1_000
        }
        now += 8 * 60 * 60_000 // Una noche sin usar las apps.
        repeat(11) {
            metrics.onEvent(Platform.TIKTOK, scroll, 1)
            now += 1_000
        }
        val s = metrics.snapshot()
        assertEquals(20_000L, s.activeMs)
        assertEquals(66.0, s.perMinute(s.handled), 1e-9)
        assertTrue(s.elapsedMs > 8 * 60 * 60_000L)
    }

    @Test
    fun `con menos de diez segundos de actividad el ritmo no se sabe`() {
        metrics.onEvent(Platform.TIKTOK, scroll, 1)
        now += 9_999
        metrics.onEvent(Platform.TIKTOK, scroll, 1)
        assertFalse(metrics.snapshot().rateKnown)
        now += 1
        metrics.onEvent(Platform.TIKTOK, scroll, 1)
        assertTrue(metrics.snapshot().rateKnown)
        now += 10 * 60_000
        assertEquals("El tiempo sin eventos no cuenta", 10_000L, metrics.snapshot().activeMs)
    }

    @Test
    fun `con el servicio parado el reloj se congela y una reconexion no borra nada`() {
        metrics.onEvent(Platform.TIKTOK, scroll, 1)
        now += 30_000
        metrics.stop()
        now += 60 * 60_000
        assertEquals(30_000L, metrics.snapshot().elapsedMs)
        metrics.start()
        now += 10_000
        metrics.onEvent(Platform.TIKTOK, scroll, 1)
        val s = metrics.snapshot()
        assertEquals(40_000L, s.elapsedMs)
        assertEquals(2L, s.handled)
        assertEquals(1L, s.connects)
        assertEquals("Tras parar, el primer evento no suma el hueco", 0L, s.activeMs)
        metrics.start()
        assertEquals(2L, metrics.snapshot().connects)
    }

    @Test
    fun `cambios de contenido fuera de un envio rapido van aparte`() {
        repeat(5) { metrics.onEvent(Platform.INSTAGRAM, content, 10, forQuickSend = true) }
        metrics.onEvent(Platform.INSTAGRAM, content, 10)
        metrics.onEvent(Platform.INSTAGRAM, scroll, 10)
        val s = metrics.snapshot()
        assertEquals(6L, s.contentChanged)
        assertEquals(1L, s.contentOutsideQuick)
        assertEquals(6L, s.events.getValue(Platform.INSTAGRAM).content)
    }

    @Test
    fun `cuenta los eventos que llegaron con la captura encendida`() {
        repeat(3) { metrics.onEvent(Platform.TIKTOK, scroll, 10, capturing = true) }
        metrics.onEvent(Platform.TIKTOK, scroll, 10)
        val s = metrics.snapshot()
        assertEquals(3L, s.captured)
        assertEquals(0.75, s.capturedFraction, 1e-9)
        assertEquals(0.0, ServiceMetrics(clockMs = { 0 }).snapshot().capturedFraction, 1e-9)
    }

    @Test
    fun `lecturas sin ventana legible no cuentan como lectura`() {
        repeat(3) { metrics.onTreeUnreadable(Platform.TIKTOK) }
        metrics.onTreeRead(Platform.TIKTOK, 50, 1_000, found = true)
        val s = metrics.snapshot()
        assertEquals(3L, s.unreadable)
        assertEquals(1L, s.reads.getValue(Platform.TIKTOK).count)
        assertEquals(50.0, s.avgNodesPerRead!!, 1e-9)
    }

    @Test
    fun `los hijos pedidos en un paso del envio rapido van aparte`() {
        metrics.onChildFetch(10)
        val result = metrics.quickTick {
            metrics.onChildFetch(7)
            // Otro hilo a la vez (lecturas de pantalla) no cuenta como envío rápido.
            Thread { metrics.onChildFetch(100) }.apply { start() }.join()
            "paso"
        }
        metrics.onChildFetch(3)
        val s = metrics.snapshot()
        assertEquals("paso", result)
        assertEquals(120L, s.childFetches)
        assertEquals(7L, s.quickChildFetches)
        assertEquals(1L, s.quick.count)
        assertTrue(s.quick.totalNanos >= 0)
    }

    @Test
    fun `reset pone todo a cero y empieza a contar el tiempo de nuevo`() {
        metrics.onEvent(Platform.TIKTOK, scroll, 50_000_000)
        metrics.onTreeRead(Platform.TIKTOK, 120, 2_000_000, found = true)
        metrics.onPresenceCheck(3_000_000)
        metrics.onChildFetch(7)
        metrics.onParentFetch()
        metrics.onFinderQuery()
        now += 60_000
        metrics.reset()
        now += 5_000
        val s = metrics.snapshot()
        assertEquals(5_000L, s.elapsedMs)
        assertEquals(0L, s.handled)
        assertEquals(0L, s.maxNanos)
        assertEquals(0L, s.slow)
        assertTrue(s.events.isEmpty() && s.reads.isEmpty())
        assertEquals(ServiceMetrics.TimingStats(0, 0, 0), s.presence)
        assertEquals(0L, s.childFetches + s.parentFetches + s.finderQueries)
        assertEquals(0L, s.captured + s.activeMs + s.connects)
    }

    @Test
    fun `lecturas de pantalla por plataforma`() {
        metrics.onTreeRead(Platform.YOUTUBE, 300, 4_000_000, found = false)
        metrics.onTreeRead(Platform.YOUTUBE, 100, 2_000_000, found = false)
        metrics.onTreeRead(Platform.YOUTUBE, 200, 9_000_000, found = true)
        metrics.onTreeRead(Platform.TIKTOK, 40, 1_000_000, found = true)
        val s = metrics.snapshot()
        val yt = s.reads.getValue(Platform.YOUTUBE)
        assertEquals(ServiceMetrics.ReadStats(3, 2, 600, 300, 15_000_000, 9_000_000), yt)
        assertEquals(200.0, yt.avgNodes, 1e-9)
        assertEquals(5.0, yt.avgMs, 1e-9)
        assertEquals(9.0, yt.maxMs, 1e-9)
        assertEquals(ServiceMetrics.ReadStats(1, 0, 40, 40, 1_000_000, 1_000_000), s.reads[Platform.TIKTOK])
        assertEquals(640.0 / 4, s.avgNodesPerRead!!, 1e-9)
        assertNull(ServiceMetrics(clockMs = { 0 }).snapshot().avgNodesPerRead)
    }

    @Test
    fun `presencia y pedidos al sistema`() {
        metrics.onPresenceCheck(2_000_000)
        metrics.onPresenceCheck(6_000_000)
        metrics.onChildFetch(30)
        metrics.onChildFetch(0)
        metrics.onChildFetch(-1)
        repeat(4) { metrics.onParentFetch() }
        repeat(2) { metrics.onFinderQuery() }
        val s = metrics.snapshot()
        assertEquals(ServiceMetrics.TimingStats(2, 8_000_000, 6_000_000), s.presence)
        assertEquals(4.0, s.presence.avgMs, 1e-9)
        assertEquals(30L, s.childFetches)
        assertEquals(4L, s.parentFetches)
        assertEquals(2L, s.finderQueries)
    }

    @Test
    fun `la foto no cambia aunque luego lleguen mas eventos`() {
        metrics.onEvent(Platform.TIKTOK, scroll, 10)
        metrics.onTreeRead(Platform.TIKTOK, 5, 10, found = true)
        val before = metrics.snapshot()
        metrics.onEvent(Platform.TIKTOK, scroll, 10)
        metrics.onTreeRead(Platform.TIKTOK, 5, 10, found = false)
        assertEquals(1L, before.handled)
        assertEquals(1L, before.events.getValue(Platform.TIKTOK).scroll)
        assertEquals(1L, before.reads.getValue(Platform.TIKTOK).count)
        assertEquals(2L, metrics.snapshot().handled)
    }

    @Test
    fun `dos hilos a la vez no pierden eventos`() {
        val threads = listOf(Platform.TIKTOK, Platform.INSTAGRAM).map { p ->
            Thread {
                repeat(10_000) {
                    metrics.onEvent(p, scroll, 1)
                    metrics.onChildFetch(1)
                }
            }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        val s = metrics.snapshot()
        assertEquals(20_000L, s.handled)
        assertEquals(20_000L, s.childFetches)
        assertEquals(10_000L, s.events.getValue(Platform.TIKTOK).scroll)
        assertEquals(10_000L, s.events.getValue(Platform.INSTAGRAM).scroll)
    }

    @Test
    fun `tipos de evento`() {
        assertEquals(ServiceMetrics.Kind.SCROLL, ServiceMetrics.kindOf(scroll))
        assertEquals(ServiceMetrics.Kind.WINDOW, ServiceMetrics.kindOf(window))
        assertEquals(ServiceMetrics.Kind.CLICK, ServiceMetrics.kindOf(click))
        assertEquals(ServiceMetrics.Kind.CONTENT, ServiceMetrics.kindOf(content))
        assertEquals(ServiceMetrics.Kind.OTHER, ServiceMetrics.kindOf(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED))
    }
}
