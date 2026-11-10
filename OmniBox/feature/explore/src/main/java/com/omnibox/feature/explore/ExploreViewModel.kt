package com.omnibox.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExploreUiState(
    val query: String = "",
    val category: ExploreCategory = ExploreCategory.ALL,
    val favorites: Set<String> = emptySet(),
    val customSites: List<CustomSite> = emptyList(),
    val useCustomTabs: Boolean = true,
    val incognito: Boolean = false,
    val recentIds: List<String> = emptyList(),
    val online: Boolean = true
) {
    /** 精选注册表 + 用户自建，顺序稳定：内置在前，自建按提交先后追加。 */
    val entries: List<ExploreEntry>
        get() = ExploreCatalog.entries + customSites.map { it.toEntry() }

    val visibleEntries: List<ExploreEntry>
        get() = ExploreCatalog.filter(entries, query, category)

    val favoriteEntries: List<ExploreEntry>
        get() = entries.filter { it.id in favorites }

    val recentEntries: List<ExploreEntry>
        get() = recentIds.mapNotNull { id -> entries.find { it.id == id } }

    val totalCount: Int get() = entries.size

    fun countOf(category: ExploreCategory): Int = ExploreCatalog.countOf(entries, category)

    fun isFavorite(id: String): Boolean = id in favorites
}

sealed interface ExploreEvent {
    data class QueryChanged(val value: String) : ExploreEvent
    data class CategorySelected(val value: ExploreCategory) : ExploreEvent
    data class ToggleFavorite(val id: String) : ExploreEvent
    data class ToggleCustomTabs(val enabled: Boolean) : ExploreEvent
    data class ToggleIncognito(val enabled: Boolean) : ExploreEvent
    data class Submit(val name: String, val url: String, val category: ExploreCategory) : ExploreEvent
    data class RemoveSite(val id: String) : ExploreEvent
    data object ClearRecent : ExploreEvent
    data object RefreshNetwork : ExploreEvent
}

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: ExploreRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _category = MutableStateFlow(ExploreCategory.ALL)
    private val _online = MutableStateFlow(true)
    private val _notice = MutableStateFlow<String?>(null)

    /** 一次性提示（添加成功 / 重复 / URL 非法），由 UI 消费后清空，避免旋转屏幕后重复弹出。 */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val core = combine(
        repository.prefs,
        repository.favorites,
        repository.customSites,
        _query,
        _category
    ) { prefs, favorites, customSites, query, category ->
        ExploreUiState(
            query = query,
            category = category,
            favorites = favorites,
            customSites = customSites,
            useCustomTabs = prefs.useCustomTabs,
            incognito = prefs.incognito,
            recentIds = prefs.recentIds
        )
    }

    val uiState: StateFlow<ExploreUiState> = combine(core, _online) { state, online ->
        state.copy(online = online)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExploreUiState())

    init {
        refreshNetwork()
    }

    fun onEvent(event: ExploreEvent) {
        when (event) {
            is ExploreEvent.QueryChanged -> _query.value = event.value
            is ExploreEvent.CategorySelected -> _category.value = event.value
            is ExploreEvent.ToggleFavorite -> viewModelScope.launch {
                repository.toggleFavorite(event.id)
            }
            is ExploreEvent.ToggleCustomTabs -> viewModelScope.launch {
                repository.setCustomTabs(event.enabled)
            }
            is ExploreEvent.ToggleIncognito -> viewModelScope.launch {
                repository.setIncognito(event.enabled)
                if (event.enabled) repository.clearRecent()
            }
            is ExploreEvent.Submit -> viewModelScope.launch {
                _notice.value = when (
                    val result = repository.addCustomSite(event.name, event.url, event.category)
                ) {
                    is AddSiteResult.Success -> "已添加「${result.site.name}」，可在「${result.site.category.label}」里找到"
                    AddSiteResult.InvalidUrl -> "网址不合法：只接受 http/https，且需要带域名"
                    AddSiteResult.EmptyName -> "请填写名称"
                    AddSiteResult.Duplicate -> "该站点已在导航里，无需重复添加"
                }
            }
            is ExploreEvent.RemoveSite -> viewModelScope.launch {
                repository.removeCustomSite(event.id)
                _notice.value = "已移除该自定义站点"
            }
            ExploreEvent.ClearRecent -> viewModelScope.launch { repository.clearRecent() }
            ExploreEvent.RefreshNetwork -> refreshNetwork()
        }
    }

    fun consumeNotice() {
        _notice.value = null
    }

    /** 打开前调用：无痕模式不写最近访问，保证开关语义与行为一致。 */
    fun recordOpen(entry: ExploreEntry) {
        if (uiState.value.incognito) return
        viewModelScope.launch { repository.recordOpen(entry.id) }
    }

    fun refreshNetwork() {
        _online.value = networkMonitor.isOnline()
    }
}
