package com.reelometro.app.ui.today

import com.reelometro.app.copy.Buttons
import com.reelometro.app.copy.CopyRules
import com.reelometro.app.copy.Direction
import com.reelometro.app.copy.Labels
import com.reelometro.app.copy.TodayCopy
import com.reelometro.app.copy.Variants
import com.reelometro.app.data.settings.AppSettings
import com.reelometro.app.data.settings.DiscoveryFeature
import com.reelometro.app.data.settings.DiscoveryState
import com.reelometro.app.stats.AllTime
import com.reelometro.app.stats.DayStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    fun `apagado por el sistema - lo dice, con el motivo que apunta Android, y lleva a ARREGLAR`() = runTest(main.dispatcher) {
        val vm = TodayViewModel(
            flowOf(dayStats()), flowOf(allTime()), flowOf(accepted), { false },
            // Con el servicio apagado, «sin funcionar» no pinta nada aunque algo lo diga.
            serviceStalled = { true },
            stallReason = { com.reelometro.app.detection.ExitCategory.USER_STOPPED },
            serviceLost = { true },
            clock = { TODAY },
            dispatcher = main.dispatcher,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value as TodayUiState.Empty
        assertNull("no hay cifra que creer", state.top.hero.count)
        val notice = state.top.notice!!
        assertEquals(TodayCopy.serviceLost(seed, com.reelometro.app.detection.ExitCategory.USER_STOPPED), notice.text)
        assertTrue(notice.text, "detenida o datos borrados" in CopyRules.plain(notice.text))
        assertEquals(TodayCopy.SERVICE_STALLED_ACTION, notice.action)
        assertEquals(NoticeTarget.CONNECT_SERVICE, notice.target)
    }

    @Test
    fun `apagado por el sistema sin motivo guardado - el aviso va sin motivo`() = runTest(main.dispatcher) {
        val vm = TodayViewModel(
            flowOf(dayStats()), flowOf(allTime()), flowOf(accepted), { false },
            serviceLost = { true },
            clock = { TODAY },
            dispatcher = main.dispatcher,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val notice = (vm.uiState.value as TodayUiState.Empty).top.notice!!
        assertEquals(TodayCopy.serviceLost(seed), notice.text)
    }

    @Test
    fun `apagado por el usuario - el aviso de siempre, sin mirar el motivo del cierre`() = runTest(main.dispatcher) {
        var reasons = 0
        val vm = TodayViewModel(
            flowOf(dayStats()), flowOf(allTime()), flowOf(accepted), { false },
            stallReason = { reasons++; com.reelometro.app.detection.ExitCategory.RECENTS_CLEAN },
            serviceLost = { false },
            clock = { TODAY },
            dispatcher = main.dispatcher,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val notice = (vm.uiState.value as TodayUiState.Empty).top.notice!!
        assertEquals(TodayCopy.serviceOff(seed), notice.text)
        assertEquals(Buttons.CONNECT, notice.action)
        assertEquals("el cierre solo se lee si explica algo", 0, reasons)
    }

    @Test
    fun `apagado por el sistema sin divulgacion aceptada - se ofrece ver el permiso, como siempre`() {
        val state = TodayMapper.map(
            dayStats(), allTime(), AppSettings(onboardingDone = true, disclosureAccepted = false),
            serviceOn = false, serviceLost = true,
        ) as TodayUiState.Empty
        assertEquals(TodayCopy.serviceNeverEnabled(seed), state.top.notice!!.text)
    }

    @Test
    fun `al volver a activarlo deja de estar perdido`() = runTest(main.dispatcher) {
        var on = false
        val vm = TodayViewModel(
            flowOf(dayStats()), flowOf(allTime()), flowOf(accepted), { on },
            serviceLost = { true },
            clock = { TODAY },
            dispatcher = main.dispatcher,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(vm.uiState.value is TodayUiState.Empty)
        on = true
        vm.refreshService()
        advanceUntilIdle()
        assertFalse("con el servicio en marcha vuelve el contador", vm.uiState.value is TodayUiState.Empty)
    }

    @Test
    fun `sin funcionar - el motivo del cierre llega despues de leerlo fuera del hilo principal`() = runTest(main.dispatcher) {
        var calls = 0
        val vm = TodayViewModel(
            flowOf(dayStats()), flowOf(allTime()), flowOf(accepted), { true },
            serviceStalled = { true },
            stallReason = { calls++; com.reelometro.app.detection.ExitCategory.APP_CRASH },
            clock = { TODAY },
            dispatcher = main.dispatcher,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(calls >= 1)
        val notice = (vm.uiState.value as TodayUiState.Empty).top.notice!!
        assertEquals(TodayCopy.serviceStalled(seed, com.reelometro.app.detection.ExitCategory.APP_CRASH), notice.text)
    }

    @Test
    fun `activado pero sin funcionar - sin señal y ARREGLAR, y vuelve solo al conectarse`() = runTest(main.dispatcher) {
        var stalled = true
        val changes = MutableStateFlow(0)
        val vm = TodayViewModel(
            flowOf(dayStats()), flowOf(allTime()), flowOf(accepted), { true },
            serviceStalled = { stalled },
            serviceChanges = changes.map { },
            clock = { TODAY },
            dispatcher = main.dispatcher,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value as TodayUiState.Empty
        assertNull("no hay cifra que creer", state.top.hero.count)
        assertTrue(state.top.hero.alert)
        val notice = state.top.notice!!
        assertEquals(TodayCopy.serviceStalled(seed), notice.text)
        assertEquals(TodayCopy.SERVICE_STALLED_ACTION, notice.action)
        assertEquals(NoticeTarget.CONNECT_SERVICE, notice.target)

        // El sistema conecta el servicio: Hoy vuelve sin esperar a ON_RESUME.
        stalled = false
        changes.value = 1
        advanceUntilIdle()
        assertTrue(vm.uiState.value is TodayUiState.Content)
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

    // ------------------------------------------------ descubrimiento progresivo

    /** Escritor falso: aplica el cambio al flujo de ajustes y cuenta las llamadas. */
    private class FakeDiscovery(val settings: MutableStateFlow<AppSettings>) : DiscoveryWriter {
        var calls = 0
        override suspend fun update(change: (DiscoveryState) -> DiscoveryState) {
            calls++
            val current = settings.value
            settings.value = current.copy(discovery = change(current.discovery))
        }
    }

    private fun TestScope.discoveryViewModel(
        settings: MutableStateFlow<AppSettings>,
        writer: DiscoveryWriter,
        all: Flow<AllTime> = flowOf(allTime(firstDay = TODAY.minusDays(1))),
        day: Flow<DayStats> = flowOf(dayStats()),
        service: () -> Boolean = { true },
        stalled: () -> Boolean = { false },
    ): TodayViewModel {
        val vm = TodayViewModel(
            day, all, settings, service,
            serviceStalled = stalled,
            discovery = writer,
            clock = { TODAY },
            dispatcher = main.dispatcher,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private fun TodayUiState.top(): TodayTop = when (this) {
        is TodayUiState.Content -> top
        is TodayUiState.Empty -> top
        else -> throw AssertionError("sin marcador: $this")
    }

    private val newUser = accepted.copy(discovery = DiscoveryState.FRESH)

    @Test
    fun `usuario nuevo con dos días - aparece Estadísticas y se presenta`() = runTest(main.dispatcher) {
        val settings = MutableStateFlow(newUser)
        val writer = FakeDiscovery(settings)
        val vm = discoveryViewModel(settings, writer)
        advanceUntilIdle()
        assertEquals(2, settings.value.discovery.tabs)
        assertEquals(TODAY.toEpochDay(), settings.value.discovery.tabsDay)
        val top = vm.uiState.value.top()
        assertEquals(DiscoveryFeature.STATS, top.news!!.feature)
        assertEquals(TodayCopy.newsAction(DiscoveryFeature.STATS), top.news!!.action)
        assertEquals(TodayCopy.revealHint(listOf(DiscoveryFeature.WRAPPED, DiscoveryFeature.PASSPORT)), top.revealHint!!.text)
        assertEquals(1, writer.calls)
    }

    @Test
    fun `el aviso sin servicio o sin señal manda sobre la novedad`() = runTest(main.dispatcher) {
        val pending = accepted.copy(discovery = DiscoveryState.FRESH.copy(tabs = 2, tabsDay = TODAY.toEpochDay()))
        for ((service, stalled) in listOf(false to false, true to true)) {
            val settings = MutableStateFlow(pending)
            val vm = discoveryViewModel(settings, FakeDiscovery(settings), service = { service }, stalled = { stalled })
            advanceUntilIdle()
            val top = vm.uiState.value.top()
            assertNotNull(top.notice)
            assertNull(top.news)
            // La pista va al final de Hoy: no tapa el aviso y deja llegar a las pestañas sin servicio.
            assertNotNull(top.revealHint)
            assertFalse(top.canOpenDay)
        }
    }

    @Test
    fun `sin apps activas hay aviso y no novedad`() = runTest(main.dispatcher) {
        val settings = MutableStateFlow(
            accepted.copy(enabledPlatforms = emptySet(), discovery = DiscoveryState.FRESH.copy(tabs = 2, tabsDay = TODAY.toEpochDay())),
        )
        val vm = discoveryViewModel(settings, FakeDiscovery(settings))
        advanceUntilIdle()
        val top = vm.uiState.value.top()
        assertEquals(NoticeTarget.SETTINGS, top.notice!!.target)
        assertNull(top.news)
    }

    @Test
    fun `ENTENDIDO pasa a la siguiente novedad`() = runTest(main.dispatcher) {
        val settings = MutableStateFlow(accepted.copy(discovery = DiscoveryState.FRESH.copy(tabs = 3, tabsDay = TODAY.toEpochDay())))
        val vm = discoveryViewModel(settings, FakeDiscovery(settings))
        advanceUntilIdle()
        assertEquals(DiscoveryFeature.STATS, vm.uiState.value.top().news!!.feature)
        vm.dismissNews(DiscoveryFeature.STATS)
        advanceUntilIdle()
        assertEquals(2, settings.value.discovery.announced)
        assertEquals(DiscoveryFeature.WRAPPED, vm.uiState.value.top().news!!.feature)
        vm.openNews(DiscoveryFeature.WRAPPED)
        advanceUntilIdle()
        assertNull(vm.uiState.value.top().news)
    }

    @Test
    fun `una doble pulsación sobre la misma novedad no se come la siguiente`() = runTest(main.dispatcher) {
        val settings = MutableStateFlow(accepted.copy(discovery = DiscoveryState.FRESH.copy(tabs = 3, tabsDay = TODAY.toEpochDay())))
        val vm = discoveryViewModel(settings, FakeDiscovery(settings))
        advanceUntilIdle()
        vm.dismissNews(DiscoveryFeature.STATS)
        vm.dismissNews(DiscoveryFeature.STATS)
        advanceUntilIdle()
        assertEquals(2, settings.value.discovery.announced)
        assertEquals(DiscoveryFeature.WRAPPED, vm.uiState.value.top().news!!.feature)
    }

    @Test
    fun `ENSEÑARLO TODO YA quita la pista y la novedad`() = runTest(main.dispatcher) {
        val settings = MutableStateFlow(newUser)
        val vm = discoveryViewModel(settings, FakeDiscovery(settings))
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.top().revealHint)
        vm.showAll()
        advanceUntilIdle()
        val top = vm.uiState.value.top()
        assertNull(top.revealHint)
        assertNull(top.news)
        assertEquals(4, settings.value.discovery.tabs)
    }

    @Test
    fun `un usuario de antes lo ve todo y no se escribe nada`() = runTest(main.dispatcher) {
        val settings = MutableStateFlow(accepted)
        val writer = FakeDiscovery(settings)
        val vm = discoveryViewModel(settings, writer, all = flowOf(allTime()))
        advanceUntilIdle()
        val top = vm.uiState.value.top()
        assertNull(top.news)
        assertNull(top.revealHint)
        assertEquals(0, writer.calls)
    }

    @Test
    fun `las emisiones repetidas no repiten la escritura`() = runTest(main.dispatcher) {
        val settings = MutableStateFlow(newUser)
        var calls = 0
        // No guarda nada: el estado sigue pidiendo subir en cada emisión.
        val writer = DiscoveryWriter { calls++ }
        val day = MutableStateFlow(dayStats())
        discoveryViewModel(settings, writer, day = day)
        advanceUntilIdle()
        day.value = dayStats(count = 213)
        advanceUntilIdle()
        day.value = dayStats(count = 214)
        advanceUntilIdle()
        assertEquals(1, calls)
    }
}
