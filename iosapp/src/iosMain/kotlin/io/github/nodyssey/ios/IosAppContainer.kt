package io.github.nodyssey.ios

import coil3.PlatformContext
import coil3.SingletonImageLoader
import io.github.nodyssey.core.NodeSeekSite
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
import io.github.nodyssey.data.imagehost.DataStoreImageHostSettings
import io.github.nodyssey.data.imagehost.ImageHostRepository
import io.github.nodyssey.data.local.createNodeSeekDatabase
import io.github.nodyssey.data.offline.OfflineSettingsStore
import io.github.nodyssey.data.offline.RoomOfflineLibrary
import io.github.nodyssey.data.offline.UrlSessionOfflineImageSource
import io.github.nodyssey.data.proxy.DataStoreProxySettings
import io.github.nodyssey.data.proxy.ProxyConnectionTester
import io.github.nodyssey.data.proxy.ProxySettings
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.update.AppUpdateRepository
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.AppVersion
import io.github.plaza.core.net.AppleCookieStore
import io.github.plaza.core.net.NSUrlSessionTransport
import io.github.plaza.core.net.SessionCookies
import io.github.plaza.core.net.SiteHtmlClient
import io.github.plaza.core.net.UserAgent
import io.github.plaza.core.net.appleUrlSession
import io.github.plaza.core.net.deviceAcceptLanguage
import io.github.plaza.core.readAppVersion
import io.github.plaza.core.runCatchingExceptCancellation
import io.github.plaza.core.update.ApkInstaller
import io.github.plaza.core.update.isPreReleaseVersionName
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import platform.Foundation.NSHTTPCookieStorage

/**
 * The dependency graph on iOS — the counterpart of `DefaultAppContainer` in `:app`.
 *
 * Read side by side with that file, the interesting thing is how little differs. Thirty of the
 * thirty-six members are the same expression on both platforms, because steps A5–A7 put the
 * repositories, the database and the network contracts in `commonMain`; what is left here is a
 * transport, a cookie jar, three directories and the four seams gathered in `Unavailable.kt`.
 *
 * **No proxy.** Every OkHttp client in the Android container carries an `AppProxySelector` and the
 * app's 代理设置 routes all three. `NSURLSession` takes a proxy through
 * `connectionProxyDictionary`, which is a different shape — per-session and set once, where the
 * selector is consulted per request precisely so an edit takes effect on the next one. [proxySettings]
 * is real here and the screen stores what it is given; nothing reads it yet. That is a gap and it is
 * named as one in `docs/kmp-migration-plan.md`.
 *
 * @param userAgent resolved before the graph is built — see [NodysseyApp] and `resolveWebKitUserAgent`
 *   for why it cannot be read synchronously and must not be guessed at.
 */
class IosAppContainer(
    override val userAgent: UserAgent,
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

    /**
     * The one session everything HTTP goes through — the pages, the JSON, the pictures.
     *
     * Internal rather than private because the image loader is built outside this class and has to be
     * given it: `:app` puts its `OkHttpClient` on `AndroidAppContainer` for the same reason, and for
     * the same reason it is not on `AppContainer` — no screen reads it.
     */
    internal val urlSession by lazy {
        appleUrlSession(
            userAgent = userAgent.value,
            acceptLanguage = acceptLanguage,
            // Applied to page *and* image requests, which both have to look like the mobile site —
            // the same argument `DefaultAppContainer` makes for its `BrowserHeadersInterceptor`. The
            // image half is not theoretical: without it an attachment host answers
            // 「只允许将图片嵌入网页」 with a 403, which is what the first run of this shell drew.
            referer = "${NodeSeekSite.BASE_URL}/",
        )
    }

    private val transport by lazy { NSUrlSessionTransport(urlSession) }

    private val htmlClient by lazy { SiteHtmlClient(transport, dispatchers, NodeSeekSite.CONFIG) }

    private val jsonClient by lazy { NodeSeekJsonClient(transport, dispatchers) }

    private val database by lazy { createNodeSeekDatabase() }

    private val remotePosts by lazy { NetworkPostDataSource(htmlClient, dispatchers, clock) }

    private val secretCipher by lazy { KeychainSecretCipher() }

    override val proxySettings: ProxySettings by lazy {
        DataStoreProxySettings(createPreferenceDataStore("proxy"), secretCipher)
    }

    override val proxyConnectionTester: ProxyConnectionTester by lazy { IosProxyConnectionTester(jsonClient) }

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
            scheduler = NoOfflineWorkScheduler,
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
        UnavailableImageHostRepository(
            DataStoreImageHostSettings(createPreferenceDataStore("imagehost"), secretCipher),
        )
    }

    override val imageUploader: ImageUploader by lazy {
        ImageHostUploader(repository = imageHostRepository, preparer = imagePreparer)
    }

    private val imagePreparer: ImagePreparer by lazy { IosImagePreparer(dispatchers) }

    override val sessionRepository: SessionRepository by lazy { SessionRepository(sessionCookies) }

    override val appVersion: AppVersion by lazy { readAppVersion() }

    override val appUpdateRepository: AppUpdateRepository get() = NoAppUpdates

    override val apkInstaller: ApkInstaller get() = NoApkInstaller

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
