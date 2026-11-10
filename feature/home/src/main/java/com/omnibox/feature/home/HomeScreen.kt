package com.omnibox.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnibox.core.designsystem.*
import com.omnibox.core.model.Routes
import com.omnibox.core.model.ToolCategory
import com.omnibox.core.model.ToolDefinition
import com.omnibox.core.model.ToolGroup

/**
 * 首页：逐项对照 home_dashboard/code.html 还原。
 * 与原型唯一的区别是：所有计数、列表、开关状态都来自 ToolCatalog / Room / DataStore，
 * 不再写死 13 或 4/2/2/4 这类演示数字（计划 §2.5）。
 */
@Composable
fun HomeRoute(
    viewModel: HomeViewModel = hiltViewModel(),
    onOpenTool: (ToolDefinition) -> Unit,
    onNavigate: (String) -> Unit,
    onSearch: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        bottomBar = {
            OmniBottomBar(currentTab = 0) { index ->
                when (index) {
                    1 -> onNavigate(Routes.EXPLORE)
                    2 -> onNavigate(Routes.FAVORITES)
                    3 -> onNavigate(Routes.SETTINGS)
                    else -> Unit
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(bottom = OmniDimens.Grid3)
        ) {
            item {
                OmniTopBar(
                    title = "万能工具箱",
                    offlineBadge = true
                ) {
                    Text("探索", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onNavigate(Routes.EXPLORE) })
                    IconButton(onClick = { onSearch(state.query) }) {
                        Icon(OmniIcon.of("search"), contentDescription = "搜索")
                    }
                    IconButton(onClick = { onNavigate(Routes.SETTINGS) }) {
                        Icon(OmniIcon.of("more_vert"), contentDescription = "更多")
                    }
                    IconButton(onClick = { onNavigate(Routes.TOOL_SETTINGS) }) {
                        Icon(OmniIcon.of("person"), contentDescription = "工具偏好")
                    }
                }
            }

            item {
                OmniSearchBar(
                    query = state.query,
                    onQueryChange = { viewModel.onEvent(HomeEvent.QueryChanged(it)) },
                    onSearch = { onSearch(state.query) },
                    onVoice = { /* P3：本地语音识别，未接入前不伪装可用 */ },
                    onFilter = { onNavigate(Routes.TOOL_SETTINGS) },
                    modifier = Modifier.padding(horizontal = OmniDimens.ScreenPadding)
                )
            }

            item { Spacer(Modifier.height(OmniDimens.Grid2)) }

            // 纯本地离线优先横幅
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OmniDimens.ScreenPadding)
                        .clip(OmniShape.CardSmall),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = OmniShape.CardSmall
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(OmniIcon.of("verified_user"), contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("核心工具全程本地 · ${state.totalCount} 款就绪",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(OmniIcon.of("check"), contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(Modifier.width(4.dp))
                                Text("无需联网即可使用", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(OmniDimens.Grid2)) }

            // 分类筛选 Chip：计数由注册表实时计算
            item {
                val chips = listOf(
                    Triple(ToolCategory.ALL, null, "check"),
                    Triple(ToolCategory.DAILY, "wb_sunny", "wb_sunny"),
                    Triple(ToolCategory.MEDIA, "photo_camera", "photo_camera"),
                    Triple(ToolCategory.SENSOR, "sensors", "sensors"),
                    Triple(ToolCategory.DEV, "terminal", "terminal")
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = OmniDimens.ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chips) { (category, _, icon) ->
                        OmniCategoryChip(
                            label = category.label,
                            count = state.countOf(category),
                            selected = state.selectedCategory == category,
                            icon = icon,
                            onClick = { viewModel.onEvent(HomeEvent.CategorySelected(category)) }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(OmniDimens.Grid2)) }

            // 常用推荐（3 张快捷卡，点击直达对应工具）
            item {
                OmniSectionHeader(
                    title = "常用推荐",
                    count = 3,
                    icon = "magic_button",
                    actionLabel = "管理",
                    onAction = { onNavigate(Routes.TOOL_SETTINGS) }
                )
                val quickIds = listOf("qrcode", "password", "image_compress")
                val quickTitles = mapOf(
                    "qrcode" to Pair("快捷扫码", "二维码与扫码 · CameraX 高速解析"),
                    "password" to Pair("离线高熵", "密码生成器 · 高强度防碰撞"),
                    "image_compress" to Pair("防 OOM", "图片压缩 · 无损局部采样")
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = OmniDimens.ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(quickIds) { id ->
                        val meta = quickTitles.getValue(id)
                        val tool = state.tools.find { it.id == id }
                        QuickCard(
                            icon = tool?.icon ?: "widgets",
                            badge = when (id) { "qrcode" -> "qr_code_scanner"; "password" -> "lock_reset"; else -> "compress" },
                            title = meta.first,
                            subtitle = meta.second,
                            onClick = { tool?.let { onOpenTool(it) } }
                        )
                    }
                }
            }

            // 三段式分组列表
            listOf(
                Triple(ToolGroup.DAILY, "widgets", "日常生活"),
                Triple(ToolGroup.MEDIA_HARDWARE, "perm_media", "媒体图像与硬件"),
                Triple(ToolGroup.DEV_TEXT, "code", "开发者与文本箱")
            ).forEach { (group, icon, label) ->
                item {
                    Spacer(Modifier.height(OmniDimens.Grid2))
                    OmniSectionHeader(
                        title = label,
                        count = state.tools.count { it.group == group },
                        icon = icon
                    )
                }
                items(
                    state.visibleTools.filter { it.group == group },
                    key = { it.id }
                ) { tool ->
                    OmniToolCard(
                        tool = tool,
                        onClick = { onOpenTool(tool) },
                        modifier = Modifier.padding(horizontal = OmniDimens.ScreenPadding, vertical = 4.dp),
                        favorite = tool.id in state.favorites,
                        onToggleFavorite = { viewModel.onEvent(HomeEvent.ToggleFavorite(tool.id)) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(OmniDimens.Grid2))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OmniDimens.ScreenPadding),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = OmniShape.CardSmall
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(OmniIcon.of("bolt"), contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("毫秒级冷启动引擎", style = MaterialTheme.typography.titleSmall)
                            Text("本地二进制加载，告别云端等待",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickCard(
    icon: String,
    badge: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(200.dp)
            .clip(OmniShape.CardSmall)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = OmniShape.CardSmall
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(OmniShape.CardSmall)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(OmniIcon.of(badge), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Icon(OmniIcon.of(icon), contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.outline)
        }
    }
}
