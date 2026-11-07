package com.reelometro.app.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los volcados de TikTok 47.7 (anonimizados) dicen lo que recoge `PlatformProfile.TIKTOK` (1.10.0). Si TikTok cambia y
 * se vuelcan de nuevo, estos tests dicen qué ha cambiado.
 */
class TikTokDumpTest {

    private fun labels(root: UiNode) = UiDump.all(root)

    @Test
    fun `el feed tiene compartir dentro del visor y el mismo id que me gusta`() {
        val feed = UiDump.load("tiktok-47.7-feed.xml")
        val share = labels(feed).single { it.contentDescription?.startsWith("Compartir el vídeo") == true }
        val like = labels(feed).single { it.contentDescription?.startsWith("Dar me gusta") == true }
        assertEquals("compartir y me gusta comparten id: no se aprende", like.viewId, share.viewId)
        assertTrue(ShareDetector().evaluate(share, UiDump.W, UiDump.H).isShare)
        var p: UiNode? = share.parent
        var inFeed = false
        while (p != null) {
            if (PlatformProfile.TIKTOK.feedIds.any { shortViewId(p!!.viewId).contains(it) }) inFeed = true
            p = p.parent
        }
        assertTrue("compartir está dentro del visor (viewpager)", inFeed)
    }

    @Test
    fun `la hoja tiene contactos descritos con el nombre entero y opciones que no lo son`() {
        val sheet = UiDump.load("tiktok-47.7-hoja.xml")
        val all = labels(sheet)
        assertTrue(all.any { shortViewId(it.viewId) == "tv_title" && it.text == "Enviar a" })
        assertTrue(all.any { shortViewId(it.viewId) == "touch_outside" })
        val cell = all.single { it.contentDescription == "MARÍA FERNÁNDEZ LÓPEZ" }
        assertTrue(cell.isClickable)
        val cut = cell.children.flatMap { it.children + it }.firstNotNullOf { it.text?.toString() }
        assertTrue("el texto va cortado y con marcas: $cut", RecipientDetector.normalize(cut).startsWith("maria fernandez") && cut.contains("..."))
        assertTrue(all.any { it.contentDescription == "💿" })
        // «Compartir» de la hoja es repostear: es una opción, nunca un contacto.
        assertTrue(all.any { it.contentDescription == "Compartir" })
        assertTrue("compartir" in PlatformProfile.TIKTOK.sheetOptions)
        assertTrue(all.none { it.className?.contains("EditText") == true })
    }

    @Test
    fun `al marcar cuenta el CheckBox y no el selected de la celda reciclada`() {
        val sheet = UiDump.load("tiktok-47.7-hoja-celda-reciclada.xml")
        val checked = UiDump.all(sheet).filter { it.isChecked }
        assertEquals(1, checked.size)
        val send = UiDump.all(sheet).single { it.text == "Enviar" }
        assertTrue(send.bounds.width > UiDump.W / 2)
        assertTrue("emojis de reacción", UiDump.all(sheet).count { it.className?.contains("GridView") == true } == 1)
    }

    @Test
    fun `el buscador tiene filas con CheckBox, usuario y grupos`() {
        val typed = UiDump.load("tiktok-47.7-buscador-escrito.xml")
        val all = UiDump.all(typed)
        assertTrue(all.any { it.className?.contains("EditText") == true && it.text == "rub" })
        assertTrue(all.any { it.text == "rubio_m" })
        assertTrue(all.any { RecipientDetector.normalize(it.text?.toString().orEmpty()) == "carlos, bea, rubio_m" })
        assertEquals(3, all.count { it.className?.contains("CheckBox") == true })
        val marked = UiDump.load("tiktok-47.7-buscador-marcado.xml")
        assertTrue(UiDump.all(marked).any { shortViewId(it.viewId) == "tv_send" && it.text == "Enviar" })
    }
}
