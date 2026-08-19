plugins {
    id("plaza.android.library")
    id("plaza.android.compose")
}

android {
    namespace = "io.github.plaza.designsys"
}

dependencies {
    // `api` because `rememberTerminalText` takes `AnsiSpan` in its signature and `RichContent` takes a
    // `RichNode`: a consumer building either has to be able to name the type.
    //
    // `:shared` rather than `:core`, which is what this used to say. The four symbols that made the
    // difference were `core/image`'s — the failure vocabulary and the metered-image extra — and they
    // are now this module's own, because both halves of that package's audience are on this side of
    // the boundary: a component reads them, and the app that enforces the preference can see here.
    // What is left in `:core` is an Android library with OkHttp on its api surface, and depending on
    // it is what stopped this module from ever compiling for anything but Android.
    api(project(":shared"))

    // `api`, not `implementation`: a consumer writes Compose against these types in its own source,
    // and both sides have to agree on one BOM or two Compose versions end up on the same classpath.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.material.icons.core)

    // The seed-colour scheme generator. `implementation`, not `api`: a caller hands `PlazaTheme` a
    // `Color` and gets a `ColorScheme` back, and never names an Hct or a DynamicScheme itself.
    implementation(libs.material.color.utilities)

    // `BackHandler`: the emoji panel stands in for the keyboard, so back has to dismiss it first.
    // Reached from `AndroidBackHandler.kt` and nowhere else — Compose Multiplatform has the same
    // function in `commonMain` and androidx publishes no `ui-backhandler`, so the one call site goes
    // through this module's own wrapper rather than naming either artifact.
    implementation(libs.androidx.activity.compose)

    // Custom Tabs: a thread is mostly other people's links, and handing each one to the system
    // browser puts a task switch between the reader and the thread they were in.
    // Reached from `AndroidCustomTabUriHandler.kt` and nowhere else; what the rest of the module
    // knows about opening a link is Compose's own `LocalUriHandler`.
    implementation(libs.androidx.browser)

    // Avatars load over the network. Only `coil-compose` — the GIF and SVG decoders are a decision
    // about a particular site's content, so they stay with the app that needs them.
    implementation(libs.coil.compose)

    // `api`, not `implementation`: `AllowMeteredImage` is an `Extras.Key` and `allowMeteredImage`
    // extends `ImageRequest.Builder`, so both names are on this module's surface. It arrives through
    // `coil-compose` anyway; saying so is what keeps that an accident rather than the reason.
    api(libs.coil.core)

    // `implementation`: reading why a fetch failed means naming Coil's `HttpException`, but that type
    // stops here — [ImageLoadFailure] is what leaves, and a consumer matches on that. Carried over
    // verbatim from `:core`, along with the code that made it true.
    implementation(libs.coil.network.core)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // The fake Coil engine: image-size-dependent layout — a badge kept at natural size, two badges
    // sharing a row — cannot be asserted against an image that never arrives.
    testImplementation(libs.coil.test)

    // Not optional, and not inherited from the app: `ui-test-junit4` launches its host activity from
    // the manifest this contributes, so a module running Compose tests has to declare it itself.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
