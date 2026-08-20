package io.github.nodyssey.ui.account

import io.github.nodyssey.data.imagehost.ConfigProblem
import io.github.nodyssey.data.imagehost.HostedImage
import io.github.nodyssey.data.imagehost.ImageHostConfig
import io.github.nodyssey.data.imagehost.ImageHostError
import io.github.nodyssey.data.imagehost.ImageHostException
import io.github.nodyssey.data.imagehost.ImageHostProvider
import io.github.nodyssey.data.imagehost.ImageHostRepository
import io.github.nodyssey.data.imagehost.ImageHostUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
private const val OTHER_KEY = "1|lskytokenthatisnothexadecimal"

@OptIn(ExperimentalCoroutinesApi::class)
class ImageHostViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeImageHostRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `with nothing connected the screen says so instead of showing an empty gallery`() = runTest(dispatcher) {
        val viewModel = ImageHostViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.connected)
        assertEquals(ImageHostError.NotConfigured, state.imagesError)
        assertEquals(0, repository.imagesCalls)
    }

    /**
     * The credential is the one string on this screen that would let somebody else upload under the
     * account, so it never goes back into the field or the state — only a fingerprint of it does.
     */
    @Test
    fun `a saved credential is never echoed back, only masked`() = runTest(dispatcher) {
        val viewModel = ImageHostViewModel(repository)
        advanceUntilIdle()

        viewModel.updateToken(KEY)
        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.connected)
        assertEquals("", state.tokenInput)
        assertEquals("0123……cdef", state.credentialMask)
        assertFalse("the full key must not be reachable from state", state.toString().contains(KEY))
    }

    @Test
    fun `something that is not a NodeImage key is refused before any request`() = runTest(dispatcher) {
        val viewModel = ImageHostViewModel(repository)
        advanceUntilIdle()

        viewModel.updateToken("粘贴错了")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(ConfigProblem.IMPLAUSIBLE_TOKEN, viewModel.uiState.value.problem)
        assertTrue(repository.saved.isEmpty())
    }

    /**
     * Switching hosts must not carry one host's credential to another, and must not make the user
     * re-paste the one they had. Each host's fields are its own.
     */
    @Test
    fun `each host keeps its own credential across a switch`() = runTest(dispatcher) {
        repository.store(
            ImageHostConfig(ImageHostProvider.LSKY_PRO, siteUrl = "https://img.example.com", token = OTHER_KEY),
        )
        val viewModel = ImageHostViewModel(repository)
        advanceUntilIdle()

        viewModel.updateToken(KEY)
        viewModel.save()
        advanceUntilIdle()
        assertEquals("0123……cdef", viewModel.uiState.value.credentialMask)

        viewModel.selectProvider(ImageHostProvider.LSKY_PRO)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ImageHostProvider.LSKY_PRO, state.provider)
        assertEquals("https://img.example.com", state.siteUrlInput)
        assertEquals("", state.tokenInput)
        assertEquals("1|ls……imal", state.credentialMask)

        viewModel.selectProvider(ImageHostProvider.NODE_IMAGE)
        advanceUntilIdle()
        assertEquals("0123……cdef", viewModel.uiState.value.credentialMask)
    }

    /** Editing the address of a connected host must not mean re-pasting a token to keep it. */
    @Test
    fun `an empty credential field on save keeps the stored one`() = runTest(dispatcher) {
        repository.store(
            ImageHostConfig(ImageHostProvider.LSKY_PRO, siteUrl = "https://old.example.com", token = OTHER_KEY),
        )
        repository.select(ImageHostProvider.LSKY_PRO)
        val viewModel = ImageHostViewModel(repository)
        advanceUntilIdle()

        viewModel.updateSiteUrl("https://new.example.com")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(OTHER_KEY, repository.saved.last().token)
        assertEquals("https://new.example.com", repository.saved.last().siteUrl)
    }

    /**
     * Two of the six publish an upload endpoint and nothing else. An empty gallery would read as
     * "the host lost your images", and the recovery for that is nothing like the real one.
     */
    @Test
    fun `a host with no listing endpoint says so rather than showing nothing`() = runTest(dispatcher) {
        repository.store(ImageHostConfig(ImageHostProvider.IMGBB, token = "imgbbkey"))
        repository.select(ImageHostProvider.IMGBB)
        val viewModel = ImageHostViewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.connected)
        assertEquals(ImageHostError.Unsupported, viewModel.uiState.value.imagesError)
        assertEquals("a host with no list must not be asked for one", 0, repository.imagesCalls)
    }

    @Test
    fun `connecting loads what is already on the host`() = runTest(dispatcher) {
        repository.images = listOf(item("abc"), item("def"))
        val viewModel = ImageHostViewModel(repository)
        advanceUntilIdle()

        viewModel.updateToken(KEY)
        viewModel.save()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.images.size)
    }

    /** Deleting is irreversible, so it goes through a confirmation and nothing happens without one. */
    @Test
    fun `delete is confirmed before it happens and removes the row after`() = runTest(dispatcher) {
        repository.store(ImageHostConfig(ImageHostProvider.NODE_IMAGE, token = KEY))
        repository.images = listOf(item("abc"), item("def"))
        val viewModel = ImageHostViewModel(repository)
        advanceUntilIdle()

        viewModel.requestDelete(item("abc"))
        advanceUntilIdle()
        assertEquals("abc", viewModel.uiState.value.deleting?.id)
        assertTrue("nothing may be deleted on request alone", repository.deleted.isEmpty())

        viewModel.confirmDelete()
        advanceUntilIdle()

        assertEquals(listOf("abc"), repository.deleted)
        assertEquals(listOf("def"), viewModel.uiState.value.images.map { it.id })
    }

    @Test
    fun `a rejected key is reported as a key problem, not an empty gallery`() = runTest(dispatcher) {
        repository.store(ImageHostConfig(ImageHostProvider.NODE_IMAGE, token = KEY))
        repository.failure = ImageHostException(ImageHostError.InvalidKey)
        val viewModel = ImageHostViewModel(repository)
        advanceUntilIdle()

        assertEquals(ImageHostError.InvalidKey, viewModel.uiState.value.imagesError)
    }

    @Test
    fun `disconnecting forgets the credential and stops offering the gallery`() = runTest(dispatcher) {
        repository.store(ImageHostConfig(ImageHostProvider.NODE_IMAGE, token = KEY))
        val viewModel = ImageHostViewModel(repository)
        advanceUntilIdle()

        viewModel.requestDisconnect()
        viewModel.confirmDisconnect()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.connected)
        assertNull(state.credentialMask)
        assertEquals(ImageHostError.NotConfigured, state.imagesError)
    }

    private fun item(id: String) = HostedImage(
        id = id,
        fileName = "$id.webp",
        url = "https://cdn.nodeimage.com/i/$id.webp",
        uploadTime = "2026-07-28T04:04:11Z",
        sizeBytes = 1214,
        mimeType = "image/webp",
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
private class FakeImageHostRepository : ImageHostRepository {
    private val configs = MutableStateFlow(emptyMap<ImageHostProvider, ImageHostConfig>())
    private val chosen = MutableStateFlow(ImageHostProvider.NODE_IMAGE)

    var images: List<HostedImage> = emptyList()
    var failure: ImageHostException? = null
    val deleted = mutableListOf<String>()
    val saved = mutableListOf<ImageHostConfig>()
    var imagesCalls = 0

    /** Seeds a host as if it had been configured on a previous visit. */
    fun store(config: ImageHostConfig) {
        configs.value = configs.value + (config.provider to config)
    }

    override val selected: Flow<ImageHostProvider> = chosen

    override fun config(provider: ImageHostProvider): Flow<ImageHostConfig> =
        configs.map { it[provider] ?: ImageHostConfig(provider) }

    override val current: Flow<ImageHostConfig> = chosen.flatMapLatest { config(it) }

    override suspend fun select(provider: ImageHostProvider) {
        chosen.value = provider
    }

    override suspend fun save(config: ImageHostConfig) {
        saved += config
        store(config)
    }

    override suspend fun disconnect(provider: ImageHostProvider) {
        store(ImageHostConfig(provider))
    }

    override suspend fun upload(
        upload: ImageHostUpload,
        onProgress: (Float) -> Unit,
    ): HostedImage = throw UnsupportedOperationException("not used by this screen")

    override suspend fun images(): List<HostedImage> {
        imagesCalls++
        if (!current.first().provider.browsable) throw ImageHostException(ImageHostError.Unsupported)
        failure?.let { throw it }
        return images
    }

    override suspend fun delete(image: HostedImage) {
        failure?.let { throw it }
        deleted += image.id
    }
}
