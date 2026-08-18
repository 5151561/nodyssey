package io.github.nodyssey.ui.account

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.data.imagehost.ImageHostProvider
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ImageHostScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent() {
        composeRule.setContent {
            var provider by remember { mutableStateOf(ImageHostProvider.DEFAULT) }
            PlazaTheme {
                ImageHostScreen(
                    state = ImageHostUiState(isLoading = false, provider = provider),
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = {},
                    onSelectProvider = { provider = it },
                    onSiteUrlChange = {},
                    onTokenChange = {},
                    onCustomChange = {},
                    onToggleCustomFields = {},
                    onSave = {},
                    onRequestDisconnect = {},
                    onDismissDisconnect = {},
                    onConfirmDisconnect = {},
                    onRefresh = {},
                    onRequestDelete = {},
                    onDismissDelete = {},
                    onConfirmDelete = {},
                    onOpenSite = {},
                    onOpenImage = {},
                )
            }
        }
    }

    /** The point of the dropdown: five of the six are not on the screen until they are asked for. */
    @Test
    fun `collapsed picker shows only the selected host`() {
        setContent()

        composeRule.onNodeWithText(NODE_IMAGE).assertIsDisplayed()
        composeRule.onNodeWithText(SMMS).assertDoesNotExist()
        composeRule.onNodeWithText("自定义图床 · 手动配置").assertDoesNotExist()
    }

    @Test
    fun `opening the picker offers every host, and choosing one closes it`() {
        setContent()

        composeRule.onNodeWithText(NODE_IMAGE).performClick()

        // The selected host is on screen twice while the menu is open — in the field and in the
        // menu — and every other host exactly once.
        composeRule.onAllNodesWithText(NODE_IMAGE).assertCountEquals(2)
        listOf(
            "兰空图床 Lsky Pro · 自建",
            "简单图床 EasyImage · 自建",
            SMMS,
            "imgbb · 公共",
            "自定义图床 · 手动配置",
        ).forEach { name -> composeRule.onAllNodesWithText(name).assertCountEquals(1) }

        composeRule.onNodeWithText(SMMS).performClick()

        // Collapsed again, now onto the host that was picked — and its own instructions with it.
        composeRule.onAllNodesWithText(SMMS).assertCountEquals(1)
        composeRule.onNodeWithText(NODE_IMAGE).assertDoesNotExist()
        composeRule.onNodeWithText("在 sm.ms 登录后于「Dashboard › API Token」生成").assertIsDisplayed()
    }

    /**
     * The hint must not be part of the field, or the menu hangs off the bottom of the sentence.
     *
     * `ExposedDropdownMenu` opens below its anchor and the anchor is the whole text field — a
     * supporting line inside it pushes the menu down by however tall that sentence wrapped to. Two
     * nodes here, never one merged node.
     */
    @Test
    fun `the picker's hint sits outside the field the menu anchors to`() {
        setContent()

        composeRule.onNode(hasText(NODE_IMAGE) and hasText(NODE_IMAGE_HINT)).assertDoesNotExist()
        composeRule.onNodeWithText(NODE_IMAGE_HINT).assertIsDisplayed()
    }

    private companion object {
        const val NODE_IMAGE = "NodeImage · 论坛常用"
        const val SMMS = "SM.MS · 公共"
        const val NODE_IMAGE_HINT =
            "网页版编辑器用的那家。密钥在 nodeimage.com 右上角「API」页面复制"
    }
}
