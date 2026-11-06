package com.reelometro.app.ui.passport

import com.reelometro.app.copy.CopyRules
import com.reelometro.app.copy.CopyRules.plain
import com.reelometro.app.copy.PassportCopy
import com.reelometro.app.copy.ShareCopy
import com.reelometro.app.game.PassportFakes
import com.reelometro.app.game.StampId
import com.reelometro.app.game.TravelerLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Datos del ticket de nivel 1080 × 1920 (JVM puro; el dibujo lo hace `LevelTicketRenderer`). */
class LevelTicketModelTest {

    private val today = LocalDate.of(2026, 11, 4)

    @Test
    fun `tarjeta de embarque con nivel, ruta, millas y sellos recientes`() {
        val data = LevelTicketModel.build(PassportFakes.sample(today, xp = 520))
        assertEquals(ShareCopy.BRAND, data.brand)
        assertEquals("PLATA", data.levelName)
        assertEquals("PLA", data.originCode)
        assertEquals("ORO", data.destinationCode)
        assertEquals(PassportCopy.LABEL_DESTINATION, data.destinationLabel)
        assertEquals("520", data.total.value)
        assertEquals(LevelTicketModel.MAX_STAMPS, data.stamps.size)
        // El más reciente primero.
        assertEquals(PassportCopy.stampName(StampId.TRES_PLATAFORMAS), data.stamps.first())
        assertEquals("9 DE 23", data.lines.first().value)
        assertEquals("reelometro-nivel-plata.png", data.fileName)
    }

    @Test
    fun `código de barras - un trazo por sello y los pendientes en blanco`() {
        val state = PassportFakes.sample(today)
        val data = LevelTicketModel.build(state)
        assertEquals(StampId.entries.size, data.barcode.size)
        state.stamps.forEachIndexed { i, s ->
            if (s.earned) assertTrue(data.barcode[i] > 0f) else assertEquals(0f, data.barcode[i])
        }
        assertEquals("0000520 · 009 · 2026", data.barcodeCaption)
        assertEquals("9999999 · 999 · 2026", LevelTicketModel.barcodeCaption(123_456_789, 5_000, 2026))
    }

    @Test
    fun `en el último nivel la ruta acaba`() {
        val data = LevelTicketModel.build(PassportFakes.sample(today, xp = 2_000), TravelerLevel.PLATINO)
        assertEquals("PLT", data.originCode)
        assertEquals("PLT", data.destinationCode)
        assertEquals(PassportCopy.LABEL_FINAL, data.destinationLabel)
    }

    @Test
    fun `al subir, el ticket es del nivel celebrado aunque falten millas en el estado`() {
        val data = LevelTicketModel.build(PassportFakes.fresh(today), TravelerLevel.FRECUENTE)
        assertEquals("FRECUENTE", data.levelName)
        assertEquals("150", plain(data.total.value))
        assertTrue(data.stamps.isEmpty())
    }

    @Test
    fun `los textos del ticket cumplen las reglas`() {
        for (level in TravelerLevel.entries) for (d in 0L..6L) {
            val data = LevelTicketModel.build(PassportFakes.sample(today.plusDays(d), xp = level.minXp), level)
            val texts = listOf(data.title, data.dateLine, data.levelLabel, data.levelName, data.originLabel, data.originCode,
                data.destinationLabel, data.destinationCode, data.stampsLabel, data.footer, data.stamp, data.stampDate,
                data.brandFooter, data.shareText) + data.stamps + data.lines.flatMap { listOf(it.label, it.value) } +
                listOf(data.total.label, data.total.value)
            texts.forEach { t -> assertTrue("«$t»: ${CopyRules.violations(t)}", CopyRules.violations(t).isEmpty()) }
            assertTrue(CopyRules.violations(data.barcodeCaption, checkNumbers = false).isEmpty())
        }
    }
}
