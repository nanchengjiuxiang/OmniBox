package com.omnibox.feature.timestamp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnibox.core.designsystem.*
import java.util.Locale

/** 对照 _3/code.html 还原；1714567890 等演示值全部换成 System.currentTimeMillis 真实计算。 */
@Composable
fun TimestampRoute(
    viewModel: TimestampViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val seconds = state.parsed?.let { TimestampEngine.secondsOf(it.epochMillis) } ?: 0L
    val millis = state.parsed?.epochMillis ?: state.epochFromDate ?: 0L

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).omniStatusBarInset().omniNavBarInset()) {
        item {
            OmniTopBar(title = "时间戳转换与计算", subtitle = "Local Epoch System · 0ms 延迟 · 离线",
                navigationIcon = OmniIcon.of("arrow_back"), onNavigationClick = onBack)
        }
        // 当前实时时钟
        item {
            TsCard("speed", "当前时间 / 毫秒秒联动") {
                val sec = state.nowMillis / 1000L
                val ms = state.nowMillis % 1000L
                Text("$sec.$ms", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text(state.currentDisplay, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row {
                    OmniPrimaryButton("复制秒 (10位)", onClick = {
                        clipboard.setText(AnnotatedString(sec.toString()))
                    }, icon = "content_copy", modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OmniPrimaryButton("复制毫秒 (13位)", onClick = {
                        clipboard.setText(AnnotatedString(state.nowMillis.toString()))
                    }, icon = "content_copy", modifier = Modifier.weight(1f))
                }
            }
        }
        // 双向 Tab
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding)) {
                TsSegment("时间戳 → 日期", state.mode == TimestampMode.TS_TO_DATE,
                    Modifier.weight(1f)) { viewModel.onEvent(TimestampEvent.ModeChanged(TimestampMode.TS_TO_DATE)) }
                Spacer(Modifier.width(8.dp))
                TsSegment("日期 → 时间戳", state.mode == TimestampMode.DATE_TO_TS,
                    Modifier.weight(1f)) { viewModel.onEvent(TimestampEvent.ModeChanged(TimestampMode.DATE_TO_TS)) }
            }
        }
        // 时区选择
        item {
            OmniLabel("时区")
            LazyRow(
                contentPadding = PaddingValues(horizontal = OmniDimens.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TimestampEngine.timeZones) { (id, label) ->
                    TsSegment(label, state.zoneId == id) {
                        viewModel.onEvent(TimestampEvent.ZoneChanged(id))
                    }
                }
            }
        }
        if (state.mode == TimestampMode.TS_TO_DATE) {
            item {
                TsCard("schedule", "输入时间戳") {
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = { viewModel.onEvent(TimestampEvent.InputChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("毫秒 (13位) 或 秒级 (10位)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = OmniShape.Input
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.onEvent(TimestampEvent.UseNow) }) { Text("当前时间") }
                    if (state.error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(state.error!!, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        } else {
            item {
                TsCard("calendar_month", "选择本地日期与时间 · 系统时区") {
                    val f = state.dateFields
                    listOf(
                        Triple("年", f.year, DateField.YEAR),
                        Triple("月", f.month, DateField.MONTH),
                        Triple("日", f.day, DateField.DAY)
                    ).let { row1 ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row1.forEach { (label, value, field) ->
                                NumberField(label, value, Modifier.weight(1f)) {
                                    viewModel.onEvent(TimestampEvent.DateFieldChanged(field, it))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple("时", f.hour, DateField.HOUR),
                            Triple("分", f.minute, DateField.MINUTE),
                            Triple("秒", f.second, DateField.SECOND)
                        ).forEach { (label, value, field) ->
                            NumberField(label, value, Modifier.weight(1f)) {
                                viewModel.onEvent(TimestampEvent.DateFieldChanged(field, it))
                            }
                        }
                    }
                }
            }
        }
        // 格式化解析结果
        state.parsed?.let { p ->
            item {
                TsCard("event", "格式化解析结果") {
                    TsLine("判定为", if (p.unit == TimestampEngine.Unit.SECONDS) "秒级 (s)" else "毫秒级 (ms)")
                    TsLine("本地时间", p.local)
                    TsLine("ISO 8601 标准", p.iso)
                    TsLine("周天序数 (Week/Day)", p.weekDay)
                }
            }
        }
        // 转换结果
        item {
            TsCard("offline_bolt", "转换结果 (Unix Epoch) · 离线即时计算") {
                TsLine("秒级时间戳 (10-bit)", seconds.toString())
                Row {
                    OmniIconAction("content_copy", "复制秒") {
                        clipboard.setText(AnnotatedString(seconds.toString()))
                    }
                    Spacer(Modifier.width(8.dp))
                    OmniIconAction("copy_all", "复制毫秒") {
                        clipboard.setText(AnnotatedString(millis.toString()))
                    }
                }
                Spacer(Modifier.height(8.dp))
                TsLine("毫秒级时间戳 (13-bit)", millis.toString())
            }
        }
        // 时间差推演
        state.diff?.let { d ->
            item {
                TsCard("hourglass_top", "时间差与跨度推演") {
                    TsLine("相对当前时刻", d.relative)
                    TsLine("相距天数", d.spanText)
                    Row(Modifier.fillMaxWidth()) {
                        TsMetric("自然天", "${d.days}", Modifier.weight(1f))
                        TsMetric("预估工作日", "${d.workDays}", Modifier.weight(1f))
                        TsMetric("小时数", "${d.hours}", Modifier.weight(1f))
                        TsMetric("总计秒数", String.format(Locale.getDefault(), "%,d", d.totalSeconds), Modifier.weight(1f))
                    }
                }
            }
        }
        // 常量表
        item {
            TsCard("data_object", "开发常用时间常量表 · 点击一键填入") {
                TimestampEngine.constants.forEach { (label, sec) ->
                    Row(
                        Modifier.fillMaxWidth().clip(OmniShape.Input).clickable {
                            viewModel.onEvent(TimestampEvent.FillConstant(sec))
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(String.format(Locale.getDefault(), "%,d s", sec),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item {
            Text("基于 java.time (JSR-310) 现代时区算法，完全离线运行。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(OmniDimens.ScreenPadding))
        }
    }
}

@Composable
private fun TsCard(icon: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = OmniShape.CardSmall
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(OmniIcon.of(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun TsSegment(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = OmniShape.Pill,
        modifier = modifier.clip(OmniShape.Pill).clickable(onClick = onClick)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NumberField(label: String, value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { it.toIntOrNull()?.let(onChange) },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = OmniShape.Input
    )
}

@Composable
private fun TsLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun TsMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
