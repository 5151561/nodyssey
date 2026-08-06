package io.github.nodyssey.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
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
        CacheSessionEntity::class,
        SelfProfileEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(RichContentConverters::class)
abstract class NodeSeekDatabase : RoomDatabase() {
    abstract fun boardDao(): BoardDao

    abstract fun feedDao(): FeedDao

    abstract fun postDetailDao(): PostDetailDao

    abstract fun readMarkDao(): ReadMarkDao

    abstract fun cacheSessionDao(): CacheSessionDao

    abstract fun profileDao(): ProfileDao

    companion object {
        fun create(context: Context): NodeSeekDatabase =
            Room
                .databaseBuilder(context, NodeSeekDatabase::class.java, "nodeseek.db")
                // Known upgrades preserve local state explicitly. The fallback remains for unknown
                // legacy versions whose downloaded content can be rebuilt; schemas stay checked in.
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
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
