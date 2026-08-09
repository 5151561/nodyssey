package io.github.bbs1.data

import io.github.bbs1.net.Bbs1ApiException

/**
 * Runs an authenticated call against [instanceId], forgetting the credential if the site refuses it.
 *
 * The drop happens here rather than in each screen so that by the time the failure reaches a UI
 * state, the app is already signed out of that site — otherwise the screen would offer a retry that
 * cannot succeed, and the next screen the user opens would still believe it was signed in.
 *
 * The exception is rethrown: forgetting the token is not the same as handling the failure, and the
 * caller still owns what the user sees.
 */
suspend fun <T> InstanceRepository.authed(instanceId: String, block: suspend () -> T): T =
    try {
        block()
    } catch (e: Bbs1ApiException.Unauthorized) {
        clearSession(instanceId)
        throw e
    }
