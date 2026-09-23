package io.github.nodyssey.ui.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.nodyssey.core.NodeImageSite
import io.github.nodyssey.data.imagehost.ConfigProblem
import io.github.nodyssey.data.imagehost.CustomHostFields
import io.github.nodyssey.data.imagehost.HostedImage
import io.github.nodyssey.data.imagehost.ImageHostError
import io.github.nodyssey.data.imagehost.ImageHostProvider
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_refresh
import io.github.nodyssey.ui.resources.imagehost_api_token_label
import io.github.nodyssey.ui.resources.imagehost_clear_custom_body
import io.github.nodyssey.ui.resources.imagehost_clear_key
import io.github.nodyssey.ui.resources.imagehost_clear_key_action
import io.github.nodyssey.ui.resources.imagehost_clear_key_body
import io.github.nodyssey.ui.resources.imagehost_clear_key_title
import io.github.nodyssey.ui.resources.imagehost_connected
import io.github.nodyssey.ui.resources.imagehost_custom_fields
import io.github.nodyssey.ui.resources.imagehost_custom_file_field
import io.github.nodyssey.ui.resources.imagehost_custom_file_field_helper
import io.github.nodyssey.ui.resources.imagehost_custom_file_field_placeholder
import io.github.nodyssey.ui.resources.imagehost_custom_file_field_required
import io.github.nodyssey.ui.resources.imagehost_custom_form_fields
import io.github.nodyssey.ui.resources.imagehost_custom_form_fields_helper
import io.github.nodyssey.ui.resources.imagehost_custom_form_fields_placeholder
import io.github.nodyssey.ui.resources.imagehost_custom_header_helper
import io.github.nodyssey.ui.resources.imagehost_custom_header_name
import io.github.nodyssey.ui.resources.imagehost_custom_header_name_placeholder
import io.github.nodyssey.ui.resources.imagehost_custom_header_value
import io.github.nodyssey.ui.resources.imagehost_custom_header_value_helper
import io.github.nodyssey.ui.resources.imagehost_custom_header_value_placeholder
import io.github.nodyssey.ui.resources.imagehost_custom_url_path
import io.github.nodyssey.ui.resources.imagehost_custom_url_path_helper
import io.github.nodyssey.ui.resources.imagehost_custom_url_path_placeholder
import io.github.nodyssey.ui.resources.imagehost_custom_url_path_required
import io.github.nodyssey.ui.resources.imagehost_custom_url_prefix
import io.github.nodyssey.ui.resources.imagehost_custom_url_prefix_helper
import io.github.nodyssey.ui.resources.imagehost_custom_url_prefix_placeholder
import io.github.nodyssey.ui.resources.imagehost_delete_action
import io.github.nodyssey.ui.resources.imagehost_delete_body
import io.github.nodyssey.ui.resources.imagehost_delete_title
import io.github.nodyssey.ui.resources.imagehost_empty
import io.github.nodyssey.ui.resources.imagehost_error_cloudflare
import io.github.nodyssey.ui.resources.imagehost_error_http
import io.github.nodyssey.ui.resources.imagehost_error_invalid_key
import io.github.nodyssey.ui.resources.imagehost_error_not_configured
import io.github.nodyssey.ui.resources.imagehost_error_rejected
import io.github.nodyssey.ui.resources.imagehost_error_session_required
import io.github.nodyssey.ui.resources.imagehost_error_unparsable
import io.github.nodyssey.ui.resources.imagehost_error_unsupported
import io.github.nodyssey.ui.resources.imagehost_hint_custom
import io.github.nodyssey.ui.resources.imagehost_hint_easyimage
import io.github.nodyssey.ui.resources.imagehost_hint_imgbb
import io.github.nodyssey.ui.resources.imagehost_hint_lsky
import io.github.nodyssey.ui.resources.imagehost_hint_nodeimage
import io.github.nodyssey.ui.resources.imagehost_hint_smms
import io.github.nodyssey.ui.resources.imagehost_key_invalid
import io.github.nodyssey.ui.resources.imagehost_key_label
import io.github.nodyssey.ui.resources.imagehost_key_replace
import io.github.nodyssey.ui.resources.imagehost_key_save
import io.github.nodyssey.ui.resources.imagehost_not_connected
import io.github.nodyssey.ui.resources.imagehost_not_connected_hint
import io.github.nodyssey.ui.resources.imagehost_open_site
import io.github.nodyssey.ui.resources.imagehost_provider_custom
import io.github.nodyssey.ui.resources.imagehost_provider_easyimage
import io.github.nodyssey.ui.resources.imagehost_provider_imgbb
import io.github.nodyssey.ui.resources.imagehost_provider_lsky
import io.github.nodyssey.ui.resources.imagehost_provider_nodeimage
import io.github.nodyssey.ui.resources.imagehost_provider_smms
import io.github.nodyssey.ui.resources.imagehost_section_connection
import io.github.nodyssey.ui.resources.imagehost_section_images
import io.github.nodyssey.ui.resources.imagehost_section_provider
import io.github.nodyssey.ui.resources.imagehost_site_helper
import io.github.nodyssey.ui.resources.imagehost_site_invalid
import io.github.nodyssey.ui.resources.imagehost_site_label
import io.github.nodyssey.ui.resources.imagehost_site_placeholder
import io.github.nodyssey.ui.resources.imagehost_summary
import io.github.nodyssey.ui.resources.imagehost_title
import io.github.nodyssey.ui.resources.imagehost_token_helper
import io.github.nodyssey.ui.resources.imagehost_token_label
import io.github.nodyssey.ui.resources.imagehost_token_placeholder
import io.github.nodyssey.ui.resources.imagehost_token_placeholder_saved
import io.github.nodyssey.ui.resources.imagehost_token_required
import io.github.nodyssey.ui.resources.imagehost_upload_url_label
import io.github.nodyssey.ui.resources.imagehost_upload_url_placeholder
import io.github.nodyssey.ui.resources.status_network_title
import io.github.nodyssey.ui.settings.SettingsItemGap
import io.github.nodyssey.ui.settings.SettingsPagePadding
import io.github.nodyssey.ui.settings.SettingsSectionTitle
import io.github.nodyssey.ui.settings.settingsRowTitleStyle
import io.github.plaza.designsys.component.ImageFallback
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerCardShape
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaFieldDefaults
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToLong

@Composable
fun ImageHostRoute(
    viewModel: ImageHostViewModel,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // No recovery wired: every refusal this screen can raise comes back as an
    // [AccountMessage.Info] — a host's own wording for a bad key or an unreachable API — so there is
    // never a [SiteError] to derive a button from. An image host is a different site with a
    // different session, and NodeSeek's web view cannot clear a wall standing in front of it.
    AccountMessageSnackbar(
        message = state.message,
        snackbarHostState = snackbarHostState,
        onShown = viewModel::consumeMessage,
    )

    ImageHostScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onSelectProvider = viewModel::selectProvider,
        onSiteUrlChange = viewModel::updateSiteUrl,
        onTokenChange = viewModel::updateToken,
        onCustomChange = viewModel::updateCustom,
        onToggleCustomFields = viewModel::toggleCustomFields,
        onSave = viewModel::save,
        onRequestDisconnect = viewModel::requestDisconnect,
        onDismissDisconnect = viewModel::dismissDisconnect,
        onConfirmDisconnect = viewModel::confirmDisconnect,
        onRefresh = { viewModel.refresh() },
        onRequestDelete = viewModel::requestDelete,
        onDismissDelete = viewModel::dismissDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onOpenSite = { onOpenUrl(state.provider.siteUrlFor(state.siteUrlInput)) },
        onOpenImage = onOpenUrl,
        modifier = modifier,
    )
}

/**
 * 图床 — where the pictures in a post actually live.
 *
 * NodeSeek stores Markdown and nothing else, so every inline image is a link to a service the forum
 * does not run, and which service that is belongs to the user. The screen reads top to bottom as the
 * three decisions that involves: which host, what it needs to let you in, and what it is holding.
 *
 * The middle section changes shape with the choice above it, because the six hosts genuinely do not
 * want the same things — two are at a fixed address and want only a key, two are somebody's own
 * server and want an address as well, and the last is described field by field. Showing every field
 * for every host would mean five of them are always wrong.
 *
 * The credential field is emptied the moment it is saved and never refilled — the row above it shows
 * a masked fingerprint instead. A settings screen is the most-screenshotted surface in any app, and
 * this is the one string on it that would let somebody else upload under this account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageHostScreen(
    state: ImageHostUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSelectProvider: (ImageHostProvider) -> Unit,
    onSiteUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onCustomChange: ((CustomHostFields) -> CustomHostFields) -> Unit,
    onToggleCustomFields: () -> Unit,
    onSave: () -> Unit,
    onRequestDisconnect: () -> Unit,
    onDismissDisconnect: () -> Unit,
    onConfirmDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onRequestDelete: (HostedImage) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onOpenSite: () -> Unit,
    onOpenImage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appBarState = rememberOneHandAppBarState()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.imagehost_title),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state.connected && state.provider.browsable) {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(Res.string.action_refresh),
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
                .padding(SettingsPagePadding),
            verticalArrangement = Arrangement.spacedBy(SettingsItemGap),
        ) {
            ProviderCard(selected = state.provider, onSelect = onSelectProvider)

            ConnectionCard(
                state = state,
                onSave = onSave,
                onDisconnect = onRequestDisconnect,
                onOpenSite = onOpenSite,
            ) {
                CredentialFields(
                    state = state,
                    onSiteUrlChange = onSiteUrlChange,
                    onTokenChange = onTokenChange,
                    onCustomChange = onCustomChange,
                    onToggleCustomFields = onToggleCustomFields,
                )
            }

            SettingsSectionTitle(
                text = stringResource(Res.string.imagehost_section_images) +
                    state.images.size.takeIf { it > 0 && !state.isLoadingImages }?.let { " · $it" }.orEmpty(),
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

    if (state.confirmingDisconnect) {
        HighRiskDialog(
            icon = PlazaIcons.Shield,
            title = stringResource(Res.string.imagehost_clear_key_title, stringResource(state.provider.nameRes())),
            // A custom host loses its address and its field names along with the credential, because
            // for that one the configuration *is* the connection — see [ImageHostSettings.disconnect].
            // The dialog says which of the two is about to happen; the promise underneath it ("no more
            // pictures in posts") is the same either way.
            body = if (state.provider == ImageHostProvider.CUSTOM) {
                stringResource(Res.string.imagehost_clear_custom_body)
            } else {
                stringResource(Res.string.imagehost_clear_key_body)
            },
            confirmLabel = stringResource(Res.string.imagehost_clear_key_action),
            onConfirm = onConfirmDisconnect,
            onDismiss = onDismissDisconnect,
            destructive = true,
        )
    }

    state.deleting?.let { target ->
        HighRiskDialog(
            icon = Icons.Default.Delete,
            title = stringResource(Res.string.imagehost_delete_title),
            // Named in the dialog because thumbnails of the same screenshot are indistinguishable,
            // and this delete cannot be undone from anywhere in the app.
            body = stringResource(Res.string.imagehost_delete_body, target.fileName),
            confirmLabel = stringResource(Res.string.imagehost_delete_action),
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete,
            destructive = true,
        )
    }
}

/**
 * The six, as chips on a card of their own (6h).
 *
 * All six stay on screen rather than behind a dropdown: at chip size they take three short rows, and
 * seeing the alternatives side by side is the one thing that helps make the choice. The line under
 * them is the selected host's own instructions, which is where the answer to "so what do I paste
 * here" lives.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderCard(
    selected: ImageHostProvider,
    onSelect: (ImageHostProvider) -> Unit,
) {
    LayerCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.imagehost_section_provider), style = settingsRowTitleStyle())
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ImageHostProvider.entries.forEach { provider ->
                val isSelected = provider == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(provider) },
                    label = { Text(stringResource(provider.nameRes())) },
                    leadingIcon =
                    if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = LocalPlazaLayers.current.card,
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    modifier = Modifier.height(36.dp),
                )
            }
        }
        Text(
            text = stringResource(selected.hintRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 连接 — whether the host is connected, what it holds for this account, the fields it needs, and
 * the two actions, on one card: they are one connection, and the status next to the fields is what
 * says whether typing into them will replace something.
 */
@Composable
private fun ConnectionCard(
    state: ImageHostUiState,
    onSave: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenSite: () -> Unit,
    fields: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    LayerCard(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.imagehost_section_connection),
                style = settingsRowTitleStyle(),
                modifier = Modifier.weight(1f),
            )
            Surface(
                color = if (state.connected) scheme.tertiaryContainer else scheme.surfaceContainerHigh,
                contentColor = if (state.connected) scheme.onTertiaryContainer else scheme.onSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (state.connected) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        stringResource(
                            if (state.connected) Res.string.imagehost_connected else Res.string.imagehost_not_connected,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                // A custom host may legitimately hold no secret at all, so a connected host with
                // no fingerprint says where it points instead of showing an empty line.
                text = state.credentialMask
                    ?: state.siteUrlInput.takeIf { state.connected && it.isNotBlank() }
                    ?: stringResource(Res.string.imagehost_not_connected_hint),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = if (state.connected) FontFamily.Monospace else FontFamily.Default,
                ),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            StorageBadge(local = true)
        }

        fields()

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (state.connected) {
                FilledTonalButton(
                    onClick = onDisconnect,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text(stringResource(Res.string.imagehost_clear_key), style = MaterialTheme.typography.titleMedium)
                }
            }
            Button(onClick = onSave, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Text(
                    stringResource(
                        if (state.connected) Res.string.imagehost_key_replace else Res.string.imagehost_key_save,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        // For the two hosted services this opens their own site; for a self-hosted one it opens
        // whatever address was typed, which is also the quickest check that it is right.
        if (state.provider.siteUrlFor(state.siteUrlInput).isNotBlank()) {
            TextButton(onClick = onOpenSite, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(Res.string.imagehost_open_site))
            }
        }
    }
}

/** Whichever of the fields the selected host actually reads, and none of the ones it does not. */
@Composable
private fun CredentialFields(
    state: ImageHostUiState,
    onSiteUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onCustomChange: ((CustomHostFields) -> CustomHostFields) -> Unit,
    onToggleCustomFields: () -> Unit,
) {
    val provider = state.provider
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (provider.needsSiteUrl) {
            HostField(
                value = state.siteUrlInput,
                onValueChange = onSiteUrlChange,
                labelRes = if (provider == ImageHostProvider.CUSTOM) {
                    Res.string.imagehost_upload_url_label
                } else {
                    Res.string.imagehost_site_label
                },
                placeholderRes = if (provider == ImageHostProvider.CUSTOM) {
                    Res.string.imagehost_upload_url_placeholder
                } else {
                    Res.string.imagehost_site_placeholder
                },
                helperRes = Res.string.imagehost_site_helper,
                errorRes = Res.string.imagehost_site_invalid,
                isError = state.problem == ConfigProblem.BAD_SITE_URL,
                keyboardType = KeyboardType.Uri,
            )
        }

        // The custom host has no token of its own — its credential is whichever header or form field
        // its API reads, both of which are below. A field labelled "API Key" that nothing sends
        // would be worse than no field at all.
        if (provider != ImageHostProvider.CUSTOM) {
            HostField(
                value = state.tokenInput,
                onValueChange = onTokenChange,
                labelRes = provider.tokenLabelRes(),
                placeholderRes = if (state.connected) {
                    Res.string.imagehost_token_placeholder_saved
                } else {
                    Res.string.imagehost_token_placeholder
                },
                helperRes = Res.string.imagehost_token_helper,
                errorRes = if (state.problem == ConfigProblem.IMPLAUSIBLE_TOKEN) {
                    Res.string.imagehost_key_invalid
                } else {
                    Res.string.imagehost_token_required
                },
                isError = state.problem == ConfigProblem.MISSING_TOKEN ||
                    state.problem == ConfigProblem.IMPLAUSIBLE_TOKEN,
                // Not a password field: the value is pasted, never typed, and dots would hide a
                // mis-paste until the first upload fails with an unexplained 401.
                keyboardType = KeyboardType.Ascii,
            )
        }

        if (provider == ImageHostProvider.CUSTOM) {
            TextButton(onClick = onToggleCustomFields) {
                Text(stringResource(Res.string.imagehost_custom_fields))
                Icon(
                    imageVector = if (state.customFieldsExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = Spacing.xs)
                        .size(18.dp),
                )
            }
            AnimatedVisibility(visible = state.customFieldsExpanded) {
                CustomFields(state = state, onCustomChange = onCustomChange)
            }
        }
    }
}

/**
 * The six knobs a host the app has never heard of needs described.
 *
 * These are shown in the clear, including the header value, which is the one place this screen
 * departs from "never redisplay a credential". It is a deliberate trade: with a host nothing here
 * can introspect, a user debugging a 401 has to be able to see what is actually being sent, and the
 * masked-fingerprint treatment that works for a known host would make that impossible.
 */
@Composable
private fun CustomFields(
    state: ImageHostUiState,
    onCustomChange: ((CustomHostFields) -> CustomHostFields) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        HostField(
            value = state.custom.fileField,
            onValueChange = { value -> onCustomChange { it.copy(fileField = value) } },
            labelRes = Res.string.imagehost_custom_file_field,
            placeholderRes = Res.string.imagehost_custom_file_field_placeholder,
            helperRes = Res.string.imagehost_custom_file_field_helper,
            errorRes = Res.string.imagehost_custom_file_field_required,
            isError = state.problem == ConfigProblem.MISSING_FILE_FIELD,
        )
        HostField(
            value = state.custom.headerName,
            onValueChange = { value -> onCustomChange { it.copy(headerName = value) } },
            labelRes = Res.string.imagehost_custom_header_name,
            placeholderRes = Res.string.imagehost_custom_header_name_placeholder,
            helperRes = Res.string.imagehost_custom_header_helper,
        )
        HostField(
            value = state.custom.headerValue,
            onValueChange = { value -> onCustomChange { it.copy(headerValue = value) } },
            labelRes = Res.string.imagehost_custom_header_value,
            placeholderRes = Res.string.imagehost_custom_header_value_placeholder,
            helperRes = Res.string.imagehost_custom_header_value_helper,
        )
        HostField(
            value = state.custom.formFields,
            onValueChange = { value -> onCustomChange { it.copy(formFields = value) } },
            labelRes = Res.string.imagehost_custom_form_fields,
            placeholderRes = Res.string.imagehost_custom_form_fields_placeholder,
            helperRes = Res.string.imagehost_custom_form_fields_helper,
            singleLine = false,
        )
        HostField(
            value = state.custom.urlPath,
            onValueChange = { value -> onCustomChange { it.copy(urlPath = value) } },
            labelRes = Res.string.imagehost_custom_url_path,
            placeholderRes = Res.string.imagehost_custom_url_path_placeholder,
            helperRes = Res.string.imagehost_custom_url_path_helper,
            errorRes = Res.string.imagehost_custom_url_path_required,
            isError = state.problem == ConfigProblem.MISSING_URL_PATH,
        )
        HostField(
            value = state.custom.urlPrefix,
            onValueChange = { value -> onCustomChange { it.copy(urlPrefix = value) } },
            labelRes = Res.string.imagehost_custom_url_prefix,
            placeholderRes = Res.string.imagehost_custom_url_prefix_placeholder,
            helperRes = Res.string.imagehost_custom_url_prefix_helper,
            keyboardType = KeyboardType.Uri,
        )
    }
}

@Composable
private fun HostField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: StringResource,
    placeholderRes: StringResource,
    helperRes: StringResource,
    errorRes: StringResource? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        isError = isError,
        label = { Text(stringResource(labelRes)) },
        placeholder = { Text(stringResource(placeholderRes)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = PlazaFieldDefaults.shape,
        colors = PlazaFieldDefaults.colors(inCard = true),
        supportingText = {
            Text(stringResource(if (isError && errorRes != null) errorRes else helperRes))
        },
    )
}

@Composable
private fun ImagesSection(
    state: ImageHostUiState,
    onRequestDelete: (HostedImage) -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenSite: () -> Unit,
) {
    when {
        state.isLoadingImages -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalArrangement = Arrangement.Center,
        ) {
            PlazaSpinner(Modifier.describedAsLoading(), size = 22.dp)
        }

        /*
         * Two of the six publish an upload endpoint and nothing else. Saying so is the point: an
         * empty gallery would read as "the host lost your images", and the recovery for that is
         * nothing like the recovery for "this host has no list, use its own page".
         */
        state.imagesError == ImageHostError.Unsupported -> InfoCard(
            text = stringResource(Res.string.imagehost_error_unsupported),
        )

        /*
         * nodeimage.com's list and delete endpoints turn the API key down — verified on device, with
         * a key that had just succeeded on upload. So this half of the screen cannot work, and it
         * says so and hands over to the website rather than showing an empty gallery.
         */
        state.imagesError == ImageHostError.SessionRequired -> InfoCard(
            text = stringResource(Res.string.imagehost_error_session_required),
            action = stringResource(Res.string.imagehost_open_site) to onOpenSite,
        )

        state.imagesError != null -> InfoCard(stringResource(state.imagesError.messageRes()))

        state.images.isEmpty() -> InfoCard(stringResource(Res.string.imagehost_empty))

        else -> LayerCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(
                    Res.string.imagehost_summary,
                    state.images.size,
                    formatBytes(state.totalBytes),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
            // Rows of three rather than a lazy grid: the page already scrolls, and a second
            // scrolling container inside it either measures to nothing or takes the page's drag.
            state.images.chunked(GALLERY_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    row.forEach { item ->
                        ImageTile(
                            item = item,
                            onDelete = { onRequestDelete(item) },
                            onOpen = { onOpenImage(item.url) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(GALLERY_COLUMNS - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * One picture on the host: the thumbnail, opened by a tap, with its delete on a white disc in the
 * corner. The file name, upload time and size ride in the tile's description rather than under it —
 * three to a row there is no room to print them, and the delete dialog names the file anyway.
 */
@Composable
private fun ImageTile(
    item: HostedImage,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val details =
        listOfNotNull(
            item.fileName,
            item.uploadTime?.takeIf(String::isNotBlank),
            // A host that reports no size at all should not be made to claim it holds 0 B.
            formatBytes(item.sizeBytes).takeIf { item.sizeBytes > 0 },
        ).joinToString(" · ")
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(LocalPlazaLayers.current.inset),
    ) {
        // A host that has lost the file, or one whose links need a referer this app does not send,
        // otherwise shows a tile that is all blank and no picture — and the reader cannot tell that
        // from a tile still loading.
        var failed by remember(item.url) { mutableStateOf(false) }
        val open = Modifier.fillMaxSize().clickable(onClick = onOpen).semantics { contentDescription = details }
        if (failed) {
            ImageFallback(modifier = open)
        } else {
            AsyncImage(
                model = item.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = open,
            )
        }
        IconButton(
            onClick = onDelete,
            colors =
            IconButtonDefaults.iconButtonColors(
                containerColor = LocalPlazaLayers.current.raised,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(36.dp),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(Res.string.imagehost_delete_action),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private const val GALLERY_COLUMNS = 3

@Composable
private fun InfoCard(
    text: String,
    action: Pair<String, () -> Unit>? = null,
) {
    Surface(
        shape = LayerCardShape,
        color = LocalPlazaLayers.current.card,
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
internal fun ImageHostProvider.nameRes(): StringResource = when (this) {
    ImageHostProvider.NODE_IMAGE -> Res.string.imagehost_provider_nodeimage
    ImageHostProvider.LSKY_PRO -> Res.string.imagehost_provider_lsky
    ImageHostProvider.EASY_IMAGE -> Res.string.imagehost_provider_easyimage
    ImageHostProvider.SMMS -> Res.string.imagehost_provider_smms
    ImageHostProvider.IMGBB -> Res.string.imagehost_provider_imgbb
    ImageHostProvider.CUSTOM -> Res.string.imagehost_provider_custom
}
private fun ImageHostProvider.hintRes(): StringResource = when (this) {
    ImageHostProvider.NODE_IMAGE -> Res.string.imagehost_hint_nodeimage
    ImageHostProvider.LSKY_PRO -> Res.string.imagehost_hint_lsky
    ImageHostProvider.EASY_IMAGE -> Res.string.imagehost_hint_easyimage
    ImageHostProvider.SMMS -> Res.string.imagehost_hint_smms
    ImageHostProvider.IMGBB -> Res.string.imagehost_hint_imgbb
    ImageHostProvider.CUSTOM -> Res.string.imagehost_hint_custom
}

/** Every host calls its credential something; the label matches so a pasted value looks right. */
private fun ImageHostProvider.tokenLabelRes(): StringResource = when (this) {
    ImageHostProvider.NODE_IMAGE, ImageHostProvider.IMGBB -> Res.string.imagehost_key_label
    ImageHostProvider.SMMS -> Res.string.imagehost_api_token_label
    else -> Res.string.imagehost_token_label
}

/**
 * Where 打开官网 goes.
 *
 * The three public hosts have a page that hands out a credential; the self-hosted ones have whatever
 * the user typed, and opening that is also the fastest way to find out the address is wrong.
 */
internal fun ImageHostProvider.siteUrlFor(typed: String): String = when (this) {
    ImageHostProvider.NODE_IMAGE -> NodeImageSite.SITE_URL
    ImageHostProvider.SMMS -> "https://sm.ms"
    ImageHostProvider.IMGBB -> "https://imgbb.com"
    else -> typed.trim()
}
internal fun ImageHostError.messageRes(): StringResource = when (this) {
    ImageHostError.NotConfigured -> Res.string.imagehost_error_not_configured
    ImageHostError.InvalidKey -> Res.string.imagehost_error_invalid_key
    ImageHostError.SessionRequired -> Res.string.imagehost_error_session_required
    is ImageHostError.Rejected -> Res.string.imagehost_error_rejected
    ImageHostError.Cloudflare -> Res.string.imagehost_error_cloudflare
    is ImageHostError.Http -> Res.string.imagehost_error_http
    ImageHostError.Unsupported -> Res.string.imagehost_error_unsupported
    ImageHostError.Network -> Res.string.status_network_title
    ImageHostError.Unparsable -> Res.string.imagehost_error_unparsable
}

/** `1536` → `1.5 KB`. Binary units, because that is what these hosts report their own sizes in. */
internal fun formatBytes(bytes: Long): String {
    if (bytes < UNIT) return "$bytes B"
    var value = bytes.toDouble()
    var index = -1
    while (value >= UNIT && index < UNITS.lastIndex) {
        value /= UNIT
        index++
    }
    // One decimal place, written out: `String.format` is a JVM extension. Rounding half up on a
    // positive number is what `%.1f` does too, so the printed figure is unchanged.
    val tenths = (value * 10).roundToLong()
    return "${tenths / 10}.${tenths % 10} ${UNITS[index]}"
}

private const val UNIT = 1024
private val UNITS = listOf("KB", "MB", "GB")

@Preview(showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun ImageHostPreview() {
    PlazaTheme {
        ImageHostScreen(
            state = ImageHostUiState(
                isLoading = false,
                provider = ImageHostProvider.NODE_IMAGE,
                connected = true,
                credentialMask = "cfbe……ac3f",
                images = listOf(
                    HostedImage(
                        id = "Yzk9P567",
                        fileName = "Yzk9P567.webp",
                        url = "https://cdn.nodeimage.com/i/Yzk9P567.webp",
                        uploadTime = "2026/7/28 12:04",
                        sizeBytes = 1214,
                        mimeType = "image/webp",
                    ),
                    HostedImage(
                        id = "vU7478n1",
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
            onSelectProvider = {},
            onSiteUrlChange = {},
            onTokenChange = {},
            onCustomChange = {},
            onToggleCustomFields = {},
            onSave = {},
            onRequestDisconnect = {},
            onDismissDisconnect = {},
            onConfirmDisconnect = {},
            onRefresh = {},
            onRequestDelete = {},
            onDismissDelete = {},
            onConfirmDelete = {},
            onOpenSite = {},
            onOpenImage = {},
        )
    }
}
