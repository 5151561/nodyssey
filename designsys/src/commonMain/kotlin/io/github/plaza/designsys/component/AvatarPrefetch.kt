package io.github.plaza.designsys.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Scale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/** See [PrefetchAvatars] — about a screen and a half of rows ahead on a phone. */
private const val ROWS_AHEAD = 10

/** See [PrefetchAvatars] — long enough that a fling passes through without fetching anything. */
private const val SETTLE_MILLIS = 120L

/**
 * Fetches the avatars of rows the reader is about to reach.
 *
 * A list page arrives as one document already carrying every row's avatar *address* — but an
 * address is not a picture, and the fifty pictures behind it are fifty more downloads. [UserAvatar]
 * asks for one only once its row is composed, which on a feed means the reader scrolls onto a row
 * and *then* waits for its face to appear. A browser handed the same document does not behave that
 * way: its parser sees all fifty `<img>` at once and has them all in flight before anything is on
 * screen, which is why the same page feels like it arrives complete.
 *
 * This is that behaviour, bounded. A page of avatars is several megabytes at the size the site
 * serves them, and the reader may never scroll past the tenth row, so what is warmed is a window
 * just past the fold rather than the whole page: far enough ahead that a picture is ready when its
 * row arrives, short enough that nothing is spent on rows nobody reaches.
 *
 * Emits nothing. [size] is not decoration: a warmed entry of the wrong size is a second decode
 * rather than a hit, so it must be the size the rows will draw the avatar at.
 */
@Composable
fun PrefetchAvatars(
    listState: LazyListState,
    itemCount: Int,
    size: Dp,
    rowsAhead: Int = ROWS_AHEAD,
    urlAt: (index: Int) -> String?,
) {
    val context = LocalPlatformContext.current
    val loader = remember(context) { SingletonImageLoader.get(context) }
    val pixels = with(LocalDensity.current) { size.roundToPx() }
    // Read through rather than captured: both change as pages load, and neither is worth tearing
    // the effect down and restarting the scroll subscription over.
    val counted by rememberUpdatedState(itemCount)
    val urls by rememberUpdatedState(urlAt)

    /*
     * Coil does not fold two in-flight requests for the same picture into one, and a row can leave
     * and re-enter this window several times on the way down a feed.
     *
     * Recorded before the request rather than after it, so a duplicate is refused while the first
     * is still in the air — and taken back out on failure, because a prefetch that 仅 Wi-Fi 加载图片
     * declined is not one that succeeded, and remembering it as done would keep that avatar cold
     * for the rest of the session, Wi-Fi or not.
     */
    val requested = remember(loader) { mutableSetOf<String>() }

    LaunchedEffect(listState, loader, pixels, rowsAhead) {
        snapshotFlow { avatarPrefetchWindow(listState.lastVisibleIndex(), counted, rowsAhead) }
            .distinctUntilChanged()
            .collectLatest { window ->
                /*
                 * A fling crosses more rows in a second than the reader will ever look at, and
                 * warming each one as it flies past is how a prefetch turns into a page-sized
                 * download. `collectLatest` throws this away on the next scroll frame, so nothing
                 * is fetched until the list is holding still.
                 */
                delay(SETTLE_MILLIS)
                for (index in window) {
                    val url = urls(index) ?: continue
                    if (!requested.add(url)) continue
                    loader.enqueue(
                        ImageRequest
                            .Builder(context)
                            .data(url)
                            .size(pixels)
                            // [UserAvatar] crops; the pair has to match or the row computes a
                            // different memory cache key and decodes the same file a second time.
                            .scale(Scale.FILL)
                            .listener(onError = { _, _ -> requested.remove(url) })
                            .build(),
                    )
                }
            }
    }
}

/** -1 when the list is holding nothing, which is not an index any window can start after. */
private fun LazyListState.lastVisibleIndex(): Int = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1

/**
 * The rows to warm: the [rowsAhead] after [lastVisibleIndex], clipped to what the list actually
 * holds. Empty when there is nothing past the fold — the last page of a feed ends with the rows on
 * screen, and an append spinner sits at an index no row will ever be at.
 */
internal fun avatarPrefetchWindow(
    lastVisibleIndex: Int,
    itemCount: Int,
    rowsAhead: Int,
): IntRange {
    if (lastVisibleIndex < 0 || itemCount <= 0 || rowsAhead <= 0) return IntRange.EMPTY
    return (lastVisibleIndex + 1)..minOf(lastVisibleIndex + rowsAhead, itemCount - 1)
}
