package com.reelometro.app.data.export

import com.reelometro.app.copy.PassportCopy
import com.reelometro.app.game.ChallengeResult
import com.reelometro.app.game.GameEvent
import com.reelometro.app.game.PassportFakes
import com.reelometro.app.game.PassportImport
import com.reelometro.app.game.PassportMemory
import com.reelometro.app.game.StampId
import com.reelometro.app.game.StampState
import com.reelometro.app.game.TravelerLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Bloque PASAPORTE al importar: lectura (por clave o por nombre) y mezcla con lo que ya hay. */
class PassportImportTest {

    private val d1 = LocalDate.of(2026, 3, 3)
    private val d2 = LocalDate.of(2026, 3, 5)

    private fun block(vararg rows: List<String>, header: List<String> = PassportCsv.HEADER) =
        CsvBlock(PassportCsv.TITLE, header, rows.toList())

    @Test
    fun `ida y vuelta del bloque con la columna clave`() {
        val today = LocalDate.of(2026, 11, 4)
        val state = PassportFakes.fresh(today).copy(
            stamps = StampId.entries.map { if (it == StampId.RACHA_3) StampState(it, earnedOn = d2) else StampState(it) },
            pastChallenges = listOf(ChallengeResult(LocalDate.of(2026, 10, 19), 1_200, 980, won = true)),
        )
        val events = mapOf(GameEvent.TICKET_SAVED to d1)
        val read = PassportCsv.read(block(*PassportCsv.rows(state, events).toTypedArray()))
        assertEquals(mapOf(StampId.RACHA_3 to d2), read.stamps)
        assertEquals(state.level.level, read.highestLevel)
        assertEquals(state.pastChallenges, read.challenges)
        assertEquals(events, read.events)
    }

    @Test
    fun `CSV viejo sin clave - por el nombre en español y retos por su texto`() {
        val old = PassportCsv.HEADER.dropLast(1)
        val read = PassportCsv.read(
            block(
                listOf("nivel", "FRECUENTE", "", "", "", "210"),
                listOf("sello", "PRIMER DÍA", "2026-03-03", "", "", "20"),
                listOf("sello", "primer dia", "2026-03-01", "", "", "20"), // sin tildes y antes: gana la más antigua
                listOf("sello", "SELLO RETIRADO", "2026-03-04", "", "", "20"),
                listOf("reto", "no cumplido", "2026-02-23", "300", "350", "0"),
                listOf("reto", "a medias", "2026-03-02", "300", "350", "0"),
                listOf("evento", "Resumen compartido", "2026-03-04", "", "", ""),
                listOf("evento", "Ticket guardado", "no es una fecha", "", "", ""),
                header = old,
            ),
        )
        assertEquals(TravelerLevel.FRECUENTE, read.highestLevel)
        assertEquals(mapOf(StampId.PRIMER_DIA to LocalDate.of(2026, 3, 1)), read.stamps)
        assertEquals(listOf(ChallengeResult(LocalDate.of(2026, 2, 23), 300, 350, won = false)), read.challenges)
        assertEquals(mapOf(GameEvent.WRAPPED_SHARED to LocalDate.of(2026, 3, 4)), read.events)
    }

    @Test
    fun `el nombre visible del idioma de la app también vale`() {
        val read = PassportCsv.read(block(listOf("sello", PassportCopy.stampName(StampId.MARATON), "2026-03-03", "", "", "0", "")))
        assertEquals(setOf(StampId.MARATON), read.stamps.keys)
    }

    @Test
    fun `bloque vacío no trae nada`() {
        val read = PassportCsv.read(block())
        assertNull(read.highestLevel)
        assertEquals(PassportImport(), read)
    }

    @Test
    fun `imported se queda con lo más antiguo, el nivel más alto y los retos guardados`() {
        val saved = ChallengeResult(LocalDate.of(2026, 2, 23), 300, 250, won = true)
        val memory = PassportMemory(
            since = LocalDate.of(2026, 11, 1),
            stamps = mapOf(StampId.PRIMER_DIA to d2, StampId.RACHA_3 to d1),
            highestLevel = TravelerLevel.PLATA,
            challenges = listOf(saved),
            events = mapOf(GameEvent.TICKET_SAVED to d2),
            celebrated = setOf("x"),
        )
        val merged = memory.imported(
            PassportImport(
                stamps = mapOf(StampId.PRIMER_DIA to d1, StampId.RACHA_3 to d2, StampId.MARATON to d2),
                highestLevel = TravelerLevel.FRECUENTE,
                challenges = listOf(
                    ChallengeResult(LocalDate.of(2026, 2, 23), 300, 999, won = false),
                    ChallengeResult(LocalDate.of(2026, 2, 16), 300, 100, won = true),
                ),
                events = mapOf(GameEvent.TICKET_SAVED to d1, GameEvent.WRAPPED_SHARED to d2),
            ),
        )
        assertEquals(mapOf(StampId.PRIMER_DIA to d1, StampId.RACHA_3 to d1, StampId.MARATON to d2), merged.stamps)
        assertEquals(TravelerLevel.PLATA, merged.highestLevel)
        assertEquals(listOf(LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 23)), merged.challenges.map { it.weekStart })
        assertEquals(saved, merged.challenges.last())
        assertEquals(mapOf(GameEvent.TICKET_SAVED to d1, GameEvent.WRAPPED_SHARED to d2), merged.events)
        assertTrue("x" in merged.celebrated)

        // Tras importar siempre hay base nueva: el próximo cálculo da por celebrado lo de antes de ayer.
        val rebased = merged.rebased()
        assertNull(rebased.since)
        assertEquals(memory.generation + 1, rebased.generation)
    }

    @Test
    fun `nivel más alto del fichero sube el guardado`() {
        val merged = PassportMemory().imported(PassportImport(highestLevel = TravelerLevel.ORO))
        assertEquals(TravelerLevel.ORO, merged.highestLevel)
        // Los niveles hasta el del fichero ya se celebraron en el otro móvil.
        assertTrue(com.reelometro.app.game.Celebration.LevelUp(TravelerLevel.ORO).key in merged.celebrated)
        assertTrue(com.reelometro.app.game.Celebration.LevelUp(TravelerLevel.FRECUENTE).key in merged.celebrated)
        assertFalse(com.reelometro.app.game.Celebration.LevelUp(TravelerLevel.PLATINO).key in merged.celebrated)
    }
}
