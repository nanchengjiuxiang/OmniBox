package com.omnibox.feature.countdown

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibox.core.data.local.CountdownDao
import com.omnibox.core.data.local.CountdownEventEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CountdownScreenState { EMPTY, LIST, EDITOR }

enum class CountdownFilter(val label: String) { ALL("全部"), UPCOMING("即将到来"), PAST("已过去") }

/** 空态「灵感模板」：点击后按真实时间戳创建一个事件写入 Room。 */
enum class CountdownTemplate(
    val title: String,
    val daysFromNow: Long?,
    val repeatYearly: Boolean,
    val token: String
) {
    EXAM("考研 / 高考冲刺倒计时", 100L, false, "purple"),
    ANNIVERSARY("结婚 / 恋爱纪念日", null, true, "rose"),
    BIRTHDAY("生日提醒", null, true, "mint"),
    DEADLINE("证件 / 账单到期", 90L, false, "night")
}

data class CountdownDraft(
    val id: Long = 0L,
    val title: String = "",
    val dateText: String = "",
    val timeText: String = "09:00",
    val repeatYearly: Boolean = false,
    val notifyEnabled: Boolean = false,
    val colorToken: String = "purple",
    val createdAt: Long = 0L
)

data class CountdownUiState(
    val events: List<CountdownEventEntity> = emptyList(),
    val screen: CountdownScreenState = CountdownScreenState.EMPTY,
    val nowMillis: Long = System.currentTimeMillis(),
    val filter: CountdownFilter = CountdownFilter.ALL,
    val draft: CountdownDraft = CountdownDraft(),
    val notificationPermissionGranted: Boolean = true,
    val message: String? = null
)

sealed interface CountdownEvent {
    data class Tick(val nowMillis: Long) : CountdownEvent
    data object CreateNew : CountdownEvent
    data class Edit(val id: Long) : CountdownEvent
    data object CancelEditor : CountdownEvent
    data object SaveEditor : CountdownEvent
    data class Delete(val id: Long) : CountdownEvent
    data class SetTitle(val value: String) : CountdownEvent
    data class SetDate(val value: String) : CountdownEvent
    data class SetTime(val value: String) : CountdownEvent
    data class ApplyDatePreset(val kind: CountdownEngine.DatePreset) : CountdownEvent
    data class SetRepeatYearly(val value: Boolean) : CountdownEvent
    data class SetNotifyEnabled(val value: Boolean) : CountdownEvent
    data class SetColorToken(val value: String) : CountdownEvent
    data class SetFilter(val value: CountdownFilter) : CountdownEvent
    data class RefreshPermission(val granted: Boolean) : CountdownEvent
    data class ApplyTemplate(val kind: CountdownTemplate) : CountdownEvent
    data object ConsumeMessage : CountdownEvent
}

@HiltViewModel
class CountdownViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val countdownDao: CountdownDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CountdownUiState(
            nowMillis = System.currentTimeMillis(),
            notificationPermissionGranted = notificationsAllowed()
        )
    )
    val uiState: StateFlow<CountdownUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            countdownDao.observeAll().collect { list ->
                _uiState.update { s ->
                    val screen = if (s.screen == CountdownScreenState.EDITOR) CountdownScreenState.EDITOR
                    else if (list.isEmpty()) CountdownScreenState.EMPTY
                    else CountdownScreenState.LIST
                    s.copy(events = list, screen = screen)
                }
            }
        }
    }

    fun onEvent(event: CountdownEvent) {
        when (event) {
            is CountdownEvent.Tick -> _uiState.update { it.copy(nowMillis = event.nowMillis) }
            CountdownEvent.CreateNew -> openEditor(null)
            is CountdownEvent.Edit -> openEditor(_uiState.value.events.firstOrNull { it.id == event.id })
            CountdownEvent.CancelEditor -> _uiState.update {
                it.copy(
                    screen = if (it.events.isEmpty()) CountdownScreenState.EMPTY else CountdownScreenState.LIST,
                    draft = CountdownDraft()
                )
            }
            CountdownEvent.SaveEditor -> saveEditor()
            is CountdownEvent.Delete -> delete(event.id)
            is CountdownEvent.SetTitle -> updateDraft { it.copy(title = event.value) }
            is CountdownEvent.SetDate -> updateDraft { it.copy(dateText = event.value) }
            is CountdownEvent.SetTime -> updateDraft { it.copy(timeText = event.value) }
            is CountdownEvent.ApplyDatePreset -> {
                val (d, t) = CountdownEngine.quickPreset(event.kind, System.currentTimeMillis())
                updateDraft { it.copy(dateText = d, timeText = t) }
            }
            is CountdownEvent.SetRepeatYearly -> updateDraft { it.copy(repeatYearly = event.value) }
            is CountdownEvent.SetNotifyEnabled -> {
                if (event.value && !notificationsAllowed()) {
                    _uiState.update { it.copy(message = "通知权限未授予，请先授权后再开启提醒") }
                } else {
                    updateDraft { it.copy(notifyEnabled = event.value) }
                }
            }
            is CountdownEvent.SetColorToken -> updateDraft { it.copy(colorToken = event.value) }
            is CountdownEvent.SetFilter -> _uiState.update { it.copy(filter = event.value) }
            is CountdownEvent.RefreshPermission -> {
                val granted = event.granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                _uiState.update { s ->
                    s.copy(
                        notificationPermissionGranted = granted,
                        draft = if (granted) s.draft else s.draft.copy(notifyEnabled = false),
                        message = if (granted) "通知权限已授予" else "未授予通知权限，提醒功能已关闭"
                    )
                }
            }
            is CountdownEvent.ApplyTemplate -> applyTemplate(event.kind)
            CountdownEvent.ConsumeMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    /* ---------- 状态辅助 ---------- */

    private fun updateDraft(block: (CountdownDraft) -> CountdownDraft) {
        _uiState.update { it.copy(draft = block(it.draft)) }
    }

    private fun openEditor(entity: CountdownEventEntity?) {
        val draft = if (entity == null) {
            val (d, t) = CountdownEngine.defaultDateText(System.currentTimeMillis())
            CountdownDraft(dateText = d, timeText = t)
        } else {
            CountdownDraft(
                id = entity.id,
                title = entity.title,
                dateText = CountdownEngine.formatDate(entity.targetEpochMillis),
                timeText = CountdownEngine.formatTime(entity.targetEpochMillis),
                repeatYearly = entity.repeatYearly,
                notifyEnabled = entity.notifyEnabled,
                colorToken = entity.colorToken,
                createdAt = entity.createdAt
            )
        }
        _uiState.update { it.copy(screen = CountdownScreenState.EDITOR, draft = draft) }
    }

    /* ---------- Room 读写（全部 viewModelScope + Dispatchers.IO） ---------- */

    private fun saveEditor() {
        val s = _uiState.value
        val d = s.draft
        val title = d.title.trim()
        if (title.isEmpty()) {
            _uiState.update { it.copy(message = "请填写事件名称") }
            return
        }
        val target = CountdownEngine.parse(d.dateText, d.timeText)
        if (target == null) {
            _uiState.update { it.copy(message = "日期或时间格式不正确（yyyy-MM-dd / HH:mm）") }
            return
        }
        // 权限不满足时绝不落库 notifyEnabled=true，避免「假装提醒已设置」
        val notify = d.notifyEnabled && notificationsAllowed()
        val entity = CountdownEventEntity(
            id = d.id,
            title = title,
            targetEpochMillis = target,
            repeatYearly = d.repeatYearly,
            notifyEnabled = notify,
            colorToken = d.colorToken,
            createdAt = if (d.id == 0L) System.currentTimeMillis() else d.createdAt
        )
        viewModelScope.launch(Dispatchers.IO) {
            val rowId = countdownDao.upsert(entity)
            val saved = entity.copy(id = if (entity.id == 0L) rowId else entity.id)
            if (notify) CountdownAlarmScheduler.schedule(appContext, saved) else CountdownAlarmScheduler.cancel(appContext, saved.id)
            // 数据变了就刷新桌面小组件，避免桌面显示 15 分钟前的旧值
            notifyCountdownWidgetChanged(appContext)
        }
        _uiState.update {
            it.copy(
                screen = CountdownScreenState.LIST,
                draft = CountdownDraft(),
                message = "「$title」已保存" + if (notify) "（提醒已写入系统闹钟）" else ""
            )
        }
    }

    private fun delete(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            CountdownAlarmScheduler.cancel(appContext, id)
            countdownDao.delete(id)
            notifyCountdownWidgetChanged(appContext)
        }
        _uiState.update { s ->
            val remaining = s.events.filter { it.id != id }
            s.copy(
                screen = if (remaining.isEmpty()) CountdownScreenState.EMPTY else CountdownScreenState.LIST,
                draft = CountdownDraft(),
                message = "事件已删除，对应闹钟已取消"
            )
        }
    }

    private fun applyTemplate(kind: CountdownTemplate) {
        val now = System.currentTimeMillis()
        val (dateText, timeText) = if (kind.daysFromNow != null) {
            val epoch = CountdownEngine.plusDays(now, kind.daysFromNow)
            CountdownEngine.formatDate(epoch) to CountdownEngine.formatTime(epoch)
        } else {
            CountdownEngine.quickPreset(CountdownEngine.DatePreset.NEXT_YEAR, now)
        }
        val target = CountdownEngine.parse(dateText, timeText) ?: return
        val entity = CountdownEventEntity(
            title = kind.title,
            targetEpochMillis = target,
            repeatYearly = kind.repeatYearly,
            notifyEnabled = false,
            colorToken = kind.token,
            createdAt = now
        )
        viewModelScope.launch(Dispatchers.IO) {
            countdownDao.upsert(entity)
            notifyCountdownWidgetChanged(appContext)
        }
        _uiState.update { it.copy(message = "已创建「${kind.title}」") }
    }

    /* ---------- 通知权限 ---------- */

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
}
