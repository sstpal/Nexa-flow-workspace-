package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.Profile
import com.example.utils.BackupManager
import com.example.utils.FirebaseSyncManager
import com.example.utils.RestoreSummary
import com.example.utils.SessionManager
import kotlinx.coroutines.launch

@Composable
fun BackupRestoreDialog(
    profiles: List<Profile>,
    onDismiss: () -> Unit,
    onRestoreCompleted: (RestoreSummary) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val db = remember { AppDatabase.getDatabase(context) }

    var activeTab by remember { mutableIntStateOf(0) } // 0: Firebase 1-Tap Cloud Sync, 1: Drive / File Picker, 2: Raw JSON

    // Live Room DB stats
    val allCookies by db.sessionCookieDao().getAllCookiesFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val allBookmarks by db.bookmarkDao().getAllBookmarksFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var restoreSummary by remember { mutableStateOf<RestoreSummary?>(null) }

    // Firebase Auth State
    var currentUser by remember { mutableStateOf(FirebaseSyncManager.currentUser) }

    // JSON payload
    var generatedJson by remember { mutableStateOf("") }
    var importJsonText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            generatedJson = BackupManager.generateBackupJson(context)
        }
    }

    // Android Storage Access Framework (SAF) - Save to File
    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            statusMessage = "Saving backup file..."
            coroutineScope.launch {
                val res = BackupManager.exportJsonToUri(context, uri)
                res.onSuccess {
                    statusMessage = "Backup saved successfully (${profiles.size} profiles, ${allCookies.size} cookies)!"
                    Toast.makeText(context, "Backup File Saved Successfully!", Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    statusMessage = "Failed to save: ${err.message}"
                    Toast.makeText(context, "Save error: ${err.message}", Toast.LENGTH_SHORT).show()
                }
                isProcessing = false
            }
        }
    }

    // Android Storage Access Framework (SAF) - Restore from File
    val importDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            statusMessage = "Restoring profiles & session cookies from file..."
            coroutineScope.launch {
                val res = BackupManager.restoreFromJsonUri(context, uri)
                res.onSuccess { summary ->
                    restoreSummary = summary
                    statusMessage = "Restored ${summary.profilesRestored} profiles & ${summary.cookiesRestored} cookies. Auto-login active!"
                    Toast.makeText(context, "Restored & Auto-Login Active!", Toast.LENGTH_LONG).show()
                    onRestoreCompleted(summary)
                }.onFailure { err ->
                    statusMessage = "Failed to restore: ${err.message}"
                    Toast.makeText(context, "Restore error: ${err.message}", Toast.LENGTH_LONG).show()
                }
                isProcessing = false
            }
        }
    }

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
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                "Cloud Vault & Recovery",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Persistent Profiles & Auto-Login Sessions",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }

                // Stats Banner
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem(label = "Profiles", value = "${profiles.size}")
                        StatItem(label = "Sessions", value = "${allCookies.size}")
                        StatItem(label = "Bookmarks", value = "${allBookmarks.size}")
                        StatItem(label = "Cloud Sync", value = if (currentUser != null) "Connected" else "Offline", isAccent = currentUser != null)
                    }
                }

                // Tab Selector
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text("Firebase Cloud", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Outlined.FolderShared, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text("File Picker", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                    Tab(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text("Raw JSON", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                }

                // TAB 0: Firebase Firestore Cloud Sync (1-Click Auto Cloud)
                if (activeTab == 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Outlined.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Text(
                                            if (currentUser != null) (currentUser?.email ?: "Account Linked") else "Google Account Login",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Surface(
                                        color = if (currentUser != null) Color(0xFF10B981).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            if (currentUser != null) "Cloud Linked" else "Required",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (currentUser != null) Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (currentUser == null) {
                                    Text(
                                        "Sign in with Google to enable automatic background backup to your personal Firebase Cloud Vault. Data restores instantly on any device.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Button(
                                        onClick = {
                                            isProcessing = true
                                            statusMessage = "Signing in with Google..."
                                            coroutineScope.launch {
                                                val res = FirebaseSyncManager.signInWithGoogle(context)
                                                res.onSuccess { user ->
                                                    currentUser = user
                                                    statusMessage = "Signed in as ${user.email}. Ready to sync!"
                                                    Toast.makeText(context, "Signed in: ${user.email}", Toast.LENGTH_SHORT).show()
                                                }.onFailure { err ->
                                                    statusMessage = "Sign-in error: ${err.message}"
                                                    Toast.makeText(context, "Sign-in failed: ${err.message}", Toast.LENGTH_SHORT).show()
                                                }
                                                isProcessing = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        enabled = !isProcessing
                                    ) {
                                        Icon(Icons.Outlined.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Sign in with Google", fontWeight = FontWeight.SemiBold)
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("UID: ${currentUser?.uid?.take(10)}...", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        TextButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    FirebaseSyncManager.signOut(context)
                                                    currentUser = null
                                                    statusMessage = "Signed out"
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Sign Out", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }

                        // Firebase Cloud Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    isProcessing = true
                                    statusMessage = "Syncing profiles & session cookies to Firebase Cloud..."
                                    coroutineScope.launch {
                                        val res = FirebaseSyncManager.syncToCloud(context)
                                        res.onSuccess { summary ->
                                            statusMessage = "Saved to Cloud Vault (${summary.profilesCount} profiles, ${summary.cookiesCount} sessions)!"
                                            Toast.makeText(context, "Cloud Backup Saved!", Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            statusMessage = "Cloud Sync Error: ${err.message}"
                                            Toast.makeText(context, "Sync Error: ${err.message}", Toast.LENGTH_SHORT).show()
                                        }
                                        isProcessing = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isProcessing && currentUser != null
                            ) {
                                Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Backup to Cloud", fontSize = 12.sp)
                            }

                            FilledTonalButton(
                                onClick = {
                                    isProcessing = true
                                    statusMessage = "Restoring profiles & cookies from Firebase Cloud..."
                                    coroutineScope.launch {
                                        val res = FirebaseSyncManager.restoreFromCloud(context)
                                        res.onSuccess { summary ->
                                            restoreSummary = summary
                                            statusMessage = "Restored ${summary.profilesRestored} profiles & ${summary.cookiesRestored} sessions from Cloud!"
                                            Toast.makeText(context, "Restored & Auto-Login Active!", Toast.LENGTH_LONG).show()
                                            onRestoreCompleted(summary)
                                        }.onFailure { err ->
                                            statusMessage = "Restore error: ${err.message}"
                                            Toast.makeText(context, "Restore error: ${err.message}", Toast.LENGTH_SHORT).show()
                                        }
                                        isProcessing = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isProcessing && currentUser != null
                            ) {
                                Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore Cloud", fontSize = 12.sp)
                            }
                        }

                        // Guide Box
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Automatic Cloud Recovery Workflow:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("1. Login to Instagram or any sites in your workspaces.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("2. Tap 'Backup to Cloud' (saves automatically under your Google ID).", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("3. Clear Data / Change Phone -> Sign in with Google -> Tap 'Restore Cloud'.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("4. All profiles & login sessions restore automatically without passwords!", fontSize = 10.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                // TAB 1: Storage Access Framework / File Picker
                if (activeTab == 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Save as Offline JSON File", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    "Export your complete backup to Google Drive or Phone Storage using Android's file manager.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val timestamp = System.currentTimeMillis()
                                exportDocumentLauncher.launch("nexaflow_backup_$timestamp.json")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isProcessing
                        ) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Backup File (.json)", fontWeight = FontWeight.SemiBold)
                        }

                        FilledTonalButton(
                            onClick = {
                                importDocumentLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isProcessing
                        ) {
                            Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restore from File (.json)", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // TAB 2: Copy / Paste Raw JSON
                if (activeTab == 2) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Export & Import Raw JSON",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )

                        OutlinedTextField(
                            value = generatedJson,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Live Backup JSON") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(generatedJson))
                                    Toast.makeText(context, "Copied backup JSON to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy JSON", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        generatedJson = BackupManager.generateBackupJson(context)
                                        Toast.makeText(context, "JSON refreshed", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Refresh", fontSize = 12.sp)
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 4.dp))

                        OutlinedTextField(
                            value = importJsonText,
                            onValueChange = { importJsonText = it },
                            label = { Text("Paste JSON to Restore") },
                            placeholder = { Text("Paste exported JSON payload here...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                if (importJsonText.isBlank()) {
                                    Toast.makeText(context, "Please paste JSON content first", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isProcessing = true
                                coroutineScope.launch {
                                    val res = BackupManager.restoreFromJson(context, importJsonText, clearExisting = false)
                                    res.onSuccess { summary ->
                                        restoreSummary = summary
                                        statusMessage = "Restored ${summary.profilesRestored} profiles, ${summary.cookiesRestored} cookies!"
                                        Toast.makeText(context, "Restored Successfully!", Toast.LENGTH_LONG).show()
                                        onRestoreCompleted(summary)
                                    }.onFailure { err ->
                                        statusMessage = "Error restoring JSON: ${err.message}"
                                        Toast.makeText(context, "Invalid JSON: ${err.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    isProcessing = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = importJsonText.isNotBlank() && !isProcessing
                        ) {
                            Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restore from Pasted JSON")
                        }
                    }
                }

                // Status Message Box
                if (statusMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (statusMessage?.contains("error", ignoreCase = true) == true || statusMessage?.contains("failed", ignoreCase = true) == true)
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = statusMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            color = if (statusMessage?.contains("error", ignoreCase = true) == true || statusMessage?.contains("failed", ignoreCase = true) == true)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, isAccent: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = if (isAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
