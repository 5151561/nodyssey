package io.github.bbs1.ui.auth

import io.github.bbs1.data.InstanceRepository
import io.github.bbs1.data.newTestInstanceRepository
import io.github.bbs1.net.ApiAuth
import io.github.bbs1.net.ApiAvatar
import io.github.bbs1.net.ApiUser
import io.github.bbs1.net.Bbs1ApiException
import io.github.bbs1.net.FakeBbs1Api
import io.github.bbs1.ui.common.ApiErrorUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(
        val repository: InstanceRepository,
        val viewModel: LoginViewModel,
        val scope: CoroutineScope,
    )

    private suspend fun TestScope.newFixture(api: FakeBbs1Api): Fixture {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val scope = CoroutineScope(dispatcher + Job())
        val repository = newTestInstanceRepository(scope, tmp.root) { "site" }
        repository.add("https://bbs1.org", "站")
        return Fixture(repository, LoginViewModel(api, repository, "site", "https://bbs1.org"), scope)
    }

    @Test
    fun `a successful login stores the token against the site`() = runTest {
        val api = FakeBbs1Api().apply {
            loginResult = { _, username, _ ->
                ApiAuth(
                    token = "2.1786000000.sig",
                    tokenExpiresAt = 1786000000,
                    user = ApiUser(id = 2, username = username, avatar = ApiAvatar(url = "https://cdn/a.png")),
                )
            }
        }
        val f = newFixture(api)

        f.viewModel.submit("  alice  ", "secret")
        advanceUntilIdle()

        val session = f.repository.session("site").first()
        assertEquals("2.1786000000.sig", session?.token)
        assertEquals(1786000000L, session?.expiresAt)
        assertEquals("alice", session?.username)
        assertEquals("https://cdn/a.png", session?.avatarUrl)
        assertTrue(f.viewModel.uiState.value.succeeded)
        // The name is sent trimmed — a trailing space from an autocomplete is not part of it.
        assertEquals(listOf("login(alice)"), api.calls)
        f.scope.cancel()
    }

    @Test
    fun `a rejected password shows the site's own wording and stores nothing`() = runTest {
        val api = FakeBbs1Api().apply {
            loginResult = { _, _, _ -> throw Bbs1ApiException.Server("用户名或密码错误") }
        }
        val f = newFixture(api)

        f.viewModel.submit("alice", "wrong")
        advanceUntilIdle()

        assertEquals(ApiErrorUi.Server("用户名或密码错误"), f.viewModel.uiState.value.error)
        assertFalse(f.viewModel.uiState.value.succeeded)
        assertNull(f.repository.session("site").first())

        f.viewModel.consumeError()
        assertNull(f.viewModel.uiState.value.error)
        f.scope.cancel()
    }

    @Test
    fun `an empty field is not a round trip`() = runTest {
        val api = FakeBbs1Api()
        val f = newFixture(api)

        f.viewModel.submit("", "secret")
        f.viewModel.submit("alice", "")
        advanceUntilIdle()

        assertEquals(emptyList<String>(), api.calls)
        f.scope.cancel()
    }
}
