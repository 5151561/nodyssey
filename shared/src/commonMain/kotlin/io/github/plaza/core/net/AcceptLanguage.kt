package io.github.plaza.core.net

/**
 * Builds the header value from [languageTags], most-preferred first.
 *
 * Follows what Chromium sends: the first language carries no `q` — it is the default of 1 — and
 * each one after it is a tenth lower, down to a floor of 0.1. A region-qualified tag is followed by
 * its bare language (`zh-CN` then `zh`), because a server that has no `zh-CN` should still be able
 * to answer in Chinese.
 *
 * Takes tags rather than a platform's locale type, which is what makes it common: reading the
 * device's preferred languages is the platform's half and lives beside each platform's own list —
 * see `deviceAcceptLanguage`. The bare language is the tag's first subtag, which is what BCP 47
 * defines it to be.
 */
internal fun acceptLanguage(languageTags: List<String>): String {
    val tags =
        languageTags
            .map { it.trim() }
            .flatMap { tag ->
                // `und` is what an empty or unparseable locale becomes; it says nothing and no
                // browser sends it.
                if (tag.isBlank() || tag == "und") {
                    emptyList()
                } else {
                    val language = tag.substringBefore('-')
                    listOf(tag) + listOfNotNull(language.takeIf { it.isNotBlank() && it != tag })
                }
            }.distinct()
            .take(MAX_LANGUAGES)

    if (tags.isEmpty()) return FALLBACK

    return tags
        .mapIndexed { index, tag ->
            if (index == 0) tag else "$tag;q=${quality(index)}"
        }.joinToString(",")
}

/** 0.9, 0.8, … never below 0.1, and formatted without a locale's idea of a decimal separator. */
private fun quality(index: Int): String {
    val tenths = (10 - index).coerceAtLeast(1)
    return "0.$tenths"
}

/**
 * Long enough for a device carrying a couple of preferred languages, short enough not to hand out a
 * fingerprint of a language list nobody else has.
 */
private const val MAX_LANGUAGES = 6

/** What a device with no usable locale gets. Sending nothing is the thing this file exists to avoid. */
private const val FALLBACK = "en-US,en;q=0.9"
