package io.github.plaza.designsys.component

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.browser.customtabs.CustomTabsService
import androidx.browser.customtabs.CustomTabsSessionToken
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Issue #140: a press on a link in a post made the app stop responding, because the press asked
 * the browser to get ready for it with a synchronous binder call on the main thread, and the
 * browser did not answer. Every call into the browser's process is synchronous, so the rule is that
 * none is made on the main thread — not the hint on a press, and not the warmup or the session
 * request on coming back to the app, which the main thread would be waiting on just the same.
 *
 * The browser here is local, so its binder calls arrive on whichever thread made them, and that is
 * what it writes down.
 */
@RunWith(RobolectricTestRunner::class)
class CustomTabsWarmerTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `no call into the browser is made on the main thread`() {
        val browser = RecordingBrowser()
        installBrowser(browser)
        val warmer = CustomTabsWarmer(context, scope)

        warmer.connect()
        awaitSession(warmer)
        warmer.mayLaunch(URL)

        assertTrue(browser.hinted.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("warmup", "newSession", "mayLaunchUrl"), browser.calls.map { it.first })
        assertEquals(emptyList<String>(), browser.calls.filter { it.second }.map { it.first })
    }

    /** The session comes back on the main thread once the browser has answered on the other one. */
    private fun awaitSession(warmer: CustomTabsWarmer) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (warmer.session == null && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        checkNotNull(warmer.session) { "the browser never produced a session" }
    }

    /**
     * Makes [browser] the default handler for web links and the one offering the Custom Tabs
     * service — the two questions `CustomTabsClient.getPackageName` asks — and what binding to it
     * returns.
     */
    private fun installBrowser(browser: CustomTabsService) {
        val packageManager = shadowOf(context.packageManager)
        packageManager.addResolveInfoForIntent(
            Intent(Intent.ACTION_VIEW, Uri.parse("http://")),
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply { packageName = BROWSER_PACKAGE }
            },
        )
        packageManager.addResolveInfoForIntent(
            Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION).setPackage(BROWSER_PACKAGE),
            ResolveInfo().apply {
                serviceInfo = ServiceInfo().apply { packageName = BROWSER_PACKAGE }
            },
        )
        shadowOf(context).setComponentNameAndServiceForBindService(
            ComponentName(BROWSER_PACKAGE, "Service"),
            browser.onBind(Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION)),
        )
    }

    private class RecordingBrowser : CustomTabsService() {
        /** Each call the browser received, and whether it arrived on the main thread. */
        val calls: MutableList<Pair<String, Boolean>> = Collections.synchronizedList(mutableListOf())
        val hinted = CountDownLatch(1)

        private fun record(name: String) {
            calls += name to (Looper.myLooper() == Looper.getMainLooper())
        }

        override fun warmup(flags: Long): Boolean = true.also { record("warmup") }

        override fun newSession(sessionToken: CustomTabsSessionToken): Boolean = true.also { record("newSession") }

        override fun mayLaunchUrl(
            sessionToken: CustomTabsSessionToken,
            url: Uri?,
            extras: Bundle?,
            otherLikelyBundles: MutableList<Bundle>?,
        ): Boolean {
            record("mayLaunchUrl")
            hinted.countDown()
            return true
        }

        override fun extraCommand(
            commandName: String,
            args: Bundle?,
        ): Bundle? = null

        override fun updateVisuals(
            sessionToken: CustomTabsSessionToken,
            bundle: Bundle?,
        ): Boolean = false

        override fun requestPostMessageChannel(
            sessionToken: CustomTabsSessionToken,
            postMessageOrigin: Uri,
        ): Boolean = false

        override fun postMessage(
            sessionToken: CustomTabsSessionToken,
            message: String,
            extras: Bundle?,
        ): Int = CustomTabsService.RESULT_FAILURE_DISALLOWED

        override fun validateRelationship(
            sessionToken: CustomTabsSessionToken,
            relation: Int,
            origin: Uri,
            extras: Bundle?,
        ): Boolean = false

        override fun receiveFile(
            sessionToken: CustomTabsSessionToken,
            uri: Uri,
            purpose: Int,
            extras: Bundle?,
        ): Boolean = false
    }

    private companion object {
        const val BROWSER_PACKAGE = "com.example.browser"
        const val URL = "https://www.nodeseek.com/"
    }
}
