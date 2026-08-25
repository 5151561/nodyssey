package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.settings.ReportFormat
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.settings.ThemeMode
import io.github.nodyssey.ui.common.UpdateDot
import io.github.nodyssey.ui.common.rememberFileSizeLabel
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.imagehost_connected
import io.github.nodyssey.ui.resources.imagehost_not_connected
import io.github.nodyssey.ui.resources.imagehost_title
import io.github.nodyssey.ui.resources.notify_master_title
import io.github.nodyssey.ui.resources.notify_settings_entry_hint
import io.github.nodyssey.ui.resources.notify_settings_title
import io.github.nodyssey.ui.resources.settings_about
import io.github.nodyssey.ui.resources.settings_about_app
import io.github.nodyssey.ui.resources.settings_about_app_update
import io.github.nodyssey.ui.resources.settings_app_links
import io.github.nodyssey.ui.resources.settings_app_links_hint_off
import io.github.nodyssey.ui.resources.settings_app_links_hint_on
import io.github.nodyssey.ui.resources.settings_appearance
import io.github.nodyssey.ui.resources.settings_body_size
import io.github.nodyssey.ui.resources.settings_body_size_value
import io.github.nodyssey.ui.resources.settings_clear_cache
import io.github.nodyssey.ui.resources.settings_clear_cache_size
import io.github.nodyssey.ui.resources.settings_content
import io.github.nodyssey.ui.resources.settings_doh_entry
import io.github.nodyssey.ui.resources.settings_doh_entry_hint_off
import io.github.nodyssey.ui.resources.settings_doh_entry_hint_on
import io.github.nodyssey.ui.resources.settings_home_page_bar
import io.github.nodyssey.ui.resources.settings_home_page_bar_hint
import io.github.nodyssey.ui.resources.settings_licenses
import io.github.nodyssey.ui.resources.settings_network
import io.github.nodyssey.ui.resources.settings_one_hand
import io.github.nodyssey.ui.resources.settings_one_hand_hint
import io.github.nodyssey.ui.resources.settings_proxy_entry
import io.github.nodyssey.ui.resources.settings_proxy_entry_hint
import io.github.nodyssey.ui.resources.settings_report_format
import io.github.nodyssey.ui.resources.settings_report_format_adapted
import io.github.nodyssey.ui.resources.settings_report_format_hint
import io.github.nodyssey.ui.resources.settings_report_format_source
import io.github.nodyssey.ui.resources.settings_sticker_size
import io.github.nodyssey.ui.resources.settings_sticker_size_value
import io.github.nodyssey.ui.resources.settings_sticker_uniform
import io.github.nodyssey.ui.resources.settings_sticker_uniform_hint
import io.github.nodyssey.ui.resources.settings_text_preview
import io.github.nodyssey.ui.resources.settings_theme
import io.github.nodyssey.ui.resources.settings_theme_dark
import io.github.nodyssey.ui.resources.settings_theme_entry_hint
import io.github.nodyssey.ui.resources.settings_theme_light
import io.github.nodyssey.ui.resources.settings_theme_mode
import io.github.nodyssey.ui.resources.settings_theme_system
import io.github.nodyssey.ui.resources.settings_title
import io.github.nodyssey.ui.resources.settings_update_dev_channel
import io.github.nodyssey.ui.resources.settings_update_dev_channel_hint
import io.github.nodyssey.ui.resources.settings_update_on_launch
import io.github.nodyssey.ui.resources.settings_update_on_launch_hint
import io.github.nodyssey.ui.resources.settings_version
import io.github.nodyssey.ui.resources.settings_wifi_images
import io.github.nodyssey.ui.resources.settings_wifi_images_hint
import io.github.nodyssey.ui.richtext.PostRichContent
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.richtext.LocalStickerSizing
import io.github.plaza.designsys.richtext.StickerSizing
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProxy: () -> Unit,
    onOpenDoh: () -> Unit,
    onOpenImageHost: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Read here rather than in the ViewModel: it is a fact about the system's settings, not about
    // this app's, and it changes while the user is away on a screen we do not own.
    val appLinkHandlingEnabled = rememberAppLinkHandlingEnabled()
    val openAppLinkSettings = rememberAppLinkSettingsLauncher()
    SettingsScreen(
        state = state,
        appLinkHandlingEnabled = appLinkHandlingEnabled,
        onOpenAppLinkSettings = openAppLinkSettings,
        onBack = onBack,
        onOpenTheme = onOpenTheme,
        onThemeModeChange = viewModel::setThemeMode,
        onOneHandModeChange = viewModel::setOneHandMode,
        onFontScaleChange = viewModel::setFontScale,
        onStickerUniformSizeChange = viewModel::setStickerUniformSize,
        onStickerSizeChange = viewModel::setStickerSize,
        onImagesOnWifiOnlyChange = viewModel::setImagesOnWifiOnly,
        onReportFormatChange = viewModel::setReportFormat,
        onHomePageBarChange = viewModel::setHomePageBar,
        onUpdateCheckOnLaunchChange = viewModel::setUpdateCheckOnLaunch,
        onUpdateDevChannelChange = viewModel::setUpdateDevChannel,
        onClearCache = viewModel::clearCache,
        onOpenNotifications = onOpenNotifications,
        onOpenProxy = onOpenProxy,
        onOpenDoh = onOpenDoh,
        onOpenImageHost = onOpenImageHost,
        onOpenAbout = onOpenAbout,
        onOpenLicenses = onOpenLicenses,
        modifier = modifier,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    /** Null where the system has no such switch — see [appLinkHandlingEnabled]. */
    appLinkHandlingEnabled: Boolean?,
    onOpenAppLinkSettings: () -> Unit,
    onBack: () -> Unit,
    onOpenTheme: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOneHandModeChange: (Boolean) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onStickerUniformSizeChange: (Boolean) -> Unit,
    onStickerSizeChange: (Int) -> Unit,
    onImagesOnWifiOnlyChange: (Boolean) -> Unit,
    onReportFormatChange: (ReportFormat) -> Unit,
    onHomePageBarChange: (Boolean) -> Unit,
    onUpdateCheckOnLaunchChange: (Boolean) -> Unit,
    onUpdateDevChannelChange: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenNotifications: () -> Unit = {},
    onOpenProxy: () -> Unit = {},
    onOpenDoh: () -> Unit = {},
    onOpenImageHost: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
) {
    var bodyFontSize by remember(state.settings.fontScale) {
        mutableFloatStateOf(fontScaleToBodySize(state.settings.fontScale))
    }
    // Dragging is local and only the released value is stored, the same deal the font slider has:
    // a write per frame would push a settings emission through every open post body.
    var stickerSize by remember(state.settings.stickerSize) {
        mutableFloatStateOf(state.settings.stickerSize.toFloat())
    }
    val appBarState = rememberOneHandAppBarState()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.settings_title),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SettingsSectionTitle(stringResource(Res.string.settings_appearance))
            SettingsGroup {
                // 明暗 stays here, one tap from 设置, while everything else about colour moved onto
                // 主题's own screen. It is not that it fits the group better — it is that it is
                // reached far more often than the rest of the theme put together, and a control
                // people use daily does not belong two screens deep behind one they set once.
                SettingsBlock(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    title = stringResource(Res.string.settings_theme_mode),
                    top = true,
                ) {
                    ConnectedThemeButtons(
                        selected = state.settings.themeMode,
                        onSelected = onThemeModeChange,
                    )
                }
                // 配色来源, the preset grid, 我的主题 and 色彩风格 are behind this row: four controls
                // and a live preview card is more than a group of eight can carry, and every one of
                // them changes the screen it is read on.
                SettingsRow(
                    leading = { Icon(PlazaIcons.Palette, contentDescription = null) },
                    title = stringResource(Res.string.settings_theme),
                    subtitle = stringResource(Res.string.settings_theme_entry_hint),
                    onClick = onOpenTheme,
                )
                // One switch for every screen that carries the bar rather than one per screen:
                // whether the title should come down to the thumb is a fact about the hand holding
                // the phone, and it does not change between 收藏 and 设置.
                SettingsRow(
                    title = stringResource(Res.string.settings_one_hand),
                    subtitle = stringResource(Res.string.settings_one_hand_hint),
                    checked = state.settings.oneHandMode,
                    onCheckedChange = onOneHandModeChange,
                    trailing = {
                        Switch(checked = state.settings.oneHandMode, onCheckedChange = null)
                    },
                )
                SettingsBlock(
                    title = stringResource(Res.string.settings_body_size),
                    subtitle = stringResource(
                        Res.string.settings_body_size_value,
                        bodyFontSize.roundToInt(),
                    ),
                ) {
                    Slider(
                        value = bodyFontSize,
                        onValueChange = { bodyFontSize = it },
                        onValueChangeFinished = {
                            onFontScaleChange(bodySizeToFontScale(bodyFontSize))
                        },
                        valueRange = BODY_FONT_SIZE_RANGE,
                        // Slider `steps` counts only the interior stops. 14..24sp therefore has
                        // nine interior stops and ten 1sp intervals.
                        steps = BODY_FONT_SIZE_STEPS,
                        modifier = Modifier.testTag(BODY_FONT_SIZE_SLIDER_TAG),
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            stringResource(Res.string.settings_text_preview),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
                }
                SettingsRow(
                    title = stringResource(Res.string.settings_sticker_uniform),
                    subtitle = stringResource(Res.string.settings_sticker_uniform_hint),
                    checked = state.settings.stickerUniformSize,
                    onCheckedChange = onStickerUniformSizeChange,
                    // The slider below only exists while the switch is on, so which of the two
                    // carries the group's rounded bottom edge moves with it.
                    bottom = !state.settings.stickerUniformSize,
                    trailing = {
                        Switch(
                            checked = state.settings.stickerUniformSize,
                            onCheckedChange = null,
                        )
                    },
                )
                if (state.settings.stickerUniformSize) {
                    SettingsBlock(
                        title = stringResource(Res.string.settings_sticker_size),
                        subtitle = stringResource(
                            Res.string.settings_sticker_size_value,
                            stickerSize.roundToInt(),
                        ),
                        bottom = true,
                    ) {
                        Slider(
                            value = stickerSize,
                            onValueChange = { stickerSize = it },
                            onValueChangeFinished = { onStickerSizeChange(stickerSize.roundToInt()) },
                            valueRange = STICKER_SIZE_RANGE,
                            // 20..90sp in 5sp stops: fourteen intervals, so thirteen interior
                            // stops. Finer than that is a difference nobody can see on a
                            // picture this small.
                            steps = STICKER_SIZE_STEPS,
                            modifier = Modifier.testTag(STICKER_SIZE_SLIDER_TAG),
                        )
                        StickerSizePreview(sizeSp = stickerSize.roundToInt())
                    }
                }
            }

            SettingsSectionTitle(stringResource(Res.string.settings_content))
            SettingsGroup {
                appLinkHandlingEnabled?.let { enabled ->
                    SettingsRow(
                        top = true,
                        leading = { Icon(PlazaIcons.Link, contentDescription = null) },
                        title = stringResource(Res.string.settings_app_links),
                        subtitle =
                        stringResource(
                            if (enabled) {
                                Res.string.settings_app_links_hint_on
                            } else {
                                Res.string.settings_app_links_hint_off
                            },
                        ),
                        onClick = onOpenAppLinkSettings,
                    )
                }
                SettingsBlock(
                    // The group's first card when the platform has no App Links notion to show —
                    // 站外链接 used to hold that place and no longer exists; see `ExternalLinks`.
                    top = appLinkHandlingEnabled == null,
                    icon = { Icon(PlazaIcons.Code, contentDescription = null) },
                    title = stringResource(Res.string.settings_report_format),
                    subtitle = stringResource(Res.string.settings_report_format_hint),
                ) {
                    ConnectedReportFormatButtons(
                        selected = state.settings.reportFormat,
                        onSelected = onReportFormatChange,
                    )
                }
                SettingsRow(
                    title = stringResource(Res.string.settings_home_page_bar),
                    subtitle = stringResource(Res.string.settings_home_page_bar_hint),
                    checked = state.settings.homePageBar,
                    onCheckedChange = onHomePageBarChange,
                    trailing = {
                        Switch(
                            checked = state.settings.homePageBar,
                            onCheckedChange = null,
                        )
                    },
                )
                SettingsRow(
                    title = stringResource(Res.string.settings_wifi_images),
                    subtitle = stringResource(Res.string.settings_wifi_images_hint),
                    checked = state.settings.imagesOnWifiOnly,
                    onCheckedChange = onImagesOnWifiOnlyChange,
                    trailing = {
                        Switch(
                            checked = state.settings.imagesOnWifiOnly,
                            onCheckedChange = null,
                        )
                    },
                )
                SettingsRow(
                    title = stringResource(Res.string.imagehost_title),
                    // 已连接 / 未连接 rather than the host's name: what the row is asked on the way
                    // to writing a post is whether inserting a picture will work at all.
                    subtitle = stringResource(
                        if (state.imageHostConnected) {
                            Res.string.imagehost_connected
                        } else {
                            Res.string.imagehost_not_connected
                        },
                    ),
                    onClick = onOpenImageHost,
                    leading = { Icon(PlazaIcons.Image, contentDescription = null) },
                )
                SettingsRow(
                    title = stringResource(Res.string.settings_clear_cache),
                    // The figure is the one system settings shows under 缓存, in the units it uses.
                    // Absent until the walk finishes, rather than a 0 that would read as an answer.
                    subtitle = state.cacheSizeBytes?.let { bytes ->
                        stringResource(
                            Res.string.settings_clear_cache_size,
                            rememberFileSizeLabel(bytes),
                        )
                    },
                    bottom = true,
                    onClick = onClearCache,
                    leading = { Icon(Icons.Default.Delete, contentDescription = null) },
                    trailing = {
                        if (state.isClearingCache) {
                            CircularProgressIndicator(Modifier.size(22.dp))
                        }
                    },
                )
            }

            SettingsSectionTitle(stringResource(Res.string.notify_settings_title))
            SettingsGroup {
                SettingsRow(
                    title = stringResource(Res.string.notify_master_title),
                    subtitle = stringResource(Res.string.notify_settings_entry_hint),
                    top = true,
                    bottom = true,
                    onClick = onOpenNotifications,
                    leading = { Icon(Icons.Default.Notifications, contentDescription = null) },
                )
            }

            SettingsSectionTitle(stringResource(Res.string.settings_network))
            SettingsGroup {
                SettingsRow(
                    title = stringResource(Res.string.settings_proxy_entry),
                    subtitle = stringResource(Res.string.settings_proxy_entry_hint),
                    top = true,
                    bottom = state.dohEnabled == null,
                    onClick = onOpenProxy,
                )
                // Absent rather than disabled where the platform cannot apply a DoH server at all —
                // see [SettingsUiState.dohEnabled], and 默认打开方式 above for the same treatment.
                state.dohEnabled?.let { enabled ->
                    SettingsRow(
                        title = stringResource(Res.string.settings_doh_entry),
                        subtitle = stringResource(
                            if (enabled) {
                                Res.string.settings_doh_entry_hint_on
                            } else {
                                Res.string.settings_doh_entry_hint_off
                            },
                        ),
                        bottom = true,
                        onClick = onOpenDoh,
                    )
                }
            }

            SettingsSectionTitle(stringResource(Res.string.settings_about))
            SettingsGroup {
                SettingsRow(
                    title = stringResource(Res.string.settings_about_app),
                    subtitle = state.updateVersionName
                        ?.let { stringResource(Res.string.settings_about_app_update, it) }
                        ?: stringResource(Res.string.settings_version, state.versionName),
                    top = true,
                    onClick = onOpenAbout,
                    leading = { Icon(Icons.Default.Info, contentDescription = null) },
                    trailing = { if (state.updateVersionName != null) UpdateDot() },
                )
                SettingsRow(
                    title = stringResource(Res.string.settings_update_on_launch),
                    subtitle = stringResource(Res.string.settings_update_on_launch_hint),
                    checked = state.settings.updateCheckOnLaunch,
                    onCheckedChange = onUpdateCheckOnLaunchChange,
                    trailing = {
                        Switch(
                            checked = state.settings.updateCheckOnLaunch,
                            onCheckedChange = null,
                        )
                    },
                )
                SettingsRow(
                    title = stringResource(Res.string.settings_update_dev_channel),
                    subtitle = stringResource(Res.string.settings_update_dev_channel_hint),
                    checked = state.settings.updateDevChannel,
                    onCheckedChange = onUpdateDevChannelChange,
                    trailing = {
                        Switch(
                            checked = state.settings.updateDevChannel,
                            onCheckedChange = null,
                        )
                    },
                )
                SettingsRow(
                    title = stringResource(Res.string.settings_licenses),
                    bottom = true,
                    onClick = onOpenLicenses,
                )
            }
        }
    }
}

/**
 * 跟随系统 / 浅色 / 深色.
 *
 * The one part of 主题 that did not move onto 主题's own screen — see the group above for why.
 */
@Composable
private fun ConnectedThemeButtons(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
) {
    val choices =
        listOf(
            ThemeMode.SYSTEM to stringResource(Res.string.settings_theme_system),
            ThemeMode.LIGHT to stringResource(Res.string.settings_theme_light),
            ThemeMode.DARK to stringResource(Res.string.settings_theme_dark),
        )
    ConnectedChoiceButtons(
        labels = choices.map { it.second },
        selectedIndex = choices.indexOfFirst { it.first == selected },
        onSelect = { onSelected(choices[it].first) },
    )
}

@Composable
private fun ConnectedReportFormatButtons(
    selected: ReportFormat,
    onSelected: (ReportFormat) -> Unit,
) {
    val choices =
        listOf(
            ReportFormat.ADAPTED to stringResource(Res.string.settings_report_format_adapted),
            ReportFormat.SOURCE to stringResource(Res.string.settings_report_format_source),
        )
    ConnectedChoiceButtons(
        labels = choices.map { it.second },
        selectedIndex = choices.indexOfFirst { it.first == selected },
        onSelect = { onSelected(choices[it].first) },
    )
}

/**
 * What 表情大小 buys, drawn by the renderer it changes.
 *
 * The same [PostRichContent] the thread uses, handed a line with two of the site's stickers in it,
 * under the size being dragged rather than the size stored — so the sample answers "how big next to
 * my body text" while the thumb is still moving. The stickers are fetched like any other: already in
 * Coil's cache for anyone who has opened a thread, a pair of fallback squares at the right size for
 * anyone who has not, which still shows the sizing this control is about.
 */
@Composable
private fun StickerSizePreview(sizeSp: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(12.dp),
    ) {
        CompositionLocalProvider(
            LocalStickerSizing provides StickerSizing(uniform = true, uniformSize = sizeSp.sp),
        ) {
            PostRichContent(
                nodes = STICKER_PREVIEW_NODES,
                onLinkClick = {},
                onImageClick = {},
                // Nothing here is worth copying, and a selection handle inside a settings row is a
                // way to lose the drag that was aimed at the slider.
                selectable = false,
                modifier = Modifier.padding(Spacing.md),
            )
        }
    }
}

private val STICKER_PREVIEW_NODES =
    listOf(
        RichNode.Paragraph(
            listOf(
                InlineNode.Text("表情就这么大"),
                InlineNode.Sticker(
                    url = NodeSeekSite.stickerUrl(group = "ac", code = "01", extension = "png"),
                    alt = "ac01",
                ),
                InlineNode.Sticker(
                    url = NodeSeekSite.stickerUrl(group = "yct", code = "001", extension = "gif"),
                    alt = "yct001",
                ),
            ),
        ),
    )

private val STICKER_SIZE_RANGE =
    SettingsRepository.MIN_STICKER_SIZE_SP.toFloat()..SettingsRepository.MAX_STICKER_SIZE_SP.toFloat()
private const val STICKER_SIZE_STEPS = 13
internal const val STICKER_SIZE_SLIDER_TAG = "sticker-size-slider"

private val BODY_FONT_SIZE_RANGE = 14f..24f
private const val BODY_FONT_SIZE_STEPS = 9
private const val BASE_BODY_FONT_SIZE = 16f
internal const val BODY_FONT_SIZE_SLIDER_TAG = "body-font-size-slider"

private fun fontScaleToBodySize(fontScale: Float): Float =
    (fontScale * BASE_BODY_FONT_SIZE)
        .roundToInt()
        .toFloat()
        .coerceIn(BODY_FONT_SIZE_RANGE)

private fun bodySizeToFontScale(bodySize: Float): Float =
    (bodySize.roundToInt() / BASE_BODY_FONT_SIZE)
        .coerceIn(SettingsRepository.MIN_FONT_SCALE, SettingsRepository.MAX_FONT_SCALE)

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SettingsPreview() {
    PlazaTheme {
        SettingsScreen(
            state = SettingsUiState(versionName = "1.1.1"),
            onBack = {},
            onOpenTheme = {},
            onThemeModeChange = {},
            onOneHandModeChange = {},
            onFontScaleChange = {},
            onStickerUniformSizeChange = {},
            onStickerSizeChange = {},
            onImagesOnWifiOnlyChange = {},
            onReportFormatChange = {},
            onHomePageBarChange = {},
            onUpdateCheckOnLaunchChange = {},
            onUpdateDevChannelChange = {},
            onClearCache = {},
            appLinkHandlingEnabled = false,
            onOpenAppLinkSettings = {},
        )
    }
}

// The tablet width — what `readableWidth` is supposed to do to a form this tall is only visible here.
@Preview(showBackground = true, widthDp = 840, heightDp = 800, name = "设置 · 840dp")
@Composable
private fun SettingsWidePreview() {
    PlazaTheme {
        SettingsScreen(
            state = SettingsUiState(versionName = "1.1.1"),
            onBack = {},
            onOpenTheme = {},
            onThemeModeChange = {},
            onOneHandModeChange = {},
            onFontScaleChange = {},
            onStickerUniformSizeChange = {},
            onStickerSizeChange = {},
            onImagesOnWifiOnlyChange = {},
            onReportFormatChange = {},
            onHomePageBarChange = {},
            onUpdateCheckOnLaunchChange = {},
            onUpdateDevChannelChange = {},
            onClearCache = {},
            appLinkHandlingEnabled = false,
            onOpenAppLinkSettings = {},
        )
    }
}
