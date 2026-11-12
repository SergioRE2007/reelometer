package com.reelometro.app.detection

import com.reelometro.app.detection.ScreenWindow.Kind.APP
import com.reelometro.app.detection.ScreenWindow.Kind.OTHER
import com.reelometro.app.detection.ScreenWindow.Kind.SYSTEM
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.10.5: la barra de notificaciones (o los ajustes rápidos) bajada encima de la app esconde las burbujas. */
class SystemShadeTest {

    private val w = SCREEN_W
    private val h = SCREEN_H

    private fun app(focused: Boolean, layer: Int = 1) = ScreenWindow(1, APP, layer, w, h, focused = focused, active = focused)
    private val statusBar = ScreenWindow(2, SYSTEM, 5, w, 110, focused = false, active = false)
    private val navBar = ScreenWindow(3, SYSTEM, 5, w, 63, focused = false, active = false)

    private fun covers(vararg windows: ScreenWindow, width: Int = w, height: Int = h) = SystemShade.covers(windows.toList(), width, height)

    @Test
    fun `la barra desplegada encima de la app las esconde`() {
        // Android 12+: la NotificationShade, a pantalla completa y con el foco.
        val shade = ScreenWindow(9, SYSTEM, 6, w, h, focused = true, active = true)
        assertTrue(covers(shade, statusBar, navBar, app(focused = false)))
        // Aunque la app siga marcada como activa, el foco lo tiene la barra.
        assertTrue(covers(shade, ScreenWindow(1, APP, 1, w, h, focused = false, active = true)))
    }

    @Test
    fun `en Android 8 a 10 la barra de estado se hace grande y se lleva el foco`() {
        val expanded = ScreenWindow(2, SYSTEM, 5, w, h, focused = true, active = false)
        assertTrue(covers(expanded, navBar, app(focused = false)))
    }

    @Test
    fun `sin ventanas de la app y el sistema a pantalla completa`() {
        // Con la barra bajada, Android puede dejar de informar de las ventanas tapadas.
        assertTrue(covers(ScreenWindow(9, SYSTEM, 6, w, h, focused = false, active = false), navBar))
    }

    @Test
    fun `la barra de estado y la de navegacion no esconden nada`() {
        assertFalse(covers(statusBar, navBar, app(focused = true)))
        assertFalse(covers(statusBar, navBar))
    }

    @Test
    fun `una notificacion emergente no le quita el foco a la app`() {
        val headsUp = ScreenWindow(9, SYSTEM, 6, w, h, focused = false, active = false)
        assertFalse(covers(headsUp, statusBar, app(focused = true)))
    }

    @Test
    fun `la barra de navegacion lateral en horizontal es estrecha`() {
        val side = ScreenWindow(3, SYSTEM, 5, 126, 1080, focused = true, active = false)
        assertFalse(covers(side, ScreenWindow(1, APP, 1, 2400, 1080, focused = false, active = false), width = 2400, height = 1080))
    }

    @Test
    fun `teclado y nuestra capa no cuentan`() {
        val keyboard = ScreenWindow(4, OTHER, 7, w, h * 2 / 3, focused = true, active = false)
        val ours = ScreenWindow(5, OTHER, 8, w, h, focused = true, active = true)
        assertFalse(covers(keyboard, ours, app(focused = false)))
    }

    @Test
    fun `una ventana del sistema por debajo de la app no la tapa`() {
        val below = ScreenWindow(9, SYSTEM, 0, w, h, focused = true, active = true)
        assertFalse(covers(below, app(focused = false, layer = 2)))
    }

    @Test
    fun `sin tamano de pantalla no se sabe y no se esconden`() {
        val shade = ScreenWindow(9, SYSTEM, 6, w, h, focused = true, active = true)
        assertFalse(covers(shade, width = 0, height = 0))
        assertFalse(SystemShade.covers(emptyList(), w, h))
    }
}
