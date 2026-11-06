package com.reelometro.app.copy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VariantsTest {

    private val keys = listOf("today.zero", "today.high", "stats.peak.late", "x", "", "una clave con tildes: añadió")

    @Test
    fun `misma semilla y clave, misma variante`() {
        for (size in 1..6) for (key in keys) for (seed in -50L..50L) {
            assertEquals(Variants.index(size, seed, key), Variants.index(size, seed, key))
        }
    }

    @Test
    fun `el índice siempre está dentro de rango, también con semillas negativas o enormes`() {
        val seeds = (-1_000L..1_000L) + listOf(Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE + 1, Long.MAX_VALUE - 1)
        for (size in 1..6) for (key in keys) for (seed in seeds) {
            assertTrue(Variants.index(size, seed, key) in 0 until size)
        }
    }

    @Test
    fun `nunca repite variante dos días seguidos`() {
        for (size in 2..6) for (key in keys) for (seed in -400L..2_000L) {
            assertNotEquals("size=$size key=$key seed=$seed", Variants.index(size, seed, key), Variants.index(size, seed + 1, key))
        }
    }

    @Test
    fun `en cada bloque de N días salen las N variantes`() {
        for (size in 3..6) for (key in keys) for (block in -20L..200L) {
            val start = block * size
            val seen = (start until start + size).map { Variants.index(size, it, key) }.toSet()
            assertEquals("size=$size key=$key bloque=$block", size, seen.size)
        }
    }

    @Test
    fun `el orden cambia de un bloque a otro y de una clave a otra`() {
        val size = 5
        val orders = (0L until 40L).map { block -> (block * size until block * size + size).map { Variants.index(size, it, "today.normal") } }
        assertTrue("Siempre el mismo orden: no es variado", orders.toSet().size > 5)
        val a = (0L until 60L).map { Variants.index(size, it, "today.normal") }
        val b = (0L until 60L).map { Variants.index(size, it, "today.high") }
        assertNotEquals("Dos situaciones van sincronizadas", a, b)
    }

    @Test
    fun `pick devuelve el elemento del índice y daySeed es el epochDay`() {
        val options = listOf("a", "b", "c", "d")
        for (seed in 0L..30L) assertEquals(options[Variants.index(4, seed, "k")], Variants.pick(options, seed, "k"))
        val d = LocalDate.of(2026, 11, 3)
        assertEquals(d.toEpochDay(), Variants.daySeed(d))
    }

    @Test
    fun `una sola variante siempre sale`() {
        for (seed in -10L..10L) assertEquals("única", Variants.pick(listOf("única"), seed, "k"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `sin variantes es un error de programación`() {
        Variants.pick(emptyList<String>(), 0, "vacía")
    }
}
