package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.Profile
import com.example.data.Workspace
import com.example.ui.theme.ExampleTheme
import com.example.utils.BackgroundSessionManager
import com.example.utils.FirebaseSyncManager
import com.example.utils.ThemePreferences
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePreferences.initialize(this)
        try {
            FirebaseSyncManager.ensureFirebase(this)
            Log.d("MainActivity", "FirebaseApp initialized successfully")
        } catch (e: Throwable) {
            Log.e("MainActivity", "Firebase init error: ${e.message}")
        }
        enableEdgeToEdge()
        
        setContent {
            val currentThemeMode by ThemePreferences.currentThemeMode
            ExampleTheme(themeMode = currentThemeMode) {
                val context = LocalContext.current
                val database = remember { AppDatabase.getDatabase(context) }
                val profileDao = database.profileDao()
                val workspaceDao = database.workspaceDao()
                val downloadDao = database.downloadDao()

                val profiles by profileDao.getAllProfiles().collectAsStateWithLifecycle(initialValue = emptyList())
                val workspaces by workspaceDao.getAll().collectAsStateWithLifecycle(initialValue = emptyList())
                val downloads by downloadDao.getAll().collectAsStateWithLifecycle(initialValue = emptyList())
                val coroutineScope = rememberCoroutineScope()

                var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

                LaunchedEffect(workspaces.isEmpty()) {
                    if (workspaces.isEmpty()) {
                        workspaceDao.insert(Workspace(name = "General", description = "Main Workspace", colorHex = "#6750A4"))
                        workspaceDao.insert(Workspace(name = "AI Video & Studio", description = "Midjourney, Sora, AI generators", colorHex = "#2563EB"))
                        workspaceDao.insert(Workspace(name = "Social & Accounts", description = "Multi-account social media profiles", colorHex = "#059669"))
                    }
                }

                LaunchedEffect(profiles.isEmpty()) {
                    if (profiles.isEmpty()) {
                        profileDao.insert(
                            Profile(
                                name = "Main Studio",
                                url = "https://www.google.com",
                                workspaceName = "General",
                                isDesktopMode = true,
                                isBackground = true
                            )
                        )
                    }
                }

                when (val screen = currentScreen) {
                    is Screen.Dashboard -> {
                        DashboardScreen(
                            profiles = profiles,
                            workspaces = workspaces,
                            downloads = downloads,
                            onOpenBrowser = { currentScreen = Screen.Browser(it) },
                            onBackup = { /* Handled via Vault & Backup */ },
                            onRestore = { /* Handled via Vault & Backup */ },
                            onCreateProfile = { name, url, workspaceName, isDesktop, isBg ->
                                coroutineScope.launch {
                                    profileDao.insert(
                                        Profile(
                                            name = name,
                                            url = url,
                                            workspaceName = workspaceName,
                                            isDesktopMode = isDesktop,
                                            isBackground = isBg
                                        )
                                    )
                                }
                            },
                            onUpdateProfile = { updatedProfile ->
                                coroutineScope.launch {
                                    profileDao.update(updatedProfile)
                                }
                            },
                            onDeleteProfile = { profileToDelete ->
                                coroutineScope.launch {
                                    profileDao.delete(profileToDelete)
                                }
                            },
                            onCreateWorkspace = { name, desc, color ->
                                coroutineScope.launch {
                                    workspaceDao.insert(Workspace(name = name, description = desc, colorHex = color))
                                }
                            },
                            onUpdateWorkspace = { updatedWs ->
                                coroutineScope.launch {
                                    workspaceDao.update(updatedWs)
                                }
                            },
                            onDeleteWorkspace = { wsToDelete ->
                                coroutineScope.launch {
                                    workspaceDao.delete(wsToDelete)
                                }
                            },
                            onDeleteDownload = { downloadItem ->
                                coroutineScope.launch {
                                    downloadDao.delete(downloadItem)
                                }
                            },
                            onClearDownloads = {
                                coroutineScope.launch {
                                    downloadDao.deleteAll()
                                }
                            },
                            onRestoreList = { restoredList ->
                                coroutineScope.launch {
                                    restoredList.forEach { profileDao.insert(it) }
                                }
                            },
                            onToggleDesktop = { profile, isDesktop ->
                                coroutineScope.launch {
                                    profileDao.update(profile.copy(isDesktopMode = isDesktop))
                                }
                            },
                            onToggleBackground = { profile, isBg ->
                                coroutineScope.launch {
                                    profileDao.update(profile.copy(isBackground = isBg))
                                }
                            }
                        )
                    }
                    is Screen.Browser -> {
                        BrowserScreen(
                            profile = screen.profile,
                            allProfiles = profiles,
                            onSwitchProfile = { newProfile ->
                                currentScreen = Screen.Browser(newProfile)
                            },
                            onToggleDesktopMode = { profileToToggle, isDesktop ->
                                coroutineScope.launch {
                                    profileDao.update(profileToToggle.copy(isDesktopMode = isDesktop))
                                }
                            },
                            onNavigateBack = { currentScreen = Screen.Dashboard }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        BackgroundSessionManager.onAppForegrounded(this)
    }

    override fun onPause() {
        super.onPause()
        BackgroundSessionManager.flushAllCookies()
        BackgroundSessionManager.onAppBackgrounded(this)
    }

    override fun onStop() {
        super.onStop()
        BackgroundSessionManager.flushAllCookies()
        BackgroundSessionManager.onAppBackgrounded(this)
    }
}

sealed class Screen {
    object Dashboard : Screen()
    data class Browser(val profile: Profile) : Screen()
}
