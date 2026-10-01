package io.github.nodyssey.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.core.report.QualityReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.IOException

/**
 * One report set aside for 测评对比, with where it was found.
 *
 * The report itself rather than a pointer to the floor: comparing should not need the network, and
 * a floor can be edited or deleted after the reader picked its report out.
 */
@Serializable
data class ReportCompareEntry(
    val report: QualityReport,
    /** Null when the report was not in a thread — a direct message, a space readme. */
    val postId: Long? = null,
    val threadTitle: String? = null,
    /** The floor as the thread labels it, `#3`; null for a report outside any floor. */
    val floor: String? = null,
) {
    /**
     * Whether [other] is this same report from this same place.
     *
     * The whole report takes part, not only its title: one floor often carries two reports from the
     * same script (a before-and-after, two machines), and those are two entries.
     */
    fun isSameAs(other: ReportCompareEntry): Boolean =
        postId == other.postId && floor == other.floor && report == other.report
}

/**
 * 测评对比's basket: the reports a reader has set aside, oldest first, at most [MAX_ENTRIES].
 *
 * Kept as one JSON value in the settings store — a handful of reports, read and written whole —
 * rather than a table: nothing queries into it, and the store already survives a restart. Made by
 * [SettingsRepository], which owns that store; two `DataStore`s over one file is an error.
 */
class ReportCompareStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val entries: Flow<List<ReportCompareEntry>> =
        dataStore.data
            .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
            .map { decode(it[KEY]) }
            .distinctUntilChanged()

    /**
     * Puts [entry] at the end of the basket; a report already in it stays where it is.
     *
     * A full basket lets its oldest entry go rather than refusing — the reader adding a report is
     * asking to compare *it*, and the one set aside longest ago is the one least likely to matter.
     */
    suspend fun add(entry: ReportCompareEntry) {
        dataStore.edit { preferences ->
            val current = decode(preferences[KEY])
            if (current.any { it.isSameAs(entry) }) return@edit
            preferences[KEY] = json.encodeToString((current + entry).takeLast(MAX_ENTRIES))
        }
    }

    suspend fun remove(entry: ReportCompareEntry) {
        dataStore.edit { preferences ->
            preferences[KEY] = json.encodeToString(decode(preferences[KEY]).filterNot { it.isSameAs(entry) })
        }
    }

    private fun decode(encoded: String?): List<ReportCompareEntry> {
        if (encoded.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<ReportCompareEntry>>(encoded) }.getOrNull().orEmpty()
    }

    companion object {
        /** Three are compared at once; twice that is room to choose without becoming a library. */
        const val MAX_ENTRIES = 6

        private val KEY = stringPreferencesKey("report_compare_basket")
    }
}
