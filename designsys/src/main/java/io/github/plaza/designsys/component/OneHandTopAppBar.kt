package io.github.plaza.designsys.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.Spacing
import kotlin.math.roundToInt

/**
 * A One UI style collapsing app bar: a band of blank above the toolbar with the title centred in it,
 * which the reader drags to whatever height suits them.
 *
 * The point is reach. On a phone this tall the title row sits outside the thumb's arc, so the screen
 * opens with a share of the top given away and the title dropped into the middle of it. From there
 * it tracks the finger one to one, anywhere between nothing and [MAX_BAR_FRACTION] of the screen,
 * and **stays where it is let go**. There is no snapping to either end and no settling animation:
 * how much of the screen to spend on reach is the reader's call, made continuously, and a bar that
 * springs to a canned height the moment they lift their finger takes that call away from them —
 * which is exactly what it feels like.
 *
 * That is the one place this parts company with Material's
 * [androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior], whose gesture
 * model it otherwise copies: fold before the page moves, reopen only once the page is back at its
 * top. Material always settles to fully open or fully closed.
 *
 * The other reason this is not a Material bar at all is the alignment. The expanded title is centred
 * and the collapsed one is not, and `LargeFlexibleTopAppBar` takes a single
 * `titleHorizontalAlignment` and hands the same value to both rows — so centring the big title
 * necessarily centres the small one, and a centred toolbar title on a settings page reads as a
 * different app. No parameter, colour or modifier splits them.
 *
 * The collapsed row is a plain Material toolbar and stays pinned. Everything the expansion adds sits
 * *above* it, which is what walks the back button down the screen with the content instead of
 * leaving it at the top out of reach.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneHandTopAppBar(
    title: String,
    state: OneHandAppBarState,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    expandedBlank: Dp = oneHandExpandedBlank(),
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    state.maxHeightPx = with(density) { expandedBlank.toPx() }

    val containerColor by animateColorAsState(
        targetValue =
        if (state.isContentOverlapped) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "oneHandAppBarContainer",
    )
    // The title exists twice and only one of them may be readable, or a screen reader announces the
    // screen's name twice. The handover is a plain threshold rather than the alpha curves below,
    // which cross over gradually and would leave a band where both or neither are the live one.
    val expandedTitleIsLive by remember { derivedStateOf { state.fraction >= SEMANTICS_HANDOVER } }

    Surface(color = containerColor, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets)) {
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    // Height read here rather than through `Modifier.height` so a scroll re-lays out
                    // without recomposing: the bar changes every frame the finger moves.
                    .layout { measurable, constraints ->
                        val blank = state.heightPx.roundToInt().coerceAtLeast(0)
                        val placeable =
                            measurable.measure(
                                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
                            )
                        layout(constraints.maxWidth, blank) {
                            placeable.place(0, (blank - placeable.height) / 2)
                        }
                    }
                    .padding(horizontal = Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                    Modifier
                        // Same reason as the height: alpha read in the draw phase, not the
                        // composition one.
                        .graphicsLayer { alpha = expandedTitleAlpha(state.fraction) }
                        .then(
                            if (expandedTitleIsLive) Modifier else Modifier.clearAndSetSemantics {},
                        ),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(CollapsedHeight)
                    .padding(horizontal = ToolbarEdgePadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navigationIcon?.invoke()
                Column(
                    modifier =
                    Modifier
                        .weight(1f)
                        // Material puts a title 16dp from the edge with no navigation icon and 56dp
                        // with one, and the icon is a 48dp box sitting on the row's own 4dp inset.
                        .padding(
                            start =
                            if (navigationIcon == null) TitleEdgePadding else ToolbarEdgePadding,
                        )
                        .graphicsLayer { alpha = collapsedTitleAlpha(state.fraction) }
                        .then(
                            if (expandedTitleIsLive) Modifier.clearAndSetSemantics {} else Modifier,
                        ),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                actions()
            }
        }
    }
}

/**
 * How far [OneHandTopAppBar] is open, and the nested-scroll connection that closes and reopens it.
 *
 * Hoisted rather than internal because the connection has to hang off the `Scaffold` — the scrolling
 * content is the `Scaffold`'s body, not the app bar's child, so the app bar can only hear about a
 * scroll through the shared nested-scroll chain.
 */
@Stable
class OneHandAppBarState internal constructor(initialHeightPx: Float, initialContentOffset: Float) {
    /**
     * `NaN` until the first measurement, which is how "start expanded" is expressed: the bar opens
     * to whatever [maxHeightPx] turns out to be on this window, and that is not known until the
     * composable has read it. A restored state carries a real number and keeps it.
     */
    private var height by mutableFloatStateOf(initialHeightPx)
    private var contentOffset by mutableFloatStateOf(initialContentOffset)

    /** How much blank stands above the toolbar, in pixels. */
    var heightPx: Float
        get() = if (height.isNaN()) maxHeightPx else height
        internal set(value) {
            height = value.coerceIn(0f, maxHeightPx)
        }

    /** The full blank, measured by the composable. Zero on a window with no room to give away. */
    var maxHeightPx: Float = 0f
        internal set(value) {
            field = value
            // A rotation into a shorter window has to bring a bar that was open down with it.
            if (!height.isNaN()) height = height.coerceIn(0f, value)
        }

    /** 0 collapsed, 1 fully open. Drives the cross-fade. */
    val fraction: Float
        get() = if (maxHeightPx <= 0f) 0f else (heightPx / maxHeightPx).coerceIn(0f, 1f)

    /**
     * Whether the page has scrolled underneath the toolbar, which is what the container tint is for.
     *
     * Counted from what the *content* consumed, never from what the bar itself took: a scroll that
     * is only folding the blank away has not moved the page at all, and charging it here made the
     * toolbar flash its scrolled tint on the way down.
     */
    val isContentOverlapped: Boolean
        get() = contentOffset < -0.5f

    private var consumedByBar = 0f

    val nestedScrollConnection: NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                consumedByBar = 0f
                // Upwards only: the blank folds away before the page underneath starts to move.
                if (available.y >= 0f || heightPx <= 0f) return Offset.Zero
                val before = heightPx
                heightPx = before + available.y
                consumedByBar = heightPx - before
                return Offset(0f, consumedByBar)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // What the page consumed is the total minus this bar's own share, which
                // `onPreScroll` left behind a moment ago. Cleared straight after so an
                // unpaired dispatch cannot subtract the previous frame's share.
                contentOffset =
                    if (available.y > 0f) 0f else contentOffset + (consumed.y - consumedByBar)
                consumedByBar = 0f
                // Downwards, and only what the page could not use — which is precisely the case
                // where the page is already back at its top. Taking it here rather than in
                // `onPreScroll` is what stops a scroll from reopening the bar while there is still
                // page left to scroll back.
                //
                // It is also the one slot pull-to-refresh wants. The two chain by nesting rather
                // than by arbitration: whichever connection sits deeper gets the leftover first, so
                // a screen with a refresh puts this one inside it. The reader sinks the bar, and
                // then — with the bar full and consuming nothing — goes on into the refresh.
                if (available.y <= 0f) return Offset.Zero
                val before = heightPx
                heightPx = before + available.y
                return Offset(0f, heightPx - before)
            }
        }

    /**
     * Folds the bar away on the screen's own initiative, for a jump the reader did not scroll to.
     *
     * A control that moves the list — a page rail, a jump sheet, a "back to top" — reaches
     * `LazyListState` directly, and a programmatic scroll dispatches no nested scroll at all. So the
     * bar hears nothing, stays open, and the page the reader just asked for arrives in whatever is
     * left of the screen below it. Every such control has to say so here.
     */
    suspend fun fold() {
        val from = heightPx
        if (from <= 0f) return
        animate(
            initialValue = from,
            targetValue = 0f,
            animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ) { value, _ -> heightPx = value }
    }

    internal companion object {
        val Saver: Saver<OneHandAppBarState, *> =
            listSaver(
                save = { listOf(it.heightPx, it.contentOffset) },
                restore = { OneHandAppBarState(it[0], it[1]) },
            )
    }
}

/**
 * Survives rotation and process death, so a screen restored mid-scroll comes back where it was.
 *
 * Pass `initiallyExpanded = false` on a screen the reader opens to *read* rather than to act on — a
 * ledger, a feed, anything whose first rows are the reason they came. There the opening screenful of
 * blank is something to scroll past before the screen starts being useful, and the bar is better off
 * waiting to be asked. It is the same bar either way: a pull still opens it, which is the point of
 * making this a starting position rather than a mode.
 */
@Composable
fun rememberOneHandAppBarState(initiallyExpanded: Boolean = true): OneHandAppBarState =
    rememberSaveable(saver = OneHandAppBarState.Saver) {
        OneHandAppBarState(if (initiallyExpanded) Float.NaN else 0f, 0f)
    }

/**
 * How far the bar can be pulled on this window: [MAX_BAR_FRACTION] of it, less the toolbar that is
 * always there.
 *
 * A fraction rather than a fixed height, because the point is where the title lands relative to the
 * hand holding the phone, and that is a property of the screen and not of the design system.
 *
 * Zero — no one-hand mode at all, just the pinned toolbar — on a window with no room to give away: a
 * landscape phone, a small free-form window, or a [androidx.compose.ui.platform.WindowInfo.containerSize]
 * that reads back empty, which the interface's own default allows for. A tablet stops growing at
 * [MAX_EXPANDED_BLANK]; past a phone's height the extra gap has stopped buying reach and is just an
 * empty screen.
 */
@Composable
fun oneHandExpandedBlank(): Dp {
    val heightPx = LocalWindowInfo.current.containerSize.height
    if (heightPx <= 0) return 0.dp
    return oneHandExpandedBlank(with(LocalDensity.current) { heightPx.toDp() })
}

/** The arithmetic alone, so the bounds can be asserted without a window to read them from. */
internal fun oneHandExpandedBlank(windowHeight: Dp): Dp =
    if (windowHeight < MIN_WINDOW_HEIGHT) {
        0.dp
    } else {
        (windowHeight * MAX_BAR_FRACTION - CollapsedHeight).coerceIn(0.dp, MAX_EXPANDED_BLANK)
    }

/**
 * The two titles hand over, and their alphas always sum to one.
 *
 * That sum is the requirement, not a nicety. The bar rests wherever the finger left it, so every
 * fraction in 0..1 is a position someone can sit and read at — a curve with a gap in the middle
 * would give them a screen whose title has simply vanished. Complementary curves make the total ink
 * constant instead, and the handover is a smoothstep so it reads as a fade rather than as a wipe.
 */
internal fun expandedTitleAlpha(fraction: Float): Float {
    val t = ((fraction - CROSSFADE_START) / (CROSSFADE_END - CROSSFADE_START)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** @see expandedTitleAlpha */
internal fun collapsedTitleAlpha(fraction: Float): Float = 1f - expandedTitleAlpha(fraction)

/**
 * A shorter sink, for a screen whose pull it has to share.
 *
 * Where pull-to-refresh is chained behind the bar, the bar's own travel is what the reader drags
 * through before the refresh even begins to arm. A third of the screen is too much to put in front
 * of a gesture people use constantly; this is short enough to read as one stage of a longer pull,
 * which is what it is.
 */
val OneHandSharedPullBlank = 120.dp

/** The most of the screen the whole bar — blank plus toolbar — is ever allowed to take. */
private const val MAX_BAR_FRACTION = 0.40f

/** Below this the window cannot spare the room — landscape phones, small free-form windows. */
private val MIN_WINDOW_HEIGHT = 600.dp

/** @see oneHandExpandedBlank */
private val MAX_EXPANDED_BLANK = 320.dp

/** Material's own toolbar height. The collapsed bar is an ordinary toolbar and should measure like one. */
private val CollapsedHeight = 64.dp

/** Puts the title's left edge at Material's 56dp, given the 48dp navigation icon between them. */
private val ToolbarEdgePadding = 4.dp

/** Material's 16dp, less the row's own inset, for a bar with nothing to the left of its title. */
private val TitleEdgePadding = 12.dp

/** The handover band. Wide, because a narrow one reads as a flicker under a slow drag. */
private const val CROSSFADE_START = 0.25f

/** @see CROSSFADE_START */
private const val CROSSFADE_END = 0.75f

/** Past halfway the big title is the one being read, so it is the one the screen reader gets. */
private const val SEMANTICS_HANDOVER = 0.5f
