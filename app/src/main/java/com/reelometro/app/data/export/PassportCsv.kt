package com.reelometro.app.data.export

import com.reelometro.app.copy.AppTexts
import com.reelometro.app.copy.PassportCopy
import com.reelometro.app.copy.Texts
import com.reelometro.app.game.ChallengeResult
import com.reelometro.app.game.GameEvent
import com.reelometro.app.game.PassportImport
import com.reelometro.app.game.PassportRules
import com.reelometro.app.game.PassportState
import com.reelometro.app.game.StampId
import com.reelometro.app.game.StampState
import com.reelometro.app.game.TravelerLevel
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.LocalDate

/**
 * Bloque PASAPORTE del CSV de exportación, detrás de los vídeos (puro, sin Android): una línea en
 * blanco, el título `PASAPORTE`, la cabecera y una fila por cosa guardada o conseguida.
 *
 * Columnas: `tipo` (`nivel`, `sello`, `reto`, `evento`), `elemento`, `fecha` (ISO, `2026-11-03`),
 * `tope` y `vídeos` (solo en los retos), `experiencia` (la del nivel, la que dio cada sello o reto) y,
 * desde la 1.6.0, `clave` (`TravelerLevel`, `StampId` o `GameEvent` por su nombre, para importar sin
 * depender del idioma de `elemento`).
 * - nivel: el nivel actual y la experiencia (una fila).
 * - sello: los conseguidos, por fecha, con el nombre que se ve en el pasaporte.
 * - reto: los retos cerrados, del más antiguo al más reciente; `fecha` es el lunes de la semana y
 *   `elemento`, `cumplido` o `no cumplido`.
 * - evento: la primera vez de cada evento (resumen compartido, ticket guardado…).
 * Los números van sin separador de millar, como en el resto del CSV.
 */
object PassportCsv {
    const val TITLE = "PASAPORTE"

    val HEADER = listOf("tipo", "elemento", "fecha", "tope", "vídeos", "experiencia", "clave")

    private const val LEVEL = "nivel"
    private const val STAMP = "sello"
    private const val CHALLENGE = "reto"
    private const val EVENT = "evento"
    private const val WON = "cumplido"
    private const val LOST = "no cumplido"

    /** Filas del bloque (sin título ni cabecera). */
    fun rows(state: PassportState, events: Map<GameEvent, LocalDate>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val level = state.level.level
        rows += listOf(LEVEL, PassportCopy.levelName(level), "", "", "", state.level.xp.toString(), level.name)
        state.stamps
            .filter { it.earnedOn != null }
            .sortedWith(compareBy<StampState> { it.earnedOn }.thenBy { it.id.ordinal })
            .forEach { stamp ->
                rows += listOf(
                    STAMP, PassportCopy.stampName(stamp.id), stamp.earnedOn.toString(), "", "", stamp.id.xp.toString(),
                    stamp.id.name,
                )
            }
        state.pastChallenges
            .sortedBy { it.weekStart }
            .forEach { result ->
                rows += listOf(
                    CHALLENGE,
                    if (result.won) WON else LOST,
                    result.weekStart.toString(),
                    result.target.toString(),
                    result.total.toString(),
                    (if (result.won) PassportRules.CHALLENGE_XP else 0).toString(),
                    "",
                )
            }
        events.entries
            .sortedWith(compareBy<Map.Entry<GameEvent, LocalDate>> { it.value }.thenBy { it.key.ordinal })
            .forEach { (event, date) -> rows += listOf(EVENT, eventLabel(event), date.toString(), "", "", "", event.name) }
        return rows
    }

    /** El bloque entero como texto (empieza con una línea en blanco que lo separa de los vídeos). */
    fun text(state: PassportState, events: Map<GameEvent, LocalDate>): String = buildString {
        append(Csv.LINE_END)
        append(Csv.row(listOf(TITLE)))
        append(Csv.row(HEADER))
        rows(state, events).forEach { append(Csv.row(it)) }
    }

    /** Escribe el bloque en [out] en UTF-8 (sin BOM: va detrás de los vídeos). No cierra [out]. */
    fun write(out: OutputStream, state: PassportState, events: Map<GameEvent, LocalDate>) {
        val writer = OutputStreamWriter(out, Charsets.UTF_8)
        writer.write(text(state, events))
        writer.flush()
    }

    fun eventLabel(event: GameEvent): String = when (event) {
        GameEvent.WRAPPED_SHARED -> "Resumen compartido"
        GameEvent.TICKET_SAVED -> "Ticket guardado"
        GameEvent.LEVEL_TICKET_SHARED -> "Tarjeta de embarque compartida"
    }

    /**
     * Lee el bloque (importar): lo que no se puede recalcular del historial. Cada fila se reconoce por
     * `clave`; sin ella (CSV de la 1.1.0 a la 1.5.0), por `elemento` comparado con los nombres del idioma
     * de la app y de cada uno de [otherTexts] (el español de las versiones viejas). Lo que no se
     * reconoce (filas rotas, sellos retirados) se ignora.
     */
    fun read(block: CsvBlock, otherTexts: List<Texts> = emptyList()): PassportImport {
        val names = Names.of(listOf(AppTexts.current) + otherTexts)
        val stamps = HashMap<StampId, LocalDate>()
        val events = HashMap<GameEvent, LocalDate>()
        val challenges = HashMap<LocalDate, ChallengeResult>()
        var level: TravelerLevel? = null
        for (row in block.rows) {
            val kind = block.field(row, "tipo")?.let(CsvReader::normalize) ?: continue
            val key = block.field(row, "clave").orEmpty()
            val element = block.field(row, "elemento").orEmpty()
            val date = block.field(row, "fecha")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            when (kind) {
                LEVEL -> {
                    val l = byName<TravelerLevel>(key) ?: names.levels[CsvReader.normalize(element)] ?: continue
                    level = if (level == null) l else maxOf(level, l)
                }
                STAMP -> {
                    val id = byName<StampId>(key) ?: names.stamps[CsvReader.normalize(element)] ?: continue
                    if (date == null) continue
                    val known = stamps[id]
                    if (known == null || date.isBefore(known)) stamps[id] = date
                }
                EVENT -> {
                    val event = byName<GameEvent>(key) ?: names.events[CsvReader.normalize(element)] ?: continue
                    if (date == null) continue
                    val known = events[event]
                    if (known == null || date.isBefore(known)) events[event] = date
                }
                CHALLENGE -> {
                    val won = when (CsvReader.normalize(element)) {
                        WON -> true
                        LOST -> false
                        else -> continue
                    }
                    if (date == null) continue
                    val target = block.field(row, "tope")?.toIntOrNull() ?: continue
                    val total = block.field(row, "vídeos")?.toIntOrNull() ?: continue
                    challenges.putIfAbsent(date, ChallengeResult(date, target, total, won))
                }
            }
        }
        return PassportImport(stamps, level, challenges.values.sortedBy { it.weekStart }, events)
    }

    private inline fun <reified E : Enum<E>> byName(key: String): E? =
        key.trim().uppercase().takeIf { it.isNotEmpty() }?.let { k -> enumValues<E>().firstOrNull { it.name == k } }

    /** Nombres visibles → clave, en todos los idiomas pedidos (comparados con [CsvReader.normalize]). */
    private class Names(
        val levels: Map<String, TravelerLevel>,
        val stamps: Map<String, StampId>,
        val events: Map<String, GameEvent>,
    ) {
        companion object {
            fun of(texts: List<Texts>): Names {
                val levels = HashMap<String, TravelerLevel>()
                val stamps = HashMap<String, StampId>()
                texts.forEach { t ->
                    runCatching {
                        AppTexts.using(t) {
                            TravelerLevel.entries.forEach { levels.putIfAbsent(CsvReader.normalize(PassportCopy.levelName(it)), it) }
                            StampId.entries.forEach { stamps.putIfAbsent(CsvReader.normalize(PassportCopy.stampName(it)), it) }
                        }
                    }
                }
                val events = GameEvent.entries.associateBy { CsvReader.normalize(eventLabel(it)) }
                return Names(levels, stamps, events)
            }
        }
    }
}
