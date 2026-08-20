package io.github.nodyssey.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.ui.common.AttendanceBoardDialog
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.UpdateDot
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_sign_out
import io.github.nodyssey.ui.resources.assets_signed_in
import io.github.nodyssey.ui.resources.profile_account_settings
import io.github.nodyssey.ui.resources.profile_assets
import io.github.nodyssey.ui.resources.profile_attendance
import io.github.nodyssey.ui.resources.profile_attendance_checking
import io.github.nodyssey.ui.resources.profile_attendance_done
import io.github.nodyssey.ui.resources.profile_chicken
import io.github.nodyssey.ui.resources.profile_collections
import io.github.nodyssey.ui.resources.profile_follow
import io.github.nodyssey.ui.resources.profile_guest_benefit_attendance
import io.github.nodyssey.ui.resources.profile_guest_benefit_attendance_hint
import io.github.nodyssey.ui.resources.profile_guest_benefit_messages
import io.github.nodyssey.ui.resources.profile_guest_benefit_messages_hint
import io.github.nodyssey.ui.resources.profile_guest_benefit_post
import io.github.nodyssey.ui.resources.profile_guest_benefit_post_hint
import io.github.nodyssey.ui.resources.profile_guest_section
import io.github.nodyssey.ui.resources.profile_guest_settings_hint
import io.github.nodyssey.ui.resources.profile_guest_tools_hint
import io.github.nodyssey.ui.resources.profile_history
import io.github.nodyssey.ui.resources.profile_level
import io.github.nodyssey.ui.resources.profile_level_unknown
import io.github.nodyssey.ui.resources.profile_session_active
import io.github.nodyssey.ui.resources.profile_sign_in
import io.github.nodyssey.ui.resources.profile_sign_in_hint
import io.github.nodyssey.ui.resources.profile_signed_out_body
import io.github.nodyssey.ui.resources.profile_signed_out_title
import io.github.nodyssey.ui.resources.profile_space
import io.github.nodyssey.ui.resources.profile_stars
import io.github.nodyssey.ui.resources.profile_tools
import io.github.nodyssey.ui.resources.settings_title
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.component.GroupedColumn
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.StatusShapes
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfileRoute(
    viewModel: ProfileViewModel,
    onSignIn: () -> Unit,
    onSettings: () -> Unit,
    hasAppUpdate: Boolean,
    onAccountSettings: () -> Unit,
    onOpenWebsite: () -> Unit,
    onOpenSpace: (Long) -> Unit,
    /**
     * 我的收藏 — its own screen (board i1).
     *
     * No uid, unlike [onOpenSpace]: the site publishes nobody else's collections, so the destination
     * is the signed-in account's by construction and there is nothing to identify.
     */
    onCollections: () -> Unit,
    onHistory: () -> Unit,
    onAssets: () -> Unit,
    onAttendance: () -> Unit,
    onFollow: () -> Unit,
    onTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnReturnToForeground(viewModel::refreshAttendance)
    ProfileScreen(
        state = state,
        onSignIn = onSignIn,
        onSignOut = viewModel::signOut,
        onRetry = viewModel::refresh,
        onSettings = onSettings,
        hasAppUpdate = hasAppUpdate,
        onAccountSettings = onAccountSettings,
        onOpenWebsite = onOpenWebsite,
        onOpenSpace = { state.uid?.let(onOpenSpace) },
        onCollections = onCollections,
        onHistory = onHistory,
        onAssets = onAssets,
        onAttendance = onAttendance,
        onAttendanceBoard = viewModel::openAttendanceBoard,
        onDismissAttendanceBoard = viewModel::dismissAttendanceBoard,
        onRetryAttendanceBoard = viewModel::loadAttendanceBoard,
        onFollow = onFollow,
        onTools = onTools,
        modifier = modifier,
    )
}

/**
 * Runs [onForeground] when the app comes back to the foreground — and only then.
 *
 * `LifecycleEventEffect(ON_RESUME)` would not do: this entry has no lifecycle of its own (the tabs
 * share the activity's), and `LifecycleRegistry` replays the up-events an already-resumed owner has
 * passed to every observer that joins late. Since 我的 leaves composition on each tab switch and
 * re-enters on the way back, that replay made "returning to the tab" indistinguishable from
 * "returning to the app", and fired the callback on every visit. The replayed event is dropped here;
 * the real transitions after it are the ones worth reacting to.
 */
@Composable
internal fun RefreshOnReturnToForeground(onForeground: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnForeground by rememberUpdatedState(onForeground)
    DisposableEffect(lifecycleOwner) {
        var replay = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer =
            LifecycleEventObserver { _, event ->
                if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
                if (replay) replay = false else currentOnForeground()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
    onAccountSettings: () -> Unit,
    onOpenWebsite: () -> Unit,
    onOpenSpace: () -> Unit,
    onCollections: () -> Unit,
    onHistory: () -> Unit,
    onAssets: () -> Unit,
    onAttendance: () -> Unit,
    onAttendanceBoard: () -> Unit,
    onFollow: () -> Unit,
    onTools: () -> Unit,
    modifier: Modifier = Modifier,
    /** 应用内更新 found something; the 设置 row carries the dot that leads to it. */
    hasAppUpdate: Boolean = false,
    onDismissAttendanceBoard: () -> Unit = {},
    onRetryAttendanceBoard: () -> Unit = {},
) {
    Scaffold(modifier = modifier) { padding ->
        if (!state.isSignedIn) {
            SignedOutProfile(
                onSignIn = onSignIn,
                onSettings = onSettings,
                hasAppUpdate = hasAppUpdate,
                onTools = onTools,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        if (state.isLoading && !state.hasProfile) {
            LoadingState(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        if (state.error != null && !state.hasProfile) {
            SiteErrorState(
                error = state.error,
                onRetry = onRetry,
                onOpenBrowser = onOpenWebsite,
                onSignIn = onSignIn,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier =
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item(key = "profile-header") {
                ProfileHeader(state, onOpenSpace)
            }
            item(key = "resources") {
                ResourceCards(state, onAssets)
            }
            item(key = "attendance") {
                Button(
                    onClick = if (state.hasSignedInToday) onAttendanceBoard else onAttendance,
                    enabled = !state.isAttendanceUnknown,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors =
                    if (state.hasSignedInToday) {
                        ButtonDefaults.filledTonalButtonColors()
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    when {
                        state.isAttendanceUnknown -> {
                            CircularProgressIndicator(Modifier.size(18.dp))
                            Text(
                                stringResource(Res.string.profile_attendance_checking),
                                modifier = Modifier.padding(start = Spacing.sm),
                            )
                        }

                        state.hasSignedInToday -> {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Text(
                                text =
                                state.attendanceGain?.let {
                                    stringResource(Res.string.assets_signed_in, it)
                                } ?: stringResource(Res.string.profile_attendance_done),
                                modifier = Modifier.padding(start = Spacing.sm),
                            )
                        }

                        else -> {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Text(
                                stringResource(Res.string.profile_attendance),
                                modifier = Modifier.padding(start = Spacing.sm),
                            )
                        }
                    }
                }
            }
            // The content menu now points at real screens. 主题帖 / 评论 / 收藏 are the space page's own
            // tabs, so they open it on the right one rather than opening three near-identical screens.
            item(key = "content-menu") {
                ProfileMenuGroup(
                    items =
                    listOf(
                        ProfileMenuItem(Res.string.profile_space, Icons.Default.Person, onOpenSpace),
                        ProfileMenuItem(Res.string.profile_collections, Icons.Default.Star, onCollections),
                        ProfileMenuItem(Res.string.profile_history, PlazaIcons.History, onHistory),
                        ProfileMenuItem(Res.string.profile_follow, PlazaIcons.Group, onFollow),
                        ProfileMenuItem(Res.string.profile_assets, PlazaIcons.Wallet, onAssets),
                        ProfileMenuItem(Res.string.profile_tools, PlazaIcons.MenuBook, onTools),
                    ),
                )
            }
            // No 在网页中打开 row here. It pointed at the site root rather than at 个人主页 like its
            // label claimed, and the real thing already lives where the page it opens is: 个人主页's
            // own top bar has 在浏览器中打开, with that user's space URL.
            item(key = "settings-menu") {
                ProfileMenuGroup(
                    items =
                    listOf(
                        ProfileMenuItem(
                            Res.string.profile_account_settings,
                            PlazaIcons.Badge,
                            onAccountSettings,
                        ),
                        ProfileMenuItem(
                            Res.string.settings_title,
                            Icons.Default.Settings,
                            onSettings,
                            badge = hasAppUpdate,
                        ),
                    ),
                )
            }
            item(key = "sign-out") {
                TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.action_sign_out))
                }
            }
        }
    }

    if (state.boardOpen) {
        AttendanceBoardDialog(
            isLoading = state.isLoadingBoard,
            entries = state.board,
            error = state.boardError,
            onRetry = onRetryAttendanceBoard,
            onDismiss = onDismissAttendanceBoard,
        )
    }
}

/** Board c7: the useful signed-out version of 我的, including the two guest-safe destinations. */
@Composable
private fun SignedOutProfile(
    onSignIn: () -> Unit,
    onSettings: () -> Unit,
    hasAppUpdate: Boolean,
    onTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().readableWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = Spacing.lg),
    ) {
        item(key = "welcome-illustration") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SignedOutIllustration()
            }
        }
        item(key = "welcome-copy") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = stringResource(Res.string.profile_signed_out_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(Res.string.profile_signed_out_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        item(key = "benefits") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SignedOutBenefit(
                    icon = Icons.Default.Create,
                    title = stringResource(Res.string.profile_guest_benefit_post),
                    subtitle = stringResource(Res.string.profile_guest_benefit_post_hint),
                    shape = RoundedCornerShape(18.dp, 5.dp, 5.dp, 18.dp),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
                SignedOutBenefit(
                    icon = PlazaIcons.ChatBubble,
                    title = stringResource(Res.string.profile_guest_benefit_messages),
                    subtitle = stringResource(Res.string.profile_guest_benefit_messages_hint),
                    shape = RoundedCornerShape(5.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                SignedOutBenefit(
                    icon = PlazaIcons.EventAvailable,
                    title = stringResource(Res.string.profile_guest_benefit_attendance),
                    subtitle = stringResource(Res.string.profile_guest_benefit_attendance_hint),
                    shape = RoundedCornerShape(5.dp, 18.dp, 18.dp, 5.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item(key = "sign-in") {
            Button(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(Sizes.minTouchTarget),
                shape = CircleShape,
            ) {
                Icon(PlazaIcons.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(Res.string.profile_sign_in),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            Text(
                text = stringResource(Res.string.profile_sign_in_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
        item(key = "guest-menu") {
            Column(modifier = Modifier.padding(top = 18.dp)) {
                SectionLabel(stringResource(Res.string.profile_guest_section))
                GroupedColumn {
                    GroupedRow(
                        title = stringResource(Res.string.settings_title),
                        subtitle = stringResource(Res.string.profile_guest_settings_hint),
                        first = true,
                        icon = Icons.Default.Settings,
                        onClick = onSettings,
                        // Updating has nothing to do with being signed in, so the guest side of 我的
                        // carries the same dot.
                        trailing = if (hasAppUpdate) {
                            { UpdateDot() }
                        } else {
                            null
                        },
                    )
                    GroupedRow(
                        title = stringResource(Res.string.profile_tools),
                        subtitle = stringResource(Res.string.profile_guest_tools_hint),
                        last = true,
                        icon = PlazaIcons.DashboardCustomize,
                        onClick = onTools,
                    )
                }
            }
        }
    }
}

/** The only custom node in c7: a decorative illustration with no input or navigation semantics. */
@Composable
private fun SignedOutIllustration(modifier: Modifier = Modifier) {
    Box(modifier.width(216.dp).height(148.dp)) {
        Surface(
            modifier = Modifier.offset(x = 50.dp, y = 12.dp).size(118.dp),
            shape = StatusShapes.Welcome,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = PlazaIcons.WavingHand,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                )
            }
        }
        Surface(
            modifier = Modifier.offset(x = 154.dp).size(56.dp),
            shape = StatusShapes.NetworkError,
            color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {}
        Surface(
            modifier = Modifier.offset(x = 8.dp, y = 96.dp).size(42.dp),
            shape = StatusShapes.Empty,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {}
        Surface(
            modifier = Modifier.offset(x = 160.dp, y = 124.dp).size(22.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {}
        Surface(
            modifier = Modifier.offset(x = 26.dp).size(14.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {}
    }
}

@Composable
private fun SignedOutBenefit(
    icon: ImageVector,
    title: String,
    subtitle: String,
    shape: RoundedCornerShape,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(text = title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun ProfileHeader(
    state: ProfileUiState,
    onOpenSpace: () -> Unit,
) {
    // 头像和 ID 本身就是进空间的入口，右边再挂一个编辑按钮只是重复，去掉。
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClickLabel = stringResource(Res.string.profile_space), onClick = onOpenSpace)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        UserAvatar(
            url = state.avatarUrl,
            name = state.displayName,
            size = Sizes.avatarProfile,
            shape = StatusShapes.Welcome,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = state.displayName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.memberSince ?: stringResource(Res.string.profile_session_active),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResourceCards(
    state: ProfileUiState,
    onAssets: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        ResourceCard(
            value = state.chickenCount?.toString() ?: "—",
            label = stringResource(Res.string.profile_chicken),
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp, 5.dp, 5.dp, 18.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onAssets,
        )
        ResourceCard(
            value = state.starCount?.toString() ?: "—",
            label = stringResource(Res.string.profile_stars),
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(5.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onAssets,
        )
        ResourceCard(
            value = state.level ?: stringResource(Res.string.profile_level_unknown),
            label = stringResource(Res.string.profile_level),
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(5.dp, 18.dp, 18.dp, 5.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onAssets,
        )
    }
}

@Composable
private fun ResourceCard(
    value: String,
    label: String,
    shape: RoundedCornerShape,
    color: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = shape,
        color = color,
        contentColor = contentColor,
    ) {
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = 14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private data class ProfileMenuItem(
    val title: StringResource,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val badge: Boolean = false,
)

/** Renders through the shared grouped-list components so 我的 matches the screens it links to. */
@Composable
private fun ProfileMenuGroup(items: List<ProfileMenuItem>) {
    GroupedColumn {
        items.forEachIndexed { index, item ->
            GroupedRow(
                title = stringResource(item.title),
                first = index == 0,
                last = index == items.lastIndex,
                icon = item.icon,
                onClick = item.onClick,
                trailing = if (item.badge) {
                    { UpdateDot() }
                } else {
                    null
                },
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ProfileSignedInPreview() {
    PlazaTheme {
        ProfileScreen(
            state =
            ProfileUiState(
                isSignedIn = true,
                displayName = "nodyssey_dev",
                level = "Lv 3",
                memberSince = "2023年5月 注册 · UID 88423",
                chickenCount = 1_284,
                starCount = 356,
            ),
            onSignIn = {},
            onSignOut = {},
            onRetry = {},
            onSettings = {},
            onAccountSettings = {},
            onOpenWebsite = {},
            onOpenSpace = {},
            onCollections = {},
            onHistory = {},
            onAssets = {},
            onAttendance = {},
            onAttendanceBoard = {},
            onFollow = {},
            onTools = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "c7 我的 · 未登录")
@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 800,
    // `uiMode = UI_MODE_NIGHT_YES` used to be here; that constant is `android.content.res`, and the
    // preview is the only thing in this file that ever named it. The dark variant is one theme
    // parameter away — see the preview body — so the tooling flag is not what it was buying.
    name = "c7 我的 · 未登录 · dark",
)
@Composable
private fun ProfileSignedOutPreview() {
    PlazaTheme {
        ProfileScreen(
            state = ProfileUiState(),
            onSignIn = {},
            onSignOut = {},
            onRetry = {},
            onSettings = {},
            onAccountSettings = {},
            onOpenWebsite = {},
            onOpenSpace = {},
            onCollections = {},
            onHistory = {},
            onAssets = {},
            onAttendance = {},
            onAttendanceBoard = {},
            onFollow = {},
            onTools = {},
        )
    }
}
