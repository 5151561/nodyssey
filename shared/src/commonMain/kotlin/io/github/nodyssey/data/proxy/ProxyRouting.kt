package io.github.nodyssey.data.proxy

/**
 * Which side of [ProxyScope] a client sits on.
 *
 * [FORUM] is the client that carries the NodeSeek session — the one the proxy exists for, and the one
 * [ProxyScope.FORUM_ONLY] keeps routed. [THIRD_PARTY] is the image host and the update check: same
 * setting, same address, but the user is allowed to leave them direct.
 */
enum class ProxyClientKind { FORUM, THIRD_PARTY }

/** Whether a client of this [kind] should be routed through this config right now. */
fun ProxyConfig.routes(kind: ProxyClientKind): Boolean =
    enabled && isUsable && (kind == ProxyClientKind.FORUM || scope == ProxyScope.EVERYTHING)
