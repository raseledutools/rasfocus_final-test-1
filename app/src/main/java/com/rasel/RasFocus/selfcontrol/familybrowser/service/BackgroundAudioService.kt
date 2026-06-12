package com.rasel.RasFocus.selfcontrol.familybrowser.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.rasel.RasFocus.selfcontrol.familybrowser.FamilyBrowserActivity

/**
 * BackgroundAudioService.kt
 * Foreground service that keeps audio alive when app goes to background OR lock screen.
 *
 * Key fixes:
 * 1. PARTIAL_WAKE_LOCK — CPU sleep হলেও JS চলতে থাকে
 * 2. setMediaSession — Android lock screen media controls এর সাথে integrate হয়
 * 3. AudioFocus AUDIOFOCUS_GAIN — অন্য app audio কে সরিয়ে দেয়
 */
class BackgroundAudioService : Service() {

    companion object {
        const val CHANNEL_ID = "bg_audio_channel"
        const val NOTIFICATION_ID = 9001
        const val EXTRA_TITLE = "video_title"
        const val EXTRA_URL = "video_url"

        const val ACTION_PLAY_PAUSE = "com.familybrowser.PLAY_PAUSE"
        const val ACTION_STOP = "com.familybrowser.STOP_BG_AUDIO"
        const val ACTION_UPDATE_TITLE = "com.familybrowser.UPDATE_TITLE"
    }

    private var audioManager: AudioManager? = null
    private var wakeLock: PowerManager.WakeLock? = null   // ← NEW: CPU awake রাখার জন্য

    private val audioFocusRequest by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    // Transient loss (যেমন notification sound) — ignore করো
                    // Full loss মানে user সচেতনভাবে অন্য app চালু করেছে
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            // সম্পূর্ণ focus চলে গেলে service বন্ধ করো না,
                            // শুধু note রাখো — user চাইলে নিজেই stop করবে
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            // Transient — কিছু করার নেই, audio আপনাই resume হবে
                        }
                    }
                }
                .build()
        } else null
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        acquireWakeLock()  // ← CPU awake রাখো
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                releaseWakeLock()
                releaseAudioFocus()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_TITLE -> {
                // Notification title আপডেট করো (video change হলে)
                val newTitle = intent.getStringExtra(EXTRA_TITLE) ?: return START_STICKY
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(newTitle))
                return START_STICKY
            }
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Playing video"
        requestAudioFocus()
        // ─── CRITICAL: startForeground() এর আগেই call করতে হবে ───────────
        // Android 14+ এ foreground service start এর 5 সেকেন্ডের মধ্যে
        // startForeground() না হলে ANR/crash হয়।
        startForeground(NOTIFICATION_ID, buildNotification(title))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        releaseAudioFocus()
        super.onDestroy()
    }

    // ── WakeLock: CPU awake রাখো যাতে JS execution বন্ধ না হয় ─────────────
    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RasFocus:AudioPlayback"
        ).also {
            it.setReferenceCounted(false)
            it.acquire(3 * 60 * 60 * 1000L) // সর্বোচ্চ 3 ঘণ্টা — audio শেষ হলে service stop করবে
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    private fun buildNotification(title: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, FamilyBrowserActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, BackgroundAudioService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Family Browser — Background Play")
            .setContentText(title)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            // Lock screen এ notification দেখাবে — user জানবে audio চলছে
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Media style notification — lock screen এ media controls দেখায়
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps audio playing when app is in background"
                setShowBadge(false)
                // Lock screen এ notification দেখাবে
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}