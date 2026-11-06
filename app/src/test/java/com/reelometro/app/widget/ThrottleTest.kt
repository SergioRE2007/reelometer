package com.reelometro.app.widget

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThrottleTest {

    private val interval = WidgetUpdater.MIN_INTERVAL_MS

    /** Arranca el limitador y devuelve el canal y los instantes (tiempo virtual) de cada ejecución. */
    private fun TestScope.start(work: Long = 0, fail: Boolean = false): Pair<Channel<Unit>, MutableList<Long>> {
        val requests = Channel<Unit>(Channel.CONFLATED)
        val runs = mutableListOf<Long>()
        backgroundScope.launch {
            throttle(requests, interval) {
                runs += currentTime
                if (work > 0) delay(work)
                if (fail) error("fallo de lectura")
            }
        }
        return requests to runs
    }

    /** Deja pasar varios intervalos (advanceUntilIdle no espera a los trabajos de backgroundScope). */
    private fun TestScope.settle() {
        advanceTimeBy(5 * interval)
        runCurrent()
    }

    @Test
    fun `una peticion se atiende al momento`() = runTest {
        val (requests, runs) = start()
        requests.trySend(Unit)
        runCurrent()
        assertEquals(listOf(0L), runs)
    }

    @Test
    fun `una rafaga se funde en una inmediata y una final`() = runTest {
        val (requests, runs) = start()
        repeat(10) {
            requests.trySend(Unit)
            advanceTimeBy(500)
        }
        settle()
        assertEquals(listOf(0L, interval), runs)
    }

    @Test
    fun `scroll continuo - como mucho una cada intervalo y siempre una final`() = runTest {
        val (requests, runs) = start()
        // Un vídeo cada 3 s durante un minuto.
        repeat(21) {
            requests.trySend(Unit)
            advanceTimeBy(3_000)
        }
        settle()
        assertEquals(listOf(0L, interval, 2 * interval, 3 * interval), runs)
        runs.zipWithNext().forEach { (a, b) -> assertEquals(interval, b - a) }
    }

    @Test
    fun `la peticion que llega mientras se leen los datos provoca otra al acabar la espera`() = runTest {
        val (requests, runs) = start(work = 1_000)
        requests.trySend(Unit)
        advanceTimeBy(500) // a mitad de la lectura
        requests.trySend(Unit)
        settle()
        assertEquals(listOf(0L, 1_000 + interval), runs)
    }

    @Test
    fun `peticiones separadas mas que el intervalo se atienden cada una al llegar`() = runTest {
        val (requests, runs) = start()
        requests.trySend(Unit)
        advanceTimeBy(interval + 5_000)
        requests.trySend(Unit)
        runCurrent()
        assertEquals(listOf(0L, interval + 5_000), runs)
    }

    @Test
    fun `un fallo no detiene el limitador`() = runTest {
        val (requests, runs) = start(fail = true)
        requests.trySend(Unit)
        advanceTimeBy(interval + 1)
        requests.trySend(Unit)
        settle()
        assertEquals(2, runs.size)
    }
}
