package io.github.nodyssey.data.update

import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.update.AppRelease
import io.github.plaza.core.update.AppUpdateException
import io.github.plaza.core.update.InstallFailure
import io.github.plaza.core.update.InstallOutcome
import io.github.plaza.core.update.ReleaseNote
import io.github.plaza.core.update.ReleaseSource
import io.github.plaza.core.update.UpdateCheck
import io.github.plaza.core.update.UpdateCheckRecord
import io.github.plaza.core.update.UpdateDownload
import io.github.plaza.core.update.UpdateFailure
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
    fun `the dev channel is what decides whether test builds are asked for`() =
        runTest {
            val source =
                FakeReleaseSource(release("1.2.0"), preRelease = release("1.3.0-dev.2", preRelease = true))
            val store = FakeUpdateCheckStore()
            val repository = repository(source, store)

            repository.check(force = true)
            advanceUntilIdle()
            assertEquals(false, source.askedForPreReleases)
            assertEquals(UpdateCheck.Available(release("1.2.0")), repository.state.value.check)

            store.devChannel = true
            repository.check(force = true)
            advanceUntilIdle()
            assertEquals(true, source.askedForPreReleases)
            assertEquals(
                UpdateCheck.Available(release("1.3.0-dev.2", preRelease = true)),
                repository.state.value.check,
            )
        }

    /**
     * The switch has to take effect now, not in six hours: the stored answer was the other channel's,
     * and reusing it is how 接收 dev 版更新 would appear to do nothing until the next day.
     */
    @Test
    fun `flipping the channel retires the stored answer`() =
        runTest {
            val store = FakeUpdateCheckStore()
            val source =
                FakeReleaseSource(release("1.2.0"), preRelease = release("1.3.0-dev.2", preRelease = true))
            val repository = repository(source, store)

            repository.check()
            advanceUntilIdle()
            assertEquals(1, source.calls)

            // Same six-hour window, unforced — but the question changed.
            store.devChannel = true
            repository.check()
            advanceUntilIdle()
            assertEquals(2, source.calls)
            assertEquals(
                UpdateCheck.Available(release("1.3.0-dev.2", preRelease = true)),
                repository.state.value.check,
            )

            // And a second unforced check on the same channel is still answered from the record.
            repository.check()
            advanceUntilIdle()
            assertEquals(2, source.calls)
        }

    /** Turning it back off leaves the dev build installed and simply stops offering another. */
    @Test
    fun `a dev build already installed is not offered again once the channel is off`() =
        runTest {
            val source = FakeReleaseSource(release("1.2.0"))
            val repository =
                repository(source, currentVersionName = "1.3.0-dev.2")

            repository.check(force = true)
            advanceUntilIdle()

            assertEquals(UpdateCheck.UpToDate, repository.state.value.check)
        }

    /** 更新日志 lists what the user could install, so the channel decides what it shows. */
    @Test
    fun `the release log follows the same channel as the check`() =
        runTest {
            val source = FakeReleaseSource(release("1.2.0"), notes = listOf(note("1.2.0")))
            val store = FakeUpdateCheckStore()
            val repository = repository(source, store)

            assertEquals(listOf(note("1.2.0")), repository.releaseNotes())
            assertEquals(false, source.askedForPreReleases)

            store.devChannel = true
            repository.releaseNotes()
            assertEquals(true, source.askedForPreReleases)
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
    fun `the launch check asks about a new release, opening 关于 does not`() =
        runTest {
            val source = FakeReleaseSource(release("1.2.0"))
            val repository = repository(source)

            repository.check()
            advanceUntilIdle()
            assertEquals(UpdateCheck.Available(release("1.2.0")), repository.state.value.check)
            assertNull(repository.launchReminder.value)

            repository.checkOnLaunch()
            advanceUntilIdle()
            assertEquals(release("1.2.0"), repository.launchReminder.value)
        }

    @Test
    fun `a launch answered from the stored record still asks`() =
        runTest {
            // The second launch inside the six-hour window: no call goes out, and the reminder is
            // exactly what that launch is for.
            val store =
                FakeUpdateCheckStore(UpdateCheckRecord(checkedAtMillis = NOW, release = release("1.2.0")))
            val source = FakeReleaseSource(release("1.2.0"))
            val repository = repository(source, store, MutableClock(NOW + 1_000L))

            repository.checkOnLaunch()
            advanceUntilIdle()

            assertEquals(0, source.calls)
            assertEquals(release("1.2.0"), repository.launchReminder.value)
        }

    @Test
    fun `稍后 settles that version, and only that version`() =
        runTest {
            val source = FakeReleaseSource(release("1.2.0"))
            val store = FakeUpdateCheckStore()
            val repository = repository(source, store)

            repository.checkOnLaunch()
            advanceUntilIdle()
            repository.postponeLaunchReminder()
            advanceUntilIdle()
            assertNull(repository.launchReminder.value)
            assertEquals("1.2.0", store.postponed)

            // Every later launch stays quiet about it — the dot on 设置 is what still carries it.
            repository.checkOnLaunch()
            advanceUntilIdle()
            assertNull(repository.launchReminder.value)
            assertEquals(UpdateCheck.Available(release("1.2.0")), repository.state.value.check)

            // The next release is a different question, and gets asked.
            source.release = release("1.3.0")
            repository.check(force = true)
            advanceUntilIdle()
            repository.checkOnLaunch()
            advanceUntilIdle()
            assertEquals(release("1.3.0"), repository.launchReminder.value)
        }

    @Test
    fun `下载并安装 from the reminder closes it and fetches the APK`() =
        runTest {
            val source = FakeReleaseSource(release("1.2.0"))
            val store = FakeUpdateCheckStore()
            val repository = repository(source, store)

            repository.checkOnLaunch()
            advanceUntilIdle()
            repository.acceptLaunchReminder()
            advanceUntilIdle()

            assertNull(repository.launchReminder.value)
            assertEquals("1.2.0", (repository.state.value.download as UpdateDownload.Ready).versionName)
            // Not 稍后: nothing was installed yet, so a launch after a download that never finished
            // installing must still be able to bring it up.
            assertNull(store.postponed)
        }

    @Test
    fun `nothing is asked when there is nothing newer`() =
        runTest {
            val repository = repository(FakeReleaseSource(release("1.0.0")))

            repository.checkOnLaunch()
            advanceUntilIdle()

            assertEquals(UpdateCheck.UpToDate, repository.state.value.check)
            assertNull(repository.launchReminder.value)
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

            repository.onInstallOutcome(InstallOutcome.Failed(InstallFailure.CONFLICT))
            assertEquals(InstallFailure.CONFLICT, repository.state.value.installFailure)

            repository.onInstallOutcome(InstallOutcome.Abandoned)
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

        fun note(versionName: String) =
            ReleaseNote(
                versionName = versionName,
                tag = "v$versionName",
                notes = "notes",
                publishedOn = "2026-08-17",
                preRelease = false,
                htmlUrl = "https://example.invalid/releases",
            )

        fun release(versionName: String, preRelease: Boolean = false) =
            AppRelease(
                versionName = versionName,
                tag = "v$versionName",
                notes = "notes",
                downloadUrl = "https://example.invalid/nodyssey-v$versionName.apk",
                assetName = "nodyssey-v$versionName.apk",
                sizeBytes = RELEASE_SIZE,
                htmlUrl = "https://example.invalid/releases",
                preRelease = preRelease,
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
    /** What the dev channel would answer, when a test cares about the difference. */
    private val preRelease: AppRelease? = null,
    private val notes: List<ReleaseNote> = emptyList(),
) : ReleaseSource {
    var calls = 0
    var downloads = 0
    var askedForPreReleases: Boolean? = null
        private set

    override suspend fun latestRelease(includePreRelease: Boolean): AppRelease? {
        calls++
        askedForPreReleases = includePreRelease
        failWith?.let { throw AppUpdateException(it) }
        return if (includePreRelease) preRelease ?: release else release
    }

    override suspend fun releaseNotes(includePreRelease: Boolean): List<ReleaseNote> {
        askedForPreReleases = includePreRelease
        return notes
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
    var devChannel: Boolean = false,
) : UpdateCheckStore {
    var postponed: String? = null
        private set

    override suspend fun updateCheckRecord(): UpdateCheckRecord = record

    override suspend fun setUpdateCheckRecord(record: UpdateCheckRecord) {
        this.record = record
    }

    override suspend fun devChannelEnabled(): Boolean = devChannel

    override suspend fun postponedUpdateVersion(): String? = postponed

    override suspend fun setPostponedUpdateVersion(versionName: String) {
        postponed = versionName
    }
}
