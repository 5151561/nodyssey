package io.github.nsreader.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nsreader.R
import io.github.nsreader.data.settings.SettingsRepository
import io.github.nsreader.data.settings.ThemeMode
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.readableWidth
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onThemeModeChange = viewModel::setThemeMode,
        onFontScaleChange = viewModel::setFontScale,
        onImagesOnWifiOnlyChange = viewModel::setImagesOnWifiOnly,
        onClearCache = viewModel::clearCache,
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
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var bodyFontSize by remember(state.settings.fontScale) {
        mutableFloatStateOf(fontScaleToBodySize(state.settings.fontScale))
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                SettingsRow(
                    title = stringResource(R.string.settings_wifi_images),
                    subtitle = stringResource(R.string.settings_wifi_images_hint),
                    top = true,
                    trailing = {
                        Switch(
                            checked = state.settings.imagesOnWifiOnly,
                            onCheckedChange = onImagesOnWifiOnlyChange,
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
                            CircularProgressIndicator(Modifier.width(22.dp))
                        }
                    },
                )
            }

            SettingsSectionTitle(stringResource(R.string.settings_about))
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_about_app),
                    subtitle = stringResource(R.string.settings_version),
                    top = true,
                    leading = { Icon(Icons.Default.Info, contentDescription = null) },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_licenses),
                    bottom = true,
                )
            }
        }
    }
}

@Composable
private fun ConnectedThemeButtons(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val choices =
            listOf(
                ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
            )
        choices.forEachIndexed { index, (mode, label) ->
            val isSelected = mode == selected
            val targetStartRadius =
                if (isSelected || index == 0) CONNECTED_OUTER_RADIUS else CONNECTED_INNER_RADIUS
            val targetEndRadius =
                if (isSelected || index == choices.lastIndex) {
                    CONNECTED_OUTER_RADIUS
                } else {
                    CONNECTED_INNER_RADIUS
                }
            val startRadius by animateDpAsState(
                targetValue = targetStartRadius,
                animationSpec = connectedButtonSpring(),
                label = "theme_${mode.name}_start_radius",
            )
            val endRadius by animateDpAsState(
                targetValue = targetEndRadius,
                animationSpec = connectedButtonSpring(),
                label = "theme_${mode.name}_end_radius",
            )
            val containerColor by animateColorAsState(
                targetValue =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                animationSpec = connectedButtonSpring(),
                label = "theme_${mode.name}_container",
            )
            val contentColor by animateColorAsState(
                targetValue =
                if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = connectedButtonSpring(),
                label = "theme_${mode.name}_content",
            )
            Surface(
                onClick = { onSelected(mode) },
                modifier = Modifier.weight(1f).semantics { this.selected = isSelected },
                shape =
                RoundedCornerShape(
                    topStart = startRadius,
                    bottomStart = startRadius,
                    topEnd = endRadius,
                    bottomEnd = endRadius,
                ),
                color = containerColor,
                contentColor = contentColor,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 11.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedVisibility(
                        visible = isSelected,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.width(16.dp))
                    }
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private val BODY_FONT_SIZE_RANGE = 14f..24f
private const val BODY_FONT_SIZE_STEPS = 9
private const val BASE_BODY_FONT_SIZE = 16f
internal const val BODY_FONT_SIZE_SLIDER_TAG = "body-font-size-slider"
private val CONNECTED_OUTER_RADIUS = 20.dp
private val CONNECTED_INNER_RADIUS = 5.dp

private fun fontScaleToBodySize(fontScale: Float): Float =
    (fontScale * BASE_BODY_FONT_SIZE)
        .roundToInt()
        .toFloat()
        .coerceIn(BODY_FONT_SIZE_RANGE)

private fun bodySizeToFontScale(bodySize: Float): Float =
    (bodySize.roundToInt() / BASE_BODY_FONT_SIZE)
        .coerceIn(SettingsRepository.MIN_FONT_SCALE, SettingsRepository.MAX_FONT_SCALE)

private fun <T> connectedButtonSpring() =
    spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SettingsPreview() {
    NodeSeekTheme {
        SettingsScreen(
            state = SettingsUiState(),
            onBack = {},
            onThemeModeChange = {},
            onFontScaleChange = {},
            onImagesOnWifiOnlyChange = {},
            onClearCache = {},
        )
    }
}
