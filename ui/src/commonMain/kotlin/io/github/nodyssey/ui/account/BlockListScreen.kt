package io.github.nodyssey.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.account.BlockedUser
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.account_block_add_action
import io.github.nodyssey.ui.resources.account_block_add_label
import io.github.nodyssey.ui.resources.account_block_add_placeholder
import io.github.nodyssey.ui.resources.account_block_empty
import io.github.nodyssey.ui.resources.account_block_footer
import io.github.nodyssey.ui.resources.account_block_row_hint
import io.github.nodyssey.ui.resources.account_block_section
import io.github.nodyssey.ui.resources.account_block_show_temporarily
import io.github.nodyssey.ui.resources.account_block_show_temporarily_hint
import io.github.nodyssey.ui.resources.account_block_title
import io.github.nodyssey.ui.resources.account_block_unblock
import io.github.nodyssey.ui.resources.account_blocked_count
import io.github.nodyssey.ui.resources.account_confirm_unblock_action
import io.github.nodyssey.ui.resources.account_confirm_unblock_body
import io.github.nodyssey.ui.resources.account_confirm_unblock_title
import io.github.nodyssey.ui.resources.action_back
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.listAvatarSize
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

@Composable
fun BlockListRoute(
    viewModel: BlockListViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    /** Clears a Cloudflare challenge, then comes back to this page. */
    onVerify: () -> Unit,
    onOpenUser: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    AccountMessageSnackbar(
        message = state.message,
        snackbarHostState = snackbarHostState,
        onShown = viewModel::consumeMessage,
        onSignIn = onSignIn,
        onVerify = onVerify,
    )

    BlockListScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onShowBlockedChange = viewModel::setShowBlockedContent,
        onNameChange = viewModel::onNameInputChange,
        onBlock = viewModel::block,
        onRequestUnblock = viewModel::requestUnblock,
        onDismissUnblock = viewModel::dismissUnblock,
        onConfirmUnblock = viewModel::confirmUnblock,
        onOpenUser = onOpenUser,
        modifier = modifier,
    )
}

/**
 * 屏蔽用户 (d6 4/5): the reveal switch on top, then the account's blocked list.
 *
 * The list is Remote and badged as such — blocking is account state, and it is the *server* that
 * decides which posts and comments arrive marked. The switch below it is the one device-side control
 * on the page: it unhides what is already downloaded, and its subtitle says out loud that the reveal
 * ends with the app. That promise is what makes it safe to flip out of curiosity, and it keeps the
 * site's own wording for the feature rather than inventing a scarier or softer version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockListScreen(
    state: BlockListUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onShowBlockedChange: (Boolean) -> Unit,
    onNameChange: (String) -> Unit,
    onBlock: () -> Unit,
    onRequestUnblock: (BlockedUser) -> Unit,
    onDismissUnblock: () -> Unit,
    onConfirmUnblock: () -> Unit,
    onOpenUser: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appBarState = rememberOneHandAppBarState()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.account_block_title),
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
            ShowBlockedSwitchCard(
                checked = state.showBlockedContent,
                onCheckedChange = onShowBlockedChange,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                AccountSectionLabel(text = stringResource(Res.string.account_block_section))
                StorageBadge(local = false)
                Spacer(Modifier.weight(1f))
                if (state.blocked.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.account_blocked_count, state.blocked.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AddBlockField(
                name = state.nameInput,
                isBlocking = state.isBlocking,
                onNameChange = onNameChange,
                onBlock = onBlock,
            )

            if (!state.isLoading && state.blocked.isEmpty()) {
                BlockedEmptyState()
            } else {
                Column {
                    state.blocked.forEachIndexed { index, user ->
                        BlockedRow(
                            user = user,
                            onOpen = { onOpenUser(user.uid) },
                            onUnblock = { onRequestUnblock(user) },
                        )
                        if (index != state.blocked.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                Text(
                    stringResource(Res.string.account_block_footer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
        }
    }

    state.unblocking?.let { target ->
        HighRiskDialog(
            icon = PlazaIcons.Block,
            title = stringResource(Res.string.account_confirm_unblock_title, target.name),
            body = stringResource(Res.string.account_confirm_unblock_body),
            confirmLabel = stringResource(Res.string.account_confirm_unblock_action),
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
        modifier =
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
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
                        stringResource(Res.string.account_block_show_temporarily),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    )
                    StorageBadge(local = true)
                }
                Text(
                    stringResource(Res.string.account_block_show_temporarily_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/**
 * 添加屏蔽, by username — the only handle `/api/block-list/add` accepts.
 *
 * The field is cleared only once the site has accepted the name. A refusal ("用户不存在") comes back as
 * the site's own sentence in the snackbar, and the reader should not have to retype what they typed.
 */
@Composable
private fun AddBlockField(
    name: String,
    isBlocking: Boolean,
    onNameChange: (String) -> Unit,
    onBlock: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        if (name.isNotBlank() && !isBlocking) {
            keyboard?.hide()
            onBlock()
        }
    }

    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !isBlocking,
        label = { Text(stringResource(Res.string.account_block_add_label)) },
        placeholder = { Text(stringResource(Res.string.account_block_add_placeholder)) },
        shape = AccountFieldShape,
        keyboardOptions =
        KeyboardOptions(
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            TextButton(onClick = submit, enabled = name.isNotBlank() && !isBlocking) {
                Text(stringResource(Res.string.account_block_add_action))
            }
        },
    )
}

@Composable
private fun BlockedRow(
    user: BlockedUser,
    onOpen: () -> Unit,
    onUnblock: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        UserAvatar(url = user.avatarUrl, name = user.name, size = listAvatarSize())
        Column(Modifier.weight(1f)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(Res.string.account_block_row_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onUnblock) {
            Text(stringResource(Res.string.account_block_unblock))
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
                PlazaIcons.VisibilityOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(Res.string.account_block_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun BlockListPreview() {
    PlazaTheme {
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
            onNameChange = {},
            onBlock = {},
            onRequestUnblock = {},
            onDismissUnblock = {},
            onConfirmUnblock = {},
            onOpenUser = {},
        )
    }
}
