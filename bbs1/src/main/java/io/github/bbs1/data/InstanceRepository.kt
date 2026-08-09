package io.github.bbs1.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.bbs1.model.ForumInstance
import io.github.bbs1.model.InstanceSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
 * The single source of truth for the user's list of sites and who is signed in to each.
 *
 * Everything that shows or switches a site collects [snapshot]; nothing keeps its own copy. The
 * whole list rides in one preferences key as JSON — it has a handful of entries and is always read
 * whole, which is not a job for a database.
 *
 * The credentials are a **second store** rather than a field in that list, and the split is a
 * backup boundary, not a modelling one: the site addresses are worth restoring onto a new device
 * and the tokens are not (see `bbs1_data_extraction_rules.xml`, which excludes this file by name).
 * [snapshot] joins them back together so no caller has to know.
 */
class InstanceRepository(
    private val dataStore: DataStore<Preferences>,
    private val sessionStore: DataStore<Preferences>,
    /** Injectable so tests get stable ids; production takes the default. */
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val json = Json { ignoreUnknownKeys = true }

    val snapshot: Flow<InstancesSnapshot> =
        combine(dataStore.data.orEmpty(), sessionStore.data.orEmpty()) { preferences, sessions ->
            InstancesSnapshot(
                instances = decode(preferences[KEY_INSTANCES]).map { it.copy(session = sessions.session(it.id)) },
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
        // Deleting a site has to take its credential with it: nothing would ever read that key
        // again, so leaving it behind would be a token lying on disk with no way to sign out of it.
        clearSession(id)
    }

    /** Switches the current site. An id not in the list is ignored rather than dangled. */
    suspend fun select(id: String) {
        dataStore.edit { preferences ->
            if (decode(preferences[KEY_INSTANCES]).any { it.id == id }) {
                preferences[KEY_CURRENT_ID] = id
            }
        }
    }

    /** One site's credential, for the screens that only care about that. */
    fun session(instanceId: String): Flow<InstanceSession?> =
        sessionStore.data
            .orEmpty()
            .map { it.session(instanceId) }
            .distinctUntilChanged()

    /** Signs in: replaces whatever credential [id] held. */
    suspend fun saveSession(id: String, session: InstanceSession) {
        sessionStore.edit { it[sessionKey(id)] = json.encodeToString(session) }
    }

    /**
     * Signs out, whether the user asked or the server did. Called from both places on purpose: a
     * token the server has stopped accepting is not different, to this app, from one the user
     * discarded.
     */
    suspend fun clearSession(id: String) {
        sessionStore.edit { it.remove(sessionKey(id)) }
    }

    private fun Preferences.session(instanceId: String): InstanceSession? {
        val raw = this[sessionKey(instanceId)] ?: return null
        return try {
            json.decodeFromString<InstanceSession>(raw)
        } catch (_: IllegalArgumentException) {
            // Same contract as `decode` below. A credential that no longer parses is a credential
            // the server would refuse anyway; reading it as "signed out" costs one sign-in.
            null
        }
    }

    private fun decode(raw: String?): List<ForumInstance> {
        if (raw.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (_: IllegalArgumentException) {
            // decodeFromString's documented contract: SerializationException for a decoding error,
            // IllegalArgumentException for JSON that parses but is not a valid instance — and the
            // former subclasses the latter, so one catch covers both. Either way the list is data
            // loss already; an empty list at least lets the app start instead of throwing on every
            // launch from a flow that exists to survive exactly this.
            emptyList()
        }
    }

    private companion object {
        val KEY_INSTANCES = stringPreferencesKey("instances")
        val KEY_CURRENT_ID = stringPreferencesKey("current_instance_id")

        /** One key per site, so signing out of one rewrites only its own entry. */
        fun sessionKey(instanceId: String) = stringPreferencesKey("session_$instanceId")

        /** A corrupt or unreadable store must not take the app down; read it as empty instead. */
        fun Flow<Preferences>.orEmpty(): Flow<Preferences> =
            catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
    }
}
