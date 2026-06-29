package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM event_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<LogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogRaw(log: LogEntity)

    @Transaction
    suspend fun insertLog(log: LogEntity) {
        var resolvedSource = log.source
        if (resolvedSource == "auto") {
            try {
                val stack = Thread.currentThread().stackTrace
                val isManual = stack.any { 
                    it.className.contains("MainViewModel") || 
                    it.className.contains("MainActivity") 
                }
                if (isManual) {
                    resolvedSource = "manual"
                }
            } catch (e: Exception) {
                // ignored
            }
        }
        insertLogRaw(log.copy(source = resolvedSource))
    }

    @Query("DELETE FROM event_logs")
    suspend fun clearLogs()

    @Query("DELETE FROM event_logs WHERE type = :type")
    suspend fun deleteLogsByType(type: String)

    @Query("DELETE FROM event_logs WHERE id = :id")
    suspend fun deleteLogById(id: Int)

    @Query("SELECT * FROM created_files ORDER BY timestamp DESC")
    fun getAllCreatedFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM event_logs WHERE type NOT IN ('clipboard_service', 'clipboard_history', 'system', 'SYSTEM', 'CLIPBOARD_SERVICE', 'CLIPBOARD_HISTORY') AND message NOT LIKE '%تجاوز النسخ%' ORDER BY timestamp DESC LIMIT 1")
    fun getLastSignificantEvent(): Flow<LogEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileEntity)

    @Query("DELETE FROM created_files WHERE id = :id")
    suspend fun deleteFileById(id: Int)

    @Query("DELETE FROM created_files")
    suspend fun clearCreatedFiles()
}
