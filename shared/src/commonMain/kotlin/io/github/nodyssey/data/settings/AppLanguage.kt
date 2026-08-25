package io.github.nodyssey.data.settings

/**
 * 语言 — which of the app's translations the interface is drawn in.
 *
 * [SYSTEM] is the default and means "whatever the device is set to", which is what every build
 * before this setting existed did. The other three name a bundle the app ships, and they are the
 * *whole* list on purpose: a language with no `values-…` directory behind it would be an entry that
 * silently draws Chinese.
 *
 * [tag] is the BCP 47 tag the platform is asked to switch to, and it is the tag the resource
 * directories are keyed by rather than the most precise name for the language. `zh-TW` stands for
 * Traditional Chinese as a whole — see [isTraditionalChineseTag] for why the region and not the
 * script does that job here.
 */
enum class AppLanguage(val tag: String?) {
    /** No override: the device's own language list decides, and a change to it takes effect at once. */
    SYSTEM(null),

    SIMPLIFIED_CHINESE("zh-CN"),

    TRADITIONAL_CHINESE("zh-TW"),

    ENGLISH("en"),
    ;

    companion object {
        /** What a stored name decodes to, including the names no build ever wrote. */
        fun ofName(stored: String?): AppLanguage =
            entries.firstOrNull { it.name == stored } ?: SYSTEM
    }
}

/**
 * Whether a BCP 47 tag names a reader of Traditional Chinese.
 *
 * Needed because the app ships its Traditional strings under a *region* qualifier — `values-zh-rTW`
 * — and not under the script qualifier that describes them. The script qualifier is the honest name
 * for that bundle and it is the one this file would rather use, but Compose Resources resolves a
 * locale by matching qualifiers in a fixed order, and a `zh` item carrying no region is preferred
 * over the unqualified default for *every* Chinese environment. So a `values-b+zh+Hant` bundle is
 * handed to a Simplified reader whenever the platform reports no script — which a `zh_CN` locale on
 * the desktop JVM does not, and which Android is nowhere required to. Failing that way round is not
 * a fallback, it is the wrong language; keyed by region instead, the same gap resolves to the
 * default, which is Simplified. See `filterByLocale` in
 * `org.jetbrains.compose.resources.ResourceEnvironment`.
 *
 * The cost of that choice is this function: `zh-Hant-HK` and `zh-MO` have to be recognised here and
 * turned into `zh-TW` before the platform is asked, because nothing downstream will do it. Delete
 * both halves the day Compose Resources can alias one bundle to several qualifiers.
 */
fun isTraditionalChineseTag(tag: String): Boolean {
    val subtags = tag.split('-', '_').filter { it.isNotEmpty() }
    val language = subtags.firstOrNull() ?: return false
    if (!language.equals("zh", ignoreCase = true)) return false
    val rest = subtags.drop(1)
    // An explicit script settles it in either direction, and `Hans` is checked first so that a tag
    // naming both a Simplified script and one of the regions below is read as its script says.
    if (rest.any { it.equals("HANS", ignoreCase = true) }) return false
    if (rest.any { it.equals("HANT", ignoreCase = true) }) return true
    return rest.any { subtag -> TRADITIONAL_REGIONS.any { it.equals(subtag, ignoreCase = true) } }
}

/** The three regions whose Chinese is written in the traditional script. */
private val TRADITIONAL_REGIONS = listOf("TW", "HK", "MO")
