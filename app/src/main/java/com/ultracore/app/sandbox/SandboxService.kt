package com.ultracore.app.sandbox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ultracore.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Lives only while sandbox apps are unfrozen. Listens for the screen turning off,
 * waits the chosen delay (screen on again = cancel) and freezes them back.
 */
class SandboxService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var freezeJob: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> scheduleFreeze()
                Intent.ACTION_SCREEN_ON -> {
                    // The user came back before the delay ended — keep the apps open.
                    freezeJob?.cancel()
                    freezeJob = null
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        SandboxManager.init(this)
        startInForeground()
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (SandboxManager.state.value.pending.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        updateNotification()
        return START_STICKY
    }

    private fun scheduleFreeze() {
        if (freezeJob?.isActive == true) return
        freezeJob = scope.launch {
            val delaySeconds = SandboxManager.state.value.delaySeconds.coerceIn(0, 600)
            val power = getSystemService(PowerManager::class.java)
            val wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UltraCore:sandbox")
            wakeLock?.acquire((delaySeconds + 60) * 1_000L)
            try {
                delay(delaySeconds * 1_000L)
                val left = SandboxManager.freezePending()
                if (left == 0) stopSelf() else updateNotification()
            } finally {
                if (wakeLock?.isHeld == true) wakeLock.release()
            }
        }
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager?.getNotificationChannel(CHANNEL_ID) == null) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Песочница", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Ожидание выключения экрана для заморозки приложений"
                },
            )
        }
        val count = SandboxManager.state.value.pending.size
        val delaySeconds = SandboxManager.state.value.delaySeconds
        val text = if (delaySeconds == 0) {
            "Заморожу $count прил. сразу после выключения экрана"
        } else {
            "Заморожу $count прил. через $delaySeconds с после выключения экрана"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_macro)
            .setContentTitle("Песочница UltraCore")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "sandbox"
        private const val NOTIFICATION_ID = 4201

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, SandboxService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SandboxService::class.java))
        }
    }
}
