package io.github.plaza.core.net

import android.os.LocaleList

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
fun deviceAcceptLanguage(): String = acceptLanguage(LocaleList.getDefault().languageTagList())

// Not `LocaleList.toLanguageTags()`, which is the platform's own and answers with one
// comma-joined string — the shape this file exists to build, and not the one to build it from.
private fun LocaleList.languageTagList(): List<String> =
    (0 until size()).mapNotNull { index -> get(index)?.toLanguageTag() }
