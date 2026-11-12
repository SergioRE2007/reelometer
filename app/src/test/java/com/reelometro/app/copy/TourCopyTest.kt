package com.reelometro.app.copy

import com.reelometro.app.stats.Format
import com.reelometro.app.ui.tour.TourStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Textos del recorrido guiado ([TourCopy]), entradas nuevas de
 * Ajustes y aviso en directo ([NotificationCopy]). Las reglas de estilo son las de
 * [CopyRules]; `CopyCatalogTest` ya recorre por reflexión las constantes de SettingsCopy y NotificationCopy.
 */
class TourCopyTest {

    private fun assertRules(texts: Collection<String>) {
        val problems = texts.mapNotNull { t ->
            CopyRules.violations(t).takeIf { it.isNotEmpty() }?.let { "«${CopyRules.plain(t)}» → $it" }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun assertUpper(texts: Collection<String>) {
        val lower = texts.filter { it != Format.upper(it) }
        assertTrue("deberían ir en MAYÚSCULAS: $lower", lower.isEmpty())
    }

    // ------------------------------------------------------------------ recorrido

    @Test
    fun `cada paso tiene titulo y texto que cumplen las reglas`() {
        val texts = TourStep.entries.flatMap { step ->
            listOf(TourCopy.title(step), TourCopy.text(step, hasTarget = true), TourCopy.text(step, hasTarget = false))
        }
        assertRules(texts)
        val titles = TourStep.entries.map(TourCopy::title)
        assertTrue("títulos en frase, no en mayúsculas: $titles", titles.all { it.first().isUpperCase() && it != Format.upper(it) })
        assertEquals(TourStep.entries.size, titles.toSet().size)
    }

    @Test
    fun `los textos del recorrido son cortos para caber junto al hueco`() {
        val long = TourStep.entries.flatMap { listOf(TourCopy.text(it, true), TourCopy.text(it, false)) }
            .filter { it.length > MAX_STEP_TEXT }
        assertTrue("demasiado largos: $long", long.isEmpty())
    }

    @Test
    fun `el paso de la barra de estadisticas dice que se mantenga el dedo`() {
        assertTrue("mantén el dedo" in TourCopy.text(TourStep.STATS_BARS).lowercase(Format.locale))
    }

    @Test
    fun `ajustes menciona la calibracion y Flap cuenta que cada video le quita vida`() {
        assertTrue("calibración" in TourCopy.text(TourStep.SETTINGS))
        assertEquals("Flap", TourCopy.title(TourStep.PASSPORT))
        val flap = TourCopy.text(TourStep.PASSPORT)
        assertTrue(flap, "vídeo" in flap && "vida" in flap && "medianoche" in flap)
        // Nada del juego de antes.
        val lower = (flap + TourCopy.title(TourStep.PASSPORT)).lowercase(Format.locale)
        assertTrue(flap, listOf("pasaporte", "sello", "nivel", "experiencia").none { it in lower })
    }

    @Test
    fun `sin comparacion visible el paso lo explica con otro texto`() {
        assertTrue(TourCopy.text(TourStep.COMPARISON, true) != TourCopy.text(TourStep.COMPARISON, false))
    }

    @Test
    fun `contador de pasos y botones`() {
        assertEquals("PASO 2 DE 6", TourCopy.step(2, 6))
        assertEquals("PASO 5 DE 5", TourCopy.step(5, 5))
        assertUpper(listOf(TourCopy.SKIP, TourCopy.NEXT, TourCopy.DONE, TourCopy.step(1, 6)))
        assertRules(
            listOf(
                TourCopy.SKIP, TourCopy.NEXT, TourCopy.DONE, TourCopy.PANE_TITLE,
                TourCopy.SKIP_DESCRIPTION, TourCopy.NEXT_DESCRIPTION, TourCopy.DONE_DESCRIPTION,
            ),
        )
    }

    @Test
    fun `talkback anuncia el numero de paso y el texto`() {
        val said = TourCopy.announcement(TourStep.COMPARISON, 2, 6)
        assertTrue(said, said.startsWith("Paso 2 de 6. A esta hora. "))
        assertTrue(said.endsWith(TourCopy.text(TourStep.COMPARISON)))
        assertRules(TourStep.entries.map { TourCopy.announcement(it, 1, 6, hasTarget = it != TourStep.COMPARISON) })
    }

    // ------------------------------------------------------------------ ajustes nuevos

    @Test
    fun `entradas nuevas de ajustes`() {
        assertEquals("Un solo aviso al día, en cuanto pases de 150 vídeos.", CopyRules.plain(SettingsCopy.goalLiveDescription(150)))
        assertEquals(SettingsCopy.GOAL_LIVE_NEEDS_GOAL, SettingsCopy.goalLiveDescription(null))
        assertEquals(SettingsCopy.GOAL_LIVE_NEEDS_GOAL, SettingsCopy.goalLiveDescription(0))
        assertTrue("1.500" in SettingsCopy.goalLiveDescription(1_500))
        assertUpper(listOf(SettingsCopy.REPLAY_TOUR))
        assertRules(
            listOf(
                SettingsCopy.PASSPORT_LABEL,
                SettingsCopy.PASSPORT_DESCRIPTION,
                SettingsCopy.GOAL_LIVE_LABEL,
                SettingsCopy.GOAL_LIVE_NEEDS_GOAL,
                SettingsCopy.goalLiveDescription(150),
                SettingsCopy.goalLiveDescription(1),
                SettingsCopy.REPLAY_TOUR,
            ),
        )
    }

    // ------------------------------------------------------------------ avisos

    @Test
    fun `el aviso en directo dice cuantos llevas y cual era el objetivo`() {
        val texts = (0L until 40L).map { NotificationCopy.goalPassed(151, 150, it).text }.toSet()
        assertEquals(3, texts.size)
        assertTrue(texts.all { it.startsWith("Llevas 151. Tu objetivo era 150.") })
        assertTrue("Llevas 151. Tu objetivo era 150." in texts)
        val big = NotificationCopy.goalPassed(2_001, 2_000, 1)
        assertTrue(big.text.startsWith("Llevas 2.001. Tu objetivo era 2.000."))
        assertRules(texts + big.text + NotificationCopy.goalPassed(151, 150, 1).title)
        assertRules(listOf(NotificationCopy.goalPassedPublic.title, NotificationCopy.goalPassedPublic.text))
        assertTrue("la versión pública no lleva cifras", NotificationCopy.goalPassedPublic.text.none { it.isDigit() })
    }

    @Test
    fun `los canales nuevos tienen nombre y descripcion`() {
        assertRules(
            listOf(
                NotificationCopy.CHANNEL_GOAL_NAME,
                NotificationCopy.CHANNEL_GOAL_DESCRIPTION,
            ),
        )
    }

    @Test
    fun `el interruptor de Ajustes habla de Flap y no del pasaporte`() {
        assertTrue(SettingsCopy.PASSPORT_LABEL, "Flap" in SettingsCopy.PASSPORT_LABEL)
        val description = SettingsCopy.PASSPORT_DESCRIPTION
        assertTrue(description, "Flap" in description && "widget" in description && "medianoche" in description)
        val lower = (SettingsCopy.PASSPORT_LABEL + " " + description).lowercase(Format.locale)
        assertTrue(description, listOf("pasaporte", "sello", "nivel", "reto").none { it in lower })
    }

    private companion object {
        /** Unas dos líneas y media a 412 dp: con la fuente al 130 % siguen cabiendo junto al hueco. */
        const val MAX_STEP_TEXT = 110
    }
}
