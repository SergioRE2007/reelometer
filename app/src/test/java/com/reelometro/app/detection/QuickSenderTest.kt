package com.reelometro.app.detection

import com.reelometro.app.detection.QuickSender.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Envío rápido con árboles con la forma de Instagram (2025-2026): el visor de Reels con su columna de
 * botones y, encima, la hoja de compartir (buscador, cuadrícula de personas y grupos, opciones y el
 * «Enviar» de abajo). También el diseño antiguo, con un «Enviar» en cada fila.
 */
class QuickSenderTest {

    private val sender = QuickSender()

    private fun tick(now: Long, vararg roots: FakeNode): Step = sender.tick(roots.toList(), SCREEN_W, SCREEN_H, now)

    private val shareButton = desc("Compartir", box(960, 1500, 1060, 1600), id = "com.instagram.android:id/direct_share_button")

    /** El visor de Reels: descripción abajo y la columna de botones a la derecha. */
    private fun reel(caption: String = "Un reel cualquiera", share: FakeNode = shareButton) = FakeNode(
        viewId = "com.instagram.android:id/clips_viewer_view_pager",
        bounds = box(0, 0, 1080, 2400),
        children = listOf(
            FakeNode(
                viewId = "com.instagram.android:id/clips_video_container",
                bounds = box(0, 0, 1080, 2400),
                children = listOf(
                    text(caption, box(40, 1900, 800, 1960)),
                    FakeNode(
                        viewId = "com.instagram.android:id/clips_ufi_component",
                        bounds = box(960, 1200, 1060, 1900),
                        children = listOf(
                            desc("Me gusta", box(960, 1200, 1060, 1300)),
                            desc("Comentar", box(960, 1350, 1060, 1450)),
                            share,
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun search(value: String = "Buscar") =
        FakeNode(className = "android.widget.EditText", text = value, viewId = QuickSender.KNOWN_SEARCH_ID, bounds = box(40, 860, 1040, 960))

    /** Celda de una hoja de otra forma (id que Instagram ya no usa): el atajo de la cuadrícula no la reconoce. */
    private val OLD_TILE = "com.instagram.android:id/direct_share_sheet_grid_item"

    private fun tile(name: String, left: Int = 40, top: Int = 1000, id: String = QuickSender.KNOWN_GRID_CELL_ID) = FakeNode(
        viewId = id,
        bounds = box(left, top, left + 240, top + 300),
        children = listOf(
            desc("Foto del perfil de $name", box(left + 40, top, left + 200, top + 160)),
            text(name, box(left, top + 180, left + 240, top + 230)),
        ),
    )

    private fun options() = FakeNode(
        bounds = box(0, 1950, 1080, 2150),
        children = listOf("Copiar enlace", "WhatsApp", "Añadir a la historia").mapIndexed { i, label ->
            FakeNode(bounds = box(40 + i * 260, 1950, 260 + i * 260, 2150), children = listOf(text(label, box(40 + i * 260, 2080, 260 + i * 260, 2130))))
        },
    )

    private fun sendButton() = FakeNode(
        className = "android.widget.Button",
        bounds = box(40, 2200, 1040, 2340),
        children = listOf(text("Enviar", box(400, 2240, 680, 2300))),
    )

    /** Hoja nueva: buscador, cuadrícula, opciones y, con alguien marcado, «Escribe un mensaje» y «Enviar». */
    private fun sheet(vararg names: String, selected: Boolean = false, field: String = "Buscar") = FakeNode(
        bounds = box(0, 800, 1080, 2400),
        children = buildList {
            add(search(field))
            add(FakeNode(bounds = box(0, 1000, 1080, 1900), children = names.mapIndexed { i, n -> tile(n, left = 40 + (i % 4) * 260, top = 1000 + (i / 4) * 320) }))
            add(options())
            if (selected) {
                add(FakeNode(className = "android.widget.EditText", text = "Escribe un mensaje", bounds = box(40, 2160, 1040, 2200)))
                add(sendButton())
            }
        },
    )

    private fun clickOf(step: Step): Step.Click {
        assertTrue("se esperaba un clic y llegó $step", step is Step.Click)
        return step as Step.Click
    }

    @Test
    fun `cuadricula con el grupo a la vista`() {
        sender.start("Los del piso", now = 0)

        val share = clickOf(tick(0, reel()))
        assertSame(shareButton, share.node)
        assertEquals("compartir", share.what)

        val open = sheet("Ana García", "Los del piso", "Marta")
        val name = clickOf(tick(300, open, reel()))
        assertEquals("nombre", name.what)
        assertEquals("Los del piso", name.node.text)

        val send = clickOf(tick(600, sheet("Ana García", "Los del piso", "Marta", selected = true), reel()))
        assertEquals("enviar", send.what)
        assertEquals("Enviar", send.node.text)

        assertEquals(Step.Done(true, "enviado", sheetOpened = true, targetSelected = true), tick(900))
        assertFalse(sender.isRunning)
    }

    @Test
    fun `el nombre se compara sin tildes ni mayusculas`() {
        sender.start("ana garcia", now = 0)
        clickOf(tick(0, reel()))
        assertEquals("Ana García", clickOf(tick(300, sheet("Marta", "Ana García"), reel())).node.text)
    }

    @Test
    fun `si no sale en la hoja se busca con el buscador`() {
        sender.start("Primos", now = 0)
        clickOf(tick(0, reel()))

        // Hoja abierta con la cuadrícula ya cargada (se ven fotos) y sin el grupo: escribe en el buscador ya.
        val open = sheet("Ana", "Marta")
        val typed = tick(250, open, reel())
        assertEquals(Step.SetText(open.children.first(), "Primos"), typed)

        // El buscador ya dice «Primos»: ese campo no cuenta, sí el resultado.
        assertEquals(Step.Wait, tick(1_100, sheet(field = "Primos"), reel()))
        val result = clickOf(tick(1_400, sheet("Primos", field = "Primos"), reel()))
        assertEquals("nombre del buscador", result.what)
        assertEquals("Primos", result.node.text)

        assertEquals("enviar", clickOf(tick(1_700, sheet("Primos", selected = true, field = "Primos"), reel())).what)
        assertTrue((tick(2_000) as Step.Done).ok)
    }

    @Test
    fun `diseno antiguo con un enviar en cada fila`() {
        fun row(name: String, top: Int) = FakeNode(
            bounds = box(0, top, 1080, top + 120),
            children = listOf(
                desc("Foto del perfil de $name", box(40, top + 10, 140, top + 110)),
                FakeNode(
                    bounds = box(160, top + 10, 800, top + 110),
                    children = listOf(text(name, box(160, top + 15, 800, top + 60)), text("Activo hace 5 min", box(160, top + 65, 800, top + 105))),
                ),
                FakeNode(className = "android.widget.Button", text = "Enviar", bounds = box(820, top + 20, 1040, top + 100)),
            ),
        )
        val list = FakeNode(
            bounds = box(0, 800, 1080, 2400),
            children = listOf(search(), FakeNode(bounds = box(0, 1000, 1080, 1600), children = listOf(row("Ana", 1000), row("Primos", 1120), row("Marta", 1240)))),
        )
        sender.start("Primos", now = 0)
        clickOf(tick(0, reel()))

        val send = clickOf(tick(300, list, reel()))
        assertEquals("enviar de la fila", send.what)
        assertEquals(box(820, 1140, 1040, 1220), send.node.bounds) // el de la fila de Primos, no otro

        assertEquals(Step.Done(true, "enviado", sheetOpened = true, targetSelected = true), tick(600))
    }

    @Test
    fun `nunca pulsa el enviar de la fila de otra persona`() {
        sender.start("Primos", now = 0)
        clickOf(tick(0, reel()))
        clickOf(tick(300, sheet("Primos"), reel()))
        // Tras pulsar el nombre, la única fila con «Enviar» es la de Ana: no vale.
        val anaRow = FakeNode(
            bounds = box(0, 1000, 1080, 1120),
            children = listOf(text("Ana", box(160, 1015, 800, 1060)), FakeNode(className = "android.widget.Button", text = "Enviar", bounds = box(820, 1020, 1040, 1100))),
        )
        val other = FakeNode(bounds = box(0, 800, 1080, 2400), children = listOf(search(), anaRow))
        assertEquals(Step.Wait, tick(600, other, reel()))
        assertEquals(Step.Done(false, "no encuentro enviar", sheetOpened = true, targetSelected = true), tick(3_700, other, reel()))
    }

    @Test
    fun `sin boton compartir se rinde a los tres segundos`() {
        sender.start("Primos", now = 0)
        val noShare = reel(share = desc("Guardar", box(960, 1500, 1060, 1600)))
        assertEquals(Step.Wait, tick(0, noShare))
        assertEquals(Step.Wait, tick(2_900, noShare))
        assertEquals(Step.Done(false, "no encuentro compartir", sheetOpened = false, targetSelected = false), tick(3_100, noShare))
    }

    @Test
    fun `la hoja que no se abre y el nombre que no aparece`() {
        sender.start("Primos", now = 0)
        clickOf(tick(0, reel()))
        assertEquals(Step.Wait, tick(1_000, reel()))
        assertEquals(Step.Done(false, "la hoja de compartir no se abre", sheetOpened = false, targetSelected = false), tick(3_100, reel()))

        // El buscador escribe, pero no sale nada: se para y deja la hoja abierta.
        sender.start("Primos", now = 10_000)
        clickOf(tick(10_000, reel()))
        assertTrue(tick(10_200, sheet("Ana"), reel()) is Step.SetText)
        assertEquals(Step.Wait, tick(12_000, sheet("Ana", field = "Primos"), reel()))
        assertEquals(
            Step.Done(false, "el buscador no lo encuentra", sheetOpened = true, targetSelected = false),
            tick(14_000, sheet("Ana", field = "Primos"), reel()),
        )
    }

    @Test
    fun `no toca nada del visor aunque diga el nombre o enviar`() {
        // El visor dice «Primos» en la descripción y la hoja está en la misma ventana, sin buscador a mano.
        val viewer = reel(caption = "Primos")
        val noField = FakeNode(bounds = box(0, 0, 1080, 2400), children = listOf(viewer, options()))
        sender.start("Primos", now = 0)
        clickOf(tick(0, reel(caption = "Primos")))
        assertEquals(Step.Wait, tick(300, noField))
        assertEquals(Step.Wait, tick(1_000, noField))
        assertEquals(
            Step.Done(false, "no encuentro el nombre", sheetOpened = true, targetSelected = false),
            tick(3_400, noField),
        )

        // El avión de papel del visor se llama «Enviar»: no es el «Enviar» de la hoja.
        val paperPlane = desc("Enviar", box(960, 1500, 1060, 1600), id = "com.instagram.android:id/direct_share_button")
        sender.start("Primos", now = 5_000)
        assertSame(paperPlane, clickOf(tick(5_000, reel(share = paperPlane))).node)
        val same = FakeNode(bounds = box(0, 0, 1080, 2400), children = listOf(reel(share = paperPlane), sheet("Primos")))
        clickOf(tick(5_300, same))
        assertEquals(Step.Wait, tick(5_600, same))
    }

    @Test
    fun `la hoja que se cierra a medias para el envio`() {
        sender.start("Primos", now = 0)
        clickOf(tick(0, reel()))
        assertTrue(tick(200, sheet("Ana"), reel()) is Step.SetText)
        assertEquals(Step.Done(false, "la hoja se ha cerrado", sheetOpened = true, targetSelected = false), tick(400, reel()))
    }

    @Test
    fun `sin envio en curso no hace nada`() {
        val done = tick(0, reel()) as Step.Done
        assertFalse(done.ok)
        sender.start("Primos", now = 0)
        sender.cancel()
        assertFalse(sender.isRunning)
    }
    @Test
    fun `el enviar de abajo vale aunque comparta barra con el mensaje y los iconos`() {
        sender.start("Primos", now = 0)
        clickOf(tick(0, reel()))
        clickOf(tick(300, sheet("Primos"), reel()))
        val send = sendButton()
        val composer = FakeNode(
            bounds = box(0, 2060, 1080, 2340),
            children = listOf(
                FakeNode(className = "android.widget.EditText", text = "Escribe un mensaje…", bounds = box(40, 2070, 900, 2180)),
                desc("Cámara", box(920, 2070, 1040, 2180)),
                send,
            ),
        )
        val open = FakeNode(bounds = box(0, 800, 1080, 2400), children = listOf(search(), FakeNode(bounds = box(0, 1000, 1080, 1900), children = listOf(tile("Primos"))), composer))
        val click = clickOf(tick(600, open, reel()))
        assertEquals("enviar", click.what)
        assertTrue(click.node === send.children.first())
    }

    @Test
    fun `si android rechaza el clic se reintenta y no da el envio por hecho`() {
        sender.start("Primos", now = 0)
        val share = clickOf(tick(0, reel()))
        assertEquals((SCREEN_W * 0.35f).toInt(), share.maxClimbWidth) // el clic no sube hasta el vídeo
        sender.onPerformed(true)
        clickOf(tick(300, sheet("Primos"), reel()))
        sender.onPerformed(true)
        val selected = sheet("Primos", selected = true)
        assertEquals("enviar", clickOf(tick(600, selected, reel())).what)
        sender.onPerformed(false) // nodo caducado: vuelve a buscar «Enviar»
        assertEquals("enviar", clickOf(tick(850, selected, reel())).what)
        sender.onPerformed(true)
        // La hoja sigue igual, con el mismo «Enviar»: aún no se ha enviado.
        assertEquals(Step.Wait, tick(1_100, selected, reel()))
        assertEquals(
            Step.Done(false, "no se confirma el envío", sheetOpened = true, targetSelected = true),
            tick(3_000, selected, reel()),
        )
    }

    @Test
    fun `se confirma con deshacer en la fila del diseno antiguo`() {
        fun list(label: String) = FakeNode(
            bounds = box(0, 800, 1080, 2400),
            children = listOf(
                search(),
                FakeNode(
                    bounds = box(0, 1000, 1080, 1120),
                    children = listOf(text("Primos", box(160, 1015, 800, 1060)), FakeNode(className = "android.widget.Button", text = label, bounds = box(820, 1020, 1040, 1100))),
                ),
            ),
        )
        sender.start("Primos", now = 0)
        clickOf(tick(0, reel()))
        assertEquals("enviar de la fila", clickOf(tick(300, list("Enviar"), reel())).what)
        sender.onPerformed(true)
        assertEquals(Step.Wait, tick(550, list("Enviar"), reel()))
        assertEquals(Step.Done(true, "enviado", sheetOpened = true, targetSelected = true), tick(800, list("Deshacer"), reel()))
    }

    @Test
    fun `con dos que se llaman igual no pulsa ninguno`() {
        sender.start("Ana", now = 0)
        clickOf(tick(0, reel()))
        assertEquals(
            Step.Done(false, "nombre repetido en la hoja", sheetOpened = true, targetSelected = false),
            tick(300, sheet("Ana", "Marta", "Ana"), reel()),
        )
    }

    @Test
    fun `nunca escribe el nombre en el campo del mensaje`() {
        sender.start("Primos", now = 0)
        clickOf(tick(0, reel()))
        val onlyMessage = FakeNode(
            bounds = box(0, 800, 1080, 2400),
            children = listOf(FakeNode(className = "android.widget.EditText", text = "Escribe un mensaje…", bounds = box(40, 860, 1040, 960)), options()),
        )
        assertEquals(Step.Wait, tick(300, onlyMessage, reel()))
        assertEquals(Step.Wait, tick(1_000, onlyMessage, reel()))
        assertTrue((tick(3_400, onlyMessage, reel()) as Step.Done).reason == "no encuentro el nombre")

        // «Buscar…» con puntos suspensivos sí es el buscador.
        sender.start("Primos", now = 10_000)
        clickOf(tick(10_000, reel()))
        assertTrue(tick(10_200, sheet("Ana", field = "Buscar…"), reel()) is Step.SetText)
    }

    @Test
    fun `el nombre y el usuario iguales en la misma fila no son un repetido`() {
        val row = FakeNode(
            bounds = box(0, 1000, 1080, 1120),
            children = listOf(
                desc("Foto del perfil de marta", box(40, 1010, 140, 1110)),
                FakeNode(
                    bounds = box(160, 1010, 800, 1110),
                    children = listOf(text("Marta", box(160, 1010, 800, 1060)), text("marta", box(160, 1060, 800, 1110))),
                ),
            ),
        )
        val results = FakeNode(bounds = box(0, 800, 1080, 2400), children = listOf(search("Marta"), row))
        sender.start("Marta", now = 0)
        clickOf(tick(0, reel()))
        val click = clickOf(tick(300, results, reel()))
        assertEquals("nombre", click.what)
        assertEquals("Marta", click.node.text)
    }

    @Test
    fun `con una hoja ya abierta no empieza`() {
        sender.start("Primos", now = 0)
        assertEquals(
            Step.Done(false, "ya hay una hoja abierta", sheetOpened = false, targetSelected = false),
            tick(0, sheet("Ana"), reel()),
        )
    }

    // ── 1.4.0: más rápido ────────────────────────────────────────────────────────────────────────

    private fun fastSender(finder: FakeFinder = FakeFinder()) = QuickSender(finder = finder)

    private fun QuickSender.tickWith(now: Long, vararg roots: FakeNode): Step = tick(roots.toList(), SCREEN_W, SCREEN_H, now)

    @Test
    fun `sin cuadricula a la vista espera un poco antes de usar el buscador`() {
        sender.start("Primos", now = 0)
        clickOf(tick(0, reel()))
        // Solo el buscador y las opciones, sin fotos de perfil: la cuadrícula aún no ha cargado.
        val field = search()
        val noGrid = FakeNode(bounds = box(0, 800, 1080, 2400), children = listOf(field, options()))
        assertEquals(Step.Wait, tick(250, noGrid, reel()))
        assertEquals(Step.SetText(field, "Primos"), tick(900, noGrid, reel()))
    }

    @Test
    fun `buscador sin espera en el mismo tick en que se abre la hoja con la cuadricula cargada`() {
        sender.start("Primos", now = 0)
        clickOf(tick(0, reel()))
        val open = sheet("Ana", "Marta", "Los del piso")
        val typed = tick(120, open, reel())
        assertEquals(Step.SetText(open.children.first(), "Primos"), typed)
        assertEquals(QuickSender.Phase.RESULT, sender.currentPhase)
    }

    @Test
    fun `el atajo por texto encuentra el nombre sin recorrer el arbol`() {
        val finder = FakeFinder()
        val fast = fastSender(finder)
        fast.start("Los del piso", now = 0)

        val share = clickOf(fast.tickWith(0, reel()))
        assertSame(shareButton, share.node)
        val name = clickOf(fast.tickWith(300, sheet("Ana García", "Los del piso", "Marta"), reel()))
        assertEquals("nombre", name.what)
        assertEquals("Los del piso", name.node.text)
        assertTrue("texto:Los del piso" in finder.calls)
        val send = clickOf(fast.tickWith(500, sheet("Ana García", "Los del piso", "Marta", selected = true), reel()))
        assertEquals("enviar", send.what)
        assertEquals(Step.Done(true, "enviado", sheetOpened = true, targetSelected = true), fast.tickWith(700, reel()))

        val report = fast.lastReport!!
        // Un solo recorrido entero: el de comprobar, antes de pulsar compartir, que no hay ya una hoja abierta.
        assertEquals(1, report.walks)
        assertEquals(4, report.fastHits)
        assertEquals(QuickSender.Found.GRID, report.foundIn)
        assertEquals("com.instagram.android:id/direct_share_button", report.shareId)
        assertEquals(
            listOf(QuickSender.Phase.SHARE, QuickSender.Phase.SHEET, QuickSender.Phase.TARGET, QuickSender.Phase.SEND, QuickSender.Phase.SENT),
            report.phases.map { it.phase },
        )
        assertEquals(listOf(0L, 300L, 0L, 200L, 200L), report.phases.map { it.ms })
        // Cada fase se decidió con el atajo.
        assertTrue(report.phases.all { it.how == QuickSender.How.FAST })
        assertEquals(700L, report.totalMs)
        assertEquals(
            "compartir 0 ms (atajo) · hoja 300 ms (atajo) · nombre 0 ms (atajo) · enviar 200 ms (atajo) · confirmar 200 ms (atajo) · " +
                "total 700 ms · atajos 4 · recorridos 1 · reloj: compartir +0 ms · nombre +300 ms · enviar +500 ms",
            report.describe(),
        )
    }

    @Test
    fun `si el atajo no lo ve recorre el arbol pero no mientras la hoja se abre`() {
        // Guardado sin tilde y en la hoja con tilde: la búsqueda de Instagram no lo encuentra. Con la cuadrícula de
        // hoy a la vista (sus celdas), al buscador sin recorrer.
        val fast = fastSender()
        fast.start("Ana Garcia", now = 0)
        clickOf(fast.tickWith(0, reel()))
        assertTrue(fast.tickWith(300, sheet("Marta", "Ana García"), reel()) is Step.SetText)
        fast.cancel()

        // En una hoja de otra forma (ids desconocidos), el recorrido lo encuentra, pero pasado walkGraceMs desde que
        // empieza la fase (1.4.2: recorrer la hoja mientras se anima costó más de 1 s medido).
        val other = FakeNode(
            bounds = box(0, 800, 1080, 2400),
            children = listOf(
                FakeNode(className = "android.widget.EditText", text = "Buscar", bounds = box(40, 860, 1040, 960)),
                FakeNode(bounds = box(0, 1000, 1080, 1900), children = listOf(tile("Marta", id = OLD_TILE), tile("Ana García", left = 300, id = OLD_TILE))),
                options(),
            ),
        )
        val walker = fastSender()
        walker.start("Ana Garcia", now = 0)
        clickOf(walker.tickWith(0, reel()))
        assertEquals(Step.Wait, walker.tickWith(300, other, reel())) // hoja vista por «Copiar enlace»; aún no recorre
        assertEquals(Step.Wait, walker.tickWith(1_200, other, reel()))
        val name = clickOf(walker.tickWith(1_300, other, reel()))
        assertEquals("Ana García", name.node.text)
        walker.cancel()
    }

    @Test
    fun `nada del visor cuenta aunque el atajo lo devuelva`() {
        val fast = fastSender()
        val viewer = reel(caption = "Primos")
        val noField = FakeNode(bounds = box(0, 0, 1080, 2400), children = listOf(viewer, options()))
        fast.start("Primos", now = 0)
        clickOf(fast.tickWith(0, reel(caption = "Primos")))
        assertEquals(Step.Wait, fast.tickWith(300, noField))
        assertEquals(Step.Wait, fast.tickWith(1_000, noField))
        assertEquals(Step.Done(false, "no encuentro el nombre", sheetOpened = true, targetSelected = false), fast.tickWith(3_400, noField))
    }

    @Test
    fun `ids aprendidos se prueban primero y el fijado que no sale va directo al buscador`() {
        val shareId = "com.instagram.android:id/direct_share_button"
        val searchId = "com.instagram.android:id/search_edit_text"
        val field = FakeNode(className = "android.widget.EditText", text = "Buscar", viewId = searchId, bounds = box(40, 860, 1040, 960))
        val open = FakeNode(
            bounds = box(0, 800, 1080, 2400),
            children = listOf(field, FakeNode(bounds = box(0, 1000, 1080, 1900), children = listOf(tile("Ana"), tile("Marta", left = 300))), options()),
        )

        // Primera vez: aprende el id de compartir y el del buscador (no estaba en la cuadrícula).
        val first = fastSender()
        first.start("Primos", now = 0)
        clickOf(first.tickWith(0, reel()))
        assertEquals(Step.SetText(field, "Primos"), first.tickWith(300, open, reel()))
        val results = FakeNode(bounds = box(0, 800, 1080, 2400), children = listOf(field, tile("Primos")))
        clickOf(first.tickWith(600, results, reel()))
        first.cancel()
        assertEquals(null, first.lastReport) // cancelado: no hay informe

        val finished = fastSender()
        finished.start("Primos", now = 0)
        clickOf(finished.tickWith(0, reel()))
        finished.tickWith(300, open, reel())
        clickOf(finished.tickWith(600, results, reel()))
        assertEquals("enviar", clickOf(finished.tickWith(900, sheet("Primos", selected = true), reel())).what)
        assertTrue((finished.tickWith(1_200, reel()) as Step.Done).ok)
        val report = finished.lastReport!!
        assertEquals(shareId, report.shareId)
        assertEquals(searchId, report.searchId)
        assertEquals(QuickSender.Found.SEARCH, report.foundIn)
        assertEquals(1, QuickSender.nextGridMisses(0, report))
        assertEquals(2, QuickSender.nextGridMisses(1, report))
        assertTrue(QuickSender.skipGrid(QuickSender.nextGridMisses(1, report)))

        // Con lo aprendido: el id de compartir va antes que el texto y, con la hoja abierta, directo al buscador.
        val finder = FakeFinder()
        val learned = fastSender(finder)
        learned.start("Primos", now = 0, hints = QuickSender.Hints(shareId = shareId, searchId = searchId, skipGrid = true))
        clickOf(learned.tickWith(0, reel()))
        // Primero las opciones de la hoja (¿ya hay una abierta?) y, para compartir, el id antes que el texto.
        assertEquals("id:$shareId", finder.calls.first { it == "id:$shareId" || it == "texto:Compartir" })
        assertEquals(Step.SetText(field, "Primos"), learned.tickWith(150, open, reel()))
        clickOf(learned.tickWith(400, results, reel()))
        learned.cancel()

        // Si al final aparece en la cuadrícula, la racha se rompe.
        val inGrid = report.copy(foundIn = QuickSender.Found.GRID)
        assertEquals(0, QuickSender.nextGridMisses(5, inGrid))
        // Tras ir directo al buscador baja uno: un envío de cada dos vuelve a probar la cuadrícula.
        assertEquals(1, QuickSender.nextGridMisses(2, report.copy(skippedGrid = true)))
        assertFalse(QuickSender.skipGrid(1))
        assertEquals(3, QuickSender.nextGridMisses(3, report.copy(foundIn = null)))
    }

    @Test
    fun `enviado antes de confirmar y pasa a fallo si no se confirma`() {
        val fast = fastSender()
        fast.start("Primos", now = 0)
        clickOf(fast.tickWith(0, reel()))
        fast.onPerformed(true)
        clickOf(fast.tickWith(300, sheet("Primos"), reel()))
        fast.onPerformed(true)
        assertFalse(fast.sentProvisionally) // pulsar el nombre no es enviar
        val selected = sheet("Primos", selected = true)
        assertEquals("enviar", clickOf(fast.tickWith(500, selected, reel())).what)
        fast.onPerformed(true)
        // Android aceptó el clic en «Enviar»: la burbuja ya puede decir «enviado».
        assertTrue(fast.sentProvisionally)
        assertTrue(fast.isRunning)
        // Pero la hoja no cambia: sigue confirmando por detrás y, al final, es un fallo.
        assertEquals(Step.Wait, fast.tickWith(800, selected, reel()))
        assertEquals(Step.Wait, fast.tickWith(2_400, selected, reel()))
        assertEquals(
            Step.Done(false, "no se confirma el envío", sheetOpened = true, targetSelected = true),
            fast.tickWith(2_600, selected, reel()),
        )
        assertTrue(fast.lastReport!!.provisional)
        assertFalse(fast.lastReport!!.ok)
    }

    @Test
    fun `si android rechaza el enviar no hay enviado provisional`() {
        val fast = fastSender()
        fast.start("Primos", now = 0)
        clickOf(fast.tickWith(0, reel()))
        clickOf(fast.tickWith(300, sheet("Primos"), reel()))
        val selected = sheet("Primos", selected = true)
        clickOf(fast.tickWith(500, selected, reel()))
        fast.onPerformed(false)
        assertFalse(fast.sentProvisionally)
        assertEquals(QuickSender.Phase.SEND, fast.currentPhase)
        clickOf(fast.tickWith(700, selected, reel()))
        fast.onPerformed(true)
        assertTrue(fast.sentProvisionally)
        assertTrue((fast.tickWith(900, reel()) as Step.Done).ok)
        // Los tiempos no cuentan el intento rechazado dos veces.
        assertEquals(1, fast.lastReport!!.phases.count { it.phase == QuickSender.Phase.SENT })
    }

    @Test
    fun `con el atajo una hoja ya abierta tampoco empieza`() {
        val fast = fastSender()
        fast.start("Primos", now = 0)
        assertEquals(
            Step.Done(false, "ya hay una hoja abierta", sheetOpened = false, targetSelected = false),
            fast.tickWith(0, sheet("Ana"), reel()),
        )
    }

    @Test
    fun `con renovar la foto el clic en el nombre trae donde esta`() {
        sender.start("Los del piso", now = 0, wantAvatar = true)
        clickOf(tick(0, reel()))
        val click = clickOf(tick(300, sheet("Ana", "Los del piso"), reel()))
        // La celda 2 empieza en x = 300: la foto es el círculo de arriba (300+40 … 300+200, 1000 … 1160).
        assertEquals(box(340, 1000, 500, 1160), click.avatar)
        sender.cancel()

        sender.start("Los del piso", now = 0)
        clickOf(tick(0, reel()))
        assertEquals(null, clickOf(tick(300, sheet("Ana", "Los del piso"), reel())).avatar)
    }

    // ── Revisión adversarial de la 1.4.0 ─────────────────────────────────────────────────────────

    /** Un aviso de Instagram (otra ventana, fuera del visor) con el nombre del grupo. */
    private fun banner(name: String) = FakeNode(
        bounds = box(0, 100, 1080, 300),
        children = listOf(desc("Foto del perfil de $name", box(20, 120, 180, 280)), text(name, box(200, 140, 900, 200)), text("Hola", box(200, 210, 900, 260))),
    )

    @Test
    fun `el atajo nunca pulsa el nombre en otra ventana que no es la hoja`() {
        val fast = fastSender()
        fast.start("Primos", now = 0)
        clickOf(fast.tickWith(0, reel()))
        // La hoja aún no se ha abierto y sale un aviso con «Primos»: no se pulsa.
        assertEquals(Step.Wait, fast.tickWith(200, banner("Primos"), reel()))
        // La hoja se abre (otra ventana) sin el grupo: escribe en el buscador, no pulsa el aviso.
        assertTrue(fast.tickWith(500, banner("Primos"), sheet("Ana", "Marta"), reel()) is Step.SetText)
        // Y en el resultado, el de la hoja.
        val result = clickOf(fast.tickWith(800, banner("Primos"), sheet("Primos", field = "Primos"), reel()))
        assertEquals("nombre del buscador", result.what)
        assertEquals(box(40 + 40, 1000 + 180, 40 + 240, 1000 + 230).top, result.node.bounds.top)
        fast.cancel()
    }

    @Test
    fun `con la hoja abierta solo con el mensaje no pulsa compartir detras`() {
        // Un envío anterior dejó la hoja abierta con el fijado marcado: «Escribe un mensaje» (pista de un campo
        // vacío, que la búsqueda de Instagram no ve) y «Enviar», sin «Copiar enlace» a la vista.
        val leftOpen = FakeNode(
            bounds = box(0, 800, 1080, 2400),
            children = listOf(
                search(),
                FakeNode(bounds = box(0, 1000, 1080, 1900), children = listOf(tile("Primos"))),
                FakeNode(className = "android.widget.EditText", text = "Escribe un mensaje…", bounds = box(40, 2060, 1040, 2180)),
                sendButton(),
            ),
        )
        val fast = fastSender()
        fast.start("Ana", now = 0)
        assertEquals(Step.Done(false, "ya hay una hoja abierta", sheetOpened = false, targetSelected = false), fast.tickWith(0, leftOpen, reel()))
        // Con «Añadir a la historia» como única opción, tampoco.
        val storyOnly = FakeNode(bounds = box(0, 800, 1080, 2400), children = listOf(text("Añadir a la historia", box(40, 2080, 400, 2130))))
        fast.start("Ana", now = 10)
        assertEquals("ya hay una hoja abierta", (fast.tickWith(10, storyOnly, reel()) as Step.Done).reason)
    }

    @Test
    fun `el enviar de otra ventana no vale`() {
        val fast = fastSender()
        fast.start("Primos", now = 0)
        clickOf(fast.tickWith(0, reel()))
        clickOf(fast.tickWith(300, sheet("Primos"), reel()))
        // Otra ventana de Instagram con un botón «Enviar» ancho y abajo; la hoja aún sin su «Enviar».
        val other = FakeNode(bounds = box(0, 0, 1080, 2400), children = listOf(sendButton()))
        assertEquals(Step.Wait, fast.tickWith(500, sheet("Primos"), other, reel()))
        fast.cancel()
    }

    @Test
    fun `una hoja abierta mas alla del limite del recorrido tambien para el envio`() {
        // Visor y hoja en la misma ventana; el recorrido se corta (pocos nodos) antes de llegar a «Copiar enlace»,
        // pero el atajo sí lo ve: no se pulsa el compartir de detrás.
        val limited = QuickSender(config = QuickSender.Config(maxNodes = 8), finder = FakeFinder())
        val both = FakeNode(bounds = box(0, 0, 1080, 2400), children = listOf(reel(), sheet("Ana")))
        limited.start("Primos", now = 0)
        assertEquals("ya hay una hoja abierta", (limited.tick(listOf(both), SCREEN_W, SCREEN_H, 0) as Step.Done).reason)
    }

    // ── 1.4.1: más rápido sobre todo a grupos, varios destinos ───────────────────────────────────

    /**
     * La celda real de un grupo: avatar compuesto (dos círculos que se pisan, sin «Foto del perfil de…»), el
     * nombre y una descripción de la celda con el nombre y «grupo».
     */
    private fun groupTile(name: String, left: Int = 40, top: Int = 1000) = FakeNode(
        viewId = QuickSender.KNOWN_GRID_CELL_ID,
        contentDescription = "$name, grupo",
        bounds = box(left, top, left + 240, top + 300),
        children = listOf(
            FakeNode(
                className = "android.widget.FrameLayout",
                bounds = box(left + 40, top, left + 200, top + 160),
                children = listOf(
                    FakeNode(className = "android.widget.ImageView", bounds = box(left + 40, top, left + 150, top + 110)),
                    FakeNode(className = "android.widget.ImageView", bounds = box(left + 90, top + 50, left + 200, top + 160)),
                ),
            ),
            text(name, box(left, top + 180, left + 240, top + 230)),
        ),
    )

    /** Hoja con celdas ya hechas (para mezclar grupos y personas). */
    private fun sheetOf(vararg tiles: FakeNode, bottom: List<FakeNode> = emptyList(), field: String = "Buscar") = FakeNode(
        bounds = box(0, 800, 1080, 2400),
        children = listOf(search(field), FakeNode(bounds = box(0, 1000, 1080, 1900), children = tiles.toList()), options()) + bottom,
    )

    /** Con dos marcados, Instagram pone «Crear grupo» y «Enviar por separado» abajo. */
    private fun twoSelectedBar() = listOf(
        FakeNode(className = "android.widget.Button", bounds = box(40, 2200, 520, 2340), children = listOf(text("Crear grupo", box(120, 2240, 440, 2300)))),
        FakeNode(className = "android.widget.Button", bounds = box(560, 2200, 1040, 2340), children = listOf(text("Enviar por separado", box(600, 2240, 1000, 2300)))),
    )

    @Test
    fun `un grupo con su celda real sale con el atajo en el mismo tick en que se abre la hoja`() {
        val finder = FakeFinder()
        val fast = fastSender(finder)
        fast.start("Los del piso", now = 0, wantAvatar = true)
        clickOf(fast.tickWith(0, reel()))
        val open = sheetOf(groupTile("Los del piso"), tile("Ana", left = 300), tile("Marta", left = 560))
        // Primer tick con la hoja abierta: pulsa el grupo ya (sin esperar a otro tick ni recorrer).
        val click = clickOf(fast.tickWith(120, open, reel()))
        assertEquals("nombre", click.what)
        assertEquals("Los del piso", click.node.text)
        // La foto del grupo: el rectángulo que envuelve los dos círculos.
        assertEquals(box(80, 1000, 240, 1160), click.avatar)
        assertEquals("Los del piso", click.avatarName)
        assertEquals("enviar", clickOf(fast.tickWith(300, sheetOf(groupTile("Los del piso"), bottom = listOf(sendButton())), reel())).what)
        assertTrue((fast.tickWith(500, reel()) as Step.Done).ok)
        val report = fast.lastReport!!
        assertEquals(1, report.walks) // solo el de «¿ya hay una hoja abierta?», que no entra en el visor
        assertEquals(QuickSender.Found.GRID, report.foundIn)
        assertEquals(listOf(0L, 120L, 0L, 180L, 200L), report.phases.map { it.ms })

        // Si la celda solo lleva la descripción («Los del piso, grupo»), también vale.
        val descOnly = FakeNode(
            bounds = box(0, 800, 1080, 2400),
            children = listOf(search(), FakeNode(contentDescription = "Los del piso, grupo", bounds = box(40, 1000, 280, 1300)), options()),
        )
        fast.start("Los del piso", now = 10_000)
        clickOf(fast.tickWith(10_000, reel()))
        assertEquals("Los del piso, grupo", clickOf(fast.tickWith(10_120, descOnly, reel())).node.contentDescription)
        fast.cancel()
    }

    @Test
    fun `una captura fallida no se reintenta en cada envio`() {
        val day = AvatarRules.REFRESH_MS
        // Sin foto y sin fallos: se recorta (una vez).
        assertTrue(AvatarRules.captureInQuickSend(savedAt = null, failedAt = null, now = 5 * day))
        // Falló hace una hora: este envío no espera a la captura.
        assertFalse(AvatarRules.captureInQuickSend(savedAt = null, failedAt = 5 * day - 3_600_000, now = 5 * day))
        // Pasado un día, se vuelve a probar.
        assertTrue(AvatarRules.captureInQuickSend(savedAt = null, failedAt = 4 * day, now = 5 * day))
        // Con foto, aunque sea vieja, el envío rápido nunca espera: se renueva en los envíos a mano.
        assertFalse(AvatarRules.captureInQuickSend(savedAt = 1L, failedAt = null, now = 5 * day))
        assertTrue(AvatarRules.needsRefresh(savedAt = 1L, now = 5 * day))
        // Y sin pedir la foto, el clic en el nombre no la trae.
        val fast = fastSender()
        fast.start(listOf(QuickSender.Target("Los del piso", wantAvatar = false)), now = 0)
        clickOf(fast.tickWith(0, reel()))
        val click = clickOf(fast.tickWith(120, sheetOf(groupTile("Los del piso")), reel()))
        assertEquals(null, click.avatar)
        assertEquals(null, click.avatarName)
        fast.cancel()
    }

    @Test
    fun `la hoja de hoy se reconoce por su buscador sin recorrer y una desconocida se recorre pasado el margen`() {
        // Hoja sin «Copiar enlace» a la vista y sin id aprendido: el buscador de hoy (search_edit_text) basta, en el
        // primer tick y sin recorrer.
        val bare = FakeNode(bounds = box(0, 800, 1080, 2400), children = listOf(search(), FakeNode(bounds = box(0, 1000, 1080, 1900), children = listOf(tile("Primos")))))
        val fast = fastSender()
        fast.start("Primos", now = 0)
        clickOf(fast.tickWith(0, reel()))
        assertEquals("Primos", clickOf(fast.tickWith(100, bare, reel())).node.text)
        fast.cancel()
        // Sin ids conocidos: solo el recorrido la ve, y no antes de walkGraceMs desde que se pulsó compartir.
        val unknown = FakeNode(
            bounds = box(0, 800, 1080, 2400),
            children = listOf(
                FakeNode(className = "android.widget.EditText", text = "Buscar", bounds = box(40, 860, 1040, 960)),
                FakeNode(bounds = box(0, 1000, 1080, 1900), children = listOf(tile("Primos", id = OLD_TILE))),
            ),
        )
        val slow = fastSender()
        slow.start("Primos", now = 0)
        clickOf(slow.tickWith(0, reel()))
        assertEquals(Step.Wait, slow.tickWith(100, unknown, reel()))
        assertEquals(Step.Wait, slow.tickWith(900, unknown, reel()))
        assertEquals("Primos", clickOf(slow.tickWith(1_000, unknown, reel())).node.text)
        slow.cancel()
    }

    @Test
    fun `primer y segundo envio con lo aprendido igual de rapidos y sin recorrer la hoja mientras se abre`() {
        // Medido en un Pixel 10a con la 1.4.1: si un tick caía con la hoja a medio abrir, el atajo aún no veía el
        // buscador y se recorría la hoja nodo a nodo mientras Instagram la animaba (0,9–1,25 s en una sola lectura;
        // envíos de 1,8–2,3 s). Ahora, mientras se abre, solo atajos, y el segundo envío, con lo aprendido, va igual.
        val halfOpen = FakeNode(
            bounds = box(0, 1800, 1080, 2400),
            children = List(30) { i -> FakeNode(bounds = box(0, 1800 + i * 10, 1080, 1810 + i * 10)) },
        )
        fun send(hints: QuickSender.Hints): QuickSender.Report {
            val s = fastSender()
            s.start(listOf(QuickSender.Target("Los del piso")), now = 0, hints = hints)
            clickOf(s.tickWith(0, reel()))
            s.onPerformed(true, performMs = 30)
            assertEquals(Step.Wait, s.tickWith(150, halfOpen, reel()))
            assertEquals("Los del piso", clickOf(s.tickWith(300, sheet("Ana García", "Los del piso", "Marta"), reel())).node.text)
            s.onPerformed(true, performMs = 15)
            assertEquals("enviar", clickOf(s.tickWith(400, sheet("Ana García", "Los del piso", "Marta", selected = true), reel())).what)
            s.onPerformed(true, performMs = 20)
            assertTrue((s.tickWith(550, reel()) as Step.Done).ok)
            return s.lastReport!!
        }
        val first = send(QuickSender.Hints())
        assertEquals(QuickSender.KNOWN_SEARCH_ID, first.searchId) // aprendido sin recorrer
        val second = send(QuickSender.Hints(shareId = first.shareId, searchId = first.searchId))
        // Ninguno en la hoja. El primero recorre una vez (¿ya hay una hoja abierta?, antes de compartir); el segundo,
        // con la hoja de hoy aprendida, ninguna (1.4.3).
        assertEquals(1, first.walks)
        assertEquals(0, second.walks)
        assertEquals(first.phases.map { it.phase to it.ms }, second.phases.map { it.phase to it.ms })
        assertEquals(550L, first.totalMs)
        assertEquals(550L, second.totalMs)
        assertEquals(mapOf("hoja: solo atajos mientras se abre" to 1), second.waits)
        assertTrue(second.describe().contains("reloj: compartir +0 ms (30 ms) · nombre +300 ms (15 ms) · enviar +400 ms (20 ms)"))
    }

    @Test
    fun `con la hoja de hoy ya aprendida no hay busquedas de texto de las opciones ni recorridos`() {
        // 1.4.3, medido: el tick de antes de pulsar «Compartir» costaba 230–460 ms (cuatro búsquedas de texto y un
        // recorrido). Con el buscador de hoy aprendido, «¿ya hay una hoja abierta?» son dos búsquedas por id.
        val finder = FakeFinder()
        val s = fastSender(finder)
        val hints = QuickSender.Hints(shareId = "com.instagram.android:id/direct_share_button", searchId = QuickSender.KNOWN_SEARCH_ID)
        s.start(listOf(QuickSender.Target("Los del piso")), now = 0, hints = hints)
        clickOf(s.tickWith(0, reel()))
        s.onPerformed(true)
        assertEquals(Step.Wait, s.tickWith(150, reel()))
        clickOf(s.tickWith(300, sheet("Ana García", "Los del piso"), reel()))
        s.onPerformed(true)
        clickOf(s.tickWith(400, sheet("Ana García", "Los del piso", selected = true), reel()))
        s.onPerformed(true)
        assertTrue((s.tickWith(550, reel()) as Step.Done).ok)
        assertEquals(0, s.lastReport!!.walks)
        assertTrue(finder.calls.none { it in QuickSender.MARKER_QUERIES.map { q -> "texto:$q" } })

        // Y una hoja abierta (el buscador de hoy a la vista) sigue parando el envío.
        val open = fastSender()
        open.start(listOf(QuickSender.Target("Ana")), now = 0, hints = hints)
        assertEquals("ya hay una hoja abierta", (open.tickWith(0, sheet("Ana"), reel()) as Step.Done).reason)
    }

    @Test
    fun `con enviar ya aceptado otro envio no espera a la confirmacion`() {
        val s = fastSender()
        s.start("Ana", now = 0)
        clickOf(s.tickWith(0, reel()))
        assertEquals(null, s.release(100)) // aún no se ha pulsado «Enviar»: no suelta nada
        assertTrue(s.isRunning)
        s.onPerformed(true)
        clickOf(s.tickWith(300, sheet("Ana", "Marta"), reel()))
        s.onPerformed(true)
        clickOf(s.tickWith(400, sheet("Ana", "Marta", selected = true), reel()))
        s.onPerformed(true)
        assertTrue(s.sentProvisionally)
        val done = s.release(450)!!
        assertTrue(done.ok)
        assertEquals(QuickSender.RELEASED, done.reason)
        assertFalse(s.isRunning)
        assertEquals(listOf("Ana" to true), s.lastResults.map { it.name to it.ok })
    }

    @Test
    fun `la cuadricula de hoy sin el nombre va al buscador sin esperar y sin aprender si no se vio`() {
        val fast = fastSender()
        fast.start("Primos", now = 0)
        clickOf(fast.tickWith(0, reel()))
        val step = fast.tickWith(300, sheet("Ana", "Marta"), reel())
        assertTrue(step is Step.SetText)
        fast.onPerformed(true)
        clickOf(fast.tickWith(500, sheet("Primos", field = "Primos"), reel()))
        fast.cancel()

        // Sin celdas a la vista (aún no ha cargado), pasado searchAfterMs se busca, pero no cuenta como «no sale».
        val empty = FakeNode(bounds = box(0, 800, 1080, 2400), children = listOf(search(), options()))
        val late = fastSender()
        late.start("Primos", now = 0)
        clickOf(late.tickWith(0, reel()))
        late.onPerformed(true)
        assertEquals(Step.Wait, late.tickWith(300, empty, reel()))
        assertTrue(late.tickWith(950, empty, reel()) is Step.SetText)
        late.onPerformed(true)
        clickOf(late.tickWith(1_200, sheet("Primos", field = "Primos"), reel()))
        late.onPerformed(true)
        clickOf(late.tickWith(1_300, sheet("Primos", field = "Primos", selected = true), reel()))
        late.onPerformed(true)
        assertTrue((late.tickWith(1_400, reel()) as Step.Done).ok)
        assertEquals(null, late.lastResults.single().foundIn)
        assertEquals(listOf("pasado el plazo sin cuadrícula"), late.lastReport!!.searches)
        assertEquals(2, QuickSender.nextGridMisses(2, late.lastResults.single()))
    }

    /** Un nodo del visor que cuenta cuántas veces se le piden los hijos. */
    private class Spy(val inner: FakeNode) : UiNode by inner {
        var reads = 0
        override val children: List<UiNode> get() = inner.children.also { reads++ }
    }

    @Test
    fun `el recorrido de la hoja no entra en el visor`() {
        val spy = Spy(FakeNode(bounds = box(0, 0, 1080, 1800), children = List(20) { i -> text("Comentario $i", box(40, 100 + i * 80, 800, 170 + i * 80)) }))
        val viewer = object : UiNode by FakeNode(viewId = "com.instagram.android:id/clips_viewer_view_pager", bounds = box(0, 0, 1080, 2400)) {
            override val children: List<UiNode> = listOf(spy, reel().children.first())
        }
        sender.start("Primos", now = 0)
        clickOf(sender.tick(listOf(viewer), SCREEN_W, SCREEN_H, 0)) // compartir: sin atajo, recorre el visor
        spy.reads = 0
        val name = clickOf(sender.tick(listOf(sheet("Ana", "Primos"), viewer), SCREEN_W, SCREEN_H, 300))
        assertEquals("Primos", name.node.text)
        assertEquals(0, spy.reads)
        sender.cancel()
    }

    @Test
    fun `dos fijados en la cuadricula se marcan los dos y un solo enviar`() {
        val fast = fastSender()
        fast.start(listOf(QuickSender.Target("Los del piso"), QuickSender.Target("Ana")), now = 0)
        clickOf(fast.tickWith(0, reel()))
        val open = sheetOf(groupTile("Los del piso"), tile("Ana", left = 300), tile("Marta", left = 560))
        assertEquals("Los del piso", clickOf(fast.tickWith(120, open, reel())).node.text)
        fast.onPerformed(true)
        assertEquals(QuickSender.Phase.TARGET, fast.currentPhase)
        // El segundo, en el siguiente tick, en la misma hoja.
        val one = sheetOf(groupTile("Los del piso"), tile("Ana", left = 300), tile("Marta", left = 560), bottom = listOf(sendButton()))
        assertEquals("Ana", clickOf(fast.tickWith(220, one, reel())).node.text)
        fast.onPerformed(true)
        // Con los dos marcados: «Crear grupo» nunca; «Enviar por separado», una vez.
        val two = sheetOf(groupTile("Los del piso"), tile("Ana", left = 300), bottom = twoSelectedBar())
        val send = clickOf(fast.tickWith(320, two, reel()))
        assertEquals("enviar", send.what)
        assertEquals("Enviar por separado", send.node.text)
        fast.onPerformed(true)
        assertTrue(fast.sentProvisionally)
        assertEquals(Step.Done(true, "enviado", sheetOpened = true, targetSelected = true), fast.tickWith(500, reel()))
        assertEquals(listOf("Los del piso" to true, "Ana" to true), fast.lastResults.map { it.name to it.ok })
        val report = fast.lastReport!!
        assertEquals(2, report.dests)
        assertEquals(2, report.sent)
        // Tiempos por destino: «nombre 1» y «nombre 2».
        assertEquals(listOf(1, 2), report.phases.filter { it.phase == QuickSender.Phase.TARGET }.map { it.dest })
        assertTrue(report.describe().contains("nombre 2 100 ms (atajo)"))
        assertTrue(report.describe().contains("destinos 2 de 2 · reloj: compartir +0 ms · nombre +120 ms · nombre +220 ms · enviar +320 ms"))
    }

    @Test
    fun `uno en la cuadricula y otro por el buscador`() {
        sender.start(listOf(QuickSender.Target("Ana"), QuickSender.Target("Primos")), now = 0)
        clickOf(tick(0, reel()))
        assertEquals("Ana", clickOf(tick(120, sheet("Ana", "Marta"), reel())).node.text)
        sender.onPerformed(true)
        // Primos no está en la cuadrícula (ya cargada): al buscador.
        val field = search()
        val noPrimos = FakeNode(bounds = box(0, 800, 1080, 2400), children = listOf(field, FakeNode(bounds = box(0, 1000, 1080, 1900), children = listOf(tile("Ana"), tile("Marta", left = 300))), options()))
        assertEquals(Step.SetText(field, "Primos"), tick(240, noPrimos, reel()))
        sender.onPerformed(true)
        val result = clickOf(tick(600, sheet("Primos", field = "Primos"), reel()))
        assertEquals("nombre del buscador", result.what)
        sender.onPerformed(true)
        assertEquals("enviar", clickOf(tick(800, sheet("Primos", selected = true, field = "Primos"), reel())).what)
        sender.onPerformed(true)
        assertTrue((tick(1_000) as Step.Done).ok)
        assertEquals(listOf(QuickSender.Found.GRID, QuickSender.Found.SEARCH), sender.lastResults.map { it.foundIn })
    }

    @Test
    fun `si uno no sale el otro se envia igual y se avisa de cual fallo`() {
        sender.start(listOf(QuickSender.Target("Ana"), QuickSender.Target("Primos")), now = 0)
        clickOf(tick(0, reel()))
        clickOf(tick(120, sheet("Ana", "Marta"), reel()))
        sender.onPerformed(true)
        assertTrue(tick(240, sheet("Ana", "Marta", selected = true), reel()) is Step.SetText)
        sender.onPerformed(true)
        // El buscador no encuentra a Primos.
        assertEquals(Step.Wait, tick(1_500, sheet(selected = true, field = "Primos"), reel()))
        val send = clickOf(tick(3_400, sheet(selected = true, field = "Primos"), reel()))
        assertEquals("enviar", send.what)
        sender.onPerformed(true)
        val done = tick(3_600) as Step.Done
        assertTrue(done.ok)
        assertEquals("enviado a 1 de 2", done.reason)
        assertEquals(
            listOf(QuickSender.DestResult("Ana", true, "enviado", QuickSender.Found.GRID, skippedGrid = false, selected = true), QuickSender.DestResult("Primos", false, "el buscador no lo encuentra", null, skippedGrid = false, selected = false)),
            sender.lastResults,
        )
        assertEquals(1, sender.lastReport!!.sent)

        // Si el repetido es uno de los dos, ese no se pulsa y el otro sigue.
        sender.start(listOf(QuickSender.Target("Marta"), QuickSender.Target("Ana")), now = 10_000)
        clickOf(tick(10_000, reel()))
        assertEquals("Ana", clickOf(tick(10_120, sheet("Marta", "Ana", "Marta"), reel())).node.text)
        sender.cancel()
    }

    @Test
    fun `crear grupo nunca se pulsa`() {
        val fast = fastSender()
        fast.start(listOf(QuickSender.Target("Ana"), QuickSender.Target("Marta")), now = 0)
        clickOf(fast.tickWith(0, reel()))
        clickOf(fast.tickWith(120, sheet("Ana", "Marta"), reel()))
        fast.onPerformed(true)
        clickOf(fast.tickWith(240, sheet("Ana", "Marta"), reel()))
        fast.onPerformed(true)
        // Solo «Crear grupo» abajo (aún sin «Enviar por separado»): espera y, al final, se rinde sin pulsarlo.
        val onlyGroup = sheetOf(tile("Ana"), tile("Marta", left = 300), bottom = twoSelectedBar().take(1))
        assertEquals(Step.Wait, fast.tickWith(400, onlyGroup, reel()))
        assertEquals(Step.Wait, fast.tickWith(2_000, onlyGroup, reel()))
        assertEquals(Step.Done(false, "no encuentro enviar", sheetOpened = true, targetSelected = true), fast.tickWith(3_400, onlyGroup, reel()))
    }

    @Test
    fun `un toque durante el envio se suma a la misma hoja o se encola`() {
        val fast = fastSender()
        fast.start(listOf(QuickSender.Target("Ana")), now = 0)
        clickOf(fast.tickWith(0, reel()))
        assertEquals("Ana", clickOf(fast.tickWith(120, sheet("Ana", "Marta"), reel())).node.text)
        fast.onPerformed(true)
        assertEquals(QuickSender.Phase.SEND, fast.currentPhase)
        // Tocan a Marta antes de «Enviar»: se marca en la misma hoja.
        assertTrue(fast.add(QuickSender.Target("Marta")))
        assertTrue(fast.add(QuickSender.Target("ana"))) // ya estaba
        assertEquals("Marta", clickOf(fast.tickWith(220, sheet("Ana", "Marta", selected = true), reel())).node.text)
        fast.onPerformed(true)
        assertEquals("enviar", clickOf(fast.tickWith(320, sheet("Ana", "Marta", selected = true), reel())).what)
        fast.onPerformed(true)
        // Con «Enviar» ya pulsado, otro no entra: el servicio lo encola.
        assertFalse(fast.add(QuickSender.Target("Primos")))
        assertTrue((fast.tickWith(500, reel()) as Step.Done).ok)
        assertEquals(listOf("Ana", "Marta"), fast.lastResults.filter { it.ok }.map { it.name })
        assertFalse(fast.add(QuickSender.Target("Primos"))) // sin envío en curso, tampoco

        // El encolado sale después: si la hoja del anterior aún se ve, espera a que se cierre (no se rinde).
        fast.start(listOf(QuickSender.Target("Primos")), now = 1_000, waitForClosedSheet = true)
        assertEquals(Step.Wait, fast.tickWith(1_000, sheet("Ana"), reel()))
        assertEquals("compartir", clickOf(fast.tickWith(1_200, reel())).what)
        fast.cancel()
    }

    // ── Revisión adversarial de la 1.4.1 ─────────────────────────────────────────────────────────

    @Test
    fun `si android rechaza siempre el mismo clic se para al vencer el plazo`() {
        sender.start("Primos", now = 0)
        var now = 0L
        var done: Step.Done? = null
        while (done == null && now < 20_000) {
            val step = tick(now, reel())
            if (step is Step.Done) done = step else sender.onPerformed(false)
            now += 100
        }
        assertEquals(Step.Done(false, "Android no acepta la acción", sheetOpened = false, targetSelected = false), done)
        assertTrue(now <= 3_200)

        // Con dos destinos, el que no se deja pulsar falla solo y el otro se envía.
        sender.start(listOf(QuickSender.Target("Primos"), QuickSender.Target("Ana")), now = 50_000)
        clickOf(tick(50_000, reel()))
        sender.onPerformed(true)
        assertEquals("Primos", clickOf(tick(50_100, sheet("Primos", "Ana"), reel())).node.text)
        sender.onPerformed(false)
        assertEquals("Primos", clickOf(tick(51_000, sheet("Primos", "Ana"), reel())).node.text)
        sender.onPerformed(false)
        assertEquals("Ana", clickOf(tick(53_200, sheet("Primos", "Ana"), reel())).node.text)
        sender.cancel()
    }

    @Test
    fun `el enviado provisional solo es de los destinos marcados`() {
        val fast = fastSender()
        fast.start(listOf(QuickSender.Target("Ana"), QuickSender.Target("Primos")), now = 0)
        clickOf(fast.tickWith(0, reel()))
        fast.onPerformed(true)
        clickOf(fast.tickWith(100, sheet("Ana", "Marta"), reel()))
        fast.onPerformed(true)
        assertEquals(emptyList<String>(), fast.provisionalNames)
        assertTrue(fast.tickWith(200, sheet("Ana", "Marta", selected = true), reel()) is Step.SetText)
        fast.onPerformed(true)
        // Primos no sale en el buscador: falla y se envía a Ana. Solo Ana dice «enviado».
        val send = clickOf(fast.tickWith(3_400, sheet(selected = true, field = "Primos"), reel()))
        assertEquals("enviar", send.what)
        fast.onPerformed(true)
        assertEquals(listOf("Ana"), fast.provisionalNames)
        fast.cancel()
    }

    @Test
    fun `un destino buscado con el buscador ocupado por otro no aprende que no sale en la cuadricula`() {
        sender.start(listOf(QuickSender.Target("Primos", skipGrid = true), QuickSender.Target("Ana")), now = 0)
        clickOf(tick(0, reel()))
        sender.onPerformed(true)
        assertTrue(tick(100, sheet("Ana", "Marta"), reel()) is Step.SetText)
        sender.onPerformed(true)
        clickOf(tick(400, sheet("Primos", field = "Primos"), reel()))
        sender.onPerformed(true)
        // El buscador sigue diciendo «Primos»: Ana no se ve y se busca también.
        assertTrue(tick(500, sheet("Primos", selected = true, field = "Primos"), reel()) is Step.SetText)
        sender.onPerformed(true)
        clickOf(tick(800, sheet("Ana", selected = true, field = "Ana"), reel()))
        sender.onPerformed(true)
        clickOf(tick(900, sheet("Ana", selected = true, field = "Ana"), reel()))
        sender.onPerformed(true)
        assertTrue((tick(1_000) as Step.Done).ok)
        val (primos, ana) = sender.lastResults
        assertEquals(QuickSender.Found.SEARCH, primos.foundIn)
        // Para Ana no se aprende nada: su racha no sube.
        assertEquals(null, ana.foundIn)
        assertEquals(1, QuickSender.nextGridMisses(1, ana))
    }

    @Test
    fun `si android rechaza compartir pero la hoja se abre se sigue con ella`() {
        sender.start("Primos", now = 0)
        clickOf(tick(0, reel()))
        sender.onPerformed(false)
        val name = clickOf(tick(300, sheet("Ana", "Primos"), reel()))
        assertEquals("Primos", name.node.text)
        sender.onPerformed(true)
        assertEquals("enviar", clickOf(tick(500, sheet("Primos", selected = true), reel())).what)
        sender.onPerformed(true)
        val done = tick(700) as Step.Done
        assertTrue(done.ok)
        assertTrue(done.sheetOpened)
    }

    @Test
    fun `buscando compartir tambien se espacian los recorridos`() {
        val fast = fastSender()
        fast.start("Primos", now = 0)
        val noShare = reel(share = desc("Guardar", box(960, 1500, 1060, 1600)))
        var now = 0L
        while (now <= 2_950) {
            assertEquals(Step.Wait, fast.tickWith(now, noShare))
            now += 50
        }
        assertEquals("no encuentro compartir", (fast.tickWith(3_100, noShare) as Step.Done).reason)
        // Como mucho cada 150 ms (en cada uno, el de fuera del visor y el entero para buscar «Compartir»), no en cada tick.
        assertTrue("recorridos: ${fast.lastReport!!.walks}", fast.lastReport!!.walks <= 2 * (3_100 / 150 + 1))
    }

    @Test
    fun `sin atajo la hoja en la misma ventana que el visor se ve aunque el visor gaste el limite de nodos`() {
        // El recorrido entero se quedaría sin nodos dentro del visor; el de «¿ya hay una hoja?» no entra en él.
        val limited = QuickSender(config = QuickSender.Config(maxNodes = 8))
        val both = FakeNode(bounds = box(0, 0, 1080, 2400), children = listOf(reel(), FakeNode(bounds = box(0, 1900, 1080, 2400), children = listOf(options()))))
        limited.start("Primos", now = 0)
        assertEquals("ya hay una hoja abierta", (limited.tick(listOf(both), SCREEN_W, SCREEN_H, 0) as Step.Done).reason)
    }

    @Test
    fun `un solo rechazo tardio no tumba el destino`() {
        sender.start("Primos", now = 0)
        clickOf(tick(0, reel()))
        sender.onPerformed(true)
        // La hoja tarda: Primos aparece casi al final del plazo y Android rechaza ese primer clic (nodo caducado).
        val noGrid = FakeNode(bounds = box(0, 800, 1080, 2400), children = listOf(options()))
        assertEquals(Step.Wait, tick(100, noGrid, reel()))
        assertEquals(Step.Wait, tick(2_900, noGrid, reel()))
        assertEquals("Primos", clickOf(tick(3_050, sheet("Primos"), reel())).node.text)
        sender.onPerformed(false)
        // El plazo del corte cuenta desde ese rechazo: se reintenta.
        assertEquals("Primos", clickOf(tick(3_150, sheet("Primos"), reel())).node.text)
        sender.onPerformed(true)
        sender.cancel()
    }

    // ── Revisión adversarial de la 1.4.3 ─────────────────────────────────────────────────────────

    private val learnedToday = QuickSender.Hints(shareId = "com.instagram.android:id/direct_share_button", searchId = QuickSender.KNOWN_SEARCH_ID)

    @Test
    fun `con la hoja de hoy aprendida una hoja abierta con otros ids o con un nombre escrito tampoco deja pulsar compartir`() {
        // Instagram cambia los ids de la hoja de compartir, pero la deja dentro de su hoja de abajo genérica.
        val changed = FakeNode(
            viewId = "com.instagram.android:id/layout_container_bottom_sheet",
            bounds = box(0, 800, 1080, 2400),
            children = listOf(
                FakeNode(className = "android.widget.EditText", text = "Buscar", viewId = "com.instagram.android:id/new_search_field", bounds = box(40, 860, 1040, 960)),
                FakeNode(bounds = box(0, 1000, 1080, 1900), children = listOf(tile("Ana", id = "com.instagram.android:id/new_cell"))),
            ),
        )
        val a = fastSender()
        a.start(listOf(QuickSender.Target("Primos")), now = 0, hints = learnedToday)
        assertEquals("ya hay una hoja abierta", (a.tickWith(0, changed, reel()) as Step.Done).reason)
        // Un envío que falló dejó «Marta» escrito en el buscador de hoy (sin el contenedor a la vista).
        val leftTyped = FakeNode(
            bounds = box(0, 800, 1080, 2400),
            children = listOf(
                FakeNode(className = "android.widget.EditText", text = "Marta", contentDescription = "Buscar", viewId = QuickSender.KNOWN_SEARCH_ID, bounds = box(40, 860, 1040, 960)),
                FakeNode(bounds = box(0, 1000, 1080, 1900), children = listOf(tile("Marta"))),
            ),
        )
        val b = fastSender()
        b.start(listOf(QuickSender.Target("Primos")), now = 0, hints = learnedToday)
        assertEquals("ya hay una hoja abierta", (b.tickWith(0, leftTyped, reel()) as Step.Done).reason)
    }

    @Test
    fun `el buscador con la descripcion buscar sigue siendolo con un nombre escrito y el segundo destino tambien se busca`() {
        // Como el real, pero con otro id (para que cuenten sus etiquetas): descripción «Buscar» y el texto escrito.
        val searchId = "com.instagram.android:id/other_search"
        fun field(value: String) = FakeNode(className = "android.widget.EditText", text = value, contentDescription = "Buscar", viewId = searchId, bounds = box(40, 860, 1040, 960))
        fun sheetWith(value: String, vararg tiles: FakeNode, selected: Boolean = false) = FakeNode(
            bounds = box(0, 800, 1080, 2400),
            children = listOf(field(value), FakeNode(bounds = box(0, 1000, 1080, 1900), children = tiles.toList()), options()) +
                if (selected) listOf(sendButton()) else emptyList(),
        )
        val s = fastSender()
        s.start(listOf(QuickSender.Target("Primos"), QuickSender.Target("Tios")), now = 0, hints = QuickSender.Hints(searchId = searchId))
        clickOf(s.tickWith(0, reel()))
        s.onPerformed(true)
        assertEquals(Step.SetText(field(""), "Primos").text, (s.tickWith(300, sheetWith("", tile("Ana")), reel()) as Step.SetText).text)
        s.onPerformed(true)
        assertEquals("nombre del buscador", clickOf(s.tickWith(500, sheetWith("Primos", tile("Primos")), reel())).what)
        s.onPerformed(true)
        // El campo dice «Primos» y «Buscar»: sigue siendo el buscador y se escribe el segundo.
        val second = s.tickWith(600, sheetWith("Primos", tile("Ana"), selected = true), reel())
        assertTrue("debía escribir «Tios» y llegó $second", second is Step.SetText && second.text == "Tios")
        s.cancel()
    }

    @Test
    fun `si el buscador aprendido ya no sale se olvida`() {
        // Aprendido un id que Instagram ya no usa; la hoja (de otra forma) solo se ve recorriendo, pasado el margen.
        val unknown = FakeNode(
            bounds = box(0, 800, 1080, 2400),
            children = listOf(
                FakeNode(className = "android.widget.EditText", text = "Buscar", bounds = box(40, 860, 1040, 960)),
                FakeNode(bounds = box(0, 1000, 1080, 1900), children = listOf(tile("Primos", id = OLD_TILE))),
            ),
        )
        val s = fastSender()
        s.start(listOf(QuickSender.Target("Primos")), now = 0, hints = QuickSender.Hints(searchId = "com.instagram.android:id/viejo"))
        clickOf(s.tickWith(0, reel()))
        s.onPerformed(true)
        assertEquals(Step.Wait, s.tickWith(300, unknown, reel()))
        clickOf(s.tickWith(1_000, unknown, reel()))
        s.onPerformed(true)
        val withSend = FakeNode(bounds = box(0, 800, 1080, 2400), children = listOf(FakeNode(className = "android.widget.EditText", text = "Buscar", bounds = box(40, 860, 1040, 960)), sendButton()))
        clickOf(s.tickWith(1_100, withSend, reel()))
        s.onPerformed(true)
        assertTrue((s.tickWith(1_300, reel()) as Step.Done).ok)
        assertTrue(s.lastReport!!.forgetSearchId)
        assertEquals(null, s.lastReport!!.searchId)

        // Con el de hoy a la vista, nunca se olvida.
        val ok = fastSender()
        ok.start(listOf(QuickSender.Target("Primos")), now = 0, hints = learnedToday)
        clickOf(ok.tickWith(0, reel()))
        ok.onPerformed(true)
        clickOf(ok.tickWith(300, sheet("Primos"), reel()))
        ok.onPerformed(true)
        clickOf(ok.tickWith(400, sheet("Primos", selected = true), reel()))
        ok.onPerformed(true)
        assertTrue((ok.tickWith(550, reel()) as Step.Done).ok)
        assertFalse(ok.lastReport!!.forgetSearchId)
    }

    @Test
    fun `la hoja vista solo por su contenedor y cerrada antes no olvida el buscador de hoy`() {
        // Segunda pasada de la 1.4.3: la hoja entrando (solo el contenedor a la vista) y cerrada antes de ver el buscador.
        val entering = FakeNode(viewId = "com.instagram.android:id/direct_private_share_container_view", bounds = box(0, 2300, 1080, 2400))
        val s = fastSender()
        s.start(listOf(QuickSender.Target("Primos")), now = 0, hints = learnedToday)
        clickOf(s.tickWith(0, reel()))
        s.onPerformed(true)
        assertEquals(Step.Wait, s.tickWith(100, entering, reel()))
        assertEquals("la hoja se ha cerrado", (s.tickWith(1_200, reel()) as Step.Done).reason)
        assertFalse(s.lastReport!!.forgetSearchId)
    }

    /** Fila de resultados del buscador de hoy: nombre y, en las personas, su usuario debajo (los grupos, sin él). */
    private fun resultRow(name: String, user: String?, top: Int) = FakeNode(
        viewId = "com.instagram.android:id/user_row_background",
        bounds = box(42, top, 1038, top + 158),
        children = listOf(
            FakeNode(viewId = "com.instagram.android:id/row_user_imageview", bounds = box(42, top + 16, 168, top + 142)),
            FakeNode(
                viewId = "com.instagram.android:id/row_user_info_layout",
                bounds = box(200, top + 24, 933, top + 134),
                children = listOfNotNull(
                    text(name, box(200, top + 24, 555, top + 79), id = "com.instagram.android:id/row_user_primary_name"),
                    user?.let { text(it, box(200, top + 79, 458, top + 134), id = "com.instagram.android:id/row_user_secondary_name") },
                ),
            ),
            FakeNode(className = "android.widget.CompoundButton", viewId = "com.instagram.android:id/recipient_toggle", bounds = box(933, top + 37, 1017, top + 121)),
        ),
    )

    private fun results(typed: String, vararg rows: Pair<String, String?>, selected: Boolean = false) = FakeNode(
        bounds = box(0, 800, 1080, 2400),
        children = buildList {
            add(search(typed))
            add(FakeNode(bounds = box(0, 1000, 1080, 1900), children = rows.mapIndexed { i, (n, u) -> resultRow(n, u, 1000 + i * 158) }))
            if (selected) add(sendButton())
        },
    )

    @Test
    fun `un grupo fijado con el nombre cortado se encuentra en el buscador`() {
        // Caso real (1.4.3): fijado «Viaje fin de» (la primera línea de la celda); el buscador
        // enseña «Viaje fin de curso» y acababa en «el buscador no lo encuentra».
        val rows = arrayOf<Pair<String, String?>>(
            "Viaje fin de curso" to null,
            "Planes del finde" to "planes.finde",
            "Marta López" to "marta_lopez.92",
        )
        for (s in listOf(QuickSender(), fastSender())) {
            s.start("Viaje fin de", now = 0)
            clickOf(s.tickWith(0, reel()))
            s.onPerformed(true)
            val open = sheet("Ana", "Marta")
            assertEquals(Step.SetText(open.children.first(), "Viaje fin de"), s.tickWith(1_100, open, reel()))
            s.onPerformed(true)
            val result = clickOf(s.tickWith(1_400, results("Viaje fin de", *rows), reel()))
            assertEquals("nombre del buscador", result.what)
            assertEquals("Viaje fin de curso", result.node.text)
            s.onPerformed(true)
            assertEquals("enviar", clickOf(s.tickWith(1_700, results("Viaje fin de", *rows, selected = true), reel())).what)
            s.onPerformed(true)
            assertTrue((s.tickWith(2_000) as Step.Done).ok)
        }
    }

    @Test
    fun `un nombre que solo empieza por el fijado no vale si es una persona o no es el unico`() {
        for (rows in listOf(
            arrayOf<Pair<String, String?>>("Pablo Pérez" to "jperez"), // persona: lleva su usuario
            arrayOf<Pair<String, String?>>("Pablo y los primos" to null, "Pablo fútbol" to null), // dos grupos
            arrayOf<Pair<String, String?>>("Pablota" to null), // sin palabra entera
        )) {
            val s = fastSender()
            s.start("Pablo", now = 0)
            clickOf(s.tickWith(0, reel()))
            s.onPerformed(true)
            assertTrue(s.tickWith(1_100, sheet("Ana", "Marta"), reel()) is Step.SetText)
            s.onPerformed(true)
            assertEquals(Step.Wait, s.tickWith(1_400, results("Pablo", *rows), reel()))
            s.cancel()
        }
    }

    /**
     * Como en el móvil: cada nodo que da la búsqueda es un objeto nuevo, y su padre y sus hijos también (cada
     * `AccessibilityUiNode` envuelve el suyo). Bajar por la fila no devuelve el mismo objeto que dio la búsqueda.
     */
    private class Copy(val inner: UiNode, private val knownParent: UiNode? = null) : UiNode by inner {
        override val children: List<UiNode> get() = inner.children.map { Copy(it, this) }
        override val parent: UiNode? get() = knownParent ?: inner.parent?.let { Copy(it) }
    }

    private class CopyFinder(private val inner: FakeFinder = FakeFinder()) : UiFinder {
        override fun byText(root: UiNode, text: String): List<UiNode> = inner.byText(root, text).map { Copy(it) }
        override fun byViewId(root: UiNode, viewId: String): List<UiNode> = inner.byViewId(root, viewId).map { Copy(it) }
    }

    @Test
    fun `el grupo cortado del buscador se pulsa por el atajo en cuanto sale sin esperar al recorrido`() {
        // 1.4.4 medido: el atajo lo veía, pero su propio nombre contaba como «otro nombre en la fila» (objetos
        // distintos) y se pulsaba al llegar el recorrido, ~1 s después de escribir.
        val s = QuickSender(finder = CopyFinder())
        val rows = arrayOf<Pair<String, String?>>("Viaje fin de curso" to null, "Marta López" to "marta_lopez.92")
        s.start("Viaje fin de", now = 0)
        clickOf(s.tickWith(0, reel()))
        s.onPerformed(true)
        assertTrue(s.tickWith(200, sheet("Ana", "Marta"), reel()) is Step.SetText)
        s.onPerformed(true)
        val result = clickOf(s.tickWith(300, results("Viaje fin de", *rows), reel()))
        assertEquals("Viaje fin de curso", result.node.text)
        s.onPerformed(true)
        clickOf(s.tickWith(400, results("Viaje fin de", *rows, selected = true), reel()))
        s.onPerformed(true)
        assertTrue((s.tickWith(500) as Step.Done).ok)
        assertTrue(s.lastReport!!.describe(), s.lastReport!!.describe().contains("buscador 100 ms (atajo)"))
    }

    /** El «Enviar» de abajo de hoy: `Button` con su id y la etiqueta en la descripción y en un texto hijo. */
    private fun sendById(label: String, left: Int = 42, right: Int = 1038) = FakeNode(
        className = "android.widget.Button",
        viewId = RecipientDetector.SEND_BUTTON_ID,
        contentDescription = label,
        bounds = box(left, 2186, right, 2302),
        children = listOf(text(label, box(left + 400, 2216, left + 530, 2271))),
    )

    private fun sentInLanguage(button: FakeNode, s: QuickSender = fastSender()): Step {
        s.start("Ana", now = 0)
        clickOf(s.tickWith(0, reel()))
        s.onPerformed(true)
        assertEquals("Ana", clickOf(s.tickWith(200, sheet("Ana", "Marta"), reel())).node.text)
        s.onPerformed(true)
        return s.tickWith(300, sheetOf(tile("Ana"), tile("Marta", left = 300), bottom = listOf(button)), reel())
    }

    @Test
    fun `enviar se encuentra por su id en cualquier idioma`() {
        // Polaco: la palabra no está en ninguna lista, pero el id sí.
        val button = sendById("Wyślij")
        val s = fastSender()
        val send = clickOf(sentInLanguage(button, s))
        assertEquals("enviar", send.what)
        assertSame(button, send.node)
        s.onPerformed(true)
        // Con el botón aún ahí no se da por enviado; con la hoja cerrada, sí.
        assertEquals(Step.Wait, s.tickWith(400, sheetOf(tile("Ana"), bottom = listOf(sendById("Wyślij"))), reel()))
        assertTrue((s.tickWith(500, reel()) as Step.Done).ok)
    }

    @Test
    fun `sin el id valen las palabras de otros idiomas`() {
        for (label in listOf("Invia", "Envoyer", "Senden", "Envia", "Verzenden", "Bidali")) {
            val button = FakeNode(className = "android.widget.Button", bounds = box(40, 2200, 1040, 2340), children = listOf(text(label, box(400, 2240, 680, 2300))))
            assertEquals(label, "enviar", clickOf(sentInLanguage(button)).what)
        }
    }

    @Test
    fun `crear grupo nunca es enviar aunque lleve el id`() {
        val group = sendById("Crear grupo", left = 40, right = 520)
        val separately = FakeNode(className = "android.widget.Button", bounds = box(560, 2186, 1040, 2302), children = listOf(text("Enviar por separado", box(600, 2216, 1000, 2271))))
        val s = fastSender()
        s.start("Ana", now = 0)
        clickOf(s.tickWith(0, reel()))
        s.onPerformed(true)
        clickOf(s.tickWith(200, sheet("Ana", "Marta"), reel()))
        s.onPerformed(true)
        val send = clickOf(s.tickWith(300, sheetOf(tile("Ana"), bottom = listOf(group, separately)), reel()))
        assertEquals("Enviar por separado", send.node.text)
    }
}
