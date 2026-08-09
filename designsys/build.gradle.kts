plugins {
    id("plaza.android.library")
    id("plaza.android.compose")
}

android {
    namespace = "io.github.plaza.designsys"
}

dependencies {
    // `api` because `rememberTerminalText` takes `AnsiSpan` in its signature: a consumer building the
    // list has to be able to name the type. Nothing else here reaches into `:core`.
    api(project(":core"))

    // `api`, not `implementation`: a consumer writes Compose against these types in its own source,
    // and both sides have to agree on one BOM or two Compose versions end up on the same classpath.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.material.icons.core)

    // `BackHandler`: the emoji panel stands in for the keyboard, so back has to dismiss it first.
    implementation(libs.androidx.activity.compose)

    // Custom Tabs: a thread is mostly other people's links, and handing each one to the system
    // browser puts a task switch between the reader and the thread they were in.
    implementation(libs.androidx.browser)

    // Avatars load over the network. Only `coil-compose` — the GIF and SVG decoders are a decision
    // about a particular site's content, so they stay with the app that needs them.
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // Not optional, and not inherited from the app: `ui-test-junit4` launches its host activity from
    // the manifest this contributes, so a module running Compose tests has to declare it itself.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
