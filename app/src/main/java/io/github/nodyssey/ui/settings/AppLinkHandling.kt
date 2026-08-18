package io.github.nodyssey.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Whether the system currently lets this app open `nodeseek.com` links, or null where the question
 * does not arise.
 *
 * Android 12 changed what a `<intent-filter>` on an https host buys you. A domain that has not
 * passed App Links verification is off by default — the app does not merely lose the "always" tie
 * break, it is left out of the candidates entirely and the link goes straight to a browser. And this
 * app cannot pass verification: that takes an `assetlinks.json` served from nodeseek.com, and the
 * domain belongs to the forum, not to us.
 *
 * So the switch has to be thrown by hand, once, in a corner of the system settings nobody visits.
 * Reading it back is what lets the settings screen say which side it is on instead of offering an
 * unexplained link — see [rememberAppLinkHandlingEnabled].
 *
 * Null below API 31, where none of this applies: an unverified filter there still puts the app in
 * the chooser, so there is nothing for the user to turn on and nothing worth showing them.
 */
fun appLinkHandlingEnabled(context: Context): Boolean? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val manager =
        context.getSystemService(DomainVerificationManager::class.java) ?: return null
    // Throws for a package the caller may not ask about; ours always qualifies, but the call is
    // cheap to guard and a settings row is not worth a crash.
    val state =
        runCatching { manager.getDomainVerificationUserState(context.packageName) }
            .getOrNull() ?: return null
    return state.hostToStateMap.values.any {
        it == DomainVerificationUserState.DOMAIN_STATE_SELECTED ||
            it == DomainVerificationUserState.DOMAIN_STATE_VERIFIED
    }
}

/**
 * The system page holding that switch, opened for this app.
 *
 * Below API 31 there is no such page, and the settings row that leads here is hidden anyway. The
 * app's own details page is the honest fallback rather than an intent that would resolve to nothing:
 * it exists on every version, and it is where someone poking at this app's system settings meant to
 * end up.
 */
fun appLinkSettingsIntent(context: Context): Intent {
    val target =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS
        } else {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }
    return Intent(target, "package:${context.packageName}".toUri())
}

/**
 * [appLinkHandlingEnabled], re-read every time the screen comes back to the front.
 *
 * The user leaves for the system page to change it and returns; nothing tells us that happened, so
 * ON_RESUME is the signal. Read once up front as well, or the row would appear a frame late.
 */
@Composable
fun rememberAppLinkHandlingEnabled(context: Context): Boolean? {
    var enabled by remember(context) { mutableStateOf(appLinkHandlingEnabled(context)) }
    LifecycleResumeEffect(context) {
        enabled = appLinkHandlingEnabled(context)
        onPauseOrDispose {}
    }
    return enabled
}
