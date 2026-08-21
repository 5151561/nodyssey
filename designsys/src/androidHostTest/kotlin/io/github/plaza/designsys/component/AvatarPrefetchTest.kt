package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.ImageRequest
import coil3.test.FakeImageLoaderEngine
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The list has fifty rows and shows eight of them. Without [PrefetchAvatars] the other forty-two
 * avatars are not asked for until the reader scrolls onto them, one row at a time; this is the test
 * that says they are asked for beforehand — and that "beforehand" stops well short of the whole page.
 */
@OptIn(ExperimentalCoilApi::class, DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class AvatarPrefetchTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** The loader singleton outlives a test; the next class to install one must not inherit this. */
    @Before
    fun reset() {
        SingletonImageLoader.reset()
    }

    private val requested = mutableListOf<String>()

    private fun setContent() {
        val engine =
            FakeImageLoaderEngine
                .Builder()
                .default(ColorImage(width = 64, height = 64))
                .build()
        composeRule.setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader
                    .Builder(context)
                    .components { add(engine) }
                    .eventListener(
                        object : EventListener() {
                            override fun onStart(request: ImageRequest) {
                                requested += request.data.toString()
                            }
                        },
                    ).build()
            }
            PlazaTheme {
                val listState = rememberLazyListState()
                PrefetchAvatars(
                    listState = listState,
                    itemCount = ROWS,
                    size = AVATAR,
                    urlAt = { index -> avatarUrl(index) },
                )
                LazyColumn(state = listState) {
                    items(List(ROWS) { it }) { index ->
                        Row(modifier = Modifier.height(ROW_HEIGHT)) {
                            UserAvatar(url = avatarUrl(index), name = "u$index", size = AVATAR)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `avatars below the fold are fetched before the reader scrolls to them`() {
        setContent()
        // Row 7 is the last one on an 800dp screen of 100dp rows, so row 8 is the first the reader
        // cannot see — and the first this exists to fetch anyway.
        composeRule.waitUntil(TIMEOUT) { avatarUrl(FIRST_HIDDEN) in requested }
        assertTrue(requested.toString(), avatarUrl(FIRST_HIDDEN + 9) in requested)
    }

    /** Fifty avatars is several megabytes; a reader who stops at row three must not pay for it. */
    @Test
    fun `the rest of the page is left alone`() {
        setContent()
        composeRule.waitUntil(TIMEOUT) { avatarUrl(FIRST_HIDDEN) in requested }
        composeRule.waitForIdle()
        assertFalse(requested.toString(), avatarUrl(FIRST_HIDDEN + 10) in requested)
        assertFalse(requested.toString(), avatarUrl(ROWS - 1) in requested)
    }

    private companion object {
        const val ROWS = 50
        const val FIRST_HIDDEN = 8
        const val TIMEOUT = 5_000L
        val ROW_HEIGHT = 100.dp
        val AVATAR = 40.dp

        fun avatarUrl(index: Int) = "https://example.test/avatar/$index.png"
    }
}
