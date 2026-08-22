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
 * 测评报告 round-trips through the store, and a value it cannot read falls back to the card.
 *
 * The stored form is the enum name, so a renamed
 * constant would otherwise hand the renderer a format it has no case for. Defaulting keeps that a
 * report drawn the ordinary way rather than a crash on the first post that carries one.
 */
class ReportFormatSettingTest {
    @Test
    fun `an empty store draws the adapted card`() =
        runTest {
            assertEquals(ReportFormat.ADAPTED, storedFormat(null))
        }

    @Test
    fun `the chosen format survives a round trip`() =
        runTest {
            val repository = repository()
            repository.setReportFormat(ReportFormat.SOURCE)
            assertEquals(ReportFormat.SOURCE, repository.settings.first().reportFormat)

            repository.setReportFormat(ReportFormat.ADAPTED)
            assertEquals(ReportFormat.ADAPTED, repository.settings.first().reportFormat)
        }

    @Test
    fun `a value this build does not know falls back to the default`() =
        runTest {
            assertEquals(ReportFormat.ADAPTED, storedFormat("TERMINAL"))
        }

    private suspend fun CoroutineScope.storedFormat(raw: String?): ReportFormat {
        val repository = repository()
        if (raw != null) dataStore.edit { it[KEY_REPORT_FORMAT] = raw }
        return repository.settings.first().reportFormat
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
        val KEY_REPORT_FORMAT = stringPreferencesKey("report_format")
    }
}
