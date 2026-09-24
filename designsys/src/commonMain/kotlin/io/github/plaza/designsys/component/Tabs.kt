package io.github.plaza.designsys.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.cardShadow

/**
 * One tab's label and, optionally, the count badge beside it — already formatted, so a caller that
 * caps at 99+ says so itself.
 */
data class TabLabel(
    val text: String,
    val badge: String? = null,
)

/**
 * Tabs that switch between lists — 互动 / 私信, 帖子 / 用户 results, 关注 / 粉丝.
 *
 * Material's [PrimaryTabRow], flush with the page (no container of its own) and with the indicator a
 * fixed 56dp under the label rather than the label's width, which is how the artboards draw it: tabs
 * of different label lengths still carry the same mark.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnderlineTabRow(
    selectedTabIndex: Int,
    tabs: List<TabLabel>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = Color.Transparent,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selectedTabIndex, matchContentSize = false),
                width = UnderlineIndicatorWidth,
            )
        },
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedTabIndex
            Tab(
                selected = selected,
                onClick = { onSelect(index) },
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { TabText(tab, selected) },
            )
        }
    }
}

/**
 * Tabs as a white thumb sliding on a tonal track — the 帖子 / 用户 switch at the head of search, the
 * 概况 / 主题帖 / 评论 switch on a member's space.
 *
 * Still Material's [PrimaryTabRow] — selection semantics, keyboard focus and the sliding animation
 * are its own — with the indicator drawn as the thumb behind the labels instead of a line under them.
 * Light draws the track a step darker than the page and the thumb white. Dark recesses the track to
 * the inset tone, the darkest on the ladder, under the raised thumb: with the track at the card tone
 * the two were one step apart and the selected tab barely read as selected. On paper the track is
 * the card and the thumb's outline does the separating.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PillTabRow(
    selectedTabIndex: Int,
    tabs: List<TabLabel>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layers = LocalPlazaLayers.current
    val track =
        when {
            layers.shadows -> MaterialTheme.colorScheme.surfaceContainerHigh
            layers.cardBorder != null -> layers.card
            else -> layers.inset
        }
    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier.clip(CircleShape).heightIn(min = PillTabHeight),
        containerColor = track,
        contentColor = MaterialTheme.colorScheme.onSurface,
        divider = {},
        indicator = {
            Box(
                Modifier
                    .tabIndicatorOffset(selectedTabIndex, matchContentSize = false)
                    .zIndex(-1f)
                    .fillMaxHeight()
                    .padding(4.dp)
                    .cardShadow(CircleShape, layers.shadows)
                    .background(layers.raised, CircleShape)
                    .then(layers.cardBorder?.let { Modifier.border(1.dp, it, CircleShape) } ?: Modifier),
            )
        },
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedTabIndex
            Tab(
                selected = selected,
                onClick = { onSelect(index) },
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.heightIn(min = PillTabHeight),
                text = { TabText(tab, selected) },
            )
        }
    }
}

@Composable
private fun TabText(
    tab: TabLabel,
    selected: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = tab.text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
        tab.badge?.let {
            Badge { Text(it, style = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR_FIGURES)) }
        }
    }
}

private val UnderlineIndicatorWidth = 56.dp

/** A minimum rather than a height: Material's 48dp touch target, and room to grow at a large font. */
private val PillTabHeight = 48.dp
