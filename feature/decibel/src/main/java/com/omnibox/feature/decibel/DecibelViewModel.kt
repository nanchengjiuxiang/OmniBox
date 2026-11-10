package com.omnibox.feature.decibel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DecibelUiState(
    val permissionGranted: Boolean = false,
    val shouldShowRationale: Boolean = false,
    val permanentlyDenied: Boolean = false,
    val isRecording: Boolean = false,
    val calibrationOffsetDb: Float = 0f,
    val instantDb: Double = 0.0,
    val dbfs: Double = 0.0,
    val rms: Double = 0.0,
    val metrics: DecibelMetrics = DecibelMetrics(),
    val level: NoiseLevel? = null,
    val message: String? = null,
    val error: String? = null
)

sealed interface DecibelEvent {
    data class PermissionResult(
        val granted: Boolean,
        val shouldShowRationale: Boolean,
        val permanentlyDenied: Boolean
    ) : DecibelEvent
    data object Start : DecibelEvent
    data object Pause : DecibelEvent
    data object ResetPeak : DecibelEvent
    data class CalibrationChanged(val value: Float) : DecibelEvent
    data class ShowMessage(val text: String) : DecibelEvent
    data object ClearMessage : DecibelEvent
    data object ClearError : DecibelEvent
}

@HiltViewModel
class DecibelViewModel @Inject constructor() : ViewModel() {

    private val engine = DecibelEngine()
    private val accumulator = DecibelAccumulator()
    private var recordJob: Job? = null

    /** 跨线程重置信号：累积器只在 IO 采样协程内被访问，避免并发破坏。 */
    @Volatile
    private var resetRequested = false

    private val _uiState = MutableStateFlow(DecibelUiState())
    val uiState: StateFlow<DecibelUiState> = _uiState.asStateFlow()

    fun onEvent(event: DecibelEvent) {
        when (event) {
            is DecibelEvent.PermissionResult -> {
                _uiState.update {
                    it.copy(
                        permissionGranted = event.granted,
                        shouldShowRationale = event.shouldShowRationale,
                        permanentlyDenied = event.permanentlyDenied,
                        error = null
                    )
                }
                if (event.granted) startRecording() else stopRecording()
            }
            DecibelEvent.Start -> startRecording()
            DecibelEvent.Pause -> stopRecording()
            DecibelEvent.ResetPeak -> {
                resetRequested = true
                _uiState.update {
                    it.copy(metrics = DecibelMetrics(), level = null, instantDb = 0.0)
                }
            }
            is DecibelEvent.CalibrationChanged -> _uiState.update {
                it.copy(
                    calibrationOffsetDb = event.value.coerceIn(
                        DecibelMath.CALIBRATION_MIN.toFloat(),
                        DecibelMath.CALIBRATION_MAX.toFloat()
                    )
                )
            }
            is DecibelEvent.ShowMessage -> _uiState.update { it.copy(message = event.text) }
            DecibelEvent.ClearMessage -> _uiState.update { it.copy(message = null) }
            DecibelEvent.ClearError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun startRecording() {
        if (recordJob?.isActive == true) return
        if (!_uiState.value.permissionGranted) {
            _uiState.update { it.copy(isRecording = false, message = "需要先授予麦克风权限") }
            return
        }
        _uiState.update { it.copy(isRecording = true, error = null) }
        recordJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                engine.record(
                    calibration = { _uiState.value.calibrationOffsetDb.toDouble() },
                    onFrame = { frame ->
                        if (resetRequested) {
                            accumulator.reset()
                            resetRequested = false
                        }
                        accumulator.add(frame.spl)
                        _uiState.update {
                            it.copy(
                                instantDb = frame.spl,
                                dbfs = frame.dbfs,
                                rms = frame.rms,
                                metrics = accumulator.snapshot(),
                                level = noiseLevelOf(frame.spl)
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isRecording = false, error = e.message ?: "麦克风采样启动失败")
                }
            }
        }
    }

    private fun stopRecording() {
        engine.stop()
        recordJob?.cancel()
        recordJob = null
        _uiState.update { it.copy(isRecording = false) }
    }

    override fun onCleared() {
        engine.stop()
        recordJob?.cancel()
        super.onCleared()
    }
}
