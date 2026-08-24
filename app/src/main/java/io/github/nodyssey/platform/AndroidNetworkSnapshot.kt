package io.github.nodyssey.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.net.toUri
import io.github.nodyssey.data.diagnostics.AppIdentity
import io.github.nodyssey.data.diagnostics.NetworkTransport
import io.github.plaza.designsys.component.customTabsProviderPackage

/**
 * What the platform will say about the connection in use, read in one pass.
 *
 * One object rather than four calls because the four have to describe the same moment: a reader
 * switching from Wi-Fi to mobile data while 网络自检 is running should not get a report that is half
 * one network and half the other.
 */
data class NetworkSnapshot(
    val transport: NetworkTransport,
    val vpnActive: Boolean,
    val metered: Boolean,
)

/**
 * The connection right now.
 *
 * [NetworkSnapshot.vpnActive] is read off the same capabilities as the rest rather than by looking
 * for a VPN app, which is the only way to see one that has no app — and the only way that keeps
 * working when the tunnel belongs to a work profile or to the system.
 *
 * Metering is the *platform's* answer and not derived from the transport. Those disagree more often
 * than anyone expects, and this screen exists to show disagreements: a tunnel that does not report
 * `NOT_METERED` makes Wi-Fi read as metered, which is the same bit 图片仅 Wi-Fi 加载 reads and the
 * same surprise — see [hasValidatedUnmeteredNetwork].
 */
fun Context.networkSnapshot(): NetworkSnapshot {
    val connectivityManager = getSystemService(ConnectivityManager::class.java)
    val active = connectivityManager?.activeNetwork
    val capabilities = active?.let(connectivityManager::getNetworkCapabilities)
        ?: return NetworkSnapshot(NetworkTransport.NONE, vpnActive = false, metered = false)
    return NetworkSnapshot(
        transport =
        when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
            else -> NetworkTransport.OTHER
        },
        vpnActive = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
        metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
    )
}

/** The app a link in a post would actually open in — see `customTabsProviderPackage`. */
fun Context.customTabsProvider(): AppIdentity? =
    customTabsProviderPackage(this)?.let(::appIdentity)

/**
 * The app the system hands an ordinary `http` link to.
 *
 * Shown beside [customTabsProvider] because the pair is the diagnosis: readers compare a Custom Tab
 * against "the browser", and where those two rows name different packages they have been comparing
 * two apps, not two ways of opening a link.
 *
 * Null where the system answers with its own chooser rather than an app, which is what a device with
 * no default browser set does. Requires the `ACTION_VIEW` + `http` entry in the manifest's
 * `<queries>`; without it API 30+ answers null for a browser that is plainly installed.
 */
fun Context.defaultBrowser(): AppIdentity? {
    val intent = Intent(Intent.ACTION_VIEW, "http://example.com".toUri())
    val resolved =
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?: return null
    val packageId = resolved.activityInfo?.packageName ?: return null
    // The system's own disambiguation activity, which is not a browser and whose label ("打开方式")
    // on this row would read as one.
    return if (resolved.isSystemChooser()) null else appIdentity(packageId)
}

private fun ResolveInfo.isSystemChooser(): Boolean =
    activityInfo?.packageName == "android" || activityInfo?.name?.contains("ResolverActivity") == true

/** Falls back to the package id where the label cannot be read, which is still the useful half. */
private fun Context.appIdentity(packageId: String): AppIdentity {
    val label =
        runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageId, 0)).toString()
        }.getOrNull()
    return AppIdentity(label = label ?: packageId, packageId = packageId)
}
