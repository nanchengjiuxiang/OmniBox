package com.omnibox.feature.explore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.exploreStore: DataStore<Preferences> by preferencesDataStore(name = "omnibox_explore")

/** 外链打开方式与留痕策略，全部来自真实持久化键。 */
data class ExplorePrefs(
    val useCustomTabs: Boolean = true,
    val incognito: Boolean = false,
    val recentIds: List<String> = emptyList()
)

/** 提交站点的结果，UI 按类型给出不同提示，不用一句「添加失败」糊过去。 */
sealed interface AddSiteResult {
    data class Success(val site: CustomSite) : AddSiteResult
    data object InvalidUrl : AddSiteResult
    data object EmptyName : AddSiteResult
    data object Duplicate : AddSiteResult
}

/**
 * 探索页的本地状态仓库。
 *
 * 有意不建服务器：收藏、自定义站点、访问顺序全部只落在本机 DataStore，
 * 换机即失，符合「导航页不需要账号」的产品定位。
 */
@Singleton
class ExploreRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val FAVORITES = stringSetPreferencesKey("favorite_ids")
        val CUSTOM_SITES = stringPreferencesKey("custom_sites")
        val USE_CUSTOM_TABS = booleanPreferencesKey("use_custom_tabs")
        val INCOGNITO = booleanPreferencesKey("incognito")
        val RECENT = stringPreferencesKey("recent_ids")
    }

    private companion object {
        const val MAX_RECENT = 12
    }

    val favorites: Flow<Set<String>> = context.exploreStore.data
        .map { it[Keys.FAVORITES] ?: emptySet() }

    val customSites: Flow<List<CustomSite>> = context.exploreStore.data
        .map { CustomSiteCodec.decode(it[Keys.CUSTOM_SITES].orEmpty()) }

    val prefs: Flow<ExplorePrefs> = context.exploreStore.data.map { p ->
        ExplorePrefs(
            useCustomTabs = p[Keys.USE_CUSTOM_TABS] ?: true,
            incognito = p[Keys.INCOGNITO] ?: false,
            recentIds = p[Keys.RECENT].orEmpty().split(',').filter { it.isNotBlank() }
        )
    }

    suspend fun toggleFavorite(id: String) {
        context.exploreStore.edit { p ->
            val current = p[Keys.FAVORITES] ?: emptySet()
            p[Keys.FAVORITES] = if (id in current) current - id else current + id
        }
    }

    suspend fun setCustomTabs(enabled: Boolean) {
        context.exploreStore.edit { it[Keys.USE_CUSTOM_TABS] = enabled }
    }

    suspend fun setIncognito(enabled: Boolean) {
        context.exploreStore.edit { it[Keys.INCOGNITO] = enabled }
    }

    /**
     * 记录最近打开顺序。无痕模式下由调用方跳过，避免「开关打开但仍在留痕」。
     * 同一站点重复打开只保留最近一次。
     */
    suspend fun recordOpen(id: String) {
        context.exploreStore.edit { p ->
            val current = p[Keys.RECENT].orEmpty().split(',').filter { it.isNotBlank() }
            val next = (listOf(id) + current.filter { it != id }).take(MAX_RECENT)
            p[Keys.RECENT] = next.joinToString(",")
        }
    }

    suspend fun clearRecent() {
        context.exploreStore.edit { it[Keys.RECENT] = "" }
    }

    suspend fun addCustomSite(rawName: String, rawUrl: String, category: ExploreCategory): AddSiteResult {
        val url = ExploreCatalog.normalizeUrl(rawUrl) ?: return AddSiteResult.InvalidUrl
        val name = rawName.trim().ifEmpty { ExploreCatalog.suggestName(url) }
        if (name.isEmpty()) return AddSiteResult.EmptyName
        if (category == ExploreCategory.ALL) return AddSiteResult.InvalidUrl

        val key = CustomSiteCodec.keyOf(url)
        val duplicated = customSitesSnapshot().any { CustomSiteCodec.keyOf(it.url) == key } ||
            ExploreCatalog.entries.any { CustomSiteCodec.keyOf(it.url) == key }
        if (duplicated) return AddSiteResult.Duplicate

        val site = CustomSite(
            id = "custom-" + UUID.randomUUID().toString().take(8),
            name = name,
            url = url,
            category = category
        )
        context.exploreStore.edit { p ->
            val list = CustomSiteCodec.decode(p[Keys.CUSTOM_SITES].orEmpty()) + site
            p[Keys.CUSTOM_SITES] = CustomSiteCodec.encode(list)
        }
        return AddSiteResult.Success(site)
    }

    suspend fun removeCustomSite(id: String) {
        context.exploreStore.edit { p ->
            val list = CustomSiteCodec.decode(p[Keys.CUSTOM_SITES].orEmpty()).filterNot { it.id == id }
            p[Keys.CUSTOM_SITES] = CustomSiteCodec.encode(list)
            p[Keys.FAVORITES] = (p[Keys.FAVORITES] ?: emptySet()) - id
        }
    }

    /** 读一次快照用于查重，避免把 Flow 当成同步数据源。 */
    private suspend fun customSitesSnapshot(): List<CustomSite> =
        CustomSiteCodec.decode(context.exploreStore.data.first()[Keys.CUSTOM_SITES].orEmpty())
}
