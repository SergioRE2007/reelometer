package com.reelometro.app.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reelometro.app.R
import com.reelometro.app.copy.AppTexts
import com.reelometro.app.copy.JvmTexts
import com.reelometro.app.copy.str
import com.reelometro.app.ui.components.flap.EmptyState
import com.reelometro.app.ui.icons.ReelIcons
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Lo que lee TalkBack de los componentes compartidos: descripciones, roles, estados y encabezados.
 * Arnés propio sobre Robolectric ([renderSemantics]); no mide texto, solo semántica y colocación relativa.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class ComponentSemanticsTest {

    @Before
    fun setUp() = AppTexts.install(JvmTexts.spanish())

    @After
    fun tearDown() = AppTexts.reset()

    private fun SemanticsNode.role() = config.getOrNull(SemanticsProperties.Role)
    private fun SemanticsNode.isClickable() = SemanticsActions.OnClick in config
    private fun SemanticsNode.isHeading() = SemanticsProperties.Heading in config

    private fun SemanticsNode.described(text: String) =
        all().filter { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(text) == true }

    @Test
    fun `iconButton tiene descripcion y rol de boton`() {
        val root = renderSemantics { ReelIconButton(ReelIcons.Settings, "Ajustes", onClick = {}) }
        val node = root.described("Ajustes").single()
        assertEquals(Role.Button, node.role())
        assertTrue(node.isClickable())
    }

    @Test
    fun `reelButton se lee en minusculas naturales`() {
        val root = renderSemantics { ReelButton("Compartir", onClick = {}) }
        val node = root.all().single { it.isClickable() }
        assertEquals(listOf("Compartir"), node.config[SemanticsProperties.ContentDescription])
    }

    @Test
    fun `reelButton respeta una descripcion propia`() {
        val root = renderSemantics { ReelButton("Compartir", onClick = {}, contentDescription = "Compartir resumen") }
        assertEquals("Compartir resumen", root.all().single { it.isClickable() }.spoken())
    }

    @Test
    fun `segmentedTabs son pestanas con seleccion y nombres naturales`() {
        val names = listOf("Semana", "Mes", "Año", "Siempre")
        val root = renderSemantics { SegmentedTabs(names, selectedIndex = 2, onSelect = {}) }
        val tabs = root.all().filter { it.role() == Role.Tab }
        assertEquals(4, tabs.size)
        assertEquals(listOf(false, false, true, false), tabs.map { it.config.getOrNull(SemanticsProperties.Selected) })
        assertEquals(names, tabs.map { it.spoken() })
    }

    @Test
    fun `segmentedTabs se desplaza en horizontal`() {
        val root = renderSemantics { SegmentedTabs(listOf("Semana", "Mes"), selectedIndex = 0, onSelect = {}) }
        assertTrue(root.all().any { SemanticsProperties.HorizontalScrollAxisRange in it.config })
    }

    @Test
    fun `sectionHeader es un encabezado sin el indice`() {
        val root = renderSemantics { SectionHeader(1, "Plataformas", trailing = "3") }
        val node = root.all().single { it.isHeading() }
        assertEquals("Plataformas, 3", node.spoken())
        assertFalse(node.spoken()!!.contains("01"))
    }

    @Test
    fun `sectionHeader sin dato final lee solo el titulo`() {
        val root = renderSemantics { SectionHeader(null, "Plataformas") }
        assertEquals("Plataformas", root.all().single { it.isHeading() }.spoken())
    }

    @Test
    fun `stepper tiene botones descritos y apagados en los extremos`() {
        val root = renderSemantics { Stepper(value = 0, onValueChange = {}, range = 0..10) }
        val minus = root.described(str(R.string.ui_stepper_decrease, "1")).single()
        val plus = root.described(str(R.string.ui_stepper_increase, "1")).single()
        assertEquals(Role.Button, minus.role())
        assertTrue(SemanticsProperties.Disabled in minus.config)
        assertFalse(SemanticsProperties.Disabled in plus.config)
    }

    @Test
    fun `switchLine es un interruptor con estado`() {
        for (checked in listOf(true, false)) {
            val root = renderSemantics { SwitchLine("Avisos", checked = checked, onCheckedChange = {}) }
            val node = root.all().single { it.role() == Role.Switch }
            assertEquals(if (checked) ToggleableState.On else ToggleableState.Off, node.config[SemanticsProperties.ToggleableState])
        }
    }

    @Test
    fun `emptyState lee la cifra con su unidad`() {
        val root = renderSemantics { EmptyState(title = "x", value = 0) }
        assertTrue(root.described("0 vídeos").isNotEmpty())
    }

    @Test
    fun `topBar con solo kicker es un encabezado`() {
        val root = renderSemantics { ReelTopBar(kicker = "Hoy") }
        assertTrue(root.all().any { it.isHeading() })
    }

    @Test
    fun `todo lo que se pulsa tiene etiqueta`() {
        val root = renderSemantics {
            Column {
                ReelTopBar(title = "Ajustes", onBack = {}) { ReelIconButton(ReelIcons.Settings, "Ajustes", onClick = {}) }
                PeriodNavigator(label = "Semana", onPrevious = {}, onNext = {})
                Band(text = "Banda", onClick = {})
                ReceiptLine(label = "Meta", value = "150", onClick = {})
                Stepper(value = 3, onValueChange = {})
                SegmentedTabs(listOf("Semana", "Mes"), selectedIndex = 0, onSelect = {})
                ButtonRow {
                    ReelButton("Cancelar", onClick = {}, modifier = it)
                    ReelButton("Aceptar", onClick = {}, modifier = it)
                }
            }
        }
        val clickables = root.all().filter { it.isClickable() }
        assertTrue(clickables.size >= 9)
        clickables.forEach { assertFalse("Sin etiqueta: $it", it.spoken().isNullOrBlank()) }
    }

    @Test
    fun `buttonRow se apila con letra grande y va en fila con letra normal`() {
        fun tops(scale: Float): Pair<List<Float>, List<Float>> {
            val root = renderSemantics(fontScale = scale) {
                ButtonRow {
                    ReelButton("Cancelar", onClick = {}, modifier = it)
                    ReelButton("Aceptar", onClick = {}, modifier = it)
                }
            }
            val buttons = root.all().filter { it.isClickable() }
            assertEquals(2, buttons.size)
            return buttons.map { it.boundsInRoot.top } to buttons.map { it.boundsInRoot.bottom }
        }
        val (normalTops, _) = tops(1f)
        assertEquals(normalTops[0], normalTops[1], 0.5f)
        val (largeTops, largeBottoms) = tops(2f)
        assertTrue(largeTops[1] >= largeBottoms[0])
        assertNotNull(largeTops)
    }
}
