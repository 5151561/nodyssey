package io.github.bbs1.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.bbs1.R
import io.github.bbs1.net.Bbs1ApiException

/**
 * A failed call, as UiState carries it: a value, not the exception — state gets compared, logged and
 * kept across recompositions, none of which an exception with a stack trace is for.
 */
sealed interface ApiErrorUi {
    data object Network : ApiErrorUi

    data object NotBbs1Api : ApiErrorUi

    /** The plugin's own refusal message, already written for people; shown verbatim. */
    data class Server(val message: String) : ApiErrorUi
}

fun Bbs1ApiException.toUi(): ApiErrorUi = when (this) {
    is Bbs1ApiException.Network -> ApiErrorUi.Network
    is Bbs1ApiException.NotBbs1Api -> ApiErrorUi.NotBbs1Api
    is Bbs1ApiException.Server -> ApiErrorUi.Server(userMessage)
}

@Composable
fun apiErrorText(error: ApiErrorUi): String = when (error) {
    ApiErrorUi.Network -> stringResource(R.string.bbs1_error_network)
    ApiErrorUi.NotBbs1Api -> stringResource(R.string.bbs1_error_not_api)
    is ApiErrorUi.Server -> error.message
}
