package io.github.plaza.designsys.image

import androidx.test.core.app.ApplicationProvider
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.request.Options
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Asked against the real [CacheControlCacheStrategy] rather than a stand-in, because the claim being
 * made is about that arithmetic: an avatar the site has already declared stale is still served from
 * disk, and everything else still is not.
 */
@OptIn(ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
class LongLivedImageCacheStrategyTest {
    private val context: PlatformContext = ApplicationProvider.getApplicationContext()
    private val options = Options(context)

    /**
     * The delegate reads a clock to decide freshness, and is handed one stopped at [NOW] rather than
     * the machine's. Otherwise a fixture written as an absolute instant is fresh until the real date
     * catches up with it and stale on every run after that.
     */
    private fun cacheControl() = CacheControlCacheStrategy(now = { Instant.fromEpochMilliseconds(NOW) })

    private val strategy =
        LongLivedImageCacheStrategy(
            delegate = cacheControl(),
            isLongLived = { url -> url.startsWith("$SITE/avatar/") },
        )

    /** What the site answers with, as measured on 2026-08-22: four hours, and an `ETag` to revalidate against. */
    private fun served(at: Long) =
        NetworkResponse(
            code = 200,
            requestMillis = at,
            responseMillis = at,
            headers =
            NetworkHeaders
                .Builder()
                .set("Cache-Control", "public, max-age=14400")
                .set("ETag", "\"637e56f0-24b0e\"")
                .build(),
        )

    @Test
    fun `an avatar is stored with the app's own lifetime, not the site's`() =
        runTest {
            val result = strategy.write(null, NetworkRequest(AVATAR), served(NOW), options)

            assertEquals("public, max-age=604800", result.response?.headers?.get("Cache-Control"))
        }

    /** An attachment's address changes when its bytes do, so it has nothing to gain and a staleness to lose. */
    @Test
    fun `anything else keeps what the server said`() =
        runTest {
            val result = strategy.write(null, NetworkRequest(ATTACHMENT), served(NOW), options)

            assertEquals("public, max-age=14400", result.response?.headers?.get("Cache-Control"))
        }

    /**
     * The point of the whole class. Five hours in, the site's own four have run out and a feed of
     * fifty faces is fifty round trips to hear that nothing changed.
     */
    @Test
    fun `a five hour old avatar is served from disk instead of asked about`() =
        runTest {
            val asServed = served(NOW - 5.hours.inWholeMilliseconds)

            val baseline = cacheControl().read(asServed, NetworkRequest(AVATAR), options)
            assertNull("the site's own answer sends this one back to the server", baseline.response)

            val stored = requireNotNull(strategy.write(null, NetworkRequest(AVATAR), asServed, options).response)
            val ours = strategy.read(stored, NetworkRequest(AVATAR), options)
            assertNotNull("and this one has six more days of it", ours.response)
        }

    /** The exemption has to survive the round trip too, or it only looks like it works on a fresh cache. */
    @Test
    fun `a five hour old attachment still goes back to the server`() =
        runTest {
            val asServed = served(NOW - 5.hours.inWholeMilliseconds)

            val stored = requireNotNull(strategy.write(null, NetworkRequest(ATTACHMENT), asServed, options).response)
            val result = strategy.read(stored, NetworkRequest(ATTACHMENT), options)

            assertNull(result.response)
        }

    private companion object {
        const val SITE = "https://www.nodeseek.com"
        const val AVATAR = "$SITE/avatar/52425.png"
        const val ATTACHMENT = "https://img.example.test/photo.png"

        /**
         * Fixed rather than read off the clock: the arithmetic under test is about elapsed time. Only
         * safe to fix because [cacheControl] is told to read the same instant back.
         */
        const val NOW = 1_787_000_000_000L
    }
}
