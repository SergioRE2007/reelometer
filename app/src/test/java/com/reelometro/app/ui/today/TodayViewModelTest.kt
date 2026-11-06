package com.reelometro.app.ui.today

import com.reelometro.app.copy.Buttons
import com.reelometro.app.copy.CopyRules
import com.reelometro.app.copy.Direction
import com.reelometro.app.copy.Labels
import com.reelometro.app.copy.TodayCopy
import com.reelometro.app.copy.Variants
import com.reelometro.app.data.settings.AppSettings
import com.reelometro.app.stats.AllTime
import com.reelometro.app.stats.DayStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val accepted = AppSettings(onboardingDone = true, disclosureAccepted = true)
    private val seed = Variants.daySeed(TODAY)

    private fun TestScope.viewModel(
        day: Flow<DayStats> = flowOf(dayStats()),
        all: Flow<AllTime> = flowOf(allTime()),
        settings: Flow<AppSettings> = flowOf(accepted),
        service: () -> Boolean = { true },
    ): TodayViewModel {
        val vm = TodayViewModel(day, all, settings, service, clock = { TODAY }, dispatcher = main.dispatcher)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `empieza cargando con la fecha de hoy`() = runTest(main.dispatcher) {
        val vm = TodayViewModel(flow { }, flowOf(allTime()), flowOf(accepted), { true }, clock = { TODAY }, dispatcher = main.dispatcher)
        val state = vm.uiState.value
        assertTrue(state is TodayUiState.Loading)
        assertEquals("HOY · MARTES 3 NOV", CopyRules.plain(state.header.text))
        assertEquals(TODAY.toEpochDay(), state.header.epochDay)
    }

    @Test
    fun `con vídeos y servicio activo, contenido con marcador y secciones`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.uiState.value as TodayUiState.Content
        val hero = state.top.hero
        assertEquals(212L, hero.count)
        assertEquals(Labels.VIDEOS, hero.label)
        assertEquals("212 vídeos", CopyRules.plain(hero.description))
        assertFalse(hero.alert)
        assertTrue(state.top.canOpenDay)
        assertNull(state.top.notice)
        assertEquals("47 MIN", CopyRules.plain(hero.screenTime!!.value))
        assertEquals("DE PANTALLA · 4 COMPARTIDOS", CopyRules.plain(hero.screenTime!!.rest))
        assertTrue(hero.quote!!.startsWith("«") && hero.quote!!.endsWith("»"))
        val comparison = hero.comparison!!
        assertEquals(Direction.UP, comparison.direction)
        assertTrue(comparison.text, comparison.text.endsWith("QUE TU MARTES MEDIO A ESTA HORA"))

        val sections = state.sections
        assertEquals(listOf("TikTok", "Reels"), sections.platforms!!.rows.map { it.name })
        assertEquals(24, sections.hours.values.size)
        assertEquals(24, sections.hours.tooltips.size)
        assertEquals(21, sections.hours.currentHour)
        assertEquals("HORA PUNTA 21:00 · 142 VÍDEOS", CopyRules.plain(sections.hours.peak!!))
        assertEquals(listOf("Humor", "Sin clasificar"), sections.categories.map { it.label })
        assertEquals(listOf("@cocinaconpaco", "@futbolclips"), sections.creators.map { it.handle })

        val band = state.top.band!!
        assertEquals("DESDE EL 3 MAR", CopyRules.plain(band.kicker))
        assertEquals("12.408 VÍDEOS · 61 H", CopyRules.plain(band.text))
    }

    @Test
    fun `cero vídeos es el estado vacío con el marcador a cero`() = runTest(main.dispatcher) {
        val vm = viewModel(day = flowOf(dayStats(count = 0, totalMs = 0, shares = 0)))
        advanceUntilIdle()
        val state = vm.uiState.value as TodayUiState.Empty
        assertEquals(0L, state.top.hero.count)
        assertTrue(state.top.canOpenDay)
        assertEquals(TodayCopy.quoted(TodayCopy.headline(0, 0, seed)), state.top.hero.quote)
    }

    @Test
    fun `servicio desactivado tras aceptar la divulgación - sin señal y CONECTAR`() = runTest(main.dispatcher) {
        val vm = viewModel(service = { false })
        advanceUntilIdle()
        val state = vm.uiState.value as TodayUiState.Empty
        val hero = state.top.hero
        assertNull("fichas en blanco", hero.count)
        assertEquals(TodayCopy.SERVICE_OFF_LABEL, hero.label)
        assertTrue(hero.alert)
        assertNull(hero.quote)
        assertFalse(state.top.canOpenDay)
        val notice = state.top.notice!!
        assertEquals(TodayCopy.serviceOff(seed), notice.text)
        assertEquals(Buttons.CONNECT, notice.action)
        assertEquals(NoticeTarget.CONNECT_SERVICE, notice.target)
        assertNotNull("la banda sigue: el historial es real", state.top.band)
    }

    @Test
    fun `sin la divulgación aceptada se ofrece ver el permiso y nunca los ajustes del sistema`() = runTest(main.dispatcher) {
        val vm = viewModel(settings = flowOf(AppSettings(onboardingDone = true, disclosureAccepted = false)), service = { false })
        advanceUntilIdle()
        val notice = (vm.uiState.value as TodayUiState.Empty).top.notice!!
        assertEquals(TodayCopy.serviceNeverEnabled(seed), notice.text)
        assertEquals(Buttons.SEE_PERMISSION, notice.action)
        assertEquals(NoticeTarget.CONNECT_SERVICE, notice.target)
    }

    @Test
    fun `el estado del servicio se vuelve a mirar al reanudar`() = runTest(main.dispatcher) {
        var enabled = false
        val vm = viewModel(service = { enabled })
        advanceUntilIdle()
        assertNull((vm.uiState.value as TodayUiState.Empty).top.hero.count)

        enabled = true
        vm.refreshService()
        advanceUntilIdle()
        assertEquals(212L, (vm.uiState.value as TodayUiState.Content).top.hero.count)
    }

    @Test
    fun `los datos nuevos llegan solos`() = runTest(main.dispatcher) {
        val day = MutableStateFlow(dayStats(count = 3, totalMs = 2 * MIN))
        val vm = viewModel(day = day)
        advanceUntilIdle()
        assertEquals(3L, (vm.uiState.value as TodayUiState.Content).top.hero.count)
        day.value = dayStats(count = 4, totalMs = 3 * MIN)
        advanceUntilIdle()
        assertEquals(4L, (vm.uiState.value as TodayUiState.Content).top.hero.count)
    }

    @Test
    fun `todas las apps apagadas - aviso que lleva a Ajustes`() = runTest(main.dispatcher) {
        val vm = viewModel(settings = flowOf(accepted.copy(enabledPlatforms = emptySet())))
        advanceUntilIdle()
        val notice = (vm.uiState.value as TodayUiState.Content).top.notice!!
        assertEquals(TodayCopy.noPlatforms(seed), notice.text)
        assertEquals(NoticeTarget.SETTINGS, notice.target)
    }

    @Test
    fun `con objetivo, columna de objetivo`() = runTest(main.dispatcher) {
        val vm = viewModel(day = flowOf(dayStats(goal = 150)))
        advanceUntilIdle()
        val goal = (vm.uiState.value as TodayUiState.Content).top.hero.goal!!
        assertEquals("OBJETIVO 150", goal.label)
        assertEquals("+62 PASADO", CopyRules.plain(goal.status))
        assertTrue(goal.exceeded)
    }

    @Test
    fun `sin historial no hay banda ni media con la que comparar`() = runTest(main.dispatcher) {
        val vm = viewModel(day = flowOf(dayStats(average = null)), all = flowOf(allTime(firstDay = TODAY)))
        advanceUntilIdle()
        val state = vm.uiState.value as TodayUiState.Content
        assertNull(state.top.band)
        assertEquals(Direction.NONE, state.top.hero.comparison!!.direction)
        assertEquals(TodayCopy.quoted(TodayCopy.headline(212, 47 * MIN, seed, firstDay = true)), state.top.hero.quote)
    }

    @Test
    fun `un fallo de datos es un error y reintentar vuelve a cargar`() = runTest(main.dispatcher) {
        var calls = 0
        val day = flow {
            calls++
            if (calls == 1) throw IllegalStateException("base de datos rota") else emit(dayStats())
        }
        val vm = viewModel(day = day)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is TodayUiState.Error)

        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is TodayUiState.Content)
    }
}
