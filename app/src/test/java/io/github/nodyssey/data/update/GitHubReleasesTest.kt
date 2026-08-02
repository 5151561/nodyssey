package io.github.nodyssey.data.update

import io.github.nodyssey.core.html.Fixtures
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
    @Test
    fun `the APK asset is picked, not the first one`() {
        val release =
            requireNotNull(GitHubReleases.parseLatest(Fixtures.load("github-release-latest.json")))

        assertEquals("1.2.0", release.versionName)
        assertEquals("v1.2.0", release.tag)
        assertEquals("nodyssey-v1.2.0.apk", release.assetName)
        assertEquals(
            "https://github.com/5151561/nodyssey/releases/download/v1.2.0/nodyssey-v1.2.0.apk",
            release.downloadUrl,
        )
        assertEquals(8_830_112L, release.sizeBytes)
        assertEquals("https://github.com/5151561/nodyssey/releases/tag/v1.2.0", release.htmlUrl)
        assertEquals(true, release.notes.startsWith("### 新增"))
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
                {"name": "source.zip", "size": 10, "browser_download_url": "https://example.invalid/source.zip"}
              ]
            }
            """.trimIndent()

        assertNull(GitHubReleases.parseLatest(payload))
    }

    @Test
    fun `a draft or a pre-release is not offered as an update`() {
        val draft =
            """
            {
              "tag_name": "v9.9.9",
              "draft": true,
              "assets": [
                {"name": "nodyssey-v9.9.9.apk", "size": 10, "browser_download_url": "https://example.invalid/a.apk"}
              ]
            }
            """.trimIndent()

        assertNull(GitHubReleases.parseLatest(draft))
        assertNull(GitHubReleases.parseLatest(draft.replace("\"draft\": true", "\"prerelease\": true")))
    }

    @Test
    fun `something other than the release JSON is reported as unreadable`() {
        val failure =
            assertThrows(AppUpdateException::class.java) {
                GitHubReleases.parseLatest("<html>rate limited</html>")
            }

        assertEquals(UpdateFailure.Unreadable, failure.failure)
    }
}
