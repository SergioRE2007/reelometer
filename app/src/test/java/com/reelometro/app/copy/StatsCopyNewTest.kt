package com.reelometro.app.copy

import com.reelometro.app.copy.CopyRules.plain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Textos de Estadísticas: comparativa semanal y accesibilidad. */
class StatsCopyNewTest {

    private val today = LocalDate.of(2026, 11, 3)

    private fun assertClean(text: String) = assertTrue("«$text»: ${CopyRules.violations(text)}", CopyRules.violations(text).isEmpty())

    @Test
    fun `las etiquetas cumplen las reglas y caben en una línea`() {
        val labels = listOf(
            StatsCopy.SECTION_WEEK_VS, StatsCopy.GHOST_LEGEND, StatsCopy.LINE_THIS_WEEK_SO_FAR, StatsCopy.LINE_THAT_WEEK,
            StatsCopy.LINE_PREVIOUS_SAME_STRETCH, StatsCopy.LINE_PREVIOUS, StatsCopy.LINE_PREVIOUS_WHOLE,
            StatsCopy.LINE_DIFFERENCE, StatsCopy.LINE_DIFFERENCE_PER_DAY,
        )
        labels.forEach(::assertClean)
        // El título de sección no se recorta al 130 % (SectionHeader es de una línea).
        assertTrue(StatsCopy.SECTION_WEEK_VS.length <= 26)
        assertEquals(StatsCopy.SECTION_WEEK_VS, StatsCopy.SECTION_WEEK_VS.uppercase())
    }

    @Test
    fun `tooltip de la comparativa - el día y su fantasma`() {
        assertEquals("125 vídeos\nmar 3 nov · la anterior, 140", plain(StatsCopy.ghostTooltip(today, 125, 140, today)))
        assertEquals("1 vídeo\nlun 2 nov · la anterior, sin datos", plain(StatsCopy.ghostTooltip(today.minusDays(1), 1, null, today)))
        assertEquals("por llegar\nmié 4 nov · la anterior, 1.204", plain(StatsCopy.ghostTooltip(today.plusDays(1), null, 1_204, today)))
        assertEquals("sin datos\nlun 2 nov · la anterior, 0", plain(StatsCopy.ghostTooltip(today.minusDays(1), null, 0, today)))
        listOf(
            StatsCopy.ghostTooltip(today, 0, 0, today),
            StatsCopy.ghostTooltip(today, 12_408, 1, today),
        ).forEach { tooltip -> tooltip.lines().forEach(::assertClean) }
    }

    @Test
    fun `resumen hablado de la comparativa`() {
        assertEquals(
            "Esta semana llevas 205 vídeos; la anterior, a estas alturas, 230. Un 11 % menos.",
            plain(StatsCopy.weekComparisonDescription(205, 230, -0.1087, inProgress = true, perDay = false)),
        )
        assertEquals(
            "Esa semana, 1.204 vídeos; la anterior, 1.000. Un 20 % más por día.",
            plain(StatsCopy.weekComparisonDescription(1_204, 1_000, 0.2, inProgress = false, perDay = true)),
        )
        assertEquals(
            "Esta semana llevas 1 vídeo; de la anterior no hay datos de esos días.",
            plain(StatsCopy.weekComparisonDescription(1, null, null, inProgress = true, perDay = false)),
        )
        assertTrue(plain(StatsCopy.weekComparisonDescription(100, 100, 0.001, inProgress = true, perDay = false)).endsWith("Prácticamente igual."))
        listOf(
            StatsCopy.weekComparisonDescription(0, 0, null, inProgress = true, perDay = false),
            StatsCopy.weekComparisonDescription(12_408, 10_001, 0.24, inProgress = false, perDay = false),
            StatsCopy.weekComparisonDescription(3, 7, -0.004, inProgress = true, perDay = true),
        ).forEach(::assertClean)
    }

    @Test
    fun `valores de los totales y la diferencia con Format`() {
        assertEquals("1.204", StatsCopy.weekTotalValue(1_204))
        assertEquals("+12 %", plain(StatsCopy.weekDifferenceValue(0.12)))
        assertEquals("−8 %", plain(StatsCopy.weekDifferenceValue(-0.08)))
        assertEquals("±0 %", plain(StatsCopy.weekDifferenceValue(0.001)))
    }

    @Test
    fun `accesibilidad - abrir un día y la línea del resumen dicha`() {
        assertEquals("Abrir el martes 3 de noviembre", StatsCopy.openDayAction(today))
        assertEquals(
            "1.204 vídeos, 9 horas y 12 minutos, 172 al día, 27 segundos por vídeo.",
            plain(StatsCopy.summaryDescription(1_204, (9 * 60 + 12) * 60_000L, 172.0, 27_000)),
        )
        assertEquals("0 vídeos.", plain(StatsCopy.summaryDescription(0, 0, 0.0, 0)))
        listOf(
            StatsCopy.openDayAction(today),
            StatsCopy.summaryDescription(1, 1_000, 1.0, 1_000),
            StatsCopy.summaryDescription(12_408, 100 * 3_600_000L, 3.2, 61_000),
        ).forEach(::assertClean)
    }
}
