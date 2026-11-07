package com.reelometro.app.detection

import com.reelometro.app.detection.QuickSender.Found
import com.reelometro.app.detection.QuickSender.Step
import com.reelometro.app.detection.QuickSender.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.10.1, TikTok **en cualquier idioma** con los volcados reales de TikTok 47.7: todos los textos y descripciones de la hoja y
 * del buscador cambiados a un idioma inventado que no está en ninguna lista ([INVENTED]; los nombres de los contactos y los
 * emojis se quedan). La lupa y «Enviar» se encuentran por su sitio; se envía por la fila y por el buscador y nunca se pulsa
 * nada fuera de la lista blanca (compartir del visor, la celda o la fila del destino, la lupa de la cabecera, «Enviar»; solo
 * se escribe en el campo del buscador). El visor se queda en español: «Compartir» del visor es cosa de [ShareDetector].
 */
class QuickSenderTikTokCualquierIdiomaTest {

    private val finder = FakeFinder()
    private val sender = QuickSender(finder = finder)

    private val feed by lazy { UiDump.load("tiktok-47.7-feed.xml", windowId = FEED) }

    /** El volcado `tiktok-47.7-[name].xml` con cambios en su XML (cada uno debe estar). */
    private fun xmlOf(name: String, vararg replace: Pair<String, String>): String {
        var xml = UiDump::class.java.classLoader!!.getResourceAsStream("calibration/tiktok-47.7-$name.xml")!!.use { it.readBytes().toString(Charsets.UTF_8) }
        replace.forEach { (from, to) ->
            assertTrue("el volcado $name tiene «$from»", xml.contains(from))
            xml = xml.replace(from, to)
        }
        return xml
    }

    /** El volcado en el idioma inventado (y con [replace] antes de traducirlo), en la ventana de la hoja. */
    private fun invented(name: String, vararg replace: Pair<String, String>, check: Boolean = true): FakeNode {
        val xml = ATTR.replace(xmlOf(name, *replace)) { m -> INVENTED[m.groupValues[2]]?.let { "${m.groupValues[1]}=\"$it\"" } ?: m.value }
        return UiDump.parse(xml, windowId = SHEET).also { if (check) assertNoKnownWord(it) }
    }

    /** Ninguna etiqueta del volcado traducido está en las listas del perfil: de verdad es un idioma sin palabras. */
    private fun assertNoKnownWord(root: UiNode) {
        val p = PlatformProfile.TIKTOK
        val known = p.sendLabels + p.searchButtonLabels + p.titleLabels + p.sheetMarkers + p.sheetOptions
        val left = UiDump.all(root).flatMap { listOfNotNull(it.text, it.contentDescription) }.map { PlatformProfile.label(it.toString()) }.filter { it in known }
        assertTrue("quedan palabras conocidas: $left", left.isEmpty())
    }

    private fun start(vararg targets: Target, s: QuickSender = sender) = s.start(targets.toList(), 0L, QuickSender.Hints(), false, PlatformProfile.TIKTOK)

    private fun tick(now: Long, vararg roots: UiNode, s: QuickSender = sender): Step = s.tick(roots.toList(), UiDump.W, UiDump.H, now)

    private fun tickCounted(now: Long, vararg roots: UiNode): Pair<Step, Int> {
        val before = finder.calls.size
        return tick(now, *roots) to finder.calls.size - before
    }

    private fun clickOf(step: Step): Step.Click {
        assertTrue("se esperaba un clic y llegó $step", step is Step.Click)
        return step as Step.Click
    }

    private fun doneOf(step: Step): Step.Done {
        assertTrue("se esperaba el final y llegó $step", step is Step.Done)
        return step as Step.Done
    }

    /** La lista blanca: solo esto se pulsa o se escribe en un envío rápido de TikTok. */
    private fun assertWhitelisted(step: Step, target: String) {
        when (step) {
            is Step.Click -> {
                val n = step.node
                val id = shortViewId(n.viewId)
                val ok = when (step.what) {
                    // En el visor (lo que vale es de ShareDetector; recorriendo puede ser un nodo de dentro del botón).
                    "compartir" -> n.windowId == FEED
                    "nombre" -> n.windowId == SHEET && n.isClickable && n.contentDescription?.toString() == target &&
                        n.parent?.className?.contains("RecyclerView") == true
                    "lupa del buscador" -> n.windowId == SHEET && id == "mjh"
                    "nombre del buscador" -> n.windowId == SHEET && id == "f6n"
                    "enviar" -> n.windowId == SHEET && id in setOf("w1p", "tv_send")
                    else -> false
                }
                assertTrue("pulsó algo fuera de la lista blanca: ${step.what} · $id · ${n.text ?: n.contentDescription}", ok)
            }
            is Step.SetText -> assertEquals("escribió fuera del buscador", "vlq", shortViewId(step.node.viewId))
            else -> Unit
        }
    }

    /** Lo pedido en cada paso, comprobado contra la lista blanca, y el clic o la escritura que se esperaba. */
    private fun next(now: Long, target: String, vararg roots: UiNode, s: QuickSender = sender): Step =
        tick(now, *roots, s = s).also { step ->
            assertWhitelisted(step, target)
            if (step is Step.Click || step is Step.SetText) s.onPerformed(true)
        }

    // ── Por la fila ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `por la fila en un idioma sin palabras envia por el sitio del boton`() {
        start(Target("💿"))
        assertEquals("compartir", clickOf(next(0, "💿", feed)).what)
        assertEquals("💿", clickOf(next(100, "💿", invented("hoja"), feed)).node.contentDescription)
        val marked = invented("hoja-marcado")
        val (send, q) = tickCounted(200, marked, feed)
        assertWhitelisted(send, "💿")
        val click = clickOf(send)
        assertEquals("enviar", click.what)
        assertEquals("el botón de abajo (id ofuscado)", "w1p", shortViewId(click.node.viewId))
        assertEquals("«Enviar», «Send», el título (sin palabras que casen), tv_send y el sitio, sin recorrer", 4, q)
        sender.onPerformed(true)
        assertTrue(doneOf(tick(300, feed)).ok)
        assertEquals(0, sender.lastReport!!.walks)
        assertEquals(Found.GRID, sender.lastReport!!.foundIn)
    }

    @Test
    fun `por la fila sin atajo tambien envia por el sitio`() {
        val walker = QuickSender()
        start(Target("💿"), s = walker)
        assertEquals("compartir", clickOf(next(0, "💿", feed, s = walker)).what)
        assertEquals("nombre", clickOf(next(100, "💿", invented("hoja"), feed, s = walker)).what)
        assertEquals("w1p", shortViewId(clickOf(next(200, "💿", invented("hoja-marcado"), feed, s = walker)).node.viewId))
        assertTrue(doneOf(tick(300, feed, s = walker)).ok)
    }

    // ── Por el buscador ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `por el buscador en un idioma sin palabras con la lupa por su sitio y los recientes`() {
        start(Target("rubio_m", skipGrid = true))
        clickOf(next(0, "rubio_m", feed))
        val lupa = clickOf(next(100, "rubio_m", invented("hoja"), feed))
        assertEquals("lupa del buscador", lupa.what)
        assertEquals("Qeltrav", lupa.node.contentDescription)
        // El campo, por la etiqueta de la lupa pulsada (el buscador lleva la misma): sin esperar al recorrido.
        val row = clickOf(next(200, "rubio_m", invented("buscador"), feed))
        assertEquals("nombre del buscador", row.what)
        assertEquals(NodeBounds(0, 587, 1080, 765), row.node.bounds)
        assertTrue(finder.calls.contains("texto:Qeltrav"))
        assertEquals("tv_send", shortViewId(clickOf(next(300, "rubio_m", invented("buscador-marcado"), feed)).node.viewId))
        assertTrue(doneOf(tick(400, feed)).ok)
        val report = sender.lastReport!!
        assertEquals(0, report.walks)
        assertEquals(Found.SEARCH, report.foundIn)
        assertEquals("com.zhiliaoapp.musically:id/vlq", report.searchId)
    }

    @Test
    fun `por el buscador escribiendo el nombre en un idioma sin palabras`() {
        start(Target("ElRubencio Cuenta secundaria"))
        clickOf(next(0, "x", feed))
        assertEquals("lupa del buscador", clickOf(next(100, "x", invented("hoja"), feed)).what)
        val typed = next(200, "x", invented("buscador"), feed)
        assertTrue("se esperaba escribir y llegó $typed", typed is Step.SetText)
        assertEquals("ElRubencio Cuenta secundaria", (typed as Step.SetText).text)
        assertEquals(NodeBounds(0, 853, 1080, 1031), clickOf(next(300, "x", invented("buscador-escrito"), feed)).node.bounds)
        assertEquals("enviar", clickOf(next(400, "x", invented("buscador-marcado"), feed)).what)
        assertTrue(doneOf(tick(500, feed)).ok)
    }

    @Test
    fun `por el buscador sin atajo en un idioma sin palabras`() {
        val walker = QuickSender()
        start(Target("ElRubencio Cuenta secundaria"), s = walker)
        clickOf(next(0, "x", feed, s = walker))
        assertEquals("lupa del buscador", clickOf(next(100, "x", invented("hoja"), feed, s = walker)).what)
        // Sin marcas que se reconozcan, el buscador se reconoce como hoja de abajo (touch_outside) y no se da por cerrado.
        assertTrue(next(200, "x", invented("buscador"), feed, s = walker) is Step.SetText)
        assertEquals("nombre del buscador", clickOf(next(300, "x", invented("buscador-escrito"), feed, s = walker)).what)
        assertEquals("tv_send", shortViewId(clickOf(next(400, "x", invented("buscador-marcado"), feed, s = walker)).node.viewId))
        assertTrue(doneOf(tick(500, feed, s = walker)).ok)
    }

    // ── Seguridad ────────────────────────────────────────────────────────────────────────────────

    /** Tras marcar a 💿, pasa [marked] hasta el final; devuelve los pasos (comprobados contra la lista blanca). */
    private fun afterMarking(marked: FakeNode, s: QuickSender = sender): List<Step> {
        start(Target("💿"), s = s)
        clickOf(next(0, "💿", feed, s = s))
        assertEquals("nombre", clickOf(next(100, "💿", invented("hoja"), feed, s = s)).what)
        val steps = mutableListOf<Step>()
        var now = 200L
        while (now < 10_000) {
            val step = next(now, "💿", marked, feed, s = s)
            steps += step
            if (step is Step.Done) return steps
            now += 100
        }
        error("no termina: $steps")
    }

    @Test
    fun `dos botones en el sitio de enviar no pulsa ninguno`() {
        val twice = invented(
            "hoja-marcado",
            "bounds=\"[21,2180][1059,2296]\" drawing-order=\"1\" hint=\"\" />" to
                "bounds=\"[21,2180][1059,2296]\" drawing-order=\"1\" hint=\"\" />" +
                "<node index=\"1\" text=\"Vrumbel\" resource-id=\"\" class=\"android.widget.Button\" package=\"com.zhiliaoapp.musically\" " +
                "content-desc=\"\" checkable=\"false\" checked=\"false\" clickable=\"true\" enabled=\"true\" focusable=\"true\" " +
                "focused=\"false\" scrollable=\"false\" long-clickable=\"false\" password=\"false\" selected=\"false\" " +
                "bounds=\"[21,2300][1059,2350]\" drawing-order=\"2\" hint=\"\" />",
        )
        for (s in listOf(sender, QuickSender())) {
            val steps = afterMarking(twice, s)
            assertTrue("pulsó: $steps", steps.none { it is Step.Click })
            assertEquals("no encuentro enviar", doneOf(steps.last()).reason)
        }
    }

    @Test
    fun `sin boton pulsable debajo del mensaje nunca pulsa los emojis ni el mensaje`() {
        val noButton = invented(
            "hoja-marcado",
            "class=\"android.widget.Button\" package=\"com.zhiliaoapp.musically\" content-desc=\"\" checkable=\"false\" checked=\"false\" clickable=\"true\" enabled=\"true\" focusable=\"true\" focused=\"false\" scrollable=\"false\" long-clickable=\"false\" password=\"false\" selected=\"false\" bounds=\"[21,2180][1059,2296]\"" to
                "class=\"android.widget.Button\" package=\"com.zhiliaoapp.musically\" content-desc=\"\" checkable=\"false\" checked=\"false\" clickable=\"false\" enabled=\"true\" focusable=\"true\" focused=\"false\" scrollable=\"false\" long-clickable=\"false\" password=\"false\" selected=\"false\" bounds=\"[21,2180][1059,2296]\"",
        )
        for (s in listOf(sender, QuickSender())) {
            val steps = afterMarking(noButton, s)
            assertTrue("pulsó: $steps", steps.none { it is Step.Click || it is Step.SetText })
            assertFalse(doneOf(steps.last()).ok)
        }
    }

    @Test
    fun `sin nadie marcado no envia por el sitio`() {
        // Tras pulsar la celda la hoja sigue sin marcar (no hay CheckBox marcado ni campo del mensaje): no se pulsa nada.
        for (s in listOf(sender, QuickSender())) {
            val steps = afterMarking(invented("hoja"), s)
            assertTrue("pulsó: $steps", steps.none { it is Step.Click })
            assertEquals("no encuentro enviar", doneOf(steps.last()).reason)
        }
    }

    @Test
    fun `un contacto que se llama como enviar en su idioma nunca es el boton`() {
        // Turco (sin comprobar en un móvil): el título es la propia palabra «Gönder» y nico_78x se llama «Gönder».
        val turkish = UiDump.parse(
            xmlOf(
                "hoja-marcado",
                "text=\"Enviar a\"" to "text=\"Gönder\"",
                "text=\"Enviar\"" to "text=\"Gönder\"",
                "content-desc=\"nico_78x\"" to "content-desc=\"Gönder\"",
                "text=\"nico_78x\"" to "text=\"Gönder\"",
            ),
            windowId = SHEET,
        )
        start(Target("💿"))
        clickOf(next(0, "💿", feed))
        clickOf(next(100, "💿", invented("hoja"), feed))
        val send = clickOf(next(200, "💿", turkish, feed))
        assertEquals("enviar", send.what)
        assertEquals("w1p", shortViewId(send.node.viewId))
        assertTrue("la palabra del título se pide al atajo: ${finder.calls}", finder.calls.contains("texto:Gönder"))
    }

    @Test
    fun `la lupa por su sitio en una hoja de derecha a izquierda`() {
        // Árabe (sin comprobar en un móvil): la cabecera va al revés; la lupa (la primera) a la derecha y «Cerrar» a la izquierda.
        val arabic = UiDump.parse(
            xmlOf(
                "hoja",
                "text=\"Enviar a\"" to "text=\"إرسال إلى\"",
                "content-desc=\"Buscar\"" to "content-desc=\"Qeltrav\"",
                "content-desc=\"Cerrar\"" to "content-desc=\"Mupsen\"",
                "[16,1434][132,1550]" to "LUPA",
                "[948,1434][1064,1550]" to "[16,1434][132,1550]",
                "LUPA" to "[948,1434][1064,1550]",
            ),
            windowId = SHEET,
        )
        start(Target("ElRubencio Cuenta secundaria"))
        clickOf(next(0, "x", feed))
        val lupa = clickOf(next(100, "x", arabic, feed))
        assertEquals("lupa del buscador", lupa.what)
        assertEquals("mjh", shortViewId(lupa.node.viewId))
        assertEquals(NodeBounds(948, 1434, 1064, 1550), lupa.node.bounds)
    }

    @Test
    fun `con la cabecera al reves y el titulo de izquierda a derecha no pulsa nada`() {
        // El primer botón queda a la derecha (donde va «Cerrar») sin que el título lo explique: no está claro, nada.
        val swapped = invented("hoja", "[16,1434][132,1550]" to "LUPA", "[948,1434][1064,1550]" to "[16,1434][132,1550]", "LUPA" to "[948,1434][1064,1550]")
        for (s in listOf(sender, QuickSender())) {
            start(Target("ElRubencio Cuenta secundaria"), s = s)
            clickOf(next(0, "x", feed, s = s))
            var now = 100L
            var step = tick(now, swapped, feed, s = s)
            while (step !is Step.Done) {
                assertFalse("pulsó: $step", step is Step.Click)
                now += 200
                step = tick(now, swapped, feed, s = s)
            }
            assertEquals("no encuentro el buscador", doneOf(step).reason)
        }
    }

    // ── Revisión de la 1.10.1 ────────────────────────────────────────────────────────────────────

    @Test
    fun `un campo del mensaje cuya pista lleva la palabra de la lupa nunca es el buscador`() {
        // La lupa no abre el buscador y el campo del mensaje dice algo con «Qeltrav» (la palabra de la lupa en este idioma): el
        // atajo lo devuelve, ya es un campo y su pista no está en MESSAGE_HINTS. Sin la lupa al lado, nunca se escribe ahí.
        val marked = invented("hoja-marcado", "id/pja\" class=\"android.widget.EditText\" package=\"com.zhiliaoapp.musically\" content-desc=\"\"" to
            "id/pja\" class=\"android.widget.EditText\" package=\"com.zhiliaoapp.musically\" content-desc=\"Qeltrav yoskem\"")
        for (s in listOf(sender, QuickSender())) {
            start(Target("💿"), Target("ElRubencio Cuenta secundaria"), s = s)
            clickOf(next(0, "x", feed, s = s))
            assertEquals("💿", clickOf(next(100, "💿", invented("hoja"), feed, s = s)).node.contentDescription)
            assertEquals("lupa del buscador", clickOf(next(200, "x", marked, feed, s = s)).what)
            var now = 300L
            while (now < 8_000) {
                val step = tick(now, marked, feed, s = s)
                assertFalse("escribió en el mensaje: $step", step is Step.SetText)
                if (step is Step.Done) break
                if (step is Step.Click) s.onPerformed(true)
                now += 100
            }
        }
    }

    @Test
    fun `la palabra conocida de la lupa va antes que el sitio`() {
        // El primer botón del lado donde empieza la lectura dice «Cerrar»; la lupa («Buscar») va al otro lado.
        val sheet = UiDump.parse(
            xmlOf("hoja", "content-desc=\"Buscar\"" to "LUPA", "content-desc=\"Cerrar\"" to "content-desc=\"Buscar\"", "LUPA" to "content-desc=\"Cerrar\""),
            windowId = SHEET,
        )
        start(Target("ElRubencio Cuenta secundaria"))
        clickOf(next(0, "x", feed))
        val lupa = clickOf(tick(100, sheet, feed))
        assertEquals("lupa del buscador", lupa.what)
        assertEquals("Buscar", lupa.node.contentDescription)
    }

    @Test
    fun `el boton del sitio de la lupa que dice cerrar no se pulsa`() {
        val sheet = invented("hoja", "content-desc=\"Buscar\"" to "content-desc=\"Close\"", check = false)
        for (s in listOf(sender, QuickSender())) {
            start(Target("ElRubencio Cuenta secundaria"), s = s)
            clickOf(next(0, "x", feed, s = s))
            var now = 100L
            var step = tick(now, sheet, feed, s = s)
            while (step !is Step.Done) {
                assertFalse("pulsó: ${(step as? Step.Click)?.node?.contentDescription}", step is Step.Click)
                now += 200
                step = tick(now, sheet, feed, s = s)
            }
            assertEquals("no encuentro el buscador", doneOf(step).reason)
        }
    }

    @Test
    fun `un enviar por su sitio sin etiqueta no se da por enviado mientras siga ahi`() {
        val noLabel = invented("hoja-marcado", "text=\"Enviar\" resource-id=\"com.zhiliaoapp.musically:id/w1p\"" to "text=\"\" resource-id=\"com.zhiliaoapp.musically:id/w1p\"")
        start(Target("💿"))
        clickOf(next(0, "💿", feed))
        clickOf(next(100, "💿", invented("hoja"), feed))
        assertEquals("w1p", shortViewId(clickOf(next(200, "💿", noLabel, feed)).node.viewId))
        assertEquals("sigue en su sitio: sin confirmar", Step.Wait, tick(300, noLabel, feed))
        assertEquals("ni recorriendo", Step.Wait, tick(1_500, noLabel, feed))
        val end = doneOf(tick(2_400, noLabel, feed))
        assertFalse(end.ok)
        assertEquals("no se confirma el envío", end.reason)
        // Y si la hoja se cierra, enviado.
        start(Target("💿"))
        clickOf(next(0, "💿", feed))
        clickOf(next(100, "💿", invented("hoja"), feed))
        clickOf(next(200, "💿", noLabel, feed))
        assertTrue(doneOf(tick(300, feed)).ok)
    }

    // ── Las listas ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `las palabras se comparan normalizadas y se piden tal cual`() {
        val p = PlatformProfile.TIKTOK
        assertEquals(listOf("Enviar", "Send"), p.sendWords.take(2))
        assertEquals(listOf("Buscar", "Search"), p.searchButtonWords.take(2))
        assertTrue("wyslij" in p.sendLabels)
        assertTrue("gonder" in p.sendLabels)
        assertTrue(PlatformProfile.label("Отправить") in p.sendLabels)
        assertTrue(PlatformProfile.label("보내기") in p.sendLabels)
        assertTrue(listOf("ara", "cari", "suche", "tim kiem").all { it in p.searchButtonLabels })
        assertTrue(listOf("envoyer a", "senden an", "wyslij do", "gui đen").all { it in p.titleLabels })
        // Un título nunca es a la vez la palabra de «Enviar»: si lo fuera, el título sería el botón.
        assertTrue("títulos que son «Enviar»: ${p.titleLabels intersect p.sendLabels}", (p.titleLabels intersect p.sendLabels).isEmpty())
        assertTrue(listOf("gonder", PlatformProfile.label("Отправить"), PlatformProfile.label("보내기")).none { it in p.titleLabels })
        // Instagram no cambia: sin estas listas.
        assertTrue(PlatformProfile.INSTAGRAM.sendLabels.isEmpty())
        assertTrue(PlatformProfile.INSTAGRAM.searchButtonLabels.isEmpty())
        assertTrue(PlatformProfile.INSTAGRAM.titleLabels.isEmpty())
    }

    private companion object {
        const val FEED = 1
        const val SHEET = 2

        private val ATTR = Regex("""(text|content-desc)="([^"]*)"""")

        /** Un idioma inventado: ninguna de estas palabras está en las listas del perfil. */
        val INVENTED = mapOf(
            "Enviar a" to "Zorvat ki", "Enviar" to "Zorvat", "Buscar" to "Qeltrav", "Cerrar" to "Mupsen",
            "Módulo inferior" to "Blorf dunem", "Escribe un mensaje..." to "Plintar yoskem...", "Chats recientes" to "Kraddle vimps",
            "· Amigos" to "· Frunteks", "3 miembros" to "3 glossen", "Compartir" to "Repzik", "WhatsApp" to "Wobbleap",
            "Telegram" to "Tellgrom", "Status" to "Stoopa", "Promocionar" to "Prumvak", "No me interesa" to "Na mi vesk",
            "Facebook" to "Fesbak", "Dúo" to "Dwoop", "Descargar" to "Daskler", "Denunciar" to "Dennurk",
            "Copiar enlace" to "Kopsel lunk", "Añadir a Story" to "Anvip ta Storv",
        )
    }
}
