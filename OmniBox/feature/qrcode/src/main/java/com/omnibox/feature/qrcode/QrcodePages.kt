package com.omnibox.feature.qrcode

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnibox.core.designsystem.*

/* ------------------------------ 离线生成页 (zxing) ------------------------------ */

@Composable
internal fun GeneratePage(
    state: QrcodeUiState,
    onEvent: (QrcodeEvent) -> Unit,
    onExportPng: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val config = state.genConfig
    val imageBitmap = remember(state.generatedBitmap) { state.generatedBitmap?.asImageBitmap() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        QrCard("security", "本地 ZXing 引擎运行中 · 内存级沙盒处理", "100% 离线") {
            Text("所有字符矩阵运算与位图栅格化均在设备 CPU 内完成，不会产生任何网络调用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .size(220.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(OmniShape.Input)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (imageBitmap != null) {
                    Image(bitmap = imageBitmap, contentDescription = "生成的二维码",
                        modifier = Modifier.fillMaxSize())
                } else {
                    Text("实时渲染画布 · 输入内容后点击生成",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            QrInfoLine("high_quality", "矢量无损 / 光栅", "${config.exportSize}×${config.exportSize} px")
            QrInfoLine("verified", "容错等级", "${config.ecLevel.label} (${config.ecLevel.percent})")
            QrInfoLine("memory", "编码与内核", "UTF-8 · ZXing 3.5.3")
        }

        QrCard("edit_note", "编码内容", "预设示例") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QR_PRESET_SAMPLES.take(3).forEach { (label, sample) ->
                    QrMiniChip(label, false) { onEvent(QrcodeEvent.GenTextChanged(sample)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QR_PRESET_SAMPLES.drop(3).forEach { (label, sample) ->
                    QrMiniChip(label, false) { onEvent(QrcodeEvent.GenTextChanged(sample)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = config.text,
                onValueChange = { onEvent(QrcodeEvent.GenTextChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                shape = OmniShape.Input,
                placeholder = { Text("输入文本、网址、Wi-Fi 配置或名片内容") }
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${config.text.length} / 1000 字符",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    onEvent(QrcodeEvent.GenTextChanged(clipboard.getText()?.text.orEmpty()))
                }) { Text("粘贴剪贴板") }
            }
        }

        QrCard("palette", "外观与容错调优", "ZXing 引擎实时映射") {
            OmniLabel("容错率等级 (Error Correction) · 推荐嵌 Logo: H (30%)")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QrEcLevel.entries.forEach { level ->
                    QrOptionChip(level.label, level.percent, config.ecLevel == level,
                        Modifier.weight(1f)) { onEvent(QrcodeEvent.SetEcLevel(level)) }
                }
            }
            Spacer(Modifier.height(14.dp))
            OmniLabel("颜色配比 (前景色 / 码点)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QR_FOREGROUND_OPTIONS.forEach { opt ->
                    val selected = config.fgArgb == opt.argb
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(OmniShape.Input)
                            .clickable { onEvent(QrcodeEvent.SetForeground(opt.argb)) }
                            .padding(4.dp)
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(argbToComposeColor(opt.argb))
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                )
                        )
                        Text(opt.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            QrInfoLine("colorize", "当前前景色", "#%06X".format(config.fgArgb and 0xFFFFFF))
            Spacer(Modifier.height(8.dp))
            QrSwitchLine("背景底色透明化", "PNG 镂空，适于排版嵌入海报",
                config.bgTransparent) { onEvent(QrcodeEvent.ToggleTransparentBg) }
            Spacer(Modifier.height(14.dp))
            OmniLabel("边缘安全留白 (Quiet Zone)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QrMiniChip("0x (紧凑)", config.quietZone == 0) { onEvent(QrcodeEvent.SetQuietZone(0)) }
                QrMiniChip("2x (标准)", config.quietZone == 2) { onEvent(QrcodeEvent.SetQuietZone(2)) }
                QrMiniChip("4x (ISO规范)", config.quietZone == 4) { onEvent(QrcodeEvent.SetQuietZone(4)) }
            }
            Spacer(Modifier.height(14.dp))
            OmniLabel("导出格式与分辨率")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(512, 1024, 2048).forEach { size ->
                    QrMiniChip("${size}px", config.exportSize == size) {
                        onEvent(QrcodeEvent.SetExportSize(size))
                    }
                }
            }
            Text("PNG 最通用的光栅位图，印刷清晰",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(Modifier.padding(horizontal = OmniDimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OmniPrimaryButton(
                if (state.generating) "生成中…" else "生成二维码",
                onClick = { onEvent(QrcodeEvent.Generate) },
                enabled = !state.generating && config.text.isNotBlank(),
                icon = "qr_code_2",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(horizontal = OmniDimens.ScreenPadding)) {
            OmniPrimaryButton("保存到系统相册 (PNG)", onClick = onExportPng,
                icon = "download", modifier = Modifier.fillMaxWidth())
        }

        QrCard("shield", "纯本地 ZXing 内核保障", "") {
            Text("所有字符矩阵运算与位图栅格化均在设备 CPU 内完成。不会产生任何网络调用，" +
                "保护您的凭据、个人名片与 WiFi 口令完全私密。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ------------------------------ 导出配置页 (_11) ------------------------------ */

@Composable
internal fun ExportConfigPage(state: QrcodeUiState, onEvent: (QrcodeEvent) -> Unit) {
    val config = state.genConfig
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        QrCard("tune", "二维码生成与导出配置", "本地 ZXing 核心即时渲染 · 毫秒级重绘") {
            Text("以下每一项都会直接参与下一次 QRCodeWriter.encode 的真实参数。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        QrCard("verified", "容错率等级 (ECC Level)", "高可靠级推荐") {
            QrEcLevel.entries.forEach { level ->
                val selected = config.ecLevel == level
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = OmniShape.CardSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(OmniShape.CardSmall)
                        .clickable { onEvent(QrcodeEvent.SetEcLevel(level)) }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${level.label} (${level.percent})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text(level.desc, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selected) {
                            Icon(OmniIcon.of("check_circle"), contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("提示：高容错等级会在同样尺寸下增加码点密度；内容较长时请优先选 L 级以保证可识读。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        QrCard("palette", "色彩与底衬", "前景色 / 透明背景") {
            OmniLabel("前景色 (码点色彩)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QR_FOREGROUND_OPTIONS.forEach { opt ->
                    val selected = config.fgArgb == opt.argb
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(OmniShape.Input)
                            .clickable { onEvent(QrcodeEvent.SetForeground(opt.argb)) }
                            .padding(4.dp)
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(argbToComposeColor(opt.argb))
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                )
                        )
                        Text(opt.hex, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            QrSwitchLine("透明背景 (PNG / SVG 镂空)", "导出无底色文件，适于排版嵌入海报",
                config.bgTransparent) { onEvent(QrcodeEvent.ToggleTransparentBg) }
        }

        QrCard("straighten", "导出尺寸与安全静区", "画布导出像素 / Quiet Zone") {
            OmniLabel("画布导出像素 (Canvas Export Size)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(512, 1024, 2048).forEach { size ->
                    QrMiniChip("${size}px", config.exportSize == size) {
                        onEvent(QrcodeEvent.SetExportSize(size))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OmniLabel("周围静区留白宽度 (Quiet Zone)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QrMiniChip("0x (贴边紧凑)", config.quietZone == 0) { onEvent(QrcodeEvent.SetQuietZone(0)) }
                QrMiniChip("2x (推荐标准)", config.quietZone == 2) { onEvent(QrcodeEvent.SetQuietZone(2)) }
                QrMiniChip("4x (ISO规范)", config.quietZone == 4) { onEvent(QrcodeEvent.SetQuietZone(4)) }
            }
        }

        QrCard("qr_code_2", "当前输出规格预估", "真实参数") {
            QrInfoLine("description", "位图规格", "${config.exportSize}px PNG")
            QrInfoLine("verified", "容错 / 静区",
                "ECC ${config.ecLevel.label} (${config.ecLevel.percent}) · ${config.quietZone} 模块")
            QrInfoLine("palette", "前景 / 背景",
                "#%06X".format(config.fgArgb and 0xFFFFFF) +
                    if (config.bgTransparent) " · 透明底" else " · 白底")
        }

        Row(Modifier.padding(horizontal = OmniDimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OmniPrimaryButton("恢复默认", onClick = { onEvent(QrcodeEvent.ResetGenDefaults) },
                icon = "restart_alt", modifier = Modifier.weight(1f))
            OmniPrimaryButton("应用并即时渲染", onClick = { onEvent(QrcodeEvent.ApplyExportConfig) },
                icon = "check", modifier = Modifier.weight(1f))
        }
    }
}

/* ------------------------------ 本地历史页 (_2) ------------------------------ */

@Composable
internal fun HistoryPage(
    state: QrcodeUiState,
    onEvent: (QrcodeEvent) -> Unit,
    onExportCsv: () -> Unit,
    clipboardText: (String) -> Unit
) {
    fun countOf(key: String): Int = if (key == "all") state.history.size
    else state.history.count { QrcodeEngine.classify(it.content) == key }

    val filtered = state.history.filter { item ->
        state.historyFilter == QrHistoryFilter.ALL ||
            QrcodeEngine.classify(item.content) == state.historyFilter.key
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        QrCard("checklist", "本地沙盒数据库 (Room v2.6)", "批量管理") {
            Text("记录仅留存于 Android App Data 分区，永不上报云端。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                QrHistoryFilter.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { filter ->
                            QrMiniChip("${filter.label} (${countOf(filter.key)})",
                                state.historyFilter == filter) {
                                onEvent(QrcodeEvent.SetHistoryFilter(filter))
                            }
                        }
                    }
                }
            }
        }

        QrCard("enhanced_encryption", "本地硬件加密快照已就绪", "SafeBox") {
            Text("记录仅留存于 Android App Data 分区，永不上报云端；共 ${state.history.size} 条元数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (filtered.isEmpty()) {
            QrCard("history", "本地识读历史", "暂无记录") {
                Text("当前筛选下没有记录。扫码、相册解析或生成二维码后会自动写入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            filtered.forEach { item ->
                QrHistoryRow(item, clipboardText)
            }
        }

        QrCard("offline_bolt", "导出与离线备份", "零网络流量消耗") {
            Text("导出 CSV 报表 · 包含时间戳、编码格式与原始文本列 · 符合 Excel / WPS",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OmniPrimaryButton("导出 CSV 报表", onClick = onExportCsv,
                icon = "table_chart", modifier = Modifier.fillMaxWidth())
        }

        Row(Modifier.padding(horizontal = OmniDimens.ScreenPadding)) {
            OmniPrimaryButton("一键清空", onClick = { onEvent(QrcodeEvent.ClearHistory) },
                icon = "delete_sweep", modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        Text("纯本地 Room 数据库沙盒存储 · 绝不出设备",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = OmniDimens.ScreenPadding))
    }
}

@Composable
private fun QrHistoryRow(item: QrHistoryItem, clipboardText: (String) -> Unit) {
    val sourceLabel = when (item.source) {
        "camera" -> "相机实时扫码"
        "image" -> "相册图片解析"
        "generated" -> "本地生成"
        else -> item.source
    }
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = OmniShape.CardSmall
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(OmniIcon.of("qr_code_2"), contentDescription = null,
                    modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(item.format, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Text(sourceLabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(item.content, style = MaterialTheme.typography.bodyMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatQrTime(item.createdAt), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                OmniIconAction("content_copy", "复制") { clipboardText(item.content) }
            }
        }
    }
}
