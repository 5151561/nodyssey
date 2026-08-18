package io.github.nodyssey.core.html

import com.fleeksoft.ksoup.Ksoup
import io.github.nodyssey.model.PostSource
import io.github.nodyssey.model.PostSourceFloor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The Markdown a floor was written in, read back out of the post page's `__config__` blob.
 *
 * The rendered article is HTML and cannot be turned back into its source: the round trip loses the
 * author's line breaks, their table alignment, their `<details>` folds and every emoji shortcode.
 * Editing what came out of [RichContentParser] would therefore rewrite the whole post on every save.
 * The site does not ask its own editor to do that either — it hands the raw text over in
 * `postData.comments[].markdown`, and this reads the same field.
 *
 * Fetched fresh at the moment 编辑 is tapped rather than cached with the thread. The cache can be
 * days old, and saving a stale body would silently undo whatever was written from the web in the
 * meantime — plus the source of a long thread is a second copy of every floor's text, which is not
 * worth carrying on disk for something used once.
 */
object PostSourceParser {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @return null when the page carried no readable blob — a signed-out read, or a template change.
     *   Both mean the source is not available, which is a different answer from "the post is empty".
     */
    fun parse(html: String, postId: Long): PostSource? {
        val decoded = SiteBootstrap.decodeOrNull(Ksoup.parse(html)) ?: return null
        val postData =
            try {
                json.parseToJsonElement(decoded).jsonObject["postData"]?.jsonObject
            } catch (exception: IllegalArgumentException) {
                null
            } ?: return null

        val floors =
            postData["comments"]?.jsonArray.orEmpty().mapNotNull { entry ->
                val comment = entry as? JsonObject ?: return@mapNotNull null
                val commentId = comment["commentId"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                PostSourceFloor(
                    commentId = commentId,
                    // The opening post is floor 0; every reply carries its own number.
                    isOpeningPost = comment["floorIndex"]?.jsonPrimitive?.intOrNull == 0,
                    markdown = comment["markdown"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    isMine = (comment["poster"] as? JsonObject)
                        ?.get("isMe")
                        ?.jsonPrimitive
                        ?.booleanOrNull == true,
                )
            }

        return PostSource(
            postId = postId,
            title = postData["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            // The 阅读权限 has to be sent back on every 主楼 edit, so a post whose blob omits it is
            // read as 公开 — the same value the site's own `<select>` falls back to.
            rank = postData["rank"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            floors = floors,
        )
    }
}
