package io.github.nodyssey.ui.account

import androidx.compose.ui.graphics.ImageBitmap
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.UserProfile
import io.github.nodyssey.data.account.AccountProfileFields
import io.github.nodyssey.data.account.AvatarUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric because [PendingAvatar] holds an [ImageBitmap], which is a real Android bitmap.
 *
 * The contract under test is the save ordering: the avatar is its own request, and getting the order
 * wrong means reporting success for text fields that were written after an avatar upload had already
 * failed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// Legacy graphics hands back null bitmaps, so ImageBitmap(1, 1) NPEs on construction.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileFieldsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val stored =
        AccountProfileFields(bio = "常驻杭州", signature = "**出杭州腾讯云轻量**", readme = "### 关于我")

    private val profiles =
        object : ProfileRepository {
            override suspend fun profile(refresh: Boolean) =
                UserProfile(
                    uid = 52425,
                    name = "林地雪原-0062",
                    avatarUrl = "https://www.nodeseek.com/avatar/52425.png",
                )

            override suspend fun profile(uid: Long) = profile()
        }

    private fun pendingAvatar() =
        PendingAvatar(
            preview = ImageBitmap(1, 1),
            upload = AvatarUpload(bytes = byteArrayOf(1, 2, 3), mimeType = "image/jpeg"),
        )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repository: FakeAccountSettingsRepository) =
        ProfileFieldsViewModel(repository, profiles)

    @Test
    fun `loads the avatar from the forum profile and the fields from the settings page`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeAccountSettingsRepository(fields = stored))
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("林地雪原-0062", state.displayName)
            assertEquals("https://www.nodeseek.com/avatar/52425.png", state.avatarUrl)
            assertEquals("常驻杭州", state.bio)
            assertEquals("### 关于我", state.readme)
        }

    /** Nothing edited means nothing to save; the top bar's 保存 is driven by this. */
    @Test
    fun `a freshly loaded form is not dirty`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeAccountSettingsRepository(fields = stored))
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isDirty)
            assertFalse(vm.uiState.value.canSave)
        }

    @Test
    fun `editing a field or picking an avatar makes the form dirty`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeAccountSettingsRepository(fields = stored))
            advanceUntilIdle()

            vm.updateBio("改成别的")
            assertTrue(vm.uiState.value.canSave)

            vm.updateBio("常驻杭州")
            assertFalse(vm.uiState.value.canSave)

            vm.setPendingAvatar(pendingAvatar())
            assertTrue("a pending avatar alone is a change worth saving", vm.uiState.value.canSave)
        }

    /** Bio is one line on the site, so a pasted paragraph must not be sent as one. */
    @Test
    fun `newlines pasted into Bio are flattened`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeAccountSettingsRepository(fields = stored))
            advanceUntilIdle()

            vm.updateBio("第一行\n第二行")

            assertEquals("第一行 第二行", vm.uiState.value.bio)
        }

    @Test
    fun `saving writes the avatar before the text fields`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository(fields = stored)
            val vm = viewModel(repository)
            advanceUntilIdle()

            vm.setPendingAvatar(pendingAvatar())
            vm.updateBio("新的一句话")
            vm.save()
            advanceUntilIdle()

            assertEquals(
                listOf("uploadAvatar", "saveProfileFields"),
                repository.calls.filter { it == "uploadAvatar" || it == "saveProfileFields" },
            )
            assertEquals("新的一句话", repository.savedFields?.bio)
            assertNull("a saved avatar is no longer pending", vm.uiState.value.pendingAvatar)
        }

    /**
     * The ordering exists for this case: an avatar that failed must stop the save, rather than let the
     * text fields succeed and the screen report "已保存" for a half-applied change.
     */
    @Test
    fun `a failed avatar upload stops the save and keeps the avatar pending`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository.failing()
            val vm = viewModel(repository)
            advanceUntilIdle()

            vm.setPendingAvatar(pendingAvatar())
            vm.updateBio("新的一句话")
            vm.save()
            advanceUntilIdle()

            assertFalse(
                "text fields must not be written after the avatar failed",
                repository.calls.contains("saveProfileFields"),
            )
            assertTrue(vm.uiState.value.message is AccountMessage.Failure)
            assertTrue("the picked image is still worth keeping", vm.uiState.value.pendingAvatar != null)
        }

    @Test
    fun `saved values become the new baseline`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeAccountSettingsRepository(fields = stored))
            advanceUntilIdle()

            vm.updateBio("新的一句话")
            vm.save()
            advanceUntilIdle()

            assertFalse("after a successful save there is nothing left to save", vm.uiState.value.canSave)
            assertEquals("新的一句话", vm.uiState.value.saved?.bio)
        }

    @Test
    fun `a failed load reports the error`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeAccountSettingsRepository.failing())
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isLoading)
            assertTrue(vm.uiState.value.message is AccountMessage.Failure)
        }
}
