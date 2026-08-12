package io.github.nodyssey.data.imagehost

import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

/**
 * The settings half, in memory.
 *
 * Substituted here rather than standing up a DataStore because the protocol tests are about what
 * goes on the wire, and a preferences file keyed by name is a process singleton that leaks state
 * between them. [DataStoreImageHostSettingsTest] covers the stored half on its own.
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

/** Answers every request with one canned body, and keeps what was sent for the assertions. */
internal class RecordingInterceptor(
    private val body: String,
    private val code: Int = 200,
    private val headers: Map<String, String> = emptyMap(),
) : Interceptor {
    var request: Request? = null
    var bodyUtf8: String = ""

    override fun intercept(chain: Interceptor.Chain): Response {
        val outgoing = chain.request()
        request = outgoing
        bodyUtf8 = Buffer().also { outgoing.body?.writeTo(it) }.readUtf8()
        return Response.Builder()
            .request(outgoing)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
}

internal fun TestScope.repositoryFor(
    config: ImageHostConfig,
    interceptor: Interceptor,
): ImageHostRepository = StandardTestDispatcher(testScheduler).let { dispatcher ->
    DefaultImageHostRepository(
        settings = FakeImageHostSettings(config),
        http = OkHttpClient.Builder().addInterceptor(interceptor).build(),
        dispatchers = AppDispatchers(dispatcher, dispatcher),
    )
}

internal fun bytes(size: Int = 8) = ImageHostUpload(ByteArray(size) { 1 }, "photo.webp", "image/webp")

internal suspend fun assertFails(block: suspend () -> Unit): ImageHostException =
    try {
        block()
        throw AssertionError("call should have failed")
    } catch (exception: ImageHostException) {
        exception
    }
