package com.reelometro.app.copy

import com.reelometro.app.R
import com.reelometro.app.stats.Format
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * La estructura de idiomas: textos desde recursos, variantes con arrays de cualquier tamaño y
 * [Format] según el idioma de los textos (en español, lo de siempre; en otro, sus separadores y
 * nombres). No hay traducciones en el repo: se simulan con [Texts] falsos.
 */
class TextsTest {

    @After
    fun tearDown() = AppTexts.reset()

    /** Español de `values/` con otro idioma, algunos textos cambiados y arrays a medida. */
    private class FakeTexts(
        override val locale: Locale,
        private val strings: Map<Int, String> = emptyMap(),
        private val arrays: Map<Int, List<String>> = emptyMap(),
    ) : Texts {
        private val base = JvmTexts.spanish()
        override fun string(id: Int, vararg args: Any): String =
            strings[id]?.let { if (args.isEmpty()) it else format(it, *args) } ?: base.string(id, *args)
        override fun plural(id: Int, count: Int, vararg args: Any): String = base.plural(id, count, *args)
        override fun array(id: Int): List<String> = arrays[id] ?: base.array(id)
    }

    @Test
    fun `sin instalar nada, los tests leen el español de res values`() {
        assertEquals(Locale.forLanguageTag("es-ES"), AppTexts.current.locale)
        assertEquals("Hoy, martes 3 de noviembre", DayCopy.title(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3)))
        assertEquals("1\u00A0vídeo", Words.videos(1))
        assertEquals("1.234\u00A0vídeos", Words.videos(1_234))
        assertEquals("0\u00A0vídeos", Words.videos(0))
        assertEquals("un minuto", Words.duration(60_000))
        assertEquals("una hora y 12\u00A0minutos", Words.duration(72 * 60_000L))
        assertEquals("TikTok, Reels e Instagram", Words.join(listOf("TikTok", "Reels", "Instagram")))
        assertEquals("a la 01:00", Words.atHour(1))
        assertEquals("a las 23:00", Words.atHour(23))
    }

    @Test
    fun `en español Format da exactamente lo de siempre`() {
        assertEquals("1.234", Format.integer(1_234))
        assertEquals("3,2", Format.decimal(3.24))
        assertEquals("34\u00A0%", Format.percent(0.34))
        assertEquals("−12\u00A0%", Format.signedPercent(-0.12))
        assertEquals("3,2\u00A0h", Format.durationCompact(3 * 3_600_000L + 12 * 60_000L))
        assertEquals("1\u00A0h\u00A012\u00A0min", Format.duration(72 * 60_000L))
        assertEquals("martes 3 de noviembre", Format.dayLong(LocalDate.of(2026, 11, 3)))
        assertEquals("3 – 9 nov", Format.range(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 9)))
        assertEquals("X", Format.weekdayInitial(DayOfWeek.WEDNESDAY))
        assertEquals("MIÉRCOLES", Format.upper("miércoles"))
    }

    @Test
    fun `en otro idioma cambian separadores, nombres y orden de las fechas`() {
        AppTexts.install(
            FakeTexts(
                Locale.ENGLISH,
                strings = mapOf(
                    R.string.format_percent to "%1\$s%%",
                    R.string.format_day_long to "%1\$s, %3\$s %2\$s",
                    R.string.format_day_short to "%2\$s %1\$s",
                    R.string.format_range_same_month to "%3\$s %1\$s – %2\$s",
                ),
            ),
        )
        assertEquals("1,234", Format.integer(1_234))
        assertEquals("3.2", Format.decimal(3.24))
        assertEquals("34%", Format.percent(0.34))
        assertEquals("Tuesday, November 3", Format.dayLong(LocalDate.of(2026, 11, 3)))
        assertEquals("Nov 3", Format.dayShort(LocalDate.of(2026, 11, 3)))
        assertEquals("Nov 3 – 9", Format.range(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 9)))
        assertEquals("W", Format.weekdayInitial(DayOfWeek.WEDNESDAY))
    }

    @Test
    fun `mayusculas y capitalizar siguen el idioma`() {
        AppTexts.install(FakeTexts(Locale.forLanguageTag("tr")))
        assertEquals("İSTANBUL", Format.upper("istanbul"))
        assertEquals("İzmir", Format.capitalize("izmir"))
    }

    @Test
    fun `cada idioma puede tener su numero de variantes sin romper lo determinista`() {
        for (size in listOf(2, 3, 5, 9)) {
            AppTexts.install(FakeTexts(Locale.ENGLISH, arrays = mapOf(R.array.day_zero to (1..size).map { "Phrase $it." })))
            val seen = mutableSetOf<String>()
            for (seed in 0L until 60L) {
                val today = DayCopy.quote(0, 0, seed)
                assertEquals("determinista con $size", today, DayCopy.quote(0, 0, seed))
                assertNotEquals("no repite con $size (día $seed)", today, DayCopy.quote(0, 0, seed + 1))
                seen += today
            }
            assertEquals("salen todas con $size", size, seen.size)
        }
    }

    @Test
    fun `lo que falta en una traduccion sale en español`() {
        AppTexts.install(FakeTexts(Locale.ENGLISH, strings = mapOf(R.string.day_title_today to "Today, %1\$s")))
        val day = LocalDate.of(2026, 11, 3)
        assertTrue(DayCopy.title(day, day).startsWith("Today, "))
        assertTrue(DayCopy.label(day, day.plusDays(1)).startsWith("AYER · TUESDAY"))
    }

    @Test
    fun `los recursos se leen con las reglas de aapt2`() {
        assertEquals("a b", JvmTexts.unescape("  a \n   b  "))
        assertEquals(", ", JvmTexts.unescape("\", \""))
        assertEquals("l'app \"x\" @ ?", JvmTexts.unescape("l\\'app \\\"x\\\" \\@ \\?"))
        assertEquals("1\u00A0%", JvmTexts.unescape("1\\u00A0%"))
        assertEquals("a\nb", JvmTexts.unescape("a\\nb"))
    }
}
