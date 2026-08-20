package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.post_badge_more
import io.github.plaza.designsys.component.BadgeChip
import io.github.plaza.designsys.component.BadgeTone
import org.jetbrains.compose.resources.stringResource

/**
 * The role badge family on a floor header — b1 §8.
 *
 * Mapped by the badge's *text* rather than its CSS class: the labels are what the requirements
 * document actually captured from the site, while the `role-*` class suffixes remain unverified.
 * Anything unrecognised (the site also ships `Dev`, which is not in the six) falls back to a
 * neutral chip instead of crashing an exhaustive `when`.
 */
enum class RoleBadgeStyle {
    ORIGINAL_POSTER,
    STAFF,
    RETIRED,
    BANNED,
    SCAMMER,
    NEUTRAL,
}

fun roleBadgeStyleOf(label: String): RoleBadgeStyle =
    when (label.replace('（', '(').replace('）', ')').trim()) {
        "楼主" -> RoleBadgeStyle.ORIGINAL_POSTER
        "服主", "管理" -> RoleBadgeStyle.STAFF
        "管理(退休)" -> RoleBadgeStyle.RETIRED
        "违规禁止" -> RoleBadgeStyle.BANNED
        "骗子" -> RoleBadgeStyle.SCAMMER
        else -> RoleBadgeStyle.NEUTRAL
    }

/** How many chips a header shows before folding into +N — b1 §8's stacking cap. */
const val MAX_ROLE_BADGES = 3

/**
 * The chips to draw and how many were folded away.
 *
 * The sort is stable, so a user whose badges all rank equally keeps the site's order (Lloyd stays
 * 楼主·服主·管理). Ranking exists for the truncated case only: the punishment badges are safety
 * signals, and dropping 骗子 while keeping a third staff chip would invert the point of showing
 * badges at all.
 */
fun visibleRoleBadges(labels: List<String>): Pair<List<String>, Int> {
    val ordered = labels.sortedBy { displayRank(roleBadgeStyleOf(it)) }
    return ordered.take(MAX_ROLE_BADGES) to (ordered.size - MAX_ROLE_BADGES).coerceAtLeast(0)
}

private fun displayRank(style: RoleBadgeStyle): Int =
    when (style) {
        RoleBadgeStyle.ORIGINAL_POSTER -> 0
        RoleBadgeStyle.SCAMMER -> 1
        RoleBadgeStyle.BANNED -> 2
        RoleBadgeStyle.STAFF -> 3
        RoleBadgeStyle.RETIRED -> 4
        RoleBadgeStyle.NEUTRAL -> 5
    }

/** Up to [MAX_ROLE_BADGES] chips plus a +N overflow chip, spaced as b1 §8 draws them. */
@Composable
fun RoleBadgeRow(
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val (shown, folded) = visibleRoleBadges(labels)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { label -> RoleBadge(label) }
        if (folded > 0) {
            BadgeChip(
                text = stringResource(Res.string.post_badge_more, folded),
                tone = RoleBadgeStyle.RETIRED.tone,
            )
        }
    }
}

@Composable
fun RoleBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    BadgeChip(text = label, tone = roleBadgeStyleOf(label).tone, modifier = modifier)
}

/**
 * How loudly each of the six speaks, which is the only part of this a shared chip can know.
 *
 * 骗子 gets [BadgeTone.Critical] rather than a second warning tone on purpose: it is the one badge a
 * reader is about to lose money by missing.
 */
private val RoleBadgeStyle.tone: BadgeTone
    get() = when (this) {
        RoleBadgeStyle.ORIGINAL_POSTER -> BadgeTone.Primary
        RoleBadgeStyle.STAFF -> BadgeTone.Accent
        RoleBadgeStyle.RETIRED -> BadgeTone.Muted
        RoleBadgeStyle.BANNED -> BadgeTone.Warning
        RoleBadgeStyle.SCAMMER -> BadgeTone.Critical
        RoleBadgeStyle.NEUTRAL -> BadgeTone.Neutral
    }
