package com.omnibox.feature.timestamp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibox.core.data.local.ToolHistoryDao
import com.omnibox.core.data.local.ToolHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.util.Locale
import javax.inject.Inject

enum class TimestampMode { TS_TO_DATE, DATE_TO_TS }

data class DateFields(
    val year: Int, val month: Int, val day: Int,
    val hour: Int, val minute: Int, val second: Int
)

data class TimestampUiState(
    val nowMillis: Long = 0L,
    val input: String = "",
    val zoneId: String = "Asia/Shanghai",
    val mode: TimestampMode = TimestampMode.TS_TO_DATE,
    val parsed: TimestampEngine.Parsed? = null,
    val diff: TimestampEngine.Diff? = null,
    val epochFromDate: Long? = null,
    val dateFields: DateFields = DateFields(2024, 5, 1, 12, 51, 30),
    val currentDisplay: String = "",
    val error: String? = null
)

sealed interface TimestampEvent {
    data class InputChanged(val value: String) : TimestampEvent
    data class ZoneChanged(val value: String) : TimestampEvent
    data class ModeChanged(val value: TimestampMode) : TimestampEvent
    data object UseNow : TimestampEvent
    data class FillConstant(val seconds: Long) : TimestampEvent
    data class DateFieldChanged(val field: DateField, val value: Int) : TimestampEvent
}

enum class DateField { YEAR, MONTH, DAY, HOUR, MINUTE, SECOND }

@HiltViewModel
class TimestampViewModel @Inject constructor(
    private val historyDao: ToolHistoryDao
) : ViewModel() {
    private val _uiState = MutableStateFlow(TimestampUiState())
    val uiState: StateFlow<TimestampUiState> = _uiState.asStateFlow()

    init {
        val now = TimestampEngine.nowMillis()
        recompute(TimestampUiState(nowMillis = now, input = TimestampEngine.secondsOf(now).toString()))
    }

    fun onEvent(event: TimestampEvent) {
        val s = _uiState.value
        when (event) {
            is TimestampEvent.InputChanged -> recompute(s.copy(input = event.value, nowMillis = TimestampEngine.nowMillis()))
            is TimestampEvent.ZoneChanged -> recompute(s.copy(zoneId = event.value))
            is TimestampEvent.ModeChanged -> recompute(s.copy(mode = event.value))
            TimestampEvent.UseNow -> {
                val now = TimestampEngine.nowMillis()
                recompute(s.copy(nowMillis = now, input = TimestampEngine.secondsOf(now).toString()))
            }
            is TimestampEvent.FillConstant -> recompute(s.copy(input = event.seconds.toString()))
            is TimestampEvent.DateFieldChanged -> {
                val f = s.dateFields
                val next = when (event.field) {
                    DateField.YEAR -> f.copy(year = event.value.coerceIn(1970, 2100))
                    DateField.MONTH -> f.copy(month = event.value.coerceIn(1, 12))
                    DateField.DAY -> f.copy(day = event.value.coerceIn(1, 31))
                    DateField.HOUR -> f.copy(hour = event.value.coerceIn(0, 23))
                    DateField.MINUTE -> f.copy(minute = event.value.coerceIn(0, 59))
                    DateField.SECOND -> f.copy(second = event.value.coerceIn(0, 59))
                }
                recompute(s.copy(dateFields = next))
            }
        }
    }

    private fun recompute(base: TimestampUiState) {
        val parsed = TimestampEngine.parse(base.input, base.zoneId)
        val epochFromDate = TimestampEngine.dateToEpochMillis(
            base.dateFields.year, base.dateFields.month, base.dateFields.day,
            base.dateFields.hour, base.dateFields.minute, base.dateFields.second, base.zoneId
        )
        val target = if (base.mode == TimestampMode.DATE_TO_TS) epochFromDate else parsed?.epochMillis
        _uiState.value = base.copy(
            currentDisplay = TimestampEngine.currentDisplay(base.zoneId),
            parsed = if (base.mode == TimestampMode.TS_TO_DATE) parsed else epochFromDate?.let {
                TimestampEngine.format(it, base.zoneId)
            },
            epochFromDate = epochFromDate,
            diff = target?.let { TimestampEngine.diff(it) },
            error = if (base.mode == TimestampMode.TS_TO_DATE && parsed == null) "无法解析，请输入 10 位秒或 13 位毫秒时间戳" else null
        )
        if (parsed != null || epochFromDate != null) {
            viewModelScope.launch {
                historyDao.insert(
                    ToolHistoryEntity(
                        toolId = "timestamp",
                        summary = "${if (base.mode == TimestampMode.TS_TO_DATE) "时间戳→日期" else "日期→时间戳"} · ${base.zoneId}",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
