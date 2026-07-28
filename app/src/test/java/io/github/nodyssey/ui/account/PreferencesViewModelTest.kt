package io.github.nodyssey.ui.account

import io.github.nodyssey.data.account.RemoteAccountPreferences
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.settings.ThemeMode
import io.github.nodyssey.data.testSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The storage contract of 偏好与首页版块: Local rows only ever touch DataStore, Remote rows write the
 * DataStore mirror *and* the account, and while the account endpoints are stubbed the mirror still
 * obeys the user — that ordering is the difference between "works offline" and "lies offline".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        repository: FakeAccountSettingsRepository,
        settings: SettingsRepository = testSettingsRepository(backgroundScope),
    ) = PreferencesViewModel(settings = settings, account = repository)

    private fun TestScope.collectState(vm: PreferencesViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
    }

    /** DataStore writes land on a real IO dispatcher the scheduler does not own; wait for the value. */
    private suspend fun SettingsRepository.awaitHiddenBoards(expected: Set<String>) =
        withTimeout(STORE_TIMEOUT_MILLIS) { settings.first { it.hiddenHomeBoards == expected } }

    @Test
    fun `hiding a board writes the mirror and the account`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository()
            val settings = testSettingsRepository(backgroundScope)
            val vm = viewModel(repository, settings)
            collectState(vm)
            advanceUntilIdle()

            vm.setBoardHidden("trade", true)
            advanceUntilIdle()

            settings.awaitHiddenBoards(setOf("trade"))
            // The account write happens after the store write lands, on the scheduler's side of the
            // seam — run it before looking.
            advanceUntilIdle()
            assertEquals(listOf("trade" to true), repository.boardHiddenWrites)
        }

    /** The app obeys the user even when the account write fails — and says the account disagrees. */
    @Test
    fun `a rejected account write still lets the mirror obey the toggle`() =
        runTest(dispatcher) {
            val settings = testSettingsRepository(backgroundScope)
            val vm = viewModel(FakeAccountSettingsRepository.failing(), settings)
            collectState(vm)
            advanceUntilIdle()

            vm.setBoardHidden("life", true)
            advanceUntilIdle()

            settings.awaitHiddenBoards(setOf("life"))
            assertTrue(vm.uiState.value.message is AccountMessage.Failure)
        }

    @Test
    fun `an unknown slug cannot be hidden`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository()
            val settings = testSettingsRepository(backgroundScope)
            val vm = viewModel(repository, settings)
            collectState(vm)
            advanceUntilIdle()

            vm.setBoardHidden("tech", true)
            advanceUntilIdle()

            assertEquals(emptySet<String>(), settings.settings.first().hiddenHomeBoards)
            assertTrue(repository.boardHiddenWrites.isEmpty())
        }

    /** The account is the authority for Remote rows: a fetched value overwrites the stale mirror. */
    @Test
    fun `fetched remote preferences sync down into the mirror`() =
        runTest(dispatcher) {
            val settings = testSettingsRepository(backgroundScope)
            settings.setHolidayTheme(false)
            settings.setHomeBoardHidden("trade", true)
            val repository =
                FakeAccountSettingsRepository(
                    remotePreferences =
                    RemoteAccountPreferences(holidayTheme = true, hiddenBoards = setOf("life")),
                )
            val vm = viewModel(repository, settings)
            collectState(vm)
            advanceUntilIdle()

            settings.awaitHiddenBoards(setOf("life"))
            withTimeout(STORE_TIMEOUT_MILLIS) { settings.settings.first { it.holidayTheme } }
        }

    @Test
    fun `the night rows map onto the theme mode`() =
        runTest(dispatcher) {
            val settings = testSettingsRepository(backgroundScope)
            val vm = viewModel(FakeAccountSettingsRepository(), settings)
            collectState(vm)
            advanceUntilIdle()

            vm.setNightBasisTimed(true)
            advanceUntilIdle()
            withTimeout(STORE_TIMEOUT_MILLIS) { settings.settings.first { it.themeMode == ThemeMode.TIMED } }

            vm.setAutoNight(false)
            advanceUntilIdle()
            withTimeout(STORE_TIMEOUT_MILLIS) { settings.settings.first { it.themeMode == ThemeMode.LIGHT } }

            vm.setAutoNight(true)
            advanceUntilIdle()
            // Back on lands on the default basis, 跟随系统 — not on the last one used.
            withTimeout(STORE_TIMEOUT_MILLIS) { settings.settings.first { it.themeMode == ThemeMode.SYSTEM } }
        }

    private companion object {
        const val STORE_TIMEOUT_MILLIS = 5_000L
    }
}
