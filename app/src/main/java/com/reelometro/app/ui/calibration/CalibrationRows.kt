package com.reelometro.app.ui.calibration

import androidx.compose.runtime.Immutable
import com.reelometro.app.R
import com.reelometro.app.copy.CalibrationCopy
import com.reelometro.app.copy.Words
import com.reelometro.app.copy.str
import com.reelometro.app.data.Platform
import com.reelometro.app.data.RecentVideo
import com.reelometro.app.detection.CalibrationEvent
import com.reelometro.app.detection.CalibrationEventType
import com.reelometro.app.stats.Format
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.IdentityHashMap

/** Filtro de tipo de la consola: grupos de [CalibrationEventType]. */
enum class TypeFilter(val label: String) {
    ALL(CalibrationCopy.ALL_TYPES),
    SCROLL(CalibrationCopy.TYPE_SCROLL),
    WINDOW(CalibrationCopy.TYPE_WINDOW),
    VIDEO(CalibrationCopy.TYPE_VIDEO),
    SHARE(CalibrationCopy.TYPE_SHARE),
    ;

    /** ¿Entra el evento (o, con `null`, un vídeo de la base de datos) en este filtro? */
    fun accepts(type: CalibrationEventType?): Boolean = when (this) {
        ALL -> true
        SCROLL -> type == CalibrationEventType.SCROLL
        WINDOW -> type == CalibrationEventType.WINDOW || type == CalibrationEventType.LEAVE
        VIDEO -> type == null || type in VIDEO_TYPES || type == CalibrationEventType.INFO
        SHARE -> type == CalibrationEventType.CLICK || type == CalibrationEventType.SHARE
    }
}

private val VIDEO_TYPES = setOf(
    CalibrationEventType.VIDEO_START,
    CalibrationEventType.VIDEO_END,
    CalibrationEventType.VIDEO_DISCARD,
)

/**
 * Una línea de la consola, ya formateada (nada se calcula al pintar). [key] es estable mientras la
 * línea siga en pantalla: los eventos, por identidad ([EventKeys]); los vídeos guardados, por su id
 * en negativo para no chocar nunca con los eventos.
 */
@Immutable
data class CalibrationRow(
    val key: Long,
    val time: Long,
    val timeText: String,
    /** «SCROLL · TikTok», «VÍDEO 128 · Reels». */
    val title: String,
    /** FEED ✓ (true) / FEED ✗ (false) / sin decisión (null). */
    val decision: Boolean?,
    val rule: String?,
    /** Datos técnicos en una línea: «id=… · clase=… · idx 3→4 de 120 · dy=1840 · nodo 1080×2210 (92 %)». */
    val details: String,
    /** Vídeo detectado (evento de vídeo o fila de la base de datos): se resalta con filete `signal`. */
    val video: Boolean,
    val type: CalibrationEventType?,
    val platform: Platform?,
)

/**
 * Claves estables para los eventos del registro: el registro emite listas nuevas, pero cada evento es
 * la misma instancia mientras sigue en el búfer, así que se numeran por identidad. Se olvidan los que
 * ya han salido del búfer.
 */
class EventKeys {
    private var next = 1L
    private var keys = IdentityHashMap<CalibrationEvent, Long>()

    fun assign(events: List<CalibrationEvent>): List<Long> {
        val fresh = IdentityHashMap<CalibrationEvent, Long>(events.size * 2)
        val out = ArrayList<Long>(events.size)
        for (e in events) {
            val k = keys[e] ?: fresh[e] ?: next++
            fresh[e] = k
            out += k
        }
        keys = fresh
        return out
    }
}

object CalibrationRows {

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    /** «12:03:44» o «—». */
    fun clock(at: Long?, zone: ZoneId): String =
        at?.let { CLOCK.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: CalibrationCopy.NONE

    /**
     * Mezcla los eventos (más reciente primero) con los vídeos guardados desde [since], ordenados del
     * más reciente al más antiguo, y aplica los filtros.
     */
    fun build(
        events: List<CalibrationEvent>,
        keys: List<Long>,
        videos: List<RecentVideo>,
        since: Long,
        platform: Platform?,
        type: TypeFilter,
        zone: ZoneId,
    ): List<CalibrationRow> {
        val eventRows = events.mapIndexedNotNull { i, e ->
            val p = e.platform ?: Platform.fromPackage(e.packageName)
            if ((platform == null || p == platform) && type.accepts(e.type)) eventRow(e, keys[i], p, zone) else null
        }
        val videoRows = videos.mapNotNull { v ->
            if (v.startedAt >= since && (platform == null || v.platform == platform) && type.accepts(null)) videoRow(v, zone) else null
        }
        return mergeByTime(eventRows, videoRows.sortedByDescending { it.time })
    }

    /** Une dos listas ya ordenadas de más reciente a más antigua. */
    private fun mergeByTime(a: List<CalibrationRow>, b: List<CalibrationRow>): List<CalibrationRow> {
        if (b.isEmpty()) return a
        if (a.isEmpty()) return b
        val out = ArrayList<CalibrationRow>(a.size + b.size)
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            if (a[i].time >= b[j].time) out += a[i++] else out += b[j++]
        }
        while (i < a.size) out += a[i++]
        while (j < b.size) out += b[j++]
        return out
    }

    fun eventRow(e: CalibrationEvent, key: Long, platform: Platform?, zone: ZoneId): CalibrationRow {
        val who = platform?.shortName ?: e.packageName
        return CalibrationRow(
            key = key,
            time = e.time,
            timeText = TIME.format(Instant.ofEpochMilli(e.time).atZone(zone)),
            title = listOfNotNull(e.type.name, who).joinToString(" · "),
            decision = e.decision,
            rule = e.rule,
            details = details(e),
            video = e.type in VIDEO_TYPES,
            type = e.type,
            platform = platform,
        )
    }

    fun videoRow(v: RecentVideo, zone: ZoneId): CalibrationRow = CalibrationRow(
        key = -v.id,
        time = v.startedAt,
        timeText = TIME.format(Instant.ofEpochMilli(v.startedAt).atZone(zone)),
        title = "${CalibrationCopy.videoTitle(shortId(v.id))} · ${v.platform.shortName}",
        decision = null,
        rule = null,
        details = buildList {
            add(if (v.durationMs > 0) Format.seconds(v.durationMs) else CalibrationCopy.ON_SCREEN)
            v.creator?.takeIf { it.isNotBlank() }?.let { add(Words.handle(it)) }
            add(v.category.label)
            if (v.shares > 0) add(CalibrationCopy.shares(v.shares))
            v.caption?.takeIf { it.isNotBlank() }?.let { add(str(R.string.ui_calibration_caption, it.take(CAPTION_MAX))) }
        }.joinToString(" · "),
        video = true,
        type = null,
        platform = v.platform,
    )

    /** Los datos de `CalibrationEvent.toLine()` (más el paquete), sin hora ni tipo: van en la cabecera de la fila. */
    fun details(e: CalibrationEvent): String = buildList {
        // TikTok tiene dos paquetes (musically / trill): el nombre de la plataforma no basta.
        if (e.platform != null) e.packageName?.let { add(it) }
        e.viewId?.let { add("id=${it.substringAfter(":id/")}") }
        e.className?.let { add(str(R.string.ui_calibration_class, it.substringAfterLast('.'))) }
        if (e.fromIndex >= 0 || e.toIndex >= 0) {
            val from = e.fromIndex.toString()
            val to = e.toIndex.toString()
            add(
                if (e.itemCount >= 0) str(R.string.ui_calibration_index_of, from, to, e.itemCount.toString())
                else str(R.string.ui_calibration_index, from, to),
            )
        } else if (e.itemCount >= 0) {
            add(str(R.string.ui_calibration_items, e.itemCount.toString()))
        }
        e.scrollDeltaY?.let { add("dy=$it") }
        e.scrollDeltaX?.takeIf { it != 0 }?.let { add("dx=$it") }
        if (e.nodeWidth > 0 || e.nodeHeight > 0) {
            val w = e.nodeWidth.toString()
            val h = e.nodeHeight.toString()
            add(
                if (e.screenHeight > 0) {
                    str(R.string.ui_calibration_node_share, w, h, Format.percent(e.nodeHeight.toDouble() / e.screenHeight))
                } else {
                    str(R.string.ui_calibration_node, w, h)
                },
            )
        }
        if (e.screenWidth > 0 || e.screenHeight > 0) add(str(R.string.ui_calibration_screen, e.screenWidth.toString(), e.screenHeight.toString()))
        e.videoId?.let { add(str(R.string.ui_calibration_video, shortId(it))) }
        e.durationMs?.let { add(Format.seconds(it)) }
        e.note?.let { add(it) }
    }.joinToString(" · ")

    /**
     * Los ids de vídeo son `inicio en ms × 1000 + secuencia` (19 cifras): en pantalla basta con las 6
     * últimas para casar cada VIDEO_START con su fila guardada. El log exportado lleva el id entero.
     */
    fun shortId(id: Long): String = "#" + (Math.floorMod(id, SHORT_ID_MOD)).toString().padStart(SHORT_ID_DIGITS, '0')

    private const val SHORT_ID_MOD = 1_000_000L
    private const val SHORT_ID_DIGITS = 6
    private const val CAPTION_MAX = 60
}
