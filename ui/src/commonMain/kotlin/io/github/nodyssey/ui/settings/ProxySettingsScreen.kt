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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.proxy.ProxyConfigProblem
import io.github.nodyssey.data.proxy.ProxyConnectionFailure
import io.github.nodyssey.data.proxy.ProxyScope
import io.github.nodyssey.data.proxy.ProxyType
import io.github.nodyssey.ui.account.AccountMessageSnackbar
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.proxy_advanced_hint
import io.github.nodyssey.ui.resources.proxy_advanced_title
import io.github.nodyssey.ui.resources.proxy_credential_hint
import io.github.nodyssey.ui.resources.proxy_host_label
import io.github.nodyssey.ui.resources.proxy_host_placeholder
import io.github.nodyssey.ui.resources.proxy_host_required
import io.github.nodyssey.ui.resources.proxy_master_hint
import io.github.nodyssey.ui.resources.proxy_master_title
import io.github.nodyssey.ui.resources.proxy_password_label
import io.github.nodyssey.ui.resources.proxy_port_invalid
import io.github.nodyssey.ui.resources.proxy_port_label
import io.github.nodyssey.ui.resources.proxy_port_placeholder
import io.github.nodyssey.ui.resources.proxy_save
import io.github.nodyssey.ui.resources.proxy_scope_hint
import io.github.nodyssey.ui.resources.proxy_scope_title
import io.github.nodyssey.ui.resources.proxy_test
import io.github.nodyssey.ui.resources.proxy_test_failure_connection
import io.github.nodyssey.ui.resources.proxy_test_failure_dns
import io.github.nodyssey.ui.resources.proxy_test_failure_other
import io.github.nodyssey.ui.resources.proxy_test_failure_socks_auth
import io.github.nodyssey.ui.resources.proxy_test_failure_timeout
import io.github.nodyssey.ui.resources.proxy_test_failure_tls
import io.github.nodyssey.ui.resources.proxy_title
import io.github.nodyssey.ui.resources.proxy_type_http
import io.github.nodyssey.ui.resources.proxy_type_socks
import io.github.nodyssey.ui.resources.proxy_type_title
import io.github.nodyssey.ui.resources.proxy_username_label
import io.github.nodyssey.ui.resources.proxy_webview_hint
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProxySettingsRoute(
    viewModel: ProxySettingsViewModel,
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

    ProxySettingsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onEnabledChange = viewModel::setEnabled,
        onTypeChange = viewModel::setType,
        onForumOnlyChange = viewModel::setForumOnly,
        onHostChange = viewModel::updateHost,
        onPortChange = viewModel::updatePort,
        onUsernameChange = viewModel::updateUsername,
        onPasswordChange = viewModel::updatePassword,
        onSave = viewModel::save,
        onTest = viewModel::test,
        modifier = modifier,
    )
}

/**
 * 代理 — an HTTP or SOCKS5 proxy for the app's own requests.
 *
 * Everything below the master switch is dimmed and inert while it is off, the same
 * f4-style treatment [NotificationSettingsScreen] uses — there is nothing to configure until there is
 * something to turn on.
 */
@Composable
fun ProxySettingsScreen(
    state: ProxySettingsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onTypeChange: (ProxyType) -> Unit,
    onForumOnlyChange: (Boolean) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
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
                title = stringResource(Res.string.proxy_title),
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
                    title = stringResource(Res.string.proxy_master_title),
                    subtitle = stringResource(Res.string.proxy_master_hint),
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
                SettingsGroup {
                    SettingsBlock(
                        title = stringResource(Res.string.proxy_type_title),
                        top = true,
                        bottom = true,
                    ) {
                        val choices = listOf(
                            ProxyType.HTTP to stringResource(Res.string.proxy_type_http),
                            ProxyType.SOCKS to stringResource(Res.string.proxy_type_socks),
                        )
                        ConnectedChoiceButtons(
                            labels = choices.map { it.second },
                            selectedIndex = choices.indexOfFirst { it.first == state.type },
                            onSelect = { onTypeChange(choices[it].first) },
                            enabled = state.enabled,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        ProxyField(
                            value = state.hostInput,
                            onValueChange = onHostChange,
                            labelRes = Res.string.proxy_host_label,
                            placeholderRes = Res.string.proxy_host_placeholder,
                            errorRes = Res.string.proxy_host_required,
                            isError = state.problem == ProxyConfigProblem.MISSING_HOST,
                            enabled = state.enabled,
                            keyboardType = KeyboardType.Uri,
                            modifier = Modifier.weight(2f),
                        )
                        ProxyField(
                            value = state.portInput,
                            onValueChange = onPortChange,
                            labelRes = Res.string.proxy_port_label,
                            placeholderRes = Res.string.proxy_port_placeholder,
                            errorRes = Res.string.proxy_port_invalid,
                            isError = state.problem == ProxyConfigProblem.INVALID_PORT,
                            enabled = state.enabled,
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ProxyField(
                        value = state.usernameInput,
                        onValueChange = onUsernameChange,
                        labelRes = Res.string.proxy_username_label,
                        placeholderRes = Res.string.proxy_credential_hint,
                        enabled = state.enabled,
                        keyboardType = KeyboardType.Ascii,
                    )
                    ProxyField(
                        value = state.passwordInput,
                        onValueChange = onPasswordChange,
                        labelRes = Res.string.proxy_password_label,
                        placeholderRes = Res.string.proxy_credential_hint,
                        enabled = state.enabled,
                        keyboardType = KeyboardType.Password,
                        obscure = true,
                    )
                }

                SettingsGroup {
                    SettingsRow(
                        title = stringResource(Res.string.proxy_scope_title),
                        subtitle = stringResource(Res.string.proxy_scope_hint),
                        top = true,
                        bottom = true,
                        enabled = state.enabled,
                        checked = state.scope == ProxyScope.FORUM_ONLY,
                        onCheckedChange = onForumOnlyChange,
                        trailing = {
                            Switch(
                                checked = state.scope == ProxyScope.FORUM_ONLY,
                                onCheckedChange = null,
                                enabled = state.enabled,
                            )
                        },
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(onClick = onSave, enabled = state.enabled) {
                        Text(stringResource(Res.string.proxy_save))
                    }
                    TextButton(onClick = onTest, enabled = state.enabled && !state.testing) {
                        if (state.testing) {
                            CircularProgressIndicator(Modifier.size(18.dp))
                        } else {
                            Text(stringResource(Res.string.proxy_test))
                        }
                    }
                }
                state.testFailure?.let { failure ->
                    Text(
                        text = proxyTestFailureText(failure),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }

            // Outside the dimmed block on purpose: this is what someone reads *before* deciding what
            // to type, and it is the only place the app admits it speaks neither VLESS nor the
            // WebView's network stack.
            SettingsGroup {
                SettingsBlock(
                    title = stringResource(Res.string.proxy_advanced_title),
                    top = true,
                    bottom = true,
                ) {
                    ProxyNote(stringResource(Res.string.proxy_advanced_hint))
                    ProxyNote(stringResource(Res.string.proxy_webview_hint))
                }
            }
        }
    }
}

@Composable
private fun proxyTestFailureText(failure: ProxyConnectionFailure): String {
    val messageRes =
        when (failure.kind) {
            ProxyConnectionFailure.Kind.DNS -> Res.string.proxy_test_failure_dns
            ProxyConnectionFailure.Kind.TIMEOUT -> Res.string.proxy_test_failure_timeout
            ProxyConnectionFailure.Kind.CONNECTION -> Res.string.proxy_test_failure_connection
            ProxyConnectionFailure.Kind.SOCKS_AUTHENTICATION -> Res.string.proxy_test_failure_socks_auth
            ProxyConnectionFailure.Kind.TLS -> Res.string.proxy_test_failure_tls
            ProxyConnectionFailure.Kind.OTHER -> Res.string.proxy_test_failure_other
        }
    return stringResource(messageRes, failure.exceptionName)
}

@Composable
private fun ProxyNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ProxyField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: StringResource,
    placeholderRes: StringResource,
    modifier: Modifier = Modifier,
    errorRes: StringResource? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    obscure: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        isError = isError,
        label = { Text(stringResource(labelRes)) },
        placeholder = { Text(stringResource(placeholderRes)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (obscure) PasswordVisualTransformation() else VisualTransformation.None,
        supportingText = if (isError && errorRes != null) {
            { Text(stringResource(errorRes)) }
        } else {
            null
        },
    )
}

private const val DISABLED_ALPHA = 0.5f

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ProxySettingsPreview() {
    PlazaTheme {
        ProxySettingsScreen(
            state = ProxySettingsUiState(enabled = true, hostInput = "192.168.1.1", portInput = "1080"),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onEnabledChange = {},
            onTypeChange = {},
            onForumOnlyChange = {},
            onHostChange = {},
            onPortChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onSave = {},
            onTest = {},
        )
    }
}
