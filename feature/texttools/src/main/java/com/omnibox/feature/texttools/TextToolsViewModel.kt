package com.omnibox.feature.texttools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibox.core.data.local.ToolHistoryDao
import com.omnibox.core.data.local.ToolHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TextTab(val label: String) { HASH("MD5/SHA 散列"), BASE64("Base64 编解码"), URL("URL 编解码"), FORMAT("排版与大小写") }

data class TextToolsUiState(
    val input: String = "",
    val tab: TextTab = TextTab.HASH,
    val base64Encode: Boolean = true,
    val urlEncode: Boolean = true,
    val output: String = "",
    val hashes: Map<String, String> = emptyMap(),
    val charCount: Int = 0,
    val lineCount: Int = 0,
    val byteSize: Int = 0
) {
    /** 设计稿 _13 的空状态页由这里驱动：输入为空即显示同一界面的空态，而非另一张假页面。 */
    val isEmpty: Boolean get() = input.isEmpty()
}

sealed interface TextToolsEvent {
    data class InputChanged(val value: String) : TextToolsEvent
    data class TabChanged(val value: TextTab) : TextToolsEvent
    data object ToggleBase64Mode : TextToolsEvent
    data object ToggleUrlMode : TextToolsEvent
    data object Clear : TextToolsEvent
    data class Paste(val value: String) : TextToolsEvent
    data object LoadSample : TextToolsEvent
    data class ApplyClean(val op: TextEngine.CleanOp) : TextToolsEvent
}

@HiltViewModel
class TextToolsViewModel @Inject constructor(
    private val historyDao: ToolHistoryDao
) : ViewModel() {
    private val _uiState = MutableStateFlow(TextToolsUiState(hashes = TextEngine.hashes("")))
    val uiState: StateFlow<TextToolsUiState> = _uiState.asStateFlow()

    fun onEvent(event: TextToolsEvent) {
        val s = _uiState.value
        val next = when (event) {
            is TextToolsEvent.InputChanged -> s.copy(input = event.value)
            is TextToolsEvent.TabChanged -> s.copy(tab = event.value)
            TextToolsEvent.ToggleBase64Mode -> s.copy(base64Encode = !s.base64Encode)
            TextToolsEvent.ToggleUrlMode -> s.copy(urlEncode = !s.urlEncode)
            TextToolsEvent.Clear -> s.copy(input = "")
            is TextToolsEvent.Paste -> s.copy(input = event.value)
            TextToolsEvent.LoadSample -> s.copy(input = TextEngine.SAMPLE)
            is TextToolsEvent.ApplyClean -> s.copy(input = TextEngine.clean(s.input, event.op))
        }
        recompute(next)
        if (event is TextToolsEvent.ApplyClean || event is TextToolsEvent.LoadSample) {
            viewModelScope.launch {
                historyDao.insert(
                    ToolHistoryEntity(
                        toolId = "text_tools",
                        summary = if (event is TextToolsEvent.ApplyClean) "文本清洗 · ${event.op.name}"
                        else "载入示例文本",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun recompute(s: TextToolsUiState) {
        val (chars, lines, bytes) = TextEngine.stats(s.input)
        val output = when (s.tab) {
            TextTab.HASH -> ""
            TextTab.BASE64 -> TextEngine.base64(s.input, s.base64Encode)
            TextTab.URL -> TextEngine.url(s.input, s.urlEncode)
            TextTab.FORMAT -> s.input
        }
        _uiState.value = s.copy(
            hashes = TextEngine.hashes(s.input),
            output = output,
            charCount = chars,
            lineCount = lines,
            byteSize = bytes
        )
    }
}
