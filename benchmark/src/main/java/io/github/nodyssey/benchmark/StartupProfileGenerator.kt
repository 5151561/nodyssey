package io.github.nodyssey.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks the journey `app/src/main/baseline-prof.txt` is a record of.
 *
 * The output is *which methods ran*, so the journey is the one every session actually starts with:
 * cold launch to the drawn feed, then the first screens of scrolling. Everything on that path — the
 * dependency graph, Room opening, the feed's paging machinery, Compose's first measure-and-draw —
 * gets compiled ahead of time on install instead of being interpreted while the user watches the
 * skeleton screen.
 *
 * Run on a booted emulator (API 33+; profile capture from an unrooted shell starts there) against
 * the **nonMinified** variant — capturing from the R8 build records the names R8 invented for that
 * one build, and a committed profile has to speak source names:
 *
 *     ./gradlew :benchmark:connectedNonMinifiedAndroidTest \
 *         -Pandroid.testInstrumentationRunnerArguments.class=io.github.nodyssey.benchmark.StartupProfileGenerator
 *
 * then copy the pulled `*-baseline-prof.txt` from
 * `benchmark/build/outputs/connected_android_test_additional_output/` over
 * `app/src/main/baseline-prof.txt` and commit the diff. Regenerate when the startup path changes
 * shape — a new screen on the launch journey, a swapped paging library — not on every release; a
 * profile is a hint list, and a slightly stale one still compiles almost all of the right methods.
 */
@RunWith(AndroidJUnit4::class)
class StartupProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collect(packageName = APP) {
            pressHome()
            startActivityAndWait()
            // The feed has drawn once its tab label is on screen; scrolling from there exercises
            // the row composables and the paging window the way a reader's first minute does.
            device.wait(Until.hasObject(By.pkg(APP).text("首页")), LAUNCH_TIMEOUT_MS)
            val list = device.findObject(By.pkg(APP).scrollable(true))
            if (list != null) {
                repeat(3) {
                    list.fling(Direction.DOWN)
                    device.waitForIdle()
                }
            }
        }
    }

    private companion object {
        const val APP = "io.github.nodyssey"
        const val LAUNCH_TIMEOUT_MS = 30_000L
    }
}
