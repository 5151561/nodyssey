package io.github.nsreader.ui.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.nsreader.R
import io.github.nsreader.data.composer.ImageAttachment
import io.github.nsreader.data.composer.UploadFailure
import io.github.nsreader.data.composer.UploadStatus
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.theme.Spacing
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
    val count = stringResource(R.string.composer_image_failed_count, failedCount)
    val reason = detail?.takeIf(String::isNotBlank) ?: when (failure) {
        UploadFailure.NOT_CONFIGURED -> stringResource(R.string.composer_upload_not_configured)
        UploadFailure.INVALID_KEY -> stringResource(R.string.composer_upload_invalid_key)
        UploadFailure.REJECTED -> stringResource(R.string.composer_upload_rejected)
        UploadFailure.CHALLENGE -> stringResource(R.string.composer_upload_challenge)
        UploadFailure.NETWORK -> stringResource(R.string.composer_upload_network)
        UploadFailure.UNKNOWN, null -> return count
    }
    return stringResource(R.string.composer_image_failed_reason, count, reason)
}

/**
 * The attachment strip from C5, shared by both editors.
 *
 * All four states sit side by side on purpose: uploads run one at a time, so "等待中" is a real
 * state a user with four screenshots will see, and showing it beats a spinner that implies
 * everything is moving at once. A failed cell is itself the retry target — the label says so — and
 * the batch retry lives on the Snackbar the caller shows.
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
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
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
    val retryDescription = stringResource(R.string.composer_image_retry_one, attachment.name)
    // The dismiss badge hangs off the thumbnail's corner, so the cell reserves the overhang rather
    // than letting the Row clip it.
    Box(modifier = Modifier.padding(top = BADGE_OVERHANG, end = BADGE_OVERHANG)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(THUMBNAIL)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (attachment.status == UploadStatus.WAITING) {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                    ).then(
                        if (failed) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        },
                    ).then(
                        if (failed) Modifier.clickable(onClick = onRetry).semantics { contentDescription = retryDescription } else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when (attachment.status) {
                    UploadStatus.UPLOADING -> {
                        Thumbnail(attachment, dimmed = true)
                        CircularProgressIndicator(
                            progress = { attachment.progress },
                            modifier = Modifier.size(34.dp),
                            strokeWidth = 4.dp,
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }

                    UploadStatus.UPLOADED -> {
                        Thumbnail(attachment, dimmed = false)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    UploadStatus.FAILED -> Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(26.dp),
                    )

                    UploadStatus.WAITING -> Icon(
                        imageVector = NodeSeekIcons.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Text(
                text = attachment.statusLabel(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = if (failed) FontWeight.SemiBold else FontWeight.Normal,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.xs).size(width = THUMBNAIL, height = 16.dp),
            )
        }
        RemoveBadge(name = attachment.name, onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun Thumbnail(
    attachment: ImageAttachment,
    dimmed: Boolean,
) {
    AsyncImage(
        model = attachment.source,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(THUMBNAIL),
    )
    if (dimmed) {
        Box(Modifier.size(THUMBNAIL).background(Color.Black.copy(alpha = SCRIM_ALPHA)))
    }
}

@Composable
private fun RemoveBadge(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.composer_image_remove, name)
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = BADGE_ALPHA))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(13.dp),
        )
    }
}

@Composable
private fun ImageAttachment.statusLabel(): String = when (status) {
    UploadStatus.UPLOADING -> stringResource(R.string.composer_image_uploading, (progress * 100).roundToInt())
    UploadStatus.UPLOADED -> stringResource(R.string.composer_image_uploaded)
    UploadStatus.FAILED -> stringResource(R.string.composer_image_failed)
    UploadStatus.WAITING -> stringResource(R.string.composer_image_waiting)
}

private val THUMBNAIL = 68.dp
private val BADGE_OVERHANG = 6.dp
private const val BADGE_ALPHA = 0.55f
private const val SCRIM_ALPHA = 0.35f
