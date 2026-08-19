package io.github.nodyssey.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.data.settings.OPTIONAL_HOME_BOARD_SLUGS
import io.github.nodyssey.ui.common.BoardTag
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth

@Composable
fun PreferencesRoute(
    viewModel: PreferencesViewModel,
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

    PreferencesScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onHolidayThemeChange = viewModel::setHolidayTheme,
        onBoardHiddenChange = viewModel::setBoardHidden,
        modifier = modifier,
    )
}

/**
 * 偏好与首页版块 (d6 5/5).
 *
 * Every row wears its storage badge because the site itself distinguishes Local from Remote rows and
 * the difference is behavioural, not cosmetic: Remote rows follow the account, Local rows stay on
 * this device. Two of the site's preferences are deliberately absent: 新标签页打开主题帖 configures a
 * browser, and 自动夜间模式 with its 依据 is the site's way of asking for a dark theme, which 设置 ·
 * 主题 already answers on this device. The caption at the end of the 常用偏好 group says both, rather
 * than leaving site users to hunt for them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    state: PreferencesUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onHolidayThemeChange: (Boolean) -> Unit,
    onBoardHiddenChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appBarState = rememberOneHandAppBarState()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            OneHandTopAppBar(
                title = stringResource(R.string.account_preferences_title),
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
        Column(
            modifier =
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            AccountSectionLabel(stringResource(R.string.account_group_preference))

            PreferenceSwitchRow(
                title = stringResource(R.string.account_holiday_theme),
                subtitle = stringResource(R.string.account_holiday_theme_hint),
                local = false,
                checked = state.holidayTheme,
                onCheckedChange = onHolidayThemeChange,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                stringResource(R.string.account_preferences_omitted_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountSectionLabel(
                    text = stringResource(R.string.account_group_homepage),
                    modifier = Modifier.weight(1f),
                )
                StorageBadge(local = false)
            }
            Text(
                stringResource(R.string.account_home_boards_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )

            OPTIONAL_HOME_BOARD_SLUGS.forEachIndexed { index, slug ->
                HomeBoardSwitchRow(
                    slug = slug,
                    hidden = slug in state.hiddenBoards,
                    onHiddenChange = { hidden -> onBoardHiddenChange(slug, hidden) },
                )
                if (index != OPTIONAL_HOME_BOARD_SLUGS.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            Text(
                stringResource(R.string.account_storage_legend),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.sm),
            )
        }
    }
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    subtitle: String,
    local: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ).padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                StorageBadge(local = local)
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun HomeBoardSwitchRow(
    slug: String,
    hidden: Boolean,
    onHiddenChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = !hidden,
                role = Role.Switch,
                onValueChange = { shown -> onHiddenChange(!shown) },
            ).padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        BoardTag(title = optionalBoardTitle(slug), slug = slug)
        Text(
            optionalBoardTitle(slug),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f),
        )
        // The switch reads as "shown on the home feed", so it is the inverse of the stored flag.
        Switch(checked = !hidden, onCheckedChange = null)
    }
}

/**
 * Fixed titles rather than a lookup through the live board list: these three rows must exist even
 * when the board list has never loaded, because the switches are what decide that list's shape.
 */
@Composable
private fun optionalBoardTitle(slug: String): String =
    when (slug) {
        "trade" -> stringResource(R.string.account_board_trade)
        "life" -> stringResource(R.string.account_board_life)
        "photo-share" -> stringResource(R.string.account_board_photo)
        else -> slug
    }

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun PreferencesPreview() {
    PlazaTheme {
        PreferencesScreen(
            state =
            PreferencesUiState(
                holidayTheme = true,
                hiddenBoards = setOf("life", "photo-share"),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onHolidayThemeChange = {},
            onBoardHiddenChange = { _, _ -> },
        )
    }
}
