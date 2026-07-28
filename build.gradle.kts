// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.spotless)
}

/**
 * ktlint rule configuration.
 *
 * It lives here rather than in `.editorconfig` because Spotless's ktlint step does not pick that file
 * up — configuring it there looks like it works and silently formats to ktlint's own defaults instead.
 * `.editorconfig` still carries the encoding and indent settings that IDEs read.
 */
val ktlintRules =
    mapOf(
        // intellij_idea, not ktlint_official: the official style reformats this codebase into
        // something markedly harder to read, and the two rules disabled below are most of the reason.
        "ktlint_code_style" to "intellij_idea",

        // Pushes the value of every multi-line assignment onto its own indented line — `val x =` on
        // one line, `listOf(` on the next. Costs a level of indentation on hundreds of lines and buys
        // nothing.
        "ktlint_standard_multiline-expression-wrapping" to "disabled",

        // Puts every parameter on its own line even when they fit comfortably on one, turning
        // compact, readable signatures into six-line blocks.
        "ktlint_standard_function-signature" to "disabled",

        // Composables are PascalCase by framework convention; ktlint's naming rule disagrees with
        // Compose, and the framework wins. Test names are readable sentences in backticks, which is
        // equally deliberate — so the naming rule is off and code review covers naming instead.
        "ktlint_standard_function-naming" to "disabled",

        // Trailing commas keep a diff to one line when a parameter is added.
        "ij_kotlin_allow_trailing_comma" to "true",
        "ij_kotlin_allow_trailing_comma_on_call_site" to "true",
    )

// Formatting is a build gate rather than a convention people are expected to remember:
// `spotlessCheck` runs in CI and `spotlessApply` fixes everything fixable. ktlint is pinned in the
// version catalog so every machine and CI agent formats identically.
spotless {
    kotlin {
        // The project is single-module, so constrain discovery to source roots. Walking the whole
        // repository races AGP while it replaces incremental resource directories during a
        // combined CI invocation, even when build/** is later excluded from formatting.
        target("app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintRules)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintRules)
    }
}
