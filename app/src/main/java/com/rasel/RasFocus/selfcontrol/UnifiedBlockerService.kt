package com.rasel.RasFocus.selfcontrol

// ============================================================
// UnifiedBlockerService.kt
// AdultBlockService + RasFocusBlockingService — একটাই Service
// Settings → Accessibility → "RasFocus Unified Blocker" enable করলেই
// দুটো file-এর সব blocking logic কাজ করবে।
// ============================================================

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.rasel.RasFocus.DataManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class UnifiedBlockerService : AccessibilityService() {

    companion object {
        var instance: UnifiedBlockerService? = null
        // extrem_block.kt এর RasFocusBlockingService যেখানে blockingInstance ব্যবহার হয়
        val blockingInstance: UnifiedBlockerService? get() = instance
        private const val NOTIFICATION_CHANNEL_ID = "RasFocus_Unified_Channel"
        private const val NOTIFICATION_ID = 2001
    }

    // ── Prefs ──────────────────────────────────────────────────────────
    private lateinit var adultPrefs: SharedPreferences    // AdultBlockService prefs
    private lateinit var blockerPrefs: BlockerPrefs       // RasFocusBlockingService prefs
    private lateinit var recoveryPrefs: SharedPreferences

    // ── Adult Block: Keyword & Site Lists ─────────────────────────────
    private val hardcoreKeywords = listOf(
        "porn", "xxx", "sex", "nude", "nsfw", "sexy", "hentai", "rule34", "milf",
        "blowjob", "tits", "boobs", "pussy", "dick", "cock", "escort", "bdsm",
        "fetish", "erotica", "dildo", "webcam", "camgirls", "xvideos", "pornhub",
        "xnxx", "xhamster", "brazzers", "onlyfans", "playboy", "chaturbate",
        "stripchat", "eporner", "spankbang", "redtube", "youporn",
        "চটি", "পর্ণ", "সেক্স", "নগ্ন", "উলঙ্গ", "বেশ্যা", "মাগি", "খানকি",
        "যৌন", "পর্ণগ্রাফি", "রেন্ডি", "চোদাচুতি", "গরম ভিডিও", "খারাপ ছবি",
        "যৌন মিলন", "যৌনাঙ্গ", "চুদো", "নগ্নতা"
    )
    private val romanticKeywords = listOf(
        "hot dance", "seductive dance", "item song", "belly dance",
        "kissing scene", "bikini", "swimsuit", "sexy dance", "cleavage", "hot scene",
        "romantic kiss", "bedroom scene", "bath scene", "rain dance", "bold scene",
        "semi nude", "lingerie", "erotic", "hot song", "romantic video hot",
        "navel show", "deep neck", "short dress sexy", "unfaithful scene"
    )
    private val adultWebsites = listOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com",
        "youporn.com", "brazzers.com", "spankbang.com", "eporner.com", "chaturbate.com"
    )
    private var dynamicAdultList = listOf<String>()

    // extrem_block এর baked-in adult site keywords
    private val adultSiteKeywords = listOf(
        "porn", "xxx", "nude", "nsfw", "sexy", "hentai", "rule34", "milf",
        "blowjob", "tits", "boobs", "pussy", "dick", "cock", "escort", "bdsm",
        "fetish", "erotica", "dildo", "webcam", "camgirls", "onlyfans", "chaturbate",
        "hot dance", "seductive dance", "item song", "belly dance",
        "kissing scene", "bikini", "swimsuit", "sexy dance", "cleavage", "hot scene",
        "romantic kiss", "bedroom scene", "bath scene", "rain dance", "bold scene",
        "semi nude", "lingerie", "erotic", "hot song", "romantic video hot",
        "navel show", "deep neck", "short dress sexy", "unfaithful scene",
        "চটি", "পর্ণ", "সেক্স", "নগ্ন", "উলঙ্গ", "বেশ্যা", "মাগি", "খানকি",
        "যৌন", "পর্ণগ্রাফি", "রেন্ডি", "চোদাচুতি", "গরম ভিডিও", "খারাপ ছবি",
        "যৌন মিলন", "যৌনাঙ্গ", "চুদো", "নগ্নতা",
        "18videosz", "24porn", "3movs", "4tube", "adulttime", "beeg", "brazzers",
        "chaturbate", "eporner", "hentai", "onlyfans", "pornhub", "redtube",
        "spankbang", "stripchat", "xhamster", "xnxx", "xvideos", "youporn"
    )

    // extrem_block এর full domain set
    private val adultDomains = setOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com",
        "youporn.com", "brazzers.com", "spankbang.com", "eporner.com", "chaturbate.com",
        "onlyfans.com", "stripchat.com", "beeg.com", "hentaigasm.com", "hentaihaven.org",
        "playboy.com", "pornmd.com", "tube8.com", "tubegalore.com", "txxx.com",
        "realitykings.com", "digitalplayground.com", "fakehub.com", "evilangel.com",
        "teamskeet.com", "mofosex.com", "bangbrosnetwork.com", "jerkmate.com",
        "luckycrush.live", "redgifs.com", "motherless.com", "hardsextube.com"
    )

    // ── Quote lists (AdultBlockService থেকে) ─────────────────────────
    private val muslimQuotesBn = listOf("মুমিনদের বলুন, তারা যেন তাদের দৃষ্টি নত রাখে...", "লজ্জাশীলতা ঈমানের অঙ্গ।")
    private val muslimQuotesEn = listOf("Tell the believing men to reduce their vision...", "Modesty is a branch of faith.")
    private val motivationalQuotesBn = listOf("সফলতা আসে ফোকাস থেকে, ডিস্ট্রাকশন থেকে নয়।", "যে নিজের মনকে নিয়ন্ত্রণ করতে পারে, সে পৃথিবী জয় করতে পারে।")
    private val motivationalQuotesEn = listOf("Success comes from focus, not from distraction.", "He who can control his mind can conquer the world.")

    // ── State Variables ────────────────────────────────────────────────
    private var lastBlockTime: Long = 0
    private var lastLoggedUrl: String = ""
    private var isPanicActive = false
    private var panicEndTime = 0L
    private var lastPopupTime = 0L
    private var fbBlockLastTime = 0L

    // Deep Study
    private var isDeepStudyActive = false
    private var isDeepStudyBreak = false
    private var dsTimer: android.os.CountDownTimer? = null
    private var dsTimeLeftMillis: Long = 0

    // Periodic Reminders
    private val periodicHandler = Handler(Looper.getMainLooper())
    private var periodicRunnable: Runnable? = null

    // Window overlays
    private var windowManager: WindowManager? = null
    private var floatingTimerView: View? = null
    private var timerTextView: TextView? = null
    private var breakScreenView: View? = null
    private var sessionCompleteView: View? = null
    private var fullScreenBlockView: View? = null
    private var overlayView: View? = null   // extrem_block overlay
    private var isPopupVisible = false

    private var initialX: Int = 0; private var initialY: Int = 0
    private var initialTouchX: Float = 0f; private var initialTouchY: Float = 0f

    // Audio
    private var audioTrack: android.media.AudioTrack? = null
    private var isPlayingNoise = false
    private var noiseThread: Thread? = null

    // adultsite.txt lazy-loaded list (extrem_block style)
    private var _adultDomainList: List<String>? = null

    // SafeSearch redirect throttle
    private val safeSearchLastRedirect = HashMap<String, Long>()

    // ── Browser package list ───────────────────────────────────────────
    private val ALL_BROWSER_PKGS = setOf(
        "com.android.chrome", "com.sec.android.app.sbrowser",
        "org.mozilla.firefox", "com.microsoft.emmx", "com.brave.browser",
        "com.opera.browser", "com.opera.mini.native", "com.UCMobile.intl",
        "com.yandex.browser", "com.kiwibrowser.browser", "com.vivaldi.browser",
        "mark.via.gp", "com.duckduckgo.mobile.android", "com.mi.globalbrowser",
        "com.hihonor.browser", "com.huawei.browser", "com.puffin.client.android"
    )

    private val BROWSER_URL_BAR_IDS = mapOf(
        "com.android.chrome" to listOf("com.android.chrome:id/url_bar", "com.android.chrome:id/omnibox_text"),
        "com.sec.android.app.sbrowser" to listOf("com.sec.android.app.sbrowser:id/location_bar_edit_text"),
        "org.mozilla.firefox" to listOf("org.mozilla.firefox:id/mozac_browser_toolbar_url_view"),
        "com.microsoft.emmx" to listOf("com.microsoft.emmx:id/address_bar_edit_text"),
        "com.brave.browser" to listOf("com.brave.browser:id/url_bar"),
        "com.duckduckgo.mobile.android" to listOf("com.duckduckgo.mobile.android:id/omnibarTextInput")
    )

    private val BLOCKED_BROWSERS = setOf(
        "com.opera.browser", "com.opera.mini.native", "com.UCMobile.intl",
        "org.mozilla.firefox", "com.brave.browser", "com.microsoft.emmx",
        "com.duckduckgo.mobile.android", "com.yandex.browser",
        "com.kiwibrowser.browser", "com.vivaldi.browser", "mark.via.gp",
        "com.puffin.client.android"
    )
    private val BLOCKED_BROWSER_NAMES = listOf(
        "Opera", "Opera Mini", "UC Browser", "Firefox", "Brave",
        "Edge", "DuckDuckGo", "Yandex Browser", "Kiwi", "Vivaldi", "Via Browser", "Puffin"
    )

    private val rasFocusName = "RasFocus"

    // ══════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        DataManager.init(this)
        adultPrefs = getSharedPreferences("RasFocusAdultPrefs", Context.MODE_PRIVATE)
        blockerPrefs = BlockerPrefs(this)
        recoveryPrefs = getSharedPreferences("FocusRecovery", Context.MODE_PRIVATE)
        createNotificationChannel()
        loadAdultSiteFile()
    }

    private fun loadAdultSiteFile() {
        try {
            dynamicAdultList = assets.open("adultsite.txt")
                .bufferedReader().readText()
                .split("\n", "\r\n")
                .map { it.trim().lowercase().replace("*.", ".") }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            android.util.Log.e("RasFocus", "adultsite.txt load error: ${e.message}")
        }
    }

    private fun getAdultDomainListLazy(): List<String> {
        if (_adultDomainList == null) {
            _adultDomainList = try {
                assets.open("adultsite.txt").bufferedReader()
                    .readLines()
                    .map { it.trim().trimStart('*', '.').lowercase() }
                    .filter { it.isNotEmpty() }
            } catch (e: Exception) { emptyList() }
        }
        return _adultDomainList!!
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 50
        }
        serviceInfo = info

        startForeground(NOTIFICATION_ID, buildNotification("RasFocus Protection Active", "Monitoring for distractions..."))
        startPeriodicPopupChecker()

        // Deep Study resume
        val isSavedActive = recoveryPrefs.getBoolean("isTimerActive", false)
        val targetEndTime = recoveryPrefs.getLong("targetEndTime", 0L)
        val sessionType = recoveryPrefs.getInt("sessionType", 0)
        val playSound = recoveryPrefs.getBoolean("playSound", false)
        val soundType = recoveryPrefs.getInt("soundType", 0)
        if (isSavedActive && targetEndTime > System.currentTimeMillis()) {
            val remainingMillis = targetEndTime - System.currentTimeMillis()
            if (sessionType == 0) resumeDeepStudySession(remainingMillis, playSound, soundType)
            else startDeepStudyBreak((remainingMillis / 60000).toInt())
        } else {
            recoveryPrefs.edit().clear().apply()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        stopPeriodicPopupChecker()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopAmbientSound()
        stopPeriodicPopupChecker()
    }

    override fun onInterrupt() {}

    // ══════════════════════════════════════════════════════════════════
    // MAIN EVENT — দুটো service-এর logic একসাথে
    // ══════════════════════════════════════════════════════════════════

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg0 = event.packageName?.toString() ?: return

        // ★ FAST PATH — extrem_block style: event.text থেকে adult URL instant check
        if (fastAdultCheck(event, pkg0)) return

        // ── 1. PANIC MODE (AdultBlockService priority #1) ──────────────
        if (isPanicActive) {
            if (System.currentTimeMillis() < panicEndTime) {
                if (isBrowserApp(pkg0)) {
                    multiLayerForceHome()
                    if (System.currentTimeMillis() - lastBlockTime > 5000) {
                        lastBlockTime = System.currentTimeMillis()
                        showBlockOverlay("PANIC MODE", "All browsers are disabled for 15 mins.")
                    }
                    return
                }
            } else {
                isPanicActive = false
            }
        }

        // ── 2. KEYBOARD TYPING BLOCK ───────────────────────────────────
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val isKeyboardBlockEnabled = adultPrefs.getBoolean("keyboardTypingBlock", false)
            if (!isSystemApp(pkg0) && isKeyboardBlockEnabled) {
                val source = event.source ?: return
                val typedText = source.text?.toString()?.lowercase() ?: event.text.joinToString(" ").lowercase()
                if (typedText.isNotBlank()) {
                    val customKeywords = DataManager.userCustomAdultKeywords.map { it.lowercase() }
                    val cbHardcore = adultPrefs.getBoolean("cbHardcore", true)
                    val cbRomantic = adultPrefs.getBoolean("cbRomantic", true)
                    val cbAdultWeb = adultPrefs.getBoolean("cbAdultWeb", true)
                    val isHardcoreMatch = cbHardcore && hardcoreKeywords.any { typedText.contains(it) }
                    val isRomanticMatch = cbRomantic && romanticKeywords.any { typedText.contains(it) }
                    val isDynamicMatch = cbAdultWeb && dynamicAdultList.any { site ->
                        val domain = site.replace(".", "").replace("www", "")
                        domain.length > 3 && typedText.contains(domain)
                    }
                    val isCustomMatch = customKeywords.isNotEmpty() && customKeywords.any { typedText.contains(it) }
                    if (isHardcoreMatch || isRomanticMatch || isDynamicMatch || isCustomMatch) {
                        try {
                            val clearArgs = android.os.Bundle().apply {
                                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                            }
                            if (!source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)) {
                                val selectArgs = android.os.Bundle().apply {
                                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, typedText.length)
                                }
                                source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectArgs)
                                source.performAction(AccessibilityNodeInfo.ACTION_CUT)
                            }
                        } catch (e: Exception) {}
                        if (DataManager.isAdultFocusActive || DataManager.is24HourLockActive) {
                            triggerAdultBlockAction(pkg0, "Blocked Keyword Typed")
                        }
                        return
                    }
                }
            }
        }

        // ── Early exit if nothing is active ───────────────────────────
        val adultFocusOn = DataManager.isFocusActive || DataManager.isAdultFocusActive ||
                isDeepStudyActive || DataManager.is24HourLockActive
        val extremeOn = blockerPrefs.blockAdult || blockerPrefs.blockAdultSiteList ||
                blockerPrefs.blockYtShorts || blockerPrefs.blockReels ||
                blockerPrefs.blockInstaSearch || blockerPrefs.blockWaChannels ||
                blockerPrefs.blockTikTok || blockerPrefs.uninstallProtection ||
                blockerPrefs.blockReboot || blockerPrefs.blockPowerOff

        if (!adultFocusOn && !extremeOn) return

        // ── 3. 24H LOCK SYNC ───────────────────────────────────────────
        if (DataManager.is24HourLockActive) {
            if (System.currentTimeMillis() >= DataManager.lock24hEndTime) {
                DataManager.is24HourLockActive = false
                DataManager.isAdultFocusActive = false
            } else {
                DataManager.isAdultFocusActive = true
            }
        }

        val root = rootInActiveWindow ?: return
        val pkg = event.packageName?.toString() ?: run { root.recycle(); return }

        try {
            var handled = false

            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

                val currentUrl = if (isBrowserApp(pkg)) extractBrowserUrl(root, pkg)?.lowercase() ?: "" else ""
                val windowTitle = if (isBrowserApp(pkg)) extractWindowTitle(root) else ""
                val screenText = event.text.joinToString(" ").lowercase()

                // ── AdultBlockService: Incognito Block ──
                val blockIncognito = adultPrefs.getBoolean("strictBlockIncognito", false)
                if (!handled && blockIncognito && isBrowserApp(pkg)) {
                    if (screenText.contains("incognito") || screenText.contains("inprivate") ||
                        screenText.contains("private browsing") || windowTitle.lowercase().contains("incognito")) {
                        multiLayerForceHome()
                        showBlockOverlay("STRICT MODE", "Incognito / Private windows are not allowed.")
                        root.recycle(); return
                    }
                }

                // ── AdultBlockService: Settings/Uninstaller Block ──
                val strictLockMode = adultPrefs.getBoolean("strictLockMode", false)
                if (!handled && (strictLockMode || DataManager.blockSettingsAndUninstall)) {
                    if (pkg.contains("com.android.settings") || pkg.contains("packageinstaller") ||
                        pkg.contains("taskmanager")) {
                        multiLayerForceHome()
                        showBlockOverlay("ACCESS DENIED", "System Settings / Uninstallers are locked.")
                        root.recycle(); return
                    }
                }

                // ── Deep Study Block ──
                if (!handled && isDeepStudyActive && DataManager.isDeepStudyStrict) {
                    checkDeepStudyBlocking(pkg, currentUrl)
                    handled = true
                }

                // ── AdultBlockService: checkAndBlockContent ──
                if (!handled && adultFocusOn) {
                    handled = checkAndBlockContent(pkg, currentUrl, screenText, windowTitle)
                }

                // ── extrem_block handlers (only if not already handled) ──
                if (!handled) handled = handleAdultContent(root, pkg)
                if (!handled) handleSafeSearch(root, pkg)
                if (!handled) handled = handleWebViewAdultBlock(root, pkg)
                if (!handled) handled = handleImageVideoSearch(root, pkg)
                if (!handled) handled = handleYouTubeShorts(root, pkg)
                if (!handled) handled = handleReels(root, pkg)
                if (!handled) handled = handleInstagramSearch(root, pkg)
                if (!handled) handled = handleWhatsAppChannels(root, pkg)
                if (!handled) handled = handleInstagramStories(root, pkg)
                if (!handled) handled = handleSnapchat(root, pkg)
                if (!handled) handled = handleWhatsAppStatus(root, pkg)
                if (!handled) handled = handleWaBusinessBlocking(root, pkg)
                if (!handled) handled = handleTikTok(root, pkg)
                if (!handled) handled = handleAppSearchKeyword(event, root, pkg)
                if (!handled && pkg == "com.facebook.katana") {
                    handled = handleFacebookVideo(root, pkg)
                    if (!handled) handled = handleFacebookFeedShortVideo(root, pkg)
                }
                if (!handled) handled = handleUnsupportedBrowsers(root, pkg)
                if (!handled) handled = handleNewlyInstalledApps(root, pkg)
                if (!handled) handled = handleUninstallProtection(root, pkg)
                if (!handled) handleRebootProtection(root, pkg)
            }
        } catch (e: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } finally {
            root.recycle()
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // ADULT BLOCK SERVICE LOGIC (AdultBlockFull.kt থেকে)
    // ══════════════════════════════════════════════════════════════════

    private fun checkAndBlockContent(
        packageName: String, url: String, screenText: String, windowTitle: String
    ): Boolean {
        val cbAdultWeb = adultPrefs.getBoolean("cbAdultWeb", true)
        val cbHardcore = adultPrefs.getBoolean("cbHardcore", true)
        val cbRomantic = adultPrefs.getBoolean("cbRomantic", true)
        val cbFbReels = adultPrefs.getBoolean("cbFbReels", true)
        val cbYtShorts = adultPrefs.getBoolean("cbYtShorts", true)

        var shouldBlockNormal = false
        var isAdultViolation = false
        var blockReason = ""

        if (DataManager.isAdultFocusActive || DataManager.is24HourLockActive) {
            when {
                cbAdultWeb && dynamicAdultList.any { url.contains(it) || screenText.contains(it) } -> {
                    isAdultViolation = true; blockReason = "Restricted Website / Keyword"
                }
                cbAdultWeb && adultWebsites.any { url.contains(it) || screenText.contains(it.substringBefore(".")) } -> {
                    isAdultViolation = true; blockReason = "Adult Website Detected"
                }
                cbHardcore && hardcoreKeywords.any { url.contains(it) || screenText.contains(it) } -> {
                    isAdultViolation = true; blockReason = "Explicit Keyword Detected"
                }
                cbRomantic && romanticKeywords.any { url.contains(it) || screenText.contains(it) } -> {
                    isAdultViolation = true; blockReason = "Softcore/Romantic Content Detected"
                }
                cbYtShorts && ((packageName.contains("youtube") && screenText.contains("shorts")) || url.contains("shorts")) -> {
                    shouldBlockNormal = true; blockReason = "YouTube Shorts are blocked!"
                }
                cbFbReels && ((packageName.contains("facebook") && screenText.contains("reels")) ||
                        url.contains("reel") || url.contains("instagram.com/reels")) -> {
                    shouldBlockNormal = true; blockReason = "FB / IG Reels are blocked!"
                }
            }

            val isSilentMonitorActive = adultPrefs.getBoolean("strictSilentMonitor", false)
            if (!isAdultViolation && isSilentMonitorActive && isBrowserApp(packageName) && url.isNotEmpty()) {
                logSilentUrl(windowTitle, url)
            }
        }

        if (DataManager.isFocusActive && !shouldBlockNormal && !isAdultViolation && url.isNotEmpty()) {
            for (web in DataManager.userWebList) {
                val coreName = if (web.contains(".")) web.substringBefore(".") else web
                if (coreName.length > 2 && url.contains(coreName)) {
                    shouldBlockNormal = true; blockReason = "Website is in your blocklist."; break
                }
            }
        }

        if (!shouldBlockNormal && !isAdultViolation && DataManager.isFocusActive) {
            if (DataManager.simpleBlockMode == 1) {
                if (DataManager.userAppList.any { packageName.contains(it) }) {
                    shouldBlockNormal = true; blockReason = "App is in your blocklist."
                }
            } else if (DataManager.simpleBlockMode == 0) {
                if (!isSystemApp(packageName) && !DataManager.userAppList.any { packageName.contains(it) }) {
                    shouldBlockNormal = true; blockReason = "Only allowed apps can run."
                }
            }
        }

        if (isAdultViolation) {
            triggerAdultBlockAction(packageName, blockReason)
            return true
        } else if (shouldBlockNormal) {
            if (System.currentTimeMillis() - lastBlockTime < 5000) return true
            lastBlockTime = System.currentTimeMillis()
            val mainMsg = if (DataManager.showQuotes) getReligiousQuote() else getMotivationalQuote()
            Handler(Looper.getMainLooper()).post {
                showBlockOverlay("ACCESS DENIED!", mainMsg)
            }
            if (isBrowserApp(packageName)) {
                Handler(Looper.getMainLooper()).postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 200)
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); setPackage(packageName)
                        })
                    } catch (_: Exception) { multiLayerForceHome() }
                }, 600)
            } else multiLayerForceHome()
            return true
        }
        return false
    }

    private fun triggerAdultBlockAction(packageName: String, reason: String) {
        if (System.currentTimeMillis() - lastBlockTime < 5000) return
        lastBlockTime = System.currentTimeMillis()
        DataManager.totalBlockedCount++
        DataManager.cleanStreakDays = 0
        Handler(Looper.getMainLooper()).post {
            showBlockOverlay("Adult Content", "Reason: $reason")
        }
        if (isBrowserApp(packageName)) {
            Handler(Looper.getMainLooper()).postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 200)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); setPackage(packageName)
                    })
                } catch (_: Exception) { multiLayerForceHome() }
            }, 600)
        } else multiLayerForceHome()
    }

    fun activatePanicMode() {
        isPanicActive = true
        panicEndTime = System.currentTimeMillis() + 15 * 60 * 1000L
        showBlockOverlay("PANIC MODE ACTIVATED", "All browsers will be blocked for 15 minutes.")
    }

    private fun logSilentUrl(windowTitle: String, url: String) {
        if (url.isEmpty() || url == lastLoggedUrl) return
        lastLoggedUrl = url
        try {
            val timeStr = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.getDefault()).format(Date())
            val logData = "[$timeStr] TITLE: $windowTitle | URL: $url\n"
            val logFile = File(applicationContext.filesDir, "silent_monitor_log.txt")
            FileOutputStream(logFile, true).use { it.write(logData.toByteArray()) }
        } catch (e: Exception) {}
    }

    private fun getReligiousQuote(): String {
        val list = when (DataManager.adultReligion) {
            0 -> if (DataManager.adultLanguage == 0) muslimQuotesBn else muslimQuotesEn
            else -> if (DataManager.adultLanguage == 0) motivationalQuotesBn else motivationalQuotesEn
        }
        return list[Random.nextInt(list.size)]
    }

    private fun getMotivationalQuote(): String {
        val list = if (DataManager.adultLanguage == 0) motivationalQuotesBn else motivationalQuotesEn
        return list[Random.nextInt(list.size)]
    }

    // ══════════════════════════════════════════════════════════════════
    // EXTREME BLOCK (extrem_block.kt / RasFocusBlockingService থেকে)
    // ══════════════════════════════════════════════════════════════════

    // ── FAST PATH ─────────────────────────────────────────────────────
    private fun fastAdultCheck(event: AccessibilityEvent, pkg: String): Boolean {
        if (pkg !in ALL_BROWSER_PKGS) return false
        if (!blockerPrefs.blockAdult && !blockerPrefs.blockAdultSiteList) return false
        val eventText = event.text.joinToString(" ").lowercase()
        if (eventText.isBlank()) return false
        if (blockerPrefs.blockAdult && (eventText.contains("safe=off") || eventText.contains("safe=images"))) {
            blockAdultInBrowser(pkg); return true
        }
        if (isAdultUrl(eventText)) { blockAdultInBrowser(pkg); return true }
        return false
    }

    private fun blockAdultInBrowser(pkg: String) {
        val now = System.currentTimeMillis()
        if (now - lastPopupTime > 1500L) {
            lastPopupTime = now
            Handler(Looper.getMainLooper()).post { showBlockOverlay("Adult Content", "This page contains adult content and has been blocked.") }
            Handler(Looper.getMainLooper()).postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 200)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (pkg in ALL_BROWSER_PKGS) setPackage(pkg)
                    })
                } catch (_: Exception) { performGlobalAction(GLOBAL_ACTION_HOME) }
            }, 600)
        }
    }

    private fun isAdultUrl(text: String): Boolean {
        if (blockerPrefs.blockAdult && adultSiteKeywords.any { text.contains(it) }) return true
        val host = try {
            val raw = if (text.startsWith("http")) text.trim() else "https://${text.trim()}"
            android.net.Uri.parse(raw).host?.lowercase()?.removePrefix("www.") ?: ""
        } catch (e: Exception) { "" }
        if (host.isNotEmpty()) {
            if (blockerPrefs.blockAdult && adultDomains.contains(host)) return true
            if (blockerPrefs.blockAdultSiteList && getAdultDomainListLazy().any { host == it || host.endsWith(".$it") }) return true
        }
        return false
    }

    private fun handleAdultContent(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (pkg !in ALL_BROWSER_PKGS) return false
        if (!blockerPrefs.blockAdult && !blockerPrefs.blockAdultSiteList) return false
        val url = extractBrowserUrl(root, pkg)?.lowercase() ?: ""
        val check = if (url.isNotBlank()) url else collectAllText(root).lowercase()
        if (check.isBlank()) return false
        if (isAdultUrl(check)) { blockAdultInBrowser(pkg); return true }
        return false
    }

    private fun handleSafeSearch(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!blockerPrefs.blockAdult) return false
        if (pkg !in ALL_BROWSER_PKGS) return false
        val url = extractBrowserUrl(root, pkg)?.lowercase() ?: return false
        if (!url.contains("google.") || !url.contains("/search")) return false
        if (url.contains("safe=off") || url.contains("safe=images")) { blockAdultInBrowser(pkg); return true }
        if (url.contains("safe=active") || url.contains("safe=strict")) return false
        val now = System.currentTimeMillis()
        if ((now - (safeSearchLastRedirect[pkg] ?: 0L)) < 1500L) return false
        safeSearchLastRedirect[pkg] = now
        val safeUrl = url + (if (url.contains("?")) "&" else "?") + "safe=active"
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(safeUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    setPackage(pkg)
                })
            } catch (_: Exception) {}
        }, 300)
        return false
    }

    private fun handleWebViewAdultBlock(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!blockerPrefs.blockAdult && !blockerPrefs.blockAdultSiteList) return false
        if (pkg in ALL_BROWSER_PKGS || pkg.startsWith("com.android.") || pkg == "android") return false
        if (!containsWebViewNode(root)) return false
        val urlText = findNodeWithUrl(root)?.lowercase() ?: ""
        val checkText = if (urlText.isNotBlank()) urlText else collectAllText(root).lowercase()
        if (checkText.isBlank()) return false
        if (isAdultUrl(checkText)) {
            Handler(Looper.getMainLooper()).post { showBlockOverlay("Adult Content", "Adult content blocked in app.") }
            performGlobalAction(GLOBAL_ACTION_BACK)
            return true
        }
        return false
    }

    private fun handleImageVideoSearch(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!blockerPrefs.blockSearch || pkg != "com.android.chrome") return false
        val url = extractBrowserUrl(root, pkg) ?: ""
        val urlBlocked = url.contains("tbm=isch") || url.contains("tbm=vid")
        val tabBlocked = url.isEmpty() && (isTabActive(root, "Images") || isTabActive(root, "Videos"))
        if (urlBlocked || tabBlocked) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        return false
    }

    private fun handleYouTubeShorts(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!blockerPrefs.blockYtShorts || pkg != "com.google.android.youtube") return false
        val tab = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/pivot_bar_item_label")
            .any { it.text?.toString()?.equals("Shorts", true) == true && (it.isSelected || it.parent?.isSelected == true) }
        if (tab) { blockWithMessage("YouTube Shorts", "YouTube Shorts is blocked."); return true }
        val player = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/shorts_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_player_page").isNotEmpty()
        if (player) { blockWithMessage("YouTube Shorts", "YouTube Shorts is blocked."); return true }
        val fb = root.findAccessibilityNodeInfosByText("Shorts")
            .any { it.isSelected || it.isChecked || it.parent?.isSelected == true }
        if (fb) { blockWithMessage("YouTube Shorts", "YouTube Shorts is blocked."); return true }
        return false
    }

    private fun handleReels(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!blockerPrefs.blockReels) return false
        when (pkg) {
            "com.instagram.android" -> {
                if (root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_tab").isNotEmpty()) {
                    blockWithMessage("Instagram Reels", "Instagram Reels is blocked."); return true
                }
                if (root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_viewer_container").isNotEmpty()) {
                    blockWithMessage("Instagram Reels", "Instagram Reels is blocked."); return true
                }
                if (isTabActive(root, "Reels")) { blockWithMessage("Instagram Reels", "Instagram Reels is blocked."); return true }
            }
            "com.facebook.katana" -> {
                if (isFbReelsViewerOpen(root)) {
                    blockFacebookContent("Facebook Reels", "Facebook Reels is blocked."); return true
                }
            }
        }
        return false
    }

    private fun handleInstagramSearch(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!blockerPrefs.blockInstaSearch || pkg != "com.instagram.android") return false
        if (root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/search_tab").isNotEmpty()) {
            performGlobalAction(GLOBAL_ACTION_BACK); return true
        }
        if (root.findAccessibilityNodeInfosByText("Search and explore").isNotEmpty()
            || root.findAccessibilityNodeInfosByText("Search").any { it.isSelected }) {
            performGlobalAction(GLOBAL_ACTION_BACK); return true
        }
        return false
    }

    private fun handleWhatsAppChannels(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!blockerPrefs.blockWaChannels || pkg != "com.whatsapp") return false
        if (root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/updates_tab").isNotEmpty()) {
            performGlobalAction(GLOBAL_ACTION_BACK); return true
        }
        if (root.findAccessibilityNodeInfosByText("Channels").any { it.isSelected || it.isChecked }) {
            performGlobalAction(GLOBAL_ACTION_BACK); return true
        }
        return false
    }

    private fun handleInstagramStories(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!blockerPrefs.blockInstaStories || pkg != "com.instagram.android") return false
        if (root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/story_container_layout").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/stories_viewer_fragment").isNotEmpty()) {
            performGlobalAction(GLOBAL_ACTION_HOME); return true
        }
        return false
    }

    private fun handleSnapchat(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (pkg != "com.snapchat.android") return false
        if (blockerPrefs.blockSnapSpotlight) {
            if (root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/spotlight_tab").isNotEmpty()
                || root.findAccessibilityNodeInfosByText("Spotlight").any { it.isSelected || it.parent?.isSelected == true }) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
        }
        if (blockerPrefs.blockSnapStories) {
            if (root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/story_viewer_container").isNotEmpty()
                || root.findAccessibilityNodeInfosByText("Stories").any { it.isSelected || it.isChecked }) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
        }
        return false
    }

    private fun handleWhatsAppStatus(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!blockerPrefs.blockWaStatus || pkg != "com.whatsapp") return false
        if (root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/status_tab").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/status_list").isNotEmpty()) {
            performGlobalAction(GLOBAL_ACTION_BACK); return true
        }
        if (root.findAccessibilityNodeInfosByText("Status").any { it.isSelected || it.isChecked }) {
            performGlobalAction(GLOBAL_ACTION_BACK); return true
        }
        return false
    }

    private fun handleWaBusinessBlocking(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (pkg != "com.whatsapp.w4b") return false
        if (blockerPrefs.blockWaBusinessStatus) {
            if (root.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/status_tab").isNotEmpty()
                || root.findAccessibilityNodeInfosByText("Status").any { it.isSelected || it.isChecked }) {
                performGlobalAction(GLOBAL_ACTION_BACK); return true
            }
        }
        if (blockerPrefs.blockWaBusinessChannels) {
            if (root.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/updates_tab").isNotEmpty()
                || root.findAccessibilityNodeInfosByText("Channels").any { it.isSelected || it.isChecked }) {
                performGlobalAction(GLOBAL_ACTION_BACK); return true
            }
        }
        return false
    }

    private fun handleTikTok(root: AccessibilityNodeInfo, pkg: String): Boolean {
        val tiktokPkgs = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.tiktok.android")
        if (pkg !in tiktokPkgs) return false
        if (blockerPrefs.blockTikTok) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        if (blockerPrefs.blockTikTokLive) {
            if (root.findAccessibilityNodeInfosByViewId("$pkg:id/live_container").isNotEmpty()
                || root.findAccessibilityNodeInfosByText("LIVE").any { it.isVisibleToUser }) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
        }
        return false
    }

    private fun handleAppSearchKeyword(event: AccessibilityEvent?, root: AccessibilityNodeInfo, pkg: String): Boolean {
        val targetPkgs = setOf("org.telegram.messenger", "org.telegram.messenger.web",
            "com.whatsapp", "com.whatsapp.w4b", "com.facebook.katana", "com.facebook.lite")
        if (pkg !in targetPkgs || event?.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return false
        val typedText = event.text.joinToString(" ").lowercase().trim()
        if (typedText.isBlank()) return false
        val matched = adultSiteKeywords.any { kw -> typedText.contains(kw) }
        if (!matched) return false
        try {
            val source = event.source
            if (source != null) {
                val args = android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                }
                if (!source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    val sel = android.os.Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, typedText.length)
                    }
                    source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
                    source.performAction(AccessibilityNodeInfo.ACTION_CUT)
                }
                source.recycle()
            }
        } catch (e: Exception) {}
        val now2 = System.currentTimeMillis()
        if (now2 - lastPopupTime > 1500L) {
            lastPopupTime = now2
            Handler(Looper.getMainLooper()).post { showBlockOverlay("Adult Content", "Blocked keyword detected in search.") }
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    startActivity(Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    })
                } catch (_: Exception) { performGlobalAction(GLOBAL_ACTION_HOME) }
            }, 400)
        }
        return true
    }

    private fun handleFacebookVideo(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!blockerPrefs.blockFbVideo) return false
        if (root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/watch_tab").isNotEmpty()
            || root.findAccessibilityNodeInfosByText("Watch").any { it.isSelected || it.parent?.isSelected == true }) {
            blockFacebookContent("Facebook Video", "Facebook Watch tab is blocked."); return true
        }
        if (isFbVideoPlayerActuallyOpen(root)) {
            blockFacebookContent("Facebook Video", "Facebook video player is blocked."); return true
        }
        return false
    }

    private fun handleFacebookFeedShortVideo(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!blockerPrefs.blockFbVideo && !blockerPrefs.blockReels) return false
        if (isFbReelsViewerOpen(root)) {
            blockFacebookContent("Facebook Reels", "Facebook Reels is blocked."); return true
        }
        if (blockerPrefs.blockFbVideo) {
            val autoplay = root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/auto_play_video")
                .any { it.isVisibleToUser }
            if (autoplay) { blockFacebookContent("Facebook Video", "Facebook auto-play video is blocked."); return true }
        }
        return false
    }

    private fun handleUnsupportedBrowsers(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!blockerPrefs.blockUnsupported) return false
        if (BLOCKED_BROWSERS.contains(pkg)) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        val recentPkgs = setOf("com.android.systemui", "com.samsung.android.systemui", "com.miui.systemui")
        if (recentPkgs.contains(pkg)) {
            if (BLOCKED_BROWSER_NAMES.any { n -> root.findAccessibilityNodeInfosByText(n).any { it.isVisibleToUser } }) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
        }
        return false
    }

    private fun handleNewlyInstalledApps(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!blockerPrefs.blockNewApps) return false
        val installerPkgs = setOf("com.android.packageinstaller", "com.google.android.packageinstaller")
        if (installerPkgs.contains(pkg)) {
            val confirm = root.findAccessibilityNodeInfosByText("Install").any { it.isClickable && it.isVisibleToUser }
                || root.findAccessibilityNodeInfosByViewId("com.android.packageinstaller:id/install_confirm_panel").any { it.isVisibleToUser }
            if (confirm) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        }
        if (pkg == "com.android.vending") {
            val install = root.findAccessibilityNodeInfosByText("Install").any { it.isClickable && it.isVisibleToUser }
            if (install) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        }
        if (pkg == "com.android.settings") {
            if (root.findAccessibilityNodeInfosByText("Allow from this source").any { it.isVisibleToUser }
                || root.findAccessibilityNodeInfosByText("Install unknown apps").any { it.isVisibleToUser }) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
        }
        return false
    }

    private fun handleUninstallProtection(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!blockerPrefs.uninstallProtection) return false
        try {
            if (pkg == "com.android.settings") {
                if (root.findAccessibilityNodeInfosByViewId("com.android.settings:id/uninstall_button").any { it.isVisibleToUser }) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
                val header = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/entity_header_title")
                    .any { it.text?.toString()?.contains(rasFocusName, true) == true }
                val unBtn = root.findAccessibilityNodeInfosByText("Uninstall").any { it.isClickable && it.isVisibleToUser }
                if (header && unBtn) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
                if (root.findAccessibilityNodeInfosByText("Do you want to uninstall this app?").isNotEmpty()) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
                val accScreen = root.findAccessibilityNodeInfosByText("Accessibility").any { it.isVisibleToUser }
                if (accScreen && root.findAccessibilityNodeInfosByText(rasFocusName).any { it.isVisibleToUser }) {
                    if (root.findAccessibilityNodeInfosByText("Turn off").any { it.isVisibleToUser }
                        || root.findAccessibilityNodeInfosByText("Stop").any { it.isClickable }) {
                        performGlobalAction(GLOBAL_ACTION_HOME); return true
                    }
                }
            }
            val launchers = setOf("com.google.android.apps.nexuslauncher", "com.samsung.android.launcher",
                "com.miui.home", "com.android.launcher", "com.android.launcher3", "com.teslacoilsw.launcher")
            if (launchers.contains(pkg)) {
                if (root.findAccessibilityNodeInfosByText("Uninstall").any { it.isVisibleToUser }) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
            }
        } catch (e: Exception) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        return false
    }

    private fun handleRebootProtection(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!blockerPrefs.blockReboot && !blockerPrefs.blockPowerOff && !blockerPrefs.blockSafeMode
            && !blockerPrefs.blockRecovery && !blockerPrefs.blockAdb) return false
        try {
            val powerMenuPkgs = setOf("com.android.systemui", "com.samsung.android.systemui",
                "com.miui.systemui", "com.oppo.systemui", "com.vivo.systemui", "com.oneplus.systemui")
            if (blockerPrefs.blockReboot && powerMenuPkgs.contains(pkg)) {
                val reboot = root.findAccessibilityNodeInfosByText("Restart").any { it.isVisibleToUser && it.isClickable }
                    || root.findAccessibilityNodeInfosByText("Reboot").any { it.isVisibleToUser && it.isClickable }
                if (reboot) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            }
            if (blockerPrefs.blockPowerOff && powerMenuPkgs.contains(pkg)) {
                val powerOff = root.findAccessibilityNodeInfosByText("Power off").any { it.isVisibleToUser && it.isClickable }
                    || root.findAccessibilityNodeInfosByText("Power off?").isNotEmpty()
                if (powerOff) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            }
            if (blockerPrefs.blockSafeMode) {
                val safeMode = root.findAccessibilityNodeInfosByText("Safe mode").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Restart to safe mode?").isNotEmpty()
                if (safeMode) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            }
            if (blockerPrefs.blockRecovery && pkg == "com.android.settings") {
                val factory = root.findAccessibilityNodeInfosByText("Factory data reset").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Erase all data").any { it.isVisibleToUser }
                if (factory) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            }
            if (blockerPrefs.blockAdb && pkg == "com.android.settings") {
                val adbDialog = root.findAccessibilityNodeInfosByText("Allow USB debugging?").any { it.isVisibleToUser }
                if (adbDialog) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
                val adbScreen = root.findAccessibilityNodeInfosByText("USB debugging").any { it.isVisibleToUser }
                if (adbScreen) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            }
        } catch (e: Exception) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        return false
    }

    // ── Facebook helpers ───────────────────────────────────────────────
    private fun blockFacebookContent(featureTitle: String, reason: String) {
        val now = System.currentTimeMillis()
        if (now - fbBlockLastTime < 1200L) return
        fbBlockLastTime = now
        Handler(Looper.getMainLooper()).post { showBlockOverlay(featureTitle, reason) }
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                startActivity(Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage("com.facebook.katana")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                })
            } catch (_: Exception) { performGlobalAction(GLOBAL_ACTION_BACK) }
        }, 400)
    }

    private fun isFbVideoPlayerActuallyOpen(root: AccessibilityNodeInfo): Boolean {
        return root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/fullscreen_video_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_scrubber").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_control_overlay").isNotEmpty()
    }

    private fun isFbReelsViewerOpen(root: AccessibilityNodeInfo): Boolean {
        return root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_viewer_root").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/short_video_player").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_tab_button")
                .any { it.isSelected || it.isChecked || it.parent?.isSelected == true }
    }

    // ── blockWithMessage (extrem_block style) ─────────────────────────
    private fun blockWithMessage(featureTitle: String, reason: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        val now = System.currentTimeMillis()
        if (now - lastPopupTime > 1500L) {
            lastPopupTime = now
            Handler(Looper.getMainLooper()).post { showBlockOverlay(featureTitle, reason) }
        }
    }

    // ── checkCurrentWindow (for settings UI switch ON callback) ───────
    fun checkCurrentWindow() {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: run { root.recycle(); return }
        try { handleAdultContent(root, pkg) } finally { root.recycle() }
    }

    // ══════════════════════════════════════════════════════════════════
    // SHARED HELPERS
    // ══════════════════════════════════════════════════════════════════

    private fun extractBrowserUrl(root: AccessibilityNodeInfo, pkg: String): String? {
        val ids = BROWSER_URL_BAR_IDS[pkg]
        if (ids != null) {
            for (id in ids) {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                if (nodes.isNotEmpty()) {
                    val t = nodes[0].text?.toString()?.lowercase()
                        ?: nodes[0].contentDescription?.toString()?.lowercase()
                    if (!t.isNullOrBlank()) return t
                }
            }
        }
        return findNodeWithUrl(root)
    }

    private fun findNodeWithUrl(node: AccessibilityNodeInfo): String? {
        val t = node.text?.toString()?.lowercase() ?: ""
        if (t.startsWith("http") || t.startsWith("www.") || t.contains(".com")) return t
        for (i in 0 until node.childCount) {
            val r = findNodeWithUrl(node.getChild(i) ?: continue)
            if (r != null) return r
        }
        return null
    }

    private fun extractWindowTitle(nodeInfo: AccessibilityNodeInfo?): String {
        if (nodeInfo == null) return ""
        if (nodeInfo.className == "android.widget.TextView") {
            val id = nodeInfo.viewIdResourceName
            if (id != null && id.contains("title")) return nodeInfo.text?.toString() ?: ""
        }
        for (i in 0 until nodeInfo.childCount) {
            val title = extractWindowTitle(nodeInfo.getChild(i))
            if (title.isNotEmpty()) return title
        }
        return ""
    }

    private fun isTabActive(root: AccessibilityNodeInfo, name: String) =
        root.findAccessibilityNodeInfosByText(name).any { it.isSelected || it.isChecked || it.isFocused }

    private fun collectAllText(node: AccessibilityNodeInfo?): String {
        node ?: return ""
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?) {
            n ?: return
            n.text?.let { sb.append(it).append(" ") }
            n.contentDescription?.let { sb.append(it).append(" ") }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(node)
        return sb.toString()
    }

    private fun containsWebViewNode(node: AccessibilityNodeInfo?): Boolean {
        node ?: return false
        val cn = node.className?.toString() ?: ""
        if (cn.contains("WebView") || cn.contains("XWalkView")) return true
        for (i in 0 until node.childCount) {
            if (containsWebViewNode(node.getChild(i))) return true
        }
        return false
    }

    private fun isSystemApp(packageName: String): Boolean {
        return packageName.contains("launcher") || packageName.contains("systemui") ||
                packageName.contains("dialer") || packageName.contains("messaging") ||
                packageName.contains("inputmethod") || packageName.contains("keyboard") ||
                packageName == "com.rasel.RasFocus"
    }

    private fun isBrowserApp(packageName: String): Boolean {
        return packageName in ALL_BROWSER_PKGS ||
                packageName.contains("chrome") || packageName.contains("browser") ||
                packageName.contains("edge") || packageName.contains("firefox") ||
                packageName.contains("brave") || packageName.contains("opera")
    }

    private fun multiLayerForceHome() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        Thread.sleep(100)
        val homeSuccess = performGlobalAction(GLOBAL_ACTION_HOME)
        if (!homeSuccess) {
            try {
                startActivity(Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            } catch (e: Exception) {}
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // OVERLAY / POPUP (উভয় service-এর design merge করা)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Unified block overlay — extrem_block-এর clean design ব্যবহার করি
     * কারণ সেটা আরো polished (feature-specific icon, quote, countdown)
     */
    private fun showBlockOverlay(featureTitle: String, reason: String) {
        if (isPopupVisible) return
        Handler(Looper.getMainLooper()).post {
            if (isPopupVisible) return@post
            isPopupVisible = true

            val ctx = this
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm
            val dp = resources.displayMetrics.density
            val sh = resources.displayMetrics.heightPixels

            val tealColor = android.graphics.Color.parseColor("#00BFA5")
            val teal700 = android.graphics.Color.parseColor("#00897B")
            val teal50 = android.graphics.Color.parseColor("#E0F2F1")
            val tealFaded = android.graphics.Color.parseColor("#3300BFA5")
            val textMid = android.graphics.Color.parseColor("#4A6080")
            val whiteFull = android.graphics.Color.WHITE

            val isAdult = featureTitle.contains("Adult", true) || featureTitle.contains("ASTAGFIRULLAH", true)
            val featureIcon = when {
                isAdult -> "🚫"
                featureTitle.contains("Shorts", true) -> "📱"
                featureTitle.contains("Reels", true) || featureTitle.contains("Reel", true) -> "🎬"
                featureTitle.contains("TikTok", true) -> "🎵"
                featureTitle.contains("Video", true) -> "▶"
                featureTitle.contains("Search", true) -> "🔍"
                featureTitle.contains("PANIC", true) -> "🚨"
                featureTitle.contains("DENIED", true) || featureTitle.contains("STRICT", true) -> "🔒"
                else -> "🛡"
            }

            val outerWrap = android.widget.FrameLayout(ctx).apply { setBackgroundColor(whiteFull) }

            val headerGrad = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(teal700, tealColor)
            )
            val headerView = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = headerGrad
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT, (sh * 0.38f).toInt()
                ).apply { gravity = Gravity.TOP }
            }

            val badgeRow = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins((20 * dp).toInt(), (48 * dp).toInt(), (20 * dp).toInt(), 0) }
            }
            val badgeBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#33FFFFFF")); cornerRadius = 100f * dp
            }
            val appBadge = android.widget.TextView(ctx).apply {
                text = "🛡  RasFocus+"
                textSize = 10f; setTextColor(whiteFull)
                typeface = android.graphics.Typeface.DEFAULT_BOLD; letterSpacing = 0.08f
                background = badgeBg
                val hp = (12 * dp).toInt(); val vp = (5 * dp).toInt(); setPadding(hp, vp, hp, vp)
            }
            val spacerBadge = android.widget.Space(ctx).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(0, 1, 1f)
            }
            val dismissBtn = android.widget.TextView(ctx).apply {
                textSize = 15f; typeface = android.graphics.Typeface.DEFAULT_BOLD
                val p = (10 * dp).toInt(); setPadding(p, p, p, p)
                if (isAdult) {
                    isEnabled = false; text = "⏳ 5s"
                    setTextColor(android.graphics.Color.parseColor("#AAFFFFFF"))
                    var timeLeft = 5
                    val timerRunnable = object : Runnable {
                        override fun run() {
                            timeLeft--
                            if (timeLeft > 0) {
                                text = "⏳ ${timeLeft}s"; Handler(Looper.getMainLooper()).postDelayed(this, 1000)
                            } else {
                                isEnabled = true; text = "✕ Close"
                                setTextColor(whiteFull)
                                setOnClickListener { removeUnifiedOverlay() }
                            }
                        }
                    }
                    Handler(Looper.getMainLooper()).postDelayed(timerRunnable, 1000)
                } else {
                    text = "✕"; setTextColor(android.graphics.Color.parseColor("#CCFFFFFF"))
                    setOnClickListener { removeUnifiedOverlay() }
                }
            }
            badgeRow.addView(appBadge); badgeRow.addView(spacerBadge); badgeRow.addView(dismissBtn)
            headerView.addView(badgeRow)

            val iconCircleBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#33FFFFFF"))
            }
            val iconCircle = android.widget.TextView(ctx).apply {
                text = featureIcon; textSize = 36f; gravity = Gravity.CENTER; background = iconCircleBg
                val s = (90 * dp).toInt()
                layoutParams = android.widget.LinearLayout.LayoutParams(s, s).apply {
                    setMargins(0, (20 * dp).toInt(), 0, (16 * dp).toInt())
                }
            }
            val blockedLabel = android.widget.TextView(ctx).apply {
                text = "BLOCKED"; textSize = 11f
                setTextColor(android.graphics.Color.parseColor("#CCFFFFFF"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD; letterSpacing = 0.3f; gravity = Gravity.CENTER
            }
            val featureTitleView = android.widget.TextView(ctx).apply {
                text = featureTitle; textSize = 22f; setTextColor(whiteFull)
                typeface = android.graphics.Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins((24 * dp).toInt(), (4 * dp).toInt(), (24 * dp).toInt(), (20 * dp).toInt()) }
            }
            headerView.addView(iconCircle); headerView.addView(blockedLabel); headerView.addView(featureTitleView)

            val bodyScroll = android.widget.ScrollView(ctx).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                ).apply { topMargin = (sh * 0.38f).toInt() }
            }
            val body = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                val p = (24 * dp).toInt(); setPadding(p, (20 * dp).toInt(), p, (32 * dp).toInt())
            }

            val reasonChipBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(teal50); cornerRadius = 12f * dp; setStroke((1f * dp).toInt(), tealFaded)
            }
            val reasonRow = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = reasonChipBg
                val p = (14 * dp).toInt(); val vp = (12 * dp).toInt(); setPadding(p, vp, p, vp)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val reasonIcon = android.widget.TextView(ctx).apply {
                text = "ℹ"; textSize = 16f; setTextColor(tealColor)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, (10 * dp).toInt(), 0) }
            }
            val reasonView = android.widget.TextView(ctx).apply {
                text = reason; textSize = 13f; setTextColor(textMid); setLineSpacing(0f, 1.4f)
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            reasonRow.addView(reasonIcon); reasonRow.addView(reasonView); body.addView(reasonRow)

            val divLine = android.view.View(ctx).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#E8EFF5"))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (1f * dp).toInt()
                ).apply { setMargins(0, (20 * dp).toInt(), 0, (16 * dp).toInt()) }
            }
            body.addView(divLine)

            // Quote
            val quoteMsg = if (isAdult) getReligiousQuote() else getMotivationalQuote()
            val quoteSectionLabel = android.widget.TextView(ctx).apply {
                text = "💭  অনুপ্রেরণা"; textSize = 11f; setTextColor(tealColor)
                typeface = android.graphics.Typeface.DEFAULT_BOLD; letterSpacing = 0.1f
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, (4 * dp).toInt(), 0, (10 * dp).toInt()) }
            }
            body.addView(quoteSectionLabel)

            val singleQuoteBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#F0FAFA"))
                cornerRadius = 14f * dp; setStroke((1f * dp).toInt(), android.graphics.Color.parseColor("#CCE8E5"))
            }
            val singleRow = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL; background = singleQuoteBg
                val p = (14 * dp).toInt(); setPadding(p, p, p, p)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val bar2 = android.view.View(ctx).apply {
                val bg = android.graphics.drawable.GradientDrawable().apply { setColor(tealColor); cornerRadius = 3f * dp }
                background = bg
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    (3f * dp).toInt(), android.widget.LinearLayout.LayoutParams.MATCH_PARENT
                ).apply { setMargins(0, 0, (12 * dp).toInt(), 0) }
            }
            val quoteCol = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val quoteText2 = android.widget.TextView(ctx).apply {
                text = "\u201c$quoteMsg\u201d"; textSize = 13f; setTextColor(textMid)
                setTypeface(typeface, android.graphics.Typeface.ITALIC); setLineSpacing(0f, 1.4f)
            }
            val authorText = android.widget.TextView(ctx).apply {
                text = "— RasFocus+"; textSize = 11f; setTextColor(tealColor)
                typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, (5 * dp).toInt(), 0, 0)
            }
            quoteCol.addView(quoteText2); quoteCol.addView(authorText)
            singleRow.addView(bar2); singleRow.addView(quoteCol); body.addView(singleRow)

            // Progress bar
            val progressBg2 = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#E0F2F1")); cornerRadius = 6f * dp
            }
            val progressTrack = android.widget.FrameLayout(ctx).apply {
                background = progressBg2
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (6f * dp).toInt()
                ).apply { setMargins(0, (24 * dp).toInt(), 0, 0) }
            }
            val progressFill = android.view.View(ctx).apply {
                val fillBg = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(tealColor, teal700)
                ).apply { cornerRadius = 6f * dp }
                background = fillBg
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT)
            }
            progressTrack.addView(progressFill); body.addView(progressTrack)

            // Home button
            val homeBtn = android.widget.TextView(ctx).apply {
                text = "🏠  হোমে ফিরুন"; textSize = 14f; setTextColor(whiteFull)
                typeface = android.graphics.Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                val btnBg = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(tealColor, teal700)
                ).apply { cornerRadius = 14f * dp }
                background = btnBg
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (52 * dp).toInt()
                ).apply { setMargins(0, (20 * dp).toInt(), 0, 0) }
                val p = (16 * dp).toInt(); setPadding(p, p, p, p)
                setOnClickListener { removeUnifiedOverlay(); performGlobalAction(GLOBAL_ACTION_HOME) }
            }
            body.addView(homeBtn)
            bodyScroll.addView(body); outerWrap.addView(headerView); outerWrap.addView(bodyScroll)

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.OPAQUE
            ).apply { gravity = Gravity.TOP or Gravity.START }

            fullScreenBlockView = outerWrap
            try {
                wm.addView(fullScreenBlockView, params)
                overlayView = outerWrap

                val totalMs = if (isAdult) 5000L else 4000L
                val updateMs = 50L; var elapsed = 0L
                val progressRunnable = object : Runnable {
                    override fun run() {
                        elapsed += updateMs
                        val fraction = 1f - (elapsed.toFloat() / totalMs)
                        progressFill.scaleX = fraction.coerceIn(0f, 1f); progressFill.pivotX = 0f
                        if (elapsed < totalMs) Handler(Looper.getMainLooper()).postDelayed(this, updateMs)
                    }
                }
                Handler(Looper.getMainLooper()).postDelayed(progressRunnable, updateMs)
                if (!isAdult) Handler(Looper.getMainLooper()).postDelayed({ removeUnifiedOverlay() }, totalMs)
            } catch (e: Exception) { isPopupVisible = false }
        }
    }

    private fun removeUnifiedOverlay() {
        Handler(Looper.getMainLooper()).post {
            fullScreenBlockView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
            fullScreenBlockView = null; overlayView = null; isPopupVisible = false
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // DEEP STUDY SESSION (AdultBlockFull.kt থেকে)
    // ══════════════════════════════════════════════════════════════════

    fun startDeepStudySession(focusMinutes: Int, playSound: Boolean, soundType: Int = 0) {
        resumeDeepStudySession(focusMinutes * 60 * 1000L, playSound, soundType)
    }

    private fun resumeDeepStudySession(timeMillis: Long, playSound: Boolean, soundType: Int) {
        isDeepStudyActive = true; isDeepStudyBreak = false
        recoveryPrefs.edit()
            .putBoolean("isTimerActive", true)
            .putLong("targetEndTime", System.currentTimeMillis() + timeMillis)
            .putInt("sessionType", 0).putBoolean("playSound", playSound).putInt("soundType", soundType).apply()
        if (playSound) playAmbientSound(soundType)
        showFloatingTimer()
        dsTimer?.cancel()
        dsTimer = object : android.os.CountDownTimer(timeMillis, 30) {
            override fun onTick(ms: Long) {
                dsTimeLeftMillis = ms; updateFloatingTimerText(ms)
                if (ms in 59000..60030) showBlockOverlay("KEEP GOING!", "⏳ Just 1 Minute Remaining!")
            }
            override fun onFinish() {
                stopAmbientSound(); removeFloatingTimer()
                isDeepStudyActive = false; DataManager.isDeepStudyStrict = false
                recoveryPrefs.edit().clear().apply()
                sendBroadcast(Intent("POMODORO_SESSION_UPDATE"))
                updateNotification("Protection is Active", "Monitoring your focus...")
                showSessionCompletePopup()
            }
        }.start()
    }

    private fun startDeepStudyBreak(breakMinutes: Int) {
        isDeepStudyBreak = true; val timeMillis = breakMinutes * 60 * 1000L
        showBreakScreenOverlay()
        recoveryPrefs.edit().putBoolean("isTimerActive", true)
            .putLong("targetEndTime", System.currentTimeMillis() + timeMillis).putInt("sessionType", 1).apply()
        dsTimer?.cancel()
        dsTimer = object : android.os.CountDownTimer(timeMillis, 1000) {
            override fun onTick(ms: Long) { updateNotification("Break Time!", "Enjoy your break. ${ms / 60000} mins left.") }
            override fun onFinish() {
                removeBreakScreenOverlay(); isDeepStudyActive = false; DataManager.isDeepStudyStrict = false
                recoveryPrefs.edit().clear().apply()
                updateNotification("Protection is Active", "Monitoring your focus...")
                showBlockOverlay("TIME'S UP!", "🎉 Break Completed! Ready to focus?")
                sendBroadcast(Intent("POMODORO_SESSION_UPDATE"))
            }
        }.start()
    }

    private fun checkDeepStudyBlocking(packageName: String, url: String) {
        if (isSystemApp(packageName)) return
        val allowedApps = DataManager.dsAllowAppList
        val allowedWebs = DataManager.dsAllowWebList
        val isAppAllowed = allowedApps.any { packageName.contains(it, ignoreCase = true) }
        val isWebAllowed = url.isNotEmpty() && allowedWebs.any { url.contains(it.substringBefore("."), ignoreCase = true) }
        val pauseDuringBreak = isDeepStudyBreak && !DataManager.dsKeepBlockingInBreak
        if (!isAppAllowed && !isWebAllowed && !pauseDuringBreak) {
            if (System.currentTimeMillis() - lastBlockTime < 5000) return
            lastBlockTime = System.currentTimeMillis()
            multiLayerForceHome()
            showBlockOverlay("STAY FOCUSED!", getMotivationalQuote())
        }
    }

    private fun startPeriodicPopupChecker() {
        periodicRunnable = object : Runnable {
            override fun run() {
                if (DataManager.isPeriodicPopupsActive && (DataManager.isAdultFocusActive || DataManager.is24HourLockActive)) {
                    showBlockOverlay("REMINDER", getMotivationalQuote())
                }
                periodicHandler.postDelayed(this, 25 * 60 * 1000L)
            }
        }
        periodicHandler.postDelayed(periodicRunnable!!, 25 * 60 * 1000L)
    }

    private fun stopPeriodicPopupChecker() {
        periodicRunnable?.let { periodicHandler.removeCallbacks(it) }
    }

    // ══════════════════════════════════════════════════════════════════
    // NOTIFICATION
    // ══════════════════════════════════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "RasFocus Unified Protection", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        // ── Main tap → RasFocus MainActivity ──────────────────────────────────
        val mainIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, Class.forName("com.rasel.RasFocus.MainActivity")),
            PendingIntent.FLAG_IMMUTABLE
        )

        // ── "🌐 RasBrowser খুলুন" action button ───────────────────────────────
        val browserIntent = PendingIntent.getActivity(
            this, 10,
            Intent(this, Class.forName(
                "com.rasel.RasFocus.selfcontrol.familybrowser.FamilyBrowserActivity"
            )).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                action = Intent.ACTION_MAIN
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_secure)
            .setContentIntent(mainIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Notification action centre এ RasBrowser shortcut
            .addAction(
                android.R.drawable.ic_menu_view,
                "🌐 RasBrowser",
                browserIntent
            )
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    // ══════════════════════════════════════════════════════════════════
    // FLOATING TIMER, BREAK SCREEN, SESSION COMPLETE (Deep Study)
    // ══════════════════════════════════════════════════════════════════

    private fun showFloatingTimer() {
        Handler(Looper.getMainLooper()).post {
            if (floatingTimerView != null) return@post
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START; x = 100; y = 200 }
            val dp = resources.displayMetrics.density
            val layout = android.widget.LinearLayout(this).apply {
                setPadding((40 * dp).toInt(), (20 * dp).toInt(), (40 * dp).toInt(), (20 * dp).toInt())
                val shape = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 30f; setColor(android.graphics.Color.parseColor("#0CA8B0"))
                }
                background = shape
                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> { initialX = params.x; initialY = params.y; initialTouchX = event.rawX; initialTouchY = event.rawY; true }
                        MotionEvent.ACTION_MOVE -> { params.x = initialX + (event.rawX - initialTouchX).toInt(); params.y = initialY + (event.rawY - initialTouchY).toInt(); wm.updateViewLayout(this, params); true }
                        else -> false
                    }
                }
            }
            timerTextView = android.widget.TextView(this).apply {
                setTextColor(android.graphics.Color.WHITE); textSize = 22f
                typeface = android.graphics.Typeface.DEFAULT_BOLD; text = "00:00:00"
            }
            layout.addView(timerTextView); floatingTimerView = layout
            try { wm.addView(floatingTimerView, params) } catch (e: Exception) {}
        }
    }

    private fun updateFloatingTimerText(millis: Long) {
        Handler(Looper.getMainLooper()).post {
            val mins = (millis / 1000) / 60; val secs = (millis / 1000) % 60; val ms = (millis % 1000) / 10
            timerTextView?.text = String.format("%02d:%02d:%02d", mins, secs, ms)
            updateNotification("Deep Study Active", "Time remaining: ${String.format("%02d:%02d", mins, secs)}")
        }
    }

    private fun removeFloatingTimer() {
        Handler(Looper.getMainLooper()).post {
            floatingTimerView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
            floatingTimerView = null
        }
    }

    private fun showBreakScreenOverlay() {
        Handler(Looper.getMainLooper()).post {
            if (breakScreenView != null) return@post
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            val dp = resources.displayMetrics.density
            val layout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL; gravity = Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(android.graphics.Color.parseColor("#4A00E0"), android.graphics.Color.parseColor("#8E2DE2"))
                )
            }
            val t = android.widget.TextView(this).apply {
                text = "TAKE A BREAK!"; textSize = 45f; setTextColor(android.graphics.Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, (30 * dp).toInt()); gravity = Gravity.CENTER
            }
            val s = android.widget.TextView(this).apply {
                text = "Breathe deep, rest your eyes, and relax your mind."; textSize = 18f
                setTextColor(android.graphics.Color.parseColor("#E2E8F0")); gravity = Gravity.CENTER
            }
            layout.addView(t); layout.addView(s); breakScreenView = layout
            try { wm.addView(breakScreenView, params) } catch (e: Exception) {}
        }
    }

    private fun removeBreakScreenOverlay() {
        Handler(Looper.getMainLooper()).post {
            breakScreenView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
            breakScreenView = null
        }
    }

    private fun showSessionCompletePopup() {
        Handler(Looper.getMainLooper()).post {
            if (sessionCompleteView != null) return@post
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager; windowManager = wm
            val dp = resources.displayMetrics.density
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )
            val layout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setBackgroundColor(android.graphics.Color.parseColor("#E6000000")); isClickable = true; isFocusable = true
            }
            val card = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding((60 * dp).toInt(), (80 * dp).toInt(), (60 * dp).toInt(), (80 * dp).toInt())
                val shape = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 40f; setColor(android.graphics.Color.WHITE) }
                background = shape
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins((80 * dp).toInt(), 0, (80 * dp).toInt(), 0) }
            }
            val title = android.widget.TextView(this).apply {
                text = "SESSION COMPLETED! 🎉"; textSize = 22f
                setTextColor(android.graphics.Color.parseColor("#0CA8B0"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, 0, 0, (60 * dp).toInt())
            }
            fun makeBtn(label: String, colorHex: String, onClick: () -> Unit): android.widget.Button {
                return android.widget.Button(this).apply {
                    text = label; setTextColor(android.graphics.Color.WHITE)
                    val s = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 24f; setColor(android.graphics.Color.parseColor(colorHex)) }
                    background = s
                    layoutParams = android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, (140 * dp).toInt()).apply { setMargins(0, 0, 0, (30 * dp).toInt()) }
                    setOnTouchListener { _, event -> if (event.action == MotionEvent.ACTION_UP) onClick(); true }
                }
            }
            val btnRest = makeBtn("Take a Rest (${DataManager.dsRestMin}m)", "#10B981") {
                removeSessionCompletePopup(); startDeepStudyBreak(DataManager.dsRestMin)
            }
            val btnStart = makeBtn("Start Again (${DataManager.dsFocusMin}m)", "#0CA8B0") {
                removeSessionCompletePopup(); startDeepStudySession(DataManager.dsFocusMin,
                    recoveryPrefs.getBoolean("playSound", false), recoveryPrefs.getInt("soundType", 0))
            }
            val btnClose = makeBtn("Close & Reset", "#E74C3C") { removeSessionCompletePopup() }
            card.addView(title); card.addView(btnRest); card.addView(btnStart); card.addView(btnClose)
            layout.addView(card); sessionCompleteView = layout
            try { wm.addView(sessionCompleteView, params) } catch (e: Exception) {}
        }
    }

    private fun removeSessionCompletePopup() {
        Handler(Looper.getMainLooper()).post {
            sessionCompleteView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
            sessionCompleteView = null
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // AMBIENT SOUND
    // ══════════════════════════════════════════════════════════════════

    private fun playAmbientSound(soundType: Int) {
        if (isPlayingNoise) return; isPlayingNoise = true
        val sampleRate = 44100
        val bufferSize = android.media.AudioTrack.getMinBufferSize(sampleRate, android.media.AudioFormat.CHANNEL_OUT_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
        audioTrack = android.media.AudioTrack(android.media.AudioManager.STREAM_MUSIC, sampleRate, android.media.AudioFormat.CHANNEL_OUT_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, bufferSize, android.media.AudioTrack.MODE_STREAM)
        audioTrack?.play()
        noiseThread = Thread {
            val buffer = ShortArray(bufferSize); val random = java.util.Random(); var lastOut = 0.0
            while (isPlayingNoise) {
                for (i in buffer.indices) {
                    val white = (random.nextDouble() * 2 - 1)
                    val output = when (soundType) {
                        1 -> { lastOut = (lastOut + 0.02 * white) / 1.02; lastOut * 3.5 }
                        2 -> { lastOut = (lastOut + 0.01 * white) / 1.01; lastOut * 4.5 }
                        else -> white * 0.1
                    }
                    buffer[i] = (output.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                }
                audioTrack?.write(buffer, 0, buffer.size)
            }
        }; noiseThread?.start()
    }

    private fun stopAmbientSound() {
        isPlayingNoise = false
        try { noiseThread?.join(500) } catch (e: Exception) {}
        audioTrack?.let { if (it.playState == android.media.AudioTrack.PLAYSTATE_PLAYING) it.stop(); it.release() }
        audioTrack = null
    }

    // ── tryStopFocus (public API) ──────────────────────────────────────
    fun tryStopFocus(input: String): Boolean {
        if (DataManager.is24HourLockActive) return false
        if (DataManager.controlMode == 1) {
            val prefs = getSharedPreferences("RasFocusData", Context.MODE_PRIVATE)
            val storedHash = prefs.getString("friendPasswordHash", null)
            val isCorrect = if (storedHash != null) DataManager.verifyPassword(input, storedHash)
            else (prefs.getString("friendPassword", "1234") ?: "1234") == input
            if (isCorrect) { DataManager.isAdultFocusActive = false; return true }
            return false
        }
        if (DataManager.controlMode == 2) {
            val savedLongText = adultPrefs.getString("savedLongText", "") ?: ""
            if (input.isNotEmpty() && input.trim() == savedLongText.trim()) {
                DataManager.isAdultFocusActive = false; return true
            }
            return false
        }
        DataManager.isAdultFocusActive = false; return true
    }
}
