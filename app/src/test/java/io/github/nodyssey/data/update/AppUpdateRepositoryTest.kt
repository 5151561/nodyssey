package io.github.nodyssey.data.update

import android.content.pm.PackageInstaller
import io.github.nodyssey.core.AppClock
import io.github.nodyssey.core.AppDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateRepositoryTest {
    @get:Rule
    val downloads = TemporaryFolder()

    @Test
    fun `a newer release is offered, an older one is not`() =
        runTest {
            val source = FakeReleaseSource(release("1.2.0"))
            val repository = repository(source)

            repository.check(force = true)
            advanceUntilIdle()
            assertEquals(UpdateCheck.Available(release("1.2.0")), repository.state.value.check)

            source.release = release("1.0.0")
            repository.check(force = true)
            advanceUntilIdle()
            assertEquals(UpdateCheck.UpToDate, repository.state.value.check)
        }

    @Test
    fun `the stored answer stands in until it goes stale`() =
        runTest {
            val source = FakeReleaseSource(release("1.2.0"))
            val store = FakeUpdateCheckStore()
            val clock = MutableClock(NOW)
            val repository = repository(source, store, clock)

            repository.check()
            advanceUntilIdle()
            assertEquals(1, source.calls)

            // Same hour, unforced: answered from what the first call stored.
            clock.now = NOW + 60 * 60 * 1000L
            repository.check()
            advanceUntilIdle()
            assertEquals(1, source.calls)
            assertEquals(UpdateCheck.Available(release("1.2.0")), repository.state.value.check)

            // The button never waits for the interval.
            repository.check(force = true)
            advanceUntilIdle()
            assertEquals(2, source.calls)

            clock.now += DefaultAppUpdateRepository.CHECK_INTERVAL_MILLIS
            repository.check()
            advanceUntilIdle()
            assertEquals(3, source.calls)
        }

    @Test
    fun `a stored answer that has since been installed is not offered again`() =
        runTest {
            // What the record looks like right after this very feature installed 1.2.0: fresh, and
            // naming the version now running.
            val store =
                FakeUpdateCheckStore(UpdateCheckRecord(checkedAtMillis = NOW, release = release("1.2.0")))
            val source = FakeReleaseSource(release("1.2.0"))
            val repository =
                repository(source, store, MutableClock(NOW + 1_000L), currentVersionName = "1.2.0")

            repository.check()
            advanceUntilIdle()

            assertEquals(UpdateCheck.UpToDate, repository.state.value.check)
            assertEquals(0, source.calls)
        }

    @Test
    fun `a failed check says so rather than claiming the app is current`() =
        runTest {
            val source = FakeReleaseSource(release("1.2.0"), failWith = UpdateFailure.Network)
            val repository = repository(source)

            repository.check(force = true)
            advanceUntilIdle()

            assertEquals(UpdateCheck.Failed(UpdateFailure.Network), repository.state.value.check)
        }

    @Test
    fun `a finished download is ready at a real path, and is not fetched twice`() =
        runTest {
            val source = FakeReleaseSource(release("1.2.0"))
            val repository = repository(source)

            repository.check(force = true)
            advanceUntilIdle()
            repository.download()
            advanceUntilIdle()

            val ready = repository.state.value.download as UpdateDownload.Ready
            assertEquals("1.2.0", ready.versionName)
            val apk = File(ready.apkPath)
            assertTrue(apk.isFile)
            assertEquals(RELEASE_SIZE, apk.length())
            assertEquals(1, source.downloads)

            repository.download()
            advanceUntilIdle()
            assertEquals(1, source.downloads)
        }

    @Test
    fun `a download cut off halfway is not left behind as an installable APK`() =
        runTest {
            val source = FakeReleaseSource(release("1.2.0"), failDownloadWith = UpdateFailure.Network)
            val repository = repository(source)

            repository.check(force = true)
            advanceUntilIdle()
            repository.download()
            advanceUntilIdle()

            assertEquals(
                UpdateDownload.Failed(UpdateFailure.Network),
                repository.state.value.download,
            )
            // The partial write is still there under its own name; nothing ends in `.apk`.
            assertTrue(downloads.root.listFiles().orEmpty().none { it.name.endsWith(".apk") })
        }

    @Test
    fun `backing out of the system installer is not reported as a failure`() =
        runTest {
            val repository = repository(FakeReleaseSource(release("1.2.0")))

            repository.onInstallStatus(PackageInstaller.STATUS_FAILURE_CONFLICT)
            assertEquals(InstallFailure.CONFLICT, repository.state.value.installFailure)

            repository.onInstallStatus(PackageInstaller.STATUS_FAILURE_ABORTED)
            assertNull(repository.state.value.installFailure)
        }

    private fun TestScope.repository(
        source: ReleaseSource,
        store: UpdateCheckStore = FakeUpdateCheckStore(),
        clock: MutableClock = MutableClock(NOW),
        currentVersionName: String = "1.1.0",
    ): DefaultAppUpdateRepository {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return DefaultAppUpdateRepository(
            source = source,
            store = store,
            clock = clock,
            dispatchers = AppDispatchers(dispatcher, dispatcher),
            scope = this,
            currentVersionName = currentVersionName,
            downloadDirectory = downloads.root,
        )
    }

    private companion object {
        /** A plausible wall clock. Epoch zero would read as "never checked", which it is not. */
        const val NOW = 1_770_000_000_000L
        const val RELEASE_SIZE = 2_048L

        fun release(versionName: String) =
            AppRelease(
                versionName = versionName,
                tag = "v$versionName",
                notes = "notes",
                downloadUrl = "https://example.invalid/nodyssey-v$versionName.apk",
                assetName = "nodyssey-v$versionName.apk",
                sizeBytes = RELEASE_SIZE,
                htmlUrl = "https://example.invalid/releases",
            )
    }
}

private class MutableClock(var now: Long) : AppClock {
    override fun nowMillis(): Long = now
}

private class FakeReleaseSource(
    var release: AppRelease?,
    private val failWith: UpdateFailure? = null,
    private val failDownloadWith: UpdateFailure? = null,
) : ReleaseSource {
    var calls = 0
    var downloads = 0

    override suspend fun latestRelease(): AppRelease? {
        calls++
        failWith?.let { throw AppUpdateException(it) }
        return release
    }

    override suspend fun download(
        release: AppRelease,
        target: File,
        onProgress: (Long, Long) -> Unit,
    ) {
        downloads++
        target.parentFile?.mkdirs()
        // Half of it lands before the failure, which is the case the `.part` rename exists for.
        target.writeBytes(ByteArray((release.sizeBytes / 2).toInt()))
        onProgress(release.sizeBytes / 2, release.sizeBytes)
        failDownloadWith?.let { throw AppUpdateException(it) }
        target.writeBytes(ByteArray(release.sizeBytes.toInt()))
        onProgress(release.sizeBytes, release.sizeBytes)
    }
}

private class FakeUpdateCheckStore(
    private var record: UpdateCheckRecord = UpdateCheckRecord(),
) : UpdateCheckStore {
    override suspend fun updateCheckRecord(): UpdateCheckRecord = record

    override suspend fun setUpdateCheckRecord(record: UpdateCheckRecord) {
        this.record = record
    }
}
