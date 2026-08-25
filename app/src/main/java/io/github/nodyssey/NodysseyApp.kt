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
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.offline.AndroidOfflineFileStore
import io.github.nodyssey.data.offline.OfflineImageInterceptor
import io.github.nodyssey.data.offline.OfflineWork
import io.github.nodyssey.di.AndroidAppContainer
import io.github.nodyssey.di.DefaultAppContainer
import io.github.nodyssey.image.ImageNetworkPolicyInterceptor
import io.github.nodyssey.notifications.NotificationChannels
import io.github.nodyssey.notifications.NotificationPollScheduler
import io.github.nodyssey.platform.CompatSvgParser
import io.github.nodyssey.platform.hasValidatedUnmeteredNetwork
import io.github.plaza.designsys.image.LongLivedImageCacheStrategy
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
    lateinit var container: AndroidAppContainer
        private set

    /**
     * On-demand WorkManager initialization — the androidx.startup initializer is removed in the
     * manifest. On demand means the same code path initializes it in production and under
     * Robolectric, where the startup provider does not run before the Application does.
     *
     * The factory is what hands each worker its repositories out of [container]; as a lambda so the
     * read happens when a worker is built, which is always after `onCreate` has assigned it.
     */
    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(NodysseyWorkerFactory { container })
                .build()

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
     * The cache strategy is neither of the two Coil offers. `DefaultCacheStrategy.read` hands back
     * the cached response whenever there is one and never asks the server about it, which is
     * invisible for an attachment — its URL changes when its bytes do — and wrong for an avatar,
     * which the site serves from `/avatar/<uid>.png` for the life of the account, so changing a
     * picture would change nothing here forever. `CacheControlCacheStrategy` alone goes too far the
     * other way: the site's own `max-age` on an avatar is four hours and most of it is spent in the
     * CDN before the response arrives, so every few hours every face in a list needs a round trip to
     * be told it has not changed. [LongLivedImageCacheStrategy] keeps the second one's behaviour and
     * gives avatars a lifetime of the app's own choosing; its note has the measurements.
     */
    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                // Ahead of the data-usage policy on purpose: a downloaded picture is already on
                // this device, so 仅 Wi-Fi 加载图片 has nothing to defer about it.
                add(OfflineImageInterceptor(AndroidOfflineFileStore.of(filesDir)))
                add(
                    ImageNetworkPolicyInterceptor(
                        // The interceptor is handed both rather than asking: which key means
                        // 仅 Wi-Fi 加载图片 is a settings question and what counts as Wi-Fi is the
                        // platform's, and it is testable without either.
                        imagesOnWifiOnly = container.settingsRepository.settings.map { it.imagesOnWifiOnly },
                        hasUnmeteredNetwork = context::hasValidatedUnmeteredNetwork,
                    ),
                )
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { container.okHttpClient },
                        cacheStrategy = {
                            LongLivedImageCacheStrategy(
                                delegate = CacheControlCacheStrategy(),
                                isLongLived = NodeSeekSite::isAvatarUrl,
                            )
                        },
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
