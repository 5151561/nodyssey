package io.github.nodyssey.ui.account

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.data.imagehost.ImageHostProvider
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /** All six are on the card at once, and the selected one is the one marked. */
    @Test
    fun `every host is offered, with the selected one marked`() {
        setContent()

        listOf(
            NODE_IMAGE,
            "兰空图床 Lsky Pro · 自建",
            "简单图床 EasyImage · 自建",
            SMMS,
            "imgbb · 公共",
            "自定义图床 · 手动配置",
        ).forEach { name -> composeRule.onAllNodesWithText(name).assertCountEquals(1) }
        composeRule.onNodeWithText(NODE_IMAGE).assertIsSelected()
        composeRule.onNodeWithText(SMMS).assertIsNotSelected()
    }

    @Test
    fun `choosing a host selects it and shows its own instructions`() {
        setContent()

        composeRule.onNodeWithText(SMMS).performClick()

        composeRule.onNodeWithText(SMMS).assertIsSelected()
        composeRule.onNodeWithText(NODE_IMAGE).assertIsNotSelected()
        composeRule.onNodeWithText("在 sm.ms 登录后于「Dashboard › API Token」生成").assertIsDisplayed()
        composeRule.onNodeWithText(NODE_IMAGE_HINT).assertDoesNotExist()
    }

    /** The hint is its own line, not folded into a chip, so a screen reader reads it once. */
    @Test
    fun `the selected host's hint is a line of its own`() {
        setContent()

        composeRule.onNode(hasText(NODE_IMAGE) and hasText(NODE_IMAGE_HINT)).assertDoesNotExist()
        composeRule.onNodeWithText(NODE_IMAGE_HINT).assertIsDisplayed()
    }

    /**
     * Each chip keeps its 48dp target, and the rows of them add nothing on top of it.
     *
     * The target reaches 6dp past the 36dp chip above and below, so those 12dp are the gap the eye
     * sees between two rows. The row spacing the chips used to have was laid over that as well, and
     * the gap came out at about 20dp.
     */
    @Test
    fun `the host chips keep their touch targets and nothing more between rows`() {
        setContent()

        val chips =
            listOf(
                NODE_IMAGE,
                "兰空图床 Lsky Pro · 自建",
                "简单图床 EasyImage · 自建",
                SMMS,
                "imgbb · 公共",
                "自定义图床 · 手动配置",
            ).map { composeRule.onNodeWithText(it).fetchSemanticsNode() }
        val density = chips.first().layoutInfo.density.density
        chips.forEach { chip -> assertTrue("${chip.touchBoundsInRoot}px", chip.touchBoundsInRoot.height / density + 0.5f >= 48f) }
        // One chip per row is enough: the rows are what is being spaced.
        val rows = chips.groupBy { it.boundsInRoot.top }.toSortedMap().values.map { row -> row.first() }
        assertTrue("${rows.size} rows", rows.size > 1)
        rows.zipWithNext().forEach { (upper, lower) ->
            // The targets meet, and what the eye sees between the chips is their slack alone.
            assertEquals(upper.touchBoundsInRoot.bottom / density, lower.touchBoundsInRoot.top / density, 0.5f)
            val gap = (lower.boundsInRoot.top - upper.boundsInRoot.bottom) / density
            assertTrue("rows ${gap}dp apart", gap <= 12.5f)
        }
    }

    private companion object {
        const val NODE_IMAGE = "NodeImage · 论坛常用"
        const val SMMS = "SM.MS · 公共"
        const val NODE_IMAGE_HINT =
            "网页版编辑器用的那家。密钥在 nodeimage.com 右上角「API」页面复制"
    }
}
