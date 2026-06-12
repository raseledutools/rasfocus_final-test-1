package com.rasel.RasFocus.selfcontrol

// ════════════════════════════════════════════════════════════════════════════
//  singel_apps.kt  —  ACCESSIBILITY-INDEPENDENT APP BLOCKER (PRO VERSION)
//
//  Blocking method: UsageStatsManager (NO Accessibility needed)
//   - Handler loop প্রতি 300ms এ foreground app check করে (UsageEvents ব্যবহার করে)
//   - System Auto-kill বন্ধ করতে Battery Optimization Bypass যুক্ত করা হয়েছে
//   - Settings block সাপোর্ট করে (Force Stop প্রতিরোধ করতে)
// ════════════════════════════════════════════════════════════════════════════

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.*
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import java.io.*
import java.net.*
import java.util.Calendar
import java.util.concurrent.Executors

// ════════════════════════════════════════════════════════════════════════════
//  1. DATA MODELS
// ════════════════════════════════════════════════════════════════════════════

enum class BlockMethod {
    TIME_RANGE,   // নির্দিষ্ট ঘণ্টায় block
    DAILY_LIMIT,  // দিনে মোট X সময়ের বেশি হলে block
    HOURLY_LIMIT, // প্রতি ঘণ্টায় X মিনিটের বেশি হলে block
    PASSWORD,     // password দিয়ে unlock
    LONG_TEXT     // text টাইপ করে unlock
}

data class BlockConfig(
    val method:        BlockMethod = BlockMethod.TIME_RANGE,
    val timeFrom:      String      = "09:00",
    val timeTo:        String      = "17:00",
    val dailyLimitMin: Int         = 60,
    val hourlyLimitMin:Int         = 20,
    val password:      String      = "",
    val longText:      String      = ""
)

// ════════════════════════════════════════════════════════════════════════════
//  2. STORAGE — SharedPreferences (blocker_prefs)
// ════════════════════════════════════════════════════════════════════════════

object BlockedData {
    private const val PREFS   = "blocker_prefs"
    private const val K_APPS  = "blocked_apps_v2"
    private const val K_SITES = "blocked_sites"
    private val gson = Gson()

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isBlockingActive(ctx: Context): Boolean =
        prefs(ctx).getBoolean("is_blocking_active", false)

    fun setBlockingActive(ctx: Context, active: Boolean) {
        prefs(ctx).edit().putBoolean("is_blocking_active", active).apply()
    }

    fun getBlockedApps(ctx: Context): MutableMap<String, BlockConfig> {
        val json = prefs(ctx).getString(K_APPS, null) ?: return mutableMapOf()
        val type = object : TypeToken<MutableMap<String, BlockConfig>>() {}.type
        return gson.fromJson(json, type) ?: mutableMapOf()
    }

    fun blockApp(ctx: Context, pkg: String, cfg: BlockConfig) {
        val map = getBlockedApps(ctx)
        map[pkg] = cfg
        prefs(ctx).edit().putString(K_APPS, gson.toJson(map)).apply()
    }

    fun unblockApp(ctx: Context, pkg: String) {
        val map = getBlockedApps(ctx)
        map.remove(pkg)
        prefs(ctx).edit().putString(K_APPS, gson.toJson(map)).apply()
    }

    fun getAppBlockConfig(ctx: Context, pkg: String): BlockConfig? =
        getBlockedApps(ctx)[pkg]

    fun getBlockedSites(ctx: Context): MutableMap<String, BlockConfig> {
        val json = prefs(ctx).getString(K_SITES, null) ?: return mutableMapOf()
        val type = object : TypeToken<MutableMap<String, BlockConfig>>() {}.type
        return gson.fromJson(json, type) ?: mutableMapOf()
    }

    fun blockSite(ctx: Context, domain: String, cfg: BlockConfig) {
        val map = getBlockedSites(ctx)
        map[domain.lowercase().trim()] = cfg
        prefs(ctx).edit().putString(K_SITES, gson.toJson(map)).apply()
    }

    fun unblockSite(ctx: Context, domain: String) {
        val map = getBlockedSites(ctx)
        map.remove(domain.lowercase().trim())
        prefs(ctx).edit().putString(K_SITES, gson.toJson(map)).apply()
    }

    fun isSiteBlocked(ctx: Context, domain: String): Boolean {
        val d   = domain.lowercase().removePrefix("www.")
        val map = getBlockedSites(ctx)
        return map[d] != null || map.keys.any { d.endsWith(".$it") || d == it }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  3. USAGE HELPER (UPDATED FOR 100% REAL-TIME BLOCKING)
// ════════════════════════════════════════════════════════════════════════════

object UsageHelper {
    fun getForegroundApp(ctx: Context): String? {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        // গত ১০ সেকেন্ডের রিয়েল-টাইম ইভেন্ট চেক করবে
        val events = usm.queryEvents(now - 10_000L, now)
        var currentApp: String? = null
        val event = android.app.usage.UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // ACTIVITY_RESUMED মানে অ্যাপটি স্ক্রিনে ফোকাস পেয়েছে
            if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                currentApp = event.packageName
            }
        }
        return currentApp
    }

    fun getDailyUsageMinutes(ctx: Context, pkg: String): Int {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        val now        = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
            ?: return 0
        val ms = stats.filter { it.packageName == pkg }.sumOf { it.totalTimeInForeground }
        return (ms / 60_000L).toInt()
    }

    fun getHourlyUsageMinutes(ctx: Context, pkg: String): Int {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startOfHour = cal.timeInMillis
        val now         = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfHour, now)
            ?: return 0
        val ms = stats.filter { it.packageName == pkg }.sumOf { it.totalTimeInForeground }
        return (ms / 60_000L).toInt()
    }

    fun isInTimeRange(from: String, to: String): Boolean {
        val cal  = Calendar.getInstance()
        val now  = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val f    = from.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
        val t    = to.split(":").let   { it[0].toInt() * 60 + it[1].toInt() }
        return if (f <= t) now in f..t else now >= f || now <= t
    }

    fun shouldBlock(ctx: Context, pkg: String, cfg: BlockConfig): Boolean = when (cfg.method) {
        BlockMethod.TIME_RANGE   -> isInTimeRange(cfg.timeFrom, cfg.timeTo)
        BlockMethod.DAILY_LIMIT  -> getDailyUsageMinutes(ctx, pkg)  >= cfg.dailyLimitMin
        BlockMethod.HOURLY_LIMIT -> getHourlyUsageMinutes(ctx, pkg) >= cfg.hourlyLimitMin
        BlockMethod.PASSWORD     -> true
        BlockMethod.LONG_TEXT    -> true
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  4. APP BLOCKER FOREGROUND SERVICE
// ════════════════════════════════════════════════════════════════════════════

class AppBlockerService : Service() {

    private val handler         = Handler(Looper.getMainLooper())
    private var lastBlocked     = ""
    private var lastBlockedTime = 0L
    private var unlockedPkg     = ""
    private var unlockedTime    = 0L

    companion object {
        private const val CHANNEL_ID   = "app_blocker_ch"
        private const val NOTIF_ID     = 2001
        private const val POLL_MS      = 300L
        private const val REBLOCK_MS   = 1_500L
        private const val GRACE_MS     = 5_000L

        fun start(ctx: Context) =
            ContextCompat.startForegroundService(ctx, Intent(ctx, AppBlockerService::class.java))

        fun stop(ctx: Context) =
            ctx.stopService(Intent(ctx, AppBlockerService::class.java))
    }

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == BlockingActivity.ACTION_RESET_BLOCK) {
                val pkg = intent.getStringExtra(BlockingActivity.EXTRA_PKG) ?: return
                unlockedPkg  = pkg
                unlockedTime = System.currentTimeMillis()
                lastBlocked  = ""
                lastBlockedTime = 0L
            }
        }
    }

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkAndBlock()
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotif())
        val filter = IntentFilter(BlockingActivity.ACTION_RESET_BLOCK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(unlockReceiver, filter)
        }
        handler.post(checkRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY নিশ্চিত করবে সিস্টেম কিল করলেও আবার রিস্টার্ট হবে
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable)
        try { unregisterReceiver(unlockReceiver) } catch (_: Exception) {}
        // যদি সার্ভিস মরে যায়, আবার অটো স্টার্ট করার চেষ্টা
        if (BlockedData.isBlockingActive(this)) {
            val broadcastIntent = Intent(this, SingleAppsBootReceiver::class.java)
            broadcastIntent.action = "RestartService"
            sendBroadcast(broadcastIntent)
        }
        super.onDestroy()
    }

    private fun checkAndBlock() {
        if (!BlockedData.isBlockingActive(this)) return

        val fg = UsageHelper.getForegroundApp(this) ?: return

        // ── SECURITY UPDATE ── 
        // "com.android.settings" skip লিস্ট থেকে বাদ দেওয়া হয়েছে! 
        // ইউজার চাইলে Settings অ্যাপ ব্লক করতে পারবে যাতে কেউ Force Stop করতে না পারে।
        val skipSet = setOf(
            packageName,
            "com.android.systemui",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher"
        )
        if (fg in skipSet) { lastBlocked = ""; return }

        val cfg = BlockedData.getAppBlockConfig(this, fg) ?: run {
            lastBlocked = ""
            return
        }

        val now = System.currentTimeMillis()
        if (fg == unlockedPkg && now - unlockedTime < GRACE_MS) return

        if (UsageHelper.shouldBlock(this, fg, cfg)) {
            if (lastBlocked == fg && now - lastBlockedTime < REBLOCK_MS) return
            lastBlocked     = fg
            lastBlockedTime = now

            val label = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(fg, 0)
                ).toString()
            } catch (e: Exception) { fg }

            val intent = Intent(this, BlockingActivity::class.java).apply {
                // AGGRESSIVE FLAGS: EXCLUDE_FROM_RECENTS যুক্ত করা হয়েছে
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK    or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK  or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP   or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
                putExtra("pkg",            fg)
                putExtra("appName",        label)
                putExtra("method",         cfg.method.name)
                putExtra("password",       cfg.password)
                putExtra("longText",       cfg.longText)
                putExtra("timeFrom",       cfg.timeFrom)
                putExtra("timeTo",         cfg.timeTo)
                putExtra("dailyLimitMin",  cfg.dailyLimitMin)
                putExtra("hourlyLimitMin", cfg.hourlyLimitMin)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            if (lastBlocked == fg) {
                lastBlocked     = ""
                lastBlockedTime = 0L
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "App Blocker", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "App blocking service" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotif() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("🛡️ RasFocus+ Protection Active")
        .setContentText("App Blocker ব্যাকগ্রাউন্ডে চলছে...")
        .setSmallIcon(android.R.drawable.ic_lock_lock)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, SingleAppsActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        ).build()
}

// ════════════════════════════════════════════════════════════════════════════
//  5. VPN SERVICE — Website Blocker
// ════════════════════════════════════════════════════════════════════════════

class BlockerVpnService : VpnService() {

    private var vpnThread: Thread?               = null
    private var vpnIface:  ParcelFileDescriptor? = null
    private val executor = Executors.newCachedThreadPool()

    companion object {
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val DNS_SERVER  = "8.8.8.8"
        private const val DNS_PORT    = 53

        fun start(ctx: Context) { ctx.startService(Intent(ctx, BlockerVpnService::class.java)) }
        fun stop(ctx: Context)  { ctx.stopService(Intent(ctx, BlockerVpnService::class.java)) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startVpn(); return START_STICKY
    }

    private fun startVpn() {
        try {
            vpnIface = Builder()
                .addAddress(VPN_ADDRESS, 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(DNS_SERVER)
                .setSession("BlockerVPN")
                .setBlocking(true)
                .establish() ?: return
            vpnThread = Thread({ runLoop() }, "VpnLoop").apply { isDaemon = true; start() }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun runLoop() {
        val iface     = vpnIface ?: return
        val inStream  = FileInputStream(iface.fileDescriptor)
        val outStream = FileOutputStream(iface.fileDescriptor)
        val buf       = ByteArray(32767)
        while (!Thread.interrupted()) {
            try {
                val len = inStream.read(buf); if (len <= 0) continue
                val ipVer = (buf[0].toInt() shr 4) and 0xF
                if (ipVer != 4) { outStream.write(buf, 0, len); continue }
                val proto = buf[9].toInt() and 0xFF
                if (proto != 17) { outStream.write(buf, 0, len); continue }
                val ihl     = (buf[0].toInt() and 0xF) * 4
                val dstPort = ((buf[ihl + 2].toInt() and 0xFF) shl 8) or (buf[ihl + 3].toInt() and 0xFF)
                if (dstPort != DNS_PORT) { outStream.write(buf, 0, len); continue }
                val dnsData = buf.copyOfRange(ihl + 8, len)
                val domain  = parseDnsQuery(dnsData)
                if (domain != null && BlockedData.isSiteBlocked(this, domain)) {
                    outStream.write(wrapUdpIp(buf, ihl, nxDomainResponse(dnsData)))
                } else {
                    val rawPkt = buf.copyOf(len); val rawIhl = ihl
                    executor.submit {
                        try {
                            val resp   = forwardDns(dnsData) ?: return@submit
                            val packet = wrapUdpIp(rawPkt, rawIhl, resp)
                            synchronized(outStream) { outStream.write(packet) }
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) { if (Thread.interrupted()) break }
        }
    }

    private fun parseDnsQuery(dns: ByteArray): String? = try {
        var i = 12
        buildString {
            while (i < dns.size) {
                val len = dns[i].toInt() and 0xFF; if (len == 0) break
                if (isNotEmpty()) append('.')
                append(String(dns, i + 1, len)); i += len + 1
            }
        }.lowercase().ifEmpty { null }
    } catch (_: Exception) { null }

    private fun nxDomainResponse(q: ByteArray) = q.copyOf().also {
        it[2] = (it[2].toInt() or 0x80).toByte(); it[3] = (it[3].toInt() or 0x83).toByte()
    }

    private fun forwardDns(query: ByteArray): ByteArray? {
        val sock = DatagramSocket().apply { soTimeout = 3000 }
        sock.send(DatagramPacket(query, query.size, InetAddress.getByName(DNS_SERVER), DNS_PORT))
        val resp = ByteArray(512); val rPkt = DatagramPacket(resp, resp.size)
        sock.receive(rPkt); sock.close(); return resp.copyOf(rPkt.length)
    }

    private fun wrapUdpIp(orig: ByteArray, ihl: Int, dns: ByteArray): ByteArray {
        val udpLen = 8 + dns.size; val totalLen = ihl + udpLen; val out = ByteArray(totalLen)
        System.arraycopy(orig, 0, out, 0, ihl)
        for (i in 0..3) { out[12 + i] = orig[16 + i]; out[16 + i] = orig[12 + i] }
        out[2] = (totalLen shr 8).toByte(); out[3] = (totalLen and 0xFF).toByte()
        out[10] = 0; out[11] = 0
        out[ihl] = orig[ihl + 2]; out[ihl + 1] = orig[ihl + 3]
        out[ihl + 2] = orig[ihl]; out[ihl + 3] = orig[ihl + 1]
        out[ihl + 4] = (udpLen shr 8).toByte(); out[ihl + 5] = (udpLen and 0xFF).toByte()
        out[ihl + 6] = 0; out[ihl + 7] = 0
        System.arraycopy(dns, 0, out, ihl + 8, dns.size)
        return out
    }

    override fun onDestroy() {
        vpnThread?.interrupt(); vpnIface?.close(); executor.shutdown(); super.onDestroy()
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  6. BOOT RECEIVER (Auto Restart Handling)
// ════════════════════════════════════════════════════════════════════════════

class SingleAppsBootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "RestartService") {
            if (BlockedData.isBlockingActive(ctx)) AppBlockerService.start(ctx)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  7. BLOCKING ACTIVITY — block screen
// ════════════════════════════════════════════════════════════════════════════

class BlockingActivity : ComponentActivity() {

    companion object {
        const val ACTION_RESET_BLOCK = "com.rasel.RasFocus.RESET_BLOCK"
        const val EXTRA_PKG          = "reset_pkg"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg           = intent.getStringExtra("pkg")      ?: ""
        val appName       = intent.getStringExtra("appName")  ?: pkg
        val method        = BlockMethod.valueOf(intent.getStringExtra("method") ?: "TIME_RANGE")
        val password      = intent.getStringExtra("password") ?: ""
        val longText      = intent.getStringExtra("longText") ?: ""
        val timeFrom      = intent.getStringExtra("timeFrom") ?: ""
        val timeTo        = intent.getStringExtra("timeTo")   ?: ""
        val dailyLimit    = intent.getIntExtra("dailyLimitMin", 60)
        val hourlyLimit   = intent.getIntExtra("hourlyLimitMin", 20)

        setContent {
            MaterialTheme {
                BlockingScreen(
                    appName      = appName,
                    method       = method,
                    password     = password,
                    longText     = longText,
                    timeFrom     = timeFrom,
                    timeTo       = timeTo,
                    dailyLimit   = dailyLimit,
                    hourlyLimit  = hourlyLimit,
                    onGoHome     = { goHome() },
                    onUnlocked   = {
                        // SECURE BROADCAST: শুধু নিজের অ্যাপেই ব্রডকাস্ট পাঠানো হবে
                        val unlockIntent = Intent(ACTION_RESET_BLOCK)
                            .putExtra(EXTRA_PKG, pkg)
                            .setPackage(packageName) // <--- MASTERSTROKE: ফেক ব্রডকাস্ট প্রতিরোধ 
                        sendBroadcast(unlockIntent)
                        finish()
                    }
                )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = goHome()

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME); flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }
}

// ── Block Screen UI ───────────────────────────────────────────────────────────

@Composable
fun BlockingScreen(
    appName:     String,
    method:      BlockMethod,
    password:    String,
    longText:    String,
    timeFrom:    String,
    timeTo:      String,
    dailyLimit:  Int,
    hourlyLimit: Int,
    onGoHome:    () -> Unit,
    onUnlocked:  () -> Unit
) {
    var input   by remember { mutableStateOf("") }
    var error   by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }

    val teal    = Color(0xFF00BFA5)
    val teal700 = Color(0xFF00897B)
    val tealBg  = Color(0xFFE0F2F1)
    val tealFad = Color(0x2200BFA5)
    val textDk  = Color(0xFF1A2332)
    val textMd  = Color(0xFF4A6080)
    val white   = Color.White

    fun verify() = when (method) {
        BlockMethod.PASSWORD  -> input == password
        BlockMethod.LONG_TEXT -> input.trim().equals(longText.trim(), ignoreCase = true)
        else                  -> false
    }

    if (success) {
        LaunchedEffect(Unit) { delay(600); onUnlocked() }
        Box(Modifier.fillMaxSize().background(teal), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.CheckCircle, null, tint = white, modifier = Modifier.size(80.dp))
                Text("আনলক হয়েছে!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = white)
            }
        }
        return
    }

    val methodIcon = when (method) {
        BlockMethod.TIME_RANGE   -> Icons.Default.Schedule
        BlockMethod.DAILY_LIMIT  -> Icons.Default.Today
        BlockMethod.HOURLY_LIMIT -> Icons.Default.HourglassEmpty
        BlockMethod.PASSWORD     -> Icons.Default.Lock
        BlockMethod.LONG_TEXT    -> Icons.Default.EditNote
        else                     -> Icons.Default.Block
    }

    Box(Modifier.fillMaxSize().background(white)) {
        Column(Modifier.fillMaxSize()) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.38f)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(teal700, teal)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(horizontal = 20.dp, vertical = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = white.copy(alpha = 0.2f),
                    ) {
                        Text(
                            "🛡  RasFocus+",
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = white, letterSpacing = 0.08.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onGoHome) {
                        Icon(Icons.Default.Close, null, tint = white.copy(0.8f))
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(90.dp).clip(CircleShape).background(white.copy(0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Block, null, tint = white, modifier = Modifier.size(48.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "BLOCKED", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = white.copy(0.8f), letterSpacing = 0.3.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "\"$appName\" ব্লক করা আছে",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = white, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = tealBg,
                    border = BorderStroke(1.dp, tealFad)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(methodIcon, null, tint = teal, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            when (method) {
                                BlockMethod.TIME_RANGE -> {
                                    Text("ব্লক সময়: $timeFrom – $timeTo", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textDk)
                                    Text("এই সময়ের মধ্যে ব্যবহার করা যাবে না।", fontSize = 12.sp, color = textMd)
                                }
                                BlockMethod.DAILY_LIMIT -> {
                                    val h = dailyLimit / 60; val m = dailyLimit % 60
                                    val label = if (h > 0) "${h}ঘণ্টা ${m}মিনিট" else "${m}মিনিট"
                                    Text("দৈনিক সীমা: $label", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textDk)
                                    Text("আজকের ব্যবহারের সীমা শেষ।", fontSize = 12.sp, color = textMd)
                                }
                                BlockMethod.HOURLY_LIMIT -> {
                                    Text("ঘণ্টায় সীমা: ${hourlyLimit}মিনিট", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textDk)
                                    Text("এই ঘণ্টায় সীমা শেষ হয়ে গেছে।", fontSize = 12.sp, color = textMd)
                                }
                                else -> {
                                    Text("অ্যাপটি ব্লক করা আছে।", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textDk)
                                }
                            }
                        }
                    }
                }

                when (method) {
                    BlockMethod.PASSWORD -> {
                        OutlinedTextField(
                            value = input, onValueChange = { input = it; error = false },
                            label = { Text("পাসওয়ার্ড দিন") },
                            visualTransformation = PasswordVisualTransformation(),
                            isError = error,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = teal, unfocusedBorderColor = tealFad,
                                focusedLabelColor = teal,
                                errorBorderColor = Color(0xFFEF4444)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (error) Text("পাসওয়ার্ড ভুল!", color = Color(0xFFEF4444), fontSize = 13.sp)
                        Button(
                            onClick = { if (verify()) success = true else error = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = teal),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) { Text("আনলক করুন", color = white, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                    }
                    BlockMethod.LONG_TEXT -> {
                        Surface(shape = RoundedCornerShape(12.dp), color = tealBg) {
                            Text(
                                "\"$longText\"", fontSize = 13.sp, color = textMd,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                modifier = Modifier.fillMaxWidth().padding(14.dp)
                            )
                        }
                        OutlinedTextField(
                            value = input, onValueChange = { input = it; error = false },
                            label = { Text("এখানে টাইপ করুন...") },
                            isError = error, minLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = teal, unfocusedBorderColor = tealFad,
                                focusedLabelColor = teal
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (error) Text("টেক্সট মিলছে না!", color = Color(0xFFEF4444), fontSize = 13.sp)
                        Button(
                            onClick = { if (verify()) success = true else error = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = teal),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) { Text("আনলক করুন", color = white, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                    }
                    else -> {}
                }

                OutlinedButton(
                    onClick = onGoHome,
                    border = BorderStroke(1.5.dp, teal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.Home, null, tint = teal, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("হোমে ফিরুন", color = teal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  8. MAIN ACTIVITY + FULL COMPOSE UI
// ════════════════════════════════════════════════════════════════════════════

private val Teal     = Color(0xFF0D9488)
private val TealDk   = Color(0xFF0F766E)
private val TealLt   = Color(0xFFCCFBF1)
private val TealSurf = Color(0xFFF0FDFA)
private val TealAcc  = Color(0xFF5EEAD4)
private val RedBl    = Color(0xFFEF4444)
private val RedLt    = Color(0xFFFEE2E2)
private val TxtPri   = Color(0xFF0F172A)
private val TxtSec   = Color(0xFF64748B)
private val BdrCol   = Color(0xFFE2E8F0)
private val Wht      = Color(0xFFFFFFFF)

data class InstalledApp(val name: String, val packageName: String, val icon: ImageBitmap? = null)

suspend fun loadInstalledApps(ctx: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
    val pm = ctx.packageManager
    pm.queryIntentActivities(
        android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }, PackageManager.GET_META_DATA
    )
        .map { ri ->
            val pkg = ri.activityInfo.packageName
            val label = ri.loadLabel(pm).toString()
            val icon: ImageBitmap? = try {
                pm.getApplicationIcon(pkg).toBitmap(48, 48).asImageBitmap()
            } catch (_: Exception) { null }
            InstalledApp(label, pkg, icon)
        }
        .filter { it.packageName != ctx.packageName }
        .sortedBy { it.name.lowercase() }
}

data class UiSite(val id: String, val domain: String)

class SingleAppsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(primary = Teal, onPrimary = Wht, primaryContainer = TealLt, surface = Wht, background = Wht)
            ) { BlockerRoot() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasUsagePerm()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            return
        }

        // ── BATTERY OPTIMIZATION BYPASS ──
        // System যেন সার্ভিসটি কিল না করে, তাই ব্যাটারি অপ্টিমাইজেশন ডিসেবল করা হচ্ছে
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
            return
        }

        BlockedData.setBlockingActive(this, true)
        AppBlockerService.start(this)
    }

    private fun hasUsagePerm(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
        else
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  9. BLOCKERROOT — Apps + Sites tabs
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockerRoot() {
    val ctx          = LocalContext.current
    var tab          by remember { mutableStateOf(0) }
    var installed    by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var appsLoading  by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        installed   = loadInstalledApps(ctx)
        appsLoading = false
    }

    var blockedApps  by remember { mutableStateOf(BlockedData.getBlockedApps(ctx)) }
    var blockedSites by remember { mutableStateOf(BlockedData.getBlockedSites(ctx)) }

    fun refreshApps()  { blockedApps  = BlockedData.getBlockedApps(ctx) }
    fun refreshSites() { blockedSites = BlockedData.getBlockedSites(ctx) }

    val sites = remember {
        mutableStateListOf(
            UiSite("fb_s",  "facebook.com"),
            UiSite("ig_s",  "instagram.com"),
            UiSite("tw_s",  "twitter.com"),
            UiSite("yt_s",  "youtube.com"),
            UiSite("rd_s",  "reddit.com"),
            UiSite("pin_s", "pinterest.com"),
            UiSite("eb_s",  "ebay.com")
        )
    }

    var showModal     by remember { mutableStateOf(false) }
    var pendingPkg    by remember { mutableStateOf<String?>(null) }
    var pendingDomain by remember { mutableStateOf<String?>(null) }
    var pendingName   by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == 0) "All Apps" else "All Sites", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = TxtPri) },
                navigationIcon = {
                    IconButton(onClick = { (ctx as? ComponentActivity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TxtPri)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Wht)
            )
        },
        containerColor = Wht
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab, containerColor = Wht, contentColor = Teal) {
                listOf("Apps", "Sites").forEachIndexed { i, lbl ->
                    Tab(
                        selected = tab == i, onClick = { tab = i },
                        text = { Text(lbl, fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal) },
                        selectedContentColor = Teal, unselectedContentColor = TxtSec
                    )
                }
            }
            when (tab) {
                0 -> AppsTab(
                    apps        = installed,
                    isLoading   = appsLoading,
                    blockedApps = blockedApps,
                    onBlock     = { app -> pendingPkg = app.packageName; pendingDomain = null; pendingName = app.name; showModal = true },
                    onUnblock   = { app -> BlockedData.unblockApp(ctx, app.packageName); refreshApps() }
                )
                1 -> SitesTab(
                    sites        = sites,
                    blockedSites = blockedSites,
                    onBlock      = { s -> pendingPkg = null; pendingDomain = s.domain; pendingName = s.domain; showModal = true },
                    onUnblock    = { s -> BlockedData.unblockSite(ctx, s.domain); refreshSites() },
                    onAddSite    = { domain ->
                        sites.add(0, UiSite("c_${System.currentTimeMillis()}", domain))
                        pendingPkg = null; pendingDomain = domain; pendingName = domain; showModal = true
                    }
                )
            }
        }
    }

    if (showModal) {
        BlockDialog(
            name      = pendingName,
            onDismiss = { showModal = false },
            onConfirm = { cfg ->
                pendingPkg?.let    { BlockedData.blockApp(ctx, it, cfg);  refreshApps() }
                pendingDomain?.let { BlockedData.blockSite(ctx, it, cfg); refreshSites() }
                showModal = false
            }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  10. TABS
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun AppsTab(apps: List<InstalledApp>, isLoading: Boolean = false, blockedApps: Map<String, BlockConfig>, onBlock: (InstalledApp) -> Unit, onUnblock: (InstalledApp) -> Unit) {
    var q by remember { mutableStateOf("") }
    val filtered  = apps.filter { it.name.contains(q, ignoreCase = true) }
    val blocked   = filtered.filter {  blockedApps.containsKey(it.packageName) }
    val unblocked = filtered.filter { !blockedApps.containsKey(it.packageName) }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = Teal, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                Text("Apps লোড হচ্ছে...", fontSize = 13.sp, color = TxtSec)
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        item { SearchBox(q, "Search apps...") { q = it } }
        if (blocked.isNotEmpty()) {
            item { SecLabel("Blocked (${blocked.size})") }
            items(blocked, key = { it.packageName }) { app ->
                ItemCard(app.name, app.icon, blockedApps[app.packageName], true) { onUnblock(app) }
            }
        }
        items(unblocked, key = { it.packageName }) { app ->
            ItemCard(app.name, app.icon, null, false) { onBlock(app) }
        }
    }
}

@Composable
fun SitesTab(sites: List<UiSite>, blockedSites: Map<String, BlockConfig>, onBlock: (UiSite) -> Unit, onUnblock: (UiSite) -> Unit, onAddSite: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val filtered  = sites.filter { it.domain.contains(q, ignoreCase = true) }
    val blocked   = filtered.filter {  blockedSites.containsKey(it.domain) }
    val unblocked = filtered.filter { !blockedSites.containsKey(it.domain) }
    val showAdd   = q.isNotBlank() && !sites.any { it.domain.contains(q.lowercase()) }

    LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        item { SearchBox(q, "Search / Add website...") { q = it } }
        if (showAdd) item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TealSurf)
                    .border(1.dp, Teal.copy(.3f), RoundedCornerShape(14.dp))
                    .clickable { onAddSite(q.trim()); q = "" }.padding(14.dp, 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(TealLt), contentAlignment = Alignment.Center) { Text("🌐", fontSize = 20.sp) }
                Column(Modifier.weight(1f)) {
                    Text("Add \"$q\"", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Teal)
                    Text("Tap to add and block", fontSize = 12.sp, color = TealDk.copy(.7f))
                }
                Icon(Icons.Default.Add, null, tint = Teal, modifier = Modifier.size(20.dp))
            }
        }
        if (blocked.isNotEmpty()) {
            item { SecLabel("Blocked (${blocked.size})") }
            items(blocked, key = { it.id }) { s -> ItemCard(s.domain, null, blockedSites[s.domain], true) { onUnblock(s) } }
        }
        items(unblocked, key = { it.id }) { s -> ItemCard(s.domain, null, null, false) { onBlock(s) } }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  11. ITEM CARD
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun ItemCard(name: String, icon: ImageBitmap? = null, cfg: BlockConfig?, isBlocked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (isBlocked) RedLt else Wht)
            .border(if (isBlocked) 1.dp else 0.5.dp, if (isBlocked) RedBl.copy(.35f) else BdrCol, RoundedCornerShape(14.dp))
            .padding(14.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (isBlocked) RedBl.copy(.1f) else TealSurf), contentAlignment = Alignment.Center) {
            if (icon != null && !isBlocked) {
                androidx.compose.foundation.Image(
                    painter = BitmapPainter(icon),
                    contentDescription = name,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Icon(if (isBlocked) Icons.Default.Lock else Icons.Default.Apps, null, tint = if (isBlocked) RedBl else Teal, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TxtPri, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                if (cfg != null) MethodBadge(cfg)
            }
            Text(if (isBlocked) "🔴 Blocked" else "Tap to block", fontSize = 12.sp, color = if (isBlocked) RedBl else TxtSec)
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .background(if (isBlocked) RedLt else TealSurf)
                .border(0.5.dp, if (isBlocked) RedBl.copy(.3f) else TealAcc.copy(.4f), CircleShape)
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(if (isBlocked) Icons.Default.Lock else Icons.Default.LockOpen, if (isBlocked) "Unblock" else "Block", tint = if (isBlocked) RedBl else Teal, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun MethodBadge(cfg: BlockConfig) {
    val (lbl, bg, tc) = when (cfg.method) {
        BlockMethod.TIME_RANGE   -> Triple("${cfg.timeFrom}–${cfg.timeTo}", Color(0xFFEFF6FF), Color(0xFF1D4ED8))
        BlockMethod.DAILY_LIMIT  -> {
            val h = cfg.dailyLimitMin / 60; val m = cfg.dailyLimitMin % 60
            Triple("📅 ${if (h > 0) "${h}h${m}m" else "${m}m"}/day", Color(0xFFFFF7ED), Color(0xFFEA580C))
        }
        BlockMethod.HOURLY_LIMIT -> Triple("⏱ ${cfg.hourlyLimitMin}m/hr", Color(0xFFFDF4FF), Color(0xFF9333EA))
        BlockMethod.PASSWORD     -> Triple("🔑 Pass", Color(0xFFF0FDF4), Color(0xFF15803D))
        BlockMethod.LONG_TEXT    -> Triple("📝 Text", RedLt, RedBl)
    }
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(7.dp, 2.dp)) {
        Text(lbl, fontSize = 10.sp, color = tc, fontWeight = FontWeight.SemiBold)
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  12. BLOCK DIALOG
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun BlockDialog(name: String, onDismiss: () -> Unit, onConfirm: (BlockConfig) -> Unit) {
    var method        by remember { mutableStateOf(BlockMethod.TIME_RANGE) }
    var timeFrom      by remember { mutableStateOf("09:00") }
    var timeTo        by remember { mutableStateOf("17:00") }
    var dailyHour     by remember { mutableStateOf(1) }
    var dailyMin      by remember { mutableStateOf(0) }
    var hourlyMin     by remember { mutableStateOf(20) }
    var password      by remember { mutableStateOf("") }
    var longText      by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Wht, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(TealLt), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, null, tint = Teal, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("Block", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TxtPri)
                        Text(name, fontSize = 13.sp, color = TxtSec, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                HorizontalDivider(color = BdrCol)

                Text("Block method", fontSize = 13.sp, color = TxtSec, fontWeight = FontWeight.Medium)

                val methods = listOf(
                    "⏰ Time Range"   to BlockMethod.TIME_RANGE,
                    "📅 Daily Limit"  to BlockMethod.DAILY_LIMIT,
                    "⏱ Hourly Limit" to BlockMethod.HOURLY_LIMIT,
                    "🔑 Password"     to BlockMethod.PASSWORD,
                    "📝 Long Text"    to BlockMethod.LONG_TEXT
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    methods.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (lbl, m) ->
                                val sel = method == m
                                Box(
                                    Modifier.clip(RoundedCornerShape(20.dp))
                                        .background(if (sel) Teal else TealSurf)
                                        .border(0.5.dp, if (sel) Teal else BdrCol, RoundedCornerShape(20.dp))
                                        .clickable { method = m }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(lbl, fontSize = 12.sp, color = if (sel) Wht else TxtSec, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }

                AnimatedContent(method, label = "mi") { m ->
                    when (m) {
                        BlockMethod.TIME_RANGE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ব্লক সময়", fontSize = 13.sp, color = TxtSec)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("From" to timeFrom, "To" to timeTo).forEachIndexed { i, (lbl, v) ->
                                    OutlinedTextField(
                                        value = v, onValueChange = { if (i == 0) timeFrom = it else timeTo = it },
                                        label = { Text(lbl) }, singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Teal, unfocusedBorderColor = BdrCol),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Text("Format: HH:mm (যেমন 09:00 – 17:00)", fontSize = 11.sp, color = TxtSec)
                        }

                        BlockMethod.DAILY_LIMIT -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("দিনে সর্বোচ্চ কতক্ষণ ব্যবহার করতে পারবে?", fontSize = 13.sp, color = TxtSec)
                            LimitPicker(
                                label = "ঘণ্টা", value = dailyHour, min = 0, max = 23,
                                onDec = { if (dailyHour > 0) dailyHour-- },
                                onInc = { if (dailyHour < 23) dailyHour++ }
                            )
                            LimitPicker(
                                label = "মিনিট", value = dailyMin, min = 0, max = 59, step = 5,
                                onDec = { if (dailyMin >= 5) dailyMin -= 5 else dailyMin = 0 },
                                onInc = { if (dailyMin + 5 <= 59) dailyMin += 5 else dailyMin = 59 }
                            )
                            val total = dailyHour * 60 + dailyMin
                            if (total == 0) {
                                Text("⚠️ অন্তত ১ মিনিট সেট করুন", fontSize = 12.sp, color = RedBl)
                            } else {
                                val h = total / 60; val mn = total % 60
                                Text(
                                    "✓ দিনে ${if (h > 0) "${h}ঘণ্টা " else ""}${if (mn > 0) "${mn}মিনিট" else ""}র বেশি হলে block হবে",
                                    fontSize = 12.sp, color = Teal
                                )
                            }
                        }

                        BlockMethod.HOURLY_LIMIT -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("প্রতি ঘণ্টায় সর্বোচ্চ কতক্ষণ?", fontSize = 13.sp, color = TxtSec)
                            LimitPicker(
                                label = "মিনিট/ঘণ্টা", value = hourlyMin, min = 1, max = 60, step = 5,
                                onDec = { if (hourlyMin > 5) hourlyMin -= 5 else hourlyMin = 1 },
                                onInc = { if (hourlyMin + 5 <= 60) hourlyMin += 5 else hourlyMin = 60 }
                            )
                            Text("✓ প্রতি ঘণ্টায় ${hourlyMin}মিনিটের বেশি হলে block হবে", fontSize = 12.sp, color = Teal)
                        }

                        BlockMethod.PASSWORD -> OutlinedTextField(
                            value = password, onValueChange = { password = it },
                            label = { Text("Password") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Teal, unfocusedBorderColor = BdrCol),
                            modifier = Modifier.fillMaxWidth()
                        )

                        BlockMethod.LONG_TEXT -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Unlock করতে এই টেক্সট টাইপ করতে হবে", fontSize = 13.sp, color = TxtSec)
                            OutlinedTextField(
                                value = longText, onValueChange = { longText = it },
                                placeholder = { Text("e.g. I will stay focused...", fontSize = 13.sp, color = TxtSec) },
                                minLines = 3, maxLines = 4,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Teal, unfocusedBorderColor = BdrCol),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                HorizontalDivider(color = BdrCol)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss, shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, BdrCol),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TxtSec),
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel") }

                    val isValid = when (method) {
                        BlockMethod.DAILY_LIMIT  -> (dailyHour * 60 + dailyMin) > 0
                        BlockMethod.PASSWORD     -> password.isNotBlank()
                        BlockMethod.LONG_TEXT    -> longText.isNotBlank()
                        else                     -> true
                    }

                    Button(
                        onClick = {
                            val cfg = when (method) {
                                BlockMethod.TIME_RANGE   -> BlockConfig(method, timeFrom = timeFrom, timeTo = timeTo)
                                BlockMethod.DAILY_LIMIT  -> BlockConfig(method, dailyLimitMin  = dailyHour * 60 + dailyMin)
                                BlockMethod.HOURLY_LIMIT -> BlockConfig(method, hourlyLimitMin = hourlyMin)
                                BlockMethod.PASSWORD     -> BlockConfig(method, password = password)
                                BlockMethod.LONG_TEXT    -> BlockConfig(method, longText = longText)
                            }
                            onConfirm(cfg)
                        },
                        enabled = isValid,
                        shape   = RoundedCornerShape(12.dp),
                        colors  = ButtonDefaults.buttonColors(containerColor = RedBl),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Block")
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  13. LIMIT PICKER COMPONENT
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun LimitPicker(label: String, value: Int, min: Int, max: Int, step: Int = 1, onDec: () -> Unit, onInc: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TealSurf).border(1.dp, BdrCol, RoundedCornerShape(12.dp)).padding(12.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = TxtSec, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(if (value > min) Teal else BdrCol).clickable(enabled = value > min) { onDec() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Remove, null, tint = Wht, modifier = Modifier.size(18.dp))
            }
            Box(Modifier.width(52.dp).clip(RoundedCornerShape(8.dp)).background(Wht).border(1.dp, BdrCol, RoundedCornerShape(8.dp)).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text(value.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Teal)
            }
            Box(Modifier.size(36.dp).clip(CircleShape).background(if (value < max) Teal else BdrCol).clickable(enabled = value < max) { onInc() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, null, tint = Wht, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  14. MICRO COMPOSABLES
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun SearchBox(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontSize = 14.sp, color = TxtSec) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = TxtSec) },
        trailingIcon = if (value.isNotEmpty()) { { IconButton({ onValueChange("") }) { Icon(Icons.Default.Close, null, tint = TxtSec, modifier = Modifier.size(18.dp)) } } } else null,
        shape = RoundedCornerShape(12.dp), singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Teal, unfocusedBorderColor = BdrCol, focusedContainerColor = Wht, unfocusedContainerColor = Wht),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun SecLabel(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TxtSec, letterSpacing = 0.8.sp, modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp))
}
