package io.github.nodyssey.render

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.data.inMemoryDatabase
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.sticker.MINE_GROUP_KEY
import io.github.nodyssey.data.sticker.MySticker
import io.github.nodyssey.data.sticker.RepoFolder
import io.github.nodyssey.data.sticker.RepoListing
import io.github.nodyssey.data.sticker.StickerLibrary
import io.github.nodyssey.data.testSettingsRepository
import io.github.nodyssey.ui.composer.NodeSeekEmojiGroups
import io.github.nodyssey.ui.sticker.FakeStickerHttp
import io.github.nodyssey.ui.sticker.LocalStickerLibrary
import io.github.nodyssey.ui.sticker.StickerGroupRoute
import io.github.nodyssey.ui.sticker.StickerGroupViewModel
import io.github.nodyssey.ui.sticker.StickerManageRoute
import io.github.nodyssey.ui.sticker.StickerManageViewModel
import io.github.nodyssey.ui.sticker.StickerSourcesRoute
import io.github.nodyssey.ui.sticker.testStickerLibrary
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 表情管理's three pages (1g, 1h, 1i) over a library holding a few saved links and one subscribed
 * repository. The pictures are fallbacks, since nothing is fetched here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1200dp")
class StickerManageScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: NodeSeekDatabase
    private lateinit var library: StickerLibrary

    @Before
    fun setUp() {
        assumeRendering()
        database = inMemoryDatabase(Dispatchers.IO)
        library = testStickerLibrary(database, testSettingsRepository(scope), FakeStickerHttp(), scope)
        runBlocking {
            library.add(listOf("捂脸", "doge", "好耶", "点赞", "吃瓜", "问号").map { MySticker("https://h/$it.gif", it) })
            library.subscribe(
                RepoListing(
                    owner = "zhaoolee",
                    repo = "ChineseBQB",
                    ref = "master",
                    sha = "a3f91c2a3f91c2a3f91c2a3f91c2a3f91c2a3f91",
                    folders = listOf(
                        RepoFolder("熊猫头", (1..184).map { "熊猫头/$it.gif" }, 184),
                        RepoFolder("程序员", (1..97).map { "程序员/$it.gif" }, 97),
                    ),
                    truncated = false,
                ),
                paths = setOf("熊猫头", "程序员"),
            )
            library.setGroupHidden("fluent", hidden = true)
            library.groupLayout.first()
        }
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        scope.cancel()
    }

    private fun render(
        name: String,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        composeRule.setContent {
            PlazaTheme(darkTheme = false) {
                CompositionLocalProvider(LocalStickerLibrary provides library) { content() }
            }
        }
        // Room and DataStore answer off the main thread; give the page their first rows.
        Thread.sleep(500)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRender(name)
    }

    @Test
    fun `manage front page`() {
        val viewModel = StickerManageViewModel(library, NodeSeekEmojiGroups)
        render("sticker-manage") {
            StickerManageRoute(
                viewModel = viewModel,
                onBack = {},
                onOpenGroup = {},
                onOpenSources = {},
                onOpenImageHost = {},
            )
        }
    }

    @Test
    fun `group page for mine`() {
        val viewModel = StickerGroupViewModel(library, MINE_GROUP_KEY, NodeSeekEmojiGroups)
        render("sticker-group-mine") { StickerGroupRoute(viewModel = viewModel, onBack = {}, onOpenUrl = {}) }
    }

    @Test
    fun `sources page`() {
        val viewModel = StickerManageViewModel(library, NodeSeekEmojiGroups)
        render("sticker-sources") { StickerSourcesRoute(viewModel = viewModel, onBack = {}) }
    }
}
