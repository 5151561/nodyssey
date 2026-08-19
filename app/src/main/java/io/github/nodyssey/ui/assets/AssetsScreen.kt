package io.github.nodyssey.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.data.AttendanceMode
import io.github.nodyssey.data.DailyQuota
import io.github.nodyssey.ui.common.AttendanceBoardDialog
import io.github.nodyssey.ui.common.NodeSeekIcons
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.SpendConfirmDialog
import io.github.nodyssey.ui.common.SpendDetail
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.readableWidth

/** What buying an invite code costs, and the reason the confirm dialog exists at all. */
const val INVITE_CODE_CHICKEN_COST = 1_000

@Composable
fun AssetsRoute(
    viewModel: AssetsViewModel,
    openAttendanceChooser: Boolean,
    onBack: () -> Unit,
    onChickenLedger: () -> Unit,
    onStardust: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(openAttendanceChooser) {
        if (openAttendanceChooser) viewModel.requestAttendance()
    }
    AssetsScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onRequestAttendance = viewModel::requestAttendance,
        onDismissAttendanceChooser = viewModel::dismissAttendanceChooser,
        onSignInForToday = viewModel::signInForToday,
        onOpenBoard = viewModel::openBoard,
        onDismissBoard = viewModel::dismissBoard,
        onRetryBoard = viewModel::loadBoard,
        onChickenLedger = onChickenLedger,
        onStardust = onStardust,
        onOpenBrowser = onOpenBrowser,
        onSignIn = onSignIn,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(
    state: AssetsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRequestAttendance: () -> Unit,
    onDismissAttendanceChooser: () -> Unit,
    onSignInForToday: (AttendanceMode) -> Unit,
    onOpenBoard: () -> Unit,
    onDismissBoard: () -> Unit,
    onRetryBoard: () -> Unit,
    onChickenLedger: () -> Unit,
    onStardust: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appBarState = rememberOneHandAppBarState()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(R.string.assets_title),
                state = appBarState,
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
        if (state.isLoading && !state.hasData) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        if (state.error != null && !state.hasData) {
            SiteErrorState(
                error = state.error,
                onRetry = onRetry,
                onOpenBrowser = onOpenBrowser,
                onSignIn = onSignIn,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            LevelCard(state)
            DailyQuotaCard(state)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BalanceCard(
                    label = stringResource(R.string.assets_chicken),
                    value = state.chickenCount,
                    action = stringResource(R.string.assets_ledger),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onChickenLedger,
                )
                BalanceCard(
                    label = stringResource(R.string.assets_stars),
                    value = state.starCount,
                    action = stringResource(R.string.assets_ledger_transfer),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onStardust,
                )
            }
            AttendanceButton(
                state = state,
                onRequestAttendance = onRequestAttendance,
                onOpenBoard = onOpenBoard,
            )
            // 邀请购码住在社区工具里，和站点的入口位置一致；这里不再重复一份。
            GroupedRow(
                title = stringResource(R.string.assets_board),
                subtitle = stringResource(R.string.assets_board_subtitle),
                icon = PlazaIcons.Group,
                first = true,
                last = true,
                onClick = onOpenBoard,
            )
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
            onRetry = onRetryBoard,
            onDismiss = onDismissBoard,
        )
    }
}

/**
 * The site's sign-in is a choice, not a button: gamble on a random count or take a flat five.
 *
 * Presented at tap time rather than as a setting, because it is a daily decision and the site's own
 * page asks it the same way.
 */
@Composable
private fun AttendanceModeDialog(
    onPick: (AttendanceMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(NodeSeekIcons.ChickenLeg, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.assets_sign_in_choice_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = { onPick(AttendanceMode.RANDOM) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(stringResource(R.string.assets_sign_in_random))
                }
                FilledTonalButton(
                    onClick = { onPick(AttendanceMode.FIXED_FIVE) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(stringResource(R.string.assets_sign_in_fixed))
                }
                Text(
                    text = stringResource(R.string.assets_sign_in_choice_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Buying an invite code, confirmed the same way as every other spend.
 *
 * Shared with the invite screen deliberately: the same 1000 chicken leave the account whichever entry
 * point was tapped, so they get the same sentence and the same disabled state when the balance is short.
 */
@Composable
fun InviteConfirmDialog(
    chickenCount: Int?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shortfall = chickenCount?.let { (INVITE_CODE_CHICKEN_COST - it).takeIf { gap -> gap > 0 } }
    SpendConfirmDialog(
        title = stringResource(R.string.invite_confirm_title),
        details =
        buildList {
            add(SpendDetail(stringResource(R.string.invite_cost), stringResource(R.string.invite_cost_value)))
            chickenCount?.let { balance ->
                add(SpendDetail(stringResource(R.string.spend_current_balance), balance.toString()))
                if (shortfall == null) {
                    add(
                        SpendDetail(
                            stringResource(R.string.invite_balance_after),
                            (balance - INVITE_CODE_CHICKEN_COST).toString(),
                        ),
                    )
                }
            }
        },
        caution =
        stringResource(R.string.invite_caution) + "\n" + stringResource(R.string.invite_opened_web),
        confirmLabel = stringResource(R.string.invite_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        icon = PlazaIcons.ConfirmationNumber,
        shortfall = shortfall?.let { stringResource(R.string.invite_shortfall, it) },
    )
}

/**
 * The level card, whose bar is the chicken count itself.
 *
 * The bar spans the current level rather than starting at zero — Lv2 runs 400 → 900 — because that
 * is the span the site's own `/progress` bar draws. See `NodeSeekSite.levelChickenSpan`.
 */
@Composable
private fun LevelCard(state: AssetsUiState) {
    AssetsCard(radius = 22.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = state.level?.let { stringResource(R.string.assets_level, it) } ?: UNKNOWN,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
            )
            val target = state.nextLevelChicken
            val chicken = state.chickenCount
            Text(
                text =
                if (target != null && chicken != null) {
                    stringResource(R.string.assets_level_progress, chicken, target)
                } else {
                    chicken?.let { stringResource(R.string.assets_chicken_count, it) } ?: UNKNOWN
                },
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ProgressTrack(progress = state.levelProgress, height = 8.dp)
        Text(
            text =
            state.chickenToNextLevel?.let { remaining ->
                stringResource(
                    R.string.assets_level_remaining,
                    remaining,
                    (state.levelBarRank ?: state.level ?: 1) + 1,
                )
            } ?: stringResource(R.string.assets_level_no_threshold),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DailyQuotaCard(state: AssetsUiState) {
    AssetsCard(radius = 22.dp) {
        Text(
            text = stringResource(R.string.assets_daily_title),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
        QuotaRow(stringResource(R.string.assets_quota_post), state.postQuota)
        QuotaRow(stringResource(R.string.assets_quota_comment), state.commentQuota)
        QuotaRow(
            label = stringResource(R.string.assets_quota_attendance),
            quota = state.attendanceQuota,
            badge = state.attendanceGain?.let { stringResource(R.string.assets_signed_in, it) },
        )
        QuotaRow(stringResource(R.string.assets_quota_feeding), state.feedingQuota)
        if (!state.postQuota.isKnown) {
            Text(
                text = stringResource(R.string.assets_quota_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuotaRow(
    label: String,
    quota: DailyQuota,
    badge: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            badge?.let {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(end = Spacing.sm),
                ) {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = quota.label(),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ProgressTrack(progress = quota.progress(), height = 4.dp)
    }
}

@Composable
private fun DailyQuota.label(): String =
    when {
        used != null && total != null -> stringResource(R.string.assets_quota_value, used, total)
        total != null -> stringResource(R.string.assets_quota_value_unknown, total)
        else -> UNKNOWN
    }

private fun DailyQuota.progress(): Float? {
    val cap = total?.takeIf { it > 0 } ?: return null
    val current = used ?: return null
    return (current.toFloat() / cap).coerceIn(0f, 1f)
}

/**
 * A determinate bar when the number is known, an empty track when it is not. Never a guessed fill.
 *
 * The gap and the stop indicator Material draws by default are turned off: at 4.dp these bars sit
 * directly under a quota row and read as one continuous track, and a dot at the far right would look
 * like a value the site never published.
 */
@Composable
private fun ProgressTrack(
    progress: Float?,
    height: Dp,
) {
    LinearProgressIndicator(
        progress = { progress ?: 0f },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
        modifier = Modifier.fillMaxWidth().height(height),
    )
}

@Composable
private fun RowScope.BalanceCard(
    label: String,
    value: Int?,
    action: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = value?.toString() ?: UNKNOWN,
                style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(action, style = MaterialTheme.typography.labelMedium)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/**
 * Sign-in, which is the only write this screen performs.
 *
 * Before signing in this opens the site's mode chooser. Afterwards it becomes a tonal receipt which
 * remains actionable: tapping it again opens today's board.
 */
@Composable
private fun AttendanceButton(
    state: AssetsUiState,
    onRequestAttendance: () -> Unit,
    onOpenBoard: () -> Unit,
) {
    val done = state.hasSignedInToday
    Button(
        onClick = if (done) onOpenBoard else onRequestAttendance,
        enabled = !state.isSigningIn,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(24.dp),
        colors =
        if (done) {
            ButtonDefaults.filledTonalButtonColors()
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        when {
            state.isSigningIn -> {
                CircularProgressIndicator(Modifier.size(18.dp))
                Text(
                    stringResource(R.string.assets_signing_in),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }

            done -> {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text =
                    state.attendanceGain?.let { stringResource(R.string.assets_signed_in, it) }
                        ?: state.attendanceMessage
                        ?: stringResource(R.string.assets_sign_in),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }

            else -> {
                Icon(NodeSeekIcons.ChickenLeg, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.assets_sign_in),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun AssetsCard(
    radius: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(radius),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

private const val UNKNOWN = "—"

// -------------------------------------------------------------------------------------------------

private val previewState =
    AssetsUiState(
        isLoading = false,
        level = 1,
        chickenCount = 344,
        starCount = 4,
        levelFloorChicken = 100,
        nextLevelChicken = 400,
        levelBarRank = 1,
        postQuota = DailyQuota(0, 20),
        commentQuota = DailyQuota(3, 20),
        attendanceQuota = DailyQuota(7, 7),
        feedingQuota = DailyQuota(0, 0),
        hasSignedInToday = true,
        attendanceGain = 7,
    )

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8d 账户与成长")
@Composable
private fun AssetsPreview() {
    PlazaTheme { PreviewScreen(previewState) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8d 额度读不到 · dark")
@Composable
private fun AssetsUnknownQuotaPreview() {
    PlazaTheme(darkTheme = true) {
        PreviewScreen(
            previewState.copy(
                postQuota = DailyQuota(null, 20),
                commentQuota = DailyQuota(null, 20),
                attendanceQuota = DailyQuota(null, null),
                feedingQuota = DailyQuota(null, null),
                hasSignedInToday = false,
                attendanceGain = null,
            ),
        )
    }
}

@Composable
private fun PreviewScreen(state: AssetsUiState) {
    AssetsScreen(
        state = state,
        onBack = {},
        onRetry = {},
        onRequestAttendance = {},
        onDismissAttendanceChooser = {},
        onSignInForToday = {},
        onOpenBoard = {},
        onDismissBoard = {},
        onRetryBoard = {},
        onChickenLedger = {},
        onStardust = {},
        onOpenBrowser = {},
        onSignIn = {},
    )
}
