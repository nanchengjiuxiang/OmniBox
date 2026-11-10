package com.omnibox.feature.password

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnibox.core.designsystem.*

/** 对照 password_generator/code.html；演示值 98.4 Bits / 84 字符 / 3.2 亿年全部改为实时计算。 */
@Composable
fun PasswordRoute(
    viewModel: PasswordViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val clipboard: ClipboardManager = LocalClipboardManager.current

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).omniStatusBarInset().omniNavBarInset()) {
        item {
            OmniTopBar(
                title = "安全密码生成器 (Password Generator)",
                subtitle = "SecureRandom 强随机 · 离线密码学",
                navigationIcon = OmniIcon.of("arrow_back"),
                onNavigationClick = onBack
            ) {
                IconButton(onClick = onOpenHistory) { Icon(OmniIcon.of("history"), contentDescription = "历史") }
                IconButton(onClick = { viewModel.onEvent(PasswordEvent.Toggle(ConfigField.DICEWARE)) }) {
                    Icon(OmniIcon.of("casino"), contentDescription = "助记词模式")
                }
            }
        }
        item {
            OmniStaticCard("shield", "纯本地 CSPRNG · SecureRandom", "零权限 · 绝不上报") {
                OmniInfoLine("key", "Android Keystore 硬件随机源", "/dev/urandom 熵池充沛")
            }
        }
        item {
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = OmniShape.Card
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OmniLabel("生成结果 (Token)")
                        Spacer(Modifier.weight(1f))
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = OmniShape.Pill) {
                            Text("${String.format("%.1f", state.entropyBits)} Bits ${state.strengthLabel}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // 每个字符独立格子，逐字符展示真实生成结果
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        state.password.take(24).forEach { ch ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(OmniShape.Input)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(ch.toString(), style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("读音助记: ${mnemonic(state.password)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${state.password.length} Chars", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(14.dp))
                    Row {
                        OmniPrimaryButton("复制密码", onClick = {
                            clipboard.setText(AnnotatedString(state.password))
                        }, icon = "content_copy", modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        OmniPrimaryButton("重新生成", onClick = {
                            viewModel.onEvent(PasswordEvent.Regenerate)
                        }, icon = "refresh", modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            OmniStaticCard("analytics", "密码学熵值与强度评估", "离线穷举不可破") {
                // 自绘进度条，避免依赖版本相关的 LinearProgressIndicator 重载
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(OmniShape.Pill)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Box(
                        Modifier.fillMaxWidth(state.strengthProgress).height(8.dp).clip(OmniShape.Pill)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("极弱", "弱", "中等", "强", "密码学安全 (${String.format("%.1f", state.entropyBits)}b)")
                        .forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
                }
                Spacer(Modifier.height(12.dp))
                OmniInfoLine("info", "信息熵 (Entropy)", "${String.format("%.1f", state.entropyBits)} bits (推荐 ≥80)")
                OmniInfoLine("schedule", "破解预估耗时", "${state.crackEstimate} · 100T/s")
                OmniInfoLine("grid_view", "字符集空间", "${state.poolSize} 个独立字符")
                OmniInfoLine("check_circle", "字典与规律校验",
                    if (state.warnings.isEmpty()) "零模式 · 零字典重复"
                    else state.warnings.joinToString("、"))
            }
        }
        item {
            OmniStaticCard("tune", "生成规则与字符集配置", "${enabledCount(state.config)} 组集合已启用") {
                OmniLabel("密码总长度：${state.config.length}")
                Slider(
                    value = state.config.length.toFloat(),
                    onValueChange = { viewModel.onEvent(PasswordEvent.LengthChanged(it.toInt())) },
                    valueRange = 6f..64f,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(6, 16, 32, 64).forEach { len ->
                        OmniMiniChip("$len${if (len == 16) " (推荐)" else ""}",
                            state.config.length == len) {
                            viewModel.onEvent(PasswordEvent.LengthChanged(len))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OmniSwitchLine("大写字母", "A-Z (26)", state.config.upper) {
                    viewModel.onEvent(PasswordEvent.Toggle(ConfigField.UPPER))
                }
                OmniSwitchLine("小写字母", "a-z (26)", state.config.lower) {
                    viewModel.onEvent(PasswordEvent.Toggle(ConfigField.LOWER))
                }
                OmniSwitchLine("数字符号", "0-9 (10)", state.config.digits) {
                    viewModel.onEvent(PasswordEvent.Toggle(ConfigField.DIGITS))
                }
                OmniSwitchLine("特殊字符", "!@#\$%^&* (${PasswordEngine.categories(PasswordConfig(symbols = true)).lastOrNull()?.length ?: 22})",
                    state.config.symbols) { viewModel.onEvent(PasswordEvent.Toggle(ConfigField.SYMBOLS)) }
            }
        }
        item {
            OmniStaticCard("security", "高级防御与防混淆", "") {
                OmniSwitchLine("排除易混淆字符", "过滤 l, 1, I, O, 0, o 避免视读偏差",
                    state.config.excludeAmbiguous) {
                    viewModel.onEvent(PasswordEvent.Toggle(ConfigField.EXCLUDE_AMBIGUOUS))
                }
                OmniSwitchLine("全类别强制覆盖", "确保至少各包含 1 个选定字符分类",
                    state.config.forceAllCategories) {
                    viewModel.onEvent(PasswordEvent.Toggle(ConfigField.FORCE_ALL_CATEGORIES))
                }
                OmniSwitchLine("助记词 Diceware 模式", "使用离线词库拼接便于记忆的长词",
                    state.config.diceware) {
                    viewModel.onEvent(PasswordEvent.Toggle(ConfigField.DICEWARE))
                }
            }
        }
        item {
            OmniStaticCard("wifi_password", "推荐预设情景", "即点即用") {
                PasswordPreset.entries.forEach { preset ->
                    Row(
                        Modifier.fillMaxWidth().clip(OmniShape.Input).clickable {
                            viewModel.onEvent(PasswordEvent.ApplyPreset(preset))
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(OmniIcon.of("bolt"), contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(preset.label, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        Text("${preset.length} 位", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item {
            OmniStaticCard("dataset", "离线批量生成", "单次输出 5~50 条，支持加密导出") {
                OmniSwitchLine("开启批量", "结果仅在内存中展示，不写盘",
                    state.config.batchMode) {
                    viewModel.onEvent(PasswordEvent.Toggle(ConfigField.BATCH))
                }
                if (state.config.batchMode) {
                    OmniLabel("数量：${state.config.batchSize}")
                    Slider(
                        value = state.config.batchSize.toFloat(),
                        onValueChange = { viewModel.onEvent(PasswordEvent.BatchSizeChanged(it.toInt())) },
                        valueRange = 5f..50f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.batch.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(p, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            OmniIconAction("content_copy", "复制") { clipboard.setText(AnnotatedString(p)) }
                        }
                    }
                }
            }
        }
        item {
            Text("Java.security.SecureRandom 密码学强随机数生成器驱动，由系统底层熵池提供真随机种子。"
                + "计算全量驻留瞬时栈内存，无剪贴板监听，不发起任何网络传输。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(OmniDimens.ScreenPadding))
        }
    }
}

private fun enabledCount(c: PasswordConfig): Int =
    listOf(c.upper, c.lower, c.digits, c.symbols).count { it }

private val NATO = mapOf(
    'A' to "Alpha", 'B' to "Bravo", 'C' to "Charlie", 'D' to "Delta", 'E' to "Echo",
    'F' to "Foxtrot", 'G' to "Golf", 'H' to "Hotel", 'I' to "India", 'J' to "Juliet",
    'K' to "Kilo", 'L' to "Lima", 'M' to "Mike", 'N' to "November", 'O' to "Oscar",
    'P' to "Papa", 'Q' to "Quebec", 'R' to "Romeo", 'S' to "Sierra", 'T' to "Tango",
    'U' to "Uniform", 'V' to "Victor", 'W' to "Whisky", 'X' to "X-ray", 'Y' to "Yankee",
    'Z' to "Zulu", '8' to "Eight", '9' to "Nine", '#' to "Hash", '0' to "Zero"
)

private fun mnemonic(password: String): String = password.take(5)
    .map { NATO[it.uppercaseChar()] ?: it.toString() }
    .joinToString(" · ")

/* ---- 本模块复用的小组件（避免与 core:designsystem 的稳定 API 冲突） ---- */

@Composable
private fun OmniStaticCard(icon: String, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
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
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall)
    }
}
