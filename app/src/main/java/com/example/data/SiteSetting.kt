package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "site_settings",
    indices = [
        Index(value = ["profileId", "domain"], unique = true)
    ]
)
data class SiteSetting(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Int,
    val domain: String,
    val desktopMode: Boolean? = null,
    val allowJavascript: Boolean = true,
    val blockAds: Boolean = false,
    val zoomPercent: Int = 100,
    val clearCookiesOnExit: Boolean = false
)
