package io.github.plaza.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The fixture is shaped after GitHub's documented `releases/latest` response with the fields this
 * app never reads dropped. It is not a capture of a real release — the point of the test is the
 * shape, and a payload with two assets is the case worth pinning: the checksum file sits before the
 * APK, so "the first asset" is the wrong answer.
 */
class GitHubReleasesTest {
    private val releasesUrl = GitHubReleaseSource.releasesUrl(REPOSITORY)

    @Test
    fun `the APK asset is picked, not the first one`() {
        val release =
            requireNotNull(
                GitHubReleases.parseLatest(load("github-release-latest.json"), releasesUrl, PREFIX),
            )

        assertEquals("1.2.0", release.versionName)
        assertEquals("v1.2.0", release.tag)
        assertEquals("plaza-v1.2.0.apk", release.assetName)
        assertEquals(
            "https://github.com/$REPOSITORY/releases/download/v1.2.0/plaza-v1.2.0.apk",
            release.downloadUrl,
        )
        assertEquals(8_830_112L, release.sizeBytes)
        assertEquals("https://github.com/$REPOSITORY/releases/tag/v1.2.0", release.htmlUrl)
        assertEquals(true, release.notes.startsWith("### 新增"))
    }

    /** A release whose own `html_url` came back blank falls back to the repository's releases page. */
    @Test
    fun `a release with no page of its own falls back to the repository's`() {
        val payload =
            """
            {
              "tag_name": "v1.2.0",
              "html_url": "",
              "assets": [
                {"name": "plaza-a.apk", "size": 10, "browser_download_url": "https://example.invalid/a.apk"}
              ]
            }
            """.trimIndent()

        assertEquals(releasesUrl, GitHubReleases.parseLatest(payload, releasesUrl, PREFIX)?.htmlUrl)
    }

    /**
     * The case a repository with two apps in it creates: `releases/latest` answers for the repository,
     * so the newest release is regularly the sibling's, and its APK carries a different
     * `applicationId`. Offering it would end as "app not installed" on every phone that took it.
     */
    @Test
    fun `a sibling app's release is not offered as this app's update`() {
        val payload =
            """
            {
              "tag_name": "other-v9.9.9",
              "draft": false,
              "prerelease": false,
              "assets": [
                {"name": "other-v9.9.9.apk", "size": 10, "browser_download_url": "https://example.invalid/other.apk"}
              ]
            }
            """.trimIndent()

        assertNull(GitHubReleases.parseLatest(payload, releasesUrl, PREFIX))
    }

    /** And when one release carries both apps, the prefix is what tells them apart. */
    @Test
    fun `the APK belonging to this app is picked out of several`() {
        val payload =
            """
            {
              "tag_name": "v1.2.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {"name": "other-v1.2.0.apk", "size": 10, "browser_download_url": "https://example.invalid/other.apk"},
                {"name": "plaza-v1.2.0.apk", "size": 20, "browser_download_url": "https://example.invalid/plaza.apk"}
              ]
            }
            """.trimIndent()

        assertEquals("plaza-v1.2.0.apk", GitHubReleases.parseLatest(payload, releasesUrl, PREFIX)?.assetName)
    }

    @Test
    fun `a release with no APK attached offers nothing`() {
        val payload =
            """
            {
              "tag_name": "v1.2.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {"name": "plaza-source.zip", "size": 10, "browser_download_url": "https://example.invalid/source.zip"}
              ]
            }
            """.trimIndent()

        assertNull(GitHubReleases.parseLatest(payload, releasesUrl, PREFIX))
    }

    @Test
    fun `a draft or a pre-release is not offered as an update`() {
        val draft =
            """
            {
              "tag_name": "v9.9.9",
              "draft": true,
              "assets": [
                {"name": "plaza-v9.9.9.apk", "size": 10, "browser_download_url": "https://example.invalid/a.apk"}
              ]
            }
            """.trimIndent()

        assertNull(GitHubReleases.parseLatest(draft, releasesUrl, PREFIX))
        assertNull(
            GitHubReleases.parseLatest(
                draft.replace("\"draft\": true", "\"prerelease\": true"),
                releasesUrl,
                PREFIX,
            ),
        )
    }

    @Test
    fun `something other than the release JSON is reported as unreadable`() {
        val failure =
            assertThrows(AppUpdateException::class.java) {
                GitHubReleases.parseLatest("<html>rate limited</html>", releasesUrl, PREFIX)
            }

        assertEquals(UpdateFailure.Unreadable, failure.failure)
    }

    private fun load(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing fixture: $name"
        }.bufferedReader().use { it.readText() }

    private companion object {
        const val REPOSITORY = "example/plaza"
        const val PREFIX = "plaza-"
    }
}
