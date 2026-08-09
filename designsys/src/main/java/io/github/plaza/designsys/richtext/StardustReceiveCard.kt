package io.github.plaza.designsys.richtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.R
import io.github.plaza.designsys.component.TonalTag
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES

/** The 🌌 the site prints beside every stardust figure, here and in its own ledger. */
private const val STARDUST_GLYPH = "🌌"

/**
 * A 星辰收款码 as it appears in a body: who is collecting, how much, and what for.
 *
 * Everything drawn here comes out of the marker itself, which is why — unlike [VotePlaceholderCard] —
 * this is a complete card rather than a stand-in. A signature, a direct message or an editor preview
 * has no business issuing requests, and none of them needs to: the ask does not change. What those
 * callers do not get is [footer], where the thread screen puts the live tally and the 付款 button.
 *
 * [avatarUrl] is the caller's because building one is a fact about a particular forum. Without it the
 * avatar falls back to the uid's initial, which is still an identity of sorts and never a blank hole.
 */
@Composable
fun StardustReceiveCard(
    node: RichNode.StardustReceive,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    footer: @Composable () -> Unit = {},
) {
    VoteCardSurface(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(
                url = avatarUrl,
                name = node.memberId.toString(),
                size = 44.dp,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${node.amount} $STARDUST_GLYPH",
                    // The amount is the one number a reader checks against what they meant to pay.
                    style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = TABULAR_FIGURES,
                    ),
                )
                if (node.description.isNotBlank()) {
                    Text(
                        text = node.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.richtext_stardust_ref_id, node.refId),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (node.onetime) {
                    TonalTag(
                        text = stringResource(R.string.richtext_stardust_onetime),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        footer()
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun StardustReceiveCardPreview() {
    PlazaTheme {
        StardustReceiveCard(
            node =
            RichNode.StardustReceive(
                memberId = 52425,
                refId = 100,
                amount = 2,
                description = "请我喝杯咖啡",
                onetime = true,
            ),
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}
