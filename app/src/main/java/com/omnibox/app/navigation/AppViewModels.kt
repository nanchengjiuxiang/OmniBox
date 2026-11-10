package com.omnibox.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibox.core.data.ToolCatalog
import com.omnibox.core.data.preferences.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 主题开关在设置页改动后即时生效，不需要重启 Activity。 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    preferences: PreferencesRepository
) : ViewModel() {
    val themeMode: StateFlow<String> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")
    val dynamicColor: StateFlow<Boolean> = preferences.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}

/** 供占位页读取注册表信息，避免出现与首页不一致的工具名称/计数。 */
@HiltViewModel
class AppViewModel @Inject constructor(
    val catalog: ToolCatalog
) : ViewModel()
