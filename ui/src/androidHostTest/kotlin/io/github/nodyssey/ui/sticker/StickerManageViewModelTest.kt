package io.github.nodyssey.ui.sticker

import io.github.nodyssey.data.inMemoryDatabase
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.sticker.MINE_GROUP_KEY
import io.github.nodyssey.data.sticker.MySticker
import io.github.nodyssey.data.sticker.RepoFolder
import io.github.nodyssey.data.sticker.RepoListing
import io.github.nodyssey.data.sticker.StickerCdn
import io.github.nodyssey.data.sticker.StickerLibrary
import io.github.nodyssey.data.testSettingsRepository
import io.github.nodyssey.ui.ViewModels
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
class StickerManageViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModels()
    private val http = FakeStickerHttp()
    private lateinit var database: NodeSeekDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = inMemoryDatabase(dispatcher)
    }

    @After
    fun tearDown() {
        viewModels.clear(dispatcher.scheduler)
        Dispatchers.resetMain()
        database.close()
    }

    private fun TestScope.library(settings: SettingsRepository = testSettingsRepository(backgroundScope)): StickerLibrary =
        testStickerLibrary(database, settings, http, backgroundScope)

    private suspend fun StickerLibrary.subscribeOne() {
        subscribe(
            RepoListing("o", "r", "main", OLD_SHA, listOf(RepoFolder("a", listOf("a/1.png"), 1)), truncated = false),
            paths = setOf("a"),
        )
    }

    @Test
    fun `a forced check that finds an update does not say up to date`() =
        runTest(dispatcher) {
            val library = library()
            library.subscribeOne()
            http.head = NEW_SHA
            http.trees[NEW_SHA] = listOf("a/1.png", "a/2.png")
            val viewModel = viewModels.track(StickerManageViewModel(library, emptyList()))

            viewModel.checkUpdates(force = true)
            advanceUntilIdle()

            assertFalse(viewModel.checkedUpToDate.value)
        }

    @Test
    fun `a forced check that finds nothing says up to date`() =
        runTest(dispatcher) {
            val library = library()
            library.subscribeOne()
            val viewModel = viewModels.track(StickerManageViewModel(library, emptyList()))

            viewModel.checkUpdates(force = true)
            advanceUntilIdle()

            assertTrue(viewModel.checkedUpToDate.value)
        }

    @Test
    fun `the custom CDN is measured when the page opens`() =
        runTest(dispatcher) {
            val settings = testSettingsRepository(backgroundScope)
            settings.stickerSettings.setCustomBase("https://cdn.example/gh/")
            val library = library(settings)
            library.subscribeOne()
            // Nothing collects the ViewModel's flows yet: measuring is the first thing the page does.
            val viewModel = viewModels.track(StickerManageViewModel(library, emptyList()))

            viewModel.measureCdns()
            advanceUntilIdle()

            assertTrue(StickerCdn.CUSTOM in viewModel.latency.value)
            assertTrue(http.heads().any { it.startsWith("https://cdn.example/gh/") })
        }

    @Test
    fun `a dropped cell never shows the order from before the drag`() =
        runTest(dispatcher) {
            val library = library()
            library.add(listOf("a", "b", "c").map { MySticker("https://h/$it.png", it) })
            val viewModel = viewModels.track(StickerGroupViewModel(library, MINE_GROUP_KEY, emptyList()))
            val seen = mutableListOf<List<String>>()
            backgroundScope.launch { viewModel.page.collect { page -> if (page != null) seen += page.cells.map { it.name } } }
            // The page also reads the group layout, which DataStore delivers from its own IO rather
            // than on the test dispatcher: wait for it instead of advancing past it.
            viewModel.page.first { it != null }
            advanceUntilIdle()
            assertEquals(listOf("a", "b", "c"), seen.last())

            viewModel.dragMove("https://h/c.png", "https://h/a.png")
            advanceUntilIdle()
            val afterDrag = seen.size
            viewModel.dragEnd()
            advanceUntilIdle()
            // Observed while writing this: an in-memory Room under the test dispatcher passed the
            // reorder on to its observers only once something queried again. This read is that query.
            library.mine.first()
            advanceUntilIdle()

            assertEquals(listOf("c", "a", "b"), seen.last())
            assertTrue(seen.drop(afterDrag).none { it == listOf("a", "b", "c") })
        }
}
