package com.omnibox.feature.qrcode

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class QrScanViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<QrScanUiState>(QrScanUiState.Loading)
    val uiState: StateFlow<QrScanUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<QrScanUiEffect>(Channel.BUFFERED)
    val uiEffect: Flow<QrScanUiEffect> = _uiEffect.receiveAsFlow()

    fun handleEvent(event: QrScanUiEvent) {
        when (event) {
            is QrScanUiEvent.OnPermissionResult -> handlePermissionResult(event.isGranted, event.shouldShowRationale)
            is QrScanUiEvent.OnBarcodeDetected -> handleBarcodeDetected(event.barcode, event.format)
            is QrScanUiEvent.ToggleTorch -> toggleTorch()
            is QrScanUiEvent.UpdateZoom -> updateZoom(event.zoom)
            is QrScanUiEvent.ResumeScanning -> resumeScanning()
            is QrScanUiEvent.PickImageFromGallery -> parseImageFromUri(event.uri)
            is QrScanUiEvent.SwitchToGenerateMode -> {
                viewModelScope.launch { _uiEffect.send(QrScanUiEffect.NavigateToGenerator()) }
            }
            is QrScanUiEvent.OpenAppSettings -> {
                viewModelScope.launch { _uiEffect.send(QrScanUiEffect.OpenSettingsScreen) }
            }
            is QrScanUiEvent.CopyResult -> copyCurrentResult()
            is QrScanUiEvent.RequestPermission -> {
                // 由 UI 侧触发 rememberLauncherForActivityResult
            }
        }
    }

    private fun handlePermissionResult(isGranted: Boolean, shouldShowRationale: Boolean) {
        if (isGranted) {
            _uiState.value = QrScanUiState.Scanning()
        } else {
            // shouldShowRationale 为 false 且未授予权限时，往往代表已被永久拒绝（点击了不再询问）
            _uiState.value = QrScanUiState.PermissionDenied(
                isPermanentlyDenied = !shouldShowRationale,
                shouldShowRationale = shouldShowRationale
            )
        }
    }

    private fun handleBarcodeDetected(barcode: String, format: String) {
        val currentState = _uiState.value
        if (currentState is QrScanUiState.Scanning && currentState.isAnalyzing) {
            _uiState.value = currentState.copy(
                isAnalyzing = false,
                scanResult = ScanResult(content = barcode, format = format)
            )
            viewModelScope.launch {
                _uiEffect.send(QrScanUiEffect.TriggerHapticFeedback)
            }
        }
    }

    private fun toggleTorch() {
        val currentState = _uiState.value
        if (currentState is QrScanUiState.Scanning) {
            _uiState.value = currentState.copy(isTorchOn = !currentState.isTorchOn)
        }
    }

    private fun updateZoom(zoom: Float) {
        val currentState = _uiState.value
        if (currentState is QrScanUiState.Scanning) {
            val clamped = zoom.coerceIn(1.0f, currentState.maxZoomRatio)
            _uiState.value = currentState.copy(zoomRatio = clamped)
        }
    }

    private fun resumeScanning() {
        val currentState = _uiState.value
        if (currentState is QrScanUiState.Scanning) {
            _uiState.value = currentState.copy(
                isAnalyzing = true,
                scanResult = null
            )
        }
    }

    private fun copyCurrentResult() {
        val currentState = _uiState.value
        if (currentState is QrScanUiState.Scanning && currentState.scanResult != null) {
            viewModelScope.launch {
                _uiEffect.send(QrScanUiEffect.CopyToClipboard(currentState.scanResult.content))
                _uiEffect.send(QrScanUiEffect.ShowSnackbar("已复制到剪贴板"))
            }
        }
    }

    /**
     * 降级方案：从相册选取二维码，进行纯本地离线解析，无相机权限也可用
     */
    private fun parseImageFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap == null) {
                    _uiEffect.send(QrScanUiEffect.ShowSnackbar("图片解析失败"))
                    return@launch
                }

                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                val source = RGBLuminanceSource(width, height, pixels)
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                val reader = MultiFormatReader()
                val result = reader.decode(binaryBitmap)

                withContext(Dispatchers.Main) {
                    _uiEffect.send(QrScanUiEffect.TriggerHapticFeedback)
                    _uiState.value = QrScanUiState.Scanning(
                        isAnalyzing = false,
                        scanResult = ScanResult(content = result.text, format = result.barcodeFormat.name)
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiEffect.send(QrScanUiEffect.ShowSnackbar("未在选中图片中识别出有效二维码"))
                }
            }
        }
    }
}
