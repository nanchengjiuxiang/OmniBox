package com.omnibox.feature.randomwheel

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnibox.core.data.local.WheelItemEntity
import com.omnibox.core.designsystem.OmniDimens
import com.omnibox.core.designsystem.OmniIcon
import com.omnibox.core.designsystem.OmniIconAction
import com.omnibox.core.designsystem.OmniLabel
import com.omnibox.core.designsystem.OmniMetricCard
import com.omnibox.core.designsystem.OmniPrimaryButton
import com.omnibox.core.designsystem.OmniShape
import com.omnibox.core.designsystem.OmniTopBar
import com.omnibox.core.designsystem.omniNavBarInset
import com.omnibox.core.designsystem.omniStatusBarInset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 扇区固定调色板（设计稿的彩色转盘）；其余界面配色一律取 MaterialTheme.colorScheme。 */
private val WheelPalette = listOf(
    Color(0xFFEF5350), Color(0xFFAB47BC), Color(0xFF42A5F5), Color(0xFFFFA726),
    Color(0xFF66BB6A), Color(0xFF26C6DA), Color(0xFFEC407A), Color(0xFF8D6E63)
)

private fun wheelColor(token: String, index: Int): Color {
    val at = WHEEL_COLOR_TOKENS.indexOf(token)
    return WheelPalette[if (at >= 0) at else index % WheelPalette.size]
}

private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))

private fun percentText(ratio: Double): String =
    String.format(Locale.getDefault(), "%.1f%%", ratio * 100.0)

/** 公开入口。 */
@Composable
fun RandomWheelRoute(
    viewModel: RandomWheelViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    if (state.editorOpen) {
        WheelEditorScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
    } else {
        WheelMainScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
    }
}

/* ============================ 主转盘（random_wheel） ============================ */

@Composable
private fun WheelMainScreen(
    state: RandomWheelUiState,
    onEvent: (RandomWheelEvent) -> Unit,
    onBack: () -> Unit
) {
    val rotation = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val items = state.items
    val geometry = remember(items) { RandomWheelEngine.sectors(items) }
    val totalWeight = remember(items) { RandomWheelEngine.totalWeight(items) }
    val presetName = state.presets.firstOrNull { it.id == state.activePresetId }?.name ?: "幸运转盘"

    LaunchedEffect(state.spinToken) {
        val index = state.winningIndex
        if (state.spinToken <= 0 || index < 0 || index >= items.size) return@LaunchedEffect
        val center = RandomWheelEngine.sectorCenterAngle(items, index)
        val target = RandomWheelEngine.targetRotation(rotation.value, center, fullTurns = 5)
        rotation.animateTo(target, tween(durationMillis = 3600, easing = FastOutSlowInEasing))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onEvent(RandomWheelEvent.SpinFinished)
    }

    if (state.message != null) {
        LaunchedEffect(state.message) {
            kotlinx.coroutines.delay(2600L)
            onEvent(RandomWheelEvent.ConsumeMessage)
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).omniStatusBarInset().omniNavBarInset()) {
        item {
            OmniTopBar(
                title = "幸运转盘 · 随机转盘抽签",
                subtitle = "SecureRandom 加密真随机 · 100% 离线",
                navigationIcon = OmniIcon.of("arrow_back"),
                onNavigationClick = onBack
            ) {
                IconButton(onClick = { onEvent(RandomWheelEvent.OpenEditor) }) {
                    Icon(OmniIcon.of("edit"), contentDescription = "编辑选项")
                }
            }
        }
        if (state.message != null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = OmniShape.CardSmall,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp)
                ) {
                    Text(
                        state.message.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(presetName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "共 ${items.size} 个选项 · 权重合计 $totalWeight · 加权几何扇区",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                WheelCanvas(
                    items = items,
                    geometry = geometry,
                    rotationDegrees = rotation.value,
                    spinning = state.spinning,
                    onSpin = { onEvent(RandomWheelEvent.Spin) }
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "加密真随机引擎 (SecureRandom) · 毫秒级触觉震感",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (state.results.isNotEmpty()) {
            item {
                val winner = state.results.last()
                OmniStaticCard("celebration", "抽取结果已揭晓", "指针停在中选扇区中心") {
                    Text(
                        winner.label,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "抽中时间 ${formatTimestamp(winner.timestampMillis)} · 共 ${state.results.size} 次抽取",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OmniMetricCard("中选权重", "${winner.weight}", Modifier.weight(1f))
                        OmniMetricCard("中选概率", percentText(winner.probability), Modifier.weight(1f))
                    }
                    if (state.results.size > 1) {
                        Spacer(Modifier.height(10.dp))
                        OmniLabel("本次连续抽取（逐次独立加权）")
                        state.results.forEachIndexed { i, p ->
                            OmniInfoLine("check_circle", "第 ${i + 1} 次", "${p.label} · ${percentText(p.probability)}")
                        }
                    }
                }
            }
        }
        item {
            OmniStaticCard("scale", "选项配置与权重", "扇区占比按权重实时换算") {
                if (items.isEmpty()) {
                    Text("当前轮盘还没有选项，点击右上角编辑添加。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    items.forEachIndexed { index, item ->
                        val ratio = if (totalWeight > 0) item.weight.toDouble() / totalWeight else 0.0
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(12.dp).clip(CircleShape)
                                    .background(wheelColor(item.colorToken, index))
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(item.label, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            Text("权重 ${item.weight}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(10.dp))
                            Text(percentText(ratio), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OmniPrimaryButton("编辑选项 / 权重", onClick = { onEvent(RandomWheelEvent.OpenEditor) },
                    icon = "edit", modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            OmniStaticCard("shuffle", "算法偏好", "全部为真实开关") {
                WheelAlgorithmPrefs(state, onEvent)
            }
        }
        item {
            OmniStaticCard("security", "防作弊机制与本地保护", "100% 离线") {
                Text(
                    "采用 java.security.SecureRandom 生成密码学安全随机种子，按每项权重做真实加权抽样；" +
                        "指针偏转角度由中选扇区中心反算，杜绝视觉与逻辑偏差。全部数据仅存本机 Room，零网络传输。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OmniInfoLine("vibration", "高敏触觉反馈", "停针时输出原生震感")
                OmniInfoLine("history_edu", "防篡改日志", "每次抽取写入 ToolHistory")
            }
        }
        item {
            OmniStaticCard("history", "最近抽选历史", "${state.history.size} 条本地记录") {
                if (state.history.isEmpty()) {
                    Text("暂无抽取记录，转动一次转盘即可生成。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    state.history.take(10).forEach { entry ->
                        OmniInfoLine("check_circle", formatTimestamp(entry.createdAt), entry.summary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onEvent(RandomWheelEvent.ClearHistory) },
                        modifier = Modifier.weight(1f)) { Text("清空记录") }
                    TextButton(onClick = { onEvent(RandomWheelEvent.SaveAsPreset) },
                        modifier = Modifier.weight(1f)) { Text("另存为方案") }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OmniPrimaryButton("打乱顺序", onClick = { onEvent(RandomWheelEvent.ShuffleOrder) },
                    icon = "shuffle", modifier = Modifier.weight(1f))
                OmniPrimaryButton("编辑选项", onClick = { onEvent(RandomWheelEvent.OpenEditor) },
                    icon = "edit", modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 用 Canvas 真实绘制扇区、扇区中文、中心圆与顶部固定指针。 */
@Composable
private fun WheelCanvas(
    items: List<WheelItemEntity>,
    geometry: List<Pair<Float, Float>>,
    rotationDegrees: Float,
    spinning: Boolean,
    onSpin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pointerColor = MaterialTheme.colorScheme.onSurface
    val hubColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val hubBorder = MaterialTheme.colorScheme.outline

    Box(modifier = modifier.size(288.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f - 6.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f
            val center = Offset(cx, cy)
            val topLeft = Offset(cx - radius, cy - radius)
            val arcSize = Size(radius * 2f, radius * 2f)
            val rot = rotationDegrees % 360f
            val paint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = 13.sp.toPx()
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD
                )
            }
            items.forEachIndexed { index, item ->
                val (start, sweep) = geometry.getOrElse(index) { -90f to 0f }
                if (sweep <= 0f) return@forEachIndexed
                drawArc(
                    color = wheelColor(item.colorToken, index),
                    startAngle = start + rot,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Fill
                )
                val mid = start + rot + sweep / 2f
                val label = if (item.label.length > 6) item.label.take(5) + "…" else item.label
                // 中文沿半径方向摆放：旋转 nativeCanvas 后用 Paint 绘制，避开 experimental TextMeasurer
                drawIntoCanvas { canvas ->
                    val nc = canvas.nativeCanvas
                    nc.save()
                    nc.rotate(mid + 90f, cx, cy)
                    val fm = paint.fontMetrics
                    val baseY = cy - radius * 0.62f - (fm.ascent + fm.descent) / 2f
                    nc.drawText(label, cx, baseY, paint)
                    nc.restore()
                }
            }
            drawCircle(color = hubColor, radius = radius * 0.19f, center = center)
            drawCircle(
                color = hubBorder, radius = radius * 0.19f, center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            // 顶部固定指针（不随转盘旋转）
            val pointer = Path().apply {
                moveTo(cx, cy - radius + 14.dp.toPx())
                lineTo(cx - 14.dp.toPx(), cy - radius - 16.dp.toPx())
                lineTo(cx + 14.dp.toPx(), cy - radius - 16.dp.toPx())
                close()
            }
            drawPath(pointer, pointerColor)
        }
        Button(
            onClick = onSpin,
            enabled = !spinning && items.isNotEmpty(),
            shape = CircleShape,
            contentPadding = PaddingValues(4.dp),
            modifier = Modifier.size(76.dp)
        ) {
            Text(
                if (spinning) "…" else "GO\n抽取",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ColumnScope.WheelAlgorithmPrefs(
    state: RandomWheelUiState,
    onEvent: (RandomWheelEvent) -> Unit
) {
    OmniSwitchLine(
        label = if (state.allowRepeat) "允许重复" else "去重抽取（最近 3 次结果排他）",
        hint = if (state.allowRepeat) "关闭后启用防刷去重冷却：避免连续命中"
        else "防刷去重冷却已开启，本批次连续抽取不重复",
        checked = state.allowRepeat,
        onChange = { onEvent(RandomWheelEvent.SetAllowRepeat(it)) }
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("连续抽取 N 次", style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        IconButton(onClick = { onEvent(RandomWheelEvent.SetDrawCount(state.drawCount - 1)) },
            enabled = state.drawCount > 1, modifier = Modifier.size(36.dp)) {
            Text("-", style = MaterialTheme.typography.titleMedium)
        }
        Text("${state.drawCount}", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.widthIn(min = 26.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onEvent(RandomWheelEvent.SetDrawCount(state.drawCount + 1)) },
            enabled = state.drawCount < 10, modifier = Modifier.size(36.dp)) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
    Text("每次抽取都是独立的 SecureRandom 加权抽样，逐条写入本地历史。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/* ============================ 轮盘编辑（wheel_editor） ============================ */

@Composable
private fun WheelEditorScreen(
    state: RandomWheelUiState,
    onEvent: (RandomWheelEvent) -> Unit,
    onBack: () -> Unit
) {
    var newItemText by remember { mutableStateOf("") }
    val totalWeight = RandomWheelEngine.totalWeight(state.items)
    val activeName = state.presets.firstOrNull { it.id == state.activePresetId }?.name ?: "未命名"

    if (state.message != null) {
        LaunchedEffect(state.message) {
            kotlinx.coroutines.delay(2600L)
            onEvent(RandomWheelEvent.ConsumeMessage)
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).omniStatusBarInset().omniNavBarInset()) {
        item {
            OmniTopBar(
                title = "转盘决策编辑",
                subtitle = "当前轮盘：$activeName",
                navigationIcon = OmniIcon.of("arrow_back"),
                onNavigationClick = onBack
            ) {
                TextButton(onClick = { onEvent(RandomWheelEvent.CloseEditor) }) {
                    Icon(OmniIcon.of("check"), contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("完成并生效")
                }
            }
        }
        if (state.message != null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = OmniShape.CardSmall,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp)
                ) {
                    Text(state.message.orEmpty(), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp))
                }
            }
        }
        item {
            OmniStaticCard("casino", "轮盘方案", "${state.presets.size} 个本地方案 · 改动即时落盘") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.presets.forEach { preset ->
                        OmniMiniChip(preset.name, preset.id == state.activePresetId) {
                            onEvent(RandomWheelEvent.SelectPreset(preset.id))
                        }
                    }
                    OmniMiniChip("+ 新建预设", false) { onEvent(RandomWheelEvent.AddPreset) }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.presetNameDraft,
                    onValueChange = { onEvent(RandomWheelEvent.SetPresetName(it)) },
                    label = { Text("轮盘名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OmniPrimaryButton("保存预设", onClick = { onEvent(RandomWheelEvent.SavePreset) },
                    icon = "save", modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            OmniStaticCard("restaurant", "快捷场景模板", "点击即替换当前轮盘选项并写入 Room") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WHEEL_TEMPLATES.forEachIndexed { index, (name, _) ->
                        OmniMiniChip(name, false) { onEvent(RandomWheelEvent.ApplyTemplate(index)) }
                    }
                }
            }
        }
        item {
            OmniStaticCard("pie_chart", "权重配置总览", "${state.items.size} 项候选") {
                Text("扇区占比按权重自动换算，当前权重合计 $totalWeight（100% 闭合）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                state.items.forEachIndexed { index, item ->
                    val ratio = if (totalWeight > 0) item.weight.toDouble() / totalWeight else 0.0
                    OmniInfoLine("scale", item.label, "${percentText(ratio)} · 权重 ${item.weight}")
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onEvent(RandomWheelEvent.ResetWeights) },
                        modifier = Modifier.weight(1f)) { Text("均分所有权重") }
                    TextButton(onClick = { onEvent(RandomWheelEvent.ShuffleOrder) },
                        modifier = Modifier.weight(1f)) { Text("打乱顺序") }
                }
            }
        }
        item {
            OmniStaticCard("list", "候选选项列表 (${state.items.size})", "所有编辑立即持久化") {
                if (state.items.isEmpty()) {
                    Text("暂无选项，新增后将立即写入本地数据库。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.items.forEachIndexed { index, item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = OmniShape.Input,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(22.dp).clip(CircleShape)
                                        .background(wheelColor(item.colorToken, index))
                                        .clickable { onEvent(RandomWheelEvent.CycleItemColor(item.id)) }
                                )
                                Spacer(Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = item.label,
                                    onValueChange = { onEvent(RandomWheelEvent.UpdateItemLabel(item.id, it)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(6.dp))
                                IconButton(onClick = { onEvent(RandomWheelEvent.DeleteItem(item.id)) }) {
                                    Icon(OmniIcon.of("delete"), contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("权重", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                                IconButton(onClick = { onEvent(RandomWheelEvent.StepItemWeight(item.id, -1)) },
                                    modifier = Modifier.size(32.dp)) {
                                    Text("-", style = MaterialTheme.typography.titleMedium)
                                }
                                Text("${item.weight}", style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.widthIn(min = 26.dp), textAlign = TextAlign.Center)
                                IconButton(onClick = { onEvent(RandomWheelEvent.StepItemWeight(item.id, 1)) },
                                    modifier = Modifier.size(32.dp)) {
                                    Text("+", style = MaterialTheme.typography.titleMedium)
                                }
                                Spacer(Modifier.weight(1f))
                                val ratio = if (totalWeight > 0) item.weight.toDouble() / totalWeight else 0.0
                                Text(percentText(ratio), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary)
                                IconButton(onClick = { onEvent(RandomWheelEvent.MoveItem(item.id, -1)) },
                                    modifier = Modifier.size(32.dp)) {
                                    Icon(OmniIcon.of("arrow_drop_down"), contentDescription = "上移",
                                        modifier = Modifier.rotate(180f).size(18.dp))
                                }
                                IconButton(onClick = { onEvent(RandomWheelEvent.MoveItem(item.id, 1)) },
                                    modifier = Modifier.size(32.dp)) {
                                    Icon(OmniIcon.of("arrow_drop_down"), contentDescription = "下移",
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            OmniStaticCard("add_circle", "新增选项", "新增项使用 id=0 由 Room 自动生成主键") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newItemText,
                        onValueChange = { newItemText = it },
                        placeholder = { Text("输入候选名称") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OmniIconAction("add", "添加") {
                        onEvent(RandomWheelEvent.AddItem(newItemText))
                        newItemText = ""
                    }
                }
            }
        }
        item {
            OmniStaticCard("content_paste", "批量文本粘贴导入", "每行一个；支持「标签,权重」或「标签 权重」") {
                OutlinedTextField(
                    value = state.batchImportText,
                    onValueChange = { onEvent(RandomWheelEvent.SetBatchImport(it)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("火锅,2\n日料便当 1\n轻食沙拉") }
                )
                Spacer(Modifier.height(8.dp))
                OmniPrimaryButton("导入到当前轮盘", onClick = { onEvent(RandomWheelEvent.ImportBatch) },
                    icon = "content_paste", modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            OmniStaticCard("tune", "交互反馈与算法偏好", "真实生效") {
                OmniInfoLine("vibration", "阻尼触觉震动", "停针时输出原生震感")
                WheelAlgorithmPrefs(state, onEvent)
            }
        }
        item {
            OmniPrimaryButton("完成并生效", onClick = { onEvent(RandomWheelEvent.CloseEditor) },
                icon = "check", modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = OmniDimens.ScreenPadding, vertical = 12.dp))
        }
    }
}

/* ---- 本模块复用的小组件（沿用 PasswordScreen 的稳定 API 模式） ---- */

@Composable
private fun OmniStaticCard(
    icon: String,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
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
private fun OmniInfoLine(icon: String, label: String, value: String) {
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
private fun OmniSwitchLine(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
private fun OmniMiniChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = OmniShape.Pill,
        modifier = Modifier.clip(OmniShape.Pill).clickable(onClick = onClick)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelSmall)
    }
}
