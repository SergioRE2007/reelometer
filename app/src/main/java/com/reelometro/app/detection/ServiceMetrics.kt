package com.reelometro.app.detection

import android.view.accessibility.AccessibilityEvent
import com.reelometro.app.data.Platform
import java.util.concurrent.atomic.AtomicLong

/**
 * Contadores del servicio de accesibilidad, solo en memoria (nada a disco ni a la base de datos), para la cabecera
 * del registro de Calibración: cuántos eventos llegan de cada app, cuánto tarda `onAccessibilityEvent`, cuántos nodos
 * se leen y cuántas veces se pide algo al sistema. Sirven para que los testers manden datos de batería y rendimiento
 * de su móvil sin tener que medir nada.
 *
 * Coste: tres lecturas del reloj y un monitor sin contención por evento (menos de un microsegundo); por dentro son
 * arrays de tamaño fijo, sin objetos por evento. Los mapas del [Snapshot] solo se crean al pedirlo. Nunca lanza.
 *
 * Tiempo: el periodo medido solo avanza con el servicio en marcha ([stop] lo congela y [start] lo reanuda) y, además,
 * se lleva el «tiempo con actividad» (huecos entre eventos de hasta [ACTIVE_GAP_MS]): los ritmos por minuto van sobre
 * ese rato, para que una noche sin usar las apps no los diluya.
 *
 * Hilos: el principal (eventos, presencia) y el de lecturas de pantalla (`readScope`) → `@Synchronized`. Los pedidos
 * al sistema ([onChildFetch], [onParentFetch], [onFinderQuery]) llegan desde cualquier hilo y van con [AtomicLong].
 *
 * @param clockMs reloj monótono en milisegundos (inyectable en los tests).
 */
class ServiceMetrics(
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
    /** Reloj de pared: solo para fechar el último retraso grande ([Snapshot.lagLastAt]). */
    private val wallMs: () -> Long = System::currentTimeMillis,
) {

    /** Tipo de evento, agrupado como lo trata el servicio. */
    enum class Kind { SCROLL, WINDOW, CLICK, CONTENT, OTHER }

    private val platforms = Platform.entries
    private val kinds = Kind.entries

    /** Fila por plataforma (en el orden de [Platform.entries]) y una última para «sin plataforma». */
    private val eventCounts = LongArray((platforms.size + 1) * kinds.size)
    private var handled = 0L
    private var totalNanos = 0L
    private var maxNanos = 0L
    private var slow = 0L
    private var contentOutsideQuick = 0L
    private var captured = 0L

    private var lagMax = 0L
    private var lagSlow = 0L
    private var lagLastAt: Long? = null

    private val readCount = LongArray(platforms.size)
    private val readEmpty = LongArray(platforms.size)
    private val readNodes = LongArray(platforms.size)
    private val readNodesMax = LongArray(platforms.size)
    private val readNanos = LongArray(platforms.size)
    private val readNanosMax = LongArray(platforms.size)
    private val readUnreadable = LongArray(platforms.size)

    private var presenceCount = 0L
    private var presenceNanos = 0L
    private var presenceMax = 0L

    private var quickCount = 0L
    private var quickNanos = 0L
    private var quickMax = 0L

    private val childFetches = AtomicLong()
    private val quickChildFetches = AtomicLong()
    private val parentFetches = AtomicLong()
    private val finderQueries = AtomicLong()

    /** El hilo que está dando un paso del envío rápido ([quickTick]); sus pedidos de hijos se cuentan también aparte. */
    @Volatile
    private var quickThread: Thread? = null

    /** Tiempo medido antes del tramo en marcha (ms) y desde cuándo corre el tramo (null: servicio parado). */
    private var elapsedBefore = 0L
    private var runningSince: Long? = clockMs()
    private var connects = 0L

    private var activeMs = 0L
    private var lastEventAt = NEVER

    /**
     * Un evento que ha pasado por `onAccessibilityEvent` y lo que ha tardado (en ns).
     *
     * @param forQuickSend un `TYPE_WINDOW_CONTENT_CHANGED` que esperaba el envío rápido en curso (de su app): no cuenta
     *   en [Snapshot.contentOutsideQuick], que tiene que ser 0.
     * @param capturing el registro de Calibración estaba capturando (su coste va dentro de [nanos]).
     * @param lagMs retraso de entrega: lo que ha pasado desde que la app mandó el evento (`eventTime`) hasta que ha
     *   entrado aquí. Lo normal son milisegundos; segundos o minutos dicen que el proceso estaba congelado o sin CPU
     *   (los ahorros de batería de algunas marcas congelan las apps en segundo plano).
     */
    @Synchronized
    fun onEvent(
        platform: Platform?,
        eventType: Int,
        nanos: Long,
        forQuickSend: Boolean = false,
        capturing: Boolean = false,
        lagMs: Long = 0,
    ) {
        val row = platform?.ordinal ?: platforms.size
        val kind = kindOf(eventType)
        eventCounts[row * kinds.size + kind.ordinal]++
        if (kind == Kind.CONTENT && !forQuickSend) contentOutsideQuick++
        if (capturing) captured++
        val n = nanos.coerceAtLeast(0)
        handled++
        totalNanos += n
        if (n > maxNanos) maxNanos = n
        if (n > SLOW_EVENT_NANOS) slow++
        if (lagMs > lagMax) lagMax = lagMs
        if (lagMs >= LAG_SLOW_MS) {
            lagSlow++
            lagLastAt = wallMs()
        }
        val now = clockMs()
        if (lastEventAt != NEVER) {
            val gap = now - lastEventAt
            if (gap in 0..ACTIVE_GAP_MS) activeMs += gap
        }
        lastEventAt = now
    }

    /** Una lectura de la pantalla (autor y descripción del vídeo): nodos visitados, tiempo y si sacó algo. */
    @Synchronized
    fun onTreeRead(platform: Platform, nodes: Int, nanos: Long, found: Boolean) {
        val i = platform.ordinal
        val n = nanos.coerceAtLeast(0)
        val count = nodes.coerceAtLeast(0).toLong()
        readCount[i]++
        if (!found) readEmpty[i]++
        readNodes[i] += count
        if (count > readNodesMax[i]) readNodesMax[i] = count
        readNanos[i] += n
        if (n > readNanosMax[i]) readNanosMax[i] = n
    }

    /** Se quiso leer la pantalla pero no había nada legible (otra ventana encima, sin raíz o de otra app). */
    @Synchronized
    fun onTreeUnreadable(platform: Platform) {
        readUnreadable[platform.ordinal]++
    }

    /** Una comprobación periódica de presencia (hilo principal: ventanas, raíces y feed). */
    @Synchronized
    fun onPresenceCheck(nanos: Long) {
        val n = nanos.coerceAtLeast(0)
        presenceCount++
        presenceNanos += n
        if (n > presenceMax) presenceMax = n
    }

    /**
     * Un paso del envío rápido ([block], síncrono): su tiempo y los hijos que pide al sistema en este hilo van aparte
     * («Envío rápido» en la cabecera). Un paso a la vez: el servicio no lanza otro hasta que acaba el anterior.
     */
    inline fun <T> quickTick(block: () -> T): T {
        val t0 = System.nanoTime()
        quickStarted()
        try {
            return block()
        } finally {
            quickEnded(System.nanoTime() - t0)
        }
    }

    @PublishedApi
    internal fun quickStarted() {
        quickThread = Thread.currentThread()
    }

    @PublishedApi
    @Synchronized
    internal fun quickEnded(nanos: Long) {
        quickThread = null
        val n = nanos.coerceAtLeast(0)
        quickCount++
        quickNanos += n
        if (n > quickMax) quickMax = n
    }

    /** Se han pedido [n] hijos de un nodo al sistema (cada uno puede ser una llamada entre procesos). */
    fun onChildFetch(n: Int) {
        if (n <= 0) return
        childFetches.addAndGet(n.toLong())
        if (quickThread === Thread.currentThread()) quickChildFetches.addAndGet(n.toLong())
    }

    /** Se ha pedido el padre de un nodo al sistema. */
    fun onParentFetch() {
        parentFetches.incrementAndGet()
    }

    /** Una búsqueda por texto o por id que hace la app de destino (`findAccessibilityNodeInfosBy…`). */
    fun onFinderQuery() {
        finderQueries.incrementAndGet()
    }

    /** El servicio se ha conectado: el reloj sigue (si estaba parado) y se cuenta una conexión más. No borra nada. */
    @Synchronized
    fun start() {
        if (runningSince == null) runningSince = clockMs()
        connects++
    }

    /** El servicio se ha parado: el periodo medido se congela hasta el siguiente [start]. */
    @Synchronized
    fun stop() {
        runningSince?.let { elapsedBefore += (clockMs() - it).coerceAtLeast(0) }
        runningSince = null
        lastEventAt = NEVER
    }

    /** Todo a cero (VACIAR en Calibración); el periodo medido empieza ahora si el servicio está en marcha. */
    @Synchronized
    fun reset() {
        eventCounts.fill(0)
        handled = 0
        totalNanos = 0
        maxNanos = 0
        slow = 0
        contentOutsideQuick = 0
        captured = 0
        lagMax = 0
        lagSlow = 0
        lagLastAt = null
        for (a in listOf(readCount, readEmpty, readNodes, readNodesMax, readNanos, readNanosMax, readUnreadable)) a.fill(0)
        presenceCount = 0
        presenceNanos = 0
        presenceMax = 0
        quickCount = 0
        quickNanos = 0
        quickMax = 0
        childFetches.set(0)
        quickChildFetches.set(0)
        parentFetches.set(0)
        finderQueries.set(0)
        elapsedBefore = 0
        if (runningSince != null) runningSince = clockMs()
        connects = 0
        activeMs = 0
        lastEventAt = NEVER
    }

    /** Copia de los contadores en este momento. */
    @Synchronized
    fun snapshot(): Snapshot {
        val events = LinkedHashMap<Platform, KindCounts>()
        for (p in platforms) counts(p.ordinal).takeIf { it.total > 0 }?.let { events[p] = it }
        val unknown = counts(platforms.size).total
        var content = 0L
        for (row in 0..platforms.size) content += eventCounts[row * kinds.size + Kind.CONTENT.ordinal]
        val reads = LinkedHashMap<Platform, ReadStats>()
        for (p in platforms) {
            val i = p.ordinal
            if (readCount[i] > 0) {
                reads[p] = ReadStats(readCount[i], readEmpty[i], readNodes[i], readNodesMax[i], readNanos[i], readNanosMax[i])
            }
        }
        return Snapshot(
            elapsedMs = elapsedBefore + (runningSince?.let { (clockMs() - it).coerceAtLeast(0) } ?: 0),
            activeMs = activeMs,
            connects = connects,
            events = events,
            unknownEvents = unknown,
            contentChanged = content,
            contentOutsideQuick = contentOutsideQuick,
            handled = handled,
            totalNanos = totalNanos,
            maxNanos = maxNanos,
            slow = slow,
            reads = reads,
            unreadable = readUnreadable.sum(),
            presence = TimingStats(presenceCount, presenceNanos, presenceMax),
            quick = TimingStats(quickCount, quickNanos, quickMax),
            childFetches = childFetches.get(),
            quickChildFetches = quickChildFetches.get(),
            parentFetches = parentFetches.get(),
            finderQueries = finderQueries.get(),
            captured = captured,
            lagMaxMs = lagMax,
            lagSlow = lagSlow,
            lagLastAt = lagLastAt,
        )
    }

    private fun counts(row: Int): KindCounts {
        fun at(kind: Kind) = eventCounts[row * kinds.size + kind.ordinal]
        return KindCounts(at(Kind.SCROLL), at(Kind.WINDOW), at(Kind.CLICK), at(Kind.CONTENT), at(Kind.OTHER))
    }

    /** Eventos de una app, por tipo. */
    data class KindCounts(val scroll: Long, val window: Long, val click: Long, val content: Long, val other: Long) {
        val total: Long get() = scroll + window + click + content + other
    }

    /** Lecturas de pantalla de una app: cuántas, cuántas sin nada, nodos y tiempos (ns). */
    data class ReadStats(
        val count: Long,
        val empty: Long,
        val nodesTotal: Long,
        val nodesMax: Long,
        val nanosTotal: Long,
        val nanosMax: Long,
    ) {
        val avgNodes: Double get() = if (count > 0) nodesTotal.toDouble() / count else 0.0
        val avgMs: Double get() = if (count > 0) nanosTotal / 1e6 / count else 0.0
        val maxMs: Double get() = nanosMax / 1e6
    }

    /** Veces y tiempos (ns) de algo que se repite. */
    data class TimingStats(val count: Long, val totalNanos: Long, val maxNanos: Long) {
        val avgMs: Double get() = if (count > 0) totalNanos / 1e6 / count else 0.0
        val maxMs: Double get() = maxNanos / 1e6
    }

    /** Foto de los contadores; no cambia aunque luego lleguen más eventos. */
    data class Snapshot(
        /** Tiempo medido con el servicio en marcha (desde VACIAR o desde que arrancó la app). */
        val elapsedMs: Long,
        /** Parte de [elapsedMs] con actividad: suma de los huecos entre eventos de hasta [ACTIVE_GAP_MS]. */
        val activeMs: Long,
        /** Veces que se ha conectado el servicio en el periodo (más de 1: el sistema lo ha reiniciado). */
        val connects: Long,
        /** Solo las plataformas de las que ha llegado algo. */
        val events: Map<Platform, KindCounts>,
        /** Eventos de un paquete que no es de ninguna plataforma (no debería pasar: el servicio filtra por paquete). */
        val unknownEvents: Long,
        /** `TYPE_WINDOW_CONTENT_CHANGED` recibidos en total (los de un envío rápido son normales). */
        val contentChanged: Long,
        /** Los de [contentChanged] que no esperaba ningún envío rápido: tiene que ser 0 (si no, se gasta batería de más). */
        val contentOutsideQuick: Long,
        val handled: Long,
        val totalNanos: Long,
        val maxNanos: Long,
        /** Eventos que han tardado más de [SLOW_EVENT_NANOS] (un fotograma). */
        val slow: Long,
        /** Solo lecturas que han recorrido algo (nodos visitados). */
        val reads: Map<Platform, ReadStats>,
        /** Lecturas que no llegaron a leer nada: otra ventana encima, sin raíz o de otra app. */
        val unreadable: Long,
        val presence: TimingStats,
        /** Pasos del envío rápido (lectura de las ventanas y decisión; sin la acción ni la foto). */
        val quick: TimingStats,
        val childFetches: Long,
        /** Los de [childFetches] pedidos durante un paso del envío rápido. */
        val quickChildFetches: Long,
        val parentFetches: Long,
        val finderQueries: Long,
        /** Eventos que llegaron con CAPTURAR encendido: su tiempo incluye el del propio registro. */
        val captured: Long,
        /** Mayor retraso de entrega de un evento (ms): desde que la app lo mandó hasta que entró en el servicio. */
        val lagMaxMs: Long = 0,
        /** Eventos que llegaron con [LAG_SLOW_MS] o más de retraso (proceso congelado o sin CPU). */
        val lagSlow: Long = 0,
        /** Hora (epoch ms) del último de [lagSlow]; null si no ha habido ninguno. */
        val lagLastAt: Long? = null,
    ) {
        /** [count] por minuto con actividad ([activeMs]): lo que cuesta mientras se usan las apps. */
        fun perMinute(count: Long): Double = count * 60_000.0 / activeMs.coerceAtLeast(1)

        /** Ha habido bastante actividad para que un ritmo por minuto diga algo. */
        val rateKnown: Boolean get() = activeMs >= MIN_RATE_MS

        /** Parte de los eventos que llegaron con CAPTURAR encendido (0 sin eventos). */
        val capturedFraction: Double get() = if (handled > 0) captured.toDouble() / handled else 0.0

        val avgEventMs: Double get() = if (handled > 0) totalNanos / 1e6 / handled else 0.0
        val maxEventMs: Double get() = maxNanos / 1e6

        /** Nodos por lectura de pantalla, de todas las apps (null si no ha habido lecturas). */
        val avgNodesPerRead: Double?
            get() {
                val count = reads.values.sumOf { it.count }
                return if (count > 0) reads.values.sumOf { it.nodesTotal }.toDouble() / count else null
            }
    }

    companion object {
        /** Un fotograma a 60 Hz: un evento que tarda más bloquea el hilo principal lo bastante para notarse. */
        const val SLOW_EVENT_NANOS = 16_000_000L

        /** Un evento que llega con este retraso o más no es una cola normal: el proceso no estaba corriendo. */
        const val LAG_SLOW_MS = 2_000L

        /** Con menos actividad, el ritmo por minuto no se enseña. */
        const val MIN_RATE_MS = 10_000L

        /** Un hueco entre dos eventos más largo es una pausa: no cuenta como tiempo con actividad. */
        const val ACTIVE_GAP_MS = 60_000L

        private const val NEVER = Long.MIN_VALUE

        fun kindOf(eventType: Int): Kind = when (eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> Kind.SCROLL
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> Kind.WINDOW
            AccessibilityEvent.TYPE_VIEW_CLICKED -> Kind.CLICK
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> Kind.CONTENT
            else -> Kind.OTHER
        }

        /** Los del servicio (se ponen a cero con VACIAR en Calibración; una reconexión no borra nada). */
        val live = ServiceMetrics()
    }
}
