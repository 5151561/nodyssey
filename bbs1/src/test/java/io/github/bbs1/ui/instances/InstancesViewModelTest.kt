package io.github.bbs1.ui.instances

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.bbs1.data.InstanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class InstancesViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.newViewModel(): Pair<InstancesViewModel, CoroutineScope> {
        // Unconfined everywhere so viewModelScope launches and DataStore reads run to completion as
        // the test body advances, without a Robolectric main looper.
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val scope = CoroutineScope(dispatcher + Job())
        val store =
            PreferenceDataStoreFactory.create(scope = scope) {
                File(tmp.root, "instances.preferences_pb")
            }
        return InstancesViewModel(InstanceRepository(store) { "id" }) to scope
    }

    @Test
    fun `uiState starts loading, settles, and reflects an add`() = runTest {
        val (viewModel, scope) = newViewModel()
        assertTrue(viewModel.uiState.value.loading)

        // stateIn is WhileSubscribed: nothing flows until someone collects, as in the UI.
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.loading)
        assertEquals(emptyList<Any>(), viewModel.uiState.value.instances)

        viewModel.add("https://bbs1.org", name = null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("bbs1.org", state.current?.name)
        assertEquals("https://bbs1.org", state.current?.baseUrl)
        scope.cancel()
    }
}
