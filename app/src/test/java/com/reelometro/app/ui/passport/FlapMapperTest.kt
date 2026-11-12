package com.reelometro.app.ui.passport

import com.reelometro.app.copy.CopyRules
import com.reelometro.app.copy.CopyRules.plain
import com.reelometro.app.copy.FlapCopy
import com.reelometro.app.game.FakeFlapSource
import com.reelometro.app.game.FlapFakes
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapRules
import com.reelometro.app.game.FlapState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** De `FlapState` a lo que pinta la pestaña, y el flujo de estados con `FakeFlapSource` (JVM puro). */
@OptIn(ExperimentalCoroutinesApi::class)
class FlapMapperTest {

    private val today = LocalDate.of(2026, 11, 4)

    private fun texts(c: FlapUiState.Content): List<String> = buildList {
        addAll(listOfNotNull(c.kicker, c.label, c.phrase, c.recoveredPhrase, c.lifeValue, c.lifeDescription, c.today, c.next))
        addAll(listOfNotNull(c.noGoal, c.streak, c.description))
        addAll(c.how)
        c.week.forEach { d -> addAll(listOfNotNull(d.initial, d.count, d.description)) }
    }

    private fun assertRules(c: FlapUiState.Content, where: String) {
        texts(c).forEach { t ->
            val v = CopyRules.violations(t)
            assertTrue("«$t» ($where): $v", v.isEmpty())
        }
    }

    // ------------------------------------------------------------------ estados

    @Test
    fun `los cinco estados tienen nombre, frase y vida coherentes`() {
        val all = FlapFakes.all(goal = 40).map { it.copy(today = today) }
        assertEquals(FlapMood.entries, all.map { it.mood })
        val lives = mutableListOf<Int>()
        for (state in all) {
            val c = FlapMapper.map(state)
            assertEquals(state.mood, c.mood)
            assertEquals(FlapCopy.moodLabel(state.mood), c.label)
            assertEquals(state.life, c.life)
            assertEquals(state.life / 100f, c.lifeFraction, 0.0001f)
            assertEquals(FlapCopy.lifeValue(state.life), c.lifeValue)
            assertEquals(state.mood == FlapMood.ENFERMO || state.mood == FlapMood.KO, c.danger)
            assertEquals(state.mood == FlapMood.KO, c.ko)
            assertTrue(c.description, plain(c.description).startsWith("Flap: ${FlapCopy.moodSpoken(state.mood)}, vida ${c.lifeValue} de 100"))
            assertEquals(FlapCopy.moodPhrase(state.mood, today.toEpochDay()), c.phrase)
            assertEquals(7, c.week.size)
            assertNull("sin cambio pendiente", c.previousMood)
            assertFalse(c.recovered)
            assertNull(c.recoveredPhrase)
            assertEquals("HOY · MIÉRCOLES 4 NOV", plain(c.kicker))
            assertRules(c, "${state.mood}")
            lives += c.life
        }
        // Del mejor al peor, la vida no sube.
        assertEquals(lives.sortedDescending(), lives)
    }

    @Test
    fun `KO dice hasta cuándo, sin vida y con el acento`() {
        val c = FlapMapper.map(FlapFakes.sample(FlapMood.KO, goal = 40, today = today))
        assertEquals(0, c.life)
        assertEquals(0f, c.lifeFraction, 0f)
        assertTrue(c.ko)
        assertTrue(c.danger)
        assertEquals(FlapCopy.KO_UNTIL_TOMORROW, c.next)
        assertEquals("KO", c.label)
        assertEquals("Flap: fuera de combate, vida 0 de 100.", c.description)
    }

    @Test
    fun `lo que falta y la línea de hoy salen de los vídeos y el aguante`() {
        val c = FlapMapper.map(FlapFakes.state(today, goal = 40, count = 35))
        assertEquals(FlapMood.CANSADO, c.mood)
        assertEquals("HOY 35 VÍDEOS · AGUANTA 40", plain(c.today))
        assertEquals("Con 6 vídeos más se pondrá enfermo.", plain(c.next))
        assertFalse(c.danger)
        val one = FlapMapper.map(FlapFakes.state(today, goal = 40, count = 1))
        assertEquals("HOY 1 VÍDEO · AGUANTA 40", plain(one.today))
    }

    // ------------------------------------------------------------------ objetivo

    @Test
    fun `sin objetivo invita a ponerlo y explica el aguante de 150`() {
        val c = FlapMapper.map(FlapFakes.sample(FlapMood.NORMAL, goal = null, today = today))
        assertNotNull(c.noGoal)
        assertTrue(c.noGoal!!, "150 vídeos" in plain(c.noGoal!!))
        assertEquals(3, c.how.size)
        assertTrue(c.how[2], "150 vídeos" in plain(c.how[2]) && "(300)" in plain(c.how[2]))
        assertTrue(plain(c.today), plain(c.today).endsWith("AGUANTA 150"))
        assertRules(c, "sin objetivo")

        val withGoal = FlapMapper.map(FlapFakes.sample(FlapMood.NORMAL, goal = 40, today = today))
        assertNull(withGoal.noGoal)
        assertTrue(withGoal.how[2], "tu objetivo (40 vídeos)" in plain(withGoal.how[2]) && "(80)" in plain(withGoal.how[2]))
    }

    // ------------------------------------------------------------------ instalación nueva

    @Test
    fun `recién instalada Flap está en forma, lo explica y no hay semana ni racha`() {
        val c = FlapMapper.map(FlapFakes.fresh(today))
        assertEquals(FlapMood.EN_FORMA, c.mood)
        assertEquals(100, c.life)
        assertTrue(c.week.isEmpty())
        assertNull(c.streak)
        assertNotNull("sin objetivo: invita a ponerlo", c.noGoal)
        assertEquals(3, c.how.size)
        assertEquals(FlapCopy.freshPhrase(today.toEpochDay()), c.phrase)
        assertEquals("Con 75 vídeos más dejará de estar en forma.", plain(c.next))
        assertRules(c, "recién instalada")
    }

    // ------------------------------------------------------------------ semana y racha

    @Test
    fun `la semana tiene huecos antes del primer registro`() {
        // Con 3 días de registro (hoy incluido), solo los 2 últimos días cerrados tienen vídeos.
        val state = FlapFakes.state(today, goal = 40, count = 10, tracked = 3)
        val c = FlapMapper.map(state)
        assertEquals(7, c.week.size)
        assertEquals(state.week.map { it.date.toString() }, c.week.map { it.key })
        val empty = c.week.take(5)
        val known = c.week.drop(5)
        empty.forEach { d ->
            assertNull(d.mood)
            assertNull(d.count)
            assertTrue(d.description, plain(d.description).endsWith(": sin registro."))
        }
        known.zip(state.week.drop(5)).forEach { (d, day) ->
            assertEquals(day.mood, d.mood)
            assertEquals(FlapCopy.dayCount(day.count), d.count)
            assertTrue(d.description, plain(d.description).endsWith(", ${FlapCopy.moodSpoken(day.mood!!)}."))
        }
        // Del miércoles 28 de octubre al martes 3 de noviembre (hoy es miércoles 4).
        assertEquals(listOf("X", "J", "V", "S", "D", "L", "M"), c.week.map { it.initial })
        assertRules(c, "semana con huecos")
    }

    @Test
    fun `racha 0, 1 y n`() {
        assertNull(FlapMapper.map(FlapFakes.state(today, healthyStreak = 0)).streak)
        assertEquals("1 día seguido sin que enferme", plain(FlapMapper.map(FlapFakes.state(today, healthyStreak = 1)).streak!!))
        assertEquals("12 días seguidos sin que enferme", plain(FlapMapper.map(FlapFakes.state(today, healthyStreak = 12)).streak!!))
    }

    // ------------------------------------------------------------------ cambios de estado

    @Test
    fun `al revivir hay salto alegre y frase de vuelta, al empeorar solo el volteo`() {
        val revived = FlapMapper.map(FlapFakes.sample(FlapMood.EN_FORMA, today = today, previousMood = FlapMood.KO))
        assertEquals(FlapMood.KO, revived.previousMood)
        assertTrue(revived.recovered)
        assertEquals(FlapCopy.recoveredPhrase(today.toEpochDay()), revived.recoveredPhrase)

        val worse = FlapMapper.map(FlapFakes.sample(FlapMood.CANSADO, today = today, previousMood = FlapMood.EN_FORMA))
        assertEquals(FlapMood.EN_FORMA, worse.previousMood)
        assertFalse(worse.recovered)
        assertNull(worse.recoveredPhrase)

        // El mismo estado no es un cambio.
        val same = FlapState(
            today = today, goal = 40, limit = 40, count = 0, mood = FlapMood.EN_FORMA, life = 100,
            videosToWorse = 20, previousMood = FlapMood.EN_FORMA,
        )
        val c = FlapMapper.map(same)
        assertNull(c.previousMood)
        assertFalse(c.recovered)
    }

    @Test
    fun `todos los vídeos posibles con varios objetivos cumplen las reglas`() {
        for (goal in listOf<Int?>(null, 1, 2, 3, 40, 150)) {
            val limit = FlapRules.limitFor(goal)
            for (count in (0..limit * 2 + 3).toList() + listOf(9_999, 1_234_567)) {
                val c = FlapMapper.map(FlapFakes.state(today, goal, count))
                assertRules(c, "objetivo $goal, $count vídeos")
                assertEquals(FlapRules.moodFor(count, limit), c.mood)
                assertTrue(c.life in 0..100)
            }
        }
    }

    @Test
    fun `el error lleva una frase`() {
        val e = FlapMapper.error(today) as FlapUiState.Error
        assertTrue(e.message.isNotBlank())
        assertTrue(FlapMapper.loading() is FlapUiState.Loading)
    }

    // ------------------------------------------------------------------ flujo de estados

    @Test
    fun `el estado del origen llega como contenido y se actualiza`() = runTest {
        val source = FakeFlapSource(FlapFakes.fresh(today))
        val out = mutableListOf<FlapUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            FlapMapper.states(source::observe, MutableStateFlow(0), { today }).collect { out += it }
        }
        assertEquals(1, out.size)
        assertTrue((out.last() as FlapUiState.Content).week.isEmpty())
        source.state.value = FlapFakes.sample(FlapMood.ENFERMO, today = today)
        assertEquals(2, out.size)
        assertEquals(FlapMood.ENFERMO, (out.last() as FlapUiState.Content).mood)
    }

    @Test
    fun `si el origen falla sale el error y al reintentar se recupera`() = runTest {
        var calls = 0
        val attempts = MutableStateFlow(0)
        val good = FlapFakes.sample(today = today)
        val out = mutableListOf<FlapUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            FlapMapper.states(
                source = { if (calls++ == 0) flow<FlapState> { throw IllegalStateException("sin base de datos") } else flowOf(good) },
                attempts = attempts,
                clock = { today },
            ).collect { out += it }
        }
        assertTrue(out.single() is FlapUiState.Error)
        attempts.value = 1
        assertEquals(3, out.size)
        assertTrue(out[1] is FlapUiState.Loading)
        assertTrue(out[2] is FlapUiState.Content)
    }

    @Test
    fun `al volver a suscribirse tras un reintento no pasa otra vez por Loading`() = runTest {
        // El stateIn del ViewModel vuelve a arrancar con el intento que ya había (1): el contenido sale directo.
        val source = FakeFlapSource(FlapFakes.sample(today = today))
        val first = FlapMapper.states(source::observe, MutableStateFlow(1), { today }).first()
        assertTrue("$first", first is FlapUiState.Content)
    }

    @Test
    fun `marcar el estado visto quita el cambio pendiente`() = runTest {
        val source = FakeFlapSource(FlapFakes.sample(FlapMood.EN_FORMA, today = today, previousMood = FlapMood.KO))
        val first = FlapMapper.states(source::observe, MutableStateFlow(0), { today }).first() as FlapUiState.Content
        assertEquals(FlapMood.KO, first.previousMood)
        assertTrue(first.recovered)
        source.markMoodSeen(FlapMood.EN_FORMA)
        val second = FlapMapper.states(source::observe, MutableStateFlow(0), { today }).first() as FlapUiState.Content
        assertNull(second.previousMood)
        assertFalse(second.recovered)
        assertEquals(listOf(FlapMood.EN_FORMA), source.seen)
    }

    @Test
    fun `el aviso de estado visto no se repite para el mismo estado`() {
        val gate = MoodSeenGate()
        assertTrue(gate.shouldMark(FlapMood.NORMAL))
        assertFalse(gate.shouldMark(FlapMood.NORMAL))
        assertTrue(gate.shouldMark(FlapMood.ENFERMO))
        assertTrue(gate.shouldMark(FlapMood.NORMAL))
    }

    @Test
    fun `tras olvidar, el mismo estado se vuelve a marcar`() {
        val gate = MoodSeenGate()
        assertTrue(gate.shouldMark(FlapMood.EN_FORMA))
        gate.reset()
        assertTrue(gate.shouldMark(FlapMood.EN_FORMA))
    }
}
