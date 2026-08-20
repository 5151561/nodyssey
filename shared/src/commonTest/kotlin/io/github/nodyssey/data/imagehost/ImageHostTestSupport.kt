package io.github.nodyssey.data.imagehost

import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HttpTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/**
 * The settings half, in memory.
 *
 * Substituted here rather than standing up a DataStore because the protocol tests are about what
 * goes on the wire, and a preferences file keyed by name is a process singleton that leaks state
 * between them. `ImageHostSettingsTest` covers the stored half on its own.
 */
internal class FakeImageHostSettings(
    initial: ImageHostConfig = ImageHostConfig(ImageHostProvider.NODE_IMAGE),
) : ImageHostSettings {
    private val state = MutableStateFlow(mapOf(initial.provider to initial))
    private val chosen = MutableStateFlow(initial.provider)

    override val selected: Flow<ImageHostProvider> = chosen

    override fun config(provider: ImageHostProvider): Flow<ImageHostConfig> =
        state.map { it[provider] ?: ImageHostConfig(provider) }

    override suspend fun select(provider: ImageHostProvider) {
        chosen.value = provider
    }

    override suspend fun save(config: ImageHostConfig) {
        state.value = state.value + (config.provider to config)
    }

    override suspend fun disconnect(provider: ImageHostProvider) {
        state.value = state.value + (provider to ImageHostConfig(provider))
    }
}

internal fun TestScope.repositoryFor(
    config: ImageHostConfig,
    transport: HttpTransport,
): ImageHostRepository = StandardTestDispatcher(testScheduler).let { dispatcher ->
    DefaultImageHostRepository(
        settings = FakeImageHostSettings(config),
        http = transport,
        dispatchers = AppDispatchers(dispatcher, dispatcher),
    )
}

internal fun bytes(size: Int = 8) = ImageHostUpload(ByteArray(size) { 1 }, "photo.webp", "image/webp")

internal suspend fun assertImageHostFails(block: suspend () -> Unit): ImageHostException =
    try {
        block()
        throw AssertionError("call should have failed")
    } catch (exception: ImageHostException) {
        exception
    }
