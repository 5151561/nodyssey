package io.github.nodyssey.ui.sticker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nodyssey.data.sticker.StickerSourceError
import io.github.nodyssey.data.sticker.StickerSubscription
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.sticker_manage_github
import io.github.nodyssey.ui.resources.sticker_manage_list_separator
import io.github.nodyssey.ui.resources.sticker_manage_unsubscribe
import io.github.nodyssey.ui.resources.sticker_manage_unsubscribe_body
import io.github.nodyssey.ui.resources.sticker_manage_unsubscribe_confirm
import io.github.nodyssey.ui.resources.sticker_manage_up_to_date
import io.github.nodyssey.ui.resources.sticker_sources_check_all
import io.github.nodyssey.ui.resources.sticker_sources_empty
import io.github.nodyssey.ui.resources.sticker_sources_folder_new
import io.github.nodyssey.ui.resources.sticker_sources_gone
import io.github.nodyssey.ui.resources.sticker_sources_gone_body
import io.github.nodyssey.ui.resources.sticker_sources_latest
import io.github.nodyssey.ui.resources.sticker_sources_meta
import io.github.nodyssey.ui.resources.sticker_sources_subscribe_new
import io.github.nodyssey.ui.resources.sticker_update_action
import io.github.plaza.designsys.component.InlineBanner
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.SectionNote
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * GitHub 订阅 (1i): one card per source. Tapping a card opens 添加表情 › GitHub on that source's
 * folders (1f); an update is taken from here, and a source whose repository is gone says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerSourcesRoute(
    viewModel: StickerManageViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val errors by viewModel.sourceErrors.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val checkError by viewModel.checkError.collectAsStateWithLifecycle()
    val upToDate by viewModel.checkedUpToDate.collectAsStateWithLifecycle()
    val library = LocalStickerLibrary.current
    val addViewModel = library?.let { viewModel(key = "sticker-add") { StickerAddViewModel(it) } }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<StickerNotice?>(null) }
    val appBarState = rememberOneHandAppBarState()

    fun openAdd(configure: StickerAddViewModel.() -> Unit) {
        addViewModel ?: return
        addViewModel.reset()
        addViewModel.configure()
        addOpen = true
    }

    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.sticker_manage_github),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { viewModel.checkUpdates(force = true) },
                        enabled = !checking && subscriptions.isNotEmpty(),
                        modifier = Modifier.padding(end = Spacing.sm),
                    ) { Text(stringResource(Res.string.sticker_sources_check_all)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .readableWidth()
                    .padding(horizontal = LayerPageGutter, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (subscriptions.isEmpty()) SectionNote(stringResource(Res.string.sticker_sources_empty))
                subscriptions.forEach { subscription ->
                    SourceCard(
                        subscription = subscription,
                        gone = errors[subscription.slug] == StickerSourceError.NotFound,
                        onOpen = { openAdd { editSource(subscription.slug) } },
                        onUpdate = { viewModel.applyUpdate(subscription.slug) },
                        onUnsubscribe = { confirming = subscription.slug },
                    )
                }
                checkError?.takeIf { it != StickerSourceError.NotFound }?.let {
                    Text(sourceErrorText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (upToDate) {
                    Text(
                        stringResource(Res.string.sticker_manage_up_to_date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (addViewModel != null) {
                    OutlinedButton(
                        onClick = { openAdd { selectTab(StickerAddTab.GITHUB) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(Spacing.sm))
                        Text(stringResource(Res.string.sticker_sources_subscribe_new))
                    }
                }
            }
            notice?.let { current ->
                LaunchedEffect(current) {
                    delay(4_000)
                    notice = null
                }
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.md)) { Text(current.text) }
            }
        }
    }

    if (addOpen && addViewModel != null) {
        StickerAddSheet(viewModel = addViewModel, onDismiss = { addOpen = false }, onNotice = { notice = it })
    }
    confirming?.let { slug ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(Res.string.sticker_manage_unsubscribe_confirm, slug)) },
            text = { Text(stringResource(Res.string.sticker_manage_unsubscribe_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unsubscribe(slug)
                    confirming = null
                }) { Text(stringResource(Res.string.sticker_manage_unsubscribe), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SourceCard(
    subscription: StickerSubscription,
    gone: Boolean,
    onOpen: () -> Unit,
    onUpdate: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    LayerCard(onClick = if (gone) null else onOpen, contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Icon(
                if (gone) PlazaIcons.FolderOff else PlazaIcons.FolderZip,
                contentDescription = null,
                tint = if (gone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Column(Modifier.weight(1f)) {
                Text("${subscription.owner} / ${subscription.repo}", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        gone -> stringResource(Res.string.sticker_sources_gone)
                        subscription.hasUpdate -> stringResource(Res.string.sticker_sources_meta, subscription.folders.size, subscription.pinnedSha.take(7))
                        else -> stringResource(Res.string.sticker_sources_latest, subscription.folders.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (gone) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!gone) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        when {
            gone -> {
                Text(
                    stringResource(Res.string.sticker_sources_gone_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onUnsubscribe) {
                    Text(stringResource(Res.string.sticker_manage_unsubscribe), color = MaterialTheme.colorScheme.error)
                }
            }

            subscription.hasUpdate -> {
                val separator = stringResource(Res.string.sticker_manage_list_separator)
                val changed = subscription.folders.filter { it.newCount > 0 }
                    .map { stringResource(Res.string.sticker_sources_folder_new, it.name, it.newCount) }
                InlineBanner(
                    // A branch that only renamed or removed pictures has no +n to show; 更新 still applies it.
                    text = changed.joinToString(separator).ifEmpty { stringResource(Res.string.sticker_update_action) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    action = { FilledTonalButton(onClick = onUpdate) { Text(stringResource(Res.string.sticker_update_action)) } },
                )
            }
        }
    }
}
