package com.omnibox.feature.randomwheel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibox.core.data.local.ToolHistoryDao
import com.omnibox.core.data.local.ToolHistoryEntity
import com.omnibox.core.data.local.WheelDao
import com.omnibox.core.data.local.WheelItemEntity
import com.omnibox.core.data.local.WheelPresetEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

private const val TOOL_ID = "random_wheel"

/** 设计稿 quick scene 模板：点击后作为真实数据写入 Room，不参与主转盘默认展示。 */
internal val WHEEL_TEMPLATES: List<Pair<String, List<String>>> = listOf(
    "今天吃什么" to listOf("火锅", "日料便当", "黄焖鸡米饭", "汉堡披萨", "轻食沙拉"),
    "周末去哪玩" to listOf("爬山", "看电影", "逛展", "宅家", "露营"),
    "真心话大冒险" to listOf("真心话", "大冒险", "再来一次", "跳过", "加倍"),
    "任务谁来做" to listOf("我", "你", "他", "大家一起", "抽签再定"),
    "聚会掷骰" to listOf("唱首歌", "讲笑话", "喝一杯", "表演节目", "免罚一次")
)

/** colorToken 的取值域；Screen 侧映射为固定扇区调色板。 */
internal val WHEEL_COLOR_TOKENS = listOf("red", "purple", "blue", "orange", "green", "cyan", "pink", "brown")

data class RandomWheelUiState(
    val presets: List<WheelPresetEntity> = emptyList(),
    val activePresetId: Long = 0L,
    val items: List<WheelItemEntity> = emptyList(),
    val editorOpen: Boolean = false,
    val presetNameDraft: String = "",
    val batchImportText: String = "",
    val allowRepeat: Boolean = true,
    val drawCount: Int = 1,
    val spinToken: Int = 0,
    val winningIndex: Int = -1,
    val results: List<RandomWheelEngine.Pick> = emptyList(),
    val recentLabels: List<String> = emptyList(),
    val history: List<ToolHistoryEntity> = emptyList(),
    val spinning: Boolean = false,
    val message: String? = null
)

sealed interface RandomWheelEvent {
    data object Spin : RandomWheelEvent
    data object SpinFinished : RandomWheelEvent
    data class SetAllowRepeat(val value: Boolean) : RandomWheelEvent
    data class SetDrawCount(val value: Int) : RandomWheelEvent
    data object OpenEditor : RandomWheelEvent
    data object CloseEditor : RandomWheelEvent
    data class SelectPreset(val id: Long) : RandomWheelEvent
    data class SetPresetName(val value: String) : RandomWheelEvent
    data object SavePreset : RandomWheelEvent
    data object AddPreset : RandomWheelEvent
    data object SaveAsPreset : RandomWheelEvent
    data class AddItem(val label: String) : RandomWheelEvent
    data class UpdateItemLabel(val id: Long, val value: String) : RandomWheelEvent
    data class UpdateItemWeight(val id: Long, val value: Int) : RandomWheelEvent
    data class StepItemWeight(val id: Long, val delta: Int) : RandomWheelEvent
    data class CycleItemColor(val id: Long) : RandomWheelEvent
    data class DeleteItem(val id: Long) : RandomWheelEvent
    data class MoveItem(val id: Long, val delta: Int) : RandomWheelEvent
    data object ResetWeights : RandomWheelEvent
    data object ShuffleOrder : RandomWheelEvent
    data class SetBatchImport(val value: String) : RandomWheelEvent
    data object ImportBatch : RandomWheelEvent
    data class ApplyTemplate(val index: Int) : RandomWheelEvent
    data object ClearHistory : RandomWheelEvent
    data object ConsumeMessage : RandomWheelEvent
}

@HiltViewModel
class RandomWheelViewModel @Inject constructor(
    private val wheelDao: WheelDao,
    private val historyDao: ToolHistoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(RandomWheelUiState())
    val uiState: StateFlow<RandomWheelUiState> = _uiState.asStateFlow()

    private var seeded = false
    private var itemsJob: Job? = null

    init {
        viewModelScope.launch {
            wheelDao.observePresets().collect { presets ->
                // 仅首次数据库为空时插入本地初始示例，名称里明确标注来源
                if (presets.isEmpty() && !seeded) {
                    seeded = true
                    seedDefaultPreset()
                }
                val before = _uiState.value.activePresetId
                val active = if (before != 0L && presets.any { it.id == before }) before
                else presets.firstOrNull()?.id ?: 0L
                val changed = active != before
                _uiState.update { cur ->
                    cur.copy(
                        presets = presets,
                        activePresetId = active,
                        presetNameDraft = if (changed)
                            presets.firstOrNull { it.id == active }?.name.orEmpty()
                        else cur.presetNameDraft
                    )
                }
                if (active != 0L && (changed || itemsJob == null)) observeItems(active)
            }
        }
        viewModelScope.launch {
            historyDao.observeByTool(TOOL_ID).collect { list ->
                _uiState.update { it.copy(history = list) }
            }
        }
    }

    fun onEvent(event: RandomWheelEvent) {
        when (event) {
            RandomWheelEvent.Spin -> spin()
            RandomWheelEvent.SpinFinished -> _uiState.update { it.copy(spinning = false) }
            is RandomWheelEvent.SetAllowRepeat -> _uiState.update { it.copy(allowRepeat = event.value) }
            is RandomWheelEvent.SetDrawCount -> _uiState.update { it.copy(drawCount = event.value.coerceIn(1, 10)) }
            RandomWheelEvent.OpenEditor -> _uiState.update { it.copy(editorOpen = true) }
            RandomWheelEvent.CloseEditor -> _uiState.update {
                it.copy(editorOpen = false, message = "编辑已生效，转盘已重新读取本地数据")
            }
            is RandomWheelEvent.SelectPreset -> selectPreset(event.id)
            is RandomWheelEvent.SetPresetName -> _uiState.update { it.copy(presetNameDraft = event.value) }
            RandomWheelEvent.SavePreset -> savePreset()
            RandomWheelEvent.AddPreset -> addPreset()
            RandomWheelEvent.SaveAsPreset -> saveAsPreset()
            is RandomWheelEvent.AddItem -> addItem(event.label)
            is RandomWheelEvent.UpdateItemLabel -> updateItem(event.id) { it.copy(label = event.value) }
            is RandomWheelEvent.UpdateItemWeight -> updateItem(event.id) {
                it.copy(weight = event.value.coerceIn(0, 99))
            }
            is RandomWheelEvent.StepItemWeight -> updateItem(event.id) {
                it.copy(weight = (it.weight + event.delta).coerceIn(0, 99))
            }
            is RandomWheelEvent.CycleItemColor -> updateItem(event.id) { item ->
                val at = WHEEL_COLOR_TOKENS.indexOf(item.colorToken)
                item.copy(colorToken = WHEEL_COLOR_TOKENS[(at + 1 + WHEEL_COLOR_TOKENS.size) % WHEEL_COLOR_TOKENS.size])
            }
            is RandomWheelEvent.DeleteItem -> deleteItem(event.id)
            is RandomWheelEvent.MoveItem -> moveItem(event.id, event.delta)
            RandomWheelEvent.ResetWeights -> resetWeights()
            RandomWheelEvent.ShuffleOrder -> shuffleOrder()
            is RandomWheelEvent.SetBatchImport -> _uiState.update { it.copy(batchImportText = event.value) }
            RandomWheelEvent.ImportBatch -> importBatch()
            is RandomWheelEvent.ApplyTemplate -> applyTemplate(event.index)
            RandomWheelEvent.ClearHistory -> clearHistory()
            RandomWheelEvent.ConsumeMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    /* ---------- 本地初始数据（仅一次） ---------- */

    private suspend fun seedDefaultPreset() {
        val now = System.currentTimeMillis()
        val presetId = wheelDao.upsertPreset(
            WheelPresetEntity(name = "今天吃什么？（本地初始示例）", updatedAt = now)
        )
        val labels = listOf("火锅", "日料便当", "黄焖鸡米饭", "汉堡披萨", "轻食沙拉")
        val weights = listOf(2, 1, 2, 1, 1)
        labels.forEachIndexed { i, label ->
            wheelDao.upsertItem(
                WheelItemEntity(
                    presetId = presetId,
                    label = label,
                    weight = weights[i],
                    colorToken = WHEEL_COLOR_TOKENS[i % WHEEL_COLOR_TOKENS.size],
                    position = i
                )
            )
        }
    }

    /* ---------- 观察 ---------- */

    private fun observeItems(presetId: Long) {
        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            wheelDao.observeItems(presetId).collect { items ->
                _uiState.update { it.copy(items = items) }
            }
        }
    }

    private fun selectPreset(id: Long) {
        _uiState.update { s ->
            s.copy(
                activePresetId = id,
                presetNameDraft = s.presets.firstOrNull { it.id == id }?.name ?: s.presetNameDraft
            )
        }
        observeItems(id)
    }

    /* ---------- 抽取 ---------- */

    private fun spin() {
        val s = _uiState.value
        if (s.items.isEmpty()) {
            _uiState.update { it.copy(message = "请先在「编辑选项」中添加至少一个候选项") }
            return
        }
        val picks = RandomWheelEngine.draw(s.items, s.allowRepeat, s.drawCount, s.recentLabels)
        if (picks.isEmpty()) {
            _uiState.update { it.copy(message = "没有可抽取的候选项：请检查权重，或关闭「去重抽取」") }
            return
        }
        _uiState.update {
            it.copy(
                spinToken = it.spinToken + 1,
                winningIndex = picks.last().index,
                results = picks,
                recentLabels = (it.recentLabels + picks.map { p -> p.label }).takeLast(3),
                spinning = true,
                message = null
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            picks.forEach { p ->
                historyDao.insert(
                    ToolHistoryEntity(
                        toolId = TOOL_ID,
                        summary = "${p.label} · 权重 ${p.weight} · 概率 " +
                            String.format(Locale.getDefault(), "%.1f%%", p.probability * 100.0),
                        createdAt = p.timestampMillis
                    )
                )
            }
        }
    }

    /* ---------- 预设 / 选项的 Room 持久化 ---------- */

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }

    private fun updateItem(id: Long, transform: (WheelItemEntity) -> WheelItemEntity) {
        // 编辑已有项必须带上原 id，Room 的 REPLACE 才会命中同一行
        val item = _uiState.value.items.firstOrNull { it.id == id } ?: return
        io { wheelDao.upsertItem(transform(item)) }
    }

    private fun addItem(label: String) {
        val text = label.trim()
        val presetId = _uiState.value.activePresetId
        if (text.isEmpty() || presetId == 0L) return
        io {
            val next = _uiState.value.items.size
            wheelDao.upsertItem(
                WheelItemEntity(
                    presetId = presetId,
                    label = text,
                    weight = 1,
                    colorToken = WHEEL_COLOR_TOKENS[next % WHEEL_COLOR_TOKENS.size],
                    position = next
                )
            )
        }
    }

    private fun deleteItem(id: Long) = io { wheelDao.deleteItem(id) }

    private fun moveItem(id: Long, delta: Int) {
        val list = _uiState.value.items
        val from = list.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to < 0 || to >= list.size) return
        val reordered = list.toMutableList()
        val moved = reordered.removeAt(from)
        reordered.add(to, moved)
        io { reordered.forEachIndexed { i, item -> wheelDao.upsertItem(item.copy(position = i)) } }
    }

    private fun resetWeights() = io {
        _uiState.value.items.forEach { wheelDao.upsertItem(it.copy(weight = 1)) }
    }

    private fun shuffleOrder() = io {
        _uiState.value.items.shuffled().forEachIndexed { i, item ->
            wheelDao.upsertItem(item.copy(position = i))
        }
    }

    private fun savePreset() {
        val name = _uiState.value.presetNameDraft.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(message = "请填写轮盘名称") }
            return
        }
        val id = _uiState.value.activePresetId
        io {
            wheelDao.upsertPreset(
                WheelPresetEntity(id = id, name = name, updatedAt = System.currentTimeMillis())
            )
        }
        _uiState.update { it.copy(message = "「$name」已保存到本地") }
    }

    private fun addPreset() {
        io {
            val id = wheelDao.upsertPreset(
                WheelPresetEntity(name = "新轮盘", updatedAt = System.currentTimeMillis())
            )
            selectPreset(id)
        }
    }

    /** 设计稿「另存为方案」：复制当前全部选项到一个新预设。 */
    private fun saveAsPreset() {
        val s = _uiState.value
        if (s.activePresetId == 0L) return
        io {
            val id = wheelDao.upsertPreset(
                WheelPresetEntity(
                    name = "「${s.presetNameDraft.ifBlank { "轮盘" }}」副本",
                    updatedAt = System.currentTimeMillis()
                )
            )
            s.items.forEachIndexed { i, item ->
                wheelDao.upsertItem(item.copy(id = 0L, presetId = id, position = i))
            }
            selectPreset(id)
        }
        _uiState.update { it.copy(message = "已另存为新的本地方案") }
    }

    private fun parseImportLine(line: String): Pair<String, Int> {
        val comma = line.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }
        if (comma.size >= 2) return comma[0] to (comma[1].toIntOrNull()?.coerceIn(1, 99) ?: 1)
        val tokens = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val last = tokens.lastOrNull()?.toIntOrNull()
        return if (last != null && tokens.size >= 2) {
            tokens.dropLast(1).joinToString(" ") to last.coerceIn(1, 99)
        } else line to 1
    }

    private fun importBatch() {
        val lines = _uiState.value.batchImportText
            .split('\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            _uiState.update { it.copy(message = "请先粘贴候选文本（每行一个，可写「标签,权重」）") }
            return
        }
        val presetId = _uiState.value.activePresetId
        if (presetId == 0L) return
        val base = _uiState.value.items.size
        val parsed = lines.mapIndexed { i, line ->
            val (label, weight) = parseImportLine(line)
            Triple(label, weight, base + i)
        }
        io {
            parsed.forEach { (label, weight, pos) ->
                wheelDao.upsertItem(
                    WheelItemEntity(
                        presetId = presetId,
                        label = label,
                        weight = weight,
                        colorToken = WHEEL_COLOR_TOKENS[pos % WHEEL_COLOR_TOKENS.size],
                        position = pos
                    )
                )
            }
        }
        _uiState.update { it.copy(batchImportText = "", message = "已导入 ${parsed.size} 个候选选项") }
    }

    private fun applyTemplate(index: Int) {
        val template = WHEEL_TEMPLATES.getOrNull(index) ?: return
        val presetId = _uiState.value.activePresetId
        if (presetId == 0L) return
        val existing = _uiState.value.items
        io {
            existing.forEach { wheelDao.deleteItem(it.id) }
            template.second.forEachIndexed { i, label ->
                wheelDao.upsertItem(
                    WheelItemEntity(
                        presetId = presetId,
                        label = label,
                        weight = 1,
                        colorToken = WHEEL_COLOR_TOKENS[i % WHEEL_COLOR_TOKENS.size],
                        position = i
                    )
                )
            }
            wheelDao.upsertPreset(
                WheelPresetEntity(id = presetId, name = template.first, updatedAt = System.currentTimeMillis())
            )
        }
        _uiState.update { it.copy(presetNameDraft = template.first, message = "已套用模板「${template.first}」") }
    }

    private fun clearHistory() {
        val ids = _uiState.value.history.map { it.id }
        if (ids.isEmpty()) return
        io { historyDao.deleteByIds(ids) }
    }
}
