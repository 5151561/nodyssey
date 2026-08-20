package io.github.nodyssey.data

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.TimeFormat
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * One row of 鸡腿流水, which is `/credit`'s four columns with nothing dropped.
 *
 * [reason] is the site's own sentence and is deliberately not parsed into a category: the four
 * documented wordings are already five in practice (`发帖奖励` was never written down), and a row
 * whose reason we failed to classify would be worse than one that simply quotes the site.
 *
 * [createdAtMillis] stays an instant rather than a formatted string because two callers want two
 * different zones — the list renders in the reader's, while the attendance check has to ask about the
 * site's own calendar day in Asia/Shanghai.
 */
data class CreditEntry(
    val change: Int,
    val balanceAfter: Int?,
    val reason: String,
    val createdAtMillis: Long?,
)

data class CreditLedgerPage(
    val entries: List<CreditEntry>,
    val page: Int,
    /** Null when the payload omitted `total`; then only [hasNextPage] can be trusted. */
    val totalPages: Int?,
    val hasNextPage: Boolean,
)

interface CreditRepository {
    /** The signed-in account's ledger. The site publishes no one else's, not even to admins. */
    suspend fun page(page: Int = 1): CreditLedgerPage
}

/**
 * The one real JSON ledger NodeSeek publishes.
 *
 * Unlike the stardust list this endpoint answers with positional arrays rather than objects, so there
 * is no field name to read by candidate — a column that moves would be silent. It has not moved since
 * 2026-07, and the alternative (scraping the client-rendered `/credit` table) cannot be done at all.
 */
class NetworkCreditRepository(
    private val jsonSource: JsonSource,
    private val dispatchers: AppDispatchers,
) : CreditRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun page(page: Int): CreditLedgerPage {
        val safePage = page.coerceIn(1, NodeSeekJsonClient.CREDIT_MAX_PAGES)
        val body =
            jsonSource.getJson(
                path = NodeSeekJsonClient.creditLedgerPath(safePage),
                referer = NodeSeekSite.BASE_URL + NodeSeekSite.CREDIT_PATH,
            )
        return withContext(dispatchers.default) {
            val root =
                runCatching { json.parseToJsonElement(body) as? JsonObject }
                    .getOrElse { throw SiteException(SiteError.Unparsable, it) }
                    ?: throw SiteException(SiteError.Unparsable)
            val rows = root["data"] as? JsonArray ?: throw SiteException(SiteError.Unparsable)
            val entries =
                rows.mapNotNull { element ->
                    val row = element as? JsonArray ?: return@mapNotNull null
                    CreditEntry(
                        change = row.getOrNull(0).intValue() ?: return@mapNotNull null,
                        balanceAfter = row.getOrNull(1).intValue(),
                        reason = row.getOrNull(2).textValue() ?: return@mapNotNull null,
                        createdAtMillis = TimeFormat.parseTimestamp(row.getOrNull(3).textValue()),
                    )
                }
            val totalPages = root.int("total")?.let(::pagesFor)
            CreditLedgerPage(
                entries = entries,
                page = safePage,
                totalPages = totalPages,
                // Without `total` a short page is the only end-of-list signal there is; a full one
                // is assumed to have a successor, and the next request returning nothing settles it.
                hasNextPage =
                when {
                    safePage >= NodeSeekJsonClient.CREDIT_MAX_PAGES -> false
                    totalPages != null -> safePage < totalPages
                    else -> entries.size >= NodeSeekJsonClient.CREDIT_PAGE_SIZE
                },
            )
        }
    }

    private companion object {
        /** `total` counts rows, not pages — the same arithmetic the site's own pager does. */
        fun pagesFor(totalRows: Int): Int {
            if (totalRows <= 0) return 1
            val pages = (totalRows + NodeSeekJsonClient.CREDIT_PAGE_SIZE - 1) / NodeSeekJsonClient.CREDIT_PAGE_SIZE
            return pages.coerceIn(1, NodeSeekJsonClient.CREDIT_MAX_PAGES)
        }
    }
}

/**
 * A ledger row's numbers arrive signed and, on the change column, sometimes as `"+1"`.
 *
 * Kept private to this file rather than folded into [JsonFields] because those readers take a field
 * name and this endpoint has none — every value is read by position.
 */
private fun kotlinx.serialization.json.JsonElement?.intValue(): Int? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.intOrNull ?: primitive.contentOrNull?.removePrefix("+")?.toIntOrNull()
}

private fun kotlinx.serialization.json.JsonElement?.textValue(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
