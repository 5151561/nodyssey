package io.github.nodyssey.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import io.github.nodyssey.NodysseyApp
import io.github.nodyssey.core.AppDispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Hands a downloaded APK to the platform's own installer.
 *
 * `PackageInstaller` rather than an `ACTION_VIEW` on a `FileProvider` URI: the session takes the
 * bytes straight from our own stream, so no content provider, no exported URI and no grant to
 * another app are involved, and the outcome comes back as a status code instead of being lost the
 * moment the other activity opens.
 *
 * Nothing here installs anything silently — that needs a privilege a normal app cannot hold. The
 * system shows its own confirmation, and the user is the one who approves it.
 */
class ApkInstaller(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) {
    /**
     * Whether this app may ask to install packages at all.
     *
     * The permission is declared in the manifest but granted per app by the user in Settings, so
     * this is a runtime question. False means send them to [unknownSourcesSettingsIntent] first;
     * committing anyway would surface the same settings screen with no explanation of why.
     */
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri(),
        )

    /**
     * Writes [apk] into an install session and commits it.
     *
     * Returns as soon as the session is committed — everything after that is the system's dialog and
     * arrives at [ApkInstallResultReceiver]. False means the session could not even be written, which
     * is a full cache or a deleted download.
     */
    suspend fun install(apk: File): Boolean =
        withContext(dispatchers.io) {
            val installer = context.packageManager.packageInstaller
            val params =
                PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                    .apply { setSize(apk.length()) }
            /*
             * `setAppPackageName` is deliberately not set. A debug build's id carries the `.debug`
             * suffix while the published APK does not, and declaring the wrong one there fails the
             * session outright — which would make this path untestable on exactly the builds it gets
             * developed on. The system verifies the package and the signature at install time either
             * way; the hint only lets it do so a moment earlier.
             */
            var sessionId = -1
            try {
                sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    session.openWrite(APK_ENTRY_NAME, 0, apk.length()).use { output ->
                        apk.inputStream().use { input -> input.copyTo(output) }
                        session.fsync(output)
                    }
                    session.commit(statusIntentSender(sessionId))
                }
                true
            } catch (e: IOException) {
                if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
                false
            }
        }

    private fun statusIntentSender(sessionId: Int): IntentSender {
        val intent =
            Intent(context, ApkInstallResultReceiver::class.java)
                .setAction(ApkInstallResultReceiver.ACTION_INSTALL_STATUS)
        // Mutable because the extras are the system's to fill in: the status, and — while the user
        // has not answered yet — the confirmation intent itself.
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender
    }

    private companion object {
        const val APK_ENTRY_NAME = "nodyssey"
    }
}

/**
 * The other end of the install session.
 *
 * Two things arrive here: `STATUS_PENDING_USER_ACTION`, which carries the system's own confirmation
 * screen and has to be started, and the outcome once the user has answered it. Reached through the
 * Application's container, the same way [io.github.nodyssey.notifications.NotificationPollWorker]
 * reaches it — a component the system constructs has no other way in.
 */
class ApkInstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        val status =
            intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation =
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?: return
            // Started from a receiver, so it needs its own task.
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(confirmation)
            return
        }

        (context.applicationContext as? NodysseyApp)
            ?.container
            ?.appUpdateRepository
            ?.onInstallStatus(status)
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "io.github.nodyssey.INSTALL_STATUS"
    }
}
