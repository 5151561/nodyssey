plugins {
    `kotlin-dsl`
}

// The convention plugins are precompiled script plugins: every `.gradle.kts` file under
// `src/main/kotlin` becomes a plugin whose id is its file name. They are ordinary Kotlin DSL, so they
// read like the build files they replace rather than like a plugin API.
//
// A precompiled script plugin can only apply a plugin that is on this project's compile classpath,
// which is what the dependencies below are for. Versions still come from the version catalog, so a
// plugin version is declared in exactly one place.
//
// Line comments rather than a block comment on purpose: Kotlin nests block comments, so a stray `/*`
// inside prose — a glob such as `kotlin/*.gradle.kts`, for instance — silently swallows the rest of
// the file, and the only symptom is a plugin reported as not found.
dependencies {
    implementation("com.android.tools.build:gradle:${libs.versions.androidGradlePlugin.get()}")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
