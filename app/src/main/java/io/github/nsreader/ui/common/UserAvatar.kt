package io.github.nsreader.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil3.compose.SubcomposeAsyncImage

/**
 * Many NodeSeek accounts never upload an avatar, so `/avatar/<uid>.png` 404s. Falling back to an
 * initial on a stable per-user color reads far better than a row of empty grey circles.
 */
@Composable
fun UserAvatar(
    url: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
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

    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        loading = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        },
        error = { fallback(Modifier.fillMaxSize()) },
        modifier = modifier
            .size(size)
            .clip(shape),
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
