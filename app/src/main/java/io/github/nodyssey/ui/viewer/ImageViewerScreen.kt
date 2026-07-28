package io.github.nodyssey.ui.viewer

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import io.github.nodyssey.R
import io.github.nodyssey.core.image.ImagesDeferredException
import io.github.nodyssey.core.image.allowMeteredImage
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.TABULAR_FIGURES
import kotlin.math.abs

/**
 * Full-screen image viewer.
 *
 * Its own screen rather than a dialog: it takes over the window, keeps its own back behaviour, and
 * survives rotation with the page it was on. The background is #0E0E11 rather than pure black — against
 * an OLED's true black a dark screenshot has no visible edge, and the gesture surface stops being legible.
 *
 * Three gestures, in the order they are checked: pinch to zoom, double-tap to toggle 2.5×, and — only
 * while unzoomed — a vertical drag in either direction to dismiss. Paging is disabled while zoomed, or a pan across a
 * magnified screenshot would flick to the next image instead.
 */
@Composable
fun ImageViewerScreen(
    urls: List<String>,
    initialIndex: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenBrowser: (String) -> Unit = {},
    saveOutcome: SaveOutcome? = null,
    onSave: (String) -> Unit = {},
) {
    if (urls.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, urls.lastIndex)) { urls.size }
    var zoomed by remember { mutableStateOf(false) }

    val notice =
        when (saveOutcome) {
            null -> null
            SaveOutcome.SAVED -> stringResource(R.string.viewer_saved)
            SaveOutcome.UNSUPPORTED_OS -> stringResource(R.string.viewer_save_unsupported)
            SaveOutcome.FAILED -> stringResource(R.string.viewer_save_failed)
        }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VIEWER_BACKGROUND),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ZoomableImage(
                url = urls[page],
                onDismiss = onClose,
                onZoomChange = { isZoomed -> if (page == pagerState.currentPage) zoomed = isZoomed },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .align(Alignment.TopCenter),
        ) {
            ViewerTopBar(
                page = pagerState.currentPage + 1,
                total = urls.size,
                onClose = onClose,
                onOpenBrowser = { onOpenBrowser(urls[pagerState.currentPage]) },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .systemBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (urls.size > 1) PageDots(count = urls.size, current = pagerState.currentPage)
            notice?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium,
                    color = VIEWER_SECONDARY_CONTENT,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ViewerAction(
                    icon = NodysseyIcons.Download,
                    label = stringResource(R.string.viewer_save),
                    modifier = Modifier.weight(1f),
                ) {
                    onSave(urls[pagerState.currentPage])
                }
                ViewerAction(
                    icon = Icons.Default.Share,
                    label = stringResource(R.string.action_share),
                    modifier = Modifier.weight(1f),
                ) {
                    // Shares the address rather than the bytes: NodeSeek attachments are hosted URLs,
                    // and a link stays a link for the person receiving it.
                    val share =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, urls[pagerState.currentPage])
                        }
                    context.startActivity(Intent.createChooser(share, null))
                }
            }
        }
    }
}

@Composable
private fun ViewerTopBar(
    page: Int,
    total: Int,
    onClose: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = VIEWER_CONTENT,
            )
        }
        Text(
            text = stringResource(R.string.viewer_page, page, total),
            style =
            MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            color = VIEWER_CONTENT,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenBrowser) {
            Icon(
                NodysseyIcons.OpenInNew,
                contentDescription = stringResource(R.string.action_open_in_browser),
                tint = VIEWER_CONTENT,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PageDots(
    count: Int,
    current: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        repeat(count) { index ->
            val active = index == current
            Box(
                Modifier
                    .width(if (active) 18.dp else 6.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (active) VIEWER_CONTENT else VIEWER_INACTIVE_DOT),
            )
        }
    }
}

@Composable
private fun ViewerAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = VIEWER_ACTION_CONTAINER,
        contentColor = VIEWER_CONTENT,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.height(48.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * One page: the image, its gestures, and the failure state that replaces it.
 *
 * A failed load keeps the filename and offers 重试. A blank black screen would leave the user unable to
 * tell a broken link from a slow one, and unable to do anything about either.
 */
@Composable
private fun ZoomableImage(
    url: String,
    onDismiss: () -> Unit,
    onZoomChange: (Boolean) -> Unit,
) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    var dragY by remember(url) { mutableFloatStateOf(0f) }
    var retryToken by remember(url) { mutableIntStateOf(0) }
    // Opening the viewer is already a deliberate act, but a skipped image is skipped here too — the
    // user has to say so once, and only then does this one image get to use mobile data.
    var allowMetered by remember(url) { mutableStateOf(false) }
    val context = LocalContext.current
    val request =
        remember(url, allowMetered) {
            ImageRequest
                .Builder(context)
                .data(url)
                .allowMeteredImage(allowMetered)
                .build()
        }
    val dismissDragThresholdPx = with(LocalDensity.current) { DISMISS_DRAG_THRESHOLD.toPx() }

    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "viewer-scale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(url) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else DOUBLE_TAP_SCALE
                        offset = Offset.Zero
                        onZoomChange(scale > 1f)
                    },
                )
            }.pointerInput(url) {
                /*
                 * Hand-rolled rather than detectTransformGestures, because that detector consumes
                 * every position change once past touch slop — including a one-finger swipe on an
                 * unzoomed image, which is the pager's page-turn. Consuming only on a second finger
                 * or on an already-zoomed image is what lets the swipe reach the pager at all.
                 */
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pinching = event.changes.count { it.pressed } > 1
                        if (pinching || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, MAX_SCALE)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                            onZoomChange(scale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }.pointerInput(url, scale > 1f) {
                if (scale > 1f) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (abs(dragY) > dismissDragThresholdPx) onDismiss() else dragY = 0f
                    },
                    onDragCancel = { dragY = 0f },
                ) { _, delta -> dragY += delta }
            },
        contentAlignment = Alignment.Center,
    ) {
        key(retryToken, allowMetered) {
            val painter = rememberAsyncImagePainter(model = request)
            val state by painter.state.collectAsState()

            when (state) {
                is AsyncImagePainter.State.Error -> {
                    // Not a failure at all when the app declined on purpose — see
                    // [ImagesDeferredException]. Naming the switch is what helps, and the action
                    // then has to be one that works: fetching it anyway, not retrying a request the
                    // app will decline again.
                    val deferred = (state as AsyncImagePainter.State.Error)
                        .result.throwable is ImagesDeferredException
                    ImageFailure(
                        url = url,
                        deferred = deferred,
                        onRetry = { if (deferred) allowMetered = true else retryToken++ },
                    )
                }

                else ->
                    Image(
                        painter = painter,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Spacing.sm)
                            .graphicsLayer(
                                scaleX = animatedScale,
                                scaleY = animatedScale,
                                translationX = offset.x,
                                translationY = offset.y + dragY,
                            ),
                    )
            }
        }
    }
}

@Composable
private fun ImageFailure(
    url: String,
    deferred: Boolean,
    onRetry: () -> Unit,
) {
    Surface(
        color = VIEWER_ACTION_CONTAINER,
        contentColor = VIEWER_CONTENT,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(horizontal = Spacing.lg),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = VIEWER_WARNING,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(
                        if (deferred) R.string.viewer_deferred_wifi_only else R.string.viewer_load_failed,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    url.toFileName(),
                    style = MaterialTheme.typography.labelSmall,
                    color = VIEWER_SECONDARY_CONTENT,
                )
            }
            Surface(
                onClick = onRetry,
                color = VIEWER_RETRY_CONTAINER,
                contentColor = VIEWER_CONTENT,
                shape = RoundedCornerShape(17.dp),
            ) {
                Text(
                    stringResource(
                        if (deferred) R.string.action_load_anyway else R.string.action_retry,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/*
 * Fixed colours rather than theme roles: this screen is the same near-black in light and dark mode,
 * because it is a lightbox — the image is the content and everything else steps out of its way.
 */
private val VIEWER_BACKGROUND = Color(0xFF0E0E11)
private val VIEWER_CONTENT = Color(0xFFE9E9EE)
private val VIEWER_SECONDARY_CONTENT = Color(0xFFC9C9D2)
private val VIEWER_INACTIVE_DOT = Color(0xFF5A5A64)
private val VIEWER_ACTION_CONTAINER = Color(0x1AFFFFFF)
private val VIEWER_RETRY_CONTAINER = Color(0x24FFFFFF)
private val VIEWER_WARNING = Color(0xFFF2B8B5)

private const val DOUBLE_TAP_SCALE = 2.5f
private const val MAX_SCALE = 5f
private val DISMISS_DRAG_THRESHOLD = 120.dp

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "8h 图片查看器")
@Composable
private fun ImageViewerPreview() {
    ImageViewerScreen(
        urls = listOf("https://www.nodeseek.com/static/nft-list-ruleset.png"),
        initialIndex = 0,
        onClose = {},
    )
}
