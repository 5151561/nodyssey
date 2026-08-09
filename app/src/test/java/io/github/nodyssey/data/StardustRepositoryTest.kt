package io.github.nodyssey.data

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins `/api/stardust/list` as read out of the site's own `stardustList` bundle and then verified
 * against a live signed-in account on 2026-07-30.
 *
 * Two things about the read are worth breaking a build over. The first is that a movement can be
 * **negative** and comes in **five kinds** — the app previously shipped a screen that hardcoded
 * "点赞 +1" for every row, which would have misreported every transfer out. The second is the parameter
 * whitelist: the server answers an unexpected query parameter with HTTP 422 rather than ignoring it,
 * so the path builder cannot be allowed to drift.
 *
 * The two writes are pinned harder still, because they are the only calls in the app with no undo:
 * which uid goes in the body, and that a `success:false` answered with HTTP 200 is a refusal rather
 * than a completed transfer.
 */
class StardustRepositoryTest {
    private val dispatchers =
        AppDispatchers(io = Dispatchers.Unconfined, default = Dispatchers.Unconfined)

    @Test
    fun `reads a record and requests the first page without a cursor`() =
        runTest {
            val source =
                FakeStardustJsonSource(
                    """
                    {"success":true,
                     "records":[{"id":187103,"member_id":52425,"peer_id":9667,"type":"upvote",
                                 "diff":1,"result":6,"ref_id":10,
                                 "created_at":"2026-07-26T11:21:41.000Z","comment_id":11491930}],
                     "exist_more":true,"cursor":187103}
                    """.trimIndent(),
                )

            val page = NetworkStardustRepository(source, dispatchers).entries(memberId = 52_425)

            assertEquals(NodeSeekJsonClient.stardustListPath(52_425), source.requestedPath)
            val entry = page.entries.single()
            assertEquals(187_103L, entry.id)
            assertEquals(StardustType.UPVOTE, entry.type)
            assertEquals(1, entry.diff)
            assertEquals(6, entry.balanceAfter)
            assertEquals(9_667L, entry.peerUid)
            assertEquals(11_491_930L, entry.commentId)
            assertEquals(10L, entry.refId)
            // 2026-07-26T11:21:41Z, kept as an instant so the row can be formatted in the reader's zone.
            assertEquals(1_785_064_901_000L, entry.createdAtMillis)
            assertEquals(187_103L, page.cursor)
            assertTrue(page.hasMore)
        }

    @Test
    fun `sends the cursor as before_id on later pages`() =
        runTest {
            val source = FakeStardustJsonSource("""{"success":true,"records":[],"exist_more":false}""")

            NetworkStardustRepository(source, dispatchers).entries(memberId = 52_425, beforeId = 185_971)

            assertEquals(
                NodeSeekJsonClient.stardustListPath(52_425, beforeId = 185_971),
                source.requestedPath,
            )
            assertTrue(source.requestedPath!!.contains("before_id=185971"))
        }

    /** Only these names are accepted; anything else is a 422, so the builder emits nothing else. */
    @Test
    fun `builds a path out of whitelisted parameters only`() {
        val path = NodeSeekJsonClient.stardustListPath(52_425, beforeId = 99, count = 20)

        assertEquals("/api/stardust/list?member_id=52425&count=20&before_id=99", path)
    }

    @Test
    fun `keeps every kind the site labels, signed`() =
        runTest {
            val source =
                FakeStardustJsonSource(
                    """
                    {"success":true,"records":[
                      {"id":5,"type":"transfer","diff":-2,"result":3,"peer_id":4471,"ref_id":108},
                      {"id":4,"type":"buyCode","diff":-1,"result":4},
                      {"id":3,"type":"system","diff":3,"result":5},
                      {"id":2,"type":"admin","diff":1,"result":2},
                      {"id":1,"type":"lottery","diff":1,"result":1}],
                     "exist_more":false,"cursor":1}
                    """.trimIndent(),
                )

            val entries = NetworkStardustRepository(source, dispatchers).entries(52_425).entries

            assertEquals(
                listOf(
                    StardustType.TRANSFER,
                    StardustType.BUY_CODE,
                    StardustType.SYSTEM,
                    StardustType.ADMIN,
                    StardustType.UNKNOWN,
                ),
                entries.map(StardustEntry::type),
            )
            assertEquals(listOf(-2, -1, 3, 1, 1), entries.map(StardustEntry::diff))
            // A kind we have never seen keeps the site's own word so the row can still say something.
            assertEquals("lottery", entries.last().rawType)
        }

    /** The id is the cursor, so a row without one would make the next request repeat this page. */
    @Test
    fun `drops rows with no id`() =
        runTest {
            val page =
                NetworkStardustRepository(
                    FakeStardustJsonSource(
                        """{"success":true,"records":[{"type":"upvote","diff":1},{"id":7,"type":"upvote","diff":1}],"exist_more":false}""",
                    ),
                    dispatchers,
                ).entries(52_425)

            assertEquals(listOf(7L), page.entries.map(StardustEntry::id))
        }

    /** `exist_more:false` is authoritative even on a full page, and stops paging. */
    @Test
    fun `trusts exist_more over page fullness`() =
        runTest {
            val rows = List(NodeSeekJsonClient.STARDUST_PAGE_SIZE) { """{"id":${100 - it},"type":"upvote","diff":1}""" }
            val page =
                NetworkStardustRepository(
                    FakeStardustJsonSource(
                        """{"success":true,"records":[${rows.joinToString(",")}],"exist_more":false,"cursor":81}""",
                    ),
                    dispatchers,
                ).entries(52_425)

            assertFalse(page.hasMore)
        }

    /** Without the flag a full page is assumed to continue: stopping early would hide rows. */
    @Test
    fun `falls back to page fullness when exist_more is missing`() =
        runTest {
            val rows = List(NodeSeekJsonClient.STARDUST_PAGE_SIZE) { """{"id":${100 - it},"type":"upvote","diff":1}""" }
            val page =
                NetworkStardustRepository(
                    FakeStardustJsonSource("""{"success":true,"records":[${rows.joinToString(",")}]}"""),
                    dispatchers,
                ).entries(52_425)

            assertTrue(page.hasMore)
            // No `cursor` field either, so the last row's id stands in for it.
            assertEquals(81L, page.cursor)
        }

    @Test
    fun `reports an unreadable payload rather than an empty ledger`() =
        runTest {
            val exception =
                runCatching {
                    NetworkStardustRepository(
                        FakeStardustJsonSource("""{"success":false,"message":"USER NOT FOUND"}"""),
                        dispatchers,
                    ).entries(1)
                }.exceptionOrNull()

            assertEquals(SiteError.Unparsable, (exception as? SiteException)?.error)
        }

    @Test
    fun `reads an empty ledger as empty rather than as a failure`() =
        runTest {
            val page =
                NetworkStardustRepository(
                    FakeStardustJsonSource("""{"success":true,"records":[],"exist_more":false}"""),
                    dispatchers,
                ).entries(52_425)

            assertTrue(page.entries.isEmpty())
            assertFalse(page.hasMore)
            assertNull(page.cursor)
        }

    @Test
    fun `asks payment-prepare who a recipient uid is`() =
        runTest {
            val api = FakeStardustJsonSource("""{"success":true,"receiver_name":"站长"}""")

            val name =
                NetworkStardustRepository(api, dispatchers)
                    .recipientName(recipientUid = 9, viewerUid = 52_425)

            assertEquals("站长", name)
            assertEquals("/api/stardust/payment-prepare", api.postedPath)
            assertEquals("""{"receiver_id":9,"origin":"https://www.nodeseek.com"}""", api.postedBody)
            // The lookup runs from the sender's own ledger page, which is where the site's layer opens.
            assertEquals(NodeSeekSite.BASE_URL + "/stardust/list?member_id=52425", api.postedReferer)
        }

    /** Answered, but with nobody named. Distinct from a refusal, and it must not read as an error. */
    @Test
    fun `reports a nameless prepare answer as no name rather than as a failure`() =
        runTest {
            val name =
                NetworkStardustRepository(
                    FakeStardustJsonSource("""{"success":true}"""),
                    dispatchers,
                ).recipientName(recipientUid = 9, viewerUid = 52_425)

            assertNull(name)
        }

    @Test
    fun `carries the site's own sentence when a recipient lookup is refused`() =
        runTest {
            val exception =
                runCatching {
                    NetworkStardustRepository(
                        FakeStardustJsonSource("""{"success":false,"message":"用户不存在"}"""),
                        dispatchers,
                    ).recipientName(recipientUid = 1, viewerUid = 52_425)
                }.exceptionOrNull()

            assertEquals("用户不存在", (exception as? SiteException)?.detail)
        }

    /**
     * `member_id` is the **receiver**, not the sender.
     *
     * The sender is whoever the cookie says. Reading this field the other way round would send the
     * stardust to the person who pressed the button, which the server would happily accept.
     */
    @Test
    fun `sends by posting the receiver, the amount and the ref`() =
        runTest {
            val api = FakeStardustJsonSource("""{"success":true,"message":"转账成功"}""")

            NetworkStardustRepository(api, dispatchers)
                .send(recipientUid = 9, amount = 2, refId = 866_042, viewerUid = 52_425)

            assertEquals("/api/stardust/send", api.postedPath)
            assertEquals("""{"member_id":9,"diff":2,"ref_id":866042,"onetime":false}""", api.postedBody)
        }

    /**
     * 收款码 payments carry the code's own `onetime`, which is what lets the server refuse a second one.
     *
     * The flag belongs to the marker in the post, not to the payer's screen, so it has to survive the
     * whole way from the parsed body to this body — dropping it silently turns a one-off code into
     * one anybody can pay twice.
     */
    @Test
    fun `passes the receive code's onetime flag through to the send`() =
        runTest {
            val api = FakeStardustJsonSource("""{"success":true}""")

            NetworkStardustRepository(api, dispatchers)
                .send(recipientUid = 9, amount = 2, refId = 100, viewerUid = 52_425, onetime = true)

            assertEquals("""{"member_id":9,"diff":2,"ref_id":100,"onetime":true}""", api.postedBody)
        }

    /**
     * A card's tally asks the ledger for one payee's one `ref_id`, and nothing else narrows it.
     *
     * Not `type=transfer`, though the server would accept it: the site's own card does not filter,
     * and matching its arithmetic matters more here than being right about it. See
     * [NodeSeekJsonClient.stardustReceiptsPath].
     */
    @Test
    fun `reads one receive code's payments off the ledger`() =
        runTest {
            val api =
                FakeStardustJsonSource(
                    """{"success":true,"records":[
                       {"id":1,"member_id":52425,"peer_id":9,"type":"transfer","diff":2,"ref_id":100},
                       {"id":2,"member_id":52425,"peer_id":11,"type":"transfer","diff":3,"ref_id":100}
                    ]}""",
                )

            val rows = NetworkStardustRepository(api, dispatchers).receipts(memberId = 52_425, refId = 100)

            assertEquals(
                "/api/stardust/list?member_id=52425&ref_id=100&count=100",
                api.requestedPath,
            )
            assertEquals(listOf(2, 3), rows.map { it.diff })
        }

    /** The "have I paid this one?" question is the same read with the payer pinned. */
    @Test
    fun `narrows a receive code's payments to one payer`() =
        runTest {
            val api = FakeStardustJsonSource("""{"success":true,"records":[]}""")

            NetworkStardustRepository(api, dispatchers)
                .receipts(memberId = 52_425, refId = 100, peerId = 9)

            assertEquals(
                "/api/stardust/list?member_id=52425&ref_id=100&count=100&peer_id=9",
                api.requestedPath,
            )
        }

    /**
     * A refusal arrives as a 200 with `success:false`.
     *
     * Status alone would report "余额不足" as a completed transfer — the one mistake on this screen
     * that cannot be walked back by looking again.
     */
    @Test
    fun `treats a success false answer to a send as a refusal`() =
        runTest {
            val exception =
                runCatching {
                    NetworkStardustRepository(
                        FakeStardustJsonSource("""{"success":false,"message":"余额不足"}"""),
                        dispatchers,
                    ).send(recipientUid = 9, amount = 999, refId = 1, viewerUid = 52_425)
                }.exceptionOrNull()

            assertEquals("余额不足", (exception as? SiteException)?.detail)
        }
}

private class FakeStardustJsonSource(
    private val body: String,
) : JsonApi {
    var requestedPath: String? = null
        private set
    var postedPath: String? = null
        private set
    var postedBody: String? = null
        private set
    var postedReferer: String? = null
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
        body: String,
        referer: String,
    ): String {
        postedPath = path
        postedBody = body
        postedReferer = referer
        return this.body
    }
}
