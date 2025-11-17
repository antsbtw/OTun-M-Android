package com.situstechnologies.OXray.ui.Oimport

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Import Configuration Screen
 *
 * Ported from iOS ImportConfigView.swift
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportConfigScreen(
    viewModel: ImportViewModel,
    onDismiss: () -> Unit,
    onShowScanner: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Configuration") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header Section
            HeaderSection()

            // Scan QR Code Button
            Button(
                onClick = onShowScanner,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan QR Code", fontWeight = FontWeight.SemiBold)
            }

            // OR Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(Modifier.weight(1f))
                Text(
                    "OR",
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                HorizontalDivider(Modifier.weight(1f))
            }

            // Manual Input Section
            ManualInputSection(
                importURL = uiState.importURL,
                onURLChanged = { viewModel.updateImportURL(it) },
                isValidURL = uiState.isValidURL
            )

            // Password Section (shown after URL detected)
            if (uiState.showPasswordField) {
                PasswordSection(
                    password = uiState.password,
                    onPasswordChanged = { viewModel.updatePassword(it) }
                )
            }

            // Import Button
            if (uiState.showPasswordField) {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.performImport()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isImporting &&
                            uiState.password.isNotEmpty() &&
                            uiState.importURL.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    if (uiState.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import Configuration", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Status Section
            if (uiState.isImporting || uiState.importStatus.isNotEmpty()) {
                StatusSection(
                    status = uiState.importStatus,
                    isError = uiState.lastError != null,
                    isImporting = uiState.isImporting
                )
            }
        }
    }

    // Success Alert
    if (uiState.showSuccessAlert) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Import Successful") },
            text = { Text("Configuration '${uiState.importedProfileName}' has been imported successfully.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissSuccessAlert()
                    onDismiss()
                }) {
                    Text("OK")
                }
            }
        )
    }

    // Error Alert
    if (uiState.showErrorAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissErrorAlert() },
            title = { Text("Import Failed") },
            text = { Text(uiState.lastError?.message ?: "Unknown error") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissErrorAlert() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun HeaderSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.QrCode,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            "Import Configuration",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            "Scan a QR code or paste an import link to add a new configuration",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualInputSection(
    importURL: String,
    onURLChanged: (String) -> Unit,
    isValidURL: Boolean?
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Manual Import",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Paste import link here",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        OutlinedTextField(
            value = importURL,
            onValueChange = onURLChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("oxray://import?encrypted=...") },
            singleLine = false,
            maxLines = 3,
            isError = isValidURL == false && importURL.isNotEmpty()
        )

        if (isValidURL == false && importURL.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "Invalid import URL format",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF9800)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordSection(
    password: String,
    onPasswordChanged: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Decryption Password",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Enter the password provided by the configuration sender",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
        }
    }
}

@Composable
fun StatusSection(
    status: String,
    isError: Boolean,
    isImporting: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isImporting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }

            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) Color.Red else Color.Gray
            )
        }
    }
}
