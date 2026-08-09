package io.github.plaza.designsys.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
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
 */
data class StatusAction(
    val label: String,
    val onClick: () -> Unit,
)

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
                .padding(horizontal = 44.dp, vertical = Spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                Modifier
                    .size(116.dp)
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
                fontSize = 19.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xl),
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier =
                    Modifier
                        .padding(top = Spacing.sm)
                        .widthIn(max = Sizes.readableContentWidth),
                )
            }
            primaryAction?.let {
                Button(
                    onClick = it.onClick,
                    modifier =
                    Modifier
                        .padding(top = 28.dp)
                        .height(Sizes.minTouchTarget),
                ) {
                    Text(it.label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
                }
            }
            secondaryAction?.let {
                TextButton(
                    onClick = it.onClick,
                    modifier = Modifier.padding(top = Spacing.xs),
                ) {
                    Text(it.label, style = MaterialTheme.typography.labelLarge)
                }
            }
            footnote?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.xl),
                )
            }
        }
    }
}

/** Full-screen spinner. Lists use a skeleton instead — a fixed structure fakes faster than a spinner. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator()
    }
}
