package com.bbioon.plantdisease.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.bbioon.plantdisease.data.model.ScanRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Insert
    suspend fun insert(scan: ScanRecord): Long

    @Query("SELECT * FROM scans ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getScans(limit: Int = 20, offset: Int = 0): List<ScanRecord>

    @Query("SELECT * FROM scans WHERE id = :id")
    suspend fun getScanById(id: Long): ScanRecord?

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM scans")
    suspend fun getCount(): Int

    @Query("SELECT * FROM scans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScanRecord>>
}
