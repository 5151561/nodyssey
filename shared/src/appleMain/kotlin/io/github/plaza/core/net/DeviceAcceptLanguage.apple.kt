package io.github.plaza.core.net

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/**
 * The `Accept-Language` a browser on this device would send.
 *
 * The measurement that made this header non-optional is written out in `DeviceAcceptLanguage.kt` in
 * `androidMain`: with the same User-Agent, adding it alone turned three 403s from a Cloudflare-fronted
 * image host into three 200s. Nothing about that is Android's.
 *
 * `NSLocale.preferredLanguages` is already a list of BCP 47 tags in preference order, which is the
 * shape [acceptLanguage] takes; the Android half has to build that list out of a `LocaleList` first.
 */
fun deviceAcceptLanguage(): String =
    acceptLanguage(NSLocale.preferredLanguages.mapNotNull { it as? String })
