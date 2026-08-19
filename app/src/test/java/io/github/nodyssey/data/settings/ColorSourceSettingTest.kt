package io.github.nodyssey.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
 * 配色来源 replaced a bare 动态取色 switch, and the store on an upgrading phone still only has the
 * switch. The migration is the interesting part: a reader who had turned wallpaper colours on has to
 * come back up on wallpaper colours, and everyone else on the brand palette — silently resetting the
 * first group to 品牌色 would look like the setting was thrown away.
 */
class ColorSourceSettingTest {
    @Test
    fun `an empty store uses the brand palette`() =
        runTest {
            assertEquals(ColorSource.BRAND, repository().settings.first().colorSource)
        }

    @Test
    fun `a store written before 配色来源 existed keeps its 动态取色 answer`() =
        runTest {
            val repository = repository()
            dataStore.edit { it[KEY_DYNAMIC_COLOR] = true }
            assertEquals(ColorSource.WALLPAPER, repository.settings.first().colorSource)
        }

    @Test
    fun `the old switch left off still means the brand palette`() =
        runTest {
            val repository = repository()
            dataStore.edit { it[KEY_DYNAMIC_COLOR] = false }
            assertEquals(ColorSource.BRAND, repository.settings.first().colorSource)
        }

    @Test
    fun `an explicit source wins over the old switch`() =
        runTest {
            val repository = repository()
            dataStore.edit { it[KEY_DYNAMIC_COLOR] = true }
            repository.setColorSource(ColorSource.SEED)
            assertEquals(ColorSource.SEED, repository.settings.first().colorSource)
        }

    @Test
    fun `a value this build does not know falls back to the brand palette`() =
        runTest {
            val repository = repository()
            dataStore.edit { it[KEY_COLOR_SOURCE] = "GRADIENT" }
            assertEquals(ColorSource.BRAND, repository.settings.first().colorSource)
        }

    @Test
    fun `the seed survives a round trip and outlives a switch away from 自选`() =
        runTest {
            val repository = repository()
            repository.setColorSource(ColorSource.SEED)
            repository.setSeedColor(0xFF8A5100.toInt())
            assertEquals(0xFF8A5100.toInt(), repository.settings.first().seedColor)

            // Going back to 品牌色 must not forget it: returning to 自选 should land on the same
            // colour rather than on the default.
            repository.setColorSource(ColorSource.BRAND)
            assertEquals(0xFF8A5100.toInt(), repository.settings.first().seedColor)
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
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_COLOR_SOURCE = stringPreferencesKey("color_source")
    }
}
