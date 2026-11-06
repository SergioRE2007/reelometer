package com.reelometro.app.data.export

import com.reelometro.app.copy.PassportCopy
import com.reelometro.app.game.ChallengeResult
import com.reelometro.app.game.GameEvent
import com.reelometro.app.game.PassportFakes
import com.reelometro.app.game.PassportRules
import com.reelometro.app.game.StampId
import com.reelometro.app.game.StampState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate

class PassportCsvTest {

    private val today = LocalDate.of(2026, 11, 4)

    private val state = PassportFakes.fresh(today).copy(
        stamps = StampId.entries.map { id ->
            when (id) {
                StampId.RACHA_3 -> StampState(id, earnedOn = LocalDate.of(2026, 11, 3))
                StampId.PRIMER_DIA -> StampState(id, earnedOn = LocalDate.of(2026, 11, 1))
                StampId.MADRUGADOR -> StampState(id, earnedOn = LocalDate.of(2026, 11, 3))
                else -> StampState(id)
            }
        },
        pastChallenges = listOf(
            ChallengeResult(LocalDate.of(2026, 10, 26), target = 1_100, total = 1_250, won = false),
            ChallengeResult(LocalDate.of(2026, 10, 19), target = 1_200, total = 980, won = true),
        ),
    )

    private val events = mapOf(
        GameEvent.TICKET_SAVED to LocalDate.of(2026, 11, 2),
        GameEvent.WRAPPED_SHARED to LocalDate.of(2026, 11, 1),
    )

    @Test
    fun `filas del bloque PASAPORTE en orden`() {
        val rows = PassportCsv.rows(state, events)
        assertEquals(
            listOf(
                listOf("nivel", PassportCopy.levelName(state.level.level), "", "", "", "0", state.level.level.name),
                listOf("sello", PassportCopy.stampName(StampId.PRIMER_DIA), "2026-11-01", "", "", "${StampId.PRIMER_DIA.xp}", "PRIMER_DIA"),
                listOf("sello", PassportCopy.stampName(StampId.RACHA_3), "2026-11-03", "", "", "${StampId.RACHA_3.xp}", "RACHA_3"),
                listOf("sello", PassportCopy.stampName(StampId.MADRUGADOR), "2026-11-03", "", "", "0", "MADRUGADOR"),
                listOf("reto", "cumplido", "2026-10-19", "1200", "980", "${PassportRules.CHALLENGE_XP}", ""),
                listOf("reto", "no cumplido", "2026-10-26", "1100", "1250", "0", ""),
                listOf("evento", "Resumen compartido", "2026-11-01", "", "", "", "WRAPPED_SHARED"),
                listOf("evento", "Ticket guardado", "2026-11-02", "", "", "", "TICKET_SAVED"),
            ),
            rows,
        )
        rows.forEach { assertEquals(PassportCsv.HEADER.size, it.size) }
    }

    @Test
    fun `el bloque va tras una línea en blanco, sin BOM y sin cerrar el flujo`() {
        var closed = false
        val out = object : ByteArrayOutputStream() {
            override fun close() {
                closed = true
                super.close()
            }
        }
        PassportCsv.write(out, state, events)
        assertFalse(closed)
        val text = String(out.toByteArray(), Charsets.UTF_8)
        assertFalse(text.startsWith(Csv.BOM))
        val lines = text.split(Csv.LINE_END)
        assertEquals("", lines[0])
        assertEquals("PASAPORTE", lines[1])
        assertEquals("tipo;elemento;fecha;tope;vídeos;experiencia;clave", lines[2])
        assertEquals("", lines.last())
        assertEquals(3 + PassportCsv.rows(state, events).size + 1, lines.size)
    }

    @Test
    fun `un pasaporte nuevo solo lleva el nivel`() {
        val rows = PassportCsv.rows(PassportFakes.fresh(today), emptyMap())
        assertEquals(1, rows.size)
        assertEquals("nivel", rows.single()[0])
        assertTrue(PassportCsv.text(PassportFakes.fresh(today), emptyMap()).endsWith(Csv.LINE_END))
    }

    @Test
    fun `cada evento tiene su nombre legible`() {
        GameEvent.entries.forEach { assertTrue("$it", PassportCsv.eventLabel(it).isNotBlank()) }
    }
}
