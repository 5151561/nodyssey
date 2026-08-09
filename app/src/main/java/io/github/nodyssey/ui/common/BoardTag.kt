package io.github.nodyssey.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nodyssey.R
import io.github.plaza.designsys.theme.LocalNodysseyExtraColors

/**
 * The board tag that appears on every list row and at the top of every thread.
 *
 * Fifteen boards, four colours. Giving each board its own hue was the obvious first idea and turns
 * the list into a rainbow, which destroys the thing the colour was for — being able to tell at a
 * glance whether a row is a trade post or a technical one. Grouping by *kind* keeps that signal and
 * costs nothing in scanability.
 */
@Immutable
internal data class BoardFamilyColors(val container: Color, val content: Color)

/**
 * Declaration order is the order the families are *shown* in, not an accident.
 *
 * The home-board picker lists all thirteen boards under these four headings and iterates
 * [BoardFamily.entries] to do it, so reordering this enum reorders that screen.
 */
internal enum class BoardFamily(
    @StringRes val labelRes: Int,
) {
    /** 日常 / 生活 / 贴图 / 无意义 / 沙盒 — everything else. */
    Everyday(R.string.board_family_everyday),

    /** 技术 / Dev / 测评 / 情报 — the reason most people are here. */
    Technical(R.string.board_family_technical),

    /** 交易 / 拼车 / 推广 — money is changing hands. */
    Trade(R.string.board_family_trade),

    /** 曝光 / 内版 — read these with more care than the rest. */
    Flagged(R.string.board_family_flagged),
}

private val FAMILY_BY_SLUG =
    mapOf(
        "tech" to BoardFamily.Technical,
        "dev" to BoardFamily.Technical,
        "review" to BoardFamily.Technical,
        "info" to BoardFamily.Technical,
        "trade" to BoardFamily.Trade,
        "carpool" to BoardFamily.Trade,
        "promotion" to BoardFamily.Trade,
        "daily" to BoardFamily.Everyday,
        "life" to BoardFamily.Everyday,
        "photo-share" to BoardFamily.Everyday,
        "meaningless" to BoardFamily.Everyday,
        "sandbox" to BoardFamily.Everyday,
        "expose" to BoardFamily.Flagged,
        "inside" to BoardFamily.Flagged,
    )

// The site renames boards more often than it renumbers them, but a scraped row may carry only the
// display title, so the Chinese name is a second key rather than the only one.
private val FAMILY_BY_TITLE =
    mapOf(
        "技术" to BoardFamily.Technical,
        "Dev" to BoardFamily.Technical,
        "测评" to BoardFamily.Technical,
        "情报" to BoardFamily.Technical,
        "交易" to BoardFamily.Trade,
        "拼车" to BoardFamily.Trade,
        "推广" to BoardFamily.Trade,
        "日常" to BoardFamily.Everyday,
        "生活" to BoardFamily.Everyday,
        "贴图" to BoardFamily.Everyday,
        "无意义" to BoardFamily.Everyday,
        "沙盒" to BoardFamily.Everyday,
        "曝光" to BoardFamily.Flagged,
        "内版" to BoardFamily.Flagged,
    )

/** The family a board belongs to; slug first, since the site renames boards more often than it re-keys them. */
internal fun boardFamilyOf(slug: String?, title: String?): BoardFamily =
    FAMILY_BY_SLUG[slug]
        ?: FAMILY_BY_TITLE[title]
        ?: BoardFamily.Everyday

@Composable
internal fun boardFamilyColors(family: BoardFamily): BoardFamilyColors {
    val scheme = MaterialTheme.colorScheme
    val extra = LocalNodysseyExtraColors.current
    return when (family) {
        BoardFamily.Technical -> BoardFamilyColors(scheme.primaryContainer, scheme.onPrimaryContainer)
        BoardFamily.Trade -> BoardFamilyColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        BoardFamily.Everyday -> BoardFamilyColors(scheme.secondaryContainer, scheme.onSecondaryContainer)
        BoardFamily.Flagged -> BoardFamilyColors(extra.warningContainer, extra.onWarningContainer)
    }
}

/**
 * A tonal board tag.
 *
 * The site's markup drops the board on some pages, so callers pass a nullable title and this draws
 * nothing at all when it is missing — the meta row keeps its height either way, which is what stops
 * the list from twitching as pages load.
 */
@Composable
fun BoardTag(
    title: String?,
    slug: String?,
    modifier: Modifier = Modifier,
) {
    if (title.isNullOrBlank()) return

    val colors = boardFamilyColors(boardFamilyOf(slug, title))

    Text(
        text = title,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        color = colors.content,
        modifier =
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.container)
            .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}
