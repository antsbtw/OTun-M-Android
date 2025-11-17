package com.situstechnologies.OXray.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.situstechnologies.OXray.data.routing.RoutingModeManager.RoutingMode

/**
 * Configuration List Component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationList(
    profiles: List<Profile>,
    selectedProfile: Profile?,
    onProfileSelected: (Profile) -> Unit,
    onProfileDeleted: (Profile) -> Unit
) {
    Column {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Configurations",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "${profiles.size}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Profile List
        if (profiles.isEmpty()) {
            EmptyListView()
        } else {
            profiles.forEach { profile ->
                var showDeleteDialog by remember { mutableStateOf(false) }

                ConfigRow(
                    profile = profile,
                    isSelected = profile.id == selectedProfile?.id,
                    onClick = { onProfileSelected(profile) },
                    onDeleteClick = { showDeleteDialog = true }
                )

                // Delete Confirmation Dialog
                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text("Delete Configuration") },
                        text = { Text("Are you sure you want to delete \"${profile.name}\"?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    onProfileDeleted(profile)
                                    showDeleteDialog = false
                                }
                            ) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Configuration Row Item
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigRow(
    profile: Profile,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Circle,
                    contentDescription = null,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Gray
                    }
                )

                Column {
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Text(
                        "Local",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isSelected) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "Active",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }

                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Empty List View
 */
@Composable
fun EmptyListView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = Color.Gray
        )

        Text(
            "No Configurations",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Import a configuration to get started",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

/**
 * Routing Mode Menu
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingModeMenu(
    currentMode: RoutingMode,
    onModeSelected: (RoutingMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = when (currentMode) {
                    RoutingMode.SMART -> Icons.Default.AccountTree
                    RoutingMode.GLOBAL -> Icons.Default.Language
                },
                contentDescription = "Routing Mode",
                tint = when (currentMode) {
                    RoutingMode.SMART -> MaterialTheme.colorScheme.primary
                    RoutingMode.GLOBAL -> Color(0xFFFF9800)
                }
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Smart Routing Option
            DropdownMenuItem(
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentMode == RoutingMode.SMART) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                        Column {
                            Text(
                                "Smart Routing",
                                fontWeight = if (currentMode == RoutingMode.SMART) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                }
                            )
                            Text(
                                "Automatically split traffic",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                },
                onClick = {
                    if (currentMode != RoutingMode.SMART) {
                        onModeSelected(RoutingMode.SMART)
                    }
                    expanded = false
                }
            )

            Divider()

            // Global Proxy Option
            DropdownMenuItem(
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentMode == RoutingMode.GLOBAL) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                        Column {
                            Text(
                                "Global Proxy",
                                fontWeight = if (currentMode == RoutingMode.GLOBAL) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                }
                            )
                            Text(
                                "All traffic through proxy",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                },
                onClick = {
                    if (currentMode != RoutingMode.GLOBAL) {
                        onModeSelected(RoutingMode.GLOBAL)
                    }
                    expanded = false
                }
            )
        }
    }
}
