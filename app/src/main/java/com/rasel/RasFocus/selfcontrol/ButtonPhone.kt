package com.rasel.RasFocus.selfcontrol

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PhoneLocked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import android.app.AlarmManager
import android.app.PendingIntent
import com.google.gson.Gson

// ─────────────────────────────────────────────────────────────────────────────
// Colors
// ─────────────────────────────────────────────────────────────────────────────
val BpTealMid    = Color(0xFF00897B)
val BpTealDark   = Color(0xFF00695C)
val BpTealAccent = Color(0xFF1DE9B6)
val BpGrayBg     = Color(0xFFF5F5F5)
val BpTextDark   = Color(0xFF212121)
val BpTextGray   = Color(0xFF757575)
val BpRedAccent  = Color(0xFFE53935)

// ─────────────────────────────────────────────────────────────────────────────
// Enums
// ─────────────────────────────────────────────────────────────────────────────
enum class BpScreen       { SETUP, RUNNING, UNLOCK, LOCK_DETAIL, ALLOW_LIST }
enum class BpLockMode     { SELF_CONTROL, PARENTS_CONTROL, LONG_TEXT }
enum class BpUnlockMode   { SELF, PARENTS }
enum class BpPhoneOption  { COMPLETE, CUSTOMIZE }
enum class BpAllowTab     { APPS, WEBSITES }

// ─────────────────────────────────────────────────────────────────────────────
// Data classes
// ─────────────────────────────────────────────────────────────────────────────
data class BpAppInfo(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable?
)

data class ButtonPhoneSession(
    val endTimeMs: Long,
    val unlockMode: BpUnlockMode,
    val parentPassword: String,
    val requireLongText: Boolean,
    val allowedPackages: List<String>,
    val allowedWebsites: List<String>,
    val blockInternet: Boolean
)

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────
val CHROME_PACKAGES = setOf(
    "com.android.chrome", "com.chrome.beta", "com.chrome.dev",
    "com.chrome.canary", "org.chromium.chrome",
    "com.google.android.apps.chrome"
)

const val LONG_UNLOCK_TEXT =
    "I acknowledge that I set this focus session to improve my productivity. " +
    "Unlocking early means I am choosing distraction over my goals. " +
    "I understand that consistent focus builds habits and leads to long-term success. " +
    "I am committed to completing my session and will return to my work with full attention."

object BpC {
    const val PREFS         = "bp_prefs_v2"
    const val KEY_BREAK_END = "bp_break_end"
    const val KEY_ALLOWED   = "bp_allowed_pkgs"
    const val CHANNEL_ID    = "bp_blocking_channel"
    const val NOTIF_ID      = 9998
    val DEFAULT_ALLOWED = setOf(
        "com.android.dialer", "com.google.android.dialer",
        "com.android.mms", "com.google.android.apps.messaging",
        "com.android.contacts", "com.android.settings",
        "com.android.clock", "com.google.android.deskclock",
        "com.android.calculator2", "com.google.android.calculator"
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// BpPrefs
// ─────────────────────────────────────────────────────────────────────────────
object BpPrefs {
    private const val PREFS       = "bp_session_v1"
    private const val KEY_SESSION = "session_json"
    private val gson              = Gson()

    fun save(context: Context, session: ButtonPhoneSession) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SESSION, gson.toJson(session)).apply()
    }
    fun load(context: Context): ButtonPhoneSession? {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SESSION, null) ?: return null
        return try { gson.fromJson(json, ButtonPhoneSession::class.java) }
        catch (_: Exception) { null }
    }
    fun clear(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    fun isActive(context: Context): Boolean {
        val s = load(context) ?: return false
        return System.currentTimeMillis() < s.endTimeMs
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PomPrefs
// ─────────────────────────────────────────────────────────────────────────────
object PomPrefs {
    private const val PREFS       = "pom_prefs_v1"
    private const val KEY_COUNT   = "pom_count"
    private const val KEY_SESSION = "pom_session_ms"
    private const val KEY_BREAK   = "pom_break_ms"
    private const val KEY_END     = "pom_end_ms"

    fun save(context: Context, count: Int, sessionMs: Long, breakMs: Long, endMs: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_COUNT, count).putLong(KEY_SESSION, sessionMs)
            .putLong(KEY_BREAK, breakMs).putLong(KEY_END, endMs).apply()
    }
    fun isActive(context: Context): Boolean {
        val end = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_END, 0L)
        return end > System.currentTimeMillis()
    }
    fun sessions(context: Context): Triple<Int, Long, Long> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Triple(p.getInt(KEY_COUNT, 0), p.getLong(KEY_SESSION, 0L), p.getLong(KEY_BREAK, 0L))
    }
    fun clear(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper functions
// ─────────────────────────────────────────────────────────────────────────────
fun launchAppReliably(context: Context, pkg: String) {
    try {
        val i = context.packageManager.getLaunchIntentForPackage(pkg)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) }
            ?: return
        context.startActivity(i)
    } catch (_: Exception) {}
}

fun promptInternetPanel(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_WIFI_SETTINGS)
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
    } catch (_: Exception) {}
}

fun schedulePomodoroAlarms(context: Context, count: Int, sessionMs: Long, breakMs: Long) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    var t = System.currentTimeMillis()
    for (i in 0 until count) {
        t += sessionMs
        val pi = PendingIntent.getBroadcast(
            context, 1000 + i,
            Intent("com.rasel.RasFocus.POMODORO_ALARM").putExtra("index", i),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        else
            am.setExact(AlarmManager.RTC_WAKEUP, t, pi)
        if (i < count - 1) t += breakMs
    }
}

fun cancelPomodoroAlarms(context: Context, sessions: Triple<Int, Long, Long>) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    for (i in 0 until sessions.first)
        am.cancel(PendingIntent.getBroadcast(
            context, 1000 + i,
            Intent("com.rasel.RasFocus.POMODORO_ALARM"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))
}

// ─────────────────────────────────────────────────────────────────────────────
// BpOverlayService alias
// ─────────────────────────────────────────────────────────────────────────────
typealias BpOverlayService = BpBlockingService

// ─────────────────────────────────────────────────────────────────────────────
// BpLauncherScreen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun BpLauncherScreen(
    session: ButtonPhoneSession,
    onSessionEnd: () -> Unit = {},
    onAppLaunched: (String) -> Unit = {},
    onHideOverlay: () -> Unit = {}
) {
    val context = LocalContext.current
    val pkgs = remember(session) { (session.allowedPackages + BpC.DEFAULT_ALLOWED).distinct() }
    Box(
        Modifier.fillMaxSize().background(Color(0xFF0A1628)),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
            Text("\uD83D\uDCF5 Focus Mode", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Allowed apps only", color = Color.White.copy(0.6f), fontSize = 13.sp)
            Spacer(Modifier.height(32.dp))
            pkgs.forEach { pkg ->
                val launchIntent = remember(pkg) { context.packageManager.getLaunchIntentForPackage(pkg) }
                if (launchIntent != null) {
                    val appName = remember(pkg) {
                        try { context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(pkg, 0)).toString()
                        } catch (_: Exception) { pkg }
                    }
                    Button(
                        onClick = { onAppLaunched(pkg) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BpTealMid)
                    ) { Text(appName, color = Color.White, fontWeight = FontWeight.SemiBold) }
                }
            }
            Spacer(Modifier.height(32.dp))
            OutlinedButton(onClick = onSessionEnd, Modifier.fillMaxWidth()) {
                Text("End Session", color = BpRedAccent)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI: FocusLauncherCard
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun FocusLauncherCard(onSessionStart: () -> Unit) {
    var showSetup by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { showSetup = true },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BpTealMid),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).background(Color.White, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PhoneLocked, contentDescription = null, tint = BpTealDark, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Button Phone Mode", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                Spacer(Modifier.height(2.dp))
                Text("Lock yourself to minimal apps only", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
        }
    }
    if (showSetup) {
        BpSetupDialog(onDismiss = { showSetup = false }, onSessionStart = { showSetup = false; onSessionStart() })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI: TakeABreakCard
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TakeABreakCard(onSessionStart: () -> Unit = {}) {
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var isActive by remember { mutableStateOf(BpPrefs.isActive(context)) }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { showDialog = true },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isActive) BpTealMid else Color(0xFFE8D5F5)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).background(if (isActive) Color.White.copy(0.15f) else BpTealMid.copy(0.12f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text("☕", fontSize = 24.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isActive) "Session Active" else "Button Phone Mode", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (isActive) Color.White else BpTextDark)
                Text(if (isActive) "চলছে — tap করে বন্ধ বা পরিবর্তন করো" else "Custom break / Pomodoro timer", fontSize = 12.sp, color = if (isActive) Color.White.copy(0.75f) else BpTextDark.copy(0.65f))
            }
            Icon(if (isActive) Icons.Default.Timer else Icons.Default.ChevronRight, contentDescription = null, tint = if (isActive) BpTealAccent else BpTextGray)
        }
    }
    if (showDialog) {
        BpBreakDialog(
            onDismiss = { showDialog = false },
            onStarted = { isActive = true; showDialog = false; onSessionStart() },
            onStopped = { isActive = false; showDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI: BpSetupDialog
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BpSetupDialog(onDismiss: () -> Unit, onSessionStart: () -> Unit) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(BpScreen.SETUP) }
    var lockMode by remember { mutableStateOf(BpLockMode.SELF_CONTROL) }
    var phoneOption by remember { mutableStateOf(BpPhoneOption.COMPLETE) }
    var lockModeExpanded by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf("0") }
    var hours by remember { mutableStateOf("0") }
    var minutes by remember { mutableStateOf("30") }
    var parentPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var blockInternet by remember { mutableStateOf(false) }
    var allowedPkgs by remember { mutableStateOf(BpC.DEFAULT_ALLOWED.toSet()) }
    var allowedWebs by remember { mutableStateOf(setOf<String>()) }
    val unlockMode by remember { derivedStateOf { if (lockMode == BpLockMode.PARENTS_CONTROL) BpUnlockMode.PARENTS else BpUnlockMode.SELF } }
    val requireLongText by remember { derivedStateOf { lockMode == BpLockMode.LONG_TEXT } }

    Dialog(onDismissRequest = { if (screen == BpScreen.SETUP) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = screen == BpScreen.SETUP)) {
        Box(Modifier.fillMaxSize()) {
            when (screen) {
                BpScreen.SETUP -> Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState()).padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, null, tint = BpTealDark) }
                        Text("Button Phone Setup", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = BpTealDark)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BpTealDark)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(BpPhoneOption.COMPLETE to ("Complete\nButton Phone" to "শুধু essential apps"), BpPhoneOption.CUSTOMIZE to ("Customize\nButton" to "Apps নিজে বেছে নাও")).forEach { (opt, texts) ->
                            val (title, sub) = texts; val sel = phoneOption == opt
                            Card(Modifier.weight(1f).clickable { phoneOption = opt }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (sel) BpTealMid else BpGrayBg), elevation = CardDefaults.cardElevation(if (sel) 4.dp else 0.dp)) {
                                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(if (opt == BpPhoneOption.COMPLETE) Icons.Default.PhoneLocked else Icons.Default.Tune, null, tint = if (sel) Color.White else BpTealDark, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.height(8.dp)); Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (sel) Color.White else BpTealDark, textAlign = TextAlign.Center)
                                    Spacer(Modifier.height(4.dp)); Text(sub, fontSize = 10.sp, color = if (sel) Color.White.copy(alpha = 0.75f) else BpTextGray, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp)); Text("Lock Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BpTealDark); Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(expanded = lockModeExpanded, onExpandedChange = { lockModeExpanded = it }) {
                        OutlinedTextField(value = when (lockMode) { BpLockMode.SELF_CONTROL -> "Self Control"; BpLockMode.PARENTS_CONTROL -> "Parents Control"; BpLockMode.LONG_TEXT -> "Long Text" }, onValueChange = {}, readOnly = true, label = { Text("Unlock type select করুন") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = lockModeExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BpTealAccent))
                        ExposedDropdownMenu(expanded = lockModeExpanded, onDismissRequest = { lockModeExpanded = false }) {
                            DropdownMenuItem(text = { Column { Text("Self Control", fontWeight = FontWeight.SemiBold); Text("নিজেই unlock করতে পারবে", fontSize = 11.sp, color = BpTextGray) } }, onClick = { lockMode = BpLockMode.SELF_CONTROL; lockModeExpanded = false })
                            DropdownMenuItem(text = { Column { Text("Parents Control", fontWeight = FontWeight.SemiBold); Text("শুধু parents password দিয়ে unlock", fontSize = 11.sp, color = BpTextGray) } }, onClick = { lockMode = BpLockMode.PARENTS_CONTROL; lockModeExpanded = false })
                            DropdownMenuItem(text = { Column { Text("Long Text", fontWeight = FontWeight.SemiBold); Text("~200 words টাইপ করলে unlock হবে", fontSize = 11.sp, color = BpTextGray) } }, onClick = { lockMode = BpLockMode.LONG_TEXT; lockModeExpanded = false })
                        }
                    }
                    Spacer(Modifier.height(24.dp)); Text("Extra Options", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BpTealDark); Spacer(Modifier.height(8.dp))
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = BpGrayBg)) {
                        Row(Modifier.fillMaxWidth().clickable { blockInternet = !blockInternet }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WifiOff, null, tint = BpTealDark, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text("Block Internet", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = BpTealDark); Text("Session চলাকালীন internet বন্ধ থাকবে", fontSize = 11.sp, color = BpTextGray) }
                            Switch(checked = blockInternet, onCheckedChange = { blockInternet = it }, colors = SwitchDefaults.colors(checkedTrackColor = BpTealAccent))
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Button(onClick = { screen = BpScreen.LOCK_DETAIL }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = BpTealMid)) { Text("Next →", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White) }
                }
                BpScreen.LOCK_DETAIL -> Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState()).padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { screen = BpScreen.SETUP }) { Icon(Icons.Default.ArrowBack, null, tint = BpTealDark) }
                        Text(when (lockMode) { BpLockMode.SELF_CONTROL -> "Set Duration"; BpLockMode.PARENTS_CONTROL -> "Set Password"; BpLockMode.LONG_TEXT -> "Long Text Mode" }, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = BpTealDark)
                    }
                    Spacer(Modifier.height(24.dp))
                    when (lockMode) {
                        BpLockMode.SELF_CONTROL -> {
                            Text("Duration", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BpTealDark); Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Days" to days, "Hours" to hours, "Mins" to minutes).forEachIndexed { i, (lbl, v) ->
                                    OutlinedTextField(v, { if (i==0) days=it else if (i==1) hours=it else minutes=it }, label = { Text(lbl) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BpTealAccent))
                                }
                            }
                        }
                        BpLockMode.PARENTS_CONTROL -> {
                            Text("Unlock Password", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BpTealDark); Spacer(Modifier.height(8.dp))
                            OutlinedTextField(parentPass, { parentPass = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BpTealAccent)); Spacer(Modifier.height(12.dp))
                            OutlinedTextField(confirmPass, { confirmPass = it }, label = { Text("Confirm Password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BpTealAccent))
                        }
                        BpLockMode.LONG_TEXT -> {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = BpTealMid.copy(alpha = 0.08f))) {
                                Column(Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.EditNote, null, tint = BpTealMid, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(10.dp)); Text("Long Text Unlock Active", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BpTealDark) }
                                    Spacer(Modifier.height(12.dp)); Text("Unlock করতে চাইলে ~200 words এর একটি paragraph টাইপ করতে হবে।", fontSize = 13.sp, color = BpTextGray, lineHeight = 20.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Button(onClick = { if (lockMode == BpLockMode.PARENTS_CONTROL && parentPass != confirmPass) return@Button; screen = BpScreen.ALLOW_LIST }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = BpTealMid)) {
                        Text(if (phoneOption == BpPhoneOption.COMPLETE) "Next: Review Apps →" else "Next: Choose Apps →", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    }
                }
                BpScreen.ALLOW_LIST -> BpAllowListScreen(
                    selectedPkgs = allowedPkgs, selectedWebs = allowedWebs,
                    isComplete = phoneOption == BpPhoneOption.COMPLETE,
                    onPkgsChanged = { allowedPkgs = it }, onWebsChanged = { allowedWebs = it },
                    onBack = { screen = BpScreen.LOCK_DETAIL },
                    onStart = {
                        if (!hasUsageStatsPermission(context)) {
                            Toast.makeText(context, "Please enable Usage Access", Toast.LENGTH_SHORT).show()
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            return@BpAllowListScreen
                        }
                        val totalMs = if (unlockMode == BpUnlockMode.PARENTS) 365L * 24 * 3600 * 1000
                            else (days.toLongOrNull() ?: 0L) * 86_400_000L + (hours.toLongOrNull() ?: 0L) * 3_600_000L + (minutes.toLongOrNull() ?: 30L) * 60_000L
                        val endTime = System.currentTimeMillis() + totalMs
                        // take_rest.kt এর মতো — SharedPreferences এ save করো তারপর BlockingService start
                        val finalPkgs = (if (phoneOption == BpPhoneOption.COMPLETE) BpC.DEFAULT_ALLOWED else allowedPkgs + BpC.DEFAULT_ALLOWED).toSet()
                        val prefs = (context as? android.app.Activity)?.getPreferences(Context.MODE_PRIVATE)
                            ?: context.getSharedPreferences(BpC.PREFS, Context.MODE_PRIVATE)
                        prefs.edit()
                            .putLong(BpC.KEY_BREAK_END, endTime)
                            .putString(BpC.KEY_ALLOWED, finalPkgs.joinToString(","))
                            .apply()
                        if (blockInternet) promptInternetPanel(context)
                        BpPrefs.save(context, ButtonPhoneSession(
                            endTimeMs = endTime, unlockMode = unlockMode, parentPassword = parentPass,
                            requireLongText = requireLongText,
                            allowedPackages = finalPkgs.toList(),
                            allowedWebsites = allowedWebs.toList(), blockInternet = blockInternet
                        ))
                        val svcIntent = Intent(context, BpBlockingService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(svcIntent)
                        else context.startService(svcIntent)
                        onSessionStart()
                    }
                )
                BpScreen.RUNNING -> {
                    // Session is running - show running state info
                    Box(Modifier.fillMaxSize().background(Color(0xFF0A1628)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Text("📵 Session Running", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(16.dp))
                            Text("Focus mode is active.", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                            Spacer(Modifier.height(24.dp))
                            OutlinedButton(onClick = onDismiss) { Text("Close", color = BpRedAccent) }
                        }
                    }
                }
                BpScreen.UNLOCK -> {
                    // Unlock confirmation screen
                    Box(Modifier.fillMaxSize().background(Color(0xFF0A1628)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Text("🔓 Unlock Session", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { screen = BpScreen.SETUP }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = BpTealMid)) {
                                Text("Go Back", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI: BpAllowListScreen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BpAllowListScreen(selectedPkgs: Set<String>, selectedWebs: Set<String>, isComplete: Boolean = false, onPkgsChanged: (Set<String>) -> Unit, onWebsChanged: (Set<String>) -> Unit, onBack: () -> Unit, onStart: () -> Unit) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(BpAllowTab.APPS) }
    var webInput by remember { mutableStateOf("") }
    val installedApps = remember {
        val pm = context.packageManager
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val dialerPkg = telecomManager?.defaultDialerPackage
        val smsPkg = Telephony.Sms.getDefaultSmsPackage(context)
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 || it.packageName == dialerPkg || it.packageName == smsPkg }
            .map { BpAppInfo(it.packageName, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it)) }
            .sortedBy { it.appName }
    }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(BpTealMid, BpTealDark))).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }; Spacer(Modifier.width(8.dp))
                Column { Text("Allow List", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White); Text(if (isComplete) "Default apps fixed — শুধু এরাই কাজ করবে" else "Apps/sites বেছে নাও", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f)) }
            }
        }
        Row(Modifier.fillMaxWidth().background(BpGrayBg).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BpTabBtn("📱 Apps (${selectedPkgs.size})", activeTab == BpAllowTab.APPS, Modifier.weight(1f)) { activeTab = BpAllowTab.APPS }
            BpTabBtn("🌐 Websites (${selectedWebs.size})", activeTab == BpAllowTab.WEBSITES, Modifier.weight(1f)) { activeTab = BpAllowTab.WEBSITES }
        }
        when (activeTab) {
            BpAllowTab.APPS -> LazyColumn(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                items(installedApps) { app ->
                    val selected = app.packageName in selectedPkgs; val isDefaultApp = app.packageName in BpC.DEFAULT_ALLOWED; val isLocked = isComplete || isDefaultApp
                    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).then(if (!isLocked) Modifier.clickable { onPkgsChanged(if (selected) selectedPkgs - app.packageName else selectedPkgs + app.packageName) } else Modifier), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (selected) BpTealMid.copy(alpha = 0.1f) else Color.White), elevation = CardDefaults.cardElevation(if (selected) 2.dp else 1.dp)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIconImage(drawable = app.icon, modifier = Modifier.size(40.dp)); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(app.appName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (isLocked && !selected) BpTextGray else BpTealDark); Text(app.packageName, fontSize = 10.sp, color = BpTextGray, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            Icon(if (isLocked) (if (selected) Icons.Default.Lock else Icons.Default.LockOpen) else (if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked), null, tint = if (isLocked) (if (selected) BpTealMid else BpTextGray.copy(alpha = 0.4f)) else (if (selected) BpTealMid else BpTextGray), modifier = Modifier.size(if (isLocked) 20.dp else 22.dp))
                        }
                    }
                }
            }
            BpAllowTab.WEBSITES -> Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = webInput, onValueChange = { webInput = it }, label = { Text("Website (e.g. google.com)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BpTealMid))
                    Button(onClick = { val site = webInput.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www."); if (site.isNotEmpty()) { onWebsChanged(selectedWebs + site); webInput = "" } }, modifier = Modifier.height(56.dp).align(Alignment.CenterVertically), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = BpTealMid)) { Icon(Icons.Default.Add, null, tint = Color.White) }
                }
                if (selectedWebs.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🌐", fontSize = 40.sp); Spacer(Modifier.height(8.dp)); Text("কোনো website add করা হয়নি", color = BpTextGray); Text("উপরে লিখে + চাপুন", fontSize = 12.sp, color = BpTextGray) } }
                } else {
                    LazyColumn(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        items(selectedWebs.toList()) { site ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = BpTealMid.copy(alpha = 0.1f))) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Language, null, tint = BpTealMid, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text(site, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = BpTealDark)
                                    IconButton(onClick = { onWebsChanged(selectedWebs - site) }) { Icon(Icons.Default.Close, null, tint = BpRedAccent, modifier = Modifier.size(18.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = BpTealDark)) { Icon(Icons.Default.Lock, null, tint = Color.White); Spacer(Modifier.width(10.dp)); Text("START FOCUS SESSION", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White) }
        }
    }
}

// BpLauncherScreen সরানো হয়েছে — overlay এখন BlockingService এর ভেতরে
// take_rest.kt এর মতো pure View দিয়ে buildBpOverlayUI() এ render হয়

// ─────────────────────────────────────────────────────────────────────────────
// UI: BpBreakDialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BpBreakDialog(onDismiss: () -> Unit, onStarted: () -> Unit, onStopped: () -> Unit) {
    val context = LocalContext.current; var tab by remember { mutableStateOf(0) }
    val isBreakActive = BpPrefs.isActive(context); val isPomActive = PomPrefs.isActive(context)
    var tbDays by remember { mutableStateOf(0) }; var tbHours by remember { mutableStateOf(0) }; var tbMins by remember { mutableStateOf(25) }
    var pomSession by remember { mutableStateOf(25) }; var pomBreak by remember { mutableStateOf(5) }; var pomCount by remember { mutableStateOf(4) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.90f).padding(horizontal = 14.dp).background(Color(0xFF0F1724), RoundedCornerShape(28.dp))) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(BpTealMid, BpTealDark)), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onDismiss, Modifier.size(36.dp)) { Icon(Icons.Default.Close, null, tint = Color.White) }; Spacer(Modifier.width(10.dp)); Column { Text("Button Phone Mode", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White); Text("Session শুরু করো", fontSize = 12.sp, color = Color.White.copy(0.7f)) } }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("☕  Break", "🍅  Pomodoro").forEachIndexed { i, label ->
                        Button(onClick = { tab = i }, Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = if (tab == i) BpTealMid else Color.White.copy(0.08f), contentColor = Color.White), elevation = ButtonDefaults.buttonElevation(if (tab == i) 4.dp else 0.dp)) { Text(label, fontSize = 13.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) }
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    AnimatedContent(tab, label = "tab") { t -> when (t) { 0 -> BpBreakContent(tbDays, { tbDays = it }, tbHours, { tbHours = it }, tbMins, { tbMins = it }); 1 -> BpPomodoroContent(pomSession, { pomSession = it }, pomBreak, { pomBreak = it }, pomCount, { pomCount = it }) } }
                }
                Column(Modifier.padding(16.dp)) {
                    if (isBreakActive || isPomActive) {
                        OutlinedButton(
                            onClick = {
                                if (isPomActive) cancelPomodoroAlarms(context, PomPrefs.sessions(context))
                                PomPrefs.clear(context); BpPrefs.clear(context)
                                // take_rest.kt এর মতো — prefs clear করো, service stop করো
                                context.getSharedPreferences(BpC.PREFS, Context.MODE_PRIVATE).edit()
                                    .putLong(BpC.KEY_BREAK_END, 0L).apply()
                                context.stopService(Intent(context, BpBlockingService::class.java))
                                onStopped()
                            },
                            Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(2.dp, Color(0xFFE53935))
                        ) { Icon(Icons.Default.Stop, null, tint = Color(0xFFE53935)); Spacer(Modifier.width(8.dp)); Text("Session বন্ধ করো", color = Color(0xFFE53935), fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = {
                            // Permission check — Usage Access only (overlay permission লাগবে না)
                            if (!hasUsageStatsPermission(context)) {
                                Toast.makeText(context, "Please enable Usage Access", Toast.LENGTH_SHORT).show()
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                return@Button
                            }
                            when (tab) {
                                0 -> {
                                    if (tbDays == 0 && tbHours == 0 && tbMins == 0) { Toast.makeText(context, "Duration সেট করুন!", Toast.LENGTH_SHORT).show(); return@Button }
                                    val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_MONTH, tbDays); add(java.util.Calendar.HOUR_OF_DAY, tbHours); add(java.util.Calendar.MINUTE, tbMins) }
                                    // take_rest.kt এর মতো — prefs save → BlockingService start
                                    context.getSharedPreferences(BpC.PREFS, Context.MODE_PRIVATE).edit()
                                        .putLong(BpC.KEY_BREAK_END, cal.timeInMillis)
                                        .putString(BpC.KEY_ALLOWED, BpC.DEFAULT_ALLOWED.joinToString(","))
                                        .apply()
                                    BpPrefs.save(context, ButtonPhoneSession(endTimeMs = cal.timeInMillis, unlockMode = BpUnlockMode.SELF, parentPassword = "", requireLongText = false, allowedPackages = BpC.DEFAULT_ALLOWED.toList(), allowedWebsites = emptyList(), blockInternet = false))
                                    val svcIntent = Intent(context, BpBlockingService::class.java)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(svcIntent)
                                    else context.startService(svcIntent)
                                    onStarted()
                                }
                                1 -> {
                                    if (pomSession == 0 || pomBreak == 0 || pomCount == 0) { Toast.makeText(context, "Pomodoro parameters সেট করুন!", Toast.LENGTH_SHORT).show(); return@Button }
                                    val sessionMs = pomSession * 60_000L; val breakMs = pomBreak * 60_000L
                                    val totalMs = (pomCount.toLong() * sessionMs) + ((pomCount - 1).toLong() * breakMs)
                                    val endMs = System.currentTimeMillis() + totalMs
                                    // take_rest.kt এর মতো — prefs save → BlockingService start
                                    context.getSharedPreferences(BpC.PREFS, Context.MODE_PRIVATE).edit()
                                        .putLong(BpC.KEY_BREAK_END, endMs)
                                        .putString(BpC.KEY_ALLOWED, BpC.DEFAULT_ALLOWED.joinToString(","))
                                        .apply()
                                    BpPrefs.save(context, ButtonPhoneSession(endTimeMs = endMs, unlockMode = BpUnlockMode.SELF, parentPassword = "", requireLongText = false, allowedPackages = BpC.DEFAULT_ALLOWED.toList(), allowedWebsites = emptyList(), blockInternet = false))
                                    PomPrefs.save(context, pomCount, sessionMs, breakMs, endMs)
                                    schedulePomodoroAlarms(context, pomCount, sessionMs, breakMs)
                                    val svcIntent = Intent(context, BpBlockingService::class.java)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(svcIntent)
                                    else context.startService(svcIntent)
                                    onStarted()
                                }
                            }
                        },
                        Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (tab == 1) Color(0xFFE65100) else BpTealMid)
                    ) { Icon(if (tab == 1) Icons.Default.Timer else Icons.Default.Coffee, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text(if (tab == 1) "Pomodoro শুরু করো" else "Break শুরু করো", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun BpBreakContent(days: Int, onDays: (Int) -> Unit, hours: Int, onHours: (Int) -> Unit, mins: Int, onMins: (Int) -> Unit) {
    val display = buildString { if (days > 0) append("${days}d "); if (hours > 0) append("${hours}h "); if (mins > 0) append("${mins}m"); if (isEmpty()) append("0m") }.trim()
    Column {
        Box(Modifier.fillMaxWidth().background(BpTealMid.copy(0.15f), RoundedCornerShape(16.dp)).border(1.dp, BpTealAccent.copy(0.4f), RoundedCornerShape(16.dp)).padding(vertical = 18.dp), contentAlignment = Alignment.Center) { Text(display, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = BpTealAccent, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(16.dp)); Text("Quick Select", fontSize = 12.sp, color = BpTextGray, fontWeight = FontWeight.Medium); Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(listOf(0,0,5,"5m"), listOf(0,0,15,"15m"), listOf(0,0,25,"25m"), listOf(0,0,30,"30m"), listOf(0,1,0,"1h")).forEach { preset ->
                val d = preset[0] as Int; val h = preset[1] as Int; val m = preset[2] as Int; val lbl = preset[3] as String; val sel = days==d && hours==h && mins==m
                Box(Modifier.weight(1f).background(if (sel) BpTealMid else Color.White.copy(0.07f), RoundedCornerShape(10.dp)).border(1.dp, if (sel) BpTealAccent else Color.Transparent, RoundedCornerShape(10.dp)).clickable { onDays(d); onHours(h); onMins(m) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Text(lbl, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else BpTextGray) }
            }
        }
        Spacer(Modifier.height(16.dp)); Text("Custom Duration", fontSize = 12.sp, color = BpTextGray); Spacer(Modifier.height(8.dp))
        BpSlider("Days", days, 0, 7) { onDays(it) }; BpSlider("Hours", hours, 0, 23) { onHours(it) }; BpSlider("Minutes", mins, 0, 59) { onMins(it) }
    }
}

@Composable
private fun BpPomodoroContent(sessionMins: Int, onSession: (Int) -> Unit, breakMins: Int, onBreak: (Int) -> Unit, sessions: Int, onSessions: (Int) -> Unit) {
    val total = (sessionMins * sessions) + (breakMins * (sessions - 1))
    Column {
        Box(Modifier.fillMaxWidth().background(Color(0xFFE65100).copy(0.15f), RoundedCornerShape(16.dp)).border(1.dp, Color(0xFFE65100).copy(0.4f), RoundedCornerShape(16.dp)).padding(vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("🍅" to "${sessions}x Sessions", "⏱" to "${sessionMins}m Focus", "☕" to "${breakMins}m Break", "🕐" to "${total}m Total").forEach { (emoji, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(emoji, fontSize = 20.sp); Text(label, fontSize = 12.sp, color = Color(0xFFFF8C00), fontWeight = FontWeight.Bold) }
                }
            }
        }
        Spacer(Modifier.height(16.dp)); Text("Preset", fontSize = 12.sp, color = BpTextGray); Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(listOf(25,5,4,"Classic"), listOf(50,10,3,"Deep"), listOf(15,3,6,"Short")).forEach { preset ->
                val s = preset[0] as Int; val b = preset[1] as Int; val n = preset[2] as Int; val lbl = preset[3] as String; val sel = sessionMins==s && breakMins==b && sessions==n
                Box(Modifier.weight(1f).background(if (sel) Color(0xFFE65100) else Color.White.copy(0.07f), RoundedCornerShape(10.dp)).clickable { onSession(s); onBreak(b); onSessions(n) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(lbl, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White); Text("${s}m/${b}m", fontSize = 10.sp, color = Color.White.copy(0.7f)) }
                }
            }
        }
        Spacer(Modifier.height(16.dp)); Text("Custom", fontSize = 12.sp, color = BpTextGray); Spacer(Modifier.height(8.dp))
        BpSlider("Focus(m)", sessionMins, 5, 90) { onSession(it) }; BpSlider("Break(m)", breakMins, 1, 30) { onBreak(it) }; BpSlider("Sessions", sessions, 1, 12) { onSessions(it) }
    }
}

@Composable
private fun BpSlider(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = BpTextGray, modifier = Modifier.width(76.dp))
        Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = min.toFloat()..max.toFloat(), steps = (max - min - 1).coerceAtLeast(0), modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = BpTealMid, activeTrackColor = BpTealAccent))
        Text("$value", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun BpUnlockScreen(session: ButtonPhoneSession, onUnlocked: () -> Unit, onBack: () -> Unit) {
    var passInput by remember { mutableStateOf("") }; var longInput by remember { mutableStateOf("") }; var errMsg by remember { mutableStateOf("") }
    BackHandler(enabled = true) { onBack() }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BpTealMid, BpTealDark))), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = BpTealDark) }; Text("Unlock Focus Mode", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = BpTealDark) }
                Spacer(Modifier.height(24.dp))
                when (session.unlockMode) {
                    BpUnlockMode.PARENTS -> {
                        Text("Unlock password লিখুন:", fontSize = 14.sp, color = BpTextDark); Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = passInput, onValueChange = { passInput = it; errMsg = "" }, label = { Text("Password", color = BpTextGray) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BpTealMid, unfocusedBorderColor = BpGrayBg, focusedTextColor = BpTextDark, unfocusedTextColor = BpTextDark))
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { if (passInput == session.parentPassword) onUnlocked() else errMsg = "ভুল পাসওয়ার্ড!" }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = BpTealMid)) { Text("Unlock", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White) }
                    }
                    BpUnlockMode.SELF -> {
                        if (session.requireLongText) {
                            Text("নিচের পুরো লেখাটি হুবহু টাইপ করুন:", fontSize = 14.sp, color = BpTextDark, fontWeight = FontWeight.Medium); Spacer(Modifier.height(10.dp))
                            Box(Modifier.fillMaxWidth().background(BpGrayBg, RoundedCornerShape(12.dp)).padding(14.dp)) { Text(LONG_UNLOCK_TEXT, fontSize = 12.sp, color = BpTextDark.copy(alpha = 0.8f), lineHeight = 18.sp, modifier = Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState())) }
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(value = longInput, onValueChange = { longInput = it; errMsg = "" }, placeholder = { Text("এখানে টাইপ করুন...", color = BpTextGray, fontSize = 13.sp) }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 200.dp), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BpTealMid, unfocusedBorderColor = BpGrayBg, focusedTextColor = BpTextDark, unfocusedTextColor = BpTextDark))
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { if (longInput.trim() == LONG_UNLOCK_TEXT.trim()) onUnlocked() else errMsg = "লেখা মিলছে না! হুবহু টাইপ করুন।" }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = BpTealMid)) { Text("Verify & Unlock", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White) }
                        } else {
                            Text("Focus session শেষ করতে চান?", fontSize = 16.sp, color = BpTextDark, fontWeight = FontWeight.Medium); Spacer(Modifier.height(20.dp))
                            Button(onClick = onUnlocked, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = BpTealMid)) { Text("Unlock Now", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White) }
                        }
                    }
                }
                if (errMsg.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Text(errMsg, color = BpRedAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(16.dp)); TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("← ফিরে যাও", color = BpTextGray, fontSize = 15.sp) }
            }
        }
    }
}

@Composable
private fun BpTabBtn(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(42.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = if (selected) BpTealMid else Color.White, contentColor = if (selected) Color.White else BpTextDark), elevation = ButtonDefaults.buttonElevation(if (selected) 4.dp else 0.dp)) { Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
}

// ─────────────────────────────────────────────────────────────────────────────
// Usage Access Helper — standalone functions (object এ রাখলে take_rest.kt এর
// hasUsageStatsPermission() এর সাথে conflict হবে, তাই এখানে শুধু
// requestUsageAccessPermission() রাখা হয়েছে)
// ─────────────────────────────────────────────────────────────────────────────
fun requestUsageAccessPermission(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  BP BLOCKING SERVICE — take_rest.kt এর BlockingService এর মতো
//  overlay, navigation, launching সব এখানে
// ═════════════════════════════════════════════════════════════════════════════
class BpBlockingService : android.app.Service() {

    private var thread: Thread? = null
    @Volatile private var running = false
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    @Volatile private var isOverlayShowing = false

    // Timer — remaining time প্রতি সেকেন্ডে update
    private var remainingTimeTv: TextView? = null
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            remainingTimeTv?.text = getRemainingTimeStr()
            timerHandler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: android.content.Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        startForeground(BpC.NOTIF_ID, buildBpNotification())
        if (!running) {
            running = true
            thread = Thread(::trackLoop).also { it.start() }
        }
        return START_STICKY
    }

    // ── Notification ──────────────────────────────────────
    private fun buildBpNotification(): android.app.Notification {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = android.app.NotificationChannel(
                BpC.CHANNEL_ID, "Button Phone Mode",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }
        return androidx.core.app.NotificationCompat.Builder(this, BpC.CHANNEL_ID)
            .setContentTitle("Button Phone চলছে 📵")
            .setContentText("Focus session active — apps locked")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
    }

    // ── Session active কিনা ───────────────────────────────
    private fun isSessionActive(): Boolean {
        val session = BpPrefs.load(this) ?: return false
        return System.currentTimeMillis() < session.endTimeMs
    }

    // ── Remaining time string ─────────────────────────────
    private fun getRemainingTimeStr(): String {
        val session = BpPrefs.load(this) ?: return "00:00"
        val remaining = maxOf(0L, session.endTimeMs - System.currentTimeMillis())
        val totalSecs = remaining / 1000
        val days  = totalSecs / 86400
        val hours = (totalSecs % 86400) / 3600
        val mins  = (totalSecs % 3600) / 60
        val secs  = totalSecs % 60
        return when {
            days > 0  -> "%dd %02dh %02dm".format(days, hours, mins)
            hours > 0 -> "%02d:%02d:%02d".format(hours, mins, secs)
            else      -> "%02d:%02d".format(mins, secs)
        }
    }

    // ── Allowed package কিনা ─────────────────────────────
    private fun isAllowed(pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg in BpC.DEFAULT_ALLOWED) return true
        val session = BpPrefs.load(this) ?: return false
        return pkg in session.allowedPackages
    }

    // ── Foreground package ────────────────────────────────
    private fun getForegroundPkg(usm: android.app.usage.UsageStatsManager): String? {
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 3000, now)
        val event = android.app.usage.UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND)
                last = event.packageName
        }
        return last
    }

    // ── Core tracking loop ────────────────────────────────
    private fun trackLoop() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        while (running) {
            try {
                if (!isSessionActive()) {
                    Handler(Looper.getMainLooper()).post {
                        timerHandler.removeCallbacks(timerRunnable)
                        removeOverlay()
                        BpPrefs.clear(this)
                        stopForeground(true)
                        stopSelf()
                    }
                    break
                }
                val pkg = getForegroundPkg(usm)
                if (pkg != null) {
                    val blocked = !isAllowed(pkg)
                    if (blocked && !isOverlayShowing)
                        Handler(Looper.getMainLooper()).post { showOverlay() }
                    else if (!blocked && isOverlayShowing)
                        Handler(Looper.getMainLooper()).post { removeOverlay() }
                }
                Thread.sleep(200)
            } catch (_: InterruptedException) { break
            } catch (_: Exception) { Thread.sleep(300) }
        }
    }

    // ── Overlay show ──────────────────────────────────────
    private fun showOverlay() {
        if (isOverlayShowing || !Settings.canDrawOverlays(this)) return
        val view = buildBpOverlayUI()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        @Suppress("DEPRECATION")
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type, flags, PixelFormat.TRANSLUCENT
        )
        windowManager.addView(view, params)
        overlayView = view
        isOverlayShowing = true
        timerHandler.post(timerRunnable)
    }

    // ── Overlay remove ────────────────────────────────────
    private fun removeOverlay() {
        if (!isOverlayShowing) return
        timerHandler.removeCallbacks(timerRunnable)
        remainingTimeTv = null
        overlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        overlayView = null
        isOverlayShowing = false
    }

    // ── Overlay UI — take_rest.kt এর মতো, কিন্তু BP session এর জন্য ──
    private fun buildBpOverlayUI(): View {
        val ctx = ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
        val session = BpPrefs.load(this)

        val scroll = ScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(AColor.parseColor("#0A1628"))
            isVerticalScrollBarEnabled = false
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(bpDp(20), bpDp(56), bpDp(20), bpDp(48))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        scroll.addView(root)

        // ── Clock ──
        val clockTv = bpTv(ctx, java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()), 64f, Typeface.NORMAL, AColor.WHITE, grav = Gravity.CENTER)
        val dateTv  = bpTv(ctx, java.text.SimpleDateFormat("EEE, dd MMM", java.util.Locale.getDefault()).format(java.util.Date()), 15f, color = AColor.parseColor("#A0AABB"), grav = Gravity.CENTER, bot = bpDp(20))
        // clock প্রতি মিনিটে update
        timerHandler.post(object : Runnable {
            override fun run() {
                clockTv.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                dateTv.text  = java.text.SimpleDateFormat("EEE, dd MMM", java.util.Locale.getDefault()).format(java.util.Date())
                timerHandler.postDelayed(this, 60_000)
            }
        })
        root.addView(clockTv)
        root.addView(dateTv)

        // ── Remaining time pill — take_rest.kt এর মতো ──
        val pillFrame = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, bpDp(180))
                .also { it.setMargins(bpDp(8), 0, bpDp(8), bpDp(28)) }

            addView(object : View(ctx) {
                private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.parseColor("#0F2040") }
                private val rect = RectF()
                init { setWillNotDraw(false) }
                override fun onDraw(c: Canvas) {
                    rect.set(0f, 0f, width.toFloat(), height.toFloat())
                    c.drawRoundRect(rect, height / 2f, height / 2f, bgPaint)
                }
            }.apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            })

            val inner = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            inner.addView(bpTv(ctx, "📵  Button Phone Mode", 13f, color = AColor.parseColor("#4DD0E1"), bot = bpDp(8), grav = Gravity.CENTER))

            val timeTv = TextView(ctx).apply {
                text = getRemainingTimeStr()
                textSize = 40f
                setTypeface(null, Typeface.BOLD)
                setTextColor(AColor.parseColor("#E3F2FD"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .also { it.setMargins(0, 0, 0, bpDp(4)) }
            }
            remainingTimeTv = timeTv
            inner.addView(timeTv)
            inner.addView(bpTv(ctx, "বাকি সময়", 12f, color = AColor.parseColor("#546E7A"), grav = Gravity.CENTER))
            addView(inner)
        }
        root.addView(pillFrame)

        // ── Allowed apps label ──
        root.addView(bpTv(ctx, "এই apps ব্যবহার করো", 13f, color = AColor.parseColor("#4DD0E1"), bot = bpDp(14), grav = Gravity.CENTER))

        // ── App grid — take_rest.kt এর মতো ──
        val appsContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        root.addView(appsContainer)
        loadBpAppsToView(ctx, appsContainer, session)

        // ── Websites (যদি থাকে) ──
        val sites = session?.allowedWebsites ?: emptyList()
        if (sites.isNotEmpty()) {
            root.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bpDp(24))
            })
            root.addView(bpTv(ctx, "Websites", 13f, color = AColor.parseColor("#4DD0E1"), bot = bpDp(10), grav = Gravity.CENTER))
            for (site in sites) {
                val siteRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(bpDp(16), bpDp(12), bpDp(16), bpDp(12))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .also { it.setMargins(0, 0, 0, bpDp(8)) }
                    background = android.graphics.drawable.GradientDrawable().also {
                        it.setColor(AColor.parseColor("#0F2040"))
                        it.cornerRadius = bpDp(12).toFloat()
                    }
                    setOnClickListener {
                        val url = if (site.startsWith("http")) site else "https://$site"
                        removeOverlay()
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                }
                siteRow.addView(bpTv(ctx, "🌐  $site", 14f, color = AColor.parseColor("#80DEEA")))
                root.addView(siteRow)
            }
        }

        // ── Unlock button — lock type অনুযায়ী ──
        root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bpDp(32))
        })
        val unlockBtn = object : TextView(ctx) {
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.parseColor("#1A2A3A") }
            private val rect = RectF()
            init { setWillNotDraw(false) }
            override fun onDraw(c: Canvas) {
                rect.set(0f, 0f, width.toFloat(), height.toFloat())
                c.drawRoundRect(rect, bpDp(14).toFloat(), bpDp(14).toFloat(), bgPaint)
                super.onDraw(c)
            }
        }.apply {
            text = "🔓  Unlock"
            textSize = 14f
            setTypeface(null, Typeface.NORMAL)
            setTextColor(AColor.parseColor("#546E7A"))
            gravity = Gravity.CENTER
            setPadding(bpDp(24), bpDp(14), bpDp(24), bpDp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        unlockBtn.setOnClickListener {
            if (session == null) return@setOnClickListener
            showBpUnlockDialog(ctx, session)
        }
        root.addView(unlockBtn)

        return scroll
    }

    // ── App chips লোড করো — take_rest.kt এর মতো ──
    private fun loadBpAppsToView(ctx: Context, container: LinearLayout, session: ButtonPhoneSession?) {
        val pkgs = ((session?.allowedPackages ?: emptyList()) + BpC.DEFAULT_ALLOWED).distinct()
        val pm = packageManager
        val validPkgs = pkgs.filter { try { pm.getApplicationInfo(it, 0); true } catch (_: Exception) { false } }

        if (validPkgs.isEmpty()) {
            container.addView(bpTv(ctx, "কোনো app allow করা হয়নি", 13f, color = AColor.parseColor("#546E7A"), grav = Gravity.CENTER))
            return
        }

        var row: LinearLayout? = null
        validPkgs.forEachIndexed { idx, pkg ->
            if (idx % 2 == 0) {
                row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .also { it.setMargins(0, 0, 0, bpDp(10)) }
                }
                container.addView(row)
            }
            val chip = buildBpAppChip(ctx, pkg, pm)
            chip.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.setMargins(if (idx % 2 == 0) 0 else bpDp(8), 0, 0, 0) }
            chip.setOnClickListener {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    removeOverlay()
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                } else {
                    Toast.makeText(ctx, "App খোলা যাচ্ছে না", Toast.LENGTH_SHORT).show()
                }
            }
            row?.addView(chip)
        }
        if (validPkgs.size % 2 != 0) {
            row?.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.setMargins(bpDp(8), 0, 0, 0) }
            })
        }
    }

    // ── App chip build ────────────────────────────────────
    private fun buildBpAppChip(ctx: Context, pkg: String, pm: PackageManager): LinearLayout {
        val radius = bpDp(20).toFloat()
        val chip = object : LinearLayout(ctx) {
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.parseColor("#0F2040") }
            private val rect = RectF()
            override fun dispatchDraw(c: Canvas) {
                rect.set(0f, 0f, width.toFloat(), height.toFloat())
                c.drawRoundRect(rect, radius, radius, bgPaint)
                super.dispatchDraw(c)
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(bpDp(12), bpDp(10), bpDp(14), bpDp(10))
            setWillNotDraw(false)
        }
        val iconView = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(bpDp(32), bpDp(32)).also { it.setMargins(0, 0, bpDp(8), 0) }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        try { iconView.setImageDrawable(pm.getApplicationIcon(pkg)) }
        catch (_: Exception) { iconView.setImageDrawable(pm.defaultActivityIcon) }

        val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                    catch (_: Exception) { pkg.substringAfterLast(".") }
        val nameTv = TextView(ctx).apply {
            text = label; textSize = 12f
            setTextColor(AColor.parseColor("#B0BEC5"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        chip.addView(iconView)
        chip.addView(nameTv)
        return chip
    }

    // ── Unlock dialog — lock type অনুযায়ী আলাদা ──────────
    private fun showBpUnlockDialog(ctx: Context, session: ButtonPhoneSession) {
        when {
            // Parents Control — password input
            session.unlockMode == BpUnlockMode.PARENTS -> {
                val dialogCtx = ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar)
                val dialog = android.app.AlertDialog.Builder(dialogCtx)
                    .setTitle("🔐 Unlock করতে পারবে না")
                    .setMessage("এই session শুধু parents unlock করতে পারবে।")
                    .setPositiveButton("ঠিক আছে", null)
                    .create()
                dialog.window?.setType(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                )
                dialog.show()
            }
            // Long Text — ~200 words টাইপ করতে হবে
            session.requireLongText -> {
                showLongTextUnlockOverlay(ctx, session)
            }
            // Self Control — সরাসরি unlock
            else -> {
                val dialogCtx = ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar)
                val dialog = android.app.AlertDialog.Builder(dialogCtx)
                    .setTitle("Session শেষ করবে?")
                    .setMessage("Focus session বন্ধ করতে চাও?")
                    .setPositiveButton("হ্যাঁ, Unlock করো") { _, _ ->
                        BpPrefs.clear(this)
                        getSharedPreferences(BpC.PREFS, MODE_PRIVATE).edit().putLong(BpC.KEY_BREAK_END, 0L).apply()
                        removeOverlay()
                        stopForeground(true)
                        stopSelf()
                    }
                    .setNegativeButton("না, থাকো", null)
                    .create()
                dialog.window?.setType(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                )
                dialog.show()
            }
        }
    }

    // ── Long Text unlock overlay ──────────────────────────
    private fun showLongTextUnlockOverlay(ctx: Context, session: ButtonPhoneSession) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )

        val unlockRoot = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AColor.parseColor("#0A1628"))
            setPadding(bpDp(24), bpDp(56), bpDp(24), bpDp(32))
        }

        unlockRoot.addView(bpTv(ctx, "Unlock করতে নিচের text টাইপ করো:", 14f, Typeface.BOLD, AColor.parseColor("#4DD0E1"), bot = bpDp(12)))

        val targetBox = android.widget.ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bpDp(120))
                .also { it.bottomMargin = bpDp(16) }
            setBackgroundColor(AColor.parseColor("#0F2040"))
            setPadding(bpDp(12), bpDp(12), bpDp(12), bpDp(12))
        }
        targetBox.addView(bpTv(ctx, LONG_UNLOCK_TEXT, 11f, color = AColor.parseColor("#B0BEC5")))
        unlockRoot.addView(targetBox)

        val inputEt = android.widget.EditText(ctx).apply {
            hint = "এখানে টাইপ করুন..."
            setHintTextColor(AColor.parseColor("#546E7A"))
            setTextColor(AColor.WHITE)
            setBackgroundColor(AColor.parseColor("#0F2040"))
            setPadding(bpDp(12), bpDp(12), bpDp(12), bpDp(12))
            minLines = 4
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = bpDp(16) }
        }
        unlockRoot.addView(inputEt)

        var unlockOverlayView: View? = unlockRoot
        windowManager.addView(unlockRoot, params)

        val verifyBtn = object : TextView(ctx) {
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.parseColor("#1B6B5A") }
            private val rect = RectF()
            init { setWillNotDraw(false) }
            override fun onDraw(c: Canvas) {
                rect.set(0f, 0f, width.toFloat(), height.toFloat())
                c.drawRoundRect(rect, bpDp(14).toFloat(), bpDp(14).toFloat(), bgPaint)
                super.onDraw(c)
            }
        }.apply {
            text = "Verify & Unlock"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(AColor.WHITE)
            gravity = Gravity.CENTER
            setPadding(bpDp(24), bpDp(16), bpDp(24), bpDp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = bpDp(10) }
        }
        verifyBtn.setOnClickListener {
            if (inputEt.text.toString().trim() == LONG_UNLOCK_TEXT.trim()) {
                unlockOverlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
                unlockOverlayView = null
                BpPrefs.clear(this)
                getSharedPreferences(BpC.PREFS, MODE_PRIVATE).edit().putLong(BpC.KEY_BREAK_END, 0L).apply()
                removeOverlay()
                stopForeground(true)
                stopSelf()
            } else {
                Toast.makeText(ctx, "লেখা মিলছে না! হুবহু টাইপ করুন।", Toast.LENGTH_SHORT).show()
            }
        }
        unlockRoot.addView(verifyBtn)

        val backBtn = bpTv(ctx, "← ফিরে যাও", 14f, color = AColor.parseColor("#546E7A"), grav = Gravity.CENTER)
        backBtn.setOnClickListener {
            unlockOverlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
            unlockOverlayView = null
        }
        unlockRoot.addView(backBtn)
    }

    // ── Helpers ───────────────────────────────────────────
    private fun bpDp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bpTv(
        ctx: Context, text: String, size: Float,
        style: Int = Typeface.NORMAL, color: Int = AColor.WHITE,
        bot: Int = 0, grav: Int = Gravity.NO_GRAVITY
    ) = TextView(ctx).apply {
        this.text = text; textSize = size
        setTypeface(null, style); setTextColor(color)
        setPadding(0, 0, 0, bot)
        if (grav != Gravity.NO_GRAVITY) gravity = grav
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDestroy() {
        running = false
        thread?.interrupt()
        timerHandler.removeCallbacks(timerRunnable)
        Handler(Looper.getMainLooper()).post { removeOverlay() }
    }
}