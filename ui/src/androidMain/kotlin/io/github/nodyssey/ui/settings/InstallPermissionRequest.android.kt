package io.github.nodyssey.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

/**
 * "允许安装未知应用" for this package.
 *
 * Whatever the user did on that screen, the answer is the same question asked again —
 * `canRequestPackageInstalls` is what decides, not the result code, which this contract does not
 * meaningfully carry for a settings toggle.
 *
 * The intent is built here rather than taken off `AndroidApkInstaller` so the launcher and the thing
 * it launches stay in one place; the installer's own copy is what the notification-free paths use.
 */
@Composable
actual fun rememberInstallPermissionRequest(onResult: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onResult() }
    return {
        val intent =
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri(),
            )
        runCatching { launcher.launch(intent) }
        Unit
    }
}
