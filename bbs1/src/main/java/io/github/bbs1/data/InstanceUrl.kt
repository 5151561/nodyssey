package io.github.bbs1.data

import java.net.URI
import java.net.URISyntaxException

/**
 * Turns whatever the user typed into an origin, or null when it cannot be one.
 *
 * Multi-instance means the address field is the front door of the whole app, and people paste
 * anything into it: a bare domain, a page URL with a path and query, an uppercased host. All of
 * those normalize to `scheme://host[:port]`; default ports are dropped so the same site typed two
 * ways cannot become two entries.
 *
 * Rejected rather than repaired: a scheme other than http/https, an empty host, and userinfo —
 * `https://evil@real.site` reads as one site and connects to another, so it does not get in.
 */
fun normalizeInstanceUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
    val uri =
        try {
            URI(withScheme)
        } catch (_: URISyntaxException) {
            return null
        }
    val scheme = uri.scheme?.lowercase()
    if (scheme != "https" && scheme != "http") return null
    if (uri.userInfo != null) return null
    val host = uri.host?.lowercase() ?: return null
    if (host.isEmpty()) return null
    val defaultPort = if (scheme == "https") 443 else 80
    val port = uri.port.takeIf { it != -1 && it != defaultPort }
    return buildString {
        append(scheme)
        append("://")
        append(host)
        if (port != null) {
            append(':')
            append(port)
        }
    }
}

/**
 * The host of a normalized origin — the default display name for a site the user did not name.
 *
 * Falls back to the input instead of throwing: the repository calls this inside a DataStore edit,
 * which is the wrong place to discover that a caller skipped [normalizeInstanceUrl].
 */
fun instanceHost(baseUrl: String): String =
    try {
        URI(baseUrl).host ?: baseUrl
    } catch (_: URISyntaxException) {
        baseUrl
    }
