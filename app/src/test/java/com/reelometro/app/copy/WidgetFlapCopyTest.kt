package com.reelometro.app.copy

import com.reelometro.app.game.FlapMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetFlapCopyTest {

    @Test
    fun `la descripcion del widget nombra el estado de Flap`() {
        val base = WidgetCopy.description(12, 9 * 60_000L, null)
        assertEquals("$base Flap: en forma.", WidgetCopy.withFlap(base, FlapMood.EN_FORMA))
        assertEquals("$base Flap: zombi del scroll.", WidgetCopy.withFlap(base, FlapMood.ZOMBI))
    }

    @Test
    fun `cumple las reglas de estilo con todos los estados`() {
        for (mood in FlapMood.entries) {
            val text = WidgetCopy.withFlap(WidgetCopy.description(1_234, 3_600_000L, 150), mood)
            assertTrue("$mood: ${CopyRules.violations(text)}", CopyRules.violations(text).isEmpty())
        }
    }
}
