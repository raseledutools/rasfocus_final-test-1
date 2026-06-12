package com.rasel.RasFocus.selfcontrol

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rasel.RasFocus.DataManager
import com.rasel.RasFocus.selfcontrol.RasFocusAccessibilityService
import kotlinx.coroutines.*
import kotlin.math.*

// ─────────────────────────────────────────
// Colors
// ─────────────────────────────────────────
val DClrTeal     = Color(0xFF0CA8B0)
val DClrTealDark = Color(0xFF0891A0)
val DClrWhite    = Color(0xFFFFFFFF)
val DClrDark     = Color(0xFF1A2332)
val DClrGray     = Color(0xFF64748B)
val DClrBg       = Color(0xFFF1F5F9)
val DClrSurface  = Color(0xFFFFFFFF)
val DClrRed      = Color(0xFFEF4444)
val DClrGreen    = Color(0xFF22C55E)
val DClrAmber    = Color(0xFFF59E0B)

data class BlockItem(val name: String)

// ─────────────────────────────────────────
// PERMISSION HELPERS
// ─────────────────────────────────────────

fun isAccessibilityEnabled(context: Context): Boolean = try {
    val svc = "${context.packageName}/${RasFocusAccessibilityService::class.java.canonicalName}"
    (Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
        .contains(svc, ignoreCase = true)
} catch (e: Exception) { false }

fun hasUsageStatsPermission(context: Context): Boolean {
    val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
}

fun hasOverlayPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true

// ─────────────────────────────────────────
// ALLOW-LIST BLOCKER  — UsageStats, NO Accessibility
// Session চলাকালে allow list ছাড়া সব app + settings block
// ─────────────────────────────────────────

object AllowListBlocker {
    private var job: Job? = null

    // Known launcher & system UI packages যেগুলো কখনো block করা যাবে না
    private val ALWAYS_ALLOWED = setOf(
        "com.android.launcher",
        "com.android.launcher2",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.sec.android.app.launcher",
        "com.oneplus.launcher",
        "com.oppo.launcher",
        "com.realme.launcher",
        "com.android.systemui",
        "android"                 // system itself
    )

    /**
     * allowedPackages: user এর allow list + আমাদের own app
     * Settings, যেকোনো অন্য app — সব redirect to home
     */
    fun start(context: Context, allowedPackages: Set<String>) {
        stop()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ownPkg = context.packageName

        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 3000, now)
                val fg = stats
                    ?.filter { it.lastTimeUsed > now - 1500 }
                    ?.maxByOrNull { it.lastTimeUsed }
                    ?.packageName ?: ""

                val isBlocked = fg.isNotEmpty()
                    && fg !in ALWAYS_ALLOWED
                    && fg != ownPkg
                    && fg !in allowedPackages

                if (isBlocked) {
                    withContext(Dispatchers.Main) {
                        context.startActivity(
                            Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }
                }
                delay(800) // 800ms — tighter check
            }
        }
    }

    fun stop() { job?.cancel(); job = null }
}

// ─────────────────────────────────────────
// FLOATING STOPWATCH — WindowManager overlay, ✕ button
// ─────────────────────────────────────────

object FloatingStopwatch {
    private var wm: WindowManager? = null
    private var rootView: android.view.View? = null
    private var tickJob: Job? = null
    var isShowing = false; private set

    fun show(context: Context, onDismiss: () -> Unit) {
        if (isShowing) return
        val mgr = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = mgr

        val root = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#E6000000"))
                cornerRadius = 100f
            }
            setPadding(28, 14, 20, 14)
        }

        val timerTv = android.widget.TextView(context).apply {
            text = "00:00"
            textSize = 18f
            setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
        }

        val closeTv = android.widget.TextView(context).apply {
            text = "  ✕"
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#80FFFFFF"))
            setPadding(4, 0, 0, 0)
            setOnClickListener { dismiss(onDismiss) }
        }

        root.addView(timerTv); root.addView(closeTv)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 16; y = 180 }

        var lx = 0; var ly = 0
        root.setOnTouchListener { _, e ->
            when (e.action) {
                android.view.MotionEvent.ACTION_DOWN -> { lx = e.rawX.toInt(); ly = e.rawY.toInt() }
                android.view.MotionEvent.ACTION_MOVE -> {
                    params.x += lx - e.rawX.toInt(); params.y += e.rawY.toInt() - ly
                    lx = e.rawX.toInt(); ly = e.rawY.toInt()
                    mgr.updateViewLayout(root, params)
                }
            }; false
        }

        mgr.addView(root, params); rootView = root; isShowing = true
        var sec = 0
        tickJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) { delay(1000); sec++; timerTv.text = "%02d:%02d".format(sec / 60, sec % 60) }
        }
    }

    fun dismiss(onDismiss: (() -> Unit)? = null) {
        tickJob?.cancel(); tickJob = null
        rootView?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        rootView = null; wm = null; isShowing = false
        onDismiss?.invoke()
    }
}

// ─────────────────────────────────────────
// SOUND ENGINE — AudioTrack, কোনো file নেই
// ─────────────────────────────────────────

enum class SoundType(val label: String, val emoji: String) {
    WHITE_NOISE("White Noise", "🌫️"),
    CLASSIC_BROWN("Classic Brown", "🟤"),
    DEEP_BROWN("Deep Brown", "🪵"),
    WARM_BROWN("Warm Brown", "☕"),
    HEAVY_RAIN("Heavy Rain", "🌧️"),
    WATERFALL("Waterfall", "💧"),
    WIND("Wind", "🌬️"),
    DEEP_FOCUS("Deep Focus", "🎯"),
    SPACE_DRONE("Space Drone", "🚀"),
    COSMIC_BROWN("Cosmic Brown", "🌌")
}

object AmbientSoundEngine {
    private var track: AudioTrack? = null
    private var genJob: Job? = null
    private const val SR = 44100
    private const val BUF = 4096

    fun play(type: SoundType) {
        stop()
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SR).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(BUF * 4).setTransferMode(AudioTrack.MODE_STREAM).build()
        track?.play()

        genJob = CoroutineScope(Dispatchers.IO).launch {
            val buf = FloatArray(BUF)
            var bL = 0f; var bR = 0f; var sampleIdx = 0L

            while (isActive) {
                for (i in 0 until BUF / 2) {
                    val w1 = (Math.random() * 2 - 1).toFloat()
                    val w2 = (Math.random() * 2 - 1).toFloat()
                    val t = sampleIdx.toDouble() / SR
                    sampleIdx++

                    val (sL, sR) = when (type) {
                        SoundType.WHITE_NOISE -> Pair(w1 * 0.3f, w2 * 0.3f)
                        SoundType.CLASSIC_BROWN -> { bL = (bL + w1 * 0.02f).coerceIn(-1f,1f); bR = (bR + w2 * 0.02f).coerceIn(-1f,1f); Pair(bL * 3.5f, bR * 3.5f) }
                        SoundType.DEEP_BROWN -> { bL = (bL * 0.998f + w1 * 0.012f).coerceIn(-1f,1f); bR = (bR * 0.998f + w2 * 0.012f).coerceIn(-1f,1f); Pair(bL * 4f, bR * 4f) }
                        SoundType.WARM_BROWN -> { bL = (bL * 0.99f + w1 * 0.025f).coerceIn(-1f,1f); bR = (bR * 0.99f + w2 * 0.025f).coerceIn(-1f,1f); Pair(bL * 3f, bR * 3f) }
                        SoundType.HEAVY_RAIN -> { val drop = if (Math.random() < 0.008) w1 * 0.5f else 0f; bL = (bL + w1 * 0.018f).coerceIn(-1f,1f); Pair(bL * 2f + drop, bL * 2f + w2 * 0.15f) }
                        SoundType.WATERFALL -> { val m = w1 * 0.5f + w2 * 0.3f; Pair(m * 0.55f, (w2 * 0.5f + w1 * 0.3f) * 0.55f) }
                        SoundType.WIND -> { bL = (bL + w1 * 0.022f).coerceIn(-1f,1f); bR = (bR + w2 * 0.022f).coerceIn(-1f,1f); val mod = (0.5f + 0.5f * sin(t * 0.3)).toFloat(); Pair(bL * mod * 3f, bR * mod * 3f) }
                        SoundType.DEEP_FOCUS -> { val drone = sin(2 * PI * 40.0 * t).toFloat() * 0.22f; bL = (bL + w1 * 0.015f).coerceIn(-1f,1f); Pair(bL * 1.8f + drone, bL * 1.8f + drone) }
                        SoundType.SPACE_DRONE -> { val d1 = sin(2 * PI * 60.0 * t).toFloat() * 0.18f; val d2 = sin(2 * PI * 90.0 * t).toFloat() * 0.09f; bL = (bL + w1 * 0.008f).coerceIn(-1f,1f); bR = (bR + w2 * 0.008f).coerceIn(-1f,1f); Pair(bL * 0.8f + d1 + d2, bR * 0.8f + d1 - d2) }
                        SoundType.COSMIC_BROWN -> { val sub = sin(2 * PI * 30.0 * t).toFloat() * 0.12f; bL = (bL * 0.9995f + w1 * 0.008f).coerceIn(-1f,1f); bR = (bR * 0.9995f + w2 * 0.008f).coerceIn(-1f,1f); Pair(bL * 4f + sub, bR * 4f + sub) }
                    }
                    buf[i * 2] = sL.coerceIn(-1f, 1f); buf[i * 2 + 1] = sR.coerceIn(-1f, 1f)
                }
                track?.write(buf, 0, BUF, AudioTrack.WRITE_BLOCKING)
            }
        }
    }

    fun stop() { genJob?.cancel(); genJob = null; track?.stop(); track?.release(); track = null }
    val isPlaying get() = track != null
}

// ─────────────────────────────────────────
// MAIN SCREEN
// ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Deep_study() {
    val context = LocalContext.current

    var activeSubTab      by remember { mutableIntStateOf(0) }
    var isFocusMode       by remember { mutableStateOf(false) }
    var isBreak           by remember { mutableStateOf(false) }
    var focusMin          by remember { mutableIntStateOf(25) }
    var restMin           by remember { mutableIntStateOf(5) }
    var totalSessions     by remember { mutableIntStateOf(4) }
    var currentSession    by remember { mutableIntStateOf(1) }
    var chkSound          by remember { mutableStateOf(false) }
    var chkFloat          by remember { mutableStateOf(false) }
    var chkNet            by remember { mutableStateOf(false) }
    var chkSet            by remember { mutableStateOf(true) }
    var chkTask           by remember { mutableStateOf(true) }
    var chkBlockBreak     by remember { mutableStateOf(false) }
    var chkStrict         by remember { mutableStateOf(DataManager.isDeepStudyStrict) }
    var chkHideBreakClose by remember { mutableStateOf(false) }
    var soundType         by remember { mutableStateOf(SoundType.WHITE_NOISE) }

    val allowWebs = remember { mutableStateListOf<BlockItem>().apply { addAll(DataManager.userWebList.map { BlockItem(it) }) } }
    val allowApps = remember { mutableStateListOf<BlockItem>().apply { addAll(DataManager.userAppList.map { BlockItem(it) }) } }

    var showBottomSheet      by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var blockingEnabled      by remember { mutableStateOf(false) }
    val strictLocked = isFocusMode && chkStrict && blockingEnabled

    // ── Floating stopwatch sync ──
    LaunchedEffect(chkFloat, isFocusMode) {
        when {
            !chkFloat -> FloatingStopwatch.dismiss()
            chkFloat && isFocusMode && hasOverlayPermission(context) ->
                FloatingStopwatch.show(context) { chkFloat = false }
        }
    }

    // ── Sound sync ──
    LaunchedEffect(chkSound, soundType, isFocusMode) {
        if (chkSound && isFocusMode) AmbientSoundEngine.play(soundType)
        else AmbientSoundEngine.stop()
    }

    // ── AllowList blocker sync ──
    LaunchedEffect(isFocusMode, blockingEnabled) {
        if (isFocusMode && blockingEnabled && hasUsageStatsPermission(context)) {
            // own package + user allow list — সব বাকি blocked
            val allowed = (allowApps.map { it.name } + context.packageName).toSet()
            AllowListBlocker.start(context, allowed)
        } else {
            AllowListBlocker.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose { AmbientSoundEngine.stop(); FloatingStopwatch.dismiss(); AllowListBlocker.stop() }
    }

    val scrollState = rememberScrollState()

    // ── Permission Dialog ──
    if (showPermissionDialog) {
        PermissionExplainerDialog(
            onGrantAccessibility = {
                showPermissionDialog = false
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                isFocusMode = true; blockingEnabled = false
            },
            onStartWithUsageStats = {
                showPermissionDialog = false
                isFocusMode = true; blockingEnabled = true
                DataManager.isDeepStudyStrict = false
            }
        )
    }

    // ── Root ──
    Column(
        Modifier
            .fillMaxSize()
            .background(DClrBg)
            // status bar safe area — header উপরে উঠে যাওয়া বন্ধ
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // ═══ TAB BAR ═══
        TabBar(activeSubTab) { activeSubTab = it }

        // ═══ CONTENT ═══
        if (activeSubTab == 0) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Timer hero card
                TimerHeroCard(
                    isFocusMode = isFocusMode,
                    isBreak = isBreak,
                    focusMin = focusMin,
                    currentSession = currentSession,
                    totalSessions = totalSessions,
                    blockingEnabled = blockingEnabled,
                    hasAccess = isAccessibilityEnabled(context)
                )

                // Start / Stop button
                StartStopButton(
                    isFocusMode = isFocusMode,
                    strictLocked = strictLocked,
                    onClick = {
                        if (isFocusMode) {
                            if (!strictLocked) {
                                isFocusMode = false; blockingEnabled = false
                                DataManager.isDeepStudyStrict = false
                                RasFocusAccessibilityService.deepStudyInstance?.stopDeepStudySession()
                            }
                        } else {
                            val hasAccess = isAccessibilityEnabled(context)
                            val needsAccess = chkNet || chkSet || chkTask || chkStrict
                            when {
                                !hasAccess && needsAccess -> showPermissionDialog = true
                                hasAccess -> {
                                    isFocusMode = true; blockingEnabled = true
                                    DataManager.isDeepStudyStrict = chkStrict
                                    RasFocusAccessibilityService.deepStudyInstance?.startDeepStudySession(focusMin, chkSound)
                                }
                                else -> { isFocusMode = true; blockingEnabled = true }
                            }
                        }
                    }
                )

                // Session setup card
                SectionCard(title = "Session Setup", icon = Icons.Default.Timer) {
                    TimerSetupRow("Focus", focusMin, 5, 120, 5, !isFocusMode) { focusMin = it }
                    TimerSetupRow("Rest", restMin, 1, 30, 1, !isFocusMode) { restMin = it }
                    TimerSetupRow("Sessions", totalSessions, 1, 10, 1, !isFocusMode) { totalSessions = it }
                }

                // Sound + Floating card (NO accessibility needed)
                SectionCard(title = "Focus Aids", icon = Icons.Default.Headphones) {
                    // Sound row
                    SoundRow(
                        checked = chkSound,
                        enabled = !strictLocked,
                        soundType = soundType,
                        onCheckedChange = { chkSound = it },
                        onSoundTypeChange = { soundType = it }
                    )
                    Spacer(Modifier.height(4.dp))
                    // Floating stopwatch row
                    FloatRow(
                        checked = chkFloat,
                        enabled = !strictLocked,
                        context = context,
                        onCheckedChange = { newVal ->
                            if (newVal && !hasOverlayPermission(context)) {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                            } else {
                                chkFloat = newVal
                                if (!newVal) FloatingStopwatch.dismiss()
                            }
                        }
                    )
                }

                // Blocking card (accessibility needed for internet/settings/task)
                val hasAccess = isAccessibilityEnabled(context)
                val hasUsage  = hasUsageStatsPermission(context)

                BlockingCard(
                    hasAccess = hasAccess,
                    hasUsage = hasUsage,
                    strictLocked = strictLocked,
                    chkNet = chkNet, chkSet = chkSet, chkTask = chkTask,
                    chkBlockBreak = chkBlockBreak, chkStrict = chkStrict, chkHideBreakClose = chkHideBreakClose,
                    onNetChange = { chkNet = it }, onSetChange = { chkSet = it }, onTaskChange = { chkTask = it },
                    onBlockBreakChange = { chkBlockBreak = it },
                    onStrictChange = { chkStrict = it; DataManager.isDeepStudyStrict = it },
                    onHideBreakCloseChange = { chkHideBreakClose = it },
                    context = context
                )

                // Allow list card
                AllowListCard(
                    appCount = allowApps.size,
                    siteCount = allowWebs.size,
                    enabled = !strictLocked,
                    onClick = { showBottomSheet = true }
                )

                Spacer(Modifier.height(32.dp))
            }
        } else {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Coming soon...", color = DClrGray, fontSize = 15.sp)
            }
        }
    }

    // Bottom sheet
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            modifier = Modifier.fillMaxHeight(0.9f),
            containerColor = DClrBg
        ) {
            BlocklistPickerSheet(
                onClose = { showBottomSheet = false },
                onSave = { selectedApps, selectedSites ->
                    allowApps.clear(); allowApps.addAll(selectedApps.map { BlockItem(it) })
                    DataManager.userAppList = selectedApps
                    allowWebs.clear(); allowWebs.addAll(selectedSites.map { BlockItem(it) })
                    DataManager.userWebList = selectedSites
                    showBottomSheet = false
                },
                initialApps  = allowApps.map { it.name },
                initialSites = allowWebs.map { it.name }
            )
        }
    }
}

// ─────────────────────────────────────────
// SUB-COMPOSABLES
// ─────────────────────────────────────────

@Composable
private fun TabBar(active: Int, onSelect: (Int) -> Unit) {
    Surface(
        color = DClrSurface,
        shadowElevation = 2.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Pomodoro", "Active Recall", "Spaced Rep.").forEachIndexed { i, title ->
                val selected = active == i
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) DClrTeal else Color(0xFFEFF3F8))
                        .clickable { onSelect(i) },
                    Alignment.Center
                ) {
                    Text(
                        title,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) DClrWhite else DClrGray,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerHeroCard(
    isFocusMode: Boolean, isBreak: Boolean,
    focusMin: Int, currentSession: Int, totalSessions: Int,
    blockingEnabled: Boolean, hasAccess: Boolean
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isBreak)
                    Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFF16A34A)))
                else
                    Brush.linearGradient(listOf(DClrTeal, DClrTealDark))
            )
            .padding(20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (isFocusMode) "%02d:00".format(focusMin) else "00:00",
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = DClrWhite,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (isBreak) "Break Time ☕" else "Session $currentSession of $totalSessions",
                fontSize = 14.sp,
                color = DClrWhite.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium
            )
            if (isFocusMode) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(DClrWhite.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    val dot = if (hasAccess) "🟢" else "🟡"
                    Text(dot, fontSize = 11.sp)
                    Text(
                        if (hasAccess) "Full Blocking" else "Allow-List Mode",
                        fontSize = 11.sp, color = DClrWhite, fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun StartStopButton(isFocusMode: Boolean, strictLocked: Boolean, onClick: () -> Unit) {
    val bg = when {
        strictLocked -> Color(0xFFC81E1E)
        isFocusMode  -> DClrRed
        else         -> DClrTeal
    }
    val label = when {
        strictLocked -> "🔒  STRICT MODE — LOCKED"
        isFocusMode  -> "⏹  STOP POMODORO"
        else         -> "▶  START POMODORO"
    }
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = bg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DClrWhite)
    }
}

@Composable
private fun SectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = DClrSurface,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(icon, null, tint = DClrTeal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DClrDark)
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundRow(
    checked: Boolean, enabled: Boolean, soundType: SoundType,
    onCheckedChange: (Boolean) -> Unit, onSoundTypeChange: (SoundType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = DClrWhite, checkedTrackColor = DClrTeal)
        )
        Spacer(Modifier.width(10.dp))
        Text("Ambient Sound", fontSize = 14.sp, color = if (enabled) DClrDark else DClrGray, modifier = Modifier.weight(1f))
        if (checked) {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFEFF3F8),
                    modifier = Modifier.menuAnchor()
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp).clickable { if (enabled) expanded = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(soundType.emoji, fontSize = 13.sp)
                        Text(soundType.label, fontSize = 11.sp, color = DClrDark, maxLines = 1)
                        Icon(Icons.Default.ArrowDropDown, null, tint = DClrGray, modifier = Modifier.size(16.dp))
                    }
                }
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    SoundType.values().forEach { s ->
                        DropdownMenuItem(
                            text = { Text("${s.emoji}  ${s.label}", fontSize = 13.sp) },
                            onClick = { onSoundTypeChange(s); expanded = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatRow(checked: Boolean, enabled: Boolean, context: Context, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = DClrWhite, checkedTrackColor = DClrTeal)
        )
        Spacer(Modifier.width(10.dp))
        Text("Floating Stopwatch", fontSize = 14.sp, color = if (enabled) DClrDark else DClrGray, modifier = Modifier.weight(1f))
        if (!hasOverlayPermission(context)) {
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }) {
                Text("Grant", fontSize = 12.sp, color = DClrAmber)
            }
        }
    }
}

@Composable
private fun BlockingCard(
    hasAccess: Boolean, hasUsage: Boolean, strictLocked: Boolean,
    chkNet: Boolean, chkSet: Boolean, chkTask: Boolean,
    chkBlockBreak: Boolean, chkStrict: Boolean, chkHideBreakClose: Boolean,
    onNetChange: (Boolean) -> Unit, onSetChange: (Boolean) -> Unit, onTaskChange: (Boolean) -> Unit,
    onBlockBreakChange: (Boolean) -> Unit, onStrictChange: (Boolean) -> Unit, onHideBreakCloseChange: (Boolean) -> Unit,
    context: Context
) {
    Surface(color = DClrSurface, shape = RoundedCornerShape(16.dp), shadowElevation = 1.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(Icons.Default.Shield, null, tint = DClrTeal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Blocking", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DClrDark)
            }

            // UsageStats banner
            if (!hasUsage) {
                PermBanner(
                    color = Color(0xFFEFF6FF),
                    tint = DClrTeal,
                    text = "Allow-list blocking needs Usage Stats",
                    btnText = "Enable",
                    onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                )
                Spacer(Modifier.height(8.dp))
            }

            // Accessibility banner
            if (!hasAccess) {
                PermBanner(
                    color = Color(0xFFFFFBEB),
                    tint = DClrAmber,
                    text = "Internet / Settings block needs Accessibility",
                    btnText = "Enable",
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )
                Spacer(Modifier.height(8.dp))
            }

            BlockSwitch("Block Internet",             chkNet,          hasAccess && !strictLocked, onNetChange)
            BlockSwitch("Block Settings & App Info",  chkSet,          hasAccess && !strictLocked, onSetChange)
            BlockSwitch("Block Task Manager",         chkTask,         hasAccess && !strictLocked, onTaskChange)
            BlockSwitch("Keep Blocking in Break",     chkBlockBreak,   hasAccess && !strictLocked, onBlockBreakChange)
            BlockSwitch("Hide Close Btn in Break",    chkHideBreakClose, hasAccess && !strictLocked, onHideBreakCloseChange)

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = Color(0xFFE2E8F0))
            Spacer(Modifier.height(8.dp))

            // Strict mode — special row
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = chkStrict,
                    onCheckedChange = onStrictChange,
                    enabled = hasAccess && !strictLocked,
                    colors = SwitchDefaults.colors(checkedThumbColor = DClrWhite, checkedTrackColor = DClrRed)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "STRICT MODE",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (chkStrict && hasAccess) DClrRed else DClrGray
                    )
                    Text("Allow list only — can't stop session", fontSize = 11.sp, color = DClrGray)
                }
            }
        }
    }
}

@Composable
private fun PermBanner(color: Color, tint: Color, text: String, btnText: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(color).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Info, null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, color = DClrDark, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Text(btnText, fontSize = 12.sp, color = tint, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BlockSwitch(title: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = DClrWhite, checkedTrackColor = DClrTeal),
            modifier = Modifier.height(28.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 14.sp, color = if (enabled) DClrDark else DClrGray)
    }
}

@Composable
private fun AllowListCard(appCount: Int, siteCount: Int, enabled: Boolean, onClick: () -> Unit) {
    Surface(color = DClrSurface, shape = RoundedCornerShape(16.dp), shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = DClrTeal, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Allow List", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DClrDark)
                Text("$appCount apps · $siteCount sites", fontSize = 12.sp, color = DClrGray)
            }
            FilledTonalButton(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFE0F7FA), contentColor = DClrTeal)
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Manage", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PermissionExplainerDialog(onGrantAccessibility: () -> Unit, onStartWithUsageStats: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = DClrSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFE0F7FA)),
                    Alignment.Center
                ) { Icon(Icons.Default.Security, null, tint = DClrTeal, modifier = Modifier.size(28.dp)) }
                Spacer(Modifier.height(10.dp))
                Text("App Blocking", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = DClrDark, textAlign = TextAlign.Center)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoBox(Color(0xFFF0FDF4), "✅  No permission needed", "Allow-list blocking (UsageStats)\nPomodoro timer · Sound · Stopwatch")
                InfoBox(Color(0xFFFFFBEB), "🔒  With Accessibility", "Internet block · Settings block\nStrict Mode")
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onGrantAccessibility,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DClrTeal)
                ) { Text("Enable Accessibility", fontWeight = FontWeight.SemiBold) }
                OutlinedButton(
                    onClick = onStartWithUsageStats,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DClrTeal)
                ) { Text("Start with Allow-List only", color = DClrTeal) }
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun InfoBox(bg: Color, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DClrDark)
        Text(body, fontSize = 12.sp, color = DClrGray, lineHeight = 16.sp)
    }
}

// ─────────────────────────────────────────
// BOTTOM SHEET
// ─────────────────────────────────────────

@Composable
fun BlocklistPickerSheet(
    onClose: () -> Unit,
    onSave: (List<String>, List<String>) -> Unit,
    initialApps: List<String>,
    initialSites: List<String>
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(1) }
    val tempApps  = remember { mutableStateListOf<String>().apply { addAll(initialApps) } }
    val tempSites = remember { mutableStateListOf<String>().apply { addAll(initialSites) } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Header
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Allow List", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DClrDark)
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFEFF3F8))) {
                Icon(Icons.Default.Close, null, tint = DClrDark, modifier = Modifier.size(16.dp))
            }
        }
        // Search
        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            placeholder = { Text("Search or add website…", color = DClrGray, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = DClrGray, modifier = Modifier.size(18.dp)) },
            shape = RoundedCornerShape(12.dp), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DClrSurface, unfocusedContainerColor = DClrSurface,
                focusedBorderColor = DClrTeal, unfocusedBorderColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        )
        // Tabs
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFEFF3F8)).padding(4.dp)) {
            listOf("Apps", "Sites", "Keywords").forEachIndexed { i, t ->
                Box(
                    Modifier.weight(1f).height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedTab == i) DClrTeal else Color.Transparent)
                        .clickable { selectedTab = i },
                    Alignment.Center
                ) { Text(t, color = if (selectedTab == i) DClrWhite else DClrGray, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        // List
        LazyColumn(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(DClrSurface).padding(8.dp)) {
            if (selectedTab == 1 && searchQuery.isNotEmpty()) {
                if (!tempSites.any { it.contains(searchQuery, ignoreCase = true) }) {
                    val url = if (searchQuery.contains(".")) searchQuery else "$searchQuery.com"
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFEFF3F8)), Alignment.Center) {
                                Text(url.first().uppercase(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DClrTeal)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(url, modifier = Modifier.weight(1f), color = DClrDark, fontSize = 14.sp)
                            FilledTonalButton(
                                onClick = { tempSites.add(url); searchQuery = "" },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFE0F7FA), contentColor = DClrTeal),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) { Text("Add", fontSize = 12.sp) }
                        }
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                    }
                }
            }
            if (selectedTab == 1) {
                items(tempSites.size) { i ->
                    val site = tempSites[i]
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage("https://www.google.com/s2/favicons?domain=$site&sz=128", null, Modifier.size(38.dp).clip(CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(site, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DClrDark)
                            Text("0 visits", color = DClrGray, fontSize = 11.sp)
                        }
                        IconButton(onClick = { tempSites.remove(site) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.RemoveCircleOutline, null, tint = DClrRed, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF8FAFC))
                }
            }
            if (selectedTab == 0) {
                items(tempApps.size) { i ->
                    val app = tempApps[i]
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFE0F7FA)), Alignment.Center) {
                            Icon(Icons.Default.Android, null, tint = DClrTeal, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(app, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DClrDark, modifier = Modifier.weight(1f))
                        IconButton(onClick = { tempApps.remove(app) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.RemoveCircleOutline, null, tint = DClrRed, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF8FAFC))
                }
            }
        }
        // Save
        Button(
            onClick = { onSave(tempApps, tempSites) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DClrTeal),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Save", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
    }
}

// ─────────────────────────────────────────
// HELPER COMPOSABLES
// ─────────────────────────────────────────

@Composable
fun TimerSetupRow(label: String, value: Int, minVal: Int, maxVal: Int, step: Int, enabled: Boolean, onValueChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = if (enabled) DClrDark else DClrGray, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            IconButton(
                onClick = { if (enabled && value > minVal) onValueChange(value - step) },
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFEFF3F8))
            ) { Icon(Icons.Default.Remove, null, tint = if (enabled) DClrDark else DClrGray, modifier = Modifier.size(16.dp)) }
            Text(
                "$value",
                fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.width(48.dp), textAlign = TextAlign.Center, color = DClrDark
            )
            IconButton(
                onClick = { if (enabled && value < maxVal) onValueChange(value + step) },
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFEFF3F8))
            ) { Icon(Icons.Default.Add, null, tint = if (enabled) DClrDark else DClrGray, modifier = Modifier.size(16.dp)) }
        }
    }
}
