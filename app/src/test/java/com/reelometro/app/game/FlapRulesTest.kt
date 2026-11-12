package com.reelometro.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas de la vida de Flap ([FlapRules]): bordes de la tabla, vida y lo que falta para ir a peor. */
class FlapRulesTest {

    private fun moods(limit: Int, vararg counts: Int): List<FlapMood> = counts.map { FlapRules.moodFor(it, limit) }

    // ------------------------------------------------------------------ aguante

    @Test
    fun `sin objetivo, o con uno de 0 o negativo, aguanta 150`() {
        assertEquals(150, FlapRules.DEFAULT_LIMIT)
        assertEquals(FlapRules.DEFAULT_LIMIT, FlapRules.limitFor(null))
        assertEquals(FlapRules.DEFAULT_LIMIT, FlapRules.limitFor(0))
        assertEquals(FlapRules.DEFAULT_LIMIT, FlapRules.limitFor(-3))
        assertEquals(40, FlapRules.limitFor(40))
        assertEquals(1, FlapRules.limitFor(1))
    }

    // ------------------------------------------------------------------ estados: bordes de la tabla

    @Test
    fun `bordes con aguante 40`() {
        assertEquals(
            listOf(
                FlapMood.EN_FORMA, FlapMood.EN_FORMA, // 0 y 19: menos de la mitad
                FlapMood.NORMAL, FlapMood.NORMAL, // 20 y 31: de la mitad al 80 %
                FlapMood.CANSADO, FlapMood.CANSADO, // 32 y 40: del 80 % al aguante, incluido
                FlapMood.ENFERMO, FlapMood.ENFERMO, // 41 y 79: por encima, sin llegar al doble
                FlapMood.KO, FlapMood.KO, // 80 y 500: el doble o más
            ),
            moods(40, 0, 19, 20, 31, 32, 40, 41, 79, 80, 500),
        )
    }

    @Test
    fun `bordes con aguante 150 (sin objetivo)`() {
        assertEquals(
            listOf(
                FlapMood.EN_FORMA, FlapMood.EN_FORMA,
                FlapMood.NORMAL, FlapMood.NORMAL,
                FlapMood.CANSADO, FlapMood.CANSADO,
                FlapMood.ENFERMO, FlapMood.ENFERMO,
                FlapMood.KO,
            ),
            moods(150, 0, 74, 75, 119, 120, 150, 151, 299, 300),
        )
    }

    @Test
    fun `bordes con aguante 5`() {
        assertEquals(
            listOf(
                FlapMood.EN_FORMA, FlapMood.EN_FORMA, FlapMood.EN_FORMA, // 0, 1, 2
                FlapMood.NORMAL, // 3
                FlapMood.CANSADO, FlapMood.CANSADO, // 4, 5
                FlapMood.ENFERMO, FlapMood.ENFERMO, // 6, 9
                FlapMood.KO, // 10
            ),
            moods(5, 0, 1, 2, 3, 4, 5, 6, 9, 10),
        )
    }

    @Test
    fun `con aguante 1 no hay NORMAL ni ENFERMO`() {
        assertEquals(listOf(FlapMood.EN_FORMA, FlapMood.CANSADO, FlapMood.KO, FlapMood.KO), moods(1, 0, 1, 2, 3))
    }

    @Test
    fun `vídeos negativos o aguante raro no rompen nada`() {
        assertEquals(FlapMood.EN_FORMA, FlapRules.moodFor(-5, 40))
        assertEquals(FlapMood.CANSADO, FlapRules.moodFor(1, 0))
        assertEquals(FlapMood.KO, FlapRules.moodFor(2, -7))
        assertEquals(100, FlapRules.life(-5, 40))
        assertEquals(3, FlapRules.videosToWorse(-5, 5))
    }

    @Test
    fun `startOf es el primer recuento de cada estado`() {
        val limits = listOf(40, 150, 5, 1)
        for (limit in limits) {
            for (mood in FlapMood.entries) {
                val start = FlapRules.startOf(mood, limit)
                assertTrue("$mood/$limit: $start", FlapRules.moodFor(start, limit).ordinal >= mood.ordinal)
                if (start > 0) assertTrue("$mood/$limit: ${start - 1}", FlapRules.moodFor(start - 1, limit).ordinal < mood.ordinal)
            }
        }
        assertEquals(listOf(0, 20, 32, 41, 80), FlapMood.entries.map { FlapRules.startOf(it, 40) })
        assertEquals(listOf(0, 75, 120, 151, 300), FlapMood.entries.map { FlapRules.startOf(it, 150) })
        assertEquals(listOf(0, 3, 4, 6, 10), FlapMood.entries.map { FlapRules.startOf(it, 5) })
        assertEquals(listOf(0, 1, 1, 2, 2), FlapMood.entries.map { FlapRules.startOf(it, 1) })
    }

    // ------------------------------------------------------------------ vida

    @Test
    fun `vida 100 al empezar, 50 con el aguante, 1 justo antes del KO y 0 en KO`() {
        for (limit in listOf(40, 150, 5, 1)) {
            assertEquals("$limit", 100, FlapRules.life(0, limit))
            assertEquals("$limit", 50, FlapRules.life(limit, limit))
            assertEquals("$limit", 0, FlapRules.life(2 * limit, limit))
            assertEquals("$limit", 0, FlapRules.life(2 * limit + 37, limit))
        }
        assertEquals(1, FlapRules.life(299, 150))
        assertEquals(2, FlapRules.life(79, 40))
        assertEquals(10, FlapRules.life(9, 5))
        assertEquals(FlapRules.MAX_LIFE, 100)
    }

    @Test
    fun `vida en los bordes de cada estado`() {
        assertEquals(listOf(100, 77, 75, 62, 60, 50, 49, 2, 0), listOf(0, 19, 20, 31, 32, 40, 41, 79, 80).map { FlapRules.life(it, 40) })
        assertEquals(listOf(76, 75, 61, 60, 50), listOf(74, 75, 119, 120, 150).map { FlapRules.life(it, 150) })
        assertEquals(listOf(80, 70, 60, 50, 40, 10, 0), listOf(2, 3, 4, 5, 6, 9, 10).map { FlapRules.life(it, 5) })
        assertEquals(listOf(100, 50, 0), listOf(0, 1, 2).map { FlapRules.life(it, 1) })
    }

    @Test
    fun `enfermo nunca enseña la media vida del aguante justo`() {
        // Con aguantes grandes, el redondeo hacia arriba daría 50 a los primeros vídeos de más.
        assertEquals(listOf(50, 49, 49, 49), listOf(150, 151, 152, 153).map { FlapRules.life(it, 150) })
        assertEquals(listOf(50, 49), listOf(2_000, 2_001).map { FlapRules.life(it, 2_000) })
    }

    @Test
    fun `la vida de cada estado cae en su tramo y nunca sube al ver más vídeos`() {
        val bands = mapOf(
            FlapMood.EN_FORMA to 76..100,
            FlapMood.NORMAL to 61..75,
            FlapMood.CANSADO to 50..60,
            FlapMood.ENFERMO to 1..49,
            FlapMood.KO to 0..0,
        )
        for (limit in (1..400) + listOf(1_000, 1_999, 2_000)) {
            var previous = FlapRules.MAX_LIFE
            for (count in 0..(2 * limit + 3)) {
                val mood = FlapRules.moodFor(count, limit)
                val life = FlapRules.life(count, limit)
                assertTrue("$count/$limit: $mood con vida $life", life in bands.getValue(mood))
                assertTrue("$count/$limit: la vida sube de $previous a $life", life <= previous)
                previous = life
            }
        }
    }

    // ------------------------------------------------------------------ lo que falta para ir a peor

    @Test
    fun `videosToWorse y nextWorse con aguante 40`() {
        assertEquals(20, FlapRules.videosToWorse(0, 40))
        assertEquals(FlapMood.NORMAL, FlapRules.nextWorse(0, 40))
        assertEquals(1, FlapRules.videosToWorse(19, 40))
        assertEquals(12, FlapRules.videosToWorse(20, 40))
        assertEquals(FlapMood.CANSADO, FlapRules.nextWorse(20, 40))
        assertEquals(9, FlapRules.videosToWorse(32, 40))
        assertEquals(1, FlapRules.videosToWorse(40, 40))
        assertEquals(FlapMood.ENFERMO, FlapRules.nextWorse(40, 40))
        assertEquals(39, FlapRules.videosToWorse(41, 40))
        assertEquals(FlapMood.KO, FlapRules.nextWorse(41, 40))
        assertEquals(1, FlapRules.videosToWorse(79, 40))
        assertNull(FlapRules.videosToWorse(80, 40))
        assertNull(FlapRules.nextWorse(80, 40))
        assertNull(FlapRules.videosToWorse(1_000, 40))
    }

    @Test
    fun `videosToWorse con aguante 150 y 5`() {
        assertEquals(listOf(75, 1, 45, 31, 1, 149, 1), listOf(0, 74, 75, 120, 150, 151, 299).map { FlapRules.videosToWorse(it, 150) })
        assertNull(FlapRules.videosToWorse(300, 150))
        assertEquals(listOf(3, 1, 1, 2, 1, 4, 1), listOf(0, 2, 3, 4, 5, 6, 9).map { FlapRules.videosToWorse(it, 5) })
        assertNull(FlapRules.videosToWorse(10, 5))
    }

    @Test
    fun `nextWorse salta los tramos vacíos (aguante 1)`() {
        // Con aguante 1, NORMAL y ENFERMO no existen: de EN_FORMA se pasa a CANSADO y de CANSADO a KO.
        assertEquals(FlapMood.CANSADO, FlapRules.nextWorse(0, 1))
        assertEquals(1, FlapRules.videosToWorse(0, 1))
        assertEquals(FlapMood.KO, FlapRules.nextWorse(1, 1))
        assertEquals(1, FlapRules.videosToWorse(1, 1))
        assertNull(FlapRules.nextWorse(2, 1))
        assertNull(FlapRules.videosToWorse(2, 1))
    }

    @Test
    fun `con los vídeos que faltan se llega justo al estado anunciado`() {
        for (limit in (1..200) + listOf(1_000, 2_000)) {
            for (count in 0 until 2 * limit) {
                val mood = FlapRules.moodFor(count, limit)
                val next = FlapRules.nextWorse(count, limit)
                val missing = FlapRules.videosToWorse(count, limit)
                assertTrue("$count/$limit", next != null && missing != null && missing > 0)
                assertTrue("$count/$limit", next!!.ordinal > mood.ordinal)
                assertEquals("$count/$limit", next, FlapRules.moodFor(count + missing!!, limit))
                assertEquals("$count/$limit", mood, FlapRules.moodFor(count + missing - 1, limit))
            }
            assertNull(FlapRules.nextWorse(2 * limit, limit))
        }
    }

    // ------------------------------------------------------------------ días sanos

    @Test
    fun `un día es sano si no pasa del aguante`() {
        assertTrue(FlapRules.healthy(0, 40))
        assertTrue(FlapRules.healthy(40, 40))
        assertEquals(false, FlapRules.healthy(41, 40))
        assertTrue(FlapRules.healthy(1, 0))
        assertEquals(false, FlapRules.healthy(2, 0))
        for (limit in 1..100) for (count in 0..(2 * limit + 1)) {
            val sick = FlapRules.moodFor(count, limit).ordinal >= FlapMood.ENFERMO.ordinal
            assertEquals("$count/$limit", !sick, FlapRules.healthy(count, limit))
        }
    }
}
