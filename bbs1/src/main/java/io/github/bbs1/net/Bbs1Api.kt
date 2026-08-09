package io.github.bbs1.net

import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * A bbs1org site's API plugin.
 *
 * Every call takes the site's base URL because this is a multi-instance app: the current site is a
 * piece of user state, not client state, and a client object per site would just duplicate the list
 * the repository already owns. For the same reason the credential is a parameter rather than
 * something the client holds — one process can be signed in to several sites at once, under
 * different names, and there is no ambient "current user" for a client object to cache.
 *
 * The read calls take a token too, and pass it when there is one: the server answers permission
 * fields (`can_post`, `can_reply`) per identity, so an anonymous read of a forum list is a different
 * answer than the signed-in one, not the same answer with less on it.
 */
interface Bbs1Api {
    /** Site identity and capability discovery — also the probe that tells a bbs1org site from anything else. */
    suspend fun meta(baseUrl: String, token: String? = null): ApiMeta

    suspend fun forums(baseUrl: String, token: String? = null): List<ApiForum>

    /** [forumId] null means every forum, which is the server's default and the app's landing view. */
    suspend fun topics(
        baseUrl: String,
        forumId: Long? = null,
        page: Int = 1,
        token: String? = null,
    ): ApiTopicsPage

    suspend fun topic(
        baseUrl: String,
        id: Long,
        page: Int = 1,
        token: String? = null,
    ): ApiTopicPage

    suspend fun login(baseUrl: String, username: String, password: String): ApiAuth

    suspend fun createTopic(
        baseUrl: String,
        token: String,
        forumId: Long,
        title: String,
        body: String,
    ): ApiTopicCreated

    suspend fun createReply(
        baseUrl: String,
        token: String,
        topicId: Long,
        body: String,
    ): ApiReplyCreated
}

/**
 * Why a call failed, split by what the UI should do about it.
 *
 * [Server] carries the plugin's own human-readable message and is shown verbatim — the server's
 * copy is already written for people ("搜索太频繁，请 30 秒后再试"). The other three get copy from
 * resources, because there the server said nothing usable or said something the app must act on
 * before showing.
 */
sealed class Bbs1ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The connection itself failed: offline, DNS, TLS, timeout. */
    class Network(cause: IOException) : Bbs1ApiException("network error", cause)

    /** The host answered, but not with this API — most likely the plugin is not installed or enabled. */
    class NotBbs1Api(cause: Throwable? = null) : Bbs1ApiException("no bbs1 api at this origin", cause)

    /**
     * The credential was refused: expired, or invalidated by a password change elsewhere. Distinct
     * from [Server] because the caller has something to do about it — drop the stored session —
     * before it decides what to show.
     */
    class Unauthorized(val userMessage: String) : Bbs1ApiException(userMessage)

    /** The plugin processed the request and refused it, saying why. */
    class Server(val userMessage: String) : Bbs1ApiException(userMessage)
}

class Bbs1ApiClient(
    private val okHttpClient: OkHttpClient,
    private val dispatchers: AppDispatchers,
) : Bbs1Api {

    override suspend fun meta(baseUrl: String, token: String?): ApiMeta = get(baseUrl, "meta", token)

    override suspend fun forums(baseUrl: String, token: String?): List<ApiForum> =
        get<ApiForumsPage>(baseUrl, "forums", token).forums

    override suspend fun topics(
        baseUrl: String,
        forumId: Long?,
        page: Int,
        token: String?,
    ): ApiTopicsPage =
        get(baseUrl, "topics", token) {
            if (forumId != null) addQueryParameter("forum_id", forumId.toString())
            if (page > 1) addQueryParameter("p", page.toString())
        }

    override suspend fun topic(
        baseUrl: String,
        id: Long,
        page: Int,
        token: String?,
    ): ApiTopicPage =
        get(baseUrl, "topic", token) {
            addQueryParameter("id", id.toString())
            if (page > 1) addQueryParameter("p", page.toString())
        }

    override suspend fun login(baseUrl: String, username: String, password: String): ApiAuth =
        post(baseUrl, "login", token = null) {
            add("username", username)
            add("password", password)
        }

    override suspend fun createTopic(
        baseUrl: String,
        token: String,
        forumId: Long,
        title: String,
        body: String,
    ): ApiTopicCreated =
        post(baseUrl, "topic_create", token) {
            add("forum_id", forumId.toString())
            add("title", title)
            add("body", body)
        }

    override suspend fun createReply(
        baseUrl: String,
        token: String,
        topicId: Long,
        body: String,
    ): ApiReplyCreated =
        post(baseUrl, "reply_create", token) {
            add("topic_id", topicId.toString())
            add("body", body)
        }

    private suspend inline fun <reified T> get(
        baseUrl: String,
        resource: String,
        token: String?,
        crossinline params: HttpUrl.Builder.() -> Unit = {},
    ): T = call(baseUrl, resource, token, form = null, params = params)

    private suspend inline fun <reified T> post(
        baseUrl: String,
        resource: String,
        token: String?,
        crossinline fields: FormBody.Builder.() -> Unit,
    ): T = call(baseUrl, resource, token, form = FormBody.Builder().apply(fields).build())

    private suspend inline fun <reified T> call(
        baseUrl: String,
        resource: String,
        token: String?,
        form: FormBody?,
        crossinline params: HttpUrl.Builder.() -> Unit = {},
    ): T = withContext(dispatchers.io) {
        val url =
            baseUrl.toHttpUrlOrNull()?.newBuilder()
                // The plugin's one route: index.php?a=api&r=<resource>. Everything else about the
                // endpoint set is discovered from meta, but the entry point itself is the contract.
                ?.addPathSegment("index.php")
                ?.addQueryParameter("a", "api")
                ?.addQueryParameter("r", resource)
                ?.apply(params)
                ?.build()
                ?: throw Bbs1ApiException.NotBbs1Api()
        val request =
            Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .apply {
                    // The plugin reads the standard header first and falls back to X-Api-Token where
                    // the host strips it; sending both costs a few bytes and covers CGI deployments.
                    token?.let {
                        header("Authorization", "Bearer $it")
                        header("X-Api-Token", it)
                    }
                    form?.let { post(it) }
                }
                .build()
        val (code, body) = try {
            okHttpClient.newCall(request).execute().use { it.code to it.body.string() }
        } catch (e: IOException) {
            throw Bbs1ApiException.Network(e)
        }
        decodeApiPayload<T>(body, code)
    }
}

// @PublishedApi so the inline decode below can reach it without widening it for everyone else.
@PublishedApi
internal val bbs1ApiJson = Json { ignoreUnknownKeys = true }

/** The status the plugin pairs with a refused credential. */
@PublishedApi
internal const val HTTP_UNAUTHORIZED: Int = 401

/**
 * Parses the plugin's response envelope and enforces its verdict: `{"ok":1, ...payload}` passes
 * through, `{"ok":0,"message":"why"}` throws [Bbs1ApiException.Server].
 *
 * Most errors arrive with HTTP 200 (the plugin keeps the core's AJAX convention), so the status code
 * is deliberately not what decides success — `ok` is. The one thing it does decide is *which* refusal
 * this is: the plugin pairs 401 with a rejected token and nothing else, and that one the caller has
 * to act on rather than merely display. Anything that is not this envelope (an HTML 404 from a forum
 * without the plugin, a parked domain's page) classifies as [Bbs1ApiException.NotBbs1Api].
 */
@PublishedApi
internal fun parseApiEnvelope(raw: String, httpCode: Int): JsonObject {
    val envelope = try {
        bbs1ApiJson.parseToJsonElement(raw).jsonObject
    } catch (e: IllegalArgumentException) {
        // Covers SerializationException on non-JSON (it subclasses IllegalArgumentException) and
        // .jsonObject on a JSON value that is not an object.
        throw Bbs1ApiException.NotBbs1Api(e)
    }
    when (envelope.okValue()) {
        1 -> Unit

        0 -> {
            val message = envelope["message"]?.jsonPrimitive?.content.orEmpty().ifEmpty { "操作失败" }
            throw if (httpCode == HTTP_UNAUTHORIZED) {
                Bbs1ApiException.Unauthorized(message)
            } else {
                Bbs1ApiException.Server(message)
            }
        }

        else -> throw Bbs1ApiException.NotBbs1Api()
    }
    return envelope
}

/** The payload rides at the envelope's top level, so the envelope object itself is what decodes into [T]. */
internal inline fun <reified T> decodeApiPayload(raw: String, httpCode: Int = 200): T {
    val envelope = parseApiEnvelope(raw, httpCode)
    return try {
        bbs1ApiJson.decodeFromJsonElement<T>(envelope)
    } catch (e: IllegalArgumentException) {
        // ok:1 but the payload does not fit the declared shape — still not an API this client speaks.
        throw Bbs1ApiException.NotBbs1Api(e)
    }
}

private fun JsonObject.okValue(): Int? =
    try {
        this["ok"]?.jsonPrimitive?.intOrNull
    } catch (_: IllegalArgumentException) {
        // `"ok": {...}` — jsonPrimitive throws on non-primitives; that shape is not the envelope.
        null
    }
