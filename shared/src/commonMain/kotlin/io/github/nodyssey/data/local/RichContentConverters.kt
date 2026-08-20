package io.github.nodyssey.data.local

import androidx.room.TypeConverter
import io.github.nodyssey.model.PostContent
import kotlinx.serialization.json.Json

/**
 * Stores a parsed [PostContent] as a JSON string.
 *
 * `ignoreUnknownKeys` matters on *read*: a row written by a newer build that has since been
 * downgraded, or a node type added and then removed, must not crash the reader. A cache is allowed
 * to lose fidelity; it is not allowed to take the app down.
 */
// Public rather than `internal`: a repository that writes a cached body the same way the converter
// would has to be able to name the format, and since step A6 that repository is in another module.
object RichContentJson {
    val format =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
}

class RichContentConverters {
    @TypeConverter
    fun contentToJson(content: PostContent): String = RichContentJson.format.encodeToString(content)

    @TypeConverter
    fun jsonToContent(value: String): PostContent = RichContentJson.format.decodeFromString(value)
}
