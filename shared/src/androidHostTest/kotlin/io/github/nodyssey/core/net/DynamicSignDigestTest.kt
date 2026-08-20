package io.github.nodyssey.core.net

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * That [dynamicSign] really is a hex SHA-1, checked against the JVM's own.
 *
 * A differential test, and here for the same reason `WebUrlTest` and `NodeSeekSiteEncodingTest` are:
 * what it preserves is not a specification of this app's but the answer another implementation
 * gives. The value under test used to come *from* `MessageDigest` — the signature was an OkHttp
 * interceptor until the vote endpoints had to work on a platform with no JVM in it. This is what
 * keeps okio's `ByteString.sha1()` honest about being the same function.
 *
 * The vectors are the four shapes the site's own hook produces: a bare read, a write with a JSON
 * body, a URL with a query, and a payload with the multibyte characters a Chinese forum puts in one.
 */
class DynamicSignDigestTest {
    @Test
    fun `matches the platform SHA-1 for every shape the site signs`() {
        val vectors = listOf(
            listOf("GET", "https://www.nodeseek.com/api/vote/info/2871", UA, ""),
            listOf("POST", "https://www.nodeseek.com/api/vote/voteforitem", UA, """{"ids":[13201]}"""),
            listOf("GET", "https://www.nodeseek.com/api/vote/voter-of-item?id=1&page=2", UA, ""),
            listOf("POST", "https://www.nodeseek.com/api/vote/voteforitem", "$UA 中文", """{"note":"评分"}"""),
        )

        for (vector in vectors) {
            val (method, url, userAgent, body) = vector
            assertEquals(
                sha1Hex(vector.joinToString("\n\n")),
                dynamicSign(method, url, userAgent, body),
                "$method $url",
            )
        }
    }

    private fun sha1Hex(value: String): String =
        MessageDigest
            .getInstance("SHA-1")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 14) NodysseyTest"
    }
}
