package io.github.nodyssey.data.sticker

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.nodyssey.data.composer.ImagePreparer
import io.github.nodyssey.data.composer.PickedImage
import io.github.nodyssey.data.imagehost.HostedImage
import io.github.nodyssey.data.imagehost.ImageHostConfig
import io.github.nodyssey.data.imagehost.ImageHostError
import io.github.nodyssey.data.imagehost.ImageHostProvider
import io.github.nodyssey.data.imagehost.ImageHostRepository
import io.github.nodyssey.data.imagehost.ImageHostUpload
import io.github.nodyssey.data.local.MyStickerEntity
import io.github.nodyssey.data.local.StickerDao
import io.github.nodyssey.data.local.StickerFolderEntity
import io.github.nodyssey.data.local.StickerRepoEntity
import io.github.nodyssey.data.settings.StickerSettingsStore
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.UploadProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.ContinuationInterceptor
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

        val listing = GitHubStickerSource(github, dispatchers()).list(GitHubRepoRef("o", "r"))

        assertEquals("main", listing.ref)
        assertEquals(NEW, listing.sha)
        assertEquals(listOf("pack"), listing.folders.map { it.path })
        assertEquals(
            listOf("/repos/o/r", "/repos/o/r/commits/main", "/repos/o/r/git/trees/$NEW"),
            github.calls.map { it.substringAfter("api.github.com").substringBefore('?') },
        )
    }

    @Test
    fun `a link typed in another case is keyed by the name GitHub gives`() = runTest {
        val github = FakeGitHub()
        github.fullName = "ZhaoOlee/ChineseBQB"
        github.trees[github.head] = listOf("pack/1.gif")

        val listing = GitHubStickerSource(github, dispatchers()).list(GitHubRepoRef("zhaoolee", "chinesebqb", ref = "master"))

        assertEquals("ZhaoOlee/ChineseBQB", listing.slug)
        assertEquals("master", listing.ref)
    }

    @Test
    fun `GitHub and the link probes are asked on the io dispatcher`() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val github = FakeGitHub()
        github.trees[github.head] = listOf("pack/1.gif")
        val library = library(github, AppDispatchers(io = io, default = io))

        library.inspect(GitHubRepoRef("o", "r"))
        library.probe("https://h/a.png")

        assertEquals(listOf<ContinuationInterceptor?>(io), github.dispatchers.distinct())
    }

    @Test
    fun `a check does not write back what changed while it was on the network`() = runTest {
        val github = FakeGitHub()
        val library = library(github)
        library.subscribe(listing("o", "r", OLD, "a", "b"), paths = setOf("a", "b"))
        library.subscribe(listing("o", "s", OLD, "a"), paths = setOf("a"))
        github.head = NEW
        github.trees[NEW] = listOf("a/1.png", "a/2.png", "b/1.png")
        val gate = CompletableDeferred<Unit>()
        github.treesGate = gate

        val check = async { library.checkForUpdates(force = true) }
        runCurrent()
        library.setFolderHidden("o/r", "b", hidden = true)
        library.reorderFolders("o/r", listOf("b", "a"))
        library.unsubscribe("o/s")
        gate.complete(Unit)
        check.await()

        val after = library.subscriptions.first()
        assertEquals(listOf("o/r"), after.map { it.slug })
        assertEquals(listOf("b" to true, "a" to false), after.single().folders.map { it.path to it.hidden })
        assertTrue(after.single().hasUpdate)
    }

    @Test
    fun `an update where every folder vanished can be dismissed and stays dismissed`() = runTest {
        val github = FakeGitHub()
        val library = library(github)
        library.subscribe(listing("o", "r", OLD, "a"), paths = setOf("a"))
        github.head = NEW
        github.trees[NEW] = listOf("elsewhere/1.png")
        library.checkForUpdates(force = true)
        assertTrue(library.subscriptions.first().single().hasUpdate)

        library.applyUpdate("o/r")
        library.checkForUpdates(force = true)

        val after = library.subscriptions.first().single()
        assertEquals(false, after.hasUpdate)
        assertEquals(OLD, after.pinnedSha)
        assertEquals(listOf("a/1.png"), after.folders.single().files)
    }

    @Test
    fun `a truncated tree does not count the folders it left out as vanished`() = runTest {
        val github = FakeGitHub()
        val library = library(github)
        library.subscribe(listing("o", "r", OLD, "a", "b"), paths = setOf("a", "b"))
        github.head = NEW
        github.trees[NEW] = listOf("a/1.png", "a/2.png")
        github.truncated = true

        library.checkForUpdates(force = true)
        library.applyUpdate("o/r")

        val after = library.subscriptions.first().single()
        assertEquals(NEW, after.pinnedSha)
        assertEquals(listOf("a", "b"), after.folders.map { it.path })
    }

    @Test
    fun `re-subscribing through a folder link keeps the folders outside it`() = runTest {
        val github = FakeGitHub()
        val library = library(github)
        library.subscribe(listing("o", "r", OLD, "a", "b/x"), paths = setOf("a", "b/x"))
        github.head = NEW
        github.trees[NEW] = listOf("a/1.png", "a/2.png", "b/x/1.png", "b/y/1.png")

        val narrowed = library.inspect(GitHubRepoRef("o", "r", ref = "main", subPath = "b"))
        assertEquals(listOf("b/x", "b/y"), narrowed.folders.map { it.path })
        library.subscribe(narrowed, paths = setOf("b/y"))

        val after = library.subscriptions.first().single()
        assertEquals(NEW, after.pinnedSha)
        assertEquals(
            listOf("a" to listOf("a/1.png", "a/2.png"), "b/y" to listOf("b/y/1.png")),
            after.folders.map { it.path to it.files },
        )
    }

    @Test
    fun `a short link to a subscribed repository is read on the branch it follows`() = runTest {
        val github = FakeGitHub()
        val library = library(github)
        library.subscribe(listing("o", "r", OLD, "a").copy(ref = "dev"), paths = setOf("a"))
        github.trees[github.head] = listOf("a/1.png")

        val listing = library.inspect(GitHubRepoRef("O", "R"))

        assertEquals("dev", listing.ref)
        assertTrue(github.calls.any { it.endsWith("/commits/dev") })
    }

    @Test
    fun `an upload the host links over http fails instead of vanishing and the next one still goes`() = runTest {
        val library = library()

        library.upload(listOf(PickedImage("content://1", "plain.png"), PickedImage("content://2", "fine.png")), keepOriginal = true)
        runCurrent()

        val (first, second) = library.uploads.value
        assertEquals(StickerUpload.State.FAILED, first.state)
        assertEquals(ImageHostError.InsecureLink, first.error?.error)
        assertEquals(StickerUpload.State.DONE, second.state)
        assertEquals(listOf("https://h/fine.png"), library.mine.first().map { it.url })
    }

    @Test
    fun `a backup brings back 我的 in its order after what is already here and resubscribes at the pinned commit`() = runTest {
        val source = library()
        source.add(listOf("a", "b", "c").map { MySticker("https://h/$it.png", it) })
        source.subscribe(listing("o", "r", OLD, "x", "y"), setOf("x", "y"))
        source.setGroupHidden(folderGroupKey("o/r", "y"), hidden = true)
        val backup = source.exportBackup()

        // The branch has moved on since; the restore must not follow it.
        val github = FakeGitHub().apply {
            head = NEW
            trees[OLD] = listOf("x/1.png", "y/1.png")
            trees[NEW] = listOf("x/1.png", "x/2.png")
        }
        val target = library(github)
        target.add(listOf(MySticker("https://h/d.png", "d"), MySticker("https://h/b.png", "b")))

        val result = target.importBackup(backup)

        assertEquals(StickerImportResult(stickers = 2, folders = 2, failedRepos = emptyList()), result)
        assertEquals(listOf("d", "b", "a", "c"), target.mine.first().map { it.name })
        val subscription = target.subscriptions.first().single()
        assertEquals(OLD, subscription.pinnedSha)
        assertEquals(listOf("x" to false, "y" to true), subscription.folders.map { it.path to it.hidden })
    }

    @Test
    fun `text that is not a backup imports nothing`() = runTest {
        val library = library()

        assertNull(library.importBackup("{}"))
        assertNull(library.importBackup("https://h/a.png"))
        assertEquals(emptyList(), library.mine.first())
    }

    @Test
    fun `hiding a folder group marks that folder and hiding a site group leaves the folders alone`() = runTest {
        val library = library()
        library.subscribe(listing("o", "r", OLD, "x", "nested/y"), setOf("x", "nested/y"))

        library.setGroupHidden(folderGroupKey("o/r", "nested/y"), hidden = true)
        library.setGroupHidden("site:ac", hidden = true)

        assertEquals(listOf(false, true), library.subscriptions.first().single().folders.map { it.hidden })
        assertEquals(setOf("site:ac"), library.groupLayout.first().hidden)
    }

    private fun TestScope.dispatchers(): AppDispatchers {
        val io = StandardTestDispatcher(testScheduler)
        return AppDispatchers(io = io, default = io)
    }

    private fun listing(
        owner: String,
        repo: String,
        sha: String,
        vararg folders: String,
    ) = RepoListing(owner, repo, "main", sha, folders.map { RepoFolder(it, listOf("$it/1.png"), 1) }, truncated = false)

    private fun TestScope.library(
        github: HttpTransport = FakeGitHub(),
        dispatchers: AppDispatchers = dispatchers(),
    ) = StickerLibrary(
        dao = FakeStickerDao(),
        github = GitHubStickerSource(github, dispatchers),
        settingsStore = StickerSettingsStore(FakePreferences()),
        imageHost = FakeImageHost,
        preparer = FakePreparer,
        http = github,
        dispatchers = dispatchers,
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
    var truncated = false
    var fullName: String? = null

    /** When set, a tree request waits for it — the moment a check is out on the network. */
    var treesGate: CompletableDeferred<Unit>? = null
    val calls = mutableListOf<String>()
    val dispatchers = mutableListOf<ContinuationInterceptor?>()

    override suspend fun execute(
        request: HttpRequest,
        onUploadProgress: UploadProgress?,
    ): HttpResponse {
        calls += request.url
        dispatchers += currentCoroutineContext()[ContinuationInterceptor]
        val path = request.url.substringAfter("api.github.com").substringBefore('?')
        val body = when {
            path.contains("/git/trees/") -> {
                treesGate?.await()
                val sha = path.substringAfterLast('/')
                val entries = trees[sha].orEmpty().joinToString(",") { """{"path":"$it","type":"blob","size":1}""" }
                """{"sha":"$sha","tree":[$entries],"truncated":$truncated}"""
            }

            path.contains("/commits/") -> head

            else -> fullName?.let { """{"default_branch":"main","full_name":"$it"}""" } ?: """{"default_branch":"main"}"""
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

/** Hands back an https link for every upload but one named `plain…`, which it links over http. */
private object FakeImageHost : ImageHostRepository {
    override val current: Flow<ImageHostConfig> = flowOf(ImageHostConfig(ImageHostProvider.NODE_IMAGE))
    override val selected: Flow<ImageHostProvider> = flowOf(ImageHostProvider.NODE_IMAGE)

    override fun config(provider: ImageHostProvider): Flow<ImageHostConfig> = flowOf(ImageHostConfig(provider))

    override suspend fun select(provider: ImageHostProvider) = Unit

    override suspend fun save(config: ImageHostConfig) = Unit

    override suspend fun disconnect(provider: ImageHostProvider) = Unit

    override suspend fun upload(
        upload: ImageHostUpload,
        onProgress: (Float) -> Unit,
    ): HostedImage {
        val scheme = if (upload.fileName.startsWith("plain")) "http" else "https"
        return HostedImage(upload.fileName, upload.fileName, "$scheme://h/${upload.fileName}")
    }

    override suspend fun images(): List<HostedImage> = emptyList()

    override suspend fun delete(image: HostedImage) = Unit
}

private object FakePreparer : ImagePreparer {
    override suspend fun prepare(
        source: String,
        displayName: String,
    ): ImageHostUpload = ImageHostUpload(ByteArray(1), displayName, "image/png")

    override suspend fun original(
        source: String,
        displayName: String,
    ): ImageHostUpload = prepare(source, displayName)
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

    override suspend fun setLatest(
        slug: String,
        pinnedSha: String,
        latestSha: String?,
        checkedAtMillis: Long,
    ): Int {
        if (repos.value.none { it.slug == slug && it.pinnedSha == pinnedSha }) return 0
        repos.value = repos.value.map { if (it.slug == slug) it.copy(latestSha = latestSha, checkedAtMillis = checkedAtMillis) else it }
        return 1
    }

    override suspend fun setPending(
        slug: String,
        path: String,
        pendingFiles: String?,
    ) {
        folders.value = folders.value.map { if (it.repoSlug == slug && it.path == path) it.copy(pendingFiles = pendingFiles) else it }
    }

    override suspend fun clearPending(slug: String) {
        folders.value = folders.value.map { if (it.repoSlug == slug) it.copy(pendingFiles = null) else it }
    }

    override suspend fun deleteRepoRow(slug: String) {
        repos.value = repos.value.filterNot { it.slug == slug }
    }

    override suspend fun deleteRepoFolders(slug: String) {
        folders.value = folders.value.filterNot { it.repoSlug == slug }
    }
}
