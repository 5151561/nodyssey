plugins {
    id("plaza.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.plaza.core"
}

dependencies {
    // `api`, not `implementation`: these types appear in this module's own signatures — `AppClock`
    // hands out no coroutines but `MinIntervalGate.spaced` is `suspend`, `HtmlSource` is built on
    // OkHttp's client, and a consumer constructing either needs the same versions on its classpath.
    api(libs.kotlinx.coroutines.core)
    api(libs.okhttp)

    // Only the update source parses JSON, and only its own DTOs, which never leave this module.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
