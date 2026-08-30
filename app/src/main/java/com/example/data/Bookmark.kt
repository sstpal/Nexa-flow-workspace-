package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["profileId", "url"], unique = true)
    ]
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Int,
    val title: String,
    val url: String,
    val faviconUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
