package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteSettingDao {
    @Query("SELECT * FROM site_settings WHERE profileId = :profileId")
    fun getSettingsForProfile(profileId: Int): Flow<List<SiteSetting>>

    @Query("SELECT * FROM site_settings WHERE profileId = :profileId")
    suspend fun getSettingsForProfileSync(profileId: Int): List<SiteSetting>

    @Query("SELECT * FROM site_settings WHERE profileId = :profileId AND domain = :domain LIMIT 1")
    suspend fun getSettingForDomain(profileId: Int, domain: String): SiteSetting?

    @Query("SELECT * FROM site_settings ORDER BY profileId, domain")
    suspend fun getAllSettingsSync(): List<SiteSetting>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(setting: SiteSetting): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(settings: List<SiteSetting>)

    @Update
    suspend fun update(setting: SiteSetting)

    @Delete
    suspend fun delete(setting: SiteSetting)

    @Query("DELETE FROM site_settings WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Int)

    @Query("DELETE FROM site_settings")
    suspend fun deleteAll()
}
