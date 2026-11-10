package com.omnibox.feature.battery

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibox.core.data.preferences.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BatteryUiState(
    val snapshot: BatterySnapshot? = null,
    /** 会话内 1Hz 采样序列（最长 15 分钟环形）。 */
    val samples: List<PowerSample> = emptyList(),
    val peakW: Double? = null,
    val meanW: Double? = null,
    val tempSlope: Float? = null,
    val targetAlert: Boolean = false,
    val targetLevel: Int = 80,
    val tempAlert: Boolean = false,
    val tempThresholdC: Int = 40,
    val fullVibrate: Boolean = false,
    val message: String? = null
)

sealed interface BatteryEvent {
    data object Refresh : BatteryEvent
    data object RereadGauge : BatteryEvent
    data class TargetAlertChanged(val value: Boolean) : BatteryEvent
    data class TargetLevelChanged(val value: Int) : BatteryEvent
    data class TempAlertChanged(val value: Boolean) : BatteryEvent
    data class TempThresholdChanged(val value: Int) : BatteryEvent
    data class FullVibrateChanged(val value: Boolean) : BatteryEvent
    data class ExportLog(val uri: Uri) : BatteryEvent
    data class ShowMessage(val text: String) : BatteryEvent
    data object ClearMessage : BatteryEvent
}

@HiltViewModel
class BatteryViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: BatteryRepository,
    private val preferences: PreferencesRepository
) : ViewModel() {

    private val history = PowerHistory()

    private val _uiState = MutableStateFlow(BatteryUiState())
    val uiState: StateFlow<BatteryUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        tick()
        startPolling()
        viewModelScope.launch {
            preferences.batteryTargetAlert.collect { v -> _uiState.update { it.copy(targetAlert = v) } }
        }
        viewModelScope.launch {
            preferences.batteryTargetLevel.collect { v -> _uiState.update { it.copy(targetLevel = v) } }
        }
        viewModelScope.launch {
            preferences.batteryTempAlert.collect { v -> _uiState.update { it.copy(tempAlert = v) } }
        }
        viewModelScope.launch {
            preferences.batteryTempThreshold.collect { v -> _uiState.update { it.copy(tempThresholdC = v) } }
        }
        viewModelScope.launch {
            preferences.batteryFullVibrate.collect { v -> _uiState.update { it.copy(fullVibrate = v) } }
        }
    }

    fun onEvent(event: BatteryEvent) {
        when (event) {
            BatteryEvent.Refresh -> tick()
            BatteryEvent.RereadGauge -> {
                tick()
                _uiState.update {
                    it.copy(
                        message = it.snapshot?.let { s ->
                            "已重读：健康 ${s.health.label} · 循环 ${s.cycleCount ?: "未上报"} · " +
                                "满充容量 ${formatMilliAmpHours(s.chargeFullMicroAh)}"
                        } ?: "已重读电量计"
                    )
                }
            }
            is BatteryEvent.TargetAlertChanged -> viewModelScope.launch {
                preferences.setBatteryTargetAlert(event.value)
                syncGuardService()
            }
            is BatteryEvent.TargetLevelChanged -> viewModelScope.launch {
                preferences.setBatteryTargetLevel(event.value)
            }
            is BatteryEvent.TempAlertChanged -> viewModelScope.launch {
                preferences.setBatteryTempAlert(event.value)
                syncGuardService()
            }
            is BatteryEvent.TempThresholdChanged -> viewModelScope.launch {
                preferences.setBatteryTempThreshold(event.value)
            }
            is BatteryEvent.FullVibrateChanged -> viewModelScope.launch {
                preferences.setBatteryFullVibrate(event.value)
                syncGuardService()
            }
            is BatteryEvent.ExportLog -> exportLog(event.uri)
            is BatteryEvent.ShowMessage -> _uiState.update { it.copy(message = event.text) }
            BatteryEvent.ClearMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun tick() {
        val snapshot = repository.current()
        history.add(
            PowerSample(
                timestampMs = snapshot.timestampMs,
                levelPercent = snapshot.levelPercent,
                voltageMv = snapshot.voltageMv,
                currentMicroA = snapshot.currentMicroA,
                temperatureC = if (snapshot.temperatureC.isNaN()) 0f else snapshot.temperatureC
            )
        )
        _uiState.update {
            it.copy(
                snapshot = snapshot,
                samples = history.asList(),
                peakW = history.peakW,
                meanW = history.meanW,
                tempSlope = history.temperatureSlopePerMinute()
            )
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        // sysfs 读文件 + 粘性广播取值，放到 Default 线程，避免每秒在主线程做 IO
        pollJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                tick()
            }
        }
    }

    /** 任一提醒开启则拉起守护服务（同时让服务读到最新阈值），全部关闭则停止。 */
    private suspend fun syncGuardService() {
        val s = _uiState.value
        val any = s.targetAlert || s.tempAlert || s.fullVibrate
        withContext(Dispatchers.Main) {
            BatteryGuardService.sync(appContext, any)
        }
    }

    private fun exportLog(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val csv = history.toCsv()
            val ok = runCatching {
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                } != null
            }.getOrDefault(false)
            _uiState.update {
                it.copy(
                    message = if (ok) "已导出 ${history.size} 条采样记录" else "导出失败，请重试"
                )
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}
