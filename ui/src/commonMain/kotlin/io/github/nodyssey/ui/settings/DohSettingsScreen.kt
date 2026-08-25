package io.github.nodyssey.ui.settings

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.dns.DnsResolution
import io.github.nodyssey.data.dns.DohConfigProblem
import io.github.nodyssey.data.dns.DohProvider
import io.github.nodyssey.ui.account.AccountMessageSnackbar
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.doh_bootstrap_invalid
import io.github.nodyssey.ui.resources.doh_bootstrap_label
import io.github.nodyssey.ui.resources.doh_bootstrap_placeholder
import io.github.nodyssey.ui.resources.doh_fallback_hint
import io.github.nodyssey.ui.resources.doh_fallback_title
import io.github.nodyssey.ui.resources.doh_ipv6_hint
import io.github.nodyssey.ui.resources.doh_ipv6_title
import io.github.nodyssey.ui.resources.doh_limits_encrypted_only_hint
import io.github.nodyssey.ui.resources.doh_limits_hint
import io.github.nodyssey.ui.resources.doh_limits_title
import io.github.nodyssey.ui.resources.doh_master_hint
import io.github.nodyssey.ui.resources.doh_master_title
import io.github.nodyssey.ui.resources.doh_provider_alidns
import io.github.nodyssey.ui.resources.doh_provider_cloudflare
import io.github.nodyssey.ui.resources.doh_provider_custom
import io.github.nodyssey.ui.resources.doh_provider_custom_hint
import io.github.nodyssey.ui.resources.doh_provider_dnspod
import io.github.nodyssey.ui.resources.doh_provider_google
import io.github.nodyssey.ui.resources.doh_provider_title
import io.github.nodyssey.ui.resources.doh_proxy_hint
import io.github.nodyssey.ui.resources.doh_save
import io.github.nodyssey.ui.resources.doh_test
import io.github.nodyssey.ui.resources.doh_test_failure
import io.github.nodyssey.ui.resources.doh_test_result
import io.github.nodyssey.ui.resources.doh_title
import io.github.nodyssey.ui.resources.doh_url_invalid
import io.github.nodyssey.ui.resources.doh_url_label
import io.github.nodyssey.ui.resources.doh_url_placeholder
import io.github.nodyssey.ui.resources.doh_url_required
import io.github.nodyssey.ui.resources.doh_webview_hint
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

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
        onProviderChange = viewModel::setProvider,
        onUrlChange = viewModel::updateUrl,
        onBootstrapChange = viewModel::updateBootstrap,
        onIncludeIPv6Change = viewModel::setIncludeIPv6,
        onFallbackChange = viewModel::setFallbackToSystem,
        onSave = viewModel::save,
        onTest = viewModel::test,
        modifier = modifier,
    )
}

/**
 * 加密 DNS — which server turns a hostname into an address for the app's own requests.
 *
 * Everything below the master switch is dimmed and inert while it is off, the same treatment
 * [ProxySettingsScreen] and [NotificationSettingsScreen] give the settings behind theirs.
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
    onProviderChange: (DohProvider) -> Unit,
    onUrlChange: (String) -> Unit,
    onBootstrapChange: (String) -> Unit,
    onIncludeIPv6Change: (Boolean) -> Unit,
    onFallbackChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appBarState = rememberOneHandAppBarState()
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
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SettingsGroup {
                SettingsRow(
                    title = stringResource(Res.string.doh_master_title),
                    subtitle = stringResource(Res.string.doh_master_hint),
                    top = true,
                    bottom = true,
                    checked = state.enabled,
                    onCheckedChange = onEnabledChange,
                    trailing = { Switch(checked = state.enabled, onCheckedChange = null) },
                )
            }

            Column(
                modifier = Modifier.alpha(if (state.enabled) 1f else DISABLED_ALPHA),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SettingsSectionTitle(stringResource(Res.string.doh_provider_title))
                SettingsGroup {
                    DohProvider.entries.forEachIndexed { index, provider ->
                        SettingsRow(
                            title = dohProviderLabel(provider),
                            // The address itself, rather than a description of it: it is the one
                            // thing about a resolver worth checking, and 自定义 is the row where it
                            // is not known yet.
                            subtitle = provider.url.ifEmpty {
                                stringResource(Res.string.doh_provider_custom_hint)
                            },
                            top = index == 0,
                            bottom = index == DohProvider.entries.lastIndex,
                            enabled = state.enabled,
                            selected = provider == state.provider,
                            onClick = { onProviderChange(provider) },
                            trailing = {
                                RadioButton(
                                    selected = provider == state.provider,
                                    onClick = null,
                                    enabled = state.enabled,
                                )
                            },
                        )
                    }
                }

                if (state.provider == DohProvider.CUSTOM) {
                    OutlinedTextField(
                        value = state.urlInput,
                        onValueChange = onUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.enabled,
                        singleLine = true,
                        isError = state.problem == DohConfigProblem.MISSING_URL ||
                            state.problem == DohConfigProblem.INVALID_URL,
                        label = { Text(stringResource(Res.string.doh_url_label)) },
                        placeholder = { Text(stringResource(Res.string.doh_url_placeholder)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        supportingText = when (state.problem) {
                            DohConfigProblem.MISSING_URL -> {
                                { Text(stringResource(Res.string.doh_url_required)) }
                            }

                            DohConfigProblem.INVALID_URL -> {
                                { Text(stringResource(Res.string.doh_url_invalid)) }
                            }

                            else -> null
                        },
                    )
                    OutlinedTextField(
                        value = state.bootstrapInput,
                        onValueChange = onBootstrapChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.enabled,
                        singleLine = true,
                        isError = state.problem == DohConfigProblem.INVALID_BOOTSTRAP,
                        label = { Text(stringResource(Res.string.doh_bootstrap_label)) },
                        placeholder = { Text(stringResource(Res.string.doh_bootstrap_placeholder)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        supportingText = if (state.problem == DohConfigProblem.INVALID_BOOTSTRAP) {
                            { Text(stringResource(Res.string.doh_bootstrap_invalid)) }
                        } else {
                            null
                        },
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
                            SettingsRow(
                                title = stringResource(Res.string.doh_ipv6_title),
                                subtitle = stringResource(Res.string.doh_ipv6_hint),
                                top = true,
                                bottom = !canFallBack,
                                enabled = state.enabled,
                                checked = state.includeIPv6,
                                onCheckedChange = onIncludeIPv6Change,
                                trailing = {
                                    Switch(
                                        checked = state.includeIPv6,
                                        onCheckedChange = null,
                                        enabled = state.enabled,
                                    )
                                },
                            )
                        }
                        if (canFallBack) {
                            SettingsRow(
                                title = stringResource(Res.string.doh_fallback_title),
                                subtitle = stringResource(Res.string.doh_fallback_hint),
                                top = !canChooseRecordTypes,
                                bottom = true,
                                enabled = state.enabled,
                                checked = state.fallbackToSystem,
                                onCheckedChange = onFallbackChange,
                                trailing = {
                                    Switch(
                                        checked = state.fallbackToSystem,
                                        onCheckedChange = null,
                                        enabled = state.enabled,
                                    )
                                },
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(onClick = onSave, enabled = state.enabled) {
                        Text(stringResource(Res.string.doh_save))
                    }
                    TextButton(onClick = onTest, enabled = state.enabled && !state.testing) {
                        if (state.testing) {
                            CircularProgressIndicator(Modifier.size(18.dp).describedAsLoading())
                        } else {
                            Text(stringResource(Res.string.doh_test))
                        }
                    }
                }
                state.resolution?.let { resolution -> DohResolutionText(resolution) }
                state.testFailure?.let { failure ->
                    Text(
                        text = stringResource(Res.string.doh_test_failure, failure),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }

            SettingsGroup {
                SettingsBlock(
                    title = stringResource(Res.string.doh_limits_title),
                    top = true,
                    bottom = true,
                ) {
                    DohNote(stringResource(Res.string.doh_limits_hint))
                    // Where there is no fallback switch, there is no fallback — the platform blocks
                    // cleartext resolution outright while this is on, and defers to an encrypted
                    // resolver the system already has. Someone about to turn it on should know both.
                    if (!state.capabilities.canFallBackToSystem) {
                        DohNote(stringResource(Res.string.doh_limits_encrypted_only_hint))
                    }
                    DohNote(stringResource(Res.string.doh_proxy_hint))
                    DohNote(stringResource(Res.string.doh_webview_hint))
                }
            }
        }
    }
}

/** The answer itself — the addresses, so the reader can tell a real one from what their network said. */
@Composable
private fun DohResolutionText(resolution: DnsResolution) {
    Text(
        text = stringResource(
            Res.string.doh_test_result,
            resolution.host,
            resolution.addresses.joinToString("、"),
            resolution.elapsedMillis.toString(),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Shared with 网络自检, which names the same provider on a row of its own. */
@Composable
internal fun dohProviderLabel(provider: DohProvider): String =
    stringResource(
        when (provider) {
            DohProvider.ALIDNS -> Res.string.doh_provider_alidns
            DohProvider.DNSPOD -> Res.string.doh_provider_dnspod
            DohProvider.CLOUDFLARE -> Res.string.doh_provider_cloudflare
            DohProvider.GOOGLE -> Res.string.doh_provider_google
            DohProvider.CUSTOM -> Res.string.doh_provider_custom
        },
    )

@Composable
private fun DohNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

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
            onProviderChange = {},
            onUrlChange = {},
            onBootstrapChange = {},
            onIncludeIPv6Change = {},
            onFallbackChange = {},
            onSave = {},
            onTest = {},
        )
    }
}
