package io.github.nodyssey.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The 回复主题 / @我 switches written by an older build, read by this one as the one 互动 switch.
 *
 * The two used to post one system notification each, which for a reply that opens with `@name #7`
 * was two notifications about one comment. Merging them is what fixed that, and a device upgrading
 * into it must not have the switch it left on come back off.
 */
class NotifyInteractionsMigrationTest {
    @Test
    fun `either old switch on means 互动 is on`() =
        runTest {
            assertTrue(notifyInteractions(mentions = true, replies = true))
            assertTrue(notifyInteractions(mentions = true, replies = false))
            assertTrue(notifyInteractions(mentions = false, replies = true))
        }

    @Test
    fun `both off stays off`() =
        runTest {
            assertFalse(notifyInteractions(mentions = false, replies = false))
        }

    @Test
    fun `an untouched store starts on`() =
        runTest {
            assertTrue(notifyInteractions(mentions = null, replies = null))
        }

    private suspend fun CoroutineScope.notifyInteractions(
        mentions: Boolean?,
        replies: Boolean?,
    ): Boolean {
        val directory = Files.createTempDirectory("nodyssey-settings").toFile().apply { deleteOnExit() }
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = this) { File(directory, "settings.preferences_pb") }
        dataStore.edit { preferences ->
            mentions?.let { preferences[KEY_NOTIFY_MENTIONS] = it }
            replies?.let { preferences[KEY_NOTIFY_REPLIES] = it }
        }
        return SettingsRepository(dataStore).settings.first().notifyInteractions
    }

    private companion object {
        val KEY_NOTIFY_MENTIONS = booleanPreferencesKey("notify_mentions")
        val KEY_NOTIFY_REPLIES = booleanPreferencesKey("notify_replies")
    }
}
