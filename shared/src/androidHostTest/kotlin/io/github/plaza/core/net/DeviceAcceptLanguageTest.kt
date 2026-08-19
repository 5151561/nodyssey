package io.github.plaza.core.net

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The half of `Accept-Language` that needs a JVM: turning the platform's locales into tags, and the
 * decimal separator the platform would have used if the value were formatted through it.
 *
 * The rest is in `AcceptLanguageTest` in `commonTest`, which is where the format itself is pinned.
 */
class DeviceAcceptLanguageTest {

    @Test
    fun `a locale's language tag is what the header carries`() {
        assertEquals("zh-CN,zh;q=0.9", acceptLanguage(languageTags(Locale.forLanguageTag("zh-CN"))))
    }

    /** `Locale("")` is how an unset locale arrives, and `und` is what the JVM calls it. */
    @Test
    fun `an empty locale still yields a header`() {
        assertEquals("en-US,en;q=0.9", acceptLanguage(languageTags(Locale.forLanguageTag(""))))
    }

    /** Never a comma as the decimal separator, whatever the JVM's default locale happens to be. */
    @Test
    fun `quality values do not follow the default locale's number format`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("zh-CN,zh;q=0.9", acceptLanguage(languageTags(Locale.forLanguageTag("zh-CN"))))
        } finally {
            Locale.setDefault(original)
        }
    }

    private fun languageTags(vararg locales: Locale): List<String> = locales.map { it.toLanguageTag() }
}
