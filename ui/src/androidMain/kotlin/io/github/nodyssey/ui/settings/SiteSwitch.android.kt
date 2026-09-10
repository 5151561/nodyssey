package io.github.nodyssey.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import io.github.nodyssey.core.ActiveSite
import io.github.nodyssey.core.Site
import kotlin.system.exitProcess

@Composable
actual fun rememberSiteSwitch(): (Site) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { site ->
            // Restart only on a write that actually landed. A restart onto the old site looks like
            // the button doing nothing at all, which is the one outcome worth not producing.
            if (site != ActiveSite.current && AndroidActiveSite.store(context, site)) {
                AndroidActiveSite.restart(context)
            }
        }
    }
}

/** Android restarts into the other site by itself; nothing is left for the reader to do. */
actual val siteSwitchRestartsApp: Boolean = true

/**
 * 站点, as this platform stores and applies it — the same shape, and for the same reason, as
 * [AndroidAppLanguage].
 *
 * **Why `SharedPreferences` and not the settings DataStore.** [install] has to run in
 * `attachBaseContext`, before anything asynchronous can have finished and before the dependency
 * graph exists — and it has to run *that* early because the answer decides which files the graph
 * then opens: the Room database's name and the composer stores' names are built from
 * [Site.storageSuffix]. A suspending `Flow` cannot answer a question asked that early. Unlike 语言,
 * there is no DataStore copy behind this one: nothing reads the site asynchronously, so a second
 * store would be a second source of truth bought for nothing.
 */
object AndroidActiveSite {
    private const val PREFERENCES = "active_site"
    private const val KEY_SITE = "site"

    /**
     * Reads the stored choice and installs it. Called from `attachBaseContext`, once per process.
     *
     * Takes the base context rather than the application: at that point there is no application to
     * take, which is the whole reason this reads a `SharedPreferences` file directly.
     */
    fun install(context: Context) {
        ActiveSite.install(Site.ofStoredValue(preferences(context).getString(KEY_SITE, null)))
    }

    /**
     * Writes the choice, synchronously, and says whether it landed.
     *
     * **`commit = true` is the whole point of this function.** `apply()` — what `edit {}` does by
     * default — returns as soon as the value is in memory and finishes the disk write on a
     * background thread, and the platform only guarantees that write is flushed at lifecycle points
     * like `onStop`. [restart] ends the process a few microseconds later, which is not one of them:
     * the new process then reads the *old* site back and the app comes up exactly where it was, the
     * switch having appeared to do nothing but restart. Committing blocks until the file is written,
     * which is the ordering this needs and the one case where the synchronous write is right.
     */
    internal fun store(context: Context, site: Site): Boolean {
        var stored = false
        preferences(context).edit(commit = true) {
            putString(KEY_SITE, site.storedValue)
            stored = true
        }
        return stored && Site.ofStoredValue(preferences(context).getString(KEY_SITE, null)) == site
    }

    /**
     * Starts the app again from scratch, on the site just stored.
     *
     * **A new process, not `Activity.recreate()`.** `recreate` keeps the `ViewModelStore`, so every
     * ViewModel would come back holding repositories built against the old site, over a Room handle
     * open on the old file. Even clearing that store by hand would leave the old graph's coroutines
     * running: `DefaultAppContainer` owns a process-lived scope, and DataStore refuses a second
     * reader of a file the first has not released. Ending the process is what makes all of that
     * moot, and it is honest about what a site switch costs — a cold start, which this app has a
     * baseline profile for.
     *
     * `CLEAR_TASK` so the back stack does not survive into a site where its post ids mean something
     * else, and `exitProcess` after `startActivity` so the new task launches into a clean process.
     */
    internal fun restart(context: Context) {
        val launch =
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        // No launch intent is not a state worth crashing over: leaving the choice stored means the
        // next ordinary launch lands on the new site anyway.
        if (launch != null) {
            context.startActivity(launch)
            exitProcess(0)
        }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
