package com.omnibox.feature.compass

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnibox.core.designsystem.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private fun Context.findActivity(): ComponentActivity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun CompassRoute(onBack: () -> Unit) {
    val viewModel: CompassViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val activity = context.findActivity()
        val rationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ?: false
        viewModel.onEvent(
            CompassEvent.LocationPermissionResult(granted, rationale, !granted && !rationale)
        )
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val activity = context.findActivity()
        val rationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ?: false
        viewModel.onEvent(
            CompassEvent.LocationPermissionResult(granted, rationale, !granted && !rationale)
        )
    }

    // 生命周期：进入注册 / 退出注销，ViewModel.onCleared 再做兜底。
    DisposableEffect(Unit) {
        viewModel.onEvent(CompassEvent.StartSensors)
        onDispose { viewModel.onEvent(CompassEvent.StopSensors) }
    }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(2200)
            viewModel.onEvent(CompassEvent.ClearMessage)
        }
    }

    val openSettings: () -> Unit = {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }
    val requestLocation: () -> Unit = { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).omniStatusBarInset().omniNavBarInset()) {
        OmniTopBar(
            title = "指南针与水平仪",
            subtitle = "磁北 / 真北 · 双轴倾斜水平仪",
            navigationIcon = OmniIcon.of("arrow_back"),
            onNavigationClick = onBack
        ) {
            IconButton(onClick = { viewModel.onEvent(CompassEvent.RescanSensors) }) {
                Icon(OmniIcon.of("refresh"), contentDescription = "重新扫描传感器")
            }
        }
        Box(Modifier.weight(1f)) {
            if (state.hasMagnetometer) {
                CompassActivePage(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onRequestLocation = requestLocation,
                    onOpenSettings = openSettings
                )
            } else {
                CompassDegradedPage(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onRequestLocation = requestLocation,
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
    }
}

/* ------------------------------ 正常工作页 (_8) ------------------------------ */

@Composable
private fun CompassActivePage(
    state: CompassUiState,
    onEvent: (CompassEvent) -> Unit,
    onRequestLocation: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val az = state.azimuthDeg
    val pitch = state.pitchDeg - state.pitchOffset
    val roll = state.rollDeg - state.rollOffset

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        CompassCard("sensors", "地磁高频传感器工作中", accuracyLabel(state.magneticAccuracy)) {
            Text("采用硬件磁力计与加速度计融合解算。如遇指针偏移，请手持设备在空中做「∞」字形轻转校准。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Surface(
            Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = OmniShape.Card
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (az != null) "%.0f°".format(az) else "--°",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    state.direction?.let { "${it.chinese} · ${it.abbr}" } ?: "等待传感器方位解算",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                CompassDial(az ?: 0f)
                Spacer(Modifier.height(8.dp))
                Text("刻度盘随方位角旋转 · 指针恒指向设备正前方",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        CompassCard("straighten", "双轴倾斜水平仪", "偏离水平") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("X 轴 · 横向倾角", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%+.1f°".format(roll), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Y 轴 · 俯仰倾角", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%+.1f°".format(pitch), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                }
                BubbleLevel(pitch, roll)
            }
            Spacer(Modifier.height(8.dp))
            Text("水平基准面: 机身背面",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OmniPrimaryButton("置零基准面", onClick = { onEvent(CompassEvent.CalibrateLevel) },
                icon = "vibration", modifier = Modifier.fillMaxWidth())
        }

        CompassCard("explore", "地磁感应强度", "环境磁场") {
            val ut = state.fieldStrengthUt
            if (ut == null) {
                Text("等待磁力计数据…", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("%.1f".format(ut) + " μT",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(fieldAssessment(ut), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        CompassCard("location_on", "WGS-84 空间定位", "度分秒 (DMS)") {
            val info = state.trueNorth
            if (info != null) {
                CompassInfoLine("my_location", "纬度 Latitude", formatDms(info.latitude, "N", "S"))
                CompassInfoLine("my_location", "经度 Longitude", formatDms(info.longitude, "E", "W"))
                CompassInfoLine("swap_horiz", "磁偏角", formatDeclination(info.declinationDeg))
                CompassInfoLine("explore", "真北方位角",
                    if (state.trueNorthAzimuth != null) "%.0f°".format(state.trueNorthAzimuth) else "—")
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = OmniShape.Pill) {
                    Text("真北校正已激活", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            } else {
                Text("仅磁北可用 · 真北校正未启用",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(6.dp))
                Text("真北需定位权限与定位缓存计算磁偏角；未启用时不显示任何真北数值。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                CompassInfoLine("wb_sunny", "日晷方位角粗估（估算 · 非实测）",
                    "%.0f°".format(state.solarEstimateDeg))
                Text("当前日晷粗估方向：${directionOf(state.solarEstimateDeg.toFloat()).chinese}（估算，误差较大）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                if (state.permanentlyDenied) {
                    OmniPrimaryButton("定位已被永久拒绝？前往系统设置", onClick = onOpenSettings,
                        icon = "settings", modifier = Modifier.fillMaxWidth())
                } else {
                    OmniPrimaryButton("启用真北校正（需定位权限）", onClick = onRequestLocation,
                        icon = "location_on", modifier = Modifier.fillMaxWidth())
                }
            }
        }

        CompassCard("verified_user", "严格端侧隐私与沙盒保护", "零网络请求") {
            Text("所有姿态解算、磁偏角计算与刻度绘制均在本机完成；定位仅读取系统缓存的最近一次位置，不产生任何网络上传。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ------------------------------ 降级页 (_15) ------------------------------ */

@Composable
private fun CompassDegradedPage(
    state: CompassUiState,
    onEvent: (CompassEvent) -> Unit,
    onRequestLocation: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val pitch = state.pitchDeg - state.pitchOffset
    val roll = state.rollDeg - state.rollOffset

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = OmniShape.CardSmall
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(OmniIcon.of("sensors_off"), contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(8.dp))
                    Text("未检测到硬件地磁计 (Magnetometer)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
                Spacer(Modifier.height(6.dp))
                Text("降级运行 · 本机型物理磁力计芯片未装配或已离线。指南针已自动切换为太阳方位角粗估，" +
                    "不提供任何磁北 / 真北实测数值。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        CompassCard("wb_sunny", "日晷与航向推演盘", "算法估算 · 非传感器实测") {
            Text("%.0f°".format(state.solarEstimateDeg),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text("${directionOf(state.solarEstimateDeg.toFloat()).chinese}（估算）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Text("北半球白天：将当前时针指向太阳所在方向，时针与表盘 12 点刻度之间的角平分线所指方向即为正南方，" +
                "相反方向即为正北方。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text("该数值由当前本地时间粗略推算，误差较大，仅供参考，绝不冒充传感器读数。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error)
        }

        CompassCard("sensors", "传感器矩阵状态", "更新于 刚刚") {
            CompassStatusRow("加速度计 (Accelerometer)", "三轴重力感应在线 · 水平仪可用", "正常", true)
            CompassStatusRow("地磁传感器 (Magnetometer)", "硬件驱动未注册 · 无法感应地球磁场", "缺失", false)
            CompassStatusRow("气压计 (Barometer)", "本工具未读取气压，不显示高程数值", "不提供", false)
        }

        CompassCard("straighten", "双轴倾斜水平仪 (实时可用)", "重力引擎已就绪") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("X 轴 (横滚)", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%+.1f°".format(roll), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text("水平微倾", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Y 轴 (俯仰)", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%+.1f°".format(pitch), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text("平衡读数来自加速度计", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BubbleLevel(pitch, roll)
            }
            Spacer(Modifier.height(8.dp))
            OmniPrimaryButton("置零基准面", onClick = { onEvent(CompassEvent.CalibrateLevel) },
                icon = "vibration", modifier = Modifier.fillMaxWidth())
        }

        CompassCard("lightbulb", "无地磁传感器？户外时钟日照法定向", "时钟对日法") {
            Text("真北 / 磁北在本机型均不可用；仅可通过日晷粗估大致定向。" +
                "如需真北校正，仍需设备具备磁力计并授予定位权限。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            if (state.permanentlyDenied) {
                OmniPrimaryButton("定位已被永久拒绝？前往系统设置", onClick = onOpenSettings,
                    icon = "settings", modifier = Modifier.fillMaxWidth())
            } else {
                OmniPrimaryButton("授予定位权限（用于真北校正）", onClick = onRequestLocation,
                    icon = "location_on", modifier = Modifier.fillMaxWidth())
            }
        }

        Row(Modifier.padding(horizontal = OmniDimens.ScreenPadding)) {
            OmniPrimaryButton("重新扫描硬件传感器", onClick = {
                onEvent(CompassEvent.RescanSensors)
            }, icon = "refresh", modifier = Modifier.fillMaxWidth())
        }
    }
}

/* ------------------------------ Canvas 自绘 ------------------------------ */

/** 罗盘刻度盘：drawLine/drawCircle + nativeCanvas.drawText 绘制中文方向字。 */
@Composable
private fun CompassDial(azimuth: Float, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().rotate(-azimuth)) {
            val r = size.minDimension / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val center = Offset(cx, cy)
            drawCircle(color = outline.copy(alpha = 0.30f), radius = r * 0.97f,
                center = center, style = Stroke(width = 2.dp.toPx()))
            drawCircle(color = outline.copy(alpha = 0.18f), radius = r * 0.72f,
                center = center, style = Stroke(width = 1.dp.toPx()))
            for (deg in 0 until 360 step 5) {
                val major = deg % 30 == 0
                val r1 = r * (if (major) 0.80f else 0.89f)
                val r2 = r * 0.97f
                val rad = Math.toRadians(deg.toDouble() - 90.0)
                val c = cos(rad).toFloat()
                val s = sin(rad).toFloat()
                drawLine(
                    color = if (major) onSurface else outline,
                    start = Offset(cx + r1 * c, cy + r1 * s),
                    end = Offset(cx + r2 * c, cy + r2 * s),
                    strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            val textSizePx = 20.dp.toPx()
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = primary.toArgb()
                    textSize = textSizePx
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    isFakeBoldText = true
                }
                val baseline = -(paint.ascent() + paint.descent()) / 2f
                val dirs = listOf(
                    Triple("北", 0, r * 0.55f),
                    Triple("东", 90, r * 0.55f),
                    Triple("南", 180, r * 0.55f),
                    Triple("西", 270, r * 0.55f)
                )
                dirs.forEach { (label, deg, rr) ->
                    val rad = Math.toRadians(deg.toDouble() - 90.0)
                    val x = cx + rr * cos(rad).toFloat()
                    val y = cy + rr * sin(rad).toFloat() + baseline
                    canvas.nativeCanvas.drawText(label, x, y, paint)
                }
            }
        }
        // 固定指针：不随刻度盘旋转，恒指向设备正前方
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawLine(color = primary, start = Offset(cx, cy), end = Offset(cx, cy - r * 0.68f),
                strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            drawLine(color = outline, start = Offset(cx, cy), end = Offset(cx, cy + r * 0.45f),
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(color = primary, radius = 5.dp.toPx(), center = Offset(cx, cy))
        }
    }
}

/** 水平仪：由真实 pitch/roll 计算气泡偏移量，自绘 Box/Canvas，不依赖图表库。 */
@Composable
private fun BubbleLevel(pitch: Float, roll: Float, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(140.dp)) {
        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val center = Offset(cx, cy)
        drawCircle(color = outline.copy(alpha = 0.30f), radius = r * 0.95f,
            center = center, style = Stroke(width = 2.dp.toPx()))
        drawCircle(color = outline.copy(alpha = 0.18f), radius = r * 0.45f,
            center = center, style = Stroke(width = 1.dp.toPx()))
        drawLine(color = outline.copy(alpha = 0.30f),
            start = Offset(cx - r * 0.95f, cy), end = Offset(cx + r * 0.95f, cy),
            strokeWidth = 1.dp.toPx())
        drawLine(color = outline.copy(alpha = 0.30f),
            start = Offset(cx, cy - r * 0.95f), end = Offset(cx, cy + r * 0.95f),
            strokeWidth = 1.dp.toPx())
        val bx = cx + (roll / 45f).coerceIn(-1f, 1f) * r * 0.7f
        val by = cy + (pitch / 45f).coerceIn(-1f, 1f) * r * 0.7f
        drawCircle(color = primary.copy(alpha = 0.30f), radius = r * 0.18f,
            center = Offset(bx, by))
        drawCircle(color = primary, radius = r * 0.18f,
            center = Offset(bx, by), style = Stroke(width = 2.dp.toPx()))
    }
}

/* ------------------------------ 本模块私有小组件 ------------------------------ */

@Composable
private fun CompassCard(icon: String, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
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
private fun CompassInfoLine(icon: String, label: String, value: String) {
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
private fun CompassStatusRow(name: String, status: String, badge: String, isNormal: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodySmall)
            Text(status, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            color = if (isNormal) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer,
            shape = OmniShape.Pill
        ) {
            Text(badge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (isNormal) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}
