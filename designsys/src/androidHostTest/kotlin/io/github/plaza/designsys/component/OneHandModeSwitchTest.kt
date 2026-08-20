package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import io.github.plaza.designsys.theme.LocalOneHandMode
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 单手模式, switched off, leaves an ordinary pinned toolbar behind.
 *
 * The setting reaches twenty-odd screens through [LocalOneHandMode] and none of them says anything
 * about it, so this is where "off actually means off" can be asserted at all — a screen test would
 * only be re-testing the local.
 *
 * Both bars are drawn in one composition and the assertion is on the *difference* between them:
 * that is the blank, and the window insets and the toolbar row that both of them carry cancel out
 * rather than having to be predicted.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class OneHandModeSwitchTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `switching 单手模式 off takes the blank away and nothing else`() {
        composeRule.setContent {
            PlazaTheme {
                Column {
                    OneHandTopAppBar(
                        title = "标题",
                        state = rememberOneHandAppBarState(),
                        modifier = Modifier.testTag(ON),
                    )
                    CompositionLocalProvider(LocalOneHandMode provides false) {
                        OneHandTopAppBar(
                            title = "标题",
                            state = rememberOneHandAppBarState(),
                            modifier = Modifier.testTag(OFF),
                        )
                    }
                }
            }
        }

        val on = composeRule.onNodeWithTag(ON).getUnclippedBoundsInRoot().height
        val off = composeRule.onNodeWithTag(OFF).getUnclippedBoundsInRoot().height
        // 40% of an 800dp window, less the 64dp toolbar that is there either way.
        assertEquals(800f * 0.4f - 64f, (on - off).value, 0.5f)
    }

    private companion object {
        const val ON = "oneHandOn"
        const val OFF = "oneHandOff"
    }
}
