package io.github.nodyssey.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.sort_by_post_time
import io.github.nodyssey.ui.resources.sort_by_reply_time
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One entry of a menu that has a current answer — a sort order, a site, a palette style, a language.
 *
 * Material's [SelectableDropdownMenuItem]: the current entry sits on a tonal pill with a tick, takes
 * the menu's first/middle/last corners from [index] and [count], and is announced as selected — the
 * tick is decoration, the `selected` state is what a screen reader says.
 */
@Composable
internal fun SelectableMenuItem(
    selected: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    SelectableDropdownMenuItem(
        selected = selected,
        onClick = onClick,
        text = text,
        shapes = MenuDefaults.itemShape(index, count),
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingContent = { if (selected) Icon(Icons.Default.Check, contentDescription = null) },
        colors =
        MenuDefaults.selectableItemColors(
            containerColor = Color.Transparent,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedTrailingContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

/** The order the site's own sort menu lists them in, and the order every sort picker here uses. */
internal val FeedSortOrder = listOf(FeedSort.LAST_REPLY, FeedSort.POST_TIME)

internal fun FeedSort.labelRes(): StringResource =
    if (this == FeedSort.POST_TIME) Res.string.sort_by_post_time else Res.string.sort_by_reply_time

/** One order in a feed's sort menu — the home feed's and the search results' are the same menu. */
@Composable
internal fun SortMenuItem(
    sort: FeedSort,
    current: FeedSort,
    onSelect: (FeedSort) -> Unit,
) {
    val isCurrent = sort == current
    SelectableMenuItem(
        selected = isCurrent,
        index = FeedSortOrder.indexOf(sort),
        count = FeedSortOrder.size,
        onClick = { onSelect(sort) },
        text = { Text(stringResource(sort.labelRes()), fontWeight = if (isCurrent) FontWeight.SemiBold else null) },
    )
}
