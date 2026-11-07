package com.reelometro.app.detection

import com.reelometro.app.detection.QuickSender.Step
import com.reelometro.app.detection.QuickSender.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.10.0, TikTok en otros idiomas y grupos (con los volcados reales): la lupa que no abre el buscador nunca hace escribir en el
 * campo del mensaje, el siguiente destino vuelve a la fila, y un grupo se busca y se escribe por su primer miembro sin pulsar
 * nunca a una persona que se llame como él.
 */
class QuickSenderTikTokIdiomasTest {

    private val finder = FakeFinder()
    private val sender = QuickSender(finder = finder)

    private val feed by lazy { UiDump.load("tiktok-47.7-feed.xml", windowId = FEED) }
    private val sheet by lazy { UiDump.load("tiktok-47.7-hoja.xml", windowId = SHEET) }
    private val search by lazy { UiDump.load("tiktok-47.7-buscador.xml", windowId = SHEET) }
    private val searchTyped by lazy { UiDump.load("tiktok-47.7-buscador-escrito.xml", windowId = SHEET) }

    private fun dumpWith(name: String, vararg replace: Pair<String, String>): FakeNode {
        var xml = UiDump::class.java.classLoader!!.getResourceAsStream("calibration/tiktok-47.7-$name.xml")!!.use { it.readBytes().toString(Charsets.UTF_8) }
        replace.forEach { (from, to) ->
            assertTrue("el volcado $name tiene «$from»", xml.contains(from))
            xml = xml.replace(from, to)
        }
        return UiDump.parse(xml, windowId = SHEET)
    }

    private val french = arrayOf("\"Enviar a\"" to "\"Envoyer à\"", "\"Buscar\"" to "\"Rechercher\"")

    private fun textsOf(node: UiNode): List<String> = buildList {
        fun visit(n: UiNode) {
            n.text?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            n.children.forEach(::visit)
        }
        visit(node)
    }

    private fun start(vararg targets: Target) = sender.start(targets.toList(), 0L, QuickSender.Hints(), false, PlatformProfile.TIKTOK)

    private fun tick(now: Long, vararg roots: UiNode): Step = sender.tick(roots.toList(), UiDump.W, UiDump.H, now)

    private fun clickOf(step: Step): Step.Click {
        assertTrue("se esperaba un clic y llegó $step", step is Step.Click)
        return step as Step.Click
    }

    @Test
    fun `un grupo cuyo primer miembro se llama como otra persona nunca pulsa a esa persona`() {
        // La fila de contactos tiene la celda «Carlos» (otra persona) y los recientes, la fila «Carlos» (carlos__567).
        start(Target("Carlos, Bea, rubio_m"))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        val lupa = clickOf(tick(100, sheet, feed))
        assertEquals("ni la celda de Carlos: la lupa", "lupa del buscador", lupa.what); sender.onPerformed(true)
        val typed = tick(200, search, feed)
        assertFalse("pulsó a otra persona: ${(typed as? Step.Click)?.node?.let(::textsOf)}", typed is Step.Click)
        assertTrue("se escribe ($typed)", typed is Step.SetText); sender.onPerformed(true)
        val row = clickOf(tick(300, searchTyped, feed))
        assertEquals(listOf("⁨Carlos⁩, ⁨Bea⁩, ⁨rubio_m⁩", "3 miembros"), textsOf(row.node))
    }

    @Test
    fun `una persona con el nombre del primer miembro de un grupo no pulsa el grupo`() {
        // Inverso: el fijado es «Carlos, Bea» no; es la persona «rubio_m» y el grupo «Carlos, Bea, rubio_m» lo lleva dentro.
        start(Target("rubio_m", skipGrid = true))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        clickOf(tick(100, sheet, feed)); sender.onPerformed(true)
        val row = clickOf(tick(200, searchTyped, feed))
        assertEquals(listOf("rubio_m", "· Amigos", "rubio_m72"), textsOf(row.node))
    }

    /**
     * Caso que el arreglo #5 no cubre: si el grupo no está en los recientes se escribe su nombre **entero**. TikTok saca los
     * grupos al escribir el nombre de un miembro («rub» → «Carlos, Bea, rubio_m», volcado real); con «Carlos, Bea, rubio_m»
     * no está medido. Escribir el primer miembro es seguro (la fila se sigue eligiendo por el nombre exacto del grupo).
     */
    @Test
    fun `un grupo que no esta en los recientes se busca por su primer miembro`() {
        start(Target("Carlos, Bea, rubio_m", skipGrid = true))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        clickOf(tick(100, sheet, feed)); sender.onPerformed(true)
        val typed = tick(200, search, feed)
        assertTrue("se escribe ($typed)", typed is Step.SetText)
        assertEquals("Carlos", (typed as Step.SetText).text)
    }

    /**
     * Fuera del español y el inglés, el campo «Escribe un mensaje…» no se reconoce ([QuickSender] MESSAGE_HINTS): si tras la
     * lupa el buscador no llega en el segundo de gracia (o no se abre: #6), el recorrido toma el campo del mensaje por el
     * buscador y escribe ahí el nombre del siguiente destino, que se enviaría como mensaje con el vídeo.
     */
    private fun lupaWithMarked(lang: Array<Pair<String, String>>, message: Array<Pair<String, String>> = arrayOf(), third: Target? = null): List<Step> {
        val sheetL = dumpWith("hoja", *lang)
        val markedL = dumpWith("hoja-marcado", *lang, *message)
        start(*listOfNotNull(Target("💿"), Target("ElRubencio Cuenta secundaria"), third).toTypedArray())
        clickOf(tick(0, feed)); sender.onPerformed(true)
        assertEquals("💿", clickOf(tick(100, sheetL, feed)).node.contentDescription); sender.onPerformed(true)
        assertEquals("lupa del buscador", clickOf(tick(200, markedL, feed)).what); sender.onPerformed(true)
        val steps = mutableListOf<Step>()
        var now = 300L
        while (now < 6_000) {
            val step = tick(now, markedL, feed)
            steps += step
            if (step is Step.Done) break
            if (step !is Step.Wait) sender.onPerformed(true)
            now += 100
        }
        return steps
    }

    @Test
    fun `control en espanol la lupa que no abre no escribe en el mensaje`() {
        val steps = lupaWithMarked(arrayOf())
        assertTrue("escribió: $steps", steps.none { it is Step.SetText })
    }

    @Test
    fun `control en espanol tras la lupa que no abre el tercero se pulsa en la fila`() {
        val steps = lupaWithMarked(arrayOf(), third = Target("rubio_m"))
        assertTrue("no pulsó rubio_m: $steps", steps.any { it is Step.Click && it.node.contentDescription == "rubio_m" })
    }

    @Test
    fun `en frances tras la lupa que no abre el tercero se pulsa en la fila`() {
        val steps = lupaWithMarked(french, arrayOf("Escribe un mensaje..." to "Écrire un message..."), third = Target("rubio_m"))
        val story = steps.filter { it != Step.Wait }.map { s ->
            when (s) {
                is Step.Click -> "clic:${s.what}:${s.node.contentDescription ?: s.node.text}"
                is Step.SetText -> "escribe «${s.text}» en «${s.node.text}»"
                else -> s.toString()
            }
        }
        assertTrue("no pulsó rubio_m: $story", steps.any { it is Step.Click && it.node.contentDescription == "rubio_m" })
    }

    @Test
    fun `en frances la lupa que no abre no escribe el nombre en el campo del mensaje`() {
        val steps = lupaWithMarked(french, arrayOf("Escribe un mensaje..." to "Écrire un message..."))
        val typed = steps.filterIsInstance<Step.SetText>()
        val story = steps.filter { it != Step.Wait }.map { s ->
            when (s) {
                is Step.Click -> "clic:${s.what}"
                is Step.SetText -> "escribe «${s.text}» en «${s.node.text}»"
                else -> s.toString()
            }
        }
        assertTrue("escribió en «${typed.firstOrNull()?.node?.text}»: $story; ${sender.lastResults.map { it.name + ": " + it.reason }}", typed.isEmpty())
    }

    private companion object {
        const val FEED = 1
        const val SHEET = 2
    }
}
