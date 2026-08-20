package io.github.nodyssey.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import io.github.nodyssey.data.session.SessionRepository
import io.github.plaza.core.net.UserAgent

/**
 * No embedded browser here, so the errand goes to the real one.
 *
 * That is not a working sign-in: the cookie the page issues lands in the system browser's jar and
 * this app cannot read it. It is the honest shape of "the desktop target proves the screens compile",
 * which is all that target is for — see `ui/build.gradle.kts`. A desktop build that actually signs in
 * needs the same thing iOS does, which is step D3.
 */
@Composable
actual fun WebViewRoute(
    url: String,
    title: String,
    goal: WebViewGoal,
    session: SessionRepository,
    userAgent: UserAgent,
    onOpenExternal: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
    isBound: (suspend () -> Boolean)?,
) {
    LaunchedEffect(url) {
        onOpenExternal(url)
        onClose()
    }
}
