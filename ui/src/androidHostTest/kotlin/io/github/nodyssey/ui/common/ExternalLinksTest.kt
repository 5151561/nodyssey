package io.github.nodyssey.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which links a Custom Tab is allowed to take.
 *
 * The handler replaces `LocalUriHandler` for the whole app, so it sees far more than post links: the
 * 联系方式 screen's `tg://`, the 两步验证 screen's `otpauth://`, 关于社区's `mailto:`. Handing any of
 * those to a browser would open a tab showing nothing instead of the app that owns the scheme, and
 * the 两步验证 screen in particular reads a failed launch to tell the user no authenticator is
 * installed — a Custom Tab that "succeeded" at showing an empty page would swallow that.
 *
 * The 站外链接 setting that used to be a third answer here is gone; see `ExternalLinks` for why.
 * What is left is the rule that never depended on it.
 */
class ExternalLinksTest {
    @Test
    fun `ordinary web links go to the custom tab`() {
        assertTrue(usesCustomTab("https://example.com/article"))
        assertTrue(usesCustomTab("http://legacy.example.com/"))
    }

    @Test
    fun `schemes owned by another app stay with the system handler`() {
        listOf(
            "mailto:admin@example.com",
            "tg://resolve?domain=example",
            "otpauth://totp/NodeSeek:me?secret=ABC",
            "javascript:alert(1)",
            "content://example.provider/item",
        ).forEach { uri ->
            assertFalse(uri, usesCustomTab(uri))
        }
    }
}
