package com.rasel.RasFocus.selfcontrol

// ============================================================
//  RasFocus+ — Self Control Profile Manager
//  FIXED:
//   • SharedProfileViewModel দিয়ে profiles persist হয়
//   • syncProfileToPrefs সম্পূর্ণ — BlockedData-তে save হয়
//   • Toggle logic সঠিক — lockMode অনুযায়ী dialog দেখায়
//   • onToggle এ profile actually update হয়
// ============================================================

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.BorderStroke
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rasel.RasFocus.RasFocusColors
import com.rasel.RasFocus.RasFocusShapes

// ============================================================
// DATA MODEL (unchanged — used across all 4 files)
// ============================================================
data class FocusProfile(
    var id: String = "",
    var profileName: String = "Deep Work Session",
    var isActive: Boolean = false,
    var lockMode: Int = 0, // 0: Self, 1: Parents, 2: Long Text
    var activeDays: List<Boolean> = List(7) { false },
    var startHour: Int = 9, var startMin: Int = 0,
    var endHour: Int = 17, var endMin: Int = 0,
    var blockInternet: Boolean = false,
    var blockUninstall: Boolean = true,
    var quickBlockYtShorts: Boolean = false,
    var quickBlockFbReels: Boolean = false,
    var quickBlockAds: Boolean = false,
    var quickBlockIgReels: Boolean = false,
    var adultWeb: Boolean = true,
    var hardcore: Boolean = true,
    var romantic: Boolean = false,
    var strictDns: Boolean = false,
    var blockedWebsites: List<String> = emptyList(),
    var blockedApps: List<String> = emptyList(),
    var adultCustomKeywords: List<String> = emptyList()
)

// ============================================================
// MAIN SCREEN
// FIX: SharedProfileViewModel দিয়ে profiles load হয় ও save হয়
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleBlocksScreen(
    navController: NavController?,
    profileViewModel: SharedProfileViewModel = viewModel(
        factory = SharedProfileViewModel.Factory(LocalContext.current)
    )
) {
    val context = LocalContext.current
    val profiles by profileViewModel.profiles.collectAsState()

    var editingProfile by remember { mutableStateOf<FocusProfile?>(null) }
    var pendingToggleProfile by remember { mutableStateOf<FocusProfile?>(null) }

    // Dialog states
    var showTimeOverlay      by remember { mutableStateOf(false) }
    var showPassOverlay      by remember { mutableStateOf(false) }
    var showTextUnlockOverlay by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (editingProfile == null) {
                TopAppBar(
                    title = { Text("Profile Manager", fontWeight = FontWeight.Bold, color = RasFocusColors.OnBackground) },
                    navigationIcon = {
                        IconButton(onClick = { navController?.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = RasFocusColors.OnBackground)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = RasFocusColors.BackgroundWhite)
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().background(RasFocusColors.BackgroundWhite).padding(paddingValues)) {

            if (editingProfile == null) {
                // ─── PROFILE LIST VIEW ────────────────────────────────────────
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Focus Profiles", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = RasFocusColors.OnBackground)
                            Text("Create dedicated schedules with advanced locks.", fontSize = 14.sp, color = RasFocusColors.SubtleText)
                        }
                        Button(
                            onClick = { editingProfile = FocusProfile(profileName = "") },
                            colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.PrimaryTeal),
                            shape = RasFocusShapes.Button
                        ) {
                            Text("+ Add Profile", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (profiles.isEmpty()) {
                        // Empty state
                        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🎯", fontSize = 48.sp)
                                Spacer(Modifier.height(12.dp))
                                Text("No profiles yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RasFocusColors.OnBackground)
                                Text("Create your first focus profile above", fontSize = 14.sp, color = RasFocusColors.SubtleText, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(profiles, key = { it.id }) { profile ->
                                ProfileCard(
                                    profile = profile,
                                    // FIX: toggle → lockMode check → correct dialog → then ViewModel update
                                    onToggle = {
                                        pendingToggleProfile = profile
                                        if (!profile.isActive) {
                                            when (profile.lockMode) {
                                                0 -> showTimeOverlay = true
                                                1 -> showPassOverlay = true
                                                2 -> showTextUnlockOverlay = true
                                            }
                                        } else {
                                            // Turning off
                                            when (profile.lockMode) {
                                                1 -> showPassOverlay = true
                                                2 -> showTextUnlockOverlay = true
                                                else -> profileViewModel.toggleProfile(profile.id)
                                            }
                                        }
                                    },
                                    onEdit = { editingProfile = profile.copy() },
                                    onDelete = { profileViewModel.deleteProfile(profile.id) }
                                )
                            }
                        }
                    }
                }

            } else {
                // ─── FULL SCREEN EDITOR OVERLAY ──────────────────────────────
                ProfileEditorOverlay(
                    initialProfile = editingProfile!!,
                    onSave = { updatedProfile ->
                        // FIX: ViewModel দিয়ে save — SharedPreferences-এ persist হয়
                        profileViewModel.addOrUpdateProfile(updatedProfile)
                        editingProfile = null
                    },
                    onCancel = { editingProfile = null }
                )
            }

            // ─── DIALOGS ─────────────────────────────────────────────────────
            if (showPassOverlay) {
                ParentsPasswordDialog(
                    onDismiss = { showPassOverlay = false; pendingToggleProfile = null },
                    onConfirm = {
                        showPassOverlay = false
                        pendingToggleProfile?.let { profileViewModel.toggleProfile(it.id) }
                        pendingToggleProfile = null
                    }
                )
            }
            if (showTextUnlockOverlay) {
                TextUnlockDialog(
                    onDismiss = { showTextUnlockOverlay = false; pendingToggleProfile = null },
                    onUnlock = {
                        showTextUnlockOverlay = false
                        pendingToggleProfile?.let { profileViewModel.toggleProfile(it.id) }
                        pendingToggleProfile = null
                    }
                )
            }
            if (showTimeOverlay) {
                TimerSetDialog(
                    onDismiss = { showTimeOverlay = false; pendingToggleProfile = null },
                    onStart = {
                        showTimeOverlay = false
                        pendingToggleProfile?.let { profileViewModel.toggleProfile(it.id) }
                        pendingToggleProfile = null
                    }
                )
            }
        }
    }
}

// ============================================================
// PROFILE CARD
// ============================================================
@Composable
fun ProfileCard(profile: FocusProfile, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(4.dp, RasFocusShapes.Card),
        shape = RasFocusShapes.Card,
        colors = CardDefaults.cardColors(containerColor = RasFocusColors.SurfaceOffWhite),
        border = BorderStroke(1.dp, if (profile.isActive) RasFocusColors.PrimaryTeal else RasFocusColors.DividerColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = if (profile.isActive) RasFocusColors.PrimaryTeal else RasFocusColors.SubtleText)
                Spacer(Modifier.width(12.dp))
                Text(profile.profileName.ifEmpty { "Unnamed Profile" }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RasFocusColors.OnBackground, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text("Blocked: ${profile.blockedWebsites.size} Web, ${profile.blockedApps.size} App", fontSize = 14.sp, color = RasFocusColors.SubtleText)
            val modeText = when (profile.lockMode) { 1 -> "Parents Control"; 2 -> "Long Text Unlock"; else -> "Self Control" }
            Text("Mode: $modeText", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RasFocusColors.PrimaryTeal)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = profile.isActive,
                        onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(checkedThumbColor = RasFocusColors.PrimaryTeal)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (profile.isActive) "Active" else "Inactive", fontWeight = FontWeight.Bold, color = if (profile.isActive) RasFocusColors.PrimaryTeal else RasFocusColors.OnBackground)
                }
                Row {
                    TextButton(onClick = onEdit) { Text("Edit", color = RasFocusColors.OnBackground, fontWeight = FontWeight.Bold) }
                    if (!profile.isActive) {
                        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = RasFocusColors.ErrorRed) }
                    }
                }
            }
        }
    }
}

// ============================================================
// PROFILE EDITOR OVERLAY (4 TABS)
// ============================================================
@Composable
fun ProfileEditorOverlay(initialProfile: FocusProfile, onSave: (FocusProfile) -> Unit, onCancel: () -> Unit) {
    var profile by remember { mutableStateOf(initialProfile.copy()) }
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Basic & Time", "Quick Settings", "Custom Lists", "Adult Block")

    Column(modifier = Modifier.fillMaxSize().background(RasFocusColors.SurfaceOffWhite)) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = RasFocusColors.BackgroundWhite,
            contentColor = RasFocusColors.PrimaryTeal,
            edgePadding = 16.dp,
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = FontWeight.Bold) })
            }
        }

        HorizontalDivider(color = RasFocusColors.DividerColor)

        Box(modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 16.dp)) {
            when (selectedTab) {
                0 -> BasicTimeTab(profile) { profile = it }
                1 -> QuickSettingsTab(profile) { profile = it }
                2 -> CustomListsTab(profile) { profile = it }
                3 -> AdultBlockTab(profile) { profile = it }
            }
        }

        Surface(shadowElevation = 8.dp, color = RasFocusColors.BackgroundWhite) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Row {
                    if (selectedTab > 0) {
                        OutlinedButton(onClick = { selectedTab-- }, shape = RasFocusShapes.Button) { Text("Back", color = RasFocusColors.OnBackground) }
                        Spacer(Modifier.width(8.dp))
                    }
                    if (selectedTab < 3) {
                        Button(onClick = { selectedTab++ }, shape = RasFocusShapes.Button, colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.PrimaryTeal)) { Text("Next") }
                    }
                }
                Row {
                    TextButton(onClick = onCancel) { Text("Cancel", color = RasFocusColors.OnBackground) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(profile) }, shape = RasFocusShapes.Button, colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.PrimaryTeal)) { Text("Save") }
                }
            }
        }
    }
}

// ─── TAB 1: BASIC & TIME ───
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicTimeTab(profile: FocusProfile, onUpdate: (FocusProfile) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val modes = listOf("Self Control", "Parents Control", "Long Text Unlock")
    val days  = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        OutlinedTextField(
            value = profile.profileName,
            onValueChange = { onUpdate(profile.copy(profileName = it)) },
            label = { Text("Profile Name") },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = modes[profile.lockMode], onValueChange = {}, readOnly = true,
                label = { Text("Lock Mode") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                modes.forEachIndexed { index, option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { onUpdate(profile.copy(lockMode = index)); expanded = false })
                }
            }
        }

        Text("Active Days", fontWeight = FontWeight.Bold, color = RasFocusColors.OnBackground)
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            days.forEachIndexed { index, day ->
                val isActive = profile.activeDays[index]
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(if (isActive) RasFocusColors.PrimaryTeal else Color.Transparent)
                        .border(1.dp, if (isActive) Color.Transparent else RasFocusColors.DividerColor, CircleShape)
                        .clickable {
                            val newDays = profile.activeDays.toMutableList()
                            newDays[index] = !isActive
                            onUpdate(profile.copy(activeDays = newDays))
                        },
                    contentAlignment = Alignment.Center
                ) { Text(day, color = if (isActive) Color.White else RasFocusColors.OnBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
        }

        Text("Session Time", fontWeight = FontWeight.Bold, color = RasFocusColors.OnBackground)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { /* TimePickerDialog */ }) { Text("Start: ${profile.startHour}:${profile.startMin.toString().padStart(2, '0')}") }
            OutlinedButton(onClick = { /* TimePickerDialog */ }) { Text("End: ${profile.endHour}:${profile.endMin.toString().padStart(2, '0')}") }
        }
    }
}

// ─── TAB 2: QUICK SETTINGS ───
@Composable
fun QuickSettingsTab(profile: FocusProfile, onUpdate: (FocusProfile) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Block Internet", fontWeight = FontWeight.Bold)
                Text("Disables all network access", fontSize = 12.sp, color = RasFocusColors.SubtleText)
            }
            Switch(checked = profile.blockInternet, onCheckedChange = { onUpdate(profile.copy(blockInternet = it)) })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Block Uninstall", fontWeight = FontWeight.Bold)
                Text("Locks Task Manager & apps", fontSize = 12.sp, color = RasFocusColors.SubtleText)
            }
            Switch(checked = profile.blockUninstall, onCheckedChange = { onUpdate(profile.copy(blockUninstall = it)) })
        }
        Spacer(Modifier.height(10.dp))
        Text("Quick Content Filters", fontWeight = FontWeight.Bold, color = RasFocusColors.OnBackground)

        val quickBlocks = listOf(
            "YT Shorts"    to profile.quickBlockYtShorts,
            "FB Reels"     to profile.quickBlockFbReels,
            "Web & YT Ads" to profile.quickBlockAds,
            "IG Reels"     to profile.quickBlockIgReels
        )
        quickBlocks.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { (title, isActive) ->
                    Card(
                        modifier = Modifier.weight(1f).height(60.dp).clickable {
                            when (title) {
                                "YT Shorts"    -> onUpdate(profile.copy(quickBlockYtShorts = !isActive))
                                "FB Reels"     -> onUpdate(profile.copy(quickBlockFbReels = !isActive))
                                "Web & YT Ads" -> onUpdate(profile.copy(quickBlockAds = !isActive))
                                "IG Reels"     -> onUpdate(profile.copy(quickBlockIgReels = !isActive))
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = if (isActive) RasFocusColors.PrimaryTeal else RasFocusColors.BackgroundWhite),
                        border = BorderStroke(1.dp, if (isActive) Color.Transparent else RasFocusColors.DividerColor)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(title, fontWeight = FontWeight.Bold, color = if (isActive) Color.White else RasFocusColors.OnBackground)
                        }
                    }
                }
            }
        }
    }
}

// ─── TAB 3: CUSTOM LISTS ───
@Composable
fun CustomListsTab(profile: FocusProfile, onUpdate: (FocusProfile) -> Unit) {
    var webInput by remember { mutableStateOf("") }
    var appInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column {
            Text("Websites (${profile.blockedWebsites.size})", fontWeight = FontWeight.Bold, color = RasFocusColors.OnBackground)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = webInput, onValueChange = { webInput = it }, placeholder = { Text("e.g. facebook.com") }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(8.dp))
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (webInput.isNotBlank()) { onUpdate(profile.copy(blockedWebsites = profile.blockedWebsites + webInput)); webInput = "" } }, shape = RoundedCornerShape(8.dp)) { Text("+ Add") }
            }
            profile.blockedWebsites.forEach { site ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("• $site", color = RasFocusColors.OnBackground)
                    IconButton(onClick = { onUpdate(profile.copy(blockedWebsites = profile.blockedWebsites.filter { it != site })) }) { Icon(Icons.Filled.Close, tint = RasFocusColors.ErrorRed, contentDescription = null) }
                }
            }
        }
        HorizontalDivider()
        Column {
            Text("Applications (${profile.blockedApps.size})", fontWeight = FontWeight.Bold, color = RasFocusColors.OnBackground)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = appInput, onValueChange = { appInput = it }, placeholder = { Text("e.g. com.instagram.android") }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(8.dp))
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (appInput.isNotBlank()) { onUpdate(profile.copy(blockedApps = profile.blockedApps + appInput)); appInput = "" } }, shape = RoundedCornerShape(8.dp)) { Text("+ Add") }
            }
            profile.blockedApps.forEach { app ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("• $app", color = RasFocusColors.OnBackground)
                    IconButton(onClick = { onUpdate(profile.copy(blockedApps = profile.blockedApps.filter { it != app })) }) { Icon(Icons.Filled.Close, tint = RasFocusColors.ErrorRed, contentDescription = null) }
                }
            }
        }
    }
}

// ─── TAB 4: ADULT BLOCK ───
@Composable
fun AdultBlockTab(profile: FocusProfile, onUpdate: (FocusProfile) -> Unit) {
    var keyInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Safe Browsing Rules", fontWeight = FontWeight.Bold, color = RasFocusColors.OnBackground)
        val rules = listOf(
            Triple("Block Adult Websites", "Resource list of explicit sites", profile.adultWeb),
            Triple("Block Hardcore Keywords", "Filters porn, NSFW terms", profile.hardcore),
            Triple("Block Romantic Content", "Softcore, suggestive content", profile.romantic),
            Triple("Strict DNS & SafeSearch", "Forces safe search on all browsers", profile.strictDns)
        )
        rules.forEach { (title, desc, isActive) ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, color = if (isActive) RasFocusColors.PrimaryTeal else RasFocusColors.OnBackground)
                    Text(desc, fontSize = 12.sp, color = RasFocusColors.SubtleText)
                }
                Switch(checked = isActive, onCheckedChange = { checked ->
                    when (title) {
                        "Block Adult Websites"     -> onUpdate(profile.copy(adultWeb = checked))
                        "Block Hardcore Keywords"  -> onUpdate(profile.copy(hardcore = checked))
                        "Block Romantic Content"   -> onUpdate(profile.copy(romantic = checked))
                        "Strict DNS & SafeSearch"  -> onUpdate(profile.copy(strictDns = checked))
                    }
                })
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Custom Bad Words (${profile.adultCustomKeywords.size})", fontWeight = FontWeight.Bold, color = RasFocusColors.OnBackground)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = keyInput, onValueChange = { keyInput = it }, placeholder = { Text("e.g. badword") }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(8.dp))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { if (keyInput.isNotBlank()) { onUpdate(profile.copy(adultCustomKeywords = profile.adultCustomKeywords + keyInput)); keyInput = "" } }, shape = RoundedCornerShape(8.dp)) { Text("+ Add") }
        }
        profile.adultCustomKeywords.forEach { word ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("• $word", color = RasFocusColors.OnBackground)
                IconButton(onClick = { onUpdate(profile.copy(adultCustomKeywords = profile.adultCustomKeywords.filter { it != word })) }) { Icon(Icons.Filled.Close, tint = RasFocusColors.ErrorRed, contentDescription = null) }
            }
        }
    }
}

// ============================================================
// DIALOGS
// ============================================================
@Composable
fun ParentsPasswordDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ENTER PARENTS PASSWORD", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
        text = { OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }) },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.PrimaryTeal)) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TextUnlockDialog(onDismiss: () -> Unit, onUnlock: () -> Unit) {
    var typedText by remember { mutableStateOf("") }
    val targetText = "To unlock this device, you must realize that focus is the key to success..."
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("EXACT TEXT UNLOCK MODE", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = RasFocusColors.OnBackground)
                Spacer(Modifier.height(10.dp))
                Text(targetText, fontSize = 13.sp, color = RasFocusColors.SubtleText)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = typedText, onValueChange = { typedText = it }, modifier = Modifier.fillMaxWidth().height(150.dp), placeholder = { Text("Type here...") })
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onUnlock, enabled = typedText == targetText, colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.PrimaryTeal)) { Text("Unlock Profile") }
                }
            }
        }
    }
}

@Composable
fun TimerSetDialog(onDismiss: () -> Unit, onStart: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SET FOCUS DURATION", fontWeight = FontWeight.Bold) },
        text = { Text("Session শুরু করতে confirm করুন।") },
        confirmButton = { Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = RasFocusColors.PrimaryTeal)) { Text("Start Profile") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
