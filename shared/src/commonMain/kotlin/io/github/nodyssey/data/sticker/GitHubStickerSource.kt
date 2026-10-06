package io.github.nodyssey.data.sticker

import io.github.nodyssey.data.text
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * A repository as the reader pasted it: `github.com/owner/repo`, with or without a scheme, a
 * trailing `.git`, or a `/tree/branch/sub/dir` that narrows it to one branch and one folder.
 *
 * A branch name with a `/` in it cannot be told apart from a folder in that last form — GitHub's own
 * URLs are ambiguous there — so the first segment after `tree` is taken as the branch, which is
 * right for every pack worth subscribing to.
 */
data class GitHubRepoRef(
    val owner: String,
    val repo: String,
    /** Null for the default branch. */
    val ref: String? = null,
    /** Repository-relative folder the listing is narrowed to; empty for the whole repository. */
    val subPath: String = "",
) {
    val slug: String get() = "$owner/$repo"

    companion object {
        private val OWNER = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})")
        private val REPO = Regex("[A-Za-z0-9._-]{1,100}")

        fun parse(input: String): GitHubRepoRef? {
            var rest = input.trim().substringBefore('#').substringBefore('?')
            rest = rest.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            if (rest.startsWith("github.com/", ignoreCase = true)) {
                rest = rest.substring("github.com/".length)
            } else if ('.' in rest.substringBefore('/')) {
                // Some other host: a jsDelivr link, a mirror. Not something this knows how to list.
                return null
            }
            val segments = rest.split('/').filter { it.isNotEmpty() }
            if (segments.size < 2) return null
            val owner = segments[0]
            val repo = segments[1].removeSuffix(".git")
            if (!OWNER.matches(owner) || !REPO.matches(repo) || repo == "." || repo == "..") return null
            // A link copied out of a browser's address bar arrives percent-encoded — `熊猫头` as
            // `%E7%86%8A…`. Decoded here once; the branch is encoded again on the way out by
            // [GitHubStickerSource.headOf], and the folder is compared against tree paths as is.
            val ref = if (segments.getOrNull(2) == "tree") segments.getOrNull(3)?.let(::decodePercent) else null
            val subPath = if (ref != null) segments.drop(4).joinToString("/") { decodePercent(it) } else ""
            return GitHubRepoRef(owner, repo, ref, subPath)
        }
    }
}

/**
 * `%E7%86%8A` → `熊`: every `%XX` read as a UTF-8 byte. A `%` that does not start a valid escape is
 * kept as it is, since a hand-typed link has no reason to have been encoded at all.
 */
internal fun decodePercent(text: String): String {
    if ('%' !in text) return text
    val bytes = ArrayList<Byte>(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        val high = text.getOrNull(i + 1)?.digitToIntOrNull(16)
        val low = text.getOrNull(i + 2)?.digitToIntOrNull(16)
        if (c == '%' && high != null && low != null) {
            bytes += (high * 16 + low).toByte()
            i += 3
        } else {
            c.toString().encodeToByteArray().forEach { bytes += it }
            i++
        }
    }
    return bytes.toByteArray().decodeToString()
}

/** What a repository holds at one commit, grouped the way the panel will show it. */
data class RepoListing(
    /** As GitHub spells it, not as the link did — the subscription is keyed by this. */
    val owner: String,
    val repo: String,
    val ref: String,
    val sha: String,
    /** The folders under [subPath], or every folder when it is empty. */
    val folders: List<RepoFolder>,
    /** GitHub cut the tree short; some folders may be missing. */
    val truncated: Boolean,
    /** What the link narrowed the listing to; empty for the whole repository. */
    val subPath: String = "",
    /**
     * The folders at [sha] outside [subPath] — read from the same tree call, so free. Re-subscribing
     * through a link to one folder moves the whole repository's pin, and the folders it already had
     * elsewhere need their lists at the new commit too.
     */
    val otherFolders: List<RepoFolder> = emptyList(),
) {
    val slug: String get() = "$owner/$repo"
}

/** One folder that directly contains images. */
data class RepoFolder(
    val path: String,
    val files: List<String>,
    val totalBytes: Long,
)

/** The tab title for a folder: its own name, or the repository's for the root. */
fun folderName(
    path: String,
    repo: String,
): String = path.substringAfterLast('/').ifEmpty { repo }

sealed interface StickerSourceError {
    /** No such repository, branch or folder — or a private one, which GitHub answers the same way. */
    data object NotFound : StickerSourceError

    /**
     * GitHub's anonymous limit: sixty calls an hour per address, shared by everybody behind the same
     * NAT. [resetAtEpochSeconds] is when it lifts, if GitHub said.
     */
    data class RateLimited(val resetAtEpochSeconds: Long?) : StickerSourceError

    /** The repository exists and has no picture in it (or not under the folder asked for). */
    data object NoImages : StickerSourceError

    data object Network : StickerSourceError

    data class Http(val code: Int) : StickerSourceError

    data object Unparsable : StickerSourceError
}

class StickerSourceException(
    val error: StickerSourceError,
) : Exception(error.toString())

/**
 * Reads a repository's file list through GitHub's REST API — three anonymous calls per listing, the
 * repository (for its default branch), the commit that branch points at, and the recursive tree of
 * that commit. Nothing is downloaded but the list: the pictures are fetched by the panel, a cell at
 * a time, from whichever CDN 表情管理 has chosen.
 *
 * The API rather than jsDelivr's own listing endpoint because only the API answers "which commit is
 * the branch on now", and that is the question an update check asks.
 */
class GitHubStickerSource(
    private val http: HttpTransport,
    /** [HttpTransport.execute] blocks on Android, and the screens call in from the main thread. */
    private val dispatchers: AppDispatchers,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Asks for the repository first even when the link named a branch, because its `full_name` is
     * the one spelling of `owner/repo`: GitHub answers a link typed in the wrong case as readily as
     * the real one, and a subscription keyed by what the reader typed would be a second subscription.
     */
    suspend fun list(ref: GitHubRepoRef): RepoListing {
        val repository = parse(get("$API/repos/${ref.owner}/${ref.repo}"))
        val (owner, repo) = repository.text("full_name")?.split('/')?.takeIf { it.size == 2 }?.let { it[0] to it[1] }
            ?: (ref.owner to ref.repo)
        val branch = ref.ref ?: repository.text("default_branch")
            ?: throw StickerSourceException(StickerSourceError.Unparsable)
        val sha = headOf(owner, repo, branch)
        return listAt(owner, repo, branch, sha, ref.subPath)
    }

    /** The commit [branch] points at now. */
    suspend fun headOf(
        owner: String,
        repo: String,
        branch: String,
    ): String {
        val response = get("$API/repos/$owner/$repo/commits/${encodeSegment(branch)}", accept = "application/vnd.github.sha")
        val sha = response.body.trim()
        if (!SHA.matches(sha)) throw StickerSourceException(StickerSourceError.Unparsable)
        return sha
    }

    suspend fun listAt(
        owner: String,
        repo: String,
        branch: String,
        sha: String,
        subPath: String = "",
    ): RepoListing {
        val body = parse(get("$API/repos/$owner/$repo/git/trees/$sha?recursive=1"))
        val tree = runCatching { body["tree"]!!.jsonArray }.getOrNull()
            ?: throw StickerSourceException(StickerSourceError.Unparsable)
        val blobs = tree.mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            if (entry.text("type") != "blob") return@mapNotNull null
            val path = entry.text("path") ?: return@mapNotNull null
            TreeBlob(path, entry["size"]?.jsonPrimitive?.longOrNull ?: 0L)
        }
        val prefix = subPath.trim('/')
        val (folders, others) = groupImageFolders(blobs, subPath = "").partition { isUnder(it.path, prefix) }
        if (folders.isEmpty()) throw StickerSourceException(StickerSourceError.NoImages)
        return RepoListing(
            owner = owner,
            repo = repo,
            ref = branch,
            sha = sha,
            folders = folders,
            truncated = runCatching { body["truncated"]?.jsonPrimitive?.boolean }.getOrNull() == true,
            subPath = prefix,
            otherFolders = others,
        )
    }

    private suspend fun get(
        url: String,
        accept: String = "application/vnd.github+json",
    ): HttpResponse {
        val response = try {
            withContext(dispatchers.io) {
                http.execute(
                    HttpRequest(
                        url = url,
                        headers = mapOf("Accept" to accept, "X-GitHub-Api-Version" to "2022-11-28"),
                    ),
                )
            }
        } catch (_: SiteException) {
            throw StickerSourceException(StickerSourceError.Network)
        }
        if (response.isSuccessful) return response
        throw StickerSourceException(classify(response))
    }

    private fun parse(response: HttpResponse): JsonObject =
        runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrNull()
            ?: throw StickerSourceException(StickerSourceError.Unparsable)

    internal data class TreeBlob(
        val path: String,
        val size: Long,
    )

    companion object {
        private const val API = "https://api.github.com"
        private val SHA = Regex("[0-9a-f]{40}")

        /**
         * 403 is GitHub's rate-limit answer as often as 429 is, and also its answer to a blocked
         * repository; the remaining-calls header is what tells them apart.
         */
        internal fun classify(response: HttpResponse): StickerSourceError =
            when {
                response.code == 404 || response.code == 422 -> StickerSourceError.NotFound

                response.code == 429 || (response.code == 403 && response.header("x-ratelimit-remaining") == "0") ->
                    StickerSourceError.RateLimited(response.header("x-ratelimit-reset")?.toLongOrNull())

                else -> StickerSourceError.Http(response.code)
            }

        /**
         * Image files grouped by the folder that directly holds them, under [subPath] when given,
         * folders in path order and files in path order within each.
         */
        internal fun groupImageFolders(
            blobs: List<TreeBlob>,
            subPath: String,
        ): List<RepoFolder> {
            val prefix = subPath.trim('/').let { if (it.isEmpty()) "" else "$it/" }
            return blobs
                .filter { it.path.startsWith(prefix) && isImagePath(it.path) }
                .groupBy { it.path.substringBeforeLast('/', missingDelimiterValue = "") }
                .map { (folder, files) ->
                    RepoFolder(
                        path = folder,
                        files = files.map { it.path }.sorted(),
                        totalBytes = files.sumOf { it.size },
                    )
                }.sortedBy { it.path }
        }

        /** [path] is the folder [folder] or somewhere below it — `a/bc` is not under `a/b`. */
        internal fun isUnder(
            path: String,
            folder: String,
        ): Boolean = folder.isEmpty() || path == folder || path.startsWith("$folder/")

        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp")

        fun isImagePath(path: String): Boolean =
            path.substringAfterLast('/').substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
    }
}
