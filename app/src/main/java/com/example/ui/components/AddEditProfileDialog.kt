package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.data.Profile
import com.example.data.Workspace

val PresetColors = listOf(
    "#6750A4", // Purple (Default)
    "#2563EB", // Blue
    "#059669", // Emerald
    "#DC2626", // Rose
    "#D97706", // Amber
    "#7C3AED", // Violet
    "#0891B2"  // Cyan
)

val QuickSites = listOf(
    "Google Flow" to "https://flow.google",
    "Google Labs" to "https://labs.google",
    "AI Studio" to "https://aistudio.google.com",
    "ChatGPT" to "https://chatgpt.com",
    "Midjourney" to "https://www.midjourney.com",
    "Claude" to "https://claude.ai",
    "GitHub" to "https://github.com",
    "YouTube" to "https://www.youtube.com"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProfileDialog(
    initialProfile: Profile? = null,
    workspaces: List<Workspace> = emptyList(),
    defaultWorkspaceName: String = "General",
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, workspaceName: String, isDesktop: Boolean, isBackground: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialProfile?.name ?: "") }
    var url by remember { mutableStateOf(initialProfile?.url ?: "https://www.google.com") }
    var workspaceName by remember { mutableStateOf(initialProfile?.workspaceName ?: defaultWorkspaceName) }
    var isDesktopMode by remember { mutableStateOf(initialProfile?.isDesktopMode ?: false) }
    var isBackground by remember { mutableStateOf(initialProfile?.isBackground ?: false) }

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
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (initialProfile == null) Icons.Outlined.PersonAdd else Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = if (initialProfile == null) "New Workspace Profile" else "Edit Profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Profile Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile / Account Name") },
                    placeholder = { Text("e.g. Sora Pro 1, Midjourney Art, Personal") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                // Workspace Category Assignment
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Workspace Category", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(workspaceName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    val allWorkspaceNames = (listOf("General") + workspaces.map { it.name }).distinct()
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(allWorkspaceNames) { ws ->
                            val isSelected = workspaceName.equals(ws, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { workspaceName = ws },
                                label = { Text(ws, fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp),
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }
                }

                // Start URL
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Startup Web URL") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Public, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                // Quick presets
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Quick Presets",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(QuickSites) { (siteName, siteUrl) ->
                            SuggestionChip(
                                onClick = { url = siteUrl },
                                label = { Text(siteName, fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                // Modes & Settings
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Desktop Mode Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.DesktopWindows, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("Desktop Mode", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Renders desktop layouts & tools", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = isDesktopMode,
                                onCheckedChange = { isDesktopMode = it }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Background Task Execution Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("Background Task Execution", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Keeps AI generation & tasks running when switching profiles", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = isBackground,
                                onCheckedChange = { isBackground = it }
                            )
                        }
                    }
                }

                // Buttons
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
                            val finalName = if (name.trim().isEmpty()) "Profile" else name.trim()
                            val finalUrl = if (url.trim().isEmpty()) "https://www.google.com" else url.trim()
                            val finalWs = if (workspaceName.trim().isEmpty()) "General" else workspaceName.trim()
                            onSave(finalName, finalUrl, finalWs, isDesktopMode, isBackground)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (initialProfile == null) "Create Profile" else "Save Changes")
                    }
                }
            }
        }
    }
}
