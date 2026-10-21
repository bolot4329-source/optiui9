package com.ultracore.app.boot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ultracore.app.MainActivity
import com.ultracore.app.R
import com.ultracore.app.sandbox.SandboxManager
import com.ultracore.app.shizuku.ShizukuManager

/**
 * After a reboot Shizuku is stopped. If the sandbox has apps, remind the user to start Shizuku:
 * without it sandboxed apps can neither be opened by their shortcuts nor put back to sleep.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        SandboxManager.init(context)
        val state = SandboxManager.state.value
        if (state.packages.isEmpty() && state.pending.isEmpty()) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Напоминания", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        // Open Shizuku if it is installed, otherwise UltraCore.
        val target = context.packageManager.getLaunchIntentForPackage(ShizukuManager.SHIZUKU_PACKAGE)
            ?: Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            0,
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (state.pending.isNotEmpty()) {
            "Телефон перезагрузился, ${state.pending.size} прил. из песочницы не усыплены. Запустите Shizuku."
        } else {
            "Телефон перезагрузился. Запустите Shizuku, чтобы открывать приложения из песочницы."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_macro)
            .setContentTitle("Запустите Shizuku")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        private const val CHANNEL_ID = "reminders"
        private const val NOTIFICATION_ID = 4301
    }
}
