package io.github.nodyssey.data.settings

import io.github.nodyssey.data.ReadingPosition
import io.github.nodyssey.data.testSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where each thread was left off, on the real DataStore.
 *
 * The behaviour worth testing is the encoding: every place lives in one preference, so a write for
 * one thread re-encodes the record for all of them — which is exactly how a store like this loses
 * everything but its most recent entry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadingPositionStoreTest {
    @Test
    fun `a thread nobody has read has no place to return to`() =
        runTest {
            val settings = testSettingsRepository(backgroundScope)

            assertNull(settings.readingPosition(703863))
        }

    @Test
    fun `each thread keeps its own place`() =
        runTest {
            val settings = testSettingsRepository(backgroundScope)

            settings.setReadingPosition(703863, ReadingPosition(page = 4, floor = "#31"))
            settings.setReadingPosition(704000, ReadingPosition(page = 9))

            assertEquals(ReadingPosition(page = 4, floor = "#31"), settings.readingPosition(703863))
            assertEquals(ReadingPosition(page = 9), settings.readingPosition(704000))
        }

    /** Reading on replaces the place rather than accumulating them; the newest is the only one worth keeping. */
    @Test
    fun `the latest place for a thread replaces the one before it`() =
        runTest {
            val settings = testSettingsRepository(backgroundScope)

            settings.setReadingPosition(703863, ReadingPosition(page = 4, floor = "#31"))
            settings.setReadingPosition(703863, ReadingPosition(page = 5, floor = "#42"))

            assertEquals(ReadingPosition(page = 5, floor = "#42"), settings.readingPosition(703863))
        }
}
