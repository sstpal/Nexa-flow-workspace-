package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.utils.AppReleaseInfo
import com.example.utils.GitHubUpdateManager
import com.example.utils.UpdateDownloadState
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun GitHubUpdateDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var repoSlug by remember { mutableStateOf(GitHubUpdateManager.getSavedRepo(context)) }
    var isEditingRepo by remember { mutableStateOf(false) }

    var updateState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }

    val currentVersion = GitHubUpdateManager.getAppVersion()

    // Auto-check on launch
    LaunchedEffect(Unit) {
        updateState = UpdateDownloadState.Checking
        val result = GitHubUpdateManager.checkLatestRelease(context, repoSlug)
        result.onSuccess { state ->
            updateState = state
        }.onFailure { err ->
            updateState = UpdateDownloadState.Error(err.message ?: "Failed to check updates", repoSlug)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
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
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column {
                            Text(
                                "App Auto-Updater",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Installed: $currentVersion",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }

                // Repository Source Selector
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Linked GitHub Repository", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(
                                onClick = { isEditingRepo = !isEditingRepo },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(if (isEditingRepo) "Done" else "Change", fontSize = 11.sp)
                            }
                        }

                        if (isEditingRepo) {
                            OutlinedTextField(
                                value = repoSlug,
                                onValueChange = { repoSlug = it },
                                label = { Text("owner/repository") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            val cleaned = GitHubUpdateManager.cleanRepoSlug(repoSlug)
                                            repoSlug = cleaned
                                            GitHubUpdateManager.saveRepo(context, cleaned)
                                            isEditingRepo = false
                                            // Re-check
                                            coroutineScope.launch {
                                                updateState = UpdateDownloadState.Checking
                                                val res = GitHubUpdateManager.checkLatestRelease(context, cleaned)
                                                res.onSuccess { state ->
                                                    updateState = state
                                                }.onFailure { e ->
                                                    updateState = UpdateDownloadState.Error(e.message ?: "Failed", cleaned)
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.Check, contentDescription = "Save")
                                    }
                                }
                            )
                        } else {
                            Text(
                                text = repoSlug,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Dynamic State View
                when (val state = updateState) {
                    is UpdateDownloadState.Checking -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                            Text("Checking GitHub for updates ($repoSlug)...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    is UpdateDownloadState.Available -> {
                        val release = state.release
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (state.isNewer) Color(0xFF10B981).copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        if (state.isNewer) "🎉 New Version Available: ${release.tagName}"
                                        else "✅ Up to Date: ${release.tagName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (state.isNewer) Color(0xFF047857) else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (release.apkSizeBytes > 0) {
                                        val mb = String.format("%.1f MB", release.apkSizeBytes / (1024.0 * 1024.0))
                                        Text("APK Size: $mb • ${release.apkFileName ?: "Direct APK"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            // Release Notes Box
                            Text("Changelog / Notes:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 130.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(10.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = release.body.ifBlank { "No release description provided." },
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            updateState = UpdateDownloadState.Checking
                                            val res = GitHubUpdateManager.checkLatestRelease(context, repoSlug)
                                            res.onSuccess { r -> updateState = r }
                                                .onFailure { e -> updateState = UpdateDownloadState.Error(e.message ?: "Failed", repoSlug) }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Check Again", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        val apkUrl = release.apkDownloadUrl
                                        if (apkUrl.isNullOrBlank()) {
                                            // Open release page if no APK attached
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "No direct APK asset in release. View on GitHub.", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                updateState = UpdateDownloadState.Downloading(0f, 0, release.apkSizeBytes)
                                                val res = GitHubUpdateManager.downloadApk(
                                                    context = context,
                                                    apkUrl = apkUrl,
                                                    fileName = release.apkFileName ?: "NexaFlow-${release.tagName}.apk",
                                                    onProgress = { p, d, t ->
                                                        updateState = UpdateDownloadState.Downloading(p, d, t)
                                                    }
                                                )
                                                res.onSuccess { file ->
                                                    downloadedFile = file
                                                    updateState = UpdateDownloadState.ReadyToInstall(file)
                                                    GitHubUpdateManager.installApk(context, file)
                                                }.onFailure { err ->
                                                    updateState = UpdateDownloadState.Error("Download failed: ${err.message}", repoSlug)
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1.3f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (state.isNewer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                    )
                                ) {
                                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (state.isNewer) "Update Now" else "Download APK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    is UpdateDownloadState.NoReleaseYet -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text(
                                    "Connected to '${state.repoSlug}'",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                "Repository successfully linked! No new Release with APK has been published on GitHub yet.",
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("How to push a new version:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("1. In GitHub repo (${state.repoSlug}), go to Releases", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("2. Click 'Draft a new release' with a tag (e.g. v1.1.0)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("3. Attach your exported APK and click Publish", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("4. The app will auto-detect and install it!", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            updateState = UpdateDownloadState.Checking
                                            val res = GitHubUpdateManager.checkLatestRelease(context, repoSlug)
                                            res.onSuccess { r -> updateState = r }
                                                .onFailure { e -> updateState = UpdateDownloadState.Error(e.message ?: "Failed", repoSlug) }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Re-check", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("${state.repoUrl}/releases"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Opening GitHub...", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1.3f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Outlined.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open GitHub Repo", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    is UpdateDownloadState.Downloading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Downloading APK...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                val pct = (state.progress * 100).toInt()
                                Text("$pct%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (state.totalBytes > 0) {
                                val dMb = String.format("%.1f", state.downloadedBytes / (1024.0 * 1024.0))
                                val tMb = String.format("%.1f MB", state.totalBytes / (1024.0 * 1024.0))
                                Text("$dMb / $tMb downloaded", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    is UpdateDownloadState.ReadyToInstall -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.1f))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(32.dp))
                            Text("APK Ready to Install!", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF047857))
                            Button(
                                onClick = {
                                    GitHubUpdateManager.installApk(context, state.apkFile)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.InstallMobile, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Install APK Now")
                            }
                        }
                    }

                    is UpdateDownloadState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Text("Update Check Error", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                            Text(state.message, fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${state.repoSlug}"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Opening GitHub...", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Open GitHub", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            updateState = UpdateDownloadState.Checking
                                            val res = GitHubUpdateManager.checkLatestRelease(context, repoSlug)
                                            res.onSuccess { r -> updateState = r }
                                                .onFailure { e -> updateState = UpdateDownloadState.Error(e.message ?: "Failed", repoSlug) }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Retry", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    is UpdateDownloadState.Idle -> {}
                }
            }
        }
    }
}
