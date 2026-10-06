package io.github.nodyssey.ui.sticker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.sticker.LinkProbe
import io.github.nodyssey.data.sticker.MySticker
import io.github.nodyssey.data.sticker.stickerNameFromUrl
import io.github.nodyssey.ui.account.formatBytes
import io.github.nodyssey.ui.common.PlazaSheet
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.sticker_added
import io.github.nodyssey.ui.resources.sticker_added_none
import io.github.nodyssey.ui.resources.sticker_image_add
import io.github.nodyssey.ui.resources.sticker_image_add_desc
import io.github.nodyssey.ui.resources.sticker_image_already
import io.github.nodyssey.ui.resources.sticker_image_copy
import io.github.nodyssey.ui.resources.sticker_image_link_copied
import io.github.nodyssey.ui.resources.sticker_image_of
import io.github.nodyssey.ui.resources.sticker_image_open
import io.github.nodyssey.ui.resources.sticker_image_save
import io.github.nodyssey.ui.resources.sticker_undo
import io.github.nodyssey.ui.resources.viewer_save_failed
import io.github.nodyssey.ui.resources.viewer_save_no_permission
import io.github.nodyssey.ui.resources.viewer_save_unsupported
import io.github.nodyssey.ui.resources.viewer_saved
import io.github.nodyssey.ui.viewer.SaveOutcome
import io.github.nodyssey.ui.viewer.rememberImageGallerySaver
import io.github.plaza.core.AppDispatchers
import io.github.plaza.designsys.component.GroupCard
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.image.allowMeteredImage
import io.github.plaza.designsys.richtext.LocalImageLongPress
import io.github.plaza.designsys.theme.Spacing
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** A picture long-pressed in a post body, with whose floor it was on. */
data class ImageActionRequest(
    val url: String,
    val author: String?,
    val floor: String?,
)

/** Opens 1a's sheet for a picture. Provided by a screen that hosts it; null leaves pictures tap-only. */
val LocalImageActions = staticCompositionLocalOf<((ImageActionRequest) -> Unit)?> { null }

/**
 * Hands [content]'s pictures a long press that knows they are [author]'s, on [floor] — what the
 * sheet's title says. Does nothing on a screen with no [LocalImageActions].
 */
@Composable
fun ImageActionsFrom(
    author: String?,
    floor: String?,
    content: @Composable () -> Unit,
) {
    val open = LocalImageActions.current
    if (open == null) {
        content()
    } else {
        val onLongPress = remember(open, author, floor) { { url: String -> open(ImageActionRequest(url, author, floor)) } }
        CompositionLocalProvider(LocalImageLongPress provides onLongPress, content = content)
    }
}

/** The open sheet, if any; [open] is what goes into [LocalImageActions]. */
@Stable
class ImageActionsState {
    var request by mutableStateOf<ImageActionRequest?>(null)
        internal set
    val open: (ImageActionRequest) -> Unit = { request = it }
}

@Composable
fun rememberImageActionsState(): ImageActionsState = remember { ImageActionsState() }

/**
 * 1a: what a long press on a picture in a post offers — 添加到我的表情 first, then the three things
 * anyone does with a picture. Outcomes go to [snackbarHostState], the adding one with its undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageActionsSheet(
    state: ImageActionsState,
    snackbarHostState: SnackbarHostState,
) {
    // Above the early return, so they belong to the screen hosting the sheet rather than to the sheet:
    // every action closes the sheet first, and a scope remembered past the return would leave the
    // composition with it — cancelling a save half-written and an add before its 撤销 is offered.
    val scope = rememberCoroutineScope()
    val saver = rememberImageGallerySaver(remember { AppDispatchers() })
    val request = state.request ?: return
    val library = LocalStickerLibrary.current
    val uriHandler = LocalUriHandler.current
    val copy = rememberClipboardCopy()
    val mine by remember(library) { library?.mine ?: flowOf(emptyList()) }.collectAsStateWithLifecycle(emptyList())
    val alreadyMine = mine.any { it.url == request.url }
    // A site sticker is in the panel already, and inserts as its shortcode; adding its picture
    // would make a second, worse copy of it.
    val isSiteSticker = NodeSeekSite.isStickerUrl(request.url)
    var size by remember(request.url) { mutableStateOf<Long?>(null) }
    var dimensions by remember(request.url) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(request.url, library) {
        (library?.probe(request.url) as? LinkProbe.Ok)?.sizeBytes?.let { size = it }
    }
    val dismiss = { state.request = null }
    val copiedText = stringResource(Res.string.sticker_image_link_copied)

    PlazaSheet(onDismiss = dismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                val context = LocalPlatformContext.current
                val imageRequest = remember(request.url) {
                    ImageRequest.Builder(context).data(request.url).allowMeteredImage(true).build()
                }
                Box(Modifier.size(64.dp).clip(MaterialTheme.shapes.small)) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onSuccess = { success ->
                            val image = success.result.image
                            dimensions = image.width to image.height
                        },
                        modifier = Modifier.size(64.dp),
                    )
                    if (request.url.substringBefore('?').endsWith(".gif", ignoreCase = true)) {
                        Surface(
                            color = MaterialTheme.colorScheme.inverseSurface,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.align(Alignment.BottomStart).padding(3.dp),
                        ) {
                            Text("GIF", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 3.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (request.author != null && request.floor != null) {
                        Text(
                            stringResource(Res.string.sticker_image_of, request.author, request.floor),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        request.url.removePrefix("https://").removePrefix("http://"),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(
                        dimensions?.let { (w, h) -> "$w × $h" },
                        size?.let { formatBytes(it) },
                    ).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (library != null && !isSiteSticker) {
                GroupCard {
                    GroupedRow(
                        title = stringResource(if (alreadyMine) Res.string.sticker_image_already else Res.string.sticker_image_add),
                        subtitle = stringResource(Res.string.sticker_image_add_desc).takeUnless { alreadyMine },
                        icon = PlazaIcons.Mood,
                        first = true,
                        last = true,
                        enabled = !alreadyMine,
                        showChevron = false,
                        onClick = {
                            dismiss()
                            scope.launch {
                                val added = library.add(listOf(MySticker(request.url, stickerNameFromUrl(request.url))))
                                if (added.isEmpty()) {
                                    // The row is disabled for a link already saved, so this is an
                                    // http picture, which 我的 does not keep.
                                    snackbarHostState.showSnackbar(getString(Res.string.sticker_added_none))
                                    return@launch
                                }
                                val result = snackbarHostState.showSnackbar(
                                    message = getString(Res.string.sticker_added),
                                    actionLabel = getString(Res.string.sticker_undo),
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) library.remove(added)
                            }
                        },
                    )
                }
            }
            GroupCard {
                GroupedRow(
                    title = stringResource(Res.string.sticker_image_save),
                    icon = PlazaIcons.Download,
                    first = true,
                    showChevron = false,
                    onClick = {
                        dismiss()
                        scope.launch {
                            val message = when (saver.save(request.url)) {
                                SaveOutcome.SAVED -> Res.string.viewer_saved
                                SaveOutcome.UNSUPPORTED_OS -> Res.string.viewer_save_unsupported
                                SaveOutcome.PERMISSION_DENIED -> Res.string.viewer_save_no_permission
                                SaveOutcome.FAILED -> Res.string.viewer_save_failed
                            }
                            snackbarHostState.showSnackbar(getString(message))
                        }
                    },
                )
                GroupedRow(
                    title = stringResource(Res.string.sticker_image_copy),
                    icon = PlazaIcons.Link,
                    showChevron = false,
                    onClick = {
                        dismiss()
                        copy("image", request.url, copiedText)
                    },
                )
                GroupedRow(
                    title = stringResource(Res.string.sticker_image_open),
                    icon = PlazaIcons.OpenInNew,
                    last = true,
                    showChevron = false,
                    onClick = {
                        dismiss()
                        runCatching { uriHandler.openUri(request.url) }
                    },
                )
            }
        }
    }
}
