package io.github.nodyssey.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import coil3.SingletonImageLoader
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.NodysseyRelease
import io.github.nodyssey.core.net.AppProxyAuthenticator
import io.github.nodyssey.core.net.AppProxySelector
import io.github.nodyssey.core.net.AppSocksAuthenticator
import io.github.nodyssey.core.net.DynamicSignInterceptor
import io.github.nodyssey.core.net.LiveProxyConfig
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.core.net.ProxyClientKind
import io.github.nodyssey.data.AppCacheStore
import io.github.nodyssey.data.AssetsRepository
import io.github.nodyssey.data.AwardRepository
import io.github.nodyssey.data.CategoryRepository
import io.github.nodyssey.data.CoilImageCaches
import io.github.nodyssey.data.CollectedPostMetaStore
import io.github.nodyssey.data.CommunityRepository
import io.github.nodyssey.data.CreditRepository
import io.github.nodyssey.data.DefaultAppCacheStore
import io.github.nodyssey.data.FollowRepository
import io.github.nodyssey.data.MessageRepository
import io.github.nodyssey.data.NetworkAssetsRepository
import io.github.nodyssey.data.NetworkAwardRepository
import io.github.nodyssey.data.NetworkCommunityRepository
import io.github.nodyssey.data.NetworkCreditRepository
import io.github.nodyssey.data.NetworkFollowRepository
import io.github.nodyssey.data.NetworkMessageRepository
import io.github.nodyssey.data.NetworkPostDataSource
import io.github.nodyssey.data.NetworkProfileRepository
import io.github.nodyssey.data.NetworkRulingRepository
import io.github.nodyssey.data.NetworkSearchRepository
import io.github.nodyssey.data.NetworkStardustRepository
import io.github.nodyssey.data.NetworkTermsRepository
import io.github.nodyssey.data.NetworkUserSpaceRepository
import io.github.nodyssey.data.NetworkVoteRepository
import io.github.nodyssey.data.NotificationRepository
import io.github.nodyssey.data.OfflineFirstPostRepository
import io.github.nodyssey.data.OfflineLibrary
import io.github.nodyssey.data.OfflineThreadReader
import io.github.nodyssey.data.PostCollectionWriter
import io.github.nodyssey.data.PostReactionWriter
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.ReadingPositionStore
import io.github.nodyssey.data.RoomCollectedPostMetaStore
import io.github.nodyssey.data.RoomReadingPositionStore
import io.github.nodyssey.data.RulingRepository
import io.github.nodyssey.data.SearchRepository
import io.github.nodyssey.data.StardustRepository
import io.github.nodyssey.data.TermsRepository
import io.github.nodyssey.data.UserSpaceRepository
import io.github.nodyssey.data.VoteRepository
import io.github.nodyssey.data.account.AccountSettingsRepository
import io.github.nodyssey.data.account.NetworkAccountSettingsRepository
import io.github.nodyssey.data.composer.CommentComposerRepository
import io.github.nodyssey.data.composer.DefaultCommentComposerRepository
import io.github.nodyssey.data.composer.DefaultPostComposerRepository
import io.github.nodyssey.data.composer.DefaultPostEditor
import io.github.nodyssey.data.composer.ImageHostUploader
import io.github.nodyssey.data.composer.ImageUploader
import io.github.nodyssey.data.composer.PostComposerRepository
import io.github.nodyssey.data.composer.PostEditor
import io.github.nodyssey.data.imagehost.DataStoreImageHostSettings
import io.github.nodyssey.data.imagehost.DefaultImageHostRepository
import io.github.nodyssey.data.imagehost.ImageHostRepository
import io.github.nodyssey.data.offline.OfflineFileStore
import io.github.nodyssey.data.offline.OfflineSettingsStore
import io.github.nodyssey.data.offline.OkHttpOfflineImageSource
import io.github.nodyssey.data.offline.RoomOfflineLibrary
import io.github.nodyssey.data.offline.WorkManagerOfflineScheduler
import io.github.nodyssey.data.proxy.DataStoreProxySettings
import io.github.nodyssey.data.proxy.NetworkProxyConnectionTester
import io.github.nodyssey.data.proxy.ProxyConnectionTester
import io.github.nodyssey.data.proxy.ProxySettings
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.update.AppUpdateRepository
import io.github.nodyssey.data.update.DefaultAppUpdateRepository
import io.github.nodyssey.platform.ApkInstaller
import io.github.nodyssey.platform.DefaultImagePreparer
import io.github.nodyssey.platform.KeystoreSecretCipher
import io.github.nodyssey.platform.createNodeSeekDatabase
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.AppVersion
import io.github.plaza.core.net.BrowserHeadersInterceptor
import io.github.plaza.core.net.CrossOriginRefererInterceptor
import io.github.plaza.core.net.OkHttpTransport
import io.github.plaza.core.net.SessionCookies
import io.github.plaza.core.net.SiteHtmlClient
import io.github.plaza.core.net.UserAgent
import io.github.plaza.core.net.WebViewCookieJar
import io.github.plaza.core.net.WebViewCookieStore
import io.github.plaza.core.net.deviceAcceptLanguage
import io.github.plaza.core.net.resolveUserAgent
import io.github.plaza.core.readAppVersion
import io.github.plaza.core.runCatchingExceptCancellation
import io.github.plaza.core.update.UpdateManifestSource
import io.github.plaza.core.update.isPreReleaseVersionName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The application's dependency graph.
 *
 * Manual constructor injection rather than Hilt: the graph is still small enough that a framework
 * would add more machinery than value. This interface keeps dependencies explicit and swappable;
 * see `docs/architecture.md` for the migration trigger.
 *
 * Nothing here is a global: the container is created by the Application and handed down. That is
 * what makes [FakeAppContainer]-style substitution possible in tests.
 */
interface AppContainer {
    val dispatchers: AppDispatchers
    val clock: AppClock

    /** What the shared cookie store says about this session. See [SessionCookies]. */
    val sessionCookies: SessionCookies
    val postRepository: PostRepository

    /** Where each thread was left off — its own store, not part of a screen's settings. */
    val readingPositionStore: ReadingPositionStore
    val categoryRepository: CategoryRepository
    val settingsRepository: SettingsRepository
    val notificationRepository: NotificationRepository
    val messageRepository: MessageRepository
    val profileRepository: ProfileRepository
    val searchRepository: SearchRepository
    val postComposerRepository: PostComposerRepository
    val accountSettingsRepository: AccountSettingsRepository
    val commentComposerRepository: CommentComposerRepository

    /** 编辑 — reads a floor back as Markdown and writes it again. See [PostEditor]. */
    val postEditor: PostEditor
    val imageUploader: ImageUploader

    /** The selected image host — a service of its own, with its own credential. See [ImageHostRepository]. */
    val imageHostRepository: ImageHostRepository
    val sessionRepository: SessionRepository
    val userSpaceRepository: UserSpaceRepository

    /**
     * 离线阅读 — what 收藏 draws its download states from, and what the download workers drive.
     *
     * Bound to `RoomOfflineLibrary`, which also implements `OfflineThreadReader` (the post cache's
     * fallback when the site cannot be reached) and `OfflineDownloads` (what WorkManager calls).
     * Both are found by casting off this one property rather than adding two more: the three are one
     * object with three audiences, and only this one belongs on a screen's graph.
     */
    val offlineLibrary: OfflineLibrary

    /**
     * What this device remembers about the threads this account has collected.
     *
     * Its own dependency rather than a corner of [postRepository] because three unrelated things
     * write into it — the star, the 收藏 list, an offline download — and only one of them is that
     * repository. See [CollectedPostMetaStore] for why the app has to remember any of this.
     */
    val collectedPostMetaStore: CollectedPostMetaStore
    val assetsRepository: AssetsRepository
    val creditRepository: CreditRepository
    val stardustRepository: StardustRepository
    val awardRepository: AwardRepository
    val communityRepository: CommunityRepository
    val termsRepository: TermsRepository

    val followRepository: FollowRepository

    /** 投票帖. Never cached — see [io.github.nodyssey.data.VoteRepository]. */
    val voteRepository: VoteRepository

    /** The installed build's own version. Read once; the About screen and the updater both use it. */
    val appVersion: AppVersion

    /** 应用内更新: asks GitHub Releases, holds the answer, fetches the APK. */
    val appUpdateRepository: AppUpdateRepository

    /** The other half of it — handing that APK to the platform installer. */
    val apkInstaller: ApkInstaller

    val rulingRepository: RulingRepository

    /** 清除缓存 — the image, WebView and update caches on disk. See [AppCacheStore]. */
    val appCacheStore: AppCacheStore

    /**
     * The UA the WebView and OkHttp both use. Shared rather than duplicated: `cf_clearance` is issued
     * against the UA that solved the challenge and rejected for any other.
     */
    val userAgent: UserAgent
    val okHttpClient: OkHttpClient

    /**
     * The app's proxy — HTTP or SOCKS, with an optional credential.
     *
     * One setting routes all three clients below, which is the point: an app where the posts go
     * through a proxy and the avatars, uploads or update check quietly do not is an app whose proxy
     * setting means something different on every screen. `ProxyScope.FORUM_ONLY` is the one way out
     * of that, and it is the user's to choose. Every other app on the device is unaffected either
     * way — nothing here is a VPN.
     */
    val proxySettings: ProxySettings
    val proxyConnectionTester: ProxyConnectionTester
}

class DefaultAppContainer(
    context: Context,
    override val dispatchers: AppDispatchers = AppDispatchers(),
    override val clock: AppClock = AppClock.System,
) : AppContainer {
    private val appContext = context.applicationContext

    /**
     * The one store OkHttp and the sign-in WebView share, and the two things built on it: the
     * `CookieJar` OkHttp is configured with, and the session read model everything else asks.
     */
    private val cookieStore = WebViewCookieStore()

    override val sessionCookies: SessionCookies by lazy { SessionCookies(NodeSeekSite.CONFIG, cookieStore) }

    override val userAgent: UserAgent by lazy { resolveUserAgent(appContext, NodeSeekSite.CONFIG) }

    /**
     * The other header a browser always sends and OkHttp never does — see [deviceAcceptLanguage].
     *
     * Without it a Cloudflare-fronted image host answered the app with a challenge and a 403 while
     * serving every browser on the same connection, and post-879848's attachment failed for
     * everyone using the app.
     */
    private val acceptLanguage: String by lazy { deviceAcceptLanguage() }

    /**
     * Lives as long as the process, and has two tenants: the update download, which has to survive
     * the 关于 screen being left (a ViewModel scope would cancel it the moment the user backs out to
     * read what changed), and [liveProxyConfig], which has to survive every screen.
     */
    private val appScope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val proxySettings: ProxySettings by lazy {
        DataStoreProxySettings(appContext.proxyDataStore, KeystoreSecretCipher())
    }

    /**
     * One pool behind all three clients.
     *
     * Pooling is per-host and per-route, so nothing crosses between them — but owning it here means
     * [liveProxyConfig] can empty it when the proxy setting changes without reaching into a client
     * that may not have been built yet.
     */
    private val connectionPool = ConnectionPool()

    /**
     * See [LiveProxyConfig] — the synchronous read the clients' routing needs on their own threads.
     *
     * Evicting the pool on a change is what makes an edit take effect immediately: the selector below
     * only decides where new connections go, and a request that lands on a connection opened before
     * the change would otherwise still travel the old way.
     */
    private val liveProxyConfig: LiveProxyConfig by lazy {
        LiveProxyConfig(appScope, proxySettings.config, onRoutingChanged = connectionPool::evictAll)
    }

    override val okHttpClient: OkHttpClient by lazy {
        // SOCKS auth has no per-client hook, only this process-wide one — see [AppSocksAuthenticator].
        java.net.Authenticator.setDefault(AppSocksAuthenticator(liveProxyConfig))
        OkHttpClient
            .Builder()
            .cookieJar(WebViewCookieJar(cookieStore))
            .connectionPool(connectionPool)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            // A ProxySelector, not `.proxy(Proxy)`: consulted on every call rather than baked in once,
            // so toggling or editing 代理设置 takes effect on the next request instead of needing this
            // client rebuilt.
            .proxySelector(AppProxySelector(liveProxyConfig, ProxyClientKind.FORUM))
            .proxyAuthenticator(AppProxyAuthenticator(liveProxyConfig))
            // Applied to page *and* image requests, which both have to look like the mobile site.
            .addInterceptor(
                BrowserHeadersInterceptor(
                    userAgent = userAgent.value,
                    acceptLanguage = acceptLanguage,
                    referer = "${NodeSeekSite.BASE_URL}/",
                ),
            )
            // After the one above, and not merged into it: the vote signature covers the very
            // `User-Agent` that interceptor just set, so it has to observe the finished request.
            .addInterceptor(DynamicSignInterceptor())
            // A *network* interceptor, because it is about a hop the application layer never sees:
            // the `Referer` stamped above must not follow a redirect off the host it was addressed
            // to, or an image host that 302s to a CDN with hotlink protection refuses every image.
            .addNetworkInterceptor(CrossOriginRefererInterceptor())
            .build()
    }

    /**
     * The one place OkHttp is named on the way *up*: everything above this line is written against
     * `HttpTransport`, which is `commonMain`, and this is the Android implementation of it.
     */
    private val transport by lazy { OkHttpTransport(okHttpClient) }

    private val htmlClient by lazy { SiteHtmlClient(transport, dispatchers, NodeSeekSite.CONFIG) }

    private val jsonClient by lazy { NodeSeekJsonClient(transport, dispatchers) }

    override val proxyConnectionTester: ProxyConnectionTester by lazy {
        NetworkProxyConnectionTester(jsonClient)
    }

    /** The offline-first SSOT. Everything below reads from it; only the data sources write to it. */
    private val database by lazy { createNodeSeekDatabase(appContext) }

    private val remotePosts by lazy { NetworkPostDataSource(htmlClient, dispatchers, clock) }

    override val postRepository: PostRepository by lazy {
        OfflineFirstPostRepository(
            database,
            remotePosts,
            clock,
            PostReactionWriter(jsonClient),
            settingsRepository.showBlockedContent,
            PostCollectionWriter(jsonClient),
            settingsRepository.settings.map { it.readHistoryLimit }.distinctUntilChanged(),
            // The downloaded copy, for the reads the network cannot serve. Handed over as the read
            // interface only: the repository must not be able to start a download.
            offlineLibrary as? OfflineThreadReader,
            collectedPostMetaStore,
        )
    }

    override val collectedPostMetaStore: CollectedPostMetaStore by lazy {
        RoomCollectedPostMetaStore(database.collectedPostMetaDao(), clock)
    }

    override val readingPositionStore: ReadingPositionStore by lazy {
        RoomReadingPositionStore(database.readingPositionDao(), clock)
    }

    override val categoryRepository: CategoryRepository by lazy {
        CategoryRepository(jsonClient, database.boardDao(), clock)
    }

    override val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(
            dataStore = appContext.settingsDataStore,
            // Whoever is running a `-dev.N` build was handed it on purpose, so that is what the
            // update check follows until they say otherwise: the stable manifest cannot name anything
            // newer than a test build cut ahead of it, and 已是最新 is what they would be told for as
            // long as that stays true. Flipping the switch off still sticks.
            devChannelDefault = isPreReleaseVersionName(appVersion.name),
        )
    }

    override val notificationRepository: NotificationRepository by lazy {
        NotificationRepository(jsonClient)
    }

    override val messageRepository: MessageRepository by lazy {
        NetworkMessageRepository(jsonClient) {
            // Only consulted when a one-conversation inbox leaves the rows ambiguous, and allowed to
            // fail: a missing uid degrades the guess, it does not fail the screen.
            runCatchingExceptCancellation { profileRepository.profile().uid }.getOrNull()
        }
    }

    override val profileRepository: ProfileRepository by lazy {
        NetworkProfileRepository(
            htmlSource = htmlClient,
            jsonSource = jsonClient,
            profileDao = database.profileDao(),
            currentSessionFingerprint = { sessionRepository.state.value.fingerprint },
            clock = clock,
        )
    }

    override val searchRepository: SearchRepository by lazy {
        NetworkSearchRepository(jsonClient, htmlClient, dispatchers)
    }

    override val postComposerRepository: PostComposerRepository by lazy {
        DefaultPostComposerRepository(appContext.postComposerDataStore, okHttpClient, dispatchers, clock)
    }

    override val accountSettingsRepository: AccountSettingsRepository by lazy {
        NetworkAccountSettingsRepository(
            jsonApi = jsonClient,
            multipartWriteSource = jsonClient,
            htmlSource = htmlClient,
            profileRepository = profileRepository,
        )
    }

    override val userSpaceRepository: UserSpaceRepository by lazy {
        NetworkUserSpaceRepository(jsonClient, dispatchers)
    }

    override val offlineLibrary: OfflineLibrary by lazy {
        RoomOfflineLibrary(
            dao = database.offlineDao(),
            remote = remotePosts,
            files = OfflineFileStore.of(appContext.filesDir),
            // The app's own client, so a stored picture arrives under the same cookies and headers
            // as one on screen — see [OkHttpOfflineImageSource]. Lazily, because building it starts
            // the proxy machinery and a graph that never downloads anything should not pay for that.
            images = OkHttpOfflineImageSource({ okHttpClient }, dispatchers),
            collectedMeta = collectedPostMetaStore,
            settingsStore = OfflineSettingsStore(appContext.offlineDataStore),
            scheduler = WorkManagerOfflineScheduler(appContext),
            clock = clock,
            dispatchers = dispatchers,
        )
    }

    override val assetsRepository: AssetsRepository by lazy {
        NetworkAssetsRepository(profileRepository, creditRepository, jsonClient, dispatchers, clock)
    }

    override val creditRepository: CreditRepository by lazy {
        NetworkCreditRepository(jsonClient, dispatchers)
    }

    override val stardustRepository: StardustRepository by lazy {
        NetworkStardustRepository(jsonClient, dispatchers)
    }

    override val awardRepository: AwardRepository by lazy {
        NetworkAwardRepository(htmlClient, dispatchers)
    }

    override val communityRepository: CommunityRepository by lazy {
        NetworkCommunityRepository(htmlClient, dispatchers)
    }

    override val termsRepository: TermsRepository by lazy {
        NetworkTermsRepository(htmlClient)
    }

    override val followRepository: FollowRepository by lazy {
        // The signed-in check is read off the published session state rather than the cookie store:
        // `/api/fans/*` answers a signed-out read with an empty list, and this is called on the main
        // thread. See [NetworkFollowRepository].
        NetworkFollowRepository(jsonClient, dispatchers) { sessionRepository.state.value.isSignedIn }
    }

    override val voteRepository: VoteRepository by lazy { NetworkVoteRepository(jsonClient) }

    override val rulingRepository: RulingRepository by lazy {
        NetworkRulingRepository(jsonClient, dispatchers)
    }

    override val commentComposerRepository: CommentComposerRepository by lazy {
        DefaultCommentComposerRepository(
            appContext.commentComposerDataStore,
            okHttpClient,
            dispatchers,
            clock,
            postRepository::noteOwnReplyPublished,
        )
    }

    override val postEditor: PostEditor by lazy {
        DefaultPostEditor(
            remote = remotePosts,
            posts = postComposerRepository,
            comments = commentComposerRepository,
            // `extend`, not `refresh`: the reader may have several pages loaded, and a refresh would
            // drop every one but the edited page out from under the scroll they are in.
            threads = postRepository::extendThread,
        )
    }

    /**
     * A client of its own for the image host, sharing with [okHttpClient] only what the user set once
     * for the whole app.
     *
     * That client carries the WebView cookie jar and stamps `Referer: nodeseek.com` on anything
     * without one. Neither is appropriate for a third-party host we hand an API key to: the key is
     * the credential, the cookies are not its business, and the referrer would tell nodeimage.com
     * about a browsing session it has no part in. What is shared is the connection pool, because
     * pooling is per-host anyway, and the proxy, because that one is a setting rather than a
     * property of this host.
     */
    private val imageHostClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectionPool(connectionPool)
            // The proxy is shared even though nothing else is: a user who routes the forum through a
            // node is usually on a network that reaches the image host no better. `FORUM_ONLY` is how
            // they say otherwise, and this selector is what reads it.
            .proxySelector(AppProxySelector(liveProxyConfig, ProxyClientKind.THIRD_PARTY))
            .proxyAuthenticator(AppProxyAuthenticator(liveProxyConfig))
            .connectTimeout(15, TimeUnit.SECONDS)
            // Uploads are the slow call here, and a photo on a weak connection takes longer than a
            // page read ever does.
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // The UA is shared but the cookie jar is not, and that is the point: every host this
            // client reaches is a third party holding a bearer-equivalent secret, so the forum's
            // session must never ride along. `api.nodeimage.com` is behind a Cloudflare managed
            // challenge and OkHttp's default `okhttp/4.x` is the single most reliable way to be
            // served the interstitial instead of JSON, and a self-hosted host may well be behind one
            // too; the device's real browser UA is what a challenge expects to see.
            // No `referer`: see the comment above, and [BrowserHeadersInterceptor].
            .addInterceptor(
                BrowserHeadersInterceptor(userAgent = userAgent.value, acceptLanguage = acceptLanguage),
            ).build()
    }

    override val imageHostRepository: ImageHostRepository by lazy {
        DefaultImageHostRepository(
            settings = DataStoreImageHostSettings(appContext.imageHostDataStore, KeystoreSecretCipher()),
            http = imageHostClient,
            dispatchers = dispatchers,
        )
    }

    override val imageUploader: ImageUploader by lazy {
        ImageHostUploader(
            repository = imageHostRepository,
            preparer = DefaultImagePreparer(appContext, dispatchers),
        )
    }

    /**
     * Shares the cookie jar rather than owning a store of its own: the cookies OkHttp sends and the
     * ones the WebView collects have to be the same cookies, or "am I signed in" gets two answers.
     */
    override val sessionRepository: SessionRepository by lazy { SessionRepository(sessionCookies) }

    override val appVersion: AppVersion by lazy { readAppVersion(appContext) }

    /**
     * A third client, for github.com only.
     *
     * Same reasoning as [imageHostClient]: [okHttpClient] carries the NodeSeek session cookies and
     * stamps `Referer: nodeseek.com` on anything without one, and neither belongs on a call to a
     * host that has no part in that session. The User-Agent names the app and its version instead of
     * borrowing the device's browser UA — GitHub's API asks callers to identify themselves, and
     * there is no challenge here to impersonate a browser for.
     */
    private val gitHubClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectionPool(connectionPool)
            // Same reasoning as `imageHostClient`: whatever reaches the forum is the user's best guess
            // at what reaches a release download too, unless they picked `FORUM_ONLY`.
            .proxySelector(AppProxySelector(liveProxyConfig, ProxyClientKind.THIRD_PARTY))
            .proxyAuthenticator(AppProxyAuthenticator(liveProxyConfig))
            .connectTimeout(15, TimeUnit.SECONDS)
            // An APK download is minutes of streaming, not a page read.
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    override val appUpdateRepository: AppUpdateRepository by lazy {
        DefaultAppUpdateRepository(
            source =
            UpdateManifestSource(
                okHttpClient = gitHubClient,
                dispatchers = dispatchers,
                userAgent = "Nodyssey/${appVersion.name} (+https://github.com/${NodysseyRelease.REPOSITORY})",
                manifestBaseUrl = NodysseyRelease.UPDATES_BASE_URL,
            ),
            store = settingsRepository,
            clock = clock,
            dispatchers = dispatchers,
            scope = appScope,
            currentVersionName = appVersion.name,
            // The cache: an APK that has been installed is dead weight, and one that never got
            // installed is not worth keeping across a system cleanup either.
            downloadDirectory = File(appContext.cacheDir, "updates"),
        )
    }

    override val apkInstaller: ApkInstaller by lazy { ApkInstaller(appContext, dispatchers) }

    override val appCacheStore: AppCacheStore by lazy {
        DefaultAppCacheStore(
            cacheDirectory = appContext.cacheDir,
            dispatchers = dispatchers,
            imageCaches = { CoilImageCaches(SingletonImageLoader.get(appContext)) },
        )
    }
}
