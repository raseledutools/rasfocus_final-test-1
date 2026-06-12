package com.rasel.RasFocus.selfcontrol.familybrowser.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.view.*
import android.webkit.*
import androidx.core.app.NotificationCompat
import com.rasel.RasFocus.selfcontrol.familybrowser.FamilyBrowserActivity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

/**
 * ═══════════════════════════════════════════════════════════════════════
 * YoutubeFloatingWindowService  — Fixed Version
 * ═══════════════════════════════════════════════════════════════════════
 *
 * BUG FIXES:
 *
 * FIX 1 — Minimize (▬) করলে audio বন্ধ হয়ে যাওয়া:
 *   কারণ: removeFull() এ windowManager.removeView(fullWindow) করলে WebView
 *   view hierarchy থেকে detach হয়। Detached WebView এ Android automatically
 *   audio/video pause করে।
 *   সমাধান: WebView কে fullWindow থেকে detach করে একটা invisible "ghost"
 *   FrameLayout এ re-attach করা হয় (windowManager এ added থাকে)।
 *   এতে WebView সবসময় একটা live view hierarchy এর মধ্যে থাকে।
 *
 * FIX 2 — Family Browser চালু থাকা অবস্থায় phone lock করলে audio বন্ধ:
 *   কারণ: Activity.onStop() → BackgroundAudioService start হয়, কিন্তু
 *   Activity.onResume() → service বন্ধ হয় (lock screen থেকে ফিরলেও)।
 *   এছাড়া WebView.onPause() suppress করা হয়নি।
 *   সমাধান: MainActivity তে onStop/onResume এর logic ঠিক করা হয়েছে।
 *   (এই file এ পরিবর্তন নেই — MainActivity.kt দেখুন)
 *
 * FIX 3 — Resize করতে করতে ছোট হলে pause হয়:
 *   কারণ: WebView height অনেক ছোট হলে renderer মনে করে view hidden/offscreen।
 *   সমাধান: Minimum size বাড়ানো (dp(280) height) + resize throttle যোগ করা
 *   যাতে বারবার updateViewLayout() call এ WebView flicker না করে।
 *
 * ═══════════════════════════════════════════════════════════════════════
 */
class YoutubeFloatingWindowService : Service() {

    // ── Constants ──────────────────────────────────────────────────────────────
    companion object {
        const val ACTION_LAUNCH   = "com.rasel.yt_float.LAUNCH"
        const val ACTION_DISMISS  = "com.rasel.yt_float.DISMISS"
        const val ACTION_MINIMIZE = "com.rasel.yt_float.MINIMIZE"
        const val ACTION_RESTORE  = "com.rasel.yt_float.RESTORE"
        const val ACTION_PLAY_PAUSE = "com.rasel.yt_float.PLAY_PAUSE"
        const val ACTION_PREV     = "com.rasel.yt_float.PREV"
        const val ACTION_NEXT     = "com.rasel.yt_float.NEXT"

        const val EXTRA_URL       = "yt_url"
        const val EXTRA_TITLE     = "yt_title"
        const val EXTRA_NO_RELOAD = "yt_no_reload"  // true হলে loadUrl() ডাকবে না

        // ═══════════════════════════════════════════════════════════════════════
        // pendingWebView — MainActivity থেকে Home button press এ set করা হয়।
        // Service এর onStartCommand এ consume করা হয়।
        // এই WebView টা ইতিমধ্যে video চালাচ্ছে — reload করা যাবে না।
        // ═══════════════════════════════════════════════════════════════════════
        @Volatile
        var pendingWebView: WebView? = null

        private const val NOTIF_ID   = 7777
        private const val CHANNEL_ID = "yt_float_channel"

        private const val YT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.6367.82 Mobile Safari/537.36"

        fun launch(context: Context, url: String, title: String) {
            val i = Intent(context, YoutubeFloatingWindowService::class.java).apply {
                action = ACTION_LAUNCH
                putExtra(EXTRA_URL,   url)
                putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(i)
            else
                context.startService(i)
        }

        /**
         * launchNoReload — Home button থেকে call করা হয় যখন WebView
         * ইতিমধ্যে ব্রাউজারে চলছে।
         *
         * EXTRA_NO_RELOAD = true দিলে ACTION_LAUNCH handler এ loadUrl()
         * ডাকা হয় না — শুধু window দেখানো হয়। এতে video restart হয় না।
         */
        fun launchNoReload(context: Context, url: String, title: String) {
            val i = Intent(context, YoutubeFloatingWindowService::class.java).apply {
                action = ACTION_LAUNCH
                putExtra(EXTRA_URL,       url)
                putExtra(EXTRA_TITLE,     title)
                putExtra(EXTRA_NO_RELOAD, true)  // ← KEY FIX: reload করো না
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(i)
            else
                context.startService(i)
        }

        fun dismiss(context: Context) {
            context.startService(Intent(context, YoutubeFloatingWindowService::class.java)
                .apply { action = ACTION_DISMISS })
        }

        fun minimize(context: Context) {
            context.startService(Intent(context, YoutubeFloatingWindowService::class.java)
                .apply { action = ACTION_MINIMIZE })
        }

        fun restore(context: Context) {
            context.startService(Intent(context, YoutubeFloatingWindowService::class.java)
                .apply { action = ACTION_RESTORE })
        }
    }

    // ── State ──────────────────────────────────────────────────────────────────
    private lateinit var windowManager: WindowManager
    private var fullWindow:    View? = null
    private var bubbleView:    View? = null
    private var webView:       WebView? = null

    // ═══════════════════════════════════════════════════════════════════════════
    // FIX 1: Ghost container — WebView কে সবসময় একটা live WindowManager view এ
    // attached রাখার জন্য। Minimize হলে fullWindow সরানো হয়, কিন্তু WebView
    // এই ghost container এ চলে আসে — detach হয় না, তাই audio চলে।
    // ═══════════════════════════════════════════════════════════════════════════
    private var ghostContainer: android.widget.FrameLayout? = null
    private var ghostParams: WindowManager.LayoutParams? = null

    private var winW = 0
    private var winH = 0
    private var posX = 40
    private var posY = 120

    private var isMinimized = false
    private var currentUrl   = "https://m.youtube.com"
    private var currentTitle = "YouTube"

    // titleTv instance — WebViewClient এ capture থাকে, rebuild এ update করা হয়
    private var titleTvRef: android.widget.TextView? = null

    // Resize tracking
    private var resizeStartW    = 0
    private var resizeStartH    = 0
    private var resizeStartRawX = 0f
    private var resizeStartRawY = 0f

    // FIX 3: Resize throttle — বারবার updateViewLayout এ flicker আটকানো
    private var lastResizeMs    = 0L
    private val RESIZE_THROTTLE = 32L  // ~30fps

    // ── MediaSession — notification এ Play/Pause/Next/Prev control এর জন্য ──
    private var mediaSession: MediaSession? = null
    private var isPlaying = true  // assume playing on launch

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        val dm = resources.displayMetrics
        winW = (dm.widthPixels  * 0.92f).toInt()
        winH = (dm.heightPixels * 0.58f).toInt()
        posX = (dm.widthPixels  - winW) / 2
        posY = (dm.heightPixels * 0.12f).toInt()

        // MediaSession setup — notification এর media controls কাজ করার জন্য
        setupMediaSession()

        // FIX 1: Ghost container তৈরি করে add করো
        setupGhostContainer()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "YtFloat").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    isPlaying = true
                    webView?.evaluateJavascript(JS_PLAY, null)
                    updateMediaState()
                    updateNotification(currentTitle)
                }
                override fun onPause() {
                    isPlaying = false
                    webView?.evaluateJavascript(JS_PAUSE, null)
                    updateMediaState()
                    updateNotification(currentTitle)
                }
                override fun onSkipToNext() {
                    // YouTube এ next video — browser history forward বা JS
                    webView?.evaluateJavascript(JS_NEXT, null)
                }
                override fun onSkipToPrevious() {
                    // YouTube এ previous — video restart বা history back
                    webView?.evaluateJavascript(JS_PREV, null)
                }
                override fun onSeekTo(pos: Long) {
                    // Seek bar drag — seconds এ convert করে YouTube কে জানাও
                    val seconds = pos / 1000
                    webView?.evaluateJavascript(
                        "try { document.querySelector('video').currentTime = $seconds; } catch(e) {}",
                        null
                    )
                }
            })
            setActive(true)
        }
        updateMediaState()
    }

    // WebView থেকে playback state sync করার জন্য JS inject করো
    // pageFinished এ call করা হয়
    private fun syncPlaybackState() {
        webView?.evaluateJavascript("""
            (function() {
                var v = document.querySelector('video');
                if (!v) return 'unknown';
                return v.paused ? 'paused' : 'playing';
            })();
        """.trimIndent()) { result ->
            val playing = result?.trim('"') == "playing"
            if (isPlaying != playing) {
                isPlaying = playing
                updateMediaState()
                updateNotification(currentTitle)
            }
        }
    }

    private fun updateMediaState() {
        val state = if (isPlaying) PlaybackState.STATE_PLAYING
                    else           PlaybackState.STATE_PAUSED
        val playbackState = PlaybackState.Builder()
            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO
            )
            .build()
        mediaSession?.setPlaybackState(playbackState)

        // Metadata update — notification এ title দেখানোর জন্য
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE,  currentTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "YouTube")
            .build()
        mediaSession?.setMetadata(metadata)
    }

    // JavaScript snippets — YouTube এ play/pause/next/prev control
    private val JS_PLAY  = "try { document.querySelector('video')?.play(); } catch(e) {}"
    private val JS_PAUSE = "try { document.querySelector('video')?.pause(); } catch(e) {}"
    private val JS_NEXT  = """
        try {
            // YouTube next button click
            var btn = document.querySelector('.ytp-next-button') ||
                      document.querySelector('[aria-label*="next" i]') ||
                      document.querySelector('[aria-label*="Next" i]');
            if (btn) btn.click();
            else { window.history.forward(); }
        } catch(e) {}
    """.trimIndent()
    private val JS_PREV  = """
        try {
            var v = document.querySelector('video');
            if (v && v.currentTime > 3) {
                // 3 সেকেন্ডের বেশি হলে restart করো
                v.currentTime = 0;
            } else {
                // একেবারে শুরুতে থাকলে previous click
                var btn = document.querySelector('.ytp-prev-button') ||
                          document.querySelector('[aria-label*="previous" i]') ||
                          document.querySelector('[aria-label*="Previous" i]');
                if (btn) btn.click();
                else { window.history.back(); }
            }
        } catch(e) {}
    """.trimIndent()

    // FIX 1: Ghost container setup
    private fun setupGhostContainer() {
        ghostContainer = android.widget.FrameLayout(this)
        // ─────────────────────────────────────────────────────────────────
        // FIX (Problem 3): Ghost container কে 1×1 pixel করলে YouTube player
        // resize event পায় → reload হয়।
        //
        // Solution: ghost container কে screen size এর সমান করো।
        // FLAG_NOT_TOUCHABLE + FLAG_NOT_FOCUSABLE থাকায় user এটা দেখবে না বা
        // interact করতে পারবে না। কিন্তু WebView মনে করবে সে full size এ আছে।
        // ─────────────────────────────────────────────────────────────────
        val dm = resources.displayMetrics
        ghostParams = WindowManager.LayoutParams(
            dm.widthPixels,     // ← 1 এর বদলে full screen width
            dm.heightPixels,    // ← 1 এর বদলে full screen height
            0, 0,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // alpha = 0 — পুরোপুরি transparent, user দেখবে না
            alpha = 0f
        }

        try {
            windowManager.addView(ghostContainer, ghostParams)
        } catch (e: Exception) {
            // Permission নেই — ghost container ছাড়াই চলবে
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification(currentTitle))

        when (intent?.action) {
            ACTION_DISMISS    -> { tearDown(); stopSelf(); return START_NOT_STICKY }
            ACTION_MINIMIZE   -> { if (!isMinimized) showMinimized() }
            ACTION_RESTORE    -> { if (isMinimized)  showFull() }
            ACTION_PLAY_PAUSE -> {
                if (isPlaying) {
                    isPlaying = false
                    webView?.evaluateJavascript(JS_PAUSE, null)
                } else {
                    isPlaying = true
                    webView?.evaluateJavascript(JS_PLAY, null)
                }
                updateMediaState()
                updateNotification(currentTitle)
            }
            ACTION_PREV -> {
                webView?.evaluateJavascript(JS_PREV, null)
            }
            ACTION_NEXT -> {
                webView?.evaluateJavascript(JS_NEXT, null)
            }
            ACTION_LAUNCH   -> {
                val newUrl    = intent.getStringExtra(EXTRA_URL)   ?: "https://m.youtube.com"
                val newTitle  = intent.getStringExtra(EXTRA_TITLE) ?: "YouTube"
                val noReload  = intent.getBooleanExtra(EXTRA_NO_RELOAD, false)

                if (webView != null && (fullWindow != null || isMinimized)) {
                    // WebView ও window আগে থেকে চলছে।
                    currentUrl   = newUrl
                    currentTitle = newTitle

                    if (isMinimized) {
                        removeBubble()
                        isMinimized = false
                        // noReload = true হলে loadUrl() ডাকবো না
                        // WebView ইতিমধ্যে MainActivity তে চলছিল,
                        // ghost container এ যাবে, video চলতে থাকবে
                        if (!noReload) webView?.loadUrl(normalizeYoutubeUrl(newUrl))
                        buildFullWindow()
                    } else {
                        // Full window চলছে
                        if (!noReload) webView?.loadUrl(normalizeYoutubeUrl(newUrl))
                        updateNotification(newTitle)
                    }
                } else {
                    // প্রথমবার launch — সব নতুন করে তৈরি করো
                    currentUrl   = newUrl
                    currentTitle = newTitle
                    removeFull(); removeBubble()

                    // ── KEY FIX: pendingWebView থাকলে সেটাই ব্যবহার করো ──
                    // MainActivity Home press করলে pendingWebView set করে।
                    // getOrBuildWebView() এ consume হবে — reload হবে না।
                    buildFullWindow()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tearDown()
        // Ghost container সরাও
        ghostContainer?.let { runCatching { windowManager.removeView(it) } }
        ghostContainer = null
        // pendingWebView cleanup — consume না হলে leak এড়াতে
        pendingWebView = null
        // MediaSession release
        mediaSession?.setActive(false)
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    // ── Full floating window ───────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    private fun buildFullWindow() {
        isMinimized = false
        val dm      = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        val overlayType = overlayWindowType()

        val params = WindowManager.LayoutParams(
            winW, winH, posX, posY,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            // FLAG_NOT_FOCUSABLE সরানো হয়েছে — back key receive করার জন্য
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background  = buildRoundedBg()
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(14).toFloat())
                }
            }
            // ── Back key intercept ────────────────────────────────────────
            // Floating window এ back চাপলে WebView history navigate করো
            // FLAG_NOT_FOCUSABLE সরানো আছে বলেই এটা কাজ করে
            isFocusableInTouchMode = true
            isFocusable = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK &&
                    event.action == KeyEvent.ACTION_UP) {
                    val wv = webView
                    if (wv != null && wv.canGoBack()) {
                        wv.goBack()
                    } else {
                        showMinimized()
                    }
                    true
                } else {
                    false
                }
            }
        }

        val titleBar = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(8), dp(8), dp(8))
            setBackgroundColor(0xFF0F0F0F.toInt())
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(44)
        }

        val ytLogo = android.widget.TextView(this).apply {
            text      = "▶ YouTube"
            textSize  = 13f
            setTextColor(0xFFFF0000.toInt())
            typeface  = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(6), 0)
        }

        val titleTv = android.widget.TextView(this).apply {
            text      = currentTitle.take(28)
            textSize  = 12f
            setTextColor(0xFFCCCCCC.toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
            maxLines  = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        // Instance variable update — WebViewClient এ এই reference capture থাকে
        titleTvRef = titleTv

        val btnMinimize = buildIconBtn("▬", 0xFFAAAAAA.toInt()) {
            showMinimized()
        }

        val btnSize = buildIconBtn("⊡", 0xFFAAAAAA.toInt()) {
            val newW: Int
            val newH: Int
            if (winW < screenW * 0.87f) {
                newW = (screenW * 0.96f).toInt()
                newH = (screenH * 0.72f).toInt()
            } else {
                newW = (screenW * 0.92f).toInt()
                newH = (screenH * 0.58f).toInt()
            }
            winW = newW; winH = newH
            params.width = winW; params.height = winH
            clampPosition(screenW, screenH, params)
            windowManager.updateViewLayout(fullWindow, params)
        }

        val btnClose = buildIconBtn("✕", 0xFFFF5555.toInt()) {
            tearDown(); stopSelf()
        }

        titleBar.addView(ytLogo)
        titleBar.addView(titleTv)
        titleBar.addView(btnMinimize)
        titleBar.addView(btnSize)
        titleBar.addView(btnClose)

        attachDragListener(titleBar, params, screenW, screenH) {
            windowManager.updateViewLayout(fullWindow, params)
        }

        // FIX 1: WebView already আছে কিনা দেখো — ghost থেকে রি-অ্যাটাচ করো
        val wv = getOrBuildWebView()
        webView = wv

        // Ghost থেকে সরিয়ে fullWindow এ attach করো
        (wv.parent as? ViewGroup)?.removeView(wv)

        // Resize handle
        val resizeHandle = android.widget.TextView(this).apply {
            text      = "⠿"
            textSize  = 18f
            setTextColor(0xFF555555.toInt())
            setPadding(dp(4), dp(2), dp(10), dp(6))
            gravity   = Gravity.END
            setBackgroundColor(0xFF0F0F0F.toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        attachResizeListener(resizeHandle, params, screenW, screenH)

        root.addView(titleBar)
        root.addView(wv, android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(resizeHandle)

        fullWindow = root
        windowManager.addView(root, params)
        updateNotification(currentTitle)
    }

    // ── Minimized bubble ───────────────────────────────────────────────────────

    private fun showMinimized() {
        isMinimized = true

        // FIX 1: fullWindow সরানোর আগে WebView কে ghost এ move করো
        // এতে WebView কখনো detach হবে না → audio চলতে থাকবে
        val wv = webView
        if (wv != null && ghostContainer != null) {
            (wv.parent as? ViewGroup)?.removeView(wv)
            // Ghost container (full screen size) এ move করো
            // WebView live থাকে, audio চলে, user দেখে না
            ghostContainer?.addView(wv, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))
            // Full visibility spoof + force video play inject করো
            // এতে minimize করলেও audio বন্ধ হয় না (Problem 1 fix)
            injectVisibilitySpoof(wv)
        }

        removeFull()  // এখন fullWindow সরালে WebView detach হবে না

        val dm = resources.displayMetrics
        val bSize = dp(60)

        val bParams = WindowManager.LayoutParams(
            bSize, bSize,
            dm.widthPixels - bSize - dp(12),
            dm.heightPixels - bSize - dp(180),
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val bubble = android.widget.FrameLayout(this).apply {
            background = buildCircleBg(0xFFFF0000.toInt())
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            elevation = dp(8).toFloat()
        }

        val icon = android.widget.TextView(this).apply {
            text      = "▶"
            textSize  = 22f
            setTextColor(0xFFFFFFFF.toInt())
            gravity   = Gravity.CENTER
            layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        bubble.addView(icon)

        var dStartX = 0f; var dStartY = 0f
        var bParamX = 0;  var bParamY = 0
        var moved = false

        bubble.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    dStartX = ev.rawX; dStartY = ev.rawY
                    bParamX = bParams.x; bParamY = bParams.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - dStartX).toInt()
                    val dy = (ev.rawY - dStartY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) {
                        moved = true
                        bParams.x = bParamX + dx
                        bParams.y = bParamY + dy
                        clampPosition(dm.widthPixels, dm.heightPixels, bParams)
                        windowManager.updateViewLayout(bubbleView, bParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) showFull()
                    true
                }
                else -> false
            }
        }

        bubbleView = bubble
        windowManager.addView(bubble, bParams)
        updateNotification("▶ Minimized — tap to restore")
    }

    private fun showFull() {
        removeBubble()
        // ─────────────────────────────────────────────────────────────────
        // FIX (Problem 3): Bubble → full restore করার সময় reload আটকানো
        //
        // Ghost container full screen size, তাই WebView সেই size এই আছে।
        // buildFullWindow() এ re-attach করলে size পরিবর্তন হতে পারে।
        // Re-attach এর আগে visibility spoof inject করো যাতে resize event
        // YouTube কে trigger না করে।
        // ─────────────────────────────────────────────────────────────────
        webView?.evaluateJavascript("""
            (function() {
                try {
                    // Resize observer কে block করো সাময়িকভাবে
                    if (!window.__rasOrigResizeObserver) {
                        window.__rasOrigResizeObserver = window.ResizeObserver;
                    }
                    window.ResizeObserver = function(cb) {
                        var obs = new window.__rasOrigResizeObserver(cb);
                        obs.__rasSuppressed = true;
                        return obs;
                    };
                    // 500ms পরে restore করো
                    setTimeout(function() {
                        if (window.__rasOrigResizeObserver) {
                            window.ResizeObserver = window.__rasOrigResizeObserver;
                        }
                    }, 500);
                } catch(e) {}
            })();
        """.trimIndent(), null)
        buildFullWindow()
    }

    // WebView একবার তৈরি করো, পরে রিইউজ করো।
    // titleTv নতুন হলেও WebViewClient এ titleTvRef দিয়ে update হবে।
    private fun getOrBuildWebView(): WebView {
        // 1. Service এর নিজস্ব WebView আছে — রিইউজ করো
        val existing = webView
        if (existing != null) {
            return existing
        }

        // 2. MainActivity থেকে pendingWebView এসেছে — সেটা consume করো
        // এই WebView টা ইতিমধ্যে video চালাচ্ছে, reload করা যাবে না
        val pending = pendingWebView
        if (pending != null) {
            pendingWebView = null   // consume — একবারই ব্যবহার হবে
            // pending WebView এর parent থেকে detach করো
            // (MainActivity র Compose hierarchy তে attached থাকতে পারে)
            (pending.parent as? ViewGroup)?.removeView(pending)
            // Visibility spoof inject করো — detach হলেও audio চলবে
            injectVisibilitySpoof(pending)
            return pending
        }

        // 3. কিছুই নেই — নতুন WebView তৈরি করো এবং load করো
        return buildYoutubeWebView().also {
            it.loadUrl(normalizeYoutubeUrl(currentUrl))
        }
    }

    // ── YouTube WebView builder ────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildYoutubeWebView(): WebView {
        return WebView(this).apply {
            settings.apply {
                javaScriptEnabled                = true
                domStorageEnabled                = true
                databaseEnabled                  = true
                loadWithOverviewMode             = true
                useWideViewPort                  = true
                builtInZoomControls              = true
                displayZoomControls              = false
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess                  = true
                allowContentAccess               = true
                loadsImagesAutomatically         = true
                setSupportZoom(true)
                cacheMode                        = WebSettings.LOAD_DEFAULT
                mixedContentMode                 = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString                  = YT_USER_AGENT
            }

            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

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

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)

                    val t = view.title?.takeIf { it.isNotEmpty() } ?: url
                    titleTvRef?.text = t.take(28)
                    currentTitle = t

                    updateMediaState()
                    updateNotification(t)

                    injectVisibilitySpoof(view)
                    injectYoutubeHacks(view, url)

                    // ── YouTube Ad Pruner inject ────────────────────────────
                    if (url.contains("youtube.com") || url.contains("youtu.be")) {
                        view.evaluateJavascript(
                            com.rasel.RasFocus.selfcontrol.familybrowser.YouTubeAdPruner
                                .getJsInjectScript(),
                            null
                        )
                    }

                    view.postDelayed({ syncPlaybackState() }, 1000L)
                }

                // ── Adult block + Safe Search (shouldOverrideUrlLoading) ──
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()

                    // Adult block
                    if (com.rasel.RasFocus.selfcontrol.familybrowser.AdBlocker.isAdultSite(url)) {
                        return true
                    }

                    // Safe Search enforce
                    val safeUrl = com.rasel.RasFocus.selfcontrol.familybrowser.SafeSearchEnforcer
                        .enforceIfNeeded(url)
                    if (safeUrl != null) {
                        view.loadUrl(safeUrl)
                        return true
                    }

                    if (!url.startsWith("http://") && !url.startsWith("https://")) return true
                    currentUrl = url
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {}
                override fun onReceivedTitle(view: WebView, title: String) {
                    // titleTvRef ব্যবহার করো
                    titleTvRef?.text = title.take(28)
                    currentTitle = title
                    updateNotification(title)
                }
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    (fullWindow as? ViewGroup)?.addView(view, 1)
                }
                override fun onHideCustomView() {}
            }
        }
    }

    // Visibility spoof — আলাদা function হিসেবে, ghost এ থাকাকালীনও call করা যায়
    private fun injectVisibilitySpoof(view: WebView) {
        view.evaluateJavascript("""
            (function() {
                try {
                    Object.defineProperty(document, 'hidden', {
                        get: function() { return false; }, configurable: true
                    });
                    Object.defineProperty(document, 'visibilityState', {
                        get: function() { return 'visible'; }, configurable: true
                    });
                    Object.defineProperty(document, 'webkitHidden', {
                        get: function() { return false; }, configurable: true
                    });
                    Object.defineProperty(document, 'webkitVisibilityState', {
                        get: function() { return 'visible'; }, configurable: true
                    });
                    var _add = document.addEventListener.bind(document);
                    document.addEventListener = function(type, fn, opts) {
                        if (type === 'visibilitychange' || type === 'webkitvisibilitychange') return;
                        _add(type, fn, opts);
                    };
                    window.addEventListener = (function(original) {
                        return function(type, fn, opts) {
                            if (type === 'pagehide') return;
                            original.call(this, type, fn, opts);
                        };
                    })(window.addEventListener);

                    // ─── FIX (Problem 2): Lock screen audio fix ───────────
                    // Screen lock হলে Page Visibility API 'hidden' হয় →
                    // YouTube player pause করে। সেটা override করার পাশাপাশি
                    // video element কে force resume করো।
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        try {
                            if (videos[i].paused) {
                                videos[i].play().catch(function(){});
                            }
                        } catch(e) {}
                    }
                    // ─────────────────────────────────────────────────────
                } catch(e) {}
            })();
        """.trimIndent(), null)
    }

    private fun injectYoutubeHacks(view: WebView, url: String) {
        if (!url.contains("youtube.com") && !url.contains("youtu.be")) return
        view.evaluateJavascript("""
            (function() {
                try {
                    var yt = window.yt || {};
                    if (yt.player && yt.player.Application) {
                        var app = yt.player.Application.create__(null, null);
                        if (app) { try { app.unmuteBackgroundAudio_(); } catch(e) {} }
                    }
                    if ('mediaSession' in navigator) {
                        navigator.mediaSession.setActionHandler('play', null);
                        navigator.mediaSession.setActionHandler('pause', null);
                    }
                } catch(e) {}
            })();
        """.trimIndent(), null)
    }

    // ── Touch helpers ──────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragListener(
        handle: View,
        params: WindowManager.LayoutParams,
        screenW: Int,
        screenH: Int,
        onUpdate: () -> Unit
    ) {
        var startX = 0f; var startY = 0f
        var pX = 0;      var pY = 0
        var dragging = false

        handle.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    startX = ev.rawX; startY = ev.rawY
                    pX = params.x;    pY = params.y
                    params.flags = params.flags and
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) {
                        params.x = pX + (ev.rawX - startX).toInt()
                        params.y = pY + (ev.rawY - startY).toInt()
                        clampPosition(screenW, screenH, params)
                        onUpdate()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachResizeListener(
        handle: View,
        params: WindowManager.LayoutParams,
        screenW: Int,
        screenH: Int
    ) {
        handle.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    resizeStartW    = params.width
                    resizeStartH    = params.height
                    resizeStartRawX = ev.rawX
                    resizeStartRawY = ev.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // FIX 3: Throttle — প্রতি 32ms এর বেশি বার update করবো না
                    val now = System.currentTimeMillis()
                    if (now - lastResizeMs < RESIZE_THROTTLE) return@setOnTouchListener true
                    lastResizeMs = now

                    val dX = (ev.rawX - resizeStartRawX).toInt()
                    val dY = (ev.rawY - resizeStartRawY).toInt()

                    // FIX 3: Minimum height dp(280) — এর নিচে গেলে WebView
                    // renderer "hidden" মনে করে pause করে দেয়
                    params.width  = max(dp(260), min(screenW, resizeStartW + dX))
                    params.height = max(dp(280), min(screenH - params.y - dp(40), resizeStartH + dY))
                    winW = params.width; winH = params.height
                    runCatching { windowManager.updateViewLayout(fullWindow, params) }
                    true
                }
                else -> false
            }
        }
    }

    // ── Utility ────────────────────────────────────────────────────────────────

    private fun normalizeYoutubeUrl(url: String): String {
        return when {
            url.contains("youtube.com") || url.contains("youtu.be") ->
                url.replace("www.youtube.com", "m.youtube.com")
                   .replace("://youtube.com", "://m.youtube.com")
            url == "about:blank" || url.isEmpty() -> "https://m.youtube.com"
            else -> url
        }
    }

    private fun clampPosition(sw: Int, sh: Int, p: WindowManager.LayoutParams) {
        p.x = max(0, min(sw - p.width,  p.x))
        p.y = max(0, min(sh - p.height - dp(40), p.y))
        posX = p.x; posY = p.y
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun overlayWindowType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun buildIconBtn(label: String, color: Int, onClick: () -> Unit) =
        android.widget.TextView(this).apply {
            text      = label
            textSize  = 16f
            setTextColor(color)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            gravity   = Gravity.CENTER
            setOnClickListener { onClick() }
        }

    private fun buildRoundedBg(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape         = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius  = dp(14).toFloat()
            setColor(0xFF0F0F0F.toInt())
            setStroke(dp(1), 0xFF333333.toInt())
        }
    }

    private fun buildCircleBg(color: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape    = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun removeFull() {
        // FIX 1: WebView destroy করবো না, ghost এ আছে
        // শুধু fullWindow (wrapper) সরাও
        fullWindow?.let { runCatching { windowManager.removeView(it) } }
        fullWindow = null
    }

    private fun removeBubble() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
    }

    private fun tearDown() {
        // Ghost থেকে WebView সরিয়ে destroy করো
        val wv = webView
        if (wv != null) {
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.stopLoading()
            wv.destroy()
            webView = null
        }
        removeFull()
        removeBubble()
        runCatching {
            stopService(Intent(this, BackgroundAudioService::class.java))
        }
    }

    // ── Notification ───────────────────────────────────────────────────────────

    private fun updateNotification(title: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(title))
    }

    private fun buildNotification(title: String): Notification {
        // ── PendingIntents ─────────────────────────────────────────────────────
        fun svcIntent(action: String, reqCode: Int) = PendingIntent.getService(
            this, reqCode,
            Intent(this, YoutubeFloatingWindowService::class.java).apply { this.action = action },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val restoreIntent   = svcIntent(ACTION_RESTORE,    1)
        val stopIntent      = svcIntent(ACTION_DISMISS,    2)
        val minimizeIntent  = svcIntent(ACTION_MINIMIZE,   3)
        val playPauseIntent = svcIntent(ACTION_PLAY_PAUSE, 4)
        val prevIntent      = svcIntent(ACTION_PREV,       5)
        val nextIntent      = svcIntent(ACTION_NEXT,       6)

        // ── Play/Pause icon — state অনুযায়ী পরিবর্তন হয় ──────────────────────
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause
                            else           android.R.drawable.ic_media_play
        val playPauseLabel = if (isPlaying) "Pause" else "Play"

        // ── Build notification with native Notification.Builder (supports MediaStyle) ──
        val session = mediaSession
        val style = android.app.Notification.MediaStyle()
            .setShowActionsInCompactView(1, 2, 3)
        if (session != null) {
            style.setMediaSession(session.sessionToken)
        }

        // ── Build notification ─────────────────────────────────────────────────
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return android.app.Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("YouTube — Floating")
                .setContentText(title.take(60))
                .setSubText("Tap to restore")
                .setContentIntent(restoreIntent)
                .addAction(android.app.Notification.Action.Builder(
                    android.R.drawable.ic_menu_view, "Minimize", minimizeIntent).build())
                .addAction(android.app.Notification.Action.Builder(
                    android.R.drawable.ic_media_previous, "Previous", prevIntent).build())
                .addAction(android.app.Notification.Action.Builder(
                    playPauseIcon, playPauseLabel, playPauseIntent).build())
                .addAction(android.app.Notification.Action.Builder(
                    android.R.drawable.ic_media_next, "Next", nextIntent).build())
                .addAction(android.app.Notification.Action.Builder(
                    android.R.drawable.ic_delete, "Close", stopIntent).build())
                .setStyle(style)
                .setOngoing(true)
                .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
                .setCategory(android.app.Notification.CATEGORY_TRANSPORT)
                .build()
        } else {
            @Suppress("DEPRECATION")
            return NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("YouTube — Floating")
                .setContentText(title.take(60))
                .setSubText("Tap to restore")
                .setContentIntent(restoreIntent)
                .addAction(android.R.drawable.ic_menu_view,       "Minimize",    minimizeIntent)
                .addAction(android.R.drawable.ic_media_previous,  "Previous",    prevIntent)
                .addAction(playPauseIcon,                          playPauseLabel, playPauseIntent)
                .addAction(android.R.drawable.ic_media_next,      "Next",        nextIntent)
                .addAction(android.R.drawable.ic_delete,          "Close",       stopIntent)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "YouTube Floating Window",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "YouTube floating player controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }
}
