package io.github.nodyssey.ui.common

import io.github.nodyssey.data.settings.ExternalLinkTarget
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
 */
class ExternalLinksTest {
    @Test
    fun `ordinary web links go to the custom tab`() {
        assertTrue(usesCustomTab("https://example.com/article", ExternalLinkTarget.CUSTOM_TAB))
        assertTrue(usesCustomTab("http://legacy.example.com/", ExternalLinkTarget.CUSTOM_TAB))
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
            assertFalse(uri, usesCustomTab(uri, ExternalLinkTarget.CUSTOM_TAB))
        }
    }

    @Test
    fun `choosing the browser sends even web links to the system handler`() {
        assertFalse(usesCustomTab("https://example.com/article", ExternalLinkTarget.BROWSER))
    }
}
