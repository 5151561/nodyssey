package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.diagnostics.AppIdentity
import io.github.nodyssey.data.diagnostics.DeviceIdentity
import io.github.nodyssey.data.diagnostics.NetworkEnvironment
import io.github.nodyssey.data.diagnostics.NetworkTransport
import io.github.nodyssey.data.diagnostics.ProbeResult
import io.github.nodyssey.data.diagnostics.ProbeTiming
import io.github.nodyssey.data.diagnostics.ProxySummary
import io.github.nodyssey.data.diagnostics.SessionSummary
import io.github.nodyssey.data.diagnostics.bodyBytesPerSecond
import io.github.nodyssey.data.diagnostics.formatBytes
import io.github.nodyssey.data.diagnostics.formatMillis
import io.github.nodyssey.data.diagnostics.formatRate
import io.github.nodyssey.data.proxy.ProxyConnectionFailure
import io.github.nodyssey.data.proxy.ProxyType
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.network_check_app_none
import io.github.nodyssey.ui.resources.network_check_app_version
import io.github.nodyssey.ui.resources.network_check_clearance
import io.github.nodyssey.ui.resources.network_check_connect
import io.github.nodyssey.ui.resources.network_check_cookie_names
import io.github.nodyssey.ui.resources.network_check_cookie_names_none
import io.github.nodyssey.ui.resources.network_check_copied
import io.github.nodyssey.ui.resources.network_check_copy
import io.github.nodyssey.ui.resources.network_check_custom_tab_app
import io.github.nodyssey.ui.resources.network_check_default_browser
import io.github.nodyssey.ui.resources.network_check_device
import io.github.nodyssey.ui.resources.network_check_dns
import io.github.nodyssey.ui.resources.network_check_doh
import io.github.nodyssey.ui.resources.network_check_doh_off
import io.github.nodyssey.ui.resources.network_check_download
import io.github.nodyssey.ui.resources.network_check_download_value
import io.github.nodyssey.ui.resources.network_check_failed
import io.github.nodyssey.ui.resources.network_check_first_byte
import io.github.nodyssey.ui.resources.network_check_hint_custom_tab
import io.github.nodyssey.ui.resources.network_check_hint_layers
import io.github.nodyssey.ui.resources.network_check_hint_scope
import io.github.nodyssey.ui.resources.network_check_hint_title
import io.github.nodyssey.ui.resources.network_check_metered
import io.github.nodyssey.ui.resources.network_check_no
import io.github.nodyssey.ui.resources.network_check_os
import io.github.nodyssey.ui.resources.network_check_pending
import io.github.nodyssey.ui.resources.network_check_proxy
import io.github.nodyssey.ui.resources.network_check_proxy_forum_only
import io.github.nodyssey.ui.resources.network_check_proxy_local
import io.github.nodyssey.ui.resources.network_check_proxy_off
import io.github.nodyssey.ui.resources.network_check_proxy_remote
import io.github.nodyssey.ui.resources.network_check_rate
import io.github.nodyssey.ui.resources.network_check_rate_immeasurable
import io.github.nodyssey.ui.resources.network_check_rerun
import io.github.nodyssey.ui.resources.network_check_reused
import io.github.nodyssey.ui.resources.network_check_running
import io.github.nodyssey.ui.resources.network_check_section_environment
import io.github.nodyssey.ui.resources.network_check_section_forum
import io.github.nodyssey.ui.resources.network_check_section_updates
import io.github.nodyssey.ui.resources.network_check_session
import io.github.nodyssey.ui.resources.network_check_status
import io.github.nodyssey.ui.resources.network_check_status_code
import io.github.nodyssey.ui.resources.network_check_summary_failed
import io.github.nodyssey.ui.resources.network_check_summary_ok
import io.github.nodyssey.ui.resources.network_check_summary_updates
import io.github.nodyssey.ui.resources.network_check_title
import io.github.nodyssey.ui.resources.network_check_tls
import io.github.nodyssey.ui.resources.network_check_transport
import io.github.nodyssey.ui.resources.network_check_transport_cellular
import io.github.nodyssey.ui.resources.network_check_transport_ethernet
import io.github.nodyssey.ui.resources.network_check_transport_none
import io.github.nodyssey.ui.resources.network_check_transport_other
import io.github.nodyssey.ui.resources.network_check_transport_wifi
import io.github.nodyssey.ui.resources.network_check_vpn
import io.github.nodyssey.ui.resources.network_check_vpn_off
import io.github.nodyssey.ui.resources.network_check_vpn_on
import io.github.nodyssey.ui.resources.network_check_web_view
import io.github.nodyssey.ui.resources.network_check_yes
import io.github.nodyssey.ui.resources.proxy_test_failure_connection
import io.github.nodyssey.ui.resources.proxy_test_failure_dns
import io.github.nodyssey.ui.resources.proxy_test_failure_other
import io.github.nodyssey.ui.resources.proxy_test_failure_socks_auth
import io.github.nodyssey.ui.resources.proxy_test_failure_timeout
import io.github.nodyssey.ui.resources.proxy_test_failure_tls
import io.github.nodyssey.ui.resources.proxy_type_http
import io.github.nodyssey.ui.resources.proxy_type_socks
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerDivider
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.SectionNote
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

@Composable
fun NetworkCheckRoute(
    viewModel: NetworkCheckViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NetworkCheckScreen(
        state = state,
        onBack = onBack,
        onRerun = viewModel::run,
        modifier = modifier,
    )
}

/**
 * 网络自检 — the app's own connection, measured and laid out to be screenshotted.
 *
 * Every row is a label and a value and nothing else, which is the whole design. This screen's reader
 * is usually not the person looking at it: it is filled in by someone reporting that the app is slow
 * and read by someone trying to work out why, in a forum thread, from a photograph of a phone. That
 * makes legibility at screenshot size the requirement, and it is why the 复制结果 button exists
 * beside it — the same content as text, for the reader who can paste instead.
 *
 * The rows are built once, as data, by [checkSections]. Rendering and the copy action in the top
 * bar then read the same list, so what is pasted is what was on screen and no second copy of the wording can drift from it.
 */
@Composable
fun NetworkCheckScreen(
    state: NetworkCheckUiState,
    onBack: () -> Unit,
    onRerun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appBarState = rememberOneHandAppBarState()
    val sections = checkSections(state)
    val copy = rememberClipboardCopy()
    val copyLabel = stringResource(Res.string.network_check_copy)
    val copied = stringResource(Res.string.network_check_copied)

    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.network_check_title),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { copy(copyLabel, sections.asShareText(), copied) },
                        // Copying a half-finished report would put numbers next to 「…」 in a
                        // thread and read as measurements that came back empty.
                        enabled = !state.running,
                    ) {
                        Icon(PlazaIcons.ContentCopy, contentDescription = copyLabel)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                .padding(SettingsPagePadding),
            verticalArrangement = Arrangement.spacedBy(SettingsItemGap),
        ) {
            CheckSummaryCard(state)

            sections.forEach { section ->
                SectionLabel(section.title)
                SettingsGroup {
                    section.lines.forEachIndexed { index, line ->
                        CheckLineRow(line = line, first = index == 0)
                    }
                }
            }

            Button(
                onClick = onRerun,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs).heightIn(min = 52.dp),
            ) {
                if (state.running) {
                    PlazaSpinner(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        size = 18.dp,
                    )
                    Text(
                        text = stringResource(Res.string.network_check_running),
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                } else {
                    Text(stringResource(Res.string.network_check_rerun), style = MaterialTheme.typography.titleMedium)
                }
            }

            Column(
                modifier = Modifier.padding(top = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                SectionNote(
                    stringResource(Res.string.network_check_hint_title),
                    modifier = Modifier.semantics { heading() },
                )
                SectionNote(stringResource(Res.string.network_check_hint_layers))
                SectionNote(stringResource(Res.string.network_check_hint_custom_tab))
                SectionNote(stringResource(Res.string.network_check_hint_scope))
            }
        }
    }
}

/**
 * The verdict a reader looks for first — can the forum be reached — on a card of its own above the
 * numbers. Read off the forum probe alone; the update source rides along on the second line only
 * when it failed, because a forum that loads is the answer to the question this screen is opened
 * with, and a slow GitHub is a footnote to it.
 */
@Composable
private fun CheckSummaryCard(state: NetworkCheckUiState) {
    val scheme = MaterialTheme.colorScheme
    val forum = state.forum
    val failed = forum is ProbeResult.Failed || (forum is ProbeResult.Answered && forum.statusCode !in 200..299)
    val title =
        when {
            state.running || forum == null -> stringResource(Res.string.network_check_running)
            failed -> stringResource(Res.string.network_check_summary_failed)
            else -> stringResource(Res.string.network_check_summary_ok)
        }
    val updates = state.updates
    val subtitle =
        (updates as? ProbeResult.Failed)?.let {
            stringResource(Res.string.network_check_summary_updates, failureText(it.failure))
        }
    LayerCard(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Surface(
                color = if (failed) scheme.errorContainer else scheme.tertiaryContainer,
                contentColor = if (failed) scheme.onErrorContainer else scheme.onTertiaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        state.running || forum == null -> PlazaSpinner(strokeWidth = 2.dp, size = 20.dp)
                        failed -> Icon(PlazaIcons.ErrorCircle, contentDescription = null)
                        else -> Icon(Icons.Default.CheckCircle, contentDescription = null)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
                )
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * One label-and-value line of the report: label on the left in the muted tone, value on the right
 * in full ink so the column of numbers is what the eye runs down. Shorter than a settings row —
 * there is nothing to tap — so a whole section fits a screenshot.
 */
@Composable
private fun CheckLineRow(
    line: CheckLine,
    first: Boolean,
) {
    Column {
        if (!first) LayerDivider(startInset = Spacing.lg)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(horizontal = Spacing.lg, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = line.label,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = line.value,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp, fontWeight = FontWeight.Medium),
                color = if (line.alert) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One label-and-value line. [alert] is "look at this one", not "this is broken". */
private data class CheckLine(
    val label: String,
    val value: String,
    val alert: Boolean = false,
)

private data class CheckSection(
    val title: String,
    val lines: List<CheckLine>,
)

/**
 * The whole report as text, in the shape a forum post wants it.
 *
 * Built from the rendered lines rather than from the state, so it cannot say anything the screen did
 * not — see the note on [NetworkCheckScreen].
 */
private fun List<CheckSection>.asShareText(): String =
    joinToString("\n\n") { section ->
        buildString {
            append("【").append(section.title).append("】")
            section.lines.forEach { line ->
                append("\n").append(line.label).append("：").append(line.value)
            }
        }
    }

@Composable
private fun checkSections(state: NetworkCheckUiState): List<CheckSection> =
    buildList {
        state.environment?.let { add(environmentSection(it)) }
        add(probeSection(stringResource(Res.string.network_check_section_forum), state.forum))
        add(probeSection(stringResource(Res.string.network_check_section_updates), state.updates))
    }

@Composable
private fun environmentSection(environment: NetworkEnvironment): CheckSection {
    // Two rows rather than one comparison, because the reader is the one who knows which app they
    // meant by "浏览器" — the screen can only put both packages where they can be read side by side.
    // Flagged when they differ, since that is the case worth a second look.
    val customTabs = environment.customTabsProvider
    val browser = environment.defaultBrowser
    val browsersDiffer = customTabs != null && browser != null && customTabs.packageId != browser.packageId
    return CheckSection(
        title = stringResource(Res.string.network_check_section_environment),
        lines = listOf(
            // The phone first: a reader of the screenshot places every row under it against a model
            // and a ROM before reading any of them.
            CheckLine(
                label = stringResource(Res.string.network_check_device),
                value = environment.device.model,
            ),
            CheckLine(
                label = stringResource(Res.string.network_check_os),
                value = environment.device.osVersion,
            ),
            CheckLine(
                label = stringResource(Res.string.network_check_app_version),
                value = environment.appVersion,
            ),
            CheckLine(
                label = stringResource(Res.string.network_check_transport),
                value = stringResource(
                    when (environment.transport) {
                        NetworkTransport.WIFI -> Res.string.network_check_transport_wifi
                        NetworkTransport.CELLULAR -> Res.string.network_check_transport_cellular
                        NetworkTransport.ETHERNET -> Res.string.network_check_transport_ethernet
                        NetworkTransport.OTHER -> Res.string.network_check_transport_other
                        NetworkTransport.NONE -> Res.string.network_check_transport_none
                    },
                ),
            ),
            CheckLine(
                label = stringResource(Res.string.network_check_vpn),
                value = stringResource(
                    if (environment.vpnActive) {
                        Res.string.network_check_vpn_on
                    } else {
                        Res.string.network_check_vpn_off
                    },
                ),
                alert = environment.vpnActive,
            ),
            CheckLine(
                label = stringResource(Res.string.network_check_metered),
                value = yesNo(environment.metered),
                alert = environment.metered,
            ),
            CheckLine(
                label = stringResource(Res.string.network_check_proxy),
                value = environment.proxy?.let { proxySummaryText(it) }
                    ?: stringResource(Res.string.network_check_proxy_off),
                alert = environment.proxy != null,
            ),
            CheckLine(
                label = stringResource(Res.string.network_check_doh),
                value = environment.dohProvider?.let { dohProviderLabel(it) }
                    ?: stringResource(Res.string.network_check_doh_off),
                alert = environment.dohProvider != null,
            ),
            CheckLine(
                label = stringResource(Res.string.network_check_custom_tab_app),
                value = appIdentityText(customTabs),
                alert = browsersDiffer,
            ),
            CheckLine(
                label = stringResource(Res.string.network_check_default_browser),
                value = appIdentityText(browser),
                alert = browsersDiffer,
            ),
            // The jar, for the reports this screen cannot otherwise answer. Never flagged: a reader
            // who is not signed in is not in an error state, and a red row here would say they were.
            CheckLine(
                label = stringResource(Res.string.network_check_session),
                value = yesNo(environment.session.signedIn),
            ),
            CheckLine(
                label = stringResource(Res.string.network_check_clearance),
                value = yesNo(environment.session.hasClearance),
            ),
            CheckLine(
                label = stringResource(Res.string.network_check_cookie_names),
                value = environment.session.cookieNames
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(" ")
                    ?: stringResource(Res.string.network_check_cookie_names_none),
            ),
            CheckLine(
                label = stringResource(Res.string.network_check_web_view),
                value = appIdentityText(environment.session.webView),
            ),
        ),
    )
}

@Composable
private fun probeSection(
    title: String,
    result: ProbeResult?,
): CheckSection =
    when (result) {
        null -> CheckSection(
            title = title,
            lines = listOf(
                CheckLine(
                    label = stringResource(Res.string.network_check_status),
                    value = stringResource(Res.string.network_check_pending),
                ),
            ),
        )

        is ProbeResult.Failed -> CheckSection(
            title = title,
            lines = listOf(
                CheckLine(
                    label = stringResource(Res.string.network_check_status),
                    value = stringResource(
                        Res.string.network_check_failed,
                        failureText(result.failure),
                    ),
                    alert = true,
                ),
            ),
        )

        is ProbeResult.Answered -> CheckSection(title = title, lines = timingLines(result))
    }

@Composable
private fun timingLines(result: ProbeResult.Answered): List<CheckLine> {
    val timing = result.timing
    val reused = stringResource(Res.string.network_check_reused)
    val rate = timing.bodyBytesPerSecond()
    return listOf(
        CheckLine(
            label = stringResource(Res.string.network_check_status),
            value = stringResource(Res.string.network_check_status_code, result.statusCode),
            alert = result.statusCode !in 200..299,
        ),
        CheckLine(stringResource(Res.string.network_check_dns), timing.dnsMillis.spanText(reused)),
        CheckLine(stringResource(Res.string.network_check_connect), timing.connectMillis.spanText(reused)),
        CheckLine(stringResource(Res.string.network_check_tls), timing.tlsMillis.spanText(reused)),
        CheckLine(stringResource(Res.string.network_check_first_byte), formatMillis(timing.firstByteMillis)),
        CheckLine(
            label = stringResource(Res.string.network_check_download),
            // Size and elapsed both, never the rate alone: a rate computed off a small body is
            // mostly noise, and only the size on the same line says so.
            value = stringResource(
                Res.string.network_check_download_value,
                formatBytes(timing.bytes),
                formatMillis(timing.totalMillis - timing.firstByteMillis),
            ),
        ),
        CheckLine(
            label = stringResource(Res.string.network_check_rate),
            value = rate?.let(::formatRate) ?: stringResource(Res.string.network_check_rate_immeasurable),
        ),
    )
}

/** Null is "the connection was already open", which is a fact about the probe and not a missing value. */
private fun Long?.spanText(reused: String): String = this?.let(::formatMillis) ?: reused

@Composable
private fun yesNo(value: Boolean): String =
    stringResource(if (value) Res.string.network_check_yes else Res.string.network_check_no)

@Composable
private fun appIdentityText(identity: AppIdentity?): String =
    identity?.let { "${it.label}\n${it.packageId}" } ?: stringResource(Res.string.network_check_app_none)

@Composable
private fun proxySummaryText(proxy: ProxySummary): String {
    val type = stringResource(
        if (proxy.type == ProxyType.HTTP) Res.string.proxy_type_http else Res.string.proxy_type_socks,
    )
    val body = stringResource(
        if (proxy.loopback) Res.string.network_check_proxy_local else Res.string.network_check_proxy_remote,
        type,
        proxy.port,
    )
    return if (proxy.forumOnly) body + stringResource(Res.string.network_check_proxy_forum_only) else body
}

/** The same six words 代理设置 uses for the same six failures — see `ProxyConnectionTester`. */
@Composable
private fun failureText(failure: ProxyConnectionFailure): String =
    stringResource(
        when (failure.kind) {
            ProxyConnectionFailure.Kind.DNS -> Res.string.proxy_test_failure_dns
            ProxyConnectionFailure.Kind.TIMEOUT -> Res.string.proxy_test_failure_timeout
            ProxyConnectionFailure.Kind.CONNECTION -> Res.string.proxy_test_failure_connection
            ProxyConnectionFailure.Kind.SOCKS_AUTHENTICATION -> Res.string.proxy_test_failure_socks_auth
            ProxyConnectionFailure.Kind.TLS -> Res.string.proxy_test_failure_tls
            ProxyConnectionFailure.Kind.OTHER -> Res.string.proxy_test_failure_other
        },
    )

@Preview
@Composable
private fun NetworkCheckScreenPreview() {
    PlazaTheme {
        NetworkCheckScreen(
            state = NetworkCheckUiState(
                environment = NetworkEnvironment(
                    device = DeviceIdentity("Xiaomi 14", "Android 15 (API 35)"),
                    appVersion = "1.2.12",
                    transport = NetworkTransport.WIFI,
                    vpnActive = true,
                    metered = true,
                    proxy = null,
                    dohProvider = null,
                    customTabsProvider = AppIdentity("Chrome", "com.android.chrome"),
                    defaultBrowser = AppIdentity("Chrome", "com.android.chrome"),
                    session = SessionSummary(
                        signedIn = true,
                        hasClearance = true,
                        cookieNames = listOf("session", "cf_clearance", "colorscheme"),
                        webView = AppIdentity("Android System WebView 131.0.6778.39", "com.google.android.webview"),
                    ),
                ),
                forum = ProbeResult.Answered(
                    statusCode = 200,
                    timing = ProbeTiming(
                        dnsMillis = 14,
                        connectMillis = 210,
                        tlsMillis = 180,
                        firstByteMillis = 640,
                        totalMillis = 13_400,
                        bytes = 69_800,
                    ),
                ),
            ),
            onBack = {},
            onRerun = {},
        )
    }
}
