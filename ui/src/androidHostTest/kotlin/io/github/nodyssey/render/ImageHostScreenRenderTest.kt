package io.github.nodyssey.render

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.data.imagehost.HostedImage
import io.github.nodyssey.data.imagehost.ImageHostProvider
import io.github.nodyssey.ui.account.ImageHostScreen
import io.github.nodyssey.ui.account.ImageHostUiState
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 图床 (6h) connected, with a gallery — the thumbnails are fallbacks, since nothing is fetched here. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1400dp")
class ImageHostScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    private fun screen(darkTheme: Boolean) {
        composeRule.setContent {
            PlazaTheme(darkTheme = darkTheme) {
                ImageHostScreen(
                    state =
                    ImageHostUiState(
                        isLoading = false,
                        provider = ImageHostProvider.SMMS,
                        connected = true,
                        credentialMask = "••••••••••••3f9a",
                        images =
                        (1..6).map {
                            HostedImage(id = "$it", fileName = "shot-$it.png", url = "", sizeBytes = 120_000L)
                        },
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onBack = {},
                    onSelectProvider = {},
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

    @Test
    fun `image host in light`() {
        screen(darkTheme = false)
        composeRule.onRoot().captureRender("image-host-light")
    }

    @Test
    fun `image host in dark`() {
        screen(darkTheme = true)
        composeRule.onRoot().captureRender("image-host-dark")
    }
}
