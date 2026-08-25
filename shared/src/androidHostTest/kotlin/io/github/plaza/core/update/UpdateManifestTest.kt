package io.github.plaza.core.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.MessageDigest

/**
 * The manifests as `.github/workflows/release.yml` writes them.
 *
 * The fixtures are what `build-update-manifests.py` produces, kept here so a change to either side
 * fails a test rather than a phone: the two files are one protocol with the generator at one end and
 * these types at the other.
 */
class UpdateManifestTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a stable manifest describes an installable release`() {
        val release =
            requireNotNull(json.decodeFromString<UpdateManifest>(load("update-stable.json")).toRelease())

        assertEquals("1.2.9", release.versionName)
        assertEquals("v1.2.9", release.tag)
        assertEquals("nodyssey-v1.2.9.apk", release.assetName)
        assertEquals(
            "https://github.com/5151561/nodyssey/releases/download/v1.2.9/nodyssey-v1.2.9.apk",
            release.downloadUrl,
        )
        assertEquals(8_830_112L, release.sizeBytes)
        assertEquals("740c22968aa915f399feb93850c689787651bc2e52daa8f166fd7f22607b2a95", release.sha256)
        assertEquals(false, release.preRelease)
        assertEquals(true, release.notes.startsWith("### 新增"))
    }

    /** The channel is what marks a test build, and it is what the card puts a 「dev 测试版」 tag on. */
    @Test
    fun `a dev manifest is marked as a pre-release`() {
        val release =
            requireNotNull(json.decodeFromString<UpdateManifest>(load("update-dev.json")).toRelease())

        assertEquals("1.3.0-dev.2", release.versionName)
        assertEquals(true, release.preRelease)
    }

    /**
     * A manifest from a future the installed build does not know is refused rather than read for the
     * fields that happen to look familiar — an update is not something to guess at.
     */
    @Test
    fun `a manifest with an unknown schema offers nothing`() {
        val payload = load("update-stable.json").replace("\"schema\": 1", "\"schema\": 2")

        assertNull(json.decodeFromString<UpdateManifest>(payload).toRelease())
    }

    /** A manifest that names no APK cannot be installed, whatever else it says. */
    @Test
    fun `a manifest with no download offers nothing`() {
        val payload =
            """
            {"schema": 1, "channel": "stable", "versionName": "1.2.9", "apk": {"name": "x.apk"}}
            """.trimIndent()

        assertNull(json.decodeFromString<UpdateManifest>(payload).toRelease())
    }

    @Test
    fun `the changelog is the version history, newest first`() {
        val log = json.decodeFromString<ChangelogManifest>(load("update-changelog.json"))
        val notes = log.versions.map { it.toNote() }

        assertEquals(listOf("1.2.9", "1.2.8", "1.2.7"), notes.map { it.versionName })
        assertEquals("2026-08-17", notes.first().publishedOn)
        assertEquals(
            "https://github.com/5151561/nodyssey/releases/tag/v1.2.9",
            notes.first().htmlUrl,
        )
    }

    /**
     * The APK is signed and the installer checks that, so this is not what stands between a user and a
     * hostile build — it is what tells a truncated download apart from a corrupt one before the
     * installer answers both with 「解析包时出现问题」.
     */
    @Test
    fun `a download is checked against the digest the manifest published`() {
        val downloaded = sha256("nodyssey".toByteArray())
        val somethingElse = sha256("nodyssey-but-truncated".toByteArray())

        assertEquals(true, matchesChecksum(hex(downloaded), downloaded))
        assertEquals(true, matchesChecksum(hex(downloaded).uppercase(), downloaded))
        assertEquals(false, matchesChecksum(hex(somethingElse), downloaded))
        // The workflow always states a digest, so a manifest without one was written by someone
        // else — the download it names is refused, not waved through.
        assertEquals(false, matchesChecksum("", downloaded))
    }

    /**
     * The manifest is a static file on a Pages branch — the least protected link in the update
     * chain — so its download URL is held to what `release.yml` actually writes: this repository's
     * own `releases/download/` tree over HTTPS, nothing else. "Somewhere on GitHub" is not enough;
     * anyone can create a repository there.
     */
    @Test
    fun `a download may only come from this repository's own releases`() {
        val repo = "5151561/nodyssey"
        val good = "https://github.com/5151561/nodyssey/releases/download/v1.2.9/nodyssey-v1.2.9.apk"

        assertEquals(true, isTrustedDownloadUrl(good, repo))
        assertEquals(false, isTrustedDownloadUrl(good.replace("https", "http"), repo))
        assertEquals(false, isTrustedDownloadUrl("https://evil.example/nodyssey.apk", repo))
        assertEquals(
            false,
            isTrustedDownloadUrl("https://github.com/attacker/repo/releases/download/v1/x.apk", repo),
        )
        // `https://github.com@evil.example/` names evil.example; a URL leaning on that reading has
        // no honest reason to be in a manifest.
        assertEquals(
            false,
            isTrustedDownloadUrl("https://github.com@evil.example/$repo/releases/download/v1/x.apk", repo),
        )
        assertEquals(false, isTrustedDownloadUrl("not a url", repo))
    }

    /**
     * The fixture — the workflow's real output — passes whole, and blanking any one claim fails it.
     * The blank-digest case is the acceptance the review asked for: a manifest that states no sha256
     * is refused, not offered.
     */
    @Test
    fun `a release is trusted only with a digest, a home URL and a bare asset name`() {
        val repo = "5151561/nodyssey"
        val release =
            requireNotNull(json.decodeFromString<UpdateManifest>(load("update-stable.json")).toRelease())

        assertEquals(true, isTrustedRelease(release, repo))
        assertEquals(false, isTrustedRelease(release.copy(sha256 = ""), repo))
        assertEquals(false, isTrustedRelease(release.copy(downloadUrl = "https://evil.example/x.apk"), repo))
        assertEquals(false, isTrustedRelease(release.copy(assetName = "../x.apk"), repo))
    }

    /** The asset name becomes a filename in the cache; a separator in it is choosing another directory. */
    @Test
    fun `an asset name with a path separator is refused`() {
        assertEquals(true, isSafeAssetName("nodyssey-v1.2.9.apk"))
        assertEquals(false, isSafeAssetName("../../../data/app/injected.apk"))
        assertEquals(false, isSafeAssetName("evil\\name.apk"))
        assertEquals(false, isSafeAssetName(""))
        assertEquals(false, isSafeAssetName(".."))
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun load(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing fixture: $name"
        }.bufferedReader().use { it.readText() }
}
