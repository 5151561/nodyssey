package io.github.plaza.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionNamesTest {
    @Test
    fun `the tag and the versionName inside the APK compare equal`() {
        assertEquals(0, compareVersionNames("v1.1.0", "1.1.0"))
        assertEquals("1.1.0", versionNameOfTag("v1.1.0"))
    }

    @Test
    fun `segments compare as numbers, not as text`() {
        assertTrue(isNewerVersionName("1.10.0", "1.9.0"))
        assertTrue(isNewerVersionName("2.0.0", "1.99.99"))
        assertFalse(isNewerVersionName("1.9.0", "1.10.0"))
    }

    @Test
    fun `a missing segment counts as zero`() {
        assertEquals(0, compareVersionNames("1.2", "1.2.0"))
        assertTrue(isNewerVersionName("1.2.1", "1.2"))
    }

    @Test
    fun `a pre-release sorts below the release of the same number`() {
        assertTrue(isNewerVersionName("1.2.0", "1.2.0-rc1"))
        assertFalse(isNewerVersionName("1.2.0-rc1", "1.2.0"))
        assertTrue(isNewerVersionName("1.2.0-rc2", "1.2.0-rc1"))
    }

    /** `vX.Y.Z-dev.N` is the shape release.yml publishes test builds under, and N passes 9. */
    @Test
    fun `dev builds count up numerically, not alphabetically`() {
        assertTrue(isNewerVersionName("1.3.0-dev.10", "1.3.0-dev.9"))
        assertFalse(isNewerVersionName("1.3.0-dev.9", "1.3.0-dev.10"))
        assertTrue(isNewerVersionName("1.3.0-dev.1", "1.2.9"))
        assertTrue(isNewerVersionName("1.3.0", "1.3.0-dev.10"))
    }

    @Test
    fun `build metadata does not make a version newer`() {
        assertEquals(0, compareVersionNames("1.2.0+42", "1.2.0"))
    }

    @Test
    fun `the same version is not an update`() {
        assertFalse(isNewerVersionName("1.1.0", "1.1.0"))
        assertFalse(isNewerVersionName("1.0.0", "1.1.0"))
    }

    @Test
    fun `a version we could not read is never an update`() {
        // What PackageManager hands back when there is no versionName to read. Offering an install
        // against it would be pushing an APK on someone for a reason we cannot state.
        assertFalse(isNewerVersionName("1.2.0", ""))
        assertFalse(isNewerVersionName("", "1.1.0"))
    }

    @Test
    fun `a tag shaped in some other way does not win on alphabetical luck`() {
        assertFalse(isNewerVersionName("nightly", "1.1.0"))
    }
}
