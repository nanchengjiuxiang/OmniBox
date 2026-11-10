package com.omnibox.feature.imagecompress

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCompressScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImageCompressViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()

    // 采用现代免权限 PhotoPicker 选择图片
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.handleEvent(ImageCompressUiEvent.PickImage(it)) }
    }

    LaunchedEffect(viewModel.uiEffect) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is ImageCompressUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is ImageCompressUiEffect.TriggerHapticFeedback -> {
                    haptic.performHapticFeedback(
                        if (effect.isStrong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
                    )
                }
                is ImageCompressUiEffect.ShareIntent -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = effect.mimeType
                        putExtra(Intent.EXTRA_STREAM, effect.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享压缩后的图片"))
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("图片无损压缩", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("防 OOM 采样流 · 本地隔离", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState is ImageCompressUiState.Ready) {
                        IconButton(onClick = { viewModel.handleEvent(ImageCompressUiEvent.Reset) }) {
                            Icon(Icons.Outlined.RestartAlt, contentDescription = "重选图片")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
                is ImageCompressUiState.Empty -> {
                    EmptySelectPlaceholder(
                        onSelectClick = { photoPickerLauncher.launch("image/*") }
                    )
                }

                is ImageCompressUiState.Processing -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(state.stage, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                is ImageCompressUiState.Ready -> {
                    ReadyContentView(
                        state = state,
                        onEvent = viewModel::handleEvent,
                        onRePick = { photoPickerLauncher.launch("image/*") }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySelectPlaceholder(
    onSelectClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoSizeSelectLarge,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "选择需压缩的图片",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "采用 Android 10+ 原生分块流式解码与 inSampleSize 降噪算法，轻松处理 4800 万以上高分辨率大图，绝无 OOM 闪退隐患。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onSelectClick,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.height(50.dp)
        ) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("从本地相册选取图片")
        }
    }
}

@Composable
private fun ReadyContentView(
    state: ImageCompressUiState.Ready,
    onEvent: (ImageCompressUiEvent) -> Unit,
    onRePick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 顶部沙盒运行承诺卡片
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "硬件级内存沙盒保护 · inJustDecodeBounds 流式计算 · 常驻 < 16MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // 1. 无损画质实时预览卡片（对比滑块与体积减少指示）
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Compare, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("无损画质实时预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    if (state.metrics.savingsPercent > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "减小 ${DecimalFormat("#.#").format(state.metrics.savingsPercent)}%",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 对比视窗
                CompareSplitView(
                    sourceUri = state.sourceUri,
                    compressedUri = state.compressedUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                )

                Spacer(Modifier.height(16.dp))

                // 指标网格
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("原始文件", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(formatFileSize(state.metrics.originalSize), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Text("${state.metrics.originalWidth} × ${state.metrics.originalHeight}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("目标文件 (${state.config.format.name})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(formatFileSize(state.metrics.compressedSize), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        Text("${state.metrics.compressedWidth} × ${state.metrics.compressedHeight}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))

                // 底层关键指标：堆内存、耗时、PSNR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricChip(
                        icon = Icons.Outlined.Memory,
                        label = "堆内存峰值",
                        value = formatFileSize(state.metrics.peakMemoryBytes)
                    )
                    MetricChip(
                        icon = Icons.Outlined.Bolt,
                        label = "耗时",
                        value = "${state.metrics.durationMs}ms"
                    )
                    MetricChip(
                        icon = Icons.Outlined.Visibility,
                        label = "PSNR",
                        value = "${DecimalFormat("#.#").format(state.metrics.psnrDb)} dB"
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 主行动按钮
        Button(
            onClick = { onEvent(ImageCompressUiEvent.SaveToGallery) },
            enabled = !state.isSaving && state.compressedUri != null,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("正在写出系统相册...")
            } else {
                Icon(Icons.Outlined.DownloadDone, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("保存无损压缩图至系统相册", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onRePick,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("重选图片")
            }

            OutlinedButton(
                onClick = { onEvent(ImageCompressUiEvent.ShareImage) },
                enabled = state.compressedUri != null,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("直接分享")
            }
        }

        Spacer(Modifier.height(24.dp))

        // 2. 压缩策略调优区
        Text("压缩策略调优", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 画质保留滑块
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("画质保留系数 (Quality)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text("${state.config.quality}% 推荐", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                Slider(
                    value = state.config.quality.toFloat(),
                    onValueChange = { onEvent(ImageCompressUiEvent.UpdateQuality(it.roundToInt())) },
                    valueRange = 10f..100f,
                    steps = 17
                )

                Spacer(Modifier.height(12.dp))

                // 封装格式单选
                Text("编码封装格式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompressFormat.entries.forEach { format ->
                        FilterChip(
                            selected = state.config.format == format,
                            onClick = { onEvent(ImageCompressUiEvent.UpdateFormat(format)) },
                            label = { Text(format.name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 分辨率下采样
                Text("分辨率等比下采样", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResolutionScale.entries.forEach { scale ->
                        FilterChip(
                            selected = state.config.resolutionScale == scale,
                            onClick = { onEvent(ImageCompressUiEvent.UpdateResolution(scale)) },
                            label = { Text(scale.label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 3. 防 OOM 机制与信息安全开关
        Text("防 OOM 机制与信息安全", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                SwitchItem(
                    title = "采样率自动计算 (inSampleSize 阶乘匹配)",
                    subtitle = "计算最适二次幂比例，防止超额分配百万像素位图",
                    checked = state.config.autoInSampleSize,
                    onCheckedChange = { onEvent(ImageCompressUiEvent.ToggleAutoInSampleSize(it)) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))

                SwitchItem(
                    title = "抹除 EXIF 敏感地理位置信息 (GPS 清理)",
                    subtitle = "净化经纬度、海拔与设备序列号，捍卫隐私安全",
                    checked = state.config.stripGpsLocation,
                    onCheckedChange = { onEvent(ImageCompressUiEvent.ToggleStripGps(it)) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))

                SwitchItem(
                    title = "保留拍摄摄影参数 (ISO / 光圈 / 快门)",
                    subtitle = "无害摄影参数封装至新生成的 WebP/JPEG 头部",
                    checked = state.config.keepPhotographyExif,
                    onCheckedChange = { onEvent(ImageCompressUiEvent.ToggleKeepExif(it)) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))

                SwitchItem(
                    title = "广色域保真 (Display P3 / sRGB ICC Profile)",
                    subtitle = "色彩映射无偏差，避免色彩压缩后发白变淡失真",
                    checked = state.config.preserveDisplayP3Color,
                    onCheckedChange = { onEvent(ImageCompressUiEvent.TogglePreserveP3(it)) }
                )
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MetricChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val df = DecimalFormat("#.##")
    return when {
        bytes >= 1024 * 1024 -> "${df.format(bytes.toDouble() / (1024 * 1024))} MB"
        bytes >= 1024 -> "${df.format(bytes.toDouble() / 1024)} KB"
        else -> "$bytes B"
    }
}
