package io.github.nodyssey.data.imagehost

import io.github.nodyssey.core.NodeImageSite

/**
 * The image hosts the app knows how to talk to.
 *
 * NodeSeek stores Markdown and nothing else, so every picture in a post is a link to somewhere the
 * forum does not run — and which somewhere that is, is the user's choice, not ours. One of these is
 * selected at a time (see [ImageHostSettings]); the editor never learns which, it only asks
 * [ImageHostRepository] to upload.
 *
 * [CUSTOM] is the escape hatch for the rest: a self-hosted service the app has never heard of, filled
 * in by hand the way PicGo's "custom web uploader" is. It exists so that adding a host does not
 * require shipping a release.
 *
 * @param id what gets written into the settings store. Renaming one silently disconnects everybody
 *   who had it selected, so these are stable strings rather than [Enum.name].
 * @param needsSiteUrl whether the user has to say *where* the host is. False for the three that live
 *   at a fixed address, true for the ones that are somebody's own server.
 * @param browsable whether the host has endpoints for listing and deleting what it holds. When false
 *   the 已上传 half of the screen says so instead of showing an empty gallery — an image host with no
 *   list is normal, an image host that lost your images is not, and the two must not look alike.
 */
enum class ImageHostProvider(
    val id: String,
    val needsSiteUrl: Boolean,
    val browsable: Boolean,
) {
    /** nodeimage.com — what the forum's own browser extension uses. See [NodeImageSite]. */
    NODE_IMAGE(id = "nodeimage", needsSiteUrl = false, browsable = true),

    /** 兰空图床 Lsky Pro V2, self-hosted. Routes confirmed against `routes/api.php` (2026-08-12). */
    LSKY_PRO(id = "lsky", needsSiteUrl = true, browsable = true),

    /** 简单图床 EasyImage 2.0, self-hosted. Upload only — its `api/` directory has no other entry. */
    EASY_IMAGE(id = "easyimage", needsSiteUrl = true, browsable = false),

    /** sm.ms, public and free. */
    SMMS(id = "smms", needsSiteUrl = false, browsable = true),

    /** imgbb, public and free. Its API documents upload and nothing else. */
    IMGBB(id = "imgbb", needsSiteUrl = false, browsable = false),

    /** Anything else, described field by field by the user. */
    CUSTOM(id = "custom", needsSiteUrl = true, browsable = false),
    ;

    companion object {
        /** What a fresh install shows selected, and what an unreadable stored id falls back to. */
        val DEFAULT = NODE_IMAGE

        fun fromId(id: String?): ImageHostProvider = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Everything needed to reach one host, as the user typed it.
 *
 * One shape for every provider rather than a sealed hierarchy per host: the settings screen renders
 * whichever fields the selected provider declares, and a single flat record is what a preferences
 * store can hold without a serializer per variant. [custom] is only read for
 * [ImageHostProvider.CUSTOM] and is left at its defaults everywhere else.
 */
data class ImageHostConfig(
    val provider: ImageHostProvider,
    /** The host's own address, `https://img.example.com`, with any trailing slash already gone. */
    val siteUrl: String = "",
    /** API key, token, whatever that host calls it. Stored on this device only. */
    val token: String = "",
    val custom: CustomHostFields = CustomHostFields(),
) {
    val isConfigured: Boolean get() = problem() == null

    /**
     * The one field on here that must never be shown.
     *
     * Five of the six hosts call it a token or an API key and it lands in [token]. A custom host
     * puts it wherever its own API wants it — an `Authorization` header, or a `token=…` line among
     * the form fields — so "the credential" is not a fixed field, and anything that masks or
     * fingerprints it has to ask rather than assume.
     */
    val secret: String
        get() = if (provider == ImageHostProvider.CUSTOM) {
            custom.headerValue.ifBlank { custom.formFields }
        } else {
            token
        }

    /**
     * What is missing or malformed, checked before any request goes out.
     *
     * Done here rather than at the first upload so a typo surfaces in 图床设置, where it can be
     * fixed, instead of as a failed attachment in the middle of writing a post.
     */
    fun problem(): ConfigProblem? = when {
        provider.needsSiteUrl && !siteUrl.looksLikeHttpUrl() -> ConfigProblem.BAD_SITE_URL

        provider == ImageHostProvider.CUSTOM && custom.fileField.isBlank() ->
            ConfigProblem.MISSING_FILE_FIELD

        provider == ImageHostProvider.CUSTOM && custom.urlPath.isBlank() ->
            ConfigProblem.MISSING_URL_PATH

        // The custom host may legitimately need no credential at all — a private uploader on a LAN
        // is the obvious case — so it is the one provider where an empty token is not a problem.
        provider != ImageHostProvider.CUSTOM && token.isBlank() -> ConfigProblem.MISSING_TOKEN

        provider == ImageHostProvider.NODE_IMAGE && !NodeImageSite.isPlausibleApiKey(token) ->
            ConfigProblem.IMPLAUSIBLE_TOKEN

        else -> null
    }
}

/** Fields only [ImageHostProvider.CUSTOM] reads. Defaults match what most self-hosted uploaders use. */
data class CustomHostFields(
    /** The multipart part the file goes in. `file` for most, `image` for the PHP ones. */
    val fileField: String = "file",
    /** e.g. `Authorization`. Blank when the host authenticates through a form field instead. */
    val headerName: String = "",
    val headerValue: String = "",
    /** Extra form fields, one `name=value` per line — where a `token=…` body parameter goes. */
    val formFields: String = "",
    /** Dot path to the URL in the answer, e.g. `data.links.url`. `.0.` indexes an array. */
    val urlPath: String = "url",
    /** Prepended when the host answers with a path rather than a full URL. */
    val urlPrefix: String = "",
) {
    /** `token=abc` per line → the form parts to add. Blank and malformed lines are dropped. */
    fun formParts(): List<Pair<String, String>> =
        formFields.lineSequence()
            .mapNotNull { line ->
                val name = line.substringBefore('=', missingDelimiterValue = "").trim()
                if (name.isEmpty() || '=' !in line) return@mapNotNull null
                name to line.substringAfter('=').trim()
            }.toList()
}

/** Why a configuration cannot be used. The screen turns each into a sentence under the offending field. */
enum class ConfigProblem { BAD_SITE_URL, MISSING_TOKEN, IMPLAUSIBLE_TOKEN, MISSING_FILE_FIELD, MISSING_URL_PATH }

/**
 * `https://img.example.com/` → `https://img.example.com`.
 *
 * A pasted address arrives with a trailing slash about half the time, and every path this app
 * appends starts with one; normalising on the way in is what keeps `//api/v1/upload` from happening.
 */
internal fun String.normalizedSiteUrl(): String = trim().trimEnd('/')

private fun String.looksLikeHttpUrl(): Boolean {
    val value = trim()
    return (value.startsWith("https://") || value.startsWith("http://")) &&
        value.substringAfter("://").isNotBlank()
}
