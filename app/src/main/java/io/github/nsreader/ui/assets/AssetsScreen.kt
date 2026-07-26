package io.github.nsreader.ui.assets

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nsreader.R
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.data.AttendanceMode
import io.github.nsreader.data.DailyQuota
import io.github.nsreader.ui.common.GroupedRow
import io.github.nsreader.ui.common.LoadingState
import io.github.nsreader.ui.common.NodeSeekErrorState
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.common.SpendConfirmDialog
import io.github.nsreader.ui.common.SpendDetail
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.TABULAR_FIGURES
import io.github.nsreader.ui.theme.readableWidth

/** What buying an invite code costs, and the reason the confirm dialog exists at all. */
const val INVITE_CODE_CHICKEN_COST = 1_000

@Composable
fun AssetsRoute(
    viewModel: AssetsViewModel,
    onBack: () -> Unit,
    onChickenLedger: () -> Unit,
    onStardust: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.assets_title)) },
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
            NodeSeekErrorState(
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
            AttendanceButton(state = state, onRequestAttendance = onRequestAttendance)
            // 邀请购码住在社区工具里，和站点的入口位置一致；这里不再重复一份。
            GroupedRow(
                title = stringResource(R.string.assets_board),
                subtitle = stringResource(R.string.assets_board_subtitle),
                icon = NodeSeekIcons.Group,
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
            state = state,
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

/** 今日签到榜 — who signed in and what they rolled, straight off `/api/attendance/board`. */
@Composable
private fun AttendanceBoardDialog(
    state: AssetsUiState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.assets_board)) },
        text = {
            when {
                state.isLoadingBoard ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(Modifier.size(24.dp)) }

                state.boardError != null ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            stringResource(R.string.assets_board_failed),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                    }

                else ->
                    LazyColumn(Modifier.height(BOARD_LIST_HEIGHT)) {
                        items(count = state.board.size, key = { it }) { index ->
                            val entry = state.board[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            ) {
                                Text(
                                    text = (index + 1).toString(),
                                    style =
                                    MaterialTheme.typography.labelMedium.copy(
                                        fontFeatureSettings = TABULAR_FIGURES,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(22.dp),
                                )
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                entry.gain?.let {
                                    Text(
                                        text = stringResource(R.string.assets_board_gain, it),
                                        style =
                                        MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFeatureSettings = TABULAR_FIGURES,
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

private val BOARD_LIST_HEIGHT = 360.dp

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
        icon = NodeSeekIcons.ConfirmationNumber,
        shortfall = shortfall?.let { stringResource(R.string.invite_shortfall, it) },
    )
}

/**
 * The level card, whose bar is the chicken count itself.
 *
 * Only Lv 1 → Lv 2 has a published threshold (400). Above that the card shows the count and says the
 * threshold is not public, rather than drawing a bar against a number we made up.
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
                stringResource(R.string.assets_level_remaining, remaining, (state.level ?: 1) + 1)
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

/** A determinate bar when the number is known, an empty track when it is not. Never a guessed fill. */
@Composable
private fun ProgressTrack(
    progress: Float?,
    height: Dp,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (progress != null && progress > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(height)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
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
 * The site does not say whether today is already signed until the request is made, so the button starts
 * enabled and becomes a tonal, disabled receipt afterwards — including when the answer was "已经签到过
 * 了", which is the site's own sentence and more use than a generic error.
 */
@Composable
private fun AttendanceButton(
    state: AssetsUiState,
    onRequestAttendance: () -> Unit,
) {
    val done = state.hasSignedInThisSession
    Button(
        onClick = onRequestAttendance,
        enabled = !done && !state.isSigningIn,
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
        nextLevelChicken = 400,
        postQuota = DailyQuota(0, 20),
        commentQuota = DailyQuota(3, 20),
        attendanceQuota = DailyQuota(7, 7),
        feedingQuota = DailyQuota(0, 0),
        hasSignedInThisSession = true,
        attendanceGain = 7,
    )

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8d 账户与成长")
@Composable
private fun AssetsPreview() {
    NodeSeekTheme { PreviewScreen(previewState) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8d 额度未接入 · dark")
@Composable
private fun AssetsUnknownQuotaPreview() {
    NodeSeekTheme(darkTheme = true) {
        PreviewScreen(
            previewState.copy(
                postQuota = DailyQuota(null, 20),
                commentQuota = DailyQuota(null, 20),
                attendanceQuota = DailyQuota(null, null),
                feedingQuota = DailyQuota(null, null),
                hasSignedInThisSession = false,
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
