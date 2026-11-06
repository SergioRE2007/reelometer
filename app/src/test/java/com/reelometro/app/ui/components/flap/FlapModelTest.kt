package com.reelometro.app.ui.components.flap

import com.reelometro.app.ui.components.flap.FlapCell.Separator
import com.reelometro.app.ui.components.flap.FlapCell.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlapModelTest {

    private fun glyphs(cells: List<FlapCell>): String =
        cells.joinToString("") { if (it is Tile) it.glyph.toString() else "." }

    // ------------------------------------------------------------------ secuencia de volteo

    @Test
    fun `una cifra que sube pasa por todos los intermedios`() {
        assertEquals("3456789".toList(), flapPath('2', '9'))
    }

    @Test
    fun `el tambor solo gira hacia delante`() {
        assertEquals("89012".toList(), flapPath('7', '2'))
        assertEquals("0".toList(), flapPath('9', '0'))
        assertEquals("234567890".toList(), flapPath('1', '0'))
    }

    @Test
    fun `sin cambio no hay volteo`() {
        assertEquals(emptyList<Char>(), flapPath('5', '5'))
        assertEquals(emptyList<Char>(), flapPath(FlapGlyphs.DASH, FlapGlyphs.DASH))
    }

    @Test
    fun `desde ficha vacia o guion las cifras empiezan en cero`() {
        assertEquals("0123".toList(), flapPath(FlapGlyphs.EMPTY, '3'))
        assertEquals("0".toList(), flapPath(FlapGlyphs.DASH, '0'))
        assertEquals("01234567".toList(), flapPath('K', '7'))
    }

    @Test
    fun `hacia el guion o la ficha vacia basta un volteo`() {
        assertEquals(listOf(FlapGlyphs.DASH), flapPath('8', FlapGlyphs.DASH))
        assertEquals(listOf(FlapGlyphs.EMPTY), flapPath('8', FlapGlyphs.EMPTY))
    }

    @Test
    fun `las letras usan su propio tambor con la enye`() {
        assertEquals("LMNÑO".toList(), flapPath('K', 'O'))
        assertEquals("ABC".toList(), flapPath('2', 'C'))
    }

    @Test
    fun `los saltos largos se recortan por el principio para llegar seguidos`() {
        val path = flapPath(FlapGlyphs.EMPTY, 'Z', maxFlips = 5)
        assertEquals("VWXYZ".toList(), path)
        assertEquals(FlapGlyphs.MAX_FLIPS, flapPath(FlapGlyphs.EMPTY, 'Y').size)
        assertEquals(1, flapPath('1', '0', maxFlips = 0).size)
    }

    @Test
    fun `cualquier salto de cifra cabe en el tope por defecto`() {
        for (from in "0123456789 –") for (to in "0123456789") {
            val path = flapPath(from, to)
            assertTrue(path.size <= FlapGlyphs.MAX_FLIPS)
            if (from != to) assertEquals(to, path.last())
        }
    }

    // ------------------------------------------------------------------ reparto en columnas

    @Test
    fun `cifras con ceros a la izquierda hasta el minimo de columnas`() {
        assertEquals("007", glyphs(flapCells(FlapContent.Number(7), 3, FlapPad.Zeros)))
        assertEquals("212", glyphs(flapCells(FlapContent.Number(212), 3, FlapPad.Zeros)))
        assertEquals("000", glyphs(flapCells(FlapContent.Number(0), 3, FlapPad.Zeros)))
    }

    @Test
    fun `relleno con fichas vacias o sin relleno`() {
        assertEquals("  7", glyphs(flapCells(FlapContent.Number(7), 3, FlapPad.Blank)))
        assertEquals("7", glyphs(flapCells(FlapContent.Number(7), 1, FlapPad.Zeros)))
    }

    @Test
    fun `punto de millar a la espanola entre fichas`() {
        assertEquals("1.234", glyphs(flapCells(FlapContent.Number(1234), 3, FlapPad.Zeros)))
        assertEquals("12.408", glyphs(flapCells(FlapContent.Number(12_408), 3, FlapPad.Zeros)))
        assertEquals("1.234.567", glyphs(flapCells(FlapContent.Number(1_234_567), 3, FlapPad.Zeros)))
        assertEquals("999", glyphs(flapCells(FlapContent.Number(999), 3, FlapPad.Zeros)))
    }

    @Test
    fun `los ceros de relleno no llevan punto`() {
        assertEquals("0999", glyphs(flapCells(FlapContent.Number(999), 4, FlapPad.Zeros)))
        assertEquals("1.000", glyphs(flapCells(FlapContent.Number(1000), 4, FlapPad.Zeros)))
        assertEquals("   7", glyphs(flapCells(FlapContent.Number(7), 4, FlapPad.Blank)))
    }

    @Test
    fun `las posiciones cuentan desde las unidades`() {
        val cells = flapCells(FlapContent.Number(1234), 3, FlapPad.Zeros)
        assertEquals(listOf(Tile(3, '1'), Separator, Tile(2, '2'), Tile(1, '3'), Tile(0, '4')), cells)
    }

    @Test
    fun `negativos en valor absoluto`() {
        assertEquals("042", glyphs(flapCells(FlapContent.Number(-42), 3, FlapPad.Zeros)))
        assertEquals(
            "9.223.372.036.854.775.808",
            glyphs(flapCells(FlapContent.Number(Long.MIN_VALUE), 3, FlapPad.Zeros)),
        )
    }

    @Test
    fun `en blanco son guiones sin separadores`() {
        assertEquals("–––", glyphs(flapCells(FlapContent.Blank, 3, FlapPad.Zeros)))
        assertEquals("–", glyphs(flapCells(FlapContent.Blank, 0, FlapPad.Zeros)))
    }

    @Test
    fun `texto en mayusculas alineado a la derecha`() {
        assertEquals("OK", glyphs(flapCells(FlapContent.Text("ok"), 2, FlapPad.Zeros)))
        assertEquals(" OK", glyphs(flapCells(FlapContent.Text("ok"), 3, FlapPad.Zeros)))
        assertEquals("AÑO", glyphs(flapCells(FlapContent.Text("año"), 1, FlapPad.Zeros)))
        val cells = flapCells(FlapContent.Text("OK"), 3, FlapPad.Zeros).tiles
        assertEquals(listOf(2, 1, 0), cells.map { it.position })
    }

    // ------------------------------------------------------------------ fotogramas y retoma

    @Test
    fun `fotograma en reposo y a mitad de volteo`() {
        assertEquals(FlapFrame('7', '7', 0f), flapFrame(listOf('7'), 0f))
        assertEquals(FlapFrame('2', '3', 0.25f), flapFrame("234".toList(), 0.25f))
        assertEquals(FlapFrame('3', '4', 0.5f), flapFrame("234".toList(), 1.5f))
        assertEquals(FlapFrame('4', '4', 0f), flapFrame("234".toList(), 2f))
    }

    @Test
    fun `el exceso del muelle se dibuja como rebote de la ultima hoja`() {
        val f = flapFrame("234".toList(), 2.04f)
        assertEquals('3', f.from)
        assertEquals('4', f.to)
        assertEquals(0.96f, f.fraction, 1e-4f)
    }

    @Test
    fun `retomar a mitad de hoja conserva la hoja en el aire`() {
        val (base, start) = flapRestart("23456".toList(), 2.4f)
        assertEquals("45".toList(), base)
        assertEquals(0.4f, start, 1e-4f)
        assertEquals("6".toList() to 0f, flapRestart("23456".toList(), 4f))
        assertEquals("3".toList() to 0f, flapRestart("23456".toList(), 1f))
    }

    // ------------------------------------------------------------------ medidas y autoajuste

    private val metrics = FlapGlyphMetrics(advanceEm = 0.5f, inkTopEm = -0.72f, inkBottomEm = 0.01f)

    @Test
    fun `ancho del marcador con fichas huecos y puntos`() {
        val w = metrics.tileWidthEm
        val three = boardWidthEm(flapCells(FlapContent.Number(212), 3, FlapPad.Zeros), w)
        assertEquals(3 * w + 2 * FlapProportions.GAP, three, 1e-5f)
        val four = boardWidthEm(flapCells(FlapContent.Number(1234), 3, FlapPad.Zeros), w)
        assertEquals(4 * w + 3 * FlapProportions.GAP + FlapProportions.DOT + FlapProportions.GAP, four, 1e-5f)
        assertEquals(0f, boardWidthEm(emptyList(), w))
    }

    @Test
    fun `autoajuste encoge para caber y nunca agranda`() {
        val wEm = 2.2f
        val hEm = metrics.tileHeightEm
        assertEquals(346f, fitFontPx(346f, wEm, hEm, maxWidthPx = 2000f), 1e-3f)
        val fs = fitFontPx(346f, wEm, hEm, maxWidthPx = 600f)
        assertEquals(600f / wEm, fs, 1e-3f)
        assertTrue(fs * wEm <= 600f + 1e-3f)
        assertEquals(100f / hEm, fitFontPx(346f, wEm, hEm, maxHeightPx = 100f), 1e-3f)
        assertEquals(346f, fitFontPx(346f, wEm, hEm), 1e-3f)
    }

    @Test
    fun `la ficha es mas alta que ancha`() {
        assertTrue(metrics.tileHeightEm > metrics.tileWidthEm)
    }

    // ------------------------------------------------------------------ odómetro

    @Test
    fun `la rueda sube por el camino corto hacia delante`() {
        assertEquals(7f, wheelTarget(3f, 7, up = true))
        assertEquals(12f, wheelTarget(7f, 2, up = true))
        assertEquals(5f, wheelTarget(5f, 5, up = true))
    }

    @Test
    fun `la rueda baja hacia atras`() {
        assertEquals(3f, wheelTarget(7f, 3, up = false))
        assertEquals(-1f, wheelTarget(2f, 9, up = false))
    }

    @Test
    fun `la rueda a mitad de giro no da vueltas de mas`() {
        assertEquals(4f, wheelTarget(3.4f, 4, up = true))
        assertEquals(3f, wheelTarget(3.4f, 3, up = false))
        assertEquals(13f, wheelTarget(3.4f, 3, up = true))
    }
}
