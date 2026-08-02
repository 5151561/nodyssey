package io.github.nodyssey.data

import io.github.nodyssey.core.AppDispatchers
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins `/api/admin/ruling/page-N` as read out of the site's own `ruling` bundle on 2026-08-02.
 *
 * Three things here are worth breaking a build over.
 *
 * The first is that a row's decision is **a JSON document inside a JSON string**. `request` is not a
 * description of the action, it is the admin call that was made, re-serialised; reading it as text
 * would put `{"coin":{"coin_diff":-10}}` on screen.
 *
 * The second is the **order** of the verbs. The site emits coin, then what happened to the post, then
 * what happened to the account, and that order is how a compound decision reads as one sentence.
 *
 * The third is the **100-page cap**, which is the server's and not a display choice: the log is
 * thousands of pages deep and page 101 is refused.
 *
 * The payloads below reproduce shapes seen live; the names, reasons and ids are substituted.
 */
class RulingRepositoryTest {
    private val dispatchers =
        AppDispatchers(io = Dispatchers.Unconfined, default = Dispatchers.Unconfined)

    private fun repository(source: FakeRulingSource) = NetworkRulingRepository(source, dispatchers)

    /** The web route pages by hash; the endpoint pages by path segment. Both have to be right. */
    @Test
    fun `reads a page off the admin ruling endpoint`() =
        runTest {
            val source = FakeRulingSource(page(rows = listOf(penaltyRow), total = 30_212))

            val result = repository(source).records(page = 7)

            assertEquals("/api/admin/ruling/page-7", source.requestedPath)
            assertEquals(NodeSeekSite.BASE_URL + "/ruling#/p-7", source.requestedReferer)
            assertEquals(7, result.page)
        }

    @Test
    fun `unpacks the decision out of the request string, in the site's order`() =
        runTest {
            val record = repository(FakeRulingSource(page(listOf(penaltyRow)))).single()

            assertEquals(
                listOf(
                    RulingAction.Coin(-10),
                    RulingAction.Move("inside"),
                    RulingAction.ReadRank(255),
                    RulingAction.Lock(true),
                    RulingAction.Suspend(3),
                ),
                record.actions,
            )
            // The reason travels alone: the row prints it once rather than inside every verb.
            assertEquals("流量拼车需发内版", record.reason)
            assertEquals("阿闯", record.targetName)
            assertEquals("xe", record.moderatorName)
        }

    /** `status:false` is the lifting of a silencing, which reads nothing like a three-day one. */
    @Test
    fun `reads a lifted suspension as a suspension with no days`() =
        runTest {
            val row = row(request = """{\"suspend\":{\"status\":false,\"value\":3}}""")

            val record = repository(FakeRulingSource(page(listOf(row)))).single()

            assertEquals(listOf(RulingAction.Suspend(null)), record.actions)
            assertEquals(RulingKind.PENALTY, record.kind)
        }

    @Test
    fun `tells a post, a comment and an account apart`() =
        runTest {
            val rows =
                listOf(
                    row(id = 3, postId = 852_128, floorIndex = 0),
                    row(id = 2, postId = 853_221, floorIndex = 6),
                    // The account-level form: the payload addresses `target.uid` and sends no post.
                    row(
                        id = 1,
                        postId = -1,
                        floorIndex = -1,
                        request = """{\"target\":{\"uid\":39158},\"suspend\":{\"status\":true,\"value\":1000}}""",
                    ),
                )

            val records = repository(FakeRulingSource(page(rows))).records().records

            assertEquals(
                listOf(RulingTarget.POST, RulingTarget.COMMENT, RulingTarget.USER),
                records.map(RulingRecord::target),
            )
            assertEquals(listOf(852_128L, 853_221L, null), records.map(RulingRecord::postId))
            // The opening post is floor 0, which is not a floor to jump to.
            assertEquals(listOf(null, 6, null), records.map(RulingRecord::floor))
        }

    /** `hideComment` on an account-level row is 隐藏全部内容, and the site reads that off the target. */
    @Test
    fun `reads the account-wide hide off the target rather than the action`() =
        runTest {
            val whole =
                row(
                    postId = -1,
                    request = """{\"target\":{\"uid\":7},\"hideComment\":{\"status\":true}}""",
                )
            val single = row(request = """{\"target\":{\"id\":11}, \"hideComment\":{\"status\":true}}""")

            val records = repository(FakeRulingSource(page(listOf(whole, single)))).records().records

            assertEquals(RulingAction.Hide(hidden = true, wholeUser = true), records[0].actions.single())
            assertEquals(RulingAction.Hide(hidden = true, wholeUser = false), records[1].actions.single())
        }

    @Test
    fun `pages by the total the payload carries, twenty rows at a time`() =
        runTest {
            val result = repository(FakeRulingSource(page(listOf(penaltyRow), total = 41))).records()

            assertEquals(3, result.totalPages)
        }

    /**
     * The cap is the server's. Page 101 answers `{"success":false,"message":"max page is 100"}` with
     * an HTTP 200, so a pager that offered 1 511 pages would be offering 1 411 error screens.
     */
    @Test
    fun `stops the page count at the hundred the site serves`() =
        runTest {
            val result = repository(FakeRulingSource(page(listOf(penaltyRow), total = 30_212))).records()

            assertEquals(100, result.totalPages)
        }

    @Test
    fun `clamps a page past the cap before asking for it`() =
        runTest {
            val source = FakeRulingSource(page(listOf(penaltyRow)))

            val result = repository(source).records(page = 400)

            assertEquals("/api/admin/ruling/page-100", source.requestedPath)
            assertEquals(100, result.page)
        }

    /**
     * A decision we cannot read still happened.
     *
     * Dropping the row would take a moderator, a member and a timestamp out of a log whose whole
     * purpose is that nothing leaves it; the row renders with no verbs instead.
     */
    @Test
    fun `keeps a row whose request will not parse`() =
        runTest {
            val rows = listOf(row(id = 2, request = "not json at all"), penaltyRow)

            val records = repository(FakeRulingSource(page(rows))).records().records

            assertEquals(2, records.size)
            assertTrue(records.first().actions.isEmpty())
            assertNull(records.first().reason)
        }

    /** No id, no stable list key — and unlike the case above, nothing is lost by leaving it out. */
    @Test
    fun `drops a row with no id`() =
        runTest {
            val rows = listOf("""{"target_member_name":"a","admin_member_name":"b"}""", penaltyRow)

            assertEquals(1, repository(FakeRulingSource(page(rows))).records().records.size)
        }

    /** Missing `data` means the shape moved; an empty log would be the wrong thing to report. */
    @Test
    fun `reports a payload with no rows as unparsable`() =
        runTest {
            val exception =
                runCatching {
                    repository(FakeRulingSource("""{"success":true,"somethingElse":{"a":1}}""")).records()
                }.exceptionOrNull()

            assertEquals(NodeSeekError.Unparsable, (exception as? NodeSeekException)?.error)
        }

    @Test
    fun `carries the site's own sentence when a read is refused`() =
        runTest {
            val exception =
                runCatching {
                    repository(FakeRulingSource("""{"success":false,"message":"max page is 100"}""")).records()
                }.exceptionOrNull()

            assertEquals("max page is 100", (exception as? NodeSeekException)?.detail)
        }

    /** The icon is all a compound decision gets, so a silencing has to outrank the 鸡腿 beside it. */
    @Test
    fun `leads a compound decision with its heaviest verb`() =
        runTest {
            val rows =
                listOf(
                    row(id = 4, request = """{\"coin\":{\"coin_diff\":-10},\"suspend\":{\"status\":true,\"value\":1}}"""),
                    row(id = 3, request = """{\"coin\":{\"coin_diff\":50,\"reason\":\"鼓励优质文章\"}}"""),
                    row(id = 2, request = """{\"postSummary\":{\"category\":\"trade\",\"locked\":true}}"""),
                    row(id = 1, request = """{\"postSummary\":{\"rank\":1}}"""),
                )

            val records = repository(FakeRulingSource(page(rows))).records().records

            assertEquals(
                listOf(RulingKind.BAN, RulingKind.REWARD, RulingKind.MOVE, RulingKind.PERMISSION),
                records.map(RulingRecord::kind),
            )
        }

    @Test
    fun `keeps the sign on a rewarded balance`() =
        runTest {
            val row = row(request = """{\"stardust\":{\"stardust_diff\":200}}""")

            val record = repository(FakeRulingSource(page(listOf(row)))).single()

            assertEquals(listOf(RulingAction.Stardust(200)), record.actions)
            assertEquals(RulingKind.REWARD, record.kind)
        }
}

private suspend fun RulingRepository.records(): RulingPage = records(1)

private suspend fun RulingRepository.single(): RulingRecord = records(1).records.single()

/** The compound shape the log is mostly made of: a deduction, a move, a permission and a silencing. */
private val penaltyRow =
    row(
        request =
        """{\"target\":{\"id\":11638605},\"coin\":{\"coin_diff\":-10,\"reason\":\"流量拼车需发内版\"},""" +
            """\"suspend\":{\"status\":true,\"value\":3},\"postSummary\":{\"rank\":255,""" +
            """\"category\":\"inside\",\"locked\":true}}""",
    )

private fun row(
    id: Long = 30_204,
    postId: Long = 853_140,
    floorIndex: Int = 0,
    request: String = """{\"target\":{\"id\":1}}""",
): String =
    """
    {"id":$id,"request":"$request","admin_member_id":26519,"admin_member_name":"xe",
     "target_member_id":27225,"target_member_name":"阿闯","post_id":$postId,"comment_id":11638605,
     "floor_index":$floorIndex,"created_at":"2026-08-02T02:18:48.000Z"}
    """.trimIndent()

private fun page(rows: List<String>, total: Long = 20): String =
    """{"success":true,"data":[${rows.joinToString(",")}],"total":$total}"""

private class FakeRulingSource(
    private val body: String,
) : JsonSource {
    var requestedPath: String? = null
        private set
    var requestedReferer: String? = null
        private set

    override suspend fun getJson(
        path: String,
        referer: String,
    ): String {
        requestedPath = path
        requestedReferer = referer
        return body
    }
}
