package io.github.nodyssey.render

import androidx.compose.ui.test.SemanticsNodeInteraction
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue

/**
 * A picture of a screen, taken on the JVM, for the pull request that changes it.
 *
 * This is not a baseline. `:designsys` pins its components to goldens under
 * `src/androidHostTest/snapshots/` and fails on drift, because that module rides Material 3 alpha
 * versions and a version number says nothing about what a bump *draws*. Screens are the other
 * trade: there are far more of them, they change on purpose several times a week, and a golden per
 * screen would put a batch of PNGs in front of a reviewer on every UI change. What a reviewer
 * needs here is the picture, not a verdict about it.
 *
 * So the images are build output. Nothing is committed, nothing is compared, and an ordinary test
 * run writes no file at all — [assumeRendering] skips these classes unless the run asked for
 * pictures:
 *
 *     ./gradlew :ui:testAndroidHostTest -PrenderUi --tests '*NetworkCheckScreenRenderTest'
 *
 * They land in `ui/build/outputs/renders/`, and what goes in the pull request is the PNG itself.
 *
 * A render test is written the same way as any other Robolectric screen test in this module —
 * `@GraphicsMode(NATIVE)`, a `@Config` naming the window, a `createComposeRule` — and differs only
 * in what it does at the end: it captures instead of asserting. Where a screen has states worth
 * seeing side by side, give each one its own test; a compose rule takes one `setContent`.
 */
internal fun assumeRendering() {
    assumeTrue(
        "pictures are off — add -PrenderUi to write them",
        System.getProperty("nodyssey.render") == "true",
    )
}

/**
 * Writes this node as `ui/build/outputs/renders/[name].png`.
 *
 * The path is relative because Gradle runs a test with the module directory as its working
 * directory — the same assumption `:designsys` makes when it names its goldens, and the same one
 * `repositoryRoot()` in the guard tests walks up from.
 */
internal fun SemanticsNodeInteraction.captureRender(name: String) {
    captureRoboImage(filePath = "build/outputs/renders/$name.png")
}
