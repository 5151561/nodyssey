package io.github.nodyssey.di

import io.github.nodyssey.data.AppCacheStore
import io.github.nodyssey.data.AssetsRepository
import io.github.nodyssey.data.AwardRepository
import io.github.nodyssey.data.CategoryRepository
import io.github.nodyssey.data.CollectedPostMetaStore
import io.github.nodyssey.data.CommunityRepository
import io.github.nodyssey.data.CreditRepository
import io.github.nodyssey.data.FollowRepository
import io.github.nodyssey.data.MessageRepository
import io.github.nodyssey.data.NotificationRepository
import io.github.nodyssey.data.OfflineLibrary
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.ReadingPositionStore
import io.github.nodyssey.data.RulingRepository
import io.github.nodyssey.data.SearchRepository
import io.github.nodyssey.data.StardustRepository
import io.github.nodyssey.data.TermsRepository
import io.github.nodyssey.data.UserSpaceRepository
import io.github.nodyssey.data.VoteRepository
import io.github.nodyssey.data.account.AccountSettingsRepository
import io.github.nodyssey.data.composer.CommentComposerRepository
import io.github.nodyssey.data.composer.ImageUploader
import io.github.nodyssey.data.composer.PostComposerRepository
import io.github.nodyssey.data.composer.PostEditor
import io.github.nodyssey.data.dns.DohSupport
import io.github.nodyssey.data.imagehost.ImageHostRepository
import io.github.nodyssey.data.proxy.ProxyConnectionTester
import io.github.nodyssey.data.proxy.ProxySettings
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.session.SignInRepository
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.update.AppUpdateRepository
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.AppVersion
import io.github.plaza.core.net.UserAgent
import io.github.plaza.core.update.ApkInstaller

/**
 * The application's dependency graph, as the screens above it see one.
 *
 * Manual constructor injection rather than Hilt: the graph is still small enough that a framework
 * would add more machinery than value. This interface keeps dependencies explicit and swappable;
 * see `docs/architecture.md` for the migration trigger.
 *
 * Nothing here is a global: the container is created by the Application and handed down. That is
 * what makes a fake container's substitution possible in tests.
 *
 * Every member is a type this module can name. The two that were not — the `OkHttpClient` and the
 * `SessionCookies` read off the cookie jar it is built with — are on `AndroidAppContainer` in `:app`
 * instead, and neither was ever read from a screen: the first is Coil's call factory, the second is
 * consumed inside the container itself. That is the whole of what step D1 had to take off this
 * interface to bring it down here with the ViewModels that name it.
 */
interface AppContainer {
    val dispatchers: AppDispatchers
    val clock: AppClock

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

    /** 登录 · 原生表单 (h1). Apart from [sessionRepository], which only reads the cookie jar. */
    val signInRepository: SignInRepository
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

    /**
     * 加密 DNS — a DoH server for the app's own lookups, and the test that proves it answers.
     *
     * Both platforms have one, by two different routes. Android hands each of its `OkHttpClient`s a
     * `Dns`, so the resolver is the app's own object. Apple has no such parameter on `NSURLSession`
     * — what it has is `nw_privacy_context_require_encrypted_name_resolution` on
     * `NW_DEFAULT_PRIVACY_CONTEXT`, which the header describes as inherited by every other
     * resolution in the same process, `NSURLSession` included. What that costs is two of the
     * switches: see `DohCapabilities`.
     *
     * Nullable because a platform could still lack it, and 设置 reads the null to leave the row out
     * rather than offer a screen that stores a server nothing reads.
     */
    val doh: DohSupport?
}
