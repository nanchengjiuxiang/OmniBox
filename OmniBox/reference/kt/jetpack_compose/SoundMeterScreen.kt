package com.omnibox.feature.soundmeter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundMeterScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMonitoring by remember { mutableStateOf(true) }
    var currentDb by remember { mutableFloatStateOf(68.2f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("环境分贝计", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 实时采样 Hero 圈
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                            Spacer(Modifier.width(6.dp))
                            Text("实时监测中", style = MaterialTheme.typography.labelSmall)
                        }
                        Text("48kHz · A加权(dBA)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "%.1f".format(currentDb),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("dB 声压级", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)

                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text("喧闹杂音 · 警惕", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            // 统计指标
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricChipBox("实时", "68.2 dBA", Modifier.weight(1f))
                MetricChipBox("最小 Min", "34.2 dBA", Modifier.weight(1f))
                MetricChipBox("平均 Avg", "56.2 dBA", Modifier.weight(1f))
                MetricChipBox("峰值 Max", "86.5 dBA", Modifier.weight(1f))
            }

            // WHO 对照基准表
            Text("环境噪声对照基准 (WHO)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            WhoBenchmarkCard("微风落叶 · 卧室轻语", "20 dB", "极度安静，助眠环境")
            WhoBenchmarkCard("静音图书馆 · 幽静住宅", "40 dB", "专注思考，理想办公")
            WhoBenchmarkCard("日常交谈 · 室内空调运转", "60 dB", "当前状态所处声压区间", isCurrent = true)
            WhoBenchmarkCard("闹市区主干道 · 地铁车厢", "80 dB", "长时间暴露容易引发听觉疲劳")
            WhoBenchmarkCard("冲击电钻 · 飞机引擎起飞", "100+ dB", "危险级声级，需即刻防护")

            // 控制按钮
            Button(
                onClick = { isMonitoring = !isMonitoring },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp)
            ) {
                Icon(if (isMonitoring) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isMonitoring) "暂停实时监测" else "继续实时监测", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MetricChipBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(12.dp), modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun WhoBenchmarkCard(title: String, db: String, desc: String, isCurrent: Boolean = false) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Text(db, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified)
        }
    }
}
