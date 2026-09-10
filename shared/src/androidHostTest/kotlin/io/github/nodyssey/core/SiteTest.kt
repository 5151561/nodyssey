package io.github.nodyssey.core

import io.github.nodyssey.data.local.databaseFileName
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What changes when the app is pointed at the other forum, and — more of this file — what must not.
 *
 * [ActiveSite] is process-global, so every test here puts it back in [restore]. Gradle runs test
 * classes in one JVM and in alphabetical order, which is exactly the arrangement where a leaked
 * global makes some *other* file fail and this one pass.
 */
class SiteTest {

    @AfterTest
    fun restore() = ActiveSite.install(Site.DEFAULT)

    /**
     * The existing install's files keep their existing names.
     *
     * The single most expensive mistake available here: a suffix on NodeSeek renames `nodeseek.db`
     * and the composer stores, and a renamed store reads — from the app's side — as a user who has
     * never opened the app. Collections, read marks and the offline library would all be "empty"
     * rather than missing, which is not a failure anybody reports as a bug.
     */
    @Test
    fun `nodeseek keeps unsuffixed storage names`() {
        assertEquals("", Site.NODESEEK.storageSuffix)
        assertEquals("nodeseek.db", databaseFileName(Site.NODESEEK))
    }

    /** And the second site is somewhere else entirely — see [databaseFileName] for why it must be. */
    @Test
    fun `deepflood stores itself apart from nodeseek`() {
        assertNotEquals(databaseFileName(Site.NODESEEK), databaseFileName(Site.DEEPFLOOD))
        assertNotEquals(Site.NODESEEK.storageSuffix, Site.DEEPFLOOD.storageSuffix)
    }

    /** A stored value that no longer names a site must land on the default, not throw. */
    @Test
    fun `unknown stored site falls back to the default`() {
        assertEquals(Site.DEFAULT, Site.ofStoredValue(null))
        assertEquals(Site.DEFAULT, Site.ofStoredValue(""))
        assertEquals(Site.DEFAULT, Site.ofStoredValue("A_SITE_THAT_WAS_REMOVED"))
        Site.entries.forEach { assertEquals(it, Site.ofStoredValue(it.storedValue)) }
    }

    /** Every URL the app builds follows the active site, without a single call site changing. */
    @Test
    fun `base url follows the active site`() {
        ActiveSite.install(Site.NODESEEK)
        assertEquals("https://www.nodeseek.com", NodeSeekSite.BASE_URL)
        assertEquals("https://www.nodeseek.com/post-1-1", NodeSeekSite.BASE_URL + NodeSeekSite.postPath(1))
        assertEquals("https://www.nodeseek.com", NodeSeekSite.CONFIG.baseUrl)

        ActiveSite.install(Site.DEEPFLOOD)
        assertEquals("https://www.deepflood.com", NodeSeekSite.BASE_URL)
        assertEquals("https://www.deepflood.com/post-1-1", NodeSeekSite.BASE_URL + NodeSeekSite.postPath(1))
        assertEquals("https://www.deepflood.com", NodeSeekSite.CONFIG.baseUrl)
    }

    /**
     * The 一键登录 hop: on DeepFlood the web view must be allowed onto nodeseek.com, because
     * DeepFlood's own sign-in page opens `nodeseek.com/connect?target=DeepFlood` to do the signing.
     */
    @Test
    fun `web view may follow the sign-in provider`() {
        ActiveSite.install(Site.DEEPFLOOD)
        assertTrue(NodeSeekSite.isTrustedWebViewUrl("https://www.nodeseek.com/connect?target=DeepFlood"))
        assertTrue(NodeSeekSite.isTrustedWebViewUrl("https://www.deepflood.com/signIn.html"))
    }

    /**
     * The other half of that, and the reason the two host sets are not one.
     *
     * A `nodeseek.com/post-123` link written *inside a DeepFlood post* is a link to a different
     * forum, where 123 is a different thread. Routing it to a native screen would open DeepFlood's
     * thread 123 under NodeSeek's link — silently, and looking entirely normal.
     */
    @Test
    fun `another site's post link is not an internal route`() {
        ActiveSite.install(Site.DEEPFLOOD)
        assertNull(NodeSeekSite.parseInternalRoute("https://www.nodeseek.com/post-123-1"))
        assertTrue(NodeSeekSite.isExternalWebUrl("https://www.nodeseek.com/post-123-1"))
        // Its own links still route.
        assertNotNull(NodeSeekSite.parseInternalRoute("https://www.deepflood.com/post-123-1"))

        ActiveSite.install(Site.NODESEEK)
        assertNull(NodeSeekSite.parseInternalRoute("https://www.deepflood.com/post-123-1"))
        // NodeSeek hands sign-in to nobody, so nothing widens its web view.
        assertFalse(NodeSeekSite.isTrustedWebViewUrl("https://www.deepflood.com/signIn.html"))
    }

    /**
     * Every site can render the native sign-in form.
     *
     * Asserted as "has a key", not "has *this* key": the two sharing one Turnstile application is a
     * measurement (see [Site.turnstileSitekey]), and a site that later gets its own must not fail
     * here. What would be a real defect is a site reaching the form with nothing to render.
     */
    @Test
    fun `every site has a turnstile sitekey`() {
        Site.entries.forEach { site ->
            ActiveSite.install(site)
            assertTrue(
                NodeSeekSite.TURNSTILE_SITEKEY.isNotBlank(),
                "${site.displayName} has no Turnstile sitekey",
            )
        }
    }

    /**
     * 一键登录 opens the *site's own* page for it, not the provider's authorisation URL.
     *
     * The distinction is the point: `deepflood.com/nsSignIn.html` is a page whose own script knows
     * how the flow finishes, while `nodeseek.com/connect?target=DeepFlood` is the middle of it. A
     * URL built on the provider's origin here would be the app reproducing a handshake it has never
     * established — see [Site.oneTapSignIn].
     */
    @Test
    fun `one-tap sign-in opens the active site's own page`() {
        ActiveSite.install(Site.DEEPFLOOD)
        val oneTap = assertNotNull(Site.DEEPFLOOD.oneTapSignIn)
        val url = NodeSeekSite.BASE_URL + oneTap.path
        assertEquals("https://www.deepflood.com/nsSignIn.html", url)
        // And the web view is allowed to follow it onward to the provider.
        assertTrue(NodeSeekSite.isTrustedWebViewUrl(url))
        assertTrue(NodeSeekSite.isTrustedWebViewUrl("https://www.nodeseek.com/connect?target=DeepFlood"))
    }

    /** NodeSeek signs its own readers in, so its card shows no such button. */
    @Test
    fun `a site that signs its own readers in offers no one-tap`() {
        assertNull(Site.NODESEEK.oneTapSignIn)
        assertTrue(Site.NODESEEK.signInProviderHosts.isEmpty())
    }

    /** Both sites' fallback strips exist; a site with none would draw an empty bar on a cold start. */
    @Test
    fun `every site has fallback boards`() {
        Site.entries.forEach { site ->
            ActiveSite.install(site)
            assertTrue(NodeSeekSite.categories.isNotEmpty(), "${site.displayName} has no fallback boards")
        }
    }
}
