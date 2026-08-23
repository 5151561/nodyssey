package io.github.nodyssey.data.dns

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import okio.IOException

/**
 * A DoH server the app offers by name, and the addresses that server's own hostname is reached at.
 *
 * [bootstrap] is the part that makes a preset worth having. Resolving `dns.alidns.com` is itself a
 * DNS lookup, and the only resolver available for it is the one this feature exists to stop
 * trusting — so the addresses are carried here instead, and the first lookup needs no resolver at
 * all. They are the providers' own documented addresses, verified against a public resolver on
 * 2026-08-23; a provider that moves would need this list edited, which is why [CUSTOM] lets the user
 * type both halves.
 *
 * The order is the order the screen lists them in, and the first is the default: [ALIDNS] and
 * [DNSPOD] answer from inside mainland China, which is where most of this forum reads from — they
 * end an ISP hijacking the answer, which is what this setting is usually reached for.
 * [CLOUDFLARE] and [GOOGLE] resolve from outside it, which is the only thing that helps when the
 * *record* is what has been poisoned rather than the reply; whether their endpoints are reachable at
 * all is a property of the network the phone is on, and 测试解析 is how someone finds out.
 */
enum class DohProvider(
    val url: String,
    val bootstrap: List<String>,
) {
    ALIDNS("https://dns.alidns.com/dns-query", listOf("223.5.5.5", "223.6.6.6", "2400:3200::1")),
    DNSPOD("https://doh.pub/dns-query", listOf("1.12.12.12", "120.53.53.53")),
    CLOUDFLARE("https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111")),
    GOOGLE("https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888")),

    /** Whatever the user typed. Both halves are theirs to fill in — see [DohConfig.customBootstrap]. */
    CUSTOM("", emptyList()),
}

/**
 * 加密 DNS — how the app's own HTTP clients turn a hostname into an address.
 *
 * The one thing this changes is where the answer comes from: a DoH server over HTTPS instead of
 * whatever resolver the network handed the device. That is enough for a domain the local resolver
 * lies about or refuses, and it is *not* enough for anything further down — an address that is
 * blackholed, a TLS handshake cut off by its SNI, a connection reset on sight. Those need a tunnel,
 * and 代理设置 is where one is configured.
 *
 * Every one of the app's clients resolves through this, the login WebView through none of it: that is
 * Chromium's network stack, with its own resolver and the system's 私人 DNS setting above it.
 *
 * [fallbackToSystem] is off by default, and that is the honest default rather than the friendly one.
 * A resolver that quietly hands the question back to the one being bypassed makes the setting mean
 * something different on every network, and the answer it falls back to is the poisoned answer this
 * was turned on to avoid. What the switch is really for is a network where the chosen server cannot
 * be reached at all, where the alternative is an app that resolves nothing.
 */
data class DohConfig(
    val enabled: Boolean = false,
    val provider: DohProvider = DohProvider.ALIDNS,
    val customUrl: String = "",
    /** Zero or more IP literals, separated by commas or spaces. Empty means "resolve the URL's host normally". */
    val customBootstrap: String = "",
    val includeIPv6: Boolean = true,
    val fallbackToSystem: Boolean = false,
) {
    /** The server this config actually queries — the preset's, or the typed one. */
    val serverUrl: String
        get() = if (provider == DohProvider.CUSTOM) customUrl.trim() else provider.url

    /** The addresses [serverUrl]'s own host is reached at without asking a resolver. May be empty. */
    val bootstrap: List<String>
        get() =
            if (provider == DohProvider.CUSTOM) {
                parseBootstrapAddresses(customBootstrap).orEmpty()
            } else {
                provider.bootstrap
            }

    val isUsable: Boolean get() = isDohUrl(serverUrl)
}

/** Whether lookups should go to [DohConfig.serverUrl] right now. The counterpart of `ProxyConfig.routes`. */
fun DohConfig.resolvesOverHttps(): Boolean = enabled && isUsable

enum class DohConfigProblem { MISSING_URL, INVALID_URL, INVALID_BOOTSTRAP }

/** `null` when [DohConfig.enabled] is false — a server nobody is asking need not be valid. */
fun DohConfig.problem(): DohConfigProblem? {
    if (!enabled || provider != DohProvider.CUSTOM) return null
    val url = customUrl.trim()
    if (url.isBlank()) return DohConfigProblem.MISSING_URL
    if (!isDohUrl(url)) return DohConfigProblem.INVALID_URL
    if (parseBootstrapAddresses(customBootstrap) == null) return DohConfigProblem.INVALID_BOOTSTRAP
    return null
}

/**
 * `https://host/path`, and nothing else.
 *
 * Plain HTTP is refused rather than accepted with a warning: a DoH endpoint reached over `http://` is
 * a query anyone on the path can read and rewrite, which is the arrangement this whole setting exists
 * to leave. The rest is deliberately shallow — the URL is handed to a real parser on the platform
 * side, and this only has to keep an unusable one from being saved.
 */
private fun isDohUrl(url: String): Boolean {
    val prefix = "https://"
    if (!url.startsWith(prefix, ignoreCase = true)) return false
    val rest = url.substring(prefix.length)
    val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
    return host.isNotBlank() && host.none(Char::isWhitespace)
}

/**
 * The bootstrap field as a list, or `null` when something in it is not an address.
 *
 * Null rather than "the entries that parsed": a typo silently dropped is a server that quietly needs
 * the system resolver after all, on the one screen whose whole subject is not needing it.
 */
fun parseBootstrapAddresses(text: String): List<String>? {
    val tokens = text.split(',', ' ', '\n', '\t').map(String::trim).filter(String::isNotEmpty)
    return if (tokens.all(::isIpLiteral)) tokens else null
}

/**
 * Whether [host] is already an address, in either family.
 *
 * Two callers, and the second is the load-bearing one: a hostname that is an IP literal must never be
 * sent to a DoH server, which would answer `NXDOMAIN` for `127.0.0.1` — see `AppDns`, where a proxy
 * at a numeric address would otherwise stop resolving the moment this setting was turned on.
 *
 * The IPv6 half accepts more than it should (it does not check group counts around `::`), which is
 * the safe direction: the platform parses the literal for real, and anything this lets through that
 * it rejects is treated as no address at all.
 */
fun isIpLiteral(host: String): Boolean =
    when {
        host.isEmpty() -> false
        host.contains(':') -> isIpv6Literal(host)
        else -> isIpv4Literal(host)
    }

private fun isIpv4Literal(host: String): Boolean {
    val groups = host.split('.')
    if (groups.size != 4) return false
    return groups.all { group ->
        group.isNotEmpty() && group.length <= 3 && group.all(Char::isDigit) && group.toInt() <= 255
    }
}

private fun isIpv6Literal(host: String): Boolean {
    // A zone index (`fe80::1%wlan0`) is part of the literal and not part of the address.
    val address = host.substringBefore('%')
    if (address.count { it == ':' } !in 2..8) return false
    if (address.contains(":::")) return false
    // The last group may be a v4 literal — `::ffff:192.168.0.1`.
    val groups = address.split(':')
    val last = groups.last()
    val hextets = if (last.contains('.')) groups.dropLast(1) else groups
    if (last.contains('.') && !isIpv4Literal(last)) return false
    return hextets.all { group ->
        group.isEmpty() || (group.length <= 4 && group.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
    }
}

private object DohKeys {
    val ENABLED = booleanPreferencesKey("enabled")
    val PROVIDER = stringPreferencesKey("provider")
    val CUSTOM_URL = stringPreferencesKey("custom_url")
    val CUSTOM_BOOTSTRAP = stringPreferencesKey("custom_bootstrap")
    val INCLUDE_IPV6 = booleanPreferencesKey("include_ipv6")
    val FALLBACK_TO_SYSTEM = booleanPreferencesKey("fallback_to_system")
}

interface DohSettings {
    val config: Flow<DohConfig>

    suspend fun save(config: DohConfig)

    /**
     * Flips the master switch on its own, leaving the server and its options as they are on disk.
     *
     * The same deal 代理设置 struck, for the same reason: the rest of the screen is a draft committed
     * with 保存, 保存 is only offered while the switch is on, and a switch that waited for it could be
     * turned on and never off again.
     */
    suspend fun setEnabled(enabled: Boolean)
}

/**
 * The stored half of 加密 DNS.
 *
 * Nothing here is a secret — a resolver's address is configuration, and there is no credential in the
 * protocol — so it is all stored as typed, unlike the proxy's password.
 */
class DataStoreDohSettings(
    private val dataStore: DataStore<Preferences>,
) : DohSettings {
    override val config: Flow<DohConfig> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { preferences ->
            DohConfig(
                enabled = preferences[DohKeys.ENABLED] == true,
                provider = preferences[DohKeys.PROVIDER]
                    ?.let { runCatching { DohProvider.valueOf(it) }.getOrNull() }
                    ?: DohConfig().provider,
                customUrl = preferences[DohKeys.CUSTOM_URL].orEmpty(),
                customBootstrap = preferences[DohKeys.CUSTOM_BOOTSTRAP].orEmpty(),
                includeIPv6 = preferences[DohKeys.INCLUDE_IPV6] != false,
                fallbackToSystem = preferences[DohKeys.FALLBACK_TO_SYSTEM] == true,
            )
        }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[DohKeys.ENABLED] = enabled }
    }

    override suspend fun save(config: DohConfig) {
        dataStore.edit { preferences ->
            preferences[DohKeys.ENABLED] = config.enabled
            preferences[DohKeys.PROVIDER] = config.provider.name
            preferences[DohKeys.CUSTOM_URL] = config.customUrl
            preferences[DohKeys.CUSTOM_BOOTSTRAP] = config.customBootstrap
            preferences[DohKeys.INCLUDE_IPV6] = config.includeIPv6
            preferences[DohKeys.FALLBACK_TO_SYSTEM] = config.fallbackToSystem
        }
    }
}
