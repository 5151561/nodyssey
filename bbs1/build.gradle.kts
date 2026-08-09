plugins {
    id("plaza.android.application")
    id("plaza.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    // Working identifiers, not a shipped name: the client has no public branding yet, and an
    // applicationId is free to change right up until the first release is installed on someone's
    // device. Renaming after that point orphans every install, so settle the name before tagging one.
    namespace = "io.github.bbs1"
    defaultConfig {
        applicationId = "io.github.bbs1"
        // Declared in `gradle.properties` under this app's own prefix; see the note there.
        versionCode = providers.gradleProperty("bbs1.versionCode").get().toInt()
        versionName = providers.gradleProperty("bbs1.versionName").get()
    }

    // No signing config yet on purpose: nothing has shipped, so there is no key identity to keep.
    // When a release pipeline appears, mirror `app/build.gradle.kts` — env-var keystore, unsigned
    // fallback — rather than inventing a second convention.
}

dependencies {
    // The Compose theme and components shared with every app in this repository. The dependency only
    // goes this way: `:designsys` cannot see this module.
    implementation(project(":designsys"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)

    // Arch Components
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)

    // The instance list is small and read as a whole, so it lives in DataStore as JSON rather than
    // in a database. Room enters when per-site content caching does.
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    // Local tests: plain JVM — DataStore's preferences core runs without Android, so no Robolectric.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
