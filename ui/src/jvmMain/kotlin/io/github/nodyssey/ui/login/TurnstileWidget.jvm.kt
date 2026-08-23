package io.github.nodyssey.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import io.github.plaza.core.net.UserAgent

/**
 * No embedded browser here, so there is no widget and no token.
 *
 * The screen is told rather than left guessing: [onUnavailable] puts the verification block in
 * [VerificationState.NotWired], which says so on the page and keeps 登录 disabled. That is the honest
 * shape of "the desktop target proves the screens compile", which is all that target is for — the
 * same note `WebViewScreen.jvm.kt` carries.
 */
@Composable
actual fun TurnstileWidget(
    sitekey: String,
    darkTheme: Boolean,
    userAgent: UserAgent,
    resetSignal: Int,
    onToken: (String) -> Unit,
    onExpired: () -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier,
) {
    LaunchedEffect(Unit) { onUnavailable() }
}
