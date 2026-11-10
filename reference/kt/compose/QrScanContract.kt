package com.omnibox.feature.qrcode

import android.net.Uri

sealed interface QrScanUiState {
    // 1. 权限检查或初始加载中
    data object Loading : QrScanUiState

    // 2. 权限未授予或被拒绝状态（含降级方案）
    data class PermissionDenied(
        val isPermanentlyDenied: Boolean = false,
        val shouldShowRationale: Boolean = true
    ) : QrScanUiState

    // 3. 正常扫码活跃状态
    data class Scanning(
        val isTorchOn: Boolean = false,
        val zoomRatio: Float = 1.0f,
        val maxZoomRatio: Float = 5.0f,
        val scanResult: ScanResult? = null,
        val isAnalyzing: Boolean = true
    ) : QrScanUiState
}

data class ScanResult(
    val content: String,
    val format: String,
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface QrScanUiEvent {
    // 权限相关
    data class OnPermissionResult(val isGranted: Boolean, val shouldShowRationale: Boolean) : QrScanUiEvent
    data object RequestPermission : QrScanUiEvent
    data object OpenAppSettings : QrScanUiEvent

    // 扫码控制
    data class OnBarcodeDetected(val barcode: String, val format: String) : QrScanUiEvent
    data object ToggleTorch : QrScanUiEvent
    data class UpdateZoom(val zoom: Float) : QrScanUiEvent
    data object ResumeScanning : QrScanUiEvent

    // 降级操作
    data class PickImageFromGallery(val uri: Uri) : QrScanUiEvent
    data object SwitchToGenerateMode : QrScanUiEvent
    data object CopyResult : QrScanUiEvent
}

sealed interface QrScanUiEffect {
    data class ShowSnackbar(val message: String) : QrScanUiEffect
    data class NavigateToGenerator(val prefillContent: String? = null) : QrScanUiEffect
    data object OpenSettingsScreen : QrScanUiEffect
    data class CopyToClipboard(val text: String) : QrScanUiEffect
    data object TriggerHapticFeedback : QrScanUiEffect
}
