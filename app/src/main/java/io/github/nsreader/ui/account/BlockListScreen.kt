package io.github.nsreader.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nsreader.R
import io.github.nsreader.data.account.BlockedUser
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.common.UserAvatar
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Sizes
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.readableWidth

@Composable
fun BlockListRoute(
    viewModel: BlockListViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { accountMessageText(it) }

    LaunchedEffect(state.message, messageText) {
        if (messageText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(messageText)
        viewModel.consumeMessage()
    }

    BlockListScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onShowBlockedChange = viewModel::setShowBlockedContent,
        onRequestUnblock = viewModel::requestUnblock,
        onDismissUnblock = viewModel::dismissUnblock,
        onConfirmUnblock = viewModel::confirmUnblock,
        modifier = modifier,
    )
}

/**
 * 屏蔽用户 (d6 4/5): the session-scoped reveal switch on top, then the site's blocked list.
 *
 * The switch's subtitle says out loud that the reveal ends with the app — that promise is what makes
 * it safe to flip out of curiosity, and it is the app keeping the site's own wording for the feature
 * rather than inventing a scarier or softer version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockListScreen(
    state: BlockListUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onShowBlockedChange: (Boolean) -> Unit,
    onRequestUnblock: (BlockedUser) -> Unit,
    onDismissUnblock: () -> Unit,
    onConfirmUnblock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_block_title)) },
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
            if (state.endpointPending) EndpointPendingBanner()

            ShowBlockedSwitchCard(
                checked = state.showBlockedContent,
                onCheckedChange = onShowBlockedChange,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountSectionLabel(
                    text = stringResource(R.string.account_block_section),
                    modifier = Modifier.weight(1f),
                )
                if (state.blocked.isNotEmpty()) {
                    Text(
                        stringResource(R.string.account_blocked_count, state.blocked.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!state.isLoading && state.blocked.isEmpty()) {
                BlockedEmptyState()
            } else {
                Column {
                    state.blocked.forEachIndexed { index, user ->
                        BlockedRow(user = user, onUnblock = { onRequestUnblock(user) })
                        if (index != state.blocked.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                Text(
                    stringResource(R.string.account_block_footer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
        }
    }

    state.unblocking?.let { target ->
        HighRiskDialog(
            icon = NodeSeekIcons.Block,
            title = stringResource(R.string.account_confirm_unblock_title, target.name),
            body = stringResource(R.string.account_confirm_unblock_body),
            confirmLabel = stringResource(R.string.account_confirm_unblock_action),
            onConfirm = onConfirmUnblock,
            onDismiss = onDismissUnblock,
        )
    }
}

@Composable
private fun ShowBlockedSwitchCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        shape = AccountFieldShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        stringResource(R.string.account_block_show_temporarily),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    )
                    StorageBadge(local = true)
                }
                Text(
                    stringResource(R.string.account_block_show_temporarily_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun BlockedRow(
    user: BlockedUser,
    onUnblock: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        UserAvatar(url = user.avatarUrl, name = user.name, size = Sizes.avatarList)
        Column(Modifier.weight(1f)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.account_block_row_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onUnblock) {
            Text(stringResource(R.string.account_block_unblock))
        }
    }
}

@Composable
private fun BlockedEmptyState() {
    Surface(
        shape = AccountFieldShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                NodeSeekIcons.VisibilityOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(R.string.account_block_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun BlockListPreview() {
    NodeSeekTheme {
        BlockListScreen(
            state =
            BlockListUiState(
                isLoading = false,
                blocked =
                listOf(
                    BlockedUser(uid = 1, name = "机场信仰充值中"),
                    BlockedUser(uid = 2, name = "vps_matthew"),
                    BlockedUser(uid = 3, name = "白嫖失败选手"),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onShowBlockedChange = {},
            onRequestUnblock = {},
            onDismissUnblock = {},
            onConfirmUnblock = {},
        )
    }
}
