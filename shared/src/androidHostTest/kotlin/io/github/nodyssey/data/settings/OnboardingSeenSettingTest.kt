package io.github.nodyssey.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 新手引导 is shown exactly once, and can be asked for again.
 *
 * Both halves matter and neither is obvious from the field. False on an empty store is what shows
 * the guide to an upgrade as well as to a fresh install — the readers reporting 单手模式 as a bug are
 * on builds that predate it, so they are the ones it is for. And the flag going back to false is the
 * whole of 再看一次引导 on 使用帮助; a one-way `markSeen` would have left that page with nothing to do.
 */
class OnboardingSeenSettingTest {
    @Test
    fun `an empty store has not seen the guide`() =
        runTest {
            assertFalse(repository().settings.first().onboardingSeen)
        }

    @Test
    fun `finishing the guide is remembered, and can be undone`() =
        runTest {
            val repository = repository()

            repository.setOnboardingSeen(true)
            assertTrue(repository.settings.first().onboardingSeen)

            repository.setOnboardingSeen(false)
            assertFalse(repository.settings.first().onboardingSeen)
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
