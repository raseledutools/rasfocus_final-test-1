package com.rasel.RasFocus.selfcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * BpAppBlockerService — ButtonPhone focus lock এর সহায়ক service।
 * মূল blocking logic BpOverlayService এ আছে।
 * এই service টি foreground notification দিয়ে process alive রাখে।
 */
class BpAppBlockerService : Service() {
    companion object {
        private const val CHANNEL_ID = "bp_app_blocker_channel"
        private const val NOTIF_ID = 3001
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = buildNotification()
        startForeground(NOTIF_ID, notif)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Lock Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps Focus Lock running" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Focus Lock Active")
                .setContentText("App blocking is running.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Focus Lock Active")
                .setContentText("App blocking is running.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
        }
    }
}

/**
 * BlockerForegroundService — VPN-based website blocker এর foreground companion।
 * BlockerVpnService চলার সময় persistent notification দিয়ে process alive রাখে।
 */
class BlockerForegroundService : Service() {
    companion object {
        private const val CHANNEL_ID = "blocker_foreground_channel"
        private const val NOTIF_ID = 3003
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Blocker Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps app blocker running" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("RasFocus Blocker Active")
                .setContentText("App blocking is running.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("RasFocus Blocker Active")
                .setContentText("App blocking is running.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
        }
        startForeground(NOTIF_ID, notif)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/**
 * UsageNotificationService — screen time usage notification দেওয়ার service।
 * ভবিষ্যতে implement করা হবে। এখন stub হিসেবে আছে।
 */
class UsageNotificationService : Service() {
    companion object {
        private const val CHANNEL_ID = "usage_notif_channel"
        private const val NOTIF_ID = 3002
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Usage Notifications",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("RasFocus Usage Tracker")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("RasFocus Usage Tracker")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build()
        }
        startForeground(NOTIF_ID, notif)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
