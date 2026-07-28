package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.nodyssey.R
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.TABULAR_FIGURES

/**
 * The site's numeric pager, for the lists that have one instead of infinite scroll.
 *
 * Curated threads and the moderation log are read by page number on the site — "第 104 页" is how
 * people refer to them — and both run to three digits, so an endless scroll would replace a two-tap
 * jump with a hundred flings. The window is deliberately narrow: current page, its neighbours, and
 * the last page, with an ellipsis standing in for everything skipped.
 */
@Composable
fun NumericPager(
    page: Int,
    totalPages: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    approximateTotal: Boolean = false,
) {
    if (totalPages <= 1) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pageWindow(page, totalPages).forEach { entry ->
            when (entry) {
                null ->
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )

                else -> PageChip(number = entry, selected = entry == page, onClick = { onPageSelected(entry) })
            }
        }
        if (approximateTotal) {
            Text(
                text = stringResource(R.string.pager_total_approx, totalPages),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** `null` marks a gap the ellipsis stands in for. */
internal fun pageWindow(page: Int, totalPages: Int): List<Int?> {
    if (totalPages <= MAX_VISIBLE_PAGES) return (1..totalPages).toList()
    val current = page.coerceIn(1, totalPages)
    val head = (1..3).toList()
    val around = ((current - 1)..(current + 1)).filter { it in 1..totalPages }
    val numbers = (head + around + totalPages).distinct().sorted()

    return buildList {
        var previous: Int? = null
        numbers.forEach { number ->
            previous?.let { if (number - it > 1) add(null) }
            add(number)
            previous = number
        }
    }
}

private const val MAX_VISIBLE_PAGES = 6

@Composable
private fun PageChip(
    number: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor =
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { this.selected = selected },
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 32.dp, minHeight = 32.dp)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style =
                MaterialTheme.typography.labelLarge.copy(
                    fontFeatureSettings = TABULAR_FIGURES,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun NumericPagerPreview() {
    NodysseyTheme {
        NumericPager(page = 1, totalPages = 104, onPageSelected = {}, approximateTotal = true)
    }
}
