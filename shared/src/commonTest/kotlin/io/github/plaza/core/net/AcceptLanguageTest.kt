package io.github.plaza.core.net

import kotlin.test.Test
import kotlin.test.assertEquals

class AcceptLanguageTest {

    /** What a phone set to 简体中文 sends, and the case the fix was measured against. */
    @Test
    fun `a single chinese locale gets its bare language as a fallback`() {
        assertEquals("zh-CN,zh;q=0.9", acceptLanguage(listOf("zh-CN")))
    }

    @Test
    fun `several preferred languages descend in quality`() {
        assertEquals("zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7", acceptLanguage(listOf("zh-CN", "en-US")))
    }

    /** A tag with no region has no bare-language form to add — and must not repeat itself. */
    @Test
    fun `a language without a region is listed once`() {
        assertEquals("ja", acceptLanguage(listOf("ja")))
    }

    @Test
    fun `a language repeated across locales is listed once`() {
        assertEquals("zh-CN,zh;q=0.9,zh-TW;q=0.8", acceptLanguage(listOf("zh-CN", "zh-TW")))
    }

    /** A script subtag is not a language: the bare form is the first subtag, not everything but the last. */
    @Test
    fun `a script-qualified tag falls back to its language`() {
        assertEquals("zh-Hans-CN,zh;q=0.9", acceptLanguage(listOf("zh-Hans-CN")))
    }

    /** The header exists to be sent; an empty device locale list must not produce an empty value. */
    @Test
    fun `no usable locale still yields a header`() {
        assertEquals("en-US,en;q=0.9", acceptLanguage(emptyList()))
        assertEquals("en-US,en;q=0.9", acceptLanguage(listOf("")))
        // What an unparseable locale's language tag is on the JVM, and the reason the check is by
        // name rather than by emptiness.
        assertEquals("en-US,en;q=0.9", acceptLanguage(listOf("und")))
    }

    /** A long list is truncated rather than turned into a fingerprint of the user's settings. */
    @Test
    fun `the list is capped`() {
        val many = listOf("zh-CN", "en-US", "ja-JP", "ko-KR", "fr-FR", "de-DE", "es-ES")

        val tags = acceptLanguage(many).split(",")

        assertEquals(6, tags.size)
        // The cap counts tags, not locales, so the bare-language forms take room too: zh-CN, zh,
        // en-US, en, ja-JP, ja — and the languages past that are simply not offered.
        assertEquals("zh-CN", tags.first())
        assertEquals("ja;q=0.5", tags.last())
    }
}
