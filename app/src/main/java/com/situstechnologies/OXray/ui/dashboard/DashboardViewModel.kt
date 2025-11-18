package com.situstechnologies.OXray.ui.dashboard

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.situstechnologies.OXray.R
import com.situstechnologies.OXray.bg.BoxService
import com.situstechnologies.OXray.bg.ServiceConnection
import com.situstechnologies.OXray.constant.Alert
import com.situstechnologies.OXray.constant.Status
import com.situstechnologies.OXray.data.routing.RoutingModeManager
import com.situstechnologies.OXray.data.storage.TestModeStorage
import com.situstechnologies.OXray.database.ProfileManager
import com.situstechnologies.OXray.database.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.StatusMessage
import com.situstechnologies.OXray.utils.CommandClient

/**
 * Dashboard ViewModel
 * Manages VPN connection state and profile selection
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val testModeStorage = TestModeStorage.getInstance(application)
    private val routingModeManager = RoutingModeManager(application)

    // VPN 服务连接
    private val serviceConnection = ServiceConnection(
        context = application,
        callback = object : ServiceConnection.Callback {
            override fun onServiceStatusChanged(status: Status) {
                Log.i(TAG, "🔥 VPN status changed: $status")
                _uiState.value = _uiState.value.copy(
                    connectionStatus = status.toConnectionStatus()
                )

                if (status == Status.Started) {
                    statusClient.connect()
                } else if (status == Status.Stopped) {
                    statusClient.disconnect()
                }
            }

            override fun onServiceAlert(type: Alert, message: String?) {
                Log.w(TAG, "🔥 Service alert: $type - $message")

                // 👇 使用字符串资源
                val alertMessage = when (type) {
                    Alert.RequestVPNPermission -> {
                        application.getString(R.string.service_error_missing_permission)
                    }
                    Alert.EmptyConfiguration -> {
                        application.getString(R.string.service_error_empty_configuration)
                    }
                    Alert.StartCommandServer -> {
                        application.getString(R.string.service_error_title_start_command_server) +
                                "\n" + message
                    }
                    Alert.CreateService -> {
                        application.getString(R.string.service_error_title_create_service) +
                                "\n" + message
                    }
                    Alert.StartService -> {
                        application.getString(R.string.service_error_title_start_service) +
                                "\n" + message
                    }
                    Alert.RequestNotificationPermission -> {
                        application.getString(R.string.notification_permission_required_description)
                    }
                    Alert.RequestLocationPermission -> {
                        application.getString(R.string.location_permission_description)
                    }
                    else -> message ?: "Unknown error"
                }

                _uiState.value = _uiState.value.copy(
                    showServiceAlert = true,
                    serviceAlertMessage = alertMessage
                )
            }
        }
    )

    private val statusClient = CommandClient(
        viewModelScope,
        CommandClient.ConnectionType.Status,
        object : CommandClient.Handler {
            override fun updateStatus(status: StatusMessage) {
                _uiState.value = _uiState.value.copy(
                    uploadSpeed = Libbox.formatBytes(status.uplink) + "/s",
                    downloadSpeed = Libbox.formatBytes(status.downlink) + "/s",
                    uploadTotal = Libbox.formatBytes(status.uplinkTotal),
                    downloadTotal = Libbox.formatBytes(status.downlinkTotal)
                )
            }
        }
    )

    companion object {
        private const val TAG = "DashboardViewModel"
    }

    init {
        Log.i(TAG, "DashboardViewModel initializing...")

        // 连接服务以监听状态
        serviceConnection.connect()
        Log.i(TAG, "ServiceConnection.connect() called")

        // 加载配置列表
        loadProfiles()

        // 加载已选中的配置
        loadSelectedProfile()

        // 注册 ProfileManager 回调
        ProfileManager.registerCallback {
            Log.i(TAG, "ProfileManager callback triggered, reloading profiles")
            loadProfiles()
        }

        // 启动连接监控
        startConnectionMonitoring()

        Log.i(TAG, "DashboardViewModel initialized")
    }

    /**
     * Load all profiles
     */
    fun loadProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbProfiles = ProfileManager.list()

                val uiProfiles = dbProfiles.map { dbProfile ->
                    Profile(
                        id = dbProfile.id,
                        name = dbProfile.name,
                        path = dbProfile.typed.path
                    )
                }

                _uiState.value = _uiState.value.copy(profiles = uiProfiles)

                Log.i(TAG, "Profiles loaded: ${uiProfiles.size} profiles")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load profiles", e)
            }
        }
    }

    /**
     * Load selected profile from Settings
     */
    private fun loadSelectedProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val selectedId = Settings.selectedProfile
                if (selectedId != -1L) {
                    val dbProfile = ProfileManager.get(selectedId)
                    if (dbProfile != null) {
                        val profile = Profile(
                            id = dbProfile.id,
                            name = dbProfile.name,
                            path = dbProfile.typed.path
                        )
                        _uiState.value = _uiState.value.copy(selectedProfile = profile)

                        // 加载路由模式
                        val mode = routingModeManager.getCurrentMode(profile.id)
                        _uiState.value = _uiState.value.copy(currentRoutingMode = mode)

                        Log.i(TAG, "Selected profile loaded: ${profile.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load selected profile", e)
            }
        }
    }

    /**
     * Select a profile
     */
    fun selectProfile(profile: Profile) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 保存选中的 Profile ID 到 Settings
                com.situstechnologies.OXray.database.Settings.selectedProfile = profile.id

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(selectedProfile = profile)
                }

                // Load routing mode
                val mode = routingModeManager.getCurrentMode(profile.id)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(currentRoutingMode = mode)
                }

                Log.i(TAG, "Profile selected: ${profile.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to select profile", e)
            }
        }
    }

    /**
     * Connect VPN (with permission check)
     */
    fun connect() {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = _uiState.value.selectedProfile ?: return@launch

            // 检查测试模式
            if (!checkTestModeBeforeConnect()) {
                return@launch
            }

            try {
                Log.i(TAG, "Starting VPN service...")

                // 重建服务模式（VPN/Proxy）
                val modeChanged = Settings.rebuildServiceMode()

                // 如果模式改变，重新连接 ServiceConnection
                if (modeChanged) {
                    Log.i(TAG, "Service mode changed, reconnecting...")
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        serviceConnection.reconnect()
                    }
                }

                // 👇 如果是 VPN 模式，发出权限检查信号
                if (Settings.serviceMode == com.situstechnologies.OXray.constant.ServiceMode.VPN) {
                    Log.i(TAG, "VPN mode detected, need to check permission")
                    _uiState.value = _uiState.value.copy(needsVpnPermissionCheck = true)
                    return@launch
                }

                // 如果不是 VPN 模式或已有权限，直接启动
                startVpnService()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start VPN", e)
                _uiState.value = _uiState.value.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED
                )
            }
        }
    }

    /**
     * 实际启动 VPN 服务（权限检查后调用）
     */
    fun startVpnService() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 启动服务
                BoxService.start()
                Log.i(TAG, "VPN service start requested")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start VPN service", e)
                _uiState.value = _uiState.value.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED
                )
            }
        }
    }

    /**
     * 重置权限检查标志
     */
    fun resetVpnPermissionCheck() {
        _uiState.value = _uiState.value.copy(needsVpnPermissionCheck = false)
    }

    /**
     * Disconnect VPN
     */
    fun disconnect() {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Stopping VPN service...")

                // 停止服务
                BoxService.stop()

                Log.i(TAG, "VPN service stop requested")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop VPN", e)
            }
        }
    }

    /**
     * Switch routing mode
     */
    fun switchRoutingMode(mode: RoutingModeManager.RoutingMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = _uiState.value.selectedProfile ?: return@launch

            try {
                // 如果已连接，先断开
                val wasConnected = _uiState.value.connectionStatus == ConnectionStatus.CONNECTED

                if (wasConnected) {
                    Log.i(TAG, "Stopping VPN before mode switch")
                    disconnect()
                    delay(1000) // 等待断开完成
                }

                // 切换模式
                routingModeManager.switchMode(profile.id, profile.path, mode)
                _uiState.value = _uiState.value.copy(currentRoutingMode = mode)

                // 如果之前已连接，自动重连
                if (wasConnected) {
                    Log.i(TAG, "Auto-reconnecting VPN")
                    delay(500)
                    connect()
                }

                Log.i(TAG, "Routing mode switched to: ${mode.value}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to switch routing mode", e)
            }
        }
    }

    /**
     * Delete a profile
     */
    fun deleteProfile(profile: Profile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 如果是当前选中的配置，先取消选中
                if (Settings.selectedProfile == profile.id) {
                    Settings.selectedProfile = -1L
                    _uiState.value = _uiState.value.copy(selectedProfile = null)
                }

                // 删除测试模式映射
                testModeStorage.deleteConfigMapping(profile.name)

                // 从数据库删除
                val dbProfile = ProfileManager.get(profile.id)
                if (dbProfile != null) {
                    ProfileManager.delete(dbProfile)
                }

                Log.i(TAG, "Profile deleted: ${profile.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete profile", e)
            }
        }
    }

    /**
     * Check test mode before connecting
     */
    private fun checkTestModeBeforeConnect(): Boolean {
        val profile = _uiState.value.selectedProfile ?: return false

        val testRecord = testModeStorage.getRecord(profile.name)

        if (testRecord != null) {
            Log.i(TAG, "🧪 Checking test account: ${profile.name}")

            if (testRecord.isExpired) {
                Log.i(TAG, "🧪 ❌ Test account expired")

                _uiState.value = _uiState.value.copy(
                    showTestExpiryAlert = true,
                    testExpiryMessage = "This test account has expired and will be removed."
                )

                testModeStorage.deleteConfigMapping(profile.name)
                return false
            } else {
                val remaining = testRecord.remainingMinutes
                Log.i(TAG, "🧪 ✅ Test account valid - $remaining minutes remaining")

                if (remaining < 10 && remaining > 0) {
                    _uiState.value = _uiState.value.copy(
                        showTestExpiryAlert = true,
                        testExpiryMessage = "This test account will expire in $remaining minutes."
                    )
                }

                _uiState.value = _uiState.value.copy(
                    testModeInfo = TestModeInfo(
                        remainingMinutes = remaining,
                        isExpired = false
                    )
                )

                return true
            }
        }

        Log.i(TAG, "ℹ️ Regular account, proceeding to connect")
        return true
    }

    /**
     * Start connection monitoring for test accounts
     */
    private fun startConnectionMonitoring() {
        viewModelScope.launch {
            while (true) {
                delay(30_000) // 每 30 秒检查一次

                if (_uiState.value.connectionStatus == ConnectionStatus.CONNECTED) {
                    checkTestAccountDuringConnection()
                }
            }
        }
    }

    /**
     * Check test account during connection
     */
    private fun checkTestAccountDuringConnection() {
        val profile = _uiState.value.selectedProfile ?: return

        val testRecord = testModeStorage.getRecord(profile.name)
        if (testRecord != null) {
            if (testRecord.isExpired) {
                Log.i(TAG, "🧪 ⚠️ Test account expired during connection! Disconnecting...")

                viewModelScope.launch {
                    disconnect()

                    testModeStorage.deleteConfigMapping(profile.name)

                    _uiState.value = _uiState.value.copy(
                        showTestExpiryAlert = true,
                        testExpiryMessage = "Your test account has expired. The connection has been stopped."
                    )

                    delay(2000)
                    deleteProfile(profile)
                }
            } else {
                val remaining = testRecord.remainingMinutes
                Log.i(TAG, "🧪 Test account check - $remaining minutes remaining")

                _uiState.value = _uiState.value.copy(
                    testModeInfo = TestModeInfo(
                        remainingMinutes = remaining,
                        isExpired = false
                    )
                )
            }
        }
    }

    fun dismissTestExpiryAlert() {
        _uiState.value = _uiState.value.copy(showTestExpiryAlert = false)
    }

    // 👇 新增
    fun dismissServiceAlert() {
        _uiState.value = _uiState.value.copy(showServiceAlert = false)
    }

    override fun onCleared() {
        super.onCleared()
        // 断开流量统计
        statusClient.disconnect()
        // 断开服务连接
        serviceConnection.disconnect()
        // 注销 ProfileManager 回调
        ProfileManager.unregisterCallback { loadProfiles() }
    }
}

/**
 * Status 到 ConnectionStatus 的映射
 */
private fun Status.toConnectionStatus(): ConnectionStatus {
    return when (this) {
        Status.Started -> ConnectionStatus.CONNECTED
        Status.Starting -> ConnectionStatus.CONNECTING
        Status.Stopping -> ConnectionStatus.DISCONNECTING
        Status.Stopped -> ConnectionStatus.DISCONNECTED
    }
}

/**
 * Dashboard UI State
 */
data class DashboardUiState(
    val profiles: List<Profile> = emptyList(),
    val selectedProfile: Profile? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val currentRoutingMode: RoutingModeManager.RoutingMode = RoutingModeManager.RoutingMode.SMART,
    val testModeInfo: TestModeInfo? = null,
    val showTestExpiryAlert: Boolean = false,
    val testExpiryMessage: String = "",

    val needsVpnPermissionCheck: Boolean = false,

    // 👇 新增：服务错误提示
    val showServiceAlert: Boolean = false,
    val serviceAlertMessage: String = "",

    // Connection stats
    val uploadSpeed: String = "0 KB/s",
    val downloadSpeed: String = "0 KB/s",
    val uploadTotal: String = "0 MB",
    val downloadTotal: String = "0 MB"
)