/*
 * Compose for whichever kind of Android module applied it.
 *
 * `buildFeatures.compose` exists on both the library and the application extension but they share no
 * usable supertype here, so this reacts to whichever one the module actually applied instead of
 * guessing. Applying this plugin alone does nothing — it is always paired with `plaza.android.library`
 * or `plaza.android.application`.
 */
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

// The `com.android.build.api.dsl` interfaces, not the older `BaseAppModuleExtension` and friends:
// AGP 9 defaults to `android.newDsl=true` and registers only these.
pluginManager.withPlugin("com.android.library") {
    extensions.configure<LibraryExtension> {
        buildFeatures {
            compose = true
        }
    }
}

pluginManager.withPlugin("com.android.application") {
    extensions.configure<ApplicationExtension> {
        buildFeatures {
            compose = true
        }
    }
}
