package io.github.nodyssey.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 接收 dev 版更新 before anyone has touched it, which is the setting a test build's whole update check
 * hangs off.
 *
 * The default is not a constant: a phone running `1.2.9-dev.3` with the switch off checks
 * `stable.json`, whose newest entry is `1.2.8` — older than what is installed — so the honest answer
 * is 已是最新, and it stays that answer through `dev.4`, `dev.5` and every test build after, until a
 * release finally passes `1.2.9`. That is the bug this default closes, and the tests below pin both
 * halves of it: a test build follows the dev channel on its own, and saying no still means no.
 */
class UpdateDevChannelDefaultTest {
    @Test
    fun `a release build only hears about releases`() =
        runTest {
            val repository = repository(devChannelDefault = false)

            assertEquals(false, repository.settings.first().updateDevChannel)
            assertEquals(false, repository.devChannelEnabled())
        }

    @Test
    fun `a test build follows the dev channel without being asked`() =
        runTest {
            val repository = repository(devChannelDefault = true)

            assertEquals(true, repository.settings.first().updateDevChannel)
            assertEquals(true, repository.devChannelEnabled())
        }

    /**
     * The switch still wins.
     *
     * Turning it off on a test build is a real answer — "leave me on this one, tell me when a release
     * passes it" — and a default that overrode it would make the switch look broken.
     */
    @Test
    fun `turning it off on a test build sticks`() =
        runTest {
            val repository = repository(devChannelDefault = true)

            repository.setUpdateDevChannel(false)

            assertEquals(false, repository.settings.first().updateDevChannel)
            assertEquals(false, repository.devChannelEnabled())
        }

    @Test
    fun `turning it on on a release build sticks`() =
        runTest {
            val repository = repository(devChannelDefault = false)

            repository.setUpdateDevChannel(true)

            assertEquals(true, repository.settings.first().updateDevChannel)
            assertEquals(true, repository.devChannelEnabled())
        }

    private fun CoroutineScope.repository(devChannelDefault: Boolean): SettingsRepository {
        val directory =
            Files.createTempDirectory("nodyssey-settings").toFile().apply { deleteOnExit() }
        val dataStore =
            PreferenceDataStoreFactory.create(scope = this) {
                File(directory, "settings.preferences_pb")
            }
        return SettingsRepository(dataStore, devChannelDefault = devChannelDefault)
    }
}
