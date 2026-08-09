package io.github.plaza.core.update

/**
 * Version-name ordering, for deciding whether a published release is newer than the installed build.
 *
 * `.github/workflows/release.yml` refuses to publish a tag whose name disagrees with the APK's
 * `versionName`, so both sides of every comparison are the same string in the same shape — `v1.2.0`
 * against `1.2.0`. That is the shape this understands: dotted numbers, an optional `-pre` suffix and
 * optional `+build` metadata.
 *
 * Segments compare as numbers, a missing segment counts as zero (`1.2` equals `1.2.0`), build
 * metadata is ignored, and a pre-release sorts *below* the release of the same number, as semver
 * requires. A segment that is not a number contributes its leading digits, or zero: a tag shaped in
 * some way this does not model must not read as "newer" on the strength of alphabetical luck.
 */
fun compareVersionNames(left: String, right: String): Int {
    val (leftCore, leftPreRelease) = splitVersionName(left)
    val (rightCore, rightPreRelease) = splitVersionName(right)
    repeat(maxOf(leftCore.size, rightCore.size)) { index ->
        val comparison = leftCore.getOrElse(index) { 0 }.compareTo(rightCore.getOrElse(index) { 0 })
        if (comparison != 0) return comparison
    }
    return comparePreRelease(leftPreRelease, rightPreRelease)
}

/**
 * True when [candidate] is a version the user does not have yet.
 *
 * A blank [current] — what `PackageManager` hands back when it cannot read a `versionName` — answers
 * false rather than "everything is newer": offering an update against a version we failed to read
 * would push an install on someone for no reason we could state.
 */
fun isNewerVersionName(candidate: String, current: String): Boolean =
    candidate.isNotBlank() &&
        current.isNotBlank() &&
        compareVersionNames(candidate, current) > 0

/** `v1.2.0` is the tag, `1.2.0` is the version inside the APK. This is the one difference. */
fun versionNameOfTag(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")

private fun splitVersionName(raw: String): Pair<List<Int>, String> {
    val withoutBuildMetadata = versionNameOfTag(raw).substringBefore('+')
    val core = withoutBuildMetadata.substringBefore('-')
    val preRelease = withoutBuildMetadata.substringAfter('-', "")
    return core.split('.').map { segment -> segment.takeWhile(Char::isDigit).toIntOrNull() ?: 0 } to
        preRelease
}

private fun comparePreRelease(left: String, right: String): Int =
    when {
        left == right -> 0

        // Absent beats present: 1.2.0 is the release that 1.2.0-rc1 was leading up to.
        left.isEmpty() -> 1

        right.isEmpty() -> -1

        else -> left.compareTo(right)
    }
