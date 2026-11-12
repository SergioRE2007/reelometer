package com.reelometro.app.ui.tour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourLogicTest {

    @Test
    fun `con Flap encendido son seis pasos en orden`() {
        assertEquals(
            listOf(
                TourStep.COUNTER,
                TourStep.COMPARISON,
                TourStep.STATS_BARS,
                TourStep.WRAPPED,
                TourStep.PASSPORT,
                TourStep.SETTINGS,
            ),
            TourLogic.steps(passportEnabled = true),
        )
    }

    @Test
    fun `con Flap apagado se salta el paso de Flap`() {
        val steps = TourLogic.steps(passportEnabled = false)
        assertEquals(5, steps.size)
        assertFalse(TourStep.PASSPORT in steps)
        assertTrue(steps.none { it.tab == TourTab.PASSPORT })
    }

    @Test
    fun `cada paso pide su pestana`() {
        assertEquals(TourTab.TODAY, TourStep.COUNTER.tab)
        assertEquals(TourTab.TODAY, TourStep.COMPARISON.tab)
        assertEquals(TourTab.STATS, TourStep.STATS_BARS.tab)
        assertEquals(TourTab.WRAPPED, TourStep.WRAPPED.tab)
        assertEquals(TourTab.PASSPORT, TourStep.PASSPORT.tab)
        assertEquals(TourTab.TODAY, TourStep.SETTINGS.tab)
        assertEquals(listOf(0, 1, 2, 3), TourTab.entries.map { it.index })
    }

    @Test
    fun `siguiente avanza y en el ultimo paso termina sin saltar`() {
        var state = TourLogic.start(passportEnabled = true)
        assertEquals(1, state.number)
        assertEquals(6, state.total)
        assertFalse(state.isLast)
        repeat(5) { state = TourLogic.next(state) }
        assertEquals(TourStep.SETTINGS, state.step)
        assertEquals(6, state.number)
        assertTrue(state.isLast)

        val done = TourLogic.next(state)
        assertTrue(done.finished)
        assertFalse(done.skipped)
        assertNull(done.step)
        // Terminado, ya no se mueve.
        assertEquals(done, TourLogic.next(done))
        assertEquals(done, TourLogic.skip(done))
    }

    @Test
    fun `saltar termina en cualquier paso y queda como saltado`() {
        val first = TourLogic.start(passportEnabled = false)
        val skipped = TourLogic.skip(first)
        assertTrue(skipped.finished)
        assertTrue(skipped.skipped)
        assertNull(skipped.step)

        val middle = TourLogic.next(TourLogic.next(first))
        val skippedMiddle = TourLogic.skip(middle)
        assertTrue(skippedMiddle.finished && skippedMiddle.skipped)
    }

    @Test
    fun `sin Flap el ultimo paso es ajustes y son cinco`() {
        var state = TourLogic.start(passportEnabled = false)
        val seen = mutableListOf<TourStep>()
        while (!state.finished) {
            seen += state.step!!
            state = TourLogic.next(state)
        }
        assertEquals(5, seen.size)
        assertEquals(TourStep.SETTINGS, seen.last())
        assertFalse(state.skipped)
    }

    @Test
    fun `la pestana que hay que abrir es la del paso si no esta ya a la vista`() {
        val start = TourLogic.start(passportEnabled = true)
        assertNull(TourLogic.tabToShow(start, currentTab = 0))
        assertEquals(TourTab.TODAY, TourLogic.tabToShow(start, currentTab = 2))
        assertEquals(TourTab.TODAY, TourLogic.tabToShow(start, currentTab = -1))

        val stats = TourLogic.resume(passportEnabled = true, stepName = "STATS_BARS")
        assertEquals(TourTab.STATS, TourLogic.tabToShow(stats, currentTab = 0))
        assertNull(TourLogic.tabToShow(stats, currentTab = 1))

        assertNull(TourLogic.tabToShow(TourLogic.skip(stats), currentTab = 0))
    }

    @Test
    fun `retomar sigue en el paso guardado y si ya no existe en el siguiente`() {
        assertEquals(TourStep.COUNTER, TourLogic.resume(true, null).step)
        assertEquals(TourStep.COUNTER, TourLogic.resume(true, "NO_EXISTE").step)
        assertEquals(TourStep.WRAPPED, TourLogic.resume(true, "WRAPPED").step)
        assertEquals(4, TourLogic.resume(true, "WRAPPED").number)

        // Apagan Flap justo en su paso: se sigue en Ajustes, que pasa a ser el 5 de 5.
        val resumed = TourLogic.resume(false, "PASSPORT")
        assertEquals(TourStep.SETTINGS, resumed.step)
        assertEquals(5, resumed.number)
        assertEquals(5, resumed.total)
        assertTrue(resumed.isLast)
    }

    @Test
    fun `el texto va debajo del hueco si este esta arriba y encima si esta abajo`() {
        assertTrue(TourLogic.textBelow(100f, 300f, 2_000f))
        assertFalse(TourLogic.textBelow(1_500f, 1_800f, 2_000f))
        assertTrue("sin hueco", TourLogic.textBelow(null, null, 2_000f))
        assertTrue("sin medir", TourLogic.textBelow(10f, 20f, 0f))
    }

    // ------------------------------------------------ descubrimiento progresivo

    @Test
    fun `con solo Hoy a la vista son tres pasos y avisa de que habrá más`() {
        assertEquals(
            listOf(TourStep.COUNTER, TourStep.COMPARISON, TourStep.SETTINGS),
            TourLogic.steps(passportEnabled = true, visibleTabs = 1),
        )
        val state = TourLogic.start(passportEnabled = true, visibleTabs = 1)
        assertEquals(3, state.total)
        assertTrue(state.moreToCome)
    }

    @Test
    fun `sin la pestaña de Flap no hay su paso`() {
        assertFalse(TourStep.PASSPORT in TourLogic.steps(passportEnabled = true, visibleTabs = 3))
        assertFalse(TourStep.PASSPORT in TourLogic.steps(passportEnabled = false, visibleTabs = 4))
        assertTrue(TourStep.WRAPPED in TourLogic.steps(passportEnabled = true, visibleTabs = 3))
    }

    @Test
    fun `con todo a la vista no avisa de nada y los pasos son los de siempre`() {
        assertFalse(TourLogic.start(passportEnabled = true, visibleTabs = 4).moreToCome)
        assertFalse(TourLogic.start(passportEnabled = false, visibleTabs = 3).moreToCome)
        assertTrue(TourLogic.start(passportEnabled = true, visibleTabs = 3).moreToCome)
        assertFalse(TourLogic.start(passportEnabled = true).moreToCome)
        assertEquals(TourLogic.steps(passportEnabled = true, visibleTabs = 4), TourLogic.steps(passportEnabled = true))
        assertEquals(TourLogic.steps(passportEnabled = false, visibleTabs = 4), TourLogic.steps(passportEnabled = false))
    }

    @Test
    fun `si el paso guardado es de una pestaña oculta sigue en Ajustes`() {
        val state = TourLogic.resume(passportEnabled = true, stepName = "STATS_BARS", visibleTabs = 1)
        assertEquals(TourStep.SETTINGS, state.step)
        assertTrue(state.isLast)
    }
}
