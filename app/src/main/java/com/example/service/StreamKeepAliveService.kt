package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity

/**
 * Lightweight mediaPlayback foreground service holding PARTIAL_WAKE_LOCK and
 * WIFI_MODE_FULL_LOW_LATENCY during active remote play streams so Galaxy A22 4G
 * and Tab A7 2020 do not throttle CPU/Wi-Fi ~60 seconds into gameplay.
 */
class StreamKeepAliveService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val consoleName = intent?.getStringExtra(EXTRA_CONSOLE_NAME).orEmpty().ifBlank { "Xbox Console" }
        ensureChannel()
        val notification = buildNotification(consoleName)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        acquireLocks()
        return START_NOT_STICKY
    }

    private fun acquireLocks() {
        runCatching {
            if (wakeLock == null) {
                val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ProjectX::StreamWakeLock")
                    ?.apply { setReferenceCounted(false) }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(MAX_WAKELOCK_MS)
            }
        }
        runCatching {
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiLock = wm?.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                    "ProjectX::StreamWifiLock"
                )?.apply { setReferenceCounted(false) }
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        }
    }

    private fun releaseLocks() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
        runCatching {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        }
        wifiLock = null
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Remote Play Stream",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps low-latency Xbox Remote Play audio and Wi-Fi active"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(consoleName: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Project X Remote Play")
            .setContentText("Streaming $consoleName")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        releaseLocks()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "project_x_stream_channel"
        private const val NOTIFICATION_ID = 4107
        private const val EXTRA_CONSOLE_NAME = "extra_console_name"
        private const val MAX_WAKELOCK_MS = 4 * 60 * 60 * 1000L

        fun start(context: Context, consoleName: String) {
            runCatching {
                val appCtx = context.applicationContext
                val intent = Intent(appCtx, StreamKeepAliveService::class.java).apply {
                    putExtra(EXTRA_CONSOLE_NAME, consoleName)
                }
                appCtx.startForegroundService(intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                val appCtx = context.applicationContext
                appCtx.stopService(Intent(appCtx, StreamKeepAliveService::class.java))
            }
        }
    }
}
