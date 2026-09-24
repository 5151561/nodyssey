package io.github.nodyssey.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onAllNodesWithText
import io.github.plaza.designsys.theme.Spacing
import org.junit.Assert.assertTrue

/**
 * On a wide window a page's content starts in the same centred column as the big title over it.
 *
 * `OneHandTopAppBar` lays its expanded title in the `readableWidth` column, [Spacing.xl] in from the
 * column's edge, for every page. A page whose own content was not in that column ran it from the
 * window's edge instead, and the title then sat a few hundred dp in from the list it headed.
 * [content] is any node of the page's that belongs at that edge or inside it.
 *
 * The big title is the lower of the title's two nodes; the other is the collapsed bar's.
 */
internal fun SemanticsNodeInteractionsProvider.assertContentUnderBigTitle(
    title: String,
    content: SemanticsNodeInteraction,
) {
    val bigTitle = onAllNodesWithText(title, useUnmergedTree = true).fetchSemanticsNodes().maxBy { it.boundsInRoot.top }
    val density = bigTitle.layoutInfo.density.density
    val columnStart = bigTitle.boundsInRoot.left / density - Spacing.xl.value
    val contentStart = content.fetchSemanticsNode().boundsInRoot.left / density
    // A column centred in the window, not the window itself — or this would pass on a phone.
    assertTrue("the title's column starts at ${columnStart}dp", columnStart > Spacing.xl.value)
    assertTrue("title's column at ${columnStart}dp, content at ${contentStart}dp", contentStart + 0.5f >= columnStart)
}
