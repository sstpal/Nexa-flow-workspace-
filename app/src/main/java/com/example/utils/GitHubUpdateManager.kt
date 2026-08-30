package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val releaseName: String,
    val body: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long,
    val publishedAt: String,
    val htmlUrl: String,
    val repoSlug: String
)

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    object Checking : UpdateDownloadState()
    data class Available(val release: AppReleaseInfo, val isNewer: Boolean) : UpdateDownloadState()
    data class NoReleaseYet(val repoSlug: String, val repoUrl: String, val message: String) : UpdateDownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String, val repoSlug: String) : UpdateDownloadState()
}

object GitHubUpdateManager {
    private const val TAG = "GitHubUpdateManager"
    private const val PREFS_NAME = "nexaflow_updates_pref"
    private const val KEY_REPO = "github_repo_slug"
    
    // Project repository configured as requested by user
    const val DEFAULT_REPO = "sstpal/Flow-ai-"

    fun getAppVersion(): String {
        return "v${BuildConfig.VERSION_NAME}"
    }

    fun cleanRepoSlug(rawInput: String): String {
        var cleaned = rawInput.trim()
        cleaned = cleaned.removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
        cleaned = cleaned.removeSuffix(".git")
        cleaned = cleaned.trim('/')
        return cleaned.ifBlank { DEFAULT_REPO }
    }

    fun getSavedRepo(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_REPO, DEFAULT_REPO) ?: DEFAULT_REPO
        return cleanRepoSlug(saved)
    }

    fun saveRepo(context: Context, repoSlug: String) {
        val cleaned = cleanRepoSlug(repoSlug)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_REPO, cleaned).apply()
    }

    /**
     * Checks the latest release on GitHub for the given repo.
     * Automatically attempts multiple slug variations (e.g. sstpal/Flow-ai- and sstpal/Flow-ai)
     * and checks both /releases/latest and /releases list.
     */
    suspend fun checkLatestRelease(
        context: Context,
        customRepo: String? = null
    ): Result<UpdateDownloadState> = withContext(Dispatchers.IO) {
        try {
            val targetRepo = customRepo?.let { cleanRepoSlug(it) } ?: getSavedRepo(context)
            
            // Build candidate repo variations to handle optional trailing dash
            val candidates = linkedSetOf(targetRepo)
            if (targetRepo.endsWith("-")) {
                candidates.add(targetRepo.removeSuffix("-"))
            } else {
                candidates.add("$targetRepo-")
            }

            var lastError: String? = null
            var validRepoFound: String? = null

            for (candidate in candidates) {
                Log.d(TAG, "Checking GitHub updates for candidate: $candidate")

                // Strategy 1: Check /releases/latest
                val latestUrl = "https://api.github.com/repos/$candidate/releases/latest"
                val latestResult = fetchReleaseFromUrl(latestUrl, candidate)
                if (latestResult != null) {
                    val isNewer = isVersionNewer(latestResult.tagName, getAppVersion())
                    return@withContext Result.success(UpdateDownloadState.Available(latestResult, isNewer))
                }

                // Strategy 2: Check all /releases (in case of pre-releases or draft tags)
                val listUrl = "https://api.github.com/repos/$candidate/releases"
                val listResult = fetchFirstReleaseFromList(listUrl, candidate)
                if (listResult != null) {
                    val isNewer = isVersionNewer(listResult.tagName, getAppVersion())
                    return@withContext Result.success(UpdateDownloadState.Available(listResult, isNewer))
                }

                // Strategy 3: Check if repository itself exists
                val repoInfoUrl = "https://api.github.com/repos/$candidate"
                val repoExists = checkRepoExists(repoInfoUrl)
                if (repoExists) {
                    validRepoFound = candidate
                    break
                }
            }

            if (validRepoFound != null) {
                // Repository exists on GitHub, but has 0 published releases yet
                val repoWebUrl = "https://github.com/$validRepoFound"
                return@withContext Result.success(
                    UpdateDownloadState.NoReleaseYet(
                        repoSlug = validRepoFound,
                        repoUrl = repoWebUrl,
                        message = "Connected to repository '$validRepoFound'. No new APK releases published on GitHub yet."
                    )
                )
            }

            // If neither releases nor repo metadata were found
            val fallbackRepo = targetRepo
            Result.success(
                UpdateDownloadState.Error(
                    message = "Could not find releases for '$fallbackRepo'. Ensure repository is public and accessible.",
                    repoSlug = fallbackRepo
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check GitHub update", e)
            val fallbackRepo = customRepo ?: getSavedRepo(context)
            Result.success(UpdateDownloadState.Error(e.message ?: "Failed to connect to GitHub", fallbackRepo))
        }
    }

    private fun isVersionNewer(remoteTag: String, currentTag: String): Boolean {
        if (remoteTag.isBlank()) return false
        val cleanRemote = remoteTag.removePrefix("v").removePrefix("V").trim()
        val cleanCurrent = currentTag.removePrefix("v").removePrefix("V").trim()
        if (cleanRemote.equals(cleanCurrent, ignoreCase = true)) return false
        return true
    }

    private fun fetchReleaseFromUrl(apiUrl: String, repoSlug: String): AppReleaseInfo? {
        return try {
            val url = URL(apiUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "NexaFlow-Android-App")
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                parseReleaseJson(JSONObject(jsonText), repoSlug)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchFirstReleaseFromList(apiUrl: String, repoSlug: String): AppReleaseInfo? {
        return try {
            val url = URL(apiUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "NexaFlow-Android-App")
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(jsonText)
                if (array.length() > 0) {
                    parseReleaseJson(array.getJSONObject(0), repoSlug)
                } else null
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun checkRepoExists(apiUrl: String): Boolean {
        return try {
            val url = URL(apiUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "NexaFlow-Android-App")
                connectTimeout = 8000
                readTimeout = 8000
            }
            conn.responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    private fun parseReleaseJson(json: JSONObject, repoSlug: String): AppReleaseInfo {
        val tagName = json.optString("tag_name", "")
        val name = json.optString("name", tagName)
        val body = json.optString("body", "No release notes provided.")
        val publishedAt = json.optString("published_at", "")
        val htmlUrl = json.optString("html_url", "https://github.com/$repoSlug/releases")

        var apkUrl: String? = null
        var apkName: String? = null
        var apkSize: Long = 0

        val assetsArray = json.optJSONArray("assets")
        if (assetsArray != null) {
            for (i in 0 until assetsArray.length()) {
                val asset = assetsArray.getJSONObject(i)
                val aName = asset.optString("name", "")
                if (aName.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    apkName = aName
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }
        }

        return AppReleaseInfo(
            tagName = tagName,
            releaseName = name,
            body = body,
            apkDownloadUrl = apkUrl,
            apkFileName = apkName ?: "NexaFlow-$tagName.apk",
            apkSizeBytes = apkSize,
            publishedAt = publishedAt,
            htmlUrl = htmlUrl,
            repoSlug = repoSlug
        )
    }

    /**
     * Downloads the APK file to the device download directory and reports progress.
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        fileName: String,
        onProgress: (Float, Long, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.cacheDir
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val sanitizedName = fileName.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            val targetFile = File(downloadDir, sanitizedName)

            if (targetFile.exists()) {
                targetFile.delete()
            }

            val url = URL(apkUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 NexaFlow-Updater")
                connectTimeout = 20000
                readTimeout = 30000
            }

            val totalBytes = connection.contentLength.toLong()
            var downloadedBytes: Long = 0

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastReport = System.currentTimeMillis()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReport > 100 || downloadedBytes == totalBytes) {
                            val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0.5f
                            onProgress(progress, downloadedBytes, totalBytes)
                            lastReport = now
                        }
                    }
                }
            }

            Log.d(TAG, "APK successfully downloaded to ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download APK", e)
            Result.failure(e)
        }
    }

    /**
     * Triggers package installer to install the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file does not exist: ${apkFile.absolutePath}")
                return false
            }

            // Android 8.0+ Check Unknown Sources Permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return false
                }
            }

            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger APK install", e)
            false
        }
    }
}
