package io.github.nodyssey.guard

import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * That copy which takes the active site's name is actually handed one.
 *
 * The app named NodeSeek in twenty-three strings, which was quietly false the moment there were two
 * sites. Nineteen of them now take the site's name as an argument — `siteName` — and a `%1$s` no call
 * site fills renders as `%1$s`.
 */
class SiteNameInCopyTest {
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
