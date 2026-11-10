package com.omnibox.feature.decibel

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnibox.core.designsystem.*
import kotlinx.coroutines.delay
import kotlin.math.abs

private fun Context.findActivity(): ComponentActivity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun DecibelRoute(onBack: () -> Unit) {
    val viewModel: DecibelViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val activity = context.findActivity()
        val rationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) ?: false
        viewModel.onEvent(DecibelEvent.PermissionResult(granted, rationale, !granted && !rationale))
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val activity = context.findActivity()
        val rationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) ?: false
        viewModel.onEvent(DecibelEvent.PermissionResult(granted, rationale, !granted && !rationale))
    }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(2200)
            viewModel.onEvent(DecibelEvent.ClearMessage)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.onEvent(DecibelEvent.Pause) }
    }

    val openSettings: () -> Unit = {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).omniStatusBarInset().omniNavBarInset()) {
        OmniTopBar(
            title = "环境分贝计",
            subtitle = "44.1kHz · PCM_16BIT · 本地估算",
            navigationIcon = OmniIcon.of("arrow_back"),
            onNavigationClick = onBack
        )
        Box(Modifier.weight(1f)) {
            if (state.permissionGranted) {
                DecibelActivePage(state = state, onEvent = viewModel::onEvent)
            } else {
                DecibelPermissionPage(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onOpenSettings = openSettings
                )
            }
        }
        state.message?.let { msg ->
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = OmniShape.Pill,
                modifier = Modifier.padding(horizontal = OmniDimens.ScreenPadding, vertical = 8.dp)
            ) {
                Text(msg, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
        state.error?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = OmniShape.Pill,
                modifier = Modifier
                    .padding(horizontal = OmniDimens.ScreenPadding, vertical = 4.dp)
                    .clip(OmniShape.Pill)
                    .clickable { viewModel.onEvent(DecibelEvent.ClearError) }
            ) {
                Text(error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

/* ------------------------------ 实时监测页 (sound_meter) ------------------------------ */

@Composable
private fun DecibelActivePage(state: DecibelUiState, onEvent: (DecibelEvent) -> Unit) {
    val hasData = state.metrics.sampleCount > 0L
    val instantText = if (state.isRecording && hasData) "%.1f".format(state.instantDb) else "--.-"
    val currentRef = if (hasData) {
        WHO_REFERENCES.minByOrNull { abs(it.centerDb - state.instantDb) }
    } else null
    val highlightRef = currentRef != null && abs(currentRef.centerDb - state.instantDb) <= 10.0

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = OmniShape.Card
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(
                        if (state.isRecording) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    ))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.isRecording) "实时监测中" else "已暂停 · 监听未启动",
                        style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.weight(1f))
                    Text("44.1kHz · 单声道 PCM_16BIT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                Text(instantText, style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("dB · 估算声压级 (SPL)", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Surface(color = if (hasData && state.level == NoiseLevel.HARMFUL)
                    MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    shape = OmniShape.Pill) {
                    Text(
                        if (hasData && state.level != null) "${state.level.label} · ${state.level.advice}"
                        else "等待真实采样数据",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasData && state.level == NoiseLevel.HARMFUL)
                            MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.height(18.dp))
                LevelBar(state.instantDb, state.isRecording && hasData)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("0dB 微弱", "60dB 适中", "85dB 嘈杂", "120dB 危险").forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OmniMetricCard("实时", "$instantText dBA", Modifier.weight(1f))
            OmniMetricCard("最小 Min", "%.1f dBA".format(state.metrics.minDb), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OmniMetricCard("平均 Avg", "%.1f dBA".format(state.metrics.movingAvgDb), Modifier.weight(1f))
            OmniMetricCard("峰值 Max", "%.1f dBA".format(state.metrics.peakDb), Modifier.weight(1f))
        }

        DbCard("graphic_eq", "声压统计明细", "全部由实测 PCM 计算") {
            DbInfoLine("equalizer", "等效连续声级 Leq", "%.1f dBA".format(state.metrics.leqDb))
            DbInfoLine("waves", "滑动平均 (EMA)", "%.1f dBA".format(state.metrics.movingAvgDb))
            DbInfoLine("speed", "当前 dBFS", "%.1f dBFS".format(state.dbfs))
            DbInfoLine("memory", "RMS 振幅 / 32768", "%.0f".format(state.rms))
            DbInfoLine("numbers", "累计采样帧", "${state.metrics.sampleCount}")
        }

        DbCard("show_chart", "近 10 秒声压波动", "采样帧驱动 · 无图表库") {
            if (state.metrics.recent.isEmpty()) {
                Text("等待采样…", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(Modifier.fillMaxWidth().height(72.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    state.metrics.recent.forEach { v ->
                        val frac = (v / 120.0).toFloat().coerceIn(0.02f, 1f)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(frac)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("-10 秒", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("现在 (RMS)", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        DbCard("hearing", "环境噪音对照基准", "WHO / 国标声学") {
            Text("按当前实测声压高亮最贴近的区间（±10 dB 内生效）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            WHO_REFERENCES.forEach { ref ->
                WhoRow(ref, highlightRef && ref == currentRef)
            }
        }

        DbCard("tune", "监测控制与硬件校准", "采样率: 44.1kHz") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OmniPrimaryButton(
                    if (state.isRecording) "暂停实时监测" else "继续实时监测",
                    onClick = { onEvent(if (state.isRecording) DecibelEvent.Pause else DecibelEvent.Start) },
                    icon = if (state.isRecording) "pause_circle" else "play_arrow",
                    modifier = Modifier.weight(1f)
                )
                OmniPrimaryButton("重置峰值", onClick = { onEvent(DecibelEvent.ResetPeak) },
                    icon = "restart_alt", modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            OmniLabel("硬件校准偏移：${"%+.1f".format(state.calibrationOffsetDb)} dB")
            Slider(
                value = state.calibrationOffsetDb,
                onValueChange = { onEvent(DecibelEvent.CalibrationChanged(it)) },
                valueRange = DecibelMath.CALIBRATION_MIN.toFloat()..DecibelMath.CALIBRATION_MAX.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("-30 dB", style = MaterialTheme.typography.labelSmall)
                Text("0", style = MaterialTheme.typography.labelSmall)
                Text("+30 dB", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text("估算声压级 = dBFS + 90 dB + 校准偏移；当前实测 dBFS = %.1f dBFS".format(state.dbfs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        DbCard("verified_user", "纯本地声压换算保障", "离线") {
            Text("仅在系统 AudioRecord 内存环形缓冲区即时计算均方根声压 (RMS)，" +
                "全程零网络请求，绝不录制、缓存或外发任何音频数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ------------------------------ 权限降级页 (_6) ------------------------------ */

@Composable
private fun DecibelPermissionPage(
    state: DecibelUiState,
    onEvent: (DecibelEvent) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var showPolicy by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OmniDimens.ScreenPadding, vertical = 12.dp)
    ) {
        Surface(
            Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = OmniShape.Card
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(OmniIcon.of("lock"), contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("硬件通道保护中 · 监听未启动",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("--.-", style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("dBA · 需要麦克风权限以采样环境声压",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
        }

        DbCard("verified_user", "分贝计隐私说明", "音频字节不落盘") {
            Text("分贝计仅通过 AudioRecord 接口实时抓取瞬时声波振幅计算均方根声压（RMS），" +
                "计算完成后立即丢弃音频字节，不存储音频文件、不录音、更无网络权限进行外发传输。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (state.permanentlyDenied) {
            OmniPrimaryButton("已被永久拒绝？前往系统设置开启", onClick = onOpenSettings,
                icon = "settings", modifier = Modifier.fillMaxWidth())
        } else {
            OmniPrimaryButton("允许麦克风权限", onClick = onRequestPermission,
                icon = "mic", modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = if (state.permanentlyDenied) onRequestPermission else onOpenSettings,
            modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (state.permanentlyDenied) "重新发送权限请求" else "重新授权 / 系统设置")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showPolicy = !showPolicy },
            modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (showPolicy) "收起隐私与安全白皮书" else "查看隐私与安全白皮书")
        }

        if (showPolicy) {
            DbCard("policy", "隐私权限透明白皮书", "3 条") {
                Text("1. 无网络栈声明：Manifest 中移除了 android.permission.INTERNET，从系统层剥离出站能力。",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Text("2. 极简缓冲区：AudioRecord.read(short[], 0, size) 单块读取，计算 RMS 后变量立即被回收。",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Text("3. 零落盘：无任何 FileOutputStream 或临时 WAV/AAC 写入逻辑，本地闪存无残留。",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OmniPrimaryButton("我已充分知悉", onClick = { showPolicy = false },
                    icon = "check", modifier = Modifier.fillMaxWidth())
            }
        }

        DbCard("equalizer", "环境声压级权威对照表 (WHO)", "离线常驻") {
            Text("基于世界卫生组织 (WHO) 与声学标准分级，即使未开启麦克风，亦可对照评估身处环境声学舒适度：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            WHO_REFERENCES.forEach { WhoRow(it, false) }
        }

        DbCard("tune", "硬件校准与传感器沙盒", "采样率: 44.1kHz") {
            OmniLabel("硬件校准偏移：${"%+.1f".format(state.calibrationOffsetDb)} dB")
            Slider(
                value = state.calibrationOffsetDb,
                onValueChange = { onEvent(DecibelEvent.CalibrationChanged(it)) },
                valueRange = DecibelMath.CALIBRATION_MIN.toFloat()..DecibelMath.CALIBRATION_MAX.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
            Text("无权限状态下仍可预先设定校准偏置，授权后立即作用于真实 dBFS 换算。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        DbCard("memory", "基于 AndroidX AudioRecord 端侧低延迟流管道保障", "零网络通讯权限") {
            Text("零网络通讯权限 · 内存即时计算 · 物理阻断截流",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ------------------------------ 本模块私有小组件 ------------------------------ */

@Composable
private fun LevelBar(db: Double, active: Boolean) {
    val fraction = if (active) (db / 120.0).toFloat().coerceIn(0f, 1f) else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(OmniShape.Pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction)
                .height(16.dp)
                .clip(OmniShape.Pill)
                .background(MaterialTheme.colorScheme.primary)
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(1f - 85f / 120f)
                .height(16.dp)
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f))
        )
    }
}

@Composable
private fun WhoRow(ref: NoiseReference, isCurrent: Boolean) {
    Surface(
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = OmniShape.CardSmall,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(OmniIcon.of(ref.icon), contentDescription = null,
                tint = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(ref.title, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(ref.desc, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Text(ref.dbLabel, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun DbCard(icon: String, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
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
private fun DbInfoLine(icon: String, label: String, value: String) {
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
