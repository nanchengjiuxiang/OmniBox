package com.omnibox.feature.exif

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import com.omnibox.core.designsystem.*

/**
 * 对照 exif_2/code.html（风险项报告）与 exif_1/code.html（擦除配置）。
 * 两屏用页面内状态切换；所有条目的值均来自 ExifInterface 真实读到的 tag。
 */
@Composable
fun ExifRoute(onBack: () -> Unit) {
    val viewModel: ExifViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()

    val pickContract = remember { ActivityResultContracts.GetContent() }
    val pickImage = rememberLauncherForActivityResult(pickContract) { uri ->
        if (uri != null) viewModel.onEvent(ExifEvent.ImageSelected(uri))
    }
    // 产物固定为重新编码的 JPEG，mimeType 稳定
    val exportContract = remember { ActivityResultContracts.CreateDocument("image/jpeg") }
    val exportDoc = rememberLauncherForActivityResult(exportContract) { uri ->
        if (uri != null) viewModel.onEvent(ExifEvent.ExportTo(uri))
    }

    ExifContent(
        state = state,
        onEvent = viewModel::onEvent,
        onPick = { pickImage.launch("image/*") },
        onExport = { exportDoc.launch(viewModel.exportFileName()) },
        onBack = onBack
    )
}

@Composable
private fun ExifContent(
    state: ExifUiState,
    onEvent: (ExifEvent) -> Unit,
    onPick: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        item {
            OmniTopBar(
                title = if (state.page == ExifPage.CONFIG) "EXIF 深度脱敏配置" else "EXIF 隐私擦除",
                subtitle = if (state.page == ExifPage.CONFIG) "exif_clean_config" else "exif_risk_report",
                navigationIcon = OmniIcon.of("arrow_back"),
                onNavigationClick = onBack
            ) {
                IconButton(onClick = onPick) {
                    Icon(OmniIcon.of("photo_library"), contentDescription = "更换图片")
                }
                IconButton(onClick = {
                    onEvent(
                        ExifEvent.PageChanged(
                            if (state.page == ExifPage.CONFIG) ExifPage.REPORT else ExifPage.CONFIG
                        )
                    )
                }) {
                    Icon(OmniIcon.of("tune"), contentDescription = "切换报告 / 配置")
                }
            }
        }

        if (state.isProcessing) {
            item {
                OmniStaticCard("sync", "正在处理", "本地解析中") {
                    Text("读取真实 EXIF 标签 / 重新编码输出…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                OmniStaticCard("check_circle", "操作完成", state.message ?: "") {
                    TextButton(onClick = { onEvent(ExifEvent.DismissMessage) }) { Text("知道了") }
                }
            }
        }

        if (state.scan == null) {
            item {
                OmniStaticCard("document_scanner", "尚未载入照片", "请选择一张图片读取真实 EXIF") {
                    Text("使用 AndroidX ExifInterface 读取原始标签；读不到的项目显示「无此元数据」，绝不编造。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OmniPrimaryButton(text = "选择图片", onClick = onPick, icon = "add",
                        modifier = Modifier.fillMaxWidth())
                }
            }
        } else if (state.page == ExifPage.CONFIG) {
            item { ConfigPage(state, onEvent, onPick) }
        } else {
            item { ReportPage(state, onEvent, onExport, onPick) }
        }
    }
}

/* ---------------- 报告屏 (exif_risk_report) ---------------- */

@Composable
private fun ReportPage(
    state: ExifUiState,
    onEvent: (ExifEvent) -> Unit,
    onExport: () -> Unit,
    onPick: () -> Unit
) {
    val scan = state.scan ?: return
    Column(Modifier.fillMaxWidth()) {
        // 高风险状态横幅
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = OmniShape.CardSmall
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(OmniIcon.of("warning"), contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(10.dp))
                    Text("已检出 ${scan.sensitiveCount} 项高度敏感隐私",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f))
                    Surface(color = MaterialTheme.colorScheme.error, shape = OmniShape.Pill) {
                        Text("高风险暴露", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("原图中包含精确物理地理坐标、唯一硬件序列号及完整拍摄时间戳，直接分享可能泄露居所与设备轨迹。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        // 源文件概览
        OmniStaticCard("raw_on", state.sourceName.ifBlank { "所选照片" }, "EXIF 原始数据诊断清单") {
            OmniInfoLine("storage", "文件体积", Formatters.bytes(scan.sourceBytes))
            OmniInfoLine("straighten", "像素尺寸", "${scan.width} × ${scan.height}")
            OmniInfoLine("tag", "已解析标签", "${scan.presentCount} 个 (共 ${scan.items.size} 项检查)")
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                ScanMetric("${scan.sensitiveCount} 项高危", "敏感个人数据",
                    MaterialTheme.colorScheme.error, Modifier.weight(1f))
                ScanMetric("${scan.safeCount} 项无害", "摄影曝光参数",
                    MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                ScanMetric("${scan.items.size} 项检查", "本地离线计算",
                    MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            }
        }

        // 逐类别诊断卡片
        ExifCategory.entries.forEach { category ->
            val group = scan.items.filter { it.category == category }
            OmniStaticCard(category.icon, category.label, "Total: ${group.size} Tags") {
                group.forEach { item ->
                    RiskRow(item)
                }
            }
        }

        // 擦除后验证报告（来自对输出字节流的二次读取）
        val result = state.cleanResult
        if (result != null) {
            OmniStaticCard(
                "verified_user",
                if (result.verifiedClean) "输出中已无 EXIF 风险项" else "输出中仍残留敏感元数据",
                "前后对比 · 真实复检"
            ) {
                OmniInfoLine("gps_off", "擦除前敏感项", "${result.before.sensitiveCount} 项")
                OmniInfoLine("gps_off", "擦除后敏感项", "${result.after.sensitiveCount} 项")
                OmniInfoLine("delete_sweep", "已移除敏感项", "${result.removedRiskCount} 项")
                OmniInfoLine("check_circle", "保留的标签", "${result.keptTagCount} 个")
                OmniInfoLine("storage", "纯净副本体积", Formatters.bytes(result.resultBytes))
                Spacer(Modifier.height(10.dp))
                val removedRatio = if (result.before.sensitiveCount > 0) {
                    (result.removedRiskCount.toFloat() / result.before.sensitiveCount).coerceIn(0f, 1f)
                } else 1f
                Text("敏感项移除率 ${Formatters.percent(removedRatio.toDouble())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            .fillMaxWidth(removedRatio)
                            .height(8.dp)
                            .clip(OmniShape.Pill)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                Spacer(Modifier.height(12.dp))
                OmniPrimaryButton(
                    text = "导出纯净副本 (SAF)",
                    onClick = onExport,
                    icon = "download",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 操作区
        Column(Modifier.padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp)) {
            OmniPrimaryButton(
                text = "一键生成安全纯净副本",
                onClick = { onEvent(ExifEvent.Clean) },
                icon = "security_update_good",
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isProcessing
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OmniPrimaryButton(text = "隐私脱敏配置", onClick = {
                    onEvent(ExifEvent.PageChanged(ExifPage.CONFIG))
                }, icon = "tune", modifier = Modifier.weight(1f))
                OmniPrimaryButton(text = "更换照片", onClick = onPick,
                    icon = "photo_library", modifier = Modifier.weight(1f))
            }
        }

        OmniStaticCard("shield", "100% 本地沙盒保护", "AndroidX ExifInterface") {
            Text("解码为 Bitmap 后重新编码输出，新文件天然不含原始 EXIF；" +
                "再用 ExifInterface 复检输出字节流生成前后对比。全过程零联网，源图保持不变。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScanMetric(value: String, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RiskRow(item: ExifRiskItem) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.tagLabel, style = MaterialTheme.typography.bodyMedium)
            Text(item.displayValue, style = MaterialTheme.typography.labelSmall,
                color = if (item.present) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        RiskChip(item.riskLevel, item.present)
    }
}

@Composable
private fun RiskChip(level: ExifRiskLevel, present: Boolean) {
    val (bg, fg) = when {
        !present -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
        level == ExifRiskLevel.HIGH || level == ExifRiskLevel.MEDIUM ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        level == ExifRiskLevel.NONE ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = bg, shape = OmniShape.Pill) {
        Text(if (present) level.label else "无此元数据",
            style = MaterialTheme.typography.labelSmall, color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

/* ---------------- 配置屏 (exif_clean_config) ---------------- */

@Composable
private fun ConfigPage(
    state: ExifUiState,
    onEvent: (ExifEvent) -> Unit,
    onPick: () -> Unit
) {
    val scan = state.scan ?: return
    val c = state.config
    Column(Modifier.fillMaxWidth()) {
        // 顶部操作条
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = OmniShape.CardSmall
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(OmniIcon.of("security"), contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("隐私深度脱敏", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { onEvent(ExifEvent.Clean) }) {
                    Text("一键安全脱敏导出")
                }
            }
        }

        // 元数据概览
        OmniStaticCard("raw_on", state.sourceName.ifBlank { "所选照片" }, "已解析 ${scan.presentCount} 个标签") {
            OmniInfoLine("storage", "文件体积", Formatters.bytes(scan.sourceBytes))
            OmniInfoLine("straighten", "像素尺寸", "${scan.width} × ${scan.height}")
            OmniInfoLine("tag", "EXIF 标签", "${scan.presentCount} / ${scan.items.size} 项")
            Spacer(Modifier.height(8.dp))
            OmniPrimaryButton(text = "更换源照片", onClick = onPick, icon = "photo_library",
                modifier = Modifier.fillMaxWidth())
        }

        // 高危敏感隐私项
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = OmniShape.CardSmall
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(OmniIcon.of("warning"), contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("高危敏感隐私项", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                        Text("包含可精确定位现实轨迹与实体设备特征的元数据",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    TextButton(onClick = { onEvent(ExifEvent.ResetDefaults) }) {
                        Text("恢复推荐配置")
                    }
                }
                Spacer(Modifier.height(8.dp))
                ConfigSwitch("擦除 GPS 地理与海拔数据", "去除经纬度及定位日期，保留光学曝光参数",
                    c.stripGps, !c.deepClean) { onEvent(ExifEvent.Toggle(ExifConfigField.GPS)) }
                ConfigSwitch("清除设备型号与唯一序列码", "抹去 Manufacturer / Model / Serial Number",
                    c.stripDevice, !c.deepClean) { onEvent(ExifEvent.Toggle(ExifConfigField.DEVICE)) }
                ConfigSwitch("擦除原始拍摄时间戳", "抹去 DateTimeOriginal / DateTime",
                    c.stripTime, !c.deepClean) { onEvent(ExifEvent.Toggle(ExifConfigField.TIME)) }
                ConfigSwitch("移除软件与作者标签", "清除 Software / Artist / UserComment",
                    c.stripSoftware, !c.deepClean) { onEvent(ExifEvent.Toggle(ExifConfigField.SOFTWARE)) }
                ConfigSwitch("移除尺寸与方向信息", "去除 ImageWidth / ImageLength / Orientation",
                    c.stripDimensions, !c.deepClean) { onEvent(ExifEvent.Toggle(ExifConfigField.DIMENSIONS)) }
                Spacer(Modifier.height(6.dp))
                ConfigSwitch("彻底抹除全部元数据 (深度脱敏)", "覆盖以上全部保留项，输出零 EXIF",
                    c.deepClean, true) { onEvent(ExifEvent.Toggle(ExifConfigField.DEEP_CLEAN)) }
            }
        }

        // 无害摄影光学参数（真实读取值）
        OmniStaticCard("photo_camera", "无害摄影光学参数", "来自实际读取的 EXIF 标签") {
            OmniSwitchLine(
                label = "保留光学参数",
                hint = "勾选后会把下列真实参数写回纯净副本",
                checked = c.keepPhotographyParams && !c.deepClean
            ) { onEvent(ExifEvent.Toggle(ExifConfigField.KEEP_PHOTOGRAPHY)) }
            Spacer(Modifier.height(10.dp))
            val chunks = scan.artParams.chunked(3)
            chunks.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, value) ->
                        ArtParamTile(label, value, Modifier.weight(1f))
                    }
                    for (i in row.size until 3) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // 导出规则
        OmniStaticCard("output", "脱敏导出规则配置", "另存为纯净副本 (推荐)") {
            OmniInfoLine("folder_special", "输出方式", "SAF CreateDocument · 无需存储权限")
            OmniInfoLine("verified_user", "源图保护", "重新编码产物，绝不修改用户原图")
            OmniInfoLine("lock", "零网络", "本地沙盒计算 · 无云端回传")
        }

        Column(Modifier.padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp)) {
            OmniPrimaryButton(
                text = "确认配置并生成纯净副本",
                onClick = { onEvent(ExifEvent.Clean) },
                icon = "shield_with_heart",
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isProcessing
            )
            Spacer(Modifier.height(8.dp))
            OmniPrimaryButton(text = "返回风险报告", onClick = {
                onEvent(ExifEvent.PageChanged(ExifPage.REPORT))
            }, icon = "arrow_back", modifier = Modifier.fillMaxWidth())
        }

        OmniStaticCard("cloud_off", "100% 本地沙盒保护", "零内存拷贝 · 硬件隔离区执行") {
            Text("基于 AndroidX ExifInterface 读取真实标签，解码后重新编码输出，" +
                "并对输出字节流二次复检生成前后对比报告。全过程无需联网。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConfigSwitch(label: String, hint: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(hint, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun ArtParamTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = OmniShape.CardSmall) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
