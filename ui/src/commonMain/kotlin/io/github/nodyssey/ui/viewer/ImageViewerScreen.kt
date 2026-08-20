package io.github.nodyssey.ui.viewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import io.github.nodyssey.ui.common.rememberShareText
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.action_load_anyway
import io.github.nodyssey.ui.resources.action_open_in_browser
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.action_share
import io.github.nodyssey.ui.resources.viewer_deferred_wifi_only
import io.github.nodyssey.ui.resources.viewer_load_failed
import io.github.nodyssey.ui.resources.viewer_page
import io.github.nodyssey.ui.resources.viewer_save
import io.github.nodyssey.ui.resources.viewer_save_failed
import io.github.nodyssey.ui.resources.viewer_save_no_permission
import io.github.nodyssey.ui.resources.viewer_save_unsupported
import io.github.nodyssey.ui.resources.viewer_saved
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.imageLoadFailureText
import io.github.plaza.designsys.image.ImageLoadFailure
import io.github.plaza.designsys.image.ImagesDeferredException
import io.github.plaza.designsys.image.allowMeteredImage
import io.github.plaza.designsys.image.diagnoseImageFailure
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import org.jetbrains.compose.resources.stringResource
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

    val shareText = rememberShareText()
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, urls.lastIndex)) { urls.size }
    var zoomed by remember { mutableStateOf(false) }

    val notice =
        when (saveOutcome) {
            null -> null
            SaveOutcome.SAVED -> stringResource(Res.string.viewer_saved)
            SaveOutcome.UNSUPPORTED_OS -> stringResource(Res.string.viewer_save_unsupported)
            SaveOutcome.PERMISSION_DENIED -> stringResource(Res.string.viewer_save_no_permission)
            SaveOutcome.FAILED -> stringResource(Res.string.viewer_save_failed)
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
                    icon = PlazaIcons.Download,
                    label = stringResource(Res.string.viewer_save),
                    modifier = Modifier.weight(1f),
                ) {
                    onSave(urls[pagerState.currentPage])
                }
                ViewerAction(
                    icon = Icons.Default.Share,
                    label = stringResource(Res.string.action_share),
                    modifier = Modifier.weight(1f),
                ) {
                    // Shares the address rather than the bytes: NodeSeek attachments are hosted URLs,
                    // and a link stays a link for the person receiving it.
                    shareText(urls[pagerState.currentPage], null)
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
                contentDescription = stringResource(Res.string.action_close),
                tint = VIEWER_CONTENT,
            )
        }
        Text(
            text = stringResource(Res.string.viewer_page, page, total),
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
                PlazaIcons.OpenInNew,
                contentDescription = stringResource(Res.string.action_open_in_browser),
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
    val context = LocalPlatformContext.current
    val request =
        remember(url, allowMetered) {
            ImageRequest
                .Builder(context)
                .data(url)
                .allowMeteredImage(allowMetered)
                .build()
        }
    val dismissDragThresholdPx = with(LocalDensity.current) { DISMISS_DRAG_THRESHOLD.toPx() }

    /*
     * `canPan` is the whole reason this is not `detectTransformGestures`: that detector consumes every
     * position change once past touch slop, including a one-finger swipe on an unzoomed image, which
     * is the pager's page-turn. The predicate says a one-finger drag is only ours once the image is
     * actually zoomed — which is exactly the rule the hand-rolled detector here used to spell out, and
     * the reason the comment explaining it is now four lines instead of six.
     */
    val transform =
        rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, MAX_SCALE)
            offset = if (scale > 1f) offset + panChange else Offset.Zero
            onZoomChange(scale > 1f)
        }

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
            }.transformable(state = transform, canPan = { scale > 1f })
            .pointerInput(url, scale > 1f) {
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
                    val throwable = (state as AsyncImagePainter.State.Error).result.throwable
                    val deferred = throwable is ImagesDeferredException
                    ImageFailure(
                        url = url,
                        deferred = deferred,
                        // A skipped image has a reason already, and it is the sentence above.
                        failure = if (deferred) null else diagnoseImageFailure(throwable),
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
    failure: ImageLoadFailure?,
    onRetry: () -> Unit,
) {
    val reason = imageLoadFailureText(failure)
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
                        if (deferred) Res.string.viewer_deferred_wifi_only else Res.string.viewer_load_failed,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                // Why it failed, above which file failed: 重试 is on this card and the reason is what
                // says whether it is worth pressing — a Cloudflare challenge will refuse it again, and
                // 用浏览器打开 in the bar above is the way through.
                if (reason != null) {
                    Text(
                        reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = VIEWER_SECONDARY_CONTENT,
                    )
                }
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
                        if (deferred) Res.string.action_load_anyway else Res.string.action_retry,
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
