package io.github.nodyssey.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.NodysseyRelease
import io.github.nodyssey.core.net.DynamicSignInterceptor
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.data.AssetsRepository
import io.github.nodyssey.data.AwardRepository
import io.github.nodyssey.data.CategoryRepository
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
import io.github.nodyssey.data.PostCollectionWriter
import io.github.nodyssey.data.PostReactionWriter
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.ReadingPositionStore
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
import io.github.nodyssey.data.composer.DefaultImagePreparer
import io.github.nodyssey.data.composer.DefaultPostComposerRepository
import io.github.nodyssey.data.composer.ImageUploader
import io.github.nodyssey.data.composer.NodeImageUploader
import io.github.nodyssey.data.composer.PostComposerRepository
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.nodeimage.DefaultNodeImageRepository
import io.github.nodyssey.data.nodeimage.NodeImageRepository
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.update.ApkInstaller
import io.github.nodyssey.data.update.AppUpdateRepository
import io.github.nodyssey.data.update.DefaultAppUpdateRepository
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.AppVersion
import io.github.plaza.core.net.SiteHtmlClient
import io.github.plaza.core.net.UserAgent
import io.github.plaza.core.net.WebViewCookieJar
import io.github.plaza.core.net.resolveUserAgent
import io.github.plaza.core.readAppVersion
import io.github.plaza.core.runCatchingExceptCancellation
import io.github.plaza.core.update.GitHubReleaseSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    val cookieJar: WebViewCookieJar
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
    val imageUploader: ImageUploader

    /** nodeimage.com — a service of its own, with its own credential. See [NodeImageRepository]. */
    val nodeImageRepository: NodeImageRepository
    val sessionRepository: SessionRepository
    val userSpaceRepository: UserSpaceRepository
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

    override val cookieJar: WebViewCookieJar by lazy { WebViewCookieJar(NodeSeekSite.CONFIG) }

    override val userAgent: UserAgent by lazy { resolveUserAgent(appContext, NodeSeekSite.CONFIG) }

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
            }
            // After the one above, and not merged into it: the vote signature covers the very
            // `User-Agent` that interceptor just set, so it has to observe the finished request.
            .addInterceptor(DynamicSignInterceptor())
            .build()
    }

    private val htmlClient by lazy { SiteHtmlClient(okHttpClient, dispatchers, NodeSeekSite.CONFIG) }

    private val jsonClient by lazy { NodeSeekJsonClient(okHttpClient, dispatchers) }

    /** The offline-first SSOT. Everything below reads from it; only the data sources write to it. */
    private val database by lazy { NodeSeekDatabase.create(appContext) }

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
        )
    }

    override val readingPositionStore: ReadingPositionStore by lazy {
        RoomReadingPositionStore(database.readingPositionDao(), clock)
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
        NetworkSearchRepository(jsonClient, dispatchers)
    }

    override val postComposerRepository: PostComposerRepository by lazy {
        DefaultPostComposerRepository(appContext, okHttpClient, dispatchers, clock)
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
        DefaultCommentComposerRepository(appContext, okHttpClient, dispatchers, clock)
    }

    /**
     * A client of its own for the image host, sharing nothing with [okHttpClient].
     *
     * That client carries the WebView cookie jar and stamps `Referer: nodeseek.com` on anything
     * without one. Neither is appropriate for a third-party host we hand an API key to: the key is
     * the credential, the cookies are not its business, and the referrer would tell nodeimage.com
     * about a browsing session it has no part in. The connection pool is shared because pooling is
     * per-host anyway, so nothing crosses between them.
     */
    private val nodeImageClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectionPool(okHttpClient.connectionPool)
            .connectTimeout(15, TimeUnit.SECONDS)
            // Uploads are the slow call here, and a photo on a weak connection takes longer than a
            // page read ever does.
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // The UA is shared but the cookie jar is not. `api.nodeimage.com` is behind a Cloudflare
            // managed challenge, and OkHttp's default `okhttp/4.x` is the single most reliable way
            // to be served the interstitial instead of JSON. The device's real browser UA is what
            // the challenge expects to see; no cookie or referrer rides along with it.
            .addInterceptor { chain ->
                val request = chain.request()
                chain.proceed(
                    if (request.header("User-Agent") != null) {
                        request
                    } else {
                        request.newBuilder().header("User-Agent", userAgent.value).build()
                    },
                )
            }.build()
    }

    override val nodeImageRepository: NodeImageRepository by lazy {
        DefaultNodeImageRepository(appContext, nodeImageClient, dispatchers)
    }

    override val imageUploader: ImageUploader by lazy {
        NodeImageUploader(
            repository = nodeImageRepository,
            preparer = DefaultImagePreparer(appContext, dispatchers),
        )
    }

    /**
     * Shares the cookie jar rather than owning a store of its own: the cookies OkHttp sends and the
     * ones the WebView collects have to be the same cookies, or "am I signed in" gets two answers.
     */
    override val sessionRepository: SessionRepository by lazy { SessionRepository(cookieJar) }

    override val appVersion: AppVersion by lazy { readAppVersion(appContext) }

    /**
     * A third client, for github.com only.
     *
     * Same reasoning as [nodeImageClient]: [okHttpClient] carries the NodeSeek session cookies and
     * stamps `Referer: nodeseek.com` on anything without one, and neither belongs on a call to a
     * host that has no part in that session. The User-Agent names the app and its version instead of
     * borrowing the device's browser UA — GitHub's API asks callers to identify themselves, and
     * there is no challenge here to impersonate a browser for.
     */
    private val gitHubClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectionPool(okHttpClient.connectionPool)
            .connectTimeout(15, TimeUnit.SECONDS)
            // An APK download is minutes of streaming, not a page read.
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Lives as long as the process, and has exactly one tenant.
     *
     * The update download has to survive the 关于 screen being left — a ViewModel scope would cancel
     * it the moment the user backs out to read what changed — and the silent check at launch belongs
     * to no screen at all.
     */
    private val appScope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val appUpdateRepository: AppUpdateRepository by lazy {
        DefaultAppUpdateRepository(
            source =
            GitHubReleaseSource(
                okHttpClient = gitHubClient,
                dispatchers = dispatchers,
                userAgent = "Nodyssey/${appVersion.name} (+https://github.com/${NodysseyRelease.REPOSITORY})",
                repository = NodysseyRelease.REPOSITORY,
                assetNamePrefix = NodysseyRelease.ASSET_NAME_PREFIX,
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
}
