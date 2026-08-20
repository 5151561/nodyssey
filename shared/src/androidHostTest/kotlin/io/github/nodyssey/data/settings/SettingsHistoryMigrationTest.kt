package io.github.nodyssey.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.model.SearchHistoryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Search history written by an older build, read by this one.
 *
 * Decoding a stored row is lossy — the multi-select's board list collapses to its first board and
 * the retired sort labels fold into the two `FeedSort` values — so rows that were distinct on disk can arrive as one
 * entry. The list goes straight into a LazyColumn keyed on [SearchHistoryEntry.key], and a repeated
 * key there is a crash on the next frame, not a cosmetic duplicate: tapping into search with two
 * such rows stored took the app down every time.
 */
class SettingsHistoryMigrationTest {
    @Test
    fun `sorts that no longer exist collapse into one entry`() =
        runTest {
            val history =
                storedHistory(
                    """{"query":"Claude","target":"POSTS","categorySlugs":[],"sort":"LAST_REPLY"}""",
                    """{"query":"Claude","target":"POSTS","categorySlugs":[],"sort":"RELEVANCE"}""",
                )

            assertEquals(listOf("Claude"), history.map(SearchHistoryEntry::query))
        }

    @Test
    fun `multi-board rows sharing a first board collapse into one entry`() =
        runTest {
            val history =
                storedHistory(
                    """{"query":"vps","target":"POSTS","categorySlugs":["daily","tech"],"sort":"RELEVANCE"}""",
                    """{"query":"vps","target":"POSTS","categorySlugs":["daily","expose"],"sort":"RELEVANCE"}""",
                )

            assertEquals(listOf("daily"), history.map(SearchHistoryEntry::categorySlug))
        }

    @Test
    fun `entries that stay distinct are all kept`() =
        runTest {
            val history =
                storedHistory(
                    """{"query":"Claude","target":"POSTS","categorySlugs":[],"sort":"RELEVANCE"}""",
                    """{"query":"Claude","target":"POSTS","categorySlugs":[],"sort":"POST_TIME"}""",
                    """{"query":"Claude","target":"USERS","categorySlugs":[],"sort":"RELEVANCE"}""",
                )

            assertEquals(3, history.size)
            assertEquals(3, history.map(SearchHistoryEntry::key).toSet().size)
        }

    /** Seeds the store with raw rows, the way a build that predates the collapse left them. */
    private suspend fun CoroutineScope.storedHistory(vararg rows: String): List<SearchHistoryEntry> {
        val directory = Files.createTempDirectory("nodyssey-settings").toFile().apply { deleteOnExit() }
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = this) { File(directory, "settings.preferences_pb") }
        dataStore.edit { it[KEY_SEARCH_HISTORY] = rows.joinToString(",", "[", "]") }
        return SettingsRepository(dataStore).settings.first().searchHistory
    }

    private companion object {
        val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history_v3")
    }
}
