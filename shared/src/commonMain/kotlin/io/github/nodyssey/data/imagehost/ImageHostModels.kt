package io.github.nodyssey.data.imagehost

/** An image, ready to go out: already decoded, resized and re-encoded by [ImagePreparer]. */
data class ImageHostUpload(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
) {
    // Data classes with an array member need these, or two uploads of the same photo compare unequal.
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is ImageHostUpload &&
                    bytes.contentEquals(other.bytes) &&
                    fileName == other.fileName &&
                    mimeType == other.mimeType
                )

    override fun hashCode(): Int =
        (bytes.contentHashCode() * 31 + fileName.hashCode()) * 31 + mimeType.hashCode()
}

/**
 * One image sitting on a host — the answer to an upload, and one row of 图床管理.
 *
 * The same type for both because the hosts answer both with the same record, and the screen wants
 * a freshly uploaded image to look like every other row. Everything past [url] is optional: half
 * these hosts report a size and a date, the rest report nothing but the link.
 *
 * [deleteToken] is separate from [id] because they are not always the same string. Lsky deletes by
 * the same `key` it lists by, sm.ms deletes by a per-image hash that its list returns alongside the
 * id, and passing the wrong one deletes nothing while reporting success.
 */
data class HostedImage(
    val id: String,
    val fileName: String,
    val url: String,
    val uploadTime: String? = null,
    val sizeBytes: Long = 0L,
    val mimeType: String? = null,
    val deleteToken: String = id,
)

/**
 * Why a call to an image host could not be completed.
 *
 * Separate from `SiteError` on purpose: the recoveries do not overlap. A NodeSeek 401 means "sign in
 * to the forum", an image host 401 means "your token is wrong" — sending the user to the forum's
 * login page for that would be actively unhelpful.
 */
sealed interface ImageHostError {
    /** No host connected yet, or the connected one is missing a field. The fix is 图床设置. */
    data object NotConfigured : ImageHostError

    /** The credential was rejected. It was revoked, regenerated, or pasted wrong. */
    data object InvalidKey : ImageHostError

    /**
     * The endpoint wants a browser session; a token is not enough.
     *
     * Measured, not assumed: on device, nodeimage.com's `GET /api/images` with a key that had *just*
     * succeeded on `POST /api/upload` answered 401 `{"error":"未认证，请先通过NodeSeek授权登录"}`
     * (2026-07-28). Kept apart from [InvalidKey] because the key is fine, and telling the user to
     * regenerate a working key would break the half that does work.
     */
    data object SessionRequired : ImageHostError

    /** The host refused this particular file — too large, or a format it does not take. */
    data class Rejected(val statusCode: Int) : ImageHostError

    /**
     * Cloudflare answered instead of the host.
     *
     * `api.nodeimage.com` sits behind a managed challenge (confirmed 2026-07-28: a plain `curl` to
     * it gets the "Just a moment…" interstitial, not JSON), and a self-hosted host may well sit
     * behind one too. A phone's real UA normally passes, but when it does not the recovery is *not*
     * "check your token" — the token was never looked at.
     */
    data object Cloudflare : ImageHostError

    data class Http(val statusCode: Int) : ImageHostError

    data object Network : ImageHostError

    /**
     * The host answered, but not with anything a URL could be read out of.
     *
     * For a configured host this means its API changed. For [ImageHostProvider.CUSTOM] it much more
     * likely means the 取值路径 field is wrong, which is why that screen shows the raw answer.
     */
    data object Unparsable : ImageHostError

    /** This host has no endpoint for what was asked — listing or deleting. See [ImageHostProvider.browsable]. */
    data object Unsupported : ImageHostError
}

class ImageHostException(
    val error: ImageHostError,
    val detail: String? = null,
    cause: Throwable? = null,
) : Exception(detail ?: error.toString(), cause)
