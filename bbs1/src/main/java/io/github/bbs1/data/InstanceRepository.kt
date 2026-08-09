package io.github.bbs1.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.bbs1.model.ForumInstance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID

/**
 * The saved sites and which one is current.
 *
 * @property instances Every site the user added, in the order they added them.
 * @property currentId The selected site's [ForumInstance.id], or null before the first add.
 */
data class InstancesSnapshot(
    val instances: List<ForumInstance> = emptyList(),
    val currentId: String? = null,
) {
    val current: ForumInstance? get() = instances.firstOrNull { it.id == currentId }
}

/**
 * The single source of truth for the user's list of sites.
 *
 * Everything that shows or switches a site collects [snapshot]; nothing keeps its own copy. The
 * whole list rides in one preferences key as JSON — it has a handful of entries and is always read
 * whole, which is not a job for a database.
 */
class InstanceRepository(
    private val dataStore: DataStore<Preferences>,
    /** Injectable so tests get stable ids; production takes the default. */
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val json = Json { ignoreUnknownKeys = true }

    val snapshot: Flow<InstancesSnapshot> = dataStore.data
        // A corrupt or unreadable store must not take the app down; fall back to an empty list.
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { preferences ->
            InstancesSnapshot(
                instances = decode(preferences[KEY_INSTANCES]),
                currentId = preferences[KEY_CURRENT_ID],
            )
        }

    /**
     * Adds a site and makes it current. [baseUrl] must already be normalized — the UI validates with
     * [normalizeInstanceUrl] before calling. Adding an origin that is already in the list selects
     * the existing entry instead of duplicating it.
     */
    suspend fun add(baseUrl: String, name: String?) {
        dataStore.edit { preferences ->
            val instances = decode(preferences[KEY_INSTANCES])
            val existing = instances.firstOrNull { it.baseUrl == baseUrl }
            if (existing != null) {
                preferences[KEY_CURRENT_ID] = existing.id
                return@edit
            }
            val instance =
                ForumInstance(
                    id = newId(),
                    baseUrl = baseUrl,
                    name = name?.takeIf { it.isNotBlank() } ?: instanceHost(baseUrl),
                )
            preferences[KEY_INSTANCES] = json.encodeToString(instances + instance)
            preferences[KEY_CURRENT_ID] = instance.id
        }
    }

    /** Removes a site; when it was current, the first remaining site takes over, or nothing does. */
    suspend fun remove(id: String) {
        dataStore.edit { preferences ->
            val remaining = decode(preferences[KEY_INSTANCES]).filterNot { it.id == id }
            preferences[KEY_INSTANCES] = json.encodeToString(remaining)
            if (preferences[KEY_CURRENT_ID] == id) {
                val next = remaining.firstOrNull()?.id
                if (next != null) preferences[KEY_CURRENT_ID] = next else preferences.remove(KEY_CURRENT_ID)
            }
        }
    }

    /** Switches the current site. An id not in the list is ignored rather than dangled. */
    suspend fun select(id: String) {
        dataStore.edit { preferences ->
            if (decode(preferences[KEY_INSTANCES]).any { it.id == id }) {
                preferences[KEY_CURRENT_ID] = id
            }
        }
    }

    private fun decode(raw: String?): List<ForumInstance> {
        if (raw.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (_: SerializationException) {
            // A list that stopped decoding is data loss either way; an empty list at least starts.
            emptyList()
        }
    }

    private companion object {
        val KEY_INSTANCES = stringPreferencesKey("instances")
        val KEY_CURRENT_ID = stringPreferencesKey("current_instance_id")
    }
}
