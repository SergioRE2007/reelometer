package com.reelometro.app.widget

import androidx.compose.ui.unit.DpSize
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapRules
import com.reelometro.app.ui.components.flap.FlapGlyphMetrics
import com.reelometro.app.ui.components.flap.boardWidthEm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Flap en el widget: solo en el tamaño grande y sin estropear ninguno de los seis tamaños. */
class WidgetFlapTest {

    private val today = LocalDate.of(2026, 11, 3)

    private fun data(count: Int, goal: Int? = null, on: Boolean = true, flap: FlapMood? = FlapMood.NORMAL) =
        WidgetData(today, count, 47 * 60_000L, goal, on, flap)

    // Las mismas medidas falsas que WidgetModelTest.
    private val metrics = FlapGlyphMetrics(advanceEm = 0.5f, inkTopEm = -0.72f, inkBottomEm = 0.01f)
    private val text = object : TextMetrics {
        override fun width(text: String, spec: TextSpec) = text.length * spec.sizeDp * (0.6f + spec.trackingEm)
        override fun lineHeight(spec: TextSpec) = spec.sizeDp * 1.32f
    }

    private fun specs(scale: Float) = listOf(
        TextSpec(11f * scale, 600, 0.12f),
        TextSpec(22f * scale, 500, 0f),
        TextSpec(15f * scale, 500, 0f),
        TextSpec(13f * scale, 500, 0f),
    )

    private fun layout(size: DpSize, m: WidgetModel, scale: Float = 1f): WidgetLayout {
        val (label, dataL, dataM, dataS) = specs(scale)
        return WidgetLayout.compute(size.width.value, size.height.value, m, metrics, label, dataL, dataM, dataS, text, maxFontDp = 132f)
    }

    private fun boardWidth(l: WidgetLayout, m: WidgetModel) = l.fontDp * boardWidthEm(m.cells, metrics.tileWidthEm)

    @Test
    fun `Flap solo sale en el tamaño grande`() {
        val m = WidgetModel.of(data(212, goal = 150))
        for (size in WidgetSizes.ALL) {
            val l = layout(size, m)
            if (size == WidgetSizes.LARGE) assertTrue("$size", l.flapDp > 0f) else assertEquals("$size", 0f, l.flapDp)
        }
    }

    @Test
    fun `en los otros cinco tamaños la maqueta es la misma que sin Flap`() {
        for (count in listOf(0, 212, 1_234)) {
            for (goal in listOf(null, 150)) {
                val flapped = WidgetModel.of(data(count, goal))
                val unflapped = WidgetModel.of(data(count, goal, flap = null))
                for (size in WidgetSizes.ALL - WidgetSizes.LARGE) assertEquals("$count en $size", layout(size, unflapped), layout(size, flapped))
                assertEquals(0f, layout(WidgetSizes.LARGE, unflapped).flapDp)
            }
        }
    }

    @Test
    fun `con Flap el marcador y el tiempo caben a su izquierda y el marcador apenas encoge`() {
        for (scale in listOf(1f, 1.3f)) {
            for (count in listOf(0, 7, 212, 1_234, 12_408)) {
                for (goal in listOf(null, 150)) {
                    val m = WidgetModel.of(data(count, goal))
                    val l = layout(WidgetSizes.LARGE, m, scale)
                    val plain = layout(WidgetSizes.LARGE, WidgetModel.of(data(count, goal, flap = null)), scale)
                    if (l.flapDp == 0f) {
                        assertEquals(plain, l)
                        continue
                    }
                    val name = "$count (objetivo $goal) al ${(scale * 100).toInt()} %"
                    assertTrue("$name: marcador", boardWidth(l, m) + WidgetLayout.COLUMN_GAP + l.flapDp <= l.innerWidth + 0.01f)
                    assertTrue("$name: tiempo", text.width(m.detail!!, l.detail) + WidgetLayout.COLUMN_GAP + l.flapDp <= l.innerWidth + 0.01f)
                    assertTrue("$name: alto", l.flapDp <= WidgetSizes.LARGE.height.value - 2 * l.padding)
                    assertTrue("$name: encoge poco", l.fontDp >= plain.fontDp * 0.75f - 0.01f)
                    assertTrue("$name: píxeles enteros", l.flapDp == 64f || l.flapDp == 48f)
                }
            }
        }
    }

    @Test
    fun `sin señal o con Flap apagado no hay Flap`() {
        assertNull(WidgetModel.of(data(0, on = false)).flap)
        assertNull(WidgetModel.of(data(212, flap = null)).flap)
        assertNull(WidgetModel.of(null).flap)
        assertEquals(0f, layout(WidgetSizes.LARGE, WidgetModel.of(data(0, on = false))).flapDp)
        assertEquals(FlapMood.KO, WidgetModel.of(data(212, flap = FlapMood.KO)).flap)
    }

    @Test
    fun `Flap del widget sale de los videos de hoy frente al objetivo, sin historial`() {
        // Con objetivo 40: a tope por debajo de 20, media vida en 40 (CANSADO), enfermo pasado 40, KO en 80.
        assertEquals(FlapMood.EN_FORMA, WidgetData.flapFor(true, 0, 40))
        assertEquals(FlapMood.EN_FORMA, WidgetData.flapFor(true, 19, 40))
        assertEquals(FlapMood.NORMAL, WidgetData.flapFor(true, 20, 40))
        assertEquals(FlapMood.CANSADO, WidgetData.flapFor(true, 40, 40))
        assertEquals(FlapMood.ENFERMO, WidgetData.flapFor(true, 41, 40))
        assertEquals(FlapMood.KO, WidgetData.flapFor(true, 80, 40))
        for (count in listOf(0, 7, 41, 79, 80, 1_234)) {
            assertEquals("$count", FlapRules.moodFor(count, 40), WidgetData.flapFor(true, count, 40))
        }
    }

    @Test
    fun `sin objetivo Flap aguanta el de por defecto y apagado no se calcula`() {
        val limit = FlapRules.DEFAULT_LIMIT
        assertEquals(FlapRules.moodFor(212, limit), WidgetData.flapFor(true, 212, null))
        assertEquals(FlapMood.CANSADO, WidgetData.flapFor(true, limit, null))
        assertEquals(FlapMood.KO, WidgetData.flapFor(true, 2 * limit, 0))
        assertNull(WidgetData.flapFor(false, 0, 40))
        assertNull(WidgetData.flapFor(false, 5_000, null))
    }
}
