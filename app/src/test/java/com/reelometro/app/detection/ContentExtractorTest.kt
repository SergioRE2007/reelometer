package com.reelometro.app.detection

import com.reelometro.app.data.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentExtractorTest {

    private fun page(top: Int, visible: Boolean = true, vararg children: FakeNode) =
        FakeNode(bounds = box(0, top, 1080, top + 2240), isVisible = visible, children = children.toList())

    private fun tiktokFeed(): FakeNode = FakeNode(
        viewId = "com.zhiliaoapp.musically:id/e3k", className = "androidx.viewpager.widget.ViewPager",
        bounds = box(0, 0, 1080, 2240),
        children = listOf(
            page(-2240, visible = false, text("@otro_creador"), text("Otro vídeo #gatos #humor")),
            page(
                0, true,
                FakeNode(className = "android.view.View", bounds = box(0, 0, 1080, 2240)),
                text("@cocina_con_ana", box(40, 1880, 500, 1940)),
                text("Tortilla de patatas en 5 minutos #recetas #cocinaEspañola #fyp", box(40, 1950, 800, 2100)),
                text("♬ sonido original - cocina_con_ana", box(40, 2110, 700, 2160)),
                desc("Me gusta, 12,3 K"),
                desc("Comentarios, 345", box(950, 1450, 1050, 1550)),
                desc("Compartir", box(950, 1700, 1050, 1800)),
            ),
            page(2240, visible = false, text("@siguiente"), text("Mañana más #futbol")),
        ),
    )

    @Test
    fun `tiktok - lee autor, descripcion y hashtags solo de la pagina visible`() {
        val info = ContentExtractor.extract(Platform.TIKTOK, tiktokFeed(), SCREEN_W, SCREEN_H, rootIsFeed = true)
        assertEquals("cocina_con_ana", info.creator)
        assertTrue(info.caption!!.startsWith("Tortilla de patatas"))
        assertEquals(listOf("recetas", "cocinaespañola"), info.hashtags)   // #fyp es genérico
    }

    @Test
    fun `instagram - usuario por id o por la foto de perfil y nada de los comentarios`() {
        val clips = FakeNode(
            viewId = "com.instagram.android:id/clips_viewer_view_pager", className = "androidx.viewpager.widget.ViewPager",
            bounds = box(0, 0, 1080, 2280),
            children = listOf(
                FakeNode(
                    bounds = box(0, 0, 1080, 2280),
                    children = listOf(
                        desc("Foto del perfil de ana.fit", box(40, 1880, 120, 1960)),
                        text("ana.fit", box(130, 1890, 400, 1950), id = "com.instagram.android:id/clips_author_username"),
                        text("Seguir", box(420, 1890, 560, 1950)),
                        text("Rutina de pierna en casa sin material 💪 #fitness #gym", box(40, 1970, 900, 2080), id = "com.instagram.android:id/clips_caption_component"),
                        text("Audio original", box(40, 2090, 500, 2140)),
                    ),
                ),
            ),
        )
        val comments = FakeNode(
            viewId = "com.instagram.android:id/bottom_sheet_container", bounds = box(0, 900, 1080, 2400),
            children = listOf(text("@troll123", box(40, 1000, 400, 1050)), text("Esto es falso, no lo hagáis #fake", box(40, 1060, 900, 1120))),
        )
        val root = FakeNode(bounds = box(0, 0, 1080, 2400), children = listOf(clips, comments))

        val fromFeed = ContentExtractor.extract(Platform.INSTAGRAM, clips, SCREEN_W, SCREEN_H, rootIsFeed = true)
        assertEquals("ana.fit", fromFeed.creator)
        assertTrue(fromFeed.caption!!.startsWith("Rutina de pierna"))
        assertEquals(listOf("fitness", "gym"), fromFeed.hashtags)

        val fromWindow = ContentExtractor.extract(Platform.INSTAGRAM, root, SCREEN_W, SCREEN_H, rootIsFeed = false)
        assertEquals("ana.fit", fromWindow.creator)
        assertFalse(fromWindow.hashtags.contains("fake"))
    }

    @Test
    fun `instagram - usuario sin @ ni id abajo a la izquierda`() {
        val page = FakeNode(
            bounds = box(0, 0, 1080, 2280),
            children = listOf(
                text("Reels", box(40, 80, 300, 160)),
                text("maria_lopez.92", box(130, 1890, 450, 1950)),
                text("Seguir", box(470, 1890, 600, 1950)),
            ),
        )
        assertEquals("maria_lopez.92", ContentExtractor.extract(Platform.INSTAGRAM, page, SCREEN_W, SCREEN_H, rootIsFeed = false).creator)
    }

    @Test
    fun `youtube - handle con arroba y titulo expuesto como descripcion`() {
        val shorts = FakeNode(
            viewId = "com.google.android.youtube:id/reel_recycler", className = "androidx.recyclerview.widget.RecyclerView",
            bounds = box(0, 0, 1080, 2200),
            children = listOf(
                FakeNode(
                    viewId = "com.google.android.youtube:id/reel_player_page_container", bounds = box(0, 0, 1080, 2200),
                    children = listOf(
                        text("@CienciaEn1Minuto", box(130, 1850, 600, 1910)),
                        text("Suscribirse", box(620, 1850, 850, 1910)),
                        desc("¿Por qué el cielo es azul? #ciencia #curiosidades #shorts", box(40, 1930, 900, 2050), cls = "android.view.ViewGroup"),
                        desc("Me gusta este vídeo junto con 12.345 personas más", box(950, 1200, 1050, 1300)),
                        desc("Compartir", box(950, 1600, 1050, 1700)),
                    ),
                ),
                FakeNode(bounds = box(0, 2200, 1080, 4400), isVisible = false, children = listOf(text("@OtroCanal"))),
            ),
        )
        val info = ContentExtractor.extract(Platform.YOUTUBE, shorts, SCREEN_W, SCREEN_H, rootIsFeed = true)
        assertEquals("CienciaEn1Minuto", info.creator)
        assertEquals("¿Por qué el cielo es azul? #ciencia #curiosidades #shorts", info.caption)
        assertEquals(listOf("ciencia", "curiosidades"), info.hashtags)
    }

    @Test
    fun `la descripcion se corta a 300 caracteres y se quita el ver mas`() {
        val long = "Hilo larguísimo ".repeat(40) + "... más"
        val info = ContentExtractor.extract(Platform.TIKTOK, FakeNode(bounds = box(0, 0, 1080, 2240), children = listOf(text(long))), SCREEN_W, SCREEN_H, rootIsFeed = false)
        assertNotNull(info.caption)
        assertTrue(info.caption!!.length <= ContentExtractor.MAX_CAPTION)
        assertFalse(info.caption!!.endsWith("más"))
    }

    @Test
    fun `sin textos no inventa nada`() {
        val info = ContentExtractor.extract(Platform.TIKTOK, FakeNode(bounds = box(0, 0, 1080, 2240), children = listOf(desc("Me gusta, 3 K"))), SCREEN_W, SCREEN_H, rootIsFeed = false)
        assertNull(info.creator)
        assertNull(info.caption)
        assertTrue(info.isEmpty)
    }
}
