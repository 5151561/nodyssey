package io.github.nsreader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The offline-first single source of truth.
 *
 * Every screen reads from here; the network layer's only job is to write into it. That inversion is
 * the whole point of phase two — before it, going back to a list meant re-requesting it and
 * aeroplane mode meant a blank screen.
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
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(RichContentConverters::class)
abstract class NodeSeekDatabase : RoomDatabase() {
    abstract fun boardDao(): BoardDao

    abstract fun feedDao(): FeedDao

    abstract fun postDetailDao(): PostDetailDao

    abstract fun readMarkDao(): ReadMarkDao

    abstract fun cacheSessionDao(): CacheSessionDao

    companion object {
        fun create(context: Context): NodeSeekDatabase =
            Room
                .databaseBuilder(context, NodeSeekDatabase::class.java, "nodeseek.db")
                // Every table here is a cache of a public page. Nothing is user-authored, so a
                // schema change is cheaper to re-download than to migrate — but the checked-in
                // schema files still make the change visible in review.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
