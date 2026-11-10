package com.omnibox.feature.explore

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.omnibox.core.designsystem.*
import com.omnibox.core.model.Routes
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

/** 组合式函数无法直接注入，用 EntryPoint 取单例的图标缓存。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ExploreEntryPoint {
    fun faviconStore(): FaviconStore
}

/**
 * 探索 · 精选导航。
 *
 * 与设计稿一致的信息层级：顶部栏 → 搜索 → 分类 Chips → 精选条目 → 外链沙盒设置 → 提交入口 → 底部四 Tab。
 * 差异只在「数据来源」：条目来自真实注册表，收藏与自建站点落本机 DataStore，所有开关都有实际行为。
 */
@Composable
fun ExploreRoute(
    viewModel: ExploreViewModel = hiltViewModel(),
    onNavigate: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val favicons = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ExploreEntryPoint::class.java
        ).faviconStore()
    }
    var showSubmit by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshNetwork()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeNotice()
        }
    }

    fun open(entry: ExploreEntry) {
        viewModel.recordOpen(entry)
        val opened = ExploreLinks.open(context, entry.url, state.useCustomTabs, state.incognito)
        if (!opened) {
            scope.launch { snackbar.showSnackbar("没有可打开网页的应用，请先安装浏览器") }
        }
    }

    Scaffold(
        // 上下留白交给 OmniTopBar / OmniBottomBar 自己避让，这里只保留左右安全区
        contentWindowInsets = OmniScaffoldInsets,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            OmniBottomBar(1) { index ->
                when (index) {
                    0 -> onNavigate(Routes.HOME)
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
                .background(MaterialTheme.colorScheme.surface)
                .omniStatusBarInset(),
            contentPadding = PaddingValues(bottom = OmniDimens.Grid3)
        ) {
            item {
                OmniTopBar(
                    title = "探索 · 精选导航",
                    subtitle = "${state.totalCount} 个站点 · 收藏 ${state.favorites.size}",
                    offlineBadge = false
                ) {
                    IconButton(onClick = { onNavigate(Routes.SETTINGS) }) {
                        Icon(OmniIcon.of("tune"), contentDescription = "更多设置")
                    }
                }
            }

            item {
                ExploreSearchField(
                    query = state.query,
                    onQueryChange = { viewModel.onEvent(ExploreEvent.QueryChanged(it)) },
                    modifier = Modifier.padding(horizontal = OmniDimens.ScreenPadding)
                )
            }

            if (!state.online) {
                item { OfflineBanner() }
            }

            item { Spacer(Modifier.height(OmniDimens.Grid1)) }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = OmniDimens.ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ExploreCatalog.chipOrder) { category ->
                        OmniCategoryChip(
                            label = category.label,
                            count = state.countOf(category),
                            selected = state.category == category,
                            icon = category.icon,
                            onClick = { viewModel.onEvent(ExploreEvent.CategorySelected(category)) }
                        )
                    }
                }
            }

            // 星标此前只能「收藏」不能「查看」：这里把收藏落成一条常驻快捷栏，
            // 保证卡片上的星标有去有回，不用在 30 张卡里找自己点过哪几个。
            if (state.query.isBlank() && state.category == ExploreCategory.ALL && state.favoriteEntries.isNotEmpty()) {
                item {
                    OmniSectionHeader(
                        title = "我的收藏",
                        count = state.favoriteEntries.size,
                        icon = "star"
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = OmniDimens.ScreenPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.favoriteEntries, key = { "fav-${it.id}" }) { entry ->
                            AssistChip(
                                onClick = { open(entry) },
                                label = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = {
                                    Icon(
                                        OmniIcon.of("star"),
                                        contentDescription = "已收藏",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            if (state.query.isBlank() && state.category == ExploreCategory.ALL && state.recentEntries.isNotEmpty()) {
                item {
                    OmniSectionHeader(
                        title = "最近打开",
                        count = state.recentEntries.size,
                        icon = "history",
                        actionLabel = "清空",
                        onAction = { viewModel.onEvent(ExploreEvent.ClearRecent) }
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = OmniDimens.ScreenPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.recentEntries, key = { "recent-${it.id}" }) { entry ->
                            AssistChip(
                                onClick = { open(entry) },
                                label = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = {
                                    Icon(
                                        OmniIcon.of(entry.icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(OmniDimens.Grid1)) }

            item {
                OmniSectionHeader(
                    title = when (state.category) {
                        ExploreCategory.ALL -> "精选导航"
                        ExploreCategory.CUSTOM -> "我添加的站点"
                        else -> state.category.label
                    },
                    count = state.visibleEntries.size,
                    icon = "auto_awesome"
                )
            }

            if (state.visibleEntries.isEmpty()) {
                item { EmptyResult(onReset = {
                    viewModel.onEvent(ExploreEvent.QueryChanged(""))
                    viewModel.onEvent(ExploreEvent.CategorySelected(ExploreCategory.ALL))
                }) }
            } else {
                items(state.visibleEntries, key = { it.id }) { entry ->
                    ExploreCard(
                        entry = entry,
                        favorite = state.isFavorite(entry.id),
                        favicons = favicons,
                        onOpen = { open(entry) },
                        onToggleFavorite = { viewModel.onEvent(ExploreEvent.ToggleFavorite(entry.id)) },
                        onRemove = { viewModel.onEvent(ExploreEvent.RemoveSite(entry.id)) },
                        modifier = Modifier.padding(horizontal = OmniDimens.ScreenPadding, vertical = 4.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(OmniDimens.Grid2)) }

            item {
                OmniSectionHeader(title = "外链沙盒设置", count = 2, icon = "security")
            }
            item {
                SandboxCard(
                    state = state,
                    onCustomTabsChange = { viewModel.onEvent(ExploreEvent.ToggleCustomTabs(it)) },
                    onIncognitoChange = { viewModel.onEvent(ExploreEvent.ToggleIncognito(it)) },
                    onSubmitClick = { showSubmit = true }
                )
            }

            item {
                Text(
                    "条目仅做跳转：不代理、不复制、不缓存页面内容；收藏与自建站点保存在本机 DataStore，"
                        + "卸载即清除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = OmniDimens.ScreenPadding,
                        end = OmniDimens.ScreenPadding,
                        top = OmniDimens.Grid2
                    )
                )
            }
        }
    }

    if (showSubmit) {
        SubmitSiteDialog(
            onDismiss = { showSubmit = false },
            onSubmit = { name, url, category ->
                viewModel.onEvent(ExploreEvent.Submit(name, url, category))
                showSubmit = false
            }
        )
    }
}

/* ============================ 搜索框 ============================ */

@Composable
private fun ExploreSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("搜索 AI 工具、网站或关键词", style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(OmniIcon.of("search"), contentDescription = "搜索") },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(OmniIcon.of("close"), contentDescription = "清空搜索")
                }
            }
        },
        singleLine = true,
        shape = OmniShape.Pill,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        )
    )
}

/* ============================ 离线提示 ============================ */

@Composable
private fun OfflineBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OmniDimens.ScreenPadding, vertical = OmniDimens.Grid1),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = OmniShape.CardSmall
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(OmniIcon.of("warning"), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "当前无网络：导航仍可浏览，点击外链会等到网络恢复后由浏览器加载。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/* ============================ 站点卡片 ============================ */

@Composable
private fun ExploreCard(
    entry: ExploreEntry,
    favorite: Boolean,
    favicons: FaviconStore,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(OmniShape.CardSmall)
            .clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = OmniShape.CardSmall
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EntryAvatar(entry, favicons)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (entry.tag.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = OmniShape.Pill) {
                            Text(
                                entry.tag,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    entry.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    entry.host,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(40.dp)) {
                Icon(
                    OmniIcon.of(if (favorite) "star" else "star_border"),
                    contentDescription = if (favorite) "取消收藏" else "收藏",
                    tint = if (favorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (entry.custom) {
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                    Icon(
                        OmniIcon.of("delete"),
                        contentDescription = "移除该自定义站点",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Icon(
                    OmniIcon.of("open_in_new"),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EntryAvatar(entry: ExploreEntry, favicons: FaviconStore) {
    val bitmap by produceState<Bitmap?>(initialValue = null, entry.host) {
        value = favicons.load(entry.host)
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(9.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                entry.initial,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/* ============================ 空结果 ============================ */

@Composable
private fun EmptyResult(onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OmniDimens.ScreenPadding, vertical = OmniDimens.Grid3),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(OmniIcon.of("search"), contentDescription = null,
            modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text("没有匹配的站点", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "换个关键词，或把当前分类切回「全部」。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onReset, shape = OmniShape.Pill) { Text("清除筛选") }
    }
}

/* ============================ 沙盒设置卡 ============================ */

@Composable
private fun SandboxCard(
    state: ExploreUiState,
    onCustomTabsChange: (Boolean) -> Unit,
    onIncognitoChange: (Boolean) -> Unit,
    onSubmitClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OmniDimens.ScreenPadding),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = OmniShape.CardSmall
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("精选", "${ExploreCatalog.entries.size}")
                Metric("收藏", "${state.favorites.size}")
                Metric("自建", "${state.customSites.size}")
                Metric("无痕", if (state.incognito) "开" else "关")
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow(
                title = "内置 Chrome Custom Tabs",
                hint = if (state.useCustomTabs) {
                    "以浏览器浮层打开，保留地址栏、证书与安全标识"
                } else {
                    "改用系统默认浏览器整页打开"
                },
                checked = state.useCustomTabs,
                onChange = onCustomTabsChange
            )
            SwitchRow(
                title = "无痕沙盒模式",
                hint = "不在本机记录访问足迹；Cookie 与历史仍由浏览器自行管理",
                checked = state.incognito,
                onChange = onIncognitoChange
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSubmitClick,
                modifier = Modifier.fillMaxWidth(),
                shape = OmniShape.Pill
            ) {
                Icon(OmniIcon.of("add"), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("提交我的常用网站 / AI 工具")
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/* ============================ 提交站点弹窗 ============================ */

@Composable
private fun SubmitSiteDialog(
    onDismiss: () -> Unit,
    onSubmit: (name: String, url: String, category: ExploreCategory) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(ExploreCategory.AI_ASSISTANT) }
    val normalized = remember(url) { ExploreCatalog.normalizeUrl(url) }
    val urlError = url.isNotBlank() && normalized == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加站点") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("网址") },
                    placeholder = { Text("example.com 或 https://example.com") },
                    singleLine = true,
                    isError = urlError,
                    supportingText = {
                        Text(
                            when {
                                urlError -> "只接受 http/https 且需要带域名"
                                normalized != null -> "将打开：$normalized"
                                else -> "可省略 https://，保存时会自动补全"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（可留空）") },
                    placeholder = {
                        Text(ExploreCatalog.suggestName(url).ifBlank { "自动取域名" })
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("分类", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ExploreCatalog.chipOrder.filter { it != ExploreCategory.ALL }) { item ->
                        val active = item == category
                        Surface(
                            color = if (active) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = OmniShape.Pill,
                            modifier = Modifier
                                .clip(OmniShape.Pill)
                                .clickable { category = item }
                        ) {
                            Text(
                                item.label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = normalized != null,
                onClick = { onSubmit(name, url, category) }
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
