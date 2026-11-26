package com.expresslease.driver.data.local

import androidx.room.*

@Entity(tableName = "pending_gps_points")
data class PendingGpsPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chassisNumber: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
)

@Dao
interface PendingGpsPointDao {
    @Insert
    suspend fun insert(point: PendingGpsPoint)

    @Query("SELECT * FROM pending_gps_points ORDER BY timestamp ASC LIMIT 50")
    suspend fun getPending(): List<PendingGpsPoint>

    @Delete
    suspend fun delete(point: PendingGpsPoint)

    @Query("DELETE FROM pending_gps_points WHERE retryCount >= 5")
    suspend fun purgeFailed()

    @Query("UPDATE pending_gps_points SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)

    @Query("SELECT COUNT(*) FROM pending_gps_points")
    suspend fun count(): Int
}

@Database(entities = [PendingGpsPoint::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pendingGpsPointDao(): PendingGpsPointDao

    companion object {
        const val DATABASE_NAME = "express_lease_db"
    }
}
