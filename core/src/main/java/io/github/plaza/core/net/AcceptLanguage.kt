package io.github.plaza.core.net

import android.os.LocaleList
import java.util.Locale

/**
 * The `Accept-Language` a browser on this device would send.
 *
 * OkHttp sends no `Accept-Language` at all, and a request without one is a request no browser
 * makes. Cloudflare's bot scoring reads that: on an image host fronted by it, the app's request was
 * answered with a managed challenge and a 403 while every browser on the same connection was served
 * the picture. Measured against `img.legend.moe` on 2026-08-18 — with the same User-Agent, adding
 * this header alone turned three 403s into three 200s, and removing it turned them back:
 *
 * ```
 * UA + Accept-Encoding + Accept-Language  →  200 200 200
 * UA + Accept-Encoding                    →  403 403 403
 * UA +                   Accept-Language  →  403 403 403
 * ```
 *
 * (`Accept-Encoding` is OkHttp's own, added to every request that does not set one, so the app
 * already had that half.)
 *
 * Sent on page requests too, not just images: the WebView sends one on every navigation, and two
 * halves of the app that disagree about what the device reads is exactly the mismatch a bot score
 * is looking for. See [resolveUserAgent] for the same argument about `User-Agent`.
 */
fun deviceAcceptLanguage(): String = acceptLanguage(LocaleList.getDefault().toLocales())

/**
 * Builds the header value from [locales], most-preferred first.
 *
 * Follows what Chromium sends: the first language carries no `q` — it is the default of 1 — and
 * each one after it is a tenth lower, down to a floor of 0.1. A region-qualified tag is followed by
 * its bare language (`zh-CN` then `zh`), because a server that has no `zh-CN` should still be able
 * to answer in Chinese.
 *
 * Pure, and takes its locales as an argument, so the format is testable without a device that has
 * to be persuaded to change its language settings.
 */
internal fun acceptLanguage(locales: List<Locale>): String {
    val tags =
        locales
            .flatMap { locale ->
                val tag = locale.toLanguageTag()
                // `und` is what an empty or unparseable locale becomes; it says nothing and no
                // browser sends it.
                if (tag.isBlank() || tag == "und") {
                    emptyList()
                } else {
                    listOf(tag) + listOfNotNull(locale.language.takeIf { it.isNotBlank() && it != tag })
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

private fun LocaleList.toLocales(): List<Locale> = (0 until size()).mapNotNull { get(it) }
