package io.github.nodyssey.data.diagnostics

import io.github.nodyssey.data.dns.DohProvider
import io.github.nodyssey.data.proxy.ProxyConnectionFailure
import io.github.nodyssey.data.proxy.ProxyType

/**
 * 网络自检 — what the app can tell a reader about its own connection, so a report of "很慢" arrives
 * with numbers attached instead of as an adjective.
 *
 * It exists because the alternative is a conversation. A reader on a phone with a VPN, a 分应用代理
 * list and two of this app's own network settings has four independent ways to be slow, and none of
 * them is visible from a video of a spinner. Asking one at a time costs a day per round trip and
 * usually ends with the wrong one ruled out; one screenshot of this screen answers all four.
 *
 * The interface is here and the implementation is not, for the reason the four other interfaces in
 * this position have: what it needs is *bytes and the times they arrived at*, and `HttpTransport`
 * hands back decoded text with no clock attached. Measuring a transfer means naming an HTTP client,
 * so the measuring lives in the module that has one — see `OkHttpNetworkDiagnostics` in `:app`,
 * which reads its numbers off OkHttp's own `EventListener` rather than timing the call from outside
 * and guessing which layer the time went to.
 *
 * Null on `AppContainer` where a platform has no implementation, exactly as `DohSupport` is: 设置
 * does not draw the row that leads here, and the destination draws nothing if it is reached anyway.
 */
interface NetworkDiagnostics {
    /**
     * The part with no network in it: what this device's connection *is*, and what the app has been
     * told to do with it.
     *
     * Separate from [probe] and shown before it, because it is instant and because it is the half
     * that most often already contains the answer. A VPN row reading 已连接 next to a 代理 row
     * reading 关闭 is the whole diagnosis for the most common report there is — the app is slow, the
     * browser is fast, and neither of those facts is about the app.
     */
    suspend fun environment(): NetworkEnvironment

    /** One measured request to [target]. Never throws; a call that did not complete comes back as [ProbeResult.Failed]. */
    suspend fun probe(target: ProbeTarget): ProbeResult
}

/**
 * The two hosts worth measuring, and they are worth measuring *together*.
 *
 * Both are hosts the app already talks to, so neither adds a third party to the privacy story. The
 * pair is chosen for the comparison it draws rather than for either number alone: they are different
 * networks, different CDNs and — where the reader picked `ProxyScope.FORUM_ONLY` — different routes.
 * Forum slow with updates fast says the route to the forum is the problem; both slow says it is the
 * connection, and the app is only the messenger.
 */
enum class ProbeTarget {
    /** The forum's own front page, through the client every post and avatar arrives on. */
    FORUM,

    /** The update manifest on this app's Pages site — see `NodysseyRelease.UPDATES_BASE_URL`. */
    UPDATES,
}

/**
 * @property transport what the system says is carrying traffic right now.
 * @property vpnActive a tunnel is up. **The single most useful row on the screen**: it is invisible
 *   from inside the app in every other way, it changes what every number below it means, and the
 *   app cannot see which of its own requests the tunnel's per-app rules selected.
 * @property metered the platform's own answer, not an interpretation of [transport]. A VPN whose
 *   tunnel does not report `NOT_METERED` makes this true on Wi-Fi — see
 *   `hasValidatedUnmeteredNetwork`, and 图片仅 Wi-Fi 加载, which reads the same bit and surprises
 *   people for the same reason.
 * @property device which phone this is, and what it is running.
 * @property appVersion which build produced the report. Without it a screenshot cannot be placed
 *   against a changelog, and half the reports a diagnostic screen collects are about something that
 *   was fixed two releases ago.
 * @property proxy null when 代理 is off.
 * @property dohProvider which server lookups go to, or null when 加密 DNS is off.
 * @property customTabsProvider which installed app answers when the reader taps a link in a post,
 *   and null where nothing on the device offers the service at all. Plain strings rather than a
 *   platform type because that is all `commonMain` may hold, and all the screen has to draw.
 * @property defaultBrowser which app the system hands an `http` link to.
 */
data class NetworkEnvironment(
    val device: DeviceIdentity,
    val appVersion: String,
    val transport: NetworkTransport,
    val vpnActive: Boolean,
    val metered: Boolean,
    val proxy: ProxySummary?,
    val dohProvider: DohProvider?,
    val customTabsProvider: AppIdentity?,
    val defaultBrowser: AppIdentity?,
)

/**
 * An installed app, named twice: once for the reader and once for whoever reads the screenshot.
 *
 * Both halves earn their place. [label] is the only one the reader can match against what they
 * think they are using; [packageId] is the only one that can be matched against a 分应用代理 list,
 * where the entries are packages and two apps can carry the same Chinese name.
 *
 * This pair is on this screen because of the report it was built for: a link opened from a post
 * crawled while the same page in "the browser" was instant. A Custom Tab is not "the browser" —
 * it is *whichever* app claims the Custom Tabs service, which need not be the one the reader
 * opens from their home screen. Where those two rows disagree, they are the answer, and no amount
 * of measuring the app's own sockets would have found it: the tab's traffic is that app's, under
 * that app's UID, subject to whatever a VPN's per-app rules say about it and nothing this app can
 * see or change.
 */
data class AppIdentity(
    val label: String,
    val packageId: String,
)

enum class NetworkTransport { WIFI, CELLULAR, ETHERNET, OTHER, NONE }

/**
 * The phone, as a bug report needs to name it.
 *
 * Both fields are already-assembled display strings rather than the parts they were built from,
 * because what a platform has to say here has no shape in common with what another one does — a
 * manufacturer and a model on one side, a device identifier and a marketing name on the other. The
 * screen prints them; nothing branches on them.
 *
 * Worth the two rows because the answers on this screen are frequently a property of the phone
 * rather than of the network. A ROM with its own per-app data policy, a system WebView too old for
 * the site, a vendor VPN service: none of those are visible in a timing, and all of them are the
 * first thing to check once the model is known.
 *
 * @property model manufacturer and model together, deduplicated — see `deviceIdentity`.
 * @property osVersion the release the reader would recognise, plus the API level that decides
 *   behaviour. The two disagree often enough to be worth carrying both.
 */
data class DeviceIdentity(
    val model: String,
    val osVersion: String,
)

/**
 * 代理 reduced to what a bug report may carry.
 *
 * The host is deliberately not here. This report is built to be pasted into a forum thread, and a
 * proxy hostname is the reader's own infrastructure — the same reason
 * `toProxyConnectionFailure` classifies an exception instead of quoting its message. [loopback] is
 * the one distinction worth keeping and costs nothing to expose: 127.0.0.1 means a local client such
 * as Clash is doing the real routing and this setting is only pointing at it, which is a different
 * problem from a proxy that is itself the far end.
 */
data class ProxySummary(
    val type: ProxyType,
    val loopback: Boolean,
    val port: Int,
    val forumOnly: Boolean,
)

sealed interface ProbeResult {
    data class Answered(val statusCode: Int, val timing: ProbeTiming) : ProbeResult

    /**
     * Reuses 代理设置's failure vocabulary rather than inventing a second one: the distinctions a
     * failed probe wants to draw — DNS, timeout, connection, TLS — are the same distinctions, and
     * that classifier already avoids putting an exception message on screen.
     */
    data class Failed(val failure: ProxyConnectionFailure) : ProbeResult
}

/**
 * Where the time went, split at the boundaries that point at different culprits.
 *
 * The split is the whole point. A single elapsed number cannot tell "the connection is slow" from
 * "the connection is fine and something took eight seconds to answer", and those two have nothing to
 * do with each other. Reading down the fields: a large [dnsMillis] is a resolver problem, a large
 * [connectMillis] is the route, a large [firstByteMillis] with the rest small is the server or a
 * proxy thinking, and a small [firstByteMillis] with a large [totalMillis] is throughput — which is
 * the one shape that matches a progress bar crawling.
 *
 * The first three are null when the call reused a connection that was already open, which is why the
 * probe opens its own — see `OkHttpNetworkDiagnostics`.
 *
 * @property connectMillis the TCP handshake alone; [tlsMillis] is counted separately rather than
 *   folded in, because a slow TLS handshake on a fast TCP one is a middlebox and worth seeing.
 * @property firstByteMillis from the call starting to the response headers arriving, so it contains
 *   the three above.
 * @property totalMillis to the last byte of the body.
 */
data class ProbeTiming(
    val dnsMillis: Long?,
    val connectMillis: Long?,
    val tlsMillis: Long?,
    val firstByteMillis: Long,
    val totalMillis: Long,
    val bytes: Long,
)

/**
 * Throughput over the body transfer alone — the bytes divided by the time between the first of them
 * and the last.
 *
 * Not bytes over [ProbeTiming.totalMillis], which would fold DNS, the handshakes and the server's
 * thinking time into a number labelled 速率 and report a fast connection as a slow one whenever the
 * far end paused before answering.
 *
 * Null when the transfer was too brief to divide by. A body that arrived inside [MIN_TRANSFER_MILLIS]
 * has a rate that is mostly rounding error, and printing "40 MB/s" off a two-millisecond sample would
 * be the one number on this screen that is confidently wrong. Null is honest, and the row that shows
 * it says 太快，测不出 rather than hiding — a transfer too quick to measure is itself an answer.
 */
fun ProbeTiming.bodyBytesPerSecond(): Long? {
    if (bytes <= 0) return null
    val transferMillis = totalMillis - firstByteMillis
    if (transferMillis < MIN_TRANSFER_MILLIS) return null
    return bytes * MILLIS_PER_SECOND / transferMillis
}

/**
 * Below this, the clock is measuring itself as much as the network. Deliberately generous: this
 * screen exists for connections where a transfer takes seconds, and on those there is no risk of
 * crossing it by accident.
 */
private const val MIN_TRANSFER_MILLIS = 20L
private const val MILLIS_PER_SECOND = 1000L
