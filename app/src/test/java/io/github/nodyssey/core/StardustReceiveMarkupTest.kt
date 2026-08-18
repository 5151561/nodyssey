package io.github.nodyssey.core

import io.github.plaza.core.richtext.RichNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the 收款码 marker against the site's own `stardustWrapper` bundle, read on 2026-08-09.
 *
 * Both halves are here because they are one contract. A marker this app writes is read by the web,
 * and one the web writes is read here; an encoder and a reader that agree only with each other would
 * pass every test and still show a different amount on the two screens.
 */
class StardustReceiveMarkupTest {
    /**
     * The site's exact output for `{member_id:52425, ref_id:100, description:"请我喝杯咖啡", diff:2}`.
     *
     * Key order included: it is not load-bearing for any reader, but a marker byte-identical to the
     * site's own is the cheapest way to stay sure that it is the same object.
     */
    @Test
    fun `writes the marker the way the site's editor does`() {
        val marker =
            StardustReceiveMarkup.marker(
                memberId = 52425,
                refId = 100,
                amount = 2,
                description = "请我喝杯咖啡",
                onetime = true,
            )

        assertEquals(
            " nsapp://stardust-receive?member_id=52425&ref_id=100" +
                "&description=%E8%AF%B7%E6%88%91%E5%96%9D%E6%9D%AF%E5%92%96%E5%95%A1" +
                "&diff=2&onetime=true ",
            marker,
        )
    }

    /**
     * Byte-for-byte against the site's own encoder, run on 2026-08-09 in a signed-in browser.
     *
     * Each expectation is the output of `insertIntoMdEditor`'s own expression —
     * `Object.entries({…}).map(([k, v]) => k + "=" + encodeURIComponent(v)).join("&")` — for the same
     * inputs. The three interesting characters are all here: a space must be `%20` and never `+`
     * (`URLEncoder` writes `+`, and `decodeURIComponent` leaves a `+` alone, so one space in a 备注
     * would reach every reader on the web as a plus sign); an `&` must be `%26` or it ends the field;
     * and a `%` must be `%25` or the marker no longer decodes.
     */
    @Test
    fun `matches the site's own encoder character for character`() {
        val cases =
            listOf(
                Triple("a b", 1 to 1L, "member_id=1&ref_id=1&description=a%20b&diff=1&onetime=false"),
                Triple(
                    "买 A&B 套餐 100%",
                    4 to 3L,
                    "member_id=7&ref_id=3&description=%E4%B9%B0%20A%26B%20%E5%A5%97%E9%A4%90%20100%25" +
                        "&diff=4&onetime=false",
                ),
            )

        cases.forEach { (description, amountAndRef, expected) ->
            val (amount, refId) = amountAndRef
            val marker =
                StardustReceiveMarkup.marker(
                    memberId = if (refId == 1L) 1 else 7,
                    refId = refId,
                    amount = amount,
                    description = description,
                    onetime = false,
                )

            assertEquals(" nsapp://stardust-receive?$expected ", marker)
            // The site's regex ends the marker at the first whitespace, so nothing inside it may be one.
            assertTrue(marker, marker.trim().none(Char::isWhitespace))
        }
    }

    @Test
    fun `reads back everything it wrote`() {
        val marker =
            StardustReceiveMarkup.marker(
                memberId = 52425,
                refId = 866042,
                amount = 10,
                description = "拼车续费 第 3 期",
                onetime = true,
            )

        assertEquals(
            RichNode.StardustReceive(
                memberId = 52425,
                refId = 866042,
                amount = 10,
                description = "拼车续费 第 3 期",
                onetime = true,
            ),
            StardustReceiveMarkup.parse(marker.substringAfter('?').trim()),
        )
    }

    /**
     * The seeded Ref ID stays in the site's own range, `100 + [0, 1e8)`.
     *
     * The floor matters twice over. It is the site's, and it also keeps a fresh code clear of
     * `ref_id` 10 — the number every `upvote` row in the ledger carries, and therefore the one
     * value where a code's payment count would silently include other people's likes.
     */
    @Test
    fun `seeds a ref id in the range the site's generator uses`() {
        repeat(200) {
            val refId = StardustReceiveMarkup.randomRefId()
            assertTrue("got $refId", refId in 100L until 100L + 100_000_000L)
        }
    }

    /** The site's `/^\d+$/` on the three numbers, refusal and all. */
    @Test
    fun `refuses a marker whose numbers are not bare digits`() {
        assertNull(StardustReceiveMarkup.parse("member_id=52425&ref_id=abc&diff=2"))
        assertNull(StardustReceiveMarkup.parse("member_id=52425&ref_id=100&diff=2.5"))
        assertNull(StardustReceiveMarkup.parse("member_id=-1&ref_id=100&diff=2"))
        assertNull(StardustReceiveMarkup.parse("ref_id=100&diff=2"))
    }

    /** `URLSearchParams` reads `+` as a space, so a hand-written marker's plus is one here too. */
    @Test
    fun `reads a plus in a hand-written marker as a space`() {
        val code = StardustReceiveMarkup.parse("member_id=9&ref_id=1&diff=5&description=a+b")

        assertEquals("a b", code?.description)
    }

    /**
     * A marker typed rather than generated can carry the character itself instead of its escape.
     *
     * `URLSearchParams` copies an unescaped character straight through, so an emoji — two `Char`s
     * that only mean anything together — has to survive as one. Decoding it as bytes one `Char` at a
     * time is the mistake this pins: that turns 🍜 into two replacement characters, and the reader
     * sees a 备注 the writer never typed.
     */
    @Test
    fun `keeps an unescaped character that takes two chars to write`() {
        val code = StardustReceiveMarkup.parse("member_id=9&ref_id=1&diff=5&description=%E8%AF%B7 🍜")

        assertEquals("请 🍜", code?.description)
    }

    /**
     * A stray `%` costs the site the whole card. Keeping the raw text instead loses nothing that was
     * ever readable and leaves a hand-written marker usable.
     */
    @Test
    fun `keeps a malformed escape as written rather than losing the code`() {
        val code = StardustReceiveMarkup.parse("member_id=9&ref_id=1&diff=5&description=100%")

        assertEquals("100%", code?.description)
    }
}
