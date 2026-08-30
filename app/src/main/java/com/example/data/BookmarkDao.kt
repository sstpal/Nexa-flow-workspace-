package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun getBookmarksForProfile(profileId: Int): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId ORDER BY createdAt DESC")
    suspend fun getBookmarksForProfileSync(profileId: Int): List<Bookmark>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarksFlow(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks ORDER BY profileId, createdAt DESC")
    suspend fun getAllBookmarksSync(): List<Bookmark>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bookmarks: List<Bookmark>)

    @Update
    suspend fun update(bookmark: Bookmark)

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Int)

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()
}
