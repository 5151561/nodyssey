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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.data.settings.ExternalLinkTarget
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.settings.ThemeMode
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.common.UpdateDot
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.readableWidth
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onThemeModeChange = viewModel::setThemeMode,
        onFontScaleChange = viewModel::setFontScale,
        onImagesOnWifiOnlyChange = viewModel::setImagesOnWifiOnly,
        onExternalLinkTargetChange = viewModel::setExternalLinkTarget,
        onClearCache = viewModel::clearCache,
        onOpenNotifications = onOpenNotifications,
        onOpenAbout = onOpenAbout,
        onOpenLicenses = onOpenLicenses,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onImagesOnWifiOnlyChange: (Boolean) -> Unit,
    onExternalLinkTargetChange: (ExternalLinkTarget) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenNotifications: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
) {
    var bodyFontSize by remember(state.settings.fontScale) {
        mutableFloatStateOf(fontScaleToBodySize(state.settings.fontScale))
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                scrollBehavior = scrollBehavior,
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
                    bottom = true,
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
            }

            SettingsSectionTitle(stringResource(R.string.settings_content))
            SettingsGroup {
                SettingsBlock(
                    icon = { Icon(NodysseyIcons.OpenInNew, contentDescription = null) },
                    title = stringResource(R.string.settings_external_link),
                    subtitle = stringResource(R.string.settings_external_link_hint),
                    top = true,
                ) {
                    ConnectedExternalLinkButtons(
                        selected = state.settings.externalLinkTarget,
                        onSelected = onExternalLinkTargetChange,
                    )
                }
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
                    title = stringResource(R.string.settings_clear_cache),
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
    NodysseyTheme {
        SettingsScreen(
            state = SettingsUiState(versionName = "1.1.1"),
            onBack = {},
            onThemeModeChange = {},
            onFontScaleChange = {},
            onImagesOnWifiOnlyChange = {},
            onExternalLinkTargetChange = {},
            onClearCache = {},
        )
    }
}
