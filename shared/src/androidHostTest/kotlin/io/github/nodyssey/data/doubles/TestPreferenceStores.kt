package io.github.nodyssey.data

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.rules.ExternalResource
import java.io.File
import java.nio.file.Files

/**
 * A real `DataStore` on a throwaway file, with no Android underneath it.
 *
 * The real store rather than an interface fake, for the same reason `inMemoryDatabase` uses real
 * Room: what is worth testing about a settings class is its *encoding* — which key holds what, what
 * a missing value falls back to, what a migration rewrites — and a fake would only prove the fake
 * encodes correctly.
 *
 * No `Context` and therefore no Robolectric, which is the other half of the point.
 * `preferencesDataStore` — the `Context` extension these stores used to be opened with — is also a
 * *process singleton keyed by file name*, so every test in a class shared one file and each one had
 * to write whatever it was about to read in case an earlier test had left something there. A fresh
 * directory per test ends that: a test that reads before writing now reads an empty store, which is
 * what it was always trying to say.
 *
 * A rule rather than a function because the store owns a collector that has to be cancelled, and a
 * JUnit rule is the one thing that runs per test method without every test having to remember.
 *
 * @param name the file's name; it appears in the temporary directory and nowhere else, so it is for
 * whoever is looking at a failure rather than for the code.
 * @param migrations run on first read, exactly as the app's own store would run them.
 */
class PreferenceStoreScope(
    private val name: String = "test",
    private val migrations: List<DataMigration<Preferences>> = emptyList(),
) : ExternalResource() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val dataStore: DataStore<Preferences> by lazy {
        val directory = Files.createTempDirectory("nodyssey-$name").toFile().apply { deleteOnExit() }
        PreferenceDataStoreFactory.create(scope = scope, migrations = migrations) {
            File(directory, "$name.preferences_pb")
        }
    }

    override fun after() {
        scope.cancel()
    }
}

/**
 * The same store as a plain value, for a test that already has a scope to hang it on.
 *
 * [scope] should be a test's `backgroundScope`, so the store's collector is cancelled with the test.
 */
internal fun testPreferenceStore(
    scope: CoroutineScope,
    name: String = "test",
    migrations: List<DataMigration<Preferences>> = emptyList(),
): DataStore<Preferences> {
    val directory = Files.createTempDirectory("nodyssey-$name").toFile().apply { deleteOnExit() }
    return PreferenceDataStoreFactory.create(scope = scope, migrations = migrations) {
        File(directory, "$name.preferences_pb")
    }
}
