package com.reelometro.app.data.export

import com.reelometro.app.copy.PassportCopy
import com.reelometro.app.game.GameEvent
import com.reelometro.app.game.PassportRules
import com.reelometro.app.game.PassportState
import com.reelometro.app.game.StampState
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.LocalDate

/**
 * Bloque PASAPORTE del CSV de exportación, detrás de los vídeos (puro, sin Android): una línea en
 * blanco, el título `PASAPORTE`, la cabecera y una fila por cosa guardada o conseguida.
 *
 * Columnas: `tipo` (`nivel`, `sello`, `reto`, `evento`), `elemento`, `fecha` (ISO, `2026-11-03`),
 * `tope` y `vídeos` (solo en los retos) y `experiencia` (la del nivel, la que dio cada sello o reto).
 * - nivel: el nivel actual y la experiencia (una fila).
 * - sello: los conseguidos, por fecha, con el nombre que se ve en el pasaporte.
 * - reto: los retos cerrados, del más antiguo al más reciente; `fecha` es el lunes de la semana y
 *   `elemento`, `cumplido` o `no cumplido`.
 * - evento: la primera vez de cada evento (resumen compartido, ticket guardado…).
 * Los números van sin separador de millar, como en el resto del CSV.
 */
object PassportCsv {
    const val TITLE = "PASAPORTE"

    val HEADER = listOf("tipo", "elemento", "fecha", "tope", "vídeos", "experiencia")

    /** Filas del bloque (sin título ni cabecera). */
    fun rows(state: PassportState, events: Map<GameEvent, LocalDate>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        rows += listOf("nivel", PassportCopy.levelName(state.level.level), "", "", "", state.level.xp.toString())
        state.stamps
            .filter { it.earnedOn != null }
            .sortedWith(compareBy<StampState> { it.earnedOn }.thenBy { it.id.ordinal })
            .forEach { stamp ->
                rows += listOf("sello", PassportCopy.stampName(stamp.id), stamp.earnedOn.toString(), "", "", stamp.id.xp.toString())
            }
        state.pastChallenges
            .sortedBy { it.weekStart }
            .forEach { result ->
                rows += listOf(
                    "reto",
                    if (result.won) "cumplido" else "no cumplido",
                    result.weekStart.toString(),
                    result.target.toString(),
                    result.total.toString(),
                    (if (result.won) PassportRules.CHALLENGE_XP else 0).toString(),
                )
            }
        events.entries
            .sortedWith(compareBy<Map.Entry<GameEvent, LocalDate>> { it.value }.thenBy { it.key.ordinal })
            .forEach { (event, date) -> rows += listOf("evento", eventLabel(event), date.toString(), "", "", "") }
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
}
