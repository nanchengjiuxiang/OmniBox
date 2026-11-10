package com.omnibox.feature.compass

import android.content.Context
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompassUiState(
    val hasMagnetometer: Boolean = false,
    val locationGranted: Boolean = false,
    val shouldShowRationale: Boolean = false,
    val permanentlyDenied: Boolean = false,
    val azimuthDeg: Float? = null,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val pitchOffset: Float = 0f,
    val rollOffset: Float = 0f,
    val fieldStrengthUt: Float? = null,
    val magneticAccuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE,
    val direction: DirectionName? = null,
    val trueNorth: TrueNorthInfo? = null,
    val trueNorthAzimuth: Float? = null,
    val solarEstimateDeg: Double = 0.0,
    val message: String? = null
)

sealed interface CompassEvent {
    data object StartSensors : CompassEvent
    data object StopSensors : CompassEvent
    data class LocationPermissionResult(
        val granted: Boolean,
        val shouldShowRationale: Boolean,
        val permanentlyDenied: Boolean
    ) : CompassEvent
    data object CalibrateLevel : CompassEvent
    data object RescanSensors : CompassEvent
    data class ShowMessage(val text: String) : CompassEvent
    data object ClearMessage : CompassEvent
}

@HiltViewModel
class CompassViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val engine = CompassEngine(context)

    private val _uiState = MutableStateFlow(
        CompassUiState(
            hasMagnetometer = engine.hasMagnetometer,
            solarEstimateDeg = SolarAzimuth.estimateDeg()
        )
    )
    val uiState: StateFlow<CompassUiState> = _uiState.asStateFlow()

    init {
        // 传感器读数在后台 HandlerThread 产生，StateFlow 线程安全地汇入状态。
        viewModelScope.launch {
            engine.readings.collect { reading ->
                if (reading == null) return@collect
                _uiState.update { s ->
                    val az = reading.azimuthDeg
                    s.copy(
                        azimuthDeg = az,
                        pitchDeg = reading.pitchDeg,
                        rollDeg = reading.rollDeg,
                        fieldStrengthUt = reading.fieldStrengthUt,
                        magneticAccuracy = reading.magneticAccuracy,
                        direction = az?.let { directionOf(it) },
                        trueNorthAzimuth = if (az != null && s.trueNorth != null)
                            normalizeDeg(az + s.trueNorth.declinationDeg) else null
                    )
                }
            }
        }
        // 太阳方位角估算随本地时间刷新，始终标注为估算。
        viewModelScope.launch {
            while (true) {
                _uiState.update { it.copy(solarEstimateDeg = SolarAzimuth.estimateDeg()) }
                delay(30_000)
            }
        }
    }

    fun onEvent(event: CompassEvent) {
        when (event) {
            CompassEvent.StartSensors -> engine.register()
            CompassEvent.StopSensors -> engine.unregister()
            is CompassEvent.LocationPermissionResult -> {
                _uiState.update {
                    it.copy(
                        locationGranted = event.granted,
                        shouldShowRationale = event.shouldShowRationale,
                        permanentlyDenied = event.permanentlyDenied
                    )
                }
                if (event.granted) {
                    resolveTrueNorth()
                } else {
                    _uiState.update { it.copy(trueNorth = null, trueNorthAzimuth = null) }
                }
            }
            CompassEvent.CalibrateLevel -> _uiState.update {
                it.copy(pitchOffset = it.pitchDeg, rollOffset = it.rollDeg)
            }
            CompassEvent.RescanSensors -> {
                val found = engine.rescan()
                engine.unregister()
                engine.register()
                _uiState.update {
                    it.copy(
                        hasMagnetometer = found,
                        message = if (found) "已重新扫描：检测到地磁计" else "已重新扫描：仍未检测到地磁计"
                    )
                }
            }
            is CompassEvent.ShowMessage -> _uiState.update { it.copy(message = event.text) }
            CompassEvent.ClearMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    /** 真实磁偏角计算放 IO；无权限 / 无缓存时保持 null，由 UI 明确显示「仅磁北可用」。 */
    private fun resolveTrueNorth() {
        viewModelScope.launch(Dispatchers.IO) {
            val info = engine.computeTrueNorth()
            _uiState.update { s ->
                s.copy(
                    trueNorth = info,
                    trueNorthAzimuth = if (info != null && s.azimuthDeg != null)
                        normalizeDeg(s.azimuthDeg + info.declinationDeg) else null
                )
            }
            if (info == null) {
                _uiState.update { it.copy(message = "暂无定位缓存 · 真北校正未启用，仅磁北可用") }
            }
        }
    }

    override fun onCleared() {
        engine.unregister()
        super.onCleared()
    }
}
