package io.github.nodyssey.ui.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.AttendanceMode
import io.github.nodyssey.data.DailyQuota
import io.github.nodyssey.ui.common.AttendanceBoardDialog
import io.github.nodyssey.ui.common.AttendanceModeDialog
import io.github.nodyssey.ui.common.GrowthProgressBar
import io.github.nodyssey.ui.common.NodeSeekIcons
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.SpendConfirmDialog
import io.github.nodyssey.ui.common.SpendDetail
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.common.webViewUrl
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.assets_board
import io.github.nodyssey.ui.resources.assets_board_subtitle
import io.github.nodyssey.ui.resources.assets_chicken
import io.github.nodyssey.ui.resources.assets_current_level
import io.github.nodyssey.ui.resources.assets_daily_title
import io.github.nodyssey.ui.resources.assets_ledger
import io.github.nodyssey.ui.resources.assets_ledger_transfer
import io.github.nodyssey.ui.resources.assets_level_no_threshold
import io.github.nodyssey.ui.resources.assets_level_remaining
import io.github.nodyssey.ui.resources.assets_quota_attendance
import io.github.nodyssey.ui.resources.assets_quota_comment
import io.github.nodyssey.ui.resources.assets_quota_feeding
import io.github.nodyssey.ui.resources.assets_quota_hint
import io.github.nodyssey.ui.resources.assets_quota_post
import io.github.nodyssey.ui.resources.assets_quota_value
import io.github.nodyssey.ui.resources.assets_quota_value_unknown
import io.github.nodyssey.ui.resources.assets_signed_in
import io.github.nodyssey.ui.resources.assets_signing_in
import io.github.nodyssey.ui.resources.assets_stars
import io.github.nodyssey.ui.resources.assets_title
import io.github.nodyssey.ui.resources.credit_level
import io.github.nodyssey.ui.resources.invite_balance_after
import io.github.nodyssey.ui.resources.invite_caution
import io.github.nodyssey.ui.resources.invite_confirm
import io.github.nodyssey.ui.resources.invite_confirm_title
import io.github.nodyssey.ui.resources.invite_cost
import io.github.nodyssey.ui.resources.invite_cost_value
import io.github.nodyssey.ui.resources.invite_opened_web
import io.github.nodyssey.ui.resources.invite_short_hint
import io.github.nodyssey.ui.resources.invite_short_title
import io.github.nodyssey.ui.resources.invite_shortfall
import io.github.nodyssey.ui.resources.invite_shortfall_label
import io.github.nodyssey.ui.resources.profile_attendance_title
import io.github.nodyssey.ui.resources.spend_current_balance
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerDivider
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.TonalTag
import io.github.plaza.designsys.component.TonalTile
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What buying an invite code costs, and the reason the confirm dialog exists at all. */
const val INVITE_CODE_CHICKEN_COST = 1_000

@Composable
fun AssetsRoute(
    viewModel: AssetsViewModel,
    onBack: () -> Unit,
    onChickenLedger: () -> Unit,
    onStardust: () -> Unit,
    onOpenBrowser: (String) -> Unit,
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
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appBarState = rememberOneHandAppBarState()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.assets_title),
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
        if (state.isLoading && !state.hasData) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        if (state.error != null && !state.hasData) {
            SiteErrorState(
                error = state.error,
                onRetry = onRetry,
                // Named rather than left to [SiteErrorState]'s fallback. This screen asks for a
                // web view in one place only, so the two are the same closure — and a challenge
                // reaching it by fallback is exactly how other screens ended up handing one to a
                // plain reading view without anything in the code saying so.
                onOpenBrowser = { onOpenBrowser(state.error.webViewUrl(NodeSeekSite.BASE_URL)) },
                onVerify = onOpenBrowser,
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
                .padding(start = LayerPageGutter, end = LayerPageGutter, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LevelCard(state)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val scheme = MaterialTheme.colorScheme
                listOf(
                    Balance(Res.string.assets_chicken, state.chickenCount, Res.string.assets_ledger, scheme.tertiaryContainer, onChickenLedger),
                    Balance(Res.string.assets_stars, state.starCount, Res.string.assets_ledger_transfer, scheme.secondaryContainer, onStardust),
                ).forEach { balance ->
                    TonalTile(
                        onClick = balance.onClick,
                        containerColor = balance.container,
                        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = 14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(balance.label),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            text = balance.value?.toString() ?: UNKNOWN,
                            style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = TABULAR_FIGURES,
                            ),
                        )
                        Text(stringResource(balance.action), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            SectionLabel(stringResource(Res.string.assets_daily_title))
            DailyQuotaCard(
                state = state,
                onRequestAttendance = onRequestAttendance,
            )
            // 邀请购码住在社区工具里，和站点的入口位置一致；这里不再重复一份。
            GroupedRow(
                title = stringResource(Res.string.assets_board),
                subtitle = stringResource(Res.string.assets_board_subtitle),
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
 * Buying an invite code, confirmed the same way as every other spend.
 *
 * Shared with the invite screen deliberately: the same 1000 chicken leave the account whichever entry
 * point was tapped, so they get the same sentence and the same dead end when the balance is short.
 * That dead end is 9e's: titled 鸡腿不够, the gap worked out on the card — balance, cost, and what is
 * still missing in the error tone — and a line on how chicken legs are earned instead of a caution
 * about a purchase that is not going to happen.
 */
@Composable
fun InviteConfirmDialog(
    chickenCount: Int?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shortfall = chickenCount?.let { (INVITE_CODE_CHICKEN_COST - it).takeIf { gap -> gap > 0 } }
    if (chickenCount != null && shortfall != null) {
        SpendConfirmDialog(
            title = stringResource(Res.string.invite_short_title),
            details =
            listOf(
                SpendDetail(stringResource(Res.string.spend_current_balance), chickenCount.toString()),
                SpendDetail(stringResource(Res.string.invite_cost), signedAmount(-INVITE_CODE_CHICKEN_COST)),
                SpendDetail(
                    stringResource(Res.string.invite_shortfall_label),
                    shortfall.toString(),
                    separated = true,
                    isError = true,
                ),
            ),
            caution = stringResource(Res.string.invite_short_hint),
            confirmLabel = stringResource(Res.string.invite_confirm),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            icon = null,
            shortfall = stringResource(Res.string.invite_shortfall, shortfall),
        )
        return
    }
    SpendConfirmDialog(
        title = stringResource(Res.string.invite_confirm_title),
        details =
        buildList {
            add(SpendDetail(stringResource(Res.string.invite_cost), stringResource(Res.string.invite_cost_value)))
            chickenCount?.let { balance ->
                add(SpendDetail(stringResource(Res.string.spend_current_balance), balance.toString()))
                add(
                    SpendDetail(
                        stringResource(Res.string.invite_balance_after),
                        (balance - INVITE_CODE_CHICKEN_COST).toString(),
                        separated = true,
                    ),
                )
            }
        },
        caution =
        stringResource(Res.string.invite_caution) + "\n" + stringResource(Res.string.invite_opened_web),
        confirmLabel = stringResource(Res.string.invite_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        icon = PlazaIcons.ConfirmationNumber,
    )
}

/**
 * 8a's lead: the level, large, with the chicken count that *is* its progress.
 *
 * The bar spans the current level rather than starting at zero — Lv2 runs 400 → 900 — because that
 * is the span the site's own `/progress` bar draws. See `NodeSeekSite.levelChickenSpan`.
 *
 * 8a also prints 「Lv5 封顶」 under the bar. It is left out: the site clamps its *bar* at Lv5, but
 * accounts above Lv5 exist, so as a statement about levels it would be wrong.
 */
@Composable
private fun LevelCard(state: AssetsUiState) {
    LayerCard(
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.assets_current_level),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.level?.let { stringResource(Res.string.credit_level, it) } ?: UNKNOWN,
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val chicken = state.chickenCount
                val target = state.nextLevelChicken
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    text =
                    buildAnnotatedString {
                        append(chicken?.toString() ?: UNKNOWN)
                        if (target != null) {
                            withStyle(
                                MaterialTheme.typography.titleSmall
                                    .copy(fontWeight = FontWeight.Medium, color = muted)
                                    .toSpanStyle(),
                            ) { append(" / $target") }
                        }
                    },
                    style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = TABULAR_FIGURES,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = stringResource(Res.string.assets_chicken),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
        }
        GrowthProgressBar(progress = state.levelProgress)
        Text(
            text =
            state.chickenToNextLevel?.let { remaining ->
                stringResource(
                    Res.string.assets_level_remaining,
                    remaining,
                    (state.levelBarRank ?: state.level ?: 1) + 1,
                )
            } ?: stringResource(Res.string.assets_level_no_threshold),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 今日额度 as one card of rows. Sign-in is its last row, and the only row that is also a control: the
 * big button this screen used to end with is now the row's trailing edge, because today's sign-in *is*
 * today's fourth allowance and reads best beside the other three.
 */
@Composable
private fun DailyQuotaCard(
    state: AssetsUiState,
    onRequestAttendance: () -> Unit,
) {
    Column {
        QuotaRow(stringResource(Res.string.assets_quota_post), state.postQuota, first = true)
        QuotaRow(stringResource(Res.string.assets_quota_comment), state.commentQuota)
        QuotaRow(stringResource(Res.string.assets_quota_feeding), state.feedingQuota)
        AttendanceRow(state, onRequestAttendance)
    }
    if (!state.postQuota.isKnown) {
        Text(
            text = stringResource(Res.string.assets_quota_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.md),
        )
    }
}

@Composable
private fun QuotaRow(
    label: String,
    quota: DailyQuota,
    first: Boolean = false,
) {
    GroupedListItem(
        first = first,
        last = false,
        headlineContent = { Text(label) },
        trailingContent = {
            Text(
                text = quota.label(),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
            )
        },
        supportingContent = { GrowthProgressBar(progress = quota.progress(), modifier = Modifier.padding(top = 6.dp)) },
    )
}

/**
 * The sign-in row: a button until today's sign-in is done, a receipt afterwards.
 *
 * The receipt names the gain when the site told us one and falls back to the allowance line — the
 * same two numbers, from two endpoints — so a sign-in made on the website still shows as done here.
 */
@Composable
private fun AttendanceRow(
    state: AssetsUiState,
    onRequestAttendance: () -> Unit,
) {
    GroupedListItem(
        first = false,
        last = true,
        headlineContent = { Text(stringResource(Res.string.assets_quota_attendance)) },
        trailingContent = { AttendanceState(state, onRequestAttendance) },
    )
}

@Composable
private fun AttendanceState(
    state: AssetsUiState,
    onRequestAttendance: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        when {
            state.isSigningIn -> {
                PlazaSpinner(Modifier.describedAsLoading(), size = 18.dp)
                Text(
                    stringResource(Res.string.assets_signing_in),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.hasSignedInToday ->
                TonalTag(
                    text =
                    state.attendanceGain?.let { stringResource(Res.string.assets_signed_in, it) }
                        ?: state.attendanceMessage
                        ?: state.attendanceQuota.label(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    icon = Icons.Default.Check,
                )

            else ->
                Button(
                    onClick = onRequestAttendance,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(NodeSeekIcons.ChickenLeg, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(Res.string.profile_attendance_title),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
        }
    }
}

@Composable
private fun DailyQuota.label(): String {
    // Read into locals first: `used` and `total` are `val`s in another module, where the compiler
    // will not smart-cast a null check into the branch that uses them.
    val used = used
    val total = total
    return when {
        used != null && total != null -> stringResource(Res.string.assets_quota_value, used, total)
        total != null -> stringResource(Res.string.assets_quota_value_unknown, total)
        else -> UNKNOWN
    }
}

private fun DailyQuota.progress(): Float? {
    val cap = total?.takeIf { it > 0 } ?: return null
    val current = used ?: return null
    return (current.toFloat() / cap).coerceIn(0f, 1f)
}

/** One of the two balance tiles: what it counts, how much, and where tapping it goes. */
private class Balance(
    val label: StringResource,
    val value: Int?,
    val action: StringResource,
    val container: Color,
    val onClick: () -> Unit,
)

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
