package io.github.nodyssey.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 表情统一缩限 and the size it clamps to, through the store.
 *
 * The size is clamped on the way in *and* on the way out, which is the part worth a test: a number
 * outside the slider's range would otherwise leave every sticker at a size no control on the screen
 * can walk back — the same reasoning 浏览历史 upper bound is guarded by.
 */
class StickerSizeSettingTest {
    @Test
    fun `an empty store keeps the inline box every earlier build drew`() =
        runTest {
            val settings = repository().settings.first()

            assertTrue("统一缩限 must default on", settings.stickerUniformSize)
            assertEquals(SettingsRepository.MIN_STICKER_SIZE_SP, settings.stickerSize)
        }

    @Test
    fun `both halves of the setting survive a round trip`() =
        runTest {
            val repository = repository()

            repository.setStickerUniformSize(false)
            repository.setStickerSize(48)

            val settings = repository.settings.first()
            assertEquals(false, settings.stickerUniformSize)
            assertEquals(48, settings.stickerSize)
        }

    @Test
    fun `a size beyond the slider is pulled back to its ends`() =
        runTest {
            val repository = repository()

            repository.setStickerSize(4)
            assertEquals(SettingsRepository.MIN_STICKER_SIZE_SP, repository.settings.first().stickerSize)

            repository.setStickerSize(400)
            assertEquals(SettingsRepository.MAX_STICKER_SIZE_SP, repository.settings.first().stickerSize)
        }

    /** A size some other build wrote is read back inside this build's range, not obeyed. */
    @Test
    fun `a stored size this build cannot offer is clamped on the way out`() =
        runTest {
            val repository = repository()
            dataStore.edit { it[KEY_STICKER_SIZE] = 512 }

            assertEquals(SettingsRepository.MAX_STICKER_SIZE_SP, repository.settings.first().stickerSize)
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
        val KEY_STICKER_SIZE = intPreferencesKey("sticker_size_sp")
    }
}
