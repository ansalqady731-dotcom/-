package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitorDao {
    // Quota Settings
    @Query("SELECT * FROM quota_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<QuotaSettingsEntity?>

    @Query("SELECT * FROM quota_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): QuotaSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: QuotaSettingsEntity)

    // Consumption Records
    @Query("SELECT * FROM consumption_records ORDER BY timestamp DESC")
    fun getAllRecordsFlow(): Flow<List<ConsumptionRecordEntity>>

    @Query("SELECT * FROM consumption_records WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    fun getRecordsSinceFlow(sinceTimestamp: Long): Flow<List<ConsumptionRecordEntity>>

    @Query("SELECT * FROM consumption_records WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    suspend fun getRecordsSince(sinceTimestamp: Long): List<ConsumptionRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: ConsumptionRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<ConsumptionRecordEntity>)

    @Query("DELETE FROM consumption_records WHERE timestamp >= :sinceTimestamp")
    suspend fun deleteRecordsSince(sinceTimestamp: Long)

    @Query("DELETE FROM consumption_records")
    suspend fun clearAllRecords()

    // Devices
    @Query("SELECT * FROM connected_devices ORDER BY isConnected DESC, lastSeen DESC")
    fun getAllDevicesFlow(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM connected_devices WHERE macAddress = :mac LIMIT 1")
    suspend fun getDeviceByMac(mac: String): DeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDevices(devices: List<DeviceEntity>)

    @Query("UPDATE connected_devices SET customNickname = :nickname WHERE macAddress = :mac")
    suspend fun updateDeviceNickname(mac: String, nickname: String)

    @Query("UPDATE connected_devices SET isConnected = 0")
    suspend fun markAllDevicesDisconnected()

    @Query("DELETE FROM connected_devices")
    suspend fun clearDevices()

    // Modem Logs
    @Query("SELECT * FROM modem_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogsFlow(limit: Int = 30): Flow<List<ModemLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ModemLogEntity)

    @Query("DELETE FROM modem_logs")
    suspend fun clearLogs()
}
