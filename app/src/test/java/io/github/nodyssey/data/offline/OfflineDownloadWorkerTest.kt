package io.github.nodyssey.data.offline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * How each way a drain can end is reported back to WorkManager — the classification that decides
 * whether the scheduler retries.
 *
 * The middle case is the one with history: before `DrainOutcome.BLOCKED` existed, a Cloudflare
 * challenge was reported as a network failure, and WorkManager's backoff curve aimed request after
 * request at exactly the wall the challenge had put up. These tests pin each outcome to its verdict
 * so the next re-mapping is a red test rather than a traffic pattern.
 */
@RunWith(RobolectricTestRunner::class)
class OfflineDownloadWorkerTest {
    @Test
    fun `a drained queue is a success`() =
        runTest {
            assertEquals(Result.success(), doWork(FakeDownloads(DrainOutcome.DRAINED)))
        }

    @Test
    fun `an unreachable site is a retry, with WorkManager's own backoff`() =
        runTest {
            assertEquals(Result.retry(), doWork(FakeDownloads(DrainOutcome.NETWORK_FAILED)))
        }

    @Test
    fun `a challenge or rate limit ends the run without a retry`() =
        runTest {
            assertEquals(Result.success(), doWork(FakeDownloads(DrainOutcome.BLOCKED)))
        }

    @Test
    fun `a library with no download half finishes with nothing to do`() =
        runTest {
            assertEquals(Result.success(), doWork(downloads = null))
        }

    private suspend fun doWork(downloads: OfflineDownloads?): Result {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker =
            TestListenableWorkerBuilder<OfflineDownloadWorker>(context)
                .setWorkerFactory(
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker = OfflineDownloadWorker(appContext, workerParameters, downloads)
                    },
                ).build()
        return worker.doWork()
    }

    private class FakeDownloads(
        private val outcome: DrainOutcome,
    ) : OfflineDownloads {
        override suspend fun hasQueuedWork(): Boolean = true

        override suspend fun staleIds(): List<Long> = emptyList()

        override suspend fun drainQueue(): DrainOutcome = outcome

        override suspend fun sweepExpired() = Unit
    }
}
