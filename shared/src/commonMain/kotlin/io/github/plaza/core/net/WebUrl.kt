package io.github.plaza.core.net

/**
 * The parts of an absolute URL that deciding what to do with a link depends on.
 *
 * This exists because that decision — is this our own site, may the login WebView load it, does it
 * open a screen or the browser — is a rule about the *site*, and it was reading it off
 * `java.net.URI`, which is a fact about the JVM. `WebUrlTest` is a differential test against
 * `java.net.URI` for exactly that reason: the replacement has to answer the way the class it
 * replaced did, including for the URLs written to get past a host check.
 *
 * Deliberately not a URL builder and not a general parser. It reads; nothing here formats a URL, and
 * the site's own path vocabulary stays where it is.
 *
 * @param scheme lower-cased on the way in. Everything else keeps its case, because a path and a
 * query are case-sensitive and a host is compared with an explicit `lowercase` where it matters.
 * @param userInfo the `user:password@` an attacker puts in front of the host they want you to read.
 * Non-null is the signal; the value itself is of no interest.
 * @param host null when the authority is not a hostname this can vouch for — absent, or carrying a
 * character a hostname may not (`_`, a non-ASCII letter, an empty label), or followed by a port that
 * is not a number. Callers read null as "not a host I know", which is the safe reading.
 * @param port -1 when the URL named none, exactly as `URI.getPort` reports it.
 * @param path percent-decoded, and null for a URL with no authority (`javascript:alert(1)`) — the
 * one case where a null path is not an absent path but a URL with nothing path-shaped in it.
 * @param rawQuery still encoded: `?to=https%3A%2F%2F…` has to be decoded per value, after the split
 * on `&`, or a `%26` inside a value would end the field it belongs to.
 * @param fragment percent-decoded. The site keeps whole routes in here (`#/message?mode=talk&to=5`),
 * so it is read rather than skipped.
 */
data class WebUrl(
    val scheme: String,
    val userInfo: String?,
    val host: String?,
    val port: Int,
    val path: String?,
    val rawQuery: String?,
    val fragment: String?,
) {
    /**
     * The value `name` carries in [rawQuery], percent-decoded, or null when the query has no such
     * field. An empty value reads as an empty string, which is what the site's own `/jump?to=` sends.
     *
     * `+` stays a plus sign. `java.net.URLDecoder`, which this replaced, would have made it a space —
     * that is form encoding, and this is a URL. Nothing the site emits can tell the difference,
     * because `encodeURIComponent` writes a literal plus as `%2B`; a hand-typed `/jump?to=` with one
     * in it now survives instead of arriving at the browser with a space in the middle.
     */
    fun queryParameter(name: String): String? =
        rawQuery
            ?.split('&')
            ?.firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=', missingDelimiterValue = "")
            ?.let(::percentDecode)

    companion object {
        /**
         * [value] as an absolute URL, or null when it is not one.
         *
         * Null covers everything `URI(value)` used to answer with an exception or with a relative
         * URI: a missing scheme, a character no URL may carry unescaped, a `%` that begins no
         * escape. Callers already treated all of those as "not a URL I can reason about".
         */
        fun parse(value: String): WebUrl? {
            val text = value.trim()
            if (!isWellFormed(text)) return null

            val schemeEnd = schemeEnd(text) ?: return null
            val scheme = text.substring(0, schemeEnd).lowercase()

            val body = text.substring(schemeEnd + 1)
            val hash = body.indexOf('#')
            val fragment = if (hash < 0) null else percentDecode(body.substring(hash + 1))
            val beforeFragment = if (hash < 0) body else body.substring(0, hash)
            val question = beforeFragment.indexOf('?')
            val rawQuery = if (question < 0) null else beforeFragment.substring(question + 1)
            val hierarchical = if (question < 0) beforeFragment else beforeFragment.substring(0, question)

            // No `//` means no authority: an opaque URL such as `mailto:` or `javascript:`, whose
            // remainder is not a path and must not be read as one.
            if (!hierarchical.startsWith("//")) {
                return WebUrl(scheme, null, null, -1, null, rawQuery, fragment)
            }

            val afterSlashes = hierarchical.substring(2)
            // An authority ends at the first `/`; what follows is the path, empty when there is none.
            val authority = afterSlashes.substringBefore('/')
            val path = afterSlashes.substring(authority.length)
            // `https://` on its own named neither; `file:///tmp` named an empty authority and a path.
            if (authority.isEmpty() && path.isEmpty()) return null

            val at = authority.lastIndexOf('@')
            val userInfo = if (at >= 0) authority.substring(0, at) else null
            // A port that is not a number makes the whole authority unreadable, not just itself.
            val (hostText, portText) = splitPort(authority.substring(at + 1)) ?: (null to null)
            val port = if (portText.isNullOrEmpty()) -1 else portText.toPort() ?: NO_AUTHORITY
            val host = hostText?.takeIf { port != NO_AUTHORITY && isHost(it) }

            return WebUrl(
                scheme = scheme,
                userInfo = userInfo,
                host = host,
                port = if (host == null) -1 else port,
                path = percentDecode(path),
                rawQuery = rawQuery,
                fragment = fragment,
            )
        }

        /**
         * Rejects what `URI(String)` threw on: a character no URL may carry unescaped, and a `%`
         * that is not the start of a two-digit escape.
         *
         * The set is RFC 2396's, which is the one `java.net.URI` enforces — narrower than what a
         * browser will swallow, and narrow on purpose: a space or a backslash inside a URL is how a
         * link gets read one way by the check and another way by whatever opens it.
         */
        private fun isWellFormed(text: String): Boolean {
            var index = 0
            while (index < text.length) {
                val char = text[index]
                if (char == '%') {
                    if (index + 3 > text.length) return false
                    if (!text[index + 1].isHex() || !text[index + 2].isHex()) return false
                    index += 3
                    continue
                }
                if (!char.isAllowedInUrl()) return false
                index++
            }
            return true
        }

        /** Where the `:` after a scheme sits, or null when the URL is relative. */
        private fun schemeEnd(text: String): Int? {
            val colon = text.indexOf(':')
            if (colon <= 0 || !text[0].isAsciiLetter()) return null
            for (index in 1 until colon) {
                val char = text[index]
                if (!char.isAsciiLetter() && !char.isAsciiDigit() && char != '+' && char != '-' && char != '.') {
                    return null
                }
            }
            return colon
        }

        /**
         * Splits `host[:port]`, keeping an IPv6 literal's own colons inside its brackets. The second
         * half is null when no `:` was written and `""` when one was written with nothing after it.
         */
        private fun splitPort(hostAndPort: String): Pair<String, String?>? {
            if (hostAndPort.startsWith("[")) {
                val close = hostAndPort.indexOf(']')
                if (close < 0) return null
                val host = hostAndPort.substring(0, close + 1)
                val remainder = hostAndPort.substring(close + 1)
                return when {
                    remainder.isEmpty() -> host to null
                    remainder.startsWith(":") -> host to remainder.substring(1)
                    else -> null
                }
            }
            val colon = hostAndPort.indexOf(':')
            return if (colon < 0) {
                hostAndPort to null
            } else {
                hostAndPort.substring(0, colon) to hostAndPort.substring(colon + 1)
            }
        }

        /** Digits only, so `:-1` is a broken authority rather than a negative port. */
        private fun String.toPort(): Int? = takeIf { it.isNotEmpty() && it.all { char -> char.isAsciiDigit() } }?.toIntOrNull()

        /**
         * A hostname or an IP literal, by RFC 2396's rules rather than a browser's.
         *
         * An underscore, a non-ASCII letter and an empty label all fail here and all failed before;
         * a hostname this refuses is simply not one of ours, and the caller hands it to the browser,
         * which has its own more forgiving parser.
         */
        private fun isHost(host: String): Boolean {
            if (host.isEmpty()) return false
            if (host.startsWith("[")) return host.endsWith("]") && host.length > 2
            // A single trailing dot is the root label written out; `nodeseek.com.` is a hostname.
            return host.removeSuffix(".").split('.').all { label ->
                label.isNotEmpty() &&
                    !label.startsWith('-') &&
                    !label.endsWith('-') &&
                    label.all { it.isAsciiLetter() || it.isAsciiDigit() || it == '-' }
            }
        }

        /**
         * `%XX` back to text, UTF-8 and all.
         *
         * A run of escapes is decoded together, so a character written as `%E4%B8%AD` comes back
         * whole; characters that were never escaped are copied as they stand, which keeps a pair of
         * surrogates from being taken apart.
         */
        private fun percentDecode(value: String): String = buildString(value.length) {
            val escaped = ByteArray(value.length / 3 + 1)
            var index = 0
            while (index < value.length) {
                if (value[index] != '%') {
                    append(value[index++])
                    continue
                }
                var count = 0
                while (index + 3 <= value.length && value[index] == '%') {
                    val octet = value.substring(index + 1, index + 3).toIntOrNull(radix = 16) ?: break
                    escaped[count++] = octet.toByte()
                    index += 3
                }
                if (count == 0) append(value[index++]) else append(escaped.decodeToString(endIndex = count))
            }
        }

        private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

        private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

        private fun Char.isHex(): Boolean = isAsciiDigit() || this in 'a'..'f' || this in 'A'..'F'

        /**
         * A non-ASCII letter is allowed through here and refused by [isHost], which is what `URI`
         * did: `https://例え.jp/` is a URL it parses and a host it will not vouch for. Rejecting it
         * outright would read the same to every caller and be a worse answer to give a browser.
         */
        private fun Char.isAllowedInUrl(): Boolean =
            isAsciiLetter() || isAsciiDigit() || this in OTHER_ALLOWED ||
                (code > 0x7F && !isISOControl() && !isWhitespace())

        /** RFC 2396's `mark` and `reserved` sets, plus the brackets RFC 2732 added for IPv6. */
        private const val OTHER_ALLOWED = "-_.!~*'();/?:@&=+$,[]#"

        /** Stands in for "this authority names no host at all"; no real port can collide with it. */
        private const val NO_AUTHORITY = -2
    }
}
