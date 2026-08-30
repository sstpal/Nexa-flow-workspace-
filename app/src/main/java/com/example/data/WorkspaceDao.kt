package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces ORDER BY id ASC")
    fun getAll(): Flow<List<Workspace>>

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Workspace?

    @Query("SELECT * FROM workspaces WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Workspace?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workspace: Workspace): Long

    @Update
    suspend fun update(workspace: Workspace)

    @Delete
    suspend fun delete(workspace: Workspace)

    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun deleteById(id: Int)
}
