package com.reelometro.app.work

import com.reelometro.app.copy.CopyRules
import com.reelometro.app.copy.NotificationCopy
import com.reelometro.app.copy.TitledText
import com.reelometro.app.copy.WidgetCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Textos nuevos de los avisos y del widget (los de siempre ya los prueba `CopyCatalogTest`). */
class NotificationTextsTest {

    private val min = 60_000L

    private fun variants(block: (Long) -> TitledText): Set<String> = (0L until 30L).map { block(it).text }.toSet()

    private fun assertRules(texts: Collection<String>) {
        val problems = texts.mapNotNull { t -> CopyRules.violations(t).takeIf { it.isNotEmpty() }?.let { "«${CopyRules.plain(t)}» → $it" } }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `el aviso que llega tarde dice ayer y no habla de mañana`() {
        val late = NotificationCopy.daily(212, 47 * min, 3, goal = 150, yesterday = true)
        assertEquals("212 vídeos ayer", CopyRules.plain(late.title))
        assertEquals("1 vídeo ayer", CopyRules.plain(NotificationCopy.daily(1, 20_000, 3, yesterday = true).title))
        val all = variants { NotificationCopy.daily(212, 47 * min, it, yesterday = true) } +
            variants { NotificationCopy.daily(0, 0, it, yesterday = true) }
        assertTrue(all.none { "mañana" in it.lowercase() || "hoy llevas" in it.lowercase() })
    }

    @Test
    fun `cada situacion nueva tiene tres variantes que cumplen las reglas`() {
        val late = variants { NotificationCopy.daily(212, 47 * min, it, yesterday = true) }
        val lateZero = variants { NotificationCopy.daily(0, 0, it, yesterday = true) }
        assertEquals(3, late.size)
        assertEquals(3, lateZero.size)
        assertRules(late + lateZero)
        assertRules(
            listOf(
                NotificationCopy.dailyPublic.title, NotificationCopy.dailyPublic.text,
                NotificationCopy.monthlyPublic.title, NotificationCopy.monthlyPublic.text,
            ),
        )
    }

    @Test
    fun `sin llegar tarde el diario sigue siendo el de hoy`() {
        assertEquals("212 vídeos hoy", CopyRules.plain(NotificationCopy.daily(212, 47 * min, 0).title))
    }

    @Test
    fun `las versiones publicas no llevan cifras`() {
        val public = listOf(NotificationCopy.dailyPublic, NotificationCopy.monthlyPublic).flatMap { listOf(it.title, it.text) }
        assertFalse(public.any { t -> t.any(Char::isDigit) })
    }

    @Test
    fun `la descripcion del widget incluye el objetivo si lo hay`() {
        val plain = CopyRules.plain(WidgetCopy.description(212, 47 * min, 150))
        assertEquals("Hoy: 212 vídeos, 47 minutos. 62 vídeos por encima del objetivo de 150.", plain)
        assertEquals(WidgetCopy.description(212, 47 * min), WidgetCopy.description(212, 47 * min, null))
        assertEquals("QUEDAN 38", WidgetCopy.goalStatus(112, 150))
        assertRules(listOf(WidgetCopy.description(1, 20_000, 150), WidgetCopy.description(150, 47 * min, 150)))
    }
}
