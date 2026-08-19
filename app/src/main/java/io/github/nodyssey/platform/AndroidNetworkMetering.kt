package io.github.nodyssey.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Whether the connection in use right now is one 仅 Wi-Fi 加载图片 should allow.
 *
 * All three capabilities are required, and `NOT_METERED` is the one that surprises people: a VPN
 * whose tunnel does not report it makes this false on Wi-Fi, so the switch skips images on what
 * looks to the user like Wi-Fi. That is the platform's answer, not a bug here — but it is why the
 * interceptor takes this as a function it is handed rather than asking the system itself.
 */
fun Context.hasValidatedUnmeteredNetwork(): Boolean {
    val connectivityManager = getSystemService(ConnectivityManager::class.java)
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}
