package io.github.bbs1.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.bbs1.model.InstanceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Against a real DataStore over a file in a temporary folder — the preferences core runs on the
 * plain JVM, so nothing here needs Robolectric.
 */
class InstanceRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var nextId = 0

    private fun TestScope.newRepository(prefix: String = ""): Pair<InstanceRepository, CoroutineScope> {
        // A child Job, not the test's own: DataStore keeps its scope for the life of the store, and
        // the test needs to cancel it to release the file before opening a second store over it.
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        return newTestInstanceRepository(scope, tmp.root, prefix) { "id-${nextId++}" } to scope
    }

    @Test
    fun `adding a site makes it current and names it after its host when unnamed`() = runTest {
        val (repository, scope) = newRepository()
        repository.add("https://bbs1.org", name = null)

        val snapshot = repository.snapshot.first()
        assertEquals(listOf("https://bbs1.org"), snapshot.instances.map { it.baseUrl })
        assertEquals("bbs1.org", snapshot.instances.single().name)
        assertEquals(snapshot.instances.single().id, snapshot.currentId)
        scope.cancel()
    }

    @Test
    fun `adding an origin that already exists selects it instead of duplicating it`() = runTest {
        val (repository, scope) = newRepository()
        repository.add("https://bbs1.org", name = "第一个")
        repository.add("https://bbs.example.com", name = null)
        repository.add("https://bbs1.org", name = "换个名字也不新建")

        val snapshot = repository.snapshot.first()
        assertEquals(2, snapshot.instances.size)
        assertEquals("第一个", snapshot.current?.name)
        scope.cancel()
    }

    @Test
    fun `removing the current site promotes the first remaining one`() = runTest {
        val (repository, scope) = newRepository()
        repository.add("https://a.example.com", name = null)
        repository.add("https://b.example.com", name = null)

        val current = repository.snapshot.first().currentId!!
        repository.remove(current)

        val snapshot = repository.snapshot.first()
        assertEquals("https://a.example.com", snapshot.current?.baseUrl)
        scope.cancel()
    }

    @Test
    fun `removing the last site clears the selection`() = runTest {
        val (repository, scope) = newRepository()
        repository.add("https://bbs1.org", name = null)
        repository.remove(repository.snapshot.first().currentId!!)

        val snapshot = repository.snapshot.first()
        assertEquals(emptyList<Any>(), snapshot.instances)
        assertNull(snapshot.currentId)
        scope.cancel()
    }

    @Test
    fun `select switches the current site and ignores an unknown id`() = runTest {
        val (repository, scope) = newRepository()
        repository.add("https://a.example.com", name = null)
        val first = repository.snapshot.first().currentId!!
        repository.add("https://b.example.com", name = null)

        repository.select(first)
        assertEquals(first, repository.snapshot.first().currentId)

        repository.select("no-such-id")
        assertEquals(first, repository.snapshot.first().currentId)
        scope.cancel()
    }

    @Test
    fun `removing a site that is not current leaves the selection alone`() = runTest {
        val (repository, scope) = newRepository()
        repository.add("https://a.example.com", name = null)
        repository.add("https://b.example.com", name = null)

        val current = repository.snapshot.first().currentId!!
        val other = repository.snapshot.first().instances.first { it.id != current }.id
        repository.remove(other)

        val snapshot = repository.snapshot.first()
        assertEquals(current, snapshot.currentId)
        assertEquals("https://b.example.com", snapshot.current?.baseUrl)
        scope.cancel()
    }

    @Test
    fun `a credential is stored against its own site and survives a reopen`() = runTest {
        val (repository, scope) = newRepository()
        repository.add("https://a.example.com", name = null)
        val first = repository.snapshot.first().currentId!!
        repository.add("https://b.example.com", name = null)
        val second = repository.snapshot.first().currentId!!

        repository.saveSession(
            first,
            InstanceSession(token = "tok-a", expiresAt = 1786000000, userId = 2, username = "alice"),
        )

        assertEquals("alice", repository.session(first).first()?.username)
        // Signing in to one forum does not sign anyone in to the next one.
        assertNull(repository.session(second).first())
        scope.cancel()

        val (reopened, reopenedScope) = newRepository()
        assertEquals("tok-a", reopened.session(first).first()?.token)
        reopenedScope.cancel()
    }

    @Test
    fun `signing out forgets the token and leaves the site`() = runTest {
        val (repository, scope) = newRepository()
        repository.add("https://bbs1.org", name = null)
        val id = repository.snapshot.first().currentId!!
        repository.saveSession(id, InstanceSession(token = "tok", expiresAt = 0, userId = 2, username = "alice"))

        repository.clearSession(id)

        assertNull(repository.session(id).first())
        assertEquals(1, repository.snapshot.first().instances.size)
        scope.cancel()
    }

    @Test
    fun `removing a site takes its credential with it`() = runTest {
        val (repository, scope) = newRepository()
        repository.add("https://bbs1.org", name = null)
        val id = repository.snapshot.first().currentId!!
        repository.saveSession(id, InstanceSession(token = "tok", expiresAt = 0, userId = 2, username = "alice"))

        repository.remove(id)

        // Not merely unreachable: the stored list is what holds the token, so it is gone from disk.
        assertFalse(repository.snapshot.first().instances.any { it.session != null })
        scope.cancel()
    }

    @Test
    fun `an expiry in the past reads as expired and a missing one does not`() {
        val now = 1786000000L
        assertTrue(
            InstanceSession(token = "t", expiresAt = now - 1, userId = 1, username = "a").isExpiredAt(now),
        )
        assertFalse(
            InstanceSession(token = "t", expiresAt = now + 1, userId = 1, username = "a").isExpiredAt(now),
        )
        assertFalse(
            InstanceSession(token = "t", expiresAt = 0, userId = 1, username = "a").isExpiredAt(now),
        )
    }

    @Test
    fun `a stored list that no longer decodes reads as empty instead of throwing`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val store =
            PreferenceDataStoreFactory.create(scope = scope) {
                File(tmp.root, "corrupt-instances.preferences_pb")
            }
        // Same key the repository writes under; the value is what a bad migration or a hand-edited
        // backup would leave behind.
        store.edit { it[stringPreferencesKey("instances")] = "{definitely not json" }

        val repository =
            InstanceRepository(
                dataStore = store,
                sessionStore =
                PreferenceDataStoreFactory.create(scope = scope) {
                    File(tmp.root, "corrupt-sessions.preferences_pb")
                },
            ) { "id" }
        val snapshot = repository.snapshot.first()
        assertEquals(emptyList<Any>(), snapshot.instances)

        // And the store still accepts new sites afterwards.
        repository.add("https://bbs1.org", name = null)
        assertEquals("bbs1.org", repository.snapshot.first().current?.name)
        scope.cancel()
    }

    @Test
    fun `the list survives a new repository over the same file`() = runTest {
        val (first, firstScope) = newRepository()
        first.add("https://bbs1.org", name = "主站")
        firstScope.cancel()

        val (second, secondScope) = newRepository()
        val snapshot = second.snapshot.first()
        assertEquals("主站", snapshot.current?.name)
        secondScope.cancel()
    }
}
