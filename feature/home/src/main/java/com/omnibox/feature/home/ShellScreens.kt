package com.omnibox.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnibox.core.designsystem.*
import com.omnibox.core.model.Routes
import com.omnibox.core.model.ToolCategory
import com.omnibox.core.model.ToolDefinition
import java.io.File

/* ============================ 常用（Favorites） ============================ */

@Composable
fun FavoritesRoute(
    home: HomeViewModel = hiltViewModel(),
    shell: ShellViewModel = hiltViewModel(),
    onOpenTool: (ToolDefinition) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val state by home.uiState.collectAsState()
    val history by home.recentHistory.collectAsState()
    val launches by shell.totalLaunches.collectAsState()

    Scaffold(
        bottomBar = { OmniBottomBar(2) { i -> when (i) { 0 -> onBack(); 1 -> onNavigate(Routes.EXPLORE); 3 -> onNavigate(Routes.SETTINGS); else -> Unit } } }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(bottom = OmniDimens.Grid3)
        ) {
            item {
                OmniTopBar(title = "常用", subtitle = "收藏的工具与最近使用") {
                    IconButton(onClick = { onNavigate(Routes.SEARCH_RESULT) }) {
                        Icon(OmniIcon.of("search"), contentDescription = "搜索")
                    }
                }
            }
            item {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = OmniShape.CardSmall
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("高频常驻工作台", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("常用置顶与快捷入口", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(6.dp))
                        Text("支持 Android 12+ Glance 桌面小组件实时联动，免启动极速直达。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            item { Spacer(Modifier.height(OmniDimens.Grid2)) }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding)) {
                    OmniMetricCard("已固定常驻", "${state.favoriteTools.size} 款核心工具", Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    OmniMetricCard("本周启动频次", "$launches 次极速唤醒", Modifier.weight(1f))
                }
            }
            item {
                OmniSectionHeader(title = "置顶核心工具集", count = state.favoriteTools.size,
                    icon = "push_pin", actionLabel = "管理",
                    onAction = { onNavigate(Routes.TOOL_SETTINGS) })
            }
            if (state.favoriteTools.isEmpty()) {
                item {
                    Text("尚未固定工具：在「工具」页的工具卡上点星标即可固定到这里。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = OmniDimens.ScreenPadding, vertical = 12.dp))
                }
            } else {
                items(state.favoriteTools, key = { it.id }) { tool ->
                    OmniToolCard(
                        tool = tool,
                        onClick = { onOpenTool(tool) },
                        modifier = Modifier.padding(horizontal = OmniDimens.ScreenPadding, vertical = 4.dp),
                        favorite = true,
                        onToggleFavorite = { home.onEvent(HomeEvent.ToggleFavorite(tool.id)) }
                    )
                }
            }
            item {
                OmniSectionHeader(title = "最近使用历史", count = history.size,
                    icon = "history", actionLabel = "清空记录",
                    onAction = { home.onEvent(HomeEvent.ClearHistory) })
            }
            items(history.take(8), key = { it.id }) { entry ->
                val tool = state.tools.find { it.id == entry.toolId }
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = OmniShape.CardSmall
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(OmniIcon.of(tool?.icon ?: "history"), contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(entry.summary.ifBlank { tool?.title ?: entry.toolId },
                                style = MaterialTheme.typography.titleSmall)
                            Text(tool?.subtitle.orEmpty(), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Text("纯本地 Room 数据库持久化 · 历史记录与收藏不出设备 · 无云端同步",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(OmniDimens.ScreenPadding))
            }
        }
    }
}

/* ============================ 设置（Settings） ============================ */

@Composable
fun SettingsRoute(
    shell: ShellViewModel = hiltViewModel(),
    versionName: String,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val state by shell.uiState.collectAsState()
    var message by remember { mutableStateOf("") }
    val storage = remember(message) { shell.storageInfo() }
    val permissions = remember(message) { shell.permissionStatuses() }

    Scaffold(
        bottomBar = { OmniBottomBar(3) { i -> when (i) { 0 -> onBack(); 1 -> onNavigate(Routes.EXPLORE); 2 -> onNavigate(Routes.FAVORITES); else -> Unit } } }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(bottom = OmniDimens.Grid3)
        ) {
            item { OmniTopBar(title = "设置", subtitle = "偏好、权限与关于") }
            item {
                OmniInfoCard(
                    icon = "terminal",
                    title = "OmniBox",
                    value = versionName,
                    lines = listOf(
                        "原生极速工具箱 · 纯本地沙盒架构",
                        "核心工具纯本地运算 · 探索页外链交给系统浏览器",
                        "Material You 动态色彩 · Android 14 就绪",
                        "基于 Jetpack Compose + Hilt + Room 的纯原生架构工具箱"
                    )
                )
            }
            item {
                OmniSectionHeader(title = "模块启停与自治配置",
                    count = ToolCategory.entries.size - 1, icon = "toggle_on")
                Text("按需开关功能模块，隐藏不常用工具，释放轻量运存",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = OmniDimens.ScreenPadding))
            }
            item {
                OmniSwitchRow("Material You 动态色彩", "跟随系统取色（Android 12+）",
                    state.dynamicColor) { shell.setDynamic(it) }
                OmniSwitchRow("全类别强制覆盖", "生成密码时每类字符至少包含 1 个",
                    state.forceAllCategories) { shell.setForceAllCategories(it) }
                OmniSwitchRow("操作成功触觉振动", "默认 (Normal)", state.hapticLevel != "off") {
                    shell.setHapticLevel(if (it) "normal" else "off")
                }
                OmniSegmentRow("主题模式", listOf("跟随系统" to "system", "浅色" to "light", "深色" to "dark"),
                    state.themeMode) { shell.setTheme(it) }
                OmniSegmentRow("触觉反馈强度",
                    listOf("微弱" to "subtle", "默认" to "normal", "强劲" to "firm"),
                    state.hapticLevel) { shell.setHapticLevel(it) }
            }
            item {
                OmniSectionHeader(title = "存储与系统沙盒管理", count = 0, icon = "inventory_2")
                OmniInfoRow("应用隔离沙盒", "本地占有空间总计", shell.storageLabel(storage.totalBytes))
                OmniInfoRow("临时缓存 (位图/条码)", "", shell.storageLabel(storage.cacheBytes))
                OmniInfoRow("Room 数据库", "", shell.storageLabel(storage.databaseBytes))
                Row(Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding)) {
                    Button(onClick = {
                        val freed = shell.clearCache()
                        message = "已清理缓存 ${shell.storageLabel(freed)}"
                    }) { Text("清空沙盒缓存") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onNavigate(Routes.TOOL_HISTORY) }) { Text("导出配置备份") }
                }
            }
            item {
                OmniSectionHeader(title = "权限审计与系统集成", count = permissions.size, icon = "security_update_good")
                permissions.forEach { (label, granted) ->
                    OmniInfoRow(label, "透明审计系统权限，无越权调用与静默常驻",
                        if (granted) "已按需授权" else "未授权")
                }
                OmniInfoRow("网络用途", "仅探索页外链跳转，不上传任何数据", "浏览器代收")
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding)) {
                    Button(onClick = { shell.resetAll(); message = "已恢复全部模块默认设置" }) {
                        Text("恢复全部模块默认开启")
                    }
                }
            }
            item {
                Text("隐私承诺：所有偏好与收藏仅持久化到本机 DataStore，"
                    + "无账号体系、无远程同步、无埋点上传。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(OmniDimens.ScreenPadding))
            }
            if (message.isNotBlank()) {
                item {
                    Text(message, style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = OmniDimens.ScreenPadding))
                }
            }
        }
    }
}

/* ============================ 工具设置（Tool Settings） ============================ */

@Composable
fun ToolSettingsRoute(
    shell: ShellViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by shell.uiState.collectAsState()
    var message by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        item {
            OmniTopBar(title = "工具设置", subtitle = "OmniEngine · 原生协程异步驱动 · 零云端通信",
                navigationIcon = OmniIcon.of("arrow_back"), onNavigationClick = onBack)
        }
        item {
            OmniSectionHeader(title = "图形与流媒体偏好", count = 3, icon = "photo_size_select_large")
            OmniSegmentRow("默认输出格式",
                listOf("WebP 82%" to "WEBP", "JPEG 90%" to "JPEG", "PNG 无损" to "PNG"),
                state.outputFormat) { shell.setOutputFormat(it) }
            OmniSwitchRow("大图流式切片解码", "超 2400 万像素开启 Chunked 采样防崩溃",
                state.streamDecode) { shell.setStreamDecode(it) }
            OmniSwitchRow("脱敏处理：剔除 EXIF 隐私", "抹除拍摄定位坐标与相机序列号",
                state.stripExif) { shell.setStripExif(it) }
            OmniSwitchRow("原图保留与文件覆写保护", "生成新文件存储至 Download/OmniTools 目录",
                state.keepOriginal) { shell.setKeepOriginal(it) }
        }
        item {
            OmniSectionHeader(title = "算法精度与随机源", count = 2, icon = "calculate")
            OmniInfoRow("随机数熵池发生源", "密码学硬件真随机", "SecureRandom")
            OmniInfoRow("浮点运算舍入法", "BigDecimal 四舍六入五成双", "HALF_EVEN · 8 位")
            OmniSegmentRow("历史记录存留期",
                listOf("7 天" to 7, "30 天" to 30, "90 天" to 90),
                state.retentionDays) { if (it is Int) shell.setRetention(it) }
        }
        item {
            OmniSectionHeader(title = "缓存与数据生命周期", count = 2, icon = "cached")
            val storage = shell.storageInfo()
            OmniInfoRow("历史记录滚动上限", "满后先进先出", "${state.historyLimit} 条")
            OmniInfoRow("流式运算临时交换区", "包含切片缓存与渲染临时贴图",
                shell.storageLabel(storage.cacheBytes))
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding)) {
                OmniPrimaryButton("保存并全局生效", onClick = { message = "设置已生效（DataStore 实时持久化）" },
                    modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button(onClick = { shell.resetAll(); message = "已恢复所有工具默认设置" }) {
                    Text("恢复默认")
                }
            }
        }
        if (message.isNotBlank()) {
            item {
                Text(message, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(OmniDimens.ScreenPadding))
            }
        }
        item {
            Text("上述所有偏好参数均直接持久化至 Android Jetpack DataStore 本地存储，"
                + "无任何远程同步与打点上传。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(OmniDimens.ScreenPadding))
        }
    }
}

/* ============================ 工具历史（Tool History） ============================ */

@Composable
fun ToolHistoryRoute(
    home: HomeViewModel = hiltViewModel(),
    shell: ShellViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val history by home.recentHistory.collectAsState()
    val state by home.uiState.collectAsState()
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<String?>(null) }
    val shown = history.filter { filter == null || it.toolId == filter }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        item {
            OmniTopBar(title = "使用历史", subtitle = "本地沙盒持久化 (Room DB) · 零数据上传 · 强离线",
                navigationIcon = OmniIcon.of("arrow_back"), onNavigationClick = onBack)
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding)) {
                OmniMetricCard("日志总数", "${history.size} 条", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OmniMetricCard("沙盒占用", shell.storageLabel(shell.storageInfo().totalBytes), Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OmniMetricCard("网络回传", "0 B/s", Modifier.weight(1f))
            }
        }
        item {
            val ids = history.map { it.toolId }.distinct()
            LazyRow(contentPadding = PaddingValues(horizontal = OmniDimens.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OmniCategoryChip("全部", history.size, filter == null, onClick = { filter = null })
                }
                items(ids) { id ->
                    OmniCategoryChip(state.tools.find { it.id == id }?.title ?: id,
                        history.count { it.toolId == id }, filter == id, onClick = { filter = id })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding)) {
                Button(onClick = { home.onEvent(HomeEvent.ClearHistory); message = "已清空全部历史" }) {
                    Text("一键粉碎")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val dir = File(context.filesDir, "exports").apply { mkdirs() }
                    val file = File(dir, "omnibox-history.json")
                    file.writeText(shell.exportHistoryJson(history))
                    message = "已导出 JSON 结构日志：${file.absolutePath}"
                }) { Text("导出 JSON") }
            }
        }
        items(shown, key = { it.id }) { entry ->
            val tool = state.tools.find { it.id == entry.toolId }
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding, vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = OmniShape.CardSmall
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(OmniIcon.of(tool?.icon ?: "history"), contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(tool?.title ?: entry.toolId, style = MaterialTheme.typography.titleSmall)
                        Text(entry.summary, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Text("数据安全承诺：历史记录严格隔离在本机 Room 数据库，密码本体不落盘。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(OmniDimens.ScreenPadding))
        }
        if (message.isNotBlank()) {
            item {
                Text(message, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = OmniDimens.ScreenPadding))
            }
        }
    }
}

/* ============================ 搜索结果（Search Result） ============================ */

@Composable
fun SearchResultRoute(
    query: String,
    home: HomeViewModel = hiltViewModel(),
    onOpenTool: (ToolDefinition) -> Unit,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit
) {
    var text by remember(query) { mutableStateOf(query) }
    val state by home.uiState.collectAsState()
    val results = remember(text) { home.search(text) }
    var category by remember { mutableStateOf(ToolCategory.ALL) }
    val shown = results.filter { category == ToolCategory.ALL || it.category == category }
    val hotWords = listOf("二维码生成", "单位换算", "Unix 时间戳", "环境分贝计", "考研倒计时", "Base64 编解码")

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        item {
            OmniTopBar(title = "工具检索", subtitle = "本地关键词索引 · 无需联网", offlineBadge = false,
                navigationIcon = OmniIcon.of("arrow_back"), onNavigationClick = onBack)
        }
        item {
            OmniSearchBar(
                query = text,
                onQueryChange = { text = it; onQueryChange(it) },
                onSearch = { onQueryChange(text) },
                modifier = Modifier.padding(horizontal = OmniDimens.ScreenPadding)
            )
        }
        item {
            Text("找到 ${shown.size} 个相关工具 · 纯端侧本地关键词索引",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(OmniDimens.ScreenPadding))
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = OmniDimens.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ToolCategory.entries.toList()) { cat ->
                    OmniCategoryChip(cat.label, results.count { cat == ToolCategory.ALL || it.category == cat },
                        category == cat) { category = cat }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text("尚未输入或粘贴待处理文本，或没有匹配的工具。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(OmniDimens.ScreenPadding))
            }
        } else {
            items(shown, key = { it.id }) { tool ->
                Surface(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp)
                        .clip(OmniShape.CardSmall)
                        .clickable { onOpenTool(tool) },
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = OmniShape.CardSmall
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(OmniIcon.of(tool.icon), contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(tool.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.weight(1f))
                            Text(tool.category.label, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(tool.subtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(OmniIcon.of("verified_user"), contentDescription = null,
                                modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text("纯离线 · 零网络调用", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.weight(1f))
                            Text("立即使用", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item {
            OmniSectionHeader(title = "热门离线搜索", count = hotWords.size, icon = "local_fire_department")
            LazyRow(contentPadding = PaddingValues(horizontal = OmniDimens.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(hotWords) { word ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = OmniShape.Pill,
                        modifier = Modifier.clip(OmniShape.Pill).clickable { text = word; onQueryChange(word) }
                    ) {
                        Text(word, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item {
            Text("搜索过程不依赖网络 · 响应速度 < 5ms · 隐私不出设备",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(OmniDimens.ScreenPadding))
        }
    }
}

/* ============================ 通用小组件 ============================ */

@Composable
private fun OmniInfoCard(icon: String, title: String, value: String, lines: List<String>) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = OmniShape.CardSmall
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(OmniIcon.of(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            lines.forEach {
                Row(Modifier.padding(vertical = 2.dp)) {
                    Icon(OmniIcon.of("check"), contentDescription = null,
                        modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun OmniInfoRow(label: String, hint: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun OmniSwitchRow(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> OmniSegmentRow(
    label: String,
    options: List<Pair<String, T>>,
    current: T,
    onSelect: (T) -> Unit
) {
    Column(Modifier.padding(horizontal = OmniDimens.ScreenPadding, vertical = 8.dp)) {
        OmniLabel(label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (text, value) ->
                val active = current == value
                Surface(
                    color = if (active) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = OmniShape.Pill,
                    modifier = Modifier.clip(OmniShape.Pill).clickable { onSelect(value) }
                ) {
                    Text(text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
