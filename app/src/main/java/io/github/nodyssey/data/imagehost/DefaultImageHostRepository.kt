package io.github.nodyssey.data.imagehost

import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/*
 * The six protocols themselves, and the OkHttp they are written against, are what kept this half
 * of `imagehost/` out of `commonMain` in step A7. `HttpTransport` reads a whole answer and reports
 * nothing while a request is in flight; an upload's progress ring needs the opposite. Giving the
 * transport an upload-progress callback is a change to a contract every other caller shares, and it
 * belongs to whichever step actually needs an Apple uploader rather than to this one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultImageHostRepository(
    private val settings: ImageHostSettings,
    http: OkHttpClient,
    private val dispatchers: AppDispatchers,
) : ImageHostRepository,
    ImageHostSettings by settings {

    /**
     * One client per protocol, built once.
     *
     * All six share the same [OkHttpClient] — the one with no NodeSeek cookie jar and no forced
     * referrer. That is not a convenience: these are third-party services holding a
     * bearer-equivalent secret, and the forum's session must not ride along to any of them.
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
     */
    private suspend fun <T> onSelectedHost(block: (ImageHostClient, ImageHostConfig) -> T): T {
        val config = current.first()
        if (!config.isConfigured) throw ImageHostException(ImageHostError.NotConfigured)
        val client = clients[config.provider]
            ?: throw ImageHostException(ImageHostError.NotConfigured)
        return withContext(dispatchers.io) { block(client, config) }
    }
}
