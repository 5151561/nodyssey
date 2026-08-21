package io.github.plaza.designsys.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/**
 * The site's own avatar shape: `border-radius: 15%` on every list and thread avatar.
 *
 * A percentage rather than a Dp so the corner keeps its proportion from the 30dp avatar in a comment
 * row up to the 60dp one on a space page — which is also how the site's CSS states it.
 */
val AvatarShape: Shape = RoundedCornerShape(percent = 15)

/**
 * An account with no uploaded picture is still served an image — a cartoon the site generates from
 * the uid — so the initial below is a last resort for the rare uid whose `/avatar/<uid>.png` 404s,
 * not the common case. It reads better than an empty grey square either way.
 */
@Composable
fun UserAvatar(
    url: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = AvatarShape,
    fontSize: TextUnit = (size.value * 0.42f).sp,
) {
    val fallback: @Composable (Modifier) -> Unit = { avatarModifier ->
        Box(
            modifier = avatarModifier
                .clip(shape)
                .background(colorForName(name)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize,
            )
        }
    }

    if (url == null || name.isEmpty()) {
        fallback(modifier.size(size))
        return
    }

    // Plain [AsyncImage], not `SubcomposeAsyncImage`. The subcompose variant runs a whole extra
    // subcomposition per avatar to lay out its `loading`/`error` slots — a cost Coil's own docs
    // steer lists away from, and one that lands on every row of every feed, thread and profile list
    // this app scrolls. The two states those slots drew are kept without the subcomposition: the
    // surfaceVariant ground shows through until the opaque, cropped picture paints over it, and a
    // 404 flips `failed` to draw the initial — the same `onError` fallback the rest of the app uses.
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) {
        fallback(modifier.size(size))
        return
    }
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { failed = true },
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

private val AVATAR_COLORS = listOf(
    Color(0xFF5B8DEF),
    Color(0xFF2FA37C),
    Color(0xFFD9803F),
    Color(0xFF8A6DDF),
    Color(0xFFCE5B7B),
    Color(0xFF3E9AA8),
)

private fun colorForName(name: String): Color =
    AVATAR_COLORS[(name.hashCode().mod(AVATAR_COLORS.size))]
