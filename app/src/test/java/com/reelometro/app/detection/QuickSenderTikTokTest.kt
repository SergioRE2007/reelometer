package com.reelometro.app.detection

import com.reelometro.app.detection.QuickSender.Found
import com.reelometro.app.detection.QuickSender.Phase
import com.reelometro.app.detection.QuickSender.Step
import com.reelometro.app.detection.QuickSender.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Envío rápido en TikTok (1.10.0) con los árboles **reales** de TikTok 47.7 (`calibration/tiktok-47.7-*.xml`,
 * anonimizados): la hoja va en su propia ventana encima del feed, el nombre es la celda cuya descripción es el nombre,
 * el buscador se abre con la lupa de la cabecera y, tras «Enviar», la ventana de la hoja desaparece.
 */
class QuickSenderTikTokTest {

    private val finder = FakeFinder()
    private val sender = QuickSender(finder = finder)

    private val feed by lazy { UiDump.load("tiktok-47.7-feed.xml", windowId = FEED) }
    private val sheet by lazy { UiDump.load("tiktok-47.7-hoja.xml", windowId = SHEET) }
    private val sheetMarked by lazy { UiDump.load("tiktok-47.7-hoja-marcado.xml", windowId = SHEET) }
    private val sheetRecycled by lazy { UiDump.load("tiktok-47.7-hoja-celda-reciclada.xml", windowId = SHEET) }
    private val search by lazy { UiDump.load("tiktok-47.7-buscador.xml", windowId = SHEET) }
    private val searchMarked by lazy { UiDump.load("tiktok-47.7-buscador-marcado.xml", windowId = SHEET) }
    private val searchTyped by lazy { UiDump.load("tiktok-47.7-buscador-escrito.xml", windowId = SHEET) }

    /** La hoja con un cambio en su XML (otra persona con el mismo nombre, sin lupa…). */
    private fun sheetWith(vararg replace: Pair<String, String>): FakeNode = dumpWith("hoja", *replace)

    /** El volcado `tiktok-47.7-[name].xml` (en la ventana de la hoja) con cambios en su XML. */
    private fun dumpWith(name: String, vararg replace: Pair<String, String>): FakeNode {
        var xml = UiDump::class.java.classLoader!!.getResourceAsStream("calibration/tiktok-47.7-$name.xml")!!.use { it.readBytes().toString(Charsets.UTF_8) }
        replace.forEach { (from, to) ->
            assertTrue("el volcado $name tiene «$from»", xml.contains(from))
            xml = xml.replace(from, to)
        }
        return UiDump.parse(xml, windowId = SHEET)
    }

    /** TikTok en francés (sin comprobar en un móvil): el título, «Enviar», la lupa y las opciones, traducidos. */
    private val french = arrayOf(
        "\"Enviar a\"" to "\"Envoyer à\"",
        "\"Buscar\"" to "\"Rechercher\"",
    )

    /** Los textos de una fila (o celda) pulsada, en orden. */
    private fun textsOf(node: UiNode): List<String> = buildList {
        fun visit(n: UiNode) {
            n.text?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            n.children.forEach(::visit)
        }
        visit(node)
    }

    private fun start(vararg targets: Target, hints: QuickSender.Hints = QuickSender.Hints(), waitForClosedSheet: Boolean = false) =
        sender.start(targets.toList(), 0L, hints, waitForClosedSheet, PlatformProfile.TIKTOK)

    private fun tick(now: Long, vararg roots: UiNode): Step = sender.tick(roots.toList(), UiDump.W, UiDump.H, now)

    /** Un tick y las búsquedas que hizo (llamadas a la app). */
    private fun tickCounted(now: Long, vararg roots: UiNode): Pair<Step, Int> {
        val before = finder.calls.size
        val step = tick(now, *roots)
        return step to finder.calls.size - before
    }

    private fun clickOf(step: Step): Step.Click {
        assertTrue("se esperaba un clic y llegó $step", step is Step.Click)
        return step as Step.Click
    }

    private fun doneOf(step: Step): Step.Done {
        assertTrue("se esperaba el final y llegó $step", step is Step.Done)
        return step as Step.Done
    }

    /** Lo pulsado, con sus antepasados (para comprobar que no es, ni está dentro de, algo prohibido). */
    private fun lineage(node: UiNode): List<UiNode> = generateSequence(node) { it.parent }.toList()

    private fun assertNeverForbidden(click: Step.Click) {
        val labels = lineage(click.node).flatMap { listOfNotNull(it.text?.toString(), it.contentDescription?.toString()) }
        val forbidden = setOf("Enviar a", "Compartir", "WhatsApp", "Copiar enlace", "Status", "🥰", "👍", "😂", "Cerrar")
        assertTrue("pulsó algo prohibido: $labels", labels.none { it in forbidden })
        assertFalse("pulsó algo del visor que no es compartir", click.what != "compartir" && click.node.windowId == FEED)
    }

    // ── Por la fila de contactos ─────────────────────────────────────────────────────────────────

    @Test
    fun `primer envio por la fila con su celda, enviar y la hoja que se cierra`() {
        start(Target("💿"))
        val (share, q0) = tickCounted(0, feed)
        val shareClick = clickOf(share)
        assertEquals("compartir", shareClick.what)
        assertTrue(shareClick.node.contentDescription!!.startsWith("Compartir el vídeo"))
        assertEquals("solo el texto «Compartir»: sin otra ventana, «¿hay una hoja?» no cuesta nada", 1, q0)
        sender.onPerformed(true)

        val (name, q1) = tickCounted(100, sheet, feed)
        val cell = clickOf(name)
        assertEquals("nombre", cell.what)
        assertEquals("la celda (descrita con el nombre), no su texto", "💿", cell.node.contentDescription)
        assertTrue(cell.node.isClickable)
        assertEquals(SHEET, cell.node.windowId)
        assertEquals("el título y el nombre, cada uno en la ventana de la hoja", 2, q1)
        assertNeverForbidden(cell)
        sender.onPerformed(true)

        val (send, q2) = tickCounted(200, sheetMarked, feed)
        val sendClick = clickOf(send)
        assertEquals("enviar", sendClick.what)
        assertEquals("Enviar", sendClick.node.text)
        assertEquals(1, q2)
        sender.onPerformed(true)
        assertTrue(sender.sentProvisionally)
        assertEquals(listOf("💿"), sender.provisionalNames)

        val (done, q3) = tickCounted(300, feed)
        val end = doneOf(done)
        assertTrue(end.ok)
        assertEquals("enviado", end.reason)
        assertEquals("la ventana de la hoja ya no está: sin consultas", 0, q3)

        val report = sender.lastReport!!
        assertEquals(listOf(Phase.SHARE, Phase.SHEET, Phase.TARGET, Phase.SEND, Phase.SENT), report.phases.map { it.phase })
        assertEquals(0, report.walks)
        assertEquals(Found.GRID, report.foundIn)
        assertNull("en TikTok el id de compartir es el de «Me gusta»: no se aprende", report.shareId)
        assertEquals(4, finder.calls.size)
        assertTrue("las búsquedas por id, con un solo paquete", finder.calls.filter { it.startsWith("id:") }.all { it.startsWith("id:com.zhiliaoapp.musically:id/") })
        assertEquals(Found.GRID, sender.lastResults.single().foundIn)
    }

    @Test
    fun `el segundo envio con lo aprendido no hace mas consultas ni recorre`() {
        fun send(hints: QuickSender.Hints): Int {
            val from = finder.calls.size
            start(Target("💿"), hints = hints)
            clickOf(tick(0, feed)); sender.onPerformed(true)
            clickOf(tick(100, sheet, feed)); sender.onPerformed(true)
            clickOf(tick(200, sheetMarked, feed)); sender.onPerformed(true)
            assertTrue(doneOf(tick(300, feed)).ok)
            assertEquals(0, sender.lastReport!!.walks)
            return finder.calls.size - from
        }
        val first = send(QuickSender.Hints())
        val report = sender.lastReport!!
        val second = send(QuickSender.Hints(shareId = report.shareId, searchId = report.searchId))
        assertTrue("segundo: $second consultas, primero: $first", second <= first)
    }

    @Test
    fun `un id de compartir aprendido no se usa en TikTok`() {
        start(Target("💿"), hints = QuickSender.Hints(shareId = "com.zhiliaoapp.musically:id/g_d"))
        val click = clickOf(tick(0, feed))
        assertTrue(click.node.contentDescription!!.startsWith("Compartir el vídeo"))
        assertTrue(finder.calls.none { it.startsWith("id:") })
    }

    @Test
    fun `la foto del fijado es la de su celda`() {
        start(Target("💿", wantAvatar = true))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        val cell = clickOf(tick(100, sheet, feed))
        assertEquals(NodeBounds(912, 1566, 1038, 1692), cell.avatar)
        assertEquals("💿", cell.avatarName)
    }

    @Test
    fun `dos destinos en la misma hoja con un solo enviar`() {
        start(Target("💿"), Target("rubio_m"))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        assertEquals("💿", clickOf(tick(100, sheet, feed)).node.contentDescription); sender.onPerformed(true)
        // Marcado, 💿 pasa a la primera celda y las demás se corren.
        val second = clickOf(tick(200, sheetRecycled, feed))
        assertEquals("rubio_m", second.node.contentDescription)
        sender.onPerformed(true)
        assertEquals("enviar", clickOf(tick(300, sheetRecycled, feed)).what); sender.onPerformed(true)
        val done = doneOf(tick(400, feed))
        assertTrue(done.ok)
        val report = sender.lastReport!!
        assertEquals(2, report.sent)
        assertEquals(1, report.actions.count { it.what == "enviar" })
    }

    @Test
    fun `sumar un destino durante el envio lo marca en la misma hoja`() {
        start(Target("💿"))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        clickOf(tick(100, sheet, feed)); sender.onPerformed(true)
        assertTrue(sender.add(Target("nico_78x")))
        assertEquals("nico_78x", clickOf(tick(200, sheetRecycled, feed)).node.contentDescription); sender.onPerformed(true)
        assertEquals("enviar", clickOf(tick(300, sheetRecycled, feed)).what); sender.onPerformed(true)
        assertFalse("con «Enviar» pulsado ya no se suma", sender.add(Target("pablo_grc")))
        assertTrue(doneOf(tick(400, feed)).ok)
    }

    // ── Por el buscador ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `directo al buscador con la lupa, los recientes y tv_send`() {
        start(Target("rubio_m", skipGrid = true))
        clickOf(tick(0, feed)); sender.onPerformed(true)

        val lupa = clickOf(tick(100, sheet, feed))
        assertEquals("lupa del buscador", lupa.what)
        assertEquals("Buscar", lupa.node.contentDescription)
        assertEquals(SHEET, lupa.node.windowId)
        assertEquals(Phase.SEARCH, sender.currentPhase)
        sender.onPerformed(true)

        // Sin escribir: rubio_m ya sale en «Chats recientes».
        val (row, q) = tickCounted(200, search, feed)
        val rowClick = clickOf(row)
        assertEquals("nombre del buscador", rowClick.what)
        assertEquals(NodeBounds(0, 587, 1080, 765), rowClick.node.bounds)
        assertTrue(rowClick.node.isClickable)
        assertEquals("el campo (junto a la lupa) y el nombre", 2, q)
        sender.onPerformed(true)

        val send = clickOf(tick(300, searchMarked, feed))
        assertEquals("enviar", send.what)
        assertEquals("tv_send", shortViewId(send.node.viewId))
        sender.onPerformed(true)
        assertTrue(doneOf(tick(400, feed)).ok)

        val report = sender.lastReport!!
        assertEquals(Found.SEARCH, report.foundIn)
        assertTrue(report.skippedGrid)
        assertEquals(0, report.walks)
        assertEquals("el campo del buscador se aprende", "com.zhiliaoapp.musically:id/vlq", report.searchId)
        assertFalse(report.forgetSearchId)
        assertTrue(report.describe(), report.describe().contains("lupa"))
        assertEquals(listOf("directo, aprendido"), report.searches)
    }

    @Test
    fun `con el buscador aprendido el segundo envio no es mas lento`() {
        fun send(hints: QuickSender.Hints): Pair<Int, QuickSender.Report> {
            val from = finder.calls.size
            start(Target("rubio_m", skipGrid = true), hints = hints)
            clickOf(tick(0, feed)); sender.onPerformed(true)
            clickOf(tick(100, sheet, feed)); sender.onPerformed(true)
            clickOf(tick(200, search, feed)); sender.onPerformed(true)
            clickOf(tick(300, searchMarked, feed)); sender.onPerformed(true)
            assertTrue(doneOf(tick(400, feed)).ok)
            return finder.calls.size - from to sender.lastReport!!
        }
        val (first, report) = send(QuickSender.Hints())
        val (second, again) = send(QuickSender.Hints(searchId = report.searchId))
        assertTrue("segundo: $second consultas, primero: $first", second <= first)
        assertEquals(0, again.walks)
        assertEquals(report.searchId, again.searchId)
        assertFalse(again.forgetSearchId)
        assertTrue("el campo por su id aprendido", finder.calls.contains("id:com.zhiliaoapp.musically:id/vlq"))
    }

    @Test
    fun `un buscador aprendido que ya no sale se cambia por el de hoy`() {
        start(Target("rubio_m", skipGrid = true), hints = QuickSender.Hints(searchId = "com.zhiliaoapp.musically:id/zz9"))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        clickOf(tick(100, sheet, feed)); sender.onPerformed(true)
        clickOf(tick(200, search, feed)); sender.onPerformed(true)
        clickOf(tick(300, searchMarked, feed)); sender.onPerformed(true)
        assertTrue(doneOf(tick(400, feed)).ok)
        assertEquals("com.zhiliaoapp.musically:id/vlq", sender.lastReport!!.searchId)
    }

    @Test
    fun `sin el nombre en la fila ni en los recientes escribe y elige su fila`() {
        start(Target("ElRubencio Cuenta secundaria"))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        // La fila de contactos ya ha cargado sin él: la lupa en este mismo tick.
        val lupa = clickOf(tick(100, sheet, feed))
        assertEquals("lupa del buscador", lupa.what)
        sender.onPerformed(true)

        val typed = tick(200, search, feed)
        assertTrue("se esperaba escribir y llegó $typed", typed is Step.SetText)
        typed as Step.SetText
        assertEquals("ElRubencio Cuenta secundaria", typed.text)
        assertEquals("vlq", shortViewId(typed.node.viewId))
        assertEquals(Phase.RESULT, sender.currentPhase)
        sender.onPerformed(true)

        val row = clickOf(tick(300, searchTyped, feed))
        assertEquals(NodeBounds(0, 853, 1080, 1031), row.node.bounds)
        sender.onPerformed(true)
        clickOf(tick(400, searchMarked, feed)); sender.onPerformed(true)
        assertTrue(doneOf(tick(500, feed)).ok)
        val result = sender.lastResults.single()
        assertEquals("salió en el buscador tras ver la fila sin él: se aprende", Found.SEARCH, result.foundIn)
        assertEquals(listOf("fila sin él", "no está en los recientes"), sender.lastReport!!.searches)
    }

    @Test
    fun `en el buscador vale la persona exacta y no el grupo que la lleva`() {
        start(Target("rubio_m", skipGrid = true))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        clickOf(tick(100, sheet, feed)); sender.onPerformed(true)
        val row = clickOf(tick(200, searchTyped, feed))
        assertEquals("la fila de rubio_m, no la del grupo «Carlos, Bea, rubio_m»", NodeBounds(0, 497, 1080, 675), row.node.bounds)
    }

    @Test
    fun `un grupo nunca vale por llevar el nombre de una persona`() {
        start(Target("Carlos", skipGrid = true))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        clickOf(tick(100, sheet, feed)); sender.onPerformed(true)
        // En pantalla solo está el grupo «Carlos, Bea, rubio_m»: se escribe y, sin su fila, se rinde.
        assertTrue(tick(200, searchTyped, feed) is Step.SetText)
        sender.onPerformed(true)
        var now = 300L
        var step = tick(now, searchTyped, feed)
        while (step !is Step.Done) {
            assertFalse("no pulsa nada: $step", step is Step.Click)
            now += 400
            step = tick(now, searchTyped, feed)
        }
        val end = doneOf(step)
        assertFalse(end.ok)
        assertEquals("el buscador no lo encuentra", end.reason)
        assertTrue(end.sheetOpened)
    }

    @Test
    fun `en los recientes no vale el usuario de otra persona que se llama como el fijado`() {
        // jose_b tiene el usuario «paco»; el fijado «Paco» es otra persona (su nombre visible) que no sale en la lista.
        val recents = dumpWith("buscador", "text=\"\u200Ejoseb_2\"" to "text=\"\u200Epaco\"")
        start(Target("Paco", skipGrid = true))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        assertEquals("lupa del buscador", clickOf(tick(100, sheet, feed)).what); sender.onPerformed(true)
        val step = tick(200, recents, feed)
        assertFalse("pulsó la fila de otra persona: ${(step as? Step.Click)?.node?.let(::textsOf)}", step is Step.Click)
        assertTrue("no está en los recientes: se escribe su nombre ($step)", step is Step.SetText)
        assertEquals("Paco", (step as Step.SetText).text)
    }

    @Test
    fun `en los resultados vale el nombre visible y no otra cuenta con ese usuario`() {
        // Otra cuenta («ElRubencio…») tiene de usuario «rubio_m», el nombre visible del fijado: solo vale la fila del amigo.
        val results = dumpWith("buscador-escrito", "text=\"elrubencio2\"" to "text=\"rubio_m\"")
        start(Target("rubio_m", skipGrid = true))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        clickOf(tick(100, sheet, feed)); sender.onPerformed(true)
        val row = clickOf(tick(200, results, feed))
        assertEquals("nombre del buscador", row.what)
        assertEquals(NodeBounds(0, 497, 1080, 675), row.node.bounds)
        assertEquals(listOf("rubio_m", "· Amigos", "rubio_m72"), textsOf(row.node))
    }

    @Test
    fun `un grupo fijado que sale en los recientes se pulsa sin escribir`() {
        // TikTok pinta el grupo con marcas de aislamiento («Carlos, Bea, rubio_m»); el fijado se guardó sin ellas.
        start(Target("Carlos, Bea, rubio_m", skipGrid = true))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        clickOf(tick(100, sheet, feed)); sender.onPerformed(true)
        val row = clickOf(tick(200, searchTyped, feed))
        assertEquals("nombre del buscador", row.what)
        assertEquals("la fila del grupo, en el mismo tick y sin escribir", NodeBounds(0, 675, 1080, 853), row.node.bounds)
        assertTrue("se pide al atajo el primer miembro: ${finder.calls}", finder.calls.contains("texto:Carlos"))
    }

    @Test
    fun `un grupo fijado en la fila de contactos se pulsa con el atajo`() {
        val group = "\u2068Carlos\u2069, \u2068Bea\u2069"
        val withGroup = sheetWith("content-desc=\"nico_78x\"" to "content-desc=\"$group\"", "text=\"nico_78x\"" to "text=\"$group\"")
        start(Target("Carlos, Bea"))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        val cell = clickOf(tick(100, withGroup, feed))
        assertEquals("nombre", cell.what)
        assertEquals("la celda del grupo, no la lupa ni la celda de Carlos", group, cell.node.contentDescription)
    }

    @Test
    fun `en frances con resultados lentos el buscador abierto no es una hoja cerrada`() {
        val sheetFr = dumpWith("hoja", *french)
        val searchFr = dumpWith("buscador", *french)
        val typedFr = dumpWith("buscador-escrito", *french)
        start(Target("ElRubencio Cuenta secundaria"))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        assertEquals("lupa del buscador", clickOf(tick(100, sheetFr, feed)).what); sender.onPerformed(true)
        assertTrue(tick(200, searchFr, feed) is Step.SetText); sender.onPerformed(true)
        // Los resultados tardan: pasado el segundo de gracia entra el recorrido, que debe ver la hoja por su título.
        assertEquals(Step.Wait, tick(300, searchFr, feed))
        assertEquals(Step.Wait, tick(700, searchFr, feed))
        assertEquals("a los 1,1 s del SetText la hoja sigue abierta", Step.Wait, tick(1_300, searchFr, feed))
        val row = clickOf(tick(1_500, typedFr, feed))
        assertEquals("nombre del buscador", row.what)
        assertEquals(NodeBounds(0, 853, 1080, 1031), row.node.bounds)
    }

    @Test
    fun `en frances sin atajo el recorrido llega al buscador`() {
        val walker = QuickSender()
        walker.start(listOf(Target("ElRubencio Cuenta secundaria")), 0L, profile = PlatformProfile.TIKTOK)
        fun t(now: Long, vararg roots: UiNode) = walker.tick(roots.toList(), UiDump.W, UiDump.H, now)
        assertEquals("compartir", clickOf(t(0, feed)).what); walker.onPerformed(true)
        assertEquals("lupa del buscador", clickOf(t(100, dumpWith("hoja", *french), feed)).what); walker.onPerformed(true)
        val typed = t(200, dumpWith("buscador", *french), feed)
        assertTrue("se esperaba escribir y llegó $typed", typed is Step.SetText); walker.onPerformed(true)
        assertEquals(NodeBounds(0, 853, 1080, 1031), clickOf(t(300, dumpWith("buscador-escrito", *french), feed)).node.bounds)
    }

    /** Dos destinos: el primero no está en la fila y la lupa **no abre** el buscador (la hoja sigue igual). */
    private fun lupaThatDoesNotOpen(s: QuickSender): Pair<Step.Click, Long> {
        s.start(listOf(Target("ElRubencio Cuenta secundaria"), Target("💿")), 0L, profile = PlatformProfile.TIKTOK)
        assertEquals("compartir", clickOf(s.tick(listOf(feed), UiDump.W, UiDump.H, 0)).what); s.onPerformed(true)
        assertEquals("lupa del buscador", clickOf(s.tick(listOf(sheet, feed), UiDump.W, UiDump.H, 100)).what); s.onPerformed(true)
        var now = 200L
        while (now < 10_000) {
            when (val step = s.tick(listOf(sheet, feed), UiDump.W, UiDump.H, now)) {
                is Step.Click -> return step to now
                is Step.Done -> error("terminó sin pulsar 💿: ${step.reason}, ${s.lastResults.map { it.name + ": " + it.reason }}")
                is Step.SetText -> error("escribió «${step.text}» sin buscador")
                Step.Wait -> now += 100
            }
        }
        error("no termina")
    }

    @Test
    fun `si la lupa no abre el buscador el siguiente destino se busca en la fila`() {
        val (cell, at) = lupaThatDoesNotOpen(sender)
        assertEquals("nombre", cell.what)
        assertEquals("💿", cell.node.contentDescription)
        assertTrue("pasado el plazo del primero ($at ms)", at > 3_000)
        sender.onPerformed(true)
        assertEquals("enviar", clickOf(tick(at + 100, sheetMarked, feed)).what); sender.onPerformed(true)
        val done = doneOf(tick(at + 200, feed))
        assertTrue(done.ok)
        assertEquals(
            listOf("no se abre el buscador", "enviado"),
            sender.lastResults.map { it.reason },
        )
    }

    @Test
    fun `si la lupa no abre el buscador sin atajo tambien se vuelve a la fila`() {
        val (cell, _) = lupaThatDoesNotOpen(QuickSender())
        assertEquals("💿", cell.node.contentDescription)
    }

    @Test
    fun `con el buscador abierto sin su campo el siguiente destino sigue en el buscador`() {
        // La lupa sí abrió el buscador (ya no está tv_title), pero su campo no sale: el primero falla en la fase de la lupa
        // y el segundo se busca en los recientes, no en la fila de contactos.
        val noField = dumpWith("buscador", "android.widget.EditText" to "android.widget.TextView")
        start(Target("Nadie Aqui"), Target("rubio_m"))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        assertEquals("lupa del buscador", clickOf(tick(100, sheet, feed)).what); sender.onPerformed(true)
        var now = 200L
        var step = tick(now, noField, feed)
        while (step == Step.Wait && now < 10_000) {
            now += 100
            step = tick(now, noField, feed)
        }
        val row = clickOf(step)
        assertEquals("nombre del buscador", row.what)
        assertEquals(NodeBounds(0, 587, 1080, 765), row.node.bounds)
        assertTrue("pasado el plazo del primero ($now ms)", now > 3_000)
    }

    // ── Seguridad ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `un fijado que se llama como un emoji de reaccion nunca lo pulsa`() {
        start(Target("🥰"))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        // Hoja con alguien ya marcado: debajo salen los emojis de reacción (🥰 el primero).
        val lupa = clickOf(tick(100, sheetMarked, feed))
        assertEquals("lupa del buscador", lupa.what)
        assertNeverForbidden(lupa)
        sender.onPerformed(true)
        // En el buscador también están los emojis: no son filas.
        val typed = tick(200, searchMarked, feed)
        assertTrue("se esperaba escribir y llegó $typed", typed is Step.SetText)
        sender.onPerformed(true)
        var now = 300L
        var step = tick(now, searchMarked, feed)
        while (step !is Step.Done) {
            if (step is Step.Click) assertNeverForbidden(step)
            assertFalse("no pulsa nada: $step", step is Step.Click)
            now += 400
            step = tick(now, searchMarked, feed)
        }
        assertFalse(doneOf(step).ok)
    }

    @Test
    fun `un fijado que se llama como una opcion de la hoja nunca la pulsa`() {
        for (name in listOf("Compartir", "WhatsApp", "Status", "Copiar enlace", "Enviar a")) {
            start(Target(name))
            clickOf(tick(0, feed)); sender.onPerformed(true)
            val step = clickOf(tick(100, sheet, feed))
            assertEquals("$name: abre el buscador en vez de pulsar la opción", "lupa del buscador", step.what)
            assertNeverForbidden(step)
            sender.cancel()
        }
    }

    @Test
    fun `dos celdas con el mismo nombre no pulsa ninguna`() {
        val twins = sheetWith("content-desc=\"nico_78x\"" to "content-desc=\"Carlos\"")
        start(Target("Carlos"))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        val done = doneOf(tick(100, twins, feed))
        assertFalse(done.ok)
        assertEquals("nombre repetido en la hoja", done.reason)
        assertFalse(done.targetSelected)
    }

    @Test
    fun `la lupa del feed nunca, aunque la hoja no tenga la suya`() {
        // 1.10.1: sin etiqueta la lupa se encuentra por su sitio; sin la lupa, ni por su sitio (no es pulsable) ni por su nombre.
        val noLupa = sheetWith(
            "content-desc=\"Buscar\" checkable=\"false\" checked=\"false\" clickable=\"true\"" to
                "content-desc=\"\" checkable=\"false\" checked=\"false\" clickable=\"false\"",
        )
        start(Target("ElRubencio Cuenta secundaria"))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        var now = 100L
        var step = tick(now, noLupa, feed)
        while (step !is Step.Done) {
            assertFalse("no pulsa nada: $step", step is Step.Click)
            now += 400
            step = tick(now, noLupa, feed)
        }
        val end = doneOf(step)
        assertFalse(end.ok)
        assertEquals("no encuentro el buscador", end.reason)
    }

    @Test
    fun `con una hoja ya abierta no pulsa compartir`() {
        start(Target("💿"))
        val done = doneOf(tick(0, sheet, feed))
        assertFalse(done.ok)
        assertEquals("ya hay una hoja abierta", done.reason)
        assertFalse(done.sheetOpened)
    }

    @Test
    fun `con una hoja cerrandose espera y luego comparte`() {
        start(Target("💿"), waitForClosedSheet = true)
        assertEquals(Step.Wait, tick(0, sheet, feed))
        assertEquals("compartir", clickOf(tick(100, feed)).what)
    }

    @Test
    fun `la celda reciclada con selected no cuenta como marcada`() {
        // pablo_grc lleva selected=true sin CheckBox: no está marcado, hay que pulsarlo.
        start(Target("pablo_grc"))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        val cell = clickOf(tick(100, sheetRecycled, feed))
        assertEquals("pablo_grc", cell.node.contentDescription)
        sender.cancel()

        // 💿 sí lleva su CheckBox marcado: pulsarla la desmarcaría; se pasa directo a «Enviar».
        start(Target("💿"))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        val send = clickOf(tick(100, sheetRecycled, feed))
        assertEquals("enviar", send.what)
        assertEquals("Enviar", send.node.text)
    }

    @Test
    fun `si Android rechaza la lupa se vuelve a buscar en la hoja`() {
        start(Target("rubio_m", skipGrid = true))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        assertEquals("lupa del buscador", clickOf(tick(100, sheet, feed)).what)
        sender.onPerformed(false)
        assertEquals(Phase.TARGET, sender.currentPhase)
        assertEquals("lupa del buscador", clickOf(tick(200, sheet, feed)).what)
    }

    @Test
    fun `sin atajo tambien funciona recorriendo`() {
        val walker = QuickSender()
        walker.start(listOf(Target("💿")), 0L, profile = PlatformProfile.TIKTOK)
        val share = walker.tick(listOf(feed), UiDump.W, UiDump.H, 0)
        assertTrue(share is Step.Click && share.what == "compartir")
        walker.onPerformed(true)
        val cell = walker.tick(listOf(sheet, feed), UiDump.W, UiDump.H, 100)
        assertTrue("$cell", cell is Step.Click && cell.node.contentDescription == "💿")
        walker.onPerformed(true)
        val send = walker.tick(listOf(sheetMarked, feed), UiDump.W, UiDump.H, 200)
        assertTrue("$send", send is Step.Click && send.what == "enviar")
        walker.onPerformed(true)
        val done = walker.tick(listOf(feed), UiDump.W, UiDump.H, 300)
        assertTrue(done is Step.Done && done.ok)
        assertNotNull(walker.lastReport)
    }

    @Test
    fun `isSheetMarker por app`() {
        assertTrue(QuickSender.isSheetMarker("Enviar a", PlatformProfile.TIKTOK))
        assertFalse(QuickSender.isSheetMarker("Enviar a"))
        assertTrue(QuickSender.isSheetMarker("Copiar enlace"))
    }

    companion object {
        private const val FEED = 1
        private const val SHEET = 2
    }
}
