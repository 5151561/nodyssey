package io.github.plaza.core.net

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import platform.Network.NW_DEFAULT_PRIVACY_CONTEXT
import platform.Network.nw_endpoint_create_host
import platform.Network.nw_endpoint_create_url
import platform.Network.nw_privacy_context_flush_cache
import platform.Network.nw_privacy_context_require_encrypted_name_resolution
import platform.Network.nw_resolver_config_add_server_address
import platform.Network.nw_resolver_config_create_https

/**
 * One DoH server, in the terms Network.framework takes them.
 *
 * Deliberately not `DohConfig`, for the reason [ProxyRoute] is not `ProxyConfig`: that type is the
 * *setting*, with an on/off switch, a preset list and a notion of being half-typed, none of which
 * mean anything once a server has been decided on. The container answers those questions and hands
 * this down.
 *
 * @param serverAddresses the addresses [url]'s own host is reached at, so resolving the resolver does
 *   not need a resolver. May be empty, in which case the system resolves that hostname in the clear
 *   first — which is a real weakening and one the screen states rather than hides.
 */
data class EncryptedResolver(
    val url: String,
    val serverAddresses: List<String> = emptyList(),
)

/**
 * 加密 DNS on Apple: the process's default privacy context, re-pointed whenever the setting changes.
 *
 * **`NSURLSession` has no resolver parameter, and it does not need one.** The seam is one level down.
 * `nw_privacy_context_require_encrypted_name_resolution` configures a privacy context rather than a
 * connection, and `privacy_context.h` says of `NW_DEFAULT_PRIVACY_CONTEXT` that DNS settings applied
 * to it "will be inherited by other resolutions in the same process" — which is every session this
 * app creates, the image loader's included. That is why this class hands nothing to anybody: unlike
 * `ProxiedUrlSession`, which must rebuild a session because a proxy is copied into its configuration,
 * this reaches the sessions that already exist.
 *
 * **What it cannot express, and what that costs.** The header is explicit on both:
 *
 * - The fallback resolver "will only take effect if require_encrypted_name_resolution is set to
 *   true", and while that is true "all cleartext name resolution will be blocked". So there is no
 *   *prefer* — either this server answers or the lookup fails. `DohCapabilities.canFallBackToSystem`
 *   is false here because of this paragraph, and 加密 DNS draws no fallback switch on this platform.
 * - The fallback resolver is used only "if no other encrypted DNS resolver is already configured for
 *   the query", so a device carrying a DoH profile keeps using that one and this setting only takes
 *   effect where the system has no opinion.
 * - Nothing in the API describes which record types to query — that is the system resolver's
 *   business, which is why `canChooseRecordTypes` is false too.
 *
 * The cache is flushed on every change because a privacy context caches answers; without it the
 * addresses the old resolver gave would keep being used, which is the same failure evicting the
 * connection pool avoids on the other platform.
 *
 * Turning the setting off is `require = false` with no config, which the header documents as the
 * default state — this restores it rather than leaving the last server quietly in place.
 */
@OptIn(ExperimentalForeignApi::class)
class EncryptedNameResolution(
    scope: CoroutineScope,
    resolver: Flow<EncryptedResolver?>,
) {
    init {
        resolver
            .distinctUntilChanged()
            .onEach(::apply)
            .launchIn(scope)
    }

    private fun apply(resolver: EncryptedResolver?) {
        val context = NW_DEFAULT_PRIVACY_CONTEXT
        val config = resolver?.let { built ->
            nw_endpoint_create_url(built.url)?.let(::nw_resolver_config_create_https)
        }
        if (config != null) {
            resolver.serverAddresses.forEach { address ->
                // The endpoint `NWEndpoint.hostPort(host:port:)` makes on the Swift side. A numeric
                // host is an address endpoint, which is what a server address has to be; a hostname
                // typed here would be one more name needing resolution, and the screen refuses to
                // save one.
                nw_endpoint_create_host(address, DOH_PORT)
                    ?.let { endpoint -> nw_resolver_config_add_server_address(config, endpoint) }
            }
        }
        nw_privacy_context_require_encrypted_name_resolution(
            privacy_context = context,
            require_encrypted_name_resolution = config != null,
            fallback_resolver_config = config,
        )
        nw_privacy_context_flush_cache(context)
    }

    private companion object {
        /** DoH is HTTPS, and a server address carries no port of its own. */
        const val DOH_PORT = "443"
    }
}
