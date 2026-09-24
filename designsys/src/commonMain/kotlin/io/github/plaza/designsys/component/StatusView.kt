package io.github.plaza.designsys.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing

/**
 * Every empty, error and blocked state in an app is this one composable.
 *
 * They share a shape language on purpose: a tonal blob, an icon, a sentence saying what happened
 * and a button that does something about it. "出错了 :(" is not a state — if there is nothing the
 * user can press, the screen has failed twice.
 *
 * Nothing here decides *which* state is being shown or what it says. The icon, the colours and every
 * word arrive as parameters, because those are the half that belongs to whichever app is asking.
 *
 * [icon] is drawn at the head of the primary button — 重试 wears a ↻ in board 2e. Optional, and
 * absent on every action that is a sentence rather than a verb.
 */
data class StatusAction(
    val label: String,
    val icon: ImageVector?,
    val onClick: () -> Unit,
) {
    /** The icon-less action — every call site that predates 2e, trailing-lambda form included. */
    constructor(label: String, onClick: () -> Unit) : this(label, null, onClick)
}

/**
 * The state itself: board 2e's white card on the grey page, the blob at its head, a full-width pill
 * for the primary action and a quiet text button under it.
 *
 * A card rather than loose text on the page because the page is grey now (see
 * [io.github.plaza.designsys.theme.PlazaLayers]): everything that is content sits on a card, and a
 * status is the content of the screen it replaces.
 */
@Composable
fun StatusView(
    icon: ImageVector,
    shape: Shape,
    containerColor: Color,
    iconColor: Color,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    footnote: String? = null,
    primaryAction: StatusAction? = null,
    secondaryAction: StatusAction? = null,
) {
    // Centred when it fits, scrollable when it does not. A status screen is the last thing that
    // should break at 200% font scale — it is often the only thing on screen.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                // More room under the card than over it, so it sits at the optical centre (2e)
                // rather than the geometric one, which reads as having slid down.
                .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xl, bottom = STATUS_LIFT),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LayerCard(
                modifier = Modifier.widthIn(max = Sizes.readableContentWidth).fillMaxWidth(),
                // Rounder than a list card: the status card is alone on its page, and board 2e draws it at 32.
                shape = MaterialTheme.shapes.extraLargeIncreased,
                contentPadding = PaddingValues(start = Spacing.xl, top = 36.dp, end = Spacing.xl, bottom = Spacing.xl),
                verticalArrangement = Arrangement.Top,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier =
                        Modifier
                            .size(120.dp)
                            .clip(shape)
                            .background(containerColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            // The blob is decoration; the title next to it already says what the state is.
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(52.dp),
                        )
                    }
                    Text(
                        text = title,
                        fontSize = 22.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 22.dp),
                    )
                    description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                    primaryAction?.let {
                        // Full width and pill-shaped: on a card this narrow the one thing to press should be the
                        // one thing a thumb cannot miss.
                        Button(
                            onClick = it.onClick,
                            modifier =
                            Modifier
                                .padding(top = Spacing.xl)
                                .fillMaxWidth()
                                .heightIn(min = ButtonDefaults.MediumContainerHeight),
                            shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                        ) {
                            it.icon?.let { glyph ->
                                Icon(glyph, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(Spacing.sm))
                            }
                            Text(it.label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                    secondaryAction?.let {
                        TextButton(
                            onClick = it.onClick,
                            modifier =
                            Modifier
                                .padding(top = if (primaryAction == null) Spacing.xl else Spacing.sm)
                                .fillMaxWidth()
                                .heightIn(min = Sizes.minTouchTarget),
                        ) {
                            it.icon?.let { glyph ->
                                Icon(glyph, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(Spacing.sm))
                            }
                            Text(it.label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                    footnote?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 18.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = Spacing.lg),
                        )
                    }
                }
            }
        }
    }
}

private val STATUS_LIFT = 72.dp

/** Full-screen spinner. Lists use a skeleton instead — a fixed structure fakes faster than a spinner. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PlazaLoadingIndicator()
    }
}
