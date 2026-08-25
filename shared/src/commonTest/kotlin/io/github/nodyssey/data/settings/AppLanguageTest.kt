package io.github.nodyssey.data.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which of the two Chinese bundles a device's own language tag asks for.
 *
 * The consequence of getting this wrong is not a fallback but the wrong language: [isTraditionalChineseTag]
 * is what a Traditional reader on `zh-Hant-HK` depends on to reach `values-zh-rTW` at all, and what
 * keeps every Simplified reader out of it. The function's own note has the resolution rule behind that.
 */
class AppLanguageTest {

    /** The three regions that write Chinese in the traditional script, with and without a script subtag. */
    @Test
    fun `traditional regions are recognised`() {
        assertTrue(isTraditionalChineseTag("zh-TW"))
        assertTrue(isTraditionalChineseTag("zh-HK"))
        assertTrue(isTraditionalChineseTag("zh-MO"))
        assertTrue(isTraditionalChineseTag("zh-Hant-TW"))
        assertTrue(isTraditionalChineseTag("zh-Hant-HK"))
    }

    /** A bare script with no region — what a device set to 繁體中文 with no country reports. */
    @Test
    fun `the script alone is enough`() {
        assertTrue(isTraditionalChineseTag("zh-Hant"))
    }

    @Test
    fun `simplified tags are not traditional`() {
        assertFalse(isTraditionalChineseTag("zh"))
        assertFalse(isTraditionalChineseTag("zh-CN"))
        assertFalse(isTraditionalChineseTag("zh-Hans-CN"))
        assertFalse(isTraditionalChineseTag("zh-SG"))
    }

    /**
     * A stated script wins over a region that disagrees with it.
     *
     * `zh-Hans-HK` is a real setting — a Simplified reader living in Hong Kong — and reading it off
     * the region alone would hand them the wrong bundle.
     */
    @Test
    fun `an explicit simplified script beats a traditional region`() {
        assertFalse(isTraditionalChineseTag("zh-Hans-HK"))
    }

    /** Underscores are what a JVM `Locale.toString()` hands over, and they have to parse the same. */
    @Test
    fun `underscores separate subtags too`() {
        assertTrue(isTraditionalChineseTag("zh_TW"))
        assertFalse(isTraditionalChineseTag("zh_CN"))
    }

    /** Case is not part of the answer: BCP 47 is case-insensitive and platforms disagree about it. */
    @Test
    fun `subtag case does not matter`() {
        assertTrue(isTraditionalChineseTag("ZH-hant-tw"))
        assertFalse(isTraditionalChineseTag("ZH-hans-cn"))
    }

    /** Everything that is not Chinese, including the tags that merely start with the letters. */
    @Test
    fun `other languages are never traditional chinese`() {
        assertFalse(isTraditionalChineseTag("en-US"))
        assertFalse(isTraditionalChineseTag("ja-JP"))
        assertFalse(isTraditionalChineseTag(""))
        assertFalse(isTraditionalChineseTag("zho-Hant"))
    }

    /**
     * The tag each entry asks the platform for.
     *
     * Pinned because these are also the resource directory names: `values-zh-rTW` answers to
     * [AppLanguage.TRADITIONAL_CHINESE] and to nothing else, so renaming one without the other is a
     * silent fall back to the default bundle.
     */
    @Test
    fun `each language names the tag its bundle is keyed by`() {
        assertEquals(null, AppLanguage.SYSTEM.tag)
        assertEquals("zh-CN", AppLanguage.SIMPLIFIED_CHINESE.tag)
        assertEquals("zh-TW", AppLanguage.TRADITIONAL_CHINESE.tag)
        assertEquals("en", AppLanguage.ENGLISH.tag)
    }

    /** A store written by an older build has no entry at all, and a bad one must not throw. */
    @Test
    fun `an unknown stored name reads as follow the system`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.ofName(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.ofName(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.ofName("KLINGON"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.ofName("ENGLISH"))
    }
}
