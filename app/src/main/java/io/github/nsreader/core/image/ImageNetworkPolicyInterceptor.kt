package io.github.nsreader.core.image

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.ImageResult
import io.github.nsreader.data.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Applies the image data-usage preference at the last boundary before Coil can use the network. */
class ImageNetworkPolicyInterceptor internal constructor(
    private val settings: Flow<UserSettings>,
    private val hasUnmeteredNetwork: () -> Boolean,
) : Interceptor {
    constructor(context: Context, settings: Flow<UserSettings>) : this(
        settings = settings,
        hasUnmeteredNetwork = context::hasValidatedUnmeteredNetwork,
    )

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request =
            applyImageNetworkPolicy(
                request = chain.request,
                imagesOnWifiOnly = settings.first().imagesOnWifiOnly,
                hasUnmeteredNetwork = hasUnmeteredNetwork(),
            )
        return chain.withRequest(request).proceed()
    }
}

/**
 * Disables only network reads. Coil can still satisfy the request from memory or its disk cache, so
 * enabling the preference does not blank images the user has already loaded.
 */
internal fun applyImageNetworkPolicy(
    request: ImageRequest,
    imagesOnWifiOnly: Boolean,
    hasUnmeteredNetwork: Boolean,
): ImageRequest =
    if (imagesOnWifiOnly && !hasUnmeteredNetwork) {
        request.newBuilder().networkCachePolicy(CachePolicy.DISABLED).build()
    } else {
        request
    }

private fun Context.hasValidatedUnmeteredNetwork(): Boolean {
    val connectivityManager = getSystemService(ConnectivityManager::class.java)
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}
