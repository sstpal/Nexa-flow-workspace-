package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id ASC")
    fun getAllProfiles(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles ORDER BY id ASC")
    suspend fun getAllProfilesSync(): List<Profile>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: Int): Profile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: Profile): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<Profile>)

    @Update
    suspend fun update(profile: Profile)

    @Delete
    suspend fun delete(profile: Profile)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM profiles")
    suspend fun deleteAllProfiles()
}
