package io.github.nsreader.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.core.AppClock
import io.github.nsreader.core.LuckyDraw
import io.github.nsreader.core.LuckyDrawParams
import io.github.nsreader.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class LuckyUiState(
    val postId: String = "",
    val drawAtMillis: Long = 0L,
    val prizeCount: String = "1",
    val startFloor: String = "1",
    val dedupeFloors: Boolean = true,
    val generatedLink: String? = null,
) {
    val postIdValue: Long? get() = postId.trim().toLongOrNull()?.takeIf { it > 0 }
    val prizeCountValue: Int? get() = prizeCount.trim().toIntOrNull()?.takeIf { it in 1..LuckyDraw.MAX_PRIZE_COUNT }
    val startFloorValue: Int? get() = startFloor.trim().toIntOrNull()?.takeIf { it >= 0 }

    val canGenerate: Boolean get() = postIdValue != null && prizeCountValue != null && startFloorValue != null
}

/**
 * 幸运抽奖 — the T-floor notary form.
 *
 * Entirely local: filling it in makes no request and spends nothing. What comes out is a link that
 * declares the rules before the draw happens, which is the whole point of the tool — the fairness comes
 * from having published the parameters in advance, not from anything the app computes.
 */
class LuckyViewModel(
    clock: AppClock,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(LuckyUiState(drawAtMillis = defaultDrawTime(clock.nowMillis())))
    val uiState: StateFlow<LuckyUiState> = _uiState.asStateFlow()

    fun setPostId(value: String) = update { copy(postId = value.digitsOnly()) }

    fun setDrawAt(millis: Long) = update { copy(drawAtMillis = millis) }

    fun setPrizeCount(value: String) = update { copy(prizeCount = value.digitsOnly()) }

    fun setStartFloor(value: String) = update { copy(startFloor = value.digitsOnly()) }

    fun setDedupeFloors(value: Boolean) = update { copy(dedupeFloors = value) }

    fun generate() {
        val state = _uiState.value
        val postId = state.postIdValue ?: return
        val link =
            LuckyDraw.link(
                LuckyDrawParams(
                    postId = postId,
                    drawAtMillis = state.drawAtMillis,
                    prizeCount = state.prizeCountValue ?: 1,
                    startFloor = state.startFloorValue ?: 1,
                    dedupeFloors = state.dedupeFloors,
                ),
            )
        _uiState.update { it.copy(generatedLink = link) }
    }

    /** Any edit invalidates the generated link, so a stale URL can never be pasted into a thread. */
    private fun update(transform: LuckyUiState.() -> LuckyUiState) =
        _uiState.update { state -> state.transform().copy(generatedLink = null) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { LuckyViewModel(container.clock) }
            }
    }
}

private fun String.digitsOnly(): String = filter(Char::isDigit).take(MAX_FIELD_LENGTH)

private const val MAX_FIELD_LENGTH = 12

/** Tomorrow, on the hour. A draw closing in the past is the one default that is always wrong. */
private fun defaultDrawTime(nowMillis: Long): Long =
    Instant
        .ofEpochMilli(nowMillis)
        .plus(1, ChronoUnit.DAYS)
        .atZone(ZoneId.systemDefault())
        .truncatedTo(ChronoUnit.HOURS)
        .toInstant()
        .toEpochMilli()
