package com.reelometro.app.detection

import com.reelometro.app.detection.RecipientDetector.Companion.OWN_SHARE_ECHO_MS
import com.reelometro.app.detection.RecipientDetector.Companion.isOwnQuickClick
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.10.1: un «Compartir» del visor pulsado a mano en el segundo siguiente a un envío rápido caía en «clic propio» y no abría
 * la sesión de «A quién envías». Con el envío ya terminado, ese clic es del usuario; los del propio envío siguen sin contar.
 */
class RecipientDetectorClicPropioTest {

    private val until = 2_000L

    @Test
    fun `mientras el envio rapido esta en curso todos sus clics son propios`() {
        assertTrue(isOwnQuickClick(quickBusy = true, now = 1_500, ignoreUntil = until, viewerShare = true))
        assertTrue(isOwnQuickClick(quickBusy = true, now = 5_000, ignoreUntil = until, viewerShare = false))
    }

    @Test
    fun `terminado, el Compartir del visor cuenta y lo demas sigue siendo propio hasta el margen`() {
        assertFalse(isOwnQuickClick(quickBusy = false, now = 1_500, ignoreUntil = until, viewerShare = true))
        assertTrue("otro clic (el «Enviar» que llega tarde, la hoja…)", isOwnQuickClick(quickBusy = false, now = 1_500, ignoreUntil = until, viewerShare = false))
        assertFalse("pasado el margen, nada es propio", isOwnQuickClick(quickBusy = false, now = 2_000, ignoreUntil = until, viewerShare = false))
    }

    @Test
    fun `el evento retrasado del Compartir que pulso el propio envio sigue siendo propio`() {
        val own = 1_000L
        assertTrue(isOwnQuickClick(quickBusy = false, now = own + OWN_SHARE_ECHO_MS - 1, ignoreUntil = until, viewerShare = true, ownShareAt = own))
        assertFalse(isOwnQuickClick(quickBusy = false, now = own + OWN_SHARE_ECHO_MS, ignoreUntil = until, viewerShare = true, ownShareAt = own))
    }
}
