package io.github.nodyssey.ios

import coil3.PlatformContext
import coil3.SingletonImageLoader
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.DynamicSignTransport
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.data.AppCacheStore
import io.github.nodyssey.data.AssetsRepository
import io.github.nodyssey.data.AwardRepository
import io.github.nodyssey.data.CategoryRepository
import io.github.nodyssey.data.CollectedPostMetaStore
import io.github.nodyssey.data.CommunityRepository
import io.github.nodyssey.data.CreditRepository
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
import io.github.nodyssey.data.composer.ImagePreparer
import io.github.nodyssey.data.composer.ImageUploader
import io.github.nodyssey.data.composer.PostComposerRepository
import io.github.nodyssey.data.composer.PostEditor
import io.github.nodyssey.data.createPreferenceDataStore
import io.github.nodyssey.data.diagnostics.NetworkDiagnostics
import io.github.nodyssey.data.dns.DataStoreDohSettings
import io.github.nodyssey.data.dns.DohCapabilities
import io.github.nodyssey.data.dns.DohSettings
import io.github.nodyssey.data.dns.DohSupport
import io.github.nodyssey.data.dns.resolvesOverHttps
import io.github.nodyssey.data.imagehost.DataStoreImageHostSettings
import io.github.nodyssey.data.imagehost.DefaultImageHostRepository
import io.github.nodyssey.data.imagehost.ImageHostRepository
import io.github.nodyssey.data.local.createNodeSeekDatabase
import io.github.nodyssey.data.offline.OfflineSettingsStore
import io.github.nodyssey.data.offline.OfflineWorkScheduler
import io.github.nodyssey.data.offline.RoomOfflineLibrary
import io.github.nodyssey.data.offline.UrlSessionOfflineImageSource
import io.github.nodyssey.data.proxy.DataStoreProxySettings
import io.github.nodyssey.data.proxy.ProxyClientKind
import io.github.nodyssey.data.proxy.ProxyConnectionTester
import io.github.nodyssey.data.proxy.ProxySettings
import io.github.nodyssey.data.proxy.ProxyType
import io.github.nodyssey.data.proxy.routes
import io.github.nodyssey.data.session.NodeSeekSignInRepository
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.session.SignInRepository
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.update.AppUpdateRepository
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.AppVersion
import io.github.plaza.core.net.AppleCookieStore
import io.github.plaza.core.net.EncryptedNameResolution
import io.github.plaza.core.net.EncryptedResolver
import io.github.plaza.core.net.NSUrlSessionTransport
import io.github.plaza.core.net.ProxiedUrlSession
import io.github.plaza.core.net.ProxyRoute
import io.github.plaza.core.net.ProxyRouteType
import io.github.plaza.core.net.SessionCookies
import io.github.plaza.core.net.SiteHtmlClient
import io.github.plaza.core.net.UserAgent
import io.github.plaza.core.net.WebUrl
import io.github.plaza.core.net.appleUrlSession
import io.github.plaza.core.net.deviceAcceptLanguage
import io.github.plaza.core.readAppVersion
import io.github.plaza.core.runCatchingExceptCancellation
import io.github.plaza.core.update.ApkInstaller
import io.github.plaza.core.update.isPreReleaseVersionName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSURLSession

/**
 * The dependency graph on iOS — the counterpart of `DefaultAppContainer` in `:app`.
 *
 * Read side by side with that file, the interesting thing is how little differs. D3b counted thirty
 * of the thirty-six members as the same expression on both platforms, because steps A5–A7 put the
 * repositories, the database and the network contracts in `commonMain`; D3c sent the six image-host
 * clients and the vote signature after them, so [imageHostRepository] joined them. What is left here
 * is two sessions, a cookie jar, three directories and the three seams gathered in `Unavailable.kt`.
 *
 * **Two sessions, where Android has three clients.** The split is the same one and drawn for the same
 * reason: [forumSession] carries the NodeSeek cookies and stamps the forum's `Referer`, and
 * [imageHostSession] carries neither, because every host behind it is a third party holding a
 * bearer-equivalent secret. Android's third client is the GitHub update check, which this platform
 * does not make.
 *
 * **The proxy is a rebuilt session rather than a selector.** `NSURLSession` takes its proxy in
 * `connectionProxyDictionary`, which is part of the configuration a session is created from, so a
 * saved edit in 代理设置 cannot be applied to a live one — see [ProxiedUrlSession]. Both sessions read
 * the same setting and honour [ProxyScope] the same way the two Android clients do.
 *
 * @param userAgent resolved before the graph is built — see [NodysseyApp] and `resolveWebKitUserAgent`
 *   for why it cannot be read synchronously and must not be guessed at.
 * @param offlineScheduler passed in rather than built here because it is registered with
 *   `BGTaskScheduler` before this graph exists — see [NodysseyApp.registerBackgroundTasks]. Its own
 *   handlers close back over the container through the same lazy resolver, so one instance serves
 *   both the engine that queues work and the tasks that drain it.
 */
class IosAppContainer(
    override val userAgent: UserAgent,
    private val offlineScheduler: OfflineWorkScheduler,
    override val dispatchers: AppDispatchers = AppDispatchers(),
    override val clock: AppClock = AppClock.System,
) : AppContainer {
    /**
     * The jar `NSURLSession` reads — and the same one `WebViewScreen.ios.kt` points its
     * [io.github.plaza.core.net.WebKitCookieBridge] at.
     *
     * That the two name `sharedHTTPCookieStorage` rather than share an object is the whole of what the
     * bridge is for: WebKit writes somewhere else, and a jar the app reads and a jar the sign-in
     * browser writes are only the same jar on Android.
     */
    private val cookieStore = AppleCookieStore(NSHTTPCookieStorage.sharedHTTPCookieStorage)

    private val sessionCookies: SessionCookies by lazy { SessionCookies(NodeSeekSite.CONFIG, cookieStore) }

    /** See `DeviceAcceptLanguage.kt` — a request without this one is a request no browser makes. */
    private val acceptLanguage: String by lazy { deviceAcceptLanguage() }

    /** Collects the proxy setting for as long as the process lives; nothing else runs on it. */
    private val appScope = CoroutineScope(SupervisorJob() + dispatchers.default)

    /**
     * 代理设置 as a route, or null when this kind of client is meant to go direct.
     *
     * The Apple counterpart of `AppProxySelector`, and it answers exactly the same question — it just
     * answers it once per saved edit instead of once per request, because that is as often as a
     * session can be told. `distinctUntilChanged` on the whole route is what keeps a keystroke in the
     * 备注 field from rebuilding two sessions.
     */
    private fun proxyRoute(kind: ProxyClientKind): Flow<ProxyRoute?> =
        proxySettings.config
            .map { config ->
                if (!config.routes(kind)) {
                    null
                } else {
                    ProxyRoute(
                        type = when (config.type) {
                            ProxyType.HTTP -> ProxyRouteType.HTTP
                            ProxyType.SOCKS -> ProxyRouteType.SOCKS
                        },
                        host = config.host,
                        port = config.port,
                        username = config.username,
                        password = config.password,
                    )
                }
            }.distinctUntilChanged()

    /**
     * The session the forum, its JSON and its pictures all go through.
     *
     * Internal rather than private because the image loader is built outside this class and has to be
     * given it: `:app` puts its `OkHttpClient` on `AndroidAppContainer` for the same reason, and for
     * the same reason it is not on `AppContainer` — no screen reads it.
     */
    internal val forumSession by lazy {
        ProxiedUrlSession(appScope, proxyRoute(ProxyClientKind.FORUM)) { proxy ->
            appleUrlSession(
                userAgent = userAgent.value,
                acceptLanguage = acceptLanguage,
                // Applied to page *and* image requests, which both have to look like the mobile site —
                // the same argument `DefaultAppContainer` makes for its `BrowserHeadersInterceptor`. The
                // image half is not theoretical: without it an attachment host answers
                // 「只允许将图片嵌入网页」 with a 403, which is what the first run of this shell drew.
                referer = "${NodeSeekSite.BASE_URL}/",
                proxy = proxy,
            )
        }
    }

    /** What the image loader and the offline downloader read; the current one, not the first one. */
    internal val urlSession: NSURLSession get() = forumSession.current

    /**
     * A session of its own for the six image hosts, sharing with [forumSession] only what the user
     * set once for the whole app.
     *
     * The reasoning is `DefaultAppContainer`'s `imageHostClient`, line for line. That client carries
     * the WebView cookie jar and stamps `Referer: nodeseek.com` on anything without one; neither is
     * appropriate for a third party we hand an API key to — the key is the credential, the cookies are
     * not its business, and the referrer would tell nodeimage.com about a browsing session it has no
     * part in. What *is* shared is the proxy, because that one is a setting rather than a property of
     * this host, and the user agent, because `api.nodeimage.com` sits behind a Cloudflare managed
     * challenge and a device's real browser UA is what a challenge expects to see.
     *
     * The longer timeout is the other difference: an upload on a weak connection takes longer than a
     * page read ever does.
     */
    private val imageHostSession by lazy {
        ProxiedUrlSession(appScope, proxyRoute(ProxyClientKind.THIRD_PARTY)) { proxy ->
            appleUrlSession(
                userAgent = userAgent.value,
                acceptLanguage = acceptLanguage,
                referer = null,
                timeoutSeconds = IMAGE_HOST_TIMEOUT_SECONDS,
                cookies = null,
                proxy = proxy,
            )
        }
    }

    /**
     * The forum's transport, signed.
     *
     * [DynamicSignTransport] is what makes the `/api/vote/` family answer anything but 403, and until D3c
     * it was an OkHttp interceptor that only Android had — which is why the D3b notes recorded voting
     * as expected to fail here. It wraps the forum's transport only: the signature is NodeSeek's, and
     * the image hosts are six other people's.
     */
    private val transport by lazy {
        DynamicSignTransport(NSUrlSessionTransport { forumSession.current }, userAgent.value)
    }

    private val htmlClient by lazy { SiteHtmlClient(transport, dispatchers, NodeSeekSite.CONFIG) }

    private val jsonClient by lazy { NodeSeekJsonClient(transport, dispatchers) }

    private val database by lazy { createNodeSeekDatabase() }

    private val remotePosts by lazy { NetworkPostDataSource(htmlClient, dispatchers, clock) }

    private val secretCipher by lazy { KeychainSecretCipher() }

    override val proxySettings: ProxySettings by lazy {
        DataStoreProxySettings(createPreferenceDataStore("proxy"), secretCipher)
    }

    override val proxyConnectionTester: ProxyConnectionTester by lazy { IosProxyConnectionTester(jsonClient) }

    private val dohSettings: DohSettings by lazy { DataStoreDohSettings(createPreferenceDataStore("dns")) }

    /**
     * 加密 DNS, with two of its switches missing and the third path to it taken.
     *
     * Neither capability is available here and both refusals are quoted from `privacy_context.h` —
     * see [EncryptedNameResolution], which is also why this needs no session rebuilt and no client
     * handed anything: the setting lands on the process's default privacy context, and every
     * resolution in the process inherits it.
     *
     * 测试解析 is a request rather than a lookup for the same reason; see [IosDnsResolutionTester].
     */
    override val doh: DohSupport by lazy {
        DohSupport(
            settings = dohSettings,
            tester = IosDnsResolutionTester(
                session = { forumSession.current },
                url = NodeSeekSite.BASE_URL,
                host = WebUrl.parse(NodeSeekSite.BASE_URL)?.host.orEmpty(),
                clock = clock,
            ),
            capabilities = DohCapabilities(canChooseRecordTypes = false, canFallBackToSystem = false),
        )
    }

    /**
     * No 网络自检 here yet, and 设置 leaves the row out rather than offering half of one.
     *
     * The screen's value is the breakdown — where in a request the time went — and on this platform
     * that means `NSURLSessionTaskTransactionMetrics`, which arrives on a session delegate rather
     * than as a return value. `ProxiedUrlSession` builds its sessions per proxy config and none of
     * them carries such a delegate today, so wiring one is a change to how sessions are made, not a
     * class to add beside them. Null until that is done.
     */
    override val networkDiagnostics: NetworkDiagnostics? = null

    /**
     * Applied at construction rather than lazily, because unlike every other member here this one is
     * not read by anybody: its whole effect is a side effect on the process, and a resolver setting
     * that only takes hold once somebody opens 设置 is a resolver setting that does not work.
     */
    private val encryptedNameResolution =
        EncryptedNameResolution(
            appScope,
            dohSettings.config
                .map { config ->
                    if (!config.resolvesOverHttps()) {
                        null
                    } else {
                        EncryptedResolver(url = config.serverUrl, serverAddresses = config.bootstrap)
                    }
                },
        )

    override val postRepository: PostRepository by lazy {
        OfflineFirstPostRepository(
            database,
            remotePosts,
            clock,
            PostReactionWriter(jsonClient),
            settingsRepository.showBlockedContent,
            PostCollectionWriter(jsonClient),
            settingsRepository.settings.map { it.readHistoryLimit }.distinctUntilChanged(),
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
            dataStore = createPreferenceDataStore("settings"),
            devChannelDefault = isPreReleaseVersionName(appVersion.name),
        )
    }

    override val notificationRepository: NotificationRepository by lazy { NotificationRepository(jsonClient) }

    override val messageRepository: MessageRepository by lazy {
        NetworkMessageRepository(jsonClient) {
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
        DefaultPostComposerRepository(createPreferenceDataStore("post-composer"), transport, dispatchers, clock)
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
            files = IosOfflineFileStore.of(applicationSupportDirectory()),
            // The app's own session, so a stored picture arrives under the same cookies and headers
            // as one on screen. Lazily for the reason the Android container gives about its client.
            images = UrlSessionOfflineImageSource { urlSession },
            collectedMeta = collectedPostMetaStore,
            settingsStore = OfflineSettingsStore(createPreferenceDataStore("offline")),
            scheduler = offlineScheduler,
            clock = clock,
            dispatchers = dispatchers,
        )
    }

    override val assetsRepository: AssetsRepository by lazy {
        NetworkAssetsRepository(profileRepository, creditRepository, jsonClient, dispatchers, clock)
    }

    override val creditRepository: CreditRepository by lazy { NetworkCreditRepository(jsonClient, dispatchers) }

    override val stardustRepository: StardustRepository by lazy { NetworkStardustRepository(jsonClient, dispatchers) }

    override val awardRepository: AwardRepository by lazy { NetworkAwardRepository(htmlClient, dispatchers) }

    override val communityRepository: CommunityRepository by lazy { NetworkCommunityRepository(htmlClient, dispatchers) }

    override val termsRepository: TermsRepository by lazy { NetworkTermsRepository(htmlClient) }

    override val followRepository: FollowRepository by lazy {
        NetworkFollowRepository(jsonClient, dispatchers) { sessionRepository.state.value.isSignedIn }
    }

    override val voteRepository: VoteRepository by lazy { NetworkVoteRepository(jsonClient) }

    override val rulingRepository: RulingRepository by lazy { NetworkRulingRepository(jsonClient, dispatchers) }

    override val commentComposerRepository: CommentComposerRepository by lazy {
        DefaultCommentComposerRepository(
            createPreferenceDataStore("comment-composer"),
            transport,
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
            threads = postRepository::extendThread,
        )
    }

    override val imageHostRepository: ImageHostRepository by lazy {
        DefaultImageHostRepository(
            settings = DataStoreImageHostSettings(createPreferenceDataStore("imagehost"), secretCipher),
            http = NSUrlSessionTransport { imageHostSession.current },
            dispatchers = dispatchers,
        )
    }

    override val imageUploader: ImageUploader by lazy {
        ImageHostUploader(repository = imageHostRepository, preparer = imagePreparer)
    }

    private val imagePreparer: ImagePreparer by lazy { IosImagePreparer(dispatchers) }

    override val sessionRepository: SessionRepository by lazy { SessionRepository(sessionCookies) }

    override val signInRepository: SignInRepository by lazy { NodeSeekSignInRepository(jsonClient) }

    override val appVersion: AppVersion by lazy { readAppVersion() }

    override val appUpdateRepository: AppUpdateRepository get() = NoAppUpdates

    override val apkInstaller: ApkInstaller get() = NoApkInstaller

    private companion object {
        /** Sixty seconds, as `DefaultAppContainer` gives its image-host client, and for its reason. */
        const val IMAGE_HOST_TIMEOUT_SECONDS = 60.0
    }

    override val appCacheStore: AppCacheStore by lazy {
        IosAppCacheStore(
            cacheDirectory = cachesDirectory(),
            dispatchers = dispatchers,
            // The singleton the screens draw through, not a loader of this store's own: emptying a
            // cache the app is not reading would report success and free nothing.
            imageCaches = { IosImageCaches { SingletonImageLoader.get(PlatformContext.INSTANCE) } },
        )
    }
}
