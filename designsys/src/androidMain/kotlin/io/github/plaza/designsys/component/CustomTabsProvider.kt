package io.github.plaza.designsys.component

import android.content.Context
import androidx.browser.customtabs.CustomTabsClient

/**
 * Which installed app would answer if a link were opened in a Custom Tab right now.
 *
 * The same question [CustomTabsWarmer] asks before it binds, exposed so a diagnostics screen can
 * *show* the answer rather than only act on it. It is here because `androidx.browser` is here: the
 * dependency is `implementation`, so no consumer can name `CustomTabsClient` even if it wanted to,
 * and this module already owns every other sentence about what a Custom Tab is.
 *
 * Worth showing because it is not the fact readers assume it is. "The browser" on a phone is the
 * app in the launcher; the Custom Tabs provider is whichever app claims the *service*, which the
 * system picks from the default browser and which a second installed browser can quietly be. When a
 * VPN routes traffic per application — Clash's 访问控制, and every client like it — those two
 * packages can land on opposite sides of the list, and then a link is slow in the app and instant in
 * the browser for a reason that lives in neither.
 *
 * Null when nothing offers the service, which on API 30+ also covers "the manifest did not declare
 * the `<queries>` entry that makes them visible" — see the note in [CustomTabsWarmer.connect].
 */
fun customTabsProviderPackage(context: Context): String? =
    runCatching { CustomTabsClient.getPackageName(context, null) }.getOrNull()
