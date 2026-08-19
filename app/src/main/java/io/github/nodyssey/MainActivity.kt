package io.github.nodyssey

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.settings.ColorSource
import io.github.nodyssey.data.settings.ThemeMode
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.ui.common.rememberExternalUriHandler
import io.github.nodyssey.ui.navigation.TopLevelDestination
import io.github.nodyssey.ui.richtext.LocalReportFormat
import io.github.plaza.designsys.richtext.LocalStickerSizing
import io.github.plaza.designsys.richtext.StickerSizing
import io.github.plaza.designsys.theme.PlazaColorSource
import io.github.plaza.designsys.theme.PlazaTheme

class MainActivity : ComponentActivity() {
    /*
     * An intent that arrived after this Activity was already up, waiting to be acted on once.
     *
     * A state rather than a plain field because the composition is what carries it out, and under
     * singleTask an intent delivered to a running app never passes through `onCreate` at all. Set
     * back to null by the composition as soon as it has been handled, so that a rotation does not
     * replay a link the user has already followed.
     */
    private var launchRequest by mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as NodysseyApp).container
        // Only a poll notification carries the extra; a cold start from it should land on 通知.
        // A saved UI state still wins — see the rememberSaveable inside MainNavigation.
        val initialTab =
            if (intent?.getStringExtra(EXTRA_OPEN_TAB) == TAB_NOTIFICATIONS) {
                TopLevelDestination.NOTIFICATIONS
            } else {
                TopLevelDestination.HOME
            }
        // A cold start from a notification is already covered by `initialTab` above; only a link
        // needs the composition to go somewhere it would not have gone on its own.
        launchRequest = intent?.let(::deepLinkOf)

        setContent {
            // Theme reads the settings SSOT directly. No copy is kept anywhere, so changing the
            // setting can never leave part of the app on the old value.
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = UserSettings())

            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            PlazaTheme(
                darkTheme = darkTheme,
                colorSource = when (settings.colorSource) {
                    ColorSource.BRAND -> PlazaColorSource.BRAND
                    ColorSource.WALLPAPER -> PlazaColorSource.WALLPAPER
                    ColorSource.SEED -> PlazaColorSource.SEED
                },
                seedColor = Color(settings.seedColor),
                fontScale = settings.fontScale,
            ) {
                // Every link that leaves the app goes through LocalUriHandler — the explicit
                // `openUri` calls in Navigation and the ones Compose resolves for a link inside post
                // text alike. Overriding it here, inside the theme so the tab can match the colours
                // on screen, is what makes 外部链接打开方式 apply everywhere at once. 测评报告 and
                // 表情大小 ride along for the same reason: both are decided per post body, a post
                // body turns up on six screens, and only this one place has to read the setting.
                val stickerSizing =
                    remember(settings.stickerUniformSize, settings.stickerSize) {
                        StickerSizing(
                            uniform = settings.stickerUniformSize,
                            uniformSize = settings.stickerSize.sp,
                        )
                    }
                CompositionLocalProvider(
                    LocalUriHandler provides
                        rememberExternalUriHandler(settings.externalLinkTarget),
                    LocalReportFormat provides settings.reportFormat,
                    LocalStickerSizing provides stickerSizing,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        MainNavigation(
                            container = container,
                            initialTab = initialTab,
                            launchRequest = launchRequest,
                            onLaunchRequestHandled = { launchRequest = null },
                        )
                    }
                }
            }
        }
    }

    /*
     * Under singleTask every later intent lands here — the notification tap that used to only bring
     * the task forward without switching tab, and every site link the system hands us.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequest =
            deepLinkOf(intent)
                ?: intent
                    .takeIf { it.getStringExtra(EXTRA_OPEN_TAB) == TAB_NOTIFICATIONS }
                    ?.let { LaunchRequest.OpenTab(TopLevelDestination.NOTIFICATIONS) }
    }

    private fun deepLinkOf(intent: Intent): LaunchRequest.OpenLink? =
        intent
            .takeIf { it.action == Intent.ACTION_VIEW }
            ?.data
            ?.toString()
            ?.let(LaunchRequest::OpenLink)

    companion object {
        const val EXTRA_OPEN_TAB = "io.github.nodyssey.OPEN_TAB"
        const val TAB_NOTIFICATIONS = "notifications"
    }
}

/**
 * Somewhere the app has been told to go by something outside it, to be acted on exactly once.
 *
 * Both arrive as an `Intent` and both can turn up while the app is already running, which is the
 * only reason they share a type: the composition needs one thing to watch, not two.
 */
sealed interface LaunchRequest {
    /** A notification tap. */
    data class OpenTab(val tab: TopLevelDestination) : LaunchRequest

    /** A `nodeseek.com` link from another app. Parsed by `NodeSeekSite.parseInternalRoute`. */
    data class OpenLink(val url: String) : LaunchRequest
}
