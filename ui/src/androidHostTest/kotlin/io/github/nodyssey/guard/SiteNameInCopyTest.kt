package io.github.nodyssey.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * That user-visible copy does not name one forum when the app can be pointed at another.
 *
 * The app named NodeSeek in twenty-three strings, which was true for as long as there was one site
 * and quietly false the moment there were two: a reader on DeepFlood was told 「登录 NodeSeek」 on
 * the sign-in card, 「连不上 NodeSeek」 when the network dropped, and 「NodeSeek 靠鸡腿升级」 on a
 * level wall. Nineteen of them now take the site's name as an argument — `siteName` — and this is
 * what stops the twentieth from being written.
 *
 * The allowlist is exact in both directions, like the parity guard's: an entry that stops naming
 * NodeSeek has to be struck off, so the list cannot quietly become a place to hide a regression.
 */
class SiteNameInCopyTest {

    @Test
    fun `only the strings that mean NodeSeek say NodeSeek`() {
        val catalog = File(repositoryRoot(), "ui/src/commonMain/composeResources/values/strings.xml")
        val text = catalog.readText()
        check(text.isNotEmpty()) { "empty catalog — the guard is checking nothing" }

        val naming =
            Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(text)
                .filter { "NodeSeek" in it.groupValues[2] }
                .map { it.groupValues[1] }
                .toSet()

        assertEquals(
            "copy that names NodeSeek as a literal changed — parameterise it with `siteName`, or " +
                "add it here with the reason it genuinely means NodeSeek and not the active site",
            MEANS_NODESEEK,
            naming,
        )
    }

    /** The same, for the placeholder half: a `%1$s` nobody fills renders as `%1$s`. */
    @Test
    fun `every site-named string is passed a site name`() {
        val root = repositoryRoot()
        val catalog = File(root, "ui/src/commonMain/composeResources/values/strings.xml").readText()
        val sources =
            productionSources(File(root, "ui")).map { it.readText() }

        val problems = buildString {
            Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(catalog)
                .forEach { match ->
                    val (key, body) = match.destructured
                    if (key !in SITE_NAMED) return@forEach
                    check("%" in body) { "$key is listed as site-named but carries no placeholder" }
                    if (key in PASSED_INDIRECTLY) return@forEach
                    val called = sources.any { source ->
                        Regex("""Res\.string\.$key\b[^)]*siteName""").containsMatchIn(source)
                    }
                    if (!called) appendLine("$key takes a site name but no call site passes one")
                }
        }
        if (problems.isNotEmpty()) fail(problems)
    }

    private companion object {
        /**
         * Copy that means NodeSeek whichever site is active, and so is right to name it.
         *
         * The three App Links strings because the intent filter carries nodeseek.com and nothing
         * else — a DeepFlood link does not open this app, and saying otherwise would be a promise
         * the manifest does not keep. The image host because nodeimage.com signs its users in with
         * a NodeSeek account regardless of which forum is being read.
         */
        val MEANS_NODESEEK = setOf(
            "settings_app_links_hint_on",
            "onboarding_app_links_body",
            "help_app_links_body",
            "imagehost_error_session_required",
        )

        /**
         * Site-named copy whose argument arrives through a variable, where the scan below cannot
         * see it.
         *
         * `OnboardingScreen` picks a `StringResource` per page and renders them all through one
         * `stringResource(body, siteName)`, so the key never appears beside the argument in the
         * source. Listed rather than special-cased in the regex: a list of two names is honest
         * about being a list, and a regex that chased Kotlin variables would be a parser.
         */
        val PASSED_INDIRECTLY = setOf("onboarding_welcome_body")

        /** The copy that takes the active site's name as an argument. */
        val SITE_NAMED = setOf(
            "profile_session_active", "about_forum_stats_snapshot", "about_unofficial_notice",
            "account_email_change_on_site", "account_telegram_dialog_body", "status_network_body",
            "status_challenge_body", "status_sign_in_body", "status_level_required_body",
            "status_level_required_body_unknown", "status_query_too_short_body",
            "profile_signed_out_title", "profile_sign_in", "status_signed_in_title",
            "status_rate_limited_body", "status_not_wired_body", "invite_body", "sign_in_title",
            "onboarding_welcome_body",
        )
    }
}
