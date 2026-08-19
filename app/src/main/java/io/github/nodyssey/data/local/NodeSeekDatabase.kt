package io.github.nodyssey.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The offline-first single source of truth.
 *
 * Offline-capable screens read their durable content from here; for those flows the network layer's
 * job is to write into the database. That inversion is the whole point of phase two — before it,
 * going back to a list meant re-requesting it and aeroplane mode meant a blank screen.
 */
@Database(
    entities = [
        BoardEntity::class,
        PostEntity::class,
        FeedPositionEntity::class,
        FeedRemoteKeyEntity::class,
        PostDetailEntity::class,
        CommentEntity::class,
        ReadMarkEntity::class,
        ReadingPositionEntity::class,
        CacheSessionEntity::class,
        SelfProfileEntity::class,
        OfflineThreadEntity::class,
        OfflineCommentEntity::class,
        OfflineImageEntity::class,
        CollectedPostMetaEntity::class,
    ],
    version = 12,
    exportSchema = true,
)
@TypeConverters(RichContentConverters::class)
abstract class NodeSeekDatabase : RoomDatabase() {
    abstract fun boardDao(): BoardDao

    abstract fun feedDao(): FeedDao

    abstract fun postDetailDao(): PostDetailDao

    abstract fun readMarkDao(): ReadMarkDao

    abstract fun readingPositionDao(): ReadingPositionDao

    abstract fun cacheSessionDao(): CacheSessionDao

    abstract fun profileDao(): ProfileDao

    abstract fun offlineDao(): OfflineDao

    abstract fun collectedPostMetaDao(): CollectedPostMetaDao
}

/** Adds the signed-in profile cache without disturbing existing posts or read marks. */
internal val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `self_profile` (
                    `sessionFingerprint` INTEGER NOT NULL,
                    `uid` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `avatarUrl` TEXT NOT NULL,
                    `rank` INTEGER,
                    `createdAt` TEXT,
                    `chickenCount` INTEGER,
                    `starCount` INTEGER,
                    `streakDays` INTEGER,
                    `bio` TEXT,
                    `readme` TEXT,
                    `topicCount` INTEGER,
                    `commentCount` INTEGER,
                    `cachedAtMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`sessionFingerprint`)
                )
                """.trimIndent(),
            )
        }
    }

/**
 * Gives a cached thread a start as well as an end, so a jump can load page 12 alone.
 *
 * Every v4 row held a prefix that began at page 1, which is exactly the default, and `loadedPages`
 * counted that prefix — the same number as the window's last page. So the whole upgrade is one
 * column, and no stored thread has to be re-fetched to be readable.
 */
internal val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `post_details` ADD COLUMN `firstLoadedPage` INTEGER NOT NULL DEFAULT 1",
            )
        }
    }

/**
 * Records the site's block mark on a cached row.
 *
 * Defaulting to 0 is right for every stored row: they were parsed before the mark was read, so
 * nothing is known about them, and "not blocked" is what the app did with them anyway. The next
 * refresh writes the truth. Comments need no migration — their column is a serialized `PostContent`
 * and the new field has a default.
 */
internal val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `posts` ADD COLUMN `isBlocked` INTEGER NOT NULL DEFAULT 0")
        }
    }

/**
 * Gives read marks enough of a thread to list it as browsing history, and cached threads a place to
 * remember whether they are collected.
 *
 * Two tables in one migration because they ship together, and because both are the same kind of
 * change: a fact the app never used to record. Every column is nullable with no default, which is
 * the whole point — a v6 row genuinely does not know its own title or collection state, and writing
 * `''` or `0` would dress that ignorance up as an answer. The screens read null as "ask the server"
 * rather than as "no".
 */
internal val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `post_read_marks` ADD COLUMN `title` TEXT")
            db.execSQL("ALTER TABLE `post_read_marks` ADD COLUMN `authorName` TEXT")
            db.execSQL("ALTER TABLE `post_read_marks` ADD COLUMN `authorUid` INTEGER")
            db.execSQL("ALTER TABLE `post_read_marks` ADD COLUMN `categoryTitle` TEXT")
            db.execSQL("ALTER TABLE `post_read_marks` ADD COLUMN `commentCount` INTEGER")
            db.execSQL("ALTER TABLE `post_details` ADD COLUMN `collected` INTEGER")
            db.execSQL("ALTER TABLE `post_details` ADD COLUMN `collectionCount` INTEGER")
        }
    }

/**
 * Gives every thread a place to remember which page and floor it was left on.
 *
 * A new table and nothing else: no existing row means anything different afterwards. Nobody is
 * upgrading with places to carry over either — the bookmark shipped in this same release, so the
 * only stores that ever held one are the debug builds this branch was tested on.
 */
internal val MIGRATION_7_8 =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `post_reading_positions` (
                    `postId` INTEGER NOT NULL,
                    `page` INTEGER NOT NULL,
                    `floor` TEXT,
                    `updatedAtMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`postId`)
                )
                """.trimIndent(),
            )
        }
    }

/**
 * Teaches a stored feed which page each row came from, and how many pages the site offered.
 *
 * Both defaults describe a v8 store honestly rather than conveniently: every stored feed began at
 * page 1 and was only ever appended to, so `page = 1` is wrong for the tail and right for the head —
 * and the tail corrects itself on the next refresh, which clears the feed and rewrites every row.
 * `totalPages = 1` is the same claim the pager makes when it offers no other page, so 首页翻页栏
 * stays hidden on an unrefreshed store instead of drawing "第 1 / 1 页" over a list of hundreds.
 */
internal val MIGRATION_8_9 =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `feed_positions` ADD COLUMN `page` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `feed_remote_keys` ADD COLUMN `totalPages` INTEGER NOT NULL DEFAULT 1")
        }
    }

/**
 * Gives stored rows somewhere to remember 推荐阅读.
 *
 * The two defaults differ because the two columns answer different questions. A list row is rewritten
 * on the next refresh, so `0` is a value that corrects itself and keeps the column non-null. A cached
 * thread may never be re-fetched from page 1, so its column stays nullable and starts null: "no page
 * has said" rather than "not 加精", which is the same rule `collected` already follows.
 */
internal val MIGRATION_9_10 =
    object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `posts` ADD COLUMN `isAwarded` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `post_details` ADD COLUMN `isAwarded` INTEGER DEFAULT NULL")
        }
    }

/**
 * Gives 离线阅读 somewhere to put the threads it downloads.
 *
 * Three new tables and not one existing column touched, which is the point: the download engine is
 * a second store beside the reader's cache, not a flag on it — see `OfflineEntities.kt` for why a
 * pin bit on `post_details` could not have held. Nothing to back-fill either; a device upgrading
 * has downloaded nothing, and that is exactly what an empty table says.
 */
internal val MIGRATION_10_11 =
    object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `offline_threads` (
                    `postId` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `body` TEXT,
                    `totalPages` INTEGER NOT NULL,
                    `storedCommentCount` INTEGER NOT NULL,
                    `remoteCommentCount` INTEGER,
                    `status` INTEGER NOT NULL,
                    `progress` REAL,
                    `failure` INTEGER,
                    `textBytes` INTEGER NOT NULL,
                    `collected` INTEGER,
                    `collectionCount` INTEGER,
                    `isAwarded` INTEGER,
                    `queuedAtMillis` INTEGER NOT NULL,
                    `downloadedAtMillis` INTEGER,
                    PRIMARY KEY(`postId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `offline_comments` (
                    `postId` INTEGER NOT NULL,
                    `page` INTEGER NOT NULL,
                    `position` INTEGER NOT NULL,
                    `content` TEXT NOT NULL,
                    PRIMARY KEY(`postId`, `page`, `position`),
                    FOREIGN KEY(`postId`) REFERENCES `offline_threads`(`postId`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `offline_images` (
                    `postId` INTEGER NOT NULL,
                    `url` TEXT NOT NULL,
                    `fileName` TEXT NOT NULL,
                    `bytes` INTEGER NOT NULL,
                    PRIMARY KEY(`postId`, `url`),
                    FOREIGN KEY(`postId`) REFERENCES `offline_threads`(`postId`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_offline_images_fileName` ON `offline_images` (`fileName`)")
        }
    }

/**
 * Gives 收藏 somewhere to keep what the site has already said about a collected thread.
 *
 * One table, every column nullable, nothing back-filled — and the empty start is honest rather than
 * merely convenient: on the device this upgrades, the app has been told plenty about these threads
 * and never wrote any of it down. The rows fill in as they are learned again, which for a thread the
 * reader opens or downloads is immediately, and for one they never touch again is never — the same
 * bare row the list already draws today.
 */
internal val MIGRATION_11_12 =
    object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `collected_post_meta` (
                    `postId` INTEGER NOT NULL,
                    `title` TEXT,
                    `categoryTitle` TEXT,
                    `categorySlug` TEXT,
                    `authorName` TEXT,
                    `commentCount` INTEGER,
                    `createdAtText` TEXT,
                    `updatedAtMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`postId`)
                )
                """.trimIndent(),
            )
        }
    }

/**
 * Every migration this schema has, in order — the list `createNodeSeekDatabase` opens the file with.
 *
 * Named here, beside the migrations themselves, rather than at the builder: which upgrades are known
 * is a fact about the schema, and leaving one out is how a device with real content in it takes the
 * destructive fallback instead.
 */
internal val NODESEEK_MIGRATIONS = arrayOf(
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
)
