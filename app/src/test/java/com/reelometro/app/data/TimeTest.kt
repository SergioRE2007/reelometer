package com.reelometro.app.data

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Relojes y avisos de tiempo (JVM puro). */
class TimeTest {

    private val slack = ClockTicker.SLACK_MS

    @Test
    fun `día y minuto locales dependen de la zona`() {
        val instant = LocalDateTime.of(2026, 11, 3, 23, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(LocalStamp(LocalDate.of(2026, 11, 4).toEpochDay(), 30), LocalStamp.of(instant, MADRID))
        assertEquals(
            LocalStamp(LocalDate.of(2026, 11, 3).toEpochDay(), 18 * 60 + 30),
            LocalStamp.of(instant, ZoneId.of("America/New_York")),
        )
        assertEquals(LocalStamp(LocalDate.of(2026, 11, 3).toEpochDay(), 1439), LocalStamp.of(at(LocalDate.of(2026, 11, 3), 23, 59, second = 59), MADRID))
    }

    @Test
    fun `el próximo aviso es la medianoche o el siguiente múltiplo de minutos`() {
        val day = LocalDate.of(2026, 11, 3)
        assertEquals(30_000 + slack, ClockTicker.millisUntilNextTick(at(day, 23, 59, second = 30), MADRID, null))
        assertEquals(30_000 + slack, ClockTicker.millisUntilNextTick(at(day, 23, 59, second = 30), MADRID, 5))
        assertEquals(170_000 + slack, ClockTicker.millisUntilNextTick(at(day, 14, 2, second = 10), MADRID, 5))
        assertEquals(
            (9 * 3_600 + 57 * 60 + 50) * 1_000L + slack,
            ClockTicker.millisUntilNextTick(at(day, 14, 2, second = 10), MADRID, null),
        )
        // Justo en la frontera: espera al siguiente.
        assertEquals(300_000 + slack, ClockTicker.millisUntilNextTick(at(day, 14, 5), MADRID, 5))
        // Zona con media hora de desfase (India, +5:30): también alinea con el reloj local.
        val kolkata = ZoneId.of("Asia/Kolkata")
        assertEquals(180_000 + slack, ClockTicker.millisUntilNextTick(at(day, 10, 2, kolkata), kolkata, 5))
    }

    @Test
    fun `los días con cambio de hora tienen su propia medianoche`() {
        // 29 mar 2026 en Madrid dura 23 h (a las 2:00 son las 3:00).
        val beforeSpring = at(LocalDate.of(2026, 3, 28), 23, 0)
        assertEquals(3_600_000 + slack, ClockTicker.millisUntilNextTick(beforeSpring, MADRID, null))
        val springDay = at(LocalDate.of(2026, 3, 29), 1, 0)
        assertEquals(22 * 3_600_000L + slack, ClockTicker.millisUntilNextTick(springDay, MADRID, null))
        // 25 oct 2026 dura 25 h.
        val autumnDay = at(LocalDate.of(2026, 10, 25), 1, 0)
        assertEquals(24 * 3_600_000L + slack, ClockTicker.millisUntilNextTick(autumnDay, MADRID, null))
    }

    @Test
    fun `el ticker avisa al suscribirse, cada 5 minutos y al cambiar de día`() = runTest {
        val start = at(LocalDate.of(2026, 11, 3), 23, 52)
        val clock = object : Clock() {
            override fun getZone(): ZoneId = MADRID
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = Instant.ofEpochMilli(start + testScheduler.currentTime)
        }
        val stamps = mutableListOf<LocalDateTime>()
        ClockTicker(clock).ticks(5).take(4).collect {
            stamps += LocalDateTime.ofInstant(clock.instant(), MADRID)
        }
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 11, 3, 23, 52),
                LocalDateTime.of(2026, 11, 3, 23, 55, 0, 250_000_000),
                LocalDateTime.of(2026, 11, 4, 0, 0, 0, 250_000_000),
                LocalDateTime.of(2026, 11, 4, 0, 5, 0, 250_000_000),
            ),
            stamps,
        )
    }
}
