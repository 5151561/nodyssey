package io.github.nodyssey.data.session

import io.github.plaza.core.net.SessionCookieStore

/**
 * A cookie store that is a map, standing in for the WebView's.
 *
 * Everything these tests are about happens *above* the store: which names count as a session, which
 * ones Cloudflare rotates, and what a change in either should cost. What the store itself has to get
 * right for that is one rule — a cookie written twice keeps only the second value, which is how a
 * sign-out (`session=`) replaces a session rather than adding one — and it is one line here.
 *
 * Single-host on purpose. Every caller reads and writes the same site, so a per-URL store would add
 * a dimension no test varies and quietly hide it if one ever did.
 */
class FakeSessionCookieStore : SessionCookieStore {
    private val cookies = LinkedHashMap<String, String>()

    var flushes: Int = 0
        private set

    override fun cookieHeader(url: String): String? =
        cookies.entries
            .joinToString("; ") { (name, value) -> "$name=$value" }
            .ifEmpty { null }

    override fun setCookie(url: String, cookie: String) {
        // Attributes are the store's business, and this one keeps none of them.
        val pair = cookie.substringBefore(';')
        cookies[pair.substringBefore('=').trim()] = pair.substringAfter('=', "").trim()
    }

    override suspend fun removeAll() = cookies.clear()

    override fun flush() {
        flushes++
    }
}
