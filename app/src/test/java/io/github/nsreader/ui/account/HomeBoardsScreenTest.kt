package io.github.nsreader.ui.account

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.nsreader.data.Board
import io.github.nsreader.ui.theme.NodeSeekTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class HomeBoardsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val boards =
        listOf(
            Board("daily", "日常", null),
            Board("life", "生活", null),
            Board("tech", "技术", null),
            Board("dev", "Dev", null),
            Board("trade", "交易", null),
            Board("expose", "曝光", null),
        )

    private fun setContent(
        selected: Set<String> = setOf("daily", "tech"),
        onToggle: (String) -> Unit = {},
        onReset: () -> Unit = {},
        onSave: () -> Unit = {},
    ) {
        composeRule.setContent {
            NodeSeekTheme {
                HomeBoardsScreen(
                    state =
                    HomeBoardsUiState(isLoading = false, boards = boards, selected = selected),
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = {},
                    onToggle = onToggle,
                    onReset = onReset,
                    onSave = onSave,
                )
            }
        }
    }

    /** The four headings are the same families the board tags are coloured by on every list row. */
    @Test
    fun `boards are grouped under the four board families`() {
        setContent()

        listOf("日常类", "技术类", "交易类", "警示类").forEach { family ->
            composeRule.onNodeWithText(family).performScrollTo().assertExists()
        }
    }

    @Test
    fun `the summary counts the selection against the total`() {
        setContent()

        composeRule.onNodeWithText("勾选的版块会出现在首页版块条 · 已选 2 / 6").assertExists()
    }

    @Test
    fun `checked state follows the selection`() {
        setContent(selected = setOf("daily"))

        composeRule.onNodeWithText("日常").performScrollTo().assertIsOn()
        composeRule.onNodeWithText("生活").performScrollTo().assertIsOff()
    }

    /** The whole row is the target, not the 20dp checkbox inside it. */
    @Test
    fun `tapping a row's label toggles it`() {
        val toggled = mutableListOf<String>()
        setContent(onToggle = { toggled += it })

        composeRule.onNodeWithText("曝光").performScrollTo().performClick()

        assertEquals(listOf("expose"), toggled)
    }

    @Test
    fun `the save button counts what will be saved`() {
        setContent(selected = setOf("daily", "tech", "dev"))

        composeRule.onNodeWithText("保存 3 个版块").assertExists()
    }

    @Test
    fun `reset and save are both reachable from the bottom bar`() {
        var reset = false
        var saved = false
        setContent(onReset = { reset = true }, onSave = { saved = true })

        composeRule.onNodeWithText("重置").performClick()
        composeRule.onNodeWithText("保存 2 个版块").performClick()

        assertEquals(true, reset)
        assertEquals(true, saved)
    }
}
