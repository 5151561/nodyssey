package io.github.nodyssey.ui.sticker

import io.github.nodyssey.data.composer.ImagePreparer
import io.github.nodyssey.data.imagehost.HostedImage
import io.github.nodyssey.data.imagehost.ImageHostConfig
import io.github.nodyssey.data.imagehost.ImageHostProvider
import io.github.nodyssey.data.imagehost.ImageHostRepository
import io.github.nodyssey.data.imagehost.ImageHostUpload
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.sticker.GitHubStickerSource
import io.github.nodyssey.data.sticker.StickerLibrary
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.HttpResponse
import io.github.plaza.core.net.HttpTransport
import io.github.plaza.core.net.UploadProgress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.coroutines.ContinuationInterceptor

internal const val OLD_SHA = "1111111111111111111111111111111111111111"
internal const val NEW_SHA = "2222222222222222222222222222222222222222"

/**
 * GitHub's three endpoints answered from [head] and [trees], and every other request — a probe, a
 * CDN sample — answered 200. Records what was asked.
 */
internal class FakeStickerHttp : HttpTransport {
    var head: String = OLD_SHA
    val trees = mutableMapOf<String, List<String>>()
    val requests = mutableListOf<HttpRequest>()

    override suspend fun execute(
        request: HttpRequest,
        onUploadProgress: UploadProgress?,
    ): HttpResponse {
        requests += request
        if (!request.url.contains("api.github.com")) return HttpResponse(200, request.url, emptyMap(), "")
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

    /** The GitHub API paths asked for, without host or query. */
    fun githubPaths(): List<String> =
        requests.map { it.url }.filter { it.contains("api.github.com") }.map { it.substringAfter("api.github.com").substringBefore('?') }

    fun heads(): List<String> = requests.filter { it.method == "HEAD" }.map { it.url }
}

internal fun testStickerLibrary(
    database: NodeSeekDatabase,
    settings: SettingsRepository,
    http: FakeStickerHttp,
    scope: CoroutineScope,
): StickerLibrary {
    // The test's own dispatcher for "io" too, so a request is still driven by the test scheduler.
    val testDispatcher = scope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
    val dispatchers = AppDispatchers(io = testDispatcher, default = testDispatcher)
    return StickerLibrary(
        dao = database.stickerDao(),
        github = GitHubStickerSource(http, dispatchers),
        cdnStore = settings.stickerCdn,
        imageHost = NoImageHost,
        preparer = NoPreparer,
        http = http,
        clock = { 1_000_000L },
        scope = scope,
        dispatchers = dispatchers,
    )
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
