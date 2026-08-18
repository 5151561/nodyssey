package io.github.nodyssey.platform

import android.content.Context
import androidx.room.Room
import io.github.nodyssey.data.local.NODESEEK_MIGRATIONS
import io.github.nodyssey.data.local.NodeSeekDatabase

/**
 * Opens the app's database file.
 *
 * Separate from [NodeSeekDatabase] itself because opening it is where the `Context` is needed and
 * where the file's name is decided — both facts about this platform — while the schema, the DAOs and
 * the migrations are facts about the app.
 *
 * `nodeseek.db` is the file already on every installed device; the name is not a choice left open.
 */
fun createNodeSeekDatabase(context: Context): NodeSeekDatabase =
    Room
        .databaseBuilder(context, NodeSeekDatabase::class.java, "nodeseek.db")
        // Known upgrades preserve local state explicitly. The fallback remains for unknown legacy
        // versions whose downloaded content can be rebuilt; schemas stay checked in.
        .addMigrations(*NODESEEK_MIGRATIONS)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
