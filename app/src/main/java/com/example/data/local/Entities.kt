package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quota_settings")
data class QuotaSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val quotaTotalGb: Double = 60.0,
    val billingCycleStartDay: Int = 1,
    val alertThreshold80: Boolean = true,
    val alertThreshold90: Boolean = true,
    val modemIp: String = "192.168.0.1",
    val modemPort: Int = 80,
    val lastResetTimestamp: Long = System.currentTimeMillis(),
    val lastSyncTimestamp: Long = 0L
)

@Entity(tableName = "consumption_records")
data class ConsumptionRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val dateString: String,
    val downloadBytes: Long,
    val uploadBytes: Long,
    val totalBytes: Long,
    val sourceNote: String = "ZTE_MF293N"
)

@Entity(tableName = "connected_devices")
data class DeviceEntity(
    @PrimaryKey val macAddress: String,
    val ipAddress: String,
    val hostName: String,
    val customNickname: String = "",
    val isConnected: Boolean = true,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val dataUsedBytes: Long = 0L
)

@Entity(tableName = "modem_logs")
data class ModemLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val title: String,
    val details: String,
    val isSuccess: Boolean
)
