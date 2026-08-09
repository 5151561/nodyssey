package io.github.bbs1.ui.instances

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.bbs1.R
import io.github.bbs1.data.normalizeInstanceUrl
import io.github.bbs1.model.ForumInstance
import io.github.bbs1.ui.common.apiErrorText
import io.github.plaza.designsys.component.APPEND_SPINNER_SIZE
import io.github.plaza.designsys.component.StatusAction
import io.github.plaza.designsys.component.StatusView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstancesScreen(
    state: InstancesUiState,
    addState: AddInstanceUiState,
    canNavigateBack: Boolean,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onAddSubmit: (baseUrl: String, name: String?) -> Unit,
    /** The add attempt saved; navigate to the new current site. */
    onAdded: () -> Unit,
    /** Acknowledge a finished attempt so the state machine returns to idle. */
    onAddConsumed: () -> Unit,
    onRemove: (String) -> Unit,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    // At the screen, not inside the dialog: success must navigate even if the dialog was dismissed
    // while the probe was still in flight.
    LaunchedEffect(addState.succeeded) {
        if (addState.succeeded) {
            showAddDialog = false
            onAddConsumed()
            onAdded()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bbs1_instances_title)) },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.bbs1_action_back),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // The empty state carries its own 添加站点 action, so the FAB would be a second copy of
            // the only thing on screen.
            if (state.instances.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.bbs1_add_instance)) },
                )
            }
        },
    ) { padding ->
        if (state.instances.isEmpty() && !state.loading) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                StatusView(
                    icon = Icons.Default.Info,
                    shape = MaterialTheme.shapes.extraLarge,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    title = stringResource(R.string.bbs1_instances_empty_title),
                    description = stringResource(R.string.bbs1_instances_empty_body),
                    primaryAction =
                    StatusAction(stringResource(R.string.bbs1_add_instance)) { showAddDialog = true },
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(state.instances, key = { it.id }) { instance ->
                    InstanceRow(
                        instance = instance,
                        isCurrent = instance.id == state.currentId,
                        onSelect = { onSelect(instance.id) },
                        onRemove = { onRemove(instance.id) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddInstanceDialog(
            addState = addState,
            onConfirm = onAddSubmit,
            onEdit = { if (addState.error != null) onAddConsumed() },
            onDismiss = {
                showAddDialog = false
                onAddConsumed()
            },
        )
    }
}

@Composable
private fun InstanceRow(
    instance: ForumInstance,
    isCurrent: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    // Null rather than an empty lambda when the row is not current, so only the current row
    // reserves the leading slot's width.
    val currentMark: (@Composable () -> Unit)? =
        if (isCurrent) {
            {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.bbs1_current_instance),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        }
    ListItem(
        onClick = onSelect,
        supportingContent = { Text(instance.baseUrl) },
        leadingContent = currentMark,
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.bbs1_action_delete_instance),
                )
            }
        },
    ) {
        Text(instance.name)
    }
}

@Composable
private fun AddInstanceDialog(
    addState: AddInstanceUiState,
    onConfirm: (baseUrl: String, name: String?) -> Unit,
    /** The user is typing again; a shown probe failure no longer describes the field's content. */
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var urlInput by rememberSaveable { mutableStateOf("") }
    var nameInput by rememberSaveable { mutableStateOf("") }

    // Validation is the pure function the tests cover; the dialog just asks it live. The probe
    // result speaks about the same field, so both errors share the URL field's supporting text.
    val normalized = normalizeInstanceUrl(urlInput)
    val formatError = urlInput.isNotBlank() && normalized == null
    val probeError = addState.error

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bbs1_add_instance)) },
        text = {
            Column {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        onEdit()
                    },
                    label = { Text(stringResource(R.string.bbs1_add_dialog_url_label)) },
                    isError = formatError || probeError != null,
                    supportingText = {
                        when {
                            formatError -> Text(stringResource(R.string.bbs1_add_dialog_url_invalid))
                            probeError != null -> Text(apiErrorText(probeError))
                        }
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(stringResource(R.string.bbs1_add_dialog_name_label)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = normalized != null && !addState.probing,
                onClick = { normalized?.let { onConfirm(it, nameInput.takeIf(String::isNotBlank)) } },
            ) {
                if (addState.probing) {
                    CircularProgressIndicator(Modifier.size(APPEND_SPINNER_SIZE))
                } else {
                    Text(stringResource(R.string.bbs1_action_confirm_add))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.bbs1_action_cancel))
            }
        },
    )
}
