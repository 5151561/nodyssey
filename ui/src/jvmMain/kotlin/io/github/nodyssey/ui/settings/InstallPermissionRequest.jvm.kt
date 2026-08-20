package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable

/**
 * Nothing to ask for.
 *
 * The desktop target exists to prove the screens compile without Android under them, and there is no
 * desktop build of this app that installs its own APK — so the 关于 screen's permission banner is
 * unreachable here: [AboutAppViewModel] only offers it once `canInstallPackages()` has said no, and
 * whatever installer a desktop build eventually gets would answer that differently or not exist.
 */
@Composable
actual fun rememberInstallPermissionRequest(onResult: () -> Unit): () -> Unit = { onResult() }
