package com.rasel.RasFocus.selfcontrol.familybrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * YoutubeActivity — পুরো native YouTube app এর মতো অভিজ্ঞতা
 *
 * - কোনো browser bar নেই, শুধু fullscreen WebView
 * - m.youtube.com দিয়ে mobile YouTube UI
 * - Lock screen এ audio চলতে থাকে (BackgroundAudioService)
 * - Home press করলে floating window এ চলে (YoutubeFloatingWindowService)
 * - Edge-to-edge fullscreen
 */
class YoutubeActivity : ComponentActivity() {

    private var webView: WebView? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    companion object {
        private const val YT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.6367.82 Mobile Safari/537.36"

        // ── Adult search keywords (YouTube search এ block করা হবে) ──────────
        private val ADULT_SEARCH_KEYWORDS = setOf(
            "porn", "pornhub", "xvideos", "xnxx", "xhamster", "sex", "nude",
            "naked", "xxx", "hentai", "nsfw", "boobs", "tits", "ass", "milf",
            "anal", "blowjob", "threesome", "orgasm", "masturbat", "erotic",
            "camgirl", "onlyfans", "brazzers", "redtube", "youporn",
            "rule34", "deepfake", "jav", "desi sex", "bangla sex", "hindi sex",
            "indian sex", "desiporn", "banglaporn", "sexy", "hottest",
            "bhabhi", "horny", "pussy", "dick", "cock", "penis", "vagina",
            "underage", "loli", "shota"
        )

        fun launch(activity: Activity) {
            val intent = Intent(activity, YoutubeActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Edge-to-edge (system bars দেখা যাবে — wifi/charge icon থাকবে) ──────
        // শুধু edge-to-edge করো — system bars hide করো না।
        // hide() call করলে status bar (wifi/charge/battery) এবং navigation bar চলে যায়।
        // Fullscreen video তে শুধু customView দেখানোর সময় hide করা হবে।
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        window.statusBarColor  = Color.BLACK
        window.navigationBarColor = Color.BLACK

        // System bar icons light করো (dark background এ white icons)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
        // NOTE: hide() এখানে call করা হচ্ছে না — status bar ও nav bar সবসময় দেখা যাবে।
        // শুধু fullscreen video (customView) এ hide করা হবে।

        // ── Root layout ────────────────────────────────────────────────────────
        val rootFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            // Edge-to-edge এ system bars এর জন্য padding দাও
            // যাতে WebView status bar ও navigation bar এর নিচে না যায়
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(0, systemBars.top, 0, systemBars.bottom)
                insets
            }
        }
        setContentView(rootFrame)

        // ── WebView setup ──────────────────────────────────────────────────────
        webView = object : WebView(this) {
            override fun onPause() { /* suppress — audio stays alive */ }
            override fun onResume() { super.onResume() }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setBackgroundColor(Color.BLACK)

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
                mixedContentMode                 = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode                        = WebSettings.LOAD_DEFAULT
                userAgentString                  = YT_USER_AGENT
            }

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    injectVisibilitySpoof(view)
                    injectYoutubeHacks(view)
                    injectRemoveOpenInAppButton(view)   // "Open in app" button মুছে দাও
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()
                    // YouTube domain এর মধ্যে থাকো
                    if (!url.startsWith("http://") && !url.startsWith("https://")) return true

                    // ── Adult Keyword Search Block ──────────────────────────
                    // YouTube search query তে adult keyword থাকলে block করো
                    val adultBlockHtml = checkAdultSearchKeyword(url)
                    if (adultBlockHtml != null) {
                        view.loadDataWithBaseURL(null, adultBlockHtml, "text/html", "UTF-8", null)
                        return true
                    }

                    // ── YouTube Safe Search (Search results filter করো) ──────
                    // YouTube search এ &safe=strict যোগ করো
                    val safeUrl = buildYoutubeSafeSearchUrl(url)
                    if (safeUrl != null && safeUrl != url) {
                        view.loadUrl(safeUrl)
                        return true
                    }

                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                // Fullscreen video support
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (customView != null) {
                        rootFrame.removeView(customView)
                        callback.onCustomViewHidden()
                        return
                    }
                    customView = view
                    customViewCallback = callback

                    // ── Fullscreen video: system bars hide করো ──────────────
                    val ctrl = WindowInsetsControllerCompat(window, window.decorView)
                    ctrl.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                    ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    // Padding সরাও — fullscreen এ সব জায়গা ব্যবহার করো
                    rootFrame.setPadding(0, 0, 0, 0)

                    rootFrame.addView(
                        view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    webView?.visibility = View.GONE
                }

                override fun onHideCustomView() {
                    webView?.visibility = View.VISIBLE
                    customView?.let { rootFrame.removeView(it) }
                    customView = null
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null

                    // ── Normal mode: system bars ফিরিয়ে আনো ───────────────
                    val ctrl = WindowInsetsControllerCompat(window, window.decorView)
                    ctrl.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                    // Padding আবার restore করো
                    ViewCompat.requestApplyInsets(rootFrame)
                }
            }

            rootFrame.addView(this)

            // Safe Search সহ YouTube load করো
            val safeYtUrl = buildYoutubeSafeSearchUrl("https://m.youtube.com") ?: "https://m.youtube.com"
            loadUrl(safeYtUrl)
        }
    }

    // ── YouTube Safe Search URL Builder ───────────────────────────────────────
    // YouTube search results এ &safe=strict যোগ করো।
    // m.youtube.com/?q=... বা /results?search_query=... URL এ কাজ করে।
    private fun buildYoutubeSafeSearchUrl(url: String): String? {
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host?.lowercase() ?: return null

            if (!host.contains("youtube.com") && !host.contains("youtu.be")) return null

            val path = uri.path ?: ""

            // Search results page: /results?search_query=... বা /?q=...
            val isSearchPage = path.contains("/results") ||
                uri.getQueryParameter("search_query") != null ||
                uri.getQueryParameter("q") != null

            if (!isSearchPage) return null

            // ইতিমধ্যে strict থাকলে পরিবর্তন দরকার নেই
            if (uri.getQueryParameter("safe") == "strict") return null

            // safe=strict যোগ বা update করো
            val builder = uri.buildUpon()
            builder.appendQueryParameter("safe", "strict")

            builder.build().toString()
        } catch (e: Exception) { null }
    }

    // ── Home press → YouTube floating window ──────────────────────────────────
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val wv = webView ?: return
        val currentUrl  = wv.url  ?: "https://m.youtube.com"
        val currentTitle = wv.title ?: "YouTube"

        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            android.provider.Settings.canDrawOverlays(this)
        else true

        if (hasOverlay) {
            // Step 1: Visibility spoof inject — page hide হওয়ার আগেই
            // এতে YouTube মনে করে page এখনো visible → audio বন্ধ হয় না
            wv.evaluateJavascript("""
                (function() {
                    try {
                        Object.defineProperty(document, 'hidden', { get: function(){ return false; }, configurable: true });
                        Object.defineProperty(document, 'visibilityState', { get: function(){ return 'visible'; }, configurable: true });
                        Object.defineProperty(document, 'webkitHidden', { get: function(){ return false; }, configurable: true });
                        Object.defineProperty(document, 'webkitVisibilityState', { get: function(){ return 'visible'; }, configurable: true });
                        // visibilitychange event suppress করো
                        var _add = document.addEventListener.bind(document);
                        document.addEventListener = function(type, fn, opts) {
                            if (type === 'visibilitychange' || type === 'webkitvisibilitychange') return;
                            _add(type, fn, opts);
                        };
                        // video pause আটকাও
                        var videos = document.querySelectorAll('video');
                        for (var i = 0; i < videos.length; i++) {
                            try {
                                videos[i].addEventListener('pause', function(e) {
                                    e.stopImmediatePropagation();
                                    e.preventDefault();
                                }, true);
                            } catch(e) {}
                        }
                    } catch(e) {}
                })();
            """.trimIndent(), null)

            // Step 2: ── KEY FIX ──
            // pendingWebView set করো — service এ নতুন WebView তৈরি হবে না
            // এই WebView-ই floating window এ reuse হবে, video restart হবে না
            com.rasel.RasFocus.selfcontrol.familybrowser.service
                .YoutubeFloatingWindowService.pendingWebView = wv

            // Step 3: launchNoReload — EXTRA_NO_RELOAD=true দিয়ে launch
            // service এ loadUrl() call হবে না — ager video চলতে থাকবে
            com.rasel.RasFocus.selfcontrol.familybrowser.service.YoutubeFloatingWindowService
                .launchNoReload(this, currentUrl, currentTitle)

            // Step 4: Activity এর webView reference সরাও
            // WebView এখন service এর — Activity destroy করলেও webView.destroy() না হোক
            webView = null
        }
    }

    // ── Lock screen → audio চলতে থাকুক ───────────────────────────────────────
    override fun onPause() {
        super.onPause()
        // সাথে সাথেই BackgroundAudioService start — YouTube pause হওয়ার আগেই
        startBgAudioService()

        // WebView resume করো — lock screen এ audio বন্ধ না হয়
        webView?.post {
            webView?.resumeTimers()
            webView?.onResume()
            webView?.postDelayed({
                injectVisibilitySpoof(webView ?: return@postDelayed)
                webView?.evaluateJavascript("""
                    (function() {
                        try {
                            var videos = document.querySelectorAll('video');
                            for (var i = 0; i < videos.length; i++) {
                                try { if (videos[i].paused) videos[i].play().catch(function(){}); } catch(e) {}
                            }
                            var p = document.getElementById('movie_player');
                            if (p && typeof p.playVideo === 'function') p.playVideo();
                        } catch(e) {}
                    })();
                """.trimIndent(), null)
            }, 50)
        }
    }

    override fun onResume() {
        super.onResume()
        webView?.resumeTimers()
        webView?.onResume()
        // Unlock করার পরে video resume
        webView?.postDelayed({
            webView?.evaluateJavascript("""
                (function() {
                    try {
                        var videos = document.querySelectorAll('video');
                        for (var i = 0; i < videos.length; i++) {
                            try { if (videos[i].paused) videos[i].play().catch(function(){}); } catch(e) {}
                        }
                        var p = document.getElementById('movie_player');
                        if (p && typeof p.playVideo === 'function') p.playVideo();
                    } catch(e) {}
                })();
            """.trimIndent(), null)
        }, 100)
    }

    override fun onRestart() {
        super.onRestart()
        // App এ ফিরলে background service বন্ধ করো
        if (!isFinishing) stopBgAudioService()
    }

    override fun onStop() {
        super.onStop()
        // webView null মানে floating service এ গেছে — audio service দরকার নেই
        if (webView != null) {
            startBgAudioService()
        }
    }

    override fun onDestroy() {
        // webView null হওয়া মানে floating service এ চলে গেছে
        // সেক্ষেত্রে destroy করা যাবে না — audio বন্ধ হয়ে যাবে
        if (webView != null) {
            stopBgAudioService()
            webView?.destroy()
            webView = null
        }
        super.onDestroy()
    }

    // ── Back press — WebView history বা Activity বন্ধ ─────────────────────────
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // ── JS inject helpers ──────────────────────────────────────────────────────

    private fun injectVisibilitySpoof(view: WebView) {
        view.evaluateJavascript("""
            (function() {
                try {
                    Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                    Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                    Object.defineProperty(document, 'webkitHidden', { get: function() { return false; }, configurable: true });
                    Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });
                    var _add = document.addEventListener.bind(document);
                    document.addEventListener = function(type, fn, opts) {
                        if (type === 'visibilitychange' || type === 'webkitvisibilitychange') return;
                        _add(type, fn, opts);
                    };
                } catch(e) {}
            })();
        """.trimIndent(), null)
    }

    private fun injectYoutubeHacks(view: WebView) {
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

    // ── "Open in App" Button Remover ──────────────────────────────────────────
    // YouTube mobile এ যে "Open in YouTube app" banner/button থাকে সেটা remove করো।
    // Selectors: #app-related-ytcutter, .ytm-action-button, [class*="open-app"],
    //            #external-app-banner, .app-badge, ytm-app-related-endscreen-renderer
    private fun injectRemoveOpenInAppButton(view: WebView) {
        view.evaluateJavascript("""
            (function() {
                function removeOpenAppElements() {
                    // Selector list — YouTube এর বিভিন্ন version এ বিভিন্ন class থাকে
                    var selectors = [
                        '#app-related-ytcutter',
                        '.ytm-action-button',
                        'ytm-action-button',
                        '[class*="open-in-app"]',
                        '[class*="openInApp"]',
                        '[class*="open_in_app"]',
                        '#external-app-banner',
                        '.external-app-banner',
                        'ytm-app-related-endscreen-renderer',
                        '[data-layer="5"]',
                        '.app-badge-container',
                        '.ytp-app-related',
                        // Smart app banner (iOS/Android smart banner meta)
                        'meta[name="apple-itunes-app"]',
                        'link[rel="alternate"][media]'
                    ];
                    selectors.forEach(function(sel) {
                        try {
                            document.querySelectorAll(sel).forEach(function(el) {
                                el.remove();
                            });
                        } catch(e) {}
                    });

                    // Text content দিয়ে button খোঁজো — "Open app", "Watch in app"
                    try {
                        var allButtons = document.querySelectorAll('button, a, .yt-spec-button-shape-next, [role="button"]');
                        allButtons.forEach(function(el) {
                            var txt = (el.innerText || el.textContent || '').toLowerCase();
                            if (txt.includes('open app') || txt.includes('open in app') ||
                                txt.includes('watch in app') || txt.includes('use the app') ||
                                txt.includes('get the app') || txt.includes('open youtube')) {
                                el.remove();
                            }
                        });
                    } catch(e) {}
                }

                // সাথে সাথে run করো
                removeOpenAppElements();

                // DOM mutation observer — dynamic inject হলেও catch করবে
                try {
                    var observer = new MutationObserver(function(mutations) {
                        mutations.forEach(function(m) {
                            if (m.addedNodes.length > 0) {
                                removeOpenAppElements();
                            }
                        });
                    });
                    observer.observe(document.body || document.documentElement, {
                        childList: true, subtree: true
                    });
                } catch(e) {}
            })();
        """.trimIndent(), null)
    }

    // ── Adult Search Keyword Checker ──────────────────────────────────────────
    // YouTube search URL এর query parameter check করো।
    // Match হলে blocked HTML return করো, না হলে null।
    private fun checkAdultSearchKeyword(url: String): String? {
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host?.lowercase() ?: return null
            if (!host.contains("youtube.com")) return null

            // Search query parameter বের করো (YouTube mobile: search_query বা q)
            val query = (
                uri.getQueryParameter("search_query") ?:
                uri.getQueryParameter("q") ?:
                ""
            ).lowercase().trim()

            if (query.isEmpty()) return null

            // keyword match check
            val matched = ADULT_SEARCH_KEYWORDS.any { keyword ->
                query.contains(keyword.lowercase())
            }

            if (matched) buildAdultSearchBlockedPage(query) else null
        } catch (e: Exception) { null }
    }

    // ── Adult Search Blocked Page HTML ───────────────────────────────────────
    private fun buildAdultSearchBlockedPage(query: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        background: #0f0f0f;
                        font-family: -apple-system, Roboto, sans-serif;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        min-height: 100vh;
                        padding: 24px;
                    }
                    .card {
                        background: #1a1a1a;
                        border-radius: 20px;
                        padding: 40px 28px;
                        text-align: center;
                        max-width: 380px;
                        width: 100%;
                        border: 1px solid #2a2a2a;
                    }
                    .icon { font-size: 64px; margin-bottom: 18px; }
                    .title {
                        color: #ff4444;
                        font-size: 22px;
                        font-weight: 700;
                        margin-bottom: 12px;
                    }
                    .brand {
                        color: #ff4444;
                        font-weight: 800;
                    }
                    .desc {
                        color: #aaaaaa;
                        font-size: 14px;
                        line-height: 1.6;
                        margin-bottom: 28px;
                    }
                    .query-tag {
                        display: inline-block;
                        background: #2a2a2a;
                        color: #888;
                        font-size: 13px;
                        padding: 4px 12px;
                        border-radius: 20px;
                        margin-bottom: 24px;
                        word-break: break-all;
                    }
                    .back-btn {
                        display: block;
                        width: 100%;
                        padding: 14px;
                        background: #272727;
                        color: #ffffff;
                        border: none;
                        border-radius: 12px;
                        font-size: 15px;
                        font-weight: 600;
                        cursor: pointer;
                        text-decoration: none;
                    }
                    .back-btn:active { background: #333; }
                    .logo {
                        color: #555;
                        font-size: 12px;
                        margin-top: 20px;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="icon">🛡️</div>
                    <div class="title">Adult Content Blocked</div>
                    <p class="desc">
                        এই search টি adult content ধারণ করে।<br>
                        <span class="brand">RasFocus Family Browser</span> এটি block করেছে।
                    </p>
                    <div class="query-tag">"${query.take(40)}"</div>
                    <button class="back-btn" onclick="history.back()">← Go Back</button>
                    <div class="logo">RasFocus Family Browser</div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    // ── BackgroundAudioService helpers ─────────────────────────────────────────

    private fun startBgAudioService() {
        val svc = Intent(
            this,
            com.rasel.RasFocus.selfcontrol.familybrowser.service.BackgroundAudioService::class.java
        ).apply {
            putExtra(
                com.rasel.RasFocus.selfcontrol.familybrowser.service.BackgroundAudioService.EXTRA_TITLE,
                "YouTube — Playing"
            )
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
            else startService(svc)
        } catch (_: Exception) {}
    }

    private fun stopBgAudioService() {
        try {
            stopService(
                Intent(
                    this,
                    com.rasel.RasFocus.selfcontrol.familybrowser.service.BackgroundAudioService::class.java
                )
            )
        } catch (_: Exception) {}
    }
}
