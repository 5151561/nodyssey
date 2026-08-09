package io.github.bbs1.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.bbs1.R
import io.github.bbs1.ui.common.apiErrorText
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth

/**
 * Username and password against one site.
 *
 * A form rather than the web login page in a WebView, which is what the other app in this repository
 * has to do: this forum hands out a bearer token for exactly this, so there is no session cookie to
 * go and collect from a browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    state: LoginUiState,
    siteName: String,
    onBack: () -> Unit,
    onSubmit: (username: String, password: String) -> Unit,
    onSucceeded: () -> Unit,
    onErrorConsumed: () -> Unit,
) {
    val username = rememberTextFieldState()
    val password = rememberTextFieldState()

    LaunchedEffect(state.succeeded) {
        if (state.succeeded) onSucceeded()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bbs1_login_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.bbs1_action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val submit = {
            onErrorConsumed()
            onSubmit(username.text.toString(), password.text.toString())
        }
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.bbs1_login_subtitle, siteName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                state = username,
                label = { Text(stringResource(R.string.bbs1_login_username)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                enabled = !state.submitting,
                keyboardOptions =
                KeyboardOptions(
                    // A forum name is not a sentence; autocapitalising it costs a correction on
                    // every login.
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedSecureTextField(
                state = password,
                label = { Text(stringResource(R.string.bbs1_login_password)) },
                enabled = !state.submitting,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onKeyboardAction = { submit() },
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.error != null) {
                Text(
                    text = apiErrorText(state.error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = submit,
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.bbs1_login_submit))
                }
            }
        }
    }
}
