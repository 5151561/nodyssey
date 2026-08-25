package io.github.nodyssey.smoke

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Launches the R8-minified, resource-shrunk build and checks it survives to a drawn frame.
 *
 * Startup is where minification failures explode: the dependency graph is built, Room opens the
 * database, serializers restore the navigation stack, and Compose Resources load the strings — a
 * class R8 renamed or a resource the shrinker removed fails right here, as a crash before the first
 * frame. So the assertion is deliberately humble: the bottom navigation is on screen with its
 * labels, and still is after switching tabs. Journey depth belongs to `:app`'s own androidTest,
 * which runs against debug where Compose rules work.
 *
 * The tab labels are spelled out rather than read from `Res.string`: reading them would mean
 * depending on `:ui`, whose classes would then exist unminified in this APK while the app carries
 * renamed ones — the exact sharing this module exists to avoid. If a label changes, this match
 * fails with a screenshot in the CI artifacts, which is a cheap price for the isolation.
 */
@RunWith(AndroidJUnit4::class)
class MinifiedStartupSmokeTest {
    @Test
    fun minifiedAppStartsAndDrawsItsNavigation() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context = InstrumentationRegistry.getInstrumentation().context

        val launch =
            requireNotNull(context.packageManager.getLaunchIntentForPackage(APP)) {
                "the minified app is not installed"
            }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launch)

        val drew = device.wait(Until.hasObject(By.pkg(APP).text("首页")), STARTUP_TIMEOUT_MS)
        if (!drew) dumpDiagnostics(device, "startup-timeout")
        assertTrue(
            "the app did not draw its bottom navigation within ${STARTUP_TIMEOUT_MS / 1000}s of launch",
            drew,
        )

        val search = device.wait(Until.findObject(By.pkg(APP).text("搜索")), UI_TIMEOUT_MS)
        assertNotNull("the 搜索 tab is missing from the bottom navigation", search)
        search.click()

        // Still alive and still drawing after a navigation — the tab bar persists across tabs.
        assertTrue(
            "the app stopped drawing after switching to 搜索",
            device.wait(Until.hasObject(By.pkg(APP).text("首页")), UI_TIMEOUT_MS),
        )
    }

    /**
     * A screenshot and the window hierarchy, pulled into the CI artifacts with the test report.
     *
     * Added after a CI-only timeout that logcat could not explain: the process started, the
     * Application ran, and then nothing — no crash, no activity, no way to tell what the screen
     * actually showed. `additionalTestOutputDir` is the directory AGP pulls off the device after a
     * connected run; absent (an unusual runner) the diagnostics are skipped rather than failing the
     * test a second time.
     */
    private fun dumpDiagnostics(device: UiDevice, name: String) {
        val dirPath =
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        runCatching {
            val dir = File(dirPath).apply { mkdirs() }
            device.takeScreenshot(File(dir, "$name.png"))
            File(dir, "$name-hierarchy.xml").outputStream().use { device.dumpWindowHierarchy(it) }
            // The app's thread stacks, captured while it is still wedged — the screenshot showed a
            // first frame that never came, and only this says what the main thread is doing
            // instead. `debuggerd -j` needs no root on the userdebug emulator images CI runs, and
            // the process is gone by the time any post-failure workflow step could ask.
            val pid = device.executeShellCommand("pidof $APP").trim()
            if (pid.isNotEmpty()) {
                File(dir, "$name-stacks.txt").writeText(device.executeShellCommand("debuggerd -j $pid"))
            }
        }
    }

    private companion object {
        /** The minified build type carries no `applicationIdSuffix` — this is the release id. */
        const val APP = "io.github.nodyssey"

        /** Generous because the CI emulator cold-starts the process under swiftshader. */
        const val STARTUP_TIMEOUT_MS = 30_000L
        const val UI_TIMEOUT_MS = 10_000L
    }
}
