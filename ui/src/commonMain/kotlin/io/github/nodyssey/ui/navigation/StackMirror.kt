package io.github.nodyssey.ui.navigation

/**
 * Keeps a platform navigation stack — one view per entry — in step with a back stack of keys.
 *
 * The back stack stays the one source of truth: every screen still navigates with `backStack.add`
 * and `removeLastOrNull`, and [reconcile] turns the result into the views the platform should show.
 * The platform is allowed exactly one move of its own, popping — a swipe, a back button it draws, the
 * tab bar's pop-to-root — and [platformPopped] is how that is written back.
 *
 * Entries are matched by position and key equality, the way `NavDisplay` keys a stack it is handed
 * again: the run that two stacks share from the bottom keeps its views, and so its state; everything
 * above that run is released and rebuilt. Releasing is the caller's business — a view leaving the
 * screen may still be animating out, so [release] only says that it is no longer part of the stack.
 *
 * Common code rather than iOS code because none of it names UIKit, and it is the part of the shell a
 * mistake in would show as state lost or kept for the wrong screen — the part worth a test.
 */
internal class StackMirror<K : Any, V : Any>(
    private val create: (index: Int, key: K) -> V,
    private val release: (V) -> Unit,
) {
    private val keys = mutableListOf<K>()
    private val mirrored = mutableListOf<V>()

    /** The views in stack order, bottom first. */
    val views: List<V> get() = mirrored.toList()

    /**
     * Brings the mirror in line with [stack].
     *
     * @return the views the platform should now show, or null when [stack] is what is already shown —
     * which is what keeps a pop the platform made, written back through [platformPopped], from being
     * handed straight back to it as a change.
     */
    fun reconcile(stack: List<K>): List<V>? {
        var shared = 0
        while (shared < keys.size && shared < stack.size && keys[shared] == stack[shared]) shared++
        if (shared == keys.size && shared == stack.size) return null

        while (mirrored.size > shared) {
            keys.removeAt(keys.lastIndex)
            release(mirrored.removeAt(mirrored.lastIndex))
        }
        for (index in shared until stack.size) {
            keys += stack[index]
            mirrored += create(index, stack[index])
        }
        return views
    }

    /**
     * The platform is down to [remaining] views on its own.
     *
     * @return how many keys the back stack has to drop to match — zero when nothing was popped, which
     * is also what a cancelled swipe reports.
     */
    fun platformPopped(remaining: Int): Int {
        val dropped = (mirrored.size - remaining).coerceAtLeast(0)
        repeat(dropped) {
            keys.removeAt(keys.lastIndex)
            release(mirrored.removeAt(mirrored.lastIndex))
        }
        return dropped
    }
}
