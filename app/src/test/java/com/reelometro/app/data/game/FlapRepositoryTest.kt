package com.reelometro.app.data.game

import com.reelometro.app.data.Ticker
import com.reelometro.app.game.FlapHistoryLoader
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapSeen
import com.reelometro.app.game.FlapSeenStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Vídeos por día en memoria; los primeros [fail] cálculos fallan. */
private class RepoTestLoader(private var fail: Int = 0) : FlapHistoryLoader {
    val data = MutableStateFlow<Map<LocalDate, Int>>(emptyMap())
    var loads = 0

    override fun changes(): Flow<Unit> = data.map { }

    override suspend fun counts(today: LocalDate): Map<LocalDate, Int> {
        loads++
        if (fail > 0) {
            fail--
            throw IllegalStateException("fallo simulado")
        }
        return data.value
    }
}

private class RepoTestSeen : FlapSeenStore {
    val state = MutableStateFlow<FlapSeen?>(null)
    var dropped = 0

    override val seen: Flow<FlapSeen?> = state

    override suspend fun save(seen: FlapSeen) {
        state.value = seen
    }

    override suspend fun clear() {
        state.value = null
    }

    override suspend fun dropLegacy() {
        dropped++
    }
}

/** [FlapRepository] sin Room: flujo compartido, reloj, BORRAR TODO y reintento tras un fallo. Solo JVM. */
@OptIn(ExperimentalCoroutinesApi::class)
class FlapRepositoryTest {

    private val zone: ZoneId = ZoneId.of("Europe/Madrid")
    private val today: LocalDate = LocalDate.of(2026, 11, 16)
    private val clock: Clock = Clock.fixed(today.atTime(20, 0).atZone(zone).toInstant(), zone)

    private fun TestScope.repository(loader: RepoTestLoader, seen: RepoTestSeen): FlapRepository = FlapRepository(
        loader = loader,
        dailyGoal = flowOf(40),
        store = seen,
        clock = clock,
        ticker = Ticker { flowOf(Unit) },
        dispatcher = UnconfinedTestDispatcher(testScheduler),
        throttleMs = 0,
    )

    @Test
    fun `observe y snapshot dan lo mismo, lo visto lleva el día del reloj y BORRAR TODO lo olvida`() = runTest {
        val loader = RepoTestLoader().apply { data.value = mapOf(today to 45) }
        val seen = RepoTestSeen()
        val flap = repository(loader, seen)
        val state = flap.observe().first()
        assertEquals(today, state.today)
        assertEquals(FlapMood.ENFERMO, state.mood)
        assertEquals(state, flap.snapshot())

        flap.markMoodSeen(FlapMood.CANSADO)
        assertEquals(FlapSeen(FlapMood.CANSADO, today), seen.state.value)
        assertEquals(FlapMood.CANSADO, flap.snapshot().previousMood)

        flap.clearAll()
        assertNull(seen.state.value)
        assertNull(flap.snapshot().previousMood)

        flap.dropLegacy()
        assertEquals(1, seen.dropped)
    }

    @Test
    fun `un fallo llega a quien mira y el cálculo se reintenta solo`() = runTest {
        // Fallan el cálculo compartido y el recálculo inmediato de quien mira.
        val loader = RepoTestLoader(fail = 2).apply { data.value = mapOf(today to 3) }
        val flap = repository(loader, RepoTestSeen())
        val error = runCatching { flap.observe().first() }.exceptionOrNull()
        assertTrue("$error", error is IllegalStateException)
        assertEquals(2, loader.loads)

        advanceTimeBy(2_500)
        // El reintento automático ya ha recalculado (3.ª lectura) sin que nadie mire.
        assertEquals(3, loader.loads)
        // Quien llega ahora lo saca de la caché (un éxito): no recalcula.
        assertEquals(FlapMood.EN_FORMA, flap.observe().first().mood)
        assertEquals(3, loader.loads)
    }

    @Test
    fun `REINTENTAR recalcula al momento aunque el cálculo compartido espere su reintento`() = runTest {
        val loader = RepoTestLoader(fail = 2).apply { data.value = mapOf(today to 3) }
        val flap = repository(loader, RepoTestSeen())
        val error = runCatching { flap.observe().first() }.exceptionOrNull()
        assertTrue("$error", error is IllegalStateException)

        // Sin esperar: en la caché sigue el fallo, pero quien vuelve a mirar recalcula.
        assertEquals(FlapMood.EN_FORMA, flap.observe().first().mood)
        assertEquals(3, loader.loads)
    }
}
