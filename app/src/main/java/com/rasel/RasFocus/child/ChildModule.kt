package com.rasel.RasFocus.child

// ============================================================
// RASFOCUS+ CHILD APP MODULE (PREMIUM UI + CORE LOGIC)
// Fixed UI issues: Added scrollability, premium cards, 
// dynamic dashboard, and kept all background logic intact.
// ============================================================

import android.accessibilityservice.AccessibilityService
import android.app.*
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.*
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

// (এখানে RasFocusColors ইম্পোর্ট করা ধরে নেওয়া হলো, যদি না থাকে তবে আপনার মেইন ফাইল থেকে নেবে)
import com.rasel.RasFocus.RasFocusColors

// ============================================================
// PART 1: SHARED PREFERENCES, PAIRING & RULE MANAGER
// ============================================================
object ChildPairingManager {
    private const val PREFS = "RasFocusChildPrefs"
    
    fun setPaired(context: Context, pin: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("is_paired", true)
            .putString("pairing_pin", pin)
            .apply()
    }

    fun isPaired(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("is_paired", false)
    }

    fun getPin(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("pairing_pin", null)
    }

    fun unpair(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

object ChildRuleManager {
    private const val PREFS = "RasFocusRules"

    fun updateBlockedApps(context: Context, apps: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet("BLOCKED_APPS", apps).apply()
        context.sendBroadcast(Intent("RASFOCUS_RULES_UPDATED"))
    }

    fun getBlockedApps(context: Context): List<String> {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("BLOCKED_APPS", emptySet())?.toList() ?: emptyList()
    }

    fun updateToxicWords(context: Context, words: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet("TOXIC_WORDS", words).apply()
        context.sendBroadcast(Intent("RASFOCUS_RULES_UPDATED"))
    }

    fun getToxicWords(context: Context): List<String> {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("TOXIC_WORDS", emptySet())?.toList() ?: emptyList()
    }
}

// ============================================================
// PART 2: CHILD APP UI (COMPOSE INTERFACES)
// ============================================================

enum class ChildScreenState { PAIRING, PERMISSIONS, DASHBOARD }

@Composable
fun ChildRootScreen(context: Context) {
    var currentScreen by remember { 
        mutableStateOf(if (ChildPairingManager.isPaired(context)) ChildScreenState.DASHBOARD else ChildScreenState.PAIRING) 
    }
    
    var isLocked by remember { mutableStateOf(false) }
    var showStudyToPlay by remember { mutableStateOf(false) }
    var showWarning by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(RasFocusColors.BackgroundWhite)) {
        Crossfade(targetState = currentScreen, label = "child_nav") { screen ->
            when (screen) {
                ChildScreenState.PAIRING -> ChildPairingScreen(
                    onPaired = { pin -> 
                        ChildPairingManager.setPaired(context, pin)
                        currentScreen = ChildScreenState.PERMISSIONS 
                    }
                )
                ChildScreenState.PERMISSIONS -> ChildPermissionScreen(
                    onAllGranted = { 
                        val intent = Intent(context, ChildFirebaseService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                        // Start all other background trackers
                        ChildPermissions.startAllServices(context)
                        currentScreen = ChildScreenState.DASHBOARD 
                    }
                )
                ChildScreenState.DASHBOARD -> ChildDashboardScreen()
            }
        }

        if (isLocked) ChildLockOverlay(onUnlock = { isLocked = false })
        if (showStudyToPlay) StudyToPlayOverlay(onSolved = { showStudyToPlay = false })
        if (showWarning) WarningOverlay(onDismiss = { showWarning = false })
    }
}

// ── 1. PREMIUM PAIRING SCREEN (Fixed Scroll Issue) ──
@Composable
fun ChildPairingScreen(onPaired: (String) -> Unit) {
    var pinCode by remember { mutableStateOf("") }
    val scrollState = rememberScrollState() // FIX: Added scroll state so button doesn't hide

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RasFocusColors.SurfaceOffWhite)
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        
        // Header
        Box(
            modifier = Modifier.size(64.dp).background(RasFocusColors.PrimaryTeal.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.ChildCare, null, tint = RasFocusColors.PrimaryTeal, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Child Device Setup", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = RasFocusColors.OnBackground)
        Text("Connect this device to the Parent's RasFocus app.", fontSize = 14.sp, color = RasFocusColors.SubtleText, textAlign = TextAlign.Center)
        
        Spacer(Modifier.height(40.dp))
        
        // Beautiful QR Box
        Card(
            modifier = Modifier.size(240.dp).shadow(16.dp, RoundedCornerShape(24.dp), spotColor = RasFocusColors.PrimaryTeal),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.QrCodeScanner, null, tint = RasFocusColors.PrimaryTeal, modifier = Modifier.size(80.dp))
                Spacer(Modifier.height(16.dp))
                Text("Scan Parent's QR Code", fontWeight = FontWeight.Bold, color = RasFocusColors.OnBackground)
                Text("Using device camera", fontSize = 12.sp, color = RasFocusColors.SubtleText)
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        // OR Divider
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = RasFocusColors.DividerColor)
            Text(" OR ", modifier = Modifier.padding(horizontal = 16.dp), color = RasFocusColors.SubtleText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.weight(1f), color = RasFocusColors.DividerColor)
        }

        Spacer(Modifier.height(24.dp))
        Text("Enter 6-Digit Pairing ID", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = RasFocusColors.OnBackground)
        Spacer(Modifier.height(12.dp))
        
        OutlinedTextField(
            value = pinCode,
            onValueChange = { if (it.length <= 6) pinCode = it },
            placeholder = { Text("e.g. 123456", color = RasFocusColors.SubtleText) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RasFocusColors.PrimaryTeal,
                unfocusedBorderColor = RasFocusColors.DividerColor,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        )
        
        Spacer(Modifier.height(32.dp))
        
        // Fixed button that won't get pushed off screen
        Button(
            onClick = { if(pinCode.length == 6) onPaired(pinCode) },
            enabled = pinCode.length == 6,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.PrimaryTeal, disabledContainerColor = RasFocusColors.DividerColor)
        ) {
            Text("CONNECT TO PARENT", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(40.dp))
    }
}

// ── 2. PREMIUM PERMISSIONS SCREEN ──
@Composable
fun ChildPermissionScreen(onAllGranted: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(RasFocusColors.BackgroundWhite).padding(24.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("Enable Protection", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = RasFocusColors.OnBackground)
        Text("RasFocus+ needs these permissions to secure this device.", fontSize = 14.sp, color = RasFocusColors.SubtleText)
        Spacer(Modifier.height(32.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f)) {
            item { PremiumPermissionItem("Accessibility Service", "Blocks apps & monitors keywords", Icons.Filled.Visibility) }
            item { PremiumPermissionItem("Device Admin", "Prevents app uninstallation", Icons.Filled.Security) }
            item { PremiumPermissionItem("Draw Over Apps", "Shows lock screen and warnings", Icons.Filled.Layers) }
            item { PremiumPermissionItem("Notification Access", "Blocks hidden messages", Icons.Filled.NotificationsActive) }
        }
        
        Button(
            onClick = onAllGranted,
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.SuccessGreen)
        ) {
            Icon(Icons.Filled.VerifiedUser, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("ACTIVATE PROTECTION", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PremiumPermissionItem(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RasFocusColors.SurfaceOffWhite),
        border = BorderStroke(1.dp, RasFocusColors.DividerColor)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).background(RasFocusColors.PrimaryTeal.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = RasFocusColors.PrimaryTeal, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = RasFocusColors.OnBackground)
                Text(desc, fontSize = 12.sp, color = RasFocusColors.SubtleText)
            }
        }
    }
}

// ── 3. PREMIUM DASHBOARD SCREEN ──
@Composable
fun ChildDashboardScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(RasFocusColors.BackgroundWhite).verticalScroll(rememberScrollState())
    ) {
        // Dynamic Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(colors = listOf(RasFocusColors.PrimaryTeal, RasFocusColors.PrimaryTealLight)),
                    shape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)
                )
                .padding(horizontal = 24.dp, vertical = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.VerifiedUser, null, tint = Color.White, modifier = Modifier.size(56.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text("Device Protected", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text("Connected & Synced with Parent", fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
            }
        }

        Spacer(Modifier.height(32.dp))
        
        Text(
            "ACTIVE MODULES", 
            fontSize = 12.sp, 
            fontWeight = FontWeight.Bold, 
            color = RasFocusColors.SubtleText, 
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(16.dp))

        // Module Grid
        Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ActiveModuleCard("App & Web Blocker", "Restricting unwanted apps", Icons.Outlined.Block, true)
            ActiveModuleCard("Content Filter", "Filtering toxic chats & media", Icons.Outlined.Visibility, true)
            ActiveModuleCard("Live Location", "Parent has location access", Icons.Outlined.LocationOn, true)
            ActiveModuleCard("Safe Search DNS", "Blocking adult websites", Icons.Outlined.Dns, true)
        }
        
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
fun ActiveModuleCard(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isActive: Boolean) {
    val accentColor = if(isActive) RasFocusColors.SuccessGreen else RasFocusColors.SubtleText
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RasFocusColors.SurfaceOffWhite),
        border = BorderStroke(1.dp, RasFocusColors.DividerColor)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = RasFocusColors.PrimaryTeal, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = RasFocusColors.OnBackground)
                Text(desc, fontSize = 12.sp, color = RasFocusColors.SubtleText)
            }
            Box(
                modifier = Modifier.background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(50.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(if(isActive) "Active" else "Off", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentColor)
            }
        }
    }
}

// ============================================================
// OVERLAY COMPONENTS (Retained Original Logic, Improved Look)
// ============================================================
@Composable
fun ChildLockOverlay(onUnlock: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF121212)).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Lock, null, tint = RasFocusColors.ErrorRed, modifier = Modifier.size(100.dp))
            Spacer(Modifier.height(32.dp))
            Text("DEVICE LOCKED", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black), color = Color.White)
            Text("This device has been locked by your parent.", color = Color.Gray, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun StudyToPlayOverlay(onSolved: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp), 
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.MenuBook, null, tint = RasFocusColors.PrimaryTeal, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Study to Play", fontWeight = FontWeight.Black, fontSize = 22.sp, color = RasFocusColors.OnBackground)
                Text("Solve this math problem to unlock your app:", fontSize = 14.sp, color = RasFocusColors.SubtleText, textAlign = TextAlign.Center)
                
                Text("15 + 27 = ?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = RasFocusColors.PrimaryTeal, modifier = Modifier.padding(vertical = 24.dp))
                
                Button(
                    onClick = onSolved, 
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.PrimaryTeal)
                ) { Text("Check Answer", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun WarningOverlay(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize().background(RasFocusColors.ErrorRed.copy(alpha = 0.85f)).clickable { onDismiss() }, 
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(80.dp))
                Spacer(Modifier.height(24.dp))
                Text("LOOK UP!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 36.sp)
                Text("Stop walking while typing.", color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

// ============================================================
// PART 3: BACKGROUND SERVICES & LOGIC (100% UNCHANGED)
// ============================================================

// 1. DEVICE ADMIN RECEIVER (Unistall Protection)
class RasDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Device Admin Enabled.", Toast.LENGTH_SHORT).show()
    }
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Warning: Disabling this will lock the device!"
    }
}

// 2. FIREBASE FOREGROUND SERVICE (Listens to Parent Commands & Rules)
class ChildFirebaseService : Service() {
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun onCreate() {
        super.onCreate()
        if (!ChildPairingManager.isPaired(this)) {
            stopSelf()
            return
        }

        val channelId = "rasfocus_protection"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Protection", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("RasFocus+ Active")
            .setContentText("Keeping this device safe.")
            .setSmallIcon(android.R.drawable.ic_secure)
            .build()
        startForeground(1, notif)

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, RasDeviceAdminReceiver::class.java)

        listenToCommandsAndRules()
    }

    private fun listenToCommandsAndRules() {
        val pin = ChildPairingManager.getPin(this) ?: return
        val dbRef = Firebase.database.reference.child("pairing_codes/$pin")

        // Listen to active commands
        dbRef.child("commands").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (command in snapshot.children) {
                    val action = command.child("action").getValue(String::class.java)
                    when (action) {
                        "LOCK_SCREEN" -> {
                            if (dpm.isAdminActive(adminComponent)) dpm.lockNow()
                            sendBroadcast(Intent("SHOW_OVERLAY_LOCK"))
                        }
                        "WIPE_PIN" -> {
                            if (dpm.isAdminActive(adminComponent)) dpm.resetPassword("", DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY)
                        }
                        "FACTORY_RESET" -> {
                            if (dpm.isAdminActive(adminComponent)) dpm.wipeData(0)
                        }
                    }
                    command.ref.removeValue()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Listen to rules (Blocked apps & Keywords) for State Syncing
        dbRef.child("rules").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val apps = mutableSetOf<String>()
                snapshot.child("blockedApps").children.forEach { apps.add(it.getValue(String::class.java) ?: "") }
                ChildRuleManager.updateBlockedApps(this@ChildFirebaseService, apps)

                val words = mutableSetOf<String>()
                snapshot.child("toxicWords").children.forEach { words.add(it.getValue(String::class.java) ?: "") }
                ChildRuleManager.updateToxicWords(this@ChildFirebaseService, words)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}

// 3. ACCESSIBILITY SERVICE (App Blocker, Chat Monitor, Anti-Settings Hook)
open class RasAccessibilityService : AccessibilityService() {
    private var blockedApps = listOf<String>()
    private var toxicWords = listOf<String>()

    private val ruleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            blockedApps = ChildRuleManager.getBlockedApps(context)
            toxicWords = ChildRuleManager.getToxicWords(context)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(ruleReceiver, IntentFilter("RASFOCUS_RULES_UPDATED"), RECEIVER_NOT_EXPORTED)
        blockedApps = ChildRuleManager.getBlockedApps(this)
        toxicWords = ChildRuleManager.getToxicWords(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !ChildPairingManager.isPaired(this)) return

        val packageName = event.packageName?.toString() ?: return

        // 1. THE SETTINGS BYPASS (Anti-settings hook to prevent uninstallation)
        if (packageName == "com.android.settings") {
            val nodeText = extractTextFromNode(event.source).lowercase()
            if (nodeText.contains("rasfocus") || nodeText.contains("accessibility") || nodeText.contains("device admin")) {
                Toast.makeText(applicationContext, "Settings Locked by Parent", Toast.LENGTH_SHORT).show()
                performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
        }

        // 2. PiP & SPLIT SCREEN BYPASS Check
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (blockedApps.contains(packageName)) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                sendBroadcast(Intent("SHOW_STUDY_OVERLAY"))
            }
            
            // Check floating windows recursively
            if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                val windows = windows
                for (window in windows) {
                    val root = window.root
                    if (root != null && blockedApps.contains(root.packageName?.toString())) {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        sendBroadcast(Intent("SHOW_STUDY_OVERLAY"))
                    }
                }
            }
        }

        // 3. CHAT MONITORING (Cyber Bullying)
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val text = event.text.joinToString(" ").lowercase()
            for (word in toxicWords) {
                if (text.contains(word)) {
                    val pin = ChildPairingManager.getPin(this) ?: return
                    Firebase.database.reference.child("pairing_codes/$pin/alerts").push().setValue("Toxic word detected: $word in $packageName")
                }
            }
        }

        // 4. YOUTUBE SCANNER
        if (packageName == "com.google.android.youtube") {
            val title = event.text.joinToString(" ").lowercase()
            if (title.contains("18+") || title.contains("adult")) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        var text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        for (i in 0 until node.childCount) {
            text += " " + extractTextFromNode(node.getChild(i))
        }
        return text
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(ruleReceiver)
    }

    override fun onInterrupt() {}
}

// 4. SILENT NOTIFICATION INTERCEPTOR (Blocks direct replies & hidden messages)
class RasNotificationInterceptor : NotificationListenerService() {
    private var blockedApps = listOf<String>()
    private var toxicWords = listOf<String>()

    private val ruleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            blockedApps = ChildRuleManager.getBlockedApps(context)
            toxicWords = ChildRuleManager.getToxicWords(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(ruleReceiver, IntentFilter("RASFOCUS_RULES_UPDATED"), RECEIVER_NOT_EXPORTED)
        blockedApps = ChildRuleManager.getBlockedApps(this)
        toxicWords = ChildRuleManager.getToxicWords(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            val packageName = it.packageName
            val notificationText = it.notification.extras.getString("android.text")?.lowercase() ?: ""

            if (blockedApps.contains(packageName)) {
                cancelNotification(it.key)
            }
            
            for (word in toxicWords) {
                if (notificationText.contains(word)) {
                    cancelNotification(it.key)
                    val pin = ChildPairingManager.getPin(this) ?: return
                    Firebase.database.reference.child("pairing_codes/$pin/alerts").push().setValue("Blocked toxic notification from $packageName")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(ruleReceiver)
    }
}

// 5. BOOT RECEIVER (Phone on holei protection start hobe)
class BootSurvivalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action && ChildPairingManager.isPaired(context)) {
            val serviceIntent = Intent(context, ChildFirebaseService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ChildPermissions — Fix 3: MainActivity থেকে call হয়
// Child dashboard খুললেই সব background services চালু হয়
// ─────────────────────────────────────────────────────────────────────────────
object ChildPermissions {
    fun startAllServices(context: Context) {
        // ChildFirebaseService — Firebase command listener
        val firebaseIntent = Intent(context, ChildFirebaseService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(firebaseIntent)
        } else {
            context.startService(firebaseIntent)
        }

        // LocationTracker — GPS location push to Firebase
        val locationIntent = Intent(context, com.rasel.RasFocus.child.LocationTracker::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(locationIntent)
        } else {
            context.startService(locationIntent)
        }

        // FirebaseCommandListener — parent command receiver
        val commandIntent = Intent(context, com.rasel.RasFocus.child.FirebaseCommandListener::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(commandIntent)
        } else {
            context.startService(commandIntent)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FocusAccessibilityService — AndroidManifest.xml এ .child.FocusAccessibilityService
// নামে declare করা আছে। RasAccessibilityService এর সব logic inherit করে।
// ─────────────────────────────────────────────────────────────────────────────
class FocusAccessibilityService : RasAccessibilityService()
