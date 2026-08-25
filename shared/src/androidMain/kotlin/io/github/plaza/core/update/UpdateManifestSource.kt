package io.github.plaza.core.update

import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Where a newer build comes from.
 *
 * An interface so the repository's decisions — is this newer, is the stored answer still fresh — can
 * be tested without a web server, and so a second source would be a constructor swap rather than a
 * rewrite.
 */
interface ReleaseSource {
    /**
     * The newest release on the given channel, or null when that channel has published nothing.
     *
     * @param includePreRelease whether test builds count — 设置 → 接收 dev 版更新. No default: which
     * channel a check reads is the user's answer, and a source that guessed it would be guessing at
     * whether someone gets shipped an untested build.
     */
    suspend fun latestRelease(includePreRelease: Boolean): AppRelease?

    /**
     * The published version history, newest first — what 更新日志 renders.
     *
     * @param includePreRelease whether test builds are listed, the same 接收 dev 版更新 answer.
     */
    suspend fun releaseNotes(includePreRelease: Boolean): List<ReleaseNote>

    /** Streams the APK into [target], reporting `(downloadedBytes, totalBytes)` as it goes. */
    suspend fun download(
        release: AppRelease,
        target: File,
        onProgress: (Long, Long) -> Unit,
    )
}

/**
 * The project's own update manifests: three static files, fetched over plain HTTPS.
 *
 * **Not `api.github.com`.** The obvious implementation of "is there a newer build" is
 * `GET /repos/<repo>/releases/latest`, and it is what this used to do. Its anonymous quota is sixty
 * calls an hour counted **per address**, shared with every other client behind the same NAT or proxy
 * exit — a browser extension, someone else's updater, a script — so a phone that has asked GitHub
 * nothing all day still meets a 403 saying it asked too much. That is not a bug to work around with a
 * fallback; it is the wrong protocol for a client that ships to strangers. What desktop updaters do
 * instead — Sparkle's appcast, electron-builder's `latest.yml` — is publish a small static file beside
 * the installer and have the client read that. This is the same shape:
 *
 * - `stable.json` / `dev.json` — one manifest per channel, describing that channel's newest build.
 * - `changelog.json` — the version history, so 更新日志 is a file rather than a query.
 *
 * The files are written by `.github/workflows/release.yml` onto the `updates` branch and served from
 * the app's own Pages site, which is a CDN with no per-address budget to spend. (`raw.githubusercontent
 * .com` serves the same branch and was tried first: it has a throttle of its own, and on the proxy
 * exit this was tested behind it answers 429 — the failure the API was left for, one host over.) The
 * release itself still hosts the APK; nothing about where builds live changed.
 *
 * Owning the schema is the other half of the argument: [UpdateManifest] carries a SHA-256 the download
 * is checked against, and adding a field later — a minimum OS, a staged rollout — is a change to this
 * project's own file rather than a hunt for a GitHub field that means roughly that.
 *
 * @param manifestBaseUrl the directory the three files sit in, trailing slash included.
 * @param repository `owner/name`, the one repository a download is allowed to come from. The manifest
 * names the download URL, and the manifest is the least protected link in the chain — a static file
 * on a Pages branch, not a signed artifact — so its claims are checked against what `release.yml`
 * would actually write rather than taken at their word.
 */
class UpdateManifestSource(
    private val okHttpClient: OkHttpClient,
    private val dispatchers: AppDispatchers,
    private val userAgent: String,
    private val manifestBaseUrl: String,
    private val repository: String,
) : ReleaseSource {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun latestRelease(includePreRelease: Boolean): AppRelease? =
        withContext(dispatchers.io) {
            val channelFile = if (includePreRelease) DEV_MANIFEST else STABLE_MANIFEST
            val manifest =
                try {
                    json.decodeFromString<UpdateManifest>(fetch(manifestBaseUrl + channelFile))
                } catch (e: IllegalArgumentException) {
                    throw AppUpdateException(UpdateFailure.Unreadable, e)
                }
            manifest.toRelease()?.also(::requireTrusted)
        }

    override suspend fun releaseNotes(includePreRelease: Boolean): List<ReleaseNote> =
        withContext(dispatchers.io) {
            val log =
                try {
                    json.decodeFromString<ChangelogManifest>(fetch(manifestBaseUrl + CHANGELOG_MANIFEST))
                } catch (e: IllegalArgumentException) {
                    throw AppUpdateException(UpdateFailure.Unreadable, e)
                }
            log.versions
                .filter { includePreRelease || !it.preRelease }
                .map { it.toNote() }
                .sortedWith { left, right -> compareVersionNames(right.versionName, left.versionName) }
        }

    override suspend fun download(
        release: AppRelease,
        target: File,
        onProgress: (Long, Long) -> Unit,
    ) {
        withContext(dispatchers.io) {
            // Checked again even though `latestRelease` already refused an untrusted answer: the
            // release being downloaded may be a stored one, written by a build that vetted less.
            requireTrusted(release)
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
                // The manifest's size is the fallback: the redirect to the storage host normally
                // declares a length, but a chunked answer would leave the bar with nothing to show.
                val declared = response.body.contentLength()
                val total = if (declared > 0L) declared else release.sizeBytes

                val digest = MessageDigest.getInstance("SHA-256")
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
                                digest.update(buffer, 0, read)
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

                // Checked here rather than after the rename: a file that fails this must never exist
                // under the name the installer is handed.
                verifyChecksum(release, digest.digest())
            }
        }
    }

    /**
     * Refuses a download whose bytes are not the ones the manifest published.
     *
     * The APK is signed and the system installer checks that, so this is not the thing standing
     * between a user and a hostile build — it is what tells a truncated or mangled download apart from
     * a corrupt one, before the installer says "解析包时出现问题" with no way to know which it was.
     */
    private fun verifyChecksum(release: AppRelease, actual: ByteArray) {
        if (!matchesChecksum(release.sha256, actual)) {
            throw AppUpdateException(UpdateFailure.Checksum)
        }
    }

    /**
     * Refuses a release whose manifest claims something `release.yml` would never write.
     *
     * The workflow always states a digest, always publishes onto this repository's own releases, and
     * always names the asset with a bare filename — so a manifest missing any of the three is not an
     * older format to be lenient with, it is a file someone else wrote. Refusing it as unreadable is
     * the honest answer: the check screen reports a broken update channel instead of quietly offering
     * 已是最新, which is what returning null here would amount to.
     *
     * This is defence in depth, not the last line — the installer still verifies the APK signature.
     * What it removes is the step before that: a tampered manifest steering the downloader at an
     * arbitrary URL, or a crafted `assetName` walking the target file out of the cache directory.
     */
    private fun requireTrusted(release: AppRelease) {
        if (!isTrustedRelease(release, repository)) {
            throw AppUpdateException(UpdateFailure.Unreadable)
        }
    }

    /**
     * A manifest's body.
     *
     * A 404 is reported like any other refusal rather than read as "nothing new". Every release
     * publishes all three files, so a build old enough to be asking is a build whose own release wrote
     * them — a missing file means the site is misconfigured or the address is wrong, and answering
     * 已是最新 to that would be the update check quietly switching itself off.
     */
    private fun fetch(url: String): String {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .build()

        return execute(request).use { response ->
            val payload =
                try {
                    response.body.string()
                } catch (e: IOException) {
                    // A connection that dies partway through the body throws here, not at execute();
                    // unwrapped it would escape the repository's catch and take the process with it.
                    throw AppUpdateException(UpdateFailure.Network, e)
                }
            if (!response.isSuccessful) {
                throw AppUpdateException(UpdateFailure.Server(response.code))
            }
            payload
        }
    }

    private fun execute(request: Request): Response =
        try {
            okHttpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw AppUpdateException(UpdateFailure.Network, e)
        }

    companion object {
        const val STABLE_MANIFEST = "stable.json"
        const val DEV_MANIFEST = "dev.json"
        const val CHANGELOG_MANIFEST = "changelog.json"
    }
}

/**
 * One channel's newest build, as `release.yml` wrote it.
 *
 * The field names are this project's own and the workflow's `build-update-manifests.py` is the other
 * end of them; changing one without the other leaves users on the old build with a manifest they
 * cannot read. [schema] exists for the day that has to happen anyway: a client that meets a number it
 * does not know refuses the file rather than guessing at its fields.
 */
@Serializable
internal data class UpdateManifest(
    val schema: Int = 1,
    val channel: String = "",
    @SerialName("versionName") val versionName: String = "",
    val tag: String = "",
    val notes: String = "",
    @SerialName("publishedOn") val publishedOn: String = "",
    val apk: ApkManifest = ApkManifest(),
    @SerialName("releaseUrl") val releaseUrl: String = "",
) {
    fun toRelease(): AppRelease? {
        if (schema != SUPPORTED_SCHEMA) return null
        if (versionName.isBlank() || apk.url.isBlank()) return null
        return AppRelease(
            versionName = versionName,
            tag = tag.ifBlank { "v$versionName" },
            notes = notes.trim(),
            downloadUrl = apk.url,
            assetName = apk.name.ifBlank { "$versionName.apk" },
            sizeBytes = apk.sizeBytes,
            htmlUrl = releaseUrl,
            preRelease = channel == DEV_CHANNEL,
            sha256 = apk.sha256,
        )
    }
}

@Serializable
internal data class ApkManifest(
    val name: String = "",
    val url: String = "",
    @SerialName("sizeBytes") val sizeBytes: Long = 0L,
    val sha256: String = "",
)

@Serializable
internal data class ChangelogManifest(
    val schema: Int = 1,
    val versions: List<ChangelogEntry> = emptyList(),
)

@Serializable
internal data class ChangelogEntry(
    @SerialName("versionName") val versionName: String = "",
    val tag: String = "",
    val notes: String = "",
    @SerialName("publishedOn") val publishedOn: String = "",
    @SerialName("preRelease") val preRelease: Boolean = false,
    @SerialName("releaseUrl") val releaseUrl: String = "",
) {
    fun toNote(): ReleaseNote =
        ReleaseNote(
            versionName = versionName,
            tag = tag.ifBlank { "v$versionName" },
            notes = notes.trim(),
            publishedOn = publishedOn,
            preRelease = preRelease,
            htmlUrl = releaseUrl,
        )
}

/** The three claims `requireTrusted` holds a manifest to, as one testable answer. */
internal fun isTrustedRelease(release: AppRelease, repository: String): Boolean =
    release.sha256.isNotBlank() &&
        isTrustedDownloadUrl(release.downloadUrl, repository) &&
        isSafeAssetName(release.assetName)

/**
 * Whether [actual] is the digest [expected] names. A blank expectation matches nothing.
 *
 * Blank used to pass, on the reasoning that refusing a download over a field the publisher left empty
 * would be inventing a requirement. But the publisher is `build-update-manifests.py` and it has always
 * filled the field in — so the only manifest that arrives without one is a manifest the workflow did
 * not write, and waving its download through is precisely the wrong reflex. `requireTrusted` refuses
 * such a release long before the bytes arrive; this returning false is the same rule restated at the
 * last gate rather than a second opinion.
 */
internal fun matchesChecksum(expected: String, actual: ByteArray): Boolean {
    if (expected.isBlank()) return false
    val hex = actual.joinToString("") { byte -> "%02x".format(byte) }
    return hex.equals(expected.trim(), ignoreCase = true)
}

/**
 * Whether [url] is somewhere `release.yml` actually publishes: HTTPS, `github.com`, and this
 * repository's own `releases/download/` tree — not merely GitHub, because "any repository on GitHub"
 * is an arbitrary host with extra steps when anyone can create one.
 *
 * The redirect GitHub answers with (to `objects.githubusercontent.com`) is followed inside OkHttp and
 * needs no entry here: this vets where the downloader is *sent*, not every hop the bytes travel.
 * A `userInfo` is refused for the same reason `NodeSeekSite.parseWebUrl` refuses one — in
 * `https://github.com@evil.example/` the host is `evil.example`, and OkHttp's parser puts it there,
 * but a URL relying on that reading has no honest reason to exist in a manifest.
 */
internal fun isTrustedDownloadUrl(url: String, repository: String): Boolean {
    val parsed = url.toHttpUrlOrNull() ?: return false
    return parsed.isHttps &&
        parsed.host == "github.com" &&
        parsed.username.isEmpty() &&
        parsed.password.isEmpty() &&
        parsed.encodedPath.startsWith("/$repository/releases/download/")
}

/**
 * Whether [name] can safely become a filename in the download cache.
 *
 * `prepareTarget` hands it to `File(directory, name)`, which happily resolves `../` segments — so a
 * name carrying a separator is a manifest choosing where on disk the APK lands, and the only names
 * with a separator in them are hostile ones: the workflow names assets `nodyssey-v1.2.9.apk`.
 */
internal fun isSafeAssetName(name: String): Boolean =
    name.isNotBlank() && '/' !in name && '\\' !in name && name != "." && name != ".."

private const val SUPPORTED_SCHEMA = 1
private const val DEV_CHANNEL = "dev"
