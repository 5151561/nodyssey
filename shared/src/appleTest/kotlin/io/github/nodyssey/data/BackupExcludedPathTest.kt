package io.github.nodyssey.data

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Moving a settings file out of backup, on a real file system.
 *
 * What an upgrade must not do is lose the settings a store already held — the whole image host
 * record, not only its token — or leave the file where backups still reach it.
 */
@OptIn(ExperimentalForeignApi::class)
class BackupExcludedPathTest {
    private val files = NSFileManager.defaultManager
    private val parent = NSTemporaryDirectory() + "backup-excluded-" + NSUUID().UUIDString()

    init {
        files.createDirectoryAtPath(parent, withIntermediateDirectories = true, attributes = null, error = null)
    }

    @AfterTest
    fun removeScratch() {
        files.removeItemAtPath(parent, error = null)
    }

    private fun write(path: String, text: String) {
        @Suppress("CAST_NEVER_SUCCEEDS")
        (text as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    private fun read(path: String): String? = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)

    @Test
    fun `a store opened before is moved into the excluded directory with its contents`() {
        write("$parent/imagehost.preferences_pb", "old settings")

        val path = backupExcludedPath(parent, "imagehost.preferences_pb")

        assertEquals("$parent/$NO_BACKUP_DIRECTORY/imagehost.preferences_pb", path)
        assertEquals("old settings", read(path))
        assertFalse(files.fileExistsAtPath("$parent/imagehost.preferences_pb"))
    }

    @Test
    fun `the directory is marked as excluded from backup`() {
        backupExcludedPath(parent, "proxy.preferences_pb")

        val values =
            NSURL.fileURLWithPath("$parent/$NO_BACKUP_DIRECTORY", isDirectory = true)
                .resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey), error = null)
        assertTrue((values?.get(NSURLIsExcludedFromBackupKey) as? NSNumber)?.boolValue == true)
    }
}
