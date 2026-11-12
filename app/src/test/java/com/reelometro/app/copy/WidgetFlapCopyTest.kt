package com.reelometro.app.copy

import com.reelometro.app.game.FlapMood
import com.reelometro.app.stats.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetFlapCopyTest {

    private val base = WidgetCopy.description(12, 9 * 60_000L, null)

    @Test
    fun `la descripcion del widget nombra el estado de Flap como la pestana`() {
        for (mood in FlapMood.entries) {
            val text = WidgetCopy.withFlap(base, mood)
            assertTrue(text, text.startsWith("$base Flap: ") && text.endsWith("."))
            val said = text.removePrefix("$base Flap: ").removeSuffix(".")
            assertEquals(text, FlapCopy.moodSpoken(mood), said)
        }
    }

    @Test
    fun `el estado va en minusculas y sin siglas`() {
        assertEquals("$base Flap: en forma.", WidgetCopy.withFlap(base, FlapMood.EN_FORMA))
        assertEquals("$base Flap: enfermo.", WidgetCopy.withFlap(base, FlapMood.ENFERMO))
        assertEquals("$base Flap: fuera de combate.", WidgetCopy.withFlap(base, FlapMood.KO))
        for (mood in FlapMood.entries) {
            val said = WidgetCopy.withFlap(base, mood).removePrefix("$base Flap: ")
            assertEquals(said, said.lowercase(Format.locale), said)
        }
    }

    @Test
    fun `cada estado se lee distinto`() {
        val texts = FlapMood.entries.map { WidgetCopy.withFlap(base, it) }
        assertEquals(FlapMood.entries.size, texts.toSet().size)
    }

    @Test
    fun `cumple las reglas de estilo con todos los estados`() {
        for (mood in FlapMood.entries) {
            val text = WidgetCopy.withFlap(WidgetCopy.description(1_234, 3_600_000L, 150), mood)
            assertTrue("$mood: ${CopyRules.violations(text)}", CopyRules.violations(text).isEmpty())
        }
    }
}
