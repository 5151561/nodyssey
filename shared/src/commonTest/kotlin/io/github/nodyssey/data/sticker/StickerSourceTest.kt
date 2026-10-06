package io.github.nodyssey.data.sticker

import io.github.plaza.core.net.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class StickerSourceTest {
    @Test
    fun `a tree link narrows to its branch and folder`() {
        val ref = GitHubRepoRef.parse("https://github.com/zhaoolee/ChineseBQB/tree/master/001Funny/sub")
        assertEquals(GitHubRepoRef("zhaoolee", "ChineseBQB", ref = "master", subPath = "001Funny/sub"), ref)
    }

    @Test
    fun `a tree link copied from the address bar is decoded once`() {
        val ref = GitHubRepoRef.parse("https://github.com/o/r/tree/%E5%88%86%E6%94%AF/%E7%86%8A%E7%8C%AB%E5%A4%B4/100%")
        assertEquals(GitHubRepoRef("o", "r", ref = "分支", subPath = "熊猫头/100%"), ref)
    }

    @Test
    fun `a bare owner and repo with a git suffix parses to the default branch`() {
        assertEquals(GitHubRepoRef("zhaoolee", "ChineseBQB"), GitHubRepoRef.parse(" zhaoolee/ChineseBQB.git "))
        assertEquals(GitHubRepoRef("a", "b"), GitHubRepoRef.parse("github.com/a/b?tab=readme#top"))
    }

    @Test
    fun `a link on another host is not taken for a repository`() {
        assertNull(GitHubRepoRef.parse("https://cdn.jsdelivr.net/gh/a/b@main/x.png"))
        assertNull(GitHubRepoRef.parse("github.com/onlyowner"))
    }

    @Test
    fun `images are grouped by the folder that holds them and other files are ignored`() {
        val blobs = listOf(
            GitHubStickerSource.TreeBlob("a/2.GIF", 20),
            GitHubStickerSource.TreeBlob("a/1.png", 10),
            GitHubStickerSource.TreeBlob("a/readme.md", 999),
            GitHubStickerSource.TreeBlob("b/c/3.webp", 30),
            GitHubStickerSource.TreeBlob("root.jpg", 5),
        )

        val all = GitHubStickerSource.groupImageFolders(blobs, subPath = "")
        assertEquals(listOf("", "a", "b/c"), all.map { it.path })
        assertEquals(listOf("a/1.png", "a/2.GIF"), all[1].files)
        assertEquals(30L, all[1].totalBytes)

        val narrowed = GitHubStickerSource.groupImageFolders(blobs, subPath = "/b/")
        assertEquals(listOf("b/c"), narrowed.map { it.path })
    }

    @Test
    fun `a 403 is a rate limit only when no calls remain`() {
        val limited = HttpResponse(403, "u", mapOf("x-ratelimit-remaining" to "0", "x-ratelimit-reset" to "1700000000"), "")
        assertEquals(StickerSourceError.RateLimited(1_700_000_000L), GitHubStickerSource.classify(limited))

        val blocked = HttpResponse(403, "u", mapOf("x-ratelimit-remaining" to "41"), "")
        assertEquals(StickerSourceError.Http(403), GitHubStickerSource.classify(blocked))

        assertIs<StickerSourceError.NotFound>(GitHubStickerSource.classify(HttpResponse(404, "u", emptyMap(), "")))
    }

    @Test
    fun `a pinned link percent-encodes a Chinese path for every CDN`() {
        val settings = StickerCdnSettings()
        val path = "熊猫头/摸鱼 1.gif"
        val encoded = "%E7%86%8A%E7%8C%AB%E5%A4%B4/%E6%91%B8%E9%B1%BC%201.gif"
        assertEquals("https://cdn.jsdelivr.net/gh/o/r@$SHA/$encoded", settings.urlFor("o", "r", SHA, path))
        assertEquals(
            "https://raw.githubusercontent.com/o/r/$SHA/$encoded",
            settings.urlFor("o", "r", SHA, path, StickerCdn.GITHUB_RAW),
        )
    }

    @Test
    fun `a custom mirror is used only once it has an https address`() {
        val unset = StickerCdnSettings(StickerCdn.CUSTOM, customBase = "fastly.jsdelivr.net/gh")
        assertEquals(StickerCdn.JSDELIVR, unset.effective)
        assertEquals("https://cdn.jsdelivr.net/gh/o/r@$SHA/a.png", unset.urlFor("o", "r", SHA, "a.png"))

        val set = StickerCdnSettings(StickerCdn.CUSTOM, customBase = " https://fastly.jsdelivr.net/gh ")
        assertEquals("https://fastly.jsdelivr.net/gh/o/r@$SHA/a.png", set.urlFor("o", "r", SHA, "a.png"))
    }

    @Test
    fun `pasted text yields its https links once each and in order`() {
        val text = "![a](https://x.com/a.gif)\nhttp://y.com/b.png\nhttps://z.com/c.png, https://x.com/a.gif"
        assertEquals(listOf("https://x.com/a.gif", "https://z.com/c.png"), extractStickerLinks(text))
    }

    @Test
    fun `a folder counts only pictures it does not have yet as new`() {
        val folder = SubscribedFolder(
            owner = "o",
            repo = "r",
            pinnedSha = SHA,
            path = "a",
            files = listOf("a/1.png", "a/2.png"),
            pendingFiles = listOf("a/2.png", "a/3.png", "a/4.png"),
            hidden = false,
        )
        assertEquals(2, folder.newCount)
    }

    private companion object {
        const val SHA = "0123456789abcdef0123456789abcdef01234567"
    }
}
