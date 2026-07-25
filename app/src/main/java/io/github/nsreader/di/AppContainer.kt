package io.github.nsreader.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.nsreader.core.AppDispatchers
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.NodeSeekClient
import io.github.nsreader.core.net.NodeSeekJsonClient
import io.github.nsreader.core.net.WebViewCookieJar
import io.github.nsreader.data.CategoryRepository
import io.github.nsreader.data.NetworkPostRepository
import io.github.nsreader.data.PostRepository
import io.github.nsreader.data.settings.SettingsRepository
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * The application's dependency graph.
 *
 * Manual constructor injection rather than Hilt: KSP has no release for Kotlin 2.3.x yet, and the
 * alternative (kapt) is the deprecated path. This interface keeps dependencies explicit and
 * swappable, which is what matters — see `docs/architecture.md` for the migration trigger.
 *
 * Nothing here is a global: the container is created by the Application and handed down. That is
 * what makes [FakeAppContainer]-style substitution possible in tests.
 */
interface AppContainer {
    val dispatchers: AppDispatchers
    val cookieJar: WebViewCookieJar
    val postRepository: PostRepository
    val categoryRepository: CategoryRepository
    val settingsRepository: SettingsRepository
    val okHttpClient: OkHttpClient
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)

class DefaultAppContainer(
    context: Context,
    override val dispatchers: AppDispatchers = AppDispatchers(),
) : AppContainer {

    private val appContext = context.applicationContext

    override val cookieJar: WebViewCookieJar by lazy { WebViewCookieJar() }

    override val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            // Applied to page *and* image requests, which both have to look like the mobile site.
            .addInterceptor { chain ->
                val request = chain.request()
                val builder = request.newBuilder()
                if (request.header("User-Agent") == null) {
                    builder.header("User-Agent", NodeSeekSite.USER_AGENT)
                }
                if (request.header("Referer") == null) {
                    builder.header("Referer", "${NodeSeekSite.BASE_URL}/")
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    private val htmlClient by lazy { NodeSeekClient(okHttpClient, dispatchers) }

    private val jsonClient by lazy { NodeSeekJsonClient(okHttpClient, dispatchers) }

    override val postRepository: PostRepository by lazy {
        NetworkPostRepository(htmlClient, dispatchers)
    }

    override val categoryRepository: CategoryRepository by lazy { CategoryRepository(jsonClient) }

    override val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(appContext.settingsDataStore)
    }
}
