package io.github.nodyssey.ui.postlist

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import io.github.nodyssey.model.FeedSort

/**
 * Where each of 首页's boards was left off.
 *
 * The boards are pages of one pager now, so "the feed's scroll position" is no longer a single
 * number: swiping to the next board and back has to return to the row it was left on, the same way
 * coming back from a thread does. One [LazyListState] per board is what that is.
 *
 * It is owned by `MainNavigation` rather than by the screen for the reason the single state was: a
 * compact `NavDisplay` removes the list while a thread is on screen, so the positions have to
 * outlive that composition. Held here rather than in the ViewModel because a `LazyListState` is a
 * property of the list on screen and not of the data behind it.
 *
 * 排序 is part of the identity because the two orders are two different feeds — the row a reader was
 * on in 新评论 is not a row in 新帖子 — so switching order starts every board at the top by
 * construction, and the states the old order was using are dropped rather than left to grow.
 */
class HomeFeedStates private constructor(
    private var sort: FeedSort?,
    private val states: MutableMap<String, LazyListState>,
) {
    constructor() : this(null, mutableMapOf())

    /** [slug]'s list state under [sort], created on first use. */
    fun listState(
        slug: String?,
        sort: FeedSort,
    ): LazyListState {
        if (sort != this.sort) {
            this.sort = sort
            states.clear()
        }
        return states.getOrPut(slug ?: FRONT_PAGE_KEY) { LazyListState() }
    }

    companion object {
        /**
         * Survives process death, which is what the single hoisted list state did before it.
         *
         * A flat list of primitives rather than a map of `LazyListState`s: the platform's saved
         * state holds primitives, and a row index with its pixel offset is the whole of what a list
         * position is. The order is the sort's name followed by a board, an index and an offset for
         * every state there is.
         */
        val Saver: Saver<HomeFeedStates, Any> =
            listSaver(
                save = { holder ->
                    buildList {
                        add(holder.sort?.name.orEmpty())
                        holder.states.forEach { (key, state) ->
                            add(key)
                            add(state.firstVisibleItemIndex)
                            add(state.firstVisibleItemScrollOffset)
                        }
                    }
                },
                restore = { saved: List<Any> ->
                    val sort = FeedSort.entries.firstOrNull { it.name == saved.firstOrNull() }
                    val states = mutableMapOf<String, LazyListState>()
                    saved.drop(1).chunked(3).forEach { (key, index, offset) ->
                        if (key is String && index is Int && offset is Int) {
                            states[key] = LazyListState(index, offset)
                        }
                    }
                    HomeFeedStates(sort, states)
                },
            )
    }
}
