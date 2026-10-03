package io.github.nodyssey.ui.assets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.nodyssey.data.AttendanceMode

/**
 * A launcher 签到 on its way to 账户与成长, taken exactly once.
 *
 * Held in memory rather than in `AssetsKey` for the reason `PendingComposerShare` gives: a key is
 * restored with the back stack, and a restored key would sign in again every time the process came
 * back — a request that spends nothing but is still one the reader never asked for twice.
 */
class PendingAttendance {
    var pending: AttendanceMode? by mutableStateOf(null)
        private set

    fun offer(mode: AttendanceMode) {
        pending = mode
    }

    fun take(): AttendanceMode? = pending.also { pending = null }
}
