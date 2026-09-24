package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.ControlShape

/**
 * A tonal square holding one icon — or a spinner in its place — at the head of a card: an update, a
 * check's verdict. Not tapped itself; the card around it is.
 *
 * A [Surface] with its content centred. Material's tonal icon container is `FilledTonalIconButton`,
 * which is a button, and `ListItem`'s leading slot would bring a list row's padding into a card that
 * lays out its own.
 */
@Composable
fun IconBadge(
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = ControlShape,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
