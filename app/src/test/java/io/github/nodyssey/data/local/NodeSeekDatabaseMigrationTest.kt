package io.github.nodyssey.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /**
     * Nothing is known about a v5 row's block state, and 0 is what the app already treated it as, so
     * the default is the honest value here — the next refresh writes the site's actual answer.
     */
    @Test
    fun `migration 5 to 6 marks stored posts unblocked without dropping them`() {
        helper.createDatabase(DATABASE_NAME, 5).apply {
            execSQL(
                """
                INSERT INTO posts(
                    postId, title, authorName, authorUid, avatarUrl, categoryTitle, categorySlug,
                    viewCount, commentCount, lastActiveText, lastActiveTitle, isPinned, isLocked,
                    lockLevel, cachedAtMillis
                )
                VALUES(42, 'a cached post', 'someone', 7, NULL, '日常', 'daily', 100, 3, NULL, NULL, 0, 0, NULL, 1000)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DATABASE_NAME, 6, true, MIGRATION_5_6)

        migrated.query("SELECT title, isBlocked FROM posts WHERE postId = 42").use { cursor ->
            cursor.moveToFirst()
            assertEquals("a cached post", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
        }
        migrated.close()
    }

    /**
     * The unread baseline is the thing that must survive: losing it would silently mark every
     * already-read thread unread again. The new snapshot columns stay null, because a v6 row really
     * does not know the thread's title and a placeholder would be a claim rather than a gap.
     */
    @Test
    fun `migration 6 to 7 keeps read marks and leaves their snapshot empty`() {
        helper.createDatabase(DATABASE_NAME, 6).apply {
            execSQL(
                """
                INSERT INTO post_read_marks(postId, lastReadAtMillis, lastSeenCommentCount)
                VALUES(42, 1000, 3)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DATABASE_NAME, 7, true, MIGRATION_6_7)

        migrated.query("SELECT lastSeenCommentCount, title, authorName FROM post_read_marks WHERE postId = 42").use {
            it.moveToFirst()
            assertEquals(3, it.getInt(0))
            assertTrue(it.isNull(1))
            assertTrue(it.isNull(2))
        }
        migrated.close()
    }

    /**
     * Null rather than 0/false: the star on a migrated thread must stay untappable until a page tells
     * us the truth, and only null can say "nobody has told us yet".
     */
    @Test
    fun `migration 6 to 7 leaves cached threads readable with unknown collection state`() {
        helper.createDatabase(DATABASE_NAME, 6).apply {
            execSQL(
                """
                INSERT INTO post_details(postId, title, body, totalPages, firstLoadedPage, loadedPages, cachedAtMillis)
                VALUES(42, 'a cached thread', NULL, 9, 1, 3, 1000)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DATABASE_NAME, 7, true, MIGRATION_6_7)

        migrated.query("SELECT title, collected, collectionCount FROM post_details WHERE postId = 42").use {
            it.moveToFirst()
            assertEquals("a cached thread", it.getString(0))
            assertTrue(it.isNull(1))
            assertTrue(it.isNull(2))
        }
        migrated.close()
    }

    /**
     * The new table is empty and everything else is untouched: a reader upgrading has read marks and
     * cached threads, and has never had a bookmark to carry over.
     */
    @Test
    fun `migration 7 to 8 adds the reading positions table and keeps read marks`() {
        helper.createDatabase(DATABASE_NAME, 7).apply {
            execSQL(
                """
                INSERT INTO post_read_marks(postId, lastReadAtMillis, lastSeenCommentCount, title)
                VALUES(42, 1000, 3, 'a thread that was read')
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DATABASE_NAME, 8, true, MIGRATION_7_8)

        migrated.query("SELECT title FROM post_read_marks WHERE postId = 42").use {
            it.moveToFirst()
            assertEquals("a thread that was read", it.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM post_reading_positions").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        migrated.close()
    }

    /**
     * The two 推荐阅读 columns start at different values on purpose: a list row is rewritten by the next
     * refresh, so `0` corrects itself, while a cached thread may never be re-read from page 1 and only
     * null can say "no page has told us".
     */
    @Test
    fun `migration 9 to 10 adds the 推荐阅读 columns without dropping rows`() {
        helper.createDatabase(DATABASE_NAME, 9).apply {
            execSQL(
                """
                INSERT INTO posts(
                    postId, title, authorName, authorUid, avatarUrl, categoryTitle, categorySlug,
                    viewCount, commentCount, lastActiveText, lastActiveTitle, isPinned, isLocked,
                    lockLevel, isBlocked, cachedAtMillis
                )
                VALUES(42, 'a cached post', 'someone', 7, NULL, '日常', 'daily', 100, 3, NULL, NULL, 0, 0, NULL, 0, 1000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO post_details(postId, title, body, totalPages, firstLoadedPage, loadedPages, cachedAtMillis)
                VALUES(42, 'a cached thread', NULL, 9, 1, 3, 1000)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DATABASE_NAME, 10, true, MIGRATION_9_10)

        migrated.query("SELECT title, isAwarded FROM posts WHERE postId = 42").use {
            it.moveToFirst()
            assertEquals("a cached post", it.getString(0))
            assertEquals(0, it.getInt(1))
        }
        migrated.query("SELECT title, isAwarded FROM post_details WHERE postId = 42").use {
            it.moveToFirst()
            assertEquals("a cached thread", it.getString(0))
            assertTrue(it.isNull(1))
        }
        migrated.close()
    }

    private companion object {
        const val DATABASE_NAME = "profile-migration-test"
    }
}
