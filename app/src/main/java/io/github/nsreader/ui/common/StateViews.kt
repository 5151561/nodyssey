package io.github.nsreader.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.nsreader.R
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.isRecoverableInBrowser
import io.github.nsreader.ui.theme.NodeSeekTheme

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Failures on NodeSeek are usually recoverable by a human in a WebView (solve the Cloudflare
 * challenge, or sign in), so the error state offers that action rather than only a retry button.
 */
@Composable
fun ErrorState(
    error: NodeSeekError,
    onRetry: () -> Unit,
    onOpenBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = error.message(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (error.isRecoverableInBrowser) {
            Button(onClick = onOpenBrowser) {
                Text(
                    stringResource(
                        if (error is NodeSeekError.LoginRequired) {
                            R.string.action_sign_in
                        } else {
                            R.string.action_verify_in_browser
                        },
                    ),
                )
            }
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        } else {
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

/** The one place that turns a data-layer error into words. */
@Composable
fun NodeSeekError.message(): String = when (this) {
    NodeSeekError.Cloudflare -> stringResource(R.string.error_cloudflare)
    NodeSeekError.LoginRequired -> stringResource(R.string.error_login_required)
    is NodeSeekError.Http -> stringResource(R.string.error_http, statusCode)
    NodeSeekError.Unparsable -> stringResource(R.string.error_unparsable)
    NodeSeekError.Network -> stringResource(R.string.error_network)
    NodeSeekError.Unknown -> stringResource(R.string.error_unknown)
}

@Preview(showBackground = true)
@Composable
private fun ErrorStateCloudflarePreview() {
    NodeSeekTheme {
        ErrorState(error = NodeSeekError.Cloudflare, onRetry = {}, onOpenBrowser = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorStateNetworkPreview() {
    NodeSeekTheme {
        ErrorState(error = NodeSeekError.Network, onRetry = {}, onOpenBrowser = {})
    }
}
