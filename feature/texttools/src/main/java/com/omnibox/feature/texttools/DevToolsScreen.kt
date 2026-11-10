package com.omnibox.feature.texttools

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnibox.core.designsystem.*

/**
 * 对应 dev_tools/code.html（该页 <h1> 模板残留 "Network Ping Diagnostic"，
 * 实际内容为 JSON / 进制 / 正则 / URL 四组工具，按实际内容还原）。
 */
@Composable
fun DevToolsRoute(
    viewModel: DevToolsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        item {
            OmniTopBar(title = "代码与格式化", subtitle = "离线模式 · 零网络依赖",
                navigationIcon = OmniIcon.of("arrow_back"), onNavigationClick = onBack)
        }
        item {
            DtCard("shield", "纯前端 / 端侧离线沙箱",
                "所有代码、凭证与敏感配置仅在本地设备解析，100% 杜绝云端泄露风险。") {}
        }
        // JSON
        item {
            DtCard("data_object", "JSON", "2 空格") {
                Row {
                    listOf(2, 4).forEach { indent ->
                        DtChip("$indent 空格", state.jsonIndent == indent) {
                            viewModel.onEvent(DevToolsEvent.JsonIndentChanged(indent))
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    DtChip("填入示例", false) { viewModel.onEvent(DevToolsEvent.FillJsonSample) }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.jsonInput,
                    onValueChange = { viewModel.onEvent(DevToolsEvent.JsonInputChanged(it)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                    label = { Text("输入源 (JSON / JS Object)") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    shape = OmniShape.Input
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.onEvent(DevToolsEvent.BeautifyJson) }) { Text("美化 / 校验") }
                Spacer(Modifier.height(10.dp))
                Text("解析视窗 (高亮结构)", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val status = state.jsonError ?: if (state.jsonOutput.isBlank()) "就绪" else "解析成功"
                Text(status, style = MaterialTheme.typography.labelSmall,
                    color = if (state.jsonError != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary)
                if (state.jsonOutput.isNotBlank()) {
                    Surface(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = OmniShape.Input
                    ) {
                        Text(state.jsonOutput, style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(12.dp))
                    }
                    OmniIconAction("content_copy", "复制结果") {
                        clipboard.setText(AnnotatedString(state.jsonOutput))
                    }
                }
            }
        }
        // 进制
        item {
            DtCard("tag", "任意进制交互转换", "核心引擎联动实时推导并格式化输出") {
                Row {
                    listOf(2, 8, 10, 16).forEach { radix ->
                        DtChip("$radix 进制", state.baseRadix == radix) {
                            viewModel.onEvent(DevToolsEvent.BaseRadixChanged(radix))
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.baseInput,
                    onValueChange = { viewModel.onEvent(DevToolsEvent.BaseInputChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("输入任意进制数值") },
                    singleLine = true,
                    shape = OmniShape.Input
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    listOf(8, 16, 32).forEach { bits ->
                        DtChip("${bits}-bit", state.bitWidth == bits) {
                            viewModel.onEvent(DevToolsEvent.BitWidthChanged(bits))
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                val r = state.baseResult
                DtResult("十进制 (Decimal / Base 10)", r?.decimal, clipboard)
                DtResult("十六进制 (Hex / Base 16)", r?.hex?.let { "0x$it" }, clipboard)
                DtResult("二进制 (Binary / Base 2)", r?.binary, clipboard)
                DtResult("八进制 (Octal / Base 8)", r?.octal, clipboard)
                if (r == null) Text("无法解析，请检查输入与所选进制",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
        // 正则
        item {
            DtCard("regular_expression", "正则表达式与修饰符", "/gi") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(RegexFlag.IGNORE_CASE to "i", RegexFlag.MULTILINE to "m").forEach { (flag, label) ->
                        DtChip(label, flag in state.regexFlags) {
                            viewModel.onEvent(DevToolsEvent.ToggleRegexFlag(flag))
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("/${state.regexPattern}/", style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.regexPattern,
                    onValueChange = { viewModel.onEvent(DevToolsEvent.RegexPatternChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("表达式") },
                    singleLine = true, shape = OmniShape.Input
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.regexTest,
                    onValueChange = { viewModel.onEvent(DevToolsEvent.RegexTestChanged(it)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    label = { Text("测试字符串") }, shape = OmniShape.Input
                )
                Spacer(Modifier.height(8.dp))
                val rr = state.regexResult
                Text("${rr?.matches?.size ?: 0} 处匹配", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                rr?.error?.let { Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error) }
                rr?.matches?.forEach { m ->
                    Surface(Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = OmniShape.Input) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(m, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            OmniIconAction("content_copy", "复制匹配") {
                                clipboard.setText(AnnotatedString(m))
                            }
                        }
                    }
                }
                val groups = rr?.groups?.flatten().orEmpty()
                if (groups.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("捕获分组提取 (Capture Groups)", style = MaterialTheme.typography.labelMedium)
                    groups.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        // URL
        item {
            DtCard("link", "完整 URL / 查询串", "URL 彻底解码") {
                OutlinedTextField(
                    value = state.urlInput,
                    onValueChange = { viewModel.onEvent(DevToolsEvent.UrlInputChanged(it)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    label = { Text("完整 URL") }, shape = OmniShape.Input
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.onEvent(DevToolsEvent.FillUrlSample) }) { Text("填入示例") }
                Spacer(Modifier.height(10.dp))
                Text("拆解参数键值表 · ${state.urlParams.size} 项",
                    style = MaterialTheme.typography.labelMedium)
                state.urlParams.forEach { (key, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(key, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(2f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OmniPrimaryButton("一键导出参数表", onClick = {
                    clipboard.setText(AnnotatedString(
                        state.urlParams.joinToString("\n") { "${it.first}=${it.second}" }
                    ))
                }, icon = "download")
            }
        }
    }
}

@Composable
private fun DtCard(icon: String, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
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
                Spacer(Modifier.weight(1f))
                Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DtChip(label: String, selected: Boolean, onClick: () -> Unit) {
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

@Composable
private fun DtResult(label: String, value: String?, clipboard: androidx.compose.ui.platform.ClipboardManager) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value ?: "—", style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace, maxLines = 2)
        }
        if (value != null) {
            OmniIconAction("content_copy", "复制") { clipboard.setText(AnnotatedString(value)) }
        }
    }
}
