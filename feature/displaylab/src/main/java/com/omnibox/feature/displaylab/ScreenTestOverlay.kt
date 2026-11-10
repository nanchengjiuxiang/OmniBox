package com.omnibox.feature.displaylab

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlin.math.pow

/** 全屏测试类型。退出统一走系统返回（手势/按键），巡检期间系统栏保持隐藏。 */
sealed interface ScreenTestMode {
    /** 纯色坏点巡检：五基色循环，轻点切换。 */
    data object DeadPixel : ScreenTestMode

    /** 256 阶灰阶：轻点在「阶梯」与「平滑渐变」间切换。 */
    data object Grayscale : ScreenTestMode

    /** 残影排查：黑白棋盘相位振荡。 */
    data object Checkerboard : ScreenTestMode

    /** 全屏多指触控：显示每个指针坐标、压感与最大并发数。 */
    data object TouchTest : ScreenTestMode

    /** 自动巡检：纯色×5 → 灰阶 → 棋盘，循环播放。 */
    data object AutoSequence : ScreenTestMode
}

/**
 * 沉浸式全屏测试：隐藏系统栏（返回手势仍可用），退出时恢复。
 * 注意：本页处于 edge-to-edge 全屏绘制，背景直接铺满整个窗口。
 */
@Composable
fun ScreenTestOverlay(mode: ScreenTestMode, onExit: () -> Unit) {
    val view = LocalView.current
    val activity = view.context as? ComponentActivity
    val controller = remember(activity) {
        activity?.let { WindowCompat.getInsetsController(it.window, view) }
    }

    DisposableEffect(Unit) {
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    BackHandler(onBack = onExit)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (mode) {
            ScreenTestMode.DeadPixel -> SolidColorTest()
            ScreenTestMode.Grayscale -> GrayscaleTest()
            ScreenTestMode.Checkerboard -> CheckerboardTest()
            ScreenTestMode.TouchTest -> FullscreenTouchTest()
            ScreenTestMode.AutoSequence -> AutoSequenceTest()
        }
    }
}

/** 顶部浮动提示：2.5 秒后淡出，避免干扰全屏纯色的检测视场。 */
@Composable
private fun BoxScope.FadingHint(text: String, key: Any?) {
    var visible by remember(key) { mutableStateOf(true) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600), label = "hint"
    )
    LaunchedEffect(key) {
        delay(2500)
        visible = false
    }
    if (alpha > 0.01f) {
        Text(
            text,
            color = Color.White.copy(alpha = alpha * 0.72f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(20.dp)
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun BoxScope.SolidColorTest() {
    var index by remember { mutableIntStateOf(0) }
    val (color, name) = DeadPixelColors[index]
    Box(
        Modifier.fillMaxSize()
            .background(Color(color))
            .clickable { index = (index + 1) % DeadPixelColors.size }
    )
    FadingHint(
        "第 ${index + 1}/${DeadPixelColors.size} 色 · $name\n轻点切换 · 返回键退出",
        index
    )
}

@Composable
private fun BoxScope.GrayscaleTest() {
    var stepped by remember { mutableStateOf(true) }
    Canvas(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .clickable { stepped = !stepped }
    ) {
        val steps = 256
        if (stepped) {
            val band = size.width / steps
            for (i in 0 until steps) {
                drawRect(
                    color = Color(grayLevel(i)),
                    topLeft = Offset(i * band, 0f),
                    size = Size(band + 1f, size.height)
                )
            }
        } else {
            // 平滑渐变：Gamma 2.2 感知均匀
            val stepPx = 2f
            var x = 0f
            while (x < size.width) {
                val t = (x / size.width).coerceIn(0f, 1f)
                val v = (255f * t.pow(1f / 2.2f)).toInt()
                drawRect(
                    color = Color(grayLevel(v)),
                    topLeft = Offset(x, 0f),
                    size = Size(stepPx + 1f, size.height)
                )
                x += stepPx
            }
        }
    }
    FadingHint(
        if (stepped) "256 阶灰阶（阶梯）" else "平滑渐变 · Gamma 2.2\n轻点切换模式",
        stepped
    )
}

@Composable
private fun BoxScope.CheckerboardTest() {
    CheckerboardBody()
    FadingHint("棋盘相位振荡 · 600ms\n观察残影与烧屏", null)
}

@Composable
private fun CheckerboardBody() {
    var phase by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(600)
            phase = !phase
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val cell = 48f
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val dark = ((r + c) % 2 == 0) != phase
                drawRect(
                    color = if (dark) Color.Black else Color.White,
                    topLeft = Offset(c * cell, r * cell),
                    size = Size(cell + 1f, cell + 1f)
                )
            }
        }
    }
}

private data class TouchDot(val id: Long, val position: Offset, val pressure: Float)

@Composable
private fun BoxScope.FullscreenTouchTest() {
    var pointers by remember { mutableStateOf(listOf<TouchDot>()) }
    var maxCount by remember { mutableIntStateOf(0) }
    var pollingMs by remember { mutableStateOf<Float?>(null) }
    val intervals = remember { ArrayDeque<Float>() }
    var lastNanos by remember { mutableStateOf(0L) }

    Canvas(
        Modifier.fillMaxSize().background(Color(0xFF101014)).pointerInput(Unit) {
            awaitEachGesture {
                while (true) {
                    val event = awaitPointerEvent()
                    val changes = event.changes.filter { it.pressed }
                    val now = System.nanoTime()
                    if (changes.isNotEmpty()) {
                        if (lastNanos != 0L && now > lastNanos) {
                            intervals.addLast((now - lastNanos) / 1_000_000f)
                            while (intervals.size > 64) intervals.removeFirst()
                            pollingMs = medianOrNull(intervals.toList())
                        }
                        lastNanos = now
                        pointers = changes.map { TouchDot(it.id.value.toLong(), it.position, it.pressure) }
                        if (changes.size > maxCount) maxCount = changes.size
                    } else {
                        pointers = emptyList()
                        lastNanos = 0L
                    }
                }
            }
        }
    ) {
        pointers.forEach { dot ->
            drawCircle(Color(0xFF6750A4), radius = 26f, center = dot.position)
            drawCircle(Color.White, radius = 4f, center = dot.position)
            drawLine(Color.White, Offset(dot.position.x, dot.position.y - 60f), Offset(dot.position.x, dot.position.y + 60f), 2f)
            drawLine(Color.White, Offset(dot.position.x - 60f, dot.position.y), Offset(dot.position.x + 60f, dot.position.y), 2f)
        }
    }
    Column(
        Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "最大并发 $maxCount 指 · 事件间隔 ${intervalLabel(pollingMs)}",
            color = Color.White, style = MaterialTheme.typography.titleMedium
        )
        pointers.forEach { dot ->
            Text(
                "#${dot.id}  x=${dot.position.x.toInt()} y=${dot.position.y.toInt()}  p=${pressureLabel(dot.pressure)}",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium
            )
        }
        if (pointers.isEmpty()) {
            Text(
                "单指 / 多指触摸屏幕开始测试",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** 自动巡检：红绿蓝黑白各 2s → 灰阶 4s → 棋盘 6s，无限循环。 */
@Composable
private fun BoxScope.AutoSequenceTest() {
    data class Step(val kind: String, val index: Int = 0)

    var step by remember { mutableStateOf(Step("solid", 0)) }

    LaunchedEffect(step) {
        when (step.kind) {
            "solid" -> {
                delay(2000)
                step = if (step.index < DeadPixelColors.size - 1) Step("solid", step.index + 1) else Step("grayscale")
            }
            "grayscale" -> { delay(4000); step = Step("checker") }
            "checker" -> { delay(6000); step = Step("solid", 0) }
        }
    }

    when (step.kind) {
        "solid" -> Box(Modifier.fillMaxSize().background(Color(DeadPixelColors[step.index].first)))
        "grayscale" -> Canvas(Modifier.fillMaxSize()) {
            val band = size.width / 256f
            for (i in 0 until 256) {
                drawRect(
                    color = Color(grayLevel(i)),
                    topLeft = Offset(i * band, 0f),
                    size = Size(band + 1f, size.height)
                )
            }
        }
        "checker" -> CheckerboardBody()
    }
    Text(
        "自动巡检 · " + when (step.kind) {
            "solid" -> "纯色 ${DeadPixelColors[step.index].second}（${step.index + 1}/${DeadPixelColors.size}）"
            "grayscale" -> "256 阶灰阶"
            else -> "棋盘振荡"
        },
        color = Color.White.copy(alpha = 0.75f),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
    )
}
