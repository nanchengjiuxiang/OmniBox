package com.omnibox.feature.imagecompress

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

@HiltViewModel
class ImageCompressViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val engine = ImageCompressEngine(application)

    private val _uiState = MutableStateFlow<ImageCompressUiState>(ImageCompressUiState.Empty)
    val uiState: StateFlow<ImageCompressUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<ImageCompressUiEffect>(Channel.BUFFERED)
    val uiEffect: Flow<ImageCompressUiEffect> = _uiEffect.receiveAsFlow()

    private var compressJob: Job? = null
    private var lastTempFile: File? = null

    fun handleEvent(event: ImageCompressUiEvent) {
        when (event) {
            is ImageCompressUiEvent.PickImage -> onImageSelected(event.uri)
            is ImageCompressUiEvent.UpdateQuality -> updateConfig { it.copy(quality = event.quality) }
            is ImageCompressUiEvent.UpdateFormat -> updateConfig { it.copy(format = event.format) }
            is ImageCompressUiEvent.UpdateResolution -> updateConfig { it.copy(resolutionScale = event.scale) }
            is ImageCompressUiEvent.ToggleAutoInSampleSize -> updateConfig { it.copy(autoInSampleSize = event.enabled) }
            is ImageCompressUiEvent.ToggleStripGps -> updateConfig { it.copy(stripGpsLocation = event.enabled) }
            is ImageCompressUiEvent.ToggleKeepExif -> updateConfig { it.copy(keepPhotographyExif = event.enabled) }
            is ImageCompressUiEvent.TogglePreserveP3 -> updateConfig { it.copy(preserveDisplayP3Color = event.enabled) }
            is ImageCompressUiEvent.SaveToGallery -> saveToSystemGallery()
            is ImageCompressUiEvent.ShareImage -> shareCompressedImage()
            is ImageCompressUiEvent.Reset -> resetState()
        }
    }

    private fun onImageSelected(uri: Uri) {
        _uiState.value = ImageCompressUiState.Ready(
            sourceUri = uri,
            config = CompressConfig()
        )
        triggerCompression(uri, CompressConfig())
    }

    private fun updateConfig(transform: (CompressConfig) -> CompressConfig) {
        val currentState = _uiState.value
        if (currentState is ImageCompressUiState.Ready) {
            val newConfig = transform(currentState.config)
            _uiState.value = currentState.copy(config = newConfig)
            triggerCompression(currentState.sourceUri, newConfig)
        }
    }

    private fun triggerCompression(sourceUri: Uri, config: CompressConfig) {
        compressJob?.cancel()
        compressJob = viewModelScope.launch {
            val result = engine.compress(sourceUri, config)
            result.onSuccess { (file, metrics) ->
                lastTempFile?.delete()
                lastTempFile = file
                val fileUri = Uri.fromFile(file)

                val state = _uiState.value
                if (state is ImageCompressUiState.Ready) {
                    _uiState.value = state.copy(
                        compressedUri = fileUri,
                        metrics = metrics
                    )
                }
                _uiEffect.send(ImageCompressUiEffect.TriggerHapticFeedback(isStrong = false))
            }.onFailure { error ->
                _uiEffect.send(ImageCompressUiEffect.ShowSnackbar("压缩失败: ${error.localizedMessage}"))
            }
        }
    }

    private fun saveToSystemGallery() {
        val tempFile = lastTempFile ?: return
        val currentState = _uiState.value as? ImageCompressUiState.Ready ?: return

        viewModelScope.launch {
            _uiState.value = currentState.copy(isSaving = true)
            try {
                val context = getApplication<Application>()
                val extension = currentState.config.format.extension
                val mimeType = currentState.config.format.mimeType
                val filename = "OmniBox_Compress_${System.currentTimeMillis()}.$extension"

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OmniBox")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IllegalStateException("无法创建系统相册条目")

                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(tempFile).use { input ->
                        input.copyTo(out)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                _uiEffect.send(ImageCompressUiEffect.TriggerHapticFeedback(isStrong = true))
                _uiEffect.send(ImageCompressUiEffect.ShowSnackbar("已成功保存至系统相册 (Pictures/OmniBox)"))
            } catch (e: Exception) {
                _uiEffect.send(ImageCompressUiEffect.ShowSnackbar("保存失败: ${e.localizedMessage}"))
            } finally {
                val updated = _uiState.value
                if (updated is ImageCompressUiState.Ready) {
                    _uiState.value = updated.copy(isSaving = false)
                }
            }
        }
    }

    private fun shareCompressedImage() {
        val file = lastTempFile ?: return
        val currentState = _uiState.value as? ImageCompressUiState.Ready ?: return
        val context = getApplication<Application>()
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            viewModelScope.launch {
                _uiEffect.send(ImageCompressUiEffect.ShareIntent(contentUri, currentState.config.format.mimeType))
            }
        } catch (e: Exception) {
            viewModelScope.launch {
                _uiEffect.send(ImageCompressUiEffect.ShowSnackbar("无法调起系统分享"))
            }
        }
    }

    private fun resetState() {
        lastTempFile?.delete()
        lastTempFile = null
        _uiState.value = ImageCompressUiState.Empty
    }

    override fun onCleared() {
        super.onCleared()
        lastTempFile?.delete()
    }
}
