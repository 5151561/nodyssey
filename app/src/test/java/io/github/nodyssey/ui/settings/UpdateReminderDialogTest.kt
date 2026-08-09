package io.github.nodyssey.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.data.update.AppRelease
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
class UpdateReminderDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the reminder names the version, the size and what changed`() {
        composeRule.setContent {
            PlazaTheme {
                UpdateReminderDialog(release = RELEASE, onDownload = {}, onPostpone = {})
            }
        }

        composeRule.onNodeWithText("发现新版本 1.2.4").assertIsDisplayed()
        // Only that the size is stated; the exact wording of "8.8 MB" is the platform formatter's.
        composeRule.onNodeWithText("安装包 ", substring = true).assertIsDisplayed()
        // The `###` in the published body is punctuation the dialog does not repeat; the text is.
        composeRule.onNodeWithText("修复", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("表格里的链接可以点了", substring = true).assertIsDisplayed()
    }

    @Test
    fun `each button reports its own answer`() {
        var downloads = 0
        var postpones = 0
        composeRule.setContent {
            PlazaTheme {
                UpdateReminderDialog(
                    release = RELEASE,
                    onDownload = { downloads++ },
                    onPostpone = { postpones++ },
                )
            }
        }

        composeRule.onNodeWithText("稍后").performClick()
        assertEquals(1, postpones)
        assertEquals(0, downloads)

        composeRule.onNodeWithText("下载并安装").performClick()
        assertEquals(1, downloads)
        assertTrue(postpones == 1)
    }

    private companion object {
        val RELEASE =
            AppRelease(
                versionName = "1.2.4",
                tag = "v1.2.4",
                notes = "### 修复\n- 表格里的链接可以点了",
                downloadUrl = "https://example.invalid/nodyssey-v1.2.4.apk",
                assetName = "nodyssey-v1.2.4.apk",
                sizeBytes = 8_800_000,
                htmlUrl = "https://example.invalid/releases",
            )
    }
}
