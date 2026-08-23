package io.github.nodyssey.data.dns

/**
 * "测试解析" — one lookup of the forum's own hostname through whatever the app is currently resolving
 * with, so a saved server is verified by the same resolver every request will use rather than by a
 * client built specially for the test.
 *
 * The interface is here and the implementation is not, for the same reason `ProxyConnectionTester`'s
 * is not: performing a lookup means naming a resolver, and on Android that is OkHttp's `Dns`.
 */
interface DnsResolutionTester {
    suspend fun resolve(): Result<DnsResolution>
}

/**
 * What a lookup answered.
 *
 * The addresses are shown rather than counted: someone who turned this on because a domain was being
 * hijacked can recognise the difference between the real answer and the one their network was
 * handing out, and no wording of "成功" carries that.
 */
data class DnsResolution(
    val host: String,
    val addresses: List<String>,
    val elapsedMillis: Long,
)

/**
 * 加密 DNS as the dependency graph carries it: the setting, the test, and what this platform can
 * actually act on.
 *
 * One member on `AppContainer` rather than three, because the three are one feature and a platform
 * either has it or does not. Both platforms have it — Android hands its clients a resolver, Apple
 * configures the process's default privacy context — but they do not have all of it, which is what
 * [capabilities] is for.
 */
class DohSupport(
    val settings: DohSettings,
    val tester: DnsResolutionTester,
    val capabilities: DohCapabilities,
)

/**
 * The parts of 加密 DNS that are not the same on both platforms.
 *
 * Not a list of what is implemented — a list of what the platform's resolver *can be asked*. Android
 * runs the resolver itself, inside the app's own HTTP clients, so both answers are yes there. Apple
 * hands the question to the system: `nw_privacy_context_require_encrypted_name_resolution` takes a
 * server and a boolean, and neither the record types it queries nor a retreat to cleartext is part
 * of that conversation.
 *
 * The screen reads these to decide which rows exist, rather than drawing a control that would store
 * a value nothing reads.
 */
data class DohCapabilities(
    /**
     * Whether the resolver can be told to skip AAAA queries — true where the app *is* the resolver.
     */
    val canChooseRecordTypes: Boolean,
    /**
     * Whether a failed encrypted lookup may retreat to the platform resolver.
     *
     * False on Apple, and not for lack of trying: the resolver config passed to
     * `nw_privacy_context_require_encrypted_name_resolution` is documented to take effect *only*
     * while `require_encrypted_name_resolution` is true, and while that is true "all cleartext name
     * resolution will be blocked". There is no third state to offer.
     */
    val canFallBackToSystem: Boolean,
)
