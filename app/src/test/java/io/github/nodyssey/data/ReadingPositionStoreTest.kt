package io.github.nodyssey.data

import io.github.nodyssey.data.local.NodeSeekDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Where each thread was left off, on the real database.
 *
 * The behaviour worth testing here is the SQL: one row per thread that a second read replaces rather
 * than duplicates. How many rows are kept is 保留条数's answer and is tested with the trim that
 * enforces it — see `ReadHistoryTest`.
 */
@RunWith(RobolectricTestRunner::class)
class ReadingPositionStoreTest {
    private lateinit var database: NodeSeekDatabase
    private val clock = MutableClock()
    private lateinit var store: RoomReadingPositionStore

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        store = RoomReadingPositionStore(database.readingPositionDao(), clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a thread nobody has read has no place to return to`() =
        runTest {
            assertNull(store.readingPosition(703863))
        }

    @Test
    fun `each thread keeps its own place`() =
        runTest {
            store.setReadingPosition(703863, ReadingPosition(page = 4, floor = "#31"))
            store.setReadingPosition(704000, ReadingPosition(page = 9))

            assertEquals(ReadingPosition(page = 4, floor = "#31"), store.readingPosition(703863))
            assertEquals(ReadingPosition(page = 9), store.readingPosition(704000))
        }

    /** Reading on replaces the place rather than accumulating them; the newest is the only one worth keeping. */
    @Test
    fun `the latest place for a thread replaces the one before it`() =
        runTest {
            store.setReadingPosition(703863, ReadingPosition(page = 4, floor = "#31"))
            store.setReadingPosition(703863, ReadingPosition(page = 5, floor = "#42"))

            assertEquals(ReadingPosition(page = 5, floor = "#42"), store.readingPosition(703863))
            assertEquals(1, database.readingPositionDao().count())
        }
}
