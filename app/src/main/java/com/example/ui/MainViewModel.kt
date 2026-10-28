package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ConsumptionRecordEntity
import com.example.data.local.DeviceEntity
import com.example.data.local.ModemLogEntity
import com.example.data.local.QuotaSettingsEntity
import com.example.data.modem.ModemConnectionState
import com.example.data.repository.SmartMonitorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class AppScreen(val titleArabic: String) {
    HOME("الرئيسية"),
    DEVICES("الأجهزة"),
    STATS("الإحصائيات"),
    SETTINGS("الإعدادات")
}

data class MonitorUiState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val settings: QuotaSettingsEntity = QuotaSettingsEntity(),
    val totalQuotaBytes: Long = (60.0 * 1024 * 1024 * 1024).toLong(),
    val usedBytes: Long = 0L,
    val remainingBytes: Long = (60.0 * 1024 * 1024 * 1024).toLong(),
    val consumptionPercent: Float = 0f,
    val dailyAverageBytes: Long = 0L,
    val forecastDaysRemaining: Int = 30,
    val forecastDepletionDate: String = "غير محدد",
    val downloadSpeedKbps: Long = 0L,
    val uploadSpeedKbps: Long = 0L,
    val connectedDeviceCount: Int = 0,
    val connectionState: ModemConnectionState = ModemConnectionState.Idle,
    val devices: List<DeviceEntity> = emptyList(),
    val records: List<ConsumptionRecordEntity> = emptyList(),
    val logs: List<ModemLogEntity> = emptyList(),
    val isRefreshing: Boolean = false,
    val isTestingConnection: Boolean = false,
    val userNotificationMessage: String? = null,
    val testResultMessage: String? = null
)

private data class RepositorySnapshot(
    val settings: QuotaSettingsEntity,
    val records: List<ConsumptionRecordEntity>,
    val devices: List<DeviceEntity>,
    val logs: List<ModemLogEntity>
)

private data class DialogMessages(
    val userMessage: String?,
    val testResult: String?
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SmartMonitorRepository

    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    private val _connectionState = MutableStateFlow<ModemConnectionState>(ModemConnectionState.Idle)
    private val _isRefreshing = MutableStateFlow(false)
    private val _isTesting = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _testResult = MutableStateFlow<String?>(null)

    init {
        val db = AppDatabase.getDatabase(application)
        repository = SmartMonitorRepository(db.monitorDao())
        viewModelScope.launch {
            repository.initializeDefaultsIfNeeded()
            refreshData(isInitial = true)
        }
    }

    private val repoSnapshotFlow = combine(
        repository.settingsFlow,
        repository.allRecordsFlow,
        repository.devicesFlow,
        repository.logsFlow
    ) { settingsOrNull, records, devices, logs ->
        RepositorySnapshot(
            settings = settingsOrNull ?: QuotaSettingsEntity(),
            records = records,
            devices = devices,
            logs = logs
        )
    }

    private val messagesFlow = combine(_userMessage, _testResult) { msg, testRes ->
        DialogMessages(userMessage = msg, testResult = testRes)
    }

    val uiState: StateFlow<MonitorUiState> = combine(
        repoSnapshotFlow,
        _currentScreen,
        _connectionState,
        combine(_isRefreshing, _isTesting, messagesFlow) { refreshing, testing, msgs ->
            Triple(refreshing, testing, msgs)
        }
    ) { repo, screen, connState, (refreshing, testing, msgs) ->
        val settings = repo.settings
        val totalBytes = (settings.quotaTotalGb * 1024 * 1024 * 1024).toLong()

        // Calculate usage since last reset
        val resetTime = settings.lastResetTimestamp
        val cycleRecords = repo.records.filter { it.timestamp >= resetTime }
        val used = cycleRecords.sumOf { it.totalBytes }
        val remaining = (totalBytes - used).coerceAtLeast(0L)
        val percent = if (totalBytes > 0) ((used.toFloat() / totalBytes) * 100f).coerceIn(0f, 100f) else 0f

        // Calculate daily average with Long coercion
        val diffMs = System.currentTimeMillis() - resetTime
        val daysElapsed = (diffMs / (1000L * 60L * 60L * 24L)).coerceAtLeast(1L)
        val dailyAvg = used / daysElapsed

        // Forecast days remaining
        val daysRemaining = if (dailyAvg > 0) (remaining / dailyAvg).toInt().coerceAtLeast(0) else 30

        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, daysRemaining)
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("ar"))
        val forecastDateStr = dateFormat.format(cal.time)

        // Speed from connection state
        var downKbps = 0L
        var upKbps = 0L
        if (connState is ModemConnectionState.Connected) {
            downKbps = connState.currentDownloadKbps
            upKbps = connState.currentUploadKbps
        }

        val connectedCount = repo.devices.count { it.isConnected }

        MonitorUiState(
            currentScreen = screen,
            settings = settings,
            totalQuotaBytes = totalBytes,
            usedBytes = used,
            remainingBytes = remaining,
            consumptionPercent = percent,
            dailyAverageBytes = dailyAvg,
            forecastDaysRemaining = daysRemaining,
            forecastDepletionDate = forecastDateStr,
            downloadSpeedKbps = downKbps,
            uploadSpeedKbps = upKbps,
            connectedDeviceCount = connectedCount,
            connectionState = connState,
            devices = repo.devices,
            records = repo.records,
            logs = repo.logs,
            isRefreshing = refreshing,
            isTestingConnection = testing,
            userNotificationMessage = msgs.userMessage,
            testResultMessage = msgs.testResult
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MonitorUiState()
    )

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun refreshData(isInitial: Boolean = false) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _connectionState.value = ModemConnectionState.Checking
            val result = repository.syncWithModem()
            _connectionState.value = result
            _isRefreshing.value = false

            if (!isInitial) {
                when (result) {
                    is ModemConnectionState.Connected -> {
                        _userMessage.value = "تم تحديث البيانات من مودم ZTE MF293N بنجاح"
                    }
                    is ModemConnectionState.Unreachable -> {
                        _userMessage.value = "المودم غير متصل، يتم عرض البيانات المحفوظة محليًا"
                    }
                    else -> {}
                }
            }
        }
    }

    fun testConnection(ip: String) {
        viewModelScope.launch {
            _isTesting.value = true
            val result = repository.syncWithModem(customIp = ip)
            _connectionState.value = result
            _isTesting.value = false

            _testResult.value = when (result) {
                is ModemConnectionState.Connected -> {
                    "تم الاتصال بنجاح بمودم ZTE MF293N على $ip.\nحالة المودم: متصل وجاهز."
                }
                is ModemConnectionState.Unreachable -> {
                    "تعذر الاتصال بالمودم على $ip.\nتأكد من الاتصال بشبكة Wi-Fi الخاصة بمودم ZTE MF293N وصحة عنوان IP."
                }
                is ModemConnectionState.AuthRequired -> {
                    "تم العثور على المودم على $ip، ويتطلب تسجيل دخول المسؤول."
                }
                else -> "اكتمل الاختبار."
            }
        }
    }

    fun saveSettings(
        quotaGb: Double,
        cycleDay: Int,
        alert80: Boolean,
        alert90: Boolean,
        modemIp: String
    ) {
        viewModelScope.launch {
            repository.updateSettings(
                quotaTotalGb = quotaGb,
                cycleStartDay = cycleDay,
                alert80 = alert80,
                alert90 = alert90,
                modemIp = modemIp
            )
            _userMessage.value = "تم حفظ الإعدادات بنجاح"
        }
    }

    fun resetCycle() {
        viewModelScope.launch {
            repository.resetCycle()
            _userMessage.value = "تمت إعادة تعيين دورة الباقة بنجاح"
        }
    }

    fun updateDeviceNickname(mac: String, nickname: String) {
        viewModelScope.launch {
            repository.updateDeviceNickname(mac, nickname.trim())
            _userMessage.value = "تم تحديث اسم الجهاز"
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun clearTestResult() {
        _testResult.value = null
    }
}
