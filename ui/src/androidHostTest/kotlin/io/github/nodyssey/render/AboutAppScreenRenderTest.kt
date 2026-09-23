package io.github.nodyssey.render

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.ui.settings.AboutAppScreen
import io.github.nodyssey.ui.settings.AboutAppUiState
import io.github.plaza.core.update.AppRelease
import io.github.plaza.core.update.AppUpdateState
import io.github.plaza.core.update.UpdateCheck
import io.github.plaza.core.update.UpdateDownload
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 关于 Nodyssey (6i), with a newer release downloading, in both themes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1500dp")
class AboutAppScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    private fun screen(darkTheme: Boolean) {
        val release =
            AppRelease(
                versionName = "1.3.0",
                tag = "v1.3.0",
                notes = "### 新增\n- 轻盈层叠的新界面",
                downloadUrl = "https://example.invalid/nodyssey-v1.3.0.apk",
                assetName = "nodyssey-v1.3.0.apk",
                sizeBytes = 12_600_000,
                htmlUrl = "https://example.invalid/releases",
            )
        composeRule.setContent {
            PlazaTheme(darkTheme = darkTheme) {
                AboutAppScreen(
                    state =
                    AboutAppUiState(
                        versionName = "1.2.21",
                        versionCode = 93,
                        update =
                        AppUpdateState(
                            check = UpdateCheck.Available(release),
                            download = UpdateDownload.Running(downloadedBytes = 7_800_000, totalBytes = 12_600_000),
                        ),
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
    }

    @Test
    fun `about in light`() {
        screen(darkTheme = false)
        composeRule.onRoot().captureRender("about-light")
    }

    @Test
    fun `about in dark`() {
        screen(darkTheme = true)
        composeRule.onRoot().captureRender("about-dark")
    }
}
