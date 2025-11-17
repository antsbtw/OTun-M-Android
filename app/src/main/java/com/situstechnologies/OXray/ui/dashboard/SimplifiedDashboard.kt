package com.situstechnologies.OXray.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Simplified Dashboard - Main VPN Control Screen
 *
 * Ported from iOS SimplifiedDashboard.swift
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimplifiedDashboardScreen(
    viewModel: DashboardViewModel,
    onImportConfig: () -> Unit,
    onShowSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VPN") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Routing Mode Menu
                    if (uiState.selectedProfile != null) {
                        RoutingModeMenu(
                            currentMode = uiState.currentRoutingMode,
                            onModeSelected = { mode ->
                                scope.launch {
                                    viewModel.switchRoutingMode(mode)
                                }
                            }
                        )
                    }

                    // Import Button
                    IconButton(onClick = onImportConfig) {
                        Icon(Icons.Default.Add, contentDescription = "Import Configuration")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            StatusCard(
                connectionStatus = uiState.connectionStatus,
                selectedProfile = uiState.selectedProfile,
                testModeInfo = uiState.testModeInfo,
                onConnect = {
                    scope.launch {
                        viewModel.connect()
                    }
                },
                onDisconnect = {
                    scope.launch {
                        viewModel.disconnect()
                    }
                }
            )

            // Connection Stats (when connected)
            if (uiState.connectionStatus == ConnectionStatus.CONNECTED) {
                ConnectionStatsCard(
                    uploadSpeed = uiState.uploadSpeed,
                    downloadSpeed = uiState.downloadSpeed,
                    uploadTotal = uiState.uploadTotal,
                    downloadTotal = uiState.downloadTotal
                )
            }

            // Configuration Info Card
            uiState.selectedProfile?.let { profile ->
                ConfigInfoCard(profile)
            }

            Divider()

            // Configuration List
            ConfigurationList(
                profiles = uiState.profiles,
                selectedProfile = uiState.selectedProfile,
                onProfileSelected = { profile ->
                    scope.launch {
                        viewModel.selectProfile(profile)
                    }
                },
                onProfileDeleted = { profile ->
                    scope.launch {
                        viewModel.deleteProfile(profile)
                    }
                }
            )
        }
    }

    // Test Mode Expiry Alert
    if (uiState.showTestExpiryAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissTestExpiryAlert() },
            title = { Text("Test Account") },
            text = { Text(uiState.testExpiryMessage) },
            confirmButton = {
                if (uiState.testExpiryMessage.contains("expired")) {
                    TextButton(onClick = {
                        scope.launch {
                            uiState.selectedProfile?.let { viewModel.deleteProfile(it) }
                            viewModel.dismissTestExpiryAlert()
                        }
                    }) {
                        Text("Delete")
                    }
                } else {
                    TextButton(onClick = { viewModel.dismissTestExpiryAlert() }) {
                        Text("OK")
                    }
                }
            },
            dismissButton = {
                if (uiState.testExpiryMessage.contains("expired")) {
                    TextButton(onClick = { viewModel.dismissTestExpiryAlert() }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

/**
 * Status Card - VPN Connection Control
 */
@Composable
fun StatusCard(
    connectionStatus: ConnectionStatus,
    selectedProfile: Profile?,
    testModeInfo: TestModeInfo?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Test Mode Indicator
            testModeInfo?.let { info ->
                if (!info.isExpired) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = Color(0xFFFF9800)
                        )
                        Text(
                            "Test account: ${info.remainingMinutes} min remaining",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF9800)
                        )
                    }
                }
            }

            // Status Text
            Text(
                text = connectionStatus.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = connectionStatus.color
            )

            // Connect/Disconnect Button
            Button(
                onClick = {
                    when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> onDisconnect()
                        else -> onConnect()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedProfile != null &&
                        connectionStatus != ConnectionStatus.CONNECTING &&
                        connectionStatus != ConnectionStatus.DISCONNECTING,
                        colors = ButtonDefaults.buttonColors()

            ) {
                Icon(
                    imageVector = if (connectionStatus == ConnectionStatus.CONNECTED) {
                        Icons.Default.Stop
                    } else {
                        Icons.Default.PlayArrow
                    },
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (connectionStatus == ConnectionStatus.CONNECTED) "Disconnect" else "Connect",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Connection Stats Card
 */
@Composable
fun ConnectionStatsCard(
    uploadSpeed: String,
    downloadSpeed: String,
    uploadTotal: String,
    downloadTotal: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Connection Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.Default.Upload,
                    label = "Upload",
                    speed = uploadSpeed,
                    total = uploadTotal
                )

                StatItem(
                    icon = Icons.Default.Download,
                    label = "Download",
                    speed = downloadSpeed,
                    total = downloadTotal
                )
            }
        }
    }
}

@Composable
fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    speed: String,
    total: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(speed, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(total, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

/**
 * Config Info Card
 */
@Composable
fun ConfigInfoCard(profile: Profile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Column {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Ready to connect",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

/**
 * Connection Status Enum
 */
enum class ConnectionStatus(val displayName: String, val color: Color) {
    CONNECTED("Connected", Color(0xFF4CAF50)),
    CONNECTING("Connecting...", Color(0xFFFF9800)),
    DISCONNECTING("Disconnecting...", Color(0xFFFF9800)),
    DISCONNECTED("Disconnected", Color.Gray)
}

// Placeholder data classes (to be defined elsewhere)
data class Profile(val id: Long, val name: String, val path: String)
data class TestModeInfo(val remainingMinutes: Int, val isExpired: Boolean)
