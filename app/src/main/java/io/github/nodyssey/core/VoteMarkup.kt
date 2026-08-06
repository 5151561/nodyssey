package io.github.nodyssey.core

/**
 * How a vote is written into a post body.
 *
 * Read off the site's own `__config__` for post 857694, whose source is
 * `" nsapp://vote?id=2871 \n 三家的价格…"` — a bare URL on its own line. NodeSeek's markdown renderer
 * auto-links it, its script recognises the resulting `data-href` and swaps in the panel.
 *
 * One function so there is one place to correct if the site ever changes what it recognises. Nothing
 * about this is guessable from the rendered HTML, which shows an `<a>` that no author ever typed.
 */
object VoteMarkup {
    /** The line to splice into the body once the vote exists server-side. */
    fun marker(voteId: Long): String = "\n\nnsapp://vote?id=$voteId\n\n"
}
