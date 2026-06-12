package com.rasel.RasFocus.parental

// ============================================================
// RASFOCUS+ PARENTAL CONTROL MODULE (CRITICAL UI UPDATE)
// Removed static instructional text, fixed empty top space,
// and added two specific fixed buttons for Mobile/PC connection.
// ============================================================

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import android.content.Context
import android.widget.Toast
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.rasel.RasFocus.* // Import common things like MainViewModel, Device, DeviceType, BlockedApp, Routes, RasFocusColors

// ─────────────────────────────────────────────────────────────
// CONSTANTS
// ─────────────────────────────────────────────────────────────
private const val CardRadius = 16

// ─────────────────────────────────────────────────────────────
// CRITICAL FIX: Updated State to pass device type to connect screen
// ─────────────────────────────────────────────────────────────
sealed class HomeUIState {
    object HOME : HomeUIState()
    // CONNECT state now accepts DeviceType to show correct UI
    data class CONNECT(val deviceType: DeviceType) : HomeUIState()
    data class DASHBOARD(val deviceId: String) : HomeUIState()
    data class DETAIL(val deviceId: String) : HomeUIState()
}

@Composable
fun ParentalRootScreen(
    viewModel: MainViewModel,
    isComboMode: Boolean = false  // ✅ Combo mode flag — HomeScreen এর header conditionally hide করবে
) {
    // UPDATED: Starting with explicit HOME state
    var uiState by remember { mutableStateOf<HomeUIState>(HomeUIState.HOME) }
    
    Crossfade(targetState = uiState, label = "parental_ui_fade") { state ->
        when (state) {
            HomeUIState.HOME -> ParentalHomeScreen(
                viewModel = viewModel,
                isComboMode = isComboMode,
                // Navigate to connect screen with specific device type
                onNavigateToConnect = { type -> uiState = HomeUIState.CONNECT(type) },
                onNavigateToDashboard = { id -> uiState = HomeUIState.DASHBOARD(id) }
            )
            is HomeUIState.CONNECT -> ParentalConnectScreen(
                viewModel = viewModel,
                deviceType = state.deviceType, // Pass device type
                onBack = { uiState = HomeUIState.HOME }
            )
            is HomeUIState.DASHBOARD -> ParentalDashboardScreen(
                viewModel = viewModel,
                deviceId = state.deviceId, // Ensure dashboard gets correct ID
                onBack = { uiState = HomeUIState.HOME },
                onNavigateToDetail = { id -> uiState = HomeUIState.DETAIL(id) }
            )
            is HomeUIState.DETAIL -> ParentalDetailScreen(
                viewModel = viewModel,
                deviceId = state.deviceId, // Ensure detail screen gets correct ID
                onBack = { uiState = HomeUIState.DASHBOARD(state.deviceId) }
            )
        }
    }
}

// ============================================================
// SECTION 1 — NEW & BEAUTIFUL HOME SCREEN (FIXED BUTTONS)
// ============================================================
@Composable
fun ParentalHomeScreen(
    viewModel: MainViewModel,
    isComboMode: Boolean = false,  // ✅ Combo mode এ gradient header scroll করলে hide হবে
    onNavigateToConnect: (DeviceType) -> Unit,
    onNavigateToDashboard: (String) -> Unit
) {
    val devices by viewModel.devices.collectAsState()
    val totalDevices = devices.size
    val onlineCount = devices.count { it.isOnline }
    val lockedCount = devices.count { it.isLocked }
    val density = LocalDensity.current

    // ── Header height (শুধু isComboMode=false এর জন্য প্রযোজ্য) ──
    // Combo mode এ header নেই, তাই collapse দরকার নেই
    val headerHeightDp = if (!isComboMode) 220.dp else 0.dp
    val headerHeightPx = with(density) { headerHeightDp.toPx() }

    // Header এর current offset (0 = সম্পূর্ণ দৃশ্যমান, -headerHeightPx = সম্পূর্ণ লুকানো)
    val headerOffsetPx = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    // NestedScrollConnection: LazyColumn scroll ইভেন্ট ধরে header offset আপডেট করে
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = (headerOffsetPx.floatValue + delta).coerceIn(-headerHeightPx, 0f)
                headerOffsetPx.floatValue = newOffset
                return Offset.Zero // LazyColumn নিজেই বাকি scroll handle করবে
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RasFocusColors.BackgroundWhite)
            .nestedScroll(nestedScrollConnection)
    ) {
        // ── LazyColumn: header এর height যতটুকু সেটুকু top padding দিয়ে শুরু হবে ──
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = headerHeightDp + 28.dp, // header এর নিচ থেকে শুরু
                bottom = 16.dp,
                start = 20.dp,
                end = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Section: Connect New Device ──
            item {
                Text(
                    "CONNECT NEW DEVICE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = RasFocusColors.SubtleText,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ConnectionCard(
                        type = DeviceType.MOBILE,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToConnect(DeviceType.MOBILE) }
                    )
                    ConnectionCard(
                        type = DeviceType.PC,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToConnect(DeviceType.PC) }
                    )
                }
                HorizontalDivider(
                    color = RasFocusColors.DividerColor,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )
            }

            // ── Section: Registered Devices Header ──
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "REGISTERED DEVICES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = RasFocusColors.SubtleText,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(RasFocusColors.PrimaryTeal.copy(0.1f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            totalDevices.toString(),
                            fontSize = 10.sp,
                            color = RasFocusColors.PrimaryTeal,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Section: Device List ──
            if (devices.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.DevicesOther, null, tint = RasFocusColors.DividerColor, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No devices connected yet.", fontSize = 16.sp, color = RasFocusColors.SubtleText, fontWeight = FontWeight.Bold)
                        Text("Click buttons above to pair.", fontSize = 14.sp, color = RasFocusColors.SubtleText.copy(0.8f))
                    }
                }
            } else {
                items(devices) { device ->
                    NewDeviceListItem(device = device, onNavigateToDashboard = onNavigateToDashboard)
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }

        // ── Collapsing Header: isComboMode=false হলেই দেখাবে ──
        // offset অনুযায়ী উপরে উঠে যাবে (smooth animation)
        if (!isComboMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(x = 0, y = headerOffsetPx.floatValue.roundToInt()) }
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                RasFocusColors.PrimaryTeal,
                                RasFocusColors.PrimaryTeal.copy(alpha = 0.85f)
                            )
                        ),
                        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    )
                    .padding(horizontal = 24.dp)
                    .padding(top = 48.dp, bottom = 32.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Security, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Family Dashboard",
                            fontSize = 14.sp,
                            color = Color.White.copy(0.7f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Welcome, Parent",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        lineHeight = 34.sp
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatQuickCard(
                            title = "Total Devices",
                            value = totalDevices.toString(),
                            icon = Icons.Outlined.DeviceUnknown,
                            modifier = Modifier.weight(1f)
                        )
                        StatQuickCard(
                            title = "Online Now",
                            value = onlineCount.toString(),
                            icon = Icons.Outlined.CellWifi,
                            modifier = Modifier.weight(1f),
                            accent = RasFocusColors.SuccessGreen
                        )
                        StatQuickCard(
                            title = "Currently Locked",
                            value = lockedCount.toString(),
                            icon = Icons.Outlined.LockClock,
                            modifier = Modifier.weight(1f),
                            accent = RasFocusColors.ErrorRed
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Beautiful Connection Card (The Fixed Buttons)
// ─────────────────────────────────────────────────────────────
@Composable
fun ConnectionCard(type: DeviceType, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val isMobile = type == DeviceType.MOBILE
    val title = if (isMobile) "Add Child\nMobile" else "Add Child\nPC"
    val icon = if (isMobile) Icons.Filled.Smartphone else Icons.Filled.Computer
    val bgColor = if (isMobile) RasFocusColors.SurfaceCard else Color(0xFFF1F8E9) // Tealish or Greenish
    val accentColor = if (isMobile) RasFocusColors.PrimaryTeal else RasFocusColors.SuccessGreen

    Card(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = RasFocusColors.CardShadow,
                spotColor = RasFocusColors.CardShadow
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, accentColor.copy(0.2f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(56.dp).background(accentColor.copy(0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = accentColor, modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun StatQuickCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, accent: Color = Color.White) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.1f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(12.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = accent)
            Text(title, fontSize = 10.sp, color = Color.White.copy(0.8f))
        }
    }
}

@Composable
fun NewDeviceListItem(device: Device, onNavigateToDashboard: (String) -> Unit) {
    val statusColor = if (device.isOnline) RasFocusColors.SuccessGreen else RasFocusColors.SubtleText.copy(0.6f)
    val deviceIcon = if (device.type == DeviceType.MOBILE) Icons.Filled.Smartphone else Icons.Filled.Computer

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onNavigateToDashboard(device.id) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RasFocusColors.SurfaceOffWhite),
        border = BorderStroke(1.dp, RasFocusColors.DividerColor)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(RasFocusColors.PrimaryTeal.copy(0.1f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(deviceIcon, null, tint = RasFocusColors.PrimaryTeal)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = RasFocusColors.OnBackground)
                Text("Owner: ${device.ownerName}", fontSize = 13.sp, color = RasFocusColors.SubtleText)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).background(statusColor, CircleShape))
                    Spacer(Modifier.width(5.dp))
                    Text(if (device.isOnline) "Online" else "Offline", fontSize = 11.sp, color = statusColor)
                    if (device.isOnline) {
                        Spacer(Modifier.width(8.dp))
                        Text("🔋${device.batteryLevel}%", fontSize = 11.sp, color = RasFocusColors.SubtleText)
                    }
                }
            }
            if (device.isLocked) {
                Icon(Icons.Filled.Lock, null, tint = RasFocusColors.ErrorRed, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Filled.ChevronRight, null, tint = RasFocusColors.SubtleText)
            }
        }
    }
}

// ============================================================
// SECTION 2 — REFACTORED CONNECT SCREEN
// ============================================================
@Composable
fun ParentalConnectScreen(
    viewModel: MainViewModel,
    deviceType: DeviceType, // UPDATED: Correct device type is passed here
    onBack: () -> Unit
) {
    val connectionPin by viewModel.connectionPin.collectAsState()
    val isMobile = deviceType == DeviceType.MOBILE
    val targetTypeLabel = if (isMobile) "Child Mobile" else "Child PC"
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().background(RasFocusColors.BackgroundWhite).systemBarsPadding()
    ) {
        // ── Clean Header ──
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = RasFocusColors.OnBackground) }
                Spacer(Modifier.width(16.dp))
                Text("Connect $targetTypeLabel", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RasFocusColors.OnBackground)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            item {
                Box(
                    modifier = Modifier.size(72.dp).background(RasFocusColors.PrimaryTeal.copy(0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isMobile) "📱" else "💻", fontSize = 40.sp)
                }
                Spacer(Modifier.height(16.dp))
                Text("Pairing Code", fontSize = 26.sp, fontWeight = FontWeight.Black, color = RasFocusColors.OnBackground)
                Text("Install RasFocus on the ${targetTypeLabel.lowercase()}.", fontSize = 15.sp, color = RasFocusColors.SubtleText, textAlign = TextAlign.Center)
                
                Spacer(Modifier.height(32.dp))
                
                // ── The PIN Display (The static thing we generated and kept) ──
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    connectionPin.forEach { digit ->
                        PinDigitCard(digit = digit.toString())
                    }
                }
                
                Spacer(Modifier.height(32.dp))
                
                Text("SCAN QR CODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RasFocusColors.SubtleText, letterSpacing = 1.sp)
                Spacer(Modifier.height(12.dp))
                
                // ── MOCK QR CODE ──
                Card(
                    shape = RoundedCornerShape(CardRadius.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(2.dp, RasFocusColors.DividerColor),
                    modifier = Modifier.size(200.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Icon(Icons.Filled.QrCode, null, tint = RasFocusColors.OnBackground, modifier = Modifier.fillMaxSize())
                        Icon(Icons.Filled.QrCodeScanner, null, tint = Color.Black.copy(alpha = 0.6f), modifier = Modifier.size(40.dp).align(Alignment.Center))
                    }
                }
                
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun PinDigitCard(digit: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RasFocusColors.SurfaceCard),
        border = BorderStroke(2.dp, RasFocusColors.PrimaryTeal.copy(0.3f)),
        modifier = Modifier.size(width = 45.dp, height = 65.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(digit, fontSize = 34.sp, fontWeight = FontWeight.Black, color = RasFocusColors.PrimaryTeal)
        }
    }
}


// ============================================================
// SECTION 3 — PARENTAL DASHBOARD & DETAIL (RETAINED FROM ORIGINAL)
// ============================================================
@Composable
fun ParentalDashboardScreen(viewModel: MainViewModel, deviceId: String? = null, onBack: () -> Unit = {}, onNavigateToDetail: (String) -> Unit) {
    // We only need the first part to show a dashboard of all mobile devices or if a specific ID is given.
    val devices by viewModel.devices.collectAsState()
    val tab by viewModel.selectedDeviceTab.collectAsState()

    // ── CRITICAL FIX: Ensure detailed dashboard for all registered mobile devices, not just the list. ──
    val filteredDevices = if (!deviceId.isNullOrEmpty()) {
        devices.filter { it.id == deviceId }
    } else {
        devices.filter { it.type == tab }
    }

    if (devices.isEmpty()) {
        // If no devices connected, show a cleaner empty state (re-using part of your instructional flow but cleaner)
        ParentalNoDeviceHome(viewModel = viewModel, onBack = onBack)
        return
    }

    // Modern Dashboard Layout
    SurfaceCard {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!deviceId.isNullOrEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = RasFocusColors.PrimaryTeal) }
                        Text("Device Overview", style = MaterialTheme.typography.titleMedium, color = RasFocusColors.PrimaryTeal, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                item {
                    // Mobile/PC Tabs
                    DashboardTabs(selected = tab, onSelected = { viewModel.selectDeviceTab(it) })
                }
            }

            if (filteredDevices.isEmpty()) {
                item { NoDeviceState(tab = tab) }
            } else {
                item { ScreenTimeStatCard() }
                item { HalalGuardStatusCard() }
                item { DistractionBlocksChart() }
                
                // Device list header (Remove instruction static text)
                item {
                    Text("REGISTERED DEVICES", style = MaterialTheme.typography.labelMedium, color = RasFocusColors.SubtleText, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }

                items(filteredDevices) { device ->
                    NewDeviceListItem(device = device, onNavigateToDashboard = { onNavigateToDetail(device.id) })
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

// ── Supporting Composables (Retaied from original) ──
@Composable
fun StatStatQuickCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, accent: Color = Color.White) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.1f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(12.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = accent)
            Text(title, fontSize = 10.sp, color = Color.White.copy(0.8f))
        }
    }
}
@Composable
fun ServiceListItem(icon: ImageVector, title: String, subtitle: String, isHalal: Boolean, statusColor: Color, modifier: Modifier = Modifier, trailingContent: @Composable RowScope.() -> Unit = {}) {
    val alpha = if (isHalal) 1f else 0.4f
    Row(modifier = modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(44.dp).background(statusColor.copy(0.12f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = statusColor, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = RasFocusColors.OnBackground)
            Text(subtitle, fontSize = 12.sp, color = RasFocusColors.SubtleText)
        }
        Row(verticalAlignment = Alignment.CenterVertically) { trailingContent() }
    }
}
@Composable
fun HalalGuardStatusCard() {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(CardRadius.dp), colors = CardDefaults.cardColors(containerColor = RasFocusColors.SurfaceOffWhite)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.HealthAndSafety, null, tint = RasFocusColors.PrimaryTeal)
                Spacer(Modifier.width(8.dp))
                Text("Halal Guard Status", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = RasFocusColors.OnBackground)
                Spacer(Modifier.weight(1f))
                Box(Modifier.background(RasFocusColors.PrimaryTeal.copy(0.1f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp)) { Text("Enabled", fontSize = 11.sp, color = RasFocusColors.PrimaryTeal) }
            }
            Spacer(Modifier.height(12.dp))
            listOf("YouTube Shorts", "Instagram Reels", "TikTok", "Pornography sites").forEach { service ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = RasFocusColors.SuccessGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(service, fontSize = 13.sp, color = RasFocusColors.SubtleText)
                }
            }
        }
    }
}
@Composable
fun DistractionBlocksChart() {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(CardRadius.dp), colors = CardDefaults.cardColors(containerColor = RasFocusColors.SurfaceOffWhite)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timeline, null, tint = RasFocusColors.PrimaryTeal)
                Spacer(Modifier.width(8.dp))
                Text("Distraction Blocks (7 Days)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = RasFocusColors.OnBackground)
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                // Mock chart bars
                listOf(5, 8, 3, 10, 4, 12, 6).forEach { value ->
                    val h = (value * 8).dp
                    Box(modifier = Modifier.weight(1f).height(h).background(RasFocusColors.PrimaryTeal, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                }
            }
        }
    }
}
@Composable
fun ParentalDetailScreen(viewModel: MainViewModel, deviceId: String? = null, onBack: () -> Unit) {
    if (deviceId == null) {
        onBack()
        return
    }
    val devices by viewModel.devices.collectAsState()
    val device = devices.find { it.id == deviceId } ?: run { onBack(); return }
    val tab by viewModel.selectedDeviceTab.collectAsState()
    val isMobile = tab == DeviceType.MOBILE
    val context = LocalContext.current
    SurfaceCard {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = RasFocusColors.PrimaryTeal) }
                    Text("Device Detail", style = MaterialTheme.typography.titleMedium, color = RasFocusColors.PrimaryTeal, fontWeight = FontWeight.Bold)
                }
            }
            item { DeviceDetailHeader(device = device, context = context, viewModel = viewModel, tab = tab) }
            item {
                ServiceListItem(
                    icon = Icons.Outlined.AppShortcut,
                    title = "App Blocking",
                    subtitle = "Monitor & block unwanted apps",
                    isHalal = true,
                    statusColor = RasFocusColors.PrimaryTeal
                ) {
                    TextButton(onClick = {}) { Text("Manage Apps", fontSize = 13.sp, color = RasFocusColors.PrimaryTeal) }
                }
            }
            item {
                HorizontalDivider(color = RasFocusColors.DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                // Halal Guard Controls
                ServiceListItem(
                    icon = Icons.Filled.HealthAndSafety,
                    title = "Halal Guard",
                    subtitle = "Remote filter commands push in real-time",
                    isHalal = true,
                    statusColor = RasFocusColors.SuccessGreen
                ) {
                    Switch(
                        checked = device.isHalalGuardOn,
                        onCheckedChange = { viewModel.toggleHalalGuard(device.id) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = RasFocusColors.SuccessGreen)
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = true, onClick = {}, label = { Text("YT Shorts") }, leadingIcon = { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) })
                    FilterChip(selected = true, onClick = {}, label = { Text("Reels") }, leadingIcon = { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) })
                    FilterChip(selected = false, onClick = {}, label = { Text("Incognito") })
                }
            }
            item { DistractionBlocksChart() }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}
@Composable
fun DeviceDetailHeader(device: Device, context: Context, viewModel: MainViewModel, tab: DeviceType) {
    val isMobile = tab == DeviceType.MOBILE
    val batteryColor = when {
        device.batteryLevel > 70 -> RasFocusColors.SuccessGreen
        device.batteryLevel > 30 -> RasFocusColors.WarningAmber
        else -> RasFocusColors.ErrorRed
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(CardRadius.dp), colors = CardDefaults.cardColors(containerColor = RasFocusColors.SurfaceOffWhite)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).background(RasFocusColors.PrimaryTeal.copy(0.12f), CircleShape), contentAlignment = Alignment.Center) { Icon(if (isMobile) Icons.Filled.Smartphone else Icons.Filled.Computer, null, tint = RasFocusColors.PrimaryTeal) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(device.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = RasFocusColors.OnBackground)
                    Text("Owner: ${device.ownerName}", fontSize = 13.sp, color = RasFocusColors.SubtleText)
                }
                Text("🔋${device.batteryLevel}%", color = batteryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Lock Button — Pushes command to Firebase
                ActionControlButton(icon = if (device.isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen, label = if (device.isLocked) "Locked" else "LockNow", modifier = Modifier.weight(1f), isDestructive = device.isLocked) {
                    viewModel.toggleDeviceLock(device.id)
                }
                ActionControlButton(icon = Icons.Outlined.PhotoCamera, label = "SnapShot", modifier = Modifier.weight(1f)) {
                    // Logic to request snapshot via Firebase command
                    Toast.makeText(context, "Snapshot command requested!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
@Composable
fun ActionControlButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, isDestructive: Boolean = false, onClick: () -> Unit) {
    val color = if (isDestructive) RasFocusColors.ErrorRed else RasFocusColors.PrimaryTeal
    val bgColor = if (isDestructive) RasFocusColors.ErrorRed.copy(0.08f) else RasFocusColors.PrimaryTeal.copy(0.08f)
    Button(onClick = onClick, modifier = modifier.height(44.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = bgColor, contentColor = color), contentPadding = PaddingValues(horizontal = 8.dp), border = BorderStroke(1.dp, color.copy(0.2f))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
@Composable
fun SurfaceCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxSize().shadow(elevation = 16.dp, shape = RasFocusShapes.BottomSheet, ambientColor = RasFocusColors.CardShadow, spotColor = RasFocusColors.CardShadow), shape = RasFocusShapes.BottomSheet, colors = CardDefaults.cardColors(containerColor = Color.White), content = { content() })
}
@Composable
fun ParentalNoDeviceHome(viewModel: MainViewModel, onBack: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxSize().background(RasFocusColors.SurfaceOffWhite)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.size(72.dp).background(RasFocusColors.PrimaryTeal.copy(0.12f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Filled.DevicesOther, null, tint = RasFocusColors.PrimaryTeal, modifier = Modifier.size(36.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("Register Devices", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = RasFocusColors.PrimaryTeal, textAlign = TextAlign.Center)
            Text("Keep your family safe with RasFocus+ Halal Guard.", style = MaterialTheme.typography.bodyMedium, color = RasFocusColors.SubtleText, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(48.dp))
            
            // Cleaned up NoDevice List from instructional flow
            listOf(Pair(Icons.Filled.PersonAdd, "Add your child's mobile"), Pair(Icons.Filled.Computer, "Connect the child's PC")).forEach { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(44.dp).background(RasFocusColors.PrimaryTeal.copy(0.08f), CircleShape), contentAlignment = Alignment.Center) { Icon(item.first, null, tint = RasFocusColors.PrimaryTeal) }
                    Spacer(Modifier.width(14.dp))
                    Text(item.second, fontWeight = FontWeight.SemiBold, color = RasFocusColors.OnBackground)
                }
            }
        }
    }
}
@Composable
fun NoDeviceState(tab: DeviceType) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(if (tab == DeviceType.MOBILE) Icons.Filled.Smartphone else Icons.Filled.Computer, null, tint = RasFocusColors.DividerColor, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(if (tab == DeviceType.MOBILE) "No Child Mobiles Connected" else "No Child PCs Connected", style = MaterialTheme.typography.titleMedium, color = RasFocusColors.SubtleText, fontWeight = FontWeight.Bold)
        Text("Scan QR code from the child's device settings.", style = MaterialTheme.typography.bodySmall, color = RasFocusColors.SubtleText)
    }
}
@Composable
fun ScreenTimeStatCard() {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(CardRadius.dp), colors = CardDefaults.cardColors(containerColor = RasFocusColors.SurfaceOffWhite)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, null, tint = RasFocusColors.PrimaryTeal)
                Spacer(Modifier.width(8.dp))
                Text("Screen Time Overview", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = RasFocusColors.OnBackground)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.MoreHoriz, null, tint = RasFocusColors.SubtleText)
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Focus progress circle
                Box(modifier = Modifier.size(70.dp).scale(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = 0.65f, color = RasFocusColors.PrimaryTeal, strokeWidth = 8.dp, modifier = Modifier.fillMaxSize())
                    Text("65%", fontWeight = FontWeight.Bold, color = RasFocusColors.PrimaryTeal)
                }
                // Stats numbers
                Column(Modifier.weight(1f)) {
                    Text("Focus: 4h 15m", fontWeight = FontWeight.Bold, color = RasFocusColors.OnBackground)
                    Text("Distraction: 1h 20m", color = RasFocusColors.SubtleText)
                }
            }
        }
    }
}
@Composable
fun DashboardTabs(selected: DeviceType, onSelected: (DeviceType) -> Unit) {
    TabRow(selectedTabIndex = selected.ordinal, containerColor = Color.Transparent, contentColor = RasFocusColors.PrimaryTeal, indicator = { TabRowDefaults.Indicator(modifier = Modifier.tabIndicatorOffset(it[selected.ordinal]), color = RasFocusColors.PrimaryTeal, height = 3.dp) }, divider = { HorizontalDivider(color = RasFocusColors.DividerColor) }) {
        DeviceType.values().forEach { type ->
            Tab(selected = selected == type, onClick = { onSelected(type) }, text = { Text(if (type == DeviceType.MOBILE) "Mobiles" else "PCs", fontWeight = FontWeight.Bold, fontSize = 15.sp) })
        }
    }
}
