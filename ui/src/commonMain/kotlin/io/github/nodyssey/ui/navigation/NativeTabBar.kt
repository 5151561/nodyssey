package io.github.nodyssey.ui.navigation

import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The native bottom bar's half of the conversation, on iOS 26 and later.
 *
 * iOS 26 is the first release whose tab bar is real Liquid Glass, and that material only exists
 * inside a `UITabBarController` — a `UITabBar` on its own draws the new shape and then paints an
 * opaque background over it, and a bar drawn by Compose draws no material at all. Borrowing the
 * controller's bar costs a whole restructuring unless the bar can be told what to show and can say
 * what was tapped, and this is that seam.
 *
 * Null everywhere else, and null is the ordinary state rather than a fallback: on Android, desktop
 * and iOS before 26, Compose draws the bar itself and none of this exists. Nothing here is a platform
 * check — a caller either has a native bar to talk to or it does not.
 *
 * **The two directions are not symmetric**, which is worth knowing before wiring it up. A tap on the
 * bar arrives at the shell first — the bar's delegate runs in UIKit and writes [selected] itself, and
 * Compose reads the answer. A change that *starts* in Compose (a deep link, 首页 from an empty state,
 * the avatar at the end of 首页's bar) has to be reported the other way, through [onTabChanged],
 * because the shell cannot see it. Compose stays the source of truth in both cases: `currentTab` is
 * written from six places and the bar is only one of them.
 *
 * @param selected which tab the bar is showing. Backed by `mutableStateOf` in the shell, because the
 * delegate that writes it runs outside any composition.
 * @param onTabChanged a tab the composition settled on, for the bar to move its highlight to. It must
 * not call back into [selected] — the shell updates that itself, and a round trip would only be a
 * second place for the two to disagree.
 * @param onTabRootVisible whether a top-level destination is on screen. A thread, the image viewer
 * and the web view are full-screen by design, and this is what tells the bar to get out of them.
 * @param onBadgeChanged the unread count, or zero. The bar has to be told rather than asked: the
 * count lives in a view model inside the composition, and `UITabBarController` does not load a
 * child's view until it is selected, so a bar that waited to read it would show nothing until the
 * reader had already been there.
 * @param height the bar's height in dp once it has been laid out, for the lists that must be able to
 * scroll their last row clear of it. Zero until the first layout pass.
 */
class NativeTabBar(
    val selected: State<TopLevelDestination>,
    val onTabChanged: (TopLevelDestination) -> Unit,
    val onTabRootVisible: (Boolean) -> Unit,
    val onBadgeChanged: (Int) -> Unit,
    val height: State<Float>,
)

/**
 * How much room the bottom bar takes, when the bar is not Compose's.
 *
 * Zero wherever the bar *is* Compose's — Android, desktop, and iOS before 26 — because there the
 * scaffold already makes room for it and a second allowance would be a gap above nothing. Non-zero
 * only on iOS 26 and later, where the system's bar floats over the content rather than taking a slice
 * of it, and anything a screen puts at the bottom would otherwise sit under glass.
 *
 * A composition local rather than a parameter because the two call sites are nowhere near the root:
 * 发帖 is inside `PostListScreen`'s own scaffold, and 新手引导 is drawn over the whole app from
 * `NodysseyRoot`. Threading a `Dp` down to two places through everything between is the job
 * `LocalPlazaLayers` and `LocalEinkMode` already do.
 */
val LocalBottomBarHeight = compositionLocalOf<Dp> { 0.dp }
