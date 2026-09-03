package io.github.nodyssey.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.common.rememberReducedMotionEnabled
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.Spacing

/**
 * The four illustrations the guide's screens open with, each one a short loop.
 *
 * Drawn out of boxes rather than shipped as images or videos, for reasons that matter more here than
 * the fidelity a real recording would have. They follow the theme — this app has six presets,
 * dynamic colour and a light/dark switch, and a recording would be somebody else's palette every
 * time. And a recording of the app inside the app dates the moment either changes, which is the
 * drift a guide can least afford: a picture that no longer matches the screen is worse than none.
 *
 * They are loops rather than stills because three of the four are about a *movement* — a bar that
 * drops, a chip that is dragged, a list that jumps back to the top — and a still of a movement is
 * the one thing that cannot show it. None is a faithful rendering; each says one thing at the size
 * of a thumbnail, and the words underneath say the rest.
 *
 * `active` is the page being looked at. A pager composes its neighbours, so without it three loops
 * would be animating off-screen for as long as the guide is open.
 */
@Composable
internal fun WelcomeFigure(active: Boolean) {
    // Rows arriving one after another: the app, doing the one thing it is for.
    val p = loopProgress(active, durationMillis = 3600, rest = 1f)
    PhoneFrame {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            TextLine(widthFraction = 0.75f, emphasis = true, modifier = Modifier.arriveAt(p, 0f))
            Spacer(Modifier.height(Spacing.xs))
            TextLine(widthFraction = 1f, modifier = Modifier.arriveAt(p, 0.10f))
            TextLine(widthFraction = 0.92f, modifier = Modifier.arriveAt(p, 0.16f))
            TextLine(widthFraction = 0.6f, modifier = Modifier.arriveAt(p, 0.22f))
            Spacer(Modifier.height(Spacing.xs))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().arriveAt(p, 0.30f),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        TextLine(widthFraction = 0.7f, tint = MaterialTheme.colorScheme.primary)
                        TextLine(widthFraction = 0.45f, tint = MaterialTheme.colorScheme.primary)
                    }
                    Icon(
                        PlazaIcons.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextLine(widthFraction = 0.85f, modifier = Modifier.arriveAt(p, 0.38f))
        }
    }
}

/**
 * 单手模式, as the movement it is: the title drops into a band of blank, then goes back up.
 *
 * The band is the whole point, so it is the only tinted area and it swings between the two heights
 * the real bar has — collapsed to an ordinary toolbar, and expanded to the two fifths of the screen
 * that is being mistaken for a bug. The content below rides on it, because that is what makes the
 * band read as part of the layout rather than as something drawn over it.
 */
@Composable
internal fun OneHandFigure(active: Boolean) {
    // Rests at either end so each state is legible before it moves; expanded is where it stops.
    val p = loopProgress(active, durationMillis = 4800, rest = 1f)
    val expansion = holdSwing(p)
    PhoneFrame {
        Column(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(COLLAPSED_BAR + (EXPANDED_BAR - COLLAPSED_BAR) * expansion)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.BottomStart,
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = Spacing.md, bottom = 7.dp)
                        .width(72.dp)
                        .height(10.dp)
                        .background(
                            MaterialTheme.colorScheme.onPrimaryContainer,
                            RoundedCornerShape(3.dp),
                        ),
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                TextLine(widthFraction = 1f)
                TextLine(widthFraction = 0.9f)
                TextLine(widthFraction = 0.95f)
                TextLine(widthFraction = 0.55f)
            }
        }
    }
}

/**
 * 首页操作 — the screen's three gestures, one after another, with dots underneath.
 *
 * A carousel rather than three figures side by side, which at this size would be three illegible
 * ones. It turns on its own and takes no gestures of its own: it sits inside the guide's own pager,
 * and a horizontal drag that some inner strip swallowed would leave the reader unable to get to the
 * next screen — the price of an inner swipe is much higher than what it buys.
 */
@Composable
internal fun BoardStripFigure(active: Boolean) {
    val p = loopProgress(active, durationMillis = 12000, rest = 0.1f)
    val slot = (p * HOME_SLOTS).toInt().coerceIn(0, HOME_SLOTS - 1)
    val within = (p * HOME_SLOTS) - slot
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // Cross-faded rather than swapped, so the change of subject is visible as a change
            // rather than as a flicker at the moment the figure is glanced at.
            SlotFade(visible = slot == 0) { ReorderStrip(within) }
            SlotFade(visible = slot == 1) { ScrollToTop(within) }
            SlotFade(visible = slot == 2) { SortMemory(within) }
            SlotFade(visible = slot == 3) { PageJump(within) }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(HOME_SLOTS) { index ->
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(
                            color = if (index == slot) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            shape = RoundedCornerShape(3.dp),
                        ),
                )
            }
        }
    }
}

/** 1/3 — a board lifted out of the strip and carried one place to the left. */
@Composable
private fun ReorderStrip(t: Float) {
    // Lifted, carried, dropped, then a beat before the loop takes it back to the start.
    val lift = ramp(t, 0.05f, 0.20f) - ramp(t, 0.62f, 0.78f)
    val travel = ramp(t, 0.24f, 0.58f)
    PhoneFrame(height = STRIP_FRAME_HEIGHT) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BoardChip(width = 24.dp, selected = true)
                Box(Modifier.width(20.dp)) {
                    // The neighbour slides right to make room, which is what the drag is *for*.
                    BoardChip(width = 20.dp, modifier = Modifier.graphicsLayer { translationX = 34.dp.toPx() * travel })
                }
                Box(Modifier.width(28.dp)) {
                    BoardChip(width = 28.dp, gap = true)
                    BoardChip(
                        width = 28.dp,
                        lifted = true,
                        modifier = Modifier.graphicsLayer {
                            translationX = -26.dp.toPx() * travel
                            translationY = -9.dp.toPx() * lift
                            rotationZ = -6f * lift
                        },
                    )
                }
                BoardChip(width = 18.dp)
            }
            Spacer(Modifier.height(Spacing.xs))
            repeat(2) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    TextLine(widthFraction = 0.8f, emphasis = true)
                    TextLine(widthFraction = 0.45f)
                }
            }
        }
    }
}

/** 2/3 — the tab is tapped, the list runs back up, a spinner turns at the top. */
@Composable
private fun ScrollToTop(t: Float) {
    val press = ramp(t, 0.06f, 0.14f) - ramp(t, 0.16f, 0.26f)
    val travel = 1f - ramp(t, 0.20f, 0.55f)
    val refreshing = ramp(t, 0.24f, 0.34f) - ramp(t, 0.70f, 0.82f)
    PhoneFrame(height = STRIP_FRAME_HEIGHT) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
                // A ring with a bite out of one corner, turning: the smallest thing that reads as a
                // spinner. A full circle at this size would just be a dot.
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .alpha(refreshing)
                        .graphicsLayer { rotationZ = t * 1440f }
                        .border(
                            width = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomEnd = 7.dp),
                        ),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md)
                    // Off the bottom of the frame and back up: the list returning to the top.
                    .graphicsLayer { translationY = 46.dp.toPx() * travel },
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                repeat(3) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        TextLine(widthFraction = 0.8f, emphasis = true)
                        TextLine(widthFraction = 0.45f)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            // The bar the tap lands on; the 首页 item lights up under the finger.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == 0) 9.dp else 7.dp)
                            .background(
                                color = if (index == 0) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f + 0.6f * press)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = RoundedCornerShape(4.dp),
                            ),
                    )
                }
            }
        }
    }
}

/**
 * 3/3 — the sort is switched, the app is closed and opened, and the sort is still where it was left.
 *
 * Switching alone would only show a segmented control working, which nobody needs telling. What the
 * line under this claims is that the choice *survives*, so the figure has to show the app going away
 * and coming back — the blank in the middle is the relaunch, and the second pill still being lit
 * afterwards is the whole point.
 */
@Composable
private fun SortMemory(t: Float) {
    val onSecond = ramp(t, 0.16f, 0.28f)
    // Away and back: the screen goes dark, then returns with the choice intact.
    val present = 1f - (ramp(t, 0.40f, 0.50f) - ramp(t, 0.62f, 0.72f))
    PhoneFrame(height = STRIP_FRAME_HEIGHT) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md).alpha(present),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SortPill(width = 38.dp, selected = 1f - onSecond)
                SortPill(width = 38.dp, selected = onSecond)
            }
            Spacer(Modifier.height(Spacing.xs))
            repeat(3) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    TextLine(widthFraction = 0.8f, emphasis = true)
                    TextLine(widthFraction = 0.45f)
                }
            }
        }
    }
}

/**
 * 4/4 — the page key at the bottom right, and the jump sheet a tap on it brings up.
 *
 * The subject is the *control*, not paging. What a reader has to be told is that the number sitting
 * under their thumb is a button and that pressing it opens somewhere to go — page numbers themselves
 * need no explaining. So the figure presses the key and lets the sheet come up, and stops there.
 *
 * Drawn as the real thing is drawn: a column at the bottom right — page key, then the two step keys,
 * then the compose button — rather than a bar across the foot of the screen, which is what an
 * earlier version of this showed and is not a control this app has.
 */
@Composable
private fun PageJump(t: Float) {
    // The press lands and lets go before the sheet starts to rise, so the two read as cause and
    // effect rather than as one thing.
    val press = ramp(t, 0.12f, 0.17f) - ramp(t, 0.21f, 0.27f)
    val sheet = ramp(t, 0.26f, 0.42f) - ramp(t, 0.84f, 0.93f)
    PhoneFrame(height = STRIP_FRAME_HEIGHT) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                repeat(3) { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        TextLine(widthFraction = ROW_WIDTHS[row], emphasis = true)
                        TextLine(widthFraction = 0.45f)
                    }
                }
            }
            // 翻页栏, where it really lives: stacked at the bottom right over the compose button.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // The page key: the current page over the total, and the tap that opens the sheet.
                Column(
                    modifier = Modifier
                        .size(width = 22.dp, height = 20.dp)
                        .background(
                            color = lerpColor(
                                MaterialTheme.colorScheme.surfaceContainer,
                                MaterialTheme.colorScheme.primary,
                                press,
                            ),
                            shape = RoundedCornerShape(7.dp),
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 11.dp, height = 5.dp)
                            .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .size(width = 8.dp, height = 2.5.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(1.dp)),
                    )
                }
                StepKey()
                StepKey()
                Spacer(Modifier.height(2.dp))
                // 发帖, which the rail sits above and never moves.
                Box(
                    Modifier
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp)),
                )
            }
            // The scrim the sheet brings with it, so the sheet reads as being *over* the feed.
            // Light: at this size a Material-weight scrim turns the whole top half into one grey
            // block, and what it is dimming stops being recognisable as a feed at all.
            Box(
                Modifier
                    .matchParentSize()
                    .alpha(sheet * 0.16f)
                    .background(MaterialTheme.colorScheme.scrim),
            )
            // 跳页面板: the row of page keys, the one you are on standing proud of the rest.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .graphicsLayer { translationY = 66.dp.toPx() * (1f - sheet) }
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    )
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(width = 18.dp, height = 3.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(6) { index ->
                        val here = index == 2
                        Box(
                            modifier = Modifier
                                .size(
                                    width = if (here) 17.dp else 13.dp,
                                    height = if (here) 17.dp else 13.dp,
                                )
                                .background(
                                    color = if (here) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLowest
                                    },
                                    shape = RoundedCornerShape(5.dp),
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** 上一页 / 下一页 — the two keys under the page key, drawn as the chevrons they carry. */
@Composable
private fun StepKey() {
    Box(
        modifier = Modifier
            .size(width = 22.dp, height = 13.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 8.dp, height = 2.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(1.dp)),
        )
    }
}

/**
 * 站内链接: a link tapped in a chat, arriving in the app rather than in a browser.
 *
 * The dot travelling the gap is the whole sentence — a link that goes *here*. The frame stays empty
 * until it lands, so that arriving is something the figure does rather than something it states.
 */
@Composable
internal fun AppLinksFigure(active: Boolean) {
    val p = loopProgress(active, durationMillis = 4200, rest = 1f)
    val travel = ramp(p, 0.12f, 0.46f)
    val landed = ramp(p, 0.42f, 0.60f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp),
        ) {
            Column(
                modifier = Modifier.width(76.dp).padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                TextLine(widthFraction = 0.9f)
                TextLine(widthFraction = 0.6f)
                TextLine(widthFraction = 1f, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            Icon(
                PlazaIcons.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp).alpha(0.35f + 0.65f * (1f - travel)),
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        translationX = 30.dp.toPx() * travel
                        alpha = if (travel <= 0f || travel >= 1f) 0f else 1f
                    }
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
            )
        }
        PhoneFrame(width = 96.dp) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.sm).alpha(landed),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                TextLine(widthFraction = 0.85f, emphasis = true)
                TextLine(widthFraction = 1f)
                TextLine(widthFraction = 0.7f)
                TextLine(widthFraction = 0.9f)
                TextLine(widthFraction = 0.5f)
            }
        }
    }
}

/**
 * 编辑器工具栏: the wrench is pressed, the panel comes up, and a key is dragged into a new place.
 *
 * The strip is drawn at the foot of a composer with a caret in it, because a toolbar on its own is
 * an abstract row of squares — it has to be sitting under something being typed to read as one.
 */
@Composable
internal fun ComposerToolbarFigure(active: Boolean) {
    val p = loopProgress(active, durationMillis = 7000, rest = 0.5f)
    val press = ramp(p, 0.07f, 0.12f) - ramp(p, 0.14f, 0.20f)
    val panel = ramp(p, 0.13f, 0.26f) - ramp(p, 0.72f, 0.82f)
    // The drag inside the panel: a row lifted, carried one slot up, and set down.
    val lift = ramp(p, 0.32f, 0.40f) - ramp(p, 0.56f, 0.64f)
    val swap = ramp(p, 0.42f, 0.56f)
    // And what it was all for: with the panel gone, the strip has a key on it that was not there
    // before. The loop ends here rather than at the press, so the last thing it says is that the
    // edit stuck. A key *added* rather than two swapped, because five identical squares trading
    // places is a change that cannot be seen — which is exactly what the first attempt looked like.
    val strip = ramp(p, 0.76f, 0.86f)
    PhoneFrame {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    TextLine(widthFraction = 0.85f, emphasis = true)
                    TextLine(widthFraction = 0.7f)
                    TextLine(widthFraction = 0.3f)
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(4) { ToolbarKey(lit = 0f) }
                    // The new one, growing into the row the panel just put it in.
                    ToolbarKey(
                        lit = 0f,
                        modifier = Modifier.graphicsLayer {
                            alpha = strip
                            scaleX = strip
                            scaleY = strip
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    WrenchKey(lit = press)
                }
            }
            // The customise sheet, over the strip it edits.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .graphicsLayer {
                        translationY = 70.dp.toPx() * (1f - panel)
                        alpha = panel
                    }
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                    )
                    .padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // Two rows that trade places: the one being dragged rides up over the other.
                PanelRow(
                    modifier = Modifier.graphicsLayer {
                        translationY = -ROW_SLOT.toPx() * swap
                        translationX = 4.dp.toPx() * lift
                        scaleX = 1f + 0.04f * lift
                        scaleY = 1f + 0.04f * lift
                    },
                    lifted = lift,
                )
                PanelRow(
                    modifier = Modifier.graphicsLayer { translationY = ROW_SLOT.toPx() * swap },
                    lifted = 0f,
                )
            }
        }
    }
}

/**
 * One key on the strip; [lit] is how pressed it currently is.
 *
 * The formatting keys are blank squares — which one is 加粗 does not matter here, and eleven tiny
 * glyphs at this size would be eleven smudges. The wrench is the exception and is drawn as the icon
 * it really carries, because the whole screen is about *which key to press*: a reader who leaves
 * with "there is a customise button somewhere on the toolbar" has been told nothing.
 */
@Composable
private fun ToolbarKey(
    lit: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(13.dp)
            .background(
                color = lerpColor(
                    MaterialTheme.colorScheme.outlineVariant,
                    MaterialTheme.colorScheme.primary,
                    lit,
                ),
                shape = RoundedCornerShape(4.dp),
            ),
    )
}

/** The key the screen is about: `PlazaIcons.Build`, the same wrench the real toolbar ends with. */
@Composable
private fun WrenchKey(lit: Float) {
    Box(
        modifier = Modifier
            .size(17.dp)
            .background(
                color = lerpColor(
                    MaterialTheme.colorScheme.surfaceContainerLowest,
                    MaterialTheme.colorScheme.primaryContainer,
                    lit,
                ),
                shape = RoundedCornerShape(5.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            PlazaIcons.Build,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = lerpColor(
                MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.colorScheme.primary,
                lit,
            ),
        )
    }
}

/** One row of the customise sheet: a drag handle and the key it moves. */
@Composable
private fun PanelRow(
    lifted: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = lerpColor(
                    MaterialTheme.colorScheme.surfaceContainerLow,
                    MaterialTheme.colorScheme.primaryContainer,
                    lifted,
                ),
                shape = RoundedCornerShape(5.dp),
            )
            .padding(horizontal = 5.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(2) {
                Box(
                    Modifier
                        .size(width = 8.dp, height = 1.5.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(1.dp)),
                )
            }
        }
        TextLine(widthFraction = 0.5f)
    }
}

/**
 * A 0→1 ramp that repeats, or a fixed frame where it must not.
 *
 * `rest` is what the figure shows when nothing is moving — off-screen, or with the OS asked for no
 * animation. It is a *chosen* frame, not zero: the still that says the most. Motion here cannot go
 * through `MaterialTheme.motionScheme` the way the rest of the app's does, because that hands out
 * finite specs and this is a loop — and 移除动画 turning every spec into `snap()` would turn a loop
 * into a strobe. Not animating at all is what "remove" means for a loop.
 */
@Composable
private fun loopProgress(
    active: Boolean,
    durationMillis: Int,
    rest: Float,
): Float {
    val reducedMotion = rememberReducedMotionEnabled()
    val running = active && !reducedMotion
    val progress = remember { Animatable(rest) }
    LaunchedEffect(running, durationMillis) {
        if (!running) {
            progress.snapTo(rest)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        )
    }
    return progress.value
}

/** 0 before [from], 1 after [to], smoothly in between. */
private fun ramp(
    t: Float,
    from: Float,
    to: Float,
): Float {
    if (t <= from) return 0f
    if (t >= to) return 1f
    val x = (t - from) / (to - from)
    // Smoothstep: a linear ramp starts and stops with a visible jolt at this size.
    return x * x * (3f - 2f * x)
}

/** Out and back with a rest at each end, for a loop that shows two states rather than a cycle. */
private fun holdSwing(t: Float): Float {
    val out = ramp(t, 0.18f, 0.38f)
    val back = ramp(t, 0.62f, 0.82f)
    return 1f - (out - back)
}

/** Fades a row in on the loop's way past [at], and leaves it there. */
private fun Modifier.arriveAt(
    progress: Float,
    at: Float,
): Modifier = alpha(ramp(progress, at, at + 0.08f))

@Composable
private fun SlotFade(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    // Cheap enough to keep all three composed: they are a dozen boxes each, and only one is opaque.
    Box(Modifier.alpha(if (visible) 1f else 0f)) { content() }
}

/** The rounded outline every figure is drawn inside; a phone, at the size of a thumbnail. */
@Composable
private fun PhoneFrame(
    modifier: Modifier = Modifier,
    width: Dp = 132.dp,
    height: Dp = FRAME_HEIGHT,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.width(width).height(height),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp),
                ),
            contentAlignment = Alignment.TopStart,
        ) {
            content()
        }
    }
}

/** One line of body text, as a grey bar. */
@Composable
private fun TextLine(
    widthFraction: Float,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
    tint: Color? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(if (emphasis) 7.dp else 5.dp)
            .background(
                color = tint
                    ?: if (emphasis) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                shape = RoundedCornerShape(3.dp),
            ),
    )
}

/** One board in the strip — or, with [gap], the hole one has been lifted out of. */
@Composable
private fun BoardChip(
    width: Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    lifted: Boolean = false,
    gap: Boolean = false,
) {
    Box(
        modifier = modifier
            .width(width)
            .height(16.dp)
            .background(
                color = when {
                    gap -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    lifted -> MaterialTheme.colorScheme.primary
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
                shape = RoundedCornerShape(8.dp),
            ),
    )
}

/** One of the two sort choices, lit by how selected it currently is. */
@Composable
private fun SortPill(
    width: Dp,
    selected: Float,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(14.dp)
            .background(
                color = lerpColor(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.colorScheme.primary,
                    selected,
                ),
                shape = RoundedCornerShape(7.dp),
            ),
    )
}

private fun lerpColor(
    from: Color,
    to: Color,
    t: Float,
): Color =
    Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = from.alpha + (to.alpha - from.alpha) * t,
    )

/** How many figures 首页操作 cycles through; the dots under it count the same list. */
private const val HOME_SLOTS = 4

/** Row widths for the feed behind the jump sheet; see [PageJump]. */
private val ROW_WIDTHS = listOf(0.8f, 0.62f, 0.74f)

/** One row of the customise sheet plus its gap — how far a dragged row travels. */
private val ROW_SLOT = 20.dp

private val FRAME_HEIGHT = 168.dp
private val STRIP_FRAME_HEIGHT = 150.dp
private val COLLAPSED_BAR = 26.dp
private val EXPANDED_BAR = 67.dp
