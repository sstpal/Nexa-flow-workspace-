package com.example.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import com.example.data.AppDatabase
import com.example.data.Bookmark
import com.example.data.Profile
import com.example.data.SessionCookie
import com.example.data.SiteSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class RestoreSummary(
    val profilesRestored: Int,
    val cookiesRestored: Int,
    val bookmarksRestored: Int,
    val siteSettingsRestored: Int,
    val isAutoLoginReady: Boolean
)

object BackupManager {
    private const val TAG = "BackupManager"

    /**
     * Builds a comprehensive JSON backup payload from Room database containing:
     * - Profile configurations
     * - Extracted session cookies (Facebook, YouTube, etc.)
     * - Isolated bookmarks
     * - Site-specific workspace settings
     */
    suspend fun generateBackupJson(context: Context): String = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val profiles = db.profileDao().getAllProfilesSync()
        val cookies = db.sessionCookieDao().getAllCookiesSync()
        val bookmarks = db.bookmarkDao().getAllBookmarksSync()
        val settings = db.siteSettingDao().getAllSettingsSync()

        val root = JSONObject().apply {
            put("app", "NexaFlow Workspaces")
            put("version", "2.0")
            put("timestamp", System.currentTimeMillis())
            put("device_info", "Android ${android.os.Build.VERSION.RELEASE}")

            // 1. Profiles
            val profilesArray = JSONArray()
            profiles.forEach { p ->
                profilesArray.put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("url", p.url)
                    put("isDesktopMode", p.isDesktopMode)
                    put("isBackground", p.isBackground)
                    put("userAgent", p.userAgent)
                    put("javascriptEnabled", p.javascriptEnabled)
                    put("cookiesEnabled", p.cookiesEnabled)
                    put("domStorageEnabled", p.domStorageEnabled)
                    put("autoLoginEnabled", p.autoLoginEnabled)
                    put("colorHex", p.colorHex)
                    put("iconKey", p.iconKey)
                    put("lastActiveAt", p.lastActiveAt)
                })
            }
            put("profiles", profilesArray)

            // 2. Session Cookies
            val cookiesArray = JSONArray()
            cookies.forEach { c ->
                cookiesArray.put(JSONObject().apply {
                    put("id", c.id)
                    put("profileId", c.profileId)
                    put("domain", c.domain)
                    put("cookieString", c.cookieString)
                    put("lastUpdated", c.lastUpdated)
                    put("isAutoLoginActive", c.isAutoLoginActive)
                })
            }
            put("session_cookies", cookiesArray)

            // 3. Bookmarks
            val bookmarksArray = JSONArray()
            bookmarks.forEach { b ->
                bookmarksArray.put(JSONObject().apply {
                    put("id", b.id)
                    put("profileId", b.profileId)
                    put("title", b.title)
                    put("url", b.url)
                    put("faviconUrl", b.faviconUrl)
                    put("createdAt", b.createdAt)
                })
            }
            put("bookmarks", bookmarksArray)

            // 4. Site Settings
            val settingsArray = JSONArray()
            settings.forEach { s ->
                settingsArray.put(JSONObject().apply {
                    put("id", s.id)
                    put("profileId", s.profileId)
                    put("domain", s.domain)
                    if (s.desktopMode != null) put("desktopMode", s.desktopMode)
                    put("allowJavascript", s.allowJavascript)
                    put("blockAds", s.blockAds)
                    put("zoomPercent", s.zoomPercent)
                    put("clearCookiesOnExit", s.clearCookiesOnExit)
                })
            }
            put("site_settings", settingsArray)
        }

        root.toString(2)
    }

    /**
     * Restores profiles, session cookies, bookmarks, and site settings from a backup JSON.
     * Injects all session cookies directly into WebView's CookieManager to restore user logins immediately!
     */
    suspend fun restoreFromJson(
        context: Context,
        jsonString: String,
        clearExisting: Boolean = false
    ): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            val db = AppDatabase.getDatabase(context)

            if (clearExisting) {
                db.profileDao().deleteAllProfiles()
                db.sessionCookieDao().deleteAll()
                db.bookmarkDao().deleteAll()
                db.siteSettingDao().deleteAll()
            }

            // 1. Restore Profiles
            val profilesArray = root.optJSONArray("profiles") ?: JSONArray()
            val restoredProfiles = mutableListOf<Profile>()
            for (i in 0 until profilesArray.length()) {
                val p = profilesArray.getJSONObject(i)
                restoredProfiles.add(
                    Profile(
                        id = p.optInt("id", 0),
                        name = p.getString("name"),
                        url = p.optString("url", "https://www.google.com"),
                        isDesktopMode = p.optBoolean("isDesktopMode", false),
                        isBackground = p.optBoolean("isBackground", false),
                        userAgent = p.optString("userAgent", ""),
                        javascriptEnabled = p.optBoolean("javascriptEnabled", true),
                        cookiesEnabled = p.optBoolean("cookiesEnabled", true),
                        domStorageEnabled = p.optBoolean("domStorageEnabled", true),
                        autoLoginEnabled = p.optBoolean("autoLoginEnabled", true),
                        colorHex = p.optString("colorHex", "#3B82F6"),
                        iconKey = p.optString("iconKey", "work"),
                        lastActiveAt = p.optLong("lastActiveAt", System.currentTimeMillis())
                    )
                )
            }
            if (restoredProfiles.isNotEmpty()) {
                db.profileDao().insertAll(restoredProfiles)
            }

            // 2. Restore Session Cookies
            val cookiesArray = root.optJSONArray("session_cookies") ?: JSONArray()
            val restoredCookies = mutableListOf<SessionCookie>()
            for (i in 0 until cookiesArray.length()) {
                val c = cookiesArray.getJSONObject(i)
                restoredCookies.add(
                    SessionCookie(
                        id = c.optLong("id", 0L),
                        profileId = c.getInt("profileId"),
                        domain = c.getString("domain"),
                        cookieString = c.getString("cookieString"),
                        lastUpdated = c.optLong("lastUpdated", System.currentTimeMillis()),
                        isAutoLoginActive = c.optBoolean("isAutoLoginActive", true)
                    )
                )
            }
            if (restoredCookies.isNotEmpty()) {
                db.sessionCookieDao().insertAll(restoredCookies)
            }

            // 3. Restore Bookmarks
            val bookmarksArray = root.optJSONArray("bookmarks") ?: JSONArray()
            val restoredBookmarks = mutableListOf<Bookmark>()
            for (i in 0 until bookmarksArray.length()) {
                val b = bookmarksArray.getJSONObject(i)
                restoredBookmarks.add(
                    Bookmark(
                        id = b.optLong("id", 0L),
                        profileId = b.getInt("profileId"),
                        title = b.getString("title"),
                        url = b.getString("url"),
                        faviconUrl = b.optString("faviconUrl", ""),
                        createdAt = b.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            if (restoredBookmarks.isNotEmpty()) {
                db.bookmarkDao().insertAll(restoredBookmarks)
            }

            // 4. Restore Site Settings
            val settingsArray = root.optJSONArray("site_settings") ?: JSONArray()
            val restoredSettings = mutableListOf<SiteSetting>()
            for (i in 0 until settingsArray.length()) {
                val s = settingsArray.getJSONObject(i)
                restoredSettings.add(
                    SiteSetting(
                        id = s.optLong("id", 0L),
                        profileId = s.getInt("profileId"),
                        domain = s.getString("domain"),
                        desktopMode = if (s.has("desktopMode")) s.getBoolean("desktopMode") else null,
                        allowJavascript = s.optBoolean("allowJavascript", true),
                        blockAds = s.optBoolean("blockAds", false),
                        zoomPercent = s.optInt("zoomPercent", 100),
                        clearCookiesOnExit = s.optBoolean("clearCookiesOnExit", false)
                    )
                )
            }
            if (restoredSettings.isNotEmpty()) {
                db.siteSettingDao().insertAll(restoredSettings)
            }

            // 5. Injects cookies into WebView CookieManager for immediate Auto-Login
            var autoLoginReady = false
            try {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                for (cookie in restoredCookies) {
                    if (cookie.cookieString.isNotBlank()) {
                        val domain = cookie.domain
                        val targetUrl = "https://$domain"
                        val individualCookies = cookie.cookieString.split(";")
                        for (indCookie in individualCookies) {
                            val trimmed = indCookie.trim()
                            if (trimmed.isNotEmpty()) {
                                cookieManager.setCookie(targetUrl, trimmed)
                                cookieManager.setCookie(".$domain", trimmed)
                            }
                        }
                    }
                }
                cookieManager.flush()
                autoLoginReady = restoredCookies.isNotEmpty()
                Log.d(TAG, "Restored and injected ${restoredCookies.size} session cookies into WebView")
            } catch (e: Exception) {
                Log.e(TAG, "Error injecting cookies during restore", e)
            }

            val summary = RestoreSummary(
                profilesRestored = restoredProfiles.size,
                cookiesRestored = restoredCookies.size,
                bookmarksRestored = restoredBookmarks.size,
                siteSettingsRestored = restoredSettings.size,
                isAutoLoginReady = autoLoginReady
            )
            Result.success(summary)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring from JSON", e)
            Result.failure(e)
        }
    }

    /**
     * Writes generated JSON backup to a user-selected SAF Uri (e.g. Google Drive, Downloads, SD Card).
     */
    suspend fun exportJsonToUri(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val json = generateBackupJson(context)
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(json.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            Result.success(json.length)
        } catch (e: Exception) {
            Log.e(TAG, "Failed exporting JSON to Uri", e)
            Result.failure(e)
        }
    }

    /**
     * Reads JSON backup from a user-selected SAF Uri (e.g. Google Drive, Downloads) and restores it.
     */
    suspend fun restoreFromJsonUri(context: Context, uri: Uri): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { `is` ->
                `is`.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } ?: return@withContext Result.failure(Exception("Could not open file stream"))

            restoreFromJson(context, jsonString, clearExisting = false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed restoring JSON from Uri", e)
            Result.failure(e)
        }
    }

    /**
     * Exports full app data to a ZIP file via SAF (Storage Access Framework).
     */
    fun exportData(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                ZipOutputStream(os).use { zos ->
                    val dataDir = context.dataDir

                    // Backup databases
                    val dbDir = File(dataDir, "databases")
                    if (dbDir.exists()) {
                        zipDirectory(dbDir, "databases", zos)
                    }

                    // Backup shared preferences
                    val prefsDir = File(dataDir, "shared_prefs")
                    if (prefsDir.exists()) {
                        zipDirectory(prefsDir, "shared_prefs", zos)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            false
        }
    }

    /**
     * Imports full app data from a ZIP file via SAF.
     */
    fun importData(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { `is` ->
                ZipInputStream(`is`).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val targetFile = File(context.dataDir, entry.name)
                        if (entry.isDirectory) {
                            targetFile.mkdirs()
                        } else {
                            targetFile.parentFile?.mkdirs()
                            FileOutputStream(targetFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            false
        }
    }

    private fun zipDirectory(dir: File, basePath: String, zos: ZipOutputStream) {
        dir.listFiles()?.forEach { file ->
            val entryName = "$basePath/${file.name}"
            if (file.isDirectory) {
                zipDirectory(file, entryName, zos)
            } else {
                zos.putNextEntry(ZipEntry(entryName))
                FileInputStream(file).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }
}
