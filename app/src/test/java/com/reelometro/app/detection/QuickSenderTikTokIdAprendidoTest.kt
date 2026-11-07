package com.reelometro.app.detection

import com.reelometro.app.detection.QuickSender.Step
import com.reelometro.app.detection.QuickSender.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verificación final de la 1.10.0: por el **id aprendido** (el atajo) solo vale un campo con la lupa al lado, como por la
 * lupa. Los ids de TikTok están ofuscados y cambian con cada versión: uno aprendido en otra versión podría caer hoy en un campo
 * «Escribe un mensaje…» (`pja` en la hoja de contactos, `h84` en el buscador). En un idioma cuya pista del mensaje no está en la
 * lista (aquí alemán con «Schreib eine Nachricht…»), el nombre del siguiente destino se habría escrito en el mensaje (y se
 * habría enviado con el vídeo). Volcados reales de TikTok 47.7.3, con la hoja en alemán.
 */
class QuickSenderTikTokIdAprendidoTest {

    private val finder = FakeFinder()
    private val sender = QuickSender(finder = finder)

    private val feed by lazy { UiDump.load("tiktok-47.7-feed.xml", windowId = FEED) }

    private fun dumpWith(name: String, vararg replace: Pair<String, String>): FakeNode {
        var xml = UiDump::class.java.classLoader!!.getResourceAsStream("calibration/tiktok-47.7-$name.xml")!!
            .use { it.readBytes().toString(Charsets.UTF_8) }
        replace.forEach { (from, to) ->
            assertTrue("el volcado $name tiene «$from»", xml.contains(from))
            xml = xml.replace(from, to)
        }
        return UiDump.parse(xml, windowId = SHEET)
    }

    private val german = arrayOf("\"Enviar a\"" to "\"Senden an\"", "\"Buscar\"" to "\"Suchen\"")

    private fun tick(now: Long, vararg roots: UiNode): Step = sender.tick(roots.toList(), UiDump.W, UiDump.H, now)

    private fun clickOf(step: Step): Step.Click {
        assertTrue("se esperaba un clic y llegó $step", step is Step.Click)
        return step as Step.Click
    }

    /** 💿 en la fila; el segundo destino no está y la lupa no abre el buscador. Devuelve lo que pide hacer después. */
    private fun lupaThatDoesNotOpen(hints: QuickSender.Hints, message: String): List<Step> {
        val sheet = dumpWith("hoja", *german)
        val marked = dumpWith("hoja-marcado", *german, "\"Escribe un mensaje...\"" to "\"$message\"")
        sender.start(listOf(Target("💿"), Target("Lucía Gómez")), 0L, hints, false, PlatformProfile.TIKTOK)
        clickOf(tick(0, feed)); sender.onPerformed(true)
        assertEquals("💿", clickOf(tick(100, sheet, feed)).node.contentDescription)
        sender.onPerformed(true)
        assertEquals("lupa del buscador", clickOf(tick(200, marked, feed)).what)
        sender.onPerformed(true)
        val steps = mutableListOf<Step>()
        var now = 300L
        while (now < 6_000) {
            val step = tick(now, marked, feed)
            steps += step
            if (step is Step.Done) break
            if (step !is Step.Wait) sender.onPerformed(true)
            now += 100
        }
        return steps
    }

    private fun typed(steps: List<Step>) = steps.filterIsInstance<Step.SetText>().map { "«${it.text}» en «${it.node.text}» (${it.node.viewId})" }

    @Test
    fun `aleman con un id aprendido que hoy es el del mensaje no escribe en el mensaje`() {
        val steps = lupaThatDoesNotOpen(QuickSender.Hints(searchId = "com.zhiliaoapp.musically:id/pja"), "Schreib eine Nachricht...")
        assertTrue("escribió ${typed(steps)}", steps.none { it is Step.SetText })
    }

    @Test
    fun `aleman sin id aprendido la lupa que no abre no escribe en el mensaje`() {
        val steps = lupaThatDoesNotOpen(QuickSender.Hints(), "Schreib eine Nachricht...")
        assertTrue("escribió ${typed(steps)}", steps.none { it is Step.SetText })
    }

    /** Con el buscador ya abierto y alguien marcado, su campo del mensaje (`h84`) tampoco vale por el id aprendido: el buscador. */
    @Test
    fun `aleman con el buscador abierto y un id aprendido que hoy es el del mensaje escribe en el buscador`() {
        val message = "\"Escribe un mensaje...\"" to "\"Schreib eine Nachricht...\""
        val sheet = dumpWith("hoja", *german)
        val marked = dumpWith("hoja-marcado", *german, message)
        val search = dumpWith("buscador-marcado", *german, message)
        sender.start(listOf(Target("💿"), Target("Lucía Gómez")), 0L, QuickSender.Hints(searchId = "com.zhiliaoapp.musically:id/h84"), false, PlatformProfile.TIKTOK)
        clickOf(tick(0, feed)); sender.onPerformed(true)
        assertEquals("💿", clickOf(tick(100, sheet, feed)).node.contentDescription)
        sender.onPerformed(true)
        assertEquals("lupa del buscador", clickOf(tick(200, marked, feed)).what)
        sender.onPerformed(true)
        val steps = mutableListOf<Step>()
        var now = 300L
        while (now < 3_000) {
            val step = tick(now, search, feed)
            steps += step
            if (step is Step.Done || step is Step.SetText) break
            if (step !is Step.Wait) sender.onPerformed(true)
            now += 100
        }
        val typedIn = steps.filterIsInstance<Step.SetText>()
        assertTrue("no escribió: $steps", typedIn.isNotEmpty())
        assertTrue("escribió ${typed(steps)}", typedIn.all { shortViewId(it.node.viewId) == "vlq" })
        assertEquals("Lucía Gómez", typedIn.first().text)
    }

    @Test
    fun `espanol con un id aprendido que hoy es el del mensaje no escribe en el mensaje`() {
        val steps = lupaThatDoesNotOpen(QuickSender.Hints(searchId = "com.zhiliaoapp.musically:id/pja"), "Escribe un mensaje...")
        assertTrue("escribió ${typed(steps)}", steps.none { it is Step.SetText })
    }

    private companion object {
        const val FEED = 1
        const val SHEET = 2
    }
}
