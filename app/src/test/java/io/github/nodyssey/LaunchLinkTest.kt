package io.github.nodyssey

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What an Activity being handed the same intent twice is allowed to do with it.
 *
 * The bug this pins: `getIntent` keeps answering with the last intent the task was given, and the
 * system hands that same one back to every recreation — a rotation included. `onCreate` read it
 * unguarded, so every rotation after a site link had ever been followed switched the tab back to
 * 首页 and pushed the thread onto 首页's stack again, underneath whatever the reader was on. Back
 * then popped that stack and landed on the feed; the tab they were really in still had their own
 * stack intact, which is why 我的 brought the thread straight back.
 */
@RunWith(RobolectricTestRunner::class)
class LaunchLinkTest {
    private val siteLink =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.nodeseek.com/post-123456-1"))

    @Test
    fun `a fresh start follows the link it was given`() {
        val request = launchLinkOf(siteLink, isRecreation = false)

        assertEquals("https://www.nodeseek.com/post-123456-1", request?.url)
    }

    @Test
    fun `a recreation does not follow the link again`() {
        assertNull(launchLinkOf(siteLink, isRecreation = true))
    }

    @Test
    fun `a launcher start asks for nothing`() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        assertNull(launchLinkOf(launcher, isRecreation = false))
    }

    @Test
    fun `a notification start is not a link`() {
        val notification =
            Intent().putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_NOTIFICATIONS)

        assertNull(launchLinkOf(notification, isRecreation = false))
    }

    @Test
    fun `no intent at all is no link`() {
        assertNull(launchLinkOf(null, isRecreation = false))
    }
}
