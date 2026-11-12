package com.reelometro.app.detection

import org.junit.Assert.assertEquals
import org.junit.Test

/** Veredicto de la prueba de un minuto. */
class ServiceCheckRulesTest {

    private fun input(
        enabled: Boolean = true,
        connected: Boolean = true,
        restarted: Boolean = false,
        events: Long = 40,
        videos: Long = 0,
        platforms: Boolean = true,
    ) = CheckInput(enabled, connected, restarted, events, videos, platforms)

    private fun verdict(i: CheckInput) = ServiceCheckRules.verdict(i)

    private fun failed(r: CheckFailure) = CheckVerdict.Failed(r)

    @Test
    fun `apagado gana a todo`() {
        assertEquals(failed(CheckFailure.OFF), verdict(input(enabled = false, videos = 5)))
        assertEquals(failed(CheckFailure.OFF), verdict(input(enabled = false, restarted = true, connected = false)))
    }

    @Test
    fun `un vídeo basta para superarla`() {
        assertEquals(CheckVerdict.Passed(1, false), verdict(input(videos = 1)))
        assertEquals(CheckVerdict.Passed(3, false), verdict(input(videos = 3, connected = false, events = 0)))
    }

    @Test
    fun `con vídeos y el proceso reiniciado, superada pero avisando`() {
        assertEquals(CheckVerdict.Passed(2, true), verdict(input(videos = 2, restarted = true)))
    }

    @Test
    fun `sin vídeos, la tabla de prioridades`() {
        assertEquals(failed(CheckFailure.KILLED), verdict(input(restarted = true, connected = false, events = 0)))
        assertEquals(failed(CheckFailure.NOT_CONNECTED), verdict(input(connected = false, events = 0)))
        assertEquals(failed(CheckFailure.PLATFORMS_OFF), verdict(input(platforms = false, events = 0)))
        assertEquals(failed(CheckFailure.PLATFORMS_OFF), verdict(input(platforms = false, events = 12)))
        assertEquals(failed(CheckFailure.NO_EVENTS), verdict(input(events = 0)))
        assertEquals(failed(CheckFailure.NO_VIDEOS), verdict(input(events = 12)))
    }
}
