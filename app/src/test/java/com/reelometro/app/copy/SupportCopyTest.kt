package com.reelometro.app.copy

import com.reelometro.app.AppDistribution
import com.reelometro.app.AppLinks
import com.reelometro.app.Distribution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El aviso del proyecto en sus dos distribuciones: en Google Play no puede invitar a apoyar ni a donar
 * (política de pagos de Play) en ningún idioma; en GitHub sigue como siempre.
 */
class SupportCopyTest {

    /** Idiomas de la app (`values/` y cada `values-xx/`). */
    private val qualifiers: List<String?> = listOf(null) + JvmTexts.resDir().listFiles().orEmpty()
        .map { it.name }
        .mapNotNull { Regex("values-([a-z]{2,3}(-r[A-Z]{2})?|b\\+[A-Za-z0-9+]+)").matchEntire(it)?.groupValues?.get(1) }
        .sorted()

    /** Palabras de pedir apoyo, donar o pagar, en todos los idiomas (raíces, sin distinguir mayúsculas). */
    private val forbidden = listOf(
        "apoy", "apoi", // es, pt
        "support", "donat", "coffee", "tip", "sponsor", // en
        "soutien", "souten", "faire un don", "café", // fr (y es, pt)
        "sosteni", "sostien", "donazi", "caffè", // it
        "donar", "donaci", "doaç", "doar", // es, pt
        "patreon", "ko-fi", "paypal", "buy me",
    )

    private fun <T> inLanguage(qualifier: String?, block: () -> T): T =
        AppTexts.using(JvmTexts.forQualifier(qualifier), block)

    @Test
    fun `hay al menos los cinco idiomas`() {
        assertTrue(qualifiers.containsAll(listOf(null, "en", "fr", "it", "pt-rBR")))
    }

    @Test
    fun `en Google Play ningun texto del aviso invita a apoyar ni a pagar`() {
        for (q in qualifiers) {
            val texts = inLanguage(q) { SupportCopy.all(Distribution.PLAY, AppLinks.PROJECT_URL) }
            for (text in texts) {
                assertTrue("vacío en ${q ?: "es"}", text.isNotBlank())
                val lower = text.lowercase()
                for (word in forbidden) {
                    assertFalse("«$text» (${q ?: "es"}) contiene «$word»", lower.contains(word))
                }
            }
        }
    }

    @Test
    fun `en Google Play el aviso habla del codigo en GitHub`() {
        for (q in qualifiers) {
            inLanguage(q) {
                assertTrue(SupportCopy.text(Distribution.PLAY).contains("GitHub"))
                assertTrue(SupportCopy.openDescription(Distribution.PLAY).contains("GitHub"))
            }
        }
        assertEquals("VER EL CÓDIGO", SupportCopy.open(Distribution.PLAY))
        assertEquals("Sobre Reelometer", SupportCopy.paneTitle(Distribution.PLAY))
    }

    @Test
    fun `en GitHub el aviso es el de siempre`() {
        assertEquals("VER SU GITHUB", SupportCopy.open(Distribution.GITHUB))
        assertEquals("Apoyar Reelometer", SupportCopy.paneTitle(Distribution.GITHUB))
        assertTrue(SupportCopy.text(Distribution.GITHUB).endsWith("si te apetece, apoyarlo."))
        assertEquals("Ver su GitHub. Se abre en el navegador", SupportCopy.openDescription(Distribution.GITHUB))
        for (q in qualifiers) {
            inLanguage(q) {
                assertNotEquals(SupportCopy.text(Distribution.GITHUB), SupportCopy.text(Distribution.PLAY))
                assertNotEquals(SupportCopy.open(Distribution.GITHUB), SupportCopy.open(Distribution.PLAY))
                assertNotEquals(SupportCopy.paneTitle(Distribution.GITHUB), SupportCopy.paneTitle(Distribution.PLAY))
            }
        }
    }

    @Test
    fun `los textos por defecto son los de esta build`() {
        assertEquals(Distribution.GITHUB, AppDistribution.current) // los tests compilan con la distribución por defecto
        assertEquals(SupportCopy.text(AppDistribution.current), SupportCopy.TEXT)
        assertEquals(SupportCopy.open(AppDistribution.current), SupportCopy.OPEN)
        assertEquals(SupportCopy.paneTitle(AppDistribution.current), SupportCopy.PANE_TITLE)
        assertEquals(SupportCopy.openDescription(AppDistribution.current), SupportCopy.OPEN_DESCRIPTION)
    }

    @Test
    fun `distribucion desconocida es un error`() {
        assertEquals(Distribution.PLAY, Distribution.fromId("play"))
        assertEquals(Distribution.GITHUB, Distribution.fromId("github"))
        assertTrue(runCatching { Distribution.fromId("amazon") }.isFailure)
    }
}
