package io.github.nodyssey.ios

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.network.NetworkClient
import coil3.network.NetworkFetcher
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.NetworkResponseBody
import coil3.network.cachecontrol.CacheControlCacheStrategy
import io.github.nodyssey.core.NodeSeekSite
import io.github.plaza.core.net.getBytes
import io.github.plaza.core.toByteArray
import io.github.plaza.designsys.image.LongLivedImageCacheStrategy
import okio.Buffer
import okio.Path.Companion.toPath
import platform.Foundation.NSURLSession

/**
 * The app's image loader, built on the app's own session.
 *
 * `:app` hands Coil its `OkHttpClient` for one reason, and it is the same reason here: an avatar
 * behind Cloudflare is served to a request carrying the session's cookies, `User-Agent` and
 * `Accept-Language`, and refused to one that carries none of them. A second HTTP client would be a
 * second identity.
 *
 * `coil-core` on this platform ships **no** network fetcher at all, so an app that installs none
 * draws every remote image as a failure. What it does ship is the [NetworkClient] interface, which is
 * four methods' worth of surface over "send a request, read a body" — small enough that satisfying it
 * from `NSURLSession` is cheaper than adding an HTTP client to the repository to satisfy it for us.
 */
@OptIn(coil3.annotation.ExperimentalCoilApi::class)
internal fun nodysseyImageLoader(
    context: PlatformContext,
    session: () -> NSURLSession,
): ImageLoader =
    ImageLoader
        .Builder(context)
        .components {
            add(
                NetworkFetcher.Factory(
                    networkClient = { UrlSessionNetworkClient(session) },
                    // Not the default one, and `NodysseyApp.kt` in `:app` gives the argument at
                    // length: Coil's default strategy hands back the cached response whenever there
                    // is one and never asks the server about it. That is invisible for an attachment
                    // — its URL changes when its bytes do — and wrong for an avatar, which the site
                    // serves from `/avatar/<uid>.png` for the life of the account, so changing your
                    // picture would change nothing in the app forever. The wrapper is the other half
                    // of that argument — see [LongLivedImageCacheStrategy], which is where the cost
                    // of honouring the site's own four hours to the letter is written down.
                    cacheStrategy = {
                        LongLivedImageCacheStrategy(
                            delegate = CacheControlCacheStrategy(),
                            isLongLived = NodeSeekSite::isAvatarUrl,
                        )
                    },
                ),
            )
            // An account that never uploaded a picture is served a generated cartoon *SVG* from
            // `/avatar/<uid>.png` — the extension lies, the `Content-Type` does not. Without an SVG
            // decoder every such user wears an initial instead, which is exactly what the first run
            // of this shell drew.
            //
            // [IosSvgDecoder] rather than Coil's own, and for the same shape of reason `:app` passes
            // its `CompatSvgParser`: the renderer this platform gets by default cannot draw the whole
            // language. It draws no `<text>` and no `<image>`, so an IP card in a readme and every
            // shields.io badge came out as an empty frame. That decoder keeps Coil's for the documents
            // Skia does draw and sends the rest through WebKit.
            add(IosSvgDecoder.Factory())
        }
        .diskCache {
            DiskCache
                .Builder()
                // Caches, not Application Support: these are re-fetchable and the system is welcome
                // to reclaim them. The offline library's own pictures are the ones that must not be —
                // see `IosOfflineFileStore`.
                .directory(requireNotNull(cachesDirectory().path).toPath() / "image_cache")
                .build()
        }
        .build()

/**
 * [NetworkClient] over `NSURLSession`.
 *
 * The whole body is read into memory before it is handed on, which is what [getBytes] does and what
 * this platform's session API offers without writing a delegate: `dataTaskWithURL` completes with an
 * `NSData`, not with a stream. For images that is the same thing Coil would do anyway — it decodes
 * from a buffered source it has fully read — and it is why the streaming shape of this interface
 * costs nothing to satisfy this way.
 *
 * A failure to reach the host comes back as a 599 rather than as an exception. Coil reads the status
 * code, and 「图片加载失败」 is one message either way; inventing an exception type here would only
 * give the layer above something else to catch.
 */
private class UrlSessionNetworkClient(
    private val session: () -> NSURLSession,
) : NetworkClient {
    override suspend fun <T> executeRequest(
        request: NetworkRequest,
        block: suspend (response: NetworkResponse) -> T,
    ): T {
        val fetched = session().getBytes(request.url)
        val response =
            if (fetched == null) {
                NetworkResponse(code = UNREACHABLE, headers = NetworkHeaders.EMPTY, body = null)
            } else {
                NetworkResponse(
                    code = 200,
                    // Carried through rather than dropped: these are what Coil's disk cache decides
                    // freshness with, and a cache with no `Cache-Control` and no `ETag` never asks
                    // the server anything again.
                    headers =
                    NetworkHeaders
                        .Builder()
                        .apply { fetched.headers.forEach { (name, value) -> add(name, value) } }
                        .build(),
                    body = NetworkResponseBody(Buffer().apply { write(fetched.data.toByteArray()) }),
                )
            }
        return block(response)
    }

    private companion object {
        /** Outside the range any server can answer with, which is what makes it readable as "never got there". */
        const val UNREACHABLE = 599
    }
}
