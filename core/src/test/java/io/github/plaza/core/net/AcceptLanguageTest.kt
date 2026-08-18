package io.github.plaza.core.net

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AcceptLanguageTest {

    /** What a phone set to 简体中文 sends, and the case the fix was measured against. */
    @Test
    fun `a single chinese locale gets its bare language as a fallback`() {
        assertEquals("zh-CN,zh;q=0.9", acceptLanguage(listOf(Locale.forLanguageTag("zh-CN"))))
    }

    @Test
    fun `several preferred languages descend in quality`() {
        val value =
            acceptLanguage(
                listOf(Locale.forLanguageTag("zh-CN"), Locale.forLanguageTag("en-US")),
            )

        assertEquals("zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7", value)
    }

    /** A tag with no region has no bare-language form to add — and must not repeat itself. */
    @Test
    fun `a language without a region is listed once`() {
        assertEquals("ja", acceptLanguage(listOf(Locale.forLanguageTag("ja"))))
    }

    @Test
    fun `a language repeated across locales is listed once`() {
        val value =
            acceptLanguage(
                listOf(Locale.forLanguageTag("zh-CN"), Locale.forLanguageTag("zh-TW")),
            )

        assertEquals("zh-CN,zh;q=0.9,zh-TW;q=0.8", value)
    }

    /** The header exists to be sent; an empty device locale list must not produce an empty value. */
    @Test
    fun `no usable locale still yields a header`() {
        assertEquals("en-US,en;q=0.9", acceptLanguage(emptyList()))
        assertEquals("en-US,en;q=0.9", acceptLanguage(listOf(Locale.forLanguageTag(""))))
    }

    /** A long list is truncated rather than turned into a fingerprint of the user's settings. */
    @Test
    fun `the list is capped`() {
        val many =
            listOf("zh-CN", "en-US", "ja-JP", "ko-KR", "fr-FR", "de-DE", "es-ES")
                .map(Locale::forLanguageTag)

        val tags = acceptLanguage(many).split(",")

        assertEquals(6, tags.size)
        // The cap counts tags, not locales, so the bare-language forms take room too: zh-CN, zh,
        // en-US, en, ja-JP, ja — and the languages past that are simply not offered.
        assertEquals("zh-CN", tags.first())
        assertEquals("ja;q=0.5", tags.last())
    }

    /** Never a comma as the decimal separator, whatever the JVM's default locale happens to be. */
    @Test
    fun `quality values do not follow the default locale's number format`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("zh-CN,zh;q=0.9", acceptLanguage(listOf(Locale.forLanguageTag("zh-CN"))))
        } finally {
            Locale.setDefault(original)
        }
    }
}
