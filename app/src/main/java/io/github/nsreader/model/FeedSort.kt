package io.github.nsreader.model

/**
 * The two orders NodeSeek serves a topic list in.
 *
 * [LAST_REPLY] is the site's default and what makes the front page feel alive; [POST_TIME] is what
 * people switch to when a single busy thread keeps dragging itself back to the top.
 */
enum class FeedSort {
    LAST_REPLY,
    POST_TIME,
}
