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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import io.github.nodyssey.data.AttendanceMode
import io.github.nodyssey.ui.common.AttendanceBoardDialog
import io.github.nodyssey.ui.common.AttendanceModeDialog
import io.github.nodyssey.ui.common.SiteErrorSnackbar
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.UpdateDot
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.assets_signed_in
import io.github.nodyssey.ui.resources.assets_signing_in
import io.github.nodyssey.ui.resources.profile_attendance
import io.github.nodyssey.ui.resources.profile_attendance_checking
import io.github.nodyssey.ui.resources.profile_attendance_done
import io.github.nodyssey.ui.resources.profile_chicken
import io.github.nodyssey.ui.resources.profile_guest_benefit_attendance
import io.github.nodyssey.ui.resources.profile_guest_benefit_attendance_hint
import io.github.nodyssey.ui.resources.profile_guest_benefit_messages
import io.github.nodyssey.ui.resources.profile_guest_benefit_messages_hint
import io.github.nodyssey.ui.resources.profile_guest_benefit_post
import io.github.nodyssey.ui.resources.profile_guest_benefit_post_hint
import io.github.nodyssey.ui.resources.profile_guest_section
import io.github.nodyssey.ui.resources.profile_guest_settings_hint
import io.github.nodyssey.ui.resources.profile_guest_tools_hint
import io.github.nodyssey.ui.resources.profile_level
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
import io.github.nodyssey.ui.resources.tab_profile
import io.github.plaza.designsys.component.GroupedColumn
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.rememberOneHandAppBarState
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
    onVerify: () -> Unit,
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
 * 我的 — board n1.
 *
 * A directory, not a menu: the twenty things this account can reach are laid out as four labelled
 * grids of icons, one tap each. The six-row list this replaced sent half of them through an
 * intermediate page — 社区工具 held six links, 个人主页 held 主题帖 and 评论 behind tabs — and the
 * hop was the whole cost of finding anything.
 *
 * Two of board n1's twenty-two tiles are not here. 草稿箱 and 离线下载 name features the app does not
 * have: there is one draft, restored by the composer itself, and offline copies are a switch inside
 * 收藏. Drawing a tile for either would promise a screen that does not exist.
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    destinations: ProfileDestinations,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    onOpenWebsite: () -> Unit,
    /** Clears a Cloudflare challenge; see [ProfileRoute] for why it is not [onOpenWebsite]. */
    onVerify: () -> Unit,
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
    val appBarState = rememberOneHandAppBarState()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        // Signed in only. Board n1 gives 我的 a bar with 设置 in it; board c7 — the signed-out
        // screen below, unchanged by this — opens on its illustration and carries 设置 as one of
        // its two guest rows, so a bar there would be the same destination twice and 64dp less
        // room for the rest.
        topBar = {
            if (state.isSignedIn) {
                OneHandTopAppBar(
                    title = stringResource(Res.string.tab_profile),
                    state = appBarState,
                    actions = {
                        Box {
                            IconButton(onClick = destinations.settings) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = stringResource(Res.string.settings_title),
                                )
                            }
                            if (hasAppUpdate) {
                                UpdateDot(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 10.dp, end = 8.dp),
                                )
                            }
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (!state.isSignedIn) {
            SignedOutProfile(
                onSignIn = onSignIn,
                destinations = destinations,
                hasAppUpdate = hasAppUpdate,
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
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item(key = "profile-header") {
                ProfileHeader(state, destinations.space)
            }
            item(key = "resources") {
                ResourceCards(state, destinations.assets)
            }
            item(key = "attendance") {
                AttendanceButton(state, onAttendance, onAttendanceBoard)
            }
            profileSections(destinations).forEach { section ->
                item(key = section.title.key) {
                    ProfileGridSection(section)
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
        )
    }
}

@Composable
private fun AttendanceButton(
    state: ProfileUiState,
    onAttendance: () -> Unit,
    onAttendanceBoard: () -> Unit,
) {
    Button(
        onClick = if (state.hasSignedInToday) onAttendanceBoard else onAttendance,
        enabled = !state.isAttendanceUnknown && !state.isSigningIn,
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
            state.isSigningIn -> {
                CircularProgressIndicator(Modifier.size(18.dp).describedAsLoading())
                Text(
                    stringResource(Res.string.assets_signing_in),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }

            state.isAttendanceUnknown -> {
                CircularProgressIndicator(Modifier.size(18.dp).describedAsLoading())
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
                    } ?: state.attendanceMessage
                        ?: stringResource(Res.string.profile_attendance_done),
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

/** One labelled grid: a section heading with its tiles under it. */
private data class ProfileSection(
    val title: StringResource,
    val tiles: List<ProfileTile>,
    /** Which tonal role the tiles wear, so a group reads as one at a glance. */
    val tone: ProfileTone,
)

private enum class ProfileTone {
    PRIMARY,
    TERTIARY,
    SECONDARY,
}

private data class ProfileTile(
    val label: StringResource,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * The four groups, in the order board n1 puts them.
 *
 * 社区 carries one tile the board does not draw: 友站. It was the sixth link on 社区工具, and that
 * page is now unreachable while signed in — dropping the tile would have quietly deleted the
 * destination rather than moved it.
 */
private fun profileSections(destinations: ProfileDestinations): List<ProfileSection> =
    listOf(
        ProfileSection(
            title = Res.string.profile_section_content,
            tone = ProfileTone.PRIMARY,
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
            tone = ProfileTone.TERTIARY,
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
            tone = ProfileTone.SECONDARY,
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
            tone = ProfileTone.SECONDARY,
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
 * A section drawn as rows of four.
 *
 * Laid out by hand rather than with `LazyVerticalGrid`: this whole screen is one `LazyColumn`, and
 * a lazy grid nested in it scrolls on the same axis — the combination throws. The counts here are
 * fixed and small, so there is nothing to be lazy about anyway.
 */
@Composable
private fun ProfileGridSection(section: ProfileSection) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionLabel(stringResource(section.title))
        section.tiles.chunked(PROFILE_GRID_COLUMNS).forEach { rowTiles ->
            Row(Modifier.fillMaxWidth()) {
                rowTiles.forEach { tile ->
                    ProfileGridTile(tile, section.tone, Modifier.weight(1f))
                }
                // Keeps the last row's tiles on the same column grid as the ones above rather than
                // spreading three of them across four columns' worth of width.
                repeat(PROFILE_GRID_COLUMNS - rowTiles.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProfileGridTile(
    tile: ProfileTile,
    tone: ProfileTone,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(tile.label)
    val container =
        when (tone) {
            ProfileTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
            ProfileTone.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer
            ProfileTone.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
        }
    val content =
        when (tone) {
            ProfileTone.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
            ProfileTone.TERTIARY -> MaterialTheme.colorScheme.onTertiaryContainer
            ProfileTone.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
        }
    Column(
        modifier =
        modifier
            .clip(RoundedCornerShape(16.dp))
            // The whole tile, icon and caption together, is the target — 48dp of coloured square is
            // under Material's minimum once the label is what the eye aims at.
            .clickable(onClickLabel = label, onClick = tile.onClick)
            .padding(vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Surface(
            modifier = Modifier.size(Sizes.minTouchTarget),
            shape = RoundedCornerShape(16.dp),
            color = container,
            contentColor = content,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(tile.icon, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Board c7: the useful signed-out version of 我的, including the two guest-safe destinations. */
@Composable
private fun SignedOutProfile(
    onSignIn: () -> Unit,
    destinations: ProfileDestinations,
    hasAppUpdate: Boolean,
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
                        onClick = destinations.settings,
                        // Updating has nothing to do with being signed in, so the guest side of 我的
                        // carries the same dot — here on the row, since it has no app bar to hang
                        // it on.
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
                        onClick = destinations.tools,
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
                text = when {
                    // uid is only ever set from a loaded profile, so its absence is the
                    // signed-in-but-still-loading window the session line covers.
                    state.uid == null -> stringResource(Res.string.profile_session_active)

                    state.registeredYear != null && state.registeredMonth != null ->
                        stringResource(
                            Res.string.profile_member_since,
                            state.registeredYear,
                            state.registeredMonth,
                            state.uid,
                        )

                    else -> stringResource(Res.string.profile_member_uid, state.uid)
                },
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
