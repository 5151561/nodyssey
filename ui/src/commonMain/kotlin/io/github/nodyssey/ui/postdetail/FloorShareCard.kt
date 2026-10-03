package io.github.nodyssey.ui.postdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.Size
import io.github.nodyssey.data.offline.imageUrls
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.ui.common.appName
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.post_share_image_truncated
import io.github.nodyssey.ui.richtext.PostRichContent
import io.github.plaza.core.runCatchingExceptCancellation
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource

/**
 * 分享为图片: draws [content] as a [FloorShareCard] nobody sees, and hands [onCaptured] a picture of
 * it — or null when the capture failed.
 *
 * Drawn into a graphics layer and never onto the screen: `drawWithContent` records the card and
 * stops there, so it is laid out and painted for real — the only way a picture of it can exist — and
 * still invisible. Laid out at a fixed width and an unbounded height, so a floor taller than the
 * window is pictured whole rather than cut at the fold. Hidden from accessibility for the same
 * reason it is hidden from sight: a screen reader announcing a second copy of the floor would be
 * describing nothing anybody can see.
 *
 * The pictures in the floor are fetched before the card is composed, through the same loader the
 * card then draws with, so its images find a warm cache on their first frame instead of each
 * starting a download of its own. That wait is bounded by [IMAGE_WAIT_MILLIS]: a slow image host
 * costs the picture its screenshot — the placeholder is drawn in its place — and not the share.
 */
@Composable
internal fun FloorShareCapture(
    content: PostContent,
    threadTitle: String,
    postUrl: String,
    onCaptured: (ImageBitmap?) -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val context = LocalPlatformContext.current
    val widthPx = with(LocalDensity.current) { ShareCardWidth.roundToPx() }
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    var imagesReady by remember(content) { mutableStateOf(false) }

    LaunchedEffect(content) {
        val loader = SingletonImageLoader.get(context)
        withTimeoutOrNull(IMAGE_WAIT_MILLIS) {
            coroutineScope {
                content.shareCardImageUrls().map { url ->
                    async {
                        runCatchingExceptCancellation {
                            loader.execute(
                                ImageRequest
                                    .Builder(context)
                                    .data(url)
                                    // The card's width and the picture's own height: a size of one
                                    // number is a square, which would shrink a tall screenshot.
                                    .size(Size(Dimension(widthPx), Dimension.Undefined))
                                    .build(),
                            )
                        }
                    }
                }.awaitAll()
            }
        }
        imagesReady = true
        // A frame for the card to compose and lay out, more for each image to swap its placeholder
        // for the cached picture and the column to grow to fit it.
        repeat(SETTLE_FRAMES) { withFrameNanos { } }
        delay(SETTLE_MILLIS)
        currentOnCaptured(runCatchingExceptCancellation { layer.toImageBitmap() }.getOrNull())
    }

    if (!imagesReady) return
    Box(
        Modifier
            .requiredWidth(ShareCardWidth)
            .wrapContentHeight(align = Alignment.Top, unbounded = true)
            .clearAndSetSemantics {}
            .drawWithContent { layer.record { this@drawWithContent.drawContent() } },
    ) {
        FloorShareCard(content = content, threadTitle = threadTitle, postUrl = postUrl)
    }
}

/**
 * One floor as a picture someone else can read without the app: who wrote it and which floor, the
 * body as the thread draws it, and where it came from — the thread's title, its address, and the
 * app's name.
 *
 * Read-only by construction: every link and image handler is a no-op, because nothing in a picture
 * can be tapped. A body longer than [MAX_BODY_HEIGHT] is cut there and says so, rather than growing
 * the picture into one no chat app will show uncropped.
 */
@Composable
internal fun FloorShareCard(
    content: PostContent,
    threadTitle: String,
    postUrl: String,
    modifier: Modifier = Modifier,
) {
    var truncated by remember(content) { mutableStateOf(false) }
    Column(
        modifier = modifier
            .width(ShareCardWidth)
            .background(LocalPlazaLayers.current.card)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            UserAvatar(url = content.avatarUrl, name = content.authorName, size = ShareAvatarSize)
            Column(Modifier.weight(1f)) {
                Text(
                    text = content.authorName,
                    style = floorNameStyle(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                content.createdAtTitle?.let { time ->
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content.floor?.let { FloorLabel(it) }
        }
        Box(Modifier.capHeight(MAX_BODY_HEIGHT) { truncated = it }) {
            PostRichContent(
                nodes = content.nodes,
                onLinkClick = {},
                onImageClick = {},
                onQuoteRefClick = {},
                textStyle = replyBodyStyle(),
                selectable = false,
            )
        }
        if (truncated) {
            Text(
                text = stringResource(Res.string.post_share_image_truncated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = LocalPlazaLayers.current.divider)
        Text(
            text = threadTitle,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = postUrl,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = appName(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Measures the content at its full height and shows at most [max] of it, telling [onTruncated]
 * whether anything was left out.
 */
private fun Modifier.capHeight(max: Dp, onTruncated: (Boolean) -> Unit): Modifier =
    clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
        val limit = max.roundToPx()
        onTruncated(placeable.height > limit)
        layout(placeable.width, minOf(placeable.height, limit)) { placeable.place(0, 0) }
    }

/** The avatar and the body's pictures — what the card draws. The signature is not on the card. */
private fun PostContent.shareCardImageUrls(): List<String> =
    copy(signatureNodes = emptyList()).imageUrls().toList()

/** About a phone's width, so the text wraps the way it does in the thread. */
private val ShareCardWidth = 360.dp

private val ShareAvatarSize = 36.dp

/** Several screens of a phone: beyond this a picture stops being something people read. */
private val MAX_BODY_HEIGHT = 1_600.dp

private const val IMAGE_WAIT_MILLIS = 4_000L
private const val SETTLE_FRAMES = 3
private const val SETTLE_MILLIS = 250L
