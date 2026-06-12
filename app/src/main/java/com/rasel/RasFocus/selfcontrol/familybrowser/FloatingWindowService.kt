package com.rasel.RasFocus.selfcontrol.familybrowser.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import com.rasel.RasFocus.selfcontrol.familybrowser.FamilyBrowserActivity
import kotlin.math.max
import kotlin.math.min

/**
 * FloatingWindowService
 *
 * যেকোনো tab এর URL কে একটা draggable, resizable floating WebView এ দেখায়।
 * SYSTEM_ALERT_WINDOW permission দরকার।
 *
 * Usage:
 *   FloatingWindowService.launch(context, url, title)
 *   FloatingWindowService.dismiss(context)
 */
class FloatingWindowService : Service() {

    companion object {
        const val ACTION_LAUNCH  = "com.rasel.familybrowser.FLOAT_LAUNCH"
        const val ACTION_DISMISS = "com.rasel.familybrowser.FLOAT_DISMISS"
        const val EXTRA_URL      = "float_url"
        const val EXTRA_TITLE    = "float_title"
        private const val NOTIF_ID   = 8888
        private const val CHANNEL_ID = "float_window_channel"

        fun launch(context: Context, url: String, title: String) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_LAUNCH
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun dismiss(context: Context) {
            context.startService(
                Intent(context, FloatingWindowService::class.java).apply {
                    action = ACTION_DISMISS
                })
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatRoot: View?    = null
    private var webView:  WebView? = null

    // Window size & position
    private var winW = 0
    private var winH = 0
    private var posX = 100
    private var posY = 200

    // Resize state
    private var resizeStartW = 0
    private var resizeStartH = 0
    private var resizeStartX = 0f
    private var resizeStartY = 0f

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        // Default size = 60% of screen width, 50% height
        val dm = resources.displayMetrics
        winW = (dm.widthPixels  * 0.60f).toInt()
        winH = (dm.heightPixels * 0.50f).toInt()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        when (intent?.action) {
            ACTION_DISMISS -> { removeWindow(); stopSelf(); return START_NOT_STICKY }
            ACTION_LAUNCH  -> {
                val url   = intent.getStringExtra(EXTRA_URL)   ?: "about:blank"
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Floating Tab"
                removeWindow()        // পুরনো থাকলে সরাও
                addWindow(url, title)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeWindow()
        super.onDestroy()
    }

    // ── Window creation ────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled", "InflateParams")
    private fun addWindow(url: String, title: String) {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            winW, winH, posX, posY,
            overlayType,
            // FLAG_NOT_FOCUSABLE সরানো হয়েছে — WebView এ keyboard আসার জন্য
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        // ── Root container ─────────────────────────────────────────────────────
        val root = buildRootView(url, title, params)
        floatRoot = root
        windowManager.addView(root, params)
    }

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    private fun buildRootView(
        url: String,
        title: String,
        params: WindowManager.LayoutParams
    ): View {
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        // Container
        val container = object : android.widget.LinearLayout(this) {}.apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            clipToOutline = true
        }

        // ── Title bar ──────────────────────────────────────────────────────────
        val titleBar = android.widget.LinearLayout(this).apply {
            orientation  = android.widget.LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(0xFF2563EB.toInt()) // BrandBlue
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleTv = android.widget.TextView(this).apply {
            text      = title.take(30)
            textSize  = 13f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            maxLines  = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        // Size toggle button (small ↔ large)
        val btnSize = buildIconButton("⊡") {
            val newW: Int
            val newH: Int
            if (winW < screenW * 0.85f) {
                newW = (screenW * 0.90f).toInt()
                newH = (screenH * 0.80f).toInt()
            } else {
                newW = (screenW * 0.60f).toInt()
                newH = (screenH * 0.50f).toInt()
            }
            winW = newW; winH = newH
            params.width = winW; params.height = winH
            clampPosition(screenW, screenH, params)
            windowManager.updateViewLayout(floatRoot, params)
        }

        // Open in main browser
        val btnOpen = buildIconButton("⤤") {
            val i = Intent(this@FloatingWindowService, FamilyBrowserActivity::class.java).apply {
                data  = android.net.Uri.parse(url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(i)
            stopSelf()
        }

        // Close button
        val btnClose = buildIconButton("✕") {
            removeWindow()
            stopSelf()
        }

        titleBar.addView(titleTv)
        titleBar.addView(btnSize)
        titleBar.addView(btnOpen)
        titleBar.addView(btnClose)

        // ── Drag: titleBar দিয়ে drag ──────────────────────────────────────────
        var dragStartX = 0f
        var dragStartY = 0f
        var dragParamsX = 0
        var dragParamsY = 0
        var dragging = false

        titleBar.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    dragStartX = ev.rawX
                    dragStartY = ev.rawY
                    dragParamsX = params.x
                    dragParamsY = params.y
                    // Drag এর সময় keyboard সরিয়ে দাও
                    params.flags = params.flags or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    windowManager.updateViewLayout(floatRoot, params)
                }
                MotionEvent.ACTION_MOVE -> if (dragging) {
                    params.x = dragParamsX + (ev.rawX - dragStartX).toInt()
                    params.y = dragParamsY + (ev.rawY - dragStartY).toInt()
                    clampPosition(screenW, screenH, params)
                    windowManager.updateViewLayout(floatRoot, params)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    // Drag শেষে keyboard আবার আসতে পারবে
                    params.flags = params.flags and
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                    windowManager.updateViewLayout(floatRoot, params)
                }
            }
            true
        }

        // ── WebView ────────────────────────────────────────────────────────────
        val wv = WebView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            settings.apply {
                javaScriptEnabled    = true
                domStorageEnabled    = true
                loadWithOverviewMode = true
                useWideViewPort      = true
                builtInZoomControls  = true
                displayZoomControls  = false
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36"
            }

            // ── WebView focusable — keyboard কাজ করবে ────────────────────
            isFocusable = true
            isFocusableInTouchMode = true

            webViewClient = object : WebViewClient() {

                // ── Adult block (shouldInterceptRequest) ──────────────────
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: android.webkit.WebResourceRequest
                ): android.webkit.WebResourceResponse? {
                    val reqUrl = request.url.toString()
                    if (com.rasel.RasFocus.selfcontrol.familybrowser.AdBlocker.isAdultSite(reqUrl)) {
                        return android.webkit.WebResourceResponse(
                            "text/html", "UTF-8",
                            "<html><body></body></html>".byteInputStream()
                        )
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                // ── Adult block + Safe Search (shouldOverrideUrlLoading) ──
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: android.webkit.WebResourceRequest
                ): Boolean {
                    val reqUrl = request.url.toString()

                    // Adult site block
                    if (com.rasel.RasFocus.selfcontrol.familybrowser.AdBlocker.isAdultSite(reqUrl)) {
                        return true
                    }

                    // Safe Search enforce
                    val safeUrl = com.rasel.RasFocus.selfcontrol.familybrowser.SafeSearchEnforcer
                        .enforceIfNeeded(reqUrl)
                    if (safeUrl != null) {
                        view.loadUrl(safeUrl)
                        return true
                    }

                    return false
                }

                override fun onPageFinished(view: WebView, pageUrl: String) {
                    titleTv.text = (view.title ?: pageUrl).take(30)
                }
            }
            loadUrl(url)
        }
        webView = wv

        // ── Resize handle (bottom-right corner) ───────────────────────────────
        val resizeHandle = android.widget.TextView(this).apply {
            text      = "⠿"
            textSize  = 18f
            setTextColor(0xFF888888.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(4))
            gravity   = Gravity.END
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        resizeHandle.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    resizeStartW = params.width
                    resizeStartH = params.height
                    resizeStartX = ev.rawX
                    resizeStartY = ev.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val dX = (ev.rawX - resizeStartX).toInt()
                    val dY = (ev.rawY - resizeStartY).toInt()
                    params.width  = max(dp(200), min(screenW, resizeStartW + dX))
                    params.height = max(dp(150), min(screenH - params.y, resizeStartH + dY))
                    winW = params.width; winH = params.height
                    windowManager.updateViewLayout(floatRoot, params)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {}
            }
            true
        }

        container.addView(titleBar)
        container.addView(wv)
        container.addView(resizeHandle)
        return container
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun clampPosition(screenW: Int, screenH: Int, p: WindowManager.LayoutParams) {
        p.x = max(0, min(screenW - p.width,  p.x))
        p.y = max(0, min(screenH - p.height, p.y))
        posX = p.x; posY = p.y
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildIconButton(label: String, onClick: () -> Unit) =
        android.widget.TextView(this).apply {
            text     = label
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener { onClick() }
        }

    private fun removeWindow() {
        webView?.destroy(); webView = null
        floatRoot?.let { runCatching { windowManager.removeView(it) } }
        floatRoot = null
    }

    // ── Notification ───────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, FamilyBrowserActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingWindowService::class.java).apply { action = ACTION_DISMISS },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("RasBrowser — Floating Tab")
            .setContentText("Tap to open browser")
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Close", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Floating Browser Tab",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows floating browser window"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }
}
