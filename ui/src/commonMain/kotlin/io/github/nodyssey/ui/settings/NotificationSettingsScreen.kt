package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.notifications_mentions
import io.github.nodyssey.ui.resources.notifications_messages
import io.github.nodyssey.ui.resources.notifications_replies
import io.github.nodyssey.ui.resources.notify_channels_section
import io.github.nodyssey.ui.resources.notify_frequency
import io.github.nodyssey.ui.resources.notify_frequency_15
import io.github.nodyssey.ui.resources.notify_frequency_30
import io.github.nodyssey.ui.resources.notify_frequency_60
import io.github.nodyssey.ui.resources.notify_frequency_hint
import io.github.nodyssey.ui.resources.notify_master_hint
import io.github.nodyssey.ui.resources.notify_master_title
import io.github.nodyssey.ui.resources.notify_quiet_hours
import io.github.nodyssey.ui.resources.notify_quiet_hours_hint
import io.github.nodyssey.ui.resources.notify_settings_title
import io.github.nodyssey.ui.resources.notify_telegram_action
import io.github.nodyssey.ui.resources.notify_telegram_body
import io.github.nodyssey.ui.resources.notify_telegram_title
import io.github.nodyssey.ui.resources.notify_wifi_only
import io.github.nodyssey.ui.resources.notify_wifi_only_hint
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

@Composable
fun NotificationSettingsRoute(
    viewModel: NotificationSettingsViewModel,
    onBack: () -> Unit,
    onOpenTelegram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    NotificationSettingsScreen(
        settings = settings,
        onBack = onBack,
        onEnabledChange = { enabled ->
            if (enabled) requestNotificationPermission()
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
    val appBarState = rememberOneHandAppBarState()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.notify_settings_title),
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
            SettingsGroup {
                SettingsRow(
                    title = stringResource(Res.string.notify_master_title),
                    subtitle = stringResource(Res.string.notify_master_hint),
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
                        title = stringResource(Res.string.notify_frequency),
                        top = true,
                        bottom = true,
                    ) {
                        val choices = SettingsRepository.POLL_MINUTE_CHOICES
                        ConnectedChoiceButtons(
                            labels =
                            listOf(
                                stringResource(Res.string.notify_frequency_15),
                                stringResource(Res.string.notify_frequency_30),
                                stringResource(Res.string.notify_frequency_60),
                            ),
                            selectedIndex = choices.indexOf(settings.notificationPollMinutes),
                            onSelect = { onPollMinutesChange(choices[it]) },
                            enabled = enabled,
                        )
                        Text(
                            stringResource(Res.string.notify_frequency_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SettingsGroup {
                    SettingsRow(
                        title = stringResource(Res.string.notify_wifi_only),
                        subtitle = stringResource(Res.string.notify_wifi_only_hint),
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
                        title = stringResource(Res.string.notify_quiet_hours),
                        subtitle = stringResource(Res.string.notify_quiet_hours_hint),
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

                SettingsSectionTitle(stringResource(Res.string.notify_channels_section))
                SettingsGroup {
                    SettingsRow(
                        title = stringResource(Res.string.notifications_mentions),
                        top = true,
                        leading = { Icon(PlazaIcons.AlternateEmail, contentDescription = null) },
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
                        title = stringResource(Res.string.notifications_replies),
                        leading = { Icon(PlazaIcons.ChatBubble, contentDescription = null) },
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
                        title = stringResource(Res.string.notifications_messages),
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
                    title = stringResource(Res.string.notify_telegram_title),
                    subtitle = stringResource(Res.string.notify_telegram_body),
                    top = true,
                    bottom = true,
                    onClick = onOpenTelegram,
                    leading = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                    trailing = {
                        Text(
                            stringResource(Res.string.notify_telegram_action),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun NotificationSettingsPreview() {
    PlazaTheme {
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
