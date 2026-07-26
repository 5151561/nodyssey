package io.github.nsreader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nsreader.R
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.ui.common.GroupedColumn
import io.github.nsreader.ui.common.GroupedRow
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.common.SectionLabel
import io.github.nsreader.ui.common.UserAvatar
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.readableWidth

@Composable
fun AccountSettingsRoute(
    viewModel: AccountSettingsViewModel,
    onBack: () -> Unit,
    onOpenSetting: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccountSettingsScreen(
        state = state,
        onBack = onBack,
        onOpenSetting = onOpenSetting,
        onSignOut = viewModel::signOut,
        modifier = modifier,
    )
}

/**
 * 账号设置 — the seven groups `/setting` has, in its order.
 *
 * Every row opens the site's own page at the matching hash instead of a native form. That is a decision,
 * not a shortcut: these rows change a password, enrol two-factor, edit contact details and unblock
 * people, and NodeSeek exposes no API for any of them. A native form would mean guessing at field names
 * and submitting credential changes we cannot verify — the one place in this app where a wrong guess is
 * worse than a web view.
 *
 * App-side display preferences stay in [SettingsScreen]; this screen never mixes the two, so "where do I
 * change this" has one answer per setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    state: AccountSettingsUiState,
    onBack: () -> Unit,
    onOpenSetting: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_settings_title)) },
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
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel(stringResource(R.string.account_group_profile))
            GroupedColumn {
                GroupedRow(
                    title = stringResource(R.string.account_avatar),
                    icon = Icons.Default.AccountCircle,
                    first = true,
                    onClick = { onOpenSetting(NodeSeekSite.SETTING_INTRODUCTION) },
                    trailing = {
                        UserAvatar(url = state.avatarUrl, name = state.name, size = 30.dp)
                    },
                )
                GroupedRow(
                    title = stringResource(R.string.account_bio),
                    icon = Icons.Default.Person,
                    value = state.bio,
                    onClick = { onOpenSetting(NodeSeekSite.SETTING_INTRODUCTION) },
                )
                GroupedRow(
                    title = stringResource(R.string.account_signature),
                    icon = Icons.Default.Create,
                    value = stringResource(R.string.account_signature_value),
                    onClick = { onOpenSetting(NodeSeekSite.SETTING_INTRODUCTION) },
                )
                GroupedRow(
                    title = stringResource(R.string.account_readme),
                    icon = Icons.Default.List,
                    value = state.readmeLength?.let { stringResource(R.string.account_readme_value, it) },
                    last = true,
                    onClick = { onOpenSetting(NodeSeekSite.SETTING_INTRODUCTION) },
                )
            }

            SectionLabel(stringResource(R.string.account_group_security))
            GroupedRow(
                title = stringResource(R.string.account_password),
                icon = Icons.Default.Lock,
                first = true,
                last = true,
                onClick = { onOpenSetting(NodeSeekSite.SETTING_SECURITY) },
            )

            SectionLabel(stringResource(R.string.account_group_two_factor))
            GroupedRow(
                title = stringResource(R.string.account_two_factor),
                icon = NodeSeekIcons.Shield,
                first = true,
                last = true,
                onClick = { onOpenSetting(NodeSeekSite.SETTING_TWO_FACTOR) },
            )

            SectionLabel(stringResource(R.string.account_group_contact))
            GroupedRow(
                title = stringResource(R.string.account_email),
                icon = Icons.Default.Email,
                first = true,
                last = true,
                onClick = { onOpenSetting(NodeSeekSite.SETTING_CONTACT) },
            )

            SectionLabel(stringResource(R.string.account_group_block))
            GroupedRow(
                title = stringResource(R.string.account_block_list),
                icon = NodeSeekIcons.Block,
                first = true,
                last = true,
                onClick = { onOpenSetting(NodeSeekSite.SETTING_BLOCK) },
            )

            SectionLabel(stringResource(R.string.account_group_preference))
            GroupedRow(
                title = stringResource(R.string.account_preference),
                icon = Icons.Default.Settings,
                first = true,
                last = true,
                onClick = { onOpenSetting(NodeSeekSite.SETTING_PREFERENCE) },
            )

            SectionLabel(stringResource(R.string.account_group_homepage))
            GroupedRow(
                title = stringResource(R.string.account_homepage_boards),
                icon = Icons.Default.Home,
                first = true,
                last = true,
                onClick = { onOpenSetting(NodeSeekSite.SETTING_HOMEPAGE) },
            )

            Text(
                text = stringResource(R.string.account_web_only),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.sm),
            )

            GroupedRow(
                title = stringResource(R.string.action_sign_out),
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                iconTint = MaterialTheme.colorScheme.error,
                titleColor = MaterialTheme.colorScheme.error,
                first = true,
                last = true,
                onClick = onSignOut,
                showChevron = false,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8g 账号设置")
@Composable
private fun AccountSettingsPreview() {
    NodeSeekTheme {
        AccountSettingsScreen(
            state =
            AccountSettingsUiState(
                name = "花田错不错",
                bio = "常驻杭州",
                readmeLength = 238,
            ),
            onBack = {},
            onOpenSetting = {},
            onSignOut = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8g 账号设置 · dark")
@Composable
private fun AccountSettingsDarkPreview() {
    NodeSeekTheme(darkTheme = true) {
        AccountSettingsScreen(
            state = AccountSettingsUiState(name = "花田错不错"),
            onBack = {},
            onOpenSetting = {},
            onSignOut = {},
        )
    }
}
