package io.github.nodyssey.data.sticker

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.nodyssey.data.composer.ImagePreparer
import io.github.nodyssey.data.imagehost.HostedImage
import io.github.nodyssey.data.imagehost.ImageHostConfig
import io.github.nodyssey.data.imagehost.ImageHostProvider
import io.github.nodyssey.data.imagehost.ImageHostRepository
import io.github.nodyssey.data.imagehost.ImageHostUpload
import io.github.nodyssey.data.local.MyStickerEntity
import io.github.nodyssey.data.local.StickerDao
import io.github.nodyssey.data.local.StickerFolderEntity
import io.github.nodyssey.data.local.StickerRepoEntity
import io.github.nodyssey.data.settings.StickerCdnStore
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.UploadProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StickerLibraryTest {
    @Test
    fun `new stickers go to the front and a link already saved keeps its place`() = runTest {
        val library = library()

        assertEquals(listOf("https://h/a.png"), library.add(listOf(MySticker("https://h/a.png", "a"))))
        val added = library.add(listOf(MySticker("https://h/b.png", "b"), MySticker("https://h/a.png", "renamed")))

        assertEquals(listOf("https://h/b.png"), added)
        assertEquals(listOf("b" to "https://h/b.png", "a" to "https://h/a.png"), library.mine.first().map { it.name to it.url })
    }

    @Test
    fun `a plain http link is never saved`() = runTest {
        val library = library()
        assertEquals(emptyList(), library.add(listOf(MySticker("http://h/a.png", "a"))))
        assertEquals(emptyList(), library.mine.first())
    }

    @Test
    fun `move to front keeps the moved stickers in their own order`() = runTest {
        val library = library()
        library.add(listOf("a", "b", "c", "d").map { MySticker("https://h/$it.png", it) })

        library.moveToTop(setOf("https://h/d.png", "https://h/b.png"))

        assertEquals(listOf("b", "d", "a", "c"), library.mine.first().map { it.name })
    }

    @Test
    fun `an update is held as pending until applied and a vanished folder goes with it`() = runTest {
        val github = FakeGitHub()
        val library = library(github)
        library.subscribe(
            RepoListing(
                owner = "o",
                repo = "r",
                ref = "main",
                sha = OLD,
                folders = listOf(
                    RepoFolder("a", listOf("a/1.png"), 1),
                    RepoFolder("b", listOf("b/1.png"), 1),
                ),
                truncated = false,
            ),
            paths = setOf("a", "b"),
        )
        github.head = NEW
        github.trees[NEW] = listOf("a/1.png", "a/2.png", "c/1.png")

        assertNull(library.checkForUpdates(force = true))

        val pending = library.subscriptions.first().single()
        assertEquals(OLD, pending.pinnedSha)
        assertEquals(listOf("a/1.png"), pending.folders.first { it.path == "a" }.files)
        assertEquals(1, pending.newCount)
        assertTrue(pending.hasUpdate)

        library.applyUpdate("o/r")

        val moved = library.subscriptions.first().single()
        assertEquals(NEW, moved.pinnedSha)
        assertEquals(listOf("a"), moved.folders.map { it.path })
        assertEquals(listOf("a/1.png", "a/2.png"), moved.folders.single().files)
        assertEquals(false, moved.hasUpdate)
    }

    @Test
    fun `a check inside the interval asks GitHub nothing`() = runTest {
        val github = FakeGitHub()
        val library = library(github)
        library.subscribe(
            RepoListing("o", "r", "main", OLD, listOf(RepoFolder("a", listOf("a/1.png"), 1)), truncated = false),
            paths = setOf("a"),
        )

        library.checkForUpdates(force = false)

        assertEquals(0, github.calls.size)
    }

    @Test
    fun `listing a repository resolves its default branch to a commit`() = runTest {
        val github = FakeGitHub()
        github.head = NEW
        github.trees[NEW] = listOf("pack/1.gif", "pack/2.png", "README.md")

        val listing = GitHubStickerSource(github).list(GitHubRepoRef("o", "r"))

        assertEquals("main", listing.ref)
        assertEquals(NEW, listing.sha)
        assertEquals(listOf("pack"), listing.folders.map { it.path })
        assertEquals(
            listOf("/repos/o/r", "/repos/o/r/commits/main", "/repos/o/r/git/trees/$NEW"),
            github.calls.map { it.substringAfter("api.github.com").substringBefore('?') },
        )
    }

    private fun TestScope.library(github: HttpTransport = FakeGitHub()) =
        StickerLibrary(
            dao = FakeStickerDao(),
            github = GitHubStickerSource(github),
            cdnStore = StickerCdnStore(FakePreferences()),
            imageHost = NoImageHost,
            preparer = NoPreparer,
            http = github,
            clock = { 1_000_000L },
            scope = backgroundScope,
        )

    private companion object {
        const val OLD = "1111111111111111111111111111111111111111"
        const val NEW = "2222222222222222222222222222222222222222"
    }
}

/** GitHub's three endpoints, answered from what a test set. */
private class FakeGitHub : HttpTransport {
    var head: String = "1111111111111111111111111111111111111111"
    val trees = mutableMapOf<String, List<String>>()
    val calls = mutableListOf<String>()

    override suspend fun execute(
        request: HttpRequest,
        onUploadProgress: UploadProgress?,
    ): HttpResponse {
        calls += request.url
        val path = request.url.substringAfter("api.github.com").substringBefore('?')
        val body = when {
            path.contains("/git/trees/") -> {
                val sha = path.substringAfterLast('/')
                val entries = trees[sha].orEmpty().joinToString(",") { """{"path":"$it","type":"blob","size":1}""" }
                """{"sha":"$sha","tree":[$entries],"truncated":false}"""
            }

            path.contains("/commits/") -> head

            else -> """{"default_branch":"main"}"""
        }
        return HttpResponse(200, request.url, emptyMap(), body)
    }
}

private class FakePreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

private object NoImageHost : ImageHostRepository {
    override val current: Flow<ImageHostConfig> = flowOf(ImageHostConfig(ImageHostProvider.NODE_IMAGE))
    override val selected: Flow<ImageHostProvider> = flowOf(ImageHostProvider.NODE_IMAGE)

    override fun config(provider: ImageHostProvider): Flow<ImageHostConfig> = flowOf(ImageHostConfig(provider))

    override suspend fun select(provider: ImageHostProvider) = Unit

    override suspend fun save(config: ImageHostConfig) = Unit

    override suspend fun disconnect(provider: ImageHostProvider) = Unit

    override suspend fun upload(
        upload: ImageHostUpload,
        onProgress: (Float) -> Unit,
    ): HostedImage = error("not used")

    override suspend fun images(): List<HostedImage> = emptyList()

    override suspend fun delete(image: HostedImage) = Unit
}

private object NoPreparer : ImagePreparer {
    override suspend fun prepare(
        source: String,
        displayName: String,
    ): ImageHostUpload = error("not used")

    override suspend fun original(
        source: String,
        displayName: String,
    ): ImageHostUpload = error("not used")
}

/** The DAO's queries, kept in lists and ordered the way the SQL orders them. */
private class FakeStickerDao : StickerDao {
    private val mine = MutableStateFlow<List<MyStickerEntity>>(emptyList())
    private val repos = MutableStateFlow<List<StickerRepoEntity>>(emptyList())
    private val folders = MutableStateFlow<List<StickerFolderEntity>>(emptyList())

    override fun observeMine() = mine.map { list -> list.sortedBy { it.position } }

    override suspend fun listMine() = mine.value.sortedBy { it.position }

    override suspend fun minPosition() = mine.value.minOfOrNull { it.position }

    override suspend fun insertMine(stickers: List<MyStickerEntity>): List<Long> =
        stickers.map { sticker ->
            if (mine.value.any { it.url == sticker.url }) {
                -1L
            } else {
                mine.value += sticker
                mine.value.size.toLong()
            }
        }

    override suspend fun rename(
        url: String,
        name: String,
    ) {
        mine.value = mine.value.map { if (it.url == url) it.copy(name = name) else it }
    }

    override suspend fun setPosition(
        url: String,
        position: Long,
    ) {
        mine.value = mine.value.map { if (it.url == url) it.copy(position = position) else it }
    }

    override suspend fun deleteMine(urls: List<String>) {
        mine.value = mine.value.filterNot { it.url in urls }
    }

    override fun observeRepos() = repos.map { list -> list.sortedBy { it.position } }

    override suspend fun listRepos() = repos.value.sortedBy { it.position }

    override suspend fun repo(slug: String) = repos.value.firstOrNull { it.slug == slug }

    override suspend fun maxRepoPosition() = repos.value.maxOfOrNull { it.position }

    override suspend fun upsertRepo(repo: StickerRepoEntity) {
        repos.value = repos.value.filterNot { it.slug == repo.slug } + repo
    }

    override fun observeFolders() = folders.map { list -> list.sortedWith(compareBy({ it.repoSlug }, { it.position })) }

    override suspend fun folders(slug: String) = folders.value.filter { it.repoSlug == slug }.sortedBy { it.position }

    override suspend fun upsertFolders(folders: List<StickerFolderEntity>) {
        val keys = folders.map { it.repoSlug to it.path }.toSet()
        this.folders.value = this.folders.value.filterNot { (it.repoSlug to it.path) in keys } + folders
    }

    override suspend fun deleteFoldersExcept(
        slug: String,
        keep: List<String>,
    ) {
        folders.value = folders.value.filterNot { it.repoSlug == slug && it.path !in keep }
    }

    override suspend fun setHidden(
        slug: String,
        path: String,
        hidden: Boolean,
    ) {
        folders.value = folders.value.map { if (it.repoSlug == slug && it.path == path) it.copy(hidden = hidden) else it }
    }

    override suspend fun setFolderPosition(
        slug: String,
        path: String,
        position: Long,
    ) {
        folders.value = folders.value.map { if (it.repoSlug == slug && it.path == path) it.copy(position = position) else it }
    }

    override suspend fun deleteRepoRow(slug: String) {
        repos.value = repos.value.filterNot { it.slug == slug }
    }

    override suspend fun deleteRepoFolders(slug: String) {
        folders.value = folders.value.filterNot { it.repoSlug == slug }
    }
}
