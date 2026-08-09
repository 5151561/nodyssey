package io.github.plaza.core.update

import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Where a newer build comes from.
 *
 * An interface so the repository's decisions — is this newer, is the stored answer still fresh — can
 * be tested without a web server, and so a second source would be a constructor swap rather than a
 * rewrite.
 */
interface ReleaseSource {
    /**
     * The newest published release that carries an APK, or null when there is none.
     *
     * Null covers "the latest release has no APK attached" as well as "there is no release at all".
     * Both mean the same thing to the caller: nothing here can be installed.
     */
    suspend fun latestRelease(): AppRelease?

    /** Streams the APK into [target], reporting `(downloadedBytes, totalBytes)` as it goes. */
    suspend fun download(
        release: AppRelease,
        target: File,
        onProgress: (Long, Long) -> Unit,
    )
}

/**
 * GitHub Releases, which is where the apps in this repository publish.
 *
 * `releases/latest` is asked rather than the full list: GitHub already excludes drafts and
 * pre-releases from it, so "latest" means the same thing here and on the releases page. The call is
 * unauthenticated — 60 requests an hour per address, against a check that runs at most every six
 * hours — so no token is needed and none is stored.
 *
 * @param repository `owner/name`, one of the two things here that are about one particular app.
 * @param assetNamePrefix what this app's own APK is named after. `releases/latest` answers for the
 * *repository*, not for an app, so when two apps publish to one repository the newest release can
 * easily be the other one's — and an APK with a different `applicationId` is either "app not
 * installed" or a second copy of a different app. No default: an app that has not stated which asset
 * is its own has not thought about the question, and the failure it invites lands on a user's phone.
 */
class GitHubReleaseSource(
    private val okHttpClient: OkHttpClient,
    private val dispatchers: AppDispatchers,
    private val userAgent: String,
    private val repository: String,
    private val assetNamePrefix: String,
) : ReleaseSource {
    private val releasesUrl = releasesUrl(repository)
    private val latestReleaseUrl = "https://api.github.com/repos/$repository/releases/latest"
    override suspend fun latestRelease(): AppRelease? =
        withContext(dispatchers.io) {
            val request =
                Request
                    .Builder()
                    .url(latestReleaseUrl)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", API_VERSION)
                    .header("User-Agent", userAgent)
                    .build()

            execute(request).use { response ->
                val payload = response.body.string()
                if (!response.isSuccessful) {
                    throw AppUpdateException(UpdateFailure.Server(response.code))
                }
                GitHubReleases.parseLatest(payload, releasesUrl, assetNamePrefix)
            }
        }

    override suspend fun download(
        release: AppRelease,
        target: File,
        onProgress: (Long, Long) -> Unit,
    ) {
        withContext(dispatchers.io) {
            val request =
                Request
                    .Builder()
                    .url(release.downloadUrl)
                    .header("Accept", "application/octet-stream")
                    .header("User-Agent", userAgent)
                    .build()

            execute(request).use { response ->
                if (!response.isSuccessful) {
                    throw AppUpdateException(UpdateFailure.Server(response.code))
                }
                // The asset's own size is the fallback: the redirect to the storage host normally
                // declares a length, but a chunked answer would leave the bar with nothing to show.
                val declared = response.body.contentLength()
                val total = if (declared > 0L) declared else release.sizeBytes

                try {
                    target.parentFile?.mkdirs()
                    response.body.byteStream().use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var downloaded = 0L
                            while (true) {
                                // Cancellation is the 取消 button and the only way out of this loop
                                // other than the end of the stream.
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                onProgress(downloaded, total)
                            }
                            output.flush()
                        }
                    }
                } catch (e: IOException) {
                    // Writing failed, not reading: the cache directory is full or gone.
                    throw AppUpdateException(UpdateFailure.Storage, e)
                }
            }
        }
    }

    private fun execute(request: Request) =
        try {
            okHttpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw AppUpdateException(UpdateFailure.Network, e)
        }

    companion object {
        /**
         * The human releases page for [repository] — where an install that will not start on its own
         * sends the user instead.
         */
        fun releasesUrl(repository: String): String = "https://github.com/$repository/releases"

        private const val API_VERSION = "2022-11-28"
    }
}

/** The parsing half, kept apart from the transport so it can be tested against a captured payload. */
internal object GitHubReleases {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseLatest(payload: String, releasesUrl: String, assetNamePrefix: String): AppRelease? {
        val release =
            try {
                json.decodeFromString<ReleaseDto>(payload)
            } catch (e: IllegalArgumentException) {
                throw AppUpdateException(UpdateFailure.Unreadable, e)
            }
        // `releases/latest` already excludes both; checked anyway because a release the project would
        // not publish must never be pushed at a user as an update.
        if (release.draft || release.prerelease) return null

        // Both halves of the name matter. `.apk` alone would take a sibling app's build off a release
        // this app has nothing to do with; the prefix alone would take a mapping file or a checksum.
        val asset =
            release.assets.firstOrNull {
                it.name.startsWith(assetNamePrefix, ignoreCase = true) &&
                    it.name.endsWith(".apk", ignoreCase = true) &&
                    it.browserDownloadUrl.isNotBlank()
            } ?: return null
        val versionName = versionNameOfTag(release.tagName)
        if (versionName.isBlank()) return null

        return AppRelease(
            versionName = versionName,
            tag = release.tagName.trim(),
            notes = release.body.trim(),
            downloadUrl = asset.browserDownloadUrl,
            assetName = asset.name,
            sizeBytes = asset.size,
            htmlUrl = release.htmlUrl.ifBlank { releasesUrl },
        )
    }

    @Serializable
    private data class ReleaseDto(
        @SerialName("tag_name") val tagName: String = "",
        val body: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        @SerialName("html_url") val htmlUrl: String = "",
        val assets: List<AssetDto> = emptyList(),
    )

    @Serializable
    private data class AssetDto(
        val name: String = "",
        val size: Long = 0L,
        @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    )
}
