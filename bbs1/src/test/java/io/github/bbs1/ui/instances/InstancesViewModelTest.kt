package io.github.bbs1.ui.instances

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.bbs1.data.InstanceRepository
import io.github.bbs1.net.ApiMeta
import io.github.bbs1.net.ApiSite
import io.github.bbs1.net.Bbs1ApiException
import io.github.bbs1.net.FakeBbs1Api
import io.github.bbs1.ui.common.ApiErrorUi
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

    private fun TestScope.newViewModel(
        api: FakeBbs1Api = FakeBbs1Api(),
    ): Pair<InstancesViewModel, CoroutineScope> {
        // Unconfined everywhere so viewModelScope launches and DataStore reads run to completion as
        // the test body advances, without a Robolectric main looper.
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val scope = CoroutineScope(dispatcher + Job())
        val store =
            PreferenceDataStoreFactory.create(scope = scope) {
                File(tmp.root, "instances.preferences_pb")
            }
        return InstancesViewModel(InstanceRepository(store) { "id" }, api) to scope
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

        viewModel.add("https://bbs1.org", name = "自己起的名")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("自己起的名", state.current?.name)
        assertEquals("https://bbs1.org", state.current?.baseUrl)
        assertTrue(viewModel.addState.value.succeeded)
        scope.cancel()
    }

    @Test
    fun `a blank name falls back to the probed site name`() = runTest {
        val api = FakeBbs1Api().apply { metaResult = { ApiMeta(ApiSite(name = "海角论坛")) } }
        val (viewModel, scope) = newViewModel(api)
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.add("https://bbs1.org", name = null)
        advanceUntilIdle()

        assertEquals("海角论坛", viewModel.uiState.value.current?.name)
        scope.cancel()
    }

    @Test
    fun `a failed probe reports the error and saves nothing`() = runTest {
        val api = FakeBbs1Api().apply {
            metaResult = { throw Bbs1ApiException.Server("站点已关闭") }
        }
        val (viewModel, scope) = newViewModel(api)
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.add("https://bbs1.org", name = null)
        advanceUntilIdle()

        assertEquals(ApiErrorUi.Server("站点已关闭"), viewModel.addState.value.error)
        assertFalse(viewModel.addState.value.succeeded)
        assertEquals(emptyList<Any>(), viewModel.uiState.value.instances)

        viewModel.consumeAdd()
        assertEquals(AddInstanceUiState(), viewModel.addState.value)
        scope.cancel()
    }
}
