package io.github.nodyssey.core

// `kotlin.concurrent`, not `kotlin.jvm`: this file is common code and the JVM annotation does not
// exist on the Apple targets.
import kotlin.concurrent.Volatile

/**
 * Both sites' Turnstile sitekey — see [Site.turnstileSitekey] for the measurement and for how to
 * take it again. Named once so that the two entries are visibly the *same* key rather than two
 * literals that happen to match.
 *
 * Top level rather than in [Site]'s companion: an enum's entries are constructed before its
 * companion object is initialised, so an entry cannot read one.
 */
private const val SHARED_TURNSTILE_SITEKEY = "0x4AAAAAAAaNy7leGjewpVyR"

/**
 * The forums this app can be pointed at, and everything that differs between them.
 *
 * DeepFlood is not a second scraper. It runs NodeSeek's own software: measured on 2026-09-10 against
 * the live site, all twelve list selectors and all thirteen detail selectors in [html.Selectors]
 * match, the page carries `id="nsk-body"`, and `/api/content/list-categories`, `/api/stardust/list`,
 * `/api/preference/list`, `/api/notification/unread-count` and `/api/account/find/` all answer on the
 * same paths. So the whole of [NodeSeekSite] — every path, every marker, every selector — is shared
 * vocabulary, and this enum holds the short list of things that are not.
 *
 * An enum rather than a config file: the set is closed, each entry is a site somebody has actually
 * checked, and a site added here has to state every field or fail to compile.
 */
enum class Site(
    /** What the switcher calls it. The site's own wordmark, not a translation. */
    val displayName: String,
    /** Origin, no trailing slash — what [NodeSeekSite.BASE_URL] resolves to while this site is active. */
    val origin: String,
    /**
     * Every host this site answers on, bare domain included.
     *
     * Two jobs. It is the trusted-host list the in-app web view is held to, and it is where a session
     * is looked for: a sign-in finished on the bare domain sets a host-only cookie there, which a jar
     * read at `www` alone cannot see — see [io.github.plaza.core.net.SiteConfig.sessionUrls].
     */
    val hosts: Set<String>,
    /**
     * The Cloudflare Turnstile sitekey the in-app sign-in form renders with.
     *
     * **The two sites share one.** Measured on 2026-09-10 by loading each `/signIn.html` and reading
     * the widget's own request to `challenges.cloudflare.com`, which carries the key in its path:
     * both answer `0x4AAAAAAAaNy7leGjewpVyR`. One Turnstile application with both hostnames on its
     * allow-list, which is what the sites being one deployment of one codebase predicts.
     *
     * Still a per-site field rather than a constant, because it is a fact *about a site* — the two
     * agreeing today is a measurement, not a guarantee — and a site added later states its own.
     *
     * **How to re-measure, because the obvious way does not work.** The key appears nowhere in the
     * markup or in any script bundle: `turnstile.render()` is handed it at runtime. Searching the
     * page for it finds nothing on NodeSeek either, which is what makes a failed search look like a
     * finding. Load the sign-in page and read
     * `performance.getEntriesByType("resource")` for the `challenges.cloudflare.com` URL instead.
     */
    val turnstileSitekey: String,
    /**
     * Hosts this site hands its sign-in to, which the in-app web view must therefore be allowed onto.
     *
     * DeepFlood's sign-in page offers 「NodeSeek一键登录」 beside its own form, and that button is
     * `window.open("https://www.nodeseek.com/connect?target=DeepFlood")` — captured by intercepting
     * the call on the live page on 2026-09-10, rather than inferred from the label. So a reader
     * signing into DeepFlood legitimately ends up on nodeseek.com mid-flow, and a trusted-host set
     * of DeepFlood's own hosts alone would refuse the one hop the flow is made of.
     *
     * Kept apart from [hosts] because the two answer different questions. This widens only
     * [NodeSeekSite.isTrustedWebViewUrl] — where the web view may go. It must not widen
     * `isOwnSiteUrl`: a nodeseek.com link *inside a DeepFlood post* is a link to another forum, and
     * opening it as an internal route would show a thread from the wrong site under the wrong ids.
     */
    val signInProviderHosts: Set<String> = emptySet(),
    /**
     * The site's own 一键登录 page, when it lets another forum's account sign its readers in.
     *
     * DeepFlood's sign-in page carries 「NodeSeek一键登录」 pointing at `/nsSignIn.html`, and that
     * page's button is `window.open("https://www.nodeseek.com/connect?target=DeepFlood")` — both
     * read off the live site on 2026-09-10, the second by intercepting the call rather than by
     * reading the label.
     *
     * **The path, not the `connect` URL.** Opening the provider's authorisation URL directly would
     * mean reproducing whatever the flow does when it finishes — a `postMessage` back to the opener,
     * a `window.close()`, a redirect — and none of that has been established. Handing the site its
     * own page instead leaves the whole dance to the code that owns it; the app's web view already
     * supports the `window.open` it needs (`setSupportMultipleWindows`), and
     * [io.github.nodyssey.ui.login.WebViewGoal.SIGN_IN] closes it as soon as a session lands,
     * however it landed.
     */
    val oneTapSignIn: OneTapSignIn? = null,
) {
    NODESEEK(
        displayName = "NodeSeek",
        origin = "https://www.nodeseek.com",
        hosts = setOf("www.nodeseek.com", "nodeseek.com"),
        turnstileSitekey = SHARED_TURNSTILE_SITEKEY,
    ),
    DEEPFLOOD(
        displayName = "DeepFlood",
        origin = "https://www.deepflood.com",
        hosts = setOf("www.deepflood.com", "deepflood.com"),
        turnstileSitekey = SHARED_TURNSTILE_SITEKEY,
        signInProviderHosts = setOf("www.nodeseek.com", "nodeseek.com"),
        oneTapSignIn = OneTapSignIn(path = "/nsSignIn.html", providerName = "NodeSeek"),
    ),
    ;

    /**
     * The two-letter tile the sign-in card leads with.
     *
     * Derived here rather than kept as a string resource, which is what it used to be: `NS` is not
     * copy and has no translation — it is the site's initials, and a second site makes that a fact
     * about the site rather than a constant of the app.
     */
    val mark: String get() = displayName.filter { it.isUpperCase() }.take(2)

    /** Where a session for this site can legitimately live. HTTPS only; the app never asks over http. */
    val sessionUrls: List<String> get() = hosts.map { "https://$it" }

    /**
     * What this site's storage is called, appended to a database or preference file's name.
     *
     * **Empty for [NODESEEK], and that is load-bearing.** Every installed device already has
     * `nodeseek.db` and a `post-composer` preferences file; a suffix here would rename both, and a
     * renamed store is indistinguishable — from the app's side — from the user never having opened
     * the app. The site that shipped alone keeps the names it shipped with, and only the sites added
     * afterwards get a suffix.
     *
     * Partitioning is not tidiness. Post ids are per-site and they overlap: DeepFlood's newest thread
     * on 2026-09-10 was `/post-38952-1`, an id NodeSeek passed years ago. Every cache table in
     * [io.github.nodyssey.data.local] is keyed on a bare `postId`, so one file for both sites means
     * one site's thread served under the other's title.
     */
    val storageSuffix: String get() = if (this == NODESEEK) "" else "-${name.lowercase()}"

    /** The persisted form of this choice. The name, so that reordering the enum cannot rewrite it. */
    val storedValue: String get() = name

    companion object {
        /** The site a first launch lands on, and what an unreadable stored choice falls back to. */
        val DEFAULT = NODESEEK

        /** Reads back [storedValue], tolerating anything — a removed site, a corrupted file, null. */
        fun ofStoredValue(value: String?): Site = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/**
 * Which [Site] this process is talking to.
 *
 * A process-level value rather than something threaded through the graph, for the same reason 语言 is
 * one: the answer is needed by code that runs before anything asynchronous has finished and in
 * places that have no dependency graph to reach into — a `WorkManager` worker waking a cold process,
 * the notification poller, `attachBaseContext`. The shell installs it once at startup from a
 * synchronous mirror of the stored choice, before the container is built.
 *
 * **It is written once per process and never flipped underneath a running graph.** Changing sites
 * restarts the app's UI stack from scratch (see `applySiteSwitch`), because the alternative —
 * flipping this while repositories built against the old site still hold coroutines in flight — is a
 * request issued to one site whose rows land in the other site's database. The window is small and
 * the corruption is silent, which is the worst combination available. A restart costs a frame.
 */
object ActiveSite {
    @Volatile
    private var installed: Site = Site.DEFAULT

    /** The site every URL in [NodeSeekSite] is built against right now. */
    val current: Site get() = installed

    /**
     * Called by the platform shell at startup, before the dependency graph exists.
     *
     * Idempotent and unguarded: a second call with the same value is what every launch after the
     * first does, and there is no state here to protect — the guarantee that nothing is *running*
     * when this changes is the restart's, not this object's.
     */
    fun install(site: Site) {
        installed = site
    }
}

/**
 * A site's 一键登录: the page that offers it, and whose account it is.
 *
 * The provider's name is carried rather than derived from [Site.signInProviderHosts], because it is
 * shown to a reader — 「使用 NodeSeek 登录」 — and a hostname is not a name.
 */
data class OneTapSignIn(
    /** Site-relative, joined to the site's own origin. */
    val path: String,
    /** What the button calls the provider. Its own wordmark, not a translation. */
    val providerName: String,
    /**
     * Presses [path]'s own 一键登录 button as soon as the page loads, so the reader does not have to
     * press a second button that says what they already said.
     *
     * **Why the app cannot skip the page instead.** The provider's authorisation URL delivers its
     * result to `window.opener` — read off `nodeseek.com/static/js/connect.*.js` on 2026-09-10:
     * `fetch("/api/cAuth?target=…")`, then `window.opener.postMessage({source:"ns-connect", …})` for
     * each allowed origin, then `window.close()`, the whole block guarded by
     * `window.opener !== window`. Opened as a top-level page there is no opener to post to, so the
     * flow completes into nothing. The site's own page has to be the opener; the most the app can do
     * is stop making the reader click through it.
     *
     * Idempotent by construction: `onPageFinished` fires more than once — the opener page is still
     * loading things while the popup runs — and a second click would open a second popup.
     *
     * Selector by button text, matching what the page ships today. If it stops matching, nothing is
     * clicked and the reader sees the page with its button, which is exactly where this started.
     */
    val triggerScript: String =
        """
        (function () {
          if (window.__nodysseyOneTap) return;
          var b = Array.prototype.slice.call(document.querySelectorAll('a,button'))
            .filter(function (e) { return /一键登录/.test(e.textContent || ''); })[0];
          if (!b) return;
          window.__nodysseyOneTap = 1;
          b.click();
        })();
        """,
)
