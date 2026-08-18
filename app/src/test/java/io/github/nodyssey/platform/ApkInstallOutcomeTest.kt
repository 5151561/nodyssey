package io.github.nodyssey.platform

import android.content.pm.PackageInstaller
import io.github.plaza.core.update.InstallFailure
import io.github.plaza.core.update.InstallOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one place `PackageInstaller`'s status integers are read.
 *
 * Worth its own test because the mapping is the sort of thing that looks obviously right and gets one
 * line wrong: `STATUS_FAILURE_ABORTED` is the user saying no, and reading it as a failure would put
 * an error on screen for their own decision. The constants are compile-time values, so this needs no
 * device and no Robolectric.
 */
class ApkInstallOutcomeTest {
    @Test
    fun `success and the user backing out are told apart, and neither is a failure`() {
        assertEquals(InstallOutcome.Installed, outcomeOf(PackageInstaller.STATUS_SUCCESS))
        assertEquals(InstallOutcome.Abandoned, outcomeOf(PackageInstaller.STATUS_FAILURE_ABORTED))
    }

    @Test
    fun `every refusal the system names keeps its own reason`() {
        val expected = mapOf(
            PackageInstaller.STATUS_FAILURE_BLOCKED to InstallFailure.BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT to InstallFailure.CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE to InstallFailure.INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_STORAGE to InstallFailure.STORAGE,
            PackageInstaller.STATUS_FAILURE_INVALID to InstallFailure.INVALID,
        )

        expected.forEach { (status, failure) ->
            assertEquals("status $status", InstallOutcome.Failed(failure), outcomeOf(status))
        }
    }

    /** The list has grown before; a status this build has never seen must not be a crash. */
    @Test
    fun `a status this build does not know is an unnamed failure`() {
        assertEquals(InstallOutcome.Failed(InstallFailure.UNKNOWN), outcomeOf(PackageInstaller.STATUS_FAILURE))
        assertEquals(InstallOutcome.Failed(InstallFailure.UNKNOWN), outcomeOf(Int.MAX_VALUE))
    }

    private fun outcomeOf(status: Int): InstallOutcome = ApkInstallResultReceiver.outcomeOf(status)
}
