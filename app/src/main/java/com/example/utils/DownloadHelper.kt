package com.example.utils

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.AppDatabase
import com.example.data.DownloadItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object DownloadHelper {

    fun downloadUrl(
        context: Context,
        url: String,
        userAgent: String = "",
        contentDisposition: String = "",
        mimeType: String = "",
        profileName: String = "",
        customFilename: String? = null
    ) {
        if (url.startsWith("data:")) {
            // Handle Data URL directly
            val commaIndex = url.indexOf(",")
            if (commaIndex != -1) {
                val header = url.substring(0, commaIndex)
                val base64Data = url.substring(commaIndex + 1)
                val detectedMime = header.substringAfter("data:").substringBefore(";")
                saveBase64Data(context, base64Data, detectedMime, customFilename, profileName)
            }
            return
        }

        if (url.startsWith("blob:")) {
            // Blob URLs cannot be fetched directly via DownloadManager.
            Toast.makeText(context, "Extracting generated file data...", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            var filename = customFilename ?: URLUtil.guessFileName(url, contentDisposition, mimeType)
            if (mimeType.startsWith("video/") && !filename.endsWith(".mp4", ignoreCase = true) && !filename.endsWith(".webm", ignoreCase = true) && !filename.endsWith(".mov", ignoreCase = true)) {
                val base = filename.substringBeforeLast(".")
                filename = "$base.mp4"
            }
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrEmpty()) {
                    addRequestHeader("Cookie", cookies)
                }
                if (userAgent.isNotEmpty()) {
                    addRequestHeader("User-Agent", userAgent)
                }
                setDescription("Downloading file from $profileName workspace...")
                setTitle(filename)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            Toast.makeText(context, "Downloading: $filename", Toast.LENGTH_SHORT).show()

            // Save record into database
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                val destinationFile = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    filename
                )
                db.downloadDao().insert(
                    DownloadItem(
                        filename = filename,
                        fileUri = destinationFile.toURI().toString(),
                        filePath = destinationFile.absolutePath,
                        mimeType = mimeType.ifEmpty { getMimeTypeFromExtension(filename) },
                        sourceUrl = url,
                        profileName = profileName
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun saveBase64Data(
        context: Context,
        base64Data: String,
        mimeType: String?,
        suggestedFilename: String?,
        profileName: String = ""
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cleanBase64 = if (base64Data.contains(",")) {
                    base64Data.substringAfter(",")
                } else {
                    base64Data
                }.replace("\n", "").replace("\r", "").trim()

                val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                val effectiveMime = mimeType?.ifEmpty { null } ?: detectMimeType(bytes)
                val ext = getExtensionFromMime(effectiveMime)

                val filename = suggestedFilename?.ifEmpty { null }
                    ?: "Generated_${System.currentTimeMillis()}.$ext"

                val finalFilename = if (filename.contains(".")) filename else "$filename.$ext"

                var targetUriString = ""
                var targetPath = ""

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, finalFilename)
                        put(MediaStore.MediaColumns.MIME_TYPE, effectiveMime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { os ->
                            os.write(bytes)
                        }
                        targetUriString = uri.toString()
                        targetPath = "${Environment.DIRECTORY_DOWNLOADS}/$finalFilename"
                    }
                } else {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    val targetFile = File(downloadsDir, finalFilename)
                    FileOutputStream(targetFile).use { fos ->
                        fos.write(bytes)
                    }
                    targetUriString = targetFile.toURI().toString()
                    targetPath = targetFile.absolutePath
                }

                // Insert into download database
                val db = AppDatabase.getDatabase(context)
                db.downloadDao().insert(
                    DownloadItem(
                        filename = finalFilename,
                        fileUri = targetUriString,
                        filePath = targetPath,
                        mimeType = effectiveMime,
                        fileSize = bytes.size.toLong(),
                        sourceUrl = "Generated Content",
                        profileName = profileName
                    )
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved to Downloads: $finalFilename", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to save file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun openFile(context: Context, item: DownloadItem) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                if (item.fileUri.startsWith("content://")) {
                    setDataAndType(Uri.parse(item.fileUri), item.mimeType)
                } else {
                    val file = File(item.filePath)
                    if (file.exists()) {
                        val contentUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        setDataAndType(contentUri, item.mimeType)
                    } else {
                        Toast.makeText(context, "File does not exist in Downloads", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open ${item.filename}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(context: Context, item: DownloadItem) {
        try {
            val shareUri: Uri = if (item.fileUri.startsWith("content://")) {
                Uri.parse(item.fileUri)
            } else {
                val file = File(item.filePath)
                if (file.exists()) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } else {
                    Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, shareUri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ${item.filename}"))
        } catch (e: Exception) {
            Toast.makeText(context, "Share error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun detectMimeType(bytes: ByteArray): String {
        if (bytes.size >= 8) {
            // PNG signature
            if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte()) {
                return "image/png"
            }
            // JPEG signature
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
                return "image/jpeg"
            }
            // GIF signature
            if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) {
                return "image/gif"
            }
            // MP4 signature (ftyp)
            if (bytes.size >= 12 && bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() && bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte()) {
                return "video/mp4"
            }
            // WEBP signature (RIFF....WEBP)
            if (bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()) {
                return "image/webp"
            }
        }
        return "application/octet-stream"
    }

    private fun getExtensionFromMime(mime: String): String {
        return when (mime.lowercase()) {
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/quicktime" -> "mov"
            "audio/mpeg", "audio/mp3" -> "mp3"
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            "application/json" -> "json"
            else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        }
    }

    private fun getMimeTypeFromExtension(filename: String): String {
        val ext = filename.substringAfterLast(".", "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
