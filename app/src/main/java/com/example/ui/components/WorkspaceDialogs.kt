package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Workspace

val WorkspaceColors = listOf(
    "#6750A4", // Purple
    "#2563EB", // Blue
    "#059669", // Emerald Green
    "#D97706", // Amber
    "#DC2626", // Crimson
    "#7C3AED", // Indigo
    "#0891B2", // Cyan
    "#DB2777"  // Pink
)

val WorkspaceIcons = listOf(
    "work" to Icons.Outlined.Workspaces,
    "ai" to Icons.Outlined.AutoAwesome,
    "group" to Icons.Outlined.Groups,
    "folder" to Icons.Outlined.Folder,
    "cloud" to Icons.Outlined.CloudQueue,
    "lock" to Icons.Outlined.Lock,
    "code" to Icons.Outlined.Code,
    "star" to Icons.Outlined.StarOutline
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditWorkspaceDialog(
    initialWorkspace: Workspace? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, colorHex: String) -> Unit
) {
    var name by remember { mutableStateOf(initialWorkspace?.name ?: "") }
    var description by remember { mutableStateOf(initialWorkspace?.description ?: "") }
    var selectedColor by remember { mutableStateOf(initialWorkspace?.colorHex ?: WorkspaceColors[0]) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val parsedColor = try {
                            Color(android.graphics.Color.parseColor(selectedColor))
                        } catch (e: Exception) {
                            MaterialTheme.colorScheme.primary
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(parsedColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Workspaces,
                                contentDescription = null,
                                tint = parsedColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = if (initialWorkspace == null) "New Workspace" else "Edit Workspace",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Workspace Name") },
                    placeholder = { Text("e.g. AI Video Generators, Social, Work") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Label, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                // Description Input
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("e.g. Accounts for Sora, Runway & Kling") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Notes, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                // Color Selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Theme Color",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(WorkspaceColors) { hex ->
                            val color = try {
                                Color(android.graphics.Color.parseColor(hex))
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.primary
                            }
                            val isSelected = selectedColor.equals(hex, ignoreCase = true)

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColor = hex }
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(name.trim(), description.trim(), selectedColor)
                            }
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (initialWorkspace == null) "Create Workspace" else "Save Changes")
                    }
                }
            }
        }
    }
}

@Composable
fun WorkspaceManagementSheet(
    workspaces: List<Workspace>,
    selectedWorkspaceName: String?,
    onSelectWorkspace: (String?) -> Unit,
    onAddWorkspace: () -> Unit,
    onEditWorkspace: (Workspace) -> Unit,
    onDeleteWorkspace: (Workspace) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Workspaces, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                        }
                        Text("Custom Workspaces", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }

                Text(
                    "Group multiple accounts of the same website or toolset into custom spaces for clean organization.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "All Profiles" item
                    item {
                        val isAllSelected = selectedWorkspaceName == null
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectWorkspace(null)
                                    onDismiss()
                                },
                            color = if (isAllSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Outlined.GridView, contentDescription = null, tint = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("All Workspaces", fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
                                }
                                if (isAllSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    items(workspaces) { ws ->
                        val isSelected = selectedWorkspaceName == ws.name
                        val color = try {
                            Color(android.graphics.Color.parseColor(ws.colorHex))
                        } catch (e: Exception) {
                            MaterialTheme.colorScheme.primary
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectWorkspace(ws.name)
                                    onDismiss()
                                },
                            color = if (isSelected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                            border = if (isSelected) BorderStroke(1.5.dp, color) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Column {
                                        Text(ws.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        if (ws.description.isNotEmpty()) {
                                            Text(ws.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onEditWorkspace(ws) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Outlined.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { onDeleteWorkspace(ws) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onAddWorkspace,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add New Workspace")
                }
            }
        }
    }
}
