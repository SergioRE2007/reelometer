package com.reelometro.app.detection

import com.reelometro.app.detection.RecipientDetector.Outcome
import com.reelometro.app.detection.RecipientDetector.SentBanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.10.1: la sesión que empieza con la hoja ya abierta (un envío rápido la dejó así) lee los avisos viejos a ciegas. Medido
 * (TikTok 47.7.3): cada aviso «Enviado a …» va en una ventana nueva (los ids crecen: 6437, 6458, 6459…), dura unos 5 s y
 * conserva su ventana bajo la hoja. Y los avisos en más idiomas (prefijo fijo + nombre; chino, pegado).
 */
class TikTokSentBannerACiegasTest {

    private fun banners(vararg labels: String, window: Int): UiNode =
        FakeNode(windowId = window, children = labels.map { FakeNode(text = it, className = "android.widget.TextView", bounds = box(40, 2200, 1040, 2260), windowId = window) })

    private fun read(vararg roots: UiNode) = RecipientDetector.sentBanners(roots.toList(), FakeFinder())

    private val lrm = Char(0x200E).toString()

    /**
     *  t=0 s    envío rápido a 💿 que sale bien: aviso «Enviado a 💿» en la ventana 6458; el servicio lo lee y guarda 6458.
     *  t=1 s    otro envío rápido (a rubio_m) que falla y deja su hoja abierta (ventana 6460): la sesión empieza a ciegas.
     *  t=2,5 s  se marca a rubio_m y se cierra con la «X»: solo se ve el aviso viejo (6458). No vale.
     */
    @Test
    fun `a ciegas, cerrar con la X con el aviso de un envio anterior no apunta nada`() {
        val memory = SentBannerWindows()
        memory.remember(read(banners(lrm + "Enviado a 💿", window = 6458)).map { it.windowId }, now = 150)
        val detector = RecipientDetector().apply { start(now = 1_000, reelId = 7, preselected = listOf("rubio_m"), profile = PlatformProfile.TIKTOK) }
        // Lectura a ciegas: con la hoja encima no se ve nada; se guarda la ventana de la hoja.
        val before = emptySet<SentBanner>()
        val sheetWindow = 6460
        val selection = detector.tikTokSelection(2_000)!!

        val seen = read(banners(lrm + "Enviado a 💿", window = 6458))
        assertEquals("sin las reglas nuevas se tomaría por nuevo", 1, RecipientDetector.freshSentBanners(before, seen).size)
        assertTrue("por la memoria", RecipientDetector.freshSentBanners(before, seen, memory.known(2_500), newerThan = null).isEmpty())
        assertTrue("por la ventana de la hoja", RecipientDetector.freshSentBanners(before, seen, emptySet(), newerThan = sheetWindow).isEmpty())
        val fresh = RecipientDetector.freshSentBanners(before, seen, memory.known(2_500), sheetWindow)
        assertEquals(Outcome.Ignored("hoja cerrada sin enviar"), detector.onTikTokSheetClosed(selection, fresh.isNotEmpty()))
    }

    @Test
    fun `a ciegas, un envio de verdad desde la hoja se apunta aunque siga el aviso viejo`() {
        val memory = SentBannerWindows().apply { remember(listOf(6458), now = 150) }
        val detector = RecipientDetector().apply { start(now = 1_000, reelId = 7, preselected = listOf("💿"), profile = PlatformProfile.TIKTOK) }
        val selection = detector.tikTokSelection(2_000)!!
        // Mismo texto que el viejo, pero en una ventana nueva, posterior a la hoja (6460).
        val seen = read(banners(lrm + "Enviado a 💿", window = 6461), banners(lrm + "Enviado a 💿", window = 6458))
        val fresh = RecipientDetector.freshSentBanners(emptySet(), seen, memory.known(2_500), newerThan = 6460)
        assertEquals(setOf(SentBanner("enviado a 💿", 6461)), fresh)
        val outcome = detector.onTikTokSheetClosed(selection, fresh.isNotEmpty())
        assertTrue(outcome is Outcome.Sent && outcome.names == listOf("💿"))
    }

    @Test
    fun `un aviso sin ventana conocida no se descarta por la memoria ni por la hoja`() {
        val seen = setOf(SentBanner("enviado a ana", UiNode.NO_WINDOW))
        assertEquals(seen, RecipientDetector.freshSentBanners(emptySet(), seen, setOf(6458), newerThan = 6460))
    }

    @Test
    fun `la memoria de ventanas esta acotada en tiempo y en numero`() {
        val memory = SentBannerWindows(keepMs = 10_000, max = 3)
        memory.remember(listOf(1, UiNode.NO_WINDOW), now = 0)
        assertEquals(setOf(1), memory.known(0))
        assertEquals(setOf(1), memory.known(10_000))
        assertEquals("pasados ~10 s, se olvida", emptySet<Int>(), memory.known(10_001))
        memory.remember(listOf(2, 3, 4, 5), now = 20_000)
        assertEquals("solo las últimas", setOf(3, 4, 5), memory.known(20_000))
        // Volver a verla la renueva.
        memory.remember(listOf(3), now = 25_000)
        assertEquals(setOf(3), memory.known(30_500))
    }

    // ── Más idiomas ─────────────────────────────────────────────────────────────────────────────

    private val nuevos = listOf(
        "Enviado para Ana", "Enviada para Ana", "Envoyé à Ana", "Envoyée à Ana", "Gesendet an Ana", "Inviato a Ana", "Inviata a Ana",
        "Verzonden naar Ana", "Verstuurd naar Ana", "Wysłano do Ana", "تم الإرسال إلى Ana", "Terkirim ke Ana", "Dikirim ke Ana",
        "Dihantar kepada Ana", "Dihantar ke Ana", "Đã gửi đến Ana", "Đã gửi tới Ana", "已发送给Ana", "已发送给 Ana", "已傳送給小明",
        "Ipinadala kay Ana", "Naipadala sa Ana", "Trimis către Ana", "Enviado a Ana", "Sent to Ana", lrm + "已发送给💿",
    )

    @Test
    fun `los avisos de los demas idiomas se reconocen y alguna consulta los encuentra`() {
        nuevos.forEach { banner ->
            assertTrue(banner, RecipientDetector.isSentBanner(banner))
            val root = banners(banner, window = 50)
            assertTrue(banner, RecipientDetector.SENT_BANNER_QUERIES.any { q -> FakeFinder().byText(root, q).isNotEmpty() })
            assertEquals(banner, setOf(RecipientDetector.normalize(banner)), read(root).mapTo(LinkedHashSet()) { it.text })
        }
    }

    @Test
    fun `solo se consultan los idiomas del sistema mas espanol e ingles`() {
        val es = RecipientDetector.sentBannerQueriesFor(listOf("es-ES"))
        assertEquals(listOf("Enviado", "Enviada", "Sent to"), es)
        assertTrue("no más que las 7 de antes", es.size <= 7)
        assertEquals(es, RecipientDetector.sentBannerQueriesFor(emptyList()))
        assertEquals(listOf("Envoyé", "Enviado", "Enviada", "Sent to"), RecipientDetector.sentBannerQueriesFor(listOf("fr")))
        assertEquals(listOf("已发送给", "已傳送給", "Enviado", "Enviada", "Sent to"), RecipientDetector.sentBannerQueriesFor(listOf("zh-TW")))
        // Todos los de la lista, sin repetir; los que no hay, nada.
        assertEquals(
            listOf("Gesendet an", "Enviado", "Enviada", "Inviat", "Sent to"),
            RecipientDetector.sentBannerQueriesFor(listOf("de-DE", "pt_BR", "it", "xx", "en-US")),
        )
        assertEquals(listOf("Terkirim ke", "Dikirim ke", "Enviado", "Enviada", "Sent to"), RecipientDetector.sentBannerQueriesFor(listOf("in-ID")))
    }

    @Test
    fun `cada forma de la regla se encuentra con las consultas de su idioma`() {
        RecipientDetector.SENT_BANNER_LANGUAGES.forEach { lang ->
            val queries = RecipientDetector.sentBannerQueriesFor(listOf(lang.codes.first()))
            lang.prefixes.forEach { prefix ->
                val forms = if (lang.joined) listOf(prefix + "小明", "$prefix Ana") else listOf("$prefix Ana", lrm + "$prefix 💿")
                forms.forEach { banner ->
                    assertTrue(banner, RecipientDetector.isSentBanner(banner))
                    val root = banners(banner, window = 60)
                    assertEquals(banner, 1, RecipientDetector.sentBanners(listOf(root), FakeFinder(), queries).size)
                }
            }
        }
    }

    @Test
    fun `sin espacio solo valen los prefijos chinos y sin nombre nada`() {
        listOf("Sent toAna", "Enviado aAna", "Gesendet anAna", "Wysłano doAna").forEach { assertFalse(it, RecipientDetector.isSentBanner(it)) }
        listOf("Enviado a", "已发送给", "已发送给 ", "Verzonden naar", "Trimis către ").forEach { assertFalse(it, RecipientDetector.isSentBanner(it)) }
        // Japonés y coreano (el nombre delante) o sin nombre: no se han añadido.
        listOf("Anaに送信しました", "Ana님에게 보냄", "Gönderildi").forEach { assertFalse(it, RecipientDetector.isSentBanner(it)) }
    }

    @Test
    fun `el pie del video sigue sin valer en los demas idiomas`() {
        assertFalse(RecipientDetector.isSentBanner("Gesendet an meine beste Freundin #fyp"))
        assertFalse(RecipientDetector.isSentBanner("已发送给我的朋友\n第二行"))
        assertFalse(RecipientDetector.isSentBanner("Verzonden naar " + "een hele lange lijst met vrienden ".repeat(4)))
        // Con el id del pie (`desc`), nunca, aunque parezca el aviso.
        val caption = FakeNode(text = "Verzonden naar Ana", className = "android.widget.TextView", viewId = "com.zhiliaoapp.musically:id/desc", windowId = 1)
        assertTrue(read(FakeNode(windowId = 1, children = listOf(caption))).isEmpty())
    }
}
