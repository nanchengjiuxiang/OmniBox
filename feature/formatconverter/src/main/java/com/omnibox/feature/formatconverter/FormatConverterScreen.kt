package com.omnibox.feature.formatconverter

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnibox.core.common.Formatters
import com.omnibox.core.data.local.ConversionOutputEntity
import com.omnibox.core.data.local.TranscodeTaskEntity
import com.omnibox.core.designsystem.*

/**
 * 对照 format_converter/code.html、_12（转码进度）、_7（转换报告）。
 * 三屏 + 历史任务列表用页面内状态切换；报告数据全部来自 Room 查询结果。
 */
@Composable
fun FormatConverterRoute(onBack: () -> Unit) {
    val viewModel: FormatConverterViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()

    val pickContract = remember { ActivityResultContracts.GetMultipleContents() }
    val pickFiles = rememberLauncherForActivityResult(pickContract) { uris ->
        if (uris.isNotEmpty()) viewModel.onEvent(FormatConverterEvent.FilesSelected(uris))
    }

    // 三种输出格式各有稳定的 CreateDocument(mimeType) 契约
    val jpegExport = rememberLauncherForActivityResult(
        remember { ActivityResultContracts.CreateDocument("image/jpeg") }
    ) { uri ->
        if (uri != null) viewModel.onEvent(FormatConverterEvent.ExportResult(uri))
    }
    val webpExport = rememberLauncherForActivityResult(
        remember { ActivityResultContracts.CreateDocument("image/webp") }
    ) { uri ->
        if (uri != null) viewModel.onEvent(FormatConverterEvent.ExportResult(uri))
    }
    val pngExport = rememberLauncherForActivityResult(
        remember { ActivityResultContracts.CreateDocument("image/png") }
    ) { uri ->
        if (uri != null) viewModel.onEvent(FormatConverterEvent.ExportResult(uri))
    }
    val onRequestExport: (String, String) -> Unit = { fileName, mime ->
        viewModel.onEvent(FormatConverterEvent.RequestExport(fileName))
        when {
            mime.contains("jpeg", ignoreCase = true) -> jpegExport.launch(fileName)
            mime.contains("png", ignoreCase = true) -> pngExport.launch(fileName)
            else -> webpExport.launch(fileName)
        }
    }

    FormatConverterContent(
        state = state,
        onEvent = viewModel::onEvent,
        onPick = { pickFiles.launch("image/*") },
        onRequestExport = onRequestExport,
        onBack = onBack
    )
}

@Composable
private fun FormatConverterContent(
    state: FormatConverterUiState,
    onEvent: (FormatConverterEvent) -> Unit,
    onPick: () -> Unit,
    onRequestExport: (String, String) -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        item {
            OmniTopBar(
                title = when (state.page) {
                    ConverterPage.SETUP -> "全能格式转换"
                    ConverterPage.PROGRESS -> "转码进行中"
                    ConverterPage.REPORT -> "转换完成报告"
                    ConverterPage.HISTORY -> "历史转换任务"
                },
                subtitle = "纯本地沙盒离线转码 · 图片 PNG / JPEG / WebP",
                navigationIcon = OmniIcon.of("arrow_back"),
                onNavigationClick = onBack
            ) {
                IconButton(onClick = { onEvent(FormatConverterEvent.PageChanged(ConverterPage.HISTORY)) }) {
                    Icon(OmniIcon.of("history"), contentDescription = "历史任务")
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
                        Icon(OmniIcon.of("warning"), contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(10.dp))
                        Text(state.error ?: "", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        if (state.message != null) {
            item {
                OmniStaticCard("check_circle", "操作反馈", state.message ?: "") {
                    TextButton(onClick = { onEvent(FormatConverterEvent.DismissMessage) }) {
                        Text("知道了")
                    }
                }
            }
        }

        item {
            when (state.page) {
                ConverterPage.SETUP -> SetupPage(state, onEvent, onPick)
                ConverterPage.PROGRESS -> ProgressPage(state, onEvent)
                ConverterPage.REPORT -> ReportPage(state, onEvent, onRequestExport)
                ConverterPage.HISTORY -> HistoryPage(state, onEvent)
            }
        }
    }
}

/* ---------------- 格式选择屏 ---------------- */

@Composable
private fun SetupPage(
    state: FormatConverterUiState,
    onEvent: (FormatConverterEvent) -> Unit,
    onPick: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        // 离线沙盒横幅
        OmniStaticCard("security", "纯本地沙盒离线转码", "离线保密") {
            Text("BitmapFactory 解码 + Bitmap.compress() 编码 · 零文件网络上传 · " +
                "所有 IO 运行于 Dispatchers.IO。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OmniInfoLine("cloud_off", "零网络", "未申请 INTERNET 权限")
            OmniInfoLine("image", "已实现范围", "PNG / JPEG / WebP 图片互转（最多 9 张）")
        }

        // 类别标签
        OmniStaticCard("transform", "转换类别", "图片转换") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = OmniShape.Pill) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(OmniIcon.of("image"), contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("图片转换", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text("音频 / 文档 / 视频类别暂未实现，不提供虚假交互。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 源文件列表
        OmniStaticCard("library_add", "待处理源文件", "多选模式 (${state.fileInfos.size} 个)") {
            if (state.fileInfos.isEmpty()) {
                Text("尚未选择文件。使用系统文件选择器可一次多选最多 9 张图片。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.fileInfos.forEach { info ->
                    SourceFileRow(info)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OmniPrimaryButton(text = "选择图片（多选）", onClick = onPick,
                    icon = "add", modifier = Modifier.weight(1f))
                OmniPrimaryButton(text = "清空列表", onClick = {
                    onEvent(FormatConverterEvent.ClearFiles)
                }, icon = "delete", modifier = Modifier.weight(1f))
            }
        }

        // 目标格式矩阵
        OmniStaticCard("tune", "目标格式与转码策略", "选择目标容器") {
            TargetFormat.entries.forEach { format ->
                FormatCard(
                    format = format,
                    selected = state.targetFormat == format,
                    onClick = { onEvent(FormatConverterEvent.FormatChanged(format)) }
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            OmniLabel("画质 / 压缩比 (Quality)：${state.quality}%")
            Slider(
                value = state.quality.toFloat(),
                onValueChange = { onEvent(FormatConverterEvent.QualityChanged(it.toInt())) },
                valueRange = 1f..100f,
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("最小文件", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("推荐 85%", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("高保真", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 实时数据（全部来自真实选中文件）
        val totalBytes = state.fileInfos.sumOf { it.bytes }
        OmniStaticCard("analytics", "待处理数据总览", "来自已选文件") {
            OmniInfoLine("list", "待处理文件", "${state.fileInfos.size} 个")
            OmniInfoLine("storage", "源体积合计", Formatters.bytes(totalBytes))
            OmniInfoLine("transform", "目标格式", "${state.targetFormat.label} · Q${state.quality}")
        }

        Column(Modifier.padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp)) {
            OmniPrimaryButton(
                text = "立即开始离线转换",
                onClick = { onEvent(FormatConverterEvent.StartConversion) },
                icon = "bolt",
                modifier = Modifier.fillMaxWidth(),
                enabled = state.fileInfos.isNotEmpty() && !state.isRunning
            )
            Spacer(Modifier.height(8.dp))
            OmniPrimaryButton(
                text = "历史转换记录 (${state.tasks.size} 条)",
                onClick = { onEvent(FormatConverterEvent.PageChanged(ConverterPage.HISTORY)) },
                icon = "history",
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            "100% 运行于 Android 本地沙盒，产物默认写入应用私有缓存，可通过 SAF 逐个导出到用户指定目录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(OmniDimens.ScreenPadding)
        )
    }
}

@Composable
private fun SourceFileRow(info: SourceFileInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = OmniShape.CardSmall
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(OmniIcon.of("image"), contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(info.name, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold)
                Text("${Formatters.bytes(info.bytes)} · ${info.width} × ${info.height}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(OmniIcon.of("check_circle"), contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun FormatCard(format: TargetFormat, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerLowest
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    val hint = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier.fillMaxWidth().clip(OmniShape.CardSmall).clickable(onClick = onClick),
        color = bg,
        shape = OmniShape.CardSmall
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(format.label, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = fg)
                Text(format.desc, style = MaterialTheme.typography.bodySmall, color = hint)
            }
            if (selected) {
                Icon(OmniIcon.of("check_circle"), contentDescription = null, tint = fg)
            }
        }
    }
}

/* ---------------- 转码进度屏 (_12) ---------------- */

@Composable
private fun ProgressPage(state: FormatConverterUiState, onEvent: (FormatConverterEvent) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        OmniStaticCard("offline_bolt", "本地转码任务运行中", "纯离线执行") {
            OmniInfoLine("bolt", "执行位置", "kotlinx.coroutines Dispatchers.IO")
            OmniInfoLine("cloud_off", "网络消耗", "零流量 · 未申请 INTERNET 权限")
        }

        OmniStaticCard("progress_activity", "实时转码进度", "OmniBox 离线转码引擎") {
            Text("${state.progressPercent}%", style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("已完成 ${state.processedCount} / ${state.totalCount} 个文件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(OmniShape.Pill)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((state.progressPercent / 100f).coerceIn(0f, 1f))
                        .height(10.dp)
                        .clip(OmniShape.Pill)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Spacer(Modifier.height(14.dp))
            OmniInfoLine("sync", "当前文件", state.currentFileName.ifBlank { "等待开始" })
            OmniInfoLine("transform", "转换目标",
                "${state.targetFormat.label} (Quality ${state.quality}%)")
            OmniInfoLine("list", "队列长度", "${state.totalCount} 个")
        }

        OmniStaticCard("view_list", "批量转换队列", "${state.processedCount} / ${state.totalCount} 已处理") {
            if (state.queueItems.isEmpty()) {
                Text("任务准备中…", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.queueItems.forEach { item -> QueueRow(item) }
            }
        }

        Column(Modifier.padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp)) {
            OmniPrimaryButton(
                text = "查看转换报告",
                onClick = { onEvent(FormatConverterEvent.PageChanged(ConverterPage.REPORT)) },
                icon = "analytics",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OmniPrimaryButton(
                text = "取消当前任务",
                onClick = { onEvent(FormatConverterEvent.CancelConversion) },
                icon = "stop",
                modifier = Modifier.fillMaxWidth(),
                enabled = state.isRunning
            )
        }
    }
}

@Composable
private fun QueueRow(item: QueueItem) {
    val done = item.status == "已完成"
    val failed = item.status == "失败"
    val tint = when {
        failed -> MaterialTheme.colorScheme.error
        done -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = OmniShape.CardSmall
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                OmniIcon.of(if (failed) "warning" else if (done) "check_circle" else "hourglass_top"),
                contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (failed) "失败：${item.reason ?: "未知原因"}"
                    else "${Formatters.bytes(item.sourceBytes)} → ${Formatters.bytes(item.outputBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(item.status, style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
}

/* ---------------- 转换报告屏 (_7) ---------------- */

@Composable
private fun ReportPage(
    state: FormatConverterUiState,
    onEvent: (FormatConverterEvent) -> Unit,
    onRequestExport: (String, String) -> Unit
) {
    val outputs = state.outputs
    val successes = outputs.filter { !it.mimeType.startsWith("error/") }
    val failures = outputs.filter { it.mimeType.startsWith("error/") }
    val totalSource = outputs.sumOf { it.sourceBytes }
    val totalOutput = outputs.sumOf { it.outputBytes }
    val savingsRatio = if (totalSource > 0) {
        (totalSource - totalOutput).toDouble() / totalSource.toDouble()
    } else 0.0
    val outputRatio = if (totalSource > 0) {
        (totalOutput.toDouble() / totalSource.toDouble()).toFloat().coerceIn(0f, 1f)
    } else 0f

    Column(Modifier.fillMaxWidth()) {
        // Hero
        OmniStaticCard(
            "task_alt",
            if (state.taskStatus == "failed") "转换完成（含失败项）" else "转换成功 · 全部就绪",
            "本地引擎处理完毕，数据未离开设备"
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportPill("check_circle", "${successes.size} 个文件成功", Modifier.weight(1f))
                ReportPill("timer", "耗时 ${state.taskDurationMs} ms", Modifier.weight(1f))
                ReportPill("cloud_off", "纯本地沙盒", Modifier.weight(1f))
            }
            OmniInfoLine("list", "产物总数", "${outputs.size} 项 (失败 ${failures.size} 项)")
            OmniInfoLine("history", "任务状态", state.taskStatus.ifBlank { "—" })
        }

        // 效能与体积节省总览（全部来自 Room 查询出的 outputs）
        OmniStaticCard("pie_chart", "效能与体积节省总览", "来自 Room conversion_outputs") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("原始体积", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formatters.bytes(totalSource), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                }
                Icon(OmniIcon.of("trending_down"), contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Column(horizontalAlignment = Alignment.End) {
                    Text("转码后产物体积", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formatters.bytes(totalOutput), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("体积变化 ${Formatters.percent(savingsRatio)} · 产物占比 ${Formatters.percent(outputRatio.toDouble())}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(OmniShape.Pill)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(outputRatio)
                        .height(10.dp)
                        .clip(OmniShape.Pill)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Spacer(Modifier.height(10.dp))
            OmniInfoLine("storage", "节省空间", Formatters.bytes(totalSource - totalOutput))
            OmniInfoLine("list", "源体积合计", Formatters.bytes(totalSource))
            OmniInfoLine("storage", "产物体积合计", Formatters.bytes(totalOutput))
        }

        // 生成文件清单（来自 Room）
        OmniStaticCard("folder_special", "生成文件清单", "${outputs.size} 项 · Room 查询") {
            if (outputs.isEmpty()) {
                Text("该任务暂无产物记录。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                outputs.forEach { output -> OutputRow(output, onRequestExport) }
            }
        }

        // 批量分发与导出
        OmniStaticCard("download", "批量分发与导出", "SAF CreateDocument · 逐个导出") {
            Text("多文件产物请用清单中的「导出」按钮逐个保存到指定目录；" +
                "也可一键导出最后一个成功产物。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            val last = successes.lastOrNull()
            OmniPrimaryButton(
                text = if (last != null) "导出最后一个产物" else "暂无可导出产物",
                onClick = { if (last != null) onRequestExport(last.fileName, last.mimeType) },
                icon = "share",
                modifier = Modifier.fillMaxWidth(),
                enabled = last != null
            )
        }

        Column(Modifier.padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp)) {
            OmniPrimaryButton(
                text = "继续批量转换新文件",
                onClick = { onEvent(FormatConverterEvent.PageChanged(ConverterPage.SETUP)) },
                icon = "add_circle",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OmniPrimaryButton(
                text = "查看转码历史日志 (Room DB)",
                onClick = { onEvent(FormatConverterEvent.PageChanged(ConverterPage.HISTORY)) },
                icon = "history",
                modifier = Modifier.fillMaxWidth()
            )
        }

        OmniStaticCard("verified_user", "离线沙盒存储与隐私生命周期", "零网络权限") {
            Text("所有转码产物安全留存于应用私有缓存，转换任务与逐文件明细持久化于 Room 数据库，" +
                "报告数值均从本地查询结果实时聚合，无任何写死数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReportPill(icon: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = OmniShape.Pill) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(OmniIcon.of(icon), contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun OutputRow(output: ConversionOutputEntity, onRequestExport: (String, String) -> Unit) {
    val failed = output.mimeType.startsWith("error/")
    val savedRatio = if (output.sourceBytes > 0) {
        (output.sourceBytes - output.outputBytes).toDouble() / output.sourceBytes.toDouble()
    } else 0.0
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        color = if (failed) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = OmniShape.CardSmall
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(OmniIcon.of(if (failed) "warning" else "image"),
                    contentDescription = null,
                    tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(output.fileName, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold)
                    Text(output.mimeType, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (failed) {
                Text("失败原因：${output.mimeType.substringAfter("error/")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
                Text("源体积 ${Formatters.bytes(output.sourceBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${Formatters.bytes(output.sourceBytes)} → ${Formatters.bytes(output.outputBytes)}",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = OmniShape.Pill) {
                        Text("减小 ${Formatters.percent(savedRatio)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OmniPrimaryButton(
                    text = "导出此文件",
                    onClick = { onRequestExport(output.fileName, output.mimeType) },
                    icon = "download",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/* ---------------- 历史任务列表 ---------------- */

@Composable
private fun HistoryPage(state: FormatConverterUiState, onEvent: (FormatConverterEvent) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        OmniStaticCard("history_edu", "转码历史日志", "Room DB · ${state.tasks.size} 条") {
            Text("点击任一任务可查看由 Room 查询出的逐文件转换报告。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (state.tasks.isEmpty()) {
                Text("暂无历史任务，先完成一次转换即可生成记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.tasks.forEach { task -> TaskRow(task) { onEvent(FormatConverterEvent.ViewTask(task.id)) } }
            }
        }
        Column(Modifier.padding(OmniDimens.ScreenPadding)) {
            OmniPrimaryButton(
                text = "返回格式选择",
                onClick = { onEvent(FormatConverterEvent.PageChanged(ConverterPage.SETUP)) },
                icon = "arrow_back",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TaskRow(task: TranscodeTaskEntity, onClick: () -> Unit) {
    val tint = when (task.status) {
        "succeeded" -> MaterialTheme.colorScheme.primary
        "failed" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .clip(OmniShape.CardSmall).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = OmniShape.CardSmall
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(OmniIcon.of("history"), contentDescription = null, tint = tint)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${task.sourceName} → ${task.targetFormat}",
                    style = MaterialTheme.typography.bodyMedium)
                Text("状态 ${task.status} · 进度 ${task.progress}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(OmniIcon.of("chevron_right"), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
