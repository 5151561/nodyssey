package io.github.bbs1.net

import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * The read side of a bbs1org site's API plugin.
 *
 * Every call takes the site's base URL because this is a multi-instance app: the current site is a
 * piece of user state, not client state, and a client object per site would just duplicate the list
 * the repository already owns.
 */
interface Bbs1Api {
    /** Site identity and capability discovery — also the probe that tells a bbs1org site from anything else. */
    suspend fun meta(baseUrl: String): ApiMeta

    suspend fun forums(baseUrl: String): List<ApiForum>

    /** [forumId] null means every forum, which is the server's default and the app's landing view. */
    suspend fun topics(
        baseUrl: String,
        forumId: Long? = null,
        page: Int = 1,
    ): ApiTopicsPage

    suspend fun topic(
        baseUrl: String,
        id: Long,
        page: Int = 1,
    ): ApiTopicPage
}

/**
 * Why a call failed, split by what the UI should do about it.
 *
 * [Server] carries the plugin's own human-readable message and is shown verbatim — the server's
 * copy is already written for people ("搜索太频繁，请 30 秒后再试"). The other two get copy from
 * resources, because there the server said nothing usable.
 */
sealed class Bbs1ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The connection itself failed: offline, DNS, TLS, timeout. */
    class Network(cause: IOException) : Bbs1ApiException("network error", cause)

    /** The host answered, but not with this API — most likely the plugin is not installed or enabled. */
    class NotBbs1Api(cause: Throwable? = null) : Bbs1ApiException("no bbs1 api at this origin", cause)

    /** The plugin processed the request and refused it, saying why. */
    class Server(val userMessage: String) : Bbs1ApiException(userMessage)
}

class Bbs1ApiClient(
    private val okHttpClient: OkHttpClient,
    private val dispatchers: AppDispatchers,
) : Bbs1Api {

    override suspend fun meta(baseUrl: String): ApiMeta = get(baseUrl, "meta")

    override suspend fun forums(baseUrl: String): List<ApiForum> =
        get<ApiForumsPage>(baseUrl, "forums").forums

    override suspend fun topics(
        baseUrl: String,
        forumId: Long?,
        page: Int,
    ): ApiTopicsPage =
        get(baseUrl, "topics") {
            if (forumId != null) addQueryParameter("forum_id", forumId.toString())
            if (page > 1) addQueryParameter("p", page.toString())
        }

    override suspend fun topic(
        baseUrl: String,
        id: Long,
        page: Int,
    ): ApiTopicPage =
        get(baseUrl, "topic") {
            addQueryParameter("id", id.toString())
            if (page > 1) addQueryParameter("p", page.toString())
        }

    private suspend inline fun <reified T> get(
        baseUrl: String,
        resource: String,
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
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        val body = try {
            okHttpClient.newCall(request).execute().use { it.body.string() }
        } catch (e: IOException) {
            throw Bbs1ApiException.Network(e)
        }
        decodeApiPayload<T>(body)
    }
}

// @PublishedApi so the inline decode below can reach it without widening it for everyone else.
@PublishedApi
internal val bbs1ApiJson = Json { ignoreUnknownKeys = true }

/**
 * Parses the plugin's response envelope and enforces its verdict: `{"ok":1, ...payload}` passes
 * through, `{"ok":0,"message":"why"}` throws [Bbs1ApiException.Server].
 *
 * Errors can arrive with HTTP 200 (the plugin keeps the core's AJAX convention), so the status code
 * is deliberately not consulted — `ok` is the truth. Anything that is not this envelope (an HTML 404
 * from a forum without the plugin, a parked domain's page) classifies as [Bbs1ApiException.NotBbs1Api].
 */
@PublishedApi
internal fun parseApiEnvelope(raw: String): JsonObject {
    val envelope = try {
        bbs1ApiJson.parseToJsonElement(raw).jsonObject
    } catch (e: IllegalArgumentException) {
        // Covers SerializationException on non-JSON (it subclasses IllegalArgumentException) and
        // .jsonObject on a JSON value that is not an object.
        throw Bbs1ApiException.NotBbs1Api(e)
    }
    when (envelope.okValue()) {
        1 -> Unit

        0 -> throw Bbs1ApiException.Server(
            envelope["message"]?.jsonPrimitive?.content.orEmpty().ifEmpty { "操作失败" },
        )

        else -> throw Bbs1ApiException.NotBbs1Api()
    }
    return envelope
}

/** The payload rides at the envelope's top level, so the envelope object itself is what decodes into [T]. */
internal inline fun <reified T> decodeApiPayload(raw: String): T {
    val envelope = parseApiEnvelope(raw)
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
