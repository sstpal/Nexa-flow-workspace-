package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filename: String,
    val fileUri: String,
    val filePath: String,
    val mimeType: String,
    val fileSize: Long = 0L,
    val sourceUrl: String = "",
    val profileName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
