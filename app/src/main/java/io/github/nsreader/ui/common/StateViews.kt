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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.nsreader.core.net.ChallengeDetector

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
    message: String,
    challenge: ChallengeDetector.Challenge?,
    onRetry: () -> Unit,
    onOpenBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recoverable = challenge == ChallengeDetector.Challenge.Cloudflare ||
        challenge == ChallengeDetector.Challenge.LoginRequired

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (recoverable) {
            Button(onClick = onOpenBrowser) {
                Text(
                    when (challenge) {
                        ChallengeDetector.Challenge.LoginRequired -> "登录"
                        else -> "在浏览器中验证"
                    },
                )
            }
            OutlinedButton(onClick = onRetry) { Text("重试") }
        } else {
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}
