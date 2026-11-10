package com.omnibox.feature.battery

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnibox.core.designsystem.*
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
fun BatteryRoute(onBack: () -> Unit) {
    val viewModel: BatteryViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(2600)
            viewModel.onEvent(BatteryEvent.ClearMessage)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) viewModel.onEvent(BatteryEvent.ExportLog(uri))
    }

    // 开启任一提醒前先拿通知权限：否则 API 33+ 上服务会跑但提醒静默失效
    val context = LocalContext.current
    val notifyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) viewModel.onEvent(BatteryEvent.ShowMessage("未授予通知权限，提醒将只在应用内生效"))
    }
    val dispatch: (BatteryEvent) -> Unit = { event ->
        val enabling = when (event) {
            is BatteryEvent.TargetAlertChanged -> event.value
            is BatteryEvent.TempAlertChanged -> event.value
            is BatteryEvent.FullVibrateChanged -> event.value
            else -> false
        }
        val needPermission = enabling &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needPermission) notifyPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        viewModel.onEvent(event)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .omniStatusBarInset()
            .omniNavBarInset()
    ) {
        OmniTopBar(
            title = "电池健康与充电",
            subtitle = "BatteryManager + sysfs · 本地实时",
            navigationIcon = OmniIcon.of("arrow_back"),
            onNavigationClick = onBack
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OmniDimens.ScreenPadding)
                .padding(bottom = OmniDimens.Grid3),
            verticalArrangement = Arrangement.spacedBy(OmniDimens.Grid2)
        ) {
            ChargingHeroCard(state)
            PowerWaveformCard(state)
            SpecSheetCard(state.snapshot)
            GuardSettingsCard(state, dispatch)
            Row(horizontalArrangement = Arrangement.spacedBy(OmniDimens.Grid1)) {
                QuickActionButton(
                    icon = "cached", text = "重新读取电量计",
                    modifier = Modifier.weight(1f)
                ) { viewModel.onEvent(BatteryEvent.RereadGauge) }
                QuickActionButton(
                    icon = "table_chart", text = "导出充放电日志",
                    modifier = Modifier.weight(1f),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    exportLauncher.launch("omnibox-battery-${System.currentTimeMillis()}.csv")
                }
            }
            Row(
                Modifier.align(Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    OmniIcon.of("lock"), contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "基于 Android BatteryManager & sysfs 驱动层读取 · 纯本地免 Root",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
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
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    }
}

/** 顶部充电主卡：仪表盘 + 实时电参数 + 健康度，全部真值。 */
@Composable
private fun ChargingHeroCard(state: BatteryUiState) {
    val snapshot = state.snapshot
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(OmniDimens.Grid2)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer))
                        Icon(
                            OmniIcon.of("bolt"), contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(OmniDimens.Grid1))
                    Column {
                        Text(
                            when {
                                snapshot == null -> "正在读取…"
                                snapshot.status == BatteryStatus.CHARGING ->
                                    "充电进行中 · ${snapshot.plugged.label}"
                                snapshot.status == BatteryStatus.FULL -> "充电完成"
                                snapshot.plugged != PowerSource.NONE -> "已连接电源（未充电）"
                                else -> "电池放电中"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            snapshot?.let {
                                when (it.status) {
                                    BatteryStatus.CHARGING -> "实测输入功率 ${formatWatts(it.powerW)}"
                                    BatteryStatus.FULL -> "已停止输入，维持满电"
                                    else -> "实测放电功率 ${formatWatts(it.powerW)}"
                                }
                            } ?: "BatteryManager",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                snapshot?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = OmniShape.Pill
                    ) {
                        Text(
                            if (it.temperatureC.isNaN()) "温度未知"
                            else "${formatCelsius(it.temperatureC)} " +
                                if (it.temperatureC >= 40) "偏高" else "正常",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(OmniDimens.Grid2))

            Row(
                horizontalArrangement = Arrangement.spacedBy(OmniDimens.Grid2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BatteryGauge(
                    percent = snapshot?.levelPercent ?: 0,
                    label = when (snapshot?.status) {
                        BatteryStatus.CHARGING -> "充入中"
                        BatteryStatus.FULL -> "已充满"
                        null -> "读取中"
                        else -> "放电中"
                    }
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "实时功率",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                snapshot?.let { formatWatts(it.powerW) } ?: "—",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = {
                                val peak = max(state.peakW ?: 1.0, 1.0)
                                ((snapshot?.powerW ?: 0.0) / peak).toFloat().coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 10.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(OmniDimens.Grid1)) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "端子电压",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    snapshot?.takeIf { it.voltageMv > 0 }
                                        ?.let { String.format("%,d mV", it.voltageMv) } ?: "—",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "充放电电流",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    snapshot?.takeIf { it.currentMicroA != 0L }
                                        ?.let { formatMilliAmps(it.currentMicroA) } ?: "—",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(OmniDimens.Grid2))

            Row(horizontalArrangement = Arrangement.spacedBy(OmniDimens.Grid1)) {
                HealthTile(
                    icon = "favorite",
                    label = "电池健康度",
                    value = snapshot?.stateOfHealthPercent?.let { "$it%" }
                        ?: (snapshot?.health?.label ?: "—"),
                    sub = snapshot?.chargeFullMicroAh
                        ?.let { "（实测满充 ${it / 1000} mAh）" } ?: "（精确 SoH 需 sysfs）",
                    modifier = Modifier.weight(1f)
                )
                HealthTile(
                    icon = "autorenew",
                    label = "完整循环次数",
                    value = snapshot?.cycleCount?.toString() ?: "未上报",
                    sub = "（需内核暴露 cycle_count）",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BatteryGauge(percent: Int, label: String) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        Modifier.size(112.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2 + 2.dp.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = stroke
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * percent.coerceIn(0, 100) / 100f,
                useCenter = false,
                topLeft = topLeft, size = arcSize, style = stroke
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$percent",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun HealthTile(icon: String, label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer))
                Icon(
                    OmniIcon.of(icon), contentDescription = null,
                    tint = if (icon == "favorite") MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** 功率波形：本会话 1Hz 实测序列（最长 15 分钟）。 */
@Composable
private fun PowerWaveformCard(state: BatteryUiState) {
    val lineColor = MaterialTheme.colorScheme.primary
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(OmniDimens.Grid2)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        OmniIcon.of("show_chart"), contentDescription = null,
                        tint = lineColor, modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "充电功率波动曲线（本会话）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    "1.0 Hz 采样 · ${state.samples.size} 点",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(OmniDimens.Grid1))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            ) {
                val samples = state.samples
                if (samples.size < 2) {
                    // 空态：采样尚未积累，诚实提示而非画假曲线
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.3f),
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 2f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                    )
                    return@Canvas
                }
                val maxW = max(samples.maxOf { it.powerW }, 1.0).toFloat()
                val stepX = size.width / (samples.size - 1)
                val path = Path()
                val fill = Path()
                samples.forEachIndexed { i, s ->
                    val x = i * stepX
                    val y = size.height - (s.powerW.toFloat() / maxW) * (size.height * 0.9f) - size.height * 0.05f
                    if (i == 0) {
                        path.moveTo(x, y)
                        fill.moveTo(x, size.height)
                        fill.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fill.lineTo(x, y)
                    }
                }
                fill.lineTo(size.width, size.height)
                fill.close()
                drawPath(
                    fill,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.32f), lineColor.copy(alpha = 0f))
                    )
                )
                drawPath(path, color = lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
            }
            Spacer(Modifier.height(OmniDimens.Grid1))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatLegend(color = MaterialTheme.colorScheme.primary, label = "峰值功率", value = state.peakW?.let { formatWatts(it) } ?: "—")
                StatLegend(color = MaterialTheme.colorScheme.secondary, label = "均值功率", value = state.meanW?.let { formatWatts(it) } ?: "—")
                StatLegend(color = MaterialTheme.colorScheme.tertiary, label = "温升斜率", value = state.tempSlope?.let { String.format("%+.1f°C/m", it) } ?: "—")
            }
        }
    }
}

@Composable
private fun StatLegend(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            "$label ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

/** 电芯硬件与驱动规范：intent / 属性接口 / sysfs 三路真值。 */
@Composable
private fun SpecSheetCard(snapshot: BatterySnapshot?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(OmniDimens.Grid2), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    OmniIcon.of("memory"), contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("电芯硬件与驱动规范", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            SpecRow("电池化学体系", snapshot?.technology ?: "读取中")
            SpecRow("出厂标称设计容量", formatMilliAmpHours(snapshot?.chargeFullDesignMicroAh))
            SpecRow(
                "当前硬件供电来源",
                when (snapshot?.plugged) {
                    null, PowerSource.NONE -> "电池（未接入外部电源）"
                    PowerSource.USB -> "USB 总线供电"
                    PowerSource.AC -> "交流适配器供电"
                    PowerSource.WIRELESS -> "无线充电线圈供电"
                }
            )
            SpecRow("底层驱动状态报告", snapshot?.health?.label ?: "读取中")
            SpecRow(
                "累计容量损耗率",
                snapshot?.capacityLossPercent?.let { "$it%（${if (it <= 10) "轻微" else if (it <= 25) "明显" else "严重"}磨损）" }
                    ?: "系统未上报（需 sysfs）"
            )
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

/** 智能电池养护：三条真实可用的本地提醒（免 Root 不能控制充电，文案如实）。 */
@Composable
private fun GuardSettingsCard(state: BatteryUiState, onEvent: (BatteryEvent) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(OmniDimens.Grid2), verticalArrangement = Arrangement.spacedBy(OmniDimens.Grid2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    OmniIcon.of("shield_with_heart"), contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("智能电池养护与温控提醒", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            }

            GuardSwitchRow(
                title = "充电目标提醒",
                subtitle = "充至 ${state.targetLevel}% 时通知 + 震动（免 Root 应用无法切断充电，仅提醒）",
                checked = state.targetAlert,
                onCheckedChange = { onEvent(BatteryEvent.TargetAlertChanged(it)) },
                extra = {
                    StepperRow(
                        value = state.targetLevel,
                        onDec = { onEvent(BatteryEvent.TargetLevelChanged((state.targetLevel - 5).coerceAtLeast(50))) },
                        onInc = { onEvent(BatteryEvent.TargetLevelChanged((state.targetLevel + 5).coerceAtMost(100))) },
                        display = { "$it%" }
                    )
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            GuardSwitchRow(
                title = "电芯高温提醒",
                subtitle = "电芯温度超过 ${state.tempThresholdC}°C 时通知（10 分钟冷却）",
                checked = state.tempAlert,
                onCheckedChange = { onEvent(BatteryEvent.TempAlertChanged(it)) },
                extra = {
                    StepperRow(
                        value = state.tempThresholdC,
                        onDec = { onEvent(BatteryEvent.TempThresholdChanged((state.tempThresholdC - 1).coerceAtLeast(35))) },
                        onInc = { onEvent(BatteryEvent.TempThresholdChanged((state.tempThresholdC + 1).coerceAtMost(50))) },
                        display = { "${it}°C" }
                    )
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            GuardSwitchRow(
                title = "涓流完成提示震动",
                subtitle = "状态变为「已充满」时震动 + 通知（每次充电会话仅一次）",
                checked = state.fullVibrate,
                onCheckedChange = { onEvent(BatteryEvent.FullVibrateChanged(it)) }
            )
            Text(
                "提醒由「电池守护」前台服务在本机触发，开启任一开关后通知栏会显示常驻状态；全部关闭即自动停止，不上传任何数据。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GuardSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    extra: @Composable () -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            extra()
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StepperRow(value: Int, onDec: () -> Unit, onInc: () -> Unit, display: (Int) -> String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 6.dp)
    ) {
        FilledTonalIconButton(onClick = onDec, modifier = Modifier.size(28.dp)) {
            Icon(OmniIcon.of("remove"), contentDescription = "减小", modifier = Modifier.size(16.dp))
        }
        Text(
            display(value),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        FilledTonalIconButton(onClick = onInc, modifier = Modifier.size(28.dp)) {
            Icon(OmniIcon.of("add"), contentDescription = "增大", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: String,
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Surface(
        color = container,
        shape = OmniShape.Pill,
        modifier = modifier.clip(OmniShape.Pill).height(40.dp)
    ) {
        Row(
            Modifier.fillMaxSize().clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(OmniIcon.of(icon), contentDescription = null, modifier = Modifier.size(18.dp), tint = content)
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}
