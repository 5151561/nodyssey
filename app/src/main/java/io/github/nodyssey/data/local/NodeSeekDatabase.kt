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
    version = 4,
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
                .addMigrations(MIGRATION_3_4)
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
