package io.github.nsreader.data

import io.github.nsreader.core.AppClock
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.HtmlSource
import io.github.nsreader.core.net.JsonSource
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.core.net.NodeSeekJsonClient
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.data.local.ProfileDao
import io.github.nsreader.data.local.SelfProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.jsoup.Jsoup
import java.nio.charset.StandardCharsets
import java.util.Base64

data class UserProfile(
    val uid: Long,
    val name: String,
    val avatarUrl: String,
    val rank: Int? = null,
    val createdAt: String? = null,
    val chickenCount: Int? = null,
    val starCount: Int? = null,
    val streakDays: Int? = null,
    val bio: String? = null,
    /** Markdown, shown on the space page. The site's own empty state is "没有找到readme 🙄". */
    val readme: String? = null,
    val topicCount: Int? = null,
    val commentCount: Int? = null,
)

interface ProfileRepository {
    /** The persisted SSOT for "我的", scoped to the active cookie fingerprint. */
    fun observeProfile(sessionFingerprint: Int): Flow<UserProfile?> = flowOf(null)

    /** Fetches the signed-in profile and writes it into [observeProfile]'s backing store. */
    suspend fun refreshProfile(sessionFingerprint: Int) {
        profile(refresh = true)
    }

    /** Removes account-scoped profile data before the session is published as signed out. */
    suspend fun clearCachedProfile() = Unit

    /**
     * The signed-in account.
     *
     * May answer from a short-lived cache: every profile-area screen asks for this on entry, and the
     * call behind it costs two round-trips (home-page HTML, then the account endpoint). [refresh]
     * bypasses the cache for the flows that must see the network — pull-to-refresh, sign-in changes.
     */
    suspend fun profile(refresh: Boolean = false): UserProfile

    /**
     * Any account, by uid — the public space page.
     *
     * Separate from [profile] because the two resolve their subject differently: this one is told who
     * to load, while [profile] has to work out who the session belongs to before it can ask.
     */
    suspend fun profile(uid: Long): UserProfile

    /**
     * The signed-in account's uid, remembered from the last successful load or persisted emission.
     *
     * Null until a profile has loaded once this process. Callers that navigate to a user's space use
     * it to decide `isSelf` — a null simply means "not known to be self", never "known not to be".
     */
    val selfUid: Long? get() = null
}

/**
 * Loads the signed-in account shown by NodeSeek itself.
 *
 * The home page hydration identifies the current user without inspecting authentication-cookie
 * values. Once the UID is known, the account endpoint is used as the freshest source. The bootstrap
 * record remains a fallback because Cloudflare can occasionally allow the document request while
 * rejecting the following XHR.
 */
class NetworkProfileRepository(
    private val htmlSource: HtmlSource,
    private val jsonSource: JsonSource,
    private val profileDao: ProfileDao,
    private val currentSessionFingerprint: () -> Int,
    private val clock: AppClock,
) : ProfileRepository {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    override var selfUid: Long? = null
        private set

    private val cacheLock = Mutex()

    override fun observeProfile(sessionFingerprint: Int): Flow<UserProfile?> =
        profileDao.observe(sessionFingerprint).map { entity ->
            entity?.toProfile()?.also { selfUid = it.uid }
        }

    override suspend fun refreshProfile(sessionFingerprint: Int) {
        cacheLock.withLock { fetchAndStore(sessionFingerprint) }
    }

    override suspend fun clearCachedProfile() {
        cacheLock.withLock {
            profileDao.deleteAll()
            selfUid = null
        }
    }

    override suspend fun profile(refresh: Boolean): UserProfile =
        // The lock also serializes concurrent callers, so two screens opening at once cost one
        // fetch, not two racing ones.
        cacheLock.withLock {
            val fingerprint = currentSessionFingerprint()
            if (!refresh) {
                profileDao
                    .find(fingerprint)
                    ?.takeIf { clock.nowMillis() - it.cachedAtMillis < PROFILE_CACHE_TTL_MILLIS }
                    ?.toProfile()
                    ?.also { selfUid = it.uid }
                    ?.let { return@withLock it }
            }
            fetchAndStore(fingerprint)
        }

    private suspend fun fetchAndStore(sessionFingerprint: Int): UserProfile =
        fetchProfile().also { profile ->
            profileDao.replace(profile.toEntity(sessionFingerprint, clock.nowMillis()))
        }

    private suspend fun fetchProfile(): UserProfile {
        val bootstrap = parseProfilePage(htmlSource.getHtml("/"))
        // The page parsed but named no account: the session cookie is missing or stale. Reporting
        // that as Unparsable sent users to a "站点改版" card whose retry can never succeed.
        val uid = bootstrap.uid ?: throw NodeSeekException(NodeSeekError.LoginRequired)
        selfUid = uid
        val account =
            runCatchingExceptCancellation {
                parseProfileJson(
                    jsonSource.getJson(
                        path = NodeSeekJsonClient.accountInfoPath(uid),
                        referer = NodeSeekSite.BASE_URL + NodeSeekSite.spacePath(uid),
                    ),
                )
            }.getOrNull()

        return bootstrap.merge(account).toProfile()
    }

    override suspend fun profile(uid: Long): UserProfile {
        val body =
            jsonSource.getJson(
                path = NodeSeekJsonClient.accountInfoPath(uid),
                referer = NodeSeekSite.BASE_URL + NodeSeekSite.spacePath(uid),
            )
        val raw = parseProfileJson(body) ?: throw NodeSeekException(NodeSeekError.Unparsable)
        // The endpoint omits the uid on some accounts, and the caller already knows it.
        return raw.copy(uid = raw.uid ?: uid).toProfile()
    }

    internal fun parseProfilePage(html: String): RawProfile {
        val encoded =
            Jsoup.parse(html)
                .getElementById(PROFILE_BOOTSTRAP_ELEMENT_ID)
                ?.data()
                ?.trim()
                .orEmpty()
        // No bootstrap element at all is how the home page looks to a signed-out visitor, not how a
        // redesign would look — a redesign would still carry *something* where the profile was.
        if (encoded.isEmpty()) throw NodeSeekException(NodeSeekError.LoginRequired)

        val decoded =
            try {
                String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
            } catch (exception: IllegalArgumentException) {
                throw NodeSeekException(NodeSeekError.Unparsable, exception)
            }
        return parseProfileJson(decoded)
            ?: throw NodeSeekException(NodeSeekError.Unparsable)
    }

    internal fun parseProfileJson(body: String): RawProfile? {
        val root =
            try {
                json.parseToJsonElement(body)
            } catch (exception: IllegalArgumentException) {
                throw NodeSeekException(NodeSeekError.Unparsable, exception)
            }
        return root.findProfileObject()?.toRawProfile()
    }

    private fun RawProfile.merge(newer: RawProfile?): RawProfile {
        if (newer == null) return this
        return RawProfile(
            uid = newer.uid ?: uid,
            name = newer.name ?: name,
            avatarUrl = newer.avatarUrl ?: avatarUrl,
            rank = newer.rank ?: rank,
            createdAt = newer.createdAt ?: createdAt,
            chickenCount = newer.chickenCount ?: chickenCount,
            starCount = newer.starCount ?: starCount,
            streakDays = newer.streakDays ?: streakDays,
            bio = newer.bio ?: bio,
            readme = newer.readme ?: readme,
            topicCount = newer.topicCount ?: topicCount,
            commentCount = newer.commentCount ?: commentCount,
        )
    }

    private fun RawProfile.toProfile(): UserProfile {
        val resolvedUid = uid ?: throw NodeSeekException(NodeSeekError.Unparsable)
        val resolvedName = name?.takeIf(String::isNotBlank) ?: throw NodeSeekException(NodeSeekError.Unparsable)
        return UserProfile(
            uid = resolvedUid,
            name = resolvedName,
            avatarUrl =
            NodeSeekSite.absoluteUrl(avatarUrl)
                ?: "${NodeSeekSite.BASE_URL}/avatar/$resolvedUid.png",
            rank = rank,
            createdAt = createdAt,
            chickenCount = chickenCount,
            starCount = starCount,
            streakDays = streakDays,
            bio = bio,
            readme = readme,
            topicCount = topicCount,
            commentCount = commentCount,
        )
    }

    private companion object {
        const val PROFILE_BOOTSTRAP_ELEMENT_ID = "temp-script"
    }
}

private fun SelfProfileEntity.toProfile(): UserProfile =
    UserProfile(
        uid = uid,
        name = name,
        avatarUrl = avatarUrl,
        rank = rank,
        createdAt = createdAt,
        chickenCount = chickenCount,
        starCount = starCount,
        streakDays = streakDays,
        bio = bio,
        readme = readme,
        topicCount = topicCount,
        commentCount = commentCount,
    )

private fun UserProfile.toEntity(
    sessionFingerprint: Int,
    cachedAtMillis: Long,
): SelfProfileEntity =
    SelfProfileEntity(
        sessionFingerprint = sessionFingerprint,
        uid = uid,
        name = name,
        avatarUrl = avatarUrl,
        rank = rank,
        createdAt = createdAt,
        chickenCount = chickenCount,
        starCount = starCount,
        streakDays = streakDays,
        bio = bio,
        readme = readme,
        topicCount = topicCount,
        commentCount = commentCount,
        cachedAtMillis = cachedAtMillis,
    )

internal data class RawProfile(
    val uid: Long? = null,
    val name: String? = null,
    val avatarUrl: String? = null,
    val rank: Int? = null,
    val createdAt: String? = null,
    val chickenCount: Int? = null,
    val starCount: Int? = null,
    val streakDays: Int? = null,
    val bio: String? = null,
    val readme: String? = null,
    val topicCount: Int? = null,
    val commentCount: Int? = null,
)

private fun JsonElement.findProfileObject(): JsonObject? {
    if (this !is JsonObject) return null
    if (keys.any { it in PROFILE_ID_KEYS } && keys.any { it in PROFILE_NAME_KEYS }) return this

    PROFILE_CONTAINER_KEYS.forEach { key ->
        this[key]?.findProfileObject()?.let { return it }
    }
    values.forEach { child ->
        child.findProfileObject()?.let { return it }
    }
    return null
}

private fun JsonObject.toRawProfile(): RawProfile =
    RawProfile(
        uid = long(*PROFILE_ID_KEYS.toTypedArray()),
        name = text(*PROFILE_NAME_KEYS.toTypedArray()),
        avatarUrl = text("avatar", "avatarUrl", "avatar_url"),
        rank = int("rank", "level"),
        createdAt = text("created_at", "createdAt", "registered_at", "created_at_str"),
        chickenCount = int("coin", "chicken", "chickenCount", "chicken_count"),
        starCount = int("stardust", "star", "stars", "starCount", "star_count"),
        streakDays = int("streak", "streakDays", "streak_days"),
        bio = text("bio", "introduction", "signature_text"),
        readme = text("readme", "readMe", "read_me"),
        topicCount = int("nPost", "post_count", "postCount", "topicCount", "discussion_count"),
        commentCount = int("nComment", "comment_count", "commentCount"),
    )

/** Long enough to cover one walk through the profile area, short enough that balances stay honest. */
private const val PROFILE_CACHE_TTL_MILLIS = 60_000L

private val PROFILE_ID_KEYS = setOf("member_id", "uid", "user_id")
private val PROFILE_NAME_KEYS = setOf("member_name", "username", "name")
private val PROFILE_CONTAINER_KEYS = listOf("user", "data", "account", "result")
