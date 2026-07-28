package io.github.nsreader.ui.account

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.nsreader.data.account.AccountProfileFields
import io.github.nsreader.data.account.TelegramBinding
import io.github.nsreader.ui.theme.NodeSeekTheme
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
            displayName = "花间一壶酒",
            fields =
            AccountProfileFields(
                bio = "常驻杭州",
                signature = "第一行\n第二行",
                readme = "1234567890",
            ),
            twoFactorEnabled = false,
            email = "hikari.zhg@gmail.com",
            telegram = TelegramBinding(bound = false),
            blockedCount = 3,
            hiddenBoardCount = 2,
        )

    private fun setContent(
        state: AccountSettingsUiState = populated,
        onOpenProfileFields: () -> Unit = {},
        onOpenSecurity: () -> Unit = {},
        onOpenContact: () -> Unit = {},
        onOpenBlockList: () -> Unit = {},
        onOpenPreferences: () -> Unit = {},
        onSignOut: () -> Unit = {},
    ) {
        composeRule.setContent {
            NodeSeekTheme {
                AccountSettingsScreen(
                    state = state,
                    onBack = {},
                    onOpenProfileFields = onOpenProfileFields,
                    onOpenSecurity = onOpenSecurity,
                    onOpenContact = onOpenContact,
                    onOpenBlockList = onOpenBlockList,
                    onOpenPreferences = onOpenPreferences,
                    onSignOut = onSignOut,
                )
            }
        }
    }

    @Test
    fun `shows all seven groups from the site's own settings page`() {
        setContent()

        listOf("个人信息", "安全", "双因素验证", "联系方式", "屏蔽用户", "常用偏好", "首页版块").forEach { group ->
            composeRule.onNodeWithText(group).performScrollTo().assertExists()
        }
    }

    /** The subtitles are the reason this screen exists rather than a link to `/setting` in a browser. */
    @Test
    fun `each row summarises its current value`() {
        setContent()

        composeRule.onNodeWithText("常驻杭州").assertExists()
        composeRule.onNodeWithText("2 行 · Markdown").assertExists()
        composeRule.onNodeWithText("10 字").assertExists()
        composeRule.onNodeWithText("未开启").assertExists()
        composeRule.onNodeWithText("暂未绑定 telegram").performScrollTo().assertExists()
        composeRule.onNodeWithText("3 人").performScrollTo().assertExists()
        composeRule.onNodeWithText("已隐藏 2 个").performScrollTo().assertExists()
    }

    /** An address on a screen that gets screenshotted; the sub-page shows it in full. */
    @Test
    fun `the email row is masked`() {
        setContent()

        composeRule.onNodeWithText("h***@gmail.com").performScrollTo().assertExists()
        composeRule.onNodeWithText("hikari.zhg@gmail.com").assertDoesNotExist()
    }

    @Test
    fun `a bound telegram row shows who it is bound to`() {
        setContent(
            populated.copy(telegram = TelegramBinding(bound = true, displayName = "Hikari Zhg")),
        )

        composeRule.onNodeWithText("Hikari Zhg").performScrollTo().assertExists()
    }

    @Test
    fun `an untouched home-board preference reads as everything shown`() {
        setContent(populated.copy(hiddenBoardCount = 0))

        composeRule.onNodeWithText("全部显示").performScrollTo().assertExists()
    }

    /** Values the site has not answered for yet are left blank, never guessed at. */
    @Test
    fun `rows carry no subtitle before the account has loaded`() {
        setContent(AccountSettingsUiState())

        composeRule.onNodeWithText("未开启").assertDoesNotExist()
        composeRule.onNodeWithText("暂未绑定 telegram").assertDoesNotExist()
    }

    @Test
    fun `every group routes somewhere`() {
        val opened = mutableListOf<String>()
        setContent(
            onOpenProfileFields = { opened += "profile" },
            onOpenSecurity = { opened += "security" },
            onOpenContact = { opened += "contact" },
            onOpenBlockList = { opened += "block" },
            onOpenPreferences = { opened += "preferences" },
        )

        composeRule.onNodeWithText("Bio").performScrollTo().performClick()
        composeRule.onNodeWithText("修改密码").performScrollTo().performClick()
        composeRule.onNodeWithText("两步验证（2FA）").performScrollTo().performClick()
        composeRule.onNodeWithText("Telegram 提醒").performScrollTo().performClick()
        composeRule.onNodeWithText("已屏蔽列表").performScrollTo().performClick()
        composeRule.onNodeWithText("偏好与首页版块").performScrollTo().performClick()
        composeRule.onNodeWithText("首页显示的版块").performScrollTo().performClick()

        assertEquals(
            listOf("profile", "security", "security", "contact", "block", "preferences", "preferences"),
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
