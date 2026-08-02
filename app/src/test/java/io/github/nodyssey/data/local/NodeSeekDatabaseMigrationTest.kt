package io.github.nodyssey.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NodeSeekDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            NodeSeekDatabase::class.java,
        )

    @Test
    fun `migration 3 to 4 keeps existing data and adds the profile table`() {
        helper.createDatabase(DATABASE_NAME, 3).apply {
            execSQL(
                """
                INSERT INTO post_read_marks(postId, lastReadAtMillis, lastSeenCommentCount)
                VALUES(42, 1000, 3)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DATABASE_NAME, 4, true, MIGRATION_3_4)

        migrated.query("SELECT lastSeenCommentCount FROM post_read_marks WHERE postId = 42").use { cursor ->
            cursor.moveToFirst()
            assertEquals(3, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM self_profile").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    /**
     * A v4 row held a prefix that began at page 1, so the new start defaults to exactly what it was
     * and no cached thread has to be re-fetched to stay readable. `loadedPages` counted that prefix,
     * which for a slice starting at page 1 is the number of its last page — the column keeps both its
     * name and its value.
     */
    @Test
    fun `migration 4 to 5 gives cached threads a start page without dropping them`() {
        helper.createDatabase(DATABASE_NAME, 4).apply {
            execSQL(
                """
                INSERT INTO post_details(postId, title, body, totalPages, loadedPages, cachedAtMillis)
                VALUES(42, 'a cached thread', NULL, 9, 3, 1000)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DATABASE_NAME, 5, true, MIGRATION_4_5)

        migrated.query("SELECT firstLoadedPage, loadedPages, title FROM post_details WHERE postId = 42").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
            assertEquals(3, cursor.getInt(1))
            assertEquals("a cached thread", cursor.getString(2))
        }
        migrated.close()
    }

    private companion object {
        const val DATABASE_NAME = "profile-migration-test"
    }
}
