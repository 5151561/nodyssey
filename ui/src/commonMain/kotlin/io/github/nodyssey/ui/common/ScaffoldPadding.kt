package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A `Scaffold`'s inner padding minus its bottom edge, for a tab root whose list runs on under the
 * bottom bar.
 *
 * On iOS the tab bar is Liquid Glass floating over the content, and the glass only shows anything if
 * there is content under it. Padding the whole screen by the scaffold's bottom inset — which on that
 * side is the bar's height — stopped every list at the bar's top edge and left the glass over an empty
 * strip of page. The bottom goes to the list as content padding instead, so the last row still scrolls
 * clear of the bar, and to whatever floats at the bottom of the screen so it stays above it.
 *
 * On Android the bar is opaque and the inset under it is already consumed by `NavigationSuiteScaffold`,
 * so the same split draws what it drew before.
 */
@Composable
internal fun PaddingValues.withoutBottom(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection),
        top = calculateTopPadding(),
        end = calculateEndPadding(layoutDirection),
        bottom = 0.dp,
    )
}
