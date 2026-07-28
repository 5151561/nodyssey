package io.github.nodyssey.core.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.size.Size
import io.github.nodyssey.data.settings.UserSettings
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class ImageNetworkPolicyInterceptorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `metered network is blocked while memory and disk caches stay readable`() {
        val original = imageRequest()

        assertTrue(
            shouldDeferImage(
                request = original,
                imagesOnWifiOnly = true,
                hasUnmeteredNetwork = false,
            ),
        )

        val result = original.withoutNetwork()

        assertEquals(CachePolicy.DISABLED, result.networkCachePolicy)
        assertEquals(CachePolicy.ENABLED, result.memoryCachePolicy)
        assertEquals(CachePolicy.ENABLED, result.diskCachePolicy)
    }

    @Test
    fun `unmetered network remains available when the preference is enabled`() {
        assertFalse(
            shouldDeferImage(
                request = imageRequest(),
                imagesOnWifiOnly = true,
                hasUnmeteredNetwork = true,
            ),
        )
    }

    @Test
    fun `network remains available when the preference is disabled`() {
        assertFalse(
            shouldDeferImage(
                request = imageRequest(),
                imagesOnWifiOnly = false,
                hasUnmeteredNetwork = false,
            ),
        )
    }

    /**
     * 仅 Wi-Fi 加载图片 stops the app spending data on its own; it is not meant to stop the user.
     *
     * Without the opt-out the switch was a wall — a tap on a skipped image was refused just like the
     * automatic load was, and the only way to see one picture was to turn the preference off.
     */
    @Test
    fun `an image the user asked for by hand is not deferred`() {
        assertFalse(
            shouldDeferImage(
                request = imageRequest(allowMetered = true),
                imagesOnWifiOnly = true,
                hasUnmeteredNetwork = false,
            ),
        )
    }

    @Test
    fun `a hand-requested image reaches the network through the interceptor`() = runTest {
        val chain = FakeChain(imageRequest(allowMetered = true))
        val interceptor = ImageNetworkPolicyInterceptor(
            settings = flowOf(UserSettings(imagesOnWifiOnly = true)),
            hasUnmeteredNetwork = { false },
        )

        val result = interceptor.intercept(chain)

        assertEquals(CachePolicy.ENABLED, chain.proceeded?.networkCachePolicy)
        assertFalse((result as ErrorResult).throwable is ImagesDeferredException)
    }

    /**
     * A skipped image must not look like a broken one.
     *
     * Without this, the viewer says "图片加载失败" for an image the app deliberately did not fetch,
     * and the user goes looking for a network fault that does not exist — which is exactly what
     * happened on 2026-07-28.
     */
    @Test
    fun `a skipped image is reported as skipped, not as a load failure`() = runTest {
        val chain = FakeChain(imageRequest())
        val interceptor = ImageNetworkPolicyInterceptor(
            settings = flowOf(UserSettings(imagesOnWifiOnly = true)),
            hasUnmeteredNetwork = { false },
        )

        val result = interceptor.intercept(chain)

        assertEquals(CachePolicy.DISABLED, chain.proceeded?.networkCachePolicy)
        assertTrue(
            "expected ImagesDeferredException, was ${(result as? ErrorResult)?.throwable}",
            (result as ErrorResult).throwable is ImagesDeferredException,
        )
    }

    /** On Wi-Fi the interceptor is a pass-through, and a real error stays a real error. */
    @Test
    fun `a genuine failure keeps its own cause`() = runTest {
        val cause = IOException("connection reset")
        val chain = FakeChain(imageRequest(), failWith = cause)
        val interceptor = ImageNetworkPolicyInterceptor(
            settings = flowOf(UserSettings(imagesOnWifiOnly = true)),
            hasUnmeteredNetwork = { true },
        )

        val result = interceptor.intercept(chain)

        assertSame(cause, (result as ErrorResult).throwable)
    }

    private fun imageRequest(allowMetered: Boolean = false) =
        ImageRequest
            .Builder(context)
            .data("https://www.nodeseek.com/avatar/1.png")
            .allowMeteredImage(allowMetered)
            .build()

    /** Always errors, which is what a cache miss with the network disabled looks like to Coil. */
    private class FakeChain(
        override val request: ImageRequest,
        private val failWith: Throwable = IOException("cache miss"),
    ) : Interceptor.Chain {
        var proceeded: ImageRequest? = null

        override val size: Size get() = Size.ORIGINAL

        override fun withRequest(request: ImageRequest): Interceptor.Chain =
            FakeChain(request, failWith).also { proceeded = request }

        override fun withSize(size: Size): Interceptor.Chain = this

        override suspend fun proceed(): ImageResult =
            ErrorResult(image = null, request = request, throwable = failWith)
    }
}
