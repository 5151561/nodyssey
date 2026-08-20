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

            // The Markdown reader, so the renderer below is fed the same way the app feeds it rather
            // than from a hand-built node tree that only proves the renderer draws what it is given.
            //
            // `:richtext` and not `:shared` since step D1: a component gallery used to resolve SQLite,
            // Paging and a network layer to draw a paragraph, which is what that lockfile said.
            implementation(project(":richtext"))

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

/*
 * The two artefacts `compose.desktop.currentOs` picks by host, left out of the lockfile.
 *
 * A lockfile records the graph so that the repository decides it rather than whichever machine ran
 * the build — and these are the one dependency in the repository where that is the wrong goal on
 * purpose (see `jvmMain` above). Locked, they pin the Skiko and Compose desktop natives of the
 * machine that last ran `--write-locks`; a Mac writes `macos-arm64`, CI resolves `linux-x64`, and
 * STRICT mode fails it both ways at once — "resolved something not in the lock state" and "did not
 * resolve something that is". Wildcards rather than the two exact coordinates because the host suffix
 * is the whole thing being ignored.
 *
 * Everything else in this module stays locked; nothing else here is chosen by the host.
 */
dependencyLocking {
    ignoredDependencies.addAll(
        listOf(
            "org.jetbrains.compose.desktop:desktop-jvm-*",
            "org.jetbrains.skiko:skiko-awt-runtime-*",
        ),
    )
}

compose.desktop {
    application {
        // `run` is the whole point; no `nativeDistributions` block, because nothing packages this
        // and a .dmg of a component gallery is not a thing anyone wants.
        mainClass = "io.github.plaza.gallery.GalleryKt"
    }
}
