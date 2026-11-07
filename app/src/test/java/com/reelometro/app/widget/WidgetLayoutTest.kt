package com.reelometro.app.widget

import com.reelometro.app.ui.components.flap.FlapGlyphMetrics
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Maqueta del widget: nada se sale a lo ancho en ninguno de los seis tamaños, ni con la fuente al 130 %. */
class WidgetLayoutTest {

    private val today = LocalDate.of(2026, 11, 3)

    // Las mismas medidas falsas que WidgetModelTest y WidgetFlapTest.
    private val metrics = FlapGlyphMetrics(advanceEm = 0.5f, inkTopEm = -0.72f, inkBottomEm = 0.01f)
    private val text = object : TextMetrics {
        override fun width(text: String, spec: TextSpec) = text.length * spec.sizeDp * (0.6f + spec.trackingEm)
        override fun lineHeight(spec: TextSpec) = spec.sizeDp * 1.32f
    }

    private fun layout(width: Float, height: Float, m: WidgetModel, scale: Float): WidgetLayout =
        WidgetLayout.compute(
            width, height, m, metrics,
            label = TextSpec(11f * scale, 600, 0.12f),
            dataL = TextSpec(22f * scale, 500, 0f),
            data = TextSpec(15f * scale, 500, 0f),
            dataS = TextSpec(13f * scale, 500, 0f),
            text = text,
            maxFontDp = 132f,
        )

    @Test
    fun `el texto bajo el marcador cabe a lo ancho, también «SIN SEÑAL» al 130 %`() {
        val models = listOf(
            WidgetModel.of(WidgetData(today, 212, 47 * 60_000L, 150, true, null)),
            WidgetModel.of(WidgetData(today, 0, 0, null, false, null)), // servicio apagado: «SIN SEÑAL»
            WidgetModel.of(WidgetData(today, 1_234, 9 * 3_600_000L, null, true, null)),
        )
        for (size in WidgetSizes.ALL) for (scale in listOf(1f, 1.3f)) for (m in models) {
            val l = layout(size.width.value, size.height.value, m, scale)
            val detail = m.detail ?: continue
            // Con sitio de sobra (los tamaños altos), el aviso sale: no se esconde para que quepa.
            if (size.height.value >= 200f) assertTrue("$size ×$scale sin «$detail»", l.showDetail)
            if (!l.showDetail || l.arrangement != Arrangement.STACKED) continue
            val w = text.width(detail, l.detail)
            assertTrue("$size ×$scale «$detail»: $w > ${l.innerWidth}", w <= l.innerWidth)
        }
    }
}
