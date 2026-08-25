package io.github.nodyssey.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import org.junit.Assert.assertEquals

/**
 * Every clickable node on the current screen has a touch target of at least 48×48dp.
 *
 * A sweep rather than one assertion per icon, because the per-icon shape is exactly how the gap
 * happened: the review found a single 48dp assertion in the whole repository, guarding one key of
 * one toolbar. Material's `minimumInteractiveComponentSize` extends small visuals to 48dp on its
 * own — `touchBoundsInRoot` is measured *after* that extension — so a violation here means a
 * component that opted out of the floor or a custom clickable that never had one, which is the
 * mistake worth catching.
 *
 * The half-dp slack absorbs px↔dp rounding on odd densities, nothing more.
 */
internal fun SemanticsNodeInteractionsProvider.assertEveryTouchTargetAtLeast48dp() {
    val nodes = onAllNodes(hasClickAction()).fetchSemanticsNodes()
    val violations =
        nodes.mapNotNull { node ->
            val bounds = node.touchBoundsInRoot
            // A clickable with no size was never placed — a row scrolled out of a lazy list's
            // window, not a target anyone can miss.
            if (bounds.width == 0f && bounds.height == 0f) return@mapNotNull null
            val density = node.layoutInfo.density.density
            val width = bounds.width / density
            val height = bounds.height / density
            if (width + 0.5f >= 48f && height + 0.5f >= 48f) return@mapNotNull null
            val label =
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                    ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
                    ?: "unnamed node ${node.id}"
            "$label: ${width}dp × ${height}dp"
        }
    assertEquals("Touch targets under 48dp", emptyList<String>(), violations)
}
