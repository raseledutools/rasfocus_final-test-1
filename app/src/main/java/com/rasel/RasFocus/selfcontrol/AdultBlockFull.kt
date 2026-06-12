package com.rasel.RasFocus.selfcontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
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
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rasel.RasFocus.DataManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ============================================================
// DESIGN TOKENS
// ============================================================
private object RC {
    val Teal       = Color(0xFF0CA8B0)
    val TealDark   = Color(0xFF087A80)
    val TealLight  = Color(0xFFDCF8FA)
    val Dark       = Color(0xFF1A1A2E)
    val DarkCard   = Color(0xFF16213E)
    val GrayText   = Color(0xFF82828C)
    val Border     = Color(0xFFE8EDF2)
    val White      = Color(0xFFFFFFFF)
    val Bg         = Color(0xFFF4F7FA)
    val CardBg     = Color(0xFFFFFFFF)
    val Red        = Color(0xFFE53E3E)
    val RedLight   = Color(0xFFFFF5F5)
    val Green      = Color(0xFF38A169)
    val GreenLight = Color(0xFFF0FFF4)
    val Orange     = Color(0xFFDD6B20)
}

// ============================================================
// ENUMS & DATA MODELS
// ============================================================
enum class ControlMode(val label: String) {
    SELF("Self Control"), PARENTS("Parents Control"), LONG_TEXT("Long Text")
}
enum class Religion(val label: String) {
    MUSLIM("Muslim"), HINDU("Hindu"), CHRISTIAN("Christian"), UNIVERSAL("Universal")
}
enum class Language(val label: String) {
    BANGLA("বাংলা"), ENGLISH("English")
}
data class CustomKeyword(val name: String)

data class AdultBlockState(
    val blockAdultWeb: Boolean = true,
    val blockFbReels: Boolean = true,
    val blockHardcore: Boolean = true,
    val blockRomantic: Boolean = true,
    val controlMode: ControlMode = ControlMode.SELF,
    val religion: Religion = Religion.MUSLIM,
    val language: Language = Language.BANGLA,
    val isAdultFocusActive: Boolean = false,
    val focusEndTimeMs: Long = 0L,
    val is24hLockActive: Boolean = false,
    val lock24hEndTimeMs: Long = 0L,
    val silentMonitor: Boolean = true,
    val familyDns: Boolean = false,
    val safeSearch: Boolean = true,
    val blockIncognito: Boolean = true,
    val strictLockMode: Boolean = false,
    val isStrictFocusActive: Boolean = false,
    val strictFocusEndTimeMs: Long = 0L,
    val periodicReminders: Boolean = false,
    val isPanicActive: Boolean = false,
    val panicStartMs: Long = 0L,
    val customKeywords: List<CustomKeyword> = emptyList(),
    val customInputText: String = "",
    val showFocusTimeDialog: Boolean = false,
    val showStrictTimeDialog: Boolean = false,
    val showPasswordDialog: Boolean = false,
    val isStoppingFocus: Boolean = false,
    val showLongTextDialog: Boolean = false,
    val longTextInput: String = "",
    val focusHours: Int = 1,
    val focusMins: Int = 0,
    val strictFocusHours: Int = 1,
    val strictFocusMins: Int = 0,
    val passwordInput: String = "",
    val totalBlockedCount: Int = 0,
    val show24hConfirm: Boolean = false,
)

// ============================================================
// KEYWORD / SITE DATABASE
// ============================================================
private object KeywordDB {
    val hardcore = listOf(
        "porn","xxx","sex","nude","nsfw","hentai","milf","blowjob",
        "xvideos","pornhub","xnxx","xhamster","brazzers","onlyfans",
        "chaturbate","spankbang","redtube","youporn",
        "চটি","পর্ণ","সেক্স","নগ্ন",
        "bhabi","chudai","bangla choti","panu","magi","choda","randi",
    )
    val romantic = listOf(
        "hot dance","seductive","item song","belly dance",
        "kissing scene","bikini","sexy dance","cleavage",
        "semi nude","lingerie","erotic","navel show",
    )
    val sites = listOf(
        "pornhub.com","xvideos.com","xnxx.com","xhamster.com","redtube.com",
        "youporn.com","spankbang.com","chaturbate.com","onlyfans.com","brazzers.com",
    )
}

// ============================================================
// VIEWMODEL
// ============================================================
class AdultBlockViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs      = app.getSharedPreferences("rasfocus_adult",    Context.MODE_PRIVATE)
    // AdultBlockService reads from this prefs — must stay in sync
    private val svcPrefs   = app.getSharedPreferences("RasFocusAdultPrefs", Context.MODE_PRIVATE)
    private val _s = MutableStateFlow(AdultBlockState())
    val state = _s.asStateFlow()

    init {
        DataManager.init(app)
        load()
    }

    // ── LOAD / SAVE ──────────────────────────────────────────
    private fun load() {
        val now = System.currentTimeMillis()
        val lock24End = prefs.getLong("lock24hEnd", 0L)
        val focusEnd  = prefs.getLong("focusEnd",   0L)
        val strictEnd = prefs.getLong("strictEnd",  0L)
        val is24h     = prefs.getBoolean("is24h",  false) && now < lock24End
        val isFocus   = when {
            is24h -> true
            prefs.getBoolean("isFocus", false) -> now < focusEnd
            else -> false
        }
        val isStrict = prefs.getBoolean("isStrict", false) && now < strictEnd
        val kwSize   = prefs.getInt("kwSize", 0)
        val kws      = (0 until kwSize).mapNotNull {
            prefs.getString("kw_$it", null)?.let { n -> CustomKeyword(n) }
        }
        _s.update { it.copy(
            blockAdultWeb      = prefs.getBoolean("bAdult",  true),
            blockFbReels       = prefs.getBoolean("bReels",  true),
            blockHardcore      = prefs.getBoolean("bHard",   true),
            blockRomantic      = prefs.getBoolean("bRom",    true),
            controlMode        = ControlMode.entries[prefs.getInt("ctrl", 0)],
            religion           = Religion.entries[prefs.getInt("rel",  0)],
            language           = Language.entries[prefs.getInt("lang", 0)],
            isAdultFocusActive = isFocus,
            focusEndTimeMs     = focusEnd,
            is24hLockActive    = is24h,
            lock24hEndTimeMs   = lock24End,
            silentMonitor      = prefs.getBoolean("silent",  true),
            familyDns          = prefs.getBoolean("dns",     false),
            safeSearch         = prefs.getBoolean("safe",    true),
            blockIncognito     = prefs.getBoolean("incog",   true),
            strictLockMode     = prefs.getBoolean("strict",  false),
            isStrictFocusActive= isStrict,
            strictFocusEndTimeMs = strictEnd,
            periodicReminders  = prefs.getBoolean("remind",  false),
            totalBlockedCount  = prefs.getInt("blocked", 0),
            customKeywords     = kws,
        )}
        syncToServices(_s.value)
    }

    private fun save() {
        val s = _s.value
        prefs.edit().apply {
            putBoolean("bAdult",  s.blockAdultWeb);  putBoolean("bReels", s.blockFbReels)
            putBoolean("bHard",   s.blockHardcore);  putBoolean("bRom",   s.blockRomantic)
            putInt("ctrl",  s.controlMode.ordinal);  putInt("rel",  s.religion.ordinal)
            putInt("lang",  s.language.ordinal)
            putBoolean("isFocus",  s.isAdultFocusActive); putLong("focusEnd",  s.focusEndTimeMs)
            putBoolean("is24h",    s.is24hLockActive);    putLong("lock24hEnd",s.lock24hEndTimeMs)
            putBoolean("silent",   s.silentMonitor);  putBoolean("dns",    s.familyDns)
            putBoolean("safe",     s.safeSearch);     putBoolean("incog",  s.blockIncognito)
            putBoolean("strict",   s.strictLockMode)
            putBoolean("isStrict", s.isStrictFocusActive); putLong("strictEnd", s.strictFocusEndTimeMs)
            putBoolean("remind",   s.periodicReminders)
            putInt("blocked", s.totalBlockedCount)
            putInt("kwSize", s.customKeywords.size)
            s.customKeywords.forEachIndexed { i, kw -> putString("kw_$i", kw.name) }
        }.apply()
        syncToServices(s)
    }

    /**
     * Sync UI state → DataManager ("RasFocusData") + AdultBlockService prefs ("RasFocusAdultPrefs")
     * AdultBlockService reads from BOTH of these. This is the critical bridge.
     */
    private fun syncToServices(s: AdultBlockState) {
        // ── DataManager (RasFocusData prefs) ──────────────────────────────
        DataManager.isAdultFocusActive     = s.isAdultFocusActive
        DataManager.is24HourLockActive     = s.is24hLockActive
        DataManager.lock24hEndTime         = s.lock24hEndTimeMs
        DataManager.controlMode            = s.controlMode.ordinal
        DataManager.adultReligion          = s.religion.ordinal
        DataManager.adultLanguage          = s.language.ordinal
        DataManager.isPeriodicPopupsActive = s.periodicReminders
        DataManager.userCustomAdultKeywords= s.customKeywords.map { it.name }

        // ── RasFocusAdultPrefs (read by AdultBlockService.onAccessibilityEvent) ──
        svcPrefs.edit().apply {
            putBoolean("cbAdultWeb",           s.blockAdultWeb)
            putBoolean("cbHardcore",           s.blockHardcore)
            putBoolean("cbRomantic",           s.blockRomantic)
            putBoolean("cbFbReels",            s.blockFbReels)
            putBoolean("cbYtShorts",           s.blockFbReels)   // same toggle
            putBoolean("keyboardTypingBlock",  false)             // kept off by default
            putBoolean("strictBlockIncognito", s.blockIncognito)
            putBoolean("strictLockMode",       s.strictLockMode)
            putBoolean("strictSilentMonitor",  s.silentMonitor)
        }.apply()

        // ── Live service instance (if already running) ────────────────────
        UnifiedBlockerService.instance?.let { svc ->
            if (s.isPanicActive) svc.activatePanicMode()
        }
    }

    // ── SAFE BROWSING TOGGLES ────────────────────────────────
    fun toggleBlockAdultWeb() = cbToggle { it.copy(blockAdultWeb = !it.blockAdultWeb) }
    fun toggleBlockFbReels()  = cbToggle { it.copy(blockFbReels  = !it.blockFbReels)  }
    fun toggleBlockHardcore() = cbToggle { it.copy(blockHardcore = !it.blockHardcore) }
    fun toggleBlockRomantic() = cbToggle { it.copy(blockRomantic = !it.blockRomantic) }

    private fun cbToggle(fn: (AdultBlockState) -> AdultBlockState) { _s.update(fn); save() }

    // ── DROPDOWNS ────────────────────────────────────────────
    fun setControlMode(m: ControlMode) { if (!_s.value.isAdultFocusActive) { _s.update { it.copy(controlMode = m) }; save() } }
    fun setReligion(r: Religion)        { _s.update { it.copy(religion = r) }; save() }
    fun setLanguage(l: Language)        { _s.update { it.copy(language = l) }; save() }

    // ── SAFE FOCUS ───────────────────────────────────────────
    fun onSafeFocusClick() {
        val s = _s.value
        if (s.is24hLockActive) return
        if (s.isAdultFocusActive) {
            when (s.controlMode) {
                ControlMode.SELF      -> stopFocusDirectly()
                ControlMode.PARENTS   -> _s.update { it.copy(showPasswordDialog = true, isStoppingFocus = true) }
                ControlMode.LONG_TEXT -> _s.update { it.copy(showLongTextDialog = true) }
            }
        } else {
            when (s.controlMode) {
                ControlMode.SELF      -> _s.update { it.copy(showFocusTimeDialog = true) }
                ControlMode.PARENTS   -> _s.update { it.copy(showPasswordDialog = true, isStoppingFocus = false) }
                ControlMode.LONG_TEXT -> _s.update { it.copy(showLongTextDialog = true) }
            }
        }
    }

    fun startSafeFocus(h: Int, m: Int) {
        val mins = h * 60 + m
        val end = if (mins == 0) Long.MAX_VALUE else System.currentTimeMillis() + (h * 3600L + m * 60L) * 1000L
        _s.update { it.copy(isAdultFocusActive = true, focusEndTimeMs = end, showFocusTimeDialog = false,
            blockAdultWeb = true, blockHardcore = true, blockRomantic = true) }
        save()
    }

    private fun stopFocusDirectly() {
        _s.update { it.copy(isAdultFocusActive = false, focusEndTimeMs = 0L) }
        save()
    }

    fun stopFocusWithPassword(pw: String): Boolean {
        // FIX: Compare against stored hash; fall back to legacy plain-text for migration
        val storedHash = prefs.getString("friendPasswordHash", null)
        val isCorrect = if (storedHash != null) {
            com.rasel.RasFocus.DataManager.verifyPassword(pw, storedHash)
        } else {
            val legacy = prefs.getString("friendPassword", "1234") ?: "1234"
            pw == legacy
        }
        if (!isCorrect) return false
        _s.update { it.copy(isAdultFocusActive = false, focusEndTimeMs = 0L,
            showPasswordDialog = false, passwordInput = "", isStoppingFocus = false) }
        save(); return true
    }

    fun startFocusWithPassword(pw: String): Boolean {
        val storedHash = prefs.getString("friendPasswordHash", null)
        val isCorrect = if (storedHash != null) {
            com.rasel.RasFocus.DataManager.verifyPassword(pw, storedHash)
        } else {
            val legacy = prefs.getString("friendPassword", "1234") ?: "1234"
            pw == legacy
        }
        if (!isCorrect) return false
        _s.update { it.copy(isAdultFocusActive = true, showPasswordDialog = false,
            passwordInput = "", isStoppingFocus = false,
            blockAdultWeb = true, blockHardcore = true, blockRomantic = true,
            focusEndTimeMs = Long.MAX_VALUE) }
        save(); return true
    }

    fun dismissPasswordDialog() = _s.update { it.copy(showPasswordDialog = false, passwordInput = "", isStoppingFocus = false) }
    fun setPasswordInput(t: String) = _s.update { it.copy(passwordInput = t) }
    fun dismissFocusTimeDialog() = _s.update { it.copy(showFocusTimeDialog = false) }
    fun setFocusHours(h: Int) = _s.update { it.copy(focusHours = h.coerceIn(0, 23)) }
    fun setFocusMins(m: Int)  = _s.update { it.copy(focusMins  = m.coerceIn(0, 59)) }

    // ── 24H LOCK ─────────────────────────────────────────────
    fun show24hConfirm()    = _s.update { it.copy(show24hConfirm = true) }
    fun dismiss24hConfirm() = _s.update { it.copy(show24hConfirm = false) }

    fun activate24hLock() {
        val end = System.currentTimeMillis() + 86_400_000L
        _s.update { it.copy(is24hLockActive = true, isAdultFocusActive = true,
            lock24hEndTimeMs = end, show24hConfirm = false) }
        save()
    }

    // ── STRICT PROTOCOLS — no premium gate ──────────────────
    fun toggleSilentMonitor()  = strictToggle { it.copy(silentMonitor  = !it.silentMonitor) }
    fun toggleFamilyDns()      = strictToggle { it.copy(familyDns      = !it.familyDns) }
    fun toggleSafeSearch()     = strictToggle { it.copy(safeSearch     = !it.safeSearch) }
    fun toggleBlockIncognito() = strictToggle { it.copy(blockIncognito = !it.blockIncognito) }
    fun toggleStrictLockMode() = strictToggle { it.copy(strictLockMode = !it.strictLockMode) }

    private fun strictToggle(fn: (AdultBlockState) -> AdultBlockState) { _s.update(fn); save() }

    // ── STRICT FOCUS ─────────────────────────────────────────
    fun onStrictFocusClick() {
        val s = _s.value
        if (s.isStrictFocusActive) { _s.update { it.copy(isStrictFocusActive = false, strictFocusEndTimeMs = 0L) }; save() }
        else _s.update { it.copy(showStrictTimeDialog = true) }
    }

    fun startStrictFocus(h: Int, m: Int) {
        val mins = h * 60 + m
        val end = if (mins == 0) Long.MAX_VALUE else System.currentTimeMillis() + (h * 3600L + m * 60L) * 1000L
        _s.update { it.copy(isStrictFocusActive = true, strictFocusEndTimeMs = end, showStrictTimeDialog = false) }
        save()
    }

    fun dismissStrictTimeDialog() = _s.update { it.copy(showStrictTimeDialog = false) }
    fun setStrictHours(h: Int) = _s.update { it.copy(strictFocusHours = h.coerceIn(0, 23)) }
    fun setStrictMins(m: Int)  = _s.update { it.copy(strictFocusMins  = m.coerceIn(0, 59)) }

    // ── PANIC ────────────────────────────────────────────────
    fun onPanicClick() {
        _s.update { it.copy(isPanicActive = !it.isPanicActive, panicStartMs = System.currentTimeMillis()) }
        save()
        UnifiedBlockerService.instance?.let { if (_s.value.isPanicActive) it.activatePanicMode() }
    }

    // ── REMINDERS ────────────────────────────────────────────
    fun toggleReminders() { _s.update { it.copy(periodicReminders = !it.periodicReminders) }; save() }

    // ── CUSTOM KEYWORDS ──────────────────────────────────────
    fun setCustomInput(t: String) = _s.update { it.copy(customInputText = t) }

    fun addCustomKeyword() {
        val t = _s.value.customInputText.trim(); if (t.isBlank()) return
        _s.update { it.copy(customKeywords = it.customKeywords + CustomKeyword(t), customInputText = "") }
        save()
    }

    fun removeCustomKeyword(kw: CustomKeyword) {
        if (_s.value.isAdultFocusActive) return
        _s.update { it.copy(customKeywords = it.customKeywords - kw) }; save()
    }

    // ── LONG TEXT ────────────────────────────────────────────
    fun setLongTextInput(t: String) = _s.update { it.copy(longTextInput = t) }
    fun dismissLongTextDialog() = _s.update { it.copy(showLongTextDialog = false, longTextInput = "") }

    fun confirmLongText() {
        val s = _s.value
        if (s.isAdultFocusActive)
            _s.update { it.copy(isAdultFocusActive = false, showLongTextDialog = false, longTextInput = "") }
        else
            _s.update { it.copy(isAdultFocusActive = true, focusEndTimeMs = Long.MAX_VALUE,
                showLongTextDialog = false, longTextInput = "",
                blockAdultWeb = true, blockHardcore = true, blockRomantic = true) }
        save()
    }

    // ── TICKER ───────────────────────────────────────────────
    fun tick() {
        val now = System.currentTimeMillis(); val s = _s.value; var changed = false; var n = s
        if (s.is24hLockActive && now >= s.lock24hEndTimeMs)
            { n = n.copy(is24hLockActive = false, isAdultFocusActive = false); changed = true }
        if (s.isAdultFocusActive && s.controlMode == ControlMode.SELF && now >= s.focusEndTimeMs && !s.is24hLockActive)
            { n = n.copy(isAdultFocusActive = false); changed = true }
        if (s.isStrictFocusActive && now >= s.strictFocusEndTimeMs)
            { n = n.copy(isStrictFocusActive = false); changed = true }
        if (s.isPanicActive && now - s.panicStartMs >= 15 * 60_000L)
            { n = n.copy(isPanicActive = false); changed = true }
        if (changed) { _s.update { n }; save() }
    }

    // ── URL CHECK ────────────────────────────────────────────
    fun shouldBlockUrl(url: String, title: String): Boolean {
        val s = _s.value; if (s.isPanicActive) return false
        val low = (url + " " + title).lowercase()
        if (s.blockHardcore  && KeywordDB.hardcore.any { low.contains(it) }) return true
        if (s.blockRomantic  && KeywordDB.romantic.any { low.contains(it) }) return true
        if (s.blockAdultWeb  && KeywordDB.sites.any    { low.contains(it) }) return true
        if (s.customKeywords.any { low.contains(it.name.lowercase()) })      return true
        if (s.blockFbReels && (low.contains("facebook.com/reel") ||
            low.contains("instagram.com/reels") || low.contains("youtube.com/shorts"))) return true
        return false
    }

    // ── SCHEDULE API ─────────────────────────────────────────
    fun applyForSchedule(enable: Boolean) {
        if (enable)
            _s.update { it.copy(isAdultFocusActive = true, blockAdultWeb = true, blockHardcore = true, blockRomantic = true, focusEndTimeMs = 0L) }
        else if (!_s.value.is24hLockActive)
            _s.update { it.copy(isAdultFocusActive = false) }
        save()
    }
}

// ============================================================
// REUSABLE COMPONENTS
// ============================================================

@Composable
private fun RasToggle(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    enabled: Boolean = true,
    activeColor: Color = RC.Teal,
) {
    val tr = updateTransition(checked, "t")
    val offset by tr.animateDp({ spring(stiffness = Spring.StiffnessMediumLow) }, "o") { if (it) 20.dp else 2.dp }
    val color  by tr.animateColor({ tween(200) }, "c") { if (it) (if (enabled) activeColor else RC.GrayText) else RC.Border }
    Box(
        Modifier.size(44.dp, 24.dp).clip(RoundedCornerShape(12.dp)).background(color = color)
            .clickable(enabled = enabled) { onCheckedChange() },
        contentAlignment = Alignment.CenterStart
    ) { Box(Modifier.padding(start = offset).size(20.dp).clip(CircleShape).background(RC.White)) }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, locked: Boolean = false, onToggle: () -> Unit) {
    val isLocked = locked && checked
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !isLocked) { onToggle() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = if (isLocked) RC.GrayText else RC.Dark, modifier = Modifier.weight(1f))
        RasToggle(checked, onToggle, !isLocked)
    }
}

@Composable
private fun StrictCard(
    icon: ImageVector, title: String, desc: String,
    checked: Boolean, locked: Boolean = false,
    activeColor: Color = RC.Teal, onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLocked = locked && checked
    Card(
        modifier.fillMaxWidth()
            .border(if (checked) 1.5.dp else 1.dp, if (checked) activeColor else RC.Border, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)).clickable(enabled = !isLocked) { onToggle() },
        colors = CardDefaults.cardColors(if (checked) RC.TealLight else RC.CardBg),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = if (checked) activeColor else RC.GrayText, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RC.Dark, modifier = Modifier.weight(1f))
                RasToggle(checked, onToggle, !isLocked, activeColor)
            }
            Spacer(Modifier.height(4.dp))
            Text(desc, fontSize = 11.sp, color = RC.GrayText, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun FocusBtn(label: String, color: Color, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick, modifier.height(48.dp), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        contentPadding = PaddingValues(horizontal = 10.dp)) {
        Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SectionLabel(title: String, subtitle: String = "") {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RC.Teal)
        if (subtitle.isNotEmpty())
            Text(subtitle, fontSize = 11.sp, color = RC.GrayText)
    }
}

@Composable
private fun Badge(label: String, icon: ImageVector, active: Boolean) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (active) RC.Teal else RC.CardBg)
            .border(if (active) 0.dp else 1.dp, RC.Border, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        val tint = if (active) RC.White else RC.GrayText
        Icon(icon, null, tint = tint, modifier = Modifier.size(12.dp))
        Text(label, fontSize = 11.sp, color = tint, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Spinner(label: String, value: Int, onDec: () -> Unit, onInc: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = RC.GrayText)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onDec, Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(RC.Border)) {
                Icon(Icons.Default.Remove, null, tint = RC.Dark, modifier = Modifier.size(18.dp))
            }
            Text(value.toString().padStart(2, '0'), fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = RC.Dark, modifier = Modifier.width(54.dp), textAlign = TextAlign.Center)
            IconButton(onInc, Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(RC.Teal)) {
                Icon(Icons.Default.Add, null, tint = RC.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun <T> RasDropdown(selected: T, items: List<T>, label: (T) -> String, onSelect: (T) -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    var exp by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { if (enabled) exp = true }, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (enabled) RC.White else RC.Bg, contentColor = RC.Dark),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
        ) {
            Text(label(selected), fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            Icon(Icons.Default.ArrowDropDown, null, tint = RC.GrayText, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(exp, { exp = false }) {
            items.forEach { DropdownMenuItem({ Text(label(it), fontSize = 13.sp) }, { onSelect(it); exp = false }) }
        }
    }
}

// ============================================================
// DIALOGS
// ============================================================

@Composable
private fun DialogHeader(title: String, subtitle: String = "") {
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(RC.Teal, RC.TealDark)))
            .padding(vertical = 20.dp, horizontal = 24.dp),
    ) {
        Column {
            Text(title, color = RC.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            if (subtitle.isNotEmpty())
                Text(subtitle, color = RC.White.copy(0.8f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun FocusDurationDialog(title: String, hours: Int, mins: Int, onH: (Int) -> Unit, onM: (Int) -> Unit, onStart: () -> Unit, onCancel: () -> Unit) {
    Dialog(onCancel) {
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(RC.White)) {
            DialogHeader(title, "Set duration (0h 0m = unlimited)")
            Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp),
                Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                Spinner("Hours", hours, { onH(hours - 1) }, { onH(hours + 1) })
                Text(":", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = RC.Dark, modifier = Modifier.padding(top = 18.dp))
                Spinner("Mins", mins, { onM((mins - 5).coerceAtLeast(0)) }, { onM(mins + 5) })
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp), Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onCancel, Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                Button(onStart, Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RC.Teal)) { Text("Start Focus", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun PasswordDialog(title: String, subtitle: String = "", pw: String, onChange: (String) -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Dialog(onCancel, DialogProperties(dismissOnClickOutside = false)) {
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(RC.White)) {
            DialogHeader(title, subtitle)
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(pw, onChange, Modifier.fillMaxWidth(),
                    placeholder = { Text("Type password here...", color = RC.GrayText) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RC.Teal, cursorColor = RC.Teal),
                    shape = RoundedCornerShape(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onCancel, Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                    Button(onConfirm, Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RC.Teal),
                        enabled = pw.isNotBlank()) { Text("Confirm", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun LongTextDialog(text: String, isStarting: Boolean, onChange: (String) -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Dialog(onCancel, DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(20.dp)).background(RC.White).fillMaxWidth()) {
            DialogHeader(
                if (isStarting) "Start Long Text Lock" else "Re-enter Text to Stop",
                if (isStarting) "You must retype this text exactly to unlock" else "Enter the exact text you saved"
            )
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(text, onChange, Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 200.dp),
                    placeholder = { Text("Type or paste a long text...", color = RC.GrayText) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RC.Teal, cursorColor = RC.Teal),
                    shape = RoundedCornerShape(10.dp))
                Text("${text.length}/2500", fontSize = 11.sp, color = RC.GrayText, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onCancel, Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                    Button(onConfirm, Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RC.Teal),
                        enabled = text.isNotBlank()) { Text(if (isStarting) "Activate Lock" else "Unlock", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun Lock24hConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Dialog(onCancel, DialogProperties(dismissOnClickOutside = false)) {
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(RC.White)) {
            Box(Modifier.fillMaxWidth().background(RC.Red).padding(20.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = RC.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("24-Hour Lockdown", color = RC.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                    Text("একবার চালু হলে ২৪ ঘণ্টার আগে বন্ধ করা যাবে না!", color = RC.White.copy(0.9f), fontSize = 12.sp)
                }
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("এই Lock চালু করলে ২৪ ঘণ্টার জন্য কোনো adult content access করা যাবে না। আপনি কি নিশ্চিত?",
                    fontSize = 14.sp, color = RC.Dark, lineHeight = 20.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onCancel, Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                    Button(onConfirm, Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RC.Red)) { Text("Yes, Lock Now", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ============================================================
// MAIN SCREEN
// ============================================================
@Composable
fun AdultBlockScreen(vm: AdultBlockViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { while (true) { delay(1000L); vm.tick() } }

    // ── DIALOGS ─────────────────────────────────────────────
    if (s.showFocusTimeDialog)
        FocusDurationDialog("Set Focus Duration", s.focusHours, s.focusMins,
            vm::setFocusHours, vm::setFocusMins,
            onStart = { vm.startSafeFocus(s.focusHours, s.focusMins) },
            onCancel = vm::dismissFocusTimeDialog)

    if (s.showStrictTimeDialog)
        FocusDurationDialog("Set Strict Focus Duration", s.strictFocusHours, s.strictFocusMins,
            vm::setStrictHours, vm::setStrictMins,
            onStart = { vm.startStrictFocus(s.strictFocusHours, s.strictFocusMins) },
            onCancel = vm::dismissStrictTimeDialog)

    if (s.showPasswordDialog) {
        if (s.isStoppingFocus) {
            PasswordDialog(
                title = "Stop Focus", subtitle = "Enter password to disable protection",
                pw = s.passwordInput, onChange = vm::setPasswordInput,
                onConfirm = { vm.stopFocusWithPassword(s.passwordInput) },
                onCancel = vm::dismissPasswordDialog)
        } else {
            PasswordDialog(
                title = "Start Focus", subtitle = "Enter parents' password to activate",
                pw = s.passwordInput, onChange = vm::setPasswordInput,
                onConfirm = { vm.startFocusWithPassword(s.passwordInput) },
                onCancel = vm::dismissPasswordDialog)
        }
    }

    if (s.showLongTextDialog)
        LongTextDialog(s.longTextInput, !s.isAdultFocusActive,
            vm::setLongTextInput, vm::confirmLongText, vm::dismissLongTextDialog)

    if (s.show24hConfirm)
        Lock24hConfirmDialog(onConfirm = { vm.activate24hLock() }, onCancel = { vm.dismiss24hConfirm() })

    // ── MAIN LAYOUT ─────────────────────────────────────────
    Column(Modifier.fillMaxSize().background(RC.Bg)) {
        // Header
        AdultBlockHeader(s)
        // Scrollable content
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item { ControlBar(s, vm) }
            item { SectionDivider() }
            item { SafeBrowsingSection(s, vm) }
            item { SectionDivider() }
            item { StrictProtocolsSection(s, vm) }
            item { SectionDivider() }
            item { AdvancedSection(s, vm) }
            item { SectionDivider() }
            item { ActiveProtectionsBar(s) }
        }
    }
}

@Composable
private fun AdultBlockHeader(s: AdultBlockState) {
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(RC.Teal, RC.TealDark)))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                        .background(RC.White.copy(0.15f)),
                    Alignment.Center
                ) { Icon(Icons.Default.Shield, null, tint = RC.White, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Adult Block", color = RC.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Content Protection Shield", color = RC.White.copy(0.8f), fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                // Live status pill
                val isActive = s.isAdultFocusActive || s.is24hLockActive
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (isActive) RC.Green else RC.White.copy(0.2f))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(RC.White))
                        Text(if (isActive) "Active" else "Inactive", color = RC.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (s.totalBlockedCount > 0) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(RC.White.copy(0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Block, null, tint = RC.White, modifier = Modifier.size(14.dp))
                    Text("${s.totalBlockedCount} blocked today", color = RC.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable private fun SectionDivider() =
    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 2.dp), color = RC.Border.copy(0.7f))

// ── CONTROL BAR ─────────────────────────────────────────────
@Composable
private fun ControlBar(s: AdultBlockState, vm: AdultBlockViewModel) {
    Column(Modifier.background(RC.White).padding(horizontal = 16.dp, vertical = 14.dp)) {

        // Focus Buttons Row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Safe Focus
            val focusLabel = when {
                s.is24hLockActive -> {
                    val l = (s.lock24hEndTimeMs - System.currentTimeMillis()).coerceAtLeast(0)
                    "Locked (${l/3600000}h)"
                }
                s.isAdultFocusActive && s.controlMode == ControlMode.SELF -> {
                    val l = (s.focusEndTimeMs - System.currentTimeMillis()).coerceAtLeast(0)
                    if (s.focusEndTimeMs == Long.MAX_VALUE) "Stop Focus" else "Stop (${l/60000+1}m)"
                }
                s.isAdultFocusActive -> "Stop Focus"
                else -> "Safe Focus"
            }
            FocusBtn(
                focusLabel,
                color = if (s.isAdultFocusActive) RC.Red else RC.Green,
                icon  = if (s.isAdultFocusActive) Icons.Default.Lock else Icons.Default.Shield,
                onClick = { vm.onSafeFocusClick() },
                modifier = Modifier.weight(1f)
            )

            // Strict Focus
            val strictLabel = if (s.isStrictFocusActive) {
                val l = (s.strictFocusEndTimeMs - System.currentTimeMillis()).coerceAtLeast(0)
                if (s.strictFocusEndTimeMs == Long.MAX_VALUE) "Stop Strict" else "Stop (${l/60000+1}m)"
            } else "Strict Focus"
            FocusBtn(
                strictLabel,
                color = if (s.isStrictFocusActive) RC.Red else Color(0xFF553C9A),
                icon  = if (s.isStrictFocusActive) Icons.Default.Lock else Icons.Default.Security,
                onClick = { vm.onStrictFocusClick() },
                modifier = Modifier.weight(1f)
            )

            // Panic
            FocusBtn(
                if (s.isPanicActive) "🔴 Active" else "Panic",
                color = if (s.isPanicActive) RC.Orange else RC.Red,
                icon  = Icons.Default.Warning,
                onClick = { vm.onPanicClick() },
                modifier = Modifier.weight(0.75f)
            )
        }

        Spacer(Modifier.height(10.dp))

        // Settings Row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RasDropdown(s.controlMode, ControlMode.entries, { it.label }, vm::setControlMode, !s.isAdultFocusActive, Modifier.weight(1.3f))
            RasDropdown(s.religion,    Religion.entries,    { it.label }, vm::setReligion,    modifier = Modifier.weight(1f))
            RasDropdown(s.language,    Language.entries,    { it.label }, vm::setLanguage,    modifier = Modifier.weight(0.85f))
        }

        // Status info
        if (s.isAdultFocusActive || s.isPanicActive) {
            Spacer(Modifier.height(10.dp))
            val infoText = when {
                s.is24hLockActive -> {
                    val l = (s.lock24hEndTimeMs - System.currentTimeMillis()).coerceAtLeast(0)
                    "🔒 24h Lock active — ${l/3600000}h ${(l%3600000)/60000}m remaining"
                }
                s.isPanicActive -> "🚨 Panic mode — all browsers blocked for 15 mins"
                s.isAdultFocusActive -> "🛡 Protection active. Blocking adult content."
                else -> ""
            }
            if (infoText.isNotEmpty()) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(if (s.isPanicActive) Color(0xFFFFF3E0) else RC.GreenLight)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(infoText, fontSize = 12.sp,
                        color = if (s.isPanicActive) RC.Orange else RC.Green,
                        fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ── SAFE BROWSING ────────────────────────────────────────────
@Composable
private fun SafeBrowsingSection(s: AdultBlockState, vm: AdultBlockViewModel) {
    val locked = s.isAdultFocusActive
    Column(Modifier.background(RC.White).padding(horizontal = 16.dp, vertical = 14.dp)) {
        SectionLabel("Safe Browsing Rules", "Content filters applied to all browsers")
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(RC.CardBg), elevation = CardDefaults.cardElevation(0.dp),
            border = CardDefaults.outlinedCardBorder()) {
            Column {
                CheckRow("Block Adult Websites",       s.blockAdultWeb, locked, vm::toggleBlockAdultWeb)
                HorizontalDivider(color = RC.Border, modifier = Modifier.padding(horizontal = 14.dp))
                CheckRow("Block Hardcore Keywords",    s.blockHardcore, locked, vm::toggleBlockHardcore)
                HorizontalDivider(color = RC.Border, modifier = Modifier.padding(horizontal = 14.dp))
                CheckRow("Block Romantic / Softcore",  s.blockRomantic, locked, vm::toggleBlockRomantic)
                HorizontalDivider(color = RC.Border, modifier = Modifier.padding(horizontal = 14.dp))
                CheckRow("Block FB Reels / YT Shorts", s.blockFbReels,  locked, vm::toggleBlockFbReels)
            }
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel("Custom Keywords", "Block specific words or phrases")
        CustomKeywordsUI(s, vm)
    }
}

@Composable
private fun CustomKeywordsUI(s: AdultBlockState, vm: AdultBlockViewModel) {
    val kb = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            OutlinedTextField(s.customInputText, { if (it.length <= 40) vm.setCustomInput(it) },
                Modifier.weight(1f), placeholder = { Text("e.g. badword", color = RC.GrayText, fontSize = 13.sp) },
                singleLine = true, shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RC.Teal, cursorColor = RC.Teal),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.addCustomKeyword(); kb?.hide() }),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
            Button({ vm.addCustomKeyword(); kb?.hide() }, shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RC.Teal),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                modifier = Modifier.height(52.dp), enabled = s.customInputText.isNotBlank()) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add", fontWeight = FontWeight.Bold)
            }
        }
        if (s.customKeywords.isEmpty()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(RC.Bg)
                .border(1.dp, RC.Border, RoundedCornerShape(10.dp)).padding(20.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FilterList, null, tint = RC.GrayText, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("No custom keywords yet", fontSize = 13.sp, color = RC.GrayText)
                }
            }
        } else {
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(10.dp),
                CardDefaults.cardColors(RC.CardBg), CardDefaults.cardElevation(0.dp),
                CardDefaults.outlinedCardBorder()) {
                s.customKeywords.forEachIndexed { i, kw ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Block, null, tint = RC.Red, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(kw.name, fontSize = 13.sp, color = RC.Dark, modifier = Modifier.weight(1f))
                        if (!s.isAdultFocusActive)
                            IconButton({ vm.removeCustomKeyword(kw) }, Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, null, tint = RC.GrayText, modifier = Modifier.size(15.dp))
                            }
                    }
                    if (i < s.customKeywords.lastIndex)
                        HorizontalDivider(color = RC.Border, modifier = Modifier.padding(horizontal = 14.dp))
                }
            }
        }
    }
}

// ── STRICT PROTOCOLS ────────────────────────────────────────
@Composable
private fun StrictProtocolsSection(s: AdultBlockState, vm: AdultBlockViewModel) {
    val locked = s.isAdultFocusActive
    Column(Modifier.background(RC.White).padding(horizontal = 16.dp, vertical = 14.dp)) {
        SectionLabel("Strict Protocol Settings", "Advanced protection layers")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StrictCard(Icons.Default.Visibility, "Silent Monitor", "Log & detect URLs silently",
                    s.silentMonitor, locked, onToggle = vm::toggleSilentMonitor, modifier = Modifier.weight(1f))
                StrictCard(Icons.Default.Dns, "Family DNS", "Cloudflare safe DNS filter",
                    s.familyDns, locked, onToggle = vm::toggleFamilyDns, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StrictCard(Icons.Default.Search, "Safe Search", "Force SafeSearch on Google",
                    s.safeSearch, locked, onToggle = vm::toggleSafeSearch, modifier = Modifier.weight(1f))
                StrictCard(Icons.Default.NoEncryption, "Block Incognito", "Close private windows",
                    s.blockIncognito, locked, onToggle = vm::toggleBlockIncognito, modifier = Modifier.weight(1f))
            }
            StrictCard(Icons.Default.AdminPanelSettings, "Strict Lock Mode",
                "Block Task Manager, Settings & Uninstallers during focus session",
                s.strictLockMode, locked, activeColor = RC.Red, onToggle = vm::toggleStrictLockMode)
        }
    }
}

// ── ADVANCED OPTIONS ─────────────────────────────────────────
@Composable
private fun AdvancedSection(s: AdultBlockState, vm: AdultBlockViewModel) {
    Column(Modifier.background(RC.White).padding(horizontal = 16.dp, vertical = 14.dp)) {
        SectionLabel("Advanced Options", "Nuclear-level protection tools")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 24h Lock card
            Card(
                Modifier.weight(1f).clickable { if (!s.is24hLockActive) vm.show24hConfirm() },
                RoundedCornerShape(12.dp),
                CardDefaults.cardColors(if (s.is24hLockActive) RC.RedLight else RC.CardBg),
                CardDefaults.cardElevation(0.dp),
                border = CardDefaults.outlinedCardBorder().let {
                    if (s.is24hLockActive) it.copy(width = 1.5.dp) else it
                }
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null,
                            tint = if (s.is24hLockActive) RC.Red else RC.Orange,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("24-Hour Lock", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            color = if (s.is24hLockActive) RC.Red else RC.Dark,
                            modifier = Modifier.weight(1f))
                        RasToggle(s.is24hLockActive, { if (!s.is24hLockActive) vm.show24hConfirm() },
                            !s.is24hLockActive, RC.Red)
                    }
                    Spacer(Modifier.height(4.dp))
                    if (s.is24hLockActive) {
                        val l = (s.lock24hEndTimeMs - System.currentTimeMillis()).coerceAtLeast(0)
                        Text("${l/3600000}h ${(l%3600000)/60000}m remaining", fontSize = 11.sp,
                            color = RC.Red, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Cannot be undone for 24h.", fontSize = 11.sp, color = RC.GrayText)
                    }
                }
            }
            // Reminders card
            Card(
                Modifier.weight(1f).clickable { vm.toggleReminders() },
                RoundedCornerShape(12.dp),
                CardDefaults.cardColors(if (s.periodicReminders) RC.TealLight else RC.CardBg),
                CardDefaults.cardElevation(0.dp),
                CardDefaults.outlinedCardBorder()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, null,
                            tint = if (s.periodicReminders) RC.Teal else RC.GrayText,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reminders", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        RasToggle(s.periodicReminders, vm::toggleReminders)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Quote popup every 25 mins.", fontSize = 11.sp, color = RC.GrayText)
                }
            }
        }
    }
}

// ── ACTIVE PROTECTIONS BAR ───────────────────────────────────
@Composable
private fun ActiveProtectionsBar(s: AdultBlockState) {
    Column(Modifier.background(RC.White).padding(horizontal = 16.dp, vertical = 14.dp)) {
        SectionLabel("Active Protections", "Current protection status")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Badge("URL Monitor",  Icons.Default.Visibility,         s.silentMonitor) }
            item { Badge("DNS Filter",   Icons.Default.Dns,                s.familyDns) }
            item { Badge("SafeSearch",   Icons.Default.Search,             s.safeSearch) }
            item { Badge("No Incognito", Icons.Default.NoEncryption,       s.blockIncognito) }
            item { Badge("Strict Lock",  Icons.Default.AdminPanelSettings, s.strictLockMode) }
            item { Badge("Focus On",     Icons.Default.Shield,             s.isAdultFocusActive || s.isStrictFocusActive) }
            item { Badge("24h Lock",     Icons.Default.Lock,               s.is24hLockActive) }
            item { Badge("Panic Mode",   Icons.Default.Warning,            s.isPanicActive) }
        }
    }
}

// ── Entry point called from NavHost ───────────────────────────────────────
@Composable
fun Adult_block() {
    AdultBlockScreen()
}


class AdultBlockService : AccessibilityService() {

    companion object {
        var instance: AdultBlockService? = null
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

    private var dynamicAdultList = listOf<String>()

    private val muslimQuotesBn = listOf("মুমিনদের বলুন, তারা যেন তাদের দৃষ্টি নত রাখে...", "লজ্জাশীলতা ঈমানের অঙ্গ।")
    private val muslimQuotesEn = listOf("Tell the believing men to reduce their vision...", "Modesty is a branch of faith.")
    private val hinduQuotesBn = listOf("যে মনকে নিয়ন্ত্রণ করতে পারে, তার মন তার সবচেয়ে বড় বন্ধু।", "কাম, ক্রোধ এবং লোভ—এই তিনটি নরকের দ্বার।")
    private val hinduQuotesEn = listOf("For him who has conquered the mind, the mind is the best of friends.", "Lust, anger, and greed are the three doors to hell.")
    private val christianQuotesBn = listOf("খারাপ সাহচর্য ভালো চরিত্র নষ্ট করে।", "অহংকার পতনের মূল।")
    private val christianQuotesEn = listOf("Bad company ruins good morals.", "Pride goes before destruction.")
    private val motivationalQuotesBn = listOf("সফলতা আসে ফোকাস থেকে, ডিস্ট্রাকশন থেকে নয়।", "যে নিজের মনকে নিয়ন্ত্রণ করতে পারে, সে পৃথিবী জয় করতে পারে।")
    private val motivationalQuotesEn = listOf("Success comes from focus, not from distraction.", "He who can control his mind can conquer the world.")

    // ── Logic States ───────────────────────────────────────────
    private var lastBlockTime: Long = 0
    private var lastLoggedUrl: String = ""
    
    // Panic Mode Logic
    private var isPanicActive = false
    private var panicEndTime = 0L

    // Periodic Popup Logic
    private val periodicHandler = Handler(Looper.getMainLooper())
    private var periodicRunnable: Runnable? = null

    // Deep Study Logic
    private var isDeepStudyActive = false
    private var isDeepStudyBreak = false
    private var windowManager: android.view.WindowManager? = null
    private var dsTimer: android.os.CountDownTimer? = null
    private var dsTimeLeftMillis: Long = 0
    
    // Overlays
    private var floatingTimerView: android.view.View? = null
    private var timerTextView: android.widget.TextView? = null
    private var breakScreenView: android.view.View? = null
    private var sessionCompleteView: android.view.View? = null
    private var fullScreenBlockView: android.view.View? = null
    private var isPopupVisible = false

    private var audioTrack: android.media.AudioTrack? = null
    private var isPlayingNoise = false
    private var noiseThread: Thread? = null

    private var initialX: Int = 0; private var initialY: Int = 0
    private var initialTouchX: Float = 0f; private var initialTouchY: Float = 0f
    private lateinit var recoveryPrefs: SharedPreferences
    private lateinit var adultPrefs: SharedPreferences // To fetch granular toggles

    override fun onCreate() {
        super.onCreate()
        DataManager.init(this)
        recoveryPrefs = getSharedPreferences("FocusRecovery", Context.MODE_PRIVATE)
        adultPrefs = getSharedPreferences("RasFocusAdultPrefs", Context.MODE_PRIVATE)
        createNotificationChannel()
        loadAdultSiteFile()
    }

    private fun loadAdultSiteFile() {
        try {
            val inputStream = assets.open("adultsite.txt")
            val text = inputStream.bufferedReader().use { it.readText() }
            dynamicAdultList = text.split("\n", "\r\n")
                .map { it.trim().lowercase().replace("*.", ".") }
                .filter { it.isNotEmpty() }
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
            notificationTimeout = 100 // Fast detection
        }
        this.serviceInfo = info

        startForeground(NOTIFICATION_ID, buildNotification("Protection is Active", "Monitoring your focus..."))
        startPeriodicPopupChecker()

        // Resume Deep Study if needed
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

    // ── Periodic Popup (Reminders) ───────────────────────────────────────
    private fun startPeriodicPopupChecker() {
        periodicRunnable = object : Runnable {
            override fun run() {
                if (DataManager.isPeriodicPopupsActive && (DataManager.isAdultFocusActive || DataManager.is24HourLockActive)) {
                    showFullScreenBlockPopup("REMINDER", getMotivationalQuote(), "Stay Focused, Stay Pure.", "#0CA8B0")
                }
                periodicHandler.postDelayed(this, 25 * 60 * 1000L) // 25 mins
            }
        }
        periodicHandler.postDelayed(periodicRunnable!!, 25 * 60 * 1000L)
    }

    private fun stopPeriodicPopupChecker() {
        periodicRunnable?.let { periodicHandler.removeCallbacks(it) }
    }

    fun activatePanicMode() {
        isPanicActive = true
        panicEndTime = System.currentTimeMillis() + 15 * 60 * 1000L
        showFullScreenBlockPopup("PANIC MODE ACTIVATED", "All browsers will be blocked for 15 minutes.", "Emergency Lock", "#F2930C")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "RasFocus Protection", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, Class.forName("com.rasel.RasFocus.MainActivity")), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title).setContentText(content).setSmallIcon(android.R.drawable.ic_secure)
            .setContentIntent(pendingIntent).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    private fun updateNotification(title: String, content: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    private fun isSystemApp(packageName: String): Boolean {
        return packageName.contains("launcher") || packageName.contains("systemui") ||
                packageName.contains("dialer") || packageName.contains("messaging") ||
                packageName.contains("inputmethod") || packageName.contains("keyboard") ||
                packageName == "com.rasel.RasFocus"
    }

    private fun isBrowserApp(packageName: String): Boolean {
        return packageName.contains("chrome") || packageName.contains("browser") ||
               packageName.contains("edge") || packageName.contains("firefox") ||
               packageName.contains("brave") || packageName.contains("opera")
    }

    // ── Silent Monitor Logging ───────────────────────────────────────────
    private fun logSilentUrl(windowTitle: String, url: String) {
        if (url.isEmpty() || url == lastLoggedUrl) return
        lastLoggedUrl = url
        try {
            val timeStr = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.getDefault()).format(Date())
            val logData = "[$timeStr] TITLE: $windowTitle | URL: $url\n"
            val logFile = File(applicationContext.filesDir, "silent_monitor_log.txt")
            FileOutputStream(logFile, true).use { it.write(logData.toByteArray()) }
        } catch (e: Exception) {
            android.util.Log.e("RasFocus", "Silent Monitor Log Error: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // Fetch Granular Toggles from SharedPreferences (Defaults to true to match C++ strictness)
        val cbAdultWeb = adultPrefs.getBoolean("cbAdultWeb", true)
        val cbHardcore = adultPrefs.getBoolean("cbHardcore", true)
        val cbRomantic = adultPrefs.getBoolean("cbRomantic", true)
        val cbFbReels = adultPrefs.getBoolean("cbFbReels", true)
        val cbYtShorts = adultPrefs.getBoolean("cbYtShorts", true)

        // ── 1. PANIC MODE CHECK (Priority #1) ────────────────────────────
        if (isPanicActive) {
            if (System.currentTimeMillis() < panicEndTime) {
                if (isBrowserApp(packageName)) {
                    multiLayerForceHome()
                    if (System.currentTimeMillis() - lastBlockTime > 5000) {
                        lastBlockTime = System.currentTimeMillis()
                        showFullScreenBlockPopup("PANIC MODE", "All browsers are disabled for 15 mins.", "Emergency Protocol Active", "#E74C3C")
                    }
                    return
                }
            } else {
                isPanicActive = false
            }
        }

        if (!DataManager.isFocusActive && !DataManager.isAdultFocusActive && !isDeepStudyActive && !DataManager.is24HourLockActive) return

        // ── 2. KEYBOARD REALTIME TYPING BLOCK (With Granular Checks) ──────
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val isKeyboardBlockEnabled = adultPrefs.getBoolean("keyboardTypingBlock", false)

            if (!isSystemApp(packageName) && isKeyboardBlockEnabled) {
                val source = event.source ?: return
                val typedText = source.text?.toString()?.lowercase() ?: event.text.joinToString(" ").lowercase()
                if (typedText.isBlank()) return

                val customKeywords = DataManager.userCustomAdultKeywords.map { it.lowercase() }
                
                // Only match if the respective setting is enabled
                val isHardcoreMatch = cbHardcore && hardcoreKeywords.any { typedText.contains(it) }
                val isRomanticMatch = cbRomantic && romanticKeywords.any { typedText.contains(it) }
                val isDynamicMatch  = cbAdultWeb && dynamicAdultList.any { site ->
                    val domain = site.replace(".", "").replace("www", "")
                    domain.length > 3 && typedText.contains(domain)
                }
                val isCustomMatch = customKeywords.isNotEmpty() && customKeywords.any { typedText.contains(it) }

                if (isHardcoreMatch || isRomanticMatch || isDynamicMatch || isCustomMatch) {
                    try {
                        val clearArgs = android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "") }
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
                        triggerAdultBlockAction(packageName, "Blocked Keyword Typed")
                    }
                    return
                }
            }
        }

        // ── 3. 24H LOCK SYNC ─────────────────────────────────────────────
        if (DataManager.is24HourLockActive) {
            if (System.currentTimeMillis() >= DataManager.lock24hEndTime) {
                DataManager.is24HourLockActive = false
                DataManager.isAdultFocusActive = false
            } else {
                DataManager.isAdultFocusActive = true
            }
        }

        // ── 4. URL & WINDOW DETECTION ────────────────────────────────────
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val rootNode = rootInActiveWindow ?: return
            var currentUrl = ""
            var windowTitle = ""

            if (isBrowserApp(packageName)) {
                currentUrl = extractUrlFromBrowser(rootNode).lowercase()
                windowTitle = extractWindowTitle(rootNode)
            }

            val screenText = event.text.joinToString(" ").lowercase()

            // ── Strict Protocols: Block Incognito ──
            val blockIncognito = adultPrefs.getBoolean("strictBlockIncognito", false)
            if (blockIncognito && isBrowserApp(packageName)) {
                if (screenText.contains("incognito") || screenText.contains("inprivate") || screenText.contains("private browsing") || windowTitle.lowercase().contains("incognito")) {
                    multiLayerForceHome()
                    showFullScreenBlockPopup("STRICT MODE", "Incognito / Private windows are not allowed.", "Strict Protocol Active", "#0CA8B0")
                    rootNode.recycle()
                    return
                }
            }

            // ── Strict Protocols: Settings & Uninstaller Block ──
            val strictLockMode = adultPrefs.getBoolean("strictLockMode", false)
            if (strictLockMode || DataManager.blockSettingsAndUninstall) {
                if (packageName.contains("com.android.settings") || packageName.contains("packageinstaller") || packageName.contains("taskmanager")) {
                    multiLayerForceHome()
                    showFullScreenBlockPopup("ACCESS DENIED", "System Settings / Uninstallers are locked.", "Strict Lock Mode", "#E74C3C")
                    rootNode.recycle()
                    return
                }
            }

            if (isDeepStudyActive && DataManager.isDeepStudyStrict) {
                checkDeepStudyBlocking(packageName, currentUrl)
            } else {
                checkAndBlockContent(packageName, currentUrl, screenText, windowTitle, cbAdultWeb, cbHardcore, cbRomantic, cbFbReels, cbYtShorts)
            }
            rootNode.recycle()
        }
    }

    private fun extractUrlFromBrowser(nodeInfo: AccessibilityNodeInfo?): String {
        if (nodeInfo == null) return ""
        if (nodeInfo.className == "android.widget.EditText") {
            val id = nodeInfo.viewIdResourceName
            if (id != null && (id.contains("url_bar") || id.contains("address_bar") || id.contains("search_box_text"))) {
                return nodeInfo.text?.toString() ?: ""
            }
        }
        for (i in 0 until nodeInfo.childCount) {
            val url = extractUrlFromBrowser(nodeInfo.getChild(i))
            if (url.isNotEmpty()) return url
        }
        return ""
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

    // ── MULTI-LAYER FORCE HOME LOGIC (Bulletproof Exit) ─────────────────
    private fun multiLayerForceHome() {
        // Layer 1: Soft Exit via Back Button
        performGlobalAction(GLOBAL_ACTION_BACK)
        Thread.sleep(100)
        
        // Layer 2: Standard Home Action
        val homeSuccess = performGlobalAction(GLOBAL_ACTION_HOME)
        
        // Layer 3: Hard Intent Fallback if System ignored Accessibility Home
        if (!homeSuccess) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            try {
                startActivity(homeIntent)
            } catch (e: Exception) {
                android.util.Log.e("RasFocus", "Failed Layer 3 Home Intent: ${e.message}")
            }
        }
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
            showFullScreenBlockPopup("STAY FOCUSED!", getMotivationalQuote(), "Reason: App/Website is restricted during Deep Study.", "#4A00E0")
        }
    }

    private fun checkAndBlockContent(packageName: String, url: String, screenText: String, windowTitle: String, 
                                     cbAdultWeb: Boolean, cbHardcore: Boolean, cbRomantic: Boolean, cbFbReels: Boolean, cbYtShorts: Boolean) {
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
                cbFbReels && ((packageName.contains("facebook") && screenText.contains("reels")) || url.contains("reel") || url.contains("instagram.com/reels")) -> {
                    shouldBlockNormal = true; blockReason = "FB / IG Reels are blocked!"
                }
            }

            // ── Silent Monitor (Only log if no violation occurred yet) ──
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
        } else if (shouldBlockNormal) {
            if (System.currentTimeMillis() - lastBlockTime < 5000) return
            lastBlockTime = System.currentTimeMillis()
            val mainMsg = if (DataManager.showQuotes) getReligiousQuote() else getMotivationalQuote()

            // তাৎক্ষণিক blocking screen
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                showFullScreenBlockPopup("ACCESS DENIED!", mainMsg, "Reason: $blockReason", "#0CA8B0")
            }

            if (isBrowserApp(packageName)) {
                // Browser: tab close + google new tab
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }, 200)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        val googleIntent = Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://www.google.com")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            setPackage(packageName)
                        }
                        startActivity(googleIntent)
                    } catch (_: Exception) { multiLayerForceHome() }
                }, 600)
            } else {
                multiLayerForceHome()
            }
        }
    }

    private fun triggerAdultBlockAction(packageName: String, reason: String) {
        if (System.currentTimeMillis() - lastBlockTime < 5000) return
        lastBlockTime = System.currentTimeMillis()

        DataManager.totalBlockedCount++
        DataManager.cleanStreakDays = 0

        // 1. তাৎক্ষণিক blocking screen দেখাও
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            showFullScreenBlockPopup("ASTAGFIRULLAH!", getReligiousQuote(), "Reason: $reason", "#F12B2C")
        }

        val isBrowser = isBrowserApp(packageName)

        if (isBrowser) {
            // 2. Browser: current tab বন্ধ করো
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                performGlobalAction(GLOBAL_ACTION_BACK)
            }, 200)

            // 3. নতুন tab এ google.com খোলো
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    val googleIntent = Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.google.com")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        setPackage(packageName)
                    }
                    startActivity(googleIntent)
                } catch (_: Exception) {
                    multiLayerForceHome()
                }
            }, 600)
        } else {
            multiLayerForceHome()
        }
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

    // ── Public: Stop Focus API (Password / Long Text) ────────────────────
    fun tryStopFocus(input: String): Boolean {
        if (DataManager.is24HourLockActive) return false
        
        // Mode 1: Friend Password
        if (DataManager.controlMode == 1) {
            val prefs = getSharedPreferences("RasFocusData", Context.MODE_PRIVATE)
            // FIX: Use hashed password comparison
            val storedHash = prefs.getString("friendPasswordHash", null)
            val isCorrect = if (storedHash != null) {
                DataManager.verifyPassword(input, storedHash)
            } else {
                val legacy = prefs.getString("friendPassword", "1234") ?: "1234"
                input == legacy
            }
            if (isCorrect) {
                DataManager.isAdultFocusActive = false
                return true
            }
            return false
        }
        
        // Mode 2: Long Text
        if (DataManager.controlMode == 2) {
            val savedLongText = adultPrefs.getString("savedLongText", "") ?: ""
            if (input.isNotEmpty() && input.trim() == savedLongText.trim()) {
                DataManager.isAdultFocusActive = false
                return true
            }
            return false
        }
        
        // Mode 0: Self
        DataManager.isAdultFocusActive = false
        return true
    }

    // ── Deep Study Session ────────────────────────────────────────────────
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
            try { windowManager?.addView(sessionCompleteView, params) } catch (e: Exception) {}
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
            try { windowManager?.addView(floatingTimerView, params) } catch (e: Exception) {}
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
            try { windowManager?.addView(breakScreenView, params) } catch (e: Exception) {}
        }
    }

    private fun removeBreakScreenOverlay() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            breakScreenView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }; breakScreenView = null
        }
    }

    private fun showFullScreenBlockPopup(title: String, message: String, reasonInfo: String, bgColorHex: String) {
        if (isPopupVisible) return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (isPopupVisible) return@post
            isPopupVisible = true

            val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            windowManager = wm
            val dp  = resources.displayMetrics.density
            val sw  = resources.displayMetrics.widthPixels
            val sh  = resources.displayMetrics.heightPixels
            val ctx = this

            val tealColor  = android.graphics.Color.parseColor("#00BFA5")
            val teal700    = android.graphics.Color.parseColor("#00897B")
            val teal50     = android.graphics.Color.parseColor("#E0F2F1")
            val tealFaded  = android.graphics.Color.parseColor("#3300BFA5")
            val textMid    = android.graphics.Color.parseColor("#4A6080")
            val whiteFull  = android.graphics.Color.WHITE

            val isAdult  = bgColorHex == "#F12B2C"
            val isStudy  = bgColorHex == "#4A00E0"
            val iconEmoji = when { isAdult -> "🚫"; isStudy -> "📚"; else -> "🛡" }

            // Full-screen white container
            val outerWrap = android.widget.FrameLayout(ctx).apply {
                setBackgroundColor(whiteFull)
            }

            // Teal gradient header (top 36% of screen)
            val headerGrad = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(teal700, tealColor)
            )
            val headerView = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                background = headerGrad
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    (sh * 0.36f).toInt()
                ).apply { gravity = android.view.Gravity.TOP }
            }

            // Top badge + dismiss row
            val topRow = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins((20*dp).toInt(), (44*dp).toInt(), (20*dp).toInt(), 0) }
            }
            val badgeBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#33FFFFFF"))
                cornerRadius = 100f * dp
            }
            val badge = android.widget.TextView(ctx).apply {
                text = "🛡  RasFocus+"
                textSize = 10f
                setTextColor(whiteFull)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
                background = badgeBg
                val hp = (12*dp).toInt(); val vp = (5*dp).toInt()
                setPadding(hp, vp, hp, vp)
            }
            val spacer = android.widget.Space(ctx).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(0, 1, 1f)
            }
            val dismissBtn = android.widget.TextView(ctx).apply {
                text = "✕ Close"
                textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#CCFFFFFF"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                val p = (10*dp).toInt(); setPadding(p, p, p, p)
                setOnClickListener { removeFullScreenBlockPopup() }
            }
            topRow.addView(badge); topRow.addView(spacer); topRow.addView(dismissBtn)
            headerView.addView(topRow)

            // Icon + title in header center
            val iconCircleBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#33FFFFFF"))
            }
            val iconCircle = android.widget.TextView(ctx).apply {
                text = iconEmoji
                textSize = 36f
                gravity = android.view.Gravity.CENTER
                background = iconCircleBg
                val s = (84*dp).toInt()
                layoutParams = android.widget.LinearLayout.LayoutParams(s, s).apply {
                    setMargins(0, (16*dp).toInt(), 0, (14*dp).toInt())
                }
            }
            val blockedLabel = android.widget.TextView(ctx).apply {
                text = "BLOCKED"
                textSize = 10f
                setTextColor(android.graphics.Color.parseColor("#CDFFFFFF"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                letterSpacing = 0.3f
                gravity = android.view.Gravity.CENTER
            }
            val titleView = android.widget.TextView(ctx).apply {
                text = title
                textSize = 20f
                setTextColor(whiteFull)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = android.view.Gravity.CENTER
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins((24*dp).toInt(), (4*dp).toInt(), (24*dp).toInt(), (20*dp).toInt()) }
            }
            headerView.addView(iconCircle)
            headerView.addView(blockedLabel)
            headerView.addView(titleView)

            // White body scroll
            val bodyScroll = android.widget.ScrollView(ctx).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                ).apply { topMargin = (sh * 0.36f).toInt() }
            }
            val body = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                val p = (24*dp).toInt()
                setPadding(p, (20*dp).toInt(), p, (32*dp).toInt())
            }

            // Reason info chip
            val chipBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(teal50)
                cornerRadius = 12f * dp
                setStroke((1f*dp).toInt(), tealFaded)
            }
            val reasonRow = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                background = chipBg
                val p = (14*dp).toInt(); val vp = (12*dp).toInt()
                setPadding(p, vp, p, vp)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val infoIcon = android.widget.TextView(ctx).apply {
                text = "ℹ"
                textSize = 16f
                setTextColor(tealColor)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, (10*dp).toInt(), 0) }
            }
            val reasonView = android.widget.TextView(ctx).apply {
                text = reasonInfo
                textSize = 13f
                setTextColor(textMid)
                setLineSpacing(0f, 1.4f)
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            reasonRow.addView(infoIcon); reasonRow.addView(reasonView)
            body.addView(reasonRow)

            // Divider
            val divider = android.view.View(ctx).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#E8EFF5"))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (1f*dp).toInt()
                ).apply { setMargins(0, (18*dp).toInt(), 0, (14*dp).toInt()) }
            }
            body.addView(divider)

            // Quote block
            val quoteSectionLabel = android.widget.TextView(ctx).apply {
                text = "💭  অনুপ্রেরণা"
                textSize = 11f
                setTextColor(tealColor)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                letterSpacing = 0.1f
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, (10*dp).toInt()) }
            }
            body.addView(quoteSectionLabel)

            val quoteCardBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#F0FAFA"))
                cornerRadius = 14f * dp
                setStroke((1f*dp).toInt(), android.graphics.Color.parseColor("#CCE8E5"))
            }
            val quoteRow = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                background = quoteCardBg
                val p = (14*dp).toInt(); setPadding(p, p, p, p)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val accentBar = android.view.View(ctx).apply {
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    setColor(tealColor); cornerRadius = 3f * dp
                }
                background = bg
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    (3f*dp).toInt(), android.widget.LinearLayout.LayoutParams.MATCH_PARENT
                ).apply { setMargins(0, 0, (12*dp).toInt(), 0) }
            }
            val quoteCol = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val quoteView = android.widget.TextView(ctx).apply {
                text = "“$message”"
                textSize = 13f
                setTextColor(textMid)
                setTypeface(typeface, android.graphics.Typeface.ITALIC)
                setLineSpacing(0f, 1.4f)
            }
            val authorView = android.widget.TextView(ctx).apply {
                text = "— RasFocus+"
                textSize = 11f
                setTextColor(tealColor)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, (4*dp).toInt(), 0, 0)
            }
            quoteCol.addView(quoteView); quoteCol.addView(authorView)
            quoteRow.addView(accentBar); quoteRow.addView(quoteCol)
            body.addView(quoteRow)

            // Active tag
            val tagBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#E0F2F1"))
                cornerRadius = 20f * dp
                setStroke((1f*dp).toInt(), tealFaded)
            }
            val tagText = android.widget.TextView(ctx).apply {
                text = "● Active — Blocked by RasFocus+"
                textSize = 10f
                setTextColor(tealColor)
                letterSpacing = 0.05f
                background = tagBg
                val hp = (12*dp).toInt(); val vp = (5*dp).toInt()
                setPadding(hp, vp, hp, vp)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, (16*dp).toInt(), 0, 0) }
            }
            body.addView(tagText)

            bodyScroll.addView(body)
            outerWrap.addView(headerView)
            outerWrap.addView(bodyScroll)

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else @Suppress("DEPRECATION") android.view.WindowManager.LayoutParams.TYPE_PHONE

            val params = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                type,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.graphics.PixelFormat.OPAQUE
            ).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.START }

            fullScreenBlockView = outerWrap
            try {
                wm.addView(fullScreenBlockView, params)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ removeFullScreenBlockPopup() }, 5000)
            } catch (e: Exception) { isPopupVisible = false }
        }
    }

    private fun removeFullScreenBlockPopup() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            fullScreenBlockView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
            fullScreenBlockView = null; isPopupVisible = false
        }
    }

    override fun onInterrupt() {}
}