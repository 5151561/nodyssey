package io.github.nsreader.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.nsreader.core.AppClock
import io.github.nsreader.core.AppDispatchers
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.NodeSeekClient
import io.github.nsreader.core.net.NodeSeekJsonClient
import io.github.nsreader.core.net.UserAgent
import io.github.nsreader.core.net.WebViewCookieJar
import io.github.nsreader.core.net.resolveUserAgent
import io.github.nsreader.data.CategoryRepository
import io.github.nsreader.data.MessageRepository
import io.github.nsreader.data.NetworkMessageRepository
import io.github.nsreader.data.NetworkPostDataSource
import io.github.nsreader.data.NetworkProfileRepository
import io.github.nsreader.data.NetworkSearchRepository
import io.github.nsreader.data.NotificationRepository
import io.github.nsreader.data.OfflineFirstPostRepository
import io.github.nsreader.data.PostRepository
import io.github.nsreader.data.ProfileRepository
import io.github.nsreader.data.SearchRepository
import io.github.nsreader.data.composer.DefaultPostComposerRepository
import io.github.nsreader.data.composer.PostComposerRepository
import io.github.nsreader.data.local.NodeSeekDatabase
import io.github.nsreader.data.session.SessionRepository
import io.github.nsreader.data.settings.SettingsRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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
    val clock: AppClock
    val cookieJar: WebViewCookieJar
    val postRepository: PostRepository
    val categoryRepository: CategoryRepository
    val settingsRepository: SettingsRepository
    val notificationRepository: NotificationRepository
    val messageRepository: MessageRepository
    val profileRepository: ProfileRepository
    val searchRepository: SearchRepository
    val postComposerRepository: PostComposerRepository
    val sessionRepository: SessionRepository

    /**
     * The UA the WebView and OkHttp both use. Shared rather than duplicated: `cf_clearance` is issued
     * against the UA that solved the challenge and rejected for any other.
     */
    val userAgent: UserAgent
    val okHttpClient: OkHttpClient
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)

class DefaultAppContainer(
    context: Context,
    override val dispatchers: AppDispatchers = AppDispatchers(),
    override val clock: AppClock = AppClock.System,
) : AppContainer {
    private val appContext = context.applicationContext

    override val cookieJar: WebViewCookieJar by lazy { WebViewCookieJar() }

    override val userAgent: UserAgent by lazy { resolveUserAgent(appContext) }

    override val okHttpClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            // Applied to page *and* image requests, which both have to look like the mobile site.
            .addInterceptor { chain ->
                val request = chain.request()
                val builder = request.newBuilder()
                if (request.header("User-Agent") == null) {
                    builder.header("User-Agent", userAgent.value)
                }
                if (request.header("Referer") == null) {
                    builder.header("Referer", "${NodeSeekSite.BASE_URL}/")
                }
                chain.proceed(builder.build())
            }.build()
    }

    private val htmlClient by lazy { NodeSeekClient(okHttpClient, dispatchers) }

    private val jsonClient by lazy { NodeSeekJsonClient(okHttpClient, dispatchers) }

    /** The offline-first SSOT. Everything below reads from it; only the data sources write to it. */
    private val database by lazy { NodeSeekDatabase.create(appContext) }

    private val remotePosts by lazy { NetworkPostDataSource(htmlClient, dispatchers) }

    override val postRepository: PostRepository by lazy {
        OfflineFirstPostRepository(database, remotePosts, clock)
    }

    override val categoryRepository: CategoryRepository by lazy {
        CategoryRepository(jsonClient, database.boardDao(), clock)
    }

    override val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(appContext.settingsDataStore)
    }

    override val notificationRepository: NotificationRepository by lazy {
        NotificationRepository(jsonClient)
    }

    override val messageRepository: MessageRepository by lazy {
        NetworkMessageRepository(jsonClient)
    }

    override val profileRepository: ProfileRepository by lazy {
        NetworkProfileRepository(htmlClient, jsonClient)
    }

    override val searchRepository: SearchRepository by lazy {
        NetworkSearchRepository(htmlClient, jsonClient, dispatchers)
    }

    override val postComposerRepository: PostComposerRepository by lazy {
        DefaultPostComposerRepository(appContext, okHttpClient, dispatchers, clock)
    }

    /**
     * Shares the cookie jar rather than owning a store of its own: the cookies OkHttp sends and the
     * ones the WebView collects have to be the same cookies, or "am I signed in" gets two answers.
     */
    override val sessionRepository: SessionRepository by lazy { SessionRepository(cookieJar) }
}
