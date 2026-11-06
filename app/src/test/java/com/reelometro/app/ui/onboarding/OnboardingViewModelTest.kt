package com.reelometro.app.ui.onboarding

import com.reelometro.app.copy.OnboardingCopy
import com.reelometro.app.data.settings.AppSettings
import com.reelometro.app.data.settings.SettingsRepository
import com.reelometro.app.ui.settings.fakeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeNotifications(var allowed: Boolean = false) : NotificationAccess {
        override val needsRuntimePermission = true

        override fun areAllowed() = allowed
    }

    private fun TestScope.viewModel(
        settings: SettingsRepository = fakeSettings(),
        notifications: FakeNotifications = FakeNotifications(),
    ): OnboardingViewModel {
        val vm = OnboardingViewModel(settings, notifications)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        return vm
    }

    private fun OnboardingViewModel.current(): OnboardingState = (state.value as OnboardingUiState.Ready).onboarding

    @Test
    fun `los pasos avanzan y retroceden sin salirse y saltar lleva al permiso`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.back()
        advanceUntilIdle()
        assertEquals(0, vm.current().step)

        vm.skipToPermission()
        advanceUntilIdle()
        assertEquals(OnboardingViewModel.PERMISSION_STEP, vm.current().step)
        vm.skipToPermission()
        advanceUntilIdle()
        assertEquals("saltar nunca pasa del permiso", OnboardingViewModel.PERMISSION_STEP, vm.current().step)

        repeat(5) { vm.next() }
        advanceUntilIdle()
        assertEquals(OnboardingCopy.STEPS - 1, vm.current().step)
        assertTrue(vm.current().isLast)

        vm.back()
        advanceUntilIdle()
        assertEquals(OnboardingCopy.STEPS - 2, vm.current().step)
        assertEquals(-1, vm.current().direction)
    }

    @Test
    fun `el objetivo guardado se reconoce como sugerido u otro`() {
        assertEquals(GoalChoice.None, OnboardingViewModel.choiceFor(null))
        assertEquals(GoalChoice.Preset(150), OnboardingViewModel.choiceFor(150))
        assertEquals(GoalChoice.Custom(120), OnboardingViewModel.choiceFor(120))
    }

    @Test
    fun `OTRO arranca en el objetivo que hubiera y se queda en el rango`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.chooseGoal(GoalChoice.Preset(60))
        vm.chooseCustomGoal()
        advanceUntilIdle()
        assertEquals(GoalChoice.Custom(60), vm.current().goal)

        vm.setCustomGoal(9_999)
        advanceUntilIdle()
        assertEquals(GoalChoice.Custom(AppSettings.MAX_GOAL), vm.current().goal)

        // Aunque el valor coincida con una sugerencia, sigue en «OTRO» (no se esconde el selector).
        vm.setCustomGoal(150)
        advanceUntilIdle()
        assertEquals(GoalChoice.Custom(150), vm.current().goal)
    }

    @Test
    fun `decir que no a los avisos los apaga`() = runTest(dispatcher) {
        val vm = viewModel()
        assertTrue(vm.current().notifyDaily)
        vm.onNotificationPermission(false)
        advanceUntilIdle()
        assertFalse(vm.current().notifyDaily)
        assertFalse(vm.current().notifyMonthly)
        assertTrue(vm.current().notificationsDenied)
    }

    @Test
    fun `EMPEZAR guarda avisos y objetivo antes de seguir`() = runTest(dispatcher) {
        val settings = fakeSettings()
        val vm = viewModel(settings)
        vm.setNotifyMonthly(false)
        vm.chooseGoal(GoalChoice.Preset(100))
        var saved = false
        vm.finish { saved = true }
        advanceUntilIdle()
        assertTrue(saved)
        val s = settings.current()
        assertTrue(s.notifyDaily)
        assertFalse(s.notifyMonthly)
        assertEquals(100, s.dailyGoal)

        vm.chooseGoal(GoalChoice.None)
        vm.finish {}
        advanceUntilIdle()
        assertNull(settings.current().dailyGoal)
    }
}
