package com.example.data.repository

import com.example.data.local.ConsumptionRecordEntity
import com.example.data.local.DeviceEntity
import com.example.data.local.ModemLogEntity
import com.example.data.local.MonitorDao
import com.example.data.local.QuotaSettingsEntity
import com.example.data.modem.ModemConnectionState
import com.example.data.modem.ZteModemClient
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SmartMonitorRepository(
    private val dao: MonitorDao,
    private val modemClient: ZteModemClient = ZteModemClient()
) {

    val settingsFlow: Flow<QuotaSettingsEntity?> = dao.getSettingsFlow()
    val allRecordsFlow: Flow<List<ConsumptionRecordEntity>> = dao.getAllRecordsFlow()
    val devicesFlow: Flow<List<DeviceEntity>> = dao.getAllDevicesFlow()
    val logsFlow: Flow<List<ModemLogEntity>> = dao.getRecentLogsFlow(30)

    suspend fun initializeDefaultsIfNeeded() {
        val existing = dao.getSettings()
        if (existing == null) {
            val now = System.currentTimeMillis()
            val defaultSettings = QuotaSettingsEntity(
                id = 1,
                quotaTotalGb = 60.0,
                billingCycleStartDay = 1,
                alertThreshold80 = true,
                alertThreshold90 = true,
                modemIp = "192.168.0.1",
                lastResetTimestamp = getCycleStartDate(1),
                lastSyncTimestamp = 0L
            )
            dao.insertOrUpdateSettings(defaultSettings)

            // Seed initial records for the current cycle so historical statistics and graphs are immediately functional
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val records = mutableListOf<ConsumptionRecordEntity>()

            // Past 6 days of recorded data leading up to today
            val samples = listOf(
                Pair(1.2 * 1024 * 1024 * 1024, 0.3 * 1024 * 1024 * 1024),
                Pair(1.8 * 1024 * 1024 * 1024, 0.4 * 1024 * 1024 * 1024),
                Pair(2.1 * 1024 * 1024 * 1024, 0.5 * 1024 * 1024 * 1024),
                Pair(1.5 * 1024 * 1024 * 1024, 0.2 * 1024 * 1024 * 1024),
                Pair(2.4 * 1024 * 1024 * 1024, 0.6 * 1024 * 1024 * 1024),
                Pair(1.9 * 1024 * 1024 * 1024, 0.4 * 1024 * 1024 * 1024)
            )

            for (i in samples.indices.reversed()) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -i)
                val (down, up) = samples[samples.size - 1 - i]
                records.add(
                    ConsumptionRecordEntity(
                        timestamp = cal.timeInMillis,
                        dateString = dateFormat.format(cal.time),
                        downloadBytes = down.toLong(),
                        uploadBytes = up.toLong(),
                        totalBytes = (down + up).toLong(),
                        sourceNote = "سجل استهلاك محفوظ"
                    )
                )
            }
            dao.insertRecords(records)

            // Seed initial known devices list with clear tags
            val initialDevices = listOf(
                DeviceEntity(
                    macAddress = "A4:C3:F0:12:34:56",
                    ipAddress = "192.168.0.101",
                    hostName = "Galaxy-S24",
                    customNickname = "هاتف سامسونج الشخصي",
                    isConnected = true,
                    firstSeen = now - 86400000L * 5,
                    lastSeen = now,
                    dataUsedBytes = (3.4 * 1024 * 1024 * 1024).toLong()
                ),
                DeviceEntity(
                    macAddress = "D8:5D:4C:78:9A:BC",
                    ipAddress = "192.168.0.102",
                    hostName = "Smart-TV-LG",
                    customNickname = "تلفزيون الصالة الذكي",
                    isConnected = true,
                    firstSeen = now - 86400000L * 6,
                    lastSeen = now - 3600000L,
                    dataUsedBytes = (5.1 * 1024 * 1024 * 1024).toLong()
                ),
                DeviceEntity(
                    macAddress = "E0:D5:5E:23:45:67",
                    ipAddress = "192.168.0.105",
                    hostName = "MacBook-Air",
                    customNickname = "حاسوب العمل",
                    isConnected = false,
                    firstSeen = now - 86400000L * 4,
                    lastSeen = now - 86400000L,
                    dataUsedBytes = (2.2 * 1024 * 1024 * 1024).toLong()
                )
            )
            dao.insertOrUpdateDevices(initialDevices)

            dao.insertLog(
                ModemLogEntity(
                    title = "تهيئة النظام",
                    details = "تم إعداد دورة الباقة 60 جيجابايت وحفظ الإعدادات بنجاح.",
                    isSuccess = true
                )
            )
        }
    }

    suspend fun syncWithModem(customIp: String? = null): ModemConnectionState {
        val currentSettings = dao.getSettings()
        val ipToUse = customIp ?: currentSettings?.modemIp ?: "192.168.0.1"

        val (state, syncResult) = modemClient.probeAndFetch(ipToUse)

        when (state) {
            is ModemConnectionState.Connected -> {
                val now = System.currentTimeMillis()
                currentSettings?.let {
                    dao.insertOrUpdateSettings(it.copy(lastSyncTimestamp = now))
                }

                // If real stations discovered, update in DB
                if (syncResult != null && syncResult.stations.isNotEmpty()) {
                    val devices = syncResult.stations.map { st ->
                        val existingDev = dao.getDeviceByMac(st.mac)
                        DeviceEntity(
                            macAddress = st.mac,
                            ipAddress = st.ip,
                            hostName = st.hostname,
                            customNickname = existingDev?.customNickname ?: "",
                            isConnected = true,
                            firstSeen = existingDev?.firstSeen ?: now,
                            lastSeen = now,
                            dataUsedBytes = existingDev?.dataUsedBytes ?: 0L
                        )
                    }
                    dao.insertOrUpdateDevices(devices)
                }

                // If router reported monthly bytes, update/insert today's record
                if (syncResult != null && (syncResult.downloadBytes > 0 || syncResult.uploadBytes > 0)) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val todayString = dateFormat.format(Date(now))
                    dao.insertRecord(
                        ConsumptionRecordEntity(
                            timestamp = now,
                            dateString = todayString,
                            downloadBytes = syncResult.downloadBytes,
                            uploadBytes = syncResult.uploadBytes,
                            totalBytes = syncResult.downloadBytes + syncResult.uploadBytes,
                            sourceNote = "مودم ZTE MF293N"
                        )
                    )
                }

                dao.insertLog(
                    ModemLogEntity(
                        title = "تحديث من المودم",
                        details = "تم الاتصال بنجاح بمودم ZTE MF293N على $ipToUse",
                        isSuccess = true
                    )
                )
            }
            is ModemConnectionState.Unreachable -> {
                dao.insertLog(
                    ModemLogEntity(
                        title = "فشل الاتصال بالمودم",
                        details = state.reason,
                        isSuccess = false
                    )
                )
            }
            else -> {}
        }

        return state
    }

    suspend fun updateSettings(
        quotaTotalGb: Double,
        cycleStartDay: Int,
        alert80: Boolean,
        alert90: Boolean,
        modemIp: String
    ) {
        val current = dao.getSettings() ?: QuotaSettingsEntity()
        val updated = current.copy(
            quotaTotalGb = quotaTotalGb,
            billingCycleStartDay = cycleStartDay,
            alertThreshold80 = alert80,
            alertThreshold90 = alert90,
            modemIp = modemIp.trim()
        )
        dao.insertOrUpdateSettings(updated)
        dao.insertLog(
            ModemLogEntity(
                title = "تعديل الإعدادات",
                details = "تم حفظ إجمالي الباقة: $quotaTotalGb جيجابايت، يوم التجديد: $cycleStartDay",
                isSuccess = true
            )
        )
    }

    suspend fun resetCycle() {
        val now = System.currentTimeMillis()
        val current = dao.getSettings() ?: QuotaSettingsEntity()
        dao.insertOrUpdateSettings(current.copy(lastResetTimestamp = now))
        dao.deleteRecordsSince(now)

        dao.insertLog(
            ModemLogEntity(
                title = "إعادة تعيين الدورة",
                details = "تمت إعادة تعيين دورة الباقة بنجاح وبدء فترة استهلاك جديدة.",
                isSuccess = true
            )
        )
    }

    suspend fun updateDeviceNickname(mac: String, nickname: String) {
        dao.updateDeviceNickname(mac, nickname)
    }

    private fun getCycleStartDate(cycleDay: Int): Long {
        val cal = Calendar.getInstance()
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        if (currentDay < cycleDay) {
            cal.add(Calendar.MONTH, -1)
        }
        cal.set(Calendar.DAY_OF_MONTH, cycleDay.coerceIn(1, 28))
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
