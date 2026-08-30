package com.example.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DriveBackupFile(
    val id: String,
    val name: String,
    val modifiedTime: String,
    val size: Long
)

sealed class DriveResult<out T> {
    data class Success<out T>(val data: T) : DriveResult<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : DriveResult<Nothing>()
}

object GoogleDriveService {
    private const val TAG = "GoogleDriveService"
    private const val DRIVE_API_URL = "https://www.googleapis.com/drive/v3/files"
    private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Lists all backup files in the user's hidden Google Drive appDataFolder.
     */
    suspend fun listBackups(accessToken: String): DriveResult<List<DriveBackupFile>> = withContext(Dispatchers.IO) {
        try {
            val url = "$DRIVE_API_URL?spaces=appDataFolder&fields=files(id,name,modifiedTime,size)&orderBy=modifiedTime%20desc"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Drive list error: ${response.code} - $responseBody")
                return@withContext DriveResult.Error("Google Drive error: HTTP ${response.code} ($responseBody)")
            }

            val json = JSONObject(responseBody)
            val filesArray = json.optJSONArray("files") ?: JSONArray()
            val list = mutableListOf<DriveBackupFile>()

            for (i in 0 until filesArray.length()) {
                val fileObj = filesArray.getJSONObject(i)
                val id = fileObj.getString("id")
                val name = fileObj.getString("name")
                val modifiedTime = fileObj.optString("modifiedTime", "")
                val size = fileObj.optLong("size", 0L)
                list.add(DriveBackupFile(id, name, modifiedTime, size))
            }

            Log.d(TAG, "Found ${list.size} backups in appDataFolder")
            DriveResult.Success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Exception listing Drive backups", e)
            DriveResult.Error(e.message ?: "Failed to list backups from Google Drive", e)
        }
    }

    /**
     * Uploads a new JSON backup directly into the hidden appDataFolder of Google Drive.
     */
    suspend fun uploadBackup(
        accessToken: String,
        backupJson: String,
        fileName: String = "nexaflow_workspace_backup_${System.currentTimeMillis()}.json"
    ): DriveResult<DriveBackupFile> = withContext(Dispatchers.IO) {
        try {
            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("parents", JSONArray().apply { put("appDataFolder") })
                put("mimeType", "application/json")
            }.toString()

            val jsonMediaType = "application/json; charset=UTF-8".toMediaType()
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(metadataJson.toRequestBody(jsonMediaType))
                .addPart(backupJson.toRequestBody(jsonMediaType))
                .build()

            val request = Request.Builder()
                .url(DRIVE_UPLOAD_URL)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(multipartBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Drive upload error: ${response.code} - $responseBody")
                return@withContext DriveResult.Error("Google Drive upload error: HTTP ${response.code} ($responseBody)")
            }

            val resultObj = JSONObject(responseBody)
            val fileId = resultObj.getString("id")
            val name = resultObj.optString("name", fileName)

            Log.d(TAG, "Successfully uploaded backup to appDataFolder: $fileId")
            DriveResult.Success(DriveBackupFile(fileId, name, System.currentTimeMillis().toString(), backupJson.toByteArray().size.toLong()))
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading Drive backup", e)
            DriveResult.Error(e.message ?: "Failed to upload backup to Google Drive", e)
        }
    }

    /**
     * Downloads and parses a backup file content from Google Drive appDataFolder.
     */
    suspend fun downloadBackup(
        accessToken: String,
        fileId: String
    ): DriveResult<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$DRIVE_API_URL/$fileId?alt=media"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val content = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Drive download error: ${response.code} - $content")
                return@withContext DriveResult.Error("Google Drive download error: HTTP ${response.code}")
            }

            Log.d(TAG, "Successfully downloaded backup payload ($fileId): ${content.length} chars")
            DriveResult.Success(content)
        } catch (e: Exception) {
            Log.e(TAG, "Exception downloading Drive backup", e)
            DriveResult.Error(e.message ?: "Failed to download backup from Google Drive", e)
        }
    }

    /**
     * Deletes an old backup file from Google Drive appDataFolder.
     */
    suspend fun deleteBackup(
        accessToken: String,
        fileId: String
    ): DriveResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "$DRIVE_API_URL/$fileId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext DriveResult.Error("Failed to delete file (HTTP ${response.code})")
            }
            DriveResult.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Exception deleting Drive backup", e)
            DriveResult.Error(e.message ?: "Failed to delete backup from Google Drive", e)
        }
    }
}
