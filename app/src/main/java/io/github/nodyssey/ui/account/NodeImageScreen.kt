package io.github.nodyssey.ui.account

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.nodyssey.R
import io.github.nodyssey.core.NodeImageSite
import io.github.nodyssey.data.nodeimage.NodeImageError
import io.github.nodyssey.data.nodeimage.NodeImageItem
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.readableWidth
import java.util.Locale

@Composable
fun NodeImageRoute(
    viewModel: NodeImageViewModel,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { accountMessageText(it) }

    LaunchedEffect(state.message, messageText) {
        if (messageText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(messageText)
        viewModel.consumeMessage()
    }

    NodeImageScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onKeyInputChange = viewModel::updateKeyInput,
        onSaveKey = viewModel::saveKey,
        onRequestClearKey = viewModel::requestClearKey,
        onDismissClearKey = viewModel::dismissClearKey,
        onConfirmClearKey = viewModel::confirmClearKey,
        onRefresh = { viewModel.refresh() },
        onRequestDelete = viewModel::requestDelete,
        onDismissDelete = viewModel::dismissDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onOpenSite = { onOpenUrl(NodeImageSite.SITE_URL) },
        onOpenImage = onOpenUrl,
        modifier = modifier,
    )
}

/**
 * 图床 — the app's side of nodeimage.com.
 *
 * NodeSeek stores Markdown and nothing else, so every picture in a post is a link to somewhere the
 * forum does not run. That somewhere is nodeimage.com, and its credential is a key the user fetches
 * from that site's own API page. The screen therefore reads as a *connection*, not a preference: it
 * says whether the app is connected, offers the one action that changes that, and then shows what is
 * stored on the other end so the connection is worth something beyond a checkmark.
 *
 * The key field is emptied the moment it is saved and never refilled — the row above it shows a
 * masked fingerprint instead. A settings screen is the most-screenshotted surface in any app, and
 * this is the one string on it that would let somebody else upload under this account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeImageScreen(
    state: NodeImageUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onKeyInputChange: (String) -> Unit,
    onSaveKey: () -> Unit,
    onRequestClearKey: () -> Unit,
    onDismissClearKey: () -> Unit,
    onConfirmClearKey: () -> Unit,
    onRefresh: () -> Unit,
    onRequestDelete: (NodeImageItem) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onOpenSite: () -> Unit,
    onOpenImage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nodeimage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state.hasKey) {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AccountSectionLabel(stringResource(R.string.nodeimage_section_connection))
            ConnectionCard(state = state, onClearKey = onRequestClearKey)

            OutlinedTextField(
                value = state.keyInput,
                onValueChange = onKeyInputChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.keyInputError,
                label = { Text(stringResource(R.string.nodeimage_key_label)) },
                placeholder = { Text(stringResource(R.string.nodeimage_key_placeholder)) },
                // Not a password field: the value is pasted, never typed, and dots would hide a
                // mis-paste until the first upload fails with an unexplained 401.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                shape = AccountFieldShape,
                supportingText = {
                    Text(
                        stringResource(
                            if (state.keyInputError) {
                                R.string.nodeimage_key_invalid
                            } else {
                                R.string.nodeimage_key_helper
                            },
                        ),
                    )
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = onSaveKey,
                    enabled = state.keyInput.isNotBlank(),
                ) {
                    Text(
                        stringResource(
                            if (state.hasKey) R.string.nodeimage_key_replace else R.string.nodeimage_key_save,
                        ),
                    )
                }
                TextButton(onClick = onOpenSite) {
                    Text(stringResource(R.string.nodeimage_open_site))
                }
            }

            AccountSectionLabel(
                text = stringResource(R.string.nodeimage_section_images),
                modifier = Modifier.padding(top = Spacing.xs),
            )
            ImagesSection(
                state = state,
                onRequestDelete = onRequestDelete,
                onOpenImage = onOpenImage,
                onOpenSite = onOpenSite,
            )
        }
    }

    if (state.confirmingClearKey) {
        HighRiskDialog(
            icon = NodysseyIcons.Shield,
            title = stringResource(R.string.nodeimage_clear_key_title),
            body = stringResource(R.string.nodeimage_clear_key_body),
            confirmLabel = stringResource(R.string.nodeimage_clear_key_action),
            onConfirm = onConfirmClearKey,
            onDismiss = onDismissClearKey,
            destructive = true,
        )
    }

    state.deleting?.let { target ->
        HighRiskDialog(
            icon = Icons.Default.Delete,
            title = stringResource(R.string.nodeimage_delete_title),
            // Named in the dialog because thumbnails of the same screenshot are indistinguishable,
            // and this delete cannot be undone from anywhere in the app.
            body = stringResource(R.string.nodeimage_delete_body, target.fileName),
            confirmLabel = stringResource(R.string.nodeimage_delete_action),
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete,
            destructive = true,
        )
    }
}

@Composable
private fun ConnectionCard(
    state: NodeImageUiState,
    onClearKey: () -> Unit,
) {
    Surface(
        shape = AccountFieldShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        stringResource(
                            if (state.hasKey) {
                                R.string.nodeimage_connected
                            } else {
                                R.string.nodeimage_not_connected
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    )
                    StorageBadge(local = true)
                }
                Text(
                    text = state.savedKeyMask ?: stringResource(R.string.nodeimage_not_connected_hint),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = if (state.hasKey) FontFamily.Monospace else FontFamily.Default,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.hasKey) {
                TextButton(onClick = onClearKey) {
                    Text(
                        stringResource(R.string.nodeimage_clear_key),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagesSection(
    state: NodeImageUiState,
    onRequestDelete: (NodeImageItem) -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenSite: () -> Unit,
) {
    when {
        state.isLoadingImages -> Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(Modifier.size(22.dp))
        }

        /*
         * The host's list and delete endpoints turn the API key down — verified on device, with a
         * key that had just succeeded on upload. So this half of the screen cannot work, and it says
         * so and hands over to the website rather than showing an empty gallery that implies the
         * account has no images. The same shape the app uses for every other site-only page.
         */
        state.imagesError == NodeImageError.SessionRequired -> InfoCard(
            text = stringResource(R.string.nodeimage_error_session_required),
            action = stringResource(R.string.nodeimage_open_site) to onOpenSite,
        )

        state.imagesError != null -> InfoCard(stringResource(state.imagesError.messageRes()))

        state.images.isEmpty() -> InfoCard(stringResource(R.string.nodeimage_empty))

        else -> {
            Text(
                stringResource(
                    R.string.nodeimage_summary,
                    state.images.size,
                    formatBytes(state.totalBytes),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
            Column {
                state.images.forEachIndexed { index, item ->
                    ImageRow(
                        item = item,
                        onDelete = { onRequestDelete(item) },
                        onOpen = { onOpenImage(item.url) },
                    )
                    if (index != state.images.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageRow(
    item: NodeImageItem,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AsyncImage(
            model = item.url,
            contentDescription = item.fileName,
            modifier = Modifier.size(44.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onOpen),
        ) {
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    item.uploadTime?.takeIf(String::isNotBlank),
                    formatBytes(item.sizeBytes),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.nodeimage_delete_action),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun InfoCard(
    text: String,
    action: Pair<String, () -> Unit>? = null,
) {
    Surface(
        shape = AccountFieldShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.lg,
                bottom = if (action == null) Spacing.lg else Spacing.xs,
            ),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action?.let { (label, onClick) ->
                TextButton(
                    onClick = onClick,
                    modifier = Modifier.align(Alignment.End),
                ) { Text(label) }
            }
        }
    }
}

@StringRes
internal fun NodeImageError.messageRes(): Int = when (this) {
    NodeImageError.NotConfigured -> R.string.nodeimage_error_not_configured
    NodeImageError.InvalidKey -> R.string.nodeimage_error_invalid_key
    NodeImageError.SessionRequired -> R.string.nodeimage_error_session_required
    is NodeImageError.Rejected -> R.string.nodeimage_error_rejected
    NodeImageError.Cloudflare -> R.string.nodeimage_error_cloudflare
    is NodeImageError.Http -> R.string.nodeimage_error_http
    NodeImageError.Network -> R.string.status_network_title
    NodeImageError.Unparsable -> R.string.status_unparsable_title
}

/** `1536` → `1.5 KB`. Binary units, because that is what the host reports its own sizes in. */
internal fun formatBytes(bytes: Long): String {
    if (bytes < UNIT) return "$bytes B"
    var value = bytes.toDouble()
    var index = -1
    while (value >= UNIT && index < UNITS.lastIndex) {
        value /= UNIT
        index++
    }
    return String.format(Locale.US, "%.1f %s", value, UNITS[index])
}

private const val UNIT = 1024
private val UNITS = listOf("KB", "MB", "GB")

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun NodeImagePreview() {
    NodysseyTheme {
        NodeImageScreen(
            state = NodeImageUiState(
                isLoadingKey = false,
                savedKeyMask = "cfbe……ac3f",
                images = listOf(
                    NodeImageItem(
                        imageId = "Yzk9P567",
                        fileName = "Yzk9P567.webp",
                        url = "https://cdn.nodeimage.com/i/Yzk9P567.webp",
                        uploadTime = "2026/7/28 12:04",
                        sizeBytes = 1214,
                        mimeType = "image/webp",
                    ),
                    NodeImageItem(
                        imageId = "vU7478n1",
                        fileName = "vU7478n1.webp",
                        url = "https://cdn.nodeimage.com/i/vU7478n1.webp",
                        uploadTime = "2026/7/27 09:31",
                        sizeBytes = 284_512,
                        mimeType = "image/webp",
                    ),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onKeyInputChange = {},
            onSaveKey = {},
            onRequestClearKey = {},
            onDismissClearKey = {},
            onConfirmClearKey = {},
            onRefresh = {},
            onRequestDelete = {},
            onDismissDelete = {},
            onConfirmDelete = {},
            onOpenSite = {},
            onOpenImage = {},
        )
    }
}
