package io.github.nsreader.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nsreader.R
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.data.RulingKind
import io.github.nsreader.data.RulingRecord
import io.github.nsreader.ui.common.LoadingState
import io.github.nsreader.ui.common.NodeSeekErrorState
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.common.NumericPager
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.TABULAR_FIGURES
import io.github.nsreader.ui.theme.readableWidth

@Composable
fun RulingRoute(
    viewModel: RulingViewModel,
    onBack: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RulingScreen(
        state = state,
        onBack = onBack,
        onPageSelected = viewModel::load,
        onRetry = viewModel::retry,
        onOpenBrowser = { onOpenBrowser(NodeSeekSite.BASE_URL + NodeSeekSite.RULING_PATH) },
        onSignIn = onSignIn,
        modifier = modifier,
    )
}

/**
 * 管理记录.
 *
 * The site presents this as a five-column table, which a 360dp screen cannot carry. Each decision
 * becomes a two-line row instead: who and why on the first line, what was done and by whom on the
 * second. The compound action stays joined by "+" rather than split into badges — "扣 20 鸡腿 + 移动版块
 * 至 促销 + 锁定修改" is one decision and reads as one sentence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulingScreen(
    state: RulingUiState,
    onBack: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onRetry: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.ruling_title))
                        Text(
                            text = stringResource(R.string.ruling_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth(),
        ) {
            when {
                state.isLoading && state.records.isEmpty() -> LoadingState(Modifier.fillMaxSize())

                state.error != null && state.records.isEmpty() ->
                    NodeSeekErrorState(
                        error = state.error,
                        onRetry = onRetry,
                        onOpenBrowser = onOpenBrowser,
                        onSignIn = onSignIn,
                        modifier = Modifier.fillMaxSize(),
                    )

                else ->
                    LazyColumn(Modifier.weight(1f)) {
                        items(count = state.records.size, key = { state.records[it].id }) { index ->
                            RulingRow(state.records[index])
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
            }
            NumericPager(
                page = state.page,
                totalPages = state.totalPages,
                onPageSelected = onPageSelected,
                approximateTotal = state.totalPages >= APPROXIMATE_TOTAL_THRESHOLD,
            )
        }
    }
}

private const val APPROXIMATE_TOTAL_THRESHOLD = 100

@Composable
private fun RulingRow(record: RulingRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = record.kind.icon(),
                contentDescription = null,
                tint = record.kind.tint(),
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // Built as an annotated string rather than one format string so the user name can carry the
            // weight — it is what the eye scans for in a log of other people's punishments.
            val targetKind = stringResource(R.string.ruling_target_kind, record.targetKind)
            val reason = record.reason?.let { stringResource(R.string.ruling_reason, it) }
            Text(
                text =
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(record.targetName) }
                    append(targetKind)
                    reason?.let(::append)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = record.metaLine(),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RulingRecord.metaLine(): String {
    val actions = actions.joinToString(" + ").ifBlank { "—" }
    val time = timeText.orEmpty()
    return moderatorName
        ?.let { stringResource(R.string.ruling_meta, actions, it, time) }
        ?: stringResource(R.string.ruling_meta_no_moderator, actions, time)
}

private fun RulingKind.icon(): ImageVector =
    when (this) {
        RulingKind.PENALTY -> NodeSeekIcons.Gavel
        RulingKind.BAN -> NodeSeekIcons.Block
        RulingKind.MOVE -> NodeSeekIcons.SwapVert
        RulingKind.PERMISSION -> NodeSeekIcons.Visibility
        RulingKind.REWARD -> NodeSeekIcons.ChickenLeg
    }

@Composable
private fun RulingKind.tint() =
    when (this) {
        RulingKind.BAN -> MaterialTheme.colorScheme.error
        RulingKind.REWARD -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

// -------------------------------------------------------------------------------------------------

private val previewRecords =
    listOf(
        RulingRecord(
            1,
            "深蓝色的天",
            "帖子",
            "无意义灌水",
            listOf("扣 10 鸡腿", "锁定修改"),
            "kanata",
            "2026/7/25 21:40",
            RulingKind.PENALTY,
        ),
        RulingRecord(
            2,
            "bigxiang",
            "评论",
            "人身攻击",
            listOf("扣 30 鸡腿", "禁言 3 天"),
            "nsadmin",
            "2026/7/25 18:12",
            RulingKind.BAN,
        ),
        RulingRecord(
            3,
            "jswcph",
            "帖子",
            "发卡站未发推广区",
            listOf("扣 20 鸡腿", "移动版块至 促销", "锁定修改"),
            "kanata",
            "2026/7/24 09:31",
            RulingKind.MOVE,
        ),
        RulingRecord(
            4,
            "wh1te",
            "帖子",
            "涉及敏感信息",
            listOf("阅读权限改为 Lv 1", "锁定修改"),
            "kanata",
            "2026/7/23 15:02",
            RulingKind.PERMISSION,
        ),
        RulingRecord(
            5,
            "haiqing123",
            "帖子",
            "优质技术分享",
            listOf("加 50 鸡腿"),
            "nsadmin",
            "2026/7/22 20:18",
            RulingKind.REWARD,
        ),
        RulingRecord(
            6,
            "terrywei",
            "评论",
            "引战言论",
            listOf("扣 15 鸡腿", "禁言 1 天"),
            "kanata",
            "2026/7/22 11:47",
            RulingKind.PENALTY,
        ),
    )

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "9d 管理记录")
@Composable
private fun RulingPreview() {
    NodeSeekTheme {
        RulingScreen(
            state = RulingUiState(isLoading = false, records = previewRecords, page = 1, totalPages = 104),
            onBack = {},
            onPageSelected = {},
            onRetry = {},
            onOpenBrowser = {},
            onSignIn = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "9d 管理记录 · 接口待接入")
@Composable
private fun RulingNotWiredPreview() {
    NodeSeekTheme(darkTheme = true) {
        RulingScreen(
            state = RulingUiState(isLoading = false, error = NodeSeekError.NotWired),
            onBack = {},
            onPageSelected = {},
            onRetry = {},
            onOpenBrowser = {},
            onSignIn = {},
        )
    }
}
