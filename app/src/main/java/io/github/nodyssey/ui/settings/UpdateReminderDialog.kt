package io.github.nodyssey.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.nodyssey.R
import io.github.nodyssey.core.update.releaseNotesText
import io.github.nodyssey.data.update.AppRelease
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Spacing

/**
 * 启动提醒 — the one thing that actually tells the owner of a sideloaded APK that a fix shipped.
 *
 * Says what the release is, how big it is, and what changed, because "有新版本，要更新吗" with none of
 * that is a question nobody can answer. 下载并安装 starts the download and moves on to 关于, which owns
 * the rest of the flow — progress, the install permission, and every way it can fail.
 *
 * Dismissing by back or by tapping outside is [onPostpone] rather than a third outcome: 稍后 is the
 * harmless answer already (the dot on 设置 stays and 关于 still offers the update), so the gesture that
 * means "not now" should not be the one that brings the dialog back tomorrow.
 */
@Composable
fun UpdateReminderDialog(
    release: AppRelease,
    onDownload: () -> Unit,
    onPostpone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onPostpone,
        modifier = modifier,
        icon = {
            Icon(
                NodysseyIcons.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                stringResource(R.string.update_reminder_title, release.versionName),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (release.sizeBytes > 0L) {
                    Text(
                        stringResource(
                            R.string.update_reminder_size,
                            Formatter.formatShortFileSize(context, release.sizeBytes),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val notes = remember(release.notes) { releaseNotesText(release.notes) }
                if (notes.isNotBlank()) {
                    // Scrolled rather than truncated: a release that changed a lot is exactly the one
                    // worth reading before installing, and the dialog cannot grow to hold it.
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .heightIn(max = NOTES_MAX_HEIGHT)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text(stringResource(R.string.about_update_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onPostpone) {
                Text(stringResource(R.string.update_reminder_later))
            }
        },
    )
}

/** Leaves the two buttons and the version on screen on a short device; the rest scrolls. */
private val NOTES_MAX_HEIGHT = 220.dp

@Preview(showBackground = true, widthDp = 360, heightDp = 640, name = "启动提醒 · 发现新版本")
@Composable
private fun UpdateReminderDialogPreview() {
    NodysseyTheme {
        UpdateReminderDialog(
            release =
            AppRelease(
                versionName = "1.2.4",
                tag = "v1.2.4",
                notes =
                "### 改进\n- 帖子里的表格按内容定列宽\n\n### 修复\n- 表格里的链接可以点了\n" +
                    "- 签名不再压过它下面的正文",
                downloadUrl = "https://example.invalid/nodyssey-v1.2.4.apk",
                assetName = "nodyssey-v1.2.4.apk",
                sizeBytes = 8_800_000,
                htmlUrl = AppLinks.RELEASES,
            ),
            onDownload = {},
            onPostpone = {},
        )
    }
}
