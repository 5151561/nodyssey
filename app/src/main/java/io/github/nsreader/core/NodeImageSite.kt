package io.github.nsreader.core

/**
 * Everything we know about the shape of nodeimage.com.
 *
 * NodeSeek has no image host of its own — the forum's editor only ever holds Markdown, and the
 * "NodeImage已就绪" badge on the web editor comes from a *browser extension*, not from the site. So
 * this is a genuinely separate service with its own account and its own credential, and it is kept in
 * its own object rather than folded into [NodeSeekSite] to make that boundary hard to blur: nothing
 * here may be sent a NodeSeek cookie, and nothing in [NodeSeekSite] may be sent the API key.
 *
 * The endpoints are the ones nodeimage.com documents on its own API page (read 2026-07-28). The web
 * uploader uses a different, cookie-authenticated path (`POST /upload`); the app deliberately takes
 * the documented key-authenticated one instead, because it needs no OAuth round trip through NodeSeek
 * and no shared browser session.
 */
object NodeImageSite {

    /** Where the API lives. Note the `api.` host — the site itself is on `www.`. */
    const val API_BASE_URL = "https://api.nodeimage.com"

    /** The human site, for the "open in browser" entry that hands out the key. */
    const val SITE_URL = "https://www.nodeimage.com"

    const val UPLOAD_PATH = "/api/upload"

    const val IMAGES_PATH = "/api/images"

    fun imagePath(imageId: String): String = "/api/image/$imageId"

    const val API_KEY_HEADER = "X-API-Key"

    /** The multipart part name the upload endpoint reads the file from. */
    const val UPLOAD_FILE_FIELD = "image"

    /** Uploads answer with a CDN URL on this host; used to recognise our own links. */
    const val CDN_HOST = "cdn.nodeimage.com"

    fun absoluteApiUrl(path: String): String = API_BASE_URL + path

    /**
     * A NodeImage key is a 64-character hex string. Checked before the first request so a pasted
     * fragment fails with "that is not a key" rather than an indistinguishable 401.
     */
    fun isPlausibleApiKey(key: String): Boolean = API_KEY.matches(key.trim())

    private val API_KEY = Regex("""[0-9a-fA-F]{32,128}""")
}
