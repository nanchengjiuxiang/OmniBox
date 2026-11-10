package com.omnibox.feature.unitconverter

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

/** 对照 unit_converter/code.html 还原；3.2808 等演示值全部换成 BigDecimal 实时换算。 */
@Composable
fun UnitConverterRoute(
    viewModel: UnitConverterViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val list = UnitEngine.units.getValue(state.kind)

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).omniStatusBarInset().omniNavBarInset()) {
        item {
            OmniTopBar(title = "单位换算", subtitle = "离线模式 · BigDecimal 零损耗",
                navigationIcon = OmniIcon.of("arrow_back"), onNavigationClick = onBack)
        }
        // 7 类物理量
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = OmniDimens.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(UnitKind.entries.toList()) { kind ->
                    UcChip(kind.label, state.kind == kind, kind.icon) {
                        viewModel.onEvent(UnitConverterEvent.KindChanged(kind))
                    }
                }
            }
        }
        // 来源物理量
        item {
            UcCard("输入源物理量", state.from.hint.ifBlank { "国际公制基础单位" }) {
                UcUnitPicker(list, state.from.id) { viewModel.onEvent(UnitConverterEvent.FromChanged(it)) }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.input,
                    onValueChange = { viewModel.onEvent(UnitConverterEvent.InputChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = OmniShape.Input
                )
                if (state.error != null) {
                    Text(state.error!!, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("来源：${state.from.label}", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    OmniIconAction("content_copy", "复制输入") {
                        clipboard.setText(AnnotatedString(state.input))
                    }
                }
            }
        }
        // 交换
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding),
                horizontalArrangement = Arrangement.Center) {
                OmniIconAction("swap_vert", "交换来源与目标") {
                    viewModel.onEvent(UnitConverterEvent.Swap)
                }
            }
        }
        // 换算目标
        item {
            UcCard("换算目标", state.to.hint.ifBlank { "实时 BigDecimal 换算" }) {
                UcUnitPicker(list, state.to.id) { viewModel.onEvent(UnitConverterEvent.ToChanged(it)) }
                Spacer(Modifier.height(10.dp))
                Text(state.result, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("目标：${state.to.label}", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    OmniIconAction("content_copy", "复制结果（长按复制）") {
                        clipboard.setText(AnnotatedString(state.result))
                    }
                }
            }
        }
        // 精度
        item {
            UcCard("计算保留小数精度", "HALF_EVEN · 四舍六入五成双") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 4, 6, 8).forEach { p ->
                        UcChip("$p 位", state.precision == p) {
                            viewModel.onEvent(UnitConverterEvent.PrecisionChanged(p))
                        }
                    }
                }
            }
        }
        // 基准速查
        item {
            UcCard("常用换算基准速查", "基于 1 标准单位") {
                state.references.forEach { (left, mid, right) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(left, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(mid, style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(right, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        // 比例尺度透视
        item {
            UcCard("两级比例尺度透视", "1 : ${state.ratio}") {
                UcLine("当前源量级比例", "1 ${state.from.label}")
                UcLine("目标单位扩展比", state.ratio)
                UcLine("舍入模式", "HALF_EVEN · ${state.precision} 位")
                if (state.kind == UnitKind.EXCHANGE) {
                    Text("汇率为内置离线快照值，不联网更新，仅作估算参考。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
        item {
            Text("100% 离线计算引擎：采用精确无损的十进制算法 (BigDecimal)，"
                + "杜绝浮点误差。汇率及基准单位均已固化存储至本地设备。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(OmniDimens.ScreenPadding))
        }
    }
}

@Composable
private fun UcCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = OmniShape.CardSmall
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun UcUnitPicker(units: List<UnitDef>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(units) { unit ->
            UcChip(unit.label, selected == unit.id) { onSelect(unit.id) }
        }
    }
}

@Composable
private fun UcChip(label: String, selected: Boolean, icon: String? = null, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = OmniShape.Pill,
        modifier = Modifier.clip(OmniShape.Pill).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(OmniIcon.of(icon), contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun UcLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}
