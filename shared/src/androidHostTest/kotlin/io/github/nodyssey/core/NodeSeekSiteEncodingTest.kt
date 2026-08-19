package io.github.nodyssey.core

import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The half of [NodeSeekSiteTest] that cannot be common, because what it asserts against is a JVM
 * class.
 *
 * Kept as a differential test rather than rewritten into hand-written expectations: what has to be
 * preserved is not a specification but the answers one particular encoder gave, and the characters
 * where the two disagree are exactly the ones a hand-written case would get wrong. Running only on
 * the JVM is the cost of that, and it is the right trade — the encoder under test is common, so any
 * change to it is caught here whatever platform the change was made for.
 */
class NodeSeekSiteEncodingTest {
    /**
     * The encoder these paths are built with, against the `java.net.URLEncoder` it replaced.
     *
     * Character for character rather than case by case, because the interesting characters are the
     * ones nobody thinks to write a case for: `URLEncoder` leaves `*` alone and escapes `~`, which
     * is the opposite of what `encodeURIComponent` does, and a search URL is what the site is asked
     * for and what the app caches under.
     */
    @Test
    fun `search paths encode exactly the way URLEncoder did`() {
        val queries = listOf(
            "花田",
            "Android TV",
            "a+b",
            "~!*'()",
            "-._",
            "100%",
            "a&b=c",
            "\"quoted\"",
            "🍜",
            "",
        )

        queries.forEach { query ->
            val expected = URLEncoder.encode(query, "UTF-8").replace("+", "%20")

            assertEquals("/member?q=$expected", NodeSeekSite.userSearchPath(query), query)
        }
    }
}
