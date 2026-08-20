package io.github.nodyssey.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

@Composable
actual fun rememberNotificationPermissionRequest(): () -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return {
        // Below Tiramisu the permission is install-time and asking for it does nothing at all.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
