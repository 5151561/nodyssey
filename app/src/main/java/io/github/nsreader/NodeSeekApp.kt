package io.github.nsreader

import android.app.Application
import android.os.Build
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import io.github.nsreader.core.image.ImageNetworkPolicyInterceptor
import io.github.nsreader.di.AppContainer
import io.github.nsreader.di.DefaultAppContainer
import io.github.nsreader.notifications.NotificationChannels
import io.github.nsreader.notifications.NotificationPollScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NodeSeekApp :
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

    /** Lives as long as the process; the settings watcher below is the only tenant. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)

        NotificationChannels.ensure(this)
        // The scheduler follows the settings SSOT rather than being poked from the settings screen,
        // so the schedule is correct even when a setting changes without that screen ever opening.
        applicationScope.launch {
            container.settingsRepository.settings
                .map(NotificationPollScheduler::specOf)
                .distinctUntilChanged()
                .collect { spec -> NotificationPollScheduler.sync(this@NodeSeekApp, spec) }
        }
    }

    /**
     * Images share the app's OkHttp client so avatars and attachments carry the same cookies and
     * browser headers as page requests — Cloudflare rejects them otherwise.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                add(ImageNetworkPolicyInterceptor(context, container.settingsRepository.settings))
                add(OkHttpNetworkFetcherFactory(callFactory = { container.okHttpClient }))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }.crossfade(true)
            .build()
}
