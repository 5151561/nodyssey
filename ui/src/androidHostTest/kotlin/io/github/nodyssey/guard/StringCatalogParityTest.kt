package io.github.nodyssey.guard

import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * That every locale catalog carries a translation for every key — or a written reason not to.
 *
 * `values/strings.xml` is the Simplified Chinese original, and a key absent from a locale silently
 * falls back to it. The header of `values-en` promises that every absence is deliberate: a string
 * that reads the same in every language — a format that is only punctuation, a proper noun, the
 * app's own name. Nothing enforced that promise, and the first pair of parallel branches broke it:
 * 网络自检 merged first, the translation pass merged second, and the whole screen shipped in
 * Chinese to a reader who had chosen English. This test is that header sentence made checkable.
 *
 * The lists below are exact, in both directions, for the reason the literal ratchet's counts are:
 * a key missing a translation must either get one or be added here as a deliberate act, and a
 * listed key that later gains a translation (or leaves the default catalog) must be struck off, so
 * the list never accretes room for a regression to hide in.
 *
 * The placeholder check rides along because it is the other way a catalog lies: a translation that
 * drops or renumbers a `%1$s` compiles fine and breaks at render time, in one language only.
 */
class StringCatalogParityTest {
    @Test
    fun `every key is translated in every locale, minus the declared identical ones`() {
        val root = repositoryRoot()
        val problems = buildString {
            for ((module, locales) in CATALOGS) {
                val values = File(root, "$module/src/commonMain/composeResources")
                val base = parseCatalog(File(values, "values/strings.xml"))
                check(base.isNotEmpty()) { "no strings parsed from $module/values — the guard is checking nothing" }
                for ((locale, allowedAbsent) in locales) {
                    val translated = parseCatalog(File(values, "$locale/strings.xml"))
                    val orphans = translated.keys - base.keys
                    if (orphans.isNotEmpty()) {
                        appendLine("$module/$locale has keys the default catalog does not: ${orphans.sorted()}")
                    }
                    val missing = base.keys - translated.keys
                    val untranslated = missing - allowedAbsent
                    if (untranslated.isNotEmpty()) {
                        appendLine("$module/$locale is missing translations (translate them, or declare them identical here):")
                        untranslated.sorted().forEach { appendLine("  $it = ${base[it]}") }
                    }
                    val stale = allowedAbsent - missing
                    if (stale.isNotEmpty()) {
                        appendLine("$module/$locale allowlist entries that are translated or gone — strike them off: ${stale.sorted()}")
                    }
                }
            }
        }
        if (problems.isNotEmpty()) fail(problems)
    }

    @Test
    fun `translations keep the placeholders of the original`() {
        val root = repositoryRoot()
        val problems = buildString {
            for ((module, locales) in CATALOGS) {
                val values = File(root, "$module/src/commonMain/composeResources")
                val base = parseCatalog(File(values, "values/strings.xml"))
                for ((locale, _) in locales) {
                    val translated = parseCatalog(File(values, "$locale/strings.xml"))
                    for ((key, text) in translated) {
                        val expected = placeholders(base[key] ?: continue)
                        val actual = placeholders(text)
                        if (expected != actual) {
                            appendLine("$module/$locale/$key: placeholders $actual, but the original has $expected")
                        }
                    }
                }
            }
        }
        if (problems.isNotEmpty()) fail(problems)
    }

    private fun parseCatalog(file: File): Map<String, String> {
        val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return buildMap {
            for (i in 0 until strings.length) {
                val element = strings.item(i) as Element
                put(element.getAttribute("name"), element.textContent)
            }
        }
    }

    private fun placeholders(text: String): List<String> =
        PLACEHOLDER.findAll(text).map { it.value }.sorted().toList()

    private companion object {
        val PLACEHOLDER = Regex("""%\d+\$[a-z]""")

        /**
         * Keys that read the same in every language, so a translation would only be a copy to
         * drift: formats that are all punctuation, unit strings, brand and proper nouns, URL and
         * config placeholders. The three language names are here for the opposite reason — each is
         * deliberately in its own language, the way every language picker names its entries.
         */
        val SAME_IN_EVERY_LANGUAGE = setOf(
            "about_deepflood",
            "account_bio",
            "account_readme",
            "account_telegram_section",
            "account_value_unknown",
            "app_name",
            "assets_board_gain",
            "assets_level",
            "assets_quota_value",
            "assets_quota_value_unknown",
            "composer_app_menu",
            "composer_emoji_group_fluent",
            "composer_image_failed_reason",
            "composer_permission_level",
            "composer_title_count",
            "credit_level",
            "doh_provider_cloudflare",
            "doh_provider_dnspod",
            "doh_provider_google",
            "doh_url_placeholder",
            "imagehost_api_token_label",
            "imagehost_custom_file_field_placeholder",
            "imagehost_custom_form_fields_placeholder",
            "imagehost_custom_header_name_placeholder",
            "imagehost_custom_header_value_placeholder",
            "imagehost_custom_url_path_placeholder",
            "imagehost_custom_url_prefix_placeholder",
            "imagehost_key_label",
            "imagehost_site_placeholder",
            "imagehost_upload_url_placeholder",
            "ledger_amount_gain",
            "ledger_amount_spend",
            "message_markdown_label",
            "message_thread_subtitle",
            "message_thread_subtitle_level",
            "network_check_download_value",
            "network_check_pending",
            "network_check_status_code",
            "network_check_transport_wifi",
            "network_check_vpn",
            "notification_time_pair",
            "offline_state_percent",
            "page_jump_of_total",
            "post_badge_more",
            "post_quote_prefix",
            "post_quote_reply",
            "profile_level_unknown",
            "profile_member_uid",
            "proxy_port_placeholder",
            "proxy_type_http",
            "proxy_type_socks",
            "ruling_action_none",
            "ruling_meta_no_moderator",
            "settings_body_size_value",
            "settings_language_en",
            "settings_language_zh_hans",
            "settings_language_zh_hant",
            "settings_sticker_size_value",
            "settings_version",
            "sign_in_mark",
            "sign_in_verify_brand",
            "space_bio",
            "space_readme",
            "space_uid",
            "space_uid_bio",
            "stardust_compose_ref",
            "stardust_entry_peer",
            "stardust_entry_ref",
            "stardust_receive_confirm_ref_label",
            "transfer_balance_change",
            "transfer_ref",
            "unread_count_capped",
            "viewer_page",
            "vote_percent",
        )

        /**
         * Keys whose Simplified original is already Traditional-correct — grammar glue whose
         * characters do not differ between the scripts, and one proper noun — so `values-zh-rTW`
         * alone may fall back to it.
         */
        val SAME_IN_TRADITIONAL = setOf(
            "composer_emoji_group_acn",
            "doh_test_result",
            "ruling_reason",
            "ruling_target_kind",
        )

        /** module → (locale directory → keys allowed to be absent from it). */
        val CATALOGS = mapOf(
            "ui" to mapOf(
                "values-en" to SAME_IN_EVERY_LANGUAGE,
                "values-zh-rTW" to SAME_IN_EVERY_LANGUAGE + SAME_IN_TRADITIONAL,
            ),
            "designsys" to mapOf(
                "values-en" to emptySet(),
                "values-zh-rTW" to setOf("richtext_sticker_fallback"),
            ),
        )
    }
}
