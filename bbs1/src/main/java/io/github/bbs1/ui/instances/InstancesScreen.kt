package io.github.bbs1.ui.instances

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.bbs1.R
import io.github.bbs1.data.normalizeInstanceUrl
import io.github.bbs1.model.ForumInstance
import io.github.plaza.designsys.component.StatusAction
import io.github.plaza.designsys.component.StatusView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstancesScreen(
    state: InstancesUiState,
    canNavigateBack: Boolean,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onAdd: (baseUrl: String, name: String?) -> Unit,
    onRemove: (String) -> Unit,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

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
            onConfirm = { baseUrl, name ->
                showAddDialog = false
                onAdd(baseUrl, name)
            },
            onDismiss = { showAddDialog = false },
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
    onConfirm: (baseUrl: String, name: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var urlInput by rememberSaveable { mutableStateOf("") }
    var nameInput by rememberSaveable { mutableStateOf("") }

    // Validation is the pure function the tests cover; the dialog just asks it live.
    val normalized = normalizeInstanceUrl(urlInput)
    val showError = urlInput.isNotBlank() && normalized == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bbs1_add_instance)) },
        text = {
            Column {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text(stringResource(R.string.bbs1_add_dialog_url_label)) },
                    isError = showError,
                    supportingText = {
                        if (showError) Text(stringResource(R.string.bbs1_add_dialog_url_invalid))
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
                enabled = normalized != null,
                onClick = { normalized?.let { onConfirm(it, nameInput.takeIf(String::isNotBlank)) } },
            ) {
                Text(stringResource(R.string.bbs1_action_confirm_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.bbs1_action_cancel))
            }
        },
    )
}
