package io.github.nodyssey.ui.settings

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.dns.DnsResolution
import io.github.nodyssey.data.dns.DohConfigProblem
import io.github.nodyssey.data.dns.DohProvider
import io.github.nodyssey.data.dns.DohServer
import io.github.nodyssey.data.dns.DohServerProblem
import io.github.nodyssey.ui.account.AccountMessageSnackbar
import io.github.nodyssey.ui.common.MediumButton
import io.github.nodyssey.ui.common.PlazaSheet
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_delete
import io.github.nodyssey.ui.resources.action_done
import io.github.nodyssey.ui.resources.doh_add_custom
import io.github.nodyssey.ui.resources.doh_bootstrap_invalid
import io.github.nodyssey.ui.resources.doh_bootstrap_label
import io.github.nodyssey.ui.resources.doh_bootstrap_placeholder
import io.github.nodyssey.ui.resources.doh_bootstrap_reset
import io.github.nodyssey.ui.resources.doh_details_note
import io.github.nodyssey.ui.resources.doh_drag_handle
import io.github.nodyssey.ui.resources.doh_fallback_hint
import io.github.nodyssey.ui.resources.doh_fallback_title
import io.github.nodyssey.ui.resources.doh_ipv6_hint
import io.github.nodyssey.ui.resources.doh_ipv6_title
import io.github.nodyssey.ui.resources.doh_limits_encrypted_only_hint
import io.github.nodyssey.ui.resources.doh_limits_hint
import io.github.nodyssey.ui.resources.doh_limits_title
import io.github.nodyssey.ui.resources.doh_master_hint
import io.github.nodyssey.ui.resources.doh_master_title
import io.github.nodyssey.ui.resources.doh_move_down
import io.github.nodyssey.ui.resources.doh_move_up
import io.github.nodyssey.ui.resources.doh_no_server
import io.github.nodyssey.ui.resources.doh_order_note
import io.github.nodyssey.ui.resources.doh_provider_alidns
import io.github.nodyssey.ui.resources.doh_provider_cloudflare
import io.github.nodyssey.ui.resources.doh_provider_custom_hint
import io.github.nodyssey.ui.resources.doh_provider_dnspod
import io.github.nodyssey.ui.resources.doh_provider_google
import io.github.nodyssey.ui.resources.doh_provider_title
import io.github.nodyssey.ui.resources.doh_proxy_hint
import io.github.nodyssey.ui.resources.doh_rank_backup
import io.github.nodyssey.ui.resources.doh_rank_first
import io.github.nodyssey.ui.resources.doh_save
import io.github.nodyssey.ui.resources.doh_server_add_title
import io.github.nodyssey.ui.resources.doh_servers_title_ordered
import io.github.nodyssey.ui.resources.doh_test
import io.github.nodyssey.ui.resources.doh_test_failure
import io.github.nodyssey.ui.resources.doh_test_result
import io.github.nodyssey.ui.resources.doh_title
import io.github.nodyssey.ui.resources.doh_url_invalid
import io.github.nodyssey.ui.resources.doh_url_label
import io.github.nodyssey.ui.resources.doh_url_placeholder
import io.github.nodyssey.ui.resources.doh_url_required
import io.github.nodyssey.ui.resources.doh_webview_hint
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.GroupedListItemSwitch
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.InlineBanner
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.SectionNote
import io.github.plaza.designsys.component.SectionNotes
import io.github.plaza.designsys.component.TonalTag
import io.github.plaza.designsys.component.groupedRowTitleStyle
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun DohSettingsRoute(
    viewModel: DohSettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    AccountMessageSnackbar(
        message = state.message,
        snackbarHostState = snackbarHostState,
        onShown = viewModel::consumeMessage,
    )

    DohSettingsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onEnabledChange = viewModel::setEnabled,
        onToggleServer = viewModel::toggleServer,
        onMoveServer = viewModel::moveServer,
        onOpenServer = viewModel::openServer,
        onAddServer = viewModel::addServer,
        onIncludeIPv6Change = viewModel::setIncludeIPv6,
        onFallbackChange = viewModel::setFallbackToSystem,
        onSave = viewModel::save,
        onTest = viewModel::test,
        modifier = modifier,
    )

    state.editing?.let { draft ->
        DohServerSheet(
            draft = draft,
            server = state.servers.firstOrNull { it.id == draft.id },
            onUrlChange = viewModel::updateEditorUrl,
            onBootstrapChange = viewModel::updateEditorBootstrap,
            onResetBootstrap = viewModel::resetEditorBootstrap,
            onDelete = viewModel::deleteEditorServer,
            onConfirm = viewModel::confirmEditor,
            onDismiss = viewModel::dismissEditor,
        )
    }
}

/**
 * 加密 DNS — which servers turn a hostname into an address for the app's own requests.
 *
 * Everything below the master switch is dimmed and inert while it is off, the same treatment
 * [ProxySettingsScreen] and [NotificationSettingsScreen] give the settings behind theirs.
 *
 * Where the platform tries servers in turn (`DohCapabilities.triesServersInOrder`), the list is 6f's:
 * a box to tick each server, the ticked ones on top in the order they are asked, dragged by a handle.
 * Where it takes one server, the same rows carry radio buttons instead. Either way a row opens its
 * details — address and bootstrap addresses — in [DohServerSheet].
 *
 * The note at the bottom sits outside that dimmed block on purpose, and it is the part of this screen
 * worth reading first: DoH answers a question about *names*, and a domain whose address is blocked,
 * reset or filtered by SNI is not being lied to about its name. Someone who reaches this screen after
 * a site stopped loading deserves to be told that before they type anything.
 */
@Composable
fun DohSettingsScreen(
    state: DohSettingsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onToggleServer: (String) -> Unit,
    onMoveServer: (id: String, toIndex: Int) -> Unit,
    onOpenServer: (String) -> Unit,
    onAddServer: () -> Unit,
    onIncludeIPv6Change: (Boolean) -> Unit,
    onFallbackChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appBarState = rememberOneHandAppBarState()
    val ordered = state.capabilities.triesServersInOrder
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.doh_title),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
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
                .padding(SettingsPagePadding),
            verticalArrangement = Arrangement.spacedBy(SettingsItemGap),
        ) {
            SettingsGroup {
                GroupedRow(
                    icon = PlazaIcons.Dns,
                    title = stringResource(Res.string.doh_master_title),
                    subtitle = stringResource(Res.string.doh_master_hint),
                    first = true,
                    last = true,
                    checked = state.enabled,
                    onCheckedChange = onEnabledChange,
                    trailing = { GroupedListItemSwitch(checked = state.enabled) },
                )
            }

            // No alpha over the block: every control in it takes `enabled` and dims itself.
            Column(verticalArrangement = Arrangement.spacedBy(SettingsItemGap)) {
                SectionLabel(
                    stringResource(if (ordered) Res.string.doh_servers_title_ordered else Res.string.doh_provider_title),
                )
                DohServerList(
                    servers = state.servers,
                    ordered = ordered,
                    enabled = state.enabled,
                    onToggle = onToggleServer,
                    onMove = onMoveServer,
                    onOpen = onOpenServer,
                    onAdd = onAddServer,
                )
                SectionNote(stringResource(if (ordered) Res.string.doh_order_note else Res.string.doh_details_note))
                if (state.problem == DohConfigProblem.NO_SERVER) {
                    InlineBanner(
                        text = stringResource(Res.string.doh_no_server),
                        icon = PlazaIcons.ErrorCircle,
                        announce = true,
                    )
                }

                // Both rows are about what the *resolver* can be asked, and on a platform where the
                // system owns the resolver there is nobody to ask — so the row is absent rather than
                // disabled. See [io.github.nodyssey.data.dns.DohCapabilities].
                val canChooseRecordTypes = state.capabilities.canChooseRecordTypes
                val canFallBack = state.capabilities.canFallBackToSystem
                if (canChooseRecordTypes || canFallBack) {
                    SettingsGroup {
                        if (canChooseRecordTypes) {
                            GroupedRow(
                                title = stringResource(Res.string.doh_ipv6_title),
                                subtitle = stringResource(Res.string.doh_ipv6_hint),
                                first = true,
                                last = !canFallBack,
                                enabled = state.enabled,
                                checked = state.includeIPv6,
                                onCheckedChange = onIncludeIPv6Change,
                                trailing = { GroupedListItemSwitch(checked = state.includeIPv6, enabled = state.enabled) },
                            )
                        }
                        if (canFallBack) {
                            GroupedRow(
                                title = stringResource(Res.string.doh_fallback_title),
                                subtitle = stringResource(Res.string.doh_fallback_hint),
                                first = !canChooseRecordTypes,
                                last = true,
                                enabled = state.enabled,
                                checked = state.fallbackToSystem,
                                onCheckedChange = onFallbackChange,
                                trailing = {
                                    GroupedListItemSwitch(checked = state.fallbackToSystem, enabled = state.enabled)
                                },
                            )
                        }
                    }
                }

                // The answer itself — the addresses, so the reader can tell a real one from what
                // their network said.
                state.resolution?.let { resolution ->
                    InlineBanner(
                        text = stringResource(
                            Res.string.doh_test_result,
                            resolution.host,
                            resolution.addresses.joinToString("、"),
                            resolution.elapsedMillis.toString(),
                        ),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        icon = Icons.Default.CheckCircle,
                        announce = true,
                    )
                }
                state.testFailure?.let { failure ->
                    InlineBanner(
                        text = stringResource(Res.string.doh_test_failure, failure),
                        icon = PlazaIcons.ErrorCircle,
                        announce = true,
                    )
                }
                SettingsTestSaveButtons(
                    testLabel = stringResource(Res.string.doh_test),
                    saveLabel = stringResource(Res.string.doh_save),
                    testing = state.testing,
                    enabled = state.enabled,
                    onTest = onTest,
                    onSave = onSave,
                )
            }

            SectionNotes(
                title = stringResource(Res.string.doh_limits_title),
                lines =
                listOfNotNull(
                    stringResource(Res.string.doh_limits_hint),
                    // Where there is no fallback switch, there is no fallback — the platform blocks
                    // cleartext resolution outright while this is on, and defers to an encrypted
                    // resolver the system already has. Someone about to turn it on should know both.
                    stringResource(Res.string.doh_limits_encrypted_only_hint).takeUnless { state.capabilities.canFallBackToSystem },
                    stringResource(Res.string.doh_proxy_hint),
                    stringResource(Res.string.doh_webview_hint),
                ),
            )
        }
    }
}

/**
 * The servers, and the row that adds one, in one card.
 *
 * The drag is the toolbar editor's (`ToolbarCustomizeSheet`): Compose has no reorderable list, so a
 * handle's `detectDragGestures` moves the row by an offset and swaps it one slot per row of travel.
 * The rows are one height — two fixed lines each — which is what makes a row of travel one division.
 * Only the ticked block can be rearranged: the unticked rows are not asked, so they have no order to
 * set, and [withMoved] holds a dragged row inside the block.
 *
 * The same swaps are offered to TalkBack as 上移 / 下移 on each ticked row, since a drag is the one
 * gesture a screen reader cannot make.
 */
@Composable
private fun DohServerList(
    servers: List<DohServer>,
    ordered: Boolean,
    enabled: Boolean,
    onToggle: (String) -> Unit,
    onMove: (id: String, toIndex: Int) -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val move by rememberUpdatedState(onMove)
    val latest by rememberUpdatedState(servers)
    /*
     * The order the gesture works on. A drag outruns composition — several move events can land in
     * one frame, and each has to see the swap the one before it made — so while a finger is down the
     * rows draw from this snapshot, taken when the drag starts, and from [servers] again once it
     * lifts. Every swap is also handed to [onMove] as it happens, so the two agree by then. The same
     * reasoning as `ToolbarCustomizeSheet`'s, which states it at length.
     */
    val order = remember { mutableStateListOf<DohServer>() }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableIntStateOf(0) }
    val rows = if (draggedId != null) order else servers
    val tickedCount = rows.count(DohServer::checked)
    val draggable = ordered && enabled && tickedCount > 1

    fun release() {
        draggedId = null
        dragOffset = 0f
    }

    SettingsGroup {
        rows.forEachIndexed { index, server ->
            key(server.id) {
                val dragging = server.id == draggedId
                val rank = rows.take(index).count(DohServer::checked).takeIf { ordered && server.checked }
                DohServerRow(
                    server = server,
                    first = index == 0,
                    ordered = ordered,
                    rank = rank,
                    enabled = enabled,
                    dragging = dragging,
                    onToggle = { onToggle(server.id) },
                    onOpen = { onOpen(server.id) },
                    onMoveUp = rank?.takeIf { draggable && it > 0 }?.let { { move(server.id, index - 1) } },
                    onMoveDown = rank?.takeIf { draggable && it < tickedCount - 1 }?.let { { move(server.id, index + 1) } },
                    modifier = Modifier
                        .onSizeChanged { if (!dragging) rowHeightPx = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .offset { IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) },
                    handle =
                    if (draggable && server.checked) {
                        {
                            DragHandle(
                                modifier = Modifier.pointerInput(server.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            order.clear()
                                            order.addAll(latest)
                                            draggedId = server.id
                                            dragOffset = 0f
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragEnd = ::release,
                                        onDragCancel = ::release,
                                    ) { change, amount ->
                                        change.consume()
                                        val id = draggedId ?: return@detectDragGestures
                                        if (rowHeightPx == 0) return@detectDragGestures
                                        dragOffset += amount.y
                                        // One row of travel is one swap, and the offset gives that
                                        // row back so the dragged row stays under the finger.
                                        val steps = (dragOffset / rowHeightPx).roundToInt()
                                        if (steps == 0) return@detectDragGestures
                                        val from = order.indexOfFirst { it.id == id }
                                        val to = (from + steps).coerceIn(0, order.count(DohServer::checked) - 1)
                                        if (to == from) return@detectDragGestures
                                        dragOffset -= (to - from) * rowHeightPx
                                        order.add(to, order.removeAt(from))
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        move(id, to)
                                    }
                                },
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
        GroupedRow(
            title = stringResource(Res.string.doh_add_custom),
            icon = Icons.Default.Add,
            last = true,
            enabled = enabled,
            contentColor = MaterialTheme.colorScheme.primary,
            onClick = onAdd,
            showChevron = false,
        )
    }
}

/**
 * One server: its box (or radio button), its name with its place in the order, its address, and —
 * while it can be dragged — the handle.
 *
 * Tapping the row opens its details; the box is its own target, so ticking a server and opening it
 * are two different taps rather than one that does both.
 */
@Composable
private fun DohServerRow(
    server: DohServer,
    first: Boolean,
    ordered: Boolean,
    /** Where among the ticked servers this one is asked, or null where there is no order to show. */
    rank: Int?,
    enabled: Boolean,
    dragging: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    handle: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val label = dohServerLabel(server)
    val moveUpLabel = stringResource(Res.string.doh_move_up)
    val moveDownLabel = stringResource(Res.string.doh_move_down)
    val layers = LocalPlazaLayers.current
    // The rows are see-through — the card behind them is drawn once, by the group — so a row lifted
    // off its slot brings a piece of card with it, and a shadow where the screen can draw one.
    Surface(
        modifier = modifier,
        color = if (dragging) layers.card else Color.Transparent,
        shape = if (dragging) MaterialTheme.shapes.medium else RectangleShape,
        shadowElevation = if (dragging && layers.shadows) DRAG_ELEVATION else 0.dp,
    ) {
        GroupedListItem(
            first = first,
            last = false,
            // On the row itself, which is the node TalkBack lands on; the Surface around it is not.
            modifier = Modifier.semantics {
                customActions = listOfNotNull(
                    onMoveUp?.let {
                        CustomAccessibilityAction(moveUpLabel) {
                            it()
                            true
                        }
                    },
                    onMoveDown?.let {
                        CustomAccessibilityAction(moveDownLabel) {
                            it()
                            true
                        }
                    },
                )
            },
            onClick = onOpen,
            enabled = enabled,
            leadingContent = {
                val toggle = Modifier.semantics { contentDescription = label }
                if (ordered) {
                    Checkbox(checked = server.checked, onCheckedChange = { onToggle() }, enabled = enabled, modifier = toggle)
                } else {
                    RadioButton(selected = server.checked, onClick = onToggle, enabled = enabled, modifier = toggle)
                }
            },
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = label,
                        style = groupedRowTitleStyle(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    rank?.let {
                        TonalTag(
                            text =
                            if (it == 0) {
                                stringResource(Res.string.doh_rank_first)
                            } else {
                                stringResource(Res.string.doh_rank_backup, it)
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            },
            supportingContent = {
                Text(
                    // The scheme is always https — the validator refuses anything else — so it is
                    // left off, and the part worth reading gets the width.
                    text = server.url.removePrefix(HTTPS_PREFIX),
                    style = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = handle,
        )
    }
}

/** The grab target: the handle icon, in a box a thumb can find. */
@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(width = 40.dp, height = 48.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = PlazaIcons.DragHandle,
            contentDescription = stringResource(Res.string.doh_drag_handle),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * One server's details: the address — typed, for one the user added; shown, for a preset — and the
 * bootstrap addresses, which a preset lets be overridden and put back ([onResetBootstrap]).
 *
 * @param server the row being edited, or null while adding one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DohServerSheet(
    draft: DohServerDraft,
    server: DohServer?,
    onUrlChange: (String) -> Unit,
    onBootstrapChange: (String) -> Unit,
    onResetBootstrap: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val preset = draft.preset
    PlazaSheet(
        onDismiss = onDismiss,
        // Straight to full height: two text fields and a row of buttons, and a half-open state would
        // put the buttons behind the keyboard.
        sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        title = server?.let { dohServerLabel(it) } ?: stringResource(Res.string.doh_server_add_title),
        subtitle =
        when {
            preset != null -> preset.url
            server == null -> stringResource(Res.string.doh_provider_custom_hint)
            else -> null
        },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.xl)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (preset == null) {
                SettingsTextField(
                    value = draft.urlInput,
                    onValueChange = onUrlChange,
                    label = stringResource(Res.string.doh_url_label),
                    placeholder = stringResource(Res.string.doh_url_placeholder),
                    isError = draft.problem == DohServerProblem.MISSING_URL ||
                        draft.problem == DohServerProblem.INVALID_URL,
                    supportingText = when (draft.problem) {
                        DohServerProblem.MISSING_URL -> stringResource(Res.string.doh_url_required)
                        DohServerProblem.INVALID_URL -> stringResource(Res.string.doh_url_invalid)
                        else -> null
                    },
                    keyboardType = KeyboardType.Uri,
                )
            }
            SettingsTextField(
                value = draft.bootstrapInput,
                onValueChange = onBootstrapChange,
                label = stringResource(Res.string.doh_bootstrap_label),
                placeholder = stringResource(Res.string.doh_bootstrap_placeholder),
                isError = draft.problem == DohServerProblem.INVALID_BOOTSTRAP,
                supportingText =
                if (draft.problem == DohServerProblem.INVALID_BOOTSTRAP) {
                    stringResource(Res.string.doh_bootstrap_invalid)
                } else {
                    null
                },
                keyboardType = KeyboardType.Uri,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    preset != null ->
                        TextButton(onClick = onResetBootstrap) { Text(stringResource(Res.string.doh_bootstrap_reset)) }

                    server != null ->
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(Res.string.action_delete)) }
                }
                Spacer(Modifier.weight(1f))
                MediumButton(onClick = onConfirm) { Text(stringResource(Res.string.action_done)) }
            }
        }
    }
}

/** Shared with 网络自检, which names the same servers on a row of its own. */
@Composable
internal fun dohServerLabel(server: DohServer): String =
    server.preset?.let { dohProviderLabel(it) }
        // A server the user added is named by its host: it is what they typed, and the one part of
        // the address that tells two of them apart.
        ?: server.url.removePrefix(HTTPS_PREFIX).substringBefore('/').ifEmpty { server.url }

@Composable
private fun dohProviderLabel(provider: DohProvider): String =
    stringResource(
        when (provider) {
            DohProvider.ALIDNS -> Res.string.doh_provider_alidns
            DohProvider.DNSPOD -> Res.string.doh_provider_dnspod
            DohProvider.CLOUDFLARE -> Res.string.doh_provider_cloudflare
            DohProvider.GOOGLE -> Res.string.doh_provider_google
        },
    )

private const val HTTPS_PREFIX = "https://"

private val DRAG_ELEVATION = 6.dp

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun DohSettingsPreview() {
    PlazaTheme {
        DohSettingsScreen(
            state = DohSettingsUiState(
                enabled = true,
                resolution = DnsResolution(
                    host = "www.nodeseek.com",
                    addresses = listOf("104.21.32.1", "172.67.140.1"),
                    elapsedMillis = 86,
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onEnabledChange = {},
            onToggleServer = {},
            onMoveServer = { _, _ -> },
            onOpenServer = {},
            onAddServer = {},
            onIncludeIPv6Change = {},
            onFallbackChange = {},
            onSave = {},
            onTest = {},
        )
    }
}
