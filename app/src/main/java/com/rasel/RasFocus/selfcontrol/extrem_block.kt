import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlinx.coroutines.*


// ╔══════════════════════════════════════════════════════════════╗
// ║  SECTION 1 — CONSTANTS & DATA                               ║
// ╚══════════════════════════════════════════════════════════════╝

object BlockType {
    const val ADULT_KEYWORD       = 0
    const val UNINSTALL_PROTECT   = 2
    const val IMG_VID_SEARCH      = 3
    const val APP_BLOCKED         = 4
    const val ADULT_APP           = 7
    const val UNSUPPORTED_BROWSER = 8
    const val IG_SEARCH           = 12
    const val IG_REELS            = 13
    const val TELEGRAM_SEARCH     = 15
    const val YT_SHORTS           = 16
    const val SNAPCHAT_STORIES    = 17
    const val WA_CHANNELS         = 18
}

data class BlockEvent(
    val blockType: Int,
    val packageName: String,
    val detectedText: String,
    val appLabel: String = "",
)

// Block type → (icon, title, subtitle, color)
data class BlockUiInfo(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val accentColor: Color,
)

private val SUPPORTED_BROWSERS = setOf(
    "com.android.chrome", "com.chrome.beta", "com.chrome.dev",
    "org.mozilla.firefox", "org.mozilla.fenix", "org.mozilla.firefox_beta",
    "org.mozilla.rocket", "com.brave.browser", "com.opera.browser",
    "com.opera.mini.native", "com.duckduckgo.mobile.android",
    "com.vivaldi.browser", "org.torproject.torbrowser",
    "com.spin.browser", "com.ecosia.android",
    "com.hsv.freeadblockerbrowser",
    "idm.internet.download.manager", "idm.internet.download.manager.plus",
    "com.instantbits.cast.webvideo", "com.kiwibrowser.browser",
    "com.microsoft.emmx",
)

private val YOUTUBE_PACKAGES = setOf(
    "com.google.android.youtube",
    "com.vanced.android.youtube",
    "app.revanced.android.youtube",
)

private val TELEGRAM_REGEX    = Pattern.compile("telegram|challegram")
private val PLUS18_PATTERN    = Pattern.compile("18\\s*\\+|\\+18")
private val SPECIAL_WITH_PLUS = Pattern.compile("[=\"\\[\\]\$%\\-\\\\,_~`\u2019';:!?/|^<>\u203a&{}()]")
private val SPECIAL_NO_PLUS   = Pattern.compile("[+=\"\\[\\]\$%\\-\\\\,_~`\u2019';:!?/|^<>\u203a&{}()]")
private val MULTI_SPACE       = Pattern.compile("\\s+")
private val regexCache        = ConcurrentHashMap<String, Pattern?>()


// ╔══════════════════════════════════════════════════════════════╗
// ║  SECTION 2 — DETECTION ENGINE                               ║
// ╚══════════════════════════════════════════════════════════════╝

class ExtremeDetector(private val service: AccessibilityService) {

    // ── Settings (SharedPreferences থেকে load করো) ─────────────────────
    var isPornBlockerEnabled         = true
    var isReelsBlocked               = true
    var isYtShortsBlocked            = true
    var isSnapchatStoriesBlocked     = true
    var isTelegramSearchBlocked      = true
    var isInstagramSearchBlocked     = true
    var isWhatsappChannelsBlocked    = false
    var isUnsupportedBrowsersBlocked = true
    var isImgVidSearchBlocked        = false

    var adultKeywords:  List<String> = emptyList()
    var whitelistWords: List<String> = emptyList()

    // ── Callback ────────────────────────────────────────────────────────
    var onBlockTriggered: ((BlockEvent) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val lastBrowserRemindAt = HashMap<String, Long>()

    // ── MAIN ENTRY POINT ─────────────────────────────────────────────────
    fun detectAndBlock(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == service.packageName) return
        if (isSystemSkipPackage(pkg)) return

        // 1. Instagram / Facebook Reels
        if (isReelsBlocked) {
            if (detectInstagramReels(event) != null) {
                triggerBlock(pkg, BlockType.IG_REELS, "Reels"); return
            }
        }

        // 2. YouTube Shorts
        if (isYtShortsBlocked && pkg in YOUTUBE_PACKAGES) {
            if (detectYouTubeShorts(event) != null) {
                triggerBlock(pkg, BlockType.YT_SHORTS, "YouTube Shorts"); return
            }
        }

        // 3. Snapchat Stories / Spotlight
        if (isSnapchatStoriesBlocked && pkg == "com.snapchat.android") {
            if (detectSnapchatStories(event) != null) {
                triggerBlock(pkg, BlockType.SNAPCHAT_STORIES, "Snapchat Stories"); return
            }
        }

        // 4. WhatsApp Channels
        if (isWhatsappChannelsBlocked &&
            (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b")) {
            if (event.className?.toString() ==
                "com.whatsapp.newsletter.directory.ui.NewsletterDirectoryCategoriesActivity") {
                triggerBlock(pkg, BlockType.WA_CHANNELS, "WhatsApp Channels"); return
            }
        }

        // 5. Telegram Search
        if (isTelegramSearchBlocked && detectTelegramSearch(event) != null) {
            triggerBlock(pkg, BlockType.TELEGRAM_SEARCH, "Telegram search"); return
        }

        // 6. Instagram Search tab
        if (isInstagramSearchBlocked && detectInstagramSearch(event) != null) {
            triggerBlock(pkg, BlockType.IG_SEARCH, "Instagram search"); return
        }

        // 7. Screen text extraction
        val rawText   = extractEventText(event)
        val cleanText = cleanText(rawText)
        val wordCount = if (cleanText.isNotBlank())
            MULTI_SPACE.split(cleanText.trim()).size else 0
        if (wordCount > 25) return   // too many words = UI text, not a URL/query

        // 8. Unsupported browser
        if (event.className?.toString() == "android.webkit.WebView") {
            if (isBrowserPackage(pkg) && pkg !in SUPPORTED_BROWSERS) {
                handleUnsupportedBrowser(event, pkg); return
            }
        }

        // 9. Adult keyword (event text)
        if (isPornBlockerEnabled && cleanText.length >= 3) {
            if (matchesAnyKeyword(cleanText, whitelistWords)) return
            if (matchesAnyKeyword(cleanText, adultKeywords)) {
                val type = if (isBrowserPackage(pkg)) BlockType.ADULT_KEYWORD
                           else BlockType.ADULT_APP
                triggerBlock(pkg, type, cleanText); return
            }
        }

        // 10. NodeInfo tree scan (scroll-discovered content)
        if (isPornBlockerEnabled) {
            val root = event.source ?: return
            scanNodeTree(root, pkg)
        }
    }

    // ── DETECTION HELPERS ────────────────────────────────────────────────

    private fun detectInstagramReels(event: AccessibilityEvent): Any? {
        val pkg = event.packageName?.toString() ?: return null
        if (pkg == "com.instagram.android" || pkg == "com.instagram.lite") {
            val src = event.source
            val clipsId = "$pkg:id/clips_tab"
            if (src?.viewIdResourceName == clipsId) return event
            val clipsTab = findNodeById(src, clipsId)
            if (clipsTab?.isSelected == true) return clipsTab
            val likeBtn = findNodeById(src, "$pkg:id/like_button")
            if (likeBtn?.isVisibleToUser == true) return likeBtn
            if (src?.viewIdResourceName == "$pkg:id/peek_container") {
                val ind = findNodeById(src, "$pkg:id/indicator_container")
                if (ind?.contentDescription != null) return event
            }
            val titleNode = findNodeById(src, "$pkg:id/action_bar_title")
            if (titleNode?.text?.toString()?.lowercase()?.contains("reel") == true)
                return titleNode
            return null
        }
        if (pkg == "com.facebook.katana" || pkg == "com.facebook.lite") {
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
                event.contentDescription?.toString() == "View reels") return event
            if (event.className?.toString() ==
                "com.facebook.fbshorts.viewer.activity.FbShortsViewerActivity") return event
            val src = event.source
            if (findNodeByText(src, "FbShortsComposer") != null) return event
            if (findNodeByText(src, "Navigate to your Reels profile") != null) return event
            if (findNodeByText(src, "Add comment...") != null) return event
        }
        return null
    }

    private fun detectYouTubeShorts(event: AccessibilityEvent): Any? {
        val pkg = event.packageName?.toString() ?: return null
        return findNodeById(event.source, "$pkg:id/reel_recycler")
    }

    private fun detectSnapchatStories(event: AccessibilityEvent): Any? {
        val pkg  = event.packageName?.toString() ?: return null
        val src  = event.source
        val vId  = src?.viewIdResourceName
        if (vId == "$pkg:id/ngs_spotlight_icon_container") return event
        if (vId == "$pkg:id/ngs_community_icon_container") return event
        if (findNodeById(src, "$pkg:id/ngs_spotlight_icon_container") != null) return event
        if (findNodeById(src, "$pkg:id/ngs_community_icon_container") != null) return event
        return null
    }

    private fun detectTelegramSearch(event: AccessibilityEvent): Any? {
        val pkg = event.packageName?.toString() ?: return null
        if (!TELEGRAM_REGEX.matcher(pkg).find()) return null
        if (event.className?.toString() != "android.widget.EditText") return null
        val text = event.text?.firstOrNull()?.toString() ?: return null
        return if (text == "Search" || text == "Qidiruv") event else null
    }

    private fun detectInstagramSearch(event: AccessibilityEvent): Any? {
        val pkg = event.packageName?.toString() ?: return null
        if (pkg != "com.instagram.android" && pkg != "com.instagram.lite") return null
        val src = event.source
        val searchTabId = "$pkg:id/search_tab"
        if (src?.viewIdResourceName == searchTabId) return event
        val st = findNodeById(src, searchTabId)
        if (st?.isSelected == true) return st
        findNodeById(src, "$pkg:id/action_bar_search_edit_text")?.let { return it }
        return findNodeById(src, "$pkg:id/search_bar_real_field")
    }

    private fun scanNodeTree(node: AccessibilityNodeInfo, pkg: String) {
        try {
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            val combined = cleanText("$text $desc")
            if (combined.length >= 3 && !node.isFocused && node.isVisibleToUser) {
                if (!matchesAnyKeyword(combined, whitelistWords) &&
                    matchesAnyKeyword(combined, adultKeywords)) {
                    triggerBlock(pkg, BlockType.ADULT_KEYWORD, combined)
                    return
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                scanNodeTree(child, pkg)
                child.recycle()
            }
        } catch (_: Exception) {}
    }

    private fun handleUnsupportedBrowser(event: AccessibilityEvent, pkg: String) {
        if (isUnsupportedBrowsersBlocked) {
            triggerBlock(pkg, BlockType.UNSUPPORTED_BROWSER, pkg)
        } else {
            val key = "$pkg-remind"
            val last = lastBrowserRemindAt[key] ?: 0L
            if (System.currentTimeMillis() >= last + 300_000L) {
                lastBrowserRemindAt[key] = System.currentTimeMillis()
                // optional: toast
            }
        }
    }

    // ── BLOCK TRIGGER (Back / Home / Redirect) ───────────────────────────
    private fun triggerBlock(pkg: String, blockType: Int, detectedText: String) {
        val label = try {
            service.packageManager
                .getApplicationInfo(pkg, 0)
                .let { service.packageManager.getApplicationLabel(it).toString() }
        } catch (_: Exception) { pkg }

        handler.post {
            onBlockTriggered?.invoke(BlockEvent(blockType, pkg, detectedText, label))

            handler.postDelayed({
                // Back press — সব type এ
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

                // Reels / Shorts / Snapchat → Home ও press করো
                if (blockType in listOf(
                    BlockType.IG_REELS, BlockType.YT_SHORTS,
                    BlockType.SNAPCHAT_STORIES, BlockType.WA_CHANNELS)) {
                    handler.postDelayed({
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    }, 500L)
                }

                // Browser adult → google.com redirect
                if (blockType == BlockType.ADULT_KEYWORD && isBrowserPackage(pkg)) {
                    handler.postDelayed({ redirectBrowser(pkg, "https://www.google.com") }, 600L)
                }

                // YouTube Shorts → dummy URL দিয়ে tab replace
                if (blockType == BlockType.YT_SHORTS && pkg in YOUTUBE_PACKAGES) {
                    handler.postDelayed({ redirectBrowser(pkg, "https://youtu.be/fwhfewhf") }, 600L)
                }
            }, 500L)
        }
    }

    private fun redirectBrowser(pkg: String, url: String) {
        try {
            service.applicationContext.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(pkg)
                    putExtra("com.android.browser.application_id", pkg)
                }
            )
        } catch (_: Exception) {}
    }

    // ── TEXT PROCESSING (D4.z.d exact) ──────────────────────────────────
    fun cleanText(raw: String): String {
        if (raw.isBlank()) return ""
        var t = raw.replace("%20", " ").replace("%23", "#")
        val keep18 = PLUS18_PATTERN.matcher(t).find()
        t = (if (keep18) SPECIAL_WITH_PLUS else SPECIAL_NO_PLUS).matcher(t).replaceAll(" ")
        t = MULTI_SPACE.matcher(t).replaceAll(" ").trim()
        return t.lowercase(Locale.ROOT)
    }

    fun matchesAnyKeyword(text: String, keywords: List<String>): Boolean {
        if (text.length < 3 || keywords.isEmpty()) return false
        for (kw in keywords) {
            val p = regexCache.getOrPut(kw) {
                try { Pattern.compile(kw, Pattern.CASE_INSENSITIVE) } catch (_: Exception) { null }
            } ?: continue
            if (p.matcher(text).find()) return true
        }
        return false
    }

    private fun extractEventText(event: AccessibilityEvent): String {
        return buildList {
            event.text?.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" ")) }
            event.contentDescription?.let { add(it.toString()) }
            event.source?.text?.let { add(it.toString()) }
            event.source?.contentDescription?.let { add(it.toString()) }
        }.filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun findNodeById(root: AccessibilityNodeInfo?, id: String) =
        try { root?.findAccessibilityNodeInfosByViewId(id)?.firstOrNull() }
        catch (_: Exception) { null }

    private fun findNodeByText(root: AccessibilityNodeInfo?, text: String) =
        try { root?.findAccessibilityNodeInfosByText(text)?.firstOrNull() }
        catch (_: Exception) { null }

    private fun isBrowserPackage(pkg: String) = try {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com")).apply { setPackage(pkg) }
        service.packageManager.resolveActivity(i, 0)?.activityInfo?.packageName == pkg
    } catch (_: Exception) { false }

    private fun isSystemSkipPackage(pkg: String) = pkg in setOf(
        "android", "com.android.phone", "com.android.incallui",
        "com.android.contacts", "com.google.android.contacts",
        "com.samsung.android.contacts", "com.samsung.android.app.contacts"
    )
}


// ╔══════════════════════════════════════════════════════════════╗
// ║  SECTION 3 — COMPOSE LIFECYCLE OWNER                        ║
// ║  (ComposeView কে Service এ use করার জন্য দরকার)             ║
// ╚══════════════════════════════════════════════════════════════╝

class ComposeLifecycleOwner : SavedStateRegistryOwner, ViewModelStoreOwner {

    private var _lifecycle = object : Lifecycle() {
        private val observers = mutableListOf<LifecycleObserver>()
        private var state = State.INITIALIZED

        override fun addObserver(observer: LifecycleObserver) { observers.add(observer) }
        override fun removeObserver(observer: LifecycleObserver) { observers.remove(observer) }
        override val currentState get() = state

        fun moveToState(newState: State) {
            state = newState
            observers.toList().filterIsInstance<DefaultLifecycleObserver>().forEach { obs ->
                when (newState) {
                    State.CREATED  -> obs.onCreate(this@ComposeLifecycleOwner)
                    State.STARTED  -> obs.onStart(this@ComposeLifecycleOwner)
                    State.RESUMED  -> obs.onResume(this@ComposeLifecycleOwner)
                    State.DESTROYED -> obs.onDestroy(this@ComposeLifecycleOwner)
                    else -> {}
                }
            }
        }
    }

    private val _vmStore = ViewModelStore()
    private val _savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = _lifecycle
    override val viewModelStore: ViewModelStore get() = _vmStore
    override val savedStateRegistry: SavedStateRegistry
        get() = _savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        _savedStateRegistryController.performRestore(null)
        _lifecycle.moveToState(Lifecycle.State.CREATED)
    }
    fun onResume() = _lifecycle.moveToState(Lifecycle.State.RESUMED)
    fun onDestroy() = _lifecycle.moveToState(Lifecycle.State.DESTROYED)

    fun attachToView(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }
}


// ╔══════════════════════════════════════════════════════════════╗
// ║  SECTION 4 — BLOCK SCREEN OVERLAY MANAGER                   ║
// ╚══════════════════════════════════════════════════════════════╝

class ExtremeBlockOverlay(private val service: AccessibilityService) {

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private val lifecycleOwner = ComposeLifecycleOwner()

    // Mutable state — Compose এ reactively update হবে
    private val _blockEvent = mutableStateOf<BlockEvent?>(null)
    private val _isVisible  = mutableStateOf(false)
    private val _countdown  = mutableStateOf(5)   // seconds

    // Countdown settings
    var countdownSeconds: Int = 5
    var customMessage: String = "This page is blocked."
    var redirectUrl: String   = "https://google.com"

    private var countdownJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun init() {
        windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        lifecycleOwner.onCreate()

        composeView = ComposeView(service).apply {
            lifecycleOwner.attachToView(this)
            setContent {
                BlockScreenContent(
                    blockEvent   = _blockEvent.value,
                    isVisible    = _isVisible.value,
                    countdown    = _countdown.value,
                    customMessage = customMessage,
                    onClose      = { hide() },
                )
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )

        try {
            windowManager?.addView(composeView, params)
            lifecycleOwner.onResume()
        } catch (_: Exception) {}
    }

    fun show(event: BlockEvent) {
        _blockEvent.value = event
        _countdown.value  = countdownSeconds
        _isVisible.value  = true
        startCountdown()
    }

    fun hide() {
        _isVisible.value = false
        countdownJob?.cancel()
    }

    fun destroy() {
        scope.cancel()
        try { windowManager?.removeViewImmediate(composeView) } catch (_: Exception) {}
        lifecycleOwner.onDestroy()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            var remaining = countdownSeconds
            while (remaining >= 0) {
                _countdown.value = remaining
                delay(1000L)
                remaining--
            }
            // countdown শেষ — screen hide করো
            hide()
        }
    }
}


// ╔══════════════════════════════════════════════════════════════╗
// ║  SECTION 5 — COMPOSE UI (Block Screen)                      ║
// ╚══════════════════════════════════════════════════════════════╝

@Composable
fun BlockScreenContent(
    blockEvent:    BlockEvent?,
    isVisible:     Boolean,
    countdown:     Int,
    customMessage: String,
    onClose:       () -> Unit,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter   = fadeIn(tween(200)) + slideInVertically(tween(250)) { -40 },
        exit    = fadeOut(tween(150)) + slideOutVertically(tween(200)) { -40 },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE6000000)),   // 90% black scrim
            contentAlignment = Alignment.Center,
        ) {
            BlockCard(
                event         = blockEvent,
                countdown     = countdown,
                customMessage = customMessage,
                onClose       = onClose,
            )
        }
    }
}

@Composable
private fun BlockCard(
    event:         BlockEvent?,
    countdown:     Int,
    customMessage: String,
    onClose:       () -> Unit,
) {
    val uiInfo = event?.let { blockUiInfo(it.blockType) } ?: defaultBlockUiInfo()

    val progress by animateFloatAsState(
        targetValue = if (countdown > 0) countdown / 5f else 0f,
        animationSpec = tween(900),
        label = "countdown_progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Icon ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(uiInfo.accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = uiInfo.icon,
                    contentDescription = null,
                    tint   = uiInfo.accentColor,
                    modifier = Modifier.size(36.dp),
                )
            }

            // ── Title ────────────────────────────────────────────────────
            Text(
                text       = uiInfo.title,
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                textAlign  = TextAlign.Center,
            )

            // ── Subtitle ─────────────────────────────────────────────────
            Text(
                text      = uiInfo.subtitle,
                fontSize  = 14.sp,
                color     = Color(0xFFAAAAAA),
                textAlign = TextAlign.Center,
            )

            // ── Custom message ────────────────────────────────────────────
            if (customMessage.isNotBlank()) {
                Surface(
                    shape  = RoundedCornerShape(10.dp),
                    color  = Color(0xFF252525),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text     = customMessage,
                        fontSize = 13.sp,
                        color    = Color(0xFFCCCCCC),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // ── App name (detected app) ────────────────────────────────
            event?.appLabel?.takeIf { it.isNotBlank() && it != event.packageName }?.let { label ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint     = Color(0xFF666666),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text     = label,
                        fontSize = 12.sp,
                        color    = Color(0xFF666666),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Countdown progress ────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color            = uiInfo.accentColor,
                    trackColor       = Color(0xFF333333),
                )
                Text(
                    text     = if (countdown > 0) "Closing in $countdown sec..." else "Closing...",
                    fontSize = 11.sp,
                    color    = Color(0xFF555555),
                )
            }

            // ── Close button ──────────────────────────────────────────────
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = uiInfo.accentColor,
                    contentColor   = Color.White,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier    = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Close", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

// Block type → UI info mapping
private fun blockUiInfo(blockType: Int): BlockUiInfo = when (blockType) {
    BlockType.ADULT_KEYWORD, BlockType.ADULT_APP -> BlockUiInfo(
        icon        = Icons.Default.VisibilityOff,
        title       = "Adult Content Blocked",
        subtitle    = "Explicit content was detected and blocked.",
        accentColor = Color(0xFFE53935),
    )
    BlockType.IG_REELS -> BlockUiInfo(
        icon        = Icons.Default.Block,
        title       = "Reels Blocked",
        subtitle    = "Instagram / Facebook Reels are blocked.",
        accentColor = Color(0xFFE91E8C),
    )
    BlockType.YT_SHORTS -> BlockUiInfo(
        icon        = Icons.Default.Block,
        title       = "YouTube Shorts Blocked",
        subtitle    = "YouTube Shorts have been blocked.",
        accentColor = Color(0xFFFF1744),
    )
    BlockType.SNAPCHAT_STORIES -> BlockUiInfo(
        icon        = Icons.Default.Block,
        title       = "Snapchat Stories Blocked",
        subtitle    = "Stories & Spotlight are blocked.",
        accentColor = Color(0xFFFFCA28),
    )
    BlockType.TELEGRAM_SEARCH -> BlockUiInfo(
        icon        = Icons.Default.Block,
        title       = "Telegram Search Blocked",
        subtitle    = "Search is blocked in Telegram.",
        accentColor = Color(0xFF29B6F6),
    )
    BlockType.IG_SEARCH -> BlockUiInfo(
        icon        = Icons.Default.Block,
        title       = "Instagram Search Blocked",
        subtitle    = "Instagram search tab is blocked.",
        accentColor = Color(0xFFAB47BC),
    )
    BlockType.WA_CHANNELS -> BlockUiInfo(
        icon        = Icons.Default.Block,
        title       = "WhatsApp Channels Blocked",
        subtitle    = "WhatsApp Channels are blocked.",
        accentColor = Color(0xFF43A047),
    )
    BlockType.UNSUPPORTED_BROWSER -> BlockUiInfo(
        icon        = Icons.Default.Shield,
        title       = "Unsupported Browser",
        subtitle    = "Please use Chrome, Firefox, Brave, Opera, or DuckDuckGo.",
        accentColor = Color(0xFFFFA726),
    )
    BlockType.UNINSTALL_PROTECT -> BlockUiInfo(
        icon        = Icons.Default.Lock,
        title       = "Uninstall Protection Active",
        subtitle    = "All settings are protected.",
        accentColor = Color(0xFF5C6BC0),
    )
    BlockType.IMG_VID_SEARCH -> BlockUiInfo(
        icon        = Icons.Default.VisibilityOff,
        title       = "Image & Video Search Blocked",
        subtitle    = "Image and video search is blocked.",
        accentColor = Color(0xFFEF6C00),
    )
    else -> defaultBlockUiInfo()
}

private fun defaultBlockUiInfo() = BlockUiInfo(
    icon        = Icons.Default.Block,
    title       = "Content Blocked",
    subtitle    = "This content is blocked.",
    accentColor = Color(0xFF757575),
)


// ╔══════════════════════════════════════════════════════════════╗
// ║  SECTION 6 — HOW TO USE                                     ║
// ╚══════════════════════════════════════════════════════════════╝
//
//  তোমার AccessibilityService এ:
//
//  class YourService : AccessibilityService() {
//
//      private lateinit var detector: ExtremeDetector
//      private lateinit var overlay:  ExtremeBlockOverlay
//
//      override fun onServiceConnected() {
//          super.onServiceConnected()
//
//          overlay = ExtremeBlockOverlay(this).apply {
//              countdownSeconds = prefs.getInt("countdown", 5)
//              customMessage    = prefs.getString("block_msg", "This page is blocked.") ?: ""
//          }
//          overlay.init()
//
//          detector = ExtremeDetector(this).apply {
//              // settings load
//              isPornBlockerEnabled         = prefs.getBoolean("adult_block", true)
//              isReelsBlocked               = prefs.getBoolean("reels_block", true)
//              isYtShortsBlocked            = prefs.getBoolean("yt_shorts_block", true)
//              isSnapchatStoriesBlocked     = prefs.getBoolean("snapchat_block", true)
//              isTelegramSearchBlocked      = prefs.getBoolean("telegram_search", true)
//              isInstagramSearchBlocked     = prefs.getBoolean("ig_search", true)
//              isWhatsappChannelsBlocked    = prefs.getBoolean("wa_channels", false)
//              isUnsupportedBrowsersBlocked = prefs.getBoolean("unsupported_browser", true)
//
//              adultKeywords  = loadKeywordList()   // List<String> regex patterns
//              whitelistWords = loadWhitelistList()
//
//              onBlockTriggered = { event -> overlay.show(event) }
//          }
//      }
//
//      override fun onAccessibilityEvent(event: AccessibilityEvent) {
//          detector.detectAndBlock(event)
//      }
//
//      override fun onInterrupt() {}
//
//      override fun onDestroy() {
//          super.onDestroy()
//          overlay.destroy()
//      }
//  }

