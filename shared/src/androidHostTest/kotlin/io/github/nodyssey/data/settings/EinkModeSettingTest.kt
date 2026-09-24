package io.github.nodyssey.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Asking for 墨水屏模式 does not disturb 明暗.
 *
 * 墨水屏模式 forces the app light while it is on, and the
 * cheap way to do that would have been to write LIGHT into [UserSettings.themeMode] — which would
 * silently overwrite an answer the reader gave, and leave them light after they switched the mode
 * back off. The forcing happens in `NodysseyRoot` instead, so the stored 明暗 has to survive
 * untouched; if some later change moves it back into the store, this is what says so.
 */
class EinkModeSettingTest {
    @Test
    fun `it leaves 明暗 exactly where the reader left it`() =
        runTest {
            val repository = repository()
            repository.setThemeMode(ThemeMode.DARK)

            repository.setEinkMode(true)

            assertEquals(ThemeMode.DARK, repository.settings.first().themeMode)
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
}
