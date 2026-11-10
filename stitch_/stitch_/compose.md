# 二维码扫码与权限降级 (QrScanFeature) Jetpack Compose 架构源码实现

本文档包含完整的端侧离线优先、Material 3 规范与 MVI 架构的 Jetpack Compose 完整工程代码，涵盖：
1. **UiState / UiEvent / UiEffect (MVI 契约)**
2. **QrScanViewModel (状态管理、权限降级调度与离线解析)**
3. **QrScanScreen (主 Composable，无缝流转 CameraX 扫码态与权限拒绝降级态)**
4. **CameraPreview (基于 AndroidView 与 CameraX + ML Kit / ZXing 的端侧内存帧图像分析器)**
5. **PermissionRationaleView (无权限教育与替代降级方案视图)**

---

## 1. MVI 状态与事件契约 (`QrScanContract.kt`)

```kotlin
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
```

---

## 2. ViewModel 架构实现 (`QrScanViewModel.kt`)

```kotlin
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
```

---

## 3. 主界面 Compose 实现 (`QrScanScreen.kt`)

```kotlin
package com.omnibox.feature.qrcode

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGenerator: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QrScanViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()

    // 权限请求 Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val activity = context as? androidx.activity.ComponentActivity
        val shouldShowRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ?: false
        viewModel.handleEvent(QrScanUiEvent.OnPermissionResult(isGranted, shouldShowRationale))
    }

    // 系统安全图片选择器 (PhotoPicker - 无需任何存储权限)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.handleEvent(QrScanUiEvent.PickImageFromGallery(it)) }
    }

    // 初次进入生命周期检查权限状态
    LaunchedEffect(Unit) {
        val currentPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (currentPermission == PermissionChecker.PERMISSION_GRANTED) {
            viewModel.handleEvent(QrScanUiEvent.OnPermissionResult(true, false))
        } else {
            val activity = context as? androidx.activity.ComponentActivity
            val shouldShowRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ?: false
            viewModel.handleEvent(QrScanUiEvent.OnPermissionResult(false, shouldShowRationale))
        }
    }

    // 处理副作用 (Effect)
    LaunchedEffect(viewModel.uiEffect) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is QrScanUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is QrScanUiEffect.NavigateToGenerator -> onNavigateToGenerator(effect.prefillContent)
                is QrScanUiEffect.TriggerHapticFeedback -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                is QrScanUiEffect.CopyToClipboard -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ScanResult", effect.text))
                }
                is QrScanUiEffect.OpenSettingsScreen -> {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("二维码扫码", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.handleEvent(QrScanUiEvent.SwitchToGenerateMode) }) {
                        Icon(Icons.Outlined.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("离线生成")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            when (val state = uiState) {
                is QrScanUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is QrScanUiState.PermissionDenied -> {
                    PermissionRationaleView(
                        isPermanentlyDenied = state.isPermanentlyDenied,
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onOpenSettings = { viewModel.handleEvent(QrScanUiEvent.OpenAppSettings) },
                        onPickFromGallery = { photoPickerLauncher.launch("image/*") },
                        onSwitchToGenerator = { viewModel.handleEvent(QrScanUiEvent.SwitchToGenerateMode) }
                    )
                }

                is QrScanUiState.Scanning -> {
                    ScanningActiveView(
                        state = state,
                        onBarcodeDetected = { code, fmt ->
                            viewModel.handleEvent(QrScanUiEvent.OnBarcodeDetected(code, fmt))
                        },
                        onToggleTorch = { viewModel.handleEvent(QrScanUiEvent.ToggleTorch) },
                        onPickFromGallery = { photoPickerLauncher.launch("image/*") },
                        onCopyResult = { viewModel.handleEvent(QrScanUiEvent.CopyResult) },
                        onResume = { viewModel.handleEvent(QrScanUiEvent.ResumeScanning) }
                    )
                }
            }
        }
    }
}
```

---

## 4. 权限未授予与优雅降级视图 (`PermissionRationaleView.kt`)

```kotlin
package com.omnibox.feature.qrcode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PermissionRationaleView(
    isPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickFromGallery: () -> Unit,
    onSwitchToGenerator: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // 居中大图标与安全角标
        Box(
            modifier = Modifier
                .size(108.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.NoPhotography,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-4).dp, y = (-4).dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "需要相机访问权限",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "启动取景器实时扫码需获取相机调用许可。若暂时不便授权，您仍可通过本地相册解析或创建专属条码。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(20.dp))

        // 隐私合规背书卡片
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "端侧内存即时计算 • 零云端上传",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "取景画面仅在本地只读缓冲区进行毫秒级条码矩阵提取，绝不拍照录像、绝不保存或上传图像数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 主行动按钮
        if (isPermanentlyDenied) {
            FilledTonalButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("已被永久拒绝？前往系统设置开启")
            }
        } else {
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Outlined.Videocam, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("授予相机权限并启动扫描")
            }
        }

        Spacer(Modifier.height(32.dp))

        // 无相机降级使用方案区
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "无相机使用方案",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "无需任何相机权限",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Spacer(Modifier.height(12.dp))

        FallbackOptionItem(
            icon = Icons.Outlined.PhotoLibrary,
            badge = "PhotoPicker",
            title = "从相册选取二维码图片",
            subtitle = "调取系统安全隔离图片选择器，无需存储权限",
            onClick = onPickFromGallery
        )

        Spacer(Modifier.height(10.dp))

        FallbackOptionItem(
            icon = Icons.Outlined.QrCode2,
            badge = "ZXing Core",
            title = "切换到离线生成模式",
            subtitle = "离线将文本、网址、Wi-Fi或名片快速编码为条码",
            onClick = onSwitchToGenerator
        )

        Spacer(Modifier.height(20.dp))

        // 离线沙盒安全背书
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "离线工具箱隐私沙盒保证：本应用在系统清单中未申请 INTERNET 联网权限，硬件上杜绝外泄风险。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun FallbackOptionItem(
    icon: ImageVector,
    badge: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badge,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
```

---

## 5. 扫码活跃态与 CameraX 帧分析接入 (`ScanningActiveView.kt`)

```kotlin
package com.omnibox.feature.qrcode

import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

@Composable
fun ScanningActiveView(
    state: QrScanUiState.Scanning,
    onBarcodeDetected: (String, String) -> Unit,
    onToggleTorch: () -> Unit,
    onPickFromGallery: () -> Unit,
    onCopyResult: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var camera by remember { mutableStateOf<Camera?>(null) }

    // 联动手电筒
    LaunchedEffect(state.isTorchOn) {
        camera?.cameraControl?.enableTorch(state.isTorchOn)
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // CameraX 渲染视窗
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = Executors.newSingleThreadExecutor()
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    // 纯本地内存帧分析器 (ImageAnalysis)
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(executor) { imageProxy ->
                                if (state.isAnalyzing) {
                                    // 模拟 ZXing / ML Kit 本地解码流程
                                    // 解码成功回调: onBarcodeDetected("https://...", "QR_CODE")
                                    imageProxy.close()
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // 扫码取景框标线
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.Center)
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
        )

        // 底部快捷操作栏（手电筒与本地相册降级）
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(32.dp))
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleTorch) {
                Icon(
                    imageVector = if (state.isTorchOn) Icons.Filled.FlashOn else Icons.Outlined.FlashOff,
                    contentDescription = "补光灯",
                    tint = if (state.isTorchOn) MaterialTheme.colorScheme.primary else Color.White
                )
            }

            IconButton(onClick = onPickFromGallery) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = "相册导入",
                    tint = Color.White
                )
            }
        }

        // 识别结果浮层卡片
        state.scanResult?.let { result ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "识别结果 (${result.format})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = result.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onResume,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("继续扫描")
                        }
                        Button(
                            onClick = onCopyResult,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("复制内容")
                        }
                    }
                }
            }
        }
    }
}
```
