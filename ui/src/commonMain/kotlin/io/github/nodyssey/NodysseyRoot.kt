package io.github.nodyssey

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.settings.ColorSource
import io.github.nodyssey.data.settings.ThemeMode
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.common.LocalAppName
import io.github.nodyssey.ui.common.rememberExternalUriHandler
import io.github.nodyssey.ui.navigation.TopLevelDestination
import io.github.nodyssey.ui.richtext.LocalReportFormat
import io.github.nodyssey.ui.settings.theme.activeCharacterPalette
import io.github.nodyssey.ui.settings.theme.rememberActiveSeed
import io.github.nodyssey.ui.settings.theme.toPlaza
import io.github.plaza.designsys.richtext.LocalStickerSizing
import io.github.plaza.designsys.richtext.StickerSizing
import io.github.plaza.designsys.theme.PlazaTheme

/**
 * Everything the app draws, from the theme down.
 *
 * This is the whole of what a platform shell has to call: on Android that is `MainActivity`, which
 * since step D1 does nothing but resolve the intent it was started with and hand the result over.
 * Keeping the theme here rather than there is what lets the five theme helpers below stay `internal`
 * to this module — a shell that reaches into `ui.settings.theme` to build a `ColorScheme` is a shell
 * that has opinions about the app.
 */
@Composable
fun NodysseyRoot(
    container: AppContainer,
    initialTab: TopLevelDestination,
    launchRequest: LaunchRequest?,
    onLaunchRequestHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Theme reads the settings SSOT directly. No copy is kept anywhere, so changing the setting can
    // never leave part of the app on the old value.
    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = UserSettings())

    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    PlazaTheme(
        darkTheme = darkTheme,
        // The three sources differ only in where the seed came from; 动态取色 is the one that can
        // also skip the generator and take the OS's palette whole.
        seedColor = Color(rememberActiveSeed(settings)),
        paletteStyle = settings.paletteStyle.toPlaza(),
        // 角色预设 is the one answer that is not a seed at all: five whole schemes written by hand,
        // which is why they win over both of the lines above.
        characterPalette = activeCharacterPalette(settings),
        useSystemPalette =
        settings.colorSource == ColorSource.WALLPAPER && settings.wallpaperSystemPalette,
        fontScale = settings.fontScale,
        // 单手模式 reaches the twenty-odd screens that carry a `OneHandTopAppBar` through a
        // composition local, so none of them has to be told about the setting.
        oneHandMode = settings.oneHandMode,
    ) {
        // Every link that leaves the app goes through LocalUriHandler — the explicit `openUri` calls
        // in Navigation and the ones Compose resolves for a link inside post text alike. Overriding
        // it here, inside the theme so the tab can match the colours on screen, is what makes
        // 外部链接打开方式 apply everywhere at once. 测评报告 and 表情大小 ride along for the same
        // reason: both are decided per post body, a post body turns up on six screens, and only this
        // one place has to read the setting.
        val stickerSizing =
            remember(settings.stickerUniformSize, settings.stickerSize) {
                StickerSizing(
                    uniform = settings.stickerUniformSize,
                    uniformSize = settings.stickerSize.sp,
                )
            }
        CompositionLocalProvider(
            LocalUriHandler provides rememberExternalUriHandler(settings.externalLinkTarget),
            LocalReportFormat provides settings.reportFormat,
            LocalStickerSizing provides stickerSizing,
            // The one thing on this list that is not a setting: it is what the platform says this
            // build is called, so that a debug build's screens say "Nodyssey·D" like its launcher
            // icon does. See `LocalAppName`.
            LocalAppName provides container.appVersion.label,
        ) {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                MainNavigation(
                    container = container,
                    initialTab = initialTab,
                    launchRequest = launchRequest,
                    onLaunchRequestHandled = onLaunchRequestHandled,
                )
            }
        }
    }
}
