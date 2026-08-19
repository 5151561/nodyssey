package io.github.nodyssey

import android.app.Application
import android.os.Build
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import io.github.nodyssey.data.offline.OfflineFileStore
import io.github.nodyssey.data.offline.OfflineImageInterceptor
import io.github.nodyssey.data.offline.OfflineWork
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.di.DefaultAppContainer
import io.github.nodyssey.notifications.NotificationChannels
import io.github.nodyssey.notifications.NotificationPollScheduler
import io.github.plaza.core.image.CompatSvgParser
import io.github.plaza.core.image.ImageNetworkPolicyInterceptor
import io.github.plaza.core.image.hasValidatedUnmeteredNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// Open only so the unit tests can install a subclass carrying Robolectric's teardown hook; see
// RobolectricApp in the test sources. Nothing in the app subclasses this.
open class NodysseyApp :
    Application(),
    SingletonImageLoader.Factory,
    Configuration.Provider {
    /** The one place the dependency graph is built. Everything else receives it. */
    lateinit var container: AppContainer
        private set

    /**
     * On-demand WorkManager initialization — the androidx.startup initializer is removed in the
     * manifest. On demand means the same code path initializes it in production and under
     * Robolectric, where the startup provider does not run before the Application does.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    /** Lives as long as the process; the launch update check and the settings watcher below use it. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)

        NotificationChannels.ensure(this)
        // 应用内更新 asks once per launch: the dot on 设置 and 我的 is there from the first frame, and a
        // release the user has not been told about raises the reminder dialog. Not forced, so the
        // stored answer covers most launches and GitHub is asked at most once every few hours; a
        // failure updates nothing and says nothing.
        //
        // Read once rather than collected: this is a decision made at launch, and a setting flipped
        // later takes effect at the next launch, which is the only thing it can mean.
        //
        // The updater is resolved here rather than inside the coroutine: building it reads the
        // installed version out of PackageManager, and doing that on a background coroutine that can
        // outlive the caller means doing it against an environment that may already be gone.
        val updates = container.appUpdateRepository
        applicationScope.launch {
            if (container.settingsRepository.settings.first().updateCheckOnLaunch) {
                updates.checkOnLaunch()
            }
        }
        // 离线内容保留 has to sweep a device nobody has opened 收藏 on for a fortnight, which is
        // exactly the case a check at screen-open misses. Scheduled unconditionally and cheap: the
        // sweep does nothing on a device that has downloaded nothing.
        OfflineWork.ensureMaintenance(this)
        // The scheduler follows the settings SSOT rather than being poked from the settings screen,
        // so the schedule is correct even when a setting changes without that screen ever opening.
        applicationScope.launch {
            container.settingsRepository.settings
                .map(NotificationPollScheduler::specOf)
                .distinctUntilChanged()
                .collect { spec -> NotificationPollScheduler.sync(this@NodysseyApp, spec) }
        }
    }

    /**
     * Images share the app's OkHttp client so avatars and attachments carry the same cookies and
     * browser headers as page requests — Cloudflare rejects them otherwise.
     *
     * The cache strategy is not the default one. Coil's `DefaultCacheStrategy.read` hands back the
     * cached response whenever there is one and never asks the server about it, which is invisible
     * for an attachment — its URL changes when its bytes do — and wrong for an avatar, which the
     * site serves from `/avatar/<uid>.png` for the life of the account. Changing your picture
     * changed nothing in the app, at any distance from the upload. [CacheControlCacheStrategy]
     * honours the `Cache-Control` and `ETag` the site actually sends on those responses, so a
     * cached avatar expires here on the same schedule it expires in a browser, and revalidating an
     * unchanged one costs a 304 rather than the image.
     */
    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                // Ahead of the data-usage policy on purpose: a downloaded picture is already on
                // this device, so 仅 Wi-Fi 加载图片 has nothing to defer about it.
                add(OfflineImageInterceptor(OfflineFileStore.of(filesDir)))
                add(
                    ImageNetworkPolicyInterceptor(
                        // The interceptor is `:core`'s and knows neither this app's settings nor this
                        // platform: which key means 仅 Wi-Fi 加载图片, and what counts as Wi-Fi, are
                        // both decided here.
                        imagesOnWifiOnly = container.settingsRepository.settings.map { it.imagesOnWifiOnly },
                        hasUnmeteredNetwork = context::hasValidatedUnmeteredNetwork,
                    ),
                )
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { container.okHttpClient },
                        cacheStrategy = { CacheControlCacheStrategy() },
                    ),
                )
                // An account that never uploaded a picture is served a generated cartoon *SVG* from
                // `/avatar/<uid>.png` — the extension lies, the Content-Type does not. Without this
                // decoder those all failed and every such user wore an initial instead.
                //
                // [CompatSvgParser] rather than the stock parser: the 测评 reports are SVG too, and
                // they use CSS units and text properties the underlying AndroidSVG never implemented.
                add(SvgDecoder.Factory(parser = CompatSvgParser()))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }.crossfade(true)
            .build()
}
