package io.github.nodyssey.data.imagehost

import kotlinx.coroutines.flow.Flow

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
