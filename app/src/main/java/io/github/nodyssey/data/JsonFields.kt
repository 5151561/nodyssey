package io.github.nodyssey.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Field readers for NodeSeek's JSON, which is not a documented API.
 *
 * The endpoints are the site's own XHRs and their payloads are inconsistent between themselves —
 * `member_id` here, `uid` there, `nPost` next to `post_count`. Reading by a list of candidate names
 * rather than by a generated model is what keeps one renamed key from emptying a whole screen, and it
 * costs nothing at these payload sizes.
 *
 * Every reader tolerates a value arriving as the wrong primitive type: ids and counts come back as
 * JSON strings often enough that requiring a number would be a bug rather than strictness.
 */
internal fun JsonObject.text(vararg names: String): String? {
    names.forEach { name ->
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it }
    }
    return null
}

internal fun JsonObject.long(vararg names: String): Long? {
    names.forEach { name ->
        (this[name] as? JsonPrimitive)?.let { primitive ->
            (primitive.longOrNull ?: primitive.contentOrNull?.trim()?.toLongOrNull())?.let { return it }
        }
    }
    return null
}

internal fun JsonObject.int(vararg names: String): Int? {
    names.forEach { name ->
        (this[name] as? JsonPrimitive)?.let { primitive ->
            (primitive.intOrNull ?: primitive.contentOrNull?.trim()?.toIntOrNull())?.let { return it }
        }
    }
    return null
}

internal fun JsonObject.bool(vararg names: String): Boolean? {
    names.forEach { name ->
        (this[name] as? JsonPrimitive)?.let { primitive ->
            primitive.booleanOrNull?.let { return it }
            primitive.intOrNull?.let { return it != 0 }
        }
    }
    return null
}

internal fun JsonObject.obj(vararg names: String): JsonObject? {
    names.forEach { name -> (this[name] as? JsonObject)?.let { return it } }
    return null
}

/**
 * The array of objects behind one of the [preferredNames], wherever the payload nests it.
 *
 * A walk rather than a fixed path: these responses wrap their list in `data`, in `result`, or in
 * nothing at all depending on the endpoint. When no preferred name matches, an unnamed array is
 * trusted only if it is the single candidate in the payload — with several, picking one by traversal
 * order could hand back a sibling block (badges, a pager) and turn a shape mismatch into a confident
 * empty list, when the caller's Unparsable path is the honest answer.
 */
internal fun JsonElement.findObjectArray(vararg preferredNames: String): List<JsonObject>? {
    if (this is JsonArray) return objectsOrNull()
    if (this !is JsonObject) return null

    findByPreferredName(preferredNames)?.let { return it }
    val candidates = collectObjectArrays()
    return when {
        candidates.size == 1 -> candidates.first()
        candidates.isNotEmpty() && candidates.all { it.isEmpty() } -> candidates.first()
        else -> null
    }
}

private fun JsonObject.findByPreferredName(preferredNames: Array<out String>): List<JsonObject>? {
    preferredNames.forEach { name ->
        (this[name] as? JsonArray)?.objectsOrNull()?.let { return it }
    }
    values.forEach { child ->
        (child as? JsonObject)?.findByPreferredName(preferredNames)?.let { return it }
    }
    return null
}

private fun JsonElement.collectObjectArrays(): List<List<JsonObject>> =
    when (this) {
        is JsonArray -> objectsOrNull()?.let { listOf(it) }.orEmpty()
        is JsonObject -> values.flatMap { it.collectObjectArrays() }
        else -> emptyList()
    }

private fun JsonArray.objectsOrNull(): List<JsonObject>? =
    if (all { it is JsonObject }) map { it as JsonObject } else null
