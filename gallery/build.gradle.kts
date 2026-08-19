plugins {
    // Not `alias(libs.plugins...)`: `build-logic` already puts the Kotlin Gradle plugin on the
    // root build's classpath, and asking for it again by version fails as a duplicate request.
    // The version is still the catalog's — `build-logic/build.gradle.kts` reads it from there.
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    id("plaza.dependency-locking")
}

kotlin {
    jvmToolchain(21)

    // One target, and it is the point of the module: everything here resolves `:designsys` and
    // `:shared` through a variant that has no Android in it. A module that compiles is a weaker claim
    // than a window that opens.
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":designsys"))

            // The parsers, so the renderer below is fed the same way the app feeds it rather than
            // from a hand-built node tree that only proves the renderer draws what it is given.
            implementation(project(":shared"))

            // Skiko and the AWT window for whichever machine this is running on. The only dependency
            // in the repository that is chosen by the host rather than by the build.
            implementation(compose.desktop.currentOs)
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))

            // `runComposeUiTest`, which needs no window and no display — which is the only reason
            // this module can be a CI gate rather than something a person has to remember to open.
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        // `run` is the whole point; no `nativeDistributions` block, because nothing packages this
        // and a .dmg of a component gallery is not a thing anyone wants.
        mainClass = "io.github.plaza.gallery.GalleryKt"
    }
}
