package io.github.nodyssey.ui.account

import io.github.nodyssey.data.account.BlockedUser
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.testSettingsRepository
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BlockListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        repository: FakeAccountSettingsRepository,
        settings: SettingsRepository = testSettingsRepository(backgroundScope),
    ) = BlockListViewModel(account = repository, settings = settings)

    /**
     * `uiState` combines only while collected (`WhileSubscribed`), so every test keeps a background
     * collector alive — reading `.value` without one would only ever see the initial state.
     */
    private fun TestScope.collectState(vm: BlockListViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
    }

    @Test
    fun `unblocking removes the row only after the dialog is confirmed`() =
        runTest(dispatcher) {
            val target = BlockedUser(uid = 7, name = "vps_matthew")
            val repository = FakeAccountSettingsRepository(blocked = listOf(target))
            val vm = viewModel(repository)
            collectState(vm)
            advanceUntilIdle()

            vm.requestUnblock(target)
            advanceUntilIdle()
            assertEquals(1, vm.uiState.value.blocked.size)
            assertTrue(repository.unblocked.isEmpty())

            vm.confirmUnblock()
            advanceUntilIdle()

            assertEquals(listOf(7L), repository.unblocked)
            assertTrue(vm.uiState.value.blocked.isEmpty())
        }

    @Test
    fun `dismissing the dialog unblocks nobody`() =
        runTest(dispatcher) {
            val target = BlockedUser(uid = 7, name = "vps_matthew")
            val repository = FakeAccountSettingsRepository(blocked = listOf(target))
            val vm = viewModel(repository)
            collectState(vm)
            advanceUntilIdle()

            vm.requestUnblock(target)
            vm.dismissUnblock()
            vm.confirmUnblock()
            advanceUntilIdle()

            assertTrue(repository.unblocked.isEmpty())
            assertEquals(1, vm.uiState.value.blocked.size)
        }

    /** The reveal switch is session state: it flows through settings, not through this ViewModel. */
    @Test
    fun `the reveal switch reflects the shared session flag`() =
        runTest(dispatcher) {
            val settings = testSettingsRepository(backgroundScope)
            val vm = viewModel(FakeAccountSettingsRepository(), settings)
            collectState(vm)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.showBlockedContent)

            vm.setShowBlockedContent(true)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.showBlockedContent)
            assertTrue(settings.showBlockedContent.first())
        }

    /**
     * The list, not the typed name, is what the row is built from: the site owns the uid and the
     * canonical spelling, so an accepted block is followed by a re-read rather than a guessed row.
     */
    @Test
    fun `blocking a name sends it and re-reads the list`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository()
            val vm = viewModel(repository)
            collectState(vm)
            advanceUntilIdle()

            vm.onNameInputChange("  vps_matthew  ")
            vm.block()
            advanceUntilIdle()

            assertEquals(listOf("vps_matthew"), repository.blockedNames)
            assertEquals(listOf("vps_matthew"), vm.uiState.value.blocked.map { it.name })
            assertEquals("", vm.uiState.value.nameInput)
            assertFalse(vm.uiState.value.isBlocking)
            assertTrue(vm.uiState.value.message is AccountMessage.Info)
        }

    @Test
    fun `a blank name is not sent`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository()
            val vm = viewModel(repository)
            collectState(vm)
            advanceUntilIdle()

            vm.onNameInputChange("   ")
            vm.block()
            advanceUntilIdle()

            assertTrue(repository.blockedNames.isEmpty())
        }

    /** A refusal is the site's sentence, and the reader keeps what they typed to fix it. */
    @Test
    fun `a refused name keeps the field and shows the site's reason`() =
        runTest(dispatcher) {
            val repository =
                FakeAccountSettingsRepository(
                    failWith = { SiteException(SiteError.Unknown, detail = "用户不存在") },
                )
            val vm = viewModel(repository)
            collectState(vm)
            advanceUntilIdle()

            vm.onNameInputChange("nobody")
            vm.block()
            advanceUntilIdle()

            assertEquals("nobody", vm.uiState.value.nameInput)
            assertFalse(vm.uiState.value.isBlocking)
            assertEquals(AccountMessage.Detail("用户不存在"), vm.uiState.value.message)
        }

    @Test
    fun `a failed list load reports the error rather than an empty list`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeAccountSettingsRepository.failing())
            collectState(vm)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.message is AccountMessage.Failure)
        }
}
