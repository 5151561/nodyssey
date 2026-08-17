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
import io.github.nodyssey.data.security.KeystoreSecretCipher
import io.github.nodyssey.data.security.SecretCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** HTTP proxies speak CONNECT/plain proxying; SOCKS ones tunnel raw sockets, DNS included. */
enum class ProxyType { HTTP, SOCKS }

/**
 * How much of the app a saved proxy carries.
 *
 * [EVERYTHING] is the default because a proxy that some of the app ignores is the failure this is
 * named after: the posts arrive and the avatars do not, and nothing on screen says why. The forum,
 * the image host and the update check are three separate OkHttp clients (see
 * [io.github.nodyssey.di.AppContainer]), and the whole point of routing them from one setting is that
 * they cannot drift apart.
 *
 * [FORUM_ONLY] is for the user whose node is not a good place to send an attachment upload or an APK
 * download through — a metered or slow node, or an image host that is reachable anyway.
 */
enum class ProxyScope { EVERYTHING, FORUM_ONLY }

/**
 * A proxy for the app's own HTTP clients — every forum request, HTML or JSON, the avatars and
 * attachments Coil pulls through the forum client, and, unless [scope] says otherwise, image-host
 * uploads and the update check too.
 *
 * The login WebView is not covered: it is Chromium's network stack, not OkHttp's, and it reads the
 * system proxy rather than this setting. `ProxySettingsScreen` says so on screen.
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
    val scope: ProxyScope = ProxyScope.EVERYTHING,
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

    /** Ciphertext, not a password — see [DataStoreProxySettings]. */
    val PASSWORD = stringPreferencesKey("password")
    val SCOPE = stringPreferencesKey("scope")
}

interface ProxySettings {
    val config: Flow<ProxyConfig>

    suspend fun save(config: ProxyConfig)
}

/**
 * The stored half of 代理设置.
 *
 * Everything but the password is stored as typed: an address and a port are configuration, and
 * hiding them would only make the file harder to inspect. The password goes through [cipher], so what
 * lands on disk — and in the cloud backup this file is part of — is ciphertext whose key never leaves
 * the device. A value that comes back unreadable is read as no password at all, which is the same
 * thing that happens when the user has not typed one.
 */
class DataStoreProxySettings(
    context: Context,
    private val cipher: SecretCipher = KeystoreSecretCipher(),
) : ProxySettings {
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
                password = cipher.decrypt(preferences[ProxyKeys.PASSWORD].orEmpty()),
                scope = preferences[ProxyKeys.SCOPE]?.let { runCatching { ProxyScope.valueOf(it) }.getOrNull() }
                    ?: ProxyScope.EVERYTHING,
            )
        }

    override suspend fun save(config: ProxyConfig) {
        dataStore.edit { preferences ->
            preferences[ProxyKeys.ENABLED] = config.enabled
            preferences[ProxyKeys.TYPE] = config.type.name
            preferences[ProxyKeys.HOST] = config.host
            preferences[ProxyKeys.PORT] = config.port
            preferences[ProxyKeys.USERNAME] = config.username
            // A device that cannot encrypt stores nothing rather than the password in the clear. The
            // user sees an empty field on the next visit, which is recoverable; a leaked password is not.
            preferences[ProxyKeys.PASSWORD] = cipher.encrypt(config.password).orEmpty()
            preferences[ProxyKeys.SCOPE] = config.scope.name
        }
    }
}
