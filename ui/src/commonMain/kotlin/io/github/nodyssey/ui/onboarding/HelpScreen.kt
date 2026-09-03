package io.github.nodyssey.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.help_app_links_body
import io.github.nodyssey.ui.resources.help_app_links_title
import io.github.nodyssey.ui.resources.help_boards_body
import io.github.nodyssey.ui.resources.help_boards_title
import io.github.nodyssey.ui.resources.help_draft_body
import io.github.nodyssey.ui.resources.help_draft_title
import io.github.nodyssey.ui.resources.help_home_tap_body
import io.github.nodyssey.ui.resources.help_home_tap_title
import io.github.nodyssey.ui.resources.help_imagehost_body
import io.github.nodyssey.ui.resources.help_imagehost_title
import io.github.nodyssey.ui.resources.help_native_body
import io.github.nodyssey.ui.resources.help_native_title
import io.github.nodyssey.ui.resources.help_one_hand_body
import io.github.nodyssey.ui.resources.help_one_hand_title
import io.github.nodyssey.ui.resources.help_page_bar_body
import io.github.nodyssey.ui.resources.help_page_bar_title
import io.github.nodyssey.ui.resources.help_preview_body
import io.github.nodyssey.ui.resources.help_preview_title
import io.github.nodyssey.ui.resources.help_replay
import io.github.nodyssey.ui.resources.help_replay_hint
import io.github.nodyssey.ui.resources.help_section_composer
import io.github.nodyssey.ui.resources.help_section_confusing
import io.github.nodyssey.ui.resources.help_section_home
import io.github.nodyssey.ui.resources.help_sort_body
import io.github.nodyssey.ui.resources.help_sort_title
import io.github.nodyssey.ui.resources.help_title
import io.github.nodyssey.ui.resources.help_toolbar_body
import io.github.nodyssey.ui.resources.help_toolbar_title
import io.github.nodyssey.ui.resources.onboarding_app_links_action
import io.github.nodyssey.ui.resources.settings_app_links_hint_on
import io.github.nodyssey.ui.settings.SettingsGroup
import io.github.nodyssey.ui.settings.SettingsRow
import io.github.nodyssey.ui.settings.SettingsSectionTitle
import io.github.nodyssey.ui.settings.rememberAppLinkHandlingEnabled
import io.github.nodyssey.ui.settings.rememberAppLinkSettingsLauncher
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.groupShape
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

/**
 * 使用帮助 — the guide's ground, kept where it can be re-read, plus what the guide had no room for.
 *
 * A child of 关于 rather than a top-level row of 设置: nothing on it is a control, and a page of prose
 * among the switches would be the one row on that screen that changes nothing. It answers the two
 * questions the guide answers and eight more, grouped the way readers arrive at them — "this looks
 * broken", "what does the feed do", "how do I post" — rather than by which screen owns the feature.
 *
 * The 站内链接 entry is the one that does something: it carries the same jump into the system settings
 * that 设置 › 内容 does, because a paragraph explaining a switch with no way to reach it is a paragraph
 * that ends in a dead end.
 */
@Composable
fun HelpRoute(
    onBack: () -> Unit,
    onReplayGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HelpScreen(
        onBack = onBack,
        onReplayGuide = onReplayGuide,
        appLinksEnabled = rememberAppLinkHandlingEnabled(),
        onOpenAppLinkSettings = rememberAppLinkSettingsLauncher(),
        modifier = modifier,
    )
}

/**
 * @param appLinksEnabled whether the system routes nodeseek.com links here, or null where the
 * platform has no such notion — on the same terms as the row in 设置; see
 * [rememberAppLinkHandlingEnabled].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBack: () -> Unit,
    onReplayGuide: () -> Unit,
    modifier: Modifier = Modifier,
    appLinksEnabled: Boolean? = null,
    onOpenAppLinkSettings: () -> Unit = {},
) {
    val appBarState = rememberOneHandAppBarState()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.help_title),
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
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SettingsSectionTitle(stringResource(Res.string.help_section_confusing))
            SettingsGroup {
                HelpItem(
                    title = stringResource(Res.string.help_one_hand_title),
                    body = stringResource(Res.string.help_one_hand_body),
                    top = true,
                )
                HelpItem(
                    title = stringResource(Res.string.help_app_links_title),
                    body = stringResource(Res.string.help_app_links_body),
                ) {
                    // Absent where the platform has no such switch — see the parameter's doc.
                    when (appLinksEnabled) {
                        false ->
                            TextButton(onClick = onOpenAppLinkSettings) {
                                Text(stringResource(Res.string.onboarding_app_links_action))
                            }

                        true ->
                            Text(
                                text = stringResource(Res.string.settings_app_links_hint_on),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )

                        null -> Unit
                    }
                }
                HelpItem(
                    title = stringResource(Res.string.help_native_title),
                    body = stringResource(Res.string.help_native_body),
                    bottom = true,
                )
            }

            SettingsSectionTitle(stringResource(Res.string.help_section_home))
            SettingsGroup {
                HelpItem(
                    title = stringResource(Res.string.help_boards_title),
                    body = stringResource(Res.string.help_boards_body),
                    top = true,
                )
                HelpItem(
                    title = stringResource(Res.string.help_home_tap_title),
                    body = stringResource(Res.string.help_home_tap_body),
                )
                HelpItem(
                    title = stringResource(Res.string.help_sort_title),
                    body = stringResource(Res.string.help_sort_body),
                )
                HelpItem(
                    title = stringResource(Res.string.help_page_bar_title),
                    body = stringResource(Res.string.help_page_bar_body),
                    bottom = true,
                )
            }

            SettingsSectionTitle(stringResource(Res.string.help_section_composer))
            SettingsGroup {
                HelpItem(
                    title = stringResource(Res.string.help_toolbar_title),
                    body = stringResource(Res.string.help_toolbar_body),
                    top = true,
                )
                HelpItem(
                    title = stringResource(Res.string.help_draft_title),
                    body = stringResource(Res.string.help_draft_body),
                )
                HelpItem(
                    title = stringResource(Res.string.help_preview_title),
                    body = stringResource(Res.string.help_preview_body),
                )
                HelpItem(
                    title = stringResource(Res.string.help_imagehost_title),
                    body = stringResource(Res.string.help_imagehost_body),
                    bottom = true,
                )
            }

            SettingsGroup {
                SettingsRow(
                    top = true,
                    bottom = true,
                    leading = { Icon(PlazaIcons.WavingHand, contentDescription = null) },
                    title = stringResource(Res.string.help_replay),
                    subtitle = stringResource(Res.string.help_replay_hint),
                    onClick = onReplayGuide,
                )
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/**
 * One entry: a heading and a paragraph, in the same grouped-card rhythm the settings screens use.
 *
 * `bodyMedium` rather than the `labelSmall` a settings row gives its subtitle. A subtitle qualifies
 * a control the reader can already see, and four words is the whole job; these paragraphs *are* the
 * content, and set at that size they would be a wall nobody reads.
 */
@Composable
private fun HelpItem(
    title: String,
    body: String,
    top: Boolean = false,
    bottom: Boolean = false,
    action: @Composable (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = groupShape(first = top, last = bottom),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action?.let {
                Row(modifier = Modifier.padding(top = Spacing.xs)) { it() }
            }
        }
    }
}

@Preview
@Composable
private fun HelpScreenPreview() {
    PlazaTheme {
        HelpScreen(onBack = {}, onReplayGuide = {}, appLinksEnabled = false)
    }
}
