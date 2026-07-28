package io.github.nsreader.ui.account

import io.github.nsreader.data.nodeimage.NodeImageError
import io.github.nsreader.data.nodeimage.NodeImageException
import io.github.nsreader.data.nodeimage.NodeImageItem
import io.github.nsreader.data.nodeimage.NodeImageRepository
import io.github.nsreader.data.nodeimage.NodeImageUpload
import io.github.nsreader.data.nodeimage.NodeImageUploaded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class NodeImageViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeNodeImageRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `with no key stored the screen says so instead of showing an empty gallery`() = runTest(dispatcher) {
        val viewModel = NodeImageViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasKey)
        assertEquals(NodeImageError.NotConfigured, state.imagesError)
        assertEquals(0, repository.imagesCalls)
    }

    /**
     * The key is the one string on this screen that would let somebody else upload under the
     * account, so it never goes back into the field or the state — only a fingerprint of it does.
     */
    @Test
    fun `a saved key is never echoed back, only masked`() = runTest(dispatcher) {
        val viewModel = NodeImageViewModel(repository)
        advanceUntilIdle()

        viewModel.updateKeyInput(KEY)
        viewModel.saveKey()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasKey)
        assertEquals("", state.keyInput)
        assertEquals("0123……cdef", state.savedKeyMask)
        assertFalse("the full key must not be reachable from state", state.toString().contains(KEY))
    }

    @Test
    fun `something that is not a key is refused before any request`() = runTest(dispatcher) {
        val viewModel = NodeImageViewModel(repository)
        advanceUntilIdle()

        viewModel.updateKeyInput("粘贴错了")
        viewModel.saveKey()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.keyInputError)
        assertNull(repository.storedKey.value)
    }

    @Test
    fun `saving a key loads what is already on the host`() = runTest(dispatcher) {
        repository.images = listOf(item("abc"), item("def"))
        val viewModel = NodeImageViewModel(repository)
        advanceUntilIdle()

        viewModel.updateKeyInput(KEY)
        viewModel.saveKey()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.images.size)
    }

    /** Deleting is irreversible, so it goes through a confirmation and nothing happens without one. */
    @Test
    fun `delete is confirmed before it happens and removes the row after`() = runTest(dispatcher) {
        repository.storedKey.value = KEY
        repository.images = listOf(item("abc"), item("def"))
        val viewModel = NodeImageViewModel(repository)
        advanceUntilIdle()

        viewModel.requestDelete(item("abc"))
        advanceUntilIdle()
        assertEquals("abc", viewModel.uiState.value.deleting?.imageId)
        assertTrue("nothing may be deleted on request alone", repository.deleted.isEmpty())

        viewModel.confirmDelete()
        advanceUntilIdle()

        assertEquals(listOf("abc"), repository.deleted)
        assertEquals(listOf("def"), viewModel.uiState.value.images.map { it.imageId })
    }

    @Test
    fun `a rejected key is reported as a key problem, not an empty gallery`() = runTest(dispatcher) {
        repository.storedKey.value = KEY
        repository.failure = NodeImageException(NodeImageError.InvalidKey)
        val viewModel = NodeImageViewModel(repository)
        advanceUntilIdle()

        assertEquals(NodeImageError.InvalidKey, viewModel.uiState.value.imagesError)
    }

    private fun item(id: String) = NodeImageItem(
        imageId = id,
        fileName = "$id.webp",
        url = "https://cdn.nodeimage.com/i/$id.webp",
        uploadTime = "2026-07-28T04:04:11Z",
        sizeBytes = 1214,
        mimeType = "image/webp",
    )
}

private class FakeNodeImageRepository : NodeImageRepository {
    val storedKey = MutableStateFlow<String?>(null)
    var images: List<NodeImageItem> = emptyList()
    var failure: NodeImageException? = null
    val deleted = mutableListOf<String>()
    var imagesCalls = 0

    override val apiKey: Flow<String?> = storedKey

    override suspend fun setApiKey(key: String) {
        storedKey.value = key
    }

    override suspend fun clearApiKey() {
        storedKey.value = null
    }

    override suspend fun upload(
        upload: NodeImageUpload,
        onProgress: (Float) -> Unit,
    ): NodeImageUploaded = throw UnsupportedOperationException("not used by this screen")

    override suspend fun images(): List<NodeImageItem> {
        imagesCalls++
        failure?.let { throw it }
        return images
    }

    override suspend fun delete(imageId: String) {
        failure?.let { throw it }
        deleted += imageId
    }
}
