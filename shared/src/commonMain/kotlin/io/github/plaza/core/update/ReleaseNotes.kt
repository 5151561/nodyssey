package io.github.plaza.core.update

/**
 * Flattens a release body into the few lines the update card shows.
 *
 * The body is the CHANGELOG section as `release.yml` published it — Markdown, and the card renders
 * plain text. Rather than pull a Markdown renderer into a six-line summary, the two markers that
 * read as noise are removed: the `###` in front of 新增 / 修复, which is punctuation the reader did
 * not ask for, and the `**Full Changelog**` line the workflow appends, which is a link the card
 * already has a button for.
 *
 * Nothing else is touched. `- ` bullets read as bullets, and rewriting the author's text further
 * would be this screen deciding what the release notes say.
 */
fun releaseNotesText(body: String): String =
    body
        .lineSequence()
        .filterNot { it.trimStart().startsWith(FULL_CHANGELOG_MARKER) }
        .map { line -> line.replace(HEADING, "") }
        .joinToString("\n")
        .replace(BLANK_RUN, "\n\n")
        .trim()

private const val FULL_CHANGELOG_MARKER = "**Full Changelog**"
private val HEADING = Regex("^#{1,6}\\s+")
private val BLANK_RUN = Regex("\n{3,}")
