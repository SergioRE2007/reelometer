package com.reelometro.app.detection

import com.reelometro.app.data.Platform
import com.reelometro.app.detection.FeedRules.IndexStyle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Lógica pura (sin Android; la hora llega en cada llamada) que decide cuándo empieza un vídeo, cuánto
 * dura y cuándo se descarta, a partir de los eventos del feed ya aceptados por [FeedRules].
 * No es thread-safe: el servicio la usa siempre desde el hilo principal.
 *
 * Posición en el feed, de más a menos fiable:
 *  1. Índices del contenedor. Con [IndexStyle.VISIBLE_RANGE] (RecyclerView) el vídeo está asentado
 *     cuando `from == to`; con [IndexStyle.CURRENT_ITEM] (ViewPager) el índice es la página actual y
 *     cambia al soltar el dedo.
 *  2. Desplazamiento vertical acumulado (`scrollDeltaY`): posición = desplazamiento / alto de página;
 *     asentado cuando cae cerca de un entero.
 *  3. Ráfagas de scroll (último recurso, Android < 9 sin índices): un vídeo por ráfaga.
 *
 * Reglas:
 *  - **Permanencia mínima**: un vídeo que está en pantalla menos de [Config.minDwellMs] (rebote,
 *    página intermedia de un fling) se descarta con [Recorder.videoDiscarded].
 *  - **Rebotes / volver atrás**: volver a un vídeo ya visto del mismo feed reanuda su registro en vez
 *    de contar otro. Por eso [Recorder.videoFinished] puede llegar varias veces para el mismo id
 *    (manda la última: es la duración total).
 *  - **Fling** que salta páginas: solo cuenta la página en la que se asienta.
 *  - Índices que cambian **sin movimiento** (la app inserta o borra datos) no cuentan un vídeo nuevo.
 *  - **Primer vídeo** de cada feed (al abrir la app, al volver de otra pantalla): el que ya estaba en
 *    pantalla también se cuenta, con inicio estimado en la llegada (como mucho [Config.maxPreviousMs]).
 *  - **Duración máxima** por vídeo [Config.maxVideoMs] (pausas, móvil olvidado con el vídeo en bucle).
 *  - Salir (otra app, pantalla apagada, otra pantalla de la app, plataforma desactivada) pausa el
 *    vídeo; si se vuelve al mismo vídeo antes de [Config.resumeWindowMs], se reanuda.
 *  - **Rango que no se cierra**: si un RecyclerView nunca llega a `from == to` (la página vecina
 *    asoma un píxel, márgenes…), el desplazamiento acumulado desde el último asiento decide cuándo
 *    se ha pasado de página.
 *  - **Segunda señal por contenido** (YouTube Shorts): el servicio compara el autor y el título del
 *    vídeo visible. Si cambian sin scroll, [onContentChanged] cuenta el vídeo que se ha perdido; si un
 *    vídeo «nuevo» resulta ser el mismo que el anterior, [mergeActiveInto] lo une al anterior.
 */
class ViewTracker(
    private val recorder: Recorder,
    private val config: Config = Config(),
) {

    interface Recorder {
        /** Empieza un vídeo nuevo. Se cuenta ya, para que el contador vaya en directo. */
        fun videoStarted(id: Long, platform: Platform, startedAt: Long)

        /** Duración total acumulada (ms). Puede llamarse más de una vez para el mismo id: manda la última. */
        fun videoFinished(id: Long, durationMs: Long)

        /** El vídeo no llegó a verse (rebote de menos de [Config.minDwellMs]): hay que borrarlo. */
        fun videoDiscarded(id: Long)

        /** Se vuelve a un vídeo ya registrado (no se cuenta otra vez; su duración sigue sumando). */
        fun videoResumed(id: Long) {}
    }

    data class Config(
        val minDwellMs: Long = MIN_DWELL_MS,
        val maxVideoMs: Long = MAX_VIDEO_MS,
        val maxPreviousMs: Long = MAX_PREVIOUS_MS,
        val burstGapMs: Long = BURST_GAP_MS,
        val resumeWindowMs: Long = RESUME_WINDOW_MS,
        /** Distancia máxima (en páginas) a un entero para considerar el feed asentado sin índices. */
        val settleTolerance: Double = 0.12,
        /** Un solo evento que mueve más de esto (en páginas) es un salto programático, no un dedo. */
        val jumpPages: Double = 1.5,
        val maxRecordsPerFeed: Int = 40,
        val maxFeeds: Int = 6,
        /** Margen para [FeedScroll.resumeOnReturn]: volver a la app antes de esto es volver al mismo vídeo. */
        val quickReturnMs: Long = QUICK_RETURN_MS,
    )

    /** Un evento de scroll del feed. */
    data class FeedScroll(
        val platform: Platform,
        /** Identifica el contenedor (plataforma + id/clase + instancia del nodo). */
        val feedKey: String,
        val fromIndex: Int = -1,
        val toIndex: Int = -1,
        val itemCount: Int = -1,
        /** Desplazamiento vertical del evento (px), o null si no se conoce (Android < 9). */
        val deltaY: Int? = null,
        /** Alto del contenedor (px) = alto de una página. */
        val pageHeight: Int = 0,
        val indexStyle: IndexStyle = IndexStyle.VISIBLE_RANGE,
        val now: Long,
        /** Tolerancia de asiento de esta app (en páginas); `null` = [Config.settleTolerance]. */
        val settleTolerance: Double? = null,
        /**
         * Si el usuario sale de la app y vuelve antes de [Config.quickReturnMs], el feed quieto que aparece es el vídeo
         * que dejó: se reanuda en vez de contarlo otra vez (YouTube redibuja el feed como un contenedor nuevo).
         */
        val resumeOnReturn: Boolean = false,
    )

    private enum class Motion { NONE, SWIPE, JUMP, UNKNOWN }

    private class Record(val id: Long, val platform: Platform, val startedAt: Long) {
        var accumulatedMs = 0L
        var activeSince = -1L
        var pausedAt = -1L

        fun total(at: Long): Long = accumulatedMs + if (activeSince >= 0) (at - activeSince).coerceAtLeast(0) else 0
    }

    private class Feed(val key: String, val platform: Platform) {
        /** Vídeos vistos en este feed por índice (para reanudar en vez de contar otra vez). */
        val records = LinkedHashMap<Int, Record>()
        var lastIndex: Int? = null
        var lastItemCount = -1
        var offsetPx = 0L
        var pendingMotionPx = 0L
        var midSeen = false
        var lastBurstAt: Long? = null

        /** Desplazamiento (px, con signo) desde el último asiento, mientras el rango no se cierra. */
        var rangeOffsetPx = 0L
        var pageHeight = 0
    }

    private class Active(val record: Record, val feed: Feed, var index: Int)

    private var active: Active? = null
    private val feeds = LinkedHashMap<String, Feed>(16, 0.75f, true)
    private var foreground: Platform? = null

    /** Cuándo llegó el usuario a la pantalla/app actual (para estimar el inicio del primer vídeo). */
    private var contextSince = 0L

    /** El vídeo que se veía al salir de la app ([onLeave]), por si se vuelve enseguida ([FeedScroll.resumeOnReturn]). */
    private var leftBehind: Active? = null
    private var leftAt = 0L
    private var idSeq = 0L

    /** Cambia con cada vídeo nuevo, reanudado o pausado (para saber si una lectura sigue siendo válida). */
    var version: Long = 0L
        private set

    val currentVideoId: Long? get() = active?.record?.id
    val currentPlatform: Platform? get() = active?.record?.platform
    val isActive: Boolean get() = active != null

    /** Hay un evento de [platform] (ventana, scroll, clic): está en primer plano. */
    fun onAppForeground(platform: Platform, now: Long) {
        if (foreground == platform) return
        if (active != null && active?.record?.platform != platform) pause(now)
        foreground = platform
        contextSince = now
    }

    /** Ha cambiado la actividad de [platform] (otra pantalla de la app): el vídeo en curso ya no se ve. */
    fun onScreenChanged(platform: Platform, now: Long) {
        leftBehind = null
        onAppForeground(platform, now)
        if (active?.record?.platform == platform) pause(now)
        contextSince = now
    }

    /** El usuario está en otra pantalla de la app (feed normal, perfil…) o el feed ya no se ve. */
    fun onLeaveFeed(platform: Platform, now: Long) {
        leftBehind = null // ha pasado por otra pantalla: lo próximo que aparezca no es el vídeo que dejó
        onAppForeground(platform, now)
        if (active?.record?.platform == platform) pause(now)
        contextSince = max(contextSince, now)
    }

    /** El usuario salió de la app, apagó la pantalla, etc. */
    fun onLeave(now: Long) {
        val leaving = active
        pause(now)
        leftBehind = leaving?.takeIf { it.feed.records.containsValue(it.record) }
        leftAt = now
        foreground = null
        contextSince = now
    }

    /** La plataforma se ha desactivado en Ajustes: se cierra su vídeo y se olvidan sus feeds. */
    fun onPlatformDisabled(platform: Platform, now: Long) {
        if (active?.record?.platform == platform) pause(now)
        feeds.values.removeAll { it.platform == platform }
        if (foreground == platform) {
            foreground = null
            contextSince = now
        }
    }

    /** Llamar de vez en cuando mientras hay un vídeo activo: aplica la duración máxima en directo. */
    fun tick(now: Long) {
        val a = active ?: return
        if (a.record.total(now) >= config.maxVideoMs) pause(now)
    }

    /** Atajo con los parámetros de siempre (rango visible, sin `itemCount`). */
    fun onFeedScroll(
        platform: Platform,
        feedKey: String,
        fromIndex: Int,
        toIndex: Int,
        deltaY: Int?,
        pageHeight: Int,
        now: Long,
    ): Boolean = onFeedScroll(FeedScroll(platform, feedKey, fromIndex, toIndex, -1, deltaY, pageHeight, IndexStyle.VISIBLE_RANGE, now))

    /** @return true si se ha empezado a contar al menos un vídeo nuevo. */
    fun onFeedScroll(s: FeedScroll): Boolean {
        onAppForeground(s.platform, s.now)
        val feed = feedFor(s.feedKey, s.platform)
        if (s.pageHeight > 0) feed.pageHeight = s.pageHeight
        var datasetChanged = false
        if (s.itemCount >= 0) {
            if (feed.lastItemCount >= 0 && s.itemCount != feed.lastItemCount) {
                datasetChanged = true
                // Si hay menos elementos, los índices antiguos ya no apuntan a los mismos vídeos.
                if (s.itemCount < feed.lastItemCount) forgetOtherPositions(feed)
            }
            feed.lastItemCount = s.itemCount
        }
        val dy = s.deltaY
        return when {
            s.fromIndex >= 0 && s.toIndex >= 0 -> onIndexed(feed, s, datasetChanged)
            dy != null && s.pageHeight > 0 -> onDelta(feed, s, dy)
            else -> onBurst(feed, s.now)
        }
    }

    // ── 1. Índices ───────────────────────────────────────────────────────────────────────────────

    private fun onIndexed(feed: Feed, s: FeedScroll, datasetChanged: Boolean): Boolean {
        val dyAbs = abs(s.deltaY ?: 0).toLong()
        val here = active?.takeIf { it.feed === feed }
        if (s.fromIndex != s.toIndex) {
            // A mitad de un deslizamiento (rango visible de un RecyclerView).
            feed.pendingMotionPx += dyAbs
            feed.midSeen = true
            if (here == null) {
                // Primer evento de este feed: el vídeo que se va es el que ya estaba en pantalla.
                val leaving = if ((s.deltaY ?: 0) < 0) s.toIndex else s.fromIndex
                val arrival = enterFeed(s.now)
                val isNew = activate(feed, leaving, newSince = arrival, resumeSince = arrival)
                feed.rangeOffsetPx = (s.deltaY ?: 0).toLong()
                return isNew
            }
            return settleByOffset(feed, here, s)
        }
        val index = s.fromIndex
        val motion = motionOf(feed, s, dyAbs)
        feed.pendingMotionPx = 0
        feed.midSeen = false
        feed.rangeOffsetPx = 0
        if (here == null) return firstSettle(feed, index, motion, s)
        if (index == here.index) return false // mismo vídeo: rebote, relayout…
        return when (motion) {
            Motion.NONE -> {
                reindex(feed, here, index)
                false
            }
            Motion.JUMP -> if (datasetChanged) {
                reindex(feed, here, index)
                false
            } else {
                moveTo(feed, index, s.now)
            }
            Motion.SWIPE, Motion.UNKNOWN -> moveTo(feed, index, s.now)
        }
    }

    /**
     * Con el rango visible abierto (`from != to`), ¿se ha avanzado ya una página entera? Sirve para los
     * contenedores que nunca informan de `from == to` en reposo. En un feed normal llega antes o a la vez
     * que el asiento y este ya no cuenta nada (mismo índice).
     */
    private fun settleByOffset(feed: Feed, here: Active, s: FeedScroll): Boolean {
        val dy = s.deltaY ?: return false
        if (s.pageHeight <= 0) return false
        feed.rangeOffsetPx += dy
        val pos = feed.rangeOffsetPx.toDouble() / s.pageHeight
        val pages = Math.round(pos)
        if (pages == 0L || abs(pos - pages) > (s.settleTolerance ?: config.settleTolerance)) return false
        feed.rangeOffsetPx -= pages * s.pageHeight
        return moveTo(feed, here.index + pages.toInt(), s.now)
    }

    private fun motionOf(feed: Feed, s: FeedScroll, dyAbs: Long): Motion {
        if (s.deltaY == null) return if (feed.midSeen) Motion.SWIPE else Motion.UNKNOWN
        val total = feed.pendingMotionPx + dyAbs
        return when {
            total == 0L && !feed.midSeen -> Motion.NONE
            !feed.midSeen && s.pageHeight > 0 && dyAbs > config.jumpPages * s.pageHeight -> Motion.JUMP
            else -> Motion.SWIPE
        }
    }

    /** Primer evento asentado de un feed (sesión nueva, vuelta de otra pantalla, otro feed). */
    private fun firstSettle(feed: Feed, index: Int, motion: Motion, s: FeedScroll): Boolean {
        // Con rango visible, asentarse tras moverse significa que ya se ha pasado del vídeo anterior.
        // Con página actual (ViewPager), el primer evento llega al empezar a arrastrar: es el de pantalla.
        val previous: Int? = when {
            s.indexStyle != IndexStyle.VISIBLE_RANGE -> null
            motion == Motion.SWIPE -> index - direction(s.deltaY)
            motion == Motion.UNKNOWN && index > 0 -> index - 1
            else -> null
        }
        val arrival = enterFeed(s.now)
        if (previous == null) {
            // Vuelta rápida a la app: el feed recién dibujado y quieto es el vídeo que se dejó (se reanuda).
            val back = leftBehind
            if (s.resumeOnReturn && motion == Motion.NONE && back != null && back.record.platform == feed.platform &&
                feed.records.isEmpty() && s.now - leftAt <= config.quickReturnMs
            ) {
                back.feed.records.values.remove(back.record)
                feed.records[index] = back.record
                leftBehind = null
                return activate(feed, index, newSince = s.now, resumeSince = s.now)
            }
            // Sin movimiento (el feed acaba de dibujarse) el vídeo aparece ahora; si no, desde la llegada.
            val appearsNow = motion == Motion.NONE || motion == Motion.JUMP ||
                (motion == Motion.UNKNOWN && s.indexStyle == IndexStyle.VISIBLE_RANGE)
            return activate(feed, index, newSince = if (appearsNow) s.now else arrival, resumeSince = arrival)
        }
        val previousIsNew = activate(feed, previous, newSince = arrival, resumeSince = arrival)
        val currentIsNew = moveTo(feed, index, s.now)
        return previousIsNew || currentIsNew
    }

    // ── 2. Desplazamiento acumulado ──────────────────────────────────────────────────────────────

    private fun onDelta(feed: Feed, s: FeedScroll, dy: Int): Boolean {
        val page = s.pageHeight.toDouble()
        feed.offsetPx += dy
        val pos = feed.offsetPx / page
        val nearest = Math.round(pos)
        val here = active?.takeIf { it.feed === feed }
        if (abs(pos - nearest) > (s.settleTolerance ?: config.settleTolerance)) {
            feed.midSeen = true
            if (here == null) {
                val leaving = (if (dy >= 0) floor(pos) else ceil(pos)).toInt()
                val arrival = enterFeed(s.now)
                return activate(feed, leaving, newSince = arrival, resumeSince = arrival)
            }
            return false
        }
        feed.offsetPx = (nearest * page).roundToLong() // re-anclar para no acumular error
        val index = nearest.toInt()
        val motion = if (dy == 0 && !feed.midSeen) Motion.NONE else Motion.SWIPE
        feed.midSeen = false
        if (here == null) return firstSettle(feed, index, motion, s.copy(indexStyle = IndexStyle.VISIBLE_RANGE))
        if (index == here.index) return false
        return moveTo(feed, index, s.now)
    }

    // ── 3. Ráfagas ───────────────────────────────────────────────────────────────────────────────

    private fun onBurst(feed: Feed, now: Long): Boolean {
        val last = feed.lastBurstAt
        feed.lastBurstAt = now
        if (last != null && now - last <= config.burstGapMs) return false
        val here = active?.takeIf { it.feed === feed }
        if (here != null) return moveTo(feed, here.index + 1, now)
        // Primera ráfaga: el vídeo que había en pantalla también se ha visto.
        val arrival = enterFeed(now)
        val base = feed.lastIndex ?: 0
        val previousIsNew = activate(feed, base, newSince = arrival, resumeSince = arrival)
        val currentIsNew = moveTo(feed, base + 1, now)
        return previousIsNew || currentIsNew
    }

    // ── 4. Contenido (segunda señal) ─────────────────────────────────────────────────────────────

    /**
     * El vídeo visible de [platform] ha cambiado sin que llegara un scroll que lo dijera (lo ha visto el
     * servicio comparando autor y título). [step] = +1 siguiente, −1 anterior (vuelve a uno ya visto y
     * se reanuda). @return true si se ha empezado a contar un vídeo nuevo.
     */
    fun onContentChanged(platform: Platform, now: Long, step: Int = 1): Boolean {
        val a = active ?: return false
        if (a.record.platform != platform || step == 0) return false
        val feed = a.feed
        feed.pendingMotionPx = 0
        feed.midSeen = false
        feed.rangeOffsetPx = 0
        val index = a.index + step
        // Sin índices, la posición sale del desplazamiento: se mueve también para que el próximo
        // deslizamiento real no caiga en el índice que ya hemos contado.
        if (feed.pageHeight > 0) feed.offsetPx = index.toLong() * feed.pageHeight
        return moveTo(feed, index, now)
    }

    /**
     * El vídeo en curso es en realidad [previousId] (mismo autor y título): el registro nuevo se descarta
     * y el anterior se reanuda con el tiempo que lleva este. Pasa cuando la app rehace el contenedor del
     * feed o llega un scroll falso. @return true si se han unido.
     */
    fun mergeActiveInto(previousId: Long, now: Long): Boolean {
        val a = active ?: return false
        val current = a.record
        if (current.id == previousId) return false
        var previous: Record? = null
        var previousFeed: Feed? = null
        for (f in feeds.values) {
            val found = f.records.values.firstOrNull { it.id == previousId } ?: continue
            previous = found
            previousFeed = f
            break
        }
        val p = previous ?: return false
        if (p.platform != current.platform || p.activeSince >= 0) return false
        a.feed.records.values.remove(current)
        previousFeed?.records?.values?.remove(p)
        p.accumulatedMs += current.accumulatedMs
        p.activeSince = if (current.activeSince >= 0) current.activeSince else now
        p.pausedAt = -1
        a.feed.records[a.index] = p
        active = Active(p, a.feed, a.index)
        version++
        recorder.videoDiscarded(current.id)
        recorder.videoResumed(p.id)
        return true
    }

    // ── Registro ─────────────────────────────────────────────────────────────────────────────────

    /** Cierra el vídeo de otro feed (si lo hay) y devuelve la hora estimada de llegada a este. */
    private fun enterFeed(now: Long): Long {
        val other = active
        if (other != null) {
            // Si hubo un cambio de contexto (ventana, app…) después de que empezara, ahí acabó; si no, ahora.
            val at = if (contextSince > other.record.activeSince && contextSince <= now) contextSince else now
            pause(at)
            return at
        }
        return max(contextSince, now - config.maxPreviousMs).coerceAtMost(now)
    }

    private fun moveTo(feed: Feed, index: Int, now: Long): Boolean {
        pause(now)
        return activate(feed, index, newSince = now, resumeSince = now)
    }

    /** Activa el vídeo de [index]: reanuda su registro si es reciente o empieza uno nuevo. @return true si es nuevo. */
    private fun activate(feed: Feed, index: Int, newSince: Long, resumeSince: Long): Boolean {
        val old = feed.records[index]
        val resumable = old != null && old.pausedAt >= 0 && resumeSince - old.pausedAt <= config.resumeWindowMs
        val record: Record
        if (old != null && resumable) {
            old.activeSince = max(resumeSince, old.pausedAt)
            old.pausedAt = -1
            record = old
        } else {
            record = Record(nextId(newSince), feed.platform, newSince).also { it.activeSince = newSince }
            feed.records.remove(index)
            feed.records[index] = record
            trimRecords(feed)
        }
        active = Active(record, feed, index)
        feed.lastIndex = index
        version++
        val isNew = record !== old
        if (isNew) recorder.videoStarted(record.id, record.platform, record.startedAt) else recorder.videoResumed(record.id)
        return isNew
    }

    private fun pause(at: Long) {
        val a = active ?: return
        active = null
        version++
        // Un deslizamiento a medias de antes de la pausa no debe contar como movimiento al volver.
        a.feed.pendingMotionPx = 0
        a.feed.midSeen = false
        a.feed.rangeOffsetPx = 0
        val r = a.record
        val total = r.total(at)
        r.accumulatedMs = total
        r.activeSince = -1
        r.pausedAt = at
        if (total < config.minDwellMs) {
            a.feed.records.values.remove(r)
            recorder.videoDiscarded(r.id)
        } else {
            recorder.videoFinished(r.id, total.coerceAtMost(config.maxVideoMs))
        }
    }

    /** Mismo contenido, otros índices (se han insertado o borrado elementos): no es un vídeo nuevo. */
    private fun reindex(feed: Feed, a: Active, index: Int) {
        feed.records.clear()
        feed.records[index] = a.record
        a.index = index
        feed.lastIndex = index
    }

    private fun forgetOtherPositions(feed: Feed) {
        val keep = active?.takeIf { it.feed === feed }
        feed.records.clear()
        if (keep != null) feed.records[keep.index] = keep.record
    }

    private fun feedFor(key: String, platform: Platform): Feed {
        feeds[key]?.let { return it }
        val feed = Feed(key, platform)
        feeds[key] = feed
        if (feeds.size > config.maxFeeds) {
            val keep = active?.feed
            feeds.values.firstOrNull { it !== keep && it !== feed }?.let { feeds.remove(it.key) }
        }
        return feed
    }

    private fun trimRecords(feed: Feed) {
        val keep = active?.record
        val iterator = feed.records.entries.iterator()
        while (feed.records.size > config.maxRecordsPerFeed && iterator.hasNext()) {
            if (iterator.next().value !== keep) iterator.remove()
        }
    }

    private fun direction(deltaY: Int?): Int = if ((deltaY ?: 0) < 0) -1 else 1

    private fun nextId(startedAt: Long): Long = startedAt * 1000 + (idSeq++ % 1000)

    companion object {
        /** Menos de esto en pantalla es un rebote o una página intermedia: no se ha visto. */
        const val MIN_DWELL_MS = 600L

        /** Nadie ve un vídeo corto más de 3 min seguidos: si pasa, dejó el móvil o cambió de pantalla. */
        const val MAX_VIDEO_MS = 3 * 60_000L

        /** Inicio estimado del vídeo que ya estaba en pantalla: como mucho 1 minuto antes. */
        const val MAX_PREVIOUS_MS = 60_000L
        const val BURST_GAP_MS = 1_200L

        /** Volver a la app antes de esto, con el feed quieto, es volver al mismo vídeo (solo apps con resumeOnReturn). */
        const val QUICK_RETURN_MS = 2 * 60_000L

        /** Volver al mismo vídeo dentro de este margen lo reanuda en vez de contarlo otra vez. */
        const val RESUME_WINDOW_MS = 10 * 60_000L
    }
}
