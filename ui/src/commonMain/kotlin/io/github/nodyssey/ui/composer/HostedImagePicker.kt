package io.github.nodyssey.ui.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nodyssey.data.imagehost.HostedImage
import io.github.nodyssey.data.imagehost.ImageHostError
import io.github.nodyssey.data.imagehost.ImageHostException
import io.github.nodyssey.data.imagehost.ImageHostRepository
import io.github.nodyssey.data.sticker.stickerNameFromUrl
import io.github.nodyssey.ui.common.PlazaSheet
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.hosted_image_insert_count
import io.github.nodyssey.ui.resources.hosted_image_pick_title
import io.github.nodyssey.ui.resources.sticker_host_clear
import io.github.nodyssey.ui.resources.sticker_host_empty
import io.github.nodyssey.ui.sticker.HostImageGrid
import io.github.nodyssey.ui.sticker.HostImagesFailed
import io.github.nodyssey.ui.sticker.HostListState
import io.github.nodyssey.ui.sticker.LocalStickerNavigation
import io.github.nodyssey.ui.sticker.rememberLeavingStickerNavigation
import io.github.nodyssey.ui.sticker.stickerMarkdown
import io.github.plaza.core.runCatchingExceptCancellation
import io.github.plaza.designsys.component.PlazaLoadingIndicator
import io.github.plaza.designsys.editor.appendBlock
import io.github.plaza.designsys.theme.Spacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The selected image host, for the editors' 从图床选择 sheet.
 *
 * A composition local for the reason [io.github.nodyssey.ui.sticker.LocalStickerLibrary] is one: the
 * sheet is drawn under three editors whose ViewModels have nothing else to do with the host's list.
 * Null where no shell provided one — a preview, a test — and the key then does nothing.
 */
val LocalImageHostRepository = staticCompositionLocalOf<ImageHostRepository?> { null }

/** What the 图床 key inserts: one picture already on the host, by its link. */
class HostedImagePickerViewModel(
    private val repository: ImageHostRepository,
) : ViewModel() {
    private val _host = MutableStateFlow<HostListState>(HostListState.Loading)
    val host: StateFlow<HostListState> = _host.asStateFlow()

    /** By URL, in the order they were picked — which is the order they go into the text. */
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private var loading: Job? = null

    /**
     * Fetches the list, dropping a fetch still in flight so that only the latest answer lands. The
     * selection stays: a retry should not undo the picking that was done before it.
     */
    fun load() {
        loading?.cancel()
        _host.value = HostListState.Loading
        loading = viewModelScope.launch {
            _host.value = runCatchingExceptCancellation { repository.images() }.fold(
                onSuccess = { HostListState.Loaded(it) },
                onFailure = { HostListState.Failed((it as? ImageHostException)?.error ?: ImageHostError.Network) },
            )
        }
    }

    /** Opened with nothing loaded yet — a first open, or one after [reset]. A rotation is neither. */
    val needsLoad: Boolean get() = loading == null

    /**
     * Back to empty when the sheet closes. The ViewModel outlives the sheet — it belongs to the screen
     * — and without this the next open would draw last time's list and picks for a frame before its
     * own fetch began, when an upload from the editor a minute ago belongs in the list.
     */
    fun reset() {
        loading?.cancel()
        loading = null
        _host.value = HostListState.Loading
        _selection.value = emptySet()
    }

    fun toggle(url: String) = _selection.update { if (url in it) it - url else it + url }

    fun select(url: String) = _selection.update { it + url }

    fun clear() {
        _selection.value = emptySet()
    }

    /** The picked pictures as Markdown, in picking order. */
    fun selectedMarkdown(): List<String> {
        val byUrl = (_host.value as? HostListState.Loaded)?.images.orEmpty().associateBy(HostedImage::url)
        return _selection.value.mapNotNull { url ->
            byUrl[url]?.let { stickerMarkdown(stickerNameFromUrl(it.fileName), it.url) }
        }
    }
}

/**
 * 从图床选择: the selected host's pictures, picked into [bodyState] on lines of their own.
 *
 * One sheet for all three editors. Appended rather than dropped at the caret, the way an upload
 * lands, so that a picture from the host and a fresh one end up in the same place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HostedImagePickerSheet(
    bodyState: TextFieldState,
    onDismiss: () -> Unit,
) {
    val repository = LocalImageHostRepository.current ?: return
    val viewModel = viewModel(key = "hosted-image-picker") { HostedImagePickerViewModel(repository) }
    val state by viewModel.host.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { if (viewModel.needsLoad) viewModel.load() }
    val close: () -> Unit = {
        viewModel.reset()
        onDismiss()
    }

    // 去连接图床 leaves from in here, and has to close the sheet on its way out.
    CompositionLocalProvider(LocalStickerNavigation provides rememberLeavingStickerNavigation(close)) {
        PlazaSheet(onDismiss = close, title = stringResource(Res.string.hosted_image_pick_title)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                when (val current = state) {
                    HostListState.Loading ->
                        Box(Modifier.fillMaxWidth().height(LOADING_HEIGHT), contentAlignment = Alignment.Center) {
                            PlazaLoadingIndicator()
                        }

                    is HostListState.Failed -> HostImagesFailed(current.error, onRetry = viewModel::load)

                    is HostListState.Loaded -> {
                        if (current.images.isEmpty()) {
                            Text(
                                stringResource(Res.string.sticker_host_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            HostImageGrid(
                                images = current.images,
                                selection = selection,
                                onToggle = viewModel::toggle,
                                onDragSelect = viewModel::select,
                                maxHeight = GRID_HEIGHT,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = viewModel::clear, enabled = selection.isNotEmpty()) {
                                Text(stringResource(Res.string.sticker_host_clear))
                            }
                            Button(
                                onClick = {
                                    val markdown = viewModel.selectedMarkdown()
                                    bodyState.edit { markdown.forEach { appendBlock(it) } }
                                    close()
                                },
                                enabled = selection.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(Res.string.hosted_image_insert_count, selection.size))
                            }
                        }
                    }
                }
            }
        }
    }
}

private val LOADING_HEIGHT = 300.dp

/** Taller than 添加表情's tab: here the grid is the whole sheet rather than one tab of four. */
private val GRID_HEIGHT = 420.dp
