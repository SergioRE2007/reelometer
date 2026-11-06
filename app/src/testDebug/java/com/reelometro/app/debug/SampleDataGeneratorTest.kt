package com.reelometro.app.debug

import com.reelometro.app.data.LocalStamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** El generador de datos de ejemplo (solo debug) produce un historial creíble y reproducible. */
class SampleDataGeneratorTest {

    private val zone = ZoneId.of("Europe/Madrid")
    private val today = LocalDate.of(2026, 11, 3)
    private val now = LocalDateTime.of(2026, 11, 3, 21, 15).atZone(zone).toInstant()
    /** Lo mismo que verá QA con `SEED` sin semilla. */
    private val data = SampleDataGenerator(seed = DebugTools.DEFAULT_SEED, zone = zone).generate(weeks = 8, now = now)
    private val total = data.views.size.toDouble()

    @Test
    fun `es reproducible con la misma semilla y cambia con otra`() {
        val again = SampleDataGenerator(seed = DebugTools.DEFAULT_SEED, zone = zone).generate(8, now)
        assertEquals(data.views, again.views)
        assertEquals(data.shares, again.shares)
        assertNotEquals(data.views, SampleDataGenerator(seed = 42, zone = zone).generate(8, now).views)
    }

    @Test
    fun `otras semillas y duraciones también son verosímiles`() {
        for (seed in listOf(1L, 42L, 7_777L)) {
            val other = SampleDataGenerator(seed, zone).generate(weeks = 2, now = now)
            val n = other.views.size
            assertTrue("semilla $seed: $n vídeos en 2 semanas", n in 1_500..5_000)
            assertTrue(other.views.all { it.startedAt + it.durationMs <= now.toEpochMilli() })
        }
        val oneWeek = SampleDataGenerator(DebugTools.DEFAULT_SEED, zone).generate(weeks = 1, now = now)
        assertEquals(today.minusDays(6).toEpochDay(), oneWeek.views.minOf { it.day })
    }

    @Test
    fun `cubre las semanas pedidas hasta ahora mismo y nada en el futuro`() {
        val nowMs = now.toEpochMilli()
        assertTrue(data.views.all { it.startedAt + it.durationMs <= nowMs })
        assertTrue(data.shares.all { it.at <= nowMs })
        assertEquals(today.minusDays(55).toEpochDay(), data.views.minOf { it.day })
        val todayViews = data.views.filter { it.day == today.toEpochDay() }
        assertTrue("hoy debería ir a medias", todayViews.isNotEmpty())
        assertTrue(todayViews.all { it.minuteOfDay <= 21 * 60 + 15 })
        assertTrue("volumen: ${data.views.size}", data.views.size in 6_000..20_000)
    }

    @Test
    fun `ids únicos y día y minuto coherentes con la zona`() {
        assertEquals(data.views.size, data.views.map { it.id }.toSet().size)
        data.views.forEach { assertEquals(LocalStamp.of(it.startedAt, zone), LocalStamp(it.day, it.minuteOfDay)) }
        data.shares.forEach { assertEquals(LocalStamp.of(it.at, zone), LocalStamp(it.day, it.minuteOfDay)) }
        assertEquals(data.views.sortedBy { it.startedAt }, data.views)
        // Nunca dos vídeos a la vez.
        data.views.zipWithNext().forEach { (a, b) ->
            assertTrue("solapan: $a / $b", a.startedAt + a.durationMs <= b.startedAt)
        }
    }

    @Test
    fun `plataformas con pesos parecidos a 50, 30 y 20 por ciento`() {
        val share = data.views.groupingBy { it.platform }.eachCount().mapValues { it.value / total }
        assertEquals(0.5, share.getValue("TIKTOK"), 0.08)
        assertEquals(0.3, share.getValue("INSTAGRAM"), 0.08)
        assertEquals(0.2, share.getValue("YOUTUBE"), 0.08)
    }

    @Test
    fun `duraciones log-normales de unos segundos`() {
        val seconds = data.views.map { it.durationMs / 1000.0 }.sorted()
        val median = seconds[seconds.size / 2]
        assertTrue("mediana $median", median in 10.0..20.0)
        assertTrue(seconds.first() >= 2.0)
        assertTrue(seconds.count { it in 4.0..60.0 } / total > 0.85)
    }

    @Test
    fun `creadores inventados con reparto tipo Zipf, cola larga y sin clasificar realista`() {
        val named = SampleDataGenerator.NAMED_CREATORS.toSet()
        assertEquals(40, named.size)
        val withCreator = data.views.filter { it.creator != null }
        assertEquals(0.75, withCreator.size / total, 0.05)
        assertEquals(0.35, data.views.count { it.creator in named } / total, 0.05)

        val byCreator = withCreator.groupingBy { it.creator!! }.eachCount()
        assertTrue("faltan recurrentes", byCreator.keys.containsAll(named))
        val top = byCreator.entries.sortedByDescending { it.value }
        assertTrue("el top 10 son recurrentes: ${top.take(10)}", top.take(10).all { it.key in named })
        val namedCounts = top.filter { it.key in named }.map { it.value }
        assertTrue("el primero debería destacar: $namedCounts", namedCounts[0] > 3 * namedCounts[namedCounts.size / 2])
        val topShare = namedCounts[0] / total
        assertTrue("el nº 1 no debería acaparar: $topShare", topShare in 0.02..0.07)

        val unclassified = data.views.count { it.category == "OTROS" } / total
        assertTrue("sin clasificar: $unclassified", unclassified in 0.2..0.4)
        val byCategory = data.views.groupingBy { it.category }.eachCount()
        assertTrue(byCategory.size >= 15)
        assertTrue("ninguna categoría real acapara: $byCategory", byCategory.filterKeys { it != "OTROS" }.values.max() / total < 0.15)
    }

    @Test
    fun `alrededor de un 2 por ciento compartidos y casi todos con su vídeo`() {
        assertEquals(0.02, data.shares.size / total, 0.008)
        val ids = data.views.map { it.id }.toSet()
        val linked = data.shares.mapNotNull { it.reelId }
        assertTrue(linked.all { it in ids })
        assertTrue(linked.size > data.shares.size * 0.75)
        assertTrue(linked.size < data.shares.size)
    }

    @Test
    fun `noches fuertes, fines de semana más cargados, un día pico y tendencia`() {
        val byHour = data.views.groupingBy { it.minuteOfDay / 60 }.eachCount()
        val peakHour = byHour.maxBy { it.value }.key
        assertTrue("hora punta $peakHour", peakHour in 21..23)
        assertTrue("también hay miradas sueltas de día: $byHour", (9..20).all { (byHour[it] ?: 0) > 0 })

        val perDay = data.views.filter { it.day < today.toEpochDay() }.groupingBy { it.day }.eachCount()
        val counts = perDay.values.sorted()
        val median = counts[counts.size / 2]
        assertTrue("pico ${counts.last()} frente a mediana $median", counts.last() >= 1.8 * median)

        fun isWeekend(day: Long) = LocalDate.ofEpochDay(day).dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val weekend = perDay.filterKeys(::isWeekend).values.average()
        val weekdays = perDay.filterKeys { !isWeekend(it) }.values.average()
        assertTrue("fin de semana $weekend, entre semana $weekdays", weekend > weekdays * 1.15)

        val days = perDay.toSortedMap().values.toList()
        assertTrue("tendencia", days.takeLast(14).average() > days.take(14).average())
    }
}
