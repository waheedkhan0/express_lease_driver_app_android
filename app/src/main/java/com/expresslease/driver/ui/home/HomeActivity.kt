package com.expresslease.driver.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expresslease.driver.model.Bike
import com.expresslease.driver.service.GpsTrackingService
import com.expresslease.driver.service.GpsWorker
import com.expresslease.driver.ui.login.LoginActivity
import com.expresslease.driver.ui.theme.ExpressLeaseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val bgGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == true
        } else true
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] == true
        } else true

        if (fineGranted) {
            if (bgGranted) {
                startGpsService()
            } else {
                // Guide user to settings for background location
                viewModel.setPermissionWarning("Please grant 'Allow all the time' location permission in Settings for background tracking")
            }
        } else {
            viewModel.setPermissionWarning("Location permission denied — tracking inactive")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsAndStart()

        setContent {
            ExpressLeaseTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(uiState.navigateToLogin) {
                    if (uiState.navigateToLogin) {
                        startActivity(Intent(this@HomeActivity, LoginActivity::class.java))
                        finish()
                    }
                }

                HomeScreen(
                    uiState = uiState,
                    onSelectBike = { viewModel.selectBike(it) },
                    onRefresh = { viewModel.loadBikes() },
                    onLogout = { viewModel.logout() },
                    onOpenSettings = {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .apply { data = Uri.fromParts("package", packageName, null) }
                        )
                    },
                )
            }
        }
    }

    private fun requestPermissionsAndStart() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ).also {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                it.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                it.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        locationPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startGpsService() {
        val intent = GpsTrackingService.startIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        GpsWorker.schedule(this)
        viewModel.setTrackingActive(true)
    }
}

// ─── Composable UI ─────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onSelectBike: (Bike) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showBikeSheet by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        LogoutDialog(
            onConfirm = { showLogoutDialog = false; onLogout() },
            onDismiss = { showLogoutDialog = false },
        )
    }

    if (showBikeSheet && uiState.bikes.isNotEmpty()) {
        BikeSelectionSheet(
            bikes = uiState.bikes,
            selectedChassis = uiState.selectedBike?.chassisNumber,
            onSelect = { onSelectBike(it); showBikeSheet = false },
            onDismiss = { showBikeSheet = false },
        )
    }

    Scaffold(
        containerColor = Color(0xFF0F172A),
        topBar = {
            TopBar(
                driverName = uiState.driverName,
                isLoading = uiState.isLoading,
                onRefresh = onRefresh,
                onLogout = { showLogoutDialog = true },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // Permission warning
            if (uiState.permissionWarning != null) {
                WarningBanner(
                    message = uiState.permissionWarning,
                    onAction = onOpenSettings,
                    actionLabel = "Open Settings",
                )
            }

            // Bike selector card
            BikeCard(
                selectedBike = uiState.selectedBike,
                isLoading = uiState.isLoading,
                onClick = { showBikeSheet = true },
            )

            // Vehicle details
            if (uiState.selectedBike != null) {
                VehicleDetailsCard(bike = uiState.selectedBike)
            }

            // Radar + GPS panel
            TrackingPanel(
                isActive = uiState.isTrackingActive,
                latitude = uiState.lastLat,
                longitude = uiState.lastLng,
                lastSyncTime = uiState.lastSyncTime,
                syncSuccess = uiState.lastSyncSuccess,
                pendingCount = uiState.pendingCount,
                statusMessage = uiState.statusMessage,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    driverName: String,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF020617),
            titleContentColor = Color.White,
        ),
        title = {
            Column {
                Text(
                    "EXPRESS LEASE",
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp,
                    fontSize = 18.sp,
                )
                Text(
                    "Welcome, $driverName",
                    fontSize = 11.sp,
                    color = Color(0xFF38BDF8),
                    letterSpacing = 0.5.sp,
                )
            }
        },
        actions = {
            IconButton(onClick = onRefresh, enabled = !isLoading) {
                if (isLoading)
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF38BDF8))
                else
                    Icon(Icons.Default.Refresh, "Refresh bikes", tint = Color(0xFF38BDF8))
            }
            IconButton(onClick = onLogout) {
                Icon(Icons.Default.Logout, "Logout", tint = Color(0xFFF87171))
            }
        }
    )
}

@Composable
fun BikeCard(selectedBike: Bike?, isLoading: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.TwoWheeler, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "ACTIVE MOTORBIKE",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.height(4.dp))
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text(
                        selectedBike?.displayName ?: "Tap to select your bike →",
                        color = if (selectedBike != null) Color.White else Color(0xFF64748B),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(Icons.Default.ExpandMore, null, tint = Color(0xFF64748B))
        }
    }
}

@Composable
fun VehicleDetailsCard(bike: Bike) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "VEHICLE DETAILS",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                bike.state?.let { state ->
                    val (bg, border, text) = when (state.lowercase()) {
                        "leased" -> Triple(Color(0x260EA5E9), Color(0xFF0EA5E9), Color(0xFF38BDF8))
                        "available" -> Triple(Color(0x2610B981), Color(0xFF10B981), Color(0xFF34D399))
                        else -> Triple(Color(0x26F59E0B), Color(0xFFF59E0B), Color(0xFFFBBF24))
                    }
                    Surface(
                        color = bg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, border),
                    ) {
                        Text(
                            state.uppercase(),
                            color = text,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            HorizontalDivider(color = Color(0x14FFFFFF), modifier = Modifier.padding(vertical = 12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailItem("BRAND / MODEL", "${bike.brand} ${bike.modelName}".uppercase(), Icons.Default.TwoWheeler, Modifier.weight(1f))
                DetailItem("REGISTRATION", bike.registrationNumber ?: "N/A", Icons.Default.Badge, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            DetailItem("CHASSIS NUMBER", bike.chassisNumber, Icons.Default.Fingerprint, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun DetailItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFF64748B), modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 9.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        }
        Spacer(Modifier.height(3.dp))
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TrackingPanel(
    isActive: Boolean,
    latitude: Double?,
    longitude: Double?,
    lastSyncTime: String?,
    syncSuccess: Boolean,
    pendingCount: Int,
    statusMessage: String?,
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.15f else 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOut), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (isActive) Color(0x4D38BDF8) else Color(0x0DFFFFFF)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Radar icon with pulse
            Box(
                modifier = Modifier
                    .scale(pulseScale)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .border(
                        2.dp,
                        if (isActive) Color(0x4D38BDF8) else Color(0x14FFFFFF),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isActive) Icons.Default.Radar else Icons.Default.LocationOff,
                    null,
                    tint = if (isActive) Color(0xFF38BDF8) else Color(0xFF64748B),
                    modifier = Modifier.size(48.dp),
                )
            }

            // Status pill
            Surface(
                color = if (isActive) Color(0x2610B981) else Color(0x2664748B),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, if (isActive) Color(0x6610B981) else Color(0x4D64748B)),
            ) {
                Text(
                    if (isActive) "● LIVE TRACKING ACTIVE" else "○ TRACKING INACTIVE",
                    color = if (isActive) Color(0xFF34D399) else Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            // Coordinate cards
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("LATITUDE", latitude?.let { "%.6f".format(it) } ?: "--.------", Icons.Default.Explore, Modifier.weight(1f))
                MetricCard("LONGITUDE", longitude?.let { "%.6f".format(it) } ?: "--.------", Icons.Default.Explore, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("LAST SYNC", lastSyncTime ?: "--:--:--", Icons.Default.Sync, Modifier.weight(1f))
                MetricCard(
                    label = "STATUS",
                    value = when {
                        lastSyncTime == null -> "No data yet"
                        syncSuccess -> "SYNCED"
                        pendingCount > 0 -> "$pendingCount QUEUED"
                        else -> "OFFLINE"
                    },
                    icon = Icons.Default.CloudDone,
                    modifier = Modifier.weight(1f),
                    valueColor = when {
                        lastSyncTime == null -> Color(0xFF94A3B8)
                        syncSuccess -> Color(0xFF34D399)
                        else -> Color(0xFFF87171)
                    }
                )
            }

            if (statusMessage != null) {
                Text(
                    statusMessage,
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                )
            }
        }
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White,
) {
    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Color(0xFF64748B), modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(5.dp))
                Text(label, fontSize = 9.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun WarningBanner(message: String, onAction: () -> Unit, actionLabel: String) {
    Surface(
        color = Color(0x26EF4444),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0x66EF4444)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Warning, null, tint = Color(0xFFF87171), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(message, color = Color(0xFFFCA5A5), fontSize = 12.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onAction) {
                Text(actionLabel, color = Color(0xFF38BDF8), fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BikeSelectionSheet(
    bikes: List<Bike>,
    selectedChassis: String?,
    onSelect: (Bike) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
    ) {
        Column(Modifier.padding(bottom = 32.dp)) {
            Text(
                "SELECT YOUR MOTORBIKE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            bikes.forEach { bike ->
                val isSelected = bike.chassisNumber == selectedChassis
                ListItem(
                    headlineContent = {
                        Text(bike.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                    },
                    supportingContent = {
                        Text("Chassis: ${bike.chassisNumber}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.TwoWheeler,
                            null,
                            tint = if (isSelected) Color(0xFF38BDF8) else Color(0xFF64748B),
                        )
                    },
                    trailingContent = {
                        if (isSelected) Icon(Icons.Default.Check, null, tint = Color(0xFF34D399))
                    },
                    modifier = Modifier.clickable { onSelect(bike) },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isSelected) Color(0x1438BDF8) else Color.Transparent,
                    ),
                )
                HorizontalDivider(color = Color(0x08FFFFFF))
            }
        }
    }
}

@Composable
fun LogoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = { Text("Log Out", color = Color.White) },
        text = {
            Text(
                "This will stop active location tracking and clear your session.",
                color = Color(0xFF94A3B8),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("LOG OUT", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color(0xFF64748B))
            }
        },
    )
}
