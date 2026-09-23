package io.github.nodyssey.ui.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.UploadFailure
import io.github.nodyssey.data.composer.UploadStatus
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.composer_image_failed
import io.github.nodyssey.ui.resources.composer_image_failed_count
import io.github.nodyssey.ui.resources.composer_image_failed_reason
import io.github.nodyssey.ui.resources.composer_image_remove
import io.github.nodyssey.ui.resources.composer_image_retry_one
import io.github.nodyssey.ui.resources.composer_image_uploaded
import io.github.nodyssey.ui.resources.composer_image_uploading
import io.github.nodyssey.ui.resources.composer_image_waiting
import io.github.nodyssey.ui.resources.composer_upload_challenge
import io.github.nodyssey.ui.resources.composer_upload_invalid_key
import io.github.nodyssey.ui.resources.composer_upload_network
import io.github.nodyssey.ui.resources.composer_upload_not_configured
import io.github.nodyssey.ui.resources.composer_upload_rejected
import io.github.plaza.designsys.component.ImageFallback
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.LocalEinkMode
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * "3 张图片上传失败 · 未配置图床，请在 账号设置 › 图床 填写 API Key".
 *
 * The count alone was the whole message before uploads were real, and it is not enough now: the two
 * failures a user actually hits — no API key yet, and a key that has been regenerated on the website
 * — are both fixed in one screen and neither is fixed by tapping 重试. The host's own sentence wins
 * over ours when it sent one, because it is the only source that knows *this* file was too large.
 */
@Composable
internal fun uploadFailureText(
    failedCount: Int,
    failure: UploadFailure?,
    detail: String?,
): String {
    val count = stringResource(Res.string.composer_image_failed_count, failedCount)
    val reason = detail?.takeIf(String::isNotBlank) ?: when (failure) {
        UploadFailure.NOT_CONFIGURED -> stringResource(Res.string.composer_upload_not_configured)
        UploadFailure.INVALID_KEY -> stringResource(Res.string.composer_upload_invalid_key)
        UploadFailure.REJECTED -> stringResource(Res.string.composer_upload_rejected)
        UploadFailure.CHALLENGE -> stringResource(Res.string.composer_upload_challenge)
        UploadFailure.NETWORK -> stringResource(Res.string.composer_upload_network)
        UploadFailure.UNKNOWN, null -> return count
    }
    return stringResource(Res.string.composer_image_failed_reason, count, reason)
}

/**
 * The attachment strip from 1d, shared by both editors.
 *
 * All four states sit side by side on purpose: uploads run one at a time, so "等待中" is a real
 * state a user with four screenshots will see, and showing it beats a spinner that implies
 * everything is moving at once. A failed cell is itself the retry target — the label says so — and
 * the batch retry lives on the Snackbar the caller shows.
 *
 * The status is written inside the tile rather than under it (1d): 88dp tiles are big enough to
 * carry it, and a caption row under every tile was a second line of type for what is mostly "已上传".
 * That one is the exception — a finished upload shows its check mark and says its status only to a
 * screen reader, because on a photo the word is noise once the mark is there.
 */
@Composable
fun AttachmentTray(
    attachments: List<ImageAttachment>,
    onRemove: (ImageAttachment) -> Unit,
    onRetry: (ImageAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        attachments.forEach { attachment ->
            AttachmentCell(
                attachment = attachment,
                onRemove = { onRemove(attachment) },
                onRetry = { onRetry(attachment) },
            )
        }
    }
}

@Composable
private fun AttachmentCell(
    attachment: ImageAttachment,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
    val failed = attachment.status == UploadStatus.FAILED
    val retryDescription = stringResource(Res.string.composer_image_retry_one, attachment.name)
    val label = attachment.statusLabel()
    Box(
        modifier = Modifier
            .size(THUMBNAIL)
            .clip(TileShape)
            .background(
                if (attachment.status == UploadStatus.WAITING) {
                    MaterialTheme.colorScheme.surfaceContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ).then(
                if (failed) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.error, TileShape)
                } else {
                    Modifier
                },
            ).then(
                when {
                    failed -> Modifier.clickable(onClick = onRetry).semantics { contentDescription = retryDescription }
                    attachment.status == UploadStatus.UPLOADED -> Modifier.semantics { contentDescription = label }
                    else -> Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (attachment.status) {
            UploadStatus.UPLOADING -> {
                Thumbnail(attachment, dimmed = true)
                StatusPill(label)
                // A bar along the tile's bottom edge rather than a ring over the photo (1d): it
                // leaves the picture readable and the percentage has the middle to itself.
                LinearProgressIndicator(
                    progress = { attachment.progress },
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).describedAsLoading(),
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Butt,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }

            UploadStatus.UPLOADED -> {
                Thumbnail(attachment, dimmed = false)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            UploadStatus.FAILED -> StatusGlyph(Icons.Default.Refresh, label, MaterialTheme.colorScheme.error)

            UploadStatus.WAITING -> StatusGlyph(PlazaIcons.Schedule, label, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RemoveBadge(
            name = attachment.name,
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        )
    }
}

/** The glyph-and-caption middle of a tile with no picture to show yet — failed, or still queued. */
@Composable
private fun StatusGlyph(
    icon: ImageVector,
    label: String,
    tint: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
    }
}

/**
 * The percentage over an uploading photo, on its own pill: text straight on a photo is legible only
 * where the photo happens to be dark, and on paper there is no scrim to lean on at all.
 */
@Composable
private fun StatusPill(label: String) {
    Surface(shape = CircleShape, color = LocalPlazaLayers.current.raised) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
        )
    }
}

@Composable
private fun Thumbnail(
    attachment: ImageAttachment,
    dimmed: Boolean,
) {
    // An unreadable pick — a URI the picker handed back and the provider has since revoked — leaves
    // the frame's status label and remove badge hanging around nothing at all. The mark says which
    // of the tray's squares is the one that cannot be shown.
    var failed by remember(attachment.source) { mutableStateOf(false) }
    if (failed) {
        ImageFallback(modifier = Modifier.size(THUMBNAIL))
    } else {
        AsyncImage(
            model = attachment.source,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { failed = true },
            modifier = Modifier.size(THUMBNAIL),
        )
    }
    if (dimmed) {
        // A translucent scrim over a photo is two dithered layers on top of each other. On paper the
        // thumbnail is left alone and outlined instead — "this one is spoken for" without a wash.
        if (LocalEinkMode.current) {
            Box(
                Modifier
                    .size(THUMBNAIL)
                    .border(2.dp, MaterialTheme.colorScheme.outline, TileShape),
            )
        } else {
            Box(Modifier.size(THUMBNAIL).background(Color.Black.copy(alpha = SCRIM_ALPHA)))
        }
    }
}

@Composable
private fun RemoveBadge(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.composer_image_remove, name)
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(
                if (LocalEinkMode.current) {
                    MaterialTheme.colorScheme.inverseSurface
                } else {
                    Color.Black.copy(alpha = BADGE_ALPHA)
                },
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun ImageAttachment.statusLabel(): String = when (status) {
    UploadStatus.UPLOADING -> stringResource(Res.string.composer_image_uploading, (progress * 100).roundToInt())
    UploadStatus.UPLOADED -> stringResource(Res.string.composer_image_uploaded)
    UploadStatus.FAILED -> stringResource(Res.string.composer_image_failed)
    UploadStatus.WAITING -> stringResource(Res.string.composer_image_waiting)
}

private val THUMBNAIL = 88.dp
private val TileShape = RoundedCornerShape(18.dp)
private const val BADGE_ALPHA = 0.5f
private const val SCRIM_ALPHA = 0.35f
