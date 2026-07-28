package io.github.nsreader.ui.account

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
import io.github.nsreader.R
import io.github.nsreader.data.account.AccountProfileFields
import io.github.nsreader.data.account.TelegramBinding
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.common.UserAvatar
import io.github.nsreader.ui.settings.SettingsGroup
import io.github.nsreader.ui.settings.SettingsRow
import io.github.nsreader.ui.settings.SettingsSectionTitle
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.readableWidth

@Composable
fun AccountSettingsRoute(
    viewModel: AccountSettingsViewModel,
    onBack: () -> Unit,
    onOpenProfileFields: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenContact: () -> Unit,
    onOpenBlockList: () -> Unit,
    onOpenPreferences: () -> Unit,
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
        onSignOut = viewModel::signOut,
        modifier = modifier,
    )
}

/**
 * 账号设置 (8g) — the seven groups NodeSeek's own `/setting` page is split into.
 *
 * Every group is a link to a page, and none of the high-risk ones gets a switch. That is the whole
 * design: 修改密码, 两步验证 and 邮箱 are one mis-tap away from locking the user out of their own
 * account, so each is a page you have to travel to and a dialog you have to read. The subtitle on
 * each row is the current value, which the site's own page does not show — it is a plain list of
 * anchors — and is the one thing this screen adds over opening `/setting` in a browser.
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
            SettingsSectionTitle(stringResource(R.string.account_group_introduction))
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.account_avatar),
                    top = true,
                    onClick = onOpenProfileFields,
                    leading = { RowIcon(Icons.Default.AccountCircle) },
                    trailing = {
                        UserAvatar(
                            url = state.avatarUrl,
                            name = state.displayName,
                            size = 30.dp,
                        )
                        Chevron()
                    },
                )
                SettingsRow(
                    title = stringResource(R.string.account_bio),
                    subtitle = state.fields?.bio?.ifBlank { stringResource(R.string.account_bio_empty) },
                    onClick = onOpenProfileFields,
                    leading = { RowIcon(NodeSeekIcons.Badge) },
                    trailing = { Chevron() },
                )
                SettingsRow(
                    title = stringResource(R.string.account_signature),
                    subtitle =
                    state.fields?.let {
                        stringResource(R.string.account_signature_lines, it.signature.lineCount())
                    },
                    onClick = onOpenProfileFields,
                    leading = { RowIcon(NodeSeekIcons.Edit) },
                    trailing = { Chevron() },
                )
                SettingsRow(
                    title = stringResource(R.string.account_readme),
                    subtitle =
                    state.fields?.let {
                        stringResource(R.string.account_readme_chars, it.readme.length)
                    },
                    bottom = true,
                    onClick = onOpenProfileFields,
                    leading = { RowIcon(NodeSeekIcons.Article) },
                    trailing = { Chevron() },
                )
            }

            SettingsSectionTitle(stringResource(R.string.account_group_security))
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.account_change_password),
                    top = true,
                    bottom = true,
                    onClick = onOpenSecurity,
                    leading = { RowIcon(Icons.Default.Lock) },
                    trailing = { Chevron() },
                )
            }

            SettingsSectionTitle(stringResource(R.string.account_group_2fa))
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.account_two_factor),
                    subtitle =
                    state.twoFactorEnabled?.let {
                        stringResource(
                            if (it) R.string.account_two_factor_on else R.string.account_two_factor_off,
                        )
                    },
                    top = true,
                    bottom = true,
                    onClick = onOpenSecurity,
                    leading = { RowIcon(NodeSeekIcons.Shield) },
                    trailing = { Chevron() },
                )
            }

            SettingsSectionTitle(stringResource(R.string.account_group_contact))
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.account_email),
                    subtitle = state.email?.maskEmail(),
                    top = true,
                    onClick = onOpenContact,
                    leading = { RowIcon(Icons.Default.Email) },
                    trailing = { Chevron() },
                )
                SettingsRow(
                    title = stringResource(R.string.account_telegram_title),
                    subtitle =
                    state.telegram?.let { binding ->
                        if (binding.bound) {
                            binding.displayName
                                ?: stringResource(R.string.account_telegram_bound)
                        } else {
                            stringResource(R.string.account_telegram_unbound)
                        }
                    },
                    bottom = true,
                    onClick = onOpenContact,
                    leading = { RowIcon(NodeSeekIcons.Send) },
                    trailing = { Chevron() },
                )
            }

            SettingsSectionTitle(stringResource(R.string.account_group_block))
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.account_blocked_list),
                    subtitle =
                    state.blockedCount?.let { stringResource(R.string.account_blocked_count, it) },
                    top = true,
                    bottom = true,
                    onClick = onOpenBlockList,
                    leading = { RowIcon(NodeSeekIcons.Block) },
                    trailing = { Chevron() },
                )
            }

            SettingsSectionTitle(stringResource(R.string.account_group_preference))
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.account_display_preferences),
                    subtitle =
                    stringResource(
                        if (state.holidayTheme) {
                            R.string.account_holiday_theme_on
                        } else {
                            R.string.account_holiday_theme_off
                        },
                    ),
                    top = true,
                    bottom = true,
                    onClick = onOpenPreferences,
                    leading = { RowIcon(Icons.Default.Settings) },
                    trailing = { Chevron() },
                )
            }

            SettingsSectionTitle(stringResource(R.string.account_group_homepage))
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.account_home_boards),
                    subtitle =
                    if (state.hiddenBoardCount > 0) {
                        stringResource(R.string.account_home_boards_hidden, state.hiddenBoardCount)
                    } else {
                        stringResource(R.string.account_home_boards_all_shown)
                    },
                    top = true,
                    bottom = true,
                    onClick = onOpenPreferences,
                    leading = { RowIcon(NodeSeekIcons.DashboardCustomize) },
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

/** A signature is stored as Markdown; the row reports how many lines it renders as, not its length. */
private fun String.lineCount(): Int = if (isBlank()) 0 else count { it == '\n' } + 1

/**
 * `hikari.zhg@gmail.com` becomes `h***@gmail.com`.
 *
 * The subtitle is enough to recognise which address is on file without printing it in full on a screen
 * that gets handed around or screenshotted; the sub-page shows the whole thing.
 */
private fun String.maskEmail(): String {
    val at = indexOf('@')
    if (at <= 0) return this
    return "${first()}***${substring(at)}"
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun AccountSettingsPreview() {
    NodeSeekTheme {
        AccountSettingsScreen(
            state =
            AccountSettingsUiState(
                displayName = "花间一壶酒",
                fields =
                AccountProfileFields(
                    bio = "常驻杭州",
                    signature = "**出杭州腾讯云轻量** · 长期收闲置小鸡",
                    readme = "### 关于我\n爱折腾的 MJJ 一枚，主力小鸡在 HK。",
                ),
                twoFactorEnabled = false,
                email = "hikari.zhg@gmail.com",
                telegram = TelegramBinding(bound = false),
                blockedCount = 3,
                hiddenBoardCount = 2,
            ),
            onBack = {},
            onOpenProfileFields = {},
            onOpenSecurity = {},
            onOpenContact = {},
            onOpenBlockList = {},
            onOpenPreferences = {},
            onSignOut = {},
        )
    }
}
