package com.omnibox.feature.qrcode

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.omnibox.core.designsystem.*
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

private fun Context.findActivity(): ComponentActivity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

internal fun formatQrTime(millis: Long): String =
    SimpleDateFormat("MM月dd日 HH:mm:ss", Locale.getDefault()).format(Date(millis))

/**
 * 相机帧 Y 平面抽取：显式 OptIn ExperimentalGetImage，并显式处理 rowStride。
 * 帧只在内存中转成 ByteArray 交给 ViewModel，绝不写盘。
 */
@ExperimentalGetImage
private fun extractYuvFrame(imageProxy: ImageProxy): YuvFrame? {
    val mediaImage = imageProxy.image
    val planeBuffer: ByteBuffer
    val rowStride: Int
    if (mediaImage != null) {
        val plane = mediaImage.planes[0]
        planeBuffer = plane.buffer
        rowStride = plane.rowStride
    } else {
        val plane = imageProxy.planes[0]
        planeBuffer = plane.buffer
        rowStride = plane.rowStride
    }
    planeBuffer.rewind()
    val bytes = ByteArray(planeBuffer.remaining())
    planeBuffer.get(bytes)
    return YuvFrame(
        bytes = bytes,
        width = imageProxy.width,
        height = imageProxy.height,
        rowStride = rowStride,
        rotationDegrees = imageProxy.imageInfo.rotationDegrees
    )
}

@Composable
fun QrcodeRoute(onBack: () -> Unit) {
    val viewModel: QrcodeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val activity = context.findActivity()
        val rationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ?: false
        viewModel.onEvent(
            QrcodeEvent.PermissionResult(granted, rationale, !granted && !rationale)
        )
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.onEvent(QrcodeEvent.PickImage(it)) } }

    val pngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? -> uri?.let { viewModel.onEvent(QrcodeEvent.ExportPng(it)) } }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? -> uri?.let { viewModel.onEvent(QrcodeEvent.ExportHistoryCsv(it)) } }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val activity = context.findActivity()
        val rationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ?: false
        viewModel.onEvent(QrcodeEvent.PermissionResult(granted, rationale, !granted && !rationale))
    }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(2200)
            viewModel.onEvent(QrcodeEvent.ClearMessage)
        }
    }

    val openSettings: () -> Unit = {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        OmniTopBar(
            title = "扫码识别",
            subtitle = "纯本地解析 · 零网络请求",
            navigationIcon = OmniIcon.of("arrow_back"),
            onNavigationClick = onBack
        ) {
            IconButton(onClick = { viewModel.onEvent(QrcodeEvent.SwitchTab(QrcodeTab.HISTORY)) }) {
                Icon(OmniIcon.of("history"), contentDescription = "历史记录")
            }
        }

        QrTabBar(state.tab) { viewModel.onEvent(QrcodeEvent.SwitchTab(it)) }

        Box(Modifier.weight(1f)) {
            when (state.tab) {
                QrcodeTab.SCAN -> if (state.cameraGranted) {
                    ScanActivePage(
                        state = state,
                        onEvent = viewModel::onEvent,
                        onFrame = { viewModel.onEvent(QrcodeEvent.Frame(it)) },
                        onOpenGallery = { galleryLauncher.launch("image/*") },
                        clipboardText = { text -> clipboard.setText(AnnotatedString(text)) }
                    )
                } else {
                    PermissionPage(
                        state = state,
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onOpenSettings = openSettings,
                        onOpenGallery = { galleryLauncher.launch("image/*") },
                        onSwitchToGenerate = { viewModel.onEvent(QrcodeEvent.SwitchTab(QrcodeTab.GENERATE)) }
                    )
                }
                QrcodeTab.GENERATE -> GeneratePage(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onExportPng = { pngLauncher.launch("omnibox_qr_${System.currentTimeMillis()}.png") }
                )
                QrcodeTab.EXPORT_CONFIG -> ExportConfigPage(
                    state = state,
                    onEvent = viewModel::onEvent
                )
                QrcodeTab.HISTORY -> HistoryPage(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onExportCsv = { csvLauncher.launch("omnibox_qr_history.csv") },
                    clipboardText = { text -> clipboard.setText(AnnotatedString(text)) }
                )
            }
        }

        state.message?.let { msg ->
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = OmniShape.Pill,
                modifier = Modifier.padding(horizontal = OmniDimens.ScreenPadding, vertical = 8.dp)
            ) {
                Text(
                    msg,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun QrTabBar(current: QrcodeTab, onSelect: (QrcodeTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        QrcodeTab.entries.forEach { tab ->
            val selected = tab == current
            Surface(
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
                shape = OmniShape.Pill,
                modifier = Modifier
                    .weight(1f)
                    .clip(OmniShape.Pill)
                    .clickable { onSelect(tab) }
            ) {
                Row(
                    Modifier.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        OmniIcon.of(tab.icon), contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/* ------------------------------ 扫码活跃页 (_4) ------------------------------ */

@Composable
private fun ScanActivePage(
    state: QrcodeUiState,
    onEvent: (QrcodeEvent) -> Unit,
    onFrame: (YuvFrame) -> Unit,
    onOpenGallery: () -> Unit,
    clipboardText: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = OmniDimens.ScreenPadding)
                .height(280.dp)
                .clip(OmniShape.Card)
                .background(MaterialTheme.colorScheme.inverseSurface)
        ) {
            CameraPreview(
                onFrame = onFrame,
                enabled = state.scanActive,
                torchOn = state.torchOn,
                zoomRatio = state.zoomRatio,
                onBindError = { onEvent(QrcodeEvent.ShowMessage(it)) },
                modifier = Modifier.fillMaxSize()
            )
            // 取景框四角标线
            Box(
                Modifier
                    .size(190.dp)
                    .align(Alignment.Center)
                    .border(2.dp, MaterialTheme.colorScheme.inversePrimary, RoundedCornerShape(20.dp))
            )
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.75f),
                shape = OmniShape.Pill,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
            ) {
                Text(
                    "CameraX · ZXing 纯离线解码",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
            Text(
                "对准二维码或条形码自动识读",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 58.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.inverseOnSurface
            )
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QrDockButton("flash_on", "补光灯") { onEvent(QrcodeEvent.ToggleTorch) }
                listOf(1f, 2f, 5f).forEach { z ->
                    QrMiniChip("${z.toInt()}x", state.zoomRatio == z) {
                        onEvent(QrcodeEvent.SetZoom(z))
                    }
                }
                QrDockButton("photo_library", "相册", onOpenGallery)
            }
        }

        QrCard("task_alt", "最近识读成功", "Room 数据库已持久化") {
            val result = state.scanResult
            if (result == null) {
                Text(
                    if (state.scanActive) "取景器就绪 · 将二维码置于取景框内即可自动识别"
                    else "已暂停识别 · 点击下方「继续扫描」重新就绪",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(result.content, style = MaterialTheme.typography.bodyMedium, maxLines = 3,
                    overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                QrInfoLine("verified_user", "本地离线安全", "格式: ${result.format} (UTF-8)")
                QrInfoLine("schedule", "来源", when (result.source) {
                    "camera" -> "相机实时扫码"; "image" -> "相册图片解析"; else -> result.source
                })
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OmniPrimaryButton("复制内容", onClick = { clipboardText(result.content) },
                        icon = "content_copy", modifier = Modifier.weight(1f))
                    OmniPrimaryButton("再次生成", onClick = {
                        onEvent(QrcodeEvent.CopyToGenerate(result.content))
                    }, icon = "qr_code_2", modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                OmniPrimaryButton("继续扫描", onClick = { onEvent(QrcodeEvent.ResumeScan) },
                    icon = "refresh", modifier = Modifier.fillMaxWidth())
            }
        }

        QrCard("filter_none", "高效连扫", "批量快速扫描") {
            QrSwitchLine("连续扫码模式", "识别后不停留，约 1.6 秒后自动就绪下一次扫码",
                state.continuousScan) { onEvent(QrcodeEvent.ToggleContinuous) }
            Text("连续识别无需停留，结果逐条写入本地 Room 历史。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        QrCard("verified", "严格端侧隐私与沙盒保护", "13 种常见格式") {
            Text(
                "遵循零网络权限规范。CameraX 帧缓冲区仅在应用内存即时进行灰度解析与边界提取，" +
                    "绝不保存图像快照，不产生任何后台网络上传。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            FlowLikeChips(QR_SUPPORTED_FORMAT_LABELS)
        }

        QrCard("history", "本地历史记录", "共 ${state.history.size} 条 · 支持分类回溯") {
            Text("纯本地 Room 数据库沙盒存储 · 绝不出设备",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OmniPrimaryButton("查看与导出历史", onClick = {
                onEvent(QrcodeEvent.SwitchTab(QrcodeTab.HISTORY))
            }, icon = "history", modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun QrDockButton(icon: String, label: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f),
        shape = OmniShape.Pill,
        modifier = Modifier.clip(OmniShape.Pill).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(OmniIcon.of(icon), contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.inverseOnSurface)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.inverseOnSurface)
        }
    }
}

/** CameraX：PreviewView 通过 AndroidView 嵌入 + ImageAnalysis 取 Y 平面。 */
@Composable
private fun CameraPreview(
    onFrame: (YuvFrame) -> Unit,
    enabled: Boolean,
    torchOn: Boolean,
    zoomRatio: Float,
    onBindError: (String) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // 捕获 State 而非首帧的值：AndroidView factory 只执行一次，需让 analyzer 读到最新回调。
    val currentOnFrame = rememberUpdatedState(onFrame)
    val currentEnabled = rememberUpdatedState(enabled)
    val currentOnBindError = rememberUpdatedState(onBindError)
    val cameraRef = remember { mutableStateOf<Camera?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            runCatching {
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    runCatching { future.get().unbindAll() }
                }, ContextCompat.getMainExecutor(context))
            }
            executor.shutdownNow()
        }
    }

    LaunchedEffect(torchOn) {
        cameraRef.value?.cameraControl?.enableTorch(torchOn)
    }
    LaunchedEffect(zoomRatio) {
        cameraRef.value?.cameraControl?.setZoomRatio(zoomRatio)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                try {
                    val provider = future.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { useCase ->
                            useCase.setAnalyzer(executor) { imageProxy ->
                                if (currentEnabled.value) {
                                    val frame = extractYuvFrame(imageProxy)
                                    if (frame != null) currentOnFrame.value(frame)
                                }
                                imageProxy.close()
                            }
                        }
                    provider.unbindAll()
                    cameraRef.value = provider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                    )
                } catch (e: Exception) {
                    currentOnBindError.value("相机启动失败：${e.message ?: "设备可能无可用摄像头"}")
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}

/* ------------------------------ 权限降级页 (_5) ------------------------------ */

@Composable
private fun PermissionPage(
    state: QrcodeUiState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGallery: () -> Unit,
    onSwitchToGenerate: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OmniDimens.ScreenPadding, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(OmniIcon.of("no_photography"), contentDescription = null,
                modifier = Modifier.size(46.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(16.dp))
        Text("需要相机访问权限", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "启动取景器实时扫码需获取相机调用许可。若暂时不便授权，您仍可通过本地相册解析或创建专属条码。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))

        QrCard("lock", "端侧内存即时计算 · 零云端上传", "") {
            Text("取景画面仅在本地只读缓冲区进行毫秒级条码矩阵提取，绝不拍照录像、绝不保存或上传图像数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (state.permanentlyDenied) {
            OmniPrimaryButton("已被永久拒绝？前往系统设置开启", onClick = onOpenSettings,
                icon = "settings", modifier = Modifier.fillMaxWidth())
        } else {
            OmniPrimaryButton("授予相机权限并启动扫描", onClick = onRequestPermission,
                icon = "videocam", modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = if (state.permanentlyDenied) onRequestPermission else onOpenSettings) {
            Text(if (state.permanentlyDenied) "重新发送权限请求" else "已被永久拒绝？前往系统设置开启")
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("无相机使用方案", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("无需任何相机权限", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))

        QrFallbackRow("photo_library", "PhotoPicker", "从相册选取二维码图片",
            "调取系统安全隔离图片选择器，无需存储读取权限", onOpenGallery)
        Spacer(Modifier.height(8.dp))
        QrFallbackRow("qr_code_2", "ZXing Core", "切换到离线生成模式",
            "离线将文本、网址、Wi-Fi或名片快速编码为高质量二维码", onSwitchToGenerate)

        Spacer(Modifier.height(16.dp))
        QrCard("health_and_safety", "离线工具箱隐私沙盒保证", "") {
            Text("本应用在系统清单中未申请 INTERNET 联网权限，硬件上杜绝外泄风险。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QrFallbackRow(
    icon: String,
    badge: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = OmniShape.CardSmall,
        modifier = Modifier.fillMaxWidth().clip(OmniShape.CardSmall).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(OmniShape.Input)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(OmniIcon.of(icon), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = OmniShape.Pill) {
                        Text(badge, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(OmniIcon.of("chevron_right"), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* --------------------- 本模块复用的小组件（照搬 PasswordScreen 模式） --------------------- */

@Composable
internal fun QrCard(icon: String, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = OmniShape.CardSmall
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(OmniIcon.of(icon), contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.weight(1f))
                    Text(subtitle, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
internal fun QrInfoLine(icon: String, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(OmniIcon.of(icon), contentDescription = null, modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun QrSwitchLine(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(hint, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun QrMiniChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = OmniShape.Pill,
        modifier = Modifier.clip(OmniShape.Pill).clickable(onClick = onClick)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun QrOptionChip(
    label: String,
    hint: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = OmniShape.CardSmall,
        modifier = modifier.clip(OmniShape.CardSmall).clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface)
            if (hint.isNotBlank()) {
                Text(hint, style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 不依赖 experimental FlowRow：用普通 Row + 换行分组展示标签。 */
@Composable
internal fun FlowLikeChips(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { item ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = OmniShape.Pill) {
                        Text(item, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/** 由 ARGB int 构造 Compose Color（仅用稳定命名的分量构造器）。 */
internal fun argbToComposeColor(argb: Int): Color = Color(
    red = ((argb shr 16) and 0xFF) / 255f,
    green = ((argb shr 8) and 0xFF) / 255f,
    blue = (argb and 0xFF) / 255f,
    alpha = ((argb shr 24) and 0xFF) / 255f
)
