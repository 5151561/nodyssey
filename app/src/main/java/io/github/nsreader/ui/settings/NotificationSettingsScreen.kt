package io.github.nsreader.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nsreader.R
import io.github.nsreader.data.settings.SettingsRepository
import io.github.nsreader.data.settings.UserSettings
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.readableWidth

@Composable
fun NotificationSettingsRoute(
    viewModel: NotificationSettingsViewModel,
    onBack: () -> Unit,
    onOpenTelegram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    // The launcher exists for its side effect: Android 13+ will not show anything the user just
    // asked for until POST_NOTIFICATIONS is granted. A denial leaves polling on — the badge still
    // updates — so the setting and the permission stay independent.
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    NotificationSettingsScreen(
        settings = settings,
        onBack = onBack,
        onEnabledChange = { enabled ->
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            viewModel.setEnabled(enabled)
        },
        onPollMinutesChange = viewModel::setPollMinutes,
        onWifiOnlyChange = viewModel::setWifiOnly,
        onQuietHoursChange = viewModel::setQuietHours,
        onNotifyMentionsChange = viewModel::setNotifyMentions,
        onNotifyRepliesChange = viewModel::setNotifyReplies,
        onNotifyMessagesChange = viewModel::setNotifyMessages,
        onOpenTelegram = onOpenTelegram,
        modifier = modifier,
    )
}

/** Board f4: master switch, poll frequency, Wi-Fi and quiet-hours guards, per-channel switches. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    settings: UserSettings,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onPollMinutesChange: (Int) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onQuietHoursChange: (Boolean) -> Unit,
    onNotifyMentionsChange: (Boolean) -> Unit,
    onNotifyRepliesChange: (Boolean) -> Unit,
    onNotifyMessagesChange: (Boolean) -> Unit,
    onOpenTelegram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = settings.notificationsEnabled
    Scaffold(
        modifier = modifier,
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.notify_settings_title)) },
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
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.notify_master_title),
                    subtitle = stringResource(R.string.notify_master_hint),
                    top = true,
                    bottom = true,
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    trailing = { Switch(checked = enabled, onCheckedChange = null) },
                )
            }

            // Everything below the master switch is one dimmed, inert block while it is off —
            // f4's "主开关关闭时下方全部禁用".
            Column(
                modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SettingsGroup {
                    SettingsBlock(
                        title = stringResource(R.string.notify_frequency),
                        top = true,
                        bottom = true,
                    ) {
                        val choices = SettingsRepository.POLL_MINUTE_CHOICES
                        ConnectedChoiceButtons(
                            labels =
                            listOf(
                                stringResource(R.string.notify_frequency_15),
                                stringResource(R.string.notify_frequency_30),
                                stringResource(R.string.notify_frequency_60),
                            ),
                            selectedIndex = choices.indexOf(settings.notificationPollMinutes),
                            onSelect = { onPollMinutesChange(choices[it]) },
                            enabled = enabled,
                        )
                        Text(
                            stringResource(R.string.notify_frequency_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SettingsGroup {
                    SettingsRow(
                        title = stringResource(R.string.notify_wifi_only),
                        subtitle = stringResource(R.string.notify_wifi_only_hint),
                        top = true,
                        checked = settings.notificationsWifiOnly,
                        onCheckedChange = onWifiOnlyChange,
                        enabled = enabled,
                        trailing = {
                            Switch(
                                checked = settings.notificationsWifiOnly,
                                onCheckedChange = null,
                                enabled = enabled,
                            )
                        },
                    )
                    SettingsRow(
                        title = stringResource(R.string.notify_quiet_hours),
                        subtitle = stringResource(R.string.notify_quiet_hours_hint),
                        bottom = true,
                        checked = settings.notificationQuietHours,
                        onCheckedChange = onQuietHoursChange,
                        enabled = enabled,
                        trailing = {
                            Switch(
                                checked = settings.notificationQuietHours,
                                onCheckedChange = null,
                                enabled = enabled,
                            )
                        },
                    )
                }

                SettingsSectionTitle(stringResource(R.string.notify_channels_section))
                SettingsGroup {
                    SettingsRow(
                        title = stringResource(R.string.notifications_mentions),
                        top = true,
                        leading = { Icon(NodeSeekIcons.AlternateEmail, contentDescription = null) },
                        checked = settings.notifyMentions,
                        onCheckedChange = onNotifyMentionsChange,
                        enabled = enabled,
                        trailing = {
                            Switch(
                                checked = settings.notifyMentions,
                                onCheckedChange = null,
                                enabled = enabled,
                            )
                        },
                    )
                    SettingsRow(
                        title = stringResource(R.string.notifications_replies),
                        leading = { Icon(NodeSeekIcons.ChatBubble, contentDescription = null) },
                        checked = settings.notifyReplies,
                        onCheckedChange = onNotifyRepliesChange,
                        enabled = enabled,
                        trailing = {
                            Switch(
                                checked = settings.notifyReplies,
                                onCheckedChange = null,
                                enabled = enabled,
                            )
                        },
                    )
                    SettingsRow(
                        title = stringResource(R.string.notifications_messages),
                        bottom = true,
                        leading = { Icon(Icons.Default.Email, contentDescription = null) },
                        checked = settings.notifyMessages,
                        onCheckedChange = onNotifyMessagesChange,
                        enabled = enabled,
                        trailing = {
                            Switch(
                                checked = settings.notifyMessages,
                                onCheckedChange = null,
                                enabled = enabled,
                            )
                        },
                    )
                }
            }

            // The Telegram pointer stays active regardless of the master switch: the site's own
            // channel is the alternative to polling, not part of it.
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.notify_telegram_title),
                    subtitle = stringResource(R.string.notify_telegram_body),
                    top = true,
                    bottom = true,
                    onClick = onOpenTelegram,
                    leading = { Icon(NodeSeekIcons.Send, contentDescription = null) },
                    trailing = {
                        Text(
                            stringResource(R.string.notify_telegram_action),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.5f

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun NotificationSettingsPreview() {
    NodeSeekTheme {
        NotificationSettingsScreen(
            settings = UserSettings(notificationsEnabled = true),
            onBack = {},
            onEnabledChange = {},
            onPollMinutesChange = {},
            onWifiOnlyChange = {},
            onQuietHoursChange = {},
            onNotifyMentionsChange = {},
            onNotifyRepliesChange = {},
            onNotifyMessagesChange = {},
            onOpenTelegram = {},
        )
    }
}
