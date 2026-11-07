package com.reelometro.app.ui.support

import com.reelometro.app.AppLinks
import com.reelometro.app.copy.SupportCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportPromptTest {

    private val today = 20_000L

    private fun show(
        state: SupportPromptState,
        day: Long = today,
        onboardingDone: Boolean = true,
        tourDone: Boolean = true,
        hasLink: Boolean = true,
    ) = SupportPrompt.shouldShow(state, day, onboardingDone, tourDone, hasLink)

    private val ready = SupportPromptState(usageDays = 3, lastUsageDay = today)

    @Test
    fun `sale la primera vez con tres dias distintos de uso`() {
        assertTrue(show(ready))
        assertTrue(show(ready.copy(usageDays = 10)))
    }

    @Test
    fun `no sale con menos de tres dias de uso`() {
        assertFalse(show(SupportPromptState()))
        assertFalse(show(ready.copy(usageDays = 1)))
        assertFalse(show(ready.copy(usageDays = 2)))
    }

    @Test
    fun `no sale sin el onboarding o sin el recorrido`() {
        assertFalse(show(ready, onboardingDone = false))
        assertFalse(show(ready, tourDone = false))
        assertFalse(show(ready, onboardingDone = false, tourDone = false))
    }

    @Test
    fun `no sale sin enlace`() {
        assertFalse(show(ready, hasLink = false))
    }

    @Test
    fun `nunca mas tras abrir el github o no volver a mostrar`() {
        val finished = SupportPrompt.finish(ready)
        assertTrue(finished.finished)
        assertFalse(show(finished))
        assertFalse(show(finished, day = today + 365))
    }

    @Test
    fun `como mucho una vez al dia`() {
        val shown = SupportPrompt.markShown(ready, today)
        assertEquals(1, shown.timesShown)
        assertEquals(today, shown.lastShownDay)
        assertFalse(show(shown))
    }

    @Test
    fun `tras ahora no vuelve una sola vez a los treinta dias`() {
        val once = SupportPrompt.markShown(ready, today)
        assertFalse(show(once, day = today + 1))
        assertFalse(show(once, day = today + SupportPrompt.RETRY_AFTER_DAYS - 1))
        assertTrue(show(once, day = today + SupportPrompt.RETRY_AFTER_DAYS))
        assertTrue(show(once, day = today + 90))

        val twice = SupportPrompt.markShown(once, today + SupportPrompt.RETRY_AFTER_DAYS)
        assertEquals(2, twice.timesShown)
        assertFalse(show(twice, day = today + 365))
        assertFalse(show(twice, day = today + 10_000))
    }

    @Test
    fun `la segunda vez sin fecha de la primera no sale`() {
        assertFalse(show(ready.copy(timesShown = 1, lastShownDay = null), day = today + 100))
    }

    @Test
    fun `con el reloj hacia atras no vuelve antes de tiempo`() {
        val once = SupportPrompt.markShown(ready, today)
        assertFalse(show(once, day = today - 40))
    }

    @Test
    fun `un dia de uso cuenta una sola vez`() {
        val first = SupportPrompt.recordUsage(SupportPromptState(), today)
        assertEquals(1, first.usageDays)
        assertEquals(today, first.lastUsageDay)
        assertSame(first, SupportPrompt.recordUsage(first, today))

        val second = SupportPrompt.recordUsage(first, today + 1)
        val third = SupportPrompt.recordUsage(SupportPrompt.recordUsage(second, today + 1), today + 5)
        assertEquals(3, third.usageDays)
        assertEquals(today + 5, third.lastUsageDay)
        assertTrue(show(third, day = today + 5))
    }

    @Test
    fun `apuntar el uso no toca lo demas`() {
        val state = SupportPromptState(usageDays = 4, lastUsageDay = today, timesShown = 1, lastShownDay = today, finished = true)
        val next = SupportPrompt.recordUsage(state, today + 1)
        assertEquals(state.copy(usageDays = 5, lastUsageDay = today + 1), next)
    }

    @Test
    fun `el gancho de qa deja el aviso listo para salir hoy`() {
        val qa = SupportPrompt.readyForQa(today)
        assertTrue(show(qa))
        // Abrir la app ese mismo día no suma otro.
        assertSame(qa, SupportPrompt.recordUsage(qa, today))
    }

    @Test
    fun `los textos no tienen emojis y no prometen internet`() {
        val all = listOf(
            SupportCopy.KICKER, SupportCopy.TITLE, SupportCopy.TEXT, SupportCopy.NOTE, SupportCopy.OPEN,
            SupportCopy.LATER, SupportCopy.NEVER, SupportCopy.openFailed(AppLinks.PROJECT_URL), SupportCopy.PANE_TITLE,
            SupportCopy.OPEN_DESCRIPTION, SupportCopy.NEVER_DESCRIPTION,
        )
        all.forEach { text ->
            assertTrue(text.isNotBlank())
            assertTrue("emoji en «$text»", text.codePoints().noneMatch { it >= 0x1F000 || it in 0x2600..0x27BF })
        }
        assertEquals("VER SU GITHUB", SupportCopy.OPEN)
        assertEquals("AHORA NO", SupportCopy.LATER)
    }

    @Test
    fun `con la fecha hacia atras el mismo dia no cuenta dos veces`() {
        // Un viaje al oeste (o el reloj cambiado): 100 → 99 → 100 son dos días reales, no tres.
        var s = SupportPrompt.recordUsage(SupportPromptState(), 100)
        s = SupportPrompt.recordUsage(s, 99)
        s = SupportPrompt.recordUsage(s, 100)
        assertEquals(1, s.usageDays)
        assertEquals(2, SupportPrompt.recordUsage(s, 101).usageDays)
    }

    @Test
    fun `el error dice donde buscarlo a partir del enlace`() {
        assertTrue(SupportCopy.openFailed("https://github.com/SergioRE2007/reelometer").endsWith("GitHub: SergioRE2007/reelometer."))
        assertTrue(SupportCopy.openFailed("https://github.com/otro/repo/").endsWith("GitHub: otro/repo."))
    }
}
