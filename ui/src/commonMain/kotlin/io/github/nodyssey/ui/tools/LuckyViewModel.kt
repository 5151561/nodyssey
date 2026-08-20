package io.github.nodyssey.ui.tools

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.LuckyDraw
import io.github.nodyssey.core.LuckyDrawParams
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * What the form shows that is not the text in its own fields.
 *
 * The three numbers live in [LuckyViewModel]'s `TextFieldState`s instead of here: a text field's
 * content and its selection are one thing, and splitting them across a state holder and a UiState is
 * what forces the round-trip that moves the caret.
 */
data class LuckyUiState(
    val drawAtMillis: Long = 0L,
    val dedupeFloors: Boolean = true,
    val generatedLink: String? = null,
    val canGenerate: Boolean = false,
)

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
    val postId = TextFieldState()
    val prizeCount = TextFieldState("1")
    val startFloor = TextFieldState("1")

    private val _uiState =
        MutableStateFlow(LuckyUiState(drawAtMillis = defaultDrawTime(clock.nowMillis())))
    val uiState: StateFlow<LuckyUiState> = _uiState.asStateFlow()

    init {
        // Mirrors the fields one way, for the two things the screen needs that are not the text
        // itself: whether 生成 is enabled, and invalidating a link the numbers no longer describe.
        viewModelScope.launch {
            snapshotFlow { Triple(postId.text, prizeCount.text, startFloor.text) }
                .collect {
                    _uiState.update { state ->
                        state.copy(generatedLink = null, canGenerate = parameters() != null)
                    }
                }
        }
    }

    fun setDrawAt(millis: Long) =
        _uiState.update { it.copy(drawAtMillis = millis, generatedLink = null) }

    fun setDedupeFloors(value: Boolean) =
        _uiState.update { it.copy(dedupeFloors = value, generatedLink = null) }

    fun generate() {
        // Reads the fields, not the mirror above: the mirror is a frame behind, and what gets
        // published has to be what is on screen at the moment 生成 was tapped.
        val params = parameters() ?: return
        _uiState.update { it.copy(generatedLink = LuckyDraw.link(params)) }
    }

    /** The form as [LuckyDrawParams], or null while any field is empty or out of range. */
    private fun parameters(): LuckyDrawParams? {
        val post = postId.text.toString().trim().toLongOrNull()?.takeIf { it > 0 } ?: return null
        val prizes =
            prizeCount.text
                .toString()
                .trim()
                .toIntOrNull()
                ?.takeIf { it in 1..LuckyDraw.MAX_PRIZE_COUNT }
                ?: return null
        val floor = startFloor.text.toString().trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return LuckyDrawParams(
            postId = post,
            drawAtMillis = _uiState.value.drawAtMillis,
            prizeCount = prizes,
            startFloor = floor,
            dedupeFloors = _uiState.value.dedupeFloors,
        )
    }

    companion object {
        /** Matches the site's own field width; also the cap the fields reject past. */
        const val MAX_FIELD_LENGTH = 12

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { LuckyViewModel(container.clock) }
            }
    }
}

/** Tomorrow, on the hour. A draw closing in the past is the one default that is always wrong. */
private fun defaultDrawTime(nowMillis: Long): Long =
    Instant
        .ofEpochMilli(nowMillis)
        .plus(1, ChronoUnit.DAYS)
        .atZone(ZoneId.systemDefault())
        .truncatedTo(ChronoUnit.HOURS)
        .toInstant()
        .toEpochMilli()
