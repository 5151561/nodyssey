package io.github.nodyssey.platform

import android.os.Build
import io.github.nodyssey.data.diagnostics.DeviceIdentity

/**
 * This phone, named the way its owner would name it.
 *
 * [Build.MODEL] is not reliably the whole name and not reliably only half of one: some vendors ship
 * "Pixel 8", others ship "2201123G", and a few already include the manufacturer. Prefixing
 * unconditionally produces "Xiaomi Xiaomi 14" on the third kind, so the prefix is skipped where the
 * model already carries it.
 *
 * [Build.VERSION.RELEASE] and the API level both, because they disagree in the cases that matter:
 * a ROM may report a release its API level does not match, and the app's own behaviour — which
 * `queries` entries apply, what the network APIs answer — follows the API level.
 */
fun deviceIdentity(): DeviceIdentity {
    val manufacturer = Build.MANUFACTURER.orEmpty().trim()
    val model = Build.MODEL.orEmpty().trim()
    val name =
        when {
            model.isEmpty() -> manufacturer
            manufacturer.isEmpty() || model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
    return DeviceIdentity(
        model = name.ifEmpty { "—" },
        osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    )
}
