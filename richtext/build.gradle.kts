plugins {
    id("plaza.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // The four targets its two consumers need between them: `:designsys` and `:gallery` want android
    // and the desktop JVM, `:shared` adds the two Apple ones. A module below another has to have at
    // least the targets that one declares, or the dependency is a variant that does not resolve.
    jvm()
    iosArm64()
    macosArm64()

    android {
        namespace = "io.github.plaza.richtext"
    }

    sourceSets {
        commonMain.dependencies {
            // `RichNode` and `AnsiSpan` are `@Serializable`, and consumers nest them inside their own
            // serializable types — so the generated serializers are part of this module's surface
            // rather than an implementation detail. The same reasoning `:shared` recorded while these
            // types were still in it.
            api(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
