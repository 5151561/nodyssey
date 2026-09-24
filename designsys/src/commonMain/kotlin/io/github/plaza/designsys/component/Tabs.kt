package io.github.plaza.designsys.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.cardBorder
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
                height = UnderlineIndicatorHeight,
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
 * The track is drawn [PillTrackInset] inside the row's top and bottom edges: Material's tabs are a
 * fixed 48dp, which is the touch target this keeps, and the Lean round draws the control itself at
 * 34. A caller spacing the row against its neighbours counts from the track, 7dp in from the edge.
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
    val thumbShape = MaterialTheme.shapes.small
    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        // Clipped to the track as well as painted with it, so a tab's ripple stays on the control.
        modifier = modifier.heightIn(min = PillTabHeight).clip(PillTrackShape).background(track, PillTrackShape),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        divider = {},
        indicator = {
            Box(
                Modifier
                    .tabIndicatorOffset(selectedTabIndex, matchContentSize = false)
                    .zIndex(-1f)
                    .fillMaxHeight()
                    .padding(horizontal = PillThumbInset, vertical = PillTrackInset + PillThumbInset)
                    .cardShadow(thumbShape, layers.shadows)
                    .background(layers.raised, thumbShape)
                    .cardBorder(layers, thumbShape),
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
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
        tab.badge?.let {
            Badge { Text(it, style = LocalTextStyle.current.copy(fontFeatureSettings = TABULAR_FIGURES)) }
        }
    }
}

private val UnderlineIndicatorWidth = 44.dp
private val UnderlineIndicatorHeight = 2.dp

/** A minimum rather than a height: Material's 48dp touch target, and room to grow at a large font. */
private val PillTabHeight = 48.dp

/** How far the drawn track stands in from the row's edges — 48dp of target around a 34dp control. */
private val PillTrackInset = 7.dp

/** The thumb's margin inside the track. */
private val PillThumbInset = 3.dp

/** The track: [PillTrackInset] in from the top and the bottom, with 10dp corners. */
private val PillTrackShape: Shape =
    object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline {
            val inset = with(density) { PillTrackInset.toPx() }
            val radius = with(density) { 10.dp.toPx() }
            return Outline.Rounded(RoundRect(0f, inset, size.width, size.height - inset, CornerRadius(radius)))
        }
    }
