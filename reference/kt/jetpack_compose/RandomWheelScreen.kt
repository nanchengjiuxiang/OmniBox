package com.omnibox.feature.randomwheel

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.security.SecureRandom

data class WheelItem(val title: String, val weight: Int, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomWheelScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val rotationAnim = remember { Animatable(0f) }

    val items = remember {
        listOf(
            WheelItem("火锅", 2, Color(0xFFEF5350)),
            WheelItem("日料便当", 1, Color(0xFFAB47BC)),
            WheelItem("黄焖鸡米饭", 2, Color(0xFF42A5F5)),
            WheelItem("汉堡披萨", 1, Color(0xFFFFA726)),
            WheelItem("轻食沙拉", 1, Color(0xFF66BB6A))
        )
    }

    val totalWeight = items.sumOf { it.weight }

    fun spinWheel() {
        scope.launch {
            val randomTurns = 5 + SecureRandom().nextFloat() * 5
            val targetAngle = rotationAnim.value + randomTurns * 360f
            rotationAnim.animateTo(
                targetValue = targetAngle,
                animationSpec = tween(durationMillis = 3500, easing = FastOutSlowInEasing)
            )
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("随机转盘抽签决策", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("今天吃什么？", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            // 转盘绘制
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    var startAngle = rotationAnim.value % 360f
                    val diameter = size.minDimension
                    items.forEach { item ->
                        val sweepAngle = (item.weight.toFloat() / totalWeight) * 360f
                        drawArc(
                            color = item.color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true,
                            size = Size(diameter, diameter),
                            style = Fill
                        )
                        startAngle += sweepAngle
                    }
                }

                // 抽签圆钮
                Button(
                    onClick = { spinWheel() },
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Text("GO\n抽脂", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }

            // 选项列表
            Text("候选集权重列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            items.forEach { item ->
                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(item.color))
                            Spacer(Modifier.width(8.dp))
                            Text(item.title, fontWeight = FontWeight.SemiBold)
                        }
                        Text("权重: ${item.weight}", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}
