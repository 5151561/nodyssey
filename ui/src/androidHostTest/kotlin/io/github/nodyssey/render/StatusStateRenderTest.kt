package io.github.nodyssey.render

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.ui.common.LocalOpenNetworkCheck
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Board 2e — the network-error state, which is the card every empty and error state now draws, in
 * both themes. Rendered inside a bare scaffold with a back arrow, the way a pushed screen shows it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class StatusStateRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen(darkTheme: Boolean) {
        PlazaTheme(darkTheme = darkTheme) {
            CompositionLocalProvider(LocalOpenNetworkCheck provides {}) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("") },
                            navigationIcon = {
                                IconButton(onClick = {}) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                }
                            },
                            colors =
                            TopAppBarDefaults.topAppBarColors(containerColor = LocalPlazaLayers.current.page),
                        )
                    },
                ) { padding ->
                    SiteErrorState(
                        error = SiteError.Network,
                        onRetry = {},
                        onOpenBrowser = {},
                        onVerify = {},
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    @Test
    fun `the network state in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("status-network-light")
    }

    @Test
    fun `the network state in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("status-network-dark")
    }
}
