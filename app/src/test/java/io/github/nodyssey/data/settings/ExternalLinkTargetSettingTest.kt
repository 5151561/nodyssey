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
 * 站外链接 round-trips through the store, and a value it cannot read falls back to the default.
 *
 * The stored form is the enum name, so a build that retires or renames a constant would otherwise
 * hand the app a link target it has no case for. Defaulting is what keeps that a no-op rather than
 * a crash the first time somebody taps a link.
 */
class ExternalLinkTargetSettingTest {
    @Test
    fun `an empty store opens links in the app`() =
        runTest {
            assertEquals(ExternalLinkTarget.CUSTOM_TAB, storedTarget(null))
        }

    @Test
    fun `the chosen target survives a round trip`() =
        runTest {
            val repository = repository()
            repository.setExternalLinkTarget(ExternalLinkTarget.BROWSER)
            assertEquals(ExternalLinkTarget.BROWSER, repository.settings.first().externalLinkTarget)

            repository.setExternalLinkTarget(ExternalLinkTarget.CUSTOM_TAB)
            assertEquals(
                ExternalLinkTarget.CUSTOM_TAB,
                repository.settings.first().externalLinkTarget,
            )
        }

    @Test
    fun `a value this build does not know falls back to the default`() =
        runTest {
            assertEquals(ExternalLinkTarget.CUSTOM_TAB, storedTarget("IN_APP_WEBVIEW"))
        }

    private suspend fun CoroutineScope.storedTarget(raw: String?): ExternalLinkTarget {
        val repository = repository()
        if (raw != null) dataStore.edit { it[KEY_EXTERNAL_LINK_TARGET] = raw }
        return repository.settings.first().externalLinkTarget
    }

    private lateinit var dataStore: DataStore<Preferences>

    private fun CoroutineScope.repository(): SettingsRepository {
        val directory =
            Files.createTempDirectory("nodyssey-settings").toFile().apply { deleteOnExit() }
        dataStore =
            PreferenceDataStoreFactory.create(scope = this) {
                File(directory, "settings.preferences_pb")
            }
        return SettingsRepository(dataStore)
    }

    private companion object {
        val KEY_EXTERNAL_LINK_TARGET = stringPreferencesKey("external_link_target")
    }
}
