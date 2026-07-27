package io.github.nsreader.core.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageNetworkPolicyInterceptorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `metered network is blocked while memory and disk caches stay readable`() {
        val original = imageRequest()

        val result =
            applyImageNetworkPolicy(
                request = original,
                imagesOnWifiOnly = true,
                hasUnmeteredNetwork = false,
            )

        assertEquals(CachePolicy.DISABLED, result.networkCachePolicy)
        assertEquals(CachePolicy.ENABLED, result.memoryCachePolicy)
        assertEquals(CachePolicy.ENABLED, result.diskCachePolicy)
    }

    @Test
    fun `unmetered network remains available when the preference is enabled`() {
        val original = imageRequest()

        val result =
            applyImageNetworkPolicy(
                request = original,
                imagesOnWifiOnly = true,
                hasUnmeteredNetwork = true,
            )

        assertSame(original, result)
        assertEquals(CachePolicy.ENABLED, result.networkCachePolicy)
    }

    @Test
    fun `network remains available when the preference is disabled`() {
        val original = imageRequest()

        val result =
            applyImageNetworkPolicy(
                request = original,
                imagesOnWifiOnly = false,
                hasUnmeteredNetwork = false,
            )

        assertSame(original, result)
        assertEquals(CachePolicy.ENABLED, result.networkCachePolicy)
    }

    private fun imageRequest() =
        ImageRequest
            .Builder(context)
            .data("https://www.nodeseek.com/avatar/1.png")
            .build()
}
