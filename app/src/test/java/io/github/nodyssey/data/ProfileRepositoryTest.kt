package io.github.nodyssey.data

import io.github.nodyssey.core.AppClock
import io.github.nodyssey.core.html.Fixtures
import io.github.nodyssey.core.net.HtmlSource
import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.data.local.NodeSeekDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileRepositoryTest {
    private lateinit var database: NodeSeekDatabase

    @Before
    fun setUp() {
        database = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `loads the current user and refreshes values from the account endpoint`() =
        runTest {
            val jsonSource =
                FakeProfileJsonSource(
                    response =
                    """
                    {"data":{"member_id":31037,"member_name":"缭雾","rank":2,
                    "coin":305,"stardust":7,"created_at":"2025-04-27T14:29:22.000Z"}}
                    """.trimIndent(),
                )
            val repository =
                NetworkProfileRepository(
                    htmlSource = FakeProfileHtmlSource(Fixtures.load("page-1.html")),
                    jsonSource = jsonSource,
                    profileDao = database.profileDao(),
                    currentSessionFingerprint = { 7 },
                    clock = AppClock { 0L },
                )

            val profile = repository.profile()

            assertEquals(31037L, profile.uid)
            assertEquals("缭雾", profile.name)
            assertEquals(2, profile.rank)
            assertEquals(305, profile.chickenCount)
            assertEquals(7, profile.starCount)
            assertEquals("https://www.nodeseek.com/avatar/31037.png", profile.avatarUrl)
            assertEquals(NodeSeekJsonClient.accountInfoPath(31037), jsonSource.requestedPath)
        }

    @Test
    fun `keeps bootstrap values when the account endpoint is temporarily unavailable`() =
        runTest {
            val repository =
                NetworkProfileRepository(
                    htmlSource = FakeProfileHtmlSource(Fixtures.load("page-1.html")),
                    jsonSource =
                    FakeProfileJsonSource(
                        error = NodeSeekException(NodeSeekError.Http(500)),
                    ),
                    profileDao = database.profileDao(),
                    currentSessionFingerprint = { 7 },
                    clock = AppClock { 0L },
                )

            val profile = repository.profile()

            assertEquals(31037L, profile.uid)
            assertEquals("缭雾", profile.name)
            assertEquals(1, profile.rank)
            assertEquals(292, profile.chickenCount)
            assertEquals(2, profile.starCount)
        }

    /** The profile-area screens each ask on entry; a minute-long cache keeps that to one fetch. */
    @Test
    fun `serves repeat profile calls from the cache until refreshed`() =
        runTest {
            val htmlSource = FakeProfileHtmlSource(Fixtures.load("page-1.html"))
            val repository =
                NetworkProfileRepository(
                    htmlSource = htmlSource,
                    jsonSource = FakeProfileJsonSource(error = NodeSeekException(NodeSeekError.Http(500))),
                    profileDao = database.profileDao(),
                    currentSessionFingerprint = { 7 },
                    clock = AppClock { 0L },
                )

            repository.profile()
            repository.profile()
            assertEquals(1, htmlSource.callCount)

            repository.profile(refresh = true)
            assertEquals(2, htmlSource.callCount)
        }

    @Test
    fun `restores the signed in profile from Room without a network request`() =
        runTest {
            val firstSource = FakeProfileHtmlSource(Fixtures.load("page-1.html"))
            val firstRepository =
                NetworkProfileRepository(
                    htmlSource = firstSource,
                    jsonSource = FakeProfileJsonSource(error = NodeSeekException(NodeSeekError.Http(500))),
                    profileDao = database.profileDao(),
                    currentSessionFingerprint = { 7 },
                    clock = AppClock { 0L },
                )
            firstRepository.refreshProfile(sessionFingerprint = 7)

            val restoredSource = FakeProfileHtmlSource("")
            val restoredRepository =
                NetworkProfileRepository(
                    htmlSource = restoredSource,
                    jsonSource = FakeProfileJsonSource(error = AssertionError("must not run")),
                    profileDao = database.profileDao(),
                    currentSessionFingerprint = { 7 },
                    clock = AppClock { 0L },
                )

            val restored = restoredRepository.observeProfile(sessionFingerprint = 7).first()

            assertEquals("缭雾", restored?.name)
            assertEquals(0, restoredSource.callCount)
        }

    @Test
    fun `does not expose a profile to a different session fingerprint`() =
        runTest {
            val repository =
                NetworkProfileRepository(
                    htmlSource = FakeProfileHtmlSource(Fixtures.load("page-1.html")),
                    jsonSource = FakeProfileJsonSource(error = NodeSeekException(NodeSeekError.Http(500))),
                    profileDao = database.profileDao(),
                    currentSessionFingerprint = { 7 },
                    clock = AppClock { 0L },
                )
            repository.refreshProfile(sessionFingerprint = 7)

            assertNull(repository.observeProfile(sessionFingerprint = 8).first())

            repository.clearCachedProfile()
            assertNull(repository.observeProfile(sessionFingerprint = 7).first())
        }

    /** Without the flag the endpoint omits `readme`, and every space page fell back to the empty state. */
    @Test
    fun `asks the account endpoint to include another user's readme`() =
        runTest {
            val jsonSource =
                FakeProfileJsonSource(
                    response = """{"data":{"member_id":42,"member_name":"某人","readme":"# 你好"}}""",
                )
            val repository =
                NetworkProfileRepository(
                    htmlSource = FakeProfileHtmlSource(""),
                    jsonSource = jsonSource,
                    profileDao = database.profileDao(),
                    currentSessionFingerprint = { 7 },
                    clock = AppClock { 0L },
                )

            val profile = repository.profile(uid = 42)

            assertEquals("# 你好", profile.readme)
            assertEquals("/api/account/getInfo/42?readme=1", jsonSource.requestedPath)
        }

    @Test(expected = NodeSeekException::class)
    fun `rejects a page without signed in profile data`() =
        runTest {
            NetworkProfileRepository(
                htmlSource = FakeProfileHtmlSource("<html><body></body></html>"),
                jsonSource = FakeProfileJsonSource(response = "{}"),
                profileDao = database.profileDao(),
                currentSessionFingerprint = { 7 },
                clock = AppClock { 0L },
            ).profile()
        }
}

private class FakeProfileHtmlSource(
    private val response: String,
) : HtmlSource {
    var callCount = 0
        private set

    override suspend fun getHtml(path: String): String {
        callCount++
        return response
    }
}

private class FakeProfileJsonSource(
    private val response: String? = null,
    private val error: Throwable? = null,
) : JsonSource {
    var requestedPath: String? = null
        private set

    override suspend fun getJson(path: String, referer: String): String {
        requestedPath = path
        error?.let { throw it }
        return requireNotNull(response)
    }
}
