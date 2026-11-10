package com.omnibox.feature.imagecompress

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnibox.core.common.Formatters
import com.omnibox.core.designsystem.*
import java.util.Locale

/** 对照 oom_1/code.html；所有演示数值均改为从真实压缩结果计算。 */
@Composable
fun ImageCompressRoute(onBack: () -> Unit) {
    val viewModel: ImageCompressViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()

    val pickContract = remember { ActivityResultContracts.GetContent() }
    val pickImage = rememberLauncherForActivityResult(pickContract) { uri ->
        if (uri != null) viewModel.onEvent(ImageCompressEvent.ImageSelected(uri))
    }

    // mimeType 随真实输出格式变化；remember(mime) 保证契约只在格式切换时重建
    val mime = state.format.mimeType
    val exportContract = remember(mime) { ActivityResultContracts.CreateDocument(mime) }
    val exportDoc = rememberLauncherForActivityResult(exportContract) { uri ->
        if (uri != null) viewModel.onEvent(ImageCompressEvent.ExportTo(uri))
    }

    ImageCompressContent(
        state = state,
        onEvent = viewModel::onEvent,
        onPick = { pickImage.launch("image/*") },
        onExport = { exportDoc.launch(viewModel.exportFileName()) },
        onBack = onBack
    )
}

@Composable
private fun ImageCompressContent(
    state: ImageCompressUiState,
    onEvent: (ImageCompressEvent) -> Unit,
    onPick: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit
) {
    val result = state.result
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            // 无底栏的工具页：滚动内容在手势条上方收住，最后一屏不会被小白条压住
            .omniStatusBarInset().omniNavBarInset()
    ) {
        item {
            OmniTopBar(
                title = "图片无损压缩",
                subtitle = "inJustDecodeBounds 流式管道 · 零 OOM 防御",
                navigationIcon = OmniIcon.of("arrow_back"),
                onNavigationClick = onBack
            ) {
                IconButton(onClick = onPick) {
                    Icon(OmniIcon.of("photo_library"), contentDescription = "重新选图")
                }
            }
        }

        // 内存安全沙盒状态条
        item {
            OmniStaticCard("verified_user", "硬件级内存沙盒保护", "inJustDecodeBounds 流式处理") {
                OmniInfoLine("memory", "峰值常驻管控", "仅一次带采样解码 · 预览限 900px")
                OmniInfoLine("bolt", "本地隔离", "全流程 Dispatchers.IO · 零网络传输")
            }
        }

        // 选图 / 错误 / 提示
        if (result == null) {
            item {
                OmniStaticCard("image", "尚未载入图片", "请选择一张图片开始真实压缩") {
                    Text(
                        "使用系统文件选择器读取原图字节，三段式采样后由 Bitmap.compress() 产出真实结果。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OmniPrimaryButton(
                        text = "选择图片",
                        onClick = onPick,
                        icon = "add",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (state.isProcessing) {
            item {
                OmniStaticCard("sync", "正在重新计算", "参数变更后即时重算") {
                    Text(
                        "已按当前配置重新采样与编码…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.error != null) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = OmniShape.CardSmall
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            OmniIcon.of("warning"), contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            state.error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        if (state.exportMessage != null) {
            item {
                OmniStaticCard("check_circle", "导出完成", state.exportMessage ?: "") {
                    TextButton(onClick = { onEvent(ImageCompressEvent.DismissMessage) }) {
                        Text("知道了")
                    }
                }
            }
        }

        // 核心对比视窗与量化指标
        if (result != null) {
            item {
                val saved = result.savedBytes
                val savingLabel = if (result.savingsRatio >= 0) {
                    "减小 ${Formatters.percent(result.savingsRatio)} (省 ${Formatters.bytes(saved)})"
                } else {
                    "增大 ${Formatters.percent(-result.savingsRatio)} (多 ${Formatters.bytes(-saved)})"
                }
                OmniStaticCard("compare", "无损画质实时预览", savingLabel) {
                    CompareSplitView(
                        original = result.originalPreview,
                        result = result.resultPreview,
                        originalLabel = "原图 ${result.originalWidth}×${result.originalHeight}",
                        resultLabel = "已优化 (${result.config.format.label})"
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpecCard(
                            title = "原始文件",
                            format = mimeShort(result.originalMime),
                            size = Formatters.bytes(result.originalBytes),
                            detail = "${result.originalWidth} × ${result.originalHeight} · ${megapixels(result.originalWidth, result.originalHeight)}",
                            modifier = Modifier.weight(1f),
                            highlight = false
                        )
                        SpecCard(
                            title = "目标文件",
                            format = result.config.format.label,
                            size = Formatters.bytes(result.resultBytes),
                            detail = "${result.resultWidth} × ${result.resultHeight} · 采样 ×${result.inSampleSize}",
                            modifier = Modifier.weight(1f),
                            highlight = true
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    // 自绘进度条：产物体积占原体积的真实比例
                    val outputRatio = if (result.originalBytes > 0) {
                        (result.resultBytes.toDouble() / result.originalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
                    } else 0f
                    Text(
                        "产物占原体积 ${Formatters.percent(outputRatio.toDouble())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(OmniShape.Pill)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(outputRatio)
                                .height(8.dp)
                                .clip(OmniShape.Pill)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Telemetry("memory", "采样倍率", "×${result.inSampleSize}")
                        Telemetry("bolt", "耗时", "${result.durationMs} ms")
                        Telemetry("high_quality", "压缩率", Formatters.percent(result.savingsRatio))
                    }
                }
            }

            // 主操作 + 次级操作
            item {
                Column(Modifier.padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp)) {
                    OmniPrimaryButton(
                        text = "保存压缩结果 (SAF)",
                        onClick = onExport,
                        icon = "download",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = result.outputBytes.isNotEmpty()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OmniPrimaryButton(
                            text = "更换图片",
                            onClick = onPick,
                            icon = "photo_library",
                            modifier = Modifier.weight(1f)
                        )
                        OmniPrimaryButton(
                            text = "源图 ${state.sourceName}",
                            onClick = onPick,
                            icon = "image",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 压缩策略调优
        item {
            OmniStaticCard("tune", "压缩策略调优", "即时重算") {
                OmniLabel("画质保留系数 (Quality factor)：${state.quality}%")
                Slider(
                    value = state.quality.toFloat(),
                    onValueChange = { onEvent(ImageCompressEvent.QualityChanged(it.toInt())) },
                    valueRange = 1f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("小文件 (1%)", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("推荐 (80-85%)", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("纯原质 (100%)", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(40, 82, 95, 100).forEach { q ->
                        OmniMiniChip("$q%", state.quality == q) {
                            onEvent(ImageCompressEvent.QualityChanged(q))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                OmniLabel("编码封装格式")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompressOutputFormat.entries.forEach { f ->
                        OmniFormatChip(f.label, state.format == f, Modifier.weight(1f)) {
                            onEvent(ImageCompressEvent.FormatChanged(f))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                OmniLabel("分辨率等比下采样 (Downsampling)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompressMaxEdge.entries.forEach { e ->
                        OmniMiniChip(e.label, state.maxEdge == e) {
                            onEvent(ImageCompressEvent.MaxEdgeChanged(e))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                OmniSwitchLine(
                    label = "保留透明通道 (Alpha)",
                    hint = "PNG / WebP 保留 ARGB_8888；JPEG 自动合成白色背景",
                    checked = state.preserveAlpha
                ) { onEvent(ImageCompressEvent.TogglePreserveAlpha) }
            }
        }

        // 防 OOM 机制与信息安全（全部为真实机制说明，不展示伪造开关状态）
        item {
            OmniStaticCard("shield_with_heart", "防 OOM 机制与信息安全", "本地执行") {
                OmniInfoLine("memory", "采样率自动计算", "inSampleSize 取最适二次幂，避免百万像素位图")
                OmniInfoLine("location_off", "EXIF 敏感信息", "重新编码产物不含原始 EXIF / GPS 标签")
                OmniInfoLine("cloud_off", "纯离线执行", "零 INTERNET 权限 · 原始字节不离开设备")
                OmniInfoLine("terminal", "协程管道", "全部 IO 运行于 Dispatchers.IO")
            }
        }

        item {
            Text(
                "采用 BitmapFactory 边界解析 → 二次采样解码 → Bitmap.compress() 三段式管道，" +
                    "大图不会一次性全分辨率反复载入；预览位图限制在 900px 以内，" +
                    "并对 OutOfMemoryError 提供降级重试与明确错误状态。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(OmniDimens.ScreenPadding)
            )
        }
    }
}

private fun megapixels(width: Int, height: Int): String =
    String.format(Locale.getDefault(), "%.1f MP", width * height / 1_000_000.0)

private fun mimeShort(mime: String): String =
    mime.substringAfter('/', "image").uppercase(Locale.getDefault())

@Composable
private fun SpecCard(
    title: String,
    format: String,
    size: String,
    detail: String,
    modifier: Modifier = Modifier,
    highlight: Boolean
) {
    val container = if (highlight) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceContainerLow
    val onContainer = if (highlight) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(modifier = modifier, color = container, shape = OmniShape.CardSmall) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = onContainer)
                Text(format, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                    color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(6.dp))
            Text(size, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = onContainer)
        }
    }
}

@Composable
private fun Telemetry(icon: String, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(OmniIcon.of(icon), contentDescription = null, modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(4.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** 可拖动分割对比：底层为压缩结果，顶层裁剪显示原图。 */
@Composable
private fun CompareSplitView(
    original: Bitmap,
    result: Bitmap,
    originalLabel: String,
    resultLabel: String,
    modifier: Modifier = Modifier
) {
    var split by remember { mutableFloatStateOf(0.52f) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(224.dp)
            .clip(OmniShape.CardSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    val w = size.width.toFloat()
                    if (w > 0f) split = (split + dragAmount / w).coerceIn(0.05f, 0.95f)
                }
            }
    ) {
        val fullWidth = maxWidth
        Image(
            bitmap = result.asImageBitmap(),
            contentDescription = "已优化图像",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(fullWidth * split)
                .clipToBounds()
        ) {
            Image(
                bitmap = original.asImageBitmap(),
                contentDescription = "原图",
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(fullWidth).fillMaxHeight()
            )
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.75f),
                shape = OmniShape.Pill,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) {
                Text(originalLabel, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            shape = OmniShape.Pill,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            Text(resultLabel, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
        Surface(
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.6f),
            shape = OmniShape.Pill,
            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
        ) {
            Text("左右拖动滑动器比对噪点与细节", fontSize = 10.sp,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
        Box(
            Modifier
                .fillMaxHeight()
                .offset(x = fullWidth * split - 1.dp)
                .width(2.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = fullWidth * split - 18.dp)
                .size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(OmniIcon.of("drag_indicator"), contentDescription = "左右拖动对比",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun OmniFormatChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = OmniShape.CardSmall,
        modifier = modifier
            .height(40.dp)
            .clip(OmniShape.CardSmall)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

/* ---- 本模块复用的小组件（照搬 PasswordScreen 的私有组件模式） ---- */

@Composable
private fun OmniStaticCard(
    icon: String,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp),
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
private fun OmniInfoLine(icon: String, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(OmniIcon.of(icon), contentDescription = null, modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun OmniSwitchLine(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(hint, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun OmniMiniChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = OmniShape.Pill,
        modifier = Modifier.clip(OmniShape.Pill).clickable(onClick = onClick)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall)
    }
}
