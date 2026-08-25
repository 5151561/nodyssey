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
        // Known upgrades preserve local state explicitly; schemas stay checked in.
        .addMigrations(*NODESEEK_MIGRATIONS)
        // The destructive fallback covers exactly the versions that have no migration — v1 and v2,
        // whose caches can be rebuilt — and downgrades, which only a sideloaded older build causes.
        // Deliberately not the version-blind `fallbackToDestructiveMigration`: that one also
        // swallows every *future* migration somebody forgets to write, silently wiping
        // `offline_threads` — the one store that is not re-downloadable — where a crash at open
        // would have named the missing step before the release shipped.
        .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2)
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        .build()
