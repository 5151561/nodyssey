package io.github.nodyssey

import android.content.Intent
import android.net.Uri
import io.github.nodyssey.data.AttendanceMode
import io.github.nodyssey.ui.composer.MAX_IMAGES_PER_PICK
import io.github.nodyssey.ui.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What an intent from outside the app is allowed to ask for, and what an Activity being handed the
 * same intent twice is allowed to do with it.
 *
 * The recreation case pins a real bug: `getIntent` keeps answering with the last intent the task was
 * given, and the system hands that same one back to every recreation — a rotation included.
 * `onCreate` read it unguarded, so every rotation after a site link had ever been followed switched
 * the tab back to 首页 and pushed the thread onto 首页's stack again, underneath whatever the reader
 * was on. A share replayed that way would add its pictures to the editor a second time.
 */
@RunWith(RobolectricTestRunner::class)
class LaunchRequestTest {
    private val siteLink =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.nodeseek.com/post-123456-1"))

    @Test
    fun `a fresh start follows the link it was given`() {
        val request = launchRequestOf(siteLink, isRecreation = false)

        assertEquals(LaunchRequest.OpenLink("https://www.nodeseek.com/post-123456-1"), request)
    }

    @Test
    fun `a recreation does not follow the link again`() {
        assertNull(launchRequestOf(siteLink, isRecreation = true))
    }

    @Test
    fun `a recreation does not replay a share`() {
        val share = textShare("hello")

        assertNull(launchRequestOf(share, isRecreation = true))
    }

    @Test
    fun `a launcher start asks for nothing`() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        assertNull(launchRequestOf(launcher, isRecreation = false))
    }

    @Test
    fun `shared text goes to the composer with its subject as the title`() {
        val share = textShare("https://example.com/article").putExtra(Intent.EXTRA_SUBJECT, "An article")

        assertEquals(
            LaunchRequest.ShareToComposer(
                title = "An article",
                text = "https://example.com/article",
                images = emptyList(),
            ),
            requestOf(share),
        )
    }

    @Test
    fun `shared text is capped`() {
        val request = requestOf(textShare("x".repeat(50_000))) as LaunchRequest.ShareToComposer

        assertTrue(request.text!!.length < 50_000)
    }

    @Test
    fun `one shared picture goes to the composer`() {
        val share =
            Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://media/external/images/media/42"))

        val request = requestOf(share) as LaunchRequest.ShareToComposer

        assertEquals(listOf("content://media/external/images/media/42"), request.images.map { it.source })
        assertNull(request.text)
    }

    @Test
    fun `a file address is refused`() {
        val share =
            Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, Uri.parse("file:///data/data/io.github.nodyssey/databases/x.png"))

        assertNull(requestOf(share))
    }

    @Test
    fun `a picture from this app's own provider is refused`() {
        val share =
            Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://io.github.nodyssey.fileprovider/share/a.png"))

        assertNull(requestOf(share, ownPackage = "io.github.nodyssey"))
    }

    @Test
    fun `several shared pictures are capped at one pick`() {
        val uris =
            ArrayList((1..20).map { Uri.parse("content://media/external/images/media/$it") })
        val share =
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("image/jpeg")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)

        val request = requestOf(share) as LaunchRequest.ShareToComposer

        assertEquals(MAX_IMAGES_PER_PICK, request.images.size)
        assertEquals("content://media/external/images/media/1", request.images.first().source)
    }

    @Test
    fun `a share that is not a picture or text asks for nothing`() {
        val share =
            Intent(Intent.ACTION_SEND)
                .setType("video/mp4")
                .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://media/external/video/media/7"))

        assertNull(requestOf(share))
    }

    @Test
    fun `selected text is searched for`() {
        val selection =
            Intent(Intent.ACTION_PROCESS_TEXT)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT, "  甲骨文  ")

        assertEquals(LaunchRequest.Search("甲骨文"), requestOf(selection))
    }

    @Test
    fun `a blank selection asks for nothing`() {
        val selection =
            Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").putExtra(Intent.EXTRA_PROCESS_TEXT, "  ")

        assertNull(requestOf(selection))
    }

    @Test
    fun `the search shortcut opens an empty search`() {
        assertEquals(LaunchRequest.Search(null), requestOf(shortcut(MainActivity.SHORTCUT_SEARCH)))
    }

    @Test
    fun `the compose shortcut opens the editor`() {
        assertEquals(LaunchRequest.OpenComposer, requestOf(shortcut(MainActivity.SHORTCUT_COMPOSE)))
    }

    @Test
    fun `the notifications shortcut opens the tab`() {
        val notifications =
            Intent(Intent.ACTION_VIEW).putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_NOTIFICATIONS)

        assertEquals(LaunchRequest.OpenTab(TopLevelDestination.NOTIFICATIONS), requestOf(notifications))
    }

    @Test
    fun `the two sign-in shortcuts each name their own mode`() {
        assertEquals(
            LaunchRequest.SignInForToday(AttendanceMode.RANDOM),
            requestOf(shortcut(MainActivity.SHORTCUT_SIGN_IN_RANDOM)),
        )
        assertEquals(
            LaunchRequest.SignInForToday(AttendanceMode.FIXED_FIVE),
            requestOf(shortcut(MainActivity.SHORTCUT_SIGN_IN_FIXED)),
        )
    }

    @Test
    fun `an unknown shortcut asks for nothing`() {
        assertNull(requestOf(shortcut("settings")))
    }

    private fun textShare(text: String): Intent =
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)

    private fun shortcut(value: String): Intent =
        Intent(Intent.ACTION_VIEW).putExtra(MainActivity.EXTRA_SHORTCUT, value)
}
