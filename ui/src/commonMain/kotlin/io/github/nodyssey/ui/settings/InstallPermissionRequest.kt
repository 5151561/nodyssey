package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable

/**
 * Sends the user to wherever this platform grants "may install applications", and calls [onResult]
 * when they come back — whatever they did there.
 *
 * The one part of 应用内更新 with no neutral shape. [AboutAppViewModel] can ask whether the permission
 * is missing ([io.github.plaza.core.update.ApkInstaller.canInstallPackages]) because that is a
 * yes-or-no; *asking for* it is an Android settings screen reached with an `Intent` and answered
 * with an activity result, and there is no second platform to generalise from yet.
 *
 * A composable rather than a method on the installer because the activity result belongs to the
 * composition: the launcher has to be registered while the screen is alive, which is the same reason
 * `:designsys` puts `PlazaBackHandler` and the clipboard behind this shape.
 */
@Composable
expect fun rememberInstallPermissionRequest(onResult: () -> Unit): () -> Unit
