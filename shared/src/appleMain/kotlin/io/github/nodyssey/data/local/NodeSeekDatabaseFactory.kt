package io.github.nodyssey.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * The same database, opened where an Apple platform keeps one.
 *
 * Application Support rather than Documents: this file is the app's cache and bookkeeping, not
 * something the person using it produced, and Documents is the directory a file-browsing user is
 * shown. The file name is the one Android already uses, which costs nothing and makes a store
 * recognisable across the two.
 *
 * [BundledSQLiteDriver] rather than the platform's own: Apple ships a system SQLite but Room's
 * driver for it is Android's, and a bundled copy is also the only way both targets are known to be
 * on the same SQLite version as the schemas were generated against.
 */
@OptIn(ExperimentalForeignApi::class)
fun createNodeSeekDatabase(): NodeSeekDatabase =
    Room
        .databaseBuilder<NodeSeekDatabase>(name = nodeSeekDatabasePath())
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*NODESEEK_MIGRATIONS)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

@OptIn(ExperimentalForeignApi::class)
private fun nodeSeekDatabasePath(): String {
    val directory: NSURL? =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    return requireNotNull(directory?.path) { "no Application Support directory" } + "/nodeseek.db"
}
