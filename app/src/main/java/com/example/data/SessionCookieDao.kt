package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionCookieDao {
    @Query("SELECT * FROM session_cookies WHERE profileId = :profileId ORDER BY lastUpdated DESC")
    fun getCookiesForProfile(profileId: Int): Flow<List<SessionCookie>>

    @Query("SELECT * FROM session_cookies WHERE profileId = :profileId ORDER BY lastUpdated DESC")
    suspend fun getCookiesForProfileSync(profileId: Int): List<SessionCookie>

    @Query("SELECT * FROM session_cookies WHERE profileId = :profileId AND domain = :domain LIMIT 1")
    suspend fun getCookieForDomain(profileId: Int, domain: String): SessionCookie?

    @Query("SELECT * FROM session_cookies ORDER BY lastUpdated DESC")
    fun getAllCookiesFlow(): Flow<List<SessionCookie>>

    @Query("SELECT * FROM session_cookies ORDER BY profileId, domain")
    suspend fun getAllCookiesSync(): List<SessionCookie>

    @Query("SELECT COUNT(*) FROM session_cookies WHERE profileId = :profileId")
    fun getCookieCountForProfile(profileId: Int): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(cookie: SessionCookie): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cookies: List<SessionCookie>)

    @Update
    suspend fun update(cookie: SessionCookie)

    @Delete
    suspend fun delete(cookie: SessionCookie)

    @Query("DELETE FROM session_cookies WHERE profileId = :profileId AND domain = :domain")
    suspend fun deleteForDomain(profileId: Int, domain: String)

    @Query("DELETE FROM session_cookies WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Int)

    @Query("DELETE FROM session_cookies")
    suspend fun deleteAll()
}
