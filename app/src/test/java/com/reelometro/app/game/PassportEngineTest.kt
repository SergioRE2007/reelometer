package com.reelometro.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Reglas del motor del pasaporte: Flap, referencia, experiencia, niveles y celebraciones. */
class PassportEngineTest {

    // ------------------------------------------------------------------ Flap y referencia

    @Test
    fun `sin historial ni objetivo Flap calienta en NORMAL y no hay nada`() {
        val s = calcular(LUNES, goal = null, counts = emptyMap()).state
        assertTrue(s.flap.warmingUp)
        assertEquals(FlapMood.NORMAL, s.flap.mood)
        assertEquals(0, s.trackedDays)
        assertEquals(0, s.streak)
        assertEquals(0, s.earnedStamps)
        assertEquals(StampId.entries.size, s.stamps.size)
        assertNull(s.challenge)
        assertEquals(LevelProgress.of(0), s.level)
        assertEquals(XpBreakdown(), s.xp)
        assertTrue(s.pending.isEmpty())
    }

    @Test
    fun `con objetivo y sin historial la referencia es el objetivo`() {
        val flap = calcular(LUNES, goal = 120, counts = emptyMap()).state.flap
        assertFalse(flap.warmingUp)
        assertEquals(120, flap.reference)
        assertTrue(flap.referenceIsGoal)
        assertEquals(0, flap.daysTracked)
        assertEquals(FlapMood.NORMAL, flap.mood)
    }

    @Test
    fun `un objetivo de 0 o negativo es como no tener objetivo`() {
        val s = calcular(dia(3), goal = 0, counts = dias(LUNES, 10, 10, 10)).state
        assertNull(s.goal)
        assertTrue(s.flap.warmingUp)
    }

    @Test
    fun `umbrales de Flap con los 7 días cerrados`() {
        val today = dia(30)
        val expected = mapOf(
            7 to FlapMood.EN_FORMA, 6 to FlapMood.EN_FORMA, 5 to FlapMood.NORMAL, 4 to FlapMood.NORMAL,
            3 to FlapMood.CANSADO, 2 to FlapMood.CANSADO, 1 to FlapMood.ZOMBI, 0 to FlapMood.ZOMBI,
        )
        for ((under, mood) in expected) {
            val window = (0 until 7).associate { i -> today.minusDays(7L - i) to if (i < 7 - under) 150 else 50 }
            val flap = calcular(today, goal = 100, counts = iguales(LUNES, 23, 50) + window).state.flap
            assertEquals("$under días bajo", mood, flap.mood)
            assertEquals(under, flap.daysUnder)
            assertEquals(7, flap.daysTracked)
            assertEquals(100, flap.reference)
            assertTrue(flap.referenceIsGoal)
        }
    }

    @Test
    fun `hoy solo empeora si ya se ha pasado de la referencia`() {
        val today = dia(30)
        val history = iguales(LUNES, 30, 50) + (today.minusDays(6) to 150)
        val atReference = calcular(today, 100, history + (today to 100)).state
        assertEquals(FlapMood.EN_FORMA, atReference.flap.mood)
        assertEquals(5, atReference.streak)
        val quiet = calcular(today, 100, history).state
        assertEquals(atReference.flap, quiet.flap)

        val over = calcular(today, 100, history + (today to 101)).state
        assertEquals(FlapMood.NORMAL, over.flap.mood)
        assertEquals(5, over.flap.daysUnder)
        assertEquals(7, over.flap.daysTracked)
        assertEquals(0, over.streak)
    }

    @Test
    fun `los días antes del primer registro no cuentan en contra`() {
        val today = dia(10)
        val good = calcular(today, 100, dias(today.minusDays(3), 50, 50, 50)).state.flap
        assertEquals(FlapMood.EN_FORMA, good.mood)
        assertEquals(3, good.daysTracked)
        val meh = calcular(today, 100, dias(today.minusDays(3), 150, 150, 50)).state.flap
        assertEquals(FlapMood.NORMAL, meh.mood)
        assertEquals(1, meh.daysUnder)
    }

    @Test
    fun `sin objetivo la referencia es la media de las 4 semanas anteriores a la ventana`() {
        val today = dia(40)
        val base = iguales(today.minusDays(35), 28, 100)
        val good = calcular(today, null, base + iguales(today.minusDays(7), 7, 90)).state.flap
        assertEquals(100, good.reference)
        assertFalse(good.referenceIsGoal)
        assertEquals(FlapMood.EN_FORMA, good.mood)
        val bad = calcular(today, null, base + iguales(today.minusDays(7), 7, 101)).state.flap
        assertEquals(FlapMood.ZOMBI, bad.mood)
        // Lo de antes de la base no cuenta.
        val older = calcular(today, null, iguales(dia(0), 5, 9_999) + base).state.flap
        assertEquals(100, older.reference)
    }

    @Test
    fun `la media redondea y nunca es 0`() {
        val today = dia(40)
        val alternating = (0 until 28).associate { i -> today.minusDays(35L - i) to if (i % 2 == 0) 100 else 101 }
        assertEquals(101, calcular(today, null, alternating).state.flap.reference)
        val almostNothing = mapOf(today.minusDays(35) to 1)
        assertEquals(1, calcular(today, null, almostNothing).state.flap.reference)
    }

    @Test
    fun `sin objetivo hace falta una semana de registro en la base`() {
        val today = dia(30)
        val six = calcular(today, null, iguales(today.minusDays(13), 13, 10)).state.flap
        assertTrue(six.warmingUp)
        assertEquals(FlapMood.NORMAL, six.mood)
        assertEquals(7, six.daysTracked)
        val seven = calcular(today, null, iguales(today.minusDays(14), 14, 10)).state.flap
        assertEquals(10, seven.reference)
    }

    @Test
    fun `el estado anterior de Flap solo sale si cambió`() {
        val history = iguales(LUNES, 10, 50)
        fun previous(seen: FlapMood?) =
            calcular(dia(10), 100, history, PassportMemory(lastSeenMood = seen)).state.flap.previousMood
        assertEquals(FlapMood.ZOMBI, previous(FlapMood.ZOMBI))
        assertNull(previous(FlapMood.EN_FORMA))
        assertNull(previous(null))
    }

    @Test
    fun `los días registrados van del primero a hoy y lo futuro se ignora`() {
        val s = calcular(dia(9), 100, dias(LUNES, 5) + (dia(20) to 400)).state
        assertEquals(10, s.trackedDays)
    }

    // ------------------------------------------------------------------ experiencia y nivel

    @Test
    fun `una semana entera bajo el objetivo, con sus sellos, nivel y celebraciones`() {
        val r = calcular(dia(7), 100, iguales(LUNES, 7, 60))
        val s = r.state
        assertEquals(XpBreakdown(days = 70, streaks = 45, challenges = 0, stamps = 150), s.xp)
        assertEquals(7, s.streak)
        assertEquals(TravelerLevel.FRECUENTE, s.level.level)
        assertEquals(265, s.level.xp)
        assertEquals(FlapMood.EN_FORMA, s.flap.mood)
        assertEquals(LUNES, s.fecha(StampId.PRIMER_DIA))
        assertEquals(dia(2), s.fecha(StampId.RACHA_3))
        assertEquals(dia(6), s.fecha(StampId.RACHA_7))
        assertEquals(dia(6), s.fecha(StampId.FIN_DE_SEMANA))
        assertEquals(dia(6), s.fecha(StampId.PRIMERA_SEMANA))
        assertEquals(dia(6), s.fecha(StampId.PRIMER_ASCENSO))
        assertNull(s.fecha(StampId.MEDIO_DIA))
        // Primer cálculo: lo de antes de ayer ya se da por celebrado; lo de ayer y el nivel, no.
        assertTrue(s.sello(StampId.PRIMER_DIA).celebrated)
        assertTrue(s.sello(StampId.RACHA_3).celebrated)
        assertFalse(s.sello(StampId.RACHA_7).celebrated)
        assertEquals(
            listOf(
                Celebration.StampEarned(StampId.FIN_DE_SEMANA, dia(6)),
                Celebration.StampEarned(StampId.RACHA_7, dia(6)),
                Celebration.StampEarned(StampId.PRIMERA_SEMANA, dia(6)),
                Celebration.StampEarned(StampId.PRIMER_ASCENSO, dia(6)),
                Celebration.LevelUp(TravelerLevel.FRECUENTE),
            ),
            s.pending,
        )
        // La memoria guarda lo conseguido.
        assertEquals(dia(7), r.memory.since)
        assertEquals(TravelerLevel.FRECUENTE, r.memory.highestLevel)
        assertEquals(dia(6), r.memory.stamps[StampId.PRIMER_ASCENSO])
        assertTrue("stamp:PRIMER_DIA" in r.memory.celebrated)
    }

    @Test
    fun `calcular otra vez con la memoria devuelta no cambia nada`() {
        val first = calcular(dia(7), 100, iguales(LUNES, 7, 60))
        val again = calcular(dia(7), 100, iguales(LUNES, 7, 60), first.memory)
        assertEquals(first.memory, again.memory)
        assertEquals(first.state, again.state)
    }

    @Test
    fun `las rachas largas dan bonificación a los 14, 30 y cada 30 días`() {
        assertEquals(105, calcular(dia(14), 100, iguales(LUNES, 14, 60)).state.xp.streaks)
        assertEquals(225, calcular(dia(59), 100, iguales(LUNES, 59, 60)).state.xp.streaks)
        val sixty = calcular(dia(60), 100, iguales(LUNES, 60, 60)).state
        assertEquals(345, sixty.xp.streaks)
        assertEquals(60, sixty.streak)
        assertEquals(dia(13), sixty.fecha(StampId.RACHA_14))
        assertEquals(dia(29), sixty.fecha(StampId.RACHA_30))
    }

    @Test
    fun `una racha rota no quita nada y la siguiente vuelve a dar`() {
        val s = calcular(dia(7), 100, dias(LUNES, 60, 60, 60, 150, 60, 60, 60)).state
        assertEquals(30, s.xp.streaks)
        assertEquals(60, s.xp.days)
        assertEquals(3, s.streak)
        assertEquals(dia(2), s.fecha(StampId.RACHA_3))
        assertNull("un sello conseguido no tiene barra", s.sello(StampId.RACHA_3).progress)
        assertEquals(3f / 7, s.sello(StampId.RACHA_7).progress ?: -1f, 0.0001f)
    }

    @Test
    fun `ver más vídeos nunca da más experiencia`() {
        val less = dias(LUNES, 10, 40, 90, 500, 10, 30, 100, 20)
        val more = less.mapValues { (_, v) -> v * 3 }
        val xpLess = calcular(dia(8), 100, less).state.xp.total
        val xpMore = calcular(dia(8), 100, more).state.xp.total
        assertTrue("$xpMore > $xpLess", xpMore <= xpLess)
        // Pasarse más un día que ya estaba pasado no cambia nada.
        val muchMore = less + (dia(3) to 50_000)
        assertEquals(calcular(dia(8), 100, less).state.xp, calcular(dia(8), 100, muchMore).state.xp)
    }

    @Test
    fun `los sellos irónicos no dan experiencia ni barra de progreso`() {
        val history = GameHistory(
            earlyBirdOn = dia(1),
            threePlatformsOn = dia(1),
            fiveCategoriesOn = dia(1),
            marathonOn = dia(1),
        )
        val counts = dias(LUNES, 9_000, 1_200)
        val s = calcular(dia(2), 100, counts, history = history).state
        StampId.entries.filter { it.ironic }.forEach { assertNotNull("$it", s.fecha(it)) }
        assertEquals(0, s.xp.total)
        val pendingIronic = calcular(dia(2), 100, dias(LUNES, 5)).state.stamps.filter { it.id.ironic }
        pendingIronic.forEach { assertNull("${it.id}", it.progress) }
    }

    @Test
    fun `el nivel conseguido no baja aunque cambie el objetivo`() {
        val history = iguales(LUNES, 7, 60)
        val withGoal = calcular(dia(7), 100, history)
        val stricter = calcular(dia(7), 10, history, withGoal.memory).state
        assertEquals(TravelerLevel.FRECUENTE, stricter.level.level)
        assertEquals(TravelerLevel.FRECUENTE.minXp, stricter.level.xp)
        assertEquals(0f, stricter.level.fraction)
        // Los sellos guardados tampoco se pierden.
        assertEquals(LUNES, stricter.fecha(StampId.PRIMER_DIA))
        assertEquals(dia(6), stricter.fecha(StampId.RACHA_7))
        assertEquals(0, stricter.xp.days)
    }

    @Test
    fun `un nivel guardado sin historial se mantiene y su sello sale hoy`() {
        val s = calcular(dia(5), null, emptyMap(), PassportMemory(highestLevel = TravelerLevel.ORO)).state
        assertEquals(TravelerLevel.ORO, s.level.level)
        assertEquals(TravelerLevel.ORO.minXp, s.level.xp)
        assertEquals(dia(5), s.fecha(StampId.PRIMER_ASCENSO))
        assertEquals(Celebration.LevelUp(TravelerLevel.ORO), s.pending.last())
    }

    @Test
    fun `la fracción del nivel siempre está entre 0 y 1`() {
        listOf(0, 7, 30, 90, 200).forEach { n ->
            val level = calcular(dia(n), 100, iguales(LUNES, n, 60)).state.level
            assertTrue("$n días: ${level.fraction}", level.fraction in 0f..1f)
            assertTrue(level.xp >= level.level.minXp)
        }
    }

    @Test
    fun `los retos cumplidos dan experiencia`() {
        val memory = estrenada().copy(
            challenges = listOf(
                ChallengeResult(dia(-28), 100, 90, won = true),
                ChallengeResult(dia(-21), 100, 120, won = false),
                ChallengeResult(dia(-14), 100, 90, won = true),
            ),
        )
        val s = calcular(LUNES, null, emptyMap(), memory).state
        assertEquals(100, s.xp.challenges)
        assertEquals(dia(-22), s.fecha(StampId.PRIMER_RETO))
        assertNull(s.fecha(StampId.CUATRO_RETOS))
        assertEquals(0.5f, s.sello(StampId.CUATRO_RETOS).progress ?: -1f, 0f)
        assertEquals(StampId.PRIMER_RETO.xp, s.xp.stamps)
    }

    // ------------------------------------------------------------------ celebraciones

    @Test
    fun `al volver a encender el modo no llega de golpe lo conseguido con el modo oculto`() {
        // Estrenado hace un año y oculto desde entonces: tres semanas bajo el objetivo sin calcular nada.
        val hidden = estrenada()
        val avalanche = calcular(dia(21), 100, iguales(LUNES, 21, 60), hidden).state.pending
        assertTrue("sin rebase saldrían los sellos antiguos", avalanche.count { it is Celebration.StampEarned } > 3)

        val state = calcular(dia(21), 100, iguales(LUNES, 21, 60), hidden.rebased()).state
        val stamps = state.pending.filterIsInstance<Celebration.StampEarned>()
        assertTrue("$stamps", stamps.all { !it.on.isBefore(dia(20)) })
        assertTrue(state.pending.none { it is Celebration.ChallengeWon })
    }

    @Test
    fun `rebase conserva lo guardado y sube la generación`() {
        val memory = estrenada().copy(generation = 2, stamps = mapOf(StampId.PRIMER_DIA to dia(1)))
        val rebased = memory.rebased()
        assertNull(rebased.since)
        assertEquals(3, rebased.generation)
        assertEquals(memory.stamps, rebased.stamps)
    }

    @Test
    fun `marcar como celebrado las quita para siempre`() {
        val first = calcular(dia(7), 100, iguales(LUNES, 7, 60))
        val seen = first.memory.celebrate(first.state.pending.map { it.key })
        val after = calcular(dia(8), 100, iguales(LUNES, 8, 60), seen).state
        assertTrue(after.pending.isEmpty())
        after.stamps.filter { it.earned }.forEach { assertTrue("${it.id}", it.celebrated) }
    }

    @Test
    fun `lo que se descubre después del estreno se celebra aunque sea de un día antiguo`() {
        // Un día a 0 solo cuenta cuando hay vídeos después.
        val memory = estrenada()
        val before = calcular(dia(10), 100, mapOf(LUNES to 60), memory)
        assertNull(before.state.fecha(StampId.DIA_EN_BLANCO))
        val after = calcular(dia(10), 100, mapOf(LUNES to 60, dia(10) to 5), before.memory).state
        assertEquals(dia(1), after.fecha(StampId.DIA_EN_BLANCO))
        assertTrue(Celebration.StampEarned(StampId.DIA_EN_BLANCO, dia(1)) in after.pending)
    }

    @Test
    fun `el orden de las celebraciones es sellos, retos y nivel al final`() {
        val memory = estrenada().copy(challenges = listOf(ChallengeResult(dia(-7), 100, 50, won = true)))
        val s = calcular(dia(3), 100, dias(LUNES, 60, 60, 60), memory).state
        // 40 + 50 (reto) el domingo, +30 el lunes, +10, y el miércoles +55: FRECUENTE el miércoles.
        assertEquals(
            listOf(
                Celebration.StampEarned(StampId.PRIMER_RETO, dia(-1)),
                Celebration.StampEarned(StampId.PRIMER_DIA, LUNES),
                Celebration.StampEarned(StampId.RACHA_3, dia(2)),
                Celebration.StampEarned(StampId.PRIMER_ASCENSO, dia(2)),
                Celebration.ChallengeWon(ChallengeResult(dia(-7), 100, 50, won = true)),
                Celebration.LevelUp(TravelerLevel.FRECUENTE),
            ),
            s.pending,
        )
    }

    @Test
    fun `cada nivel se celebra una vez y el siguiente vuelve a salir`() {
        val memory = estrenada().copy(highestLevel = TravelerLevel.FRECUENTE, celebrated = setOf("level:FRECUENTE"))
        val same = calcular(LUNES, null, emptyMap(), memory).state
        assertTrue(same.pending.none { it is Celebration.LevelUp })
        val higher = calcular(LUNES, null, emptyMap(), memory.copy(highestLevel = TravelerLevel.PLATA)).state
        assertEquals(listOf(Celebration.LevelUp(TravelerLevel.PLATA)), higher.pending.filterIsInstance<Celebration.LevelUp>())
    }

    @Test
    fun `al estrenar el pasaporte los retos antiguos no se celebran`() {
        val counts = iguales(LUNES, 7, 100) + iguales(dia(7), 7, 50) + iguales(dia(14), 7, 50)
        val s = calcular(dia(30), null, counts).state
        val won = s.pastChallenges.filter { it.won }
        assertTrue(won.isNotEmpty())
        assertTrue(s.pending.none { it is Celebration.ChallengeWon })
    }
}
