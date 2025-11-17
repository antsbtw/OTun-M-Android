package com.situstechnologies.OXray.ui.dashboard

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.situstechnologies.OXray.data.routing.RoutingModeManager
import com.situstechnologies.OXray.data.storage.TestModeStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Dashboard ViewModel
 * Manages VPN connection state and profile selection
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val testModeStorage = TestModeStorage.getInstance(application)
    private val routingModeManager = RoutingModeManager(application)

    companion object {
        private const val TAG = "DashboardViewModel"
    }

    init {
        loadProfiles()
        startConnectionMonitoring()
    }

    /**
     * Load all profiles
     */
    fun loadProfiles() {
        viewModelScope.launch {
            try {
                // TODO: Load profiles from database
                // val profiles = ProfileManager.list()
                // _uiState.value = _uiState.value.copy(profiles = profiles)

                // Load selected profile
                // val selectedId = SharedPreferences.getSelectedProfileId()
                // val selected = profiles.firstOrNull { it.id == selectedId }
                // _uiState.value = _uiState.value.copy(selectedProfile = selected)

                Log.i(TAG, "Profiles loaded")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load profiles", e)
            }
        }
    }

    /**
     * Select a profile
     */
    fun selectProfile(profile: Profile) {
        viewModelScope.launch {
            try {
                // TODO: Save selected profile ID
                // SharedPreferences.setSelectedProfileId(profile.id)

                _uiState.value = _uiState.value.copy(selectedProfile = profile)

                // Load routing mode
                val mode = routingModeManager.getCurrentMode(profile.id)
                _uiState.value = _uiState.value.copy(currentRoutingMode = mode)

                Log.i(TAG, "Profile selected: ${profile.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to select profile", e)
            }
        }
    }

    /**
     * Connect VPN
     */
    fun connect() {
        viewModelScope.launch {
            val profile = _uiState.value.selectedProfile ?: return@launch

            // Check test mode before connecting
            if (!checkTestModeBeforeConnect()) {
                return@launch
            }

            try {
                _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.CONNECTING)

                // TODO: Start VPN service
                // VpnService.start(profile)

                delay(1000) // Simulate connection
                _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.CONNECTED)

                Log.i(TAG, "VPN connected")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect VPN", e)
                _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.DISCONNECTED)
            }
        }
    }

    /**
     * Disconnect VPN
     */
    fun disconnect() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.DISCONNECTING)

                // TODO: Stop VPN service
                // VpnService.stop()

                delay(500) // Simulate disconnection
                _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.DISCONNECTED)

                Log.i(TAG, "VPN disconnected")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to disconnect VPN", e)
            }
        }
    }

    /**
     * Switch routing mode
     */
    fun switchRoutingMode(mode: RoutingModeManager.RoutingMode) {
        viewModelScope.launch {
            val profile = _uiState.value.selectedProfile ?: return@launch

            try {
                // If VPN is connected, disconnect first
                val wasConnected = _uiState.value.connectionStatus == ConnectionStatus.CONNECTED

                if (wasConnected) {
                    Log.i(TAG, "Stopping VPN before mode switch")
                    disconnect()
                    delay(500)
                }

                // Switch mode
                routingModeManager.switchMode(profile.id, profile.path, mode)

                _uiState.value = _uiState.value.copy(currentRoutingMode = mode)

                // Auto-reconnect if was connected
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
        viewModelScope.launch {
            try {
                // Delete test mode mapping
                testModeStorage.deleteConfigMapping(profile.name)

                // TODO: Delete from database
                // ProfileManager.delete(profile.id)

                // Reload profiles
                loadProfiles()

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

                // Update test mode info in UI
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
                delay(30_000) // Check every 30 seconds

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
                        testExpiryMessage = "Your test account has expired. The connection has been stopped and the configuration will be removed."
                    )

                    delay(2000)
                    deleteProfile(profile)
                }
            } else {
                val remaining = testRecord.remainingMinutes
                Log.i(TAG, "🧪 Test account check - $remaining minutes remaining")

                // Update test mode info
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

    // Connection stats
    val uploadSpeed: String = "0 KB/s",
    val downloadSpeed: String = "0 KB/s",
    val uploadTotal: String = "0 MB",
    val downloadTotal: String = "0 MB"
)
