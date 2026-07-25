package io.github.nsreader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nsreader.R
import io.github.nsreader.data.settings.SettingsRepository
import io.github.nsreader.data.settings.ThemeMode
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.readableWidth

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
    var fontScale by remember(state.settings.fontScale) {
        mutableFloatStateOf(state.settings.fontScale)
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
                        (16 * state.settings.fontScale).toInt(),
                    ),
                    bottom = true,
                ) {
                    Slider(
                        value = fontScale,
                        onValueChange = { fontScale = it },
                        onValueChangeFinished = { onFontScaleChange(fontScale) },
                        valueRange =
                        SettingsRepository.MIN_FONT_SCALE..SettingsRepository.MAX_FONT_SCALE,
                        steps = 5,
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
            Surface(
                onClick = { onSelected(mode) },
                modifier = Modifier.weight(1f),
                shape =
                if (isSelected) {
                    RoundedCornerShape(50)
                } else {
                    when (index) {
                        0 -> RoundedCornerShape(20.dp, 5.dp, 5.dp, 20.dp)
                        choices.lastIndex -> RoundedCornerShape(5.dp, 20.dp, 20.dp, 5.dp)
                        else -> RoundedCornerShape(5.dp)
                    }
                },
                color =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor =
                if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 11.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.width(16.dp))
                    }
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs),
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

@Composable
private fun SettingsBlock(
    title: String,
    top: Boolean = false,
    bottom: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = expressiveGroupShape(top, bottom),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.invoke()
                Column(
                    modifier = Modifier.weight(1f).padding(start = if (icon == null) 0.dp else Spacing.md),
                ) {
                    Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    top: Boolean = false,
    bottom: Boolean = false,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = expressiveGroupShape(top, bottom),
        modifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing()
        }
    }
}

private fun expressiveGroupShape(top: Boolean, bottom: Boolean) =
    RoundedCornerShape(
        topStart = if (top) 18.dp else 5.dp,
        topEnd = if (top) 18.dp else 5.dp,
        bottomEnd = if (bottom) 18.dp else 5.dp,
        bottomStart = if (bottom) 18.dp else 5.dp,
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
