package io.github.nodyssey.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * Opens one of the app's settings files on an Apple platform.
 *
 * The Android counterpart is `PreferenceStores.kt` in `:app` — a set of `Context` extensions, which
 * is what `preferencesDataStore` is and where it insists on living. This is the same decision made
 * twice, and it is the whole of what a platform contributes to storing a setting: *where the file
 * goes*. What is in the file — the keys, the defaults, the migrations — is a fact about the app and
 * is the repository's, not this file's.
 *
 * [name] is the store's name without the extension, and the caller passes the same string Android
 * does: `settings`, `proxy`, `offline`. **Those names are load-bearing on Android** — each is an
 * existing file on every installed device — and matching them here costs nothing while making a
 * store recognisable across the two.
 */
@OptIn(ExperimentalForeignApi::class)
fun createPreferenceDataStore(name: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        // The extension is Android's own, appended by `preferencesDataStore` there and stated here so
        // the two platforms write a file of the same name as well as the same format.
        produceFile = { (applicationSupportDirectory() + "/$name.preferences_pb").toPath() },
    )

@OptIn(ExperimentalForeignApi::class)
private fun applicationSupportDirectory(): String {
    val directory =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    return requireNotNull(directory?.path) { "no Application Support directory" }
}
