package com.omnibox.feature.texttools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnibox.core.common.Formatters
import com.omnibox.core.designsystem.*

/**
 * 同时覆盖两个页面 ID：
 * text_tools（text_hash，有输入时的结果页）与 text_tools_empty（_13，空输入时的空态）。
 * 两者是同一界面的两个真实状态，不是两张独立假页。
 */
@Composable
fun TextToolsRoute(
    viewModel: TextToolsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenDevTools: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).omniStatusBarInset().omniNavBarInset()) {
        item {
            OmniTopBar(
                title = "文本全能箱",
                subtitle = "纯本地离线沙盒 · 零网络请求",
                navigationIcon = OmniIcon.of("arrow_back"),
                onNavigationClick = onBack
            ) {
                IconButton(onClick = onOpenDevTools) { Icon(OmniIcon.of("code"), contentDescription = "开发者箱") }
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = OmniDimens.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TextTab.entries.toList()) { tab ->
                    TtChip(tab.label, state.tab == tab) { viewModel.onEvent(TextToolsEvent.TabChanged(tab)) }
                }
            }
        }
        // 输入区
        item {
            TtCard("edit_note", "源文本输入", "UTF-8 实时") {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = { viewModel.onEvent(TextToolsEvent.InputChanged(it)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    placeholder = { Text("输入或粘贴待处理文本") },
                    shape = OmniShape.Input
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = {
                        viewModel.onEvent(TextToolsEvent.Paste(clipboard.getText()?.text.orEmpty()))
                    }) { Text("粘贴") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { viewModel.onEvent(TextToolsEvent.Clear) }) { Text("清空") }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("字符 ${state.charCount}", style = MaterialTheme.typography.labelMedium)
                    Text("行数 ${state.lineCount}", style = MaterialTheme.typography.labelMedium)
                    Text("大小 ${Formatters.bytes(state.byteSize.toLong())}",
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (state.isEmpty) {
            // 页面 ID: text_tools_empty（_13）
            item {
                TtCard("auto_fix_high", "尚未输入或粘贴待处理文本",
                    "已就绪，正在准备沙盒运行环境...") {
                    Text("支持哈希摘要计算 (MD5 / SHA-256)、多维文本清洗 (去空行/首尾空格/全半角)、"
                        + "Base64 编解码与字数实时统计。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        OmniPrimaryButton("从剪贴板粘贴文本", onClick = {
                            viewModel.onEvent(TextToolsEvent.Paste(clipboard.getText()?.text.orEmpty()))
                        }, icon = "content_paste", modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OmniPrimaryButton("加载示例测试数据", onClick = {
                            viewModel.onEvent(TextToolsEvent.LoadSample)
                        }, icon = "dataset", modifier = Modifier.weight(1f))
                    }
                }
            }
            item {
                TtCard("bolt", "高频文本清洗模式", "点击填入") {
                    listOf(
                        Triple("reorder", "代码 / 日志清理", TextEngine.CleanOp.REMOVE_BLANK),
                        Triple("tag", "敏感散列计算 (MD5 / SHA)", TextEngine.CleanOp.TRIM_LINES),
                        Triple("spellcheck", "全角转半角排版", TextEngine.CleanOp.FULLWIDTH_TO_HALF),
                        Triple("sync_alt", "Base64 快速编解码", TextEngine.CleanOp.UNIQUE_LINES)
                    ).forEach { (icon, label, op) ->
                        TtPresetRow(icon, label, "一键尝试") {
                            viewModel.onEvent(TextToolsEvent.LoadSample)
                            viewModel.onEvent(TextToolsEvent.ApplyClean(op))
                        }
                    }
                }
            }
        } else {
            // 页面 ID: text_tools（text_hash）
            item {
                TtCard("space_bar", "快速文本清洗", "一触即洗") {
                    val ops = listOf(
                        "去除首尾空格" to TextEngine.CleanOp.TRIM_LINES,
                        "去除空白行" to TextEngine.CleanOp.REMOVE_BLANK,
                        "全角转半角" to TextEngine.CleanOp.FULLWIDTH_TO_HALF,
                        "中英排版规整" to TextEngine.CleanOp.TITLE
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ops) { (label, op) ->
                            TtChip(label, false) { viewModel.onEvent(TextToolsEvent.ApplyClean(op)) }
                        }
                    }
                }
            }
            if (state.tab == TextTab.HASH) {
                item {
                    TtCard("fingerprint", "数字摘要与散列计算", "") {
                        state.hashes.forEach { (label, value) ->
                            TtResultRow(label, value, clipboard)
                        }
                    }
                }
            }
            if (state.tab == TextTab.BASE64) {
                item {
                    TtCard("sync_alt", "Base64 结果", if (state.base64Encode) "编码" else "解码") {
                        Row {
                            TtChip("编码", state.base64Encode) { if (!state.base64Encode) viewModel.onEvent(TextToolsEvent.ToggleBase64Mode) }
                            Spacer(Modifier.width(8.dp))
                            TtChip("解码", !state.base64Encode) { if (state.base64Encode) viewModel.onEvent(TextToolsEvent.ToggleBase64Mode) }
                        }
                        Spacer(Modifier.height(10.dp))
                        TtResultRow("转换结果", state.output, clipboard)
                    }
                }
            }
            if (state.tab == TextTab.URL) {
                item {
                    TtCard("link", "URL 编码与解码", "URI 转化输出") {
                        Row {
                            TtChip("Encode", state.urlEncode) { if (!state.urlEncode) viewModel.onEvent(TextToolsEvent.ToggleUrlMode) }
                            Spacer(Modifier.width(8.dp))
                            TtChip("Decode", !state.urlEncode) { if (state.urlEncode) viewModel.onEvent(TextToolsEvent.ToggleUrlMode) }
                        }
                        Spacer(Modifier.height(10.dp))
                        TtResultRow("URI 转化输出", state.output, clipboard)
                    }
                }
            }
            if (state.tab == TextTab.FORMAT) {
                item {
                    TtCard("format_size", "文本大小写与样式变异", "") {
                        listOf(
                            "全部转大写" to TextEngine.CleanOp.UPPER,
                            "全部转小写" to TextEngine.CleanOp.LOWER,
                            "首字母大写" to TextEngine.CleanOp.TITLE,
                            "逐行文本去重" to TextEngine.CleanOp.UNIQUE_LINES
                        ).forEach { (label, op) ->
                            TtChip(label, false) { viewModel.onEvent(TextToolsEvent.ApplyClean(op)) }
                            Spacer(Modifier.height(6.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        TtResultRow("当前文本", state.input, clipboard)
                    }
                }
            }
        }
        item {
            Text("算法在当前设备原生内存中瞬时完成，剪贴板读取仅在明确点击「粘贴」时触发，"
                + "绝不静默监听后台，零网络收发。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(OmniDimens.ScreenPadding))
        }
    }
}

@Composable
private fun TtCard(icon: String, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
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
private fun TtChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = OmniShape.Pill,
        modifier = Modifier.clip(OmniShape.Pill).clickable(onClick = onClick)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TtResultRow(label: String, value: String, clipboard: androidx.compose.ui.platform.ClipboardManager) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall,
                maxLines = 2)
        }
        Spacer(Modifier.width(8.dp))
        OmniIconAction("content_copy", "复制") { clipboard.setText(AnnotatedString(value)) }
    }
}

@Composable
private fun TtPresetRow(icon: String, label: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(OmniShape.Input).clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(OmniIcon.of(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(action, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary)
    }
}
