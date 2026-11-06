package com.reelometro.app.detection

import com.reelometro.app.detection.RecipientDetector.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Árboles con la forma de la hoja de compartir de Instagram (2025-2026): una cuadrícula de personas y
 * grupos (foto + nombre) que se marcan al tocarlas y un botón «Enviar» abajo. También el diseño antiguo,
 * con un «Enviar» en cada fila que pasa a «Deshacer».
 */
class RecipientDetectorTest {

    private val detector = RecipientDetector().apply { start(now = 0, reelId = 42) }

    private fun click(node: FakeNode, now: Long = 1_000) = detector.onClick(node, SCREEN_H, now)

    /** Celda de la cuadrícula: foto con descripción y el nombre debajo. */
    private fun tile(name: String, left: Int = 40, top: Int = 1200) =
        FakeNode(
            viewId = "com.instagram.android:id/direct_share_sheet_grid_item",
            bounds = box(left, top, left + 240, top + 300),
            children = listOf(
                desc("Foto del perfil de $name", box(left + 40, top, left + 200, top + 160)),
                text(name, box(left, top + 180, left + 240, top + 230)),
            ),
        )

    private fun sendButton(label: String = "Enviar") =
        FakeNode(
            className = "android.widget.Button",
            bounds = box(40, 2200, 1040, 2340),
            children = listOf(text(label, box(400, 2240, 680, 2300))),
        )

    @Test
    fun `marcar personas y enviar con el boton de abajo`() {
        val ana = tile("Ana García")
        val piso = tile("Los del piso", left = 300)
        assertEquals(Outcome.Toggled("Ana García", 1, "marcado"), click(ana))
        assertEquals(Outcome.Toggled("Los del piso", 2, "marcado"), click(piso))

        val sent = click(sendButton("Enviar por separado"))

        assertEquals(Outcome.Sent(listOf("Ana García", "Los del piso"), 42, true, "botón enviar (2)"), sent)
        // La hoja se ha cerrado: lo siguiente ya no se mira.
        assertFalse(detector.isOpen(2_000))
        assertTrue(click(tile("Marta")) is Outcome.Ignored)
    }

    @Test
    fun `tocar dos veces la misma persona la desmarca`() {
        val ana = tile("Ana")
        click(ana)
        assertEquals(Outcome.Toggled("Ana", 0, "desmarcado"), click(ana))
        assertEquals(Outcome.Ignored("enviar sin destinatario"), click(sendButton()))
    }

    @Test
    fun `tocar solo la foto vale y la descripcion se limpia`() {
        val ana = tile("Ana")
        val photo = ana.children.first() as FakeNode
        assertEquals("Ana", (click(photo) as Outcome.Toggled).name)

        val selected = FakeNode(contentDescription = "Marta López, marta.lopez, seleccionado", bounds = box(300, 1200, 540, 1500))
        assertEquals("Marta López", (click(selected) as Outcome.Toggled).name)
    }

    @Test
    fun `el enviar de cada fila envia a esa fila y deshacer lo anula`() {
        val sendInRow = FakeNode(className = "android.widget.Button", text = "Enviar", bounds = box(820, 1400, 1040, 1480))
        FakeNode(
            bounds = box(0, 1380, 1080, 1500),
            children = listOf(
                desc("Foto del perfil de primos_2026", box(40, 1390, 140, 1490)),
                FakeNode(
                    bounds = box(160, 1390, 800, 1490),
                    children = listOf(text("Primos", box(160, 1395, 800, 1440)), text("Activo hace 5 min", box(160, 1445, 800, 1485))),
                ),
                sendInRow,
            ),
        )
        assertEquals(Outcome.Sent(listOf("Primos"), 42, false, "enviar de la fila"), click(sendInRow))
        assertTrue(detector.isOpen(2_000))

        val undo = FakeNode(className = "android.widget.Button", text = "Deshacer", bounds = sendInRow.bounds)
        FakeNode(bounds = box(0, 1380, 1080, 1500), children = listOf(text("Primos", box(160, 1395, 800, 1440)), undo))
        assertEquals(Outcome.Undone("Primos", 0, "deshacer"), click(undo))
    }

    @Test
    fun `las otras opciones de la hoja, el buscador y el video no cuentan`() {
        val options = listOf("Copiar enlace", "Añadir a la historia", "WhatsApp", "Compartir en...", "Crear grupo", "Más")
        options.forEach { label ->
            val option = FakeNode(
                bounds = box(40, 2000, 240, 2180),
                children = listOf(desc(label, box(80, 2000, 200, 2100)), text(label, box(40, 2110, 240, 2160))),
            )
            assertEquals(label, Outcome.Ignored("otra opción de la hoja"), click(option))
        }
        val search = FakeNode(className = "android.widget.EditText", text = "Buscar", bounds = box(40, 900, 1040, 1000))
        assertEquals(Outcome.Ignored("campo de texto"), click(search))

        val like = desc("Me gusta", box(950, 1500, 1050, 1600))
        FakeNode(viewId = "com.instagram.android:id/clips_viewer_view_pager", bounds = box(0, 0, 1080, 2400), children = listOf(like))
        assertEquals(Outcome.Ignored("dentro del visor de Reels, no en la hoja"), click(like))

        assertEquals(Outcome.Ignored("sin nombre"), click(FakeNode(bounds = box(0, 800, 1080, 2400))))
    }

    @Test
    fun `lo que dejo marcado el envio rapido cuenta al enviar a mano`() {
        val manual = RecipientDetector().apply { start(now = 0, reelId = 7, preselected = listOf("Los del piso")) }
        assertEquals(
            Outcome.Sent(listOf("Los del piso"), 7, true, "botón enviar (1)"),
            manual.onClick(sendButton(), SCREEN_H, 1_000),
        )
    }

    @Test
    fun `sin sesion o pasado el tiempo no se mira nada`() {
        val fresh = RecipientDetector()
        assertEquals(Outcome.Ignored("sin hoja de compartir abierta"), fresh.onClick(tile("Ana"), SCREEN_H, 1_000))
        val late = RecipientDetector().apply { start(0, null) }
        assertFalse(late.isOpen(RecipientDetector.Config().sessionMaxMs + 1))
        assertTrue(late.onClick(tile("Ana"), SCREEN_H, RecipientDetector.Config().sessionMaxMs + 1) is Outcome.Ignored)
    }

    @Test
    fun `limpieza de nombres`() {
        val d = RecipientDetector()
        assertEquals("Ana", d.clean("Foto del perfil de Ana", fromText = false))
        assertEquals("Los del piso", d.clean("  Los   del piso ", fromText = true))
        assertEquals("Juan, Pedro y Luis", d.clean("Juan, Pedro y Luis", fromText = true)) // grupo sin nombre
        assertEquals("Ana", d.clean("Ana, seleccionada", fromText = false))
        assertEquals("Grupo 2026", d.clean("Grupo 2026", fromText = true))
        listOf("Enviar", "Deshacer", "Activo ahora", "Visto", "12", "Copiar enlace").forEach {
            assertEquals(it, null, d.clean(it, fromText = true))
        }
        assertEquals(null, d.clean("···", fromText = true))
        assertEquals(null, d.clean("x".repeat(65), fromText = true))
    }

    /** Celda de hoy (Instagram 454, Pixel 10a, nov 2026), con el nombre largo partido en dos líneas. */
    private fun pog(first: String, second: String?, left: Int = 40, top: Int = 1200, state: String = "Chat sin seleccionar") =
        FakeNode(
            viewId = "com.instagram.android:id/direct_share_sheet_grid_view_pog",
            contentDescription = listOfNotNull(first, second).joinToString(" ") + " " + state,
            bounds = box(left, top, left + 360, top + 360),
            children = listOf(
                FakeNode(viewId = "com.instagram.android:id/grid_view_pog_avatar_view", bounds = box(left + 70, top + 30, left + 290, top + 250)),
                FakeNode(
                    viewId = "com.instagram.android:id/grid_view_pog_text_view_container",
                    bounds = box(left + 50, top + 250, left + 310, top + 360),
                    children = listOfNotNull(
                        text(first, box(left + 75, top + 250, left + 285, top + 300), id = "com.instagram.android:id/grid_view_pog_text_view_first_line"),
                        second?.let { text(it, box(left + 75, top + 300, left + 285, top + 350), id = "com.instagram.android:id/grid_view_pog_text_view_second_line") },
                    ),
                ),
            ),
        )

    @Test
    fun `un nombre largo partido en dos lineas en la cuadricula es un solo nombre`() {
        // Hasta la 1.4.3 se guardaba «Viaje fin de» y el envío rápido no lo encontraba en el buscador.
        val group = pog("Viaje fin de", "curso")
        assertEquals("Viaje fin de curso", (click(group) as Outcome.Toggled).name)
        val emoji = pog("Amigos del", "insta😎", left = 400)
        assertEquals("Amigos del insta😎", (click(emoji.children.first() as FakeNode) as Outcome.Toggled).name) // la foto
        assertEquals("Pablo", (click(pog("Pablo", null, left = 760)) as Outcome.Toggled).name)
        // Una segunda línea que es un estado no es parte del nombre.
        assertEquals("Marta", (click(pog("Marta", "Activa ahora", top = 1600)) as Outcome.Toggled).name)
    }

    @Test
    fun `la descripcion de la celda de hoy se limpia`() {
        val d = RecipientDetector()
        assertEquals("Viaje fin de curso", d.clean("Viaje fin de curso Chat sin seleccionar", fromText = false))
        assertEquals("Pablo", d.clean("Pablo Chat seleccionado", fromText = false))
        assertEquals("Ana", d.clean("Ana sin seleccionar", fromText = false))
    }

    @Test
    fun `el enviar de abajo se reconoce por su id en cualquier idioma`() {
        click(tile("Ana García"))
        val button = FakeNode(
            className = "android.widget.Button",
            viewId = RecipientDetector.SEND_BUTTON_ID,
            contentDescription = "Wyślij",
            bounds = box(42, 2186, 1038, 2302),
        )
        val sent = click(button, now = 2_000) as Outcome.Sent
        assertEquals(listOf("Ana García"), sent.names)
    }
}
