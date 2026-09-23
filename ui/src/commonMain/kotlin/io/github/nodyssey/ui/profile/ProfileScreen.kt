package io.github.nodyssey.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.AttendanceMode
import io.github.nodyssey.ui.common.AttendanceBoardDialog
import io.github.nodyssey.ui.common.AttendanceModeDialog
import io.github.nodyssey.ui.common.GrowthProgressBar
import io.github.nodyssey.ui.common.SiteErrorSnackbar
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.UpdateDot
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.common.siteName
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.assets_level_no_threshold
import io.github.nodyssey.ui.resources.assets_quota_value
import io.github.nodyssey.ui.resources.assets_signed_in
import io.github.nodyssey.ui.resources.assets_signing_in
import io.github.nodyssey.ui.resources.profile_attendance_board
import io.github.nodyssey.ui.resources.profile_attendance_checking
import io.github.nodyssey.ui.resources.profile_attendance_done
import io.github.nodyssey.ui.resources.profile_attendance_modes
import io.github.nodyssey.ui.resources.profile_attendance_title
import io.github.nodyssey.ui.resources.profile_chicken
import io.github.nodyssey.ui.resources.profile_guest_benefit_attendance
import io.github.nodyssey.ui.resources.profile_guest_benefit_attendance_hint
import io.github.nodyssey.ui.resources.profile_guest_benefit_messages
import io.github.nodyssey.ui.resources.profile_guest_benefit_messages_hint
import io.github.nodyssey.ui.resources.profile_guest_benefit_post
import io.github.nodyssey.ui.resources.profile_guest_benefit_post_hint
import io.github.nodyssey.ui.resources.profile_guest_section
import io.github.nodyssey.ui.resources.profile_level
import io.github.nodyssey.ui.resources.profile_level_remaining
import io.github.nodyssey.ui.resources.profile_level_unknown
import io.github.nodyssey.ui.resources.profile_member_since
import io.github.nodyssey.ui.resources.profile_member_uid
import io.github.nodyssey.ui.resources.profile_section_assets
import io.github.nodyssey.ui.resources.profile_section_community
import io.github.nodyssey.ui.resources.profile_section_content
import io.github.nodyssey.ui.resources.profile_section_settings
import io.github.nodyssey.ui.resources.profile_session_active
import io.github.nodyssey.ui.resources.profile_sign_in
import io.github.nodyssey.ui.resources.profile_sign_in_hint
import io.github.nodyssey.ui.resources.profile_signed_out_body
import io.github.nodyssey.ui.resources.profile_signed_out_title
import io.github.nodyssey.ui.resources.profile_space
import io.github.nodyssey.ui.resources.profile_stars
import io.github.nodyssey.ui.resources.profile_tile_about
import io.github.nodyssey.ui.resources.profile_tile_about_community
import io.github.nodyssey.ui.resources.profile_tile_account
import io.github.nodyssey.ui.resources.profile_tile_award
import io.github.nodyssey.ui.resources.profile_tile_block
import io.github.nodyssey.ui.resources.profile_tile_collections
import io.github.nodyssey.ui.resources.profile_tile_comments
import io.github.nodyssey.ui.resources.profile_tile_credit
import io.github.nodyssey.ui.resources.profile_tile_followers
import io.github.nodyssey.ui.resources.profile_tile_following
import io.github.nodyssey.ui.resources.profile_tile_friends
import io.github.nodyssey.ui.resources.profile_tile_history
import io.github.nodyssey.ui.resources.profile_tile_invite
import io.github.nodyssey.ui.resources.profile_tile_lucky
import io.github.nodyssey.ui.resources.profile_tile_notifications
import io.github.nodyssey.ui.resources.profile_tile_providers
import io.github.nodyssey.ui.resources.profile_tile_ruling
import io.github.nodyssey.ui.resources.profile_tile_stardust
import io.github.nodyssey.ui.resources.profile_tile_theme
import io.github.nodyssey.ui.resources.profile_tile_topics
import io.github.nodyssey.ui.resources.profile_tile_transfer
import io.github.nodyssey.ui.resources.profile_tools
import io.github.nodyssey.ui.resources.settings_title
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.TonalTile
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.cardShadow
import io.github.plaza.designsys.theme.floatShadow
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfileRoute(
    viewModel: ProfileViewModel,
    destinations: ProfileDestinations,
    onSignIn: () -> Unit,
    hasAppUpdate: Boolean,
    onOpenWebsite: () -> Unit,
    /**
     * Clears a Cloudflare challenge, then returns here.
     *
     * Separate from [onOpenWebsite]: 访问网站 is an invitation to go read the forum and stays open
     * until the reader leaves, while a wall is an errand that ends when the pass arrives. One
     * callback served both, so 去验证 opened the reading web view — which never closes itself and
     * carries a way out to a real browser, where a pass earned is a pass the app cannot see.
     */
    onVerify: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnReturnToForeground(viewModel::refreshAttendance)
    ProfileScreen(
        state = state,
        destinations = destinations,
        onSignIn = onSignIn,
        onRetry = viewModel::refresh,
        hasAppUpdate = hasAppUpdate,
        onOpenWebsite = onOpenWebsite,
        onVerify = onVerify,
        onAttendance = viewModel::requestAttendance,
        onSignInForToday = viewModel::signInForToday,
        onDismissAttendanceChooser = viewModel::dismissAttendanceChooser,
        onAttendanceFailureShown = viewModel::attendanceFailureShown,
        onAttendanceBoard = viewModel::openAttendanceBoard,
        onDismissAttendanceBoard = viewModel::dismissAttendanceBoard,
        onRetryAttendanceBoard = viewModel::loadAttendanceBoard,
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

/**
 * 我的 — artboards 2b (signed in) and 9f (signed out).
 *
 * A directory, not a menu: the account sits on one card at the top — who, how much, how far to the
 * next level — and the twenty-one things it can reach are laid out below as four cards of icons, one
 * tap each. The six-row list this once replaced sent half of them through an intermediate page, and
 * the hop was the whole cost of finding anything.
 *
 * The four groups are no longer headed. 2b separates them by the gap between cards alone, and a
 * heading over each would say "内容 / 资产 / 社区 / 设置" about tiles whose own labels already say it.
 *
 * Two tiles an older board drew are still not here. 草稿箱 and 离线下载 name features the app does not
 * have: there is one draft, restored by the composer itself, and offline copies are a switch inside
 * 收藏. Drawing a tile for either would promise a screen that does not exist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    destinations: ProfileDestinations,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    onOpenWebsite: () -> Unit,
    /** Clears a Cloudflare challenge; see [ProfileRoute] for why it is not [onOpenWebsite]. */
    onVerify: (String) -> Unit,
    onAttendance: () -> Unit,
    onAttendanceBoard: () -> Unit,
    modifier: Modifier = Modifier,
    /** 应用内更新 found something; the gear in the app bar carries the dot that leads to it. */
    hasAppUpdate: Boolean = false,
    onSignInForToday: (AttendanceMode) -> Unit = {},
    onDismissAttendanceChooser: () -> Unit = {},
    onAttendanceFailureShown: () -> Unit = {},
    onDismissAttendanceBoard: () -> Unit = {},
    onRetryAttendanceBoard: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // The sign-in request now runs on this screen, so its refusals have to land here too — and
    // through [SiteErrorSnackbar], because a bare sentence is the wrong answer to a wall: 领鸡腿
    // refused with 需要确认一下你不是机器人 and nothing to press left the reader knowing what was
    // wrong and not that a web view fixes it.
    SiteErrorSnackbar(
        error = state.attendanceFailure,
        snackbarHostState = snackbarHostState,
        onShown = onAttendanceFailureShown,
        onVerify = onVerify,
        onSignIn = onSignIn,
        // The chooser, not the request: the mode picked is not kept once the sheet closes, so the
        // only honest retry is the one that asks again.
        onRetry = onAttendance,
    )
    Scaffold(
        modifier = modifier,
        // Both states get the same bar: a gear and nothing else. 2b and 9f open straight onto the
        // account card with no title over it — the tab bar already says 我的 — and the signed-out
        // screen, which used to carry 设置 as a row because it had no bar, now reaches it the same way
        // the signed-in one does.
        topBar = {
            TopAppBar(
                title = {},
                actions = { SettingsAction(hasAppUpdate = hasAppUpdate, onClick = destinations.settings) },
                colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (!state.isSignedIn) {
            SignedOutProfile(
                onSignIn = onSignIn,
                destinations = destinations,
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
                onVerify = onVerify,
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
            contentPadding = PaddingValues(start = LayerPageGutter, end = LayerPageGutter, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "account") {
                AccountCard(state, onOpenSpace = destinations.space, onAssets = destinations.assets)
            }
            item(key = "attendance") {
                AttendanceBanner(state, onAttendance, onAttendanceBoard)
            }
            profileSections(destinations).forEach { section ->
                item(key = section.title.key) {
                    ProfileGridCard(section.tiles)
                }
            }
        }
    }

    if (state.choosingAttendanceMode) {
        AttendanceModeDialog(
            onPick = onSignInForToday,
            onDismiss = onDismissAttendanceChooser,
        )
    }

    if (state.boardOpen) {
        AttendanceBoardDialog(
            isLoading = state.isLoadingBoard,
            entries = state.board,
            error = state.boardError,
            onRetry = onRetryAttendanceBoard,
            onDismiss = onDismissAttendanceBoard,
            selfUid = state.uid,
        )
    }
}

@Composable
private fun SettingsAction(
    hasAppUpdate: Boolean,
    onClick: () -> Unit,
) {
    Box {
        IconButton(onClick = onClick) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.settings_title))
        }
        if (hasAppUpdate) {
            UpdateDot(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 8.dp),
            )
        }
    }
}

/**
 * The account, on one card: who, the three balances, and how far the chicken count is from the next
 * level. The identity row opens 个人主页; the three tiles open 账户与成长, where a balance is explained.
 */
@Composable
private fun AccountCard(
    state: ProfileUiState,
    onOpenSpace: () -> Unit,
    onAssets: () -> Unit,
) {
    LayerCard(
        shape = MaterialTheme.shapes.extraLarge,
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IdentityRow(state, onOpenSpace)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val scheme = MaterialTheme.colorScheme
            listOf(
                Triple(Res.string.profile_chicken, state.chickenCount?.toString() ?: UNKNOWN, scheme.tertiaryContainer),
                Triple(Res.string.profile_stars, state.starCount?.toString() ?: UNKNOWN, scheme.secondaryContainer),
                Triple(
                    Res.string.profile_level,
                    state.level ?: stringResource(Res.string.profile_level_unknown),
                    scheme.primaryContainer,
                ),
            ).forEach { (label, value, container) ->
                TonalTile(
                    onClick = onAssets,
                    containerColor = container,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = value,
                        style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = TABULAR_FIGURES,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
        LevelProgress(state)
    }
}

@Composable
private fun IdentityRow(
    state: ProfileUiState,
    onOpenSpace: () -> Unit,
) {
    // 头像和 ID 本身就是进空间的入口，右边再挂一个编辑按钮只是重复，去掉。
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.largeIncreased)
            .clickable(onClickLabel = stringResource(Res.string.profile_space), onClick = onOpenSpace),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        UserAvatar(url = state.avatarUrl, name = state.displayName, size = 60.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = state.displayName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    // uid is only ever set from a loaded profile, so its absence is the
                    // signed-in-but-still-loading window the session line covers.
                    state.uid == null -> stringResource(Res.string.profile_session_active, siteName)

                    state.registeredYear != null && state.registeredMonth != null ->
                        stringResource(
                            Res.string.profile_member_since,
                            state.registeredYear,
                            state.registeredMonth,
                            state.uid,
                        )

                    else -> stringResource(Res.string.profile_member_uid, state.uid)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 距 Lv N 还差 x 鸡腿, over the same bar 账户与成长 draws.
 *
 * Absent rather than empty while the level is unknown: a grey track with no caption would read as a
 * level that has not started.
 */
@Composable
private fun LevelProgress(state: ProfileUiState) {
    val progress = state.levelProgress ?: return
    val next = state.nextLevelChicken ?: return
    val chicken = state.chickenCount ?: return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(
                text =
                state.chickenToNextLevel?.let { remaining ->
                    stringResource(Res.string.profile_level_remaining, state.nextLevelRank ?: 0, remaining)
                } ?: stringResource(Res.string.assets_level_no_threshold),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.assets_quota_value, chicken, next),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GrowthProgressBar(progress = progress)
    }
}

/**
 * Sign-in, drawn as the one filled block on the page — the daily action, one thumb's reach below the
 * balances it adds to — with 签到榜 at its trailing edge.
 *
 * Three states, as before: unknown (a spinner while today's receipt is read), not yet (opens the site's
 * mode chooser), and done (tonal, and the whole banner then opens today's board, since there is
 * nothing left to sign). The board button is there in all three: who signed in today is worth a look
 * whether or not you have.
 */
@Composable
private fun AttendanceBanner(
    state: ProfileUiState,
    onAttendance: () -> Unit,
    onAttendanceBoard: () -> Unit,
) {
    val done = state.hasSignedInToday
    val busy = state.isSigningIn || state.isAttendanceUnknown
    val layers = LocalPlazaLayers.current
    val shape = MaterialTheme.shapes.largeIncreased
    val container = if (done) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary
    val content = if (done) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary
    Surface(
        onClick = if (done) onAttendanceBoard else onAttendance,
        enabled = !busy,
        shape = shape,
        color = container,
        contentColor = content,
        modifier =
        Modifier
            .fillMaxWidth()
            // Only the unsigned banner floats: it is the call to action. Once done it is a receipt
            // and sits flat on the page like any other card-level thing.
            .then(if (done) Modifier.cardShadow(shape, layers.shadows) else Modifier.floatShadow(shape, layers.shadows)),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 64.dp).padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                when {
                    busy -> PlazaSpinner(Modifier.describedAsLoading(), size = 20.dp, color = content)
                    done -> Icon(Icons.Default.CheckCircle, contentDescription = null)
                    else -> Icon(PlazaIcons.EventAvailable, contentDescription = null)
                }
            }
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(
                    text =
                    when {
                        state.isSigningIn -> stringResource(Res.string.assets_signing_in)

                        done ->
                            state.attendanceGain?.let { stringResource(Res.string.assets_signed_in, it) }
                                ?: state.attendanceMessage
                                ?: stringResource(Res.string.profile_attendance_done)

                        else -> stringResource(Res.string.profile_attendance_title)
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle =
                    when {
                        state.isAttendanceUnknown -> stringResource(Res.string.profile_attendance_checking)
                        !done && !state.isSigningIn -> stringResource(Res.string.profile_attendance_modes)
                        else -> null
                    }
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.9f),
                    )
                }
            }
            TextButton(
                onClick = onAttendanceBoard,
                colors = ButtonDefaults.textButtonColors(contentColor = content),
            ) {
                Text(
                    stringResource(Res.string.profile_attendance_board),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

/** One group of tiles; [title] names it for the lazy key, not on screen. */
private data class ProfileSection(
    val title: StringResource,
    val tiles: List<ProfileTile>,
)

private data class ProfileTile(
    val label: StringResource,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * The four groups, in the order 2b puts them: 内容, 资产, 社区, 设置 — 6 + 4 + 7 + 4 = 21 tiles.
 *
 * 社区 carries 友站, which the older boards did not draw. It was the sixth link on 社区工具, and that
 * page is not reached from here while signed in — dropping the tile would have quietly deleted the
 * destination rather than moved it.
 */
private fun profileSections(destinations: ProfileDestinations): List<ProfileSection> =
    listOf(
        ProfileSection(
            title = Res.string.profile_section_content,
            tiles =
            listOf(
                ProfileTile(Res.string.profile_tile_topics, PlazaIcons.Article, destinations.topics),
                ProfileTile(Res.string.profile_tile_comments, PlazaIcons.ChatBubble, destinations.comments),
                ProfileTile(Res.string.profile_tile_collections, PlazaIcons.Bookmark, destinations.collections),
                ProfileTile(Res.string.profile_tile_history, PlazaIcons.History, destinations.history),
                ProfileTile(Res.string.profile_tile_following, Icons.Default.Person, destinations.following),
                ProfileTile(Res.string.profile_tile_followers, PlazaIcons.Group, destinations.followers),
            ),
        ),
        ProfileSection(
            title = Res.string.profile_section_assets,
            tiles =
            listOf(
                ProfileTile(Res.string.profile_tile_credit, PlazaIcons.Wallet, destinations.credit),
                ProfileTile(Res.string.profile_tile_stardust, Icons.Default.Star, destinations.stardust),
                ProfileTile(Res.string.profile_tile_transfer, PlazaIcons.SwapVert, destinations.transfer),
                ProfileTile(Res.string.profile_tile_invite, PlazaIcons.ConfirmationNumber, destinations.invite),
            ),
        ),
        ProfileSection(
            title = Res.string.profile_section_community,
            tiles =
            listOf(
                ProfileTile(Res.string.profile_tile_award, PlazaIcons.MenuBook, destinations.award),
                ProfileTile(Res.string.profile_tile_lucky, PlazaIcons.Casino, destinations.lucky),
                ProfileTile(Res.string.profile_tile_ruling, PlazaIcons.Gavel, destinations.ruling),
                ProfileTile(Res.string.profile_tile_providers, Icons.Default.ShoppingCart, destinations.providers),
                ProfileTile(Res.string.profile_tile_friends, PlazaIcons.Link, destinations.friends),
                ProfileTile(Res.string.profile_tile_block, PlazaIcons.Block, destinations.blockList),
                ProfileTile(Res.string.profile_tile_about_community, PlazaIcons.Forum, destinations.aboutCommunity),
            ),
        ),
        ProfileSection(
            title = Res.string.profile_section_settings,
            tiles =
            listOf(
                ProfileTile(Res.string.profile_tile_account, PlazaIcons.Badge, destinations.accountSettings),
                ProfileTile(
                    Res.string.profile_tile_notifications,
                    Icons.Default.Notifications,
                    destinations.notificationSettings,
                ),
                ProfileTile(Res.string.profile_tile_theme, PlazaIcons.Palette, destinations.themeSettings),
                ProfileTile(Res.string.profile_tile_about, Icons.Default.Info, destinations.about),
            ),
        ),
    )

private const val PROFILE_GRID_COLUMNS = 4

/**
 * One card of tiles, four to a row.
 *
 * A [FlowRow] rather than `LazyVerticalGrid`: this whole screen is one `LazyColumn`, and a lazy grid
 * nested in it scrolls on the same axis — the combination throws. The counts here are fixed and small,
 * so there is nothing to be lazy about anyway. Each tile takes a quarter of the width rather than a
 * weight, so a short last row stays on the column grid of the rows above it.
 */
@Composable
private fun ProfileGridCard(tiles: List<ProfileTile>) {
    LayerCard(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.Top) {
        FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = PROFILE_GRID_COLUMNS) {
            tiles.forEach { tile -> ProfileGridTile(tile, Modifier.fillMaxWidth(1f / PROFILE_GRID_COLUMNS)) }
        }
    }
}

@Composable
private fun ProfileGridTile(
    tile: ProfileTile,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(tile.label)
    Column(
        modifier =
        modifier
            .heightIn(min = 68.dp)
            .clip(MaterialTheme.shapes.large)
            // The whole tile, icon and caption together, is the target: a bare 24dp glyph is far
            // under Material's minimum, and the caption is what the eye aims at.
            .clickable(onClickLabel = label, onClick = tile.onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(tile.icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 9f: the useful signed-out version of 我的 — why to sign in, the button, and the community tools a
 * guest can already use.
 */
@Composable
private fun SignedOutProfile(
    onSignIn: () -> Unit,
    destinations: ProfileDestinations,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().readableWidth(),
        contentPadding = PaddingValues(start = LayerPageGutter, end = LayerPageGutter, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "welcome") {
            LayerCard(
                shape = MaterialTheme.shapes.extraLarge,
                contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    WelcomeMark()
                    Text(
                        text = stringResource(Res.string.profile_signed_out_title, siteName),
                        // Balanced rather than greedy: a heading this long otherwise leaves one character on
                        // its second line.
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, lineBreak = LineBreak.Heading),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp).semantics { heading() },
                    )
                    Text(
                        text = stringResource(Res.string.profile_signed_out_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item(key = "benefits") {
            LayerCard(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SignedOutBenefit(
                    icon = Icons.Default.Create,
                    title = stringResource(Res.string.profile_guest_benefit_post),
                    subtitle = stringResource(Res.string.profile_guest_benefit_post_hint),
                )
                SignedOutBenefit(
                    icon = Icons.Default.Email,
                    title = stringResource(Res.string.profile_guest_benefit_messages),
                    subtitle = stringResource(Res.string.profile_guest_benefit_messages_hint),
                )
                SignedOutBenefit(
                    icon = PlazaIcons.EventAvailable,
                    title = stringResource(Res.string.profile_guest_benefit_attendance),
                    subtitle = stringResource(Res.string.profile_guest_benefit_attendance_hint),
                )
            }
        }
        item(key = "sign-in") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                    shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                ) {
                    Icon(PlazaIcons.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(Res.string.profile_sign_in, siteName),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
                Text(
                    text = stringResource(Res.string.profile_sign_in_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item(key = "guest-tools") {
            Column {
                SectionLabel(stringResource(Res.string.profile_guest_section))
                Spacer(Modifier.height(8.dp))
                // The four tools 9f draws, then 社区工具 itself: that page also holds 友站, 关于社区 and
                // 邀请好友, which a guest used to reach through it and still should.
                ProfileGridCard(
                    listOf(
                        ProfileTile(Res.string.profile_tile_award, PlazaIcons.MenuBook, destinations.award),
                        ProfileTile(Res.string.profile_tile_ruling, PlazaIcons.Gavel, destinations.ruling),
                        ProfileTile(Res.string.profile_tile_lucky, PlazaIcons.Casino, destinations.lucky),
                        ProfileTile(
                            Res.string.profile_tile_providers,
                            Icons.Default.ShoppingCart,
                            destinations.providers,
                        ),
                        ProfileTile(Res.string.profile_tools, PlazaIcons.DashboardCustomize, destinations.tools),
                    ),
                )
            }
        }
    }
}

/** 9f's mark: a person on a leaf-shaped blob. Decorative — the heading under it says everything. */
@Composable
private fun WelcomeMark() {
    Surface(
        modifier = Modifier.size(96.dp),
        shape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 50, bottomStartPercent = 0),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun SignedOutBenefit(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val UNKNOWN = "—"

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "n1 我的 · 分区图标直达")
@Composable
private fun ProfileSignedInPreview() {
    PlazaTheme {
        ProfileScreen(
            state =
            ProfileUiState(
                isSignedIn = true,
                uid = 88423,
                displayName = "nodyssey_dev",
                level = "Lv 3",
                registeredYear = 2023,
                registeredMonth = 5,
                chickenCount = 1_284,
                starCount = 356,
            ),
            destinations = ProfileDestinations(),
            onSignIn = {},
            onRetry = {},
            onOpenWebsite = {},
            onVerify = {},
            onAttendance = {},
            onAttendanceBoard = {},
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
            destinations = ProfileDestinations(),
            onSignIn = {},
            onRetry = {},
            onOpenWebsite = {},
            onVerify = {},
            onAttendance = {},
            onAttendanceBoard = {},
        )
    }
}
