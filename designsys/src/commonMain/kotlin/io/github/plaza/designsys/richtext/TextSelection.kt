package io.github.plaza.designsys.richtext

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuData
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates

/**
 * Lets a tap anywhere in [content] put down a selection made inside a [RichContent].
 *
 * Every post body is its own `SelectionContainer`, and a container only listens for taps inside its
 * own bounds: a selection made in one paragraph of the opening post stayed up through taps on the
 * comments, the toolbar's blank space, the gap between floors — anywhere but the body it started in.
 * One container around the whole list was the alternative, and was not taken: 全选 would then mean
 * the page, a selection could run from one floor's text into the next, and the docs give a lazy list
 * inside a container undefined behaviour for the rows that are not composed.
 *
 * Only a tap counts. A press that travels is a scroll — the platform keeps a selection through those
 * and so does this — and one held past the long-press timeout is the press that just made a new
 * selection somewhere else, which its own release must not take away again. The handles and the
 * floating toolbar are windows of their own, so dragging one never reaches this.
 */
@Composable
fun DismissTextSelectionOnTap(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val registry = remember { TextSelectionRegistry() }
    CompositionLocalProvider(LocalTextSelectionRegistry provides registry) {
        Box(
            modifier = modifier.pointerInput(registry) {
                awaitEachGesture {
                    // Initial pass and unconsumed or not: the tap usually lands on something that
                    // handles it — a button, a card, a link — and it still means "put that down".
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.id == down.id } ?: break
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                        if (change.changedToUpIgnoreConsumed()) {
                            if (change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                                registry.clearAll()
                            }
                            break
                        }
                    }
                }
            },
        ) {
            content()
        }
    }
}

/** The selection containers on screen, so that a tap outside every one of them can reach them. */
private class TextSelectionRegistry {
    private val states = mutableSetOf<SelectionState>()

    fun add(state: SelectionState) {
        states += state
    }

    fun remove(state: SelectionState) {
        states -= state
    }

    fun clearAll() {
        states.forEach { if (it.hasSelection()) it.clear() }
    }
}

/** Null outside [DismissTextSelectionOnTap]: a body there keeps the container's own behaviour. */
private val LocalTextSelectionRegistry = staticCompositionLocalOf<TextSelectionRegistry?> { null }

private fun SelectionState.hasSelection(): Boolean = selectedTexts.any { it.isNotEmpty() }

/**
 * A `SelectionContainer` that registers with [DismissTextSelectionOnTap] and whose toolbar leaves
 * without changing first.
 *
 * Two containers, and the outer one selects nothing. It is there for the toolbar: a container that
 * finds no toolbar provider above it installs the platform's own one *inside* itself, past the
 * reach of anything a caller provides, and nothing above a post body provides one — so wrapping the
 * provider from out here wraps `null`, and the first version of [SteadySelectionToolbar] was never
 * called once. The outer container installs the platform provider for its content, where it can be
 * read and wrapped, and the inner one then finds a provider and leaves it alone. The outer contributes
 * no menu items of its own: with nothing registered under it, 复制 and 全选 are both disabled there,
 * and disabled items are left out rather than greyed. The platform provider is `internal` in
 * foundation 1.13.0-alpha02 (`ProvidePlatformTextContextMenuToolbar`, "Consider making public");
 * calling that instead is the day this collapses into one container.
 */
@Composable
internal fun RichSelectionContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSelectionState()
    val registry = LocalTextSelectionRegistry.current
    if (registry != null) {
        DisposableEffect(registry, state) {
            registry.add(state)
            onDispose { registry.remove(state) }
        }
    }
    SelectionContainer(modifier = modifier) {
        DisableSelection {
            val platformToolbar = LocalTextContextMenuToolbarProvider.current
            val toolbar = remember(platformToolbar, state) {
                platformToolbar?.let { SteadySelectionToolbar(it, state) }
            }
            CompositionLocalProvider(LocalTextContextMenuToolbarProvider provides toolbar) {
                SelectionContainer(state = state, content = content)
            }
        }
    }
}

/**
 * Holds the toolbar's last items and position while the selection under it is being cleared.
 *
 * Without it, putting a selection down flashed a lone 全选 where the toolbar had been. Clearing
 * changes the menu's data — 复制 drops out with the selection, 全选 stays because the container is
 * no longer entirely selected — and the platform provider observes that data and invalidates the
 * floating `ActionMode` at once, while its `finish()` waits for the menu session's coroutine to be
 * resumed. In between, the toolbar is relaid out with the one item left; on One UI that is a whole
 * new pill fading in and back out. Compose 1.13.0-alpha02's `SelectionManager.onRelease()` hides the
 * toolbar and clears the selection in the same breath, and `filterTextContextMenuComponents` can
 * only take items away, which would invalidate the menu just the same. Remove this when the provider
 * stops redrawing a menu it has been told to close.
 */
private class SteadySelectionToolbar(
    private val platform: TextContextMenuProvider,
    private val state: SelectionState,
) : TextContextMenuProvider {
    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        platform.showTextContextMenu(
            object : TextContextMenuDataProvider by dataProvider {
                private var lastData: TextContextMenuData? = null
                private var lastBounds: Rect? = null

                override fun data(): TextContextMenuData {
                    if (!state.hasSelection()) lastData?.let { return it }
                    return dataProvider.data().also { lastData = it }
                }

                override fun contentBounds(destinationCoordinates: LayoutCoordinates): Rect {
                    if (!state.hasSelection()) lastBounds?.let { return it }
                    return dataProvider.contentBounds(destinationCoordinates).also { lastBounds = it }
                }
            },
        )
    }
}
