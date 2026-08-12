package io.github.nodyssey.data.imagehost

import io.github.nodyssey.core.html.Selectors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException

/** One parser for every host. None of them send anything this app would rather reject than ignore. */
internal val imageHostJson = Json { ignoreUnknownKeys = true }

/**
 * Runs a request and hands back its body, with every non-2xx already turned into an exception.
 *
 * Shared by all six clients because the *failure* vocabulary is the part they agree on even when
 * their payloads do not: everybody's 401 means the credential, everybody's 413 means the file, and
 * anybody sitting behind Cloudflare answers with an interstitial that is neither.
 *
 * @param keyIsEnough whether a token alone authenticates this endpoint. It decides what a 401
 *   *means*, which is the difference between "your token is broken" and "this one needs the
 *   website". See [ImageHostError.SessionRequired].
 */
internal fun OkHttpClient.readBody(request: Request, keyIsEnough: Boolean = true): String {
    val response = try {
        newCall(request).execute()
    } catch (error: IOException) {
        throw ImageHostException(ImageHostError.Network, cause = error)
    }
    return response.use {
        val payload = it.body.string()
        // Runs first, and before the status check: Cloudflare wraps its interstitial in a 403, which
        // is the same status a host uses for a bad token. Reading the challenge as a bad token would
        // send the user off to regenerate a credential that was never the problem.
        //
        // Only the markers count, not "the body starts with `<`". A self-hosted host answers with an
        // HTML error page often enough that the looser test would report somebody's PHP stack trace
        // as a human-verification block; an HTML body with no marker falls through to [asJsonObject]
        // and comes out as [ImageHostError.Unparsable], which is what it is.
        val isChallenge =
            it.header("cf-mitigated")?.equals("challenge", ignoreCase = true) == true ||
                Selectors.CLOUDFLARE_MARKERS.any(payload::contains)
        if (isChallenge) throw ImageHostException(ImageHostError.Cloudflare)

        val detail = payload.errorDetail()
        when {
            it.isSuccessful -> payload

            it.code == 401 || it.code == 403 -> throw ImageHostException(
                if (keyIsEnough) ImageHostError.InvalidKey else ImageHostError.SessionRequired,
                detail,
            )

            // 413 is over the size cap, 415 an unsupported format, 422 a file it could not decode.
            it.code == 413 || it.code == 415 || it.code == 422 ->
                throw ImageHostException(ImageHostError.Rejected(it.code), detail)

            else -> throw ImageHostException(ImageHostError.Http(it.code), detail)
        }
    }
}

/** The host's own sentence about what went wrong, under whichever of the three keys it used. */
internal fun String.errorDetail(): String? = runCatching {
    imageHostJson.parseToJsonElement(this).jsonObject.let { root ->
        root["error"]?.jsonPrimitive?.contentOrNull
            ?: root["message"]?.jsonPrimitive?.contentOrNull
            ?: root["msg"]?.jsonPrimitive?.contentOrNull
    }
}.getOrNull()?.takeIf { it.isNotBlank() }

internal fun String.asJsonObject(): JsonObject =
    runCatching { imageHostJson.parseToJsonElement(this).jsonObject }
        .getOrElse { throw ImageHostException(ImageHostError.Unparsable, detail = take(DETAIL_CHARS), cause = it) }

/**
 * Reads `data.links.url` out of an answer, and `images.0.url` out of one with an array in the way.
 *
 * The custom host needs this because the user describes where its URL lives instead of the app
 * knowing; the built-in clients use it too, since a dotted path reads better at the call site than
 * four nested `jsonObject[...]` lookups that each have to be null-checked.
 */
internal fun JsonElement.stringAtPath(path: String): String? {
    var current: JsonElement = this
    for (segment in path.split('.')) {
        if (segment.isBlank()) continue
        val index = segment.toIntOrNull()
        current = runCatching {
            if (index != null) current.jsonArray[index] else current.jsonObject[segment]
        }.getOrNull() ?: return null
    }
    return runCatching { current.jsonPrimitive.contentOrNull }.getOrNull()?.takeIf { it.isNotBlank() }
}

internal fun JsonObject.stringAt(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key -> this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() } }
        ?.takeIf { it.isNotBlank() }

/** Sizes arrive as ints, as floats, and occasionally as strings; none of those may zero a row. */
internal fun JsonObject.longAt(vararg keys: String): Long? =
    keys.firstNotNullOfOrNull { key ->
        this[key]?.let { element ->
            runCatching {
                element.jsonPrimitive.let { it.longOrNull ?: it.doubleOrNull?.toLong() ?: it.contentOrNull?.toLongOrNull() }
            }.getOrNull()
        }
    }

/** How much of an unreadable body is worth carrying into an error message. */
internal const val DETAIL_CHARS = 200

/**
 * A byte-array body that reports how much of itself has been written.
 *
 * OkHttp gives no upload-progress callback of its own, and wrapping the sink is the documented way
 * to get one. Written in chunks rather than one `write` call because a single write reports 0% and
 * then 100%, which makes the tray's ring a decoration instead of a signal.
 */
internal class ProgressRequestBody(
    private val bytes: ByteArray,
    private val contentType: MediaType,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    override fun contentType() = contentType

    override fun contentLength(): Long = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        var written = 0
        onProgress(0f)
        while (written < bytes.size) {
            val count = minOf(CHUNK_BYTES, bytes.size - written)
            sink.write(bytes, written, count)
            written += count
            onProgress(written.toFloat() / bytes.size)
        }
    }

    private companion object {
        const val CHUNK_BYTES = 16 * 1024
    }
}
