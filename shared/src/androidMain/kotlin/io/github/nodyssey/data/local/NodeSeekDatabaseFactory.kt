package io.github.nodyssey.data.local

import android.content.Context
import androidx.room.Room

/**
 * Opens the app's database file.
 *
 * Separate from [NodeSeekDatabase] itself because opening it is where the `Context` is needed and
 * where the file's name is decided — both facts about this platform — while the schema, the DAOs and
 * the migrations are facts about the app.
 *
 * `nodeseek.db` is the file already on every installed device; the name is not a choice left open.
 *
 * **The driver is deliberately unstated.** `Room.databaseBuilder(context, …)` defaults to Android's
 * own SQLite, which is what every installed copy of this app is already running on. Naming
 * `BundledSQLiteDriver` here — the driver the Apple side has no choice about — would swap the SQLite
 * implementation under a live database file, which is a behaviour change and not a build detail.
 */
fun createNodeSeekDatabase(context: Context): NodeSeekDatabase =
    Room
        .databaseBuilder<NodeSeekDatabase>(context, "nodeseek.db")
        // Known upgrades preserve local state explicitly. The fallback remains for unknown legacy
        // versions whose downloaded content can be rebuilt; schemas stay checked in.
        .addMigrations(*NODESEEK_MIGRATIONS)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
