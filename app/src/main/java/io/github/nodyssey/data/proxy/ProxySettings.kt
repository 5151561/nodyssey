package io.github.nodyssey.data.proxy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** HTTP proxies speak CONNECT/plain proxying; SOCKS ones tunnel raw sockets, DNS included. */
enum class ProxyType { HTTP, SOCKS }

/**
 * A proxy for [io.github.nodyssey.di.AppContainer.okHttpClient] only — every forum request, HTML or
 * JSON, plus the avatars and attachments Coil pulls through that same client. The image-host and
 * update-check clients are separate on purpose (see `AppContainer`) and never see this.
 *
 * [port] of `0` means "not typed yet", not port 0 — no listener answers on it, so treating it as
 * unset costs nothing.
 */
data class ProxyConfig(
    val enabled: Boolean = false,
    val type: ProxyType = ProxyType.HTTP,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "",
) {
    val isUsable: Boolean get() = host.isNotBlank() && port in 1..PORT_MAX

    companion object {
        private const val PORT_MAX = 65535
    }
}

enum class ProxyConfigProblem { MISSING_HOST, INVALID_PORT }

/** `null` when [ProxyConfig.enabled] is false — an address nobody is routing through need not be valid. */
fun ProxyConfig.problem(): ProxyConfigProblem? {
    if (!enabled) return null
    if (host.isBlank()) return ProxyConfigProblem.MISSING_HOST
    if (port !in 1..65535) return ProxyConfigProblem.INVALID_PORT
    return null
}

private val Context.proxyDataStore: DataStore<Preferences> by preferencesDataStore(name = "proxy")

private object ProxyKeys {
    val ENABLED = booleanPreferencesKey("enabled")
    val TYPE = stringPreferencesKey("type")
    val HOST = stringPreferencesKey("host")
    val PORT = intPreferencesKey("port")
    val USERNAME = stringPreferencesKey("username")
    val PASSWORD = stringPreferencesKey("password")
}

interface ProxySettings {
    val config: Flow<ProxyConfig>

    suspend fun save(config: ProxyConfig)
}

class DataStoreProxySettings(context: Context) : ProxySettings {
    private val dataStore = context.applicationContext.proxyDataStore

    override val config: Flow<ProxyConfig> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { preferences ->
            ProxyConfig(
                enabled = preferences[ProxyKeys.ENABLED] == true,
                type = preferences[ProxyKeys.TYPE]?.let { runCatching { ProxyType.valueOf(it) }.getOrNull() }
                    ?: ProxyType.HTTP,
                host = preferences[ProxyKeys.HOST].orEmpty(),
                port = preferences[ProxyKeys.PORT] ?: 0,
                username = preferences[ProxyKeys.USERNAME].orEmpty(),
                password = preferences[ProxyKeys.PASSWORD].orEmpty(),
            )
        }

    override suspend fun save(config: ProxyConfig) {
        dataStore.edit { preferences ->
            preferences[ProxyKeys.ENABLED] = config.enabled
            preferences[ProxyKeys.TYPE] = config.type.name
            preferences[ProxyKeys.HOST] = config.host
            preferences[ProxyKeys.PORT] = config.port
            preferences[ProxyKeys.USERNAME] = config.username
            preferences[ProxyKeys.PASSWORD] = config.password
        }
    }
}
