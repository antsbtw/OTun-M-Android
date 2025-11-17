package com.situstechnologies.OXray.ui.Oimport

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.situstechnologies.OXray.data.Oimport.ImportHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Import ViewModel
 * Manages configuration import process
 */
class ImportViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val importHandler = ImportHandler(application)

    companion object {
        private const val TAG = "ImportViewModel"
    }

    /**
     * Update import URL
     */
    fun updateImportURL(url: String) {
        _uiState.value = _uiState.value.copy(importURL = url)

        // Validate URL
        if (url.isNotEmpty()) {
            val isValid = ImportHandler.isValidImportURL(url)
            _uiState.value = _uiState.value.copy(isValidURL = isValid)

            if (isValid) {
                // Parse URL to get payload
                when (val result = importHandler.parseImportURL(url)) {
                    is ImportHandler.ImportResult.RequiresPassword -> {
                        _uiState.value = _uiState.value.copy(
                            showPasswordField = true,
                            pendingPayload = result.payload
                        )
                        Log.i(TAG, "Valid encrypted payload detected, awaiting password")
                    }
                    is ImportHandler.ImportResult.Failure -> {
                        _uiState.value = _uiState.value.copy(
                            showPasswordField = false,
                            lastError = result.error
                        )
                        Log.e(TAG, "URL parsing failed: ${result.error.message}")
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(showPasswordField = false)
                    }
                }
            } else {
                _uiState.value = _uiState.value.copy(showPasswordField = false)
            }
        } else {
            _uiState.value = _uiState.value.copy(
                isValidURL = null,
                showPasswordField = false
            )
        }
    }

    /**
     * Update password
     */
    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    /**
     * Perform import
     */
    suspend fun performImport() {
        val payload = _uiState.value.pendingPayload ?: return
        val password = _uiState.value.password

        if (password.isEmpty()) return

        Log.i(TAG, "Starting import process")

        _uiState.value = _uiState.value.copy(
            isImporting = true,
            importStatus = "Decrypting configuration..."
        )

        viewModelScope.launch {
            when (val result = importHandler.importConfiguration(
                payload = payload,
                password = password,
                onProfileCreated = { name, path ->
                    // TODO: Create Profile in database
                    // ProfileManager.create(Profile(name = name, path = path))
                    Log.i(TAG, "Profile created: $name at $path")
                    1L // Return fake profile ID
                }
            )) {
                is ImportHandler.ImportResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        importStatus = "Successfully imported: ${result.profileName}",
                        importedProfileName = result.profileName,
                        showSuccessAlert = true
                    )
                    Log.i(TAG, "Import successful: ${result.profileName}")
                }

                is ImportHandler.ImportResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        importStatus = "Import failed: ${result.error.message}",
                        lastError = result.error,
                        showErrorAlert = true
                    )
                    Log.e(TAG, "Import failed: ${result.error.message}")
                }

                else -> {
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        importStatus = "Unexpected state"
                    )
                }
            }
        }
    }

    /**
     * Handle scanned QR code
     */
    fun handleScannedQR(qrString: String) {
        Log.i(TAG, "QR code scanned")
        updateImportURL(qrString)
    }

    fun dismissSuccessAlert() {
        _uiState.value = _uiState.value.copy(showSuccessAlert = false)
    }

    fun dismissErrorAlert() {
        _uiState.value = _uiState.value.copy(showErrorAlert = false)
    }
}

/**
 * Import UI State
 */
data class ImportUiState(
    val importURL: String = "",
    val password: String = "",
    val isValidURL: Boolean? = null,
    val showPasswordField: Boolean = false,
    val isImporting: Boolean = false,
    val importStatus: String = "",
    val lastError: ImportHandler.ImportError? = null,
    val pendingPayload: com.situstechnologies.OXray.data.crypto.EncryptedSharePayload? = null,
    val importedProfileName: String = "",
    val showSuccessAlert: Boolean = false,
    val showErrorAlert: Boolean = false
)
