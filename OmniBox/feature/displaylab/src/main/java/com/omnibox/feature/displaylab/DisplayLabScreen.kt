package com.omnibox.feature.displaylab

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnibox.core.designsystem.*
import kotlinx.coroutines.delay

private fun Context.findActivity(): ComponentActivity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun DisplayLabRoute(onBack: () -> Unit) {
    val viewModel: DisplayLabViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()

    var testMode by remember { mutableStateOf<ScreenTestMode?>(null) }

    // 「保持屏幕常亮」：真实套用 FLAG_KEEP_SCREEN_ON（此前该设置键无人消费）
    LaunchedEffect(state.keepScreenOn) {
        activity?.applyKeepScreenOn(state.keepScreenOn)
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.applyKeepScreenOn(false)
            testMode = null
        }
    }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(2200)
            viewModel.onEvent(DisplayLabEvent.ClearMessage)
        }
    }

    testMode?.let { mode ->
        ScreenTestOverlay(mode = mode, onExit = { testMode = null })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .omniStatusBarInset()
            .omniNavBarInset()
    ) {
        OmniTopBar(
            title = "屏幕检测与触控",
            subtitle = "Display & Touch · 本地实时读取",
            navigationIcon = OmniIcon.of("arrow_back"),
            onNavigationClick = onBack,
            actions = {
                IconButton(onClick = { viewModel.onEvent(DisplayLabEvent.Refresh) }) {
                    Icon(OmniIcon.of("sync"), contentDescription = "刷新显示参数")
                }
                IconButton(onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
                }) {
                    Icon(OmniIcon.of("tune"), contentDescription = "打开系统显示设置")
                }
            }
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OmniDimens.ScreenPadding)
                .padding(bottom = OmniDimens.Grid3),
            verticalArrangement = Arrangement.spacedBy(OmniDimens.Grid2)
        ) {
            DisplayMetricsCard(state.specs)
            TestMatrixSection(
                onTest = { mode -> testMode = mode }
            )
            TouchSandboxSection(specs = state.specs)
            DisplaySystemSection(
                specs = state.specs,
                keepScreenOn = state.keepScreenOn,
                onKeepScreenOn = { viewModel.onEvent(DisplayLabEvent.KeepScreenOnChanged(it)) }
            )
            // 底部主操作：真实启动全屏自动巡检
            OmniPrimaryButton(
                text = "启动全屏自动化屏幕巡检",
                onClick = { testMode = ScreenTestMode.AutoSequence },
                icon = "play_circle",
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.align(Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    OmniIcon.of("verified_user"), contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "100% 本地硬件渲染基准测试 · 纯本地沙盒",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 顶部显示参数卡：全部真值 —— 刷新率/分辨率/DPI 来自 DisplayManager，亮度来自 Settings.System。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DisplayMetricsCard(specs: DisplaySpecs?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = OmniShape.CardSmall,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(OmniDimens.Grid2)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer))
                        Icon(
                            OmniIcon.of("smartphone"), contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(Modifier.width(OmniDimens.Grid1))
                    Column {
                        Text(
                            specs?.model ?: "正在读取…",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            specs?.let { "显示模式 ${it.modeCount} 种 · 系统上报参数" } ?: "DisplayManager",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                specs?.let {
                    RefreshBadge(it.refreshLabel)
                }
            }
            Spacer(Modifier.height(OmniDimens.Grid2))
            Row(horizontalArrangement = Arrangement.spacedBy(OmniDimens.Grid1)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "面板规格与分辨率",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            specs?.diagonalLabel ?: "—",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            specs?.let { "${it.resolutionLabel} · ${it.ppiLabel}" } ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "当前亮度",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${specs?.brightnessPercent ?: "—"}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "% · ${if (specs?.adaptiveBrightness == true) "自适应" else "手动"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (specs?.brightnessFraction ?: 0f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }
            Spacer(Modifier.height(OmniDimens.Grid1))
            // 能力徽章：HDR 类型 / 广色域 / 刷新率档位 —— 全部真值
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val chips = buildList {
                    specs?.hdrTypes?.takeIf { it.isNotEmpty() }?.let {
                        addAll(HdrLabels.labels(it.toIntArray()).map { it to false })
                    } ?: add("HDR 未上报" to false)
                    add((if (specs?.wideColorGamut == true) "广色域 P3" else "sRGB 色域") to false)
                    specs?.supportedRefreshRates?.takeIf { it.isNotEmpty() }?.let { rates ->
                        add((rates.joinToString("/") { String.format("%.0f", it) } + "Hz 档位") to true)
                    }
                }
                chips.forEach { (text, highlight) ->
                    Surface(
                        color = if (highlight) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = OmniShape.Pill
                    ) {
                        Text(
                            text,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (highlight) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RefreshBadge(label: String) {
    val transition = rememberInfiniteTransition(label = "ping")
    val alpha by transition.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "ping"
    )
    Surface(color = MaterialTheme.colorScheme.primary, shape = OmniShape.Pill) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier.size(7.dp).clip(CircleShape).background(
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = alpha)
                )
            )
            Text(
                "$label 实时",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/** 四项硬件诊断入口 —— 全部可点、全部真实进入全屏测试。 */
@Composable
private fun TestMatrixSection(onTest: (ScreenTestMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OmniDimens.Grid1)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("硬件诊断基准测试", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text("4 项专项", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(OmniDimens.Grid1)) {
            TestCard(
                icon = "filter_vintage",
                title = "纯色坏点巡检",
                body = "红绿蓝黑白五基色全屏均匀度及亮点巡测",
                footer = "五基色",
                modifier = Modifier.weight(1f),
                onClick = { onTest(ScreenTestMode.DeadPixel) }
            )
            TestCard(
                icon = "touch_app",
                title = "多指触控测试",
                body = "全屏多指同步压感检测与事件轮询响应",
                footer = "全屏沙盒",
                modifier = Modifier.weight(1f),
                onClick = { onTest(ScreenTestMode.TouchTest) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(OmniDimens.Grid1)) {
            TestCard(
                icon = "gradient",
                title = "256阶灰阶阶梯",
                body = "暗部细节截断度、Gamma 2.2 平滑度验证",
                footer = "阶梯/渐变",
                modifier = Modifier.weight(1f),
                onClick = { onTest(ScreenTestMode.Grayscale) }
            )
            TestCard(
                icon = "grid_view",
                title = "残影与烧屏排查",
                body = "交替黑白棋盘相位振荡及长时间残影比对",
                footer = "600ms 振荡",
                modifier = Modifier.weight(1f),
                onClick = { onTest(ScreenTestMode.Checkerboard) }
            )
        }
    }
}

@Composable
private fun TestCard(
    icon: String,
    title: String,
    body: String,
    footer: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = OmniShape.CardSmall,
        modifier = modifier.clip(OmniShape.CardSmall).clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest))
                Icon(
                    OmniIcon.of(icon), contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    footer,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "开始",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/** 页内触控采样沙盒：Canvas 真实绘制轨迹，指标行显示实测压感与事件间隔（面积字段厂商多不上报，如实显示「—」）。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TouchSandboxSection(specs: DisplaySpecs?) {
    val density = LocalDensity.current
    var frame by remember { mutableIntStateOf(0) }
    var metrics by remember { mutableStateOf(TouchSnapshot()) }
    val tracker = remember { TouchTracker() }
    var canvasBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val lastPerPointer = remember { mutableMapOf<Int, Offset>() }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = OmniShape.CardSmall,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(OmniDimens.Grid2), verticalArrangement = Arrangement.spacedBy(OmniDimens.Grid1)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        OmniIcon.of("gesture"), contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("实时触控采样沙盒", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                }
                Text(
                    "清除轨迹",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(OmniShape.Pill)
                        .clickable {
                            tracker.reset()
                            metrics = TouchSnapshot()
                            canvasBitmap?.eraseColor(android.graphics.Color.TRANSPARENT)
                            frame++
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("瞬态压感", pressureLabel(metrics.pressure), Modifier.weight(1f))
                MetricTile("最大并发触点", "${metrics.maxPointers} 指", Modifier.weight(1f))
                MetricTile(
                    "事件间隔",
                    intervalLabel(metrics.intervalMs),
                    Modifier.weight(1f),
                    highlight = true
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .onSizeChanged { size ->
                        if (size.width > 0 && size.height > 0 &&
                            (canvasBitmap == null || canvasBitmap!!.width != size.width ||
                                canvasBitmap!!.height != size.height)
                        ) {
                            canvasBitmap = Bitmap.createBitmap(
                                size.width, size.height, Bitmap.Config.ARGB_8888
                            )
                        }
                    }
                    .pointerInput(canvasBitmap) {
                        awaitEachGesture {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isNotEmpty()) {
                                    val now = System.nanoTime()
                                    val primary = pressed.first()
                                    // Compose 触摸层不暴露 touchMajor/Minor，面积无从测起 → 显示「—」；
                                    // 压感 getPressure 与事件间隔均为实测值。
                                    tracker.onEvent(
                                        eventNanos = now,
                                        pressedPointerCount = pressed.size,
                                        pressure = primary.pressure,
                                        touchMajorPx = 0f,
                                        touchMinorPx = 0f
                                    )
                                    metrics = metrics.copy(
                                        pressure = tracker.lastPressure,
                                        intervalMs = tracker.pollingIntervalMs,
                                        maxPointers = maxOf(metrics.maxPointers, pressed.size),
                                        hasDrawn = true
                                    )
                                    drawIntoBitmap(
                                        canvasBitmap, frame++,
                                        pressed.map { it.id.value.toInt() to it.position },
                                        lastPerPointer
                                    )
                                    lastPerPointer.clear()
                                    pressed.forEach { lastPerPointer[it.id.value.toInt()] = it.position }
                                }
                            }
                        }
                    }
            ) {
                val bmp = canvasBitmap
                Canvas(Modifier.fillMaxSize()) {
                    // 读取帧计数：状态读取发生在 draw 阶段，变化即触发重绘
                    if (frame >= 0 && bmp != null) drawImage(bmp.asImageBitmap())
                }
                if (!metrics.hasDrawn) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            OmniIcon.of("pinch"), contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "在此区域单指/多指滑动画线测试采样轨迹",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Text(
                "注：接触面积需触控 IC 上报椭圆主/次轴，多数消费级屏幕不上报，显示「—」为诚实缺省；压感与事件间隔为实测值。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

private data class TouchSnapshot(
    val pressure: Float = 0f,
    val intervalMs: Float? = null,
    val maxPointers: Int = 0,
    val hasDrawn: Boolean = false
)

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** 轨迹绘制：直接写进 Bitmap（和设计稿 canvas 2d 一致的手法），避免高频重组。 */
private fun drawIntoBitmap(
    bitmap: Bitmap?,
    frame: Int,
    points: List<Pair<Int, Offset>>,
    lastPerPointer: Map<Int, Offset>
) {
    val bmp = bitmap ?: return
    if (points.isEmpty()) return
    val canvas = AndroidCanvas(bmp)
    val paint = Paint().apply {
        color = 0xFF6750A4.toInt()
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    val stroke = Paint().apply {
        color = 0xFF4F378A.toInt()
        strokeWidth = 5f
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    points.forEach { (id, pos) ->
        canvas.drawCircle(pos.x, pos.y, 10f, paint)
        val last = lastPerPointer[id]
        if (last != null) {
            canvas.drawLine(last.x, last.y, pos.x, pos.y, stroke)
        }
    }
}

/** 显示系统状态区：设计稿的 PWM/True Tone 开关在免 Root 环境不可写，如实改为真值展示 + 系统设置直达。 */
@Composable
private fun DisplaySystemSection(
    specs: DisplaySpecs?,
    keepScreenOn: Boolean,
    onKeepScreenOn: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = OmniShape.CardSmall,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(OmniDimens.Grid2), verticalArrangement = Arrangement.spacedBy(OmniDimens.Grid2)) {
            Text("显示驱动与护眼调教", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("测试期间保持屏幕常亮", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "全屏巡检时防止系统息屏中断测试（全局设置，持久化保存）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOn)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("系统显示设置", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "PWM 调光 / 原色显示等硬件级开关由厂商 ROM 控制，此处直达系统设置页",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
                }) {
                    Icon(OmniIcon.of("tune"), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("直达")
                }
            }
        }
    }
}
