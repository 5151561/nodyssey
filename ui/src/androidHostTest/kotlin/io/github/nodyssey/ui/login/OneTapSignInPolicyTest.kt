package io.github.nodyssey.ui.login

import io.github.nodyssey.core.ActiveSite
import io.github.nodyssey.core.Site
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一键登录's half of [webViewPolicy]: what the screen is told to do with the page it lands on.
 *
 * The close condition is not retested here — [WebViewGoal.ONE_TAP_SIGN_IN] shares
 * [WebViewGoal.SIGN_IN]'s branch, and a test asserting that two arms of one `when` are the same arm
 * asserts the code's shape rather than its behaviour. What is worth pinning is the script: which
 * goals carry one, and that running it twice cannot open two popups.
 */
class OneTapSignInPolicyTest {

    @After
    fun restore() = ActiveSite.install(Site.DEFAULT)

    /**
     * The trigger runs at most once per page.
     *
     * `onPageFinished` fires more than once during this errand — the opener page keeps loading while
     * the popup runs — and a second click would open a second authorisation window on top of the
     * first. The guard is inside the script rather than in the caller because the script is what
     * crosses into the page, where the app has no state of its own.
     */
    @Test
    fun `the trigger script refuses to run twice`() {
        val script = requireNotNull(Site.DEEPFLOOD.oneTapSignIn).triggerScript
        assertTrue("no guard flag in the script", "__nodysseyOneTap" in script)
        // Set before the click, so a click that navigates cannot leave the flag unwritten.
        val flag = script.indexOf("window.__nodysseyOneTap = 1")
        val click = script.indexOf("b.click()")
        assertTrue("the guard must be set before the click, not after", flag in 0 until click)
    }

    /** It looks for the site's own button, and does nothing when the page has changed under it. */
    @Test
    fun `the trigger script gives up quietly when the button is gone`() {
        val script = requireNotNull(Site.DEEPFLOOD.oneTapSignIn).triggerScript
        assertTrue("the script no longer names the button it presses", "一键登录" in script)
        assertTrue("a missing button must be a no-op, not an error", "if (!b) return;" in script)
    }

    /** A site that signs its own readers in has no page to press, and gets no script. */
    @Test
    fun `only the one-tap errand carries a script`() {
        ActiveSite.install(Site.NODESEEK)
        assertNull(Site.NODESEEK.oneTapSignIn)

        ActiveSite.install(Site.DEEPFLOOD)
        val oneTap = requireNotNull(Site.DEEPFLOOD.oneTapSignIn)
        assertEquals("/nsSignIn.html", oneTap.path)
        assertEquals("NodeSeek", oneTap.providerName)
        // The provider's own authorisation URL must not be what the app opens: its result goes to
        // `window.opener`, which a top-level page does not have. See [OneTapSignIn.triggerScript].
        assertFalse("the app must open the site's page, not the handshake", "connect" in oneTap.path)
    }
}
