package io.github.nodyssey.core

import io.github.plaza.core.richtext.RichNode
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.random.Random

/**
 * How a 星辰收款码 is written into, and read back out of, a post body.
 *
 * Read off the site's own `stardustWrapper` bundle, which both writes the marker
 * (`insertIntoMdEditor`) and mounts the card from it. The written form is a bare URL surrounded by
 * spaces — NodeSeek's Markdown renderer auto-links it, and its script then recognises the resulting
 * `data-href` and swaps in the card, exactly as it does for [VoteMarkup].
 *
 * Both directions live here because they are one contract with two halves: a change to the key
 * order, the encoding or the accepted values has to happen to both at once or the app stops
 * recognising what it just wrote.
 */
object StardustReceiveMarkup {
    /** The site's own marker kind, the `stardust-receive` in `nsapp://stardust-receive?…`. */
    const val KIND = "stardust-receive"

    /**
     * What the site's generator puts in the 备注 box before anyone types: `"Pay with Stardust"`.
     *
     * English in a Chinese forum, which looks like an oversight but is what every code generated on
     * the web carries, so a code made here reads the same as one made there.
     */
    const val DEFAULT_DESCRIPTION = "Pay with Stardust"

    /**
     * A Ref ID the way the site's generator seeds one: `100 + Math.floor(1e8 * Math.random())`.
     *
     * The number is the payee's own reference and nothing on the server checks it, which is why the
     * site can pick one at random and why it stays editable. Seeding it matters more than it looks:
     * the tally is keyed on `member_id + ref_id`, so two codes that share a number share their
     * payment counts — and an empty box invites everybody to type `1`.
     *
     * The 100 floor is the site's and worth keeping for a reason it probably did not intend: every
     * `upvote` row in the ledger carries `ref_id` 10, so a low number is exactly where a code's
     * count would collide with likes.
     */
    fun randomRefId(): Long = 100L + Random.nextInt(RANDOM_REF_ID_RANGE)

    /**
     * The line to splice into the body.
     *
     * Key order and the surrounding spaces are the site's, kept so a marker written here is
     * byte-identical to one written on the web. [memberId] is always the author's own uid: a receive
     * code collects for whoever posted it, and the site's editor has no field for anyone else.
     */
    fun marker(
        memberId: Long,
        refId: Long,
        amount: Int,
        description: String,
        onetime: Boolean,
    ): String =
        " nsapp://$KIND?" +
            "member_id=$memberId" +
            "&ref_id=$refId" +
            "&description=${encodeUriComponent(description)}" +
            "&diff=$amount" +
            "&onetime=$onetime "

    /**
     * Reads the query of a `nsapp://stardust-receive?…` marker, or null when it is not one we may
     * draw.
     *
     * [query] is everything after the `?`. Null is not an error path — it is the site's own
     * behaviour, and matching it is the point: `stardustWrapper` requires `member_id`, `ref_id` and
     * `diff` to each be a bare run of digits and abandons the whole marker otherwise, leaving it on
     * screen as an ordinary link. Anything looser here would draw a card where the web shows text.
     */
    fun parse(query: String): RichNode.StardustReceive? {
        val fields = queryFields(query)
        val memberId = fields["member_id"]?.digits()?.toLongOrNull() ?: return null
        val refId = fields["ref_id"]?.digits()?.toLongOrNull() ?: return null
        val amount = fields["diff"]?.digits()?.toIntOrNull() ?: return null
        return RichNode.StardustReceive(
            memberId = memberId,
            refId = refId,
            amount = amount,
            // The site decodes this twice — `URLSearchParams` already did once. That extra pass is a
            // bug of theirs that only bites on a literal `%`, and decoding once is what actually
            // inverts their own encoder, so we do that.
            description = fields["description"].orEmpty(),
            onetime = fields["onetime"] == "true",
        )
    }

    /** Itself when it is nothing but digits, null otherwise — the site's `/^\d+$/`. */
    private fun String.digits(): String? = takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

    /**
     * The query split into decoded pairs, the way `URLSearchParams` would.
     *
     * Which includes reading `+` as a space: our own [marker] never writes one (a space is `%20`),
     * but a marker typed by hand can carry it, and the web would show that as a space.
     */
    private fun queryFields(query: String): Map<String, String> =
        query.split('&')
            .mapNotNull { pair ->
                if (pair.isEmpty()) return@mapNotNull null
                val separator = pair.indexOf('=')
                val rawKey = if (separator < 0) pair else pair.substring(0, separator)
                val rawValue = if (separator < 0) "" else pair.substring(separator + 1)
                decode(rawKey) to decode(rawValue)
            }.toMap()

    /**
     * Percent-decoding that survives a malformed marker.
     *
     * A stray `%` makes the site throw and lose the card altogether; keeping the raw text instead
     * costs nothing and leaves a hand-written marker readable.
     */
    private fun decode(value: String): String =
        try {
            // The `String` charset overload, not the `Charset` one: that is API 33 and this app runs
            // from 26.
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        } catch (exception: IllegalArgumentException) {
            value
        }

    /**
     * JavaScript's `encodeURIComponent`, which is what the site's editor applies to every value.
     *
     * Not `URLEncoder`: that writes a space as `+`, and the site reads the description back through
     * `decodeURIComponent`, which leaves a `+` alone. One space in a 备注 would reach every reader on
     * the web as a plus sign. The unreserved set below is the one the spec names.
     */
    private fun encodeUriComponent(value: String): String =
        buildString {
            for (byte in value.toByteArray(StandardCharsets.UTF_8)) {
                val octet = byte.toInt() and 0xFF
                val char = octet.toChar()
                if (octet < 0x80 && (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in UNRESERVED)) {
                    append(char)
                } else {
                    append('%')
                    append(HEX[octet shr 4])
                    append(HEX[octet and 0xF])
                }
            }
        }

    private const val UNRESERVED = "-_.!~*'()"
    private const val HEX = "0123456789ABCDEF"

    /** `1e8` in the site's expression. */
    private const val RANDOM_REF_ID_RANGE = 100_000_000
}
