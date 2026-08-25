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
     * Both defaults describe a v8 store honestly: every stored feed began at page 1, so `page = 1`
     * is right for the head and self-correcting for the tail (the next refresh rewrites every row),
     * and `totalPages = 1` is the claim that keeps 首页翻页栏 hidden until a fresh page says more.
     * The row that must survive is the position itself — it is what puts a stored feed back in order.
     */
    @Test
    fun `migration 8 to 9 teaches stored feeds their page without dropping rows`() {
        helper.createDatabase(DATABASE_NAME, 8).apply {
            execSQL(
                """
                INSERT INTO feed_positions(feedKey, postId, sortIndex)
                VALUES('daily', 42, 7)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO feed_remote_keys(feedKey, nextPage, refreshedAtMillis)
                VALUES('daily', 3, 1000)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DATABASE_NAME, 9, true, MIGRATION_8_9)

        migrated.query("SELECT sortIndex, page FROM feed_positions WHERE postId = 42").use {
            it.moveToFirst()
            assertEquals(7, it.getInt(0))
            assertEquals(1, it.getInt(1))
        }
        migrated.query("SELECT nextPage, totalPages FROM feed_remote_keys WHERE feedKey = 'daily'").use {
            it.moveToFirst()
            assertEquals(3, it.getInt(0))
            assertEquals(1, it.getInt(1))
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

    /**
     * Three new tables and nothing else touched.
     *
     * `runMigrationsAndValidate` is doing the load-bearing work here: it compares the migrated file
     * against the exported v11 schema, so a column the hand-written `CREATE TABLE` spells
     * differently from the entity fails here rather than on a reader's device at open time.
     */
    @Test
    fun `migration 10 to 11 adds the offline tables and leaves the reader's cache alone`() {
        helper.createDatabase(DATABASE_NAME, 10).apply {
            execSQL(
                """
                INSERT INTO post_details(postId, title, body, totalPages, firstLoadedPage, loadedPages, cachedAtMillis)
                VALUES(42, 'a cached thread', NULL, 9, 1, 3, 1000)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DATABASE_NAME, 11, true, MIGRATION_10_11)

        migrated.query("SELECT title FROM post_details WHERE postId = 42").use {
            it.moveToFirst()
            assertEquals("a cached thread", it.getString(0))
        }
        listOf("offline_threads", "offline_comments", "offline_images").forEach { table ->
            migrated.query("SELECT COUNT(*) FROM $table").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        }
        migrated.close()
    }

    /**
     * The empty start is the honest one: this device has been told plenty about its collected
     * threads and never wrote any of it down, so there is nothing to back-fill and every row fills
     * in the next time something learns it again.
     */
    @Test
    fun `migration 11 to 12 adds the collected-thread details table`() {
        helper.createDatabase(DATABASE_NAME, 11).apply {
            execSQL(
                """
                INSERT INTO post_details(postId, title, body, totalPages, firstLoadedPage, loadedPages, cachedAtMillis)
                VALUES(42, 'a cached thread', NULL, 9, 1, 3, 1000)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DATABASE_NAME, 12, true, MIGRATION_11_12)

        migrated.query("SELECT title FROM post_details WHERE postId = 42").use {
            it.moveToFirst()
            assertEquals("a cached thread", it.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM collected_post_meta").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        migrated.close()
    }

    /**
     * Null on every existing row, and null is what a v12 row honestly knows: it stored the author's
     * name and never asked anything for a face, so there is nothing in the file to back-fill from.
     */
    @Test
    fun `migration 12 to 13 gives remembered threads an avatar without dropping them`() {
        helper.createDatabase(DATABASE_NAME, 12).apply {
            execSQL(
                """
                INSERT INTO collected_post_meta(postId, title, authorName, updatedAtMillis)
                VALUES(42, '一篇收藏', '原作者', 1000)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DATABASE_NAME, 13, true, MIGRATION_12_13)

        migrated.query("SELECT authorName, avatarUrl, authorUid FROM collected_post_meta WHERE postId = 42").use {
            it.moveToFirst()
            assertEquals("原作者", it.getString(0))
            assertTrue(it.isNull(1))
            assertTrue(it.isNull(2))
        }
        migrated.close()
    }

    /**
     * Null on every existing row, and null is the honest value: v13 had no way to record which
     * threads were on the list, so there is nothing in the file to back-fill from and the first
     * successful walk writes one.
     */
    @Test
    fun `migration 13 to 14 gives remembered threads a place on the list`() {
        helper.createDatabase(DATABASE_NAME, 13).apply {
            execSQL(
                """
                INSERT INTO collected_post_meta(postId, title, authorName, updatedAtMillis)
                VALUES(42, '一篇收藏', '原作者', 1000)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DATABASE_NAME, 14, true, MIGRATION_13_14)

        migrated.query("SELECT title, listedOrder FROM collected_post_meta WHERE postId = 42").use {
            it.moveToFirst()
            assertEquals("一篇收藏", it.getString(0))
            assertTrue(it.isNull(1))
        }
        migrated.close()
    }

    /**
     * The whole ladder at once, which is the only test that runs it the way a device does.
     *
     * Every step above proves its own rung against the schema beside it; none of them proves that
     * the rungs compose — that a column one migration adds is where a later migration's SQL expects
     * it, on a file that has actually climbed the versions in between rather than being created at
     * the version under test. A reader on the oldest still-migratable store upgrades through all
     * eleven in one open, so that is what this runs: real v3 rows in, `runMigrationsAndValidate`
     * against the exported v14 schema out, the oldest data still readable at the top.
     */
    @Test
    fun `a v3 store climbs every migration to v14 with its data intact`() {
        helper.createDatabase(DATABASE_NAME, 3).apply {
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
            execSQL(
                """
                INSERT INTO post_details(postId, title, body, totalPages, loadedPages, cachedAtMillis)
                VALUES(42, 'a cached thread', NULL, 9, 3, 1000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO post_read_marks(postId, lastReadAtMillis, lastSeenCommentCount)
                VALUES(42, 1000, 3)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DATABASE_NAME, 14, true, *NODESEEK_MIGRATIONS)

        migrated.query("SELECT title, isBlocked, isAwarded FROM posts WHERE postId = 42").use {
            it.moveToFirst()
            assertEquals("a cached post", it.getString(0))
            assertEquals(0, it.getInt(1))
            assertEquals(0, it.getInt(2))
        }
        migrated.query("SELECT title, firstLoadedPage, collected FROM post_details WHERE postId = 42").use {
            it.moveToFirst()
            assertEquals("a cached thread", it.getString(0))
            assertEquals(1, it.getInt(1))
            assertTrue(it.isNull(2))
        }
        migrated.query("SELECT lastSeenCommentCount, title FROM post_read_marks WHERE postId = 42").use {
            it.moveToFirst()
            assertEquals(3, it.getInt(0))
            assertTrue(it.isNull(1))
        }
        migrated.close()
    }

    private companion object {
        const val DATABASE_NAME = "profile-migration-test"
    }
}
