package com.omnibox.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibox.core.data.ToolCatalog
import com.omnibox.core.data.local.FavoriteToolEntity
import com.omnibox.core.data.local.ToolHistoryDao
import com.omnibox.core.data.local.ToolHistoryEntity
import com.omnibox.core.data.local.ToolUsageDao
import com.omnibox.core.data.local.ToolUsageStatEntity
import com.omnibox.core.data.local.FavoriteToolDao
import com.omnibox.core.data.preferences.PreferencesRepository
import com.omnibox.core.model.ToolCategory
import com.omnibox.core.model.ToolDefinition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val tools: List<ToolDefinition> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val query: String = "",
    val selectedCategory: ToolCategory = ToolCategory.ALL,
    val currentTab: Int = 0,
    val themeMode: String = "system",
    val dynamicColor: Boolean = true
) {
    /** 分组与分类计数均由注册表实时计算，不写死设计稿里的演示数字。 */
    val totalCount: Int get() = tools.size
    fun countOf(category: ToolCategory): Int = when (category) {
        ToolCategory.ALL -> tools.size
        else -> tools.count { it.category == category }
    }
    val visibleTools: List<ToolDefinition>
        get() = tools.filter {
            (selectedCategory == ToolCategory.ALL || it.category == selectedCategory) &&
                (query.isBlank() || it.title.contains(query, ignoreCase = true) ||
                    it.subtitle.contains(query, ignoreCase = true))
        }
    val favoriteTools: List<ToolDefinition>
        get() = tools.filter { it.id in favorites }
}

sealed interface HomeEvent {
    data class QueryChanged(val value: String) : HomeEvent
    data class CategorySelected(val category: ToolCategory) : HomeEvent
    data class TabSelected(val index: Int) : HomeEvent
    data class ToggleFavorite(val toolId: String) : HomeEvent
    data class ThemeChanged(val mode: String) : HomeEvent
    data class DynamicColorChanged(val enabled: Boolean) : HomeEvent
    data class HapticChanged(val enabled: Boolean) : HomeEvent
    data object ClearHistory : HomeEvent
    data class ToolOpened(val toolId: String) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalog: ToolCatalog,
    private val favoriteDao: FavoriteToolDao,
    private val historyDao: ToolHistoryDao,
    private val usageDao: ToolUsageDao,
    private val preferences: PreferencesRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _category = MutableStateFlow(ToolCategory.ALL)
    private val _tab = MutableStateFlow(0)

    val recentHistory: StateFlow<List<ToolHistoryEntity>> = historyDao.observeRecent(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val haptic: StateFlow<Boolean> = preferences.haptic
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val localState = combine(_query, _category, _tab) { q, c, t -> Triple(q, c, t) }

    val uiState: StateFlow<HomeUiState> = combine(
        catalog.tools,
        favoriteDao.observeAll(),
        preferences.themeMode,
        preferences.dynamicColor,
        localState
    ) { tools, favs, theme, dynamic, local ->
        HomeUiState(
            tools = tools,
            favorites = favs.map { it.toolId }.toSet(),
            query = local.first,
            selectedCategory = local.second,
            currentTab = local.third,
            themeMode = theme,
            dynamicColor = dynamic
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.QueryChanged -> _query.value = event.value
            is HomeEvent.CategorySelected -> _category.value = event.category
            is HomeEvent.TabSelected -> _tab.value = event.index
            is HomeEvent.ToggleFavorite -> viewModelScope.launch {
                val isFav = uiState.value.favorites.contains(event.toolId)
                if (isFav) favoriteDao.remove(event.toolId)
                else favoriteDao.upsert(
                    FavoriteToolEntity(
                        toolId = event.toolId,
                        pinnedAt = System.currentTimeMillis(),
                        position = uiState.value.favorites.size
                    )
                )
            }
            is HomeEvent.ThemeChanged -> viewModelScope.launch { preferences.setThemeMode(event.mode) }
            is HomeEvent.DynamicColorChanged -> viewModelScope.launch { preferences.setDynamicColor(event.enabled) }
            is HomeEvent.HapticChanged -> viewModelScope.launch { preferences.setHaptic(event.enabled) }
            HomeEvent.ClearHistory -> viewModelScope.launch { historyDao.clearAll() }
            is HomeEvent.ToolOpened -> viewModelScope.launch {
                val now = System.currentTimeMillis()
                val prev = usageDao.find(event.toolId)
                usageDao.upsert(
                    ToolUsageStatEntity(
                        toolId = event.toolId,
                        launchCount = (prev?.launchCount ?: 0) + 1,
                        lastUsedAt = now
                    )
                )
            }
        }
    }

    /** 搜索页与首页共用同一个查询状态。 */
    val query: StateFlow<String> get() = _query
    val category: StateFlow<ToolCategory> get() = _category

    fun search(query: String): List<ToolDefinition> = catalog.search(query)
    fun byId(id: String): ToolDefinition? = catalog.byId(id)
}
