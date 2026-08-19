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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 配色来源 has been renamed twice under the same store.
 *
 * It started as a bare 动态取色 switch, became a three-way choice written as `BRAND`/`WALLPAPER`/`SEED`,
 * and is now `PRESET`/`WALLPAPER`/`CUSTOM` — the first and last renamed when 主题 grew a preset grid
 * and the hand-tuned brand palette retired. Both migrations are here because both have the same
 * failure mode: a value this build does not recognise silently resets an upgrading phone to the
 * default, which looks exactly like the setting was thrown away.
 */
class ColorSourceSettingTest {
    @Test
    fun `an empty store uses the presets`() =
        runTest {
            assertEquals(ColorSource.PRESET, repository().settings.first().colorSource)
        }

    @Test
    fun `a store written before 配色来源 existed keeps its 动态取色 answer`() =
        runTest {
            val repository = repository()
            dataStore.edit { it[KEY_DYNAMIC_COLOR] = true }
            assertEquals(ColorSource.WALLPAPER, repository.settings.first().colorSource)
        }

    @Test
    fun `the old switch left off still means the app's own colours`() =
        runTest {
            val repository = repository()
            dataStore.edit { it[KEY_DYNAMIC_COLOR] = false }
            assertEquals(ColorSource.PRESET, repository.settings.first().colorSource)
        }

    @Test
    fun `品牌色 written by an older build reads as 预设`() =
        runTest {
            val repository = repository()
            dataStore.edit { it[KEY_COLOR_SOURCE] = "BRAND" }
            assertEquals(ColorSource.PRESET, repository.settings.first().colorSource)
        }

    @Test
    fun `自选 written by an older build reads as 自定义`() =
        runTest {
            val repository = repository()
            dataStore.edit { it[KEY_COLOR_SOURCE] = "SEED" }
            assertEquals(ColorSource.CUSTOM, repository.settings.first().colorSource)
        }

    @Test
    fun `an explicit source wins over the old switch`() =
        runTest {
            val repository = repository()
            dataStore.edit { it[KEY_DYNAMIC_COLOR] = true }
            repository.setColorSource(ColorSource.CUSTOM)
            assertEquals(ColorSource.CUSTOM, repository.settings.first().colorSource)
        }

    @Test
    fun `a value this build does not know falls back to the presets`() =
        runTest {
            val repository = repository()
            dataStore.edit { it[KEY_COLOR_SOURCE] = "GRADIENT" }
            assertEquals(ColorSource.PRESET, repository.settings.first().colorSource)
        }

    @Test
    fun `each source keeps its own answer`() =
        runTest {
            val repository = repository()
            repository.setPresetId(PRESET_ID)
            repository.setSeedColor(CUSTOM)
            repository.setWallpaperSeed(WALLPAPER)

            // The point of three fields rather than one: tapping a preset to see what it looks like
            // must not overwrite the colour that took a minute in the picker to arrive at.
            repository.setColorSource(ColorSource.PRESET)
            repository.settings.first().let {
                assertEquals(PRESET_ID, it.presetId)
                assertEquals(CUSTOM, it.seedColor)
                assertEquals(WALLPAPER, it.wallpaperSeed)
            }
        }

    @Test
    fun `saving the same colour twice renames it rather than duplicating it`() =
        runTest {
            val repository = repository()
            repository.saveTheme("海雾", CUSTOM)
            repository.saveTheme("夜樱", 0xFF8A4A66.toInt())
            repository.saveTheme("晨雾", CUSTOM)

            val saved = repository.settings.first().savedThemes
            assertEquals(listOf("夜樱", "晨雾"), saved.map { it.name })
            assertEquals(listOf(0xFF8A4A66.toInt(), CUSTOM), saved.map { it.color })
        }

    @Test
    fun `deleting a saved theme leaves the others`() =
        runTest {
            val repository = repository()
            repository.saveTheme("海雾", CUSTOM)
            repository.saveTheme("夜樱", 0xFF8A4A66.toInt())
            repository.deleteSavedTheme(CUSTOM)
            assertEquals(listOf("夜樱"), repository.settings.first().savedThemes.map { it.name })
        }

    @Test
    fun `the saved list stops growing at its cap`() =
        runTest {
            val repository = repository()
            repeat(SettingsRepository.MAX_SAVED_THEMES + 4) { index ->
                repository.saveTheme("主题 $index", 0xFF000000.toInt() or index)
            }
            val saved = repository.settings.first().savedThemes
            assertEquals(SettingsRepository.MAX_SAVED_THEMES, saved.size)
            // The cap drops from the front, so the newest colour a reader saved is always still there.
            assertTrue(saved.last().name.endsWith("${SettingsRepository.MAX_SAVED_THEMES + 3}"))
        }

    @Test
    fun `a corrupt saved-theme list reads as none rather than taking the store down`() =
        runTest {
            val repository = repository()
            dataStore.edit { it[KEY_SAVED_THEMES] = "not json" }
            assertEquals(emptyList<SavedTheme>(), repository.settings.first().savedThemes)
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
        val KEY_SAVED_THEMES = stringPreferencesKey("saved_themes")

        const val PRESET_ID = "miku"
        const val CUSTOM = 0xFF2F6D8C.toInt()
        const val WALLPAPER = 0xFF7C6A50.toInt()
    }
}
