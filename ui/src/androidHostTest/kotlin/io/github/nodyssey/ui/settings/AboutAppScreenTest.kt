package io.github.nodyssey.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.nodyssey.ui.assertEveryTouchTargetAtLeast48dp
import io.github.plaza.core.crash.CrashReport
import io.github.plaza.core.update.AppRelease
import io.github.plaza.core.update.AppUpdateState
import io.github.plaza.core.update.UpdateCheck
import io.github.plaza.core.update.UpdateDownload
import io.github.plaza.designsys.theme.PlazaTheme
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
            PlazaTheme {
                AboutAppScreen(
                    state = AboutAppUiState(versionName = "9.9", versionCode = 99),
                    onBack = {},
                    onCheckUpdates = {},
                    onDownloadUpdate = {},
                    onCancelDownload = {},
                    onInstallUpdate = {},
                    onGrantInstallPermission = {},
                    onOpenChangelog = {},
                    onOpenHelp = {},
                    onOpenLicenses = { openedLicenses = true },
                    onOpenUri = {},
                    onExportCrashReport = {},
                    onClearCrashReport = {},
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
            PlazaTheme {
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
                    onOpenHelp = {},
                    onOpenLicenses = {},
                    onOpenUri = {},
                    onExportCrashReport = {},
                    onClearCrashReport = {},
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
            PlazaTheme {
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
                    onOpenHelp = {},
                    onOpenLicenses = {},
                    onOpenUri = {},
                    onExportCrashReport = {},
                    onClearCrashReport = {},
                )
            }
        }

        composeRule.onNodeWithText("新版本 1.2.0").assertIsDisplayed()
        composeRule.onNodeWithText("下载并安装").performClick()
        assertTrue(downloaded)

        composeRule.onNodeWithText("立即安装").performClick()
        assertTrue(installed)
    }

    /** Every row and button on 关于 — with the update card and crash rows up — clears 48dp. */
    @Test
    fun `every touch target on the about screen holds 48dp`() {
        composeRule.setContent {
            PlazaTheme {
                AboutAppScreen(
                    state =
                    AboutAppUiState(
                        versionName = "1.1.0",
                        versionCode = 3,
                        update = AppUpdateState(check = UpdateCheck.Available(RELEASE)),
                        crashReport = CRASH,
                    ),
                    onBack = {},
                    onCheckUpdates = {},
                    onDownloadUpdate = {},
                    onCancelDownload = {},
                    onInstallUpdate = {},
                    onGrantInstallPermission = {},
                    onOpenChangelog = {},
                    onOpenHelp = {},
                    onOpenLicenses = {},
                    onOpenUri = {},
                    onExportCrashReport = {},
                    onClearCrashReport = {},
                )
            }
        }

        composeRule.assertEveryTouchTargetAtLeast48dp()
    }

    /**
     * The crash rows exist only after a crash: a healthy install shows nothing, a recorded one shows
     * 导出 with the crash's own moment and version, and 清除 is the way back to nothing.
     */
    @Test
    fun `a recorded crash offers export and clearing, and a healthy install offers neither`() {
        var exported: CrashReport? = null
        var cleared = false
        composeRule.setContent {
            PlazaTheme {
                val report = remember { mutableStateOf<CrashReport?>(CRASH) }
                AboutAppScreen(
                    state = AboutAppUiState(versionName = "1.3.0", versionCode = 5, crashReport = report.value),
                    onBack = {},
                    onCheckUpdates = {},
                    onDownloadUpdate = {},
                    onCancelDownload = {},
                    onInstallUpdate = {},
                    onGrantInstallPermission = {},
                    onOpenChangelog = {},
                    onOpenHelp = {},
                    onOpenLicenses = {},
                    onOpenUri = {},
                    onExportCrashReport = { exported = it },
                    onClearCrashReport = {
                        cleared = true
                        report.value = null
                    },
                )
            }
        }

        composeRule.onNodeWithText("导出崩溃日志").performScrollTo().performClick()
        assertTrue(exported === CRASH)

        composeRule.onNodeWithText("清除崩溃记录").performScrollTo().performClick()
        assertTrue(cleared)
        composeRule.onNodeWithText("导出崩溃日志").assertDoesNotExist()
        composeRule.onNodeWithText("清除崩溃记录").assertDoesNotExist()
    }

    /**
     * 使用帮助 is only reachable from here, and it is the only place in the app that says what the
     * blank band under a second-level title is — so a row that quietly stopped being drawn would
     * take the answer with it.
     */
    @Test
    fun `app page leads to 使用帮助`() {
        var openedHelp = false
        composeRule.setContent {
            PlazaTheme {
                AboutAppScreen(
                    state = AboutAppUiState(versionName = "9.9", versionCode = 99),
                    onBack = {},
                    onCheckUpdates = {},
                    onDownloadUpdate = {},
                    onCancelDownload = {},
                    onInstallUpdate = {},
                    onGrantInstallPermission = {},
                    onOpenChangelog = {},
                    onOpenHelp = { openedHelp = true },
                    onOpenLicenses = {},
                    onOpenUri = {},
                    onExportCrashReport = {},
                    onClearCrashReport = {},
                )
            }
        }

        composeRule.onNodeWithText("使用帮助").performScrollTo().performClick()

        assertTrue(openedHelp)
    }

    private companion object {
        val CRASH =
            CrashReport(
                occurredAtMillis = 1_756_000_000_000,
                versionName = "1.3.0",
                text = "java.lang.IllegalStateException: it died",
            )

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
