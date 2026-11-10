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

enum class RegexFlag { IGNORE_CASE, MULTILINE }

data class DevToolsUiState(
    val jsonInput: String = "",
    val jsonOutput: String = "",
    val jsonError: String? = null,
    val jsonIndent: Int = 2,
    val baseInput: String = "255",
    val baseRadix: Int = 10,
    val bitWidth: Int = 32,
    val baseResult: TextEngine.BaseResult? = null,
    val regexPattern: String = "[\\w.+-]+@[\\w-]+\\.[\\w.]+",
    val regexTest: String = TextEngine.REGEX_SAMPLE,
    val regexFlags: Set<RegexFlag> = setOf(RegexFlag.IGNORE_CASE),
    val regexResult: TextEngine.RegexResult? = null,
    val urlInput: String = TextEngine.URL_SAMPLE,
    val urlParams: List<Pair<String, String>> = emptyList(),
    val urlDecoded: String = ""
)

sealed interface DevToolsEvent {
    data class JsonInputChanged(val value: String) : DevToolsEvent
    data class JsonIndentChanged(val value: Int) : DevToolsEvent
    data object FillJsonSample : DevToolsEvent
    data object BeautifyJson : DevToolsEvent
    data class BaseInputChanged(val value: String) : DevToolsEvent
    data class BaseRadixChanged(val value: Int) : DevToolsEvent
    data class BitWidthChanged(val value: Int) : DevToolsEvent
    data class RegexPatternChanged(val value: String) : DevToolsEvent
    data class RegexTestChanged(val value: String) : DevToolsEvent
    data class ToggleRegexFlag(val flag: RegexFlag) : DevToolsEvent
    data class UrlInputChanged(val value: String) : DevToolsEvent
    data object FillUrlSample : DevToolsEvent
}

@HiltViewModel
class DevToolsViewModel @Inject constructor(
    private val historyDao: ToolHistoryDao
) : ViewModel() {
    private val _uiState = MutableStateFlow(DevToolsUiState())
    val uiState: StateFlow<DevToolsUiState> = _uiState.asStateFlow()

    init { recompute(_uiState.value) }

    fun onEvent(event: DevToolsEvent) {
        val s = _uiState.value
        val next = when (event) {
            is DevToolsEvent.JsonInputChanged -> s.copy(jsonInput = event.value)
            is DevToolsEvent.JsonIndentChanged -> s.copy(jsonIndent = event.value)
            DevToolsEvent.FillJsonSample -> s.copy(jsonInput = TextEngine.JSON_SAMPLE)
            DevToolsEvent.BeautifyJson -> {
                viewModelScope.launch {
                    historyDao.insert(
                        ToolHistoryEntity(
                            toolId = "dev_tools",
                            summary = "JSON 美化与语法校验",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
                s
            }
            is DevToolsEvent.BaseInputChanged -> s.copy(baseInput = event.value)
            is DevToolsEvent.BaseRadixChanged -> s.copy(baseRadix = event.value)
            is DevToolsEvent.BitWidthChanged -> s.copy(bitWidth = event.value)
            is DevToolsEvent.RegexPatternChanged -> s.copy(regexPattern = event.value)
            is DevToolsEvent.RegexTestChanged -> s.copy(regexTest = event.value)
            is DevToolsEvent.ToggleRegexFlag -> s.copy(
                regexFlags = if (event.flag in s.regexFlags) s.regexFlags - event.flag else s.regexFlags + event.flag
            )
            is DevToolsEvent.UrlInputChanged -> s.copy(urlInput = event.value)
            DevToolsEvent.FillUrlSample -> s.copy(urlInput = TextEngine.URL_SAMPLE)
        }
        recompute(next)
    }

    private fun recompute(s: DevToolsUiState) {
        val (json, err) = if (s.jsonInput.isBlank()) Pair("", null)
        else TextEngine.prettyJson(s.jsonInput, s.jsonIndent)
        val flags = buildSet {
            if (RegexFlag.IGNORE_CASE in s.regexFlags) add(RegexOption.IGNORE_CASE)
            if (RegexFlag.MULTILINE in s.regexFlags) add(RegexOption.MULTILINE)
        }
        _uiState.value = s.copy(
            jsonOutput = json,
            jsonError = err,
            baseResult = TextEngine.convertBase(s.baseInput, s.baseRadix, s.bitWidth),
            regexResult = TextEngine.regex(s.regexPattern, flags, s.regexTest),
            urlParams = TextEngine.urlParams(s.urlInput),
            urlDecoded = TextEngine.url(s.urlInput, encode = false)
        )
    }
}
