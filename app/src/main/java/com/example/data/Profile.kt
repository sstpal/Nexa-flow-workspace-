package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val url: String,
    val workspaceName: String = "General",
    val isDesktopMode: Boolean = false,
    val isBackground: Boolean = false,
    val userAgent: String = "",
    val javascriptEnabled: Boolean = true,
    val cookiesEnabled: Boolean = true,
    val domStorageEnabled: Boolean = true,
    val autoLoginEnabled: Boolean = true,
    val colorHex: String = "#3B82F6",
    val iconKey: String = "work",
    val lastActiveAt: Long = System.currentTimeMillis()
)
