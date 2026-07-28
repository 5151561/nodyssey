package io.github.nodyssey.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.settings.SettingsGroup
import io.github.nodyssey.ui.settings.SettingsRow
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.readableWidth

@Composable
fun AccountSettingsRoute(
    viewModel: AccountSettingsViewModel,
    onBack: () -> Unit,
    onOpenProfileFields: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenContact: () -> Unit,
    onOpenBlockList: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenNodeImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Signing out unmakes this screen: leaving it on the stack would show the account settings of an
    // account nobody is signed in to.
    LaunchedEffect(state.signedOut) {
        if (state.signedOut) onBack()
    }

    AccountSettingsScreen(
        state = state,
        onBack = onBack,
        onOpenProfileFields = onOpenProfileFields,
        onOpenSecurity = onOpenSecurity,
        onOpenContact = onOpenContact,
        onOpenBlockList = onOpenBlockList,
        onOpenPreferences = onOpenPreferences,
        onOpenNodeImage = onOpenNodeImage,
        onSignOut = viewModel::signOut,
        modifier = modifier,
    )
}

/**
 * 账号设置只展示真实的二级页入口。
 *
 * NodeSeek 站点按 hash 将资料、安全等页面内容分成小板块，但 App 中多个小板块会进入
 * 同一个原生页面。主列表因此按 destination 合并，页面内的编辑项不再伪装成多个路由。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    state: AccountSettingsUiState,
    onBack: () -> Unit,
    onOpenProfileFields: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenContact: () -> Unit,
    onOpenBlockList: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenNodeImage: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_title)) },
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
                    title = stringResource(R.string.account_profile_title),
                    subtitle = stringResource(R.string.account_profile_summary),
                    top = true,
                    onClick = onOpenProfileFields,
                    leading = { RowIcon(Icons.Default.AccountCircle) },
                    trailing = { Chevron() },
                )
                SettingsRow(
                    title = stringResource(R.string.account_security_title),
                    subtitle = stringResource(R.string.account_security_summary),
                    onClick = onOpenSecurity,
                    leading = { RowIcon(Icons.Default.Lock) },
                    trailing = { Chevron() },
                )
                SettingsRow(
                    title = stringResource(R.string.account_contact_title),
                    subtitle = stringResource(R.string.account_contact_summary),
                    onClick = onOpenContact,
                    leading = { RowIcon(Icons.Default.Email) },
                    trailing = { Chevron() },
                )
                SettingsRow(
                    title = stringResource(R.string.account_block_title),
                    subtitle =
                    state.blockedCount?.let { stringResource(R.string.account_blocked_count, it) },
                    onClick = onOpenBlockList,
                    leading = { RowIcon(NodysseyIcons.Block) },
                    trailing = { Chevron() },
                )
                SettingsRow(
                    title = stringResource(R.string.account_preferences_title),
                    subtitle = stringResource(R.string.account_preferences_summary),
                    onClick = onOpenPreferences,
                    leading = { RowIcon(Icons.Default.Settings) },
                    trailing = { Chevron() },
                )
                SettingsRow(
                    title = stringResource(R.string.nodeimage_title),
                    subtitle =
                    stringResource(
                        if (state.imageHostConnected) {
                            R.string.nodeimage_connected
                        } else {
                            R.string.nodeimage_not_connected
                        },
                    ),
                    bottom = true,
                    onClick = onOpenNodeImage,
                    leading = { RowIcon(NodysseyIcons.Image) },
                    trailing = { Chevron() },
                )
            }

            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.action_sign_out),
                    top = true,
                    bottom = true,
                    onClick = onSignOut,
                    contentColor = MaterialTheme.colorScheme.error,
                    leading = {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun AccountSettingsPreview() {
    NodysseyTheme {
        AccountSettingsScreen(
            state =
            AccountSettingsUiState(
                blockedCount = 3,
                imageHostConnected = true,
            ),
            onBack = {},
            onOpenProfileFields = {},
            onOpenSecurity = {},
            onOpenContact = {},
            onOpenBlockList = {},
            onOpenPreferences = {},
            onOpenNodeImage = {},
            onSignOut = {},
        )
    }
}
