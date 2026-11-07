package com.reelometro.app.detection

import com.reelometro.app.detection.RecipientDetector.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipientDetectorContactoEnviarTest {

    private fun sheet(nick: String): FakeNode {
        val xml = javaClass.classLoader!!.getResource("calibration/tiktok-47.7-hoja.xml")!!.readText(Charsets.UTF_8)
            .replace("\"rubio_m\"", "\"$nick\"")
        return UiDump.parse(xml, windowId = 2)
    }

    private fun detector() = RecipientDetector().apply { start(now = 0, reelId = 7, profile = PlatformProfile.TIKTOK) }

    private fun cellOf(root: UiNode, desc: String) =
        UiDump.all(root).single { it.contentDescription?.toString() == desc && it.className == "android.widget.Button" }

    private fun results(nick: String): List<String> {
        val out = mutableListOf<String>()
        for (part in listOf("celda", "nombre", "foto")) {
            val d = detector()
            val root = sheet(nick)
            d.onClick(cellOf(root, "💿"), UiDump.H, 1_000)
            val c = cellOf(root, nick)
            val target = when (part) {
                "celda" -> c
                "nombre" -> UiDump.all(c).first { it !== c && it.text?.toString() == nick }
                else -> UiDump.all(c).first { it.className == "android.widget.ImageView" }
            }
            val r = d.onClick(target, UiDump.H, 2_000)
            out += "$nick/$part -> $r abierta=${d.isOpen(3_000)}"
        }
        return out
    }

    @Test
    fun `una celda llamada Send no es el boton Enviar`() {
        val all = listOf("Send", "Enviar", "Envoyer", "Senden", "rubio_m").flatMap { results(it) }
        all.forEach { println(it) }
        val bad = all.filter { "Sent(" in it }
        assertTrue("falsos envíos:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun `control - el Enviar real sigue valiendo`() {
        val d = detector()
        d.onClick(cellOf(sheet("rubio_m"), "💿"), UiDump.H, 1_000)
        val send = UiDump.all(UiDump.load("tiktok-47.7-hoja-marcado.xml", windowId = 2)).single { it.text == "Enviar" }
        assertEquals("botón enviar (1)", d.onClick(send, UiDump.H, 2_000).rule)
    }
}
