package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.nodyssey.core.ActiveSite
import io.github.nodyssey.core.Site
import platform.Foundation.NSUserDefaults

@Composable
actual fun rememberSiteSwitch(): (Site) -> Unit =
    remember {
        { site -> if (site != ActiveSite.current) IosActiveSite.store(site) }
    }

/**
 * iOS stores the choice and applies it at the next launch. It does not restart itself.
 *
 * Not a shortcut — there is no supported way to do it. Terminating an app from inside it is grounds
 * for App Store rejection and reads to the user as a crash, and the alternative of rebuilding the
 * graph in place is ruled out by the shell itself: `IosNodysseyApp.ensureContainer` builds exactly
 * one container per process *because* two of them over the same DataStore files raise
 * `IllegalStateException` the moment both are serving one file. Per-site file names widen that only
 * as far as the two containers not sharing files; the old one's UI, its coroutines and its Room
 * handle would still be live behind the new one.
 *
 * So the switcher says so on this platform instead of pretending — see [siteSwitchRestartsApp].
 */
actual val siteSwitchRestartsApp: Boolean = false

/** 站点, as an Apple platform stores it. The Android note on [AndroidActiveSite] applies here too. */
object IosActiveSite {
    private const val KEY_SITE = "active_site"

    /** Reads the stored choice and installs it. Called from the shell's entry point, once. */
    fun install() {
        ActiveSite.install(Site.ofStoredValue(NSUserDefaults.standardUserDefaults.stringForKey(KEY_SITE)))
    }

    internal fun store(site: Site) {
        NSUserDefaults.standardUserDefaults.setObject(site.storedValue, KEY_SITE)
    }
}
