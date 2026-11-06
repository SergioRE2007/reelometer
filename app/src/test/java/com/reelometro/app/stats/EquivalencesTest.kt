package com.reelometro.app.stats

import com.reelometro.app.copy.CopyRules
import com.reelometro.app.copy.CopyRules.plain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EquivalencesTest {

    private val min = 60_000L
    private val hour = 3_600_000L

    private fun amount(e: Equivalence, hours: Double) = plain(e.value(hours).amountText)

    @Test
    fun `catálogo completo y con fuentes`() {
        val all = Equivalences.ALL
        assertTrue("Lista corta: ${all.size}", all.size >= 20)
        assertEquals("ids repetidos", all.size, all.map { it.id }.toSet().size)
        all.forEach { e ->
            assertTrue("${e.id}: horas", e.hoursEach > 0)
            assertTrue("${e.id}: base «${e.basis}»", e.basis.startsWith("Base: ") && e.basis.length > 12)
            assertTrue("${e.id}: emoji", e.emoji.isNotBlank() && CopyRules.containsEmoji(e.emoji))
            assertEquals("${e.id}: etiqueta en mayúsculas", Format.upper(e.label), e.label)
            assertTrue("${e.id}: base con reglas ${CopyRules.violations(e.basis)}", CopyRules.violations(e.basis).isEmpty())
        }
        // Las que pide el briefing
        listOf(
            Equivalences.LANGUAGE, Equivalences.DEGREE, Equivalences.MASTER, Equivalences.BOOK, Equivalences.QUIJOTE,
            Equivalences.GOT, Equivalences.MOVIE, Equivalences.MARATHON, Equivalences.CAMINO, Equivalences.TOLEDO,
            Equivalences.TORTILLA, Equivalences.NAP, Equivalences.SLEEP,
        ).forEach { assertTrue(it.id, it in all) }
        assertEquals(plain("Base: 240 ECTS × 25 h (Plan Bolonia)"), plain(Equivalences.DEGREE.basis))
        assertTrue(Equivalences.LANGUAGE.basis.startsWith("Base: FSI"))
    }

    @Test
    fun `cálculos`() {
        assertEquals(6_000.0, Equivalences.DEGREE.hoursEach, 0.0)
        assertEquals(1_500.0, Equivalences.MASTER.hoursEach, 0.0)
        assertEquals(600.0, Equivalences.LANGUAGE.hoursEach, 0.0)
        assertEquals(6.0, Equivalences.BOOK.hoursEach, 0.0)
        assertEquals(195.0, Equivalences.CAMINO.hoursEach, 0.0)
        assertEquals(14.4, Equivalences.TOLEDO.hoursEach, 1e-9)
        assertEquals(0.5, Equivalences.DEGREE.value(3_000.0).amount, 1e-9)
        assertEquals(14.0, Equivalences.BOOK.forDuration(84 * hour).amount, 1e-9)
        val v = Equivalences.forDuration(61 * hour)
        assertEquals(Equivalences.ALL.size, v.size)
        v.forEach { assertEquals(61.0 / it.equivalence.hoursEach, it.amount, 1e-9) }
    }

    @Test
    fun `singular, plural y medios`() {
        assertEquals("un libro", amount(Equivalences.BOOK, 6.0))
        assertEquals("14 libros", amount(Equivalences.BOOK, 84.0))
        assertEquals("un libro y medio", amount(Equivalences.BOOK, 9.0))
        assertEquals("2 idiomas y medio", amount(Equivalences.LANGUAGE, 1_500.0))
        assertEquals("un grado universitario y medio", amount(Equivalences.DEGREE, 9_000.0))
        assertEquals("una película", amount(Equivalences.MOVIE, 2.0))
        assertEquals("una película y media", amount(Equivalences.MOVIE, 3.0))
        assertEquals("0 libros", amount(Equivalences.BOOK, 0.0))
        assertEquals("un libro", amount(Equivalences.BOOK, 5.8)) // 0,97 ya se lee «un libro»
        assertEquals("10 libros", amount(Equivalences.BOOK, 61.0)) // a partir de 10, entero
    }

    @Test
    fun `cantidades como se dicen, no como se calculan`() {
        assertEquals("más de 3 libros", amount(Equivalences.BOOK, 19.2))
        assertEquals("más de un máster", amount(Equivalences.MASTER, 1_650.0))
        assertEquals("más de 3 másteres", amount(Equivalences.MASTER, 5_000.0))
        assertEquals("casi 5 huevos duros", amount(Equivalences.EGG, 47 / 60.0))
        assertEquals("una siesta y media", amount(Equivalences.NAP, 47 / 60.0))
        assertEquals("casi 2 siestas", amount(Equivalences.NAP, 0.9))
        assertEquals("una tortilla y media de patatas", amount(Equivalences.TORTILLA, 1.125))
        assertEquals("2 tortillas y media de patatas", amount(Equivalences.TORTILLA, 1.875))
        assertEquals("un vaso y medio de leche en el microondas", amount(Equivalences.MILK, 1.5 / 60))
        assertEquals("medio vaso de leche en el microondas", amount(Equivalences.MILK, 0.5 / 60))
        assertEquals("un partido de fútbol", amount(Equivalences.FOOTBALL, 1.75))
        assertEquals("casi 4 kilómetros", amount(Equivalences.WALK, 0.76))
        assertEquals("4 kilómetros", amount(Equivalences.WALK, 47 / 60.0))
        assertEquals("el Quijote entero más de una vez", amount(Equivalences.QUIJOTE, Equivalences.QUIJOTE.hoursEach * 1.1))
        assertEquals("Juego de Tronos entera más de 4 veces", amount(Equivalences.GOT, 70 * 4.3))
        assertEquals("Juego de Tronos entera casi 3 veces", amount(Equivalences.GOT, 70 * 2.8))
        assertEquals("Juego de Tronos entera 2 veces y media", amount(Equivalences.GOT, 70 * 2.5))
    }

    @Test
    fun `fracciones bien redactadas`() {
        assertEquals("medio grado universitario", amount(Equivalences.DEGREE, 3_000.0))
        assertEquals("el 40 % de un máster", amount(Equivalences.MASTER, 600.0))
        assertEquals("media película", amount(Equivalences.MOVIE, 1.0))
        assertEquals("media maratón", amount(Equivalences.MARATHON, 2.25))
        assertEquals("casi un máster", amount(Equivalences.MASTER, 1_350.0))
        assertEquals("un cuarto de grado universitario", amount(Equivalences.DEGREE, 1_500.0))
        assertEquals("tres cuartos de grado universitario", amount(Equivalences.DEGREE, 4_500.0))
        assertEquals("el 0,4 % de un grado universitario", amount(Equivalences.DEGREE, 24.0))
        assertEquals("menos del 0,1 % de un grado universitario", amount(Equivalences.DEGREE, 1 / 60.0))
    }

    @Test
    fun `obras y trayectos que se repiten`() {
        assertEquals("Juego de Tronos entera", amount(Equivalences.GOT, 70.0))
        assertEquals("Juego de Tronos entera 2 veces", amount(Equivalences.GOT, 140.0))
        assertEquals("Juego de Tronos entera una vez y media", amount(Equivalences.GOT, 105.0))
        assertEquals("la mitad de Juego de Tronos", amount(Equivalences.GOT, 35.0))
        assertEquals("el 10 % del Camino de Santiago", amount(Equivalences.CAMINO, 19.5))
        assertEquals("el Quijote entero", amount(Equivalences.QUIJOTE, Equivalences.QUIJOTE.hoursEach))
        assertEquals("la mitad del camino de Madrid a Toledo", amount(Equivalences.TOLEDO, 7.2))
        assertEquals("5 kilómetros", amount(Equivalences.WALK, 1.0))
        assertEquals("un kilómetro y medio", amount(Equivalences.WALK, 0.3))
        assertEquals("830 metros", amount(Equivalences.WALK, 0.166))
    }

    @Test
    fun `verbo, frase y valor de ticket`() {
        assertEquals("sacarte medio grado universitario", plain(Equivalences.DEGREE.value(3_000.0).infinitive))
        assertEquals("ver Juego de Tronos entera 2 veces", plain(Equivalences.GOT.value(140.0).infinitive))
        assertEquals("ir andando de Madrid a Toledo", plain(Equivalences.TOLEDO.value(14.4).infinitive))
        val sentences = (0L until 30L).map { plain(Equivalences.BOOK.value(84.0).sentence(it)) }.toSet()
        assertEquals(
            setOf("Con ese tiempo podrías leer 14 libros.", "Te daba para leer 14 libros.", "Eso da para leer 14 libros."),
            sentences,
        )
        assertEquals("14", Equivalences.BOOK.value(84.0).ticketValue)
        assertEquals("0,5", Equivalences.DEGREE.value(3_000.0).ticketValue)
        assertEquals("×2", Equivalences.GOT.value(140.0).ticketValue)
        assertEquals("0,04", Equivalences.DEGREE.value(240.0).ticketValue)
        assertEquals("<0,01", Equivalences.DEGREE.value(1.0).ticketValue)
        assertEquals("14 libros", plain(Equivalences.BOOK.value(84.0).text))
        assertEquals("Medio grado universitario", plain(Equivalences.DEGREE.value(3_000.0).text))
    }

    @Test
    fun `nunca 1 con plural ni cantidades mal escritas`() {
        val hours = listOf(0.0, 0.01, 0.2, 0.5, 1.0, 2.0, 6.0, 9.0, 24.0, 70.0, 140.0, 600.0, 1_500.0, 6_000.0, 9_000.0, 100_000.0)
        // «1 libros» no; «el 1 % de un libro» sí
        val oneWithSpace = Regex("(?<![\\d.,])1[\\u00A0 ](?!%)")
        for (h in hours) for (e in Equivalences.ALL) {
            val v = e.value(h)
            for (text in listOf(v.amountText, v.infinitive, v.sentence(3))) {
                assertTrue("${e.id} $h h: «${plain(text)}»", !oneWithSpace.containsMatchIn(text))
                val problems = CopyRules.violations(text)
                assertTrue("${e.id} $h h: «${plain(text)}» → $problems", problems.isEmpty())
            }
        }
    }

    @Test
    fun `siempre hay algo que decir con un minuto y con 5000 horas`() {
        for (ms in listOf(1_000L, 30_000L, min, 47 * min, 5_000 * hour, 50_000 * hour)) {
            for (seed in 0L..20L) {
                val picks = Equivalences.pick(ms, 3, seed)
                assertEquals("$ms ms: ${picks.map { it.equivalence.id }}", 3, picks.size)
                assertTrue(picks.all { it.infinitive.isNotBlank() })
                val headline = Equivalences.headline(ms, seed)
                assertTrue(headline.isNotBlank() && headline.endsWith("."))
                assertTrue("«$headline» → ${CopyRules.violations(headline)}", CopyRules.violations(headline).isEmpty())
            }
        }
        assertEquals(
            "calentar un vaso de leche en el microondas",
            plain(Equivalences.pick(min, 1, 0).single().infinitive),
        )
        // Sin nada que llegue a 1, primero la fracción más cercana a 1
        assertEquals(
            "calentar medio vaso de leche en el microondas",
            plain(Equivalences.pick(30_000, 3, 0).first().infinitive),
        )
        assertEquals("Todavía no hay tiempo que comparar.", Equivalences.headline(0, 0))
        assertTrue(Equivalences.pick(0, 3, 0).isEmpty())
    }

    @Test
    fun `prefiere lo que llega a una unidad y da variedad`() {
        for (seed in 0L..30L) {
            val big = Equivalences.pick(5_000 * hour, 3, seed)
            assertTrue(big.all { it.reachesOne })
            assertEquals("Familias repetidas: ${big.map { it.equivalence.id }}", 3, big.map { it.equivalence.group }.toSet().size)
            assertTrue("La última es la de andar por casa", big.last().equivalence.absurd)
            assertTrue(big.dropLast(1).none { it.equivalence.absurd })
            val mid = Equivalences.pick(61 * hour, 3, seed)
            assertTrue(mid.all { it.reachesOne })
        }
        val headlines = (0L..30L).map { Equivalences.pick(61 * hour, 1, it).single().equivalence.id }.toSet()
        assertTrue("El titular no rota: $headlines", headlines.size >= 2)
    }

    @Test
    fun `selección determinista`() {
        for (seed in 0L..20L) for (ms in listOf(min, 3 * hour, 61 * hour, 5_000 * hour)) {
            assertEquals(Equivalences.pick(ms, 3, seed), Equivalences.pick(ms, 3, seed))
            assertEquals(Equivalences.headline(ms, seed), Equivalences.headline(ms, seed))
        }
    }

    @Test
    fun `compatibilidad con la api anterior`() {
        @Suppress("DEPRECATION")
        val old = Equivalences.headline(2.0)
        assertTrue(old.isNotBlank())
        assertEquals(Equivalences.ALL.size, Equivalences.forHours(10.0).size)
        assertEquals(12.0, Equivalences.swipeMetres(100), 1e-9)
    }
}
