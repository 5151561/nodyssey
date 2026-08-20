package io.github.nodyssey.data.imagehost

import io.github.nodyssey.core.html.Selectors
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.net.UploadProgress
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** One parser for every host. None of them send anything this app would rather reject than ignore. */
internal val imageHostJson = Json { ignoreUnknownKeys = true }

/**
 * Runs a request and hands back its body, with every non-2xx already turned into an exception.
 *
 * Shared by all six clients because the *failure* vocabulary is the part they agree on even when
 * their payloads do not: everybody's 401 means the credential, everybody's 413 means the file, and
 * anybody sitting behind Cloudflare answers with an interstitial that is neither.
 *
 * On [HttpTransport] rather than on an `OkHttpClient`, which is what let this whole directory leave
 * `:app` — see [ImageHostClient]. The transport reports a call that never completed as
 * [SiteException] with `Network`, and that is the one exception shape a caller of this has to
 * translate: everything else it hands back is an answer, which is what the `when` below reads.
 *
 * @param keyIsEnough whether a token alone authenticates this endpoint. It decides what a 401
 *   *means*, which is the difference between "your token is broken" and "this one needs the
 *   website". See [ImageHostError.SessionRequired].
 */
internal suspend fun HttpTransport.readBody(
    request: HttpRequest,
    keyIsEnough: Boolean = true,
    onUploadProgress: UploadProgress? = null,
): String {
    val response = try {
        execute(request, onUploadProgress)
    } catch (error: SiteException) {
        throw ImageHostException(ImageHostError.Network, cause = error)
    }
    val payload = response.body
    // Runs first, and before the status check: Cloudflare wraps its interstitial in a 403, which
    // is the same status a host uses for a bad token. Reading the challenge as a bad token would
    // send the user off to regenerate a credential that was never the problem.
    //
    // Only the markers count, not "the body starts with `<`". A self-hosted host answers with an
    // HTML error page often enough that the looser test would report somebody's PHP stack trace
    // as a human-verification block; an HTML body with no marker falls through to [asJsonObject]
    // and comes out as [ImageHostError.Unparsable], which is what it is.
    val isChallenge =
        response.header("cf-mitigated")?.equals("challenge", ignoreCase = true) == true ||
            Selectors.CLOUDFLARE_MARKERS.any(payload::contains)
    if (isChallenge) throw ImageHostException(ImageHostError.Cloudflare)

    val detail = payload.errorDetail()
    return when {
        response.isSuccessful -> payload

        response.code == 401 || response.code == 403 -> throw ImageHostException(
            if (keyIsEnough) ImageHostError.InvalidKey else ImageHostError.SessionRequired,
            detail,
        )

        // 413 is over the size cap, 415 an unsupported format, 422 a file it could not decode.
        response.code == 413 || response.code == 415 || response.code == 422 ->
            throw ImageHostException(ImageHostError.Rejected(response.code), detail)

        else -> throw ImageHostException(ImageHostError.Http(response.code), detail)
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
