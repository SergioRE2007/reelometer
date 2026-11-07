package com.reelometro.app.detection

import com.reelometro.app.detection.RecipientDetector.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.10.0, medido en el móvil (TikTok 47.7): el «Enviar» de la hoja la cierra **sin** `TYPE_VIEW_CLICKED` (por la mañana; por la tarde sí llegó, sin nodo). «A quién
 * envías» apunta el envío a mano por el aviso «Enviado a …» que TikTok pone abajo al cerrarse la hoja.
 */
class TikTokSentBannerTest {

    private val detector = RecipientDetector().apply { start(now = 0, reelId = 7, profile = PlatformProfile.TIKTOK) }

    private fun cell(name: String): UiNode =
        UiDump.all(UiDump.load("tiktok-47.7-hoja.xml", windowId = 2))
            .single { it.contentDescription?.toString() == name && it.className == "android.widget.Button" }

    @Test
    fun `el aviso de enviado se reconoce y el titulo de la hoja no`() {
        assertTrue(RecipientDetector.isSentBanner("Enviado a 💿"))
        assertTrue(RecipientDetector.isSentBanner("Sent to Ana"))
        assertTrue(RecipientDetector.isSentBanner(Char(0x200E) + "Enviado a Carlos, Bea"))
        assertFalse(RecipientDetector.isSentBanner("Enviar a"))
        assertFalse(RecipientDetector.isSentBanner("Enviado"))
        assertFalse(RecipientDetector.isSentBanner("Enviar"))
        assertFalse(RecipientDetector.isSentBanner("Escribe un mensaje..."))
    }

    @Test
    fun `con el aviso se apunta a los marcados y la sesion se cierra`() {
        detector.onClick(cell("💿"), UiDump.H, 1_000)
        detector.onClick(cell("rubio_m"), UiDump.H, 1_100)
        val selection = detector.tikTokSelection(2_000)!!
        assertEquals(listOf("💿", "rubio_m"), selection.names)
        val sent = detector.onTikTokSheetClosed(selection, sentBanner = true)
        assertEquals(Outcome.Sent(listOf("💿", "rubio_m"), 7, closesSession = true, rule = "aviso de enviado (2)"), sent)
        assertFalse(detector.isOpen(2_100))
    }

    @Test
    fun `sin el aviso (cerrada con la X o atras) no se apunta nada`() {
        detector.onClick(cell("💿"), UiDump.H, 1_000)
        val selection = detector.tikTokSelection(2_000)!!
        assertEquals(Outcome.Ignored("hoja cerrada sin enviar"), detector.onTikTokSheetClosed(selection, sentBanner = false))
        assertFalse(detector.isOpen(2_100))
    }

    @Test
    fun `sin marcados o en Instagram no hay nada que comprobar`() {
        assertNull("sin nadie marcado", detector.tikTokSelection(1_000))
        val instagram = RecipientDetector().apply { start(now = 0, reelId = 1) }
        assertNull(instagram.tikTokSelection(1_000))
    }

    @Test
    fun `una seleccion vieja no cierra la sesion nueva`() {
        detector.onClick(cell("💿"), UiDump.H, 1_000)
        val old = detector.tikTokSelection(2_000)!!
        detector.start(now = 3_000, reelId = 8, profile = PlatformProfile.TIKTOK)
        detector.onTikTokSheetClosed(old, sentBanner = false)
        assertTrue("la sesión de después sigue abierta", detector.isOpen(3_100))
    }

    /** El volcado real del feed con el pie del vídeo cambiado por [caption]. */
    private fun feedWithCaption(caption: String): UiNode {
        val original = "Un vídeo de ejemplo #ejemplo "
        val xml = javaClass.classLoader!!.getResourceAsStream("calibration/tiktok-47.7-feed.xml")!!.use { it.readBytes().toString(Charsets.UTF_8) }
        check(original in xml)
        return UiDump.parse(xml.replace(original, caption), windowId = 1)
    }

    /**
     * Una ventana [window] con estos avisos abajo, como el que pone TikTok tras enviar: cada aviso en su propia ventana, nueva
     * (medido: 6437, 6458, 6459), fuera de la hoja.
     */
    private fun banners(vararg labels: String, window: Int = 31): UiNode =
        FakeNode(windowId = window, children = labels.map { FakeNode(text = it, className = "android.widget.TextView", bounds = box(40, 2200, 1040, 2260), windowId = window) })

    private fun texts(banners: Set<RecipientDetector.SentBanner>): Set<String> = banners.mapTo(LinkedHashSet()) { it.text }

    @Test
    fun `el pie del video no es el aviso aunque empiece por Sent to`() {
        assertFalse(RecipientDetector.isSentBanner("Sent to my bestie and she never replied 😂 #fyp"))
        assertFalse(RecipientDetector.isSentBanner("Enviado a mi grupo de amigas y nadie contesta #humor"))
        assertFalse(RecipientDetector.isSentBanner("Sent to my bestie\nand she never replied"))
        assertFalse(RecipientDetector.isSentBanner("Enviado a " + "una lista muy larga de amigas ".repeat(4)))
        // Como lo busca el servicio en el feed real: el pie (id `desc`) no vale, lleve «#» o no.
        listOf("Sent to my bestie and she never replied 😂 #fyp", "Enviado a mi grupo de amigas", "Sent to Ana").forEach { caption ->
            assertEquals(caption, emptySet<String>(), texts(RecipientDetector.sentBanners(listOf(feedWithCaption(caption)), FakeFinder())))
        }
        // El aviso de verdad, con el mismo feed debajo, sí.
        val feed = feedWithCaption("Sent to my bestie and she never replied 😂 #fyp")
        assertEquals(setOf("enviado a 💿"), texts(RecipientDetector.sentBanners(listOf(banners(Char(0x200E) + "Enviado a 💿"), feed), FakeFinder())))
    }

    @Test
    fun `cada aviso que reconoce la regla se encuentra con alguna consulta`() {
        val all = listOf(
            "Enviado a Ana", "Enviada a Ana", "Enviado para Ana", "Sent to Ana", "Envoyé à Ana", "Inviato a Ana", "Gesendet an Ana",
            Char(0x200E) + "Enviado a 💿",
        )
        all.forEach { banner ->
            assertTrue(banner, RecipientDetector.isSentBanner(banner))
            val root = banners(banner)
            // Lo que hace el servicio: el atajo de texto con cada consulta (contiene, sin mayúsculas, con tildes).
            assertTrue(banner, RecipientDetector.SENT_BANNER_QUERIES.any { q -> FakeFinder().byText(root, q).isNotEmpty() })
            assertEquals(banner, setOf(RecipientDetector.normalize(banner)), texts(RecipientDetector.sentBanners(listOf(root), FakeFinder())))
        }
    }

    @Test
    fun `solo vale un aviso nuevo y no el que sigue en pantalla de un envio anterior`() {
        val feed = UiDump.load("tiktok-47.7-feed.xml", windowId = 1)
        fun read(vararg labels: String, window: Int) = RecipientDetector.sentBanners(listOf(banners(*labels, window = window), feed), FakeFinder())

        // Se envió a 💿 hace un momento y su aviso sigue abajo (ventana 40). Se vuelve a abrir la hoja, se marca a rubio_m y se
        // cierra con la «X»: la lectura del principio (al pulsar «Compartir») ya vio ese aviso, así que no es de este cierre.
        detector.onClick(cell("rubio_m"), UiDump.H, 1_000)
        val selection = detector.tikTokSelection(2_000)!!
        val before = read(Char(0x200E) + "Enviado a 💿", window = 40)
        assertEquals(setOf(RecipientDetector.SentBanner("enviado a 💿", 40)), before)
        val stale = RecipientDetector.freshSentBanners(before, read(Char(0x200E) + "Enviado a 💿", window = 40)).isNotEmpty()
        assertFalse(stale)
        assertEquals(Outcome.Ignored("hoja cerrada sin enviar"), detector.onTikTokSheetClosed(selection, stale))

        // Con un envío de verdad, su aviso sale en otra ventana: ese sí vale.
        assertTrue(RecipientDetector.freshSentBanners(before, read(Char(0x200E) + "Enviado a rubio_m", window = 41)).isNotEmpty())
        assertTrue(RecipientDetector.freshSentBanners(emptySet(), read(Char(0x200E) + "Enviado a 💿", window = 41)).isNotEmpty())
        assertTrue(RecipientDetector.freshSentBanners(emptySet(), read(window = 41)).isEmpty())
    }

    /**
     * Verificación final de la 1.10.0: comparando solo el texto, el aviso viejo visto al pulsar «Compartir» dejaba sin apuntar
     * cualquier envío a esa misma persona desde la hoja, por mucho que se tardara en enviar. El aviso nuevo sale en otra ventana.
     *  t=0 s    envío a 💿; TikTok pone «Enviado a 💿» abajo (ventana 40; dura unos 5 s).
     *  t=2 s    vídeo siguiente y «Compartir»: la lectura del principio ve aún ese aviso.
     *  t=3 s    se marca a 💿 (con la hoja encima no se ve nada) y se pulsa «Enviar»: sale el aviso NUEVO (ventana 41), con el
     *           mismo texto, y el viejo puede seguir abajo.
     */
    @Test
    fun `un envio a mano a la misma persona tras compartir con su aviso viejo a la vista se apunta`() {
        val feed = UiDump.load("tiktok-47.7-feed.xml", windowId = 1)
        val share = RecipientDetector().apply { start(now = 2_000, reelId = 8, profile = PlatformProfile.TIKTOK) }

        // snapshotSentBanners (al pulsar «Compartir»): el aviso del envío anterior sigue abajo.
        var before = RecipientDetector.sentBanners(listOf(banners(Char(0x200E) + "Enviado a 💿", window = 40), feed), FakeFinder())
        // noteStaleSentBanners (al marcar, con la hoja encima): no ve nada.
        share.onClick(cell("💿"), UiDump.H, 3_000)
        before = before + RecipientDetector.sentBanners(listOf(UiDump.load("tiktok-47.7-hoja-marcado.xml", windowId = 2)), FakeFinder())
        assertEquals(setOf(RecipientDetector.SentBanner("enviado a 💿", 40)), before)
        val selection = share.tikTokSelection(3_100)!!

        // watchSheetClose: la hoja ya no está; el aviso viejo sigue y sale el de ESTE envío, en su ventana.
        val roots = listOf(banners(Char(0x200E) + "Enviado a 💿", window = 41), banners(Char(0x200E) + "Enviado a 💿", window = 40), feed)
        val fresh = RecipientDetector.freshSentBanners(before, RecipientDetector.sentBanners(roots, FakeFinder()))
        assertEquals(setOf(RecipientDetector.SentBanner("enviado a 💿", 41)), fresh)
        val outcome = share.onTikTokSheetClosed(selection, fresh.isNotEmpty())
        assertTrue("envío real perdido: $outcome", outcome is Outcome.Sent)
        assertEquals(listOf("💿"), (outcome as Outcome.Sent).names)
    }

    /** El caso (b) del móvil, por mucho que dure el aviso viejo: con la «X», solo se ve ese (su misma ventana) y no vale. */
    @Test
    fun `el aviso viejo no vale aunque siga en pantalla mucho rato y sin ventana basta el texto`() {
        val old = setOf(RecipientDetector.SentBanner("enviado a 💿", 40))
        assertTrue(RecipientDetector.freshSentBanners(old, setOf(RecipientDetector.SentBanner("enviado a 💿", 40))).isEmpty())
        // Otra ventana: otro aviso, aunque diga lo mismo.
        assertEquals(1, RecipientDetector.freshSentBanners(old, setOf(RecipientDetector.SentBanner("enviado a 💿", 41))).size)
        // Sin ventana conocida (NO_WINDOW) no se distinguen: mejor no apuntar que inventar.
        val unknown = setOf(RecipientDetector.SentBanner("enviado a 💿", UiNode.NO_WINDOW))
        assertTrue(RecipientDetector.freshSentBanners(unknown, setOf(RecipientDetector.SentBanner("enviado a 💿", 41))).isEmpty())
        assertTrue(RecipientDetector.freshSentBanners(old, unknown).isEmpty())
    }

    @Test
    fun `un clic sin nodo con el texto Enviar envia a los marcados`() {
        // Lo que construye el servicio con el texto del evento cuando `event.source` llega a null.
        detector.onClick(cell("💿"), UiDump.H, 1_000)
        val send = object : UiNode {
            override val text: CharSequence = "Enviar"
            override val contentDescription: CharSequence? = null
            override val viewId: String? = null
            override val className: CharSequence = "android.widget.Button"
            override val bounds = NodeBounds.EMPTY
            override val isVisible = true
            override val children = emptyList<UiNode>()
            override val parent: UiNode? = null
        }
        val out = detector.onClick(send, UiDump.H, 1_500)
        assertTrue(out is Outcome.Sent && out.names == listOf("💿"))
    }
}
