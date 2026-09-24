package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.notifications_interactions
import io.github.nodyssey.ui.resources.notifications_messages
import io.github.nodyssey.ui.resources.notify_channels_section
import io.github.nodyssey.ui.resources.notify_check_section
import io.github.nodyssey.ui.resources.notify_frequency
import io.github.nodyssey.ui.resources.notify_frequency_15
import io.github.nodyssey.ui.resources.notify_frequency_30
import io.github.nodyssey.ui.resources.notify_frequency_60
import io.github.nodyssey.ui.resources.notify_frequency_every
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
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.GroupedListItemSwitch
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.groupedListItemColors
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.ControlShape
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.StringResource
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
        onNotifyInteractionsChange = viewModel::setNotifyInteractions,
        onNotifyMessagesChange = viewModel::setNotifyMessages,
        onOpenTelegram = onOpenTelegram,
        modifier = modifier,
    )
}

/**
 * Board f4, in 6d's cards: the master switch on a tinted card of its own, then 检查 and 通知渠道, then
 * the Telegram pointer.
 *
 * 检查频率 is a row with its answer on the second line and the three choices in a sheet (6d2) rather
 * than a segmented control in the card: it is set once, and the sheet has the room to say what a
 * shorter interval costs.
 */
@Composable
fun NotificationSettingsScreen(
    settings: UserSettings,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onPollMinutesChange: (Int) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onQuietHoursChange: (Boolean) -> Unit,
    onNotifyInteractionsChange: (Boolean) -> Unit,
    onNotifyMessagesChange: (Boolean) -> Unit,
    onOpenTelegram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = settings.notificationsEnabled
    val appBarState = rememberOneHandAppBarState()
    var frequencySheet by remember { mutableStateOf(false) }
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
                .padding(SettingsPagePadding),
            verticalArrangement = Arrangement.spacedBy(SettingsItemGap),
        ) {
            // The master switch as 6d draws it: a group of its own in the primary container tone, larger
            // type than the rows under it, because everything else on the page is conditional on it.
            val scheme = MaterialTheme.colorScheme
            GroupedListItem(
                first = true,
                last = true,
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = groupedListItemColors(scheme.onPrimaryContainer, scheme.primaryContainer),
                headlineContent = {
                    Text(
                        stringResource(Res.string.notify_master_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
                    )
                },
                supportingContent = {
                    Text(
                        stringResource(Res.string.notify_master_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onPrimaryContainer,
                    )
                },
                trailingContent = { GroupedListItemSwitch(checked = enabled) },
            )

            // Everything below the master switch is inert while it is off — f4's "主开关关闭时下方
            // 全部禁用". Each control takes `enabled` and dims itself the way Material dims it; an
            // alpha over the block on top of that would dim the text twice and turn the cards
            // translucent over the page.
            Column(verticalArrangement = Arrangement.spacedBy(SettingsItemGap)) {
                SectionLabel(stringResource(Res.string.notify_check_section))
                SettingsGroup {
                    SettingsRow(
                        leading = { Icon(PlazaIcons.Schedule, contentDescription = null) },
                        title = stringResource(Res.string.notify_frequency),
                        subtitle =
                        stringResource(
                            Res.string.notify_frequency_every,
                            stringResource(pollMinutesLabel(settings.notificationPollMinutes)),
                        ),
                        top = true,
                        enabled = enabled,
                        onClick = { frequencySheet = true },
                        chevron = true,
                    )
                    SettingsRow(
                        leading = { Icon(PlazaIcons.Wifi, contentDescription = null) },
                        title = stringResource(Res.string.notify_wifi_only),
                        subtitle = stringResource(Res.string.notify_wifi_only_hint),
                        checked = settings.notificationsWifiOnly,
                        onCheckedChange = onWifiOnlyChange,
                        enabled = enabled,
                        trailing = { GroupedListItemSwitch(checked = settings.notificationsWifiOnly, enabled = enabled) },
                    )
                    SettingsRow(
                        leading = { Icon(PlazaIcons.Bedtime, contentDescription = null) },
                        title = stringResource(Res.string.notify_quiet_hours),
                        subtitle = stringResource(Res.string.notify_quiet_hours_hint),
                        bottom = true,
                        checked = settings.notificationQuietHours,
                        onCheckedChange = onQuietHoursChange,
                        enabled = enabled,
                        trailing = { GroupedListItemSwitch(checked = settings.notificationQuietHours, enabled = enabled) },
                    )
                }

                SectionLabel(stringResource(Res.string.notify_channels_section))
                SettingsGroup {
                    // One row for the two site groups, because one comment is filed under both and
                    // the app posts one notification for it — see `NotificationChannels`.
                    SettingsRow(
                        title = stringResource(Res.string.notifications_interactions),
                        top = true,
                        leading = { Icon(PlazaIcons.AlternateEmail, contentDescription = null) },
                        checked = settings.notifyInteractions,
                        onCheckedChange = onNotifyInteractionsChange,
                        enabled = enabled,
                        trailing = { GroupedListItemSwitch(checked = settings.notifyInteractions, enabled = enabled) },
                    )
                    SettingsRow(
                        title = stringResource(Res.string.notifications_messages),
                        bottom = true,
                        leading = { Icon(Icons.Default.Email, contentDescription = null) },
                        checked = settings.notifyMessages,
                        onCheckedChange = onNotifyMessagesChange,
                        enabled = enabled,
                        trailing = { GroupedListItemSwitch(checked = settings.notifyMessages, enabled = enabled) },
                    )
                }
            }

            // The Telegram pointer stays active regardless of the master switch: the site's own
            // channel is the alternative to polling, not part of it.
            TelegramCard(onClick = onOpenTelegram)
        }
    }

    if (frequencySheet) {
        FrequencySheet(
            selected = settings.notificationPollMinutes,
            onSelect = {
                onPollMinutesChange(it)
                frequencySheet = false
            },
            onDismiss = { frequencySheet = false },
        )
    }
}

/** 想要即时提醒？ — a card rather than a row, because it points out of this page and out of polling. */
@Composable
private fun TelegramCard(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    LayerCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Surface(
                color = scheme.tertiaryContainer,
                contentColor = scheme.onTertiaryContainer,
                shape = ControlShape,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(Res.string.notify_telegram_title),
                    style = settingsRowTitleStyle().copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    stringResource(Res.string.notify_telegram_body),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(Res.string.notify_telegram_action),
                color = scheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * 6d2 — the three intervals as radio rows in a sheet, with the cost of a short one said above them.
 *
 * A pick applies and closes in the same tap: the sheet is one question with three answers, and a
 * confirm button would make the reader answer it twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrequencySheet(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LocalPlazaLayers.current.page,
    ) {
        Column(
            modifier =
            Modifier
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(Res.string.notify_frequency),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                stringResource(Res.string.notify_frequency_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm),
            )
            Column(Modifier.selectableGroup()) {
                SettingsRepository.POLL_MINUTE_CHOICES.forEach { minutes ->
                    val isSelected = minutes == selected
                    Row(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(minutes) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Text(
                            stringResource(
                                Res.string.notify_frequency_every,
                                stringResource(pollMinutesLabel(minutes)),
                            ),
                            style =
                            settingsRowTitleStyle().copy(
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** The three intervals the store accepts, by name. Anything else is a value this build never wrote. */
private fun pollMinutesLabel(minutes: Int): StringResource =
    when (minutes) {
        15 -> Res.string.notify_frequency_15
        60 -> Res.string.notify_frequency_60
        else -> Res.string.notify_frequency_30
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
            onNotifyInteractionsChange = {},
            onNotifyMessagesChange = {},
            onOpenTelegram = {},
        )
    }
}
