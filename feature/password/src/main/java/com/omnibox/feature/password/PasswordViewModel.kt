package com.omnibox.feature.password

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

enum class PasswordPreset(val label: String, val length: Int, val symbols: Boolean, val diceware: Boolean) {
    WIFI("Wi-Fi 路由器安全码", 20, true, false),
    FINANCE("金融级 24 位特强", 24, true, false),
    GEEK("极客高混淆 32 位", 32, true, false),
    PASSPHRASE("易读发音 Passphrase", 24, false, true)
}

data class PasswordUiState(
    val config: PasswordConfig = PasswordConfig(),
    val password: String = "",
    val entropyBits: Double = 0.0,
    val poolSize: Int = 0,
    val crackEstimate: String = "—",
    val strengthLabel: String = "—",
    val strengthProgress: Float = 0f,
    val warnings: List<String> = emptyList(),
    val batch: List<String> = emptyList()
)

sealed interface PasswordEvent {
    data object Regenerate : PasswordEvent
    data class LengthChanged(val value: Int) : PasswordEvent
    data class Toggle(val field: ConfigField) : PasswordEvent
    data class ApplyPreset(val preset: PasswordPreset) : PasswordEvent
    data class BatchSizeChanged(val value: Int) : PasswordEvent
}

enum class ConfigField {
    UPPER, LOWER, DIGITS, SYMBOLS, EXCLUDE_AMBIGUOUS, FORCE_ALL_CATEGORIES, DICEWARE, BATCH
}

@HiltViewModel
class PasswordViewModel @Inject constructor(
    private val historyDao: ToolHistoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(PasswordUiState())
    val uiState: StateFlow<PasswordUiState> = _uiState.asStateFlow()

    init { regenerate(PasswordConfig()) }

    fun onEvent(event: PasswordEvent) {
        val c = _uiState.value.config
        val next = when (event) {
            PasswordEvent.Regenerate -> c
            is PasswordEvent.LengthChanged -> c.copy(length = event.value.coerceIn(6, 64))
            is PasswordEvent.ApplyPreset -> c.copy(
                length = event.preset.length,
                symbols = event.preset.symbols,
                diceware = event.preset.diceware
            )
            is PasswordEvent.BatchSizeChanged -> c.copy(batchSize = event.value.coerceIn(5, 50))
            is PasswordEvent.Toggle -> when (event.field) {
                ConfigField.UPPER -> c.copy(upper = !c.upper)
                ConfigField.LOWER -> c.copy(lower = !c.lower)
                ConfigField.DIGITS -> c.copy(digits = !c.digits)
                ConfigField.SYMBOLS -> c.copy(symbols = !c.symbols)
                ConfigField.EXCLUDE_AMBIGUOUS -> c.copy(excludeAmbiguous = !c.excludeAmbiguous)
                ConfigField.FORCE_ALL_CATEGORIES -> c.copy(forceAllCategories = !c.forceAllCategories)
                ConfigField.DICEWARE -> c.copy(diceware = !c.diceware)
                ConfigField.BATCH -> c.copy(batchMode = !c.batchMode)
            }
        }
        // 至少保留一个字符集，避免生成空密码
        if (!next.upper && !next.lower && !next.digits && !next.symbols && !next.diceware) return
        regenerate(next)
    }

    private fun regenerate(config: PasswordConfig) {
        val value = PasswordEngine.generate(config)
        val bits = PasswordEngine.entropyBits(config)
        val batch = if (config.batchMode) PasswordEngine.generateBatch(config) else emptyList()
        _uiState.value = PasswordUiState(
            config = config,
            password = value,
            entropyBits = bits,
            poolSize = PasswordEngine.poolSize(config),
            crackEstimate = PasswordEngine.crackEstimate(bits),
            strengthLabel = PasswordEngine.strengthLabel(bits),
            strengthProgress = PasswordEngine.strengthProgress(bits),
            warnings = PasswordEngine.patternWarnings(value),
            batch = batch
        )
        // 只记录元信息（长度 / 熵值），密码本体绝不落盘
        viewModelScope.launch {
            historyDao.insert(
                ToolHistoryEntity(
                    toolId = "password",
                    summary = "生成 ${config.length} 位密码 · ${String.format("%.1f", bits)} bits",
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }
}
