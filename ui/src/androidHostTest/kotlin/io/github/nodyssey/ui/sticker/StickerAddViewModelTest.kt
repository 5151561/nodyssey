package io.github.nodyssey.ui.sticker

import io.github.nodyssey.data.inMemoryDatabase
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.sticker.RepoFolder
import io.github.nodyssey.data.sticker.RepoListing
import io.github.nodyssey.data.sticker.StickerLibrary
import io.github.nodyssey.data.testSettingsRepository
import io.github.nodyssey.ui.ViewModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StickerAddViewModelTest {
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

    private fun TestScope.library(): StickerLibrary = testStickerLibrary(database, testSettingsRepository(backgroundScope), http, backgroundScope)

    @Test
    fun `changing folders ticks the subscribed ones and stays on the subscribed branch`() =
        runTest(dispatcher) {
            val library = library()
            library.subscribe(
                RepoListing(
                    owner = "o",
                    repo = "r",
                    ref = "dev",
                    sha = OLD_SHA,
                    folders = listOf("a", "b", "c").map { RepoFolder(it, listOf("$it/1.png"), 1) },
                    truncated = false,
                ),
                paths = setOf("a", "b"),
            )
            http.trees[OLD_SHA] = listOf("a/1.png", "b/1.png", "c/1.png")
            // Built and driven the way 表情管理 does it: nothing is collecting its flows yet.
            val viewModel = viewModels.track(StickerAddViewModel(library))

            viewModel.reset()
            viewModel.editSource("o/r")
            advanceUntilIdle()

            assertEquals(setOf("a", "b"), viewModel.folderSelection.value)
            // The repository is asked for its canonical name, but the head is the subscribed branch's,
            // not the default branch's (the fake answers `main` there).
            assertEquals(
                listOf("/repos/o/r", "/repos/o/r/commits/dev", "/repos/o/r/git/trees/$OLD_SHA"),
                http.githubPaths(),
            )
        }

    @Test
    fun `a link that answered is added though its preview never drew`() =
        runTest(dispatcher) {
            val viewModel = viewModels.track(StickerAddViewModel(library()))

            viewModel.setLinkText("https://h/a.png\nhttps://h/b.png")
            advanceUntilIdle()
            // Only the first tile was on screen to draw.
            viewModel.onPreviewLoaded("https://h/a.png", loads = true)

            assertEquals(listOf("https://h/a.png", "https://h/b.png"), viewModel.addLinks())
        }

    @Test
    fun `a link that drew broken is left out though its server answered`() =
        runTest(dispatcher) {
            val viewModel = viewModels.track(StickerAddViewModel(library()))

            viewModel.setLinkText("https://h/a.png\nhttps://h/b.png")
            advanceUntilIdle()
            viewModel.onPreviewLoaded("https://h/b.png", loads = false)

            assertEquals(listOf("https://h/a.png"), viewModel.addLinks())
        }

    @Test
    fun `typing a link out asks its server once`() =
        runTest(dispatcher) {
            val viewModel = viewModels.track(StickerAddViewModel(library()))

            listOf("https://h/a", "https://h/a.p", "https://h/a.pn", "https://h/a.png").forEach {
                viewModel.setLinkText(it)
                advanceTimeBy(100)
            }
            advanceUntilIdle()

            assertEquals(listOf("https://h/a.png"), http.heads())
        }
}
