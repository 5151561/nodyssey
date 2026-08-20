package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Nothing to ask for, because there is nothing to grant.
 *
 * 应用内更新 is an Android feature end to end: it downloads an APK and hands it to the package
 * installer, and the permission this would request is the one that lets that happen. iOS has no
 * equivalent — an app arrives from the App Store or it does not arrive — so `ApkInstaller` has no
 * Apple implementation, `canInstallPackages` never answers false on this platform because nothing
 * asks it, and the settings row that leads here is not drawn.
 *
 * [onResult] is not called either: it exists to make the screen re-read the permission, and re-reading
 * a permission that does not exist would only redraw the same row.
 */
@Composable
actual fun rememberInstallPermissionRequest(onResult: () -> Unit): () -> Unit = remember { {} }
