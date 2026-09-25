package io.github.nodyssey.data

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
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
 *
 * [keepOutOfBackup] is for a store that holds a secret as typed — see `PlaintextSecretCipher`. The
 * file goes into [NO_BACKUP_DIRECTORY] instead of beside the others, and one opened without it before
 * is moved there first, so the settings it held come along.
 */
@OptIn(ExperimentalForeignApi::class)
fun createPreferenceDataStore(
    name: String,
    keepOutOfBackup: Boolean = false,
    migrations: List<DataMigration<Preferences>> = emptyList(),
): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        migrations = migrations,
        produceFile = {
            // The extension is Android's own, appended by `preferencesDataStore` there and stated here
            // so the two platforms write a file of the same name as well as the same format.
            val fileName = "$name.preferences_pb"
            val directory = applicationSupportDirectory()
            val path = if (keepOutOfBackup) backupExcludedPath(directory, fileName) else "$directory/$fileName"
            path.toPath()
        },
    )

/**
 * Where [fileName] lives once it is kept out of backup: inside [parent]'s [NO_BACKUP_DIRECTORY], which
 * this creates and marks, having moved an earlier copy of the file there from [parent] itself.
 *
 * The mark goes on the directory and not on the file because DataStore never rewrites a file in
 * place: it writes a new one beside it and renames it over the old, and a resource value set on the
 * old file goes with the old file. Apple's own advice for a group of files is the same — move them
 * into a directory and mark that ("Optimizing your app's data for iCloud Backup").
 *
 * Which is also where the limit is stated: the mark is guidance to the system about what it may
 * leave out, not a guarantee that nothing inside ever appears in a backup.
 *
 * An earlier copy is moved only when the directory has none of its own, so a file that is already
 * there is never overwritten by a stale one.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun backupExcludedPath(parent: String, fileName: String): String {
    val files = NSFileManager.defaultManager
    val directory = "$parent/$NO_BACKUP_DIRECTORY"
    files.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
    // On every open rather than only when the directory is created: one attribute write, and nothing
    // has to remember whether it was done.
    NSURL.fileURLWithPath(directory, isDirectory = true)
        .setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
    val target = "$directory/$fileName"
    val earlier = "$parent/$fileName"
    if (files.fileExistsAtPath(earlier) && !files.fileExistsAtPath(target)) {
        files.moveItemAtPath(earlier, toPath = target, error = null)
    }
    return target
}

/**
 * The directory a [createPreferenceDataStore] store kept out of backup goes into.
 *
 * Named for the app, as Apple suggests for a directory made inside Application Support, so it cannot
 * collide with one the system may create there later.
 */
internal const val NO_BACKUP_DIRECTORY = "io.github.nodyssey.no-backup"

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
