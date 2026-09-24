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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.IOException

/**
 * A DoH server the app offers by name, and the addresses that server's own hostname is reached at.
 *
 * [bootstrap] is the part that makes a preset worth having. Resolving `dns.alidns.com` is itself a
 * DNS lookup, and the only resolver available for it is the one this feature exists to stop
 * trusting — so the addresses are carried here instead, and the first lookup needs no resolver at
 * all. They are the providers' own documented addresses, verified against a public resolver on
 * 2026-08-23; a provider that moves leaves them stale, which is why each preset's can be overridden
 * from its row ([DohServer.bootstrapOverride]).
 *
 * The order is the order a fresh install lists them in: [ALIDNS] and [DNSPOD] answer from inside
 * mainland China, which is where most of this forum reads from — they end an ISP hijacking the
 * answer, which is what this setting is usually reached for. [CLOUDFLARE] and [GOOGLE] resolve from
 * outside it, which is the only thing that helps when the *record* is what has been poisoned rather
 * than the reply; whether their endpoints are reachable at all is a property of the network the phone
 * is on, and 测试解析 is how someone finds out.
 */
enum class DohProvider(
    val url: String,
    val bootstrap: List<String>,
) {
    ALIDNS("https://dns.alidns.com/dns-query", listOf("223.5.5.5", "223.6.6.6", "2400:3200::1")),
    DNSPOD("https://doh.pub/dns-query", listOf("1.12.12.12", "120.53.53.53")),
    CLOUDFLARE("https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111")),
    GOOGLE("https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888")),
}

/**
 * One row of 加密 DNS's list: a [preset], or an address the user added.
 *
 * [checked] rows are the ones asked, top to bottom — see [DohConfig.chain]. A row that is not checked
 * stays in the list rather than being dropped, so a preset turned off can be turned back on and a
 * custom address does not have to be typed twice.
 *
 * @param id stable across edits and reorders: the preset's name, or `custom-N` for an added one.
 * @param customUrl the typed address; unused for a preset, whose address is its own.
 * @param bootstrapOverride the typed bootstrap addresses. Null for a preset still using the ones it
 *   ships with; never null for a custom server, where an empty string means "resolve the URL's host
 *   normally".
 */
data class DohServer(
    val id: String,
    val preset: DohProvider? = null,
    val customUrl: String = "",
    val bootstrapOverride: String? = null,
    val checked: Boolean = false,
) {
    /** The server this row queries — the preset's, or the typed one. */
    val url: String get() = preset?.url ?: customUrl.trim()

    /** The addresses [url]'s own host is reached at without asking a resolver. May be empty. */
    val bootstrap: List<String>
        get() = bootstrapOverride?.let { parseBootstrapAddresses(it).orEmpty() } ?: preset?.bootstrap.orEmpty()

    val isUsable: Boolean get() = isDohUrl(url)

    companion object {
        fun preset(provider: DohProvider, checked: Boolean = false) =
            DohServer(id = provider.name, preset = provider, checked = checked)
    }
}

/**
 * What a fresh install lists: every preset, the two mainland ones ticked. Two rather than one because
 * a second server is the whole point of an ordered list: the first one being unreachable is the
 * failure a single server has no answer to.
 */
val DefaultDohServers: List<DohServer> =
    DohProvider.entries.map { DohServer.preset(it, checked = it == DohProvider.ALIDNS || it == DohProvider.DNSPOD) }

/** An id no row in this list has yet, for a custom server about to join it. */
fun List<DohServer>.nextCustomId(): String {
    val highest =
        mapNotNull { server ->
            server.id.takeIf { it.startsWith(CUSTOM_ID_PREFIX) }?.removePrefix(CUSTOM_ID_PREFIX)?.toIntOrNull()
        }.maxOrNull() ?: 0
    return "$CUSTOM_ID_PREFIX${highest + 1}"
}

private const val CUSTOM_ID_PREFIX = "custom-"

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
 * was turned on to avoid. What the switch is really for is a network where none of the chosen servers
 * can be reached at all, where the alternative is an app that resolves nothing.
 */
data class DohConfig(
    val enabled: Boolean = false,
    /** Every row the screen lists, in the order it lists them. */
    val servers: List<DohServer> = DefaultDohServers,
    val includeIPv6: Boolean = true,
    val fallbackToSystem: Boolean = false,
) {
    /**
     * The servers a lookup goes to, in the order it tries them: the next is asked only when the one
     * before it failed.
     *
     * A platform whose resolver takes one server (`DohCapabilities.triesServersInOrder` false) uses the
     * first of these and nothing else; its screen lets only one be ticked.
     */
    val chain: List<DohServer> get() = servers.filter { it.checked && it.isUsable }
}

/** Whether lookups should go to [DohConfig.chain] right now. The counterpart of `ProxyConfig.routes`. */
fun DohConfig.resolvesOverHttps(): Boolean = enabled && chain.isNotEmpty()

/** Why 保存 was refused on the page as a whole. */
enum class DohConfigProblem {
    NO_SERVER,
}

/** `null` when [DohConfig.enabled] is false — a list nobody is asking need not be complete. */
fun DohConfig.problem(): DohConfigProblem? = if (enabled && chain.isEmpty()) DohConfigProblem.NO_SERVER else null

/** Why one server's details could not be kept. */
enum class DohServerProblem { MISSING_URL, INVALID_URL, INVALID_BOOTSTRAP }

/**
 * Checks a server as typed into its details. [url] is null for a preset, whose address is not the
 * user's to type.
 */
fun dohServerProblem(url: String?, bootstrap: String): DohServerProblem? {
    if (url != null) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return DohServerProblem.MISSING_URL
        if (!isDohUrl(trimmed)) return DohServerProblem.INVALID_URL
    }
    if (parseBootstrapAddresses(bootstrap) == null) return DohServerProblem.INVALID_BOOTSTRAP
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
    val SERVERS = stringPreferencesKey("servers")
    val INCLUDE_IPV6 = booleanPreferencesKey("include_ipv6")
    val FALLBACK_TO_SYSTEM = booleanPreferencesKey("fallback_to_system")

    // What a build that offered one server at a time wrote. Read once, into [SERVERS], and removed by
    // the next save.
    val LEGACY_PROVIDER = stringPreferencesKey("provider")
    val LEGACY_CUSTOM_URL = stringPreferencesKey("custom_url")
    val LEGACY_CUSTOM_BOOTSTRAP = stringPreferencesKey("custom_bootstrap")
}

interface DohSettings {
    val config: Flow<DohConfig>

    suspend fun save(config: DohConfig)

    /**
     * Flips the master switch on its own, leaving the servers and options as they are on disk.
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
 * protocol — so it is all stored as typed, unlike the proxy's password. The list is one JSON value
 * rather than a key per row: its order is part of the setting, and preference keys have none.
 */
class DataStoreDohSettings(
    private val dataStore: DataStore<Preferences>,
) : DohSettings {
    override val config: Flow<DohConfig> = dataStore.data
        .catch { throwable -> if (throwable is IOException) emit(emptyPreferences()) else throw throwable }
        .map { preferences ->
            DohConfig(
                enabled = preferences[DohKeys.ENABLED] == true,
                servers = preferences[DohKeys.SERVERS]?.let(::decodeServers) ?: legacyServers(preferences),
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
            preferences[DohKeys.SERVERS] = encodeServers(config.servers)
            preferences[DohKeys.INCLUDE_IPV6] = config.includeIPv6
            preferences[DohKeys.FALLBACK_TO_SYSTEM] = config.fallbackToSystem
            preferences.remove(DohKeys.LEGACY_PROVIDER)
            preferences.remove(DohKeys.LEGACY_CUSTOM_URL)
            preferences.remove(DohKeys.LEGACY_CUSTOM_BOOTSTRAP)
        }
    }
}

@Serializable
private data class StoredDohServer(
    val id: String,
    val preset: String? = null,
    val url: String = "",
    val bootstrap: String? = null,
    val checked: Boolean = false,
)

private val serversJson = Json { ignoreUnknownKeys = true }

private fun encodeServers(servers: List<DohServer>): String =
    serversJson.encodeToString(
        servers.map { StoredDohServer(it.id, it.preset?.name, it.customUrl, it.bootstrapOverride, it.checked) },
    )

/**
 * The stored list, made whole again.
 *
 * A preset this build no longer ships is dropped — its address went with it — and one it ships that
 * the list has never seen is added at the end, unticked: a new preset is something to offer, not
 * something to start sending lookups to. A value that does not parse at all reads as the defaults
 * rather than as a crash, the same courtesy the other keys get.
 */
private fun decodeServers(text: String): List<DohServer> {
    val stored = runCatching { serversJson.decodeFromString<List<StoredDohServer>>(text) }.getOrNull()
        ?: return DefaultDohServers
    val servers = stored
        .mapNotNull { row ->
            when (row.preset) {
                null -> DohServer(row.id, customUrl = row.url, bootstrapOverride = row.bootstrap.orEmpty(), checked = row.checked)

                else -> DohProvider.entries.firstOrNull { it.name == row.preset }?.let { provider ->
                    DohServer(provider.name, provider, bootstrapOverride = row.bootstrap, checked = row.checked)
                }
            }
        }.distinctBy { it.id }
    val missing = DohProvider.entries.filter { provider -> servers.none { it.preset == provider } }
    return servers + missing.map { DohServer.preset(it) }
}

/**
 * The list as a build that offered one server at a time left it: that one server ticked and first,
 * the other presets after it unticked, and a typed custom address kept as a row of its own even if it
 * was not the one chosen. A store that never chose anything — or chose something this build does not
 * know — reads as the defaults.
 */
private fun legacyServers(preferences: Preferences): List<DohServer> {
    val chosen = preferences[DohKeys.LEGACY_PROVIDER] ?: return DefaultDohServers
    val customUrl = preferences[DohKeys.LEGACY_CUSTOM_URL].orEmpty()
    val presets = DohProvider.entries.map { DohServer.preset(it, checked = it.name == chosen) }
    val custom =
        customUrl.takeIf { it.isNotBlank() }?.let {
            DohServer(
                id = presets.nextCustomId(),
                customUrl = it,
                bootstrapOverride = preferences[DohKeys.LEGACY_CUSTOM_BOOTSTRAP].orEmpty(),
                checked = chosen == LEGACY_CUSTOM,
            )
        }
    val servers = presets + listOfNotNull(custom)
    if (servers.none { it.checked }) return DefaultDohServers
    return servers.sortedByDescending { it.checked }
}

private const val LEGACY_CUSTOM = "CUSTOM"
