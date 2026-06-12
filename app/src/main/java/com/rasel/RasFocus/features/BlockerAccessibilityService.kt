package com.rasel.RasFocus.features

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.rasel.RasFocus.DataManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

class BlockerAccessibilityService : AccessibilityService() {

    companion object {
        var instance: BlockerAccessibilityService? = null
        private const val NOTIFICATION_CHANNEL_ID = "RasFocus_Channel"
        private const val NOTIFICATION_ID = 1001
    }

    // ── Adult Block: Keyword & Site Lists ──────────────────────────────────
    private val hardcoreKeywords = listOf(
        "porn", "xxx", "sex", "nude", "nsfw", "sexy", "hentai", "rule34", "milf",
        "blowjob", "tits", "boobs", "pussy", "dick", "cock", "escort", "bdsm",
        "fetish", "erotica", "dildo", "webcam", "camgirls", "xvideos", "pornhub",
        "xnxx", "xhamster", "brazzers", "onlyfans", "playboy", "chaturbate",
        "stripchat", "eporner", "spankbang", "redtube", "youporn", "mia khalifa",
        "sunny leone", "dani daniels", "johnny sins", "kendra lust",
        "চটি", "পর্ণ", "সেক্স", "নগ্ন", "উলঙ্গ", "বেশ্যা", "মাগি", "খানকি",
        "যৌন", "পর্ণগ্রাফি", "রেন্ডি", "চোদাচুতি", "গরম ভিডিও", "খারাপ ছবি",
        "যৌন মিলন", "যৌনাঙ্গ", "চুদো", "নগ্নতা"
    )

    private val romanticKeywords = listOf(
        "hot dance", "seductive dance", "item song", "belly dance", "hot",
        "kissing scene", "bikini", "swimsuit", "sexy dance", "cleavage", "hot scene",
        "romantic kiss", "bedroom scene", "bath scene", "rain dance", "bold scene",
        "semi nude", "lingerie", "erotic", "hot song", "romantic video hot",
        "navel show", "deep neck", "short dress sexy", "unfaithful scene"
    )

    private val adultWebsites = listOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com",
        "youporn.com", "brazzers.com", "spankbang.com", "eporner.com", "chaturbate.com"
    )

    // ── assets/adultsite.txt থেকে load হওয়া dynamic list ─────────────────
    private var dynamicAdultList = listOf<String>()

    private val muslimQuotesBn = listOf(
        "মুমিনদের বলুন, তারা যেন তাদের দৃষ্টি নত রাখে...",
        "লজ্জাশীলতা ঈমানের অঙ্গ।"
    )
    private val muslimQuotesEn = listOf(
        "Tell the believing men to reduce their vision...",
        "Modesty is a branch of faith."
    )
    private val hinduQuotesBn = listOf(
        "যে মনকে নিয়ন্ত্রণ করতে পারে, তার মন তার সবচেয়ে বড় বন্ধু।",
        "কাম, ক্রোধ এবং লোভ—এই তিনটি নরকের দ্বার।"
    )
    private val hinduQuotesEn = listOf(
        "For him who has conquered the mind, the mind is the best of friends.",
        "Lust, anger, and greed are the three doors to hell."
    )
    private val christianQuotesBn = listOf(
        "খারাপ সাহচর্য ভালো চরিত্র নষ্ট করে।",
        "অহংকার পতনের মূল।"
    )
    private val christianQuotesEn = listOf(
        "Bad company ruins good morals.",
        "Pride goes before destruction."
    )
    private val motivationalQuotesBn = listOf(
        "সময়ের মূল্য বোঝো, জীবন তোমার মূল্য বুঝবে।",
        "সফলতা আসে ফোকাস থেকে, ডিস্ট্রাকশন থেকে নয়।",
        "আজকের সময় নষ্ট মানে, কালকের স্বপ্ন নষ্ট।",
        "যে নিজের মনকে নিয়ন্ত্রণ করতে পারে, সে পৃথিবী জয় করতে পারে।"
    )
    private val motivationalQuotesEn = listOf(
        "Understand the value of time, life will understand your value.",
        "Success comes from focus, not from distraction.",
        "Wasting time today means ruining tomorrow's dreams.",
        "He who can control his mind can conquer the world."
    )

    private var lastBlockTime: Long = 0
    private var isDeepStudyActive = false
    private var isDeepStudyBreak = false

    // FIX: Pre-compiled Regex for O(n) multi-pattern matching instead of O(k×n) repeated
    // String.contains() calls on every accessibility event. Built once in onCreate().
    private lateinit var hardcoreRegex: Regex
    private lateinit var romanticRegex: Regex
    private lateinit var dynamicSiteRegex: Regex

    // FIX: AtomicBoolean prevents race condition between accessibility thread and main thread
    private val isPopupVisible = AtomicBoolean(false)

    private var windowManager: android.view.WindowManager? = null
    private var dsTimer: android.os.CountDownTimer? = null
    private var dsTimeLeftMillis: Long = 0
    private var floatingTimerView: android.view.View? = null
    private var timerTextView: android.widget.TextView? = null
    private var breakScreenView: android.view.View? = null
    private var sessionCompleteView: android.view.View? = null
    private var fullScreenBlockView: android.view.View? = null

    private var audioTrack: android.media.AudioTrack? = null
    private var isPlayingNoise = false
    private var noiseThread: Thread? = null

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    private lateinit var recoveryPrefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        DataManager.init(this)
        recoveryPrefs = getSharedPreferences("FocusRecovery", Context.MODE_PRIVATE)
        createNotificationChannel()
        loadAdultSiteFile()
        // FIX: Compile all keyword lists into single Regex patterns once at startup.
        // Avoids scanning 50+ patterns individually on every accessibility event.
        buildKeywordRegexes()
    }

    private fun buildKeywordRegexes() {
        hardcoreRegex = Regex(
            hardcoreKeywords.map { Regex.escape(it) }.joinToString("|"),
            RegexOption.IGNORE_CASE
        )
        romanticRegex = Regex(
            romanticKeywords.map { Regex.escape(it) }.joinToString("|"),
            RegexOption.IGNORE_CASE
        )
        rebuildDynamicRegex()
    }

    private fun rebuildDynamicRegex() {
        dynamicSiteRegex = if (dynamicAdultList.isEmpty()) Regex("(?!)")
        else Regex(
            dynamicAdultList.filter { it.length > 3 }.map { Regex.escape(it) }.joinToString("|"),
            RegexOption.IGNORE_CASE
        )
    }

    // ── assets/adultsite.txt লোড করা ──────────────────────────────────────
    private fun loadAdultSiteFile() {
        try {
            val inputStream = assets.open("adultsite.txt")
            val text = inputStream.bufferedReader().use { it.readText() }
            dynamicAdultList = text.split("\n", "\r\n")
                .map { it.trim().lowercase().replace("*.", ".") }
                .filter { it.isNotEmpty() }
            android.util.Log.d("RasFocus", "Loaded ${dynamicAdultList.size} adult sites from file")
            rebuildDynamicRegex()
        } catch (e: Exception) {
            android.util.Log.e("RasFocus", "Error reading adultsite.txt: ${e.message}")
        }
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
            notificationTimeout = 100
        }
        this.serviceInfo = info

        startForeground(NOTIFICATION_ID, buildNotification("Protection is Active", "Monitoring your focus..."))

        val isSavedActive = recoveryPrefs.getBoolean("isTimerActive", false)
        val targetEndTime = recoveryPrefs.getLong("targetEndTime", 0L)
        val sessionType = recoveryPrefs.getInt("sessionType", 0)
        val soundType = recoveryPrefs.getInt("soundType", 0)
        val playSound = recoveryPrefs.getBoolean("playSound", false)

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
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopAmbientSound()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "RasFocus Protection", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps the app running in background to ensure focus protection." }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, Class.forName("com.rasel.RasFocus.MainActivity")),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title).setContentText(content)
            .setSmallIcon(android.R.drawable.ic_secure)
            .setContentIntent(pendingIntent).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    private fun updateNotification(title: String, content: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    private fun isSystemApp(packageName: String): Boolean {
        return packageName.contains("launcher") || packageName.contains("systemui") ||
                packageName.contains("dialer") || packageName.contains("telecom") ||
                packageName.contains("messaging") || packageName.contains("mms") ||
                packageName.contains("contacts") || packageName.contains("inputmethod") ||
                packageName.contains("keyboard") || packageName == "com.rasel.RasFocus"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || (!DataManager.isFocusActive && !DataManager.isAdultFocusActive && !isDeepStudyActive)) return

        val packageName = event.packageName?.toString() ?: return

        // ── Typed keyword realtime block ─────────────────────────────────
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val prefs = getSharedPreferences("RasFocusAdultPrefs", Context.MODE_PRIVATE)
            val isKeyboardBlockEnabled = prefs.getBoolean("keyboardTypingBlock", false)

            if (!isSystemApp(packageName) && isKeyboardBlockEnabled) {
                val source = event.source ?: return
                // event.text এ পুরো field-এর text থাকে, সেটাই check করি
                val typedText = source.text?.toString()?.lowercase()
                    ?: event.text.joinToString(" ").lowercase()

                if (typedText.isBlank()) return

                // FIX: Single regex pass instead of scanning each keyword list individually
                val customKeywords = DataManager.userCustomAdultKeywords
                val customRegex = if (customKeywords.isEmpty()) null
                    else Regex(customKeywords.map { Regex.escape(it) }.joinToString("|"), RegexOption.IGNORE_CASE)

                val isHardcoreMatch = hardcoreRegex.containsMatchIn(typedText)
                val isRomanticMatch = !isHardcoreMatch && romanticRegex.containsMatchIn(typedText)
                val isDynamicMatch  = !isHardcoreMatch && !isRomanticMatch && dynamicSiteRegex.containsMatchIn(typedText)
                val isCustomMatch   = customRegex != null && customRegex.containsMatchIn(typedText)

                if (isHardcoreMatch || isRomanticMatch || isDynamicMatch || isCustomMatch) {
                    // ── Text field clear করো ─────────────────────────────
                    try {
                        // METHOD 1: ACTION_SET_TEXT দিয়ে empty set (fastest)
                        val clearArgs = android.os.Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, ""
                            )
                        }
                        val cleared = source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)

                        if (!cleared) {
                            // METHOD 2: Select all then cut (fallback)
                            val selectArgs = android.os.Bundle().apply {
                                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, typedText.length)
                            }
                            source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectArgs)
                            source.performAction(AccessibilityNodeInfo.ACTION_CUT)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("RasFocus", "Keyboard clear error: ${e.message}")
                    }

                    val reason = when {
                        isHardcoreMatch -> "Explicit Keyword Typed"
                        isRomanticMatch -> "Romantic Keyword Typed"
                        isCustomMatch   -> "Custom Blocked Keyword Typed"
                        else            -> "Adult Site Keyword Typed"
                    }

                    // Adult focus active থাকলে তবেই full block popup দেখাবে
                    if (DataManager.isAdultFocusActive) {
                        triggerAdultBlockAction(packageName, reason)
                    }
                    return
                }
            }
        }

        // ── 24h lock sync ────────────────────────────────────────────────
        if (DataManager.is24HourLockActive) {
            if (System.currentTimeMillis() >= DataManager.lock24hEndTime) {
                DataManager.is24HourLockActive = false
                DataManager.isAdultFocusActive = false
            } else {
                DataManager.isAdultFocusActive = true
            }
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            val rootNode = rootInActiveWindow ?: return
            var currentUrl = ""

            if (packageName.contains("chrome") || packageName.contains("browser") ||
                packageName.contains("edge") || packageName.contains("firefox")
            ) {
                currentUrl = extractUrlFromBrowser(rootNode).lowercase()
            }

            val screenText = event.text.joinToString(" ").lowercase()

            if (isDeepStudyActive && DataManager.isDeepStudyStrict) {
                checkDeepStudyBlocking(packageName, currentUrl)
            } else {
                checkAndBlockContent(packageName, currentUrl, screenText)
            }
            rootNode.recycle()
        }
    }

    private fun extractUrlFromBrowser(nodeInfo: AccessibilityNodeInfo?): String {
        if (nodeInfo == null) return ""
        if (nodeInfo.className == "android.widget.EditText") {
            val id = nodeInfo.viewIdResourceName
            if (id != null && (id.contains("url_bar") || id.contains("address_bar"))) {
                return nodeInfo.text?.toString() ?: ""
            }
        }
        for (i in 0 until nodeInfo.childCount) {
            val childNode = nodeInfo.getChild(i)
            val url = extractUrlFromBrowser(childNode)
            childNode?.recycle()
            if (url.isNotEmpty()) return url
        }
        return ""
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
            val goBackSuccess = performGlobalAction(GLOBAL_ACTION_BACK)
            if (!goBackSuccess) performGlobalAction(GLOBAL_ACTION_HOME)
            showFullScreenBlockPopup("STAY FOCUSED!", getMotivationalQuote(), "Reason: App/Website is restricted during Deep Study.", "#4A00E0")
        }
    }

    private fun checkAndBlockContent(packageName: String, url: String, screenText: String) {
        var shouldBlockNormal = false
        var isAdultViolation = false
        var blockReason = ""

        if (DataManager.blockSettingsAndUninstall) {
            if (packageName.contains("com.android.settings") || packageName.contains("packageinstaller")) {
                shouldBlockNormal = true; blockReason = "Settings are blocked!"
            }
        }

        if (DataManager.isAdultFocusActive && !shouldBlockNormal) {
            when {
                // FIX: Use pre-compiled regex for performance
                dynamicSiteRegex.containsMatchIn(url) || dynamicSiteRegex.containsMatchIn(screenText) -> {
                    isAdultViolation = true; blockReason = "Restricted Website / Keyword"
                }
                adultWebsites.any { url.contains(it) || screenText.contains(it.substringBefore(".")) } -> {
                    isAdultViolation = true; blockReason = "Adult Website Detected"
                }
                hardcoreRegex.containsMatchIn(url) || hardcoreRegex.containsMatchIn(screenText) -> {
                    isAdultViolation = true; blockReason = "Explicit Keyword Detected"
                }
                romanticRegex.containsMatchIn(url) || romanticRegex.containsMatchIn(screenText) -> {
                    isAdultViolation = true; blockReason = "Softcore/Romantic Content Detected"
                }
                (packageName.contains("youtube") && screenText.contains("shorts")) || url.contains("shorts") -> {
                    shouldBlockNormal = true; blockReason = "YouTube Shorts are blocked!"
                }
                (packageName.contains("facebook") && screenText.contains("reels")) || url.contains("reel") -> {
                    shouldBlockNormal = true; blockReason = "Facebook Reels are blocked!"
                }
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
        } else if (shouldBlockNormal) {
            if (System.currentTimeMillis() - lastBlockTime < 5000) return
            lastBlockTime = System.currentTimeMillis()
            performGlobalAction(GLOBAL_ACTION_HOME)
            val mainMsg = if (DataManager.showQuotes) getReligiousQuote() else getMotivationalQuote()
            showFullScreenBlockPopup("ACCESS DENIED!", mainMsg, "Reason: $blockReason", "#0CA8B0")
        }
    }

    private fun triggerAdultBlockAction(packageName: String, reason: String) {
        if (System.currentTimeMillis() - lastBlockTime < 5000) return
        lastBlockTime = System.currentTimeMillis()

        val isBrowser = packageName.contains("chrome") || packageName.contains("browser") ||
                packageName.contains("edge") || packageName.contains("firefox")
        if (isBrowser) {
            // FIX: Thread.sleep() on the accessibility/main thread causes ANR risk.
            // Use Handler.postDelayed() for the second back action instead.
            performGlobalAction(GLOBAL_ACTION_BACK)
            Handler(Looper.getMainLooper()).postDelayed({
                performGlobalAction(GLOBAL_ACTION_BACK)
            }, 150)
        } else {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        DataManager.totalBlockedCount++
        DataManager.cleanStreakDays = 0

        showFullScreenBlockPopup("ASTAGFIRULLAH!", getReligiousQuote(), "Reason: $reason", "#F12B2C")
    }

    private fun getReligiousQuote(): String {
        val list = when (DataManager.adultReligion) {
            0 -> if (DataManager.adultLanguage == 0) muslimQuotesBn else muslimQuotesEn
            1 -> if (DataManager.adultLanguage == 0) hinduQuotesBn else hinduQuotesEn
            2 -> if (DataManager.adultLanguage == 0) christianQuotesBn else christianQuotesEn
            else -> if (DataManager.adultLanguage == 0) motivationalQuotesBn else motivationalQuotesEn
        }
        return list[Random.nextInt(list.size)]
    }

    private fun getMotivationalQuote(): String {
        val list = if (DataManager.adultLanguage == 0) motivationalQuotesBn else motivationalQuotesEn
        return list[Random.nextInt(list.size)]
    }

    // ── Public: Adult_block.kt password stop ─────────────────────────────
    fun tryStopFocus(inputPassword: String): Boolean {
        if (DataManager.is24HourLockActive) return false
        val prefs = getSharedPreferences("RasFocusData", Context.MODE_PRIVATE)
        // FIX: Compare against stored hash instead of plain-text password.
        val storedHash = prefs.getString("friendPasswordHash", null)
        return if (DataManager.controlMode == 1) {
            if (storedHash != null) {
                // New hash-based verification
                if (DataManager.verifyPassword(inputPassword, storedHash)) {
                    DataManager.isAdultFocusActive = false; true
                } else false
            } else {
                // Legacy fallback: plain-text (migrates on next password save)
                val legacyPw = prefs.getString("friendPassword", "1234") ?: "1234"
                if (inputPassword == legacyPw) { DataManager.isAdultFocusActive = false; true } else false
            }
        } else {
            DataManager.isAdultFocusActive = false; true
        }
    }

    // ── Deep Study Session ────────────────────────────────────────────────
    fun startDeepStudySession(focusMinutes: Int, playSound: Boolean, soundType: Int = 0) {
        resumeDeepStudySession(focusMinutes * 60 * 1000L, playSound, soundType)
    }

    fun stopDeepStudySession() {
        dsTimer?.cancel()
        dsTimer = null
        isDeepStudyActive = false
        isDeepStudyBreak = false
        DataManager.isDeepStudyStrict = false
        stopAmbientSound()
        removeFloatingTimer()
        recoveryPrefs.edit().clear().apply()
        sendBroadcast(Intent("POMODORO_SESSION_UPDATE"))
        updateNotification("Protection is Active", "Monitoring your focus...")
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
                dsTimeLeftMillis = ms
                updateFloatingTimerText(ms)
                if (ms in 59000..60030) showFullScreenBlockPopup("KEEP GOING!", "⏳ Just 1 Minute Remaining!", "Reason: Deep Study Session Alert", "#4A00E0")
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
        isDeepStudyBreak = true
        val timeMillis = breakMinutes * 60 * 1000L
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
                showFullScreenBlockPopup("TIME'S UP!", "🎉 Break Completed! Ready to focus?", "Reason: Deep Study Break Ended", "#0CA8B0")
                sendBroadcast(Intent("POMODORO_SESSION_UPDATE"))
            }
        }.start()
    }

    private fun showSessionCompletePopup() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (sessionCompleteView != null) return@post
            windowManager = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val params = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            val layout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER
                setBackgroundColor(android.graphics.Color.parseColor("#E6000000")); isClickable = true; isFocusable = true
            }
            val card = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER
                setPadding(60, 80, 60, 80)
                val shape = android.graphics.drawable.GradientDrawable(); shape.cornerRadius = 40f; shape.setColor(android.graphics.Color.WHITE); background = shape
                layoutParams = android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(80, 0, 80, 0) }
            }
            val title = android.widget.TextView(this).apply { text = "SESSION COMPLETED! 🎉"; textSize = 22f; setTextColor(android.graphics.Color.parseColor("#0CA8B0")); setTypeface(null, android.graphics.Typeface.BOLD); gravity = android.view.Gravity.CENTER; setPadding(0, 0, 0, 60) }
            val btnRest = makeBtn("Take a Rest (${DataManager.dsRestMin}m)", "#10B981") { removeSessionCompletePopup(); startDeepStudyBreak(DataManager.dsRestMin) }
            val btnStart = makeBtn("Start Again (${DataManager.dsFocusMin}m)", "#0CA8B0") { removeSessionCompletePopup(); startDeepStudySession(DataManager.dsFocusMin, recoveryPrefs.getBoolean("playSound", false), recoveryPrefs.getInt("soundType", 0)) }
            val btnClose = makeBtn("Close & Reset", "#E74C3C") { removeSessionCompletePopup() }
            card.addView(title); card.addView(btnRest); card.addView(btnStart); card.addView(btnClose)
            layout.addView(card); sessionCompleteView = layout
            try { windowManager?.addView(sessionCompleteView, params) } catch (e: Exception) { android.util.Log.e("RasFocus", "sessionCompletePopup: ${e.message}") }
        }
    }

    private fun makeBtn(label: String, colorHex: String, onClick: () -> Unit): android.widget.Button {
        return android.widget.Button(this).apply {
            text = label; setTextColor(android.graphics.Color.WHITE)
            val s = android.graphics.drawable.GradientDrawable(); s.cornerRadius = 24f; s.setColor(android.graphics.Color.parseColor(colorHex)); background = s
            layoutParams = android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 140).apply { setMargins(0, 0, 0, 30) }
            setOnTouchListener { _, event -> if (event.action == MotionEvent.ACTION_UP) onClick(); true }
        }
    }

    private fun removeSessionCompletePopup() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            sessionCompleteView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }; sessionCompleteView = null
        }
    }

    private fun playAmbientSound(soundType: Int) {
        if (isPlayingNoise) return; isPlayingNoise = true
        val sampleRate = 44100
        val bufferSize = android.media.AudioTrack.getMinBufferSize(sampleRate, android.media.AudioFormat.CHANNEL_OUT_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
        audioTrack = android.media.AudioTrack(android.media.AudioManager.STREAM_MUSIC, sampleRate, android.media.AudioFormat.CHANNEL_OUT_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, bufferSize, android.media.AudioTrack.MODE_STREAM)
        audioTrack?.play()
        noiseThread = Thread {
            val buffer = ShortArray(bufferSize); val random = java.util.Random(); var lastOut = 0.0; var phase = 0.0
            while (isPlayingNoise) {
                for (i in buffer.indices) {
                    val white = (random.nextDouble() * 2 - 1); var output = 0.0
                    when (soundType) {
                        0 -> output = white * 0.1
                        1 -> { lastOut = (lastOut + 0.02 * white) / 1.02; output = lastOut * 3.5 }
                        2 -> { lastOut = (lastOut + 0.01 * white) / 1.01; output = lastOut * 4.5 }
                        3 -> { lastOut = (lastOut + 0.04 * white) / 1.04; output = lastOut * 2.5 }
                        4 -> { lastOut = (lastOut + 0.02 * white) / 1.02; output = lastOut * 3.5 + (if (random.nextDouble() > 0.99) white * 0.3 else 0.0) }
                        5 -> { lastOut = (lastOut + 0.02 * white) / 1.02; output = lastOut * 2.0 + white * 0.05 }
                        6 -> { lastOut = (lastOut + 0.015 * white) / 1.015; phase += 0.0001; val mod = Math.sin(phase) * 0.5 + 0.5; output = lastOut * 3.0 * (0.4 + 0.6 * mod) }
                        7 -> { lastOut = (lastOut + 0.005 * white) / 1.005; output = lastOut * 6.0 }
                        8 -> { lastOut = (lastOut + 0.008 * white) / 1.008; phase += 0.0005; val drone = Math.sin(phase) * 0.15; output = lastOut * 4.0 + drone }
                        9 -> { lastOut = (lastOut + 0.01 * white) / 1.01; phase += 0.0002; val throb = Math.sin(phase) * 0.3; output = lastOut * 3.5 * (0.7 + throb) }
                        else -> { lastOut = (lastOut + 0.02 * white) / 1.02; output = lastOut * 3.5 }
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
        audioTrack?.let { if (it.playState == android.media.AudioTrack.PLAYSTATE_PLAYING) it.stop(); it.release() }; audioTrack = null
    }

    private fun showFloatingTimer() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (floatingTimerView != null) return@post
            windowManager = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val params = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.WRAP_CONTENT, android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.START; x = 100; y = 200 }
            val layout = android.widget.LinearLayout(this).apply {
                setPadding(40, 20, 40, 20)
                val shape = android.graphics.drawable.GradientDrawable(); shape.cornerRadius = 30f; shape.setColor(android.graphics.Color.parseColor("#0CA8B0")); background = shape
                setOnTouchListener { _, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> { initialX = params.x; initialY = params.y; initialTouchX = event.rawX; initialTouchY = event.rawY; true }
                        android.view.MotionEvent.ACTION_MOVE -> { params.x = initialX + (event.rawX - initialTouchX).toInt(); params.y = initialY + (event.rawY - initialTouchY).toInt(); windowManager?.updateViewLayout(this, params); true }
                        else -> false
                    }
                }
            }
            timerTextView = android.widget.TextView(this).apply { setTextColor(android.graphics.Color.WHITE); textSize = 22f; setTypeface(null, android.graphics.Typeface.BOLD); text = "00:00:00" }
            layout.addView(timerTextView); floatingTimerView = layout
            try { windowManager?.addView(floatingTimerView, params) } catch (e: Exception) { android.util.Log.e("RasFocus", "floatingTimer: ${e.message}") }
        }
    }

    private fun updateFloatingTimerText(millis: Long) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val mins = (millis / 1000) / 60; val secs = (millis / 1000) % 60; val ms = (millis % 1000) / 10
            timerTextView?.text = String.format("%02d:%02d:%02d", mins, secs, ms)
            updateNotification("Deep Study Active", "Time remaining: ${String.format("%02d:%02d", mins, secs)}")
        }
    }

    private fun removeFloatingTimer() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            floatingTimerView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }; floatingTimerView = null
        }
    }

    private fun showBreakScreenOverlay() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (breakScreenView != null) return@post
            windowManager = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val params = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            val layout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, intArrayOf(android.graphics.Color.parseColor("#4A00E0"), android.graphics.Color.parseColor("#8E2DE2")))
            }
            val t = android.widget.TextView(this).apply { text = "TAKE A BREAK!"; textSize = 45f; setTextColor(android.graphics.Color.WHITE); setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 0, 0, 30); gravity = android.view.Gravity.CENTER }
            val s = android.widget.TextView(this).apply { text = "Breathe deep, rest your eyes, and relax your mind."; textSize = 18f; setTextColor(android.graphics.Color.parseColor("#E2E8F0")); gravity = android.view.Gravity.CENTER }
            layout.addView(t); layout.addView(s); breakScreenView = layout
            try { windowManager?.addView(breakScreenView, params) } catch (e: Exception) { android.util.Log.e("RasFocus", "breakScreen: ${e.message}") }
        }
    }

    private fun removeBreakScreenOverlay() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            breakScreenView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }; breakScreenView = null
        }
    }

    private fun showFullScreenBlockPopup(title: String, message: String, reasonInfo: String, bgColorHex: String) {
        // FIX: compareAndSet(false, true) atomically checks-and-sets — thread-safe guard
        // against double-popup when accessibility events fire from multiple threads.
        if (!isPopupVisible.compareAndSet(false, true)) return
        android.os.Handler(android.os.Looper.getMainLooper()).post {

            val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            windowManager = wm
            val dp = resources.displayMetrics.density
            val ctx = this

            // ── Icon + color নির্ধারণ ──
            val isAdult   = bgColorHex == "#F12B2C"
            val isStudy   = bgColorHex == "#4A00E0"
            val accentHex = when { isAdult -> "#F12B2C"; isStudy -> "#7C3AED"; else -> "#0CA8B0" }
            val iconEmoji = when { isAdult -> "⚠️"; isStudy -> "📚"; else -> "🛡" }

            // ── Root container (পুরো background transparent) ──
            val root = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                background = null
                setPadding(0, 0, 0, 0)
            }

            // ── Card ──
            val card = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#EE0D1117"))
                    cornerRadius = 20f * dp
                    setStroke((1.5f * dp).toInt(), android.graphics.Color.parseColor("#2A3040"))
                }
                background = bg
                val pad = (16 * dp).toInt()
                setPadding(pad, pad, pad, pad)
            }

            // ── Top row: icon badge + app label + spacer + dismiss X ──
            val topRow = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val badge = android.widget.TextView(ctx).apply {
                text = iconEmoji; textSize = 18f
                setPadding(0, 0, (8 * dp).toInt(), 0)
            }
            val appLabel = android.widget.TextView(ctx).apply {
                text = "RasFocus+"; textSize = 11f
                setTextColor(android.graphics.Color.parseColor(accentHex))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
            }
            val spacer = android.widget.Space(ctx).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(0, 1, 1f)
            }
            val dismissBtn = android.widget.TextView(ctx).apply {
                text = "✕"; textSize = 13f
                setTextColor(android.graphics.Color.parseColor("#6B7280"))
                setOnClickListener { removeFullScreenBlockPopup() }
                val p = (6 * dp).toInt(); setPadding(p, p, p, p)
            }
            topRow.addView(badge); topRow.addView(appLabel)
            topRow.addView(spacer); topRow.addView(dismissBtn)

            // ── Divider ──
            val divider = android.view.View(ctx).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#1E222C"))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (1f * dp).toInt()
                ).apply { setMargins(0, (10 * dp).toInt(), 0, (10 * dp).toInt()) }
            }

            // ── Title ──
            val titleView = android.widget.TextView(ctx).apply {
                text = "⊘  $title"; textSize = 15f
                setTextColor(android.graphics.Color.parseColor("#EAEDF3"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            // ── Reason subtitle ──
            val reasonView = android.widget.TextView(ctx).apply {
                text = reasonInfo; textSize = 12f
                setTextColor(android.graphics.Color.parseColor("#9CA3AF"))
                setPadding(0, (5 * dp).toInt(), 0, 0)
                setLineSpacing(0f, 1.3f)
            }

            // ── Quote section ──
            val quoteDivider = android.view.View(ctx).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#1A2436"))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (1f * dp).toInt()
                ).apply { setMargins(0, (12 * dp).toInt(), 0, (10 * dp).toInt()) }
            }
            val quoteRow = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
            }
            val accentBar = android.view.View(ctx).apply {
                setBackgroundColor(android.graphics.Color.parseColor(accentHex))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    (3f * dp).toInt(), android.widget.LinearLayout.LayoutParams.MATCH_PARENT
                ).apply { setMargins(0, 0, (10 * dp).toInt(), 0) }
            }
            val quoteCol = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val quoteView = android.widget.TextView(ctx).apply {
                text = "\u201C$message\u201D"; textSize = 11.5f
                setTextColor(android.graphics.Color.parseColor("#CBD5E0"))
                setTypeface(typeface, android.graphics.Typeface.ITALIC)
                setLineSpacing(0f, 1.35f)
            }
            val authorView = android.widget.TextView(ctx).apply {
                text = "— RasFocus"; textSize = 10f
                setTextColor(android.graphics.Color.parseColor(accentHex))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, (4 * dp).toInt(), 0, 0)
            }
            quoteCol.addView(quoteView); quoteCol.addView(authorView)
            quoteRow.addView(accentBar); quoteRow.addView(quoteCol)

            // ── Bottom tag ──
            val tagRow = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, (12 * dp).toInt(), 0, 0)
            }
            val tagBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#0F1A13"))
                cornerRadius = 20f * dp
                setStroke((1f * dp).toInt(), android.graphics.Color.parseColor("#00F5C430"))
            }
            val tagText = android.widget.TextView(ctx).apply {
                text = "● Active — Blocked by RasFocus+"
                textSize = 10f
                setTextColor(android.graphics.Color.parseColor("#00F5C4"))
                letterSpacing = 0.05f; background = tagBg
                val hp = (10 * dp).toInt(); val vp = (4 * dp).toInt()
                setPadding(hp, vp, hp, vp)
            }
            tagRow.addView(tagText)

            // ── Card assemble ──
            card.addView(topRow); card.addView(divider)
            card.addView(titleView); card.addView(reasonView)
            card.addView(quoteDivider); card.addView(quoteRow)
            card.addView(tagRow)
            root.addView(card)

            // ── WindowManager params — half-screen, top-center ──
            val type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else
                @Suppress("DEPRECATION") android.view.WindowManager.LayoutParams.TYPE_PHONE

            val params = android.view.WindowManager.LayoutParams(
                (300 * dp).toInt(),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                y = (60 * dp).toInt()
            }

            fullScreenBlockView = root
            try {
                wm.addView(fullScreenBlockView, params)
                // 3 সেকেন্ড পরে auto-dismiss
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ removeFullScreenBlockPopup() }, 3000)
            } catch (e: Exception) {
                android.util.Log.e("RasFocus", "blockPopup: ${e.message}")
                isPopupVisible.set(false)
            }
        }
    }

    private fun removeFullScreenBlockPopup() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            fullScreenBlockView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
            fullScreenBlockView = null
            isPopupVisible.set(false)
        }
    }

    override fun onInterrupt() {}
}
