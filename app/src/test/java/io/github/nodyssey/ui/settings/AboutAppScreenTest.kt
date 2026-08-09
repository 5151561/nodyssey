package io.github.nodyssey.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.nodyssey.data.update.AppRelease
import io.github.nodyssey.data.update.AppUpdateState
import io.github.nodyssey.data.update.UpdateCheck
import io.github.nodyssey.data.update.UpdateDownload
import io.github.plaza.designsys.theme.NodysseyTheme
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
class AboutAppScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app page exposes version and license action`() {
        var openedLicenses = false
        composeRule.setContent {
            NodysseyTheme {
                AboutAppScreen(
                    state = AboutAppUiState(versionName = "9.9", versionCode = 99),
                    onBack = {},
                    onCheckUpdates = {},
                    onDownloadUpdate = {},
                    onCancelDownload = {},
                    onInstallUpdate = {},
                    onGrantInstallPermission = {},
                    onOpenChangelog = {},
                    onOpenLicenses = { openedLicenses = true },
                    onOpenUri = {},
                )
            }
        }

        composeRule.onNodeWithText("版本 9.9 (99)").assertIsDisplayed()
        composeRule.onNodeWithText("开源许可").performScrollTo().performClick()

        assertTrue(openedLicenses)
    }

    @Test
    fun `community information is absent from the app page`() {
        composeRule.setContent {
            NodysseyTheme {
                AboutAppScreen(
                    state =
                    AboutAppUiState(
                        versionName = "1.0",
                        versionCode = 1,
                        update = AppUpdateState(check = UpdateCheck.UpToDate),
                    ),
                    onBack = {},
                    onCheckUpdates = {},
                    onDownloadUpdate = {},
                    onCancelDownload = {},
                    onInstallUpdate = {},
                    onGrantInstallPermission = {},
                    onOpenChangelog = {},
                    onOpenLicenses = {},
                    onOpenUri = {},
                )
            }
        }

        composeRule.onNodeWithText("关于 Nodyssey").assertIsDisplayed()
        composeRule.onNodeWithText("已是最新").assertIsDisplayed()
        composeRule.onNodeWithText("关于本站").assertDoesNotExist()
        composeRule.onNodeWithText("RSS 订阅").assertDoesNotExist()
        composeRule.onNodeWithText("电报频道").assertDoesNotExist()
    }

    @Test
    fun `an available release offers the download, and the finished download offers the install`() {
        var downloaded = false
        var installed = false
        composeRule.setContent {
            NodysseyTheme {
                val download = remember { mutableStateOf<UpdateDownload>(UpdateDownload.Idle) }
                AboutAppScreen(
                    state =
                    AboutAppUiState(
                        versionName = "1.1.0",
                        versionCode = 3,
                        update =
                        AppUpdateState(
                            check = UpdateCheck.Available(RELEASE),
                            download = download.value,
                        ),
                    ),
                    onBack = {},
                    onCheckUpdates = {},
                    onDownloadUpdate = {
                        downloaded = true
                        download.value = UpdateDownload.Ready("/tmp/nodyssey.apk", "1.2.0")
                    },
                    onCancelDownload = {},
                    onInstallUpdate = { installed = true },
                    onGrantInstallPermission = {},
                    onOpenChangelog = {},
                    onOpenLicenses = {},
                    onOpenUri = {},
                )
            }
        }

        composeRule.onNodeWithText("新版本 1.2.0").assertIsDisplayed()
        composeRule.onNodeWithText("下载并安装").performClick()
        assertTrue(downloaded)

        composeRule.onNodeWithText("立即安装").performClick()
        assertTrue(installed)
    }

    private companion object {
        val RELEASE =
            AppRelease(
                versionName = "1.2.0",
                tag = "v1.2.0",
                notes = "- 应用内更新",
                downloadUrl = "https://example.invalid/nodyssey-v1.2.0.apk",
                assetName = "nodyssey-v1.2.0.apk",
                sizeBytes = 8_830_112,
                htmlUrl = "https://example.invalid/releases",
            )
    }
}
