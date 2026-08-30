package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workspaces")
data class Workspace(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val colorHex: String = "#6750A4",
    val iconKey: String = "work",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
