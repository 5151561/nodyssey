package io.github.nodyssey.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start, measured twice: as a fresh sideload runs (no profile, JIT only) and as an install
 * with the committed baseline profile runs. The *gap* between the two is the number that matters —
 * it is what `baseline-prof.txt` buys, measured on the same device in the same run, which is the
 * only comparison an emulator can be trusted with. Absolute emulator timings are not phone timings
 * and are not read as such.
 *
 * On demand, not CI: `./gradlew :benchmark:connectedMinifiedAndroidTest
 * -Pandroid.testInstrumentationRunnerArguments.class=io.github.nodyssey.benchmark.StartupBenchmark`
 * — results land in the module's build outputs as JSON alongside logcat's `Benchmark` lines.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartWithoutProfile() = measureColdStart(CompilationMode.None())

    @Test
    fun coldStartWithBaselineProfile() =
        measureColdStart(
            CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
        )

    private fun measureColdStart(compilation: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = APP,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilation,
            startupMode = StartupMode.COLD,
            iterations = 5,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }

    private companion object {
        const val APP = "io.github.nodyssey"
    }
}
