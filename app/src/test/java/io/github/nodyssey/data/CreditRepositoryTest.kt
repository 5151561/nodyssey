package io.github.nodyssey.data

import io.github.nodyssey.core.AppDispatchers
import io.github.nodyssey.core.net.JsonPostResponse
import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.core.net.NodeSeekJsonClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins `/api/account/credit/page-N` as captured from a signed-in account on 2026-07-30.
 *
 * The rows are positional arrays, which is the fragile part: there is no field name to fall back on if
 * a column moves, so the fixture below is the only thing standing between a reordered payload and a
 * screen that confidently shows the balance in the amount column.
 */
class CreditRepositoryTest {
    private val dispatchers =
        AppDispatchers(io = Dispatchers.Unconfined, default = Dispatchers.Unconfined)

    @Test
    fun `reads the four columns and asks for the requested page`() =
        runTest {
            val source =
                FakeCreditPageJsonSource(
                    """
                    {"success":true,
                     "data":[[1,384,"回帖奖励","2026-07-30T12:28:11.000Z"],
                             [-1,350,"投喂鸡腿","2026-07-26T10:53:40.000Z"]],
                     "total":151}
                    """.trimIndent(),
                )

            val page = NetworkCreditRepository(source, dispatchers).page(2)

            assertEquals(NodeSeekJsonClient.creditLedgerPath(2), source.requestedPath)
            assertEquals(2, page.entries.size)
            val first = page.entries.first()
            assertEquals(1, first.change)
            assertEquals(384, first.balanceAfter)
            assertEquals("回帖奖励", first.reason)
            // 2026-07-30T12:28:11Z, so the model keeps an instant and lets the screen pick a zone.
            assertEquals(1_785_414_491_000L, first.createdAtMillis)
            // A spend stays negative rather than being normalised to a magnitude plus a flag.
            assertEquals(-1, page.entries[1].change)
        }

    /** `total` counts rows, not pages: 151 rows at 20 a page is 8, the same as the site's own pager. */
    @Test
    fun `turns the row total into a page count`() =
        runTest {
            val page =
                NetworkCreditRepository(FakeCreditPageJsonSource(bodyWith(total = 151)), dispatchers).page(1)

            assertEquals(8, page.totalPages)
            assertTrue(page.hasNextPage)
        }

    @Test
    fun `stops at the last page the total implies`() =
        runTest {
            val page =
                NetworkCreditRepository(FakeCreditPageJsonSource(bodyWith(total = 21)), dispatchers).page(2)

            assertEquals(2, page.totalPages)
            assertFalse(page.hasNextPage)
        }

    /** The site's own pager stops at 100, so asking for 101 buys nothing and is clamped. */
    @Test
    fun `clamps the page number to the site's own ceiling`() =
        runTest {
            val source = FakeCreditPageJsonSource(bodyWith(total = 100_000))

            val page = NetworkCreditRepository(source, dispatchers).page(400)

            assertEquals(NodeSeekJsonClient.creditLedgerPath(100), source.requestedPath)
            assertEquals(100, page.page)
            assertFalse(page.hasNextPage)
        }

    /** Without `total` the only end-of-list signal is a short page, so a full one keeps going. */
    @Test
    fun `falls back to page fullness when the total is missing`() =
        runTest {
            val full = List(NodeSeekJsonClient.CREDIT_PAGE_SIZE) { """[1,${300 + it},"回帖奖励",null]""" }
            val page =
                NetworkCreditRepository(
                    FakeCreditPageJsonSource("""{"success":true,"data":[${full.joinToString(",")}]}"""),
                    dispatchers,
                ).page(1)

            assertNull(page.totalPages)
            assertTrue(page.hasNextPage)
        }

    @Test
    fun `reports an unreadable payload rather than an empty ledger`() =
        runTest {
            val exception =
                runCatching {
                    NetworkCreditRepository(FakeCreditPageJsonSource("""{"success":false}"""), dispatchers).page(1)
                }.exceptionOrNull()

            assertEquals(NodeSeekError.Unparsable, (exception as? NodeSeekException)?.error)
        }

    /** A row missing the change or the reason is dropped; the rest of the page still renders. */
    @Test
    fun `drops rows it cannot read without failing the page`() =
        runTest {
            val page =
                NetworkCreditRepository(
                    FakeCreditPageJsonSource(
                        """{"success":true,"data":[[null,1,"回帖奖励",null],[2,3,null,null],[5,9,"发帖奖励",null]],"total":3}""",
                    ),
                    dispatchers,
                ).page(1)

            assertEquals(listOf("发帖奖励"), page.entries.map(CreditEntry::reason))
        }

    private fun bodyWith(total: Int) =
        """{"success":true,"data":[[1,384,"回帖奖励","2026-07-30T12:28:11.000Z"]],"total":$total}"""
}

private class FakeCreditPageJsonSource(
    private val body: String,
) : JsonSource {
    var requestedPath: String? = null
        private set

    override suspend fun getJson(
        path: String,
        referer: String,
    ): String {
        requestedPath = path
        return body
    }

    override suspend fun postJson(
        path: String,
        referer: String,
    ): JsonPostResponse = error("The ledger is read-only")
}
