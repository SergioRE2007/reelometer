package com.reelometro.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PassportMemoryTest {

    private val full = PassportMemory(
        generation = 3,
        since = dia(-30),
        stamps = mapOf(StampId.PRIMER_DIA to dia(-20), StampId.MADRUGADOR to dia(-3)),
        celebrated = setOf("stamp:PRIMER_DIA", "level:FRECUENTE"),
        highestLevel = TravelerLevel.PLATA,
        lastSeenMood = FlapMood.CANSADO,
        challenges = listOf(
            ChallengeResult(dia(-14), target = 699, total = 600, won = true),
            ChallengeResult(dia(-7), target = 540, total = 700, won = false),
        ),
        events = mapOf(GameEvent.WRAPPED_SHARED to dia(-2), GameEvent.TICKET_SAVED to dia(-1)),
    )

    @Test
    fun `el JSON guarda y recupera toda la memoria`() {
        val json = PassportMemoryCodec.encode(full)
        assertEquals(full, PassportMemoryCodec.decode(json))
        assertTrue("las fechas van en ISO: $json", "\"2026-10-13\"" in json)
    }

    @Test
    fun `un texto vacío o ilegible da un pasaporte vacío`() {
        assertEquals(PassportMemory(), PassportMemoryCodec.decode(null))
        assertEquals(PassportMemory(), PassportMemoryCodec.decode(""))
        assertEquals(PassportMemory(), PassportMemoryCodec.decode("{esto no es json"))
        assertEquals(PassportMemory(), PassportMemoryCodec.decode("[]"))
    }

    @Test
    fun `lo que no se entiende se ignora sin perder lo demás`() {
        val json = """
            {"v":9,"generation":2,"since":"ayer","futuro":true,
             "stamps":{"PRIMER_DIA":"2026-11-02","SELLO_RETIRADO":"2026-11-03","RACHA_3":"no-es-fecha"},
             "celebrated":["stamp:PRIMER_DIA"],"highestLevel":"DIAMANTE","lastSeenMood":"ZOMBI",
             "challenges":[{"weekStart":"2026-10-26","target":10,"total":5,"won":true},
                           {"weekStart":"mal","target":1,"total":1,"won":false}],
             "events":{"WRAPPED_SHARED":"2026-11-01","OTRO":"2026-11-01"}}
        """.trimIndent()
        val memory = PassportMemoryCodec.decode(json)
        assertEquals(2, memory.generation)
        assertNull(memory.since)
        assertEquals(mapOf(StampId.PRIMER_DIA to LUNES), memory.stamps)
        assertEquals(setOf("stamp:PRIMER_DIA"), memory.celebrated)
        assertEquals(TravelerLevel.PASAJERO, memory.highestLevel)
        assertEquals(FlapMood.ZOMBI, memory.lastSeenMood)
        assertEquals(listOf(ChallengeResult(dia(-7), 10, 5, true)), memory.challenges)
        assertEquals(mapOf(GameEvent.WRAPPED_SHARED to dia(-1)), memory.events)
    }

    @Test
    fun `absorber no pierde nada de lo guardado`() {
        val update = PassportMemory(
            since = dia(0),
            stamps = mapOf(StampId.PRIMER_DIA to dia(-1), StampId.RACHA_3 to dia(-1)),
            celebrated = setOf("stamp:MADRUGADOR"),
            highestLevel = TravelerLevel.FRECUENTE,
            lastSeenMood = FlapMood.EN_FORMA,
            challenges = listOf(
                ChallengeResult(dia(-7), target = 1, total = 1, won = true),
                ChallengeResult(dia(-21), target = 5, total = 4, won = true),
            ),
            events = mapOf(GameEvent.WRAPPED_SHARED to dia(-5), GameEvent.TICKET_SAVED to dia(0)),
        )
        val merged = full.absorb(update)
        assertEquals(dia(-30), merged.since)
        assertEquals(dia(-20), merged.stamps[StampId.PRIMER_DIA])
        assertEquals(dia(-1), merged.stamps[StampId.RACHA_3])
        assertEquals(setOf("stamp:PRIMER_DIA", "level:FRECUENTE", "stamp:MADRUGADOR"), merged.celebrated)
        assertEquals(TravelerLevel.PLATA, merged.highestLevel)
        assertEquals(FlapMood.CANSADO, merged.lastSeenMood)
        assertEquals(listOf(dia(-21), dia(-14), dia(-7)), merged.challenges.map { it.weekStart })
        assertFalse("el reto guardado manda", merged.challenges.last().won)
        assertEquals(dia(-5), merged.events[GameEvent.WRAPPED_SHARED])
        assertEquals(dia(-1), merged.events[GameEvent.TICKET_SAVED])
        assertEquals(3, merged.generation)
    }

    @Test
    fun `cada evento se guarda con su primera fecha`() {
        val once = PassportMemory().withEvent(GameEvent.WRAPPED_SHARED, dia(3))
        assertEquals(dia(3), once.events[GameEvent.WRAPPED_SHARED])
        assertSame(once, once.withEvent(GameEvent.WRAPPED_SHARED, dia(5)))
        assertEquals(dia(1), once.withEvent(GameEvent.WRAPPED_SHARED, dia(1)).events[GameEvent.WRAPPED_SHARED])
    }

    @Test
    fun `celebrar, ver a Flap y borrar`() {
        val memory = PassportMemory()
        val celebrated = memory.celebrate(listOf("stamp:RACHA_3"))
        assertEquals(setOf("stamp:RACHA_3"), celebrated.celebrated)
        assertSame(celebrated, celebrated.celebrate(listOf("stamp:RACHA_3")))
        assertSame(celebrated, celebrated.celebrate(emptyList()))
        val seen = memory.seeing(FlapMood.ZOMBI)
        assertEquals(FlapMood.ZOMBI, seen.lastSeenMood)
        assertSame(seen, seen.seeing(FlapMood.ZOMBI))
        val cleared = full.cleared()
        assertEquals(PassportMemory(generation = 4), cleared)
    }

    @Test
    fun `las celebraciones de prueba quedan pendientes`() {
        val today = dia(10)
        val stamp = full.forcing(ForcedCelebration.STAMP, StampId.PRIMER_DIA, today)
        assertEquals(today, stamp.stamps[StampId.PRIMER_DIA])
        assertFalse("stamp:PRIMER_DIA" in stamp.celebrated)

        val level = PassportMemory(celebrated = setOf("level:FRECUENTE")).forcing(ForcedCelebration.LEVEL, StampId.PRIMER_DIA, today)
        assertEquals(TravelerLevel.FRECUENTE, level.highestLevel)
        assertFalse("level:FRECUENTE" in level.celebrated)
        assertEquals(TravelerLevel.PLATA, full.forcing(ForcedCelebration.LEVEL, StampId.PRIMER_DIA, today).highestLevel)

        // Jueves 12: el reto de la semana pasada es el del lunes 2.
        val challenge = full.forcing(ForcedCelebration.CHALLENGE, StampId.PRIMER_DIA, today)
        val last = challenge.challenges.last()
        assertEquals(dia(0), last.weekStart)
        assertTrue(last.won)
        assertFalse(Celebration.ChallengeWon(last).key in challenge.celebrated)
        assertEquals(dia(-30), challenge.since)
    }

    @Test
    fun `el tipo de celebración de prueba se lee del gancho de QA`() {
        assertEquals(ForcedCelebration.STAMP, ForcedCelebration.parse(null))
        assertEquals(ForcedCelebration.STAMP, ForcedCelebration.parse(" "))
        assertEquals(ForcedCelebration.LEVEL, ForcedCelebration.parse("level"))
        assertEquals(ForcedCelebration.CHALLENGE, ForcedCelebration.parse(" Challenge "))
        assertNull(ForcedCelebration.parse("confeti"))
    }
}
