package com.reelometro.app.ui.wrapped

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.reelometro.app.data.ROBOLECTRIC_SDK
import com.reelometro.app.data.ReelRobolectricRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * El ticket se pinta con `android.graphics`: aquí solo se comprueba que sale del tamaño de una
 * story y que no lanza con datos normales ni con casos límite (en el modo gráfico por defecto de
 * Robolectric el lienzo no pinta píxeles reales; el aspecto se revisa en el emulador).
 *
 * Con [ReelRobolectricRunner]: si este es el primer test Robolectric de la JVM en un equipo que
 * bloquea su runtime nativo (Smart App Control), ese primer test sale como omitido; los demás
 * comprueban también el tamaño.
 */
@RunWith(ReelRobolectricRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class TicketRendererTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `el ticket mide 1080 x 1920`() {
        val bitmap = TicketRenderer.render(context, TicketModel.build(periodStats()))
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
    }

    @Test
    fun `no lanza con un año entero, un creador larguísimo ni sin equivalencias`() {
        val year = TicketModel.build(periodStats(ref = com.reelometro.app.stats.PeriodRef.THIS_YEAR, creator = "la_cocina_de_maria_jose_y_sus_recetas_oficial"))
        assertEquals(1920, TicketRenderer.render(context, year).height)
        val tiny = TicketModel.build(periodStats(perDay = { if (it.dayOfMonth == 5) 3 else 0 }, msPerVideo = 20_000))
        assertEquals(1080, TicketRenderer.render(context, tiny.copy(equivalences = emptyList())).width)
    }

    @Test
    fun `el PNG no sale vacío`() {
        val bitmap = TicketRenderer.render(context, TicketModel.build(periodStats(ref = com.reelometro.app.stats.PeriodRef.THIS_WEEK)))
        assertEquals(1080 to 1920, bitmap.width to bitmap.height)
        assertTrue(TicketRenderer.encodePng(bitmap).isNotEmpty())
    }
}
