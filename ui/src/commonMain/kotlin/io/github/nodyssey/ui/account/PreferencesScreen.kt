package io.github.nodyssey.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.settings.OPTIONAL_HOME_BOARD_SLUGS
import io.github.nodyssey.ui.common.BoardTag
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.account_board_life
import io.github.nodyssey.ui.resources.account_board_photo
import io.github.nodyssey.ui.resources.account_board_trade
import io.github.nodyssey.ui.resources.account_group_homepage
import io.github.nodyssey.ui.resources.account_group_preference
import io.github.nodyssey.ui.resources.account_holiday_theme
import io.github.nodyssey.ui.resources.account_holiday_theme_hint
import io.github.nodyssey.ui.resources.account_home_boards_hint
import io.github.nodyssey.ui.resources.account_preferences_omitted_note
import io.github.nodyssey.ui.resources.account_preferences_title
import io.github.nodyssey.ui.resources.account_storage_legend
import io.github.nodyssey.ui.resources.action_back
import io.github.plaza.designsys.component.GroupDividerInset
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.GroupedListItemSwitch
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.SectionNote
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

@Composable
fun PreferencesRoute(
    viewModel: PreferencesViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    /** Clears a Cloudflare challenge, then comes back to this page. */
    onVerify: (String) -> Unit,
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
                title = stringResource(Res.string.account_preferences_title),
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
                .padding(horizontal = LayerPageGutter, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SectionLabel(stringResource(Res.string.account_group_preference))

            Column {
                PreferenceSwitchRow(
                    title = stringResource(Res.string.account_holiday_theme),
                    subtitle = stringResource(Res.string.account_holiday_theme_hint),
                    local = false,
                    checked = state.holidayTheme,
                    onCheckedChange = onHolidayThemeChange,
                )
            }

            SectionNote(
                stringResource(Res.string.account_preferences_omitted_note),
                contentPadding = PaddingValues(Spacing.xs),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(
                    text = stringResource(Res.string.account_group_homepage),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = Spacing.md),
                )
                StorageBadge(local = false)
            }
            SectionNote(
                stringResource(Res.string.account_home_boards_hint),
                contentPadding = PaddingValues(horizontal = Spacing.xs),
            )

            Column {
                OPTIONAL_HOME_BOARD_SLUGS.forEachIndexed { index, slug ->
                    HomeBoardSwitchRow(
                        first = index == 0,
                        last = index == OPTIONAL_HOME_BOARD_SLUGS.lastIndex,
                        slug = slug,
                        hidden = slug in state.hiddenBoards,
                        onHiddenChange = { hidden -> onBoardHiddenChange(slug, hidden) },
                    )
                }
            }

            Text(
                stringResource(Res.string.account_storage_legend),
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
    GroupedListItem(
        first = true,
        last = true,
        checked = checked,
        onCheckedChange = onCheckedChange,
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(title)
                StorageBadge(local = local)
            }
        },
        supportingContent = { Text(subtitle) },
        trailingContent = { GroupedListItemSwitch(checked = checked) },
    )
}

@Composable
private fun HomeBoardSwitchRow(
    first: Boolean,
    last: Boolean,
    slug: String,
    hidden: Boolean,
    onHiddenChange: (Boolean) -> Unit,
) {
    // The switch reads as "shown on the home feed", so it is the inverse of the stored flag.
    GroupedListItem(
        first = first,
        last = last,
        checked = !hidden,
        onCheckedChange = { shown -> onHiddenChange(!shown) },
        leadingContent = { BoardTag(title = optionalBoardTitle(slug), slug = slug) },
        headlineContent = { Text(optionalBoardTitle(slug)) },
        trailingContent = { GroupedListItemSwitch(checked = !hidden) },
        dividerInset = GroupDividerInset,
    )
}

/**
 * Fixed titles rather than a lookup through the live board list: these three rows must exist even
 * when the board list has never loaded, because the switches are what decide that list's shape.
 */
@Composable
private fun optionalBoardTitle(slug: String): String =
    when (slug) {
        "trade" -> stringResource(Res.string.account_board_trade)
        "life" -> stringResource(Res.string.account_board_life)
        "photo-share" -> stringResource(Res.string.account_board_photo)
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
