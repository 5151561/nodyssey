package io.github.plaza.core.image

import coil3.Extras
import coil3.getExtra
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * Per-request opt-out from 仅 Wi-Fi 加载图片.
 *
 * The preference is about not spending mobile data *unasked*. A tap on a skipped image is the user
 * asking, so a request carrying this extra goes to the network on any connection. Without it the
 * switch is a wall rather than a default, and the only way to see one image is to go to 设置 and
 * turn the whole thing off.
 */
val AllowMeteredImage = Extras.Key(default = false)

/** Marks this request as one the user asked for by hand — see [AllowMeteredImage]. */
fun ImageRequest.Builder.allowMeteredImage(allow: Boolean): ImageRequest.Builder =
    apply { extras.set(AllowMeteredImage, allow) }

/**
 * Applies the image data-usage preference at the last boundary before Coil can use the network.
 *
 * [imagesOnWifiOnly] is a flow rather than a boolean because the setting is observable and this is
 * consulted per request; where that flow comes from — which DataStore key, under which name — is the
 * app's business, and the interceptor is deliberately not told.
 */
class ImageNetworkPolicyInterceptor(
    private val imagesOnWifiOnly: Flow<Boolean>,
    /** See `hasValidatedUnmeteredNetwork` — what counts as unmetered is the platform's to answer. */
    private val hasUnmeteredNetwork: () -> Boolean,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val deferred =
            shouldDeferImage(
                request = chain.request,
                imagesOnWifiOnly = imagesOnWifiOnly.first(),
                hasUnmeteredNetwork = hasUnmeteredNetwork(),
            )
        val request = if (deferred) chain.request.withoutNetwork() else chain.request
        val result = chain.withRequest(request).proceed()
        /*
         * Re-label the failure as ours.
         *
         * With the network disabled, a cache miss surfaces as an ordinary transport error, and the
         * viewer says "图片加载失败" — which sends the user to debug a network that is working fine.
         * It cost a real debugging session (2026-07-28: a freshly posted image "failed" on a phone
         * whose only problem was this switch plus a VPN whose tunnel does not report NOT_METERED).
         * The app declined on purpose; it should say so — and offer to fetch it anyway.
         */
        return if (deferred && result is ErrorResult) {
            ErrorResult(
                image = result.image,
                request = result.request,
                throwable = ImagesDeferredException(),
            )
        } else {
            result
        }
    }
}

/**
 * The app chose not to use the network for this image — 仅 Wi-Fi 加载图片 is on and this is not Wi-Fi.
 *
 * Not a failure of the request. The UI tells the two apart so it can name the switch instead of
 * blaming the network, and so it can offer 仍要加载 — which does work — instead of a 重试 that cannot.
 */
class ImagesDeferredException : IOException("Image skipped: images are limited to Wi-Fi")

/** True when this request must not touch the network, i.e. the user has not asked for it by hand. */
internal fun shouldDeferImage(
    request: ImageRequest,
    imagesOnWifiOnly: Boolean,
    hasUnmeteredNetwork: Boolean,
): Boolean =
    imagesOnWifiOnly && !hasUnmeteredNetwork && !request.getExtra(AllowMeteredImage)

/**
 * Disables only network reads. Coil can still satisfy the request from memory or its disk cache, so
 * enabling the preference does not blank images the user has already loaded.
 */
internal fun ImageRequest.withoutNetwork(): ImageRequest =
    newBuilder().networkCachePolicy(CachePolicy.DISABLED).build()
