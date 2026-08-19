package io.github.nodyssey.ui.settings

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.settings.ExternalLinkTarget
import io.github.nodyssey.data.settings.ReportFormat
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.settings.ThemeMode
import io.github.nodyssey.ui.common.UpdateDot
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
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProxy: () -> Unit,
    onOpenImageHost: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Read here rather than in the ViewModel: it is a fact about the system's settings, not about
    // this app's, and it changes while the user is away on a screen we do not own.
    val appLinkHandlingEnabled = rememberAppLinkHandlingEnabled(context)
    val appLinkSettings =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Nothing to read from the result — the value comes back through ON_RESUME above.
        }
    SettingsScreen(
        state = state,
        appLinkHandlingEnabled = appLinkHandlingEnabled,
        onOpenAppLinkSettings = {
            runCatching { appLinkSettings.launch(appLinkSettingsIntent(context)) }
        },
        onBack = onBack,
        onThemeModeChange = viewModel::setThemeMode,
        onFontScaleChange = viewModel::setFontScale,
        onStickerUniformSizeChange = viewModel::setStickerUniformSize,
        onStickerSizeChange = viewModel::setStickerSize,
        onImagesOnWifiOnlyChange = viewModel::setImagesOnWifiOnly,
        onExternalLinkTargetChange = viewModel::setExternalLinkTarget,
        onReportFormatChange = viewModel::setReportFormat,
        onHomePageBarChange = viewModel::setHomePageBar,
        onUpdateCheckOnLaunchChange = viewModel::setUpdateCheckOnLaunch,
        onUpdateDevChannelChange = viewModel::setUpdateDevChannel,
        onClearCache = viewModel::clearCache,
        onOpenNotifications = onOpenNotifications,
        onOpenProxy = onOpenProxy,
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
    onThemeModeChange: (ThemeMode) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onStickerUniformSizeChange: (Boolean) -> Unit,
    onStickerSizeChange: (Int) -> Unit,
    onImagesOnWifiOnlyChange: (Boolean) -> Unit,
    onExternalLinkTargetChange: (ExternalLinkTarget) -> Unit,
    onReportFormatChange: (ReportFormat) -> Unit,
    onHomePageBarChange: (Boolean) -> Unit,
    onUpdateCheckOnLaunchChange: (Boolean) -> Unit,
    onUpdateDevChannelChange: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenNotifications: () -> Unit = {},
    onOpenProxy: () -> Unit = {},
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
                title = stringResource(R.string.settings_title),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
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
            SettingsSectionTitle(stringResource(R.string.settings_appearance))
            SettingsGroup {
                SettingsBlock(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    title = stringResource(R.string.settings_theme),
                    top = true,
                ) {
                    ConnectedThemeButtons(
                        selected = state.settings.themeMode,
                        onSelected = onThemeModeChange,
                    )
                }
                SettingsBlock(
                    title = stringResource(R.string.settings_body_size),
                    subtitle = stringResource(
                        R.string.settings_body_size_value,
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
                            stringResource(R.string.settings_text_preview),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
                }
                SettingsRow(
                    title = stringResource(R.string.settings_sticker_uniform),
                    subtitle = stringResource(R.string.settings_sticker_uniform_hint),
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
                        title = stringResource(R.string.settings_sticker_size),
                        subtitle = stringResource(
                            R.string.settings_sticker_size_value,
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

            SettingsSectionTitle(stringResource(R.string.settings_content))
            SettingsGroup {
                SettingsBlock(
                    icon = { Icon(PlazaIcons.OpenInNew, contentDescription = null) },
                    title = stringResource(R.string.settings_external_link),
                    subtitle = stringResource(R.string.settings_external_link_hint),
                    top = true,
                ) {
                    ConnectedExternalLinkButtons(
                        selected = state.settings.externalLinkTarget,
                        onSelected = onExternalLinkTargetChange,
                    )
                }
                appLinkHandlingEnabled?.let { enabled ->
                    SettingsRow(
                        leading = { Icon(PlazaIcons.Link, contentDescription = null) },
                        title = stringResource(R.string.settings_app_links),
                        subtitle =
                        stringResource(
                            if (enabled) {
                                R.string.settings_app_links_hint_on
                            } else {
                                R.string.settings_app_links_hint_off
                            },
                        ),
                        onClick = onOpenAppLinkSettings,
                    )
                }
                SettingsBlock(
                    icon = { Icon(PlazaIcons.Code, contentDescription = null) },
                    title = stringResource(R.string.settings_report_format),
                    subtitle = stringResource(R.string.settings_report_format_hint),
                ) {
                    ConnectedReportFormatButtons(
                        selected = state.settings.reportFormat,
                        onSelected = onReportFormatChange,
                    )
                }
                SettingsRow(
                    title = stringResource(R.string.settings_home_page_bar),
                    subtitle = stringResource(R.string.settings_home_page_bar_hint),
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
                    title = stringResource(R.string.settings_wifi_images),
                    subtitle = stringResource(R.string.settings_wifi_images_hint),
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
                    title = stringResource(R.string.imagehost_title),
                    // 已连接 / 未连接 rather than the host's name: what the row is asked on the way
                    // to writing a post is whether inserting a picture will work at all.
                    subtitle = stringResource(
                        if (state.imageHostConnected) {
                            R.string.imagehost_connected
                        } else {
                            R.string.imagehost_not_connected
                        },
                    ),
                    onClick = onOpenImageHost,
                    leading = { Icon(PlazaIcons.Image, contentDescription = null) },
                )
                val context = LocalContext.current
                SettingsRow(
                    title = stringResource(R.string.settings_clear_cache),
                    // The figure is the one system settings shows under 缓存, in the units it uses.
                    // Absent until the walk finishes, rather than a 0 that would read as an answer.
                    subtitle = state.cacheSizeBytes?.let { bytes ->
                        stringResource(
                            R.string.settings_clear_cache_size,
                            Formatter.formatShortFileSize(context, bytes),
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

            SettingsSectionTitle(stringResource(R.string.notify_settings_title))
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.notify_master_title),
                    subtitle = stringResource(R.string.notify_settings_entry_hint),
                    top = true,
                    bottom = true,
                    onClick = onOpenNotifications,
                    leading = { Icon(Icons.Default.Notifications, contentDescription = null) },
                )
            }

            SettingsSectionTitle(stringResource(R.string.settings_network))
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_proxy_entry),
                    subtitle = stringResource(R.string.settings_proxy_entry_hint),
                    top = true,
                    bottom = true,
                    onClick = onOpenProxy,
                )
            }

            SettingsSectionTitle(stringResource(R.string.settings_about))
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_about_app),
                    subtitle = state.updateVersionName
                        ?.let { stringResource(R.string.settings_about_app_update, it) }
                        ?: stringResource(R.string.settings_version, state.versionName),
                    top = true,
                    onClick = onOpenAbout,
                    leading = { Icon(Icons.Default.Info, contentDescription = null) },
                    trailing = { if (state.updateVersionName != null) UpdateDot() },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_update_on_launch),
                    subtitle = stringResource(R.string.settings_update_on_launch_hint),
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
                    title = stringResource(R.string.settings_update_dev_channel),
                    subtitle = stringResource(R.string.settings_update_dev_channel_hint),
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
                    title = stringResource(R.string.settings_licenses),
                    bottom = true,
                    onClick = onOpenLicenses,
                )
            }
        }
    }
}

@Composable
private fun ConnectedExternalLinkButtons(
    selected: ExternalLinkTarget,
    onSelected: (ExternalLinkTarget) -> Unit,
) {
    val choices =
        listOf(
            ExternalLinkTarget.CUSTOM_TAB to
                stringResource(R.string.settings_external_link_custom_tab),
            ExternalLinkTarget.BROWSER to stringResource(R.string.settings_external_link_browser),
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
            ReportFormat.ADAPTED to stringResource(R.string.settings_report_format_adapted),
            ReportFormat.SOURCE to stringResource(R.string.settings_report_format_source),
        )
    ConnectedChoiceButtons(
        labels = choices.map { it.second },
        selectedIndex = choices.indexOfFirst { it.first == selected },
        onSelect = { onSelected(choices[it].first) },
    )
}

@Composable
private fun ConnectedThemeButtons(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
) {
    val choices =
        listOf(
            ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
            ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
            ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
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
            onThemeModeChange = {},
            onFontScaleChange = {},
            onStickerUniformSizeChange = {},
            onStickerSizeChange = {},
            onImagesOnWifiOnlyChange = {},
            onExternalLinkTargetChange = {},
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
