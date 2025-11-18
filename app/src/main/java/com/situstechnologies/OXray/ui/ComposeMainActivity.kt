package com.situstechnologies.OXray.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.situstechnologies.OXray.ui.dashboard.SimplifiedDashboardScreen
import com.situstechnologies.OXray.ui.dashboard.DashboardViewModel
import com.situstechnologies.OXray.ui.dashboard.VpnPermissionHelper
import com.situstechnologies.OXray.ui.Oimport.ImportConfigScreen
import com.situstechnologies.OXray.ui.Oimport.ImportViewModel
import com.situstechnologies.OXray.ui.scanner.QRScannerScreen

class ComposeMainActivity : ComponentActivity() {
    
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val importViewModel: ImportViewModel by viewModels()

    // VPN 权限请求 launcher
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // 授权成功，触发实际的启动
            dashboardViewModel.startVpnService()  // 👈 改为 startVpnService
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                Surface {
                    AppNavigation()
                }
            }
        }
    }
    
    @Composable
    fun AppNavigation() {
        var showImportScreen by remember { mutableStateOf(false) }
        var showScanner by remember { mutableStateOf(false) }

        val dashboardUiState by dashboardViewModel.uiState.collectAsState()

        // 👇 添加权限检查监听
        LaunchedEffect(dashboardUiState.needsVpnPermissionCheck) {
            if (dashboardUiState.needsVpnPermissionCheck) {
                val needsPermission = VpnPermissionHelper.checkAndRequestPermission(
                    this@ComposeMainActivity,
                    vpnPermissionLauncher
                )

                if (!needsPermission) {
                    // 已有权限，直接启动
                    dashboardViewModel.startVpnService()
                }

                // 重置标志
                dashboardViewModel.resetVpnPermissionCheck()
            }
        }
        
        when {
            showScanner -> {
                QRScannerScreen(
                    onQRCodeDetected = { qrCode ->
                        importViewModel.handleScannedQR(qrCode)
                        showScanner = false
                        showImportScreen = true
                    },
                    onDismiss = { showScanner = false }
                )
            }
            
            showImportScreen -> {
                ImportConfigScreen(
                    viewModel = importViewModel,
                    onDismiss = {
                        showImportScreen = false
                        dashboardViewModel.loadProfiles()
                    },
                    onShowScanner = { showScanner = true }
                )
            }
            
            else -> {
                SimplifiedDashboardScreen(
                    viewModel = dashboardViewModel,
                    onImportConfig = { showImportScreen = true },
                    onShowSettings = { finish() }  // 返回到原来的界面
                )
            }
        }
    }
}
