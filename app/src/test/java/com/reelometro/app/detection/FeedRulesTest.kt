package com.reelometro.app.detection

import com.reelometro.app.data.Platform
import com.reelometro.app.detection.FeedRules.IndexStyle
import com.reelometro.app.detection.FeedRules.Orientation
import com.reelometro.app.detection.FeedRules.Verdict
import com.reelometro.app.detection.FeedRules.WindowKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Casos conocidos de cada app (pantalla 1080 × 2400). */
class FeedRulesTest {

    private fun eval(
        platform: Platform, id: String?, cls: String?, top: Int = 0, w: Int = 1080, h: Int = 2240,
        from: Int = -1, to: Int = -1, dx: Int? = 0, dy: Int? = 300, screen: String? = null,
        orientation: Orientation? = null,
    ) = FeedRules.evaluate(
        FeedRules.ScrollSample(platform, id, cls, 0, top, w, h, SCREEN_W, SCREEN_H, from, to, -1, dx, dy, screen, orientation),
    )

    private val pagerClass = "androidx.viewpager.widget.ViewPager"
    private val recyclerClass = "androidx.recyclerview.widget.RecyclerView"

    // ── Instagram ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `instagram - el visor de reels es el feed`() {
        val d = eval(Platform.INSTAGRAM, "com.instagram.android:id/clips_viewer_view_pager", pagerClass, from = 4, to = 4)
        assertEquals(Verdict.FEED, d.verdict)
        assertTrue(d.rule.contains("clips_viewer"))
    }

    @Test
    fun `instagram - el feed normal es otra pantalla aunque tenga un video grande`() {
        val d = eval(Platform.INSTAGRAM, "android:id/list", recyclerClass, top = 200, h = 1900, from = 3, to = 4)
        assertEquals(Verdict.OTHER_SCREEN, d.verdict)
    }

    @Test
    fun `instagram - stories no es el feed de reels`() {
        val d = eval(Platform.INSTAGRAM, "com.instagram.android:id/reel_viewer_root", pagerClass, dx = 500, dy = 0)
        assertEquals(Verdict.OTHER_SCREEN, d.verdict)
    }

    @Test
    fun `instagram - comentarios en hoja inferior no cambian nada`() {
        val d = eval(Platform.INSTAGRAM, "com.instagram.android:id/sticky_header_list", recyclerClass, top = 900, h = 1500, from = 0, to = 6)
        assertEquals(Verdict.IGNORED, d.verdict)
    }

    @Test
    fun `instagram - bandeja de stories, perfil y explorar no son el feed`() {
        assertEquals(Verdict.IGNORED, eval(Platform.INSTAGRAM, "com.instagram.android:id/reels_tray_container", recyclerClass, h = 300, dx = 300, dy = 0).verdict)
        assertEquals(Verdict.OTHER_SCREEN, eval(Platform.INSTAGRAM, "com.instagram.android:id/profile_tab_layout", recyclerClass, top = 300, h = 2000).verdict)
        assertEquals(Verdict.OTHER_SCREEN, eval(Platform.INSTAGRAM, "com.instagram.android:id/recycler_view", recyclerClass, orientation = Orientation.GRID).verdict)
    }

    // ── YouTube ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `youtube - el pager de shorts es el feed`() {
        assertEquals(Verdict.FEED, eval(Platform.YOUTUBE, "com.google.android.youtube:id/reel_recycler", recyclerClass, h = 2200, from = 2, to = 3).verdict)
    }

    @Test
    fun `youtube - portada, estanteria de shorts, comentarios y reproductor normal no son el feed`() {
        assertEquals(Verdict.OTHER_SCREEN, eval(Platform.YOUTUBE, "com.google.android.youtube:id/results", recyclerClass, top = 150, h = 2050, from = 0, to = 1).verdict)
        assertEquals(Verdict.IGNORED, eval(Platform.YOUTUBE, "com.google.android.youtube:id/reel_shelf_items", recyclerClass, h = 700, dx = 400, dy = 0).verdict)
        assertEquals(Verdict.IGNORED, eval(Platform.YOUTUBE, "com.google.android.youtube:id/engagement_panel_list", recyclerClass, top = 800, h = 1600).verdict)
        assertEquals(Verdict.OTHER_SCREEN, eval(Platform.YOUTUBE, "com.google.android.youtube:id/watch_list", recyclerClass, top = 700, h = 1700).verdict)
    }

    @Test
    fun `youtube - tres paginas asomando en shorts siguen siendo el feed y la portada no`() {
        assertEquals(Verdict.FEED, eval(Platform.YOUTUBE, "com.google.android.youtube:id/reel_recycler", recyclerClass, h = 2200, from = 4, to = 6).verdict)
        assertEquals(Verdict.OTHER_SCREEN, eval(Platform.YOUTUBE, "com.google.android.youtube:id/reel_recycler", recyclerClass, h = 2200, from = 4, to = 8).verdict)
        assertFalse(FeedRules.rulesFor(Platform.YOUTUBE).contentCheck) // YouTube 21.44 no expone autor ni título
        assertFalse(FeedRules.rulesFor(Platform.TIKTOK).contentCheck)
        assertFalse(FeedRules.rulesFor(Platform.INSTAGRAM).contentCheck)
    }

    @Test
    fun `youtube - sin id no se acepta`() {
        assertEquals(Verdict.OTHER_SCREEN, eval(Platform.YOUTUBE, null, recyclerClass, from = 1, to = 1).verdict)
    }

    // ── TikTok ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `tiktok - pager vertical a pantalla completa es el feed (principal y detalle)`() {
        val main = eval(Platform.TIKTOK, "com.zhiliaoapp.musically:id/e3k", pagerClass, from = 5, to = 5, screen = "com.ss.android.ugc.aweme.main.MainActivity")
        assertEquals(Verdict.FEED, main.verdict)
        assertTrue(main.rule.contains("ViewPager"))
        assertEquals(Verdict.FEED, eval(Platform.TIKTOK, null, recyclerClass, from = 3, to = 4, screen = "com.ss.android.ugc.aweme.detail.ui.DetailActivity").verdict)
    }

    @Test
    fun `tiktok - rejillas, listas y comentarios no son el feed`() {
        assertEquals(Verdict.OTHER_SCREEN, eval(Platform.TIKTOK, null, recyclerClass, top = 300, h = 2100, from = 0, to = 8).verdict)
        assertEquals(Verdict.IGNORED, eval(Platform.TIKTOK, null, recyclerClass, top = 800, h = 1600, from = 0, to = 5).verdict)
        assertEquals(Verdict.IGNORED, eval(Platform.TIKTOK, null, "android.widget.TextView", h = 2240).verdict)
        assertEquals(Verdict.IGNORED, eval(Platform.TIKTOK, null, pagerClass, w = 600).verdict)
    }

    @Test
    fun `tiktok - el pager horizontal de pestanas no es el feed ni siquiera sin movimiento`() {
        assertEquals(Verdict.IGNORED, eval(Platform.TIKTOK, null, pagerClass, dx = 400, dy = 0).verdict)
        assertEquals(Verdict.IGNORED, eval(Platform.TIKTOK, null, pagerClass, dx = 0, dy = 0, orientation = Orientation.HORIZONTAL).verdict)
    }

    @Test
    fun `tiktok - LIVE queda fuera aunque sea un pager vertical`() {
        val d = eval(Platform.TIKTOK, null, pagerClass, from = 1, to = 1, screen = "com.ss.android.ugc.aweme.live.LivePlayActivity")
        assertEquals(Verdict.OTHER_SCREEN, d.verdict)
        assertTrue(d.rule.contains("pantalla excluida"))
    }

    // ── Generales ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `sin tamano de pantalla o sin nodo no se decide nada`() {
        val noScreen = FeedRules.evaluate(FeedRules.ScrollSample(Platform.TIKTOK, null, pagerClass, 0, 0, 1080, 2240, 0, 0))
        assertEquals(Verdict.IGNORED, noScreen.verdict)
        assertEquals(Verdict.IGNORED, eval(Platform.TIKTOK, null, pagerClass, w = 0, h = 0).verdict)
    }

    @Test
    fun `tipo de ventana y estilo de indices`() {
        assertEquals(WindowKind.ACTIVITY, FeedRules.windowKind("com.ss.android.ugc.aweme.main.MainActivity"))
        assertEquals(WindowKind.OVERLAY, FeedRules.windowKind("com.google.android.material.bottomsheet.BottomSheetDialog"))
        assertEquals(WindowKind.OTHER, FeedRules.windowKind("android.widget.FrameLayout"))
        assertEquals(WindowKind.OTHER, FeedRules.windowKind(null))
        assertEquals(IndexStyle.CURRENT_ITEM, FeedRules.indexStyleFor(pagerClass))
        assertEquals(IndexStyle.VISIBLE_RANGE, FeedRules.indexStyleFor(recyclerClass))
    }
}
