package io.github.nodyssey.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle

/**
 * Keeps every ViewModel a test builds, so they can all be shut down together afterwards.
 *
 * `onCleared` is protected, so nothing outside the class can retire a ViewModel — which was
 * harmless while their only state was a `MutableStateFlow`. It stopped being harmless once they
 * started mirroring `TextFieldState`s: `snapshotFlow` registers an observer on the *global*
 * snapshot, so a ViewModel left running outlives its test and keeps watching writes made by every
 * test that follows it in the same JVM. That is what turned the Compose screen tests flaky.
 */
internal class ViewModels {
    private val tracked = mutableListOf<ViewModel>()

    fun <T : ViewModel> track(viewModel: T): T = viewModel.also { tracked += it }

    /**
     * Call from `@After`, before `Dispatchers.resetMain()`.
     *
     * Cancelling is only half of it: a cancelled `snapshotFlow` collector unregisters its observer
     * from its own `finally`, which never runs unless something dispatches the coroutine one last
     * time. Hence the scheduler — without draining it the observers survive the test that made them.
     */
    fun clear(scheduler: TestCoroutineScheduler) {
        tracked.forEach { it.viewModelScope.cancel() }
        tracked.clear()
        scheduler.advanceUntilIdle()
    }
}

/**
 * Types [text] into a field the way a keyboard would, from a test with no composition running.
 *
 * The `sendApplyNotifications` is the part that is easy to forget: the ViewModels observe their
 * fields with `snapshotFlow`, and outside a Compose frame nothing pumps the snapshot system, so a
 * write lands in the state but no collector ever hears about it.
 */
internal fun TextFieldState.typeText(text: String) {
    setTextAndPlaceCursorAtEnd(text)
    Snapshot.sendApplyNotifications()
}

/** Types on at the end, leaving what the field already holds — a quote block, an uploaded image. */
internal fun TextFieldState.typeMore(text: String) = typeText(this.text.toString() + text)
