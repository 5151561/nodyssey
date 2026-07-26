package io.github.nsreader.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nsreader.R
import io.github.nsreader.data.Board
import io.github.nsreader.ui.common.BoardFamily
import io.github.nsreader.ui.common.boardFamilyColors
import io.github.nsreader.ui.common.boardFamilyOf
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Sizes
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.readableWidth

@Composable
fun HomeBoardsRoute(
    viewModel: HomeBoardsViewModel,
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

    HomeBoardsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onToggle = viewModel::toggle,
        onReset = viewModel::reset,
        // Saving returns to 账号设置, whose 首页版块 row immediately reads the new count — that is the
        // confirmation, and it is a truer one than a snackbar because it comes from the stored value.
        onSave = { viewModel.save(onSaved = onBack) },
        modifier = modifier,
    )
}

/**
 * 首页版块 (d6 4/4) — which boards appear on the home strip.
 *
 * Grouped under the same four headings the board tags are coloured by, so the dot beside each heading
 * is the colour the reader already associates with those boards from every list row. Thirteen flat
 * checkboxes would fit; four groups of three or four is what makes them scannable.
 *
 * Edits are local until 保存, so leaving without saving changes nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeBoardsScreen(
    state: HomeBoardsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grouped =
        remember(state.boards) {
            state.boards.groupBy { boardFamilyOf(it.slug, it.title) }
        }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_home_boards_title)) },
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
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AccountBottomBar {
                    TextButton(onClick = onReset) {
                        Text(stringResource(R.string.account_home_boards_reset))
                    }
                    Box(Modifier.weight(1f))
                    Button(
                        onClick = onSave,
                        enabled = state.selected.isNotEmpty(),
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Text(stringResource(R.string.account_home_boards_save, state.selected.size))
                    }
                }
            }
        },
    ) { padding ->
        if (!state.isLoading && state.boards.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.account_home_boards_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.xl),
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().readableWidth(),
        ) {
            item(key = "summary") {
                Text(
                    stringResource(
                        R.string.account_home_boards_summary,
                        state.selected.size,
                        state.total,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
                )
            }

            BoardFamily.entries.forEach { family ->
                val boards = grouped[family].orEmpty()
                if (boards.isEmpty()) return@forEach

                item(key = "family-${family.name}") {
                    FamilyHeading(family)
                }
                items(count = boards.size, key = { boards[it].slug.orEmpty() }) { index ->
                    val board = boards[index]
                    val slug = board.slug ?: return@items
                    BoardCheckRow(
                        board = board,
                        checked = slug in state.selected,
                        onToggle = { onToggle(slug) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FamilyHeading(family: BoardFamily) {
    val colors = boardFamilyColors(family)
    Row(
        modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.md, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(colors.container, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        Text(
            stringResource(family.labelRes),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BoardCheckRow(
    board: Board,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            // The whole row toggles, and the row is the accessibility node — a Checkbox with its own
            // click handler would announce twice and give a 20dp target inside a 48dp row.
            .toggleable(value = checked, onValueChange = { onToggle() }, role = Role.Checkbox)
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .heightIn(min = Sizes.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        /*
         * Two lines rather than title-then-description on one.
         *
         * The board list is the live one, and the site's descriptions run to a full sentence — 交易's is
         * thirty characters. Laid out side by side they squeezed the title to nothing and wrapped back
         * over it. d6 draws bare single-line rows because it was mocked without descriptions; keeping
         * them is worth the second line, since "沙盒主要用来测试发帖" is exactly what decides the tick.
         */
        Column(Modifier.weight(1f)) {
            Text(board.title, style = MaterialTheme.typography.bodyLarge)
            board.description?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun HomeBoardsPreview() {
    val boards =
        listOf(
            // The site's own descriptions, because their length is what the layout has to survive.
            Board("daily", "日常", "日常交流板块，通用话题分区"),
            Board("life", "生活", "聊聊工作、生活、感情，不要阴阳怪气"),
            Board("photo-share", "贴图", "贴图板块，一般发布日常图片/沙雕图/帅哥美女，禁止NSFW图"),
            Board("sandbox", "沙盒", "沙盒主要用来测试发帖"),
            Board("tech", "技术", "分享交流技术相关，比如技术方案分享，科技新闻，技术咨询与交流"),
            Board("dev", "Dev", "论坛开发及bug反馈区"),
            Board("review", "测评", "服务器测评，欢迎分享测试跑分及个人评价"),
            Board("info", "情报", "传递行业新闻，推广好项目好软件，推广个人开发者的创意作品"),
            Board("trade", "交易", "二手物品交易板块，交易内容不限于vps、电子设备等；禁止插广告；禁止非法交易"),
            Board("carpool", "拼车", "拼车拼团板块，包括但不限于Netflix，Office 365，Google One"),
            Board("promotion", "推广", "商家推广专区"),
            Board("expose", "曝光", "曝光不良商家与用户"),
            Board("inside", "内版", "内部版块"),
        )
    NodeSeekTheme {
        HomeBoardsScreen(
            state =
            HomeBoardsUiState(
                isLoading = false,
                boards = boards,
                selected = setOf("daily", "tech", "dev", "review", "info", "trade"),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onToggle = {},
            onReset = {},
            onSave = {},
        )
    }
}
