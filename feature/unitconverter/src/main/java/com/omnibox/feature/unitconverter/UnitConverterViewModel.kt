package com.omnibox.feature.unitconverter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibox.core.data.local.ToolHistoryDao
import com.omnibox.core.data.local.ToolHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class UnitConverterUiState(
    val kind: UnitKind = UnitKind.LENGTH,
    val from: UnitDef = UnitEngine.defaultFrom(UnitKind.LENGTH),
    val to: UnitDef = UnitEngine.defaultTo(UnitKind.LENGTH),
    val input: String = "1",
    val precision: Int = 4,
    val result: String = "",
    val references: List<Triple<String, String, String>> = emptyList(),
    val ratio: String = "",
    val error: String? = null
)

sealed interface UnitConverterEvent {
    data class KindChanged(val kind: UnitKind) : UnitConverterEvent
    data class FromChanged(val id: String) : UnitConverterEvent
    data class ToChanged(val id: String) : UnitConverterEvent
    data class InputChanged(val value: String) : UnitConverterEvent
    data class PrecisionChanged(val value: Int) : UnitConverterEvent
    data object Swap : UnitConverterEvent
}

@HiltViewModel
class UnitConverterViewModel @Inject constructor(
    private val historyDao: ToolHistoryDao
) : ViewModel() {
    private val _uiState = MutableStateFlow(UnitConverterUiState())
    val uiState: StateFlow<UnitConverterUiState> = _uiState.asStateFlow()

    init { recompute(_uiState.value) }

    fun onEvent(event: UnitConverterEvent) {
        val s = _uiState.value
        val next = when (event) {
            is UnitConverterEvent.KindChanged -> s.copy(
                kind = event.kind,
                from = UnitEngine.defaultFrom(event.kind),
                to = UnitEngine.defaultTo(event.kind)
            )
            is UnitConverterEvent.FromChanged -> s.copy(
                from = UnitEngine.units.getValue(s.kind).first { it.id == event.id })
            is UnitConverterEvent.ToChanged -> s.copy(
                to = UnitEngine.units.getValue(s.kind).first { it.id == event.id })
            is UnitConverterEvent.InputChanged -> s.copy(input = event.value)
            is UnitConverterEvent.PrecisionChanged -> s.copy(precision = event.value)
            UnitConverterEvent.Swap -> s.copy(from = s.to, to = s.from, input = s.result.takeIf {
                it != "—" && it.toBigDecimalOrNull() != null
            } ?: s.input)
        }
        recompute(next)
        if (event is UnitConverterEvent.KindChanged || event is UnitConverterEvent.Swap) {
            viewModelScope.launch {
                historyDao.insert(
                    ToolHistoryEntity(
                        toolId = "unit_converter",
                        summary = "${next.kind.label} · ${next.from.label} → ${next.to.label}",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun recompute(s: UnitConverterUiState) {
        val value = s.input.trim().toBigDecimalOrNull()
        val converted = value?.let { UnitEngine.convert(it, s.from, s.to) }
        _uiState.value = s.copy(
            result = UnitEngine.format(converted, s.precision),
            references = UnitEngine.references(s.kind, s.precision),
            ratio = UnitEngine.ratio(s.from, s.to),
            error = if (value == null && s.input.isNotBlank()) "无法解析数值，请输入十进制数字" else null
        )
    }
}
