package com.reelometro.app.ui.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.reelometro.app.data.Platform
import com.reelometro.app.data.settings.AppSettings
import com.reelometro.app.data.settings.SettingsRepository
import com.reelometro.app.detection.AvatarRules
import com.reelometro.app.detection.BubbleSpot
import com.reelometro.app.detection.PlatformProfile
import com.reelometro.app.detection.RecipientDetector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.10.0: el envío rápido también en TikTok, con fijados, posición y fotos por app (contratos de la fase 0). */
class QuickSendPerAppTest {

    @Test
    fun `lo guardado antes de la 1_10_0 es de Instagram y no se pierde`() = runTest {
        val store = FakePreferencesStore()
        store.edit {
            it[stringPreferencesKey("quick_send_targets")] = "Ana\nLos del piso"
            it[floatPreferencesKey("quick_send_bubble_cx")] = 0.3f
            it[floatPreferencesKey("quick_send_bubble_cy")] = 0.8f
        }
        val s = SettingsRepository(store).current()
        assertEquals(listOf("Ana", "Los del piso"), s.quickSendTargets)
        assertEquals(listOf("Ana", "Los del piso"), s.quickSendTargets(Platform.INSTAGRAM))
        assertEquals(BubbleSpot(0.3f, 0.8f), s.quickSendBubble(Platform.INSTAGRAM))
        assertTrue(s.quickSendTargets(Platform.TIKTOK).isEmpty())
        assertNull(s.quickSendBubble(Platform.TIKTOK))
        assertTrue(s.quickSendTargets(Platform.YOUTUBE).isEmpty())
    }

    @Test
    fun `cada app tiene sus fijados y su posicion`() = runTest {
        val store = FakePreferencesStore()
        val settings = SettingsRepository(store)
        settings.toggleQuickSendTarget(Platform.INSTAGRAM, "Ana")
        settings.toggleQuickSendTarget(Platform.TIKTOK, "💿")
        settings.toggleQuickSendTarget(Platform.TIKTOK, "Ana")
        settings.setQuickSendBubble(Platform.TIKTOK, BubbleSpot(0.9f, 0.3f))
        var s = settings.current()
        assertEquals(listOf("Ana"), s.quickSendTargets)
        assertEquals(listOf("💿", "Ana"), s.quickSendTargets(Platform.TIKTOK))
        assertNull("la posición de TikTok no mueve las de Instagram", s.quickSendBubble(Platform.INSTAGRAM))
        assertEquals(BubbleSpot(0.9f, 0.3f), s.quickSendBubble(Platform.TIKTOK))
        assertEquals(mapOf(Platform.INSTAGRAM to listOf("Ana"), Platform.TIKTOK to listOf("💿", "Ana")), s.allQuickSendTargets())

        // Quitar en una app no toca la otra; el cupo es por app.
        assertEquals(listOf("💿"), settings.toggleQuickSendTarget(Platform.TIKTOK, "Ana"))
        settings.toggleQuickSendTarget(Platform.TIKTOK, "B")
        settings.toggleQuickSendTarget(Platform.TIKTOK, "C")
        settings.toggleQuickSendTarget(Platform.TIKTOK, "D")
        s = settings.current()
        assertEquals(AppSettings.MAX_QUICK_SEND, s.quickSendTargets(Platform.TIKTOK).size)
        assertEquals(listOf("Ana"), s.quickSendTargets)

        // RECOLOCAR en TikTok solo mueve las de TikTok; YouTube no tiene envío rápido.
        settings.setQuickSendBubble(Platform.INSTAGRAM, BubbleSpot(0.2f, 0.5f))
        settings.resetQuickSendBubble(Platform.TIKTOK)
        settings.toggleQuickSendTarget(Platform.YOUTUBE, "X")
        s = settings.current()
        assertNull(s.quickSendBubble(Platform.TIKTOK))
        assertEquals(BubbleSpot(0.2f, 0.5f), s.quickSendBubble)
        assertTrue(s.quickSendTargets(Platform.YOUTUBE).isEmpty())
        // Instagram sigue en sus claves de siempre (una versión anterior las leería igual).
        assertEquals("Ana", store.data.first()[stringPreferencesKey("quick_send_targets")])
    }

    @Test
    fun `la foto y la memoria de Instagram no cambian de fichero y las de TikTok van aparte`() {
        // El hash de Instagram es el de siempre (las fotos ya guardadas siguen valiendo).
        assertEquals(AvatarRules.fileName("Los del piso"), AvatarRules.fileName("Los del piso", Platform.INSTAGRAM))
        assertNotEquals(AvatarRules.fileName("Ana", Platform.INSTAGRAM), AvatarRules.fileName("Ana", Platform.TIKTOK))
        assertEquals(AvatarRules.fileName("Ana", Platform.TIKTOK), AvatarRules.fileName("ANA", Platform.TIKTOK))
        assertTrue(AvatarRules.fileNames("Ana", Platform.TIKTOK).none { it in AvatarRules.fileNames("Ana") })
    }

    @Test
    fun `las marcas de direccion de TikTok no cuentan y los nombres de emojis valen solo en TikTok`() {
        val lrm = Char(0x200E).toString()
        val fsi = Char(0x2068).toString()
        val pdi = Char(0x2069).toString()
        assertEquals("rubio_m", RecipientDetector.normalize(lrm + "rubio_m"))
        assertEquals("carlos, bea", RecipientDetector.normalize(fsi + "Carlos" + pdi + ", " + fsi + "Bea" + pdi))
        val detector = RecipientDetector()
        // Las marcas se quitan con las reglas de nombres de TikTok; en Instagram el nombre se apunta como en la 1.9.0.
        assertEquals("rubio_m", detector.clean(lrm + "rubio_m", fromText = true, symbolNames = PlatformProfile.TIKTOK.symbolNames))
        assertEquals(lrm + "rubio_m", detector.clean(lrm + "rubio_m", fromText = true, symbolNames = PlatformProfile.INSTAGRAM.symbolNames))
        assertNull("en Instagram, sin letras no es un nombre", detector.clean("💿", fromText = false))
        assertEquals("💿", detector.clean(lrm + "💿", fromText = true, symbolNames = PlatformProfile.TIKTOK.symbolNames))
        assertNull(detector.clean("12", fromText = true, symbolNames = true))
        assertNull(detector.clean("…", fromText = true, symbolNames = true))
    }
}
