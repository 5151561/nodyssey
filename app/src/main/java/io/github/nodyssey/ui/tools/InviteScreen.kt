package io.github.nodyssey.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.ui.assets.InviteConfirmDialog
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.invite_body
import io.github.nodyssey.ui.resources.invite_buy
import io.github.nodyssey.ui.resources.invite_chicken_balance
import io.github.nodyssey.ui.resources.invite_title
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

@Composable
fun InviteRoute(
    viewModel: InviteViewModel,
    onBack: () -> Unit,
    onBuyOnSite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chickenCount by viewModel.chickenCount.collectAsStateWithLifecycle()
    InviteScreen(
        chickenCount = chickenCount,
        onBack = onBack,
        onBuy = onBuyOnSite,
        modifier = modifier,
    )
}

/**
 * 邀请好友 — one sentence and one button, which is all the site's own page has.
 *
 * There is no invite-code list to show: NodeSeek generates a code, shows it once and keeps no history
 * the app can read. Adding "已邀请 N 人" or a code list would be inventing a feature.
 *
 * The chicken balance is shown next to the button rather than only inside the confirmation, because
 * whether 1000 is affordable is the first thing anyone wants to know here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteScreen(
    chickenCount: Int?,
    onBack: () -> Unit,
    onBuy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }

    val appBarState = rememberOneHandAppBarState()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.invite_title),
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
                // Scrollable although the card fits: the app bar folds away on a scroll and this is
                // the only screen here with nothing to scroll, so without this the reader who pulls
                // it open can never put it back. It also earns its keep at the larger font scales.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Icon(
                            PlazaIcons.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                        Text(
                            text = stringResource(Res.string.invite_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = { confirming = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Icon(
                            PlazaIcons.ConfirmationNumber,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            stringResource(Res.string.invite_buy),
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                    chickenCount?.let {
                        Text(
                            text = stringResource(Res.string.invite_chicken_balance, it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (confirming) {
        InviteConfirmDialog(
            chickenCount = chickenCount,
            onConfirm = {
                confirming = false
                onBuy()
            },
            onDismiss = { confirming = false },
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 500, name = "9d 邀请好友")
@Composable
private fun InvitePreview() {
    PlazaTheme {
        InviteScreen(chickenCount = 344, onBack = {}, onBuy = {})
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 500, name = "9d 邀请好友 · dark")
@Composable
private fun InviteDarkPreview() {
    PlazaTheme(darkTheme = true) {
        InviteScreen(chickenCount = 1_240, onBack = {}, onBuy = {})
    }
}
