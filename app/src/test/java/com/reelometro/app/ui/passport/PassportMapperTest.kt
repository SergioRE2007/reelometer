package com.reelometro.app.ui.passport

import com.reelometro.app.copy.CopyRules
import com.reelometro.app.copy.CopyRules.plain
import com.reelometro.app.copy.PassportCopy
import com.reelometro.app.copy.TicketLine
import com.reelometro.app.game.ChallengeStatus
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.PassportFakes
import com.reelometro.app.game.StampId
import com.reelometro.app.game.TravelerLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** De `PassportState` a lo que pinta la pestaña (JVM puro). */
class PassportMapperTest {

    private val today = LocalDate.of(2026, 11, 4)

    private fun texts(c: PassportUiState.Content): List<String> = buildList {
        add(c.kicker)
        add(c.stampsCount)
        c.referenceNote?.let(::add)
        with(c.flap) { addAll(listOfNotNull(label, record, reference, phrase, description)) }
        with(c.level) {
            addAll(listOfNotNull(code, name, tagline, nextCode, nextName, index, miles, toNext, note, description))
            (breakdown + total + streak).forEach { addAll(listOf(it.label, it.value)) }
        }
        when (val ch = c.challenge) {
            is ChallengeUi.Warmup -> addAll(listOf(ch.label, ch.text))
            is ChallengeUi.Active -> {
                addAll(listOf(ch.title, ch.statusLabel, ch.total, ch.caption, ch.phrase, ch.chartDescription, ch.legend, ch.description))
                ch.lines.forEach { addAll(listOf(it.label, it.value)) }
                ch.days.forEach { addAll(listOf(it.label, it.description)) }
            }
        }
        c.past.forEach { addAll(listOf(it.label, it.value, it.description)) }
        c.stamps.forEach { s -> addAll(listOfNotNull(s.name, s.kindLabel, s.date, s.progressText, s.hint, s.xp, s.status, s.description)) }
    }

    @Test
    fun `un pasaporte recién estrenado calienta y no tiene reto`() {
        val c = PassportMapper.map(PassportFakes.fresh(today))
        assertEquals(PassportCopy.WARMING_UP, c.flap.label)
        assertTrue(c.flap.warmingUp)
        assertNull(c.flap.reference)
        assertTrue(c.challenge is ChallengeUi.Warmup)
        assertEquals("0 DE 23", c.stampsCount)
        assertTrue(c.stamps.none { it.earned || it.latest })
        assertTrue(c.stamps.all { it.status == PassportCopy.STAMP_PENDING && it.date == null })
        assertEquals(PassportCopy.REFERENCE_NOTE, c.referenceNote)
        assertEquals("PAS", c.level.code)
        assertEquals("FRE", c.level.nextCode)
        assertEquals("FALTAN 150 MILLAS PARA FRECUENTE", plain(c.level.toNext))
        assertEquals("0 DÍAS A BORDO", plain(c.kicker))
        assertTrue(c.past.isEmpty())
    }

    @Test
    fun `con historia, Flap, nivel y reto salen formateados`() {
        val c = PassportMapper.map(PassportFakes.sample(today, mood = FlapMood.NORMAL, xp = 520, goal = 150))
        assertEquals("NORMAL", c.flap.label)
        assertEquals("5 DE 7 DÍAS BAJO TU OBJETIVO", plain(c.flap.record))
        assertEquals("TU OBJETIVO: 150 AL DÍA", plain(c.flap.reference!!))
        assertNull(c.referenceNote)
        assertEquals(TravelerLevel.PLATA, c.level.level)
        assertEquals("PLA", c.level.code)
        assertEquals("ORO", c.level.nextCode)
        assertEquals("520 MILLAS", plain(c.level.miles))
        assertEquals("FALTAN 380 MILLAS PARA ORO", plain(c.level.toNext))
        assertEquals(0.24f, c.level.fraction, 0.001f)
        assertEquals(PassportCopy.lineDays(true), c.level.breakdown.first().label)
        assertEquals(TicketLine(PassportCopy.LINE_STREAK, "4 DÍAS"), c.level.streak.copy(value = plain(c.level.streak.value)))

        val ch = c.challenge as ChallengeUi.Active
        assertEquals("ESTA SEMANA, UN 10 % MENOS", plain(ch.title))
        assertEquals(ChallengeStatus.EN_CURSO, ch.status)
        assertEquals(7, ch.days.size)
        assertEquals(2, ch.todayIndex) // Miércoles 4 de noviembre.
        assertEquals(listOf("L", "M", "X", "J", "V", "S", "D"), ch.days.map { it.label })
        assertNull(ch.days[3].count)
        assertEquals("405", ch.total)
        assertFalse(ch.over)
        assertTrue(ch.lines.any { it.label == PassportCopy.LINE_REMAINING })
        assertEquals(5, c.past.size)
        assertEquals("9 DE 23", c.stampsCount)
    }

    @Test
    fun `sin objetivo se habla de la media y las pistas lo aclaran`() {
        val c = PassportMapper.map(PassportFakes.sample(today, goal = null))
        assertEquals("5 DE 7 DÍAS BAJO TU MEDIA", plain(c.flap.record))
        assertEquals(PassportCopy.REFERENCE_NOTE, c.referenceNote)
        assertEquals(PassportCopy.lineDays(false), c.level.breakdown.first().label)
    }

    @Test
    fun `en el último nivel no hay destino`() {
        val c = PassportMapper.map(PassportFakes.sample(today, xp = 5_000))
        assertEquals(TravelerLevel.PLATINO, c.level.level)
        assertNull(c.level.nextCode)
        assertNull(c.level.nextName)
        assertEquals(PassportCopy.TOP_LEVEL, c.level.toNext)
        assertEquals(1f, c.level.fraction)
    }

    @Test
    fun `el cambio de estado solo se anima si de verdad cambia`() {
        val base = PassportFakes.sample(today, mood = FlapMood.EN_FORMA)
        val changed = PassportMapper.map(base.copy(flap = base.flap.copy(previousMood = FlapMood.CANSADO)))
        assertEquals(FlapMood.CANSADO, changed.flap.previousMood)
        val same = PassportMapper.map(base.copy(flap = base.flap.copy(previousMood = FlapMood.EN_FORMA)))
        assertNull(same.flap.previousMood)
        val back = PassportMapper.map(base.copy(flap = base.flap.copy(previousMood = FlapMood.ZOMBI)))
        assertTrue(back.flap.phrase in (0L..20L).map { PassportCopy.recoveredPhrase(it) })
    }

    @Test
    fun `sellos - orden, fecha, el más reciente en acento y los irónicos sin millas`() {
        val c = PassportMapper.map(PassportFakes.sample(today))
        assertEquals(StampId.entries, c.stamps.map { it.id })
        val earned = c.stamps.filter { it.earned }
        assertEquals(9, earned.size)
        assertTrue(earned.all { it.date != null && it.status.startsWith("CONSEGUIDO EL ") })
        assertEquals(listOf(StampId.TRES_PLATAFORMAS), c.stamps.filter { it.latest }.map { it.id })
        c.stamps.filter { it.ironic }.forEach { assertEquals("SIN MILLAS", it.xp) }
        val racha14 = c.stamps.first { it.id == StampId.RACHA_14 }
        assertEquals(0.5f, racha14.progress)
        assertEquals("50 % HECHO", plain(racha14.progressText!!))
        assertNull(c.stamps.first { it.id == StampId.RACHA_3 }.progress)
        c.stamps.forEach { assertTrue("${it.id}: ${it.tilt}", it.tilt in -5f..5f && it.tilt != 0f) }
        assertTrue(c.stamps.map { it.tilt }.toSet().size >= 5)
    }

    @Test
    fun `pasado el tope, la barra se llena y la frase no culpa`() {
        val base = PassportFakes.sample(today)
        val over = base.copy(challenge = base.challenge!!.copy(total = 2_000))
        val ch = PassportMapper.map(over).challenge as ChallengeUi.Active
        assertTrue(ch.over)
        assertEquals(1f, ch.fraction)
        assertNotNull(ch.phrase)
        val done = base.copy(challenge = base.challenge!!.copy(status = ChallengeStatus.CUMPLIDO))
        val closed = PassportMapper.map(done).challenge as ChallengeUi.Active
        assertEquals("CUMPLIDO", closed.statusLabel)
        assertTrue(closed.lines.none { it.label == PassportCopy.LINE_REMAINING })
    }

    @Test
    fun `todos los textos de todos los estados cumplen las reglas`() {
        val states = buildList {
            add(PassportFakes.fresh(today))
            for (mood in FlapMood.entries) for (goal in listOf(150, null)) for (xp in listOf(0, 149, 150, 520, 1_799, 1_800, 99_999)) {
                add(PassportFakes.sample(today.plusDays(xp.toLong() % 7), mood = mood, xp = xp, goal = goal, withPending = true))
            }
        }
        states.forEach { s ->
            texts(PassportMapper.map(s)).forEach { t ->
                val v = CopyRules.violations(t)
                assertTrue("«$t»: $v", v.isEmpty())
            }
        }
    }

    @Test
    fun `los retos pasados son una lista breve, el más reciente primero`() {
        val base = PassportFakes.sample(today)
        val many = (1..20).map { w -> base.pastChallenges.first().copy(weekStart = today.minusWeeks(w.toLong())) }
        val c = PassportMapper.map(base.copy(pastChallenges = many))
        assertEquals(PassportMapper.MAX_PAST, c.past.size)
        assertEquals(today.minusWeeks(1).toString(), c.past.first().key)
    }

    @Test
    fun `el error usa el texto de estado de la app`() {
        val e = PassportMapper.error(today) as PassportUiState.Error
        assertTrue(CopyRules.violations(e.message).isEmpty())
    }
}
