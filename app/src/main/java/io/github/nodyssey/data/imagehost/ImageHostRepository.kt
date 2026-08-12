package io.github.nodyssey.data.imagehost

import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * The app's one door to whichever image host is selected.
 *
 * Everything above this line — the editor, the attachment tray, 图床设置 — talks to this and never to
 * a host. That is what keeps six protocols from leaking into the composer: [upload] takes bytes and
 * answers a URL, and which of nodeimage.com, 兰空, 简单图床, sm.ms, imgbb or somebody's own uploader
 * produced it is not a question anything upstream is in a position to ask.
 *
 * It extends [ImageHostSettings] rather than wrapping it because the settings screen needs both
 * halves — the stored configuration and the calls that prove it works — and one dependency reads
 * better there than two that have to be kept in step.
 */
interface ImageHostRepository : ImageHostSettings {
    /** The selected host's configuration, re-emitting when either the selection or its fields change. */
    val current: Flow<ImageHostConfig>

    /** @param onProgress 0f–1f as bytes go out, for the tray's progress ring. */
    suspend fun upload(
        upload: ImageHostUpload,
        onProgress: (Float) -> Unit = {},
    ): HostedImage

    /** @throws ImageHostException with [ImageHostError.Unsupported] on a host that publishes no list. */
    suspend fun images(): List<HostedImage>

    suspend fun delete(image: HostedImage)
}

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
