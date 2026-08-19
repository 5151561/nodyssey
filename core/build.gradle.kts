plugins {
    id("plaza.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.plaza.core"
}

dependencies {
    // The platform-neutral half of what used to live here: the rich-text tree, the ANSI decoder, the
    // site config, `WebUrl`. `api` so that every consumer that already imported those types under
    // `io.github.plaza.core.*` keeps compiling without knowing they now arrive from a module below.
    api(project(":shared"))

    // `api`, not `implementation`: these types appear in this module's own signatures — `AppClock`
    // hands out no coroutines but `MinIntervalGate.spaced` is `suspend`, `HtmlSource` is built on
    // OkHttp's client, and a consumer constructing either needs the same versions on its classpath.
    api(libs.kotlinx.coroutines.core)
    api(libs.okhttp)

    // `api` because `AnsiSpan` is `@Serializable` and a consumer nests it inside its own serializable
    // types — the generated serializer is part of this module's surface, not an implementation detail.
    // The update source's own DTOs never leave here, and would have been happy with `implementation`.
    api(libs.kotlinx.serialization.json)

    // The image data-usage policy is a Coil interceptor. Only `coil-core` — how an image is *drawn*
    // is a Compose question and belongs to the module that draws it.
    api(libs.coil.core)

    // `implementation`: reading why a fetch failed means naming Coil's `HttpException`, but that type
    // stops here — [ImageLoadFailure] is what leaves, and a consumer matches on that.
    implementation(libs.coil.network.core)

    // `api`, not `implementation`: [CompatSvgParser] *is* a `coil3.svg.Svg.Parser`, so the module
    // that hands one to `SvgDecoder.Factory` needs that interface on its own classpath.
    api(libs.coil.svg)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // The interceptor reads ConnectivityManager, so its test needs a Context.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
