package io.github.nodyssey.ios

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/*
 * The two directories this app writes to, and the line between them.
 *
 * Application Support is what the system keeps: the database, the settings files, the downloaded
 * threads. Caches is what the system may take back whenever it is short of room: decoded images, the
 * web view's own store. That is the same distinction `filesDir` and `cacheDir` draw on Android, and
 * getting it backwards is how 「已离线」 stops being true after a night of low disk space.
 */

@OptIn(ExperimentalForeignApi::class)
internal fun applicationSupportDirectory(): NSURL = systemDirectory(NSApplicationSupportDirectory)

@OptIn(ExperimentalForeignApi::class)
internal fun cachesDirectory(): NSURL = systemDirectory(NSCachesDirectory)

@OptIn(ExperimentalForeignApi::class)
private fun systemDirectory(directory: NSSearchPathDirectory): NSURL =
    requireNotNull(
        NSFileManager.defaultManager.URLForDirectory(
            directory = directory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            // Application Support is not created for an app on its own; Caches is, and asking twice
            // costs nothing.
            create = true,
            error = null,
        ),
    ) { "no directory $directory in the user domain" }
