// ============================================================
// BpOverlayServiceFixed.kt
// Button Phone Mode — CRITICAL FIXES
// ============================================================
// Problems Solved:
// 1. Launcher detection BEFORE grace period check ✓
// 2. Allowed apps actually block notification overlay ✓
// 3. Home press → launcher page + overlay ✓
// 4. allowedPackages properly enforced ✓
// ============================================================

package com.rasel.RasFocus.selfcontrol

import android.accessibilityservice.AccessibilityService
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BpOverlayServiceFixed : Service() {

    private var windowManager: WindowManager? = null
    private var composeView: android.view.View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var homeButtonReceiver: BroadcastReceiver? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isOverlayVisible = false
    private var job: Job? = null

    // ★ CRITICAL: Track allowed app launch
    @Volatile private var launchedAllowedPkg: String? = null
    @Volatile private var lastAllowedLaunchTime: Long = 0L
    private val LAUNCH_GRACE_MS = 7000L  // 7s grace — app properly লঞ্চ হবার সময় দাও

    // ★ CRITICAL: Allowed packages কখনো overlay show করবে না
    private val coreAllowedPackages = mutableSetOf(
        "com.android.dialer", "com.google.android.dialer",
        "com.android.mms", "com.google.android.apps.messaging",
        "com.android.server.telecom", "com.rasel.RasFocus",
        "com.android.contacts", "com.android.settings",
        "com.android.clock", "com.google.android.deskclock"
    )

    // ★ কোনো launcher/home এখানে আসবে না — launcherPackages set এ যাবে
    private val launcherPackages = mutableSetOf(
        "com.android.launcher", "com.android.launcher2", "com.android.launcher3",
        "com.google.android.apps.nexuslauncher", "com.google.android.launcher",
        "com.samsung.android.launcher", "com.sec.android.app.launcher",
        "com.miui.home", "com.huawei.android.launcher", "com.oppo.launcher",
        "com.vivo.launcher", "com.oneplus.launcher", "com.coloros.launcher",
        "com.realme.launcher", "com.teslacoilsw.launcher",
        "com.action.launcher", "net.oneplus.launcher"
    )

    private val lifecycleOwner = CustomLifecycleOwner()

    override fun onCreate() {
        super.onCreate()

        // Detect default dialer & SMS
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            telecomManager?.defaultDialerPackage?.let { coreAllowedPackages.add(it) }
            android.provider.Telephony.Sms.getDefaultSmsPackage(this)?.let { coreAllowedPackages.add(it) }
        } catch (_: Exception) {}

        // Detect default launcher (কিন্তু coreAllowed নয়, launcherPackages এ দাও)
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val homeInfo = packageManager.resolveActivity(homeIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            homeInfo?.activityInfo?.packageName?.let { launcherPackages.add(it) }
        } catch (_: Exception) {}

        // ════ LAYER 1: Home Button Broadcast Receiver ════
        // Home press → ACTION_CLOSE_SYSTEM_DIALOGS broadcast
        homeButtonReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                    val reason = intent.getStringExtra("reason") ?: ""
                    // "homekey" = home, "recentapps" = recents
                    if (reason == "homekey" || reason == "recentapps") {
                        // ★ Home press detected
                        // Grace period reset করো — launcher এখন foreground আসবে
                        lastAllowedLaunchTime = 0L
                        // Overlay দেখাও (launcher page সহ)
                        showOverlay()
                    }
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(homeButtonReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(homeButtonReceiver, filter)
        }

        startForegroundNotification()
        lifecycleOwner.performRestore(null)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        setupOverlay()
        startMonitoring()
    }

    private fun setupOverlay() {
        val session = BpPrefs.load(this) ?: return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val view = ComposeView(this).apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                BpLauncherScreen(
                    session = session,
                    onAppLaunched = { pkg ->
                        // ★ Allowed app launch
                        launchedAllowedPkg = pkg
                        lastAllowedLaunchTime = System.currentTimeMillis()
                        hideOverlay()
                        // Grace period-এর মধ্যে app launch হচ্ছে — সময় দাও
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (System.currentTimeMillis() - lastAllowedLaunchTime < LAUNCH_GRACE_MS) {
                                launchAppReliably(this@BpOverlayServiceFixed, pkg)
                            }
                        }, 500)
                    },
                    onHideOverlay = {
                        // Website launch
                        lastAllowedLaunchTime = System.currentTimeMillis()
                        hideOverlay()
                    },
                    onSessionEnd = {
                        BpPrefs.clear(this@BpOverlayServiceFixed)
                        stopSelf()
                    }
                )
            }
        }

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        windowManager?.addView(view, overlayParams)
        composeView = view
        isOverlayVisible = true
    }

    private fun hideOverlay() {
        CoroutineScope(Dispatchers.Main).launch {
            if (!isOverlayVisible) return@launch
            isOverlayVisible = false
            try {
                composeView?.let { windowManager?.removeView(it) }
            } catch (_: Exception) {}
        }
    }

    private fun showOverlay() {
        if (isOverlayVisible) return
        CoroutineScope(Dispatchers.Main).launch {
            isOverlayVisible = true
            try {
                if (composeView?.windowToken == null) {
                    composeView?.let { windowManager?.addView(it, overlayParams) }
                } else {
                    overlayParams?.let { p ->
                        p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                        try { windowManager?.updateViewLayout(composeView, p) } catch (_: Exception) {}
                    }
                    composeView?.visibility = android.view.View.VISIBLE
                }
            } catch (_: Exception) {}
        }
    }

    private fun startMonitoring() {
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        job = scope.launch {
            while (isActive) {
                delay(400)  // 400ms check interval

                val session = BpPrefs.load(this@BpOverlayServiceFixed)
                if (session == null || !BpPrefs.isActive(this@BpOverlayServiceFixed)) {
                    stopSelf()
                    break
                }

                // ★ allowed packages + browsers (website allowed)
                val browserPkgs = if (session.allowedWebsites.isNotEmpty()) CHROME_PACKAGES else emptySet<String>()
                val allAllowed = coreAllowedPackages + session.allowedPackages.toSet() + browserPkgs

                // ════ Foreground app detection (recent 1.5s window) ════
                val now = System.currentTimeMillis()
                val events = usageStatsManager.queryEvents(now - 1500, now)
                val ev = UsageEvents.Event()
                var topPkg = ""
                var topPkgTime = 0L

                while (events.hasNextEvent()) {
                    events.getNextEvent(ev)
                    if (ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                        val p = ev.packageName
                        // Skip self & systemui
                        if (p == packageName) continue
                        if (p.contains("systemui", ignoreCase = true)) continue
                        if (ev.timeStamp > topPkgTime) {
                            topPkg = p
                            topPkgTime = ev.timeStamp
                        }
                    }
                }

                // 1.5s window empty — try 4s window
                if (topPkg.isEmpty()) {
                    val events2 = usageStatsManager.queryEvents(now - 4000, now)
                    while (events2.hasNextEvent()) {
                        events2.getNextEvent(ev)
                        if (ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                            val p = ev.packageName
                            if (p == packageName) continue
                            if (p.contains("systemui", ignoreCase = true)) continue
                            if (ev.timeStamp > topPkgTime) {
                                topPkg = p
                                topPkgTime = ev.timeStamp
                            }
                        }
                    }
                }

                if (topPkg.isEmpty()) continue

                // ════ CRITICAL DECISION TREE ════
                val isAllowed = allAllowed.contains(topPkg)
                val inGrace = (System.currentTimeMillis() - lastAllowedLaunchTime) < LAUNCH_GRACE_MS

                // ★ Launcher detection — FIRST CHECK
                val isLauncher = launcherPackages.contains(topPkg) ||
                    topPkg.contains("launcher", ignoreCase = true) ||
                    topPkg.contains(".home", ignoreCase = true)

                // DECISION ORDER:
                // 1. Grace period active → কিছু করবো না (app launch হচ্ছে)
                // 2. Launcher detected → overlay দেখাও (HOME press)
                // 3. Allowed app → overlay সরাও
                // 4. Blocked app → overlay দেখাও

                when {
                    inGrace && !isLauncher -> {
                        // Allowed app launching — overlay রাখবো না
                    }

                    isLauncher -> {
                        // ★ Home button pressed — launcher আসছে
                        lastAllowedLaunchTime = 0L
                        showOverlay()
                    }

                    isAllowed && !inGrace -> {
                        // Allowed app active (গ্রেস পিরিয়ড শেষ)
                        hideOverlay()
                    }

                    else -> {
                        // Blocked app চেষ্টা করছে
                        launchedAllowedPkg = null
                        showOverlay()
                    }
                }
            }
        }
    }

    private fun startForegroundNotification() {
        val channelId = "bp_overlay_channel_fixed"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Focus Mode Active",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("🎯 Focus Mode Active")
                .setContentText("Only allowed apps can run")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("🎯 Focus Mode Active")
                .setContentText("Only allowed apps can run")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
        }
        startForeground(3, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isOverlayVisible) showOverlay()
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        super.onDestroy()
        try { homeButtonReceiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        job?.cancel()
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        composeView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
    }

    class CustomLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry get() = savedStateRegistryController.savedStateRegistry
        fun handleLifecycleEvent(event: Lifecycle.Event) = lifecycleRegistry.handleLifecycleEvent(event)
        fun performRestore(savedState: android.os.Bundle?) = savedStateRegistryController.performRestore(savedState)
    }
}
