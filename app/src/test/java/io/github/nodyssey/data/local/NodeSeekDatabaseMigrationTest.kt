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

    private companion object {
        const val DATABASE_NAME = "profile-migration-test"
    }
}
