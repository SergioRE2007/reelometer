package com.reelometro.app.detection

import com.reelometro.app.data.Platform
import com.reelometro.app.detection.RecipientDetector.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «A quién envías» en la hoja de compartir de TikTok 47.7 (1.10.0): clics sobre los nodos reales de los volcados
 * anonimizados (`src/test/resources/calibration/tiktok-47.7-*.xml`, ver [UiDump]).
 */
class RecipientDetectorTikTokTest {

    private val detector = RecipientDetector().apply { start(now = 0, reelId = 7, profile = PlatformProfile.TIKTOK) }

    private fun click(node: UiNode, now: Long = 1_000) = detector.onClick(node, UiDump.H, now)

    /** La hoja va en su propia ventana (2); el feed, en la 1. */
    private fun load(name: String) = UiDump.load("tiktok-47.7-$name.xml", windowId = 2)

    private fun all(root: UiNode) = UiDump.all(root)

    /** La celda de la fila de contactos descrita con [name]. */
    private fun cell(root: UiNode, name: String) =
        all(root).single { it.contentDescription?.toString() == name && it.className == "android.widget.Button" }

    /** La fila del buscador cuyo primer texto es [name] (sin marcas de dirección). */
    private fun row(root: UiNode, name: String): UiNode {
        val label = all(root).single { it.text?.toString()?.let(RecipientDetector::stripBidi) == name }
        var n: UiNode = label
        while (!(n.isClickable && n.className == "android.view.ViewGroup")) n = n.parent!!
        return n
    }

    @Test
    fun `la celda marca con la descripcion entera aunque el texto vaya cortado`() {
        val maria = cell(load("hoja"), "MARÍA FERNÁNDEZ LÓPEZ")
        assertEquals(Outcome.Toggled("MARÍA FERNÁNDEZ LÓPEZ", 1, "marcado (fila)"), click(maria))
        // El nombre de dentro (cortado, con marcas de dirección) también es pulsable: misma persona.
        val cut = all(maria).first { it !== maria && it.text != null }
        assertEquals(Outcome.Toggled("MARÍA FERNÁNDEZ LÓPEZ", 0, "desmarcado (fila)"), click(cut))
    }

    @Test
    fun `la foto de la celda y los nombres con emojis valen`() {
        val sheet = load("hoja")
        val disco = cell(sheet, "💿")
        val photo = all(disco).first { it.className == "android.widget.ImageView" }
        assertEquals(Outcome.Toggled("💿", 1, "marcado (fila)"), click(photo))
        assertEquals(Outcome.Toggled("lucia 🦇", 2, "marcado (fila)"), click(cell(sheet, "lucia 🦇")))
    }

    @Test
    fun `enviar con marcados apunta a todos y cierra la sesion`() {
        click(cell(load("hoja"), "💿"))
        click(cell(load("hoja"), "rubio_m"))
        val send = all(load("hoja-marcado")).single { it.text == "Enviar" }
        assertEquals(Outcome.Sent(listOf("💿", "rubio_m"), 7L, true, "botón enviar (2)"), click(send))
        assertFalse(detector.isOpen(2_000))
        assertNull(detector.platform)
    }

    @Test
    fun `enviar sin nadie marcado no apunta nada`() {
        val send = all(load("hoja-marcado")).single { it.text == "Enviar" }
        assertEquals(Outcome.Ignored("enviar sin destinatario"), click(send))
        // La hoja de TikTok se cierra al momento: la sesión también.
        assertFalse(detector.isOpen(2_000))
    }

    @Test
    fun `las opciones de debajo nunca son contactos`() {
        val sheet = load("hoja")
        val divider = all(sheet).single { it.bounds.height == 1 && it.bounds.width == UiDump.W }.bounds.bottom
        val buttons = all(sheet).filter { it.className == "android.widget.Button" && it.contentDescription != null }
        val options = buttons.filter { it.bounds.top >= divider }
        val contacts = buttons.filter { it.bounds.bottom < divider }
        assertEquals(12, options.size) // Compartir (repostear), WhatsApp, Copiar enlace… y Denunciar, Descargar, Dúo…
        assertEquals(7, contacts.size)
        options.forEach { assertTrue("${it.contentDescription}", click(it) is Outcome.Ignored) }
        assertEquals(0, detector.selectedCount)
        // De respaldo, por posición: todas van debajo de la línea; los contactos, no.
        options.forEach { assertTrue("${it.contentDescription}", detector.belowDivider(it)) }
        contacts.forEach { assertFalse("${it.contentDescription}", detector.belowDivider(it)) }
    }

    @Test
    fun `una opcion que no conocemos se ignora por estar debajo de la linea`() {
        fun option(label: String, left: Int) = FakeNode(
            contentDescription = label, className = "android.widget.Button", isClickable = true,
            bounds = box(left, 1856, left + 168, 2077),
            children = listOf(FakeNode(className = "android.widget.ImageView", bounds = box(left + 21, 1856, left + 147, 1982))),
        )
        val ana = FakeNode(
            contentDescription = "Ana", className = "android.widget.Button", isClickable = true, bounds = box(24, 1566, 187, 1786),
            children = listOf(FakeNode(className = "android.widget.ImageView", isClickable = true, bounds = box(42, 1566, 168, 1692))),
        )
        val line = option("LINE", 24)
        val other = option("Kakao", 203)
        FakeNode(
            bounds = box(0, 1424, 1080, 2361),
            children = listOf(
                FakeNode(bounds = box(0, 1424, 1080, 1561), children = listOf(text("Enviar a", box(0, 1463, 1080, 1521)))),
                FakeNode(bounds = box(0, 1561, 1080, 1813), children = listOf(FakeNode(bounds = box(0, 1566, 1080, 1787), children = listOf(ana)))),
                FakeNode(className = "android.view.View", bounds = box(0, 1813, 1080, 1814)),
                FakeNode(bounds = box(0, 1814, 1080, 2361), children = listOf(FakeNode(bounds = box(0, 1856, 1080, 2077), children = listOf(line, other)))),
            ),
        )
        assertEquals(Outcome.Ignored("debajo de los contactos (opción de la hoja)"), click(line))
        assertEquals(Outcome.Ignored("debajo de los contactos (opción de la hoja)"), click(other))
        assertEquals(Outcome.Toggled("Ana", 1, "marcado (fila)"), click(ana.children.single()))
    }

    @Test
    fun `los emojis de reaccion no se apuntan`() {
        val marked = load("hoja-marcado")
        assertEquals(Outcome.Toggled("💿", 1, "marcado (fila)"), click(cell(marked, "💿")))
        val emojis = all(marked).filter { it.parent?.className == "android.widget.GridView" }
        assertEquals(7, emojis.size)
        emojis.forEach { emoji ->
            assertEquals(Outcome.Ignored("emoji de reacción (no se apunta)"), click(emoji))
            assertEquals(Outcome.Ignored("emoji de reacción (no se apunta)"), click(emoji.children.single()))
        }
        assertEquals(1, detector.selectedCount)
    }

    @Test
    fun `cabecera, mensaje y fuera de la hoja se ignoran`() {
        val sheet = load("hoja")
        val header = listOf(
            all(sheet).single { it.contentDescription == "Buscar" },
            all(sheet).single { it.contentDescription == "Cerrar" },
            all(sheet).single { shortViewId(it.viewId) == "tv_title" },
            all(sheet).single { shortViewId(it.viewId) == "tv_title" }.parent!!,
            all(sheet).single { shortViewId(it.viewId) == "touch_outside" },
        )
        header.forEach { assertTrue(click(it) is Outcome.Ignored) }
        val marked = load("hoja-marcado")
        val field = all(marked).single { it.className == "android.widget.EditText" }
        assertEquals(Outcome.Ignored("campo de texto"), click(field))
        assertTrue(click(field.parent!!.parent!!) is Outcome.Ignored)
        assertEquals(0, detector.selectedCount)
    }

    @Test
    fun `un clic en el visor con la hoja abierta no es de la hoja`() {
        val feed = UiDump.load("tiktok-47.7-feed.xml", windowId = 1)
        val like = all(feed).single { it.contentDescription?.startsWith("Dar me gusta") == true }
        assertEquals(Outcome.Ignored("dentro del visor de vídeos, no en la hoja"), click(like))
        val share = all(feed).single { it.contentDescription?.startsWith("Compartir el vídeo") == true }
        assertTrue(detector.insideFeed(share, PlatformProfile.TIKTOK.feedIds))
        assertFalse("con los ids de Instagram no es su visor", detector.insideFeed(share))
    }

    @Test
    fun `en el buscador cuenta la fila con su primer texto`() {
        val search = load("buscador")
        assertEquals(Outcome.Toggled("rubio_m", 1, "marcado (buscador)"), click(row(search, "rubio_m")))
        // La foto de la fila también es pulsable.
        val photo = all(row(search, "💿")).first { it.className == "android.widget.ImageView" }
        assertEquals(Outcome.Toggled("💿", 2, "marcado (buscador)"), click(photo))
        assertEquals(Outcome.Ignored("campo de texto"), click(all(search).single { it.className == "android.widget.EditText" }))
        assertTrue(click(all(search).single { it.contentDescription == "Cerrar" }) is Outcome.Ignored)
        val send = all(load("buscador-marcado")).single { shortViewId(it.viewId) == "tv_send" }
        assertEquals(Outcome.Sent(listOf("rubio_m", "💿"), 7L, true, "botón enviar (2)"), click(send))
    }

    @Test
    fun `en el buscador el usuario de la fila no es un enviar ni una opcion`() {
        // El volcado real del buscador con el usuario de rubio_m («rubio_m72», debajo de su nombre) cambiado por una palabra
        // de «Enviar» o de una opción. Tocar la fila marca a rubio_m; nunca envía ni cierra la sesión.
        val xml = javaClass.classLoader!!.getResourceAsStream("calibration/tiktok-47.7-buscador.xml")!!.use { it.readBytes().toString(Charsets.UTF_8) }
        check("rubio_m72" in xml)
        listOf("send", "enviar", "invia", "audio", "status").forEach { user ->
            detector.start(now = 0, reelId = 7, profile = PlatformProfile.TIKTOK)
            val search = UiDump.parse(xml.replace("rubio_m72", user), windowId = 2)
            assertEquals(user, Outcome.Toggled("💿", 1, "marcado (buscador)"), click(row(search, "💿")))
            assertEquals(user, Outcome.Toggled("rubio_m", 2, "marcado (buscador)"), click(row(search, "rubio_m")))
            assertTrue(user, detector.isOpen(2_000))
        }
    }

    @Test
    fun `un clic sin nodo solo se mira en TikTok y en Instagram se descarta como en la 1_9_0`() {
        // TikTok: un nombre sin nodo no marca a nadie (sin fila ni celda); «Enviar» sin nodo sí vale (TikTokSentBannerTest).
        assertTrue(detector.acceptsNodelessClicks)
        assertTrue(click(EventUiNode("Ana", null, "android.widget.TextView")) is Outcome.Ignored)

        // Instagram: sin nodo no se sabe si viene del visor (el nombre del creador, su foto…). Ni marca, ni envía, ni deshace.
        val instagram = RecipientDetector().apply { start(now = 0, reelId = 42) }
        assertFalse(instagram.acceptsNodelessClicks)
        val nodeless = listOf(
            EventUiNode("creador_ejemplo", null, "android.widget.TextView"),
            EventUiNode(null, "Foto del perfil de otro_creador", "android.widget.ImageView"),
            EventUiNode("Ana García anagarcia", null, "android.widget.LinearLayout"),
            EventUiNode(null, "Enviar", "android.widget.Button"),
            EventUiNode("Deshacer", null, "android.widget.Button"),
        )
        nodeless.forEach { assertEquals("${it.text ?: it.contentDescription}", Outcome.Ignored("clic sin nodo"), instagram.onClick(it, SCREEN_H, 1_000)) }
        assertEquals(0, instagram.selectedCount)
        assertTrue(instagram.isOpen(1_000))
        assertFalse("sin sesión, tampoco", RecipientDetector().acceptsNodelessClicks)
    }

    @Test
    fun `al escribir salen grupos y cuentas parecidas con su nombre entero`() {
        val typed = load("buscador-escrito")
        assertEquals(Outcome.Toggled("Carlos, Bea, rubio_m", 1, "marcado (buscador)"), click(row(typed, "Carlos, Bea, rubio_m")))
        assertEquals(
            Outcome.Toggled("ElRubencio Cuenta secundaria", 2, "marcado (buscador)"),
            click(row(typed, "ElRubencio Cuenta secundaria")),
        )
        assertEquals(Outcome.Toggled("rubio_m", 3, "marcado (buscador)"), click(row(typed, "rubio_m")))
    }

    @Test
    fun `de la fila del buscador, el boton tv_send vale como enviar por su id`() {
        val marked = load("buscador-marcado")
        val tvSend = all(marked).single { shortViewId(it.viewId) == "tv_send" }
        assertTrue(RecipientDetector.isSendId(tvSend, PlatformProfile.TIKTOK))
        assertFalse(RecipientDetector.isSendId(tvSend))
        assertFalse(RecipientDetector.isSendId(tvSend, PlatformProfile.INSTAGRAM))
    }

    @Test
    fun `el selected de una celda reciclada no cuenta`() {
        val recycled = load("hoja-celda-reciclada")
        // pablo_grc lleva selected=true sin CheckBox: no estaba marcado. Al tocarlo, se marca (y es el único).
        assertEquals(Outcome.Toggled("pablo_grc", 1, "marcado (fila)"), click(cell(recycled, "pablo_grc")))
        assertFalse(detector.belowDivider(cell(recycled, "pablo_grc")))
    }

    @Test
    fun `la sesion dice su app`() {
        assertEquals(Platform.TIKTOK, detector.platform)
        detector.start(now = 0, reelId = 1)
        assertEquals(Platform.INSTAGRAM, detector.platform)
        detector.stop()
        assertNull(detector.platform)
    }
}
