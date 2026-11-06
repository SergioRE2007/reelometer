package com.reelometro.app.ui.wrapped

import com.reelometro.app.copy.CopyPeriod
import com.reelometro.app.copy.CopyRules
import com.reelometro.app.copy.Variants
import com.reelometro.app.copy.WrappedCopy
import com.reelometro.app.stats.PeriodRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StorySlidesTest {

    private val fullOrder = listOf("cover", "total", "time", "equivalences", "creator", "category", "worst-day", "rhythm", "closing")

    @Test
    fun `con todos los datos salen las nueve diapositivas en el orden del diseño`() {
        val slides = StorySlides.build(periodStats())
        assertEquals(fullOrder, slides.map { it.key })
    }

    @Test
    fun `un periodo sin vídeos es una única diapositiva de vacío`() {
        val slides = StorySlides.build(periodStats(perDay = { 0 }))
        assertEquals(listOf("empty"), slides.map { it.key })
        val empty = slides.single() as Slide.Empty
        assertTrue(empty.title.isNotBlank())
    }

    @Test
    fun `sin creador la diapositiva sigue pero sin usuario`() {
        val creator = StorySlides.build(periodStats(creator = null)).filterIsInstance<Slide.Creator>().single()
        assertNull(creator.handle)
        assertTrue(creator.details.isEmpty())
        assertTrue(creator.quote.isNotBlank())
    }

    @Test
    fun `sin categorías ni plataformas ni hora punta se omiten sus diapositivas`() {
        val slides = StorySlides.build(periodStats(categories = false, platforms = false, peakHour = null))
        assertEquals(fullOrder - "category" - "rhythm", slides.map { it.key })
    }

    @Test
    fun `con un solo día registrado no hay peor día`() {
        // Esta semana empezó hoy mismo para la app: un único día con registro.
        val stats = periodStats(ref = PeriodRef.THIS_WEEK, firstDay = TODAY)
        assertFalse(StorySlides.build(stats).any { it is Slide.WorstDay })
    }

    @Test
    fun `el peor día resalta la barra más alta`() {
        val worst = StorySlides.build(periodStats()).filterIsInstance<Slide.WorstDay>().single()
        val max = worst.bars.max()
        assertEquals(max, worst.bars[worst.highlight])
        assertEquals(31, worst.bars.size) // octubre entero registrado
    }

    @Test
    fun `el cierre de un mes cerrado no es provisional y el de uno en curso sí`() {
        val past = periodStats()
        val closing = StorySlides.build(past).last() as Slide.Closing
        val seed = Variants.daySeed(LocalDate.of(2026, 10, 1))
        assertEquals(WrappedCopy.closing(CopyPeriod.MONTH, LocalDate.of(2026, 10, 1), true, seed), closing.quote)

        val current = periodStats(ref = PeriodRef.THIS_MONTH)
        val open = StorySlides.build(current).last() as Slide.Closing
        val seedNow = Variants.daySeed(LocalDate.of(2026, 11, 1))
        assertEquals(WrappedCopy.closing(CopyPeriod.MONTH, LocalDate.of(2026, 11, 1), false, seedNow), open.quote)
    }

    @Test
    fun `las diapositivas son deterministas para el mismo periodo`() {
        assertEquals(StorySlides.build(periodStats()), StorySlides.build(periodStats()))
    }

    @Test
    fun `las equivalencias llevan su base de cálculo`() {
        val eq = StorySlides.build(periodStats()).filterIsInstance<Slide.Equivalences>().single()
        assertEquals(3, eq.lines.size)
        eq.lines.forEach {
            assertTrue(it.basis, it.basis.startsWith("Base"))
            assertTrue(it.text.endsWith("."))
        }
        assertNull(eq.empty)
    }

    @Test
    fun `con muy poco tiempo no hay equivalencias que enseñar`() {
        // 3 vídeos de 15 s: 45 s, menos que la equivalencia más pequeña (un vaso de leche, 1 min).
        val stats = periodStats(perDay = { if (it.dayOfMonth == 5) 3 else 0 }, msPerVideo = 15_000)
        val eq = StorySlides.build(stats).filterIsInstance<Slide.Equivalences>().single()
        assertTrue(eq.lines.isEmpty())
        assertEquals(WrappedCopy.EQUIVALENCES_EMPTY, eq.empty)
    }

    @Test
    fun `los textos de todas las diapositivas cumplen las reglas de estilo`() {
        val texts = StorySlides.build(periodStats()).flatMap { slide ->
            when (slide) {
                is Slide.Cover -> listOf(slide.title, slide.hint)
                is Slide.Total -> listOf(slide.quote)
                is Slide.Time -> listOf(slide.value, slide.quote)
                is Slide.Equivalences -> slide.lines.map { it.text }
                is Slide.Creator -> listOf(slide.quote)
                is Slide.Category -> listOf(slide.name, slide.quote)
                is Slide.WorstDay -> listOf(slide.day, slide.weekday, slide.quote)
                is Slide.Rhythm -> listOfNotNull(slide.peakQuote, slide.platformsText)
                is Slide.Closing -> listOf(slide.quote, slide.imageDescription)
                is Slide.Empty -> listOf(slide.title)
            }
        }
        texts.forEach { assertEquals(it, emptyList<String>(), CopyRules.violations(it)) }
    }
}
