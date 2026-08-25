package io.github.nodyssey.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 语言, through the store.
 *
 * The stored value is an enum *name* and the platform is told about it at every launch, so a name
 * that stops decoding does not fail loudly — it silently puts the app back on the device's language
 * and takes the reader's choice with it. That is what the round trip below is guarding.
 */
class AppLanguageSettingTest {
    @Test
    fun `a fresh store follows the system`() =
        runTest {
            assertEquals(AppLanguage.SYSTEM, storedLanguage())
        }

    @Test
    fun `each language survives the round trip`() =
        runTest {
            for (language in AppLanguage.entries) {
                assertEquals(language, storedLanguage(written = language))
            }
        }

    /** A build that predates 语言 wrote nothing, and a bad write is the same case. */
    @Test
    fun `an unreadable stored name falls back to the system`() =
        runTest {
            assertEquals(AppLanguage.SYSTEM, storedLanguage(raw = "TRADITIONAL"))
        }

    private suspend fun CoroutineScope.storedLanguage(
        written: AppLanguage? = null,
        raw: String? = null,
    ): AppLanguage {
        val directory = Files.createTempDirectory("nodyssey-language").toFile().apply { deleteOnExit() }
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = this) { File(directory, "settings.preferences_pb") }
        val repository = SettingsRepository(dataStore)
        raw?.let { name -> dataStore.edit { it[KEY_APP_LANGUAGE] = name } }
        written?.let { repository.setAppLanguage(it) }
        return repository.settings.first().appLanguage
    }

    private companion object {
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
    }
}
