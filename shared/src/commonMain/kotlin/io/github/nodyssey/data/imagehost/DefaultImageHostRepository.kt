package io.github.nodyssey.data.imagehost

import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HttpTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext

/*
 * Step A7 left this half of `imagehost/` in `:app`, and step D3c brought it down. What kept it up
 * there was the progress ring: `HttpTransport` read a whole answer and said nothing while a request
 * was in flight, and giving it an upload callback is a change to a contract every other caller
 * shares. The step that needed an Apple uploader is the one that paid for it — see
 * [io.github.plaza.core.net.UploadProgress] and [ImageHostClient].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultImageHostRepository(
    private val settings: ImageHostSettings,
    http: HttpTransport,
    private val dispatchers: AppDispatchers,
) : ImageHostRepository,
    ImageHostSettings by settings {

    /**
     * One client per protocol, built once.
     *
     * All six share the same [HttpTransport] — the one built over a client with no NodeSeek cookie
     * jar and no forced referrer. That is not a convenience: these are third-party services holding
     * a bearer-equivalent secret, and the forum's session must not ride along to any of them. Both
     * containers assemble that separately from the forum's; see `DefaultAppContainer` and
     * `IosAppContainer`.
     */
    private val clients: Map<ImageHostProvider, ImageHostClient> = mapOf(
        ImageHostProvider.NODE_IMAGE to NodeImageClient(http),
        ImageHostProvider.LSKY_PRO to LskyProClient(http),
        ImageHostProvider.EASY_IMAGE to EasyImageClient(http),
        ImageHostProvider.SMMS to SmmsClient(http),
        ImageHostProvider.IMGBB to ImgbbClient(http),
        ImageHostProvider.CUSTOM to CustomImageHostClient(http),
    )

    override val current: Flow<ImageHostConfig> =
        settings.selected.flatMapLatest { provider -> settings.config(provider) }

    override suspend fun upload(
        upload: ImageHostUpload,
        onProgress: (Float) -> Unit,
    ): HostedImage = onSelectedHost { client, config -> client.upload(config, upload, onProgress) }

    override suspend fun images(): List<HostedImage> =
        onSelectedHost { client, config ->
            if (!config.provider.browsable) throw ImageHostException(ImageHostError.Unsupported)
            client.images(config)
        }

    override suspend fun delete(image: HostedImage) {
        onSelectedHost { client, config -> client.delete(config, image) }
    }

    /**
     * Resolves the selected host, refuses early if it is not usable, and runs the call on IO.
     *
     * The configuration check happens here rather than in each client so that a half-filled host
     * fails as "go to 图床设置" everywhere, instead of as whatever 400 that particular service
     * answers a request with no credential.
     *
     * The IO hop stays now that the clients suspend, and it is doing less than it was. It used to be
     * the whole reason they could be written as blocking calls; what it covers today is the
     * *encoding* — a multipart body is built by copying an image's bytes about, and on Android
     * `HttpTransport` is OkHttp, whose `execute` blocks the caller's thread outright.
     */
    private suspend fun <T> onSelectedHost(block: suspend (ImageHostClient, ImageHostConfig) -> T): T {
        val config = current.first()
        if (!config.isConfigured) throw ImageHostException(ImageHostError.NotConfigured)
        val client = clients[config.provider]
            ?: throw ImageHostException(ImageHostError.NotConfigured)
        return withContext(dispatchers.io) { block(client, config) }
    }
}
