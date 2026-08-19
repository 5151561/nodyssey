package io.github.plaza.core.net

/**
 * The one User-Agent both halves of the app send.
 *
 * Where it comes from is [resolveUserAgent]'s business, and lives beside it; this is the value and
 * what a consumer has to know about it.
 *
 * @property value what OkHttp must put on every request.
 * @property isWebViewDefault true when [value] was read straight off the WebView, meaning the WebView
 *   needs no override — and must not be given one. Calling `setUserAgentString` marks the UA as
 *   overridden in Chromium, which changes what it reports through UA client hints; setting it to the
 *   value it already had is therefore not a no-op.
 */
data class UserAgent(
    val value: String,
    val isWebViewDefault: Boolean,
)
