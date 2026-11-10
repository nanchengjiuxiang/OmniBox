package com.omnibox.feature.displaylab

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibox.core.data.preferences.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DisplayLabUiState(
    val specs: DisplaySpecs? = null,
    /** KEEP_SCREEN_ON 全局键在此页实时生效（此前是无人消费的死键）。 */
    val keepScreenOn: Boolean = false,
    val message: String? = null
)

sealed interface DisplayLabEvent {
    data object Refresh : DisplayLabEvent
    data class KeepScreenOnChanged(val value: Boolean) : DisplayLabEvent
    data class ShowMessage(val text: String) : DisplayLabEvent
    data object ClearMessage : DisplayLabEvent
}

@HiltViewModel
class DisplayLabViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val preferences: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DisplayLabUiState())
    val uiState: StateFlow<DisplayLabUiState> = _uiState.asStateFlow()

    val keepScreenOnPref: StateFlow<Boolean> = preferences.keepScreenOn
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        refresh()
        viewModelScope.launch {
            preferences.keepScreenOn.collect { enabled ->
                _uiState.update { it.copy(keepScreenOn = enabled) }
            }
        }
    }

    fun onEvent(event: DisplayLabEvent) {
        when (event) {
            DisplayLabEvent.Refresh -> refresh()
            is DisplayLabEvent.KeepScreenOnChanged -> viewModelScope.launch {
                preferences.setKeepScreenOn(event.value)
            }
            is DisplayLabEvent.ShowMessage -> _uiState.update { it.copy(message = event.text) }
            DisplayLabEvent.ClearMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun refresh() {
        val display = appContext
            .getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display == null) {
            _uiState.update { it.copy(specs = null, message = "未能获取默认显示屏") }
            return
        }
        val mode = display.mode
        val modes = display.supportedModes
        val refreshRates = modes.map { it.refreshRate }.distinct().sorted()
        // API 名称是 getSupportedHdrTypes()（Kotlin 属性访问为 supportedHdrTypes）
        val hdr = runCatching { display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0) }
            .getOrDefault(IntArray(0))
        val metrics = appContext.resources.displayMetrics
        val brightness = readBrightnessFraction()
        val adaptive = runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == 1
        }.getOrDefault(false)

        val specs = DisplaySpecs(
            model = Build.MODEL ?: Build.DEVICE ?: "Android 设备",
            widthPx = mode.physicalWidth,
            heightPx = mode.physicalHeight,
            physicalXDpi = metrics.xdpi,
            physicalYDpi = metrics.ydpi,
            refreshRateHz = mode.refreshRate,
            supportedRefreshRates = refreshRates,
            hdrTypes = hdr.toList(),
            wideColorGamut = display.isWideColorGamut,
            brightnessFraction = brightness,
            adaptiveBrightness = adaptive,
            modeCount = modes.size
        )
        _uiState.update { it.copy(specs = specs) }
    }

    /**
     * 读 0..255 的 SCREEN_BRIGHTNESS 并归一化为 0..1。
     * 注：SCREEN_BRIGHTNESS_FLOAT 常量在 compileSdk 35 的公开 SDK 中不存在，统一走 int 通道。
     * 读取失败（部分 ROM 拒绝）返回 null，界面显示「—」，不造默认值。
     */
    private fun readBrightnessFraction(): Float? = runCatching {
        val v = Settings.System.getInt(
            appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1
        )
        if (v >= 0) v / 255f else null
    }.getOrNull()
}

/** Activity 窗口是否保持常亮 —— 由页面按设置键实时套用/撤销 FLAG_KEEP_SCREEN_ON。 */
fun Activity.applyKeepScreenOn(enabled: Boolean) {
    if (enabled) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}
