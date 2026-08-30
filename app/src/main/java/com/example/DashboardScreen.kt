package com.example

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DownloadItem
import com.example.data.Profile
import com.example.data.Workspace
import com.example.ui.components.*
import com.example.ui.theme.AppThemeMode
import com.example.utils.BackgroundSessionManager
import com.example.utils.DownloadHelper
import com.example.utils.FirebaseSyncManager
import com.example.utils.ThemePreferences


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    profiles: List<Profile>,
    workspaces: List<Workspace>,
    downloads: List<DownloadItem>,
    onOpenBrowser: (Profile) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onCreateProfile: (name: String, url: String, workspaceName: String, isDesktop: Boolean, isBg: Boolean) -> Unit,
    onUpdateProfile: (Profile) -> Unit,
    onDeleteProfile: (Profile) -> Unit,
    onCreateWorkspace: (name: String, desc: String, colorHex: String) -> Unit,
    onUpdateWorkspace: (Workspace) -> Unit,
    onDeleteWorkspace: (Workspace) -> Unit,
    onDeleteDownload: (DownloadItem) -> Unit,
    onClearDownloads: () -> Unit,
    onRestoreList: (List<Profile>) -> Unit,
    onToggleDesktop: (Profile, Boolean) -> Unit,
    onToggleBackground: (Profile, Boolean) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Home, 1: Profiles, 2: Downloads/Vault, 3: Settings
    var selectedWorkspaceFilter by remember { mutableStateOf<String?>(null) } // null = All

    val filteredProfiles = remember(profiles, selectedWorkspaceFilter) {
        if (selectedWorkspaceFilter == null) profiles
        else profiles.filter { it.workspaceName.equals(selectedWorkspaceFilter, ignoreCase = true) }
    }

    var selectedProfileId by remember(filteredProfiles) {
        mutableStateOf(filteredProfiles.firstOrNull()?.id ?: profiles.firstOrNull()?.id)
    }
    val activeProfile = profiles.find { it.id == selectedProfileId } ?: filteredProfiles.firstOrNull() ?: profiles.firstOrNull()

    // Dialog state
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var showAddWorkspaceDialog by remember { mutableStateOf(false) }
    var editingWorkspace by remember { mutableStateOf<Workspace?>(null) }
    var showWorkspaceSheet by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showDownloadsDialog by remember { mutableStateOf(false) }

    if (showUpdateDialog) {
        GitHubUpdateDialog(onDismiss = { showUpdateDialog = false })
    }

    if (showDownloadsDialog) {
        DownloadsDialog(
            downloads = downloads,
            onDismiss = { showDownloadsDialog = false },
            onDelete = onDeleteDownload,
            onClearAll = onClearDownloads
        )
    }

    if (showWorkspaceSheet) {
        WorkspaceManagementSheet(
            workspaces = workspaces,
            selectedWorkspaceName = selectedWorkspaceFilter,
            onSelectWorkspace = { selectedWorkspaceFilter = it },
            onAddWorkspace = {
                showWorkspaceSheet = false
                showAddWorkspaceDialog = true
            },
            onEditWorkspace = { ws ->
                showWorkspaceSheet = false
                editingWorkspace = ws
            },
            onDeleteWorkspace = { ws ->
                onDeleteWorkspace(ws)
                Toast.makeText(context, "Workspace '${ws.name}' deleted", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showWorkspaceSheet = false }
        )
    }

    if (showAddWorkspaceDialog) {
        AddEditWorkspaceDialog(
            initialWorkspace = null,
            onDismiss = { showAddWorkspaceDialog = false },
            onSave = { name, desc, color ->
                onCreateWorkspace(name, desc, color)
                showAddWorkspaceDialog = false
                Toast.makeText(context, "Workspace '$name' created", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (editingWorkspace != null) {
        AddEditWorkspaceDialog(
            initialWorkspace = editingWorkspace,
            onDismiss = { editingWorkspace = null },
            onSave = { name, desc, color ->
                editingWorkspace?.let {
                    onUpdateWorkspace(it.copy(name = name, description = desc, colorHex = color))
                }
                editingWorkspace = null
                Toast.makeText(context, "Workspace updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAddProfileDialog) {
        AddEditProfileDialog(
            initialProfile = null,
            workspaces = workspaces,
            defaultWorkspaceName = selectedWorkspaceFilter ?: "General",
            onDismiss = { showAddProfileDialog = false },
            onSave = { name, url, wsName, isDesktop, isBg ->
                onCreateProfile(name, url, wsName, isDesktop, isBg)
                showAddProfileDialog = false
                Toast.makeText(context, "Profile '$name' created in '$wsName'", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (editingProfile != null) {
        AddEditProfileDialog(
            initialProfile = editingProfile,
            workspaces = workspaces,
            defaultWorkspaceName = editingProfile?.workspaceName ?: "General",
            onDismiss = { editingProfile = null },
            onSave = { name, url, wsName, isDesktop, isBg ->
                editingProfile?.let {
                    onUpdateProfile(it.copy(name = name, url = url, workspaceName = wsName, isDesktopMode = isDesktop, isBackground = isBg))
                }
                editingProfile = null
                Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showBackupDialog) {
        BackupRestoreDialog(
            profiles = profiles,
            onDismiss = { showBackupDialog = false },
            onRestoreCompleted = { summary ->
                Toast.makeText(
                    context,
                    "Restored ${summary.profilesRestored} profiles & ${summary.cookiesRestored} session cookies!",
                    Toast.LENGTH_LONG
                ).show()
                showBackupDialog = false
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DashboardTopBar(
                profileCount = profiles.size,
                downloadsCount = downloads.size,
                onSyncClick = { showBackupDialog = true },
                onAddClick = { showAddProfileDialog = true },
                onUpdateClick = { showUpdateDialog = true },
                onDownloadsClick = { showDownloadsDialog = true },
                onWorkspacesClick = { showWorkspaceSheet = true }
            )
        },
        bottomBar = {
            DashboardBottomBar(
                selectedTab = selectedTab,
                downloadsCount = downloads.size,
                onTabSelected = { selectedTab = it }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0 || selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showAddProfileDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Profile")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> HomeDashboardTab(
                    profiles = profiles,
                    workspaces = workspaces,
                    selectedWorkspaceFilter = selectedWorkspaceFilter,
                    filteredProfiles = filteredProfiles,
                    activeProfile = activeProfile,
                    selectedProfileId = selectedProfileId,
                    onSelectWorkspace = { selectedWorkspaceFilter = it },
                    onManageWorkspaces = { showWorkspaceSheet = true },
                    onSelectProfile = { selectedProfileId = it },
                    onOpenBrowser = onOpenBrowser,
                    onToggleDesktop = onToggleDesktop,
                    onToggleBackground = onToggleBackground,
                    onEditProfile = { editingProfile = it },
                    onOpenBackup = { showBackupDialog = true },
                    onOpenDownloads = { showDownloadsDialog = true },
                    onAddNew = { showAddProfileDialog = true }
                )
                1 -> ProfilesManagerTab(
                    profiles = profiles,
                    workspaces = workspaces,
                    selectedWorkspaceFilter = selectedWorkspaceFilter,
                    onSelectWorkspace = { selectedWorkspaceFilter = it },
                    onManageWorkspaces = { showWorkspaceSheet = true },
                    onOpenBrowser = onOpenBrowser,
                    onEditProfile = { editingProfile = it },
                    onDeleteProfile = onDeleteProfile,
                    onToggleDesktop = onToggleDesktop,
                    onToggleBackground = onToggleBackground,
                    onAddNew = { showAddProfileDialog = true }
                )
                2 -> VaultAndDownloadsTab(
                    profiles = profiles,
                    downloads = downloads,
                    onOpenBackupDialog = { showBackupDialog = true },
                    onOpenDownloadsDialog = { showDownloadsDialog = true },
                    onDeleteDownload = onDeleteDownload,
                    onClearDownloads = onClearDownloads
                )
                3 -> EngineSettingsTab(
                    profiles = profiles,
                    onOpenBrowser = onOpenBrowser,
                    onOpenBackupDialog = { showBackupDialog = true },
                    onOpenUpdateDialog = { showUpdateDialog = true }
                )
            }
        }
    }
}

@Composable
fun DashboardTopBar(
    profileCount: Int,
    downloadsCount: Int,
    onSyncClick: () -> Unit,
    onAddClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onWorkspacesClick: () -> Unit
) {
    val context = LocalContext.current
    val currentThemeMode by ThemePreferences.currentThemeMode

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NexaFlowHeaderTitle(
            subtitle = "$profileCount Profiles • Multi-Workspace Active"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { ThemePreferences.toggleTheme(context) }) {
                val (themeIcon, themeDesc) = when (currentThemeMode) {
                    AppThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto to "Theme: System Adaptive"
                    AppThemeMode.DARK -> Icons.Outlined.DarkMode to "Theme: Dark"
                    AppThemeMode.LIGHT -> Icons.Outlined.LightMode to "Theme: Light"
                }
                Icon(
                    themeIcon,
                    contentDescription = themeDesc,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onWorkspacesClick) {
                Icon(Icons.Outlined.FolderSpecial, contentDescription = "Manage Workspaces", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDownloadsClick) {
                BadgedBox(badge = {
                    if (downloadsCount > 0) {
                        Badge { Text("$downloadsCount") }
                    }
                }) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = "Downloads", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onUpdateClick) {
                Icon(Icons.Outlined.SystemUpdate, contentDescription = "Check for Updates", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onSyncClick) {
                Icon(Icons.Outlined.CloudSync, contentDescription = "Sync & Backup", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onAddClick) {
                Icon(Icons.Outlined.AddCircleOutline, contentDescription = "New Profile", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun HomeDashboardTab(
    profiles: List<Profile>,
    workspaces: List<Workspace>,
    selectedWorkspaceFilter: String?,
    filteredProfiles: List<Profile>,
    activeProfile: Profile?,
    selectedProfileId: Int?,
    onSelectWorkspace: (String?) -> Unit,
    onManageWorkspaces: () -> Unit,
    onSelectProfile: (Int) -> Unit,
    onOpenBrowser: (Profile) -> Unit,
    onToggleDesktop: (Profile, Boolean) -> Unit,
    onToggleBackground: (Profile, Boolean) -> Unit,
    onEditProfile: (Profile) -> Unit,
    onOpenBackup: () -> Unit,
    onOpenDownloads: () -> Unit,
    onAddNew: () -> Unit
) {
    val activeBgSessionsCount = remember(profiles) { profiles.count { it.isBackground } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Multi-Workspace Selector Bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Workspaces & Categories",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = onManageWorkspaces,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Manage", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                val allWsOptions = listOf<Pair<String?, String>>(null to "All (${profiles.size})") +
                        workspaces.map { ws ->
                            val count = profiles.count { it.workspaceName.equals(ws.name, ignoreCase = true) }
                            ws.name to "${ws.name} ($count)"
                        }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(allWsOptions) { (wsName, label) ->
                        val isSelected = selectedWorkspaceFilter == wsName
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectWorkspace(wsName) },
                            label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            shape = RoundedCornerShape(10.dp),
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }
            }
        }

        // Active Background Tasks Status Card
        if (activeBgSessionsCount > 0 && BackgroundSessionManager.globalBackgroundTasksEnabled.value) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
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
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF10B981)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("$activeBgSessionsCount Background Tasks Active", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF065F46))
                                Text("Profiles running live in memory for AI generation", fontSize = 10.sp, color = Color(0xFF047857))
                            }
                        }
                    }
                }
            }
        }

        // Horizontal Profile Chips Selector
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Accounts & Profiles",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${filteredProfiles.size} Shown",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (filteredProfiles.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("No profiles in this workspace", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = onAddNew, shape = RoundedCornerShape(8.dp)) {
                                Text("Add Profile", fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(filteredProfiles) { profile ->
                            val isSelected = profile.id == selectedProfileId
                            val chipBg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            val chipText = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(chipBg)
                                    .clickable {
                                        onSelectProfile(profile.id)
                                        onOpenBrowser(profile)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    if (profile.isDesktopMode) Icons.Outlined.DesktopWindows else Icons.Outlined.PhoneAndroid,
                                    contentDescription = null,
                                    tint = chipText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = profile.name,
                                    color = chipText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Selected Profile Card
        item {
            if (activeProfile != null) {
                EnhancedProfileCard(
                    profile = activeProfile,
                    onOpen = { onOpenBrowser(activeProfile) },
                    onEdit = { onEditProfile(activeProfile) },
                    onToggleDesktop = { onToggleDesktop(activeProfile, it) },
                    onToggleBackground = { onToggleBackground(activeProfile, it) }
                )
            }
        }

        // Quick Launch Website Shortcuts
        if (activeProfile != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.RocketLaunch, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("Launch Site in '${activeProfile.name}'", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(QuickSites) { (siteName, siteUrl) ->
                                OutlinedButton(
                                    onClick = {
                                        onOpenBrowser(activeProfile.copy(url = siteUrl))
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(siteName, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Downloads & Media Quick Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column {
                            Text("Downloads & Media Vault", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Generated images, videos & saved media", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(
                        onClick = onOpenDownloads,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("OPEN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedProfileCard(
    profile: Profile,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onToggleDesktop: (Boolean) -> Unit,
    onToggleBackground: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Profile Header
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
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (profile.isDesktopMode) Icons.Outlined.DesktopWindows else Icons.Outlined.Smartphone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                profile.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    profile.workspaceName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            profile.url,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit Profile", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }

            // Toggles Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Desktop Toggle Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("RENDERING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (profile.isDesktopMode) "Desktop" else "Mobile", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Switch(
                                checked = profile.isDesktopMode,
                                onCheckedChange = onToggleDesktop,
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }

                // Background Toggle Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("BACKGROUND", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (profile.isBackground) "Running" else "Off", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Switch(
                                checked = profile.isBackground,
                                onCheckedChange = onToggleBackground,
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Action Launch Button
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Outlined.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Isolated Browser", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ProfilesManagerTab(
    profiles: List<Profile>,
    workspaces: List<Workspace>,
    selectedWorkspaceFilter: String?,
    onSelectWorkspace: (String?) -> Unit,
    onManageWorkspaces: () -> Unit,
    onOpenBrowser: (Profile) -> Unit,
    onEditProfile: (Profile) -> Unit,
    onDeleteProfile: (Profile) -> Unit,
    onToggleDesktop: (Profile, Boolean) -> Unit,
    onToggleBackground: (Profile, Boolean) -> Unit,
    onAddNew: () -> Unit
) {
    val filteredProfiles = remember(profiles, selectedWorkspaceFilter) {
        if (selectedWorkspaceFilter == null) profiles
        else profiles.filter { it.workspaceName.equals(selectedWorkspaceFilter, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Workspace Profiles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Group accounts and run tasks independently", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = onAddNew,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Profile", fontSize = 11.sp)
                }
            }
        }

        // Workspace Filter Chips
        item {
            val allWsOptions = listOf<Pair<String?, String>>(null to "All (${profiles.size})") +
                    workspaces.map { ws ->
                        val count = profiles.count { it.workspaceName.equals(ws.name, ignoreCase = true) }
                        ws.name to "${ws.name} ($count)"
                    }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allWsOptions) { (wsName, label) ->
                    val isSelected = selectedWorkspaceFilter == wsName
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectWorkspace(wsName) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        if (filteredProfiles.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.FolderOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                        Text("No profiles found in this workspace", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Button(onClick = onAddNew) {
                            Text("Create Profile")
                        }
                    }
                }
            }
        } else {
            items(filteredProfiles, key = { it.id }) { profile ->
                ProfileRowCard(
                    profile = profile,
                    onOpen = { onOpenBrowser(profile) },
                    onEdit = { onEditProfile(profile) },
                    onDelete = { onDeleteProfile(profile) },
                    onToggleDesktop = { onToggleDesktop(profile, it) },
                    onToggleBackground = { onToggleBackground(profile, it) }
                )
            }
        }
    }
}

@Composable
fun ProfileRowCard(
    profile: Profile,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleDesktop: (Boolean) -> Unit,
    onToggleBackground: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (profile.isDesktopMode) Icons.Outlined.DesktopWindows else Icons.Outlined.Smartphone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    profile.workspaceName,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(profile.url, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Desktop", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Switch(
                            checked = profile.isDesktopMode,
                            onCheckedChange = onToggleDesktop,
                            modifier = Modifier.height(20.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Background", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Switch(
                            checked = profile.isBackground,
                            onCheckedChange = onToggleBackground,
                            modifier = Modifier.height(20.dp)
                        )
                    }
                }

                Button(
                    onClick = onOpen,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Launch", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun VaultAndDownloadsTab(
    profiles: List<Profile>,
    downloads: List<DownloadItem>,
    onOpenBackupDialog: () -> Unit,
    onOpenDownloadsDialog: () -> Unit,
    onDeleteDownload: (DownloadItem) -> Unit,
    onClearDownloads: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Downloads Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Downloads & Media Vault", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${downloads.size} Media Files Downloaded", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Button(
                            onClick = onOpenDownloadsDialog,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Manage Files", fontSize = 11.sp)
                        }
                    }

                    if (downloads.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            downloads.take(4).forEach { item ->
                                DownloadItemRow(
                                    item = item,
                                    onOpen = { DownloadHelper.openFile(context, item) },
                                    onShare = { DownloadHelper.shareFile(context, item) },
                                    onDelete = { onDeleteDownload(item) }
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No files downloaded yet from AI generators or websites.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Backup Vault Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Cloud & Local Backup", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("AES-256 JSON & Firebase Sync", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Text(
                        "Export full browser state, isolated accounts, workspaces, and cookies to an encrypted file or sync to Firebase Cloud.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = onOpenBackupDialog,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Backup & Sync Vault")
                    }
                }
            }
        }
    }
}

@Composable
fun EngineSettingsTab(
    profiles: List<Profile>,
    onOpenBrowser: (Profile) -> Unit,
    onOpenBackupDialog: () -> Unit,
    onOpenUpdateDialog: () -> Unit
) {
    val context = LocalContext.current
    var globalBackground by remember { BackgroundSessionManager.globalBackgroundTasksEnabled }
    var trackingProtection by remember { mutableStateOf(true) }
    var hardwareAccel by remember { mutableStateOf(true) }
    var doNotTrack by remember { mutableStateOf(true) }
    val user = FirebaseSyncManager.currentUser

    val currentThemeMode by ThemePreferences.currentThemeMode

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App Theme & Appearance Mode
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("App Theme & Dark Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Adaptive OLED/Midnight system & high contrast", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple(AppThemeMode.SYSTEM, "System Adaptive", Icons.Outlined.BrightnessAuto),
                            Triple(AppThemeMode.DARK, "Dark Mode", Icons.Outlined.DarkMode),
                            Triple(AppThemeMode.LIGHT, "Light Mode", Icons.Outlined.LightMode)
                        ).forEach { (mode, title, icon) ->
                            val isSelected = currentThemeMode == mode
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        ThemePreferences.setThemeMode(context, mode)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        title,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // App Update Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Automatic App Updates", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Check GitHub Releases for latest APK", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Button(
                        onClick = onOpenUpdateDialog,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("CHECK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Global Background Task Engine Switch
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Background Task Engine", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Allow Background Sessions", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Keeps active tabs in memory so video/image generation runs in background", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = globalBackground,
                            onCheckedChange = {
                                BackgroundSessionManager.setGlobalBackgroundEnabled(it)
                                Toast.makeText(context, if (it) "Background tasks enabled" else "All background tasks suspended", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    if (!globalBackground) {
                        Text("Background execution is currently paused. Switch on to allow profiles to continue generation while you switch accounts.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Web Engine Preferences
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Engine Preferences", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Tracking Protection", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Blocks fingerprinting & ads", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = trackingProtection, onCheckedChange = { trackingProtection = it })
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Hardware Acceleration", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Smooth WebGL & CSS rendering", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = hardwareAccel, onCheckedChange = { hardwareAccel = it })
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Send 'Do Not Track'", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Requests sites to not track activity", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = doNotTrack, onCheckedChange = { doNotTrack = it })
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Storage Management", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Button(
                        onClick = {
                            BackgroundSessionManager.clearAll()
                            Toast.makeText(context, "Engine cache and background sessions cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Clear Background Sessions & Cache")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("NexaFlow MultiSpace Browser", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Multi-Workspace • Media Downloader • Background AI Execution", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun DashboardBottomBar(
    selectedTab: Int,
    downloadsCount: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Outlined.Home, contentDescription = "Home") },
            label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.Medium) }
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Outlined.GridView, contentDescription = "Profiles") },
            label = { Text("Profiles", fontSize = 11.sp, fontWeight = FontWeight.Medium) }
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = {
                BadgedBox(badge = {
                    if (downloadsCount > 0) {
                        Badge { Text("$downloadsCount") }
                    }
                }) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = "Vault & Downloads")
                }
            },
            label = { Text("Vault", fontSize = 11.sp, fontWeight = FontWeight.Medium) }
        )
        NavigationBarItem(
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            icon = { Icon(Icons.Outlined.Settings, contentDescription = "Settings") },
            label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.Medium) }
        )
    }
}
