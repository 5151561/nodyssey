package io.github.nodyssey.ui.account

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.nodyssey.ui.theme.NodysseyTheme
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
class AccountSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val populated =
        AccountSettingsUiState(
            blockedCount = 3,
            imageHostConnected = true,
        )

    private fun setContent(
        state: AccountSettingsUiState = populated,
        onOpenProfileFields: () -> Unit = {},
        onOpenSecurity: () -> Unit = {},
        onOpenContact: () -> Unit = {},
        onOpenBlockList: () -> Unit = {},
        onOpenPreferences: () -> Unit = {},
        onOpenNodeImage: () -> Unit = {},
        onSignOut: () -> Unit = {},
    ) {
        composeRule.setContent {
            NodysseyTheme {
                AccountSettingsScreen(
                    state = state,
                    onBack = {},
                    onOpenProfileFields = onOpenProfileFields,
                    onOpenSecurity = onOpenSecurity,
                    onOpenContact = onOpenContact,
                    onOpenBlockList = onOpenBlockList,
                    onOpenPreferences = onOpenPreferences,
                    onOpenNodeImage = onOpenNodeImage,
                    onSignOut = onSignOut,
                )
            }
        }
    }

    @Test
    fun `shows exactly one entry for each destination`() {
        setContent()

        listOf("个人信息", "安全", "联系方式", "屏蔽用户", "偏好与首页版块", "NodeImage 图床")
            .forEach { destination ->
                composeRule.onAllNodesWithText(destination).assertCountEquals(1)
            }
    }

    @Test
    fun `does not expose page sections as separate routes`() {
        setContent()

        listOf(
            "头像",
            "Bio",
            "签名",
            "Readme",
            "修改密码",
            "两步验证（2FA）",
            "邮箱",
            "Telegram 提醒",
            "首页显示的版块",
        ).forEach { section ->
            composeRule.onNodeWithText(section).assertDoesNotExist()
        }
    }

    @Test
    fun `destination summaries explain what each page contains`() {
        setContent()

        composeRule.onNodeWithText("头像、Bio、签名与 Readme").assertExists()
        composeRule.onNodeWithText("修改密码与两步验证").assertExists()
        composeRule.onNodeWithText("邮箱、手机与 Telegram").assertExists()
        composeRule.onNodeWithText("3 人").performScrollTo().assertExists()
        composeRule.onNodeWithText("节日主题、夜间模式与首页版块").performScrollTo().assertExists()
    }

    @Test
    fun `each destination routes exactly once`() {
        val opened = mutableListOf<String>()
        setContent(
            onOpenProfileFields = { opened += "profile" },
            onOpenSecurity = { opened += "security" },
            onOpenContact = { opened += "contact" },
            onOpenBlockList = { opened += "block" },
            onOpenPreferences = { opened += "preferences" },
            onOpenNodeImage = { opened += "nodeimage" },
        )

        composeRule.onNodeWithText("个人信息").performScrollTo().performClick()
        composeRule.onNodeWithText("安全").performScrollTo().performClick()
        composeRule.onNodeWithText("联系方式").performScrollTo().performClick()
        composeRule.onNodeWithText("屏蔽用户").performScrollTo().performClick()
        composeRule.onNodeWithText("偏好与首页版块").performScrollTo().performClick()
        composeRule.onNodeWithText("NodeImage 图床").performScrollTo().performClick()

        assertEquals(
            listOf("profile", "security", "contact", "block", "preferences", "nodeimage"),
            opened,
        )
    }

    @Test
    fun `signing out is reachable from the bottom of the list`() {
        var signedOut = false
        setContent(onSignOut = { signedOut = true })

        composeRule.onNodeWithText("退出登录").performScrollTo().performClick()

        assertTrue(signedOut)
    }
}
