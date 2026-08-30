package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "session_cookies",
    indices = [
        Index(value = ["profileId", "domain"], unique = true)
    ]
)
data class SessionCookie(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Int,
    val domain: String,
    val cookieString: String,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isAutoLoginActive: Boolean = true
)
