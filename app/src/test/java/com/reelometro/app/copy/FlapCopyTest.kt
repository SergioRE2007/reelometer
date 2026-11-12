package com.reelometro.app.copy

import com.reelometro.app.copy.CopyRules.plain
import com.reelometro.app.game.FlapDay
import com.reelometro.app.game.FlapFakes
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier
import java.time.LocalDate

/** Textos de la pestaña Flap: reglas de estilo (CopyRules), variantes deterministas, plurales y los 5 idiomas. */
class FlapCopyTest {

    private val seeds = -3L..80L
    private val today = LocalDate.of(2026, 11, 4)
    private val numbers = listOf(0, 1, 2, 7, 12, 150, 999, 1_000, 1_234, 99_999, 1_234_567)

    /** Traducciones además del español (`values/`). */
    private val translations = listOf("en", "fr", "it", "pt-rBR")

    /** Palabras de culpa o sermón que Flap no usa nunca (además de las de CopyRules). */
    private val guilt = listOf("culpa", "fracas", "castigo", "vergüenza", "fallaste", "has fallado", "perdedor", "deberías", "tienes que")

    private fun check(text: String?, where: String) {
        assertTrue("$where: vacío", !text.isNullOrBlank())
        val v = CopyRules.violations(text!!)
        assertTrue("«$text» ($where): $v", v.isEmpty())
        val lower = text.lowercase(com.reelometro.app.stats.Format.locale)
        guilt.forEach { assertFalse("«$text» ($where) suena a culpa: «$it»", it in lower) }
    }

    /** Para las traducciones: lo que vale en cualquier idioma (sin «¿» ni plurales del español). */
    private fun checkAnyLanguage(text: String, where: String) {
        assertTrue("$where: vacío", text.isNotBlank())
        assertEquals("$where: espacios al principio o al final", text.trim(), text)
        assertFalse("$where: exclamación en «$text»", '!' in text || '¡' in text)
        assertFalse("$where: emoji en «$text»", CopyRules.containsEmoji(text))
        assertFalse("$where: puntos suspensivos en «$text»", "..." in text || '…' in text)
        assertFalse("$where: hueco sin rellenar en «$text»", Regex("%\\d+\\$[sd]").containsMatchIn(text))
    }

    private fun <T> inLanguage(qualifier: String, block: () -> T): T = AppTexts.using(JvmTexts.forQualifier(qualifier), block)

    /** Todo lo que puede decir la pestaña, en el idioma de ahora. */
    private fun everything(): List<Pair<String, String>> = buildList {
        add("TITLE" to FlapCopy.TITLE)
        add("kicker" to FlapCopy.kicker(today))
        add("STATE_LABEL" to FlapCopy.STATE_LABEL)
        add("FLAP_TAP" to FlapCopy.FLAP_TAP)
        add("LIFE" to FlapCopy.LIFE)
        add("KO_UNTIL_TOMORROW" to FlapCopy.KO_UNTIL_TOMORROW)
        add("SECTION_HOW" to FlapCopy.SECTION_HOW)
        add("HOW_START" to FlapCopy.HOW_START)
        add("HOW_VIDEO" to FlapCopy.HOW_VIDEO)
        add("SET_GOAL" to FlapCopy.SET_GOAL)
        add("SET_GOAL_DESCRIPTION" to FlapCopy.SET_GOAL_DESCRIPTION)
        add("SECTION_WEEK" to FlapCopy.SECTION_WEEK)
        add("STREAK_LABEL" to FlapCopy.STREAK_LABEL)
        for (mood in FlapMood.entries) {
            add("moodLabel $mood" to FlapCopy.moodLabel(mood))
            add("moodSpoken $mood" to FlapCopy.moodSpoken(mood))
            for (seed in seeds) add("moodPhrase $mood $seed" to FlapCopy.moodPhrase(mood, seed))
            for (life in listOf(-5, 0, 1, 50, 100, 140)) add("description $mood $life" to FlapCopy.description(mood, life))
            for (n in numbers + listOf(-3)) add("toWorse $mood $n" to FlapCopy.toWorse(mood, n))
        }
        for (seed in seeds) {
            add("freshPhrase $seed" to FlapCopy.freshPhrase(seed))
            add("recoveredPhrase $seed" to FlapCopy.recoveredPhrase(seed))
        }
        for (n in numbers + listOf(-4)) {
            add("lifeValue $n" to FlapCopy.lifeValue(n))
            add("lifeDescription $n" to FlapCopy.lifeDescription(n))
            add("todayLine $n" to FlapCopy.todayLine(n, 40))
            add("howLimit goal $n" to FlapCopy.howLimit(n, hasGoal = true))
            add("howLimit default $n" to FlapCopy.howLimit(n, hasGoal = false))
            add("noGoal $n" to FlapCopy.noGoal(n))
            FlapCopy.streak(n)?.let { add("streak $n" to it) }
        }
        for (goal in listOf<Int?>(null, 1, 2, 3, 40, 150)) {
            val limit = FlapRules.limitFor(goal)
            for (count in (0..limit * 2 + 2).toList() + listOf(12_345)) {
                add("nextLine $goal $count" to FlapCopy.nextLine(FlapFakes.state(today, goal, count)))
            }
            FlapFakes.state(today, goal, tracked = 3).week.forEach { add("dayDescription ${it.date}" to FlapCopy.dayDescription(it)) }
        }
    }

    // ------------------------------------------------------------------ español

    @Test
    fun `todas las constantes cumplen las reglas`() {
        val getters = FlapCopy::class.java.declaredMethods
            .filter {
                it.name.startsWith("get") && it.name.length > 3 && it.parameterCount == 0 &&
                    it.returnType == String::class.java && Modifier.isPublic(it.modifiers)
            }
            .map { it.name.removePrefix("get") to it.invoke(FlapCopy) as String }
        assertTrue("se esperaban al menos 12 constantes, hay ${getters.size}", getters.size >= 12)
        getters.forEach { (name, value) -> check(value, name) }
    }

    @Test
    fun `todo lo que dice la pestaña cumple las reglas`() {
        everything().forEach { (where, text) -> check(text, where) }
    }

    @Test
    fun `cada estado tiene su nombre, distinto de los demás, y en mayúsculas`() {
        val labels = FlapMood.entries.map { FlapCopy.moodLabel(it) }
        assertEquals(listOf("EN FORMA", "NORMAL", "CANSADO", "ENFERMO", "KO"), labels)
        assertEquals(labels.size, labels.toSet().size)
        val spoken = FlapMood.entries.map { FlapCopy.moodSpoken(it) }
        assertEquals(spoken.size, spoken.toSet().size)
        // TalkBack no lee siglas: KO se dice entero.
        assertEquals("fuera de combate", FlapCopy.moodSpoken(FlapMood.KO))
        spoken.forEach { assertEquals(it, it.lowercase(com.reelometro.app.stats.Format.locale)) }
    }

    @Test
    fun `las variantes son deterministas, de 3 a 6, y no se repiten dos días seguidos`() {
        val families: Map<String, (Long) -> String> = buildMap {
            FlapMood.entries.forEach { m -> put("mood $m") { s: Long -> FlapCopy.moodPhrase(m, s) } }
            put("fresh") { s: Long -> FlapCopy.freshPhrase(s) }
            put("recovered") { s: Long -> FlapCopy.recoveredPhrase(s) }
        }
        families.forEach { (name, f) ->
            val all = seeds.map(f)
            assertEquals("$name: misma semilla, misma frase", all, seeds.map(f))
            assertTrue("$name: ${all.toSet().size} variantes", all.toSet().size in 3..6)
            all.zipWithNext().forEach { (a, b) -> assertNotEquals("$name: repite dos días seguidos «$a»", a, b) }
        }
        // Cada estado dice cosas distintas (ninguna frase compartida entre dos estados).
        val byMood = FlapMood.entries.map { m -> seeds.map { FlapCopy.moodPhrase(m, it) }.toSet() }
        byMood.forEachIndexed { i, a -> byMood.drop(i + 1).forEach { b -> assertTrue(a.intersect(b).isEmpty()) } }
    }

    @Test
    fun `recién instalada sale la frase de llegada y luego la del estado`() {
        val fresh = FlapFakes.fresh(today)
        val freshPhrases = seeds.map { FlapCopy.freshPhrase(it) }.toSet()
        seeds.forEach { assertTrue(FlapCopy.phrase(fresh, it) in freshPhrases) }
        val sick = FlapFakes.sample(FlapMood.ENFERMO, today = today)
        seeds.forEach { assertEquals(FlapCopy.moodPhrase(FlapMood.ENFERMO, it), FlapCopy.phrase(sick, it)) }
    }

    @Test
    fun `la línea de hoy y lo que falta concuerdan en singular y plural`() {
        assertEquals("HOY 31 VÍDEOS · AGUANTA 40", plain(FlapCopy.todayLine(31, 40)))
        assertEquals("HOY 1 VÍDEO · AGUANTA 40", plain(FlapCopy.todayLine(1, 40)))
        assertEquals("HOY 0 VÍDEOS · AGUANTA 150", plain(FlapCopy.todayLine(0, 150)))
        assertEquals("Con 9 vídeos más se pondrá enfermo.", plain(FlapCopy.toWorse(FlapMood.ENFERMO, 9)))
        assertEquals("Con 1 vídeo más caerá KO.", plain(FlapCopy.toWorse(FlapMood.KO, 1)))
        assertEquals("Con 6 vídeos más se cansará.", plain(FlapCopy.toWorse(FlapMood.CANSADO, 6)))
        assertEquals("Con 20 vídeos más dejará de estar en forma.", plain(FlapCopy.toWorse(FlapMood.NORMAL, 20)))
    }

    @Test
    fun `lo que falta sale de las reglas y en KO dice hasta cuándo`() {
        // Objetivo 40: con 35 vídeos está CANSADO y enferma con el 41.
        val tired = FlapFakes.state(today, goal = 40, count = 35)
        assertEquals(FlapMood.CANSADO, tired.mood)
        assertEquals("Con 6 vídeos más se pondrá enfermo.", plain(FlapCopy.nextLine(tired)))
        // Con 79 está ENFERMO y cae KO con el 80.
        assertEquals("Con 1 vídeo más caerá KO.", plain(FlapCopy.nextLine(FlapFakes.state(today, goal = 40, count = 79))))
        // Recién empezado el día, sin objetivo (aguante 150): deja de estar en forma con el 75.
        assertEquals("Con 75 vídeos más dejará de estar en forma.", plain(FlapCopy.nextLine(FlapFakes.fresh(today))))
        val ko = FlapFakes.state(today, goal = 40, count = 80)
        assertEquals(FlapMood.KO, ko.mood)
        assertEquals(FlapCopy.KO_UNTIL_TOMORROW, FlapCopy.nextLine(ko))
        assertEquals("KO hasta mañana. A medianoche revive.", FlapCopy.KO_UNTIL_TOMORROW)
    }

    @Test
    fun `cómo funciona dice el objetivo, el doble y el aguante sin objetivo`() {
        val how = FlapCopy.howLines(40, hasGoal = true).map(::plain)
        assertEquals(3, how.size)
        assertEquals("Cada día Flap empieza a tope, con 100 de vida. A medianoche revive.", how[0])
        assertTrue(how[2], "tu objetivo (40 vídeos)" in how[2] && "(80)" in how[2])
        val default = plain(FlapCopy.howLimit(FlapRules.DEFAULT_LIMIT, hasGoal = false))
        assertTrue(default, "150 vídeos" in default && "(300)" in default && "objetivo" !in default)
        assertEquals(
            "Sin objetivo, Flap aguanta 150 vídeos al día. Con el tuyo, aguanta lo que tú decidas.",
            plain(FlapCopy.noGoal(FlapRules.DEFAULT_LIMIT)),
        )
        assertTrue(plain(FlapCopy.howLimit(1, hasGoal = true)).contains("(1 vídeo)"))
    }

    @Test
    fun `racha en singular y plural, y nada con cero`() {
        assertNull(FlapCopy.streak(0))
        assertNull(FlapCopy.streak(-2))
        assertEquals("1 día seguido sin que enferme", plain(FlapCopy.streak(1)!!))
        assertEquals("5 días seguidos sin que enferme", plain(FlapCopy.streak(5)!!))
        assertEquals("1.234 días seguidos sin que enferme", plain(FlapCopy.streak(1_234)!!))
    }

    @Test
    fun `TalkBack lee el estado, la vida y cada día de la semana`() {
        assertEquals("Flap: enfermo, vida 32 de 100.", FlapCopy.description(FlapMood.ENFERMO, 32))
        assertEquals("Flap: fuera de combate, vida 0 de 100.", FlapCopy.description(FlapMood.KO, 0))
        assertEquals("Vida: 62 de 100", FlapCopy.lifeDescription(62))
        assertEquals("Vida: 100 de 100", FlapCopy.lifeDescription(140))
        val monday = LocalDate.of(2026, 11, 2)
        assertEquals(
            "Lunes 2 de noviembre: 34 vídeos, cansado.",
            plain(FlapCopy.dayDescription(FlapDay(monday, 34, FlapMood.CANSADO))),
        )
        assertEquals("Lunes 2 de noviembre: sin registro.", plain(FlapCopy.dayDescription(FlapDay(monday, null, null))))
        assertEquals("L", FlapCopy.dayInitial(monday))
        assertEquals("1.234", FlapCopy.dayCount(1_234))
        assertNull(FlapCopy.dayCount(null))
    }

    // ------------------------------------------------------------------ los 5 idiomas

    @Test
    fun `cada estado tiene nombre y frases propias en los cinco idiomas`() {
        val spanishLabels = FlapMood.entries.associateWith { FlapCopy.moodLabel(it) }
        val spanishPhrases = FlapMood.entries.associateWith { m -> seeds.map { FlapCopy.moodPhrase(m, it) }.toSet() }
        translations.forEach { q ->
            inLanguage(q) {
                val labels = FlapMood.entries.map { FlapCopy.moodLabel(it) }
                assertEquals("$q: nombres repetidos $labels", labels.size, labels.toSet().size)
                // Alguno puede coincidir («NORMAL», «KO», «CANSADO» en portugués), pero no todos.
                assertNotEquals("$q: nombres sin traducir", spanishLabels.values.toList(), labels)
                for (mood in FlapMood.entries) {
                    val label = FlapCopy.moodLabel(mood)
                    checkAnyLanguage(label, "$q moodLabel $mood")
                    assertEquals("$q: «$label» tiene que ir en mayúsculas", label.uppercase(com.reelometro.app.stats.Format.locale), label)
                    checkAnyLanguage(FlapCopy.moodSpoken(mood), "$q moodSpoken $mood")
                    val phrases = seeds.map { FlapCopy.moodPhrase(mood, it) }.toSet()
                    assertTrue("$q $mood: ${phrases.size} variantes", phrases.size in 2..6)
                    assertTrue("$q $mood: frases en español", phrases.intersect(spanishPhrases.getValue(mood)).isEmpty())
                }
            }
        }
    }

    @Test
    fun `todo lo de la pestaña sale bien en los cinco idiomas`() {
        translations.forEach { q ->
            inLanguage(q) {
                everything().forEach { (where, text) -> checkAnyLanguage(text, "$q $where") }
                // La cifra va formateada en el idioma de cada uno.
                assertTrue("$q: ${FlapCopy.todayLine(1_234, 40)}", FlapCopy.todayLine(1_234, 40).contains(com.reelometro.app.stats.Format.integer(1_234)))
                assertNotEquals("$q: sin traducir", "KO hasta mañana. A medianoche revive.", FlapCopy.KO_UNTIL_TOMORROW)
            }
        }
    }
}
