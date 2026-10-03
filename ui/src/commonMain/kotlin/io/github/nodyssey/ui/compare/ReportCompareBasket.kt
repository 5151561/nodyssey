package io.github.nodyssey.ui.compare

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.settings.ReportCompareEntry
import io.github.nodyssey.data.settings.ReportCompareStore
import kotlinx.coroutines.launch

/**
 * 测评对比's basket as a report card sees it: what is in it, and a way to put a report in or take
 * it out.
 *
 * A `CompositionLocal` for the reason [io.github.nodyssey.ui.common.LocalUserNotes] is one: a report
 * is drawn by [io.github.nodyssey.ui.richtext.PostRichContent], which six screens call and none of
 * them owns a ViewModel that has any business knowing about the basket.
 */
@Immutable
class ReportCompareBasket(
    private val entries: List<ReportCompareEntry>,
    private val onAdd: (ReportCompareEntry) -> Unit,
    private val onRemove: (ReportCompareEntry) -> Unit,
) {
    val size: Int get() = entries.size

    operator fun contains(entry: ReportCompareEntry): Boolean = entries.any { it.isSameAs(entry) }

    fun add(entry: ReportCompareEntry) = onAdd(entry)

    fun remove(entry: ReportCompareEntry) = onRemove(entry)
}

/** Null outside the app's root — a preview, a screen test — where the card offers no 加入对比. */
val LocalReportCompareBasket = staticCompositionLocalOf<ReportCompareBasket?> { null }

/**
 * Opens 测评对比, for the card's 对比 (n). Provided by the navigation host, the one place that can
 * push a screen; null elsewhere, where the button is left off.
 */
val LocalOpenReportCompare = compositionLocalOf<(() -> Unit)?> { null }

/** The basket in [store], as the value [LocalReportCompareBasket] provides. */
@Composable
fun rememberReportCompareBasket(store: ReportCompareStore): ReportCompareBasket {
    val entries by store.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    return remember(entries) {
        ReportCompareBasket(
            entries = entries,
            onAdd = { scope.launch { store.add(it) } },
            onRemove = { scope.launch { store.remove(it) } },
        )
    }
}

/**
 * Where a report on screen was posted: the thread, and once a floor has said so, the floor.
 *
 * What the basket shows as a column's name and what tapping that name opens. Only the thread screen
 * provides one; a report in a direct message or a space readme goes into the basket without it.
 */
@Immutable
data class ReportOrigin(
    val postId: Long,
    val threadTitle: String,
    val floor: String? = null,
)

val LocalReportOrigin = compositionLocalOf<ReportOrigin?> { null }

/** [content] with the thread's [LocalReportOrigin] narrowed to [floor]. */
@Composable
fun ReportOriginFloor(
    floor: String?,
    content: @Composable () -> Unit,
) {
    val origin = LocalReportOrigin.current
    CompositionLocalProvider(LocalReportOrigin provides origin?.copy(floor = floor), content = content)
}
