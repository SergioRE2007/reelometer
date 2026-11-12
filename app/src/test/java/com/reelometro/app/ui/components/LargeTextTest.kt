package com.reelometro.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Desde qué escala de letra del sistema la app cuenta la letra como «grande». */
class LargeTextTest {
    @Test
    fun `por debajo del 130 por ciento no es grande`() {
        assertFalse(isLargeFontScale(1f))
        assertFalse(isLargeFontScale(1.15f))
    }

    @Test
    fun `desde el 130 por ciento es grande, con margen por redondeos`() {
        assertTrue(isLargeFontScale(1.3f))
        assertTrue(isLargeFontScale(1.2999f))
        assertTrue(isLargeFontScale(1.5f))
        assertTrue(isLargeFontScale(2f))
    }
}
