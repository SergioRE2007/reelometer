package com.reelometro.app.ui.settings

import com.reelometro.app.copy.SettingsCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Índice de Ajustes: a qué elemento de la lista salta cada acceso, en qué sección estás y las líneas plegables. */
class SettingsIndexTest {

    @Test
    fun `cada seccion salta a su elemento, en orden y tras el indice`() {
        val items = SettingsPart.entries.map(SettingsLayout::itemOf)
        assertEquals((SettingsLayout.FIRST_SECTION_ITEM until SettingsLayout.TOOLS_ITEM).toList(), items)
        assertTrue(SettingsLayout.FIRST_SECTION_ITEM > SettingsLayout.INDEX_ITEM)
        SettingsPart.entries.forEach { assertEquals(it, SettingsLayout.partOf(SettingsLayout.itemOf(it))) }
        // Las secciones del índice son las 8 de SettingsCopy, con su número.
        assertEquals(SettingsCopy.SECTIONS, SettingsPart.entries.map { it.section })
        assertEquals((1..8).map { "%02d".format(it) }, SettingsPart.entries.map { it.section.index })
    }

    @Test
    fun `la barra de arriba no tiene seccion y las herramientas cuentan como acerca de`() {
        assertNull(SettingsLayout.partOf(SettingsLayout.TOP_ITEM))
        assertNull(SettingsLayout.partOf(SettingsLayout.INDEX_ITEM))
        assertEquals(SettingsPart.ABOUT, SettingsLayout.partOf(SettingsLayout.TOOLS_ITEM))
    }

    @Test
    fun `la barra fija sale cuando el indice ya no se ve`() {
        assertFalse(SettingsLayout.showsBar(SettingsLayout.TOP_ITEM))
        assertFalse(SettingsLayout.showsBar(SettingsLayout.INDEX_ITEM))
        assertTrue(SettingsLayout.showsBar(SettingsLayout.itemOf(SettingsPart.GOAL)))
    }

    @Test
    fun `la seccion actual es la ultima que empieza por encima de la linea`() {
        val goal = SettingsLayout.itemOf(SettingsPart.GOAL)
        // AVISOS empieza a 40 px (por encima de la línea de 100): es la actual, aunque OBJETIVO asome arriba.
        val visible = listOf(goal to -300, goal + 1 to 40, goal + 2 to 900)
        assertEquals(SettingsPart.NOTIFICATIONS, SettingsLayout.currentPart(visible, linePx = 100))
        // Con la línea más arriba, sigue siendo OBJETIVO.
        assertEquals(SettingsPart.GOAL, SettingsLayout.currentPart(visible, linePx = 20))
        // Arriba del todo no hay sección.
        assertNull(SettingsLayout.currentPart(listOf(0 to 0, 1 to 200), linePx = 100))
        assertNull(SettingsLayout.currentPart(emptyList(), linePx = 100))
    }

    @Test
    fun `al final de la lista cuenta la seccion que llega a media pantalla`() {
        val privacy = SettingsLayout.itemOf(SettingsPart.PRIVACY)
        val about = SettingsLayout.itemOf(SettingsPart.ABOUT)
        val visible = listOf(privacy to -400, about to 600, SettingsLayout.TOOLS_ITEM to 1900)
        assertEquals(100, SettingsLayout.lineFor(barLinePx = 100, viewportPx = 2400, atEnd = false))
        assertEquals(SettingsPart.PRIVACY, SettingsLayout.currentPart(visible, SettingsLayout.lineFor(100, 2400, atEnd = false)))
        assertEquals(SettingsPart.ABOUT, SettingsLayout.currentPart(visible, SettingsLayout.lineFor(100, 2400, atEnd = true)))
    }

    @Test
    fun `la linea del envio rapido dice si esta apagado y cuantos hay fijados`() {
        assertEquals("Envío rápido · apagado", SettingsCopy.quickSendFold(enabled = false, pinned = 3))
        assertEquals("Envío rápido · nadie fijado", SettingsCopy.quickSendFold(enabled = true, pinned = 0))
        assertEquals("Envío rápido · 1 fijado", SettingsCopy.quickSendFold(enabled = true, pinned = 1))
        assertEquals("Envío rápido · 3 fijados", SettingsCopy.quickSendFold(enabled = true, pinned = 3))
    }

    @Test
    fun `cada acceso del indice tiene nombre corto y descripcion para TalkBack`() {
        val shorts = SettingsPart.entries.map { it.section.short }
        assertEquals(shorts.size, shorts.toSet().size)
        assertTrue(shorts.all { it.isNotBlank() && it.length <= 12 })
        assertEquals("Ir a Tus datos", SettingsCopy.indexGo(SettingsCopy.DATA))
        assertEquals("05 · TUS DATOS", SettingsCopy.indexCurrent(SettingsCopy.DATA))
    }
}
