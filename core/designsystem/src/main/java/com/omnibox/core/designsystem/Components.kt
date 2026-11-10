package com.omnibox.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibox.core.model.ToolDefinition

/**
 * 计划 §3.4 的 7 个基础组件。
 * 刻意用稳定 API（Box/Row/Surface/OutlinedTextField）实现，
 * 避免依赖 experimental M3 组件导致还原时被迫改版式。
 */

/** 1. 顶部栏：标题 + 「离线」徽章 + 右侧操作。对应 home_dashboard 顶部区。 */
@Composable
fun OmniTopBar(
    title: String,
    subtitle: String? = null,
    offlineBadge: Boolean = true,
    navigationIcon: ImageVector? = null,
    onNavigationClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(OmniDimens.TopBarHeight)
            .padding(horizontal = OmniDimens.ScreenPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (navigationIcon != null) {
            IconButton(onClick = { onNavigationClick?.invoke() }) {
                Icon(navigationIcon, contentDescription = "返回")
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (offlineBadge) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = OmniShape.Pill) {
                Text("离线", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(8.dp))
        }
        actions()
    }
}

/** 2. 搜索栏：设计稿的 search / mic / tune 三件套。 */
@Composable
fun OmniSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索工具",
    onSearch: () -> Unit = {},
    onVoice: () -> Unit = {},
    onFilter: () -> Unit = {}
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(OmniIcon.of("search"), contentDescription = "搜索") },
        trailingIcon = {
            Row {
                IconButton(onClick = onVoice) { Icon(OmniIcon.of("mic"), contentDescription = "语音输入") }
                IconButton(onClick = onFilter) { Icon(OmniIcon.of("tune"), contentDescription = "筛选") }
            }
        },
        singleLine = true,
        shape = OmniShape.Pill,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        )
    )
}

/** 3. 分类筛选 Chip：全部 / 日常生活 / 媒体图像 / 传感器硬件 / 开发调试。 */
@Composable
fun OmniCategoryChip(
    label: String,
    count: Int,
    selected: Boolean,
    icon: String? = null,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = bg,
        shape = OmniShape.Chip,
        modifier = Modifier
            .height(40.dp)
            .clip(OmniShape.Chip)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(OmniIcon.of(icon), contentDescription = null,
                    modifier = Modifier.size(18.dp), tint = fg)
                Spacer(Modifier.width(6.dp))
            }
            Text("$label ($count)", style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}

/** 4. 工具条目卡：图标盒 + 标题 + 副标题 + 徽章 + 右箭头。 */
@Composable
fun OmniToolCard(tool: ToolDefinition, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(OmniShape.CardSmall)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = OmniShape.CardSmall
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(OmniDimens.ToolIconBox)
                    .clip(OmniShape.CardSmall)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(OmniIcon.of(tool.icon), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tool.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = OmniShape.Pill) {
                        Text(tool.badge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Text(tool.subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(OmniIcon.of("chevron_right"), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 5. 分组标题：标题 + 动态计数 + 可选右侧「管理」。 */
@Composable
fun OmniSectionHeader(
    title: String,
    count: Int,
    icon: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OmniDimens.ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(OmniIcon.of(icon), contentDescription = null,
                modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(6.dp))
        Text("($count)", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** 6. 主操作按钮：胶囊状、28dp 高度的交互锚点。 */
@Composable
fun OmniPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = OmniShape.Pill
    ) {
        if (icon != null) {
            Icon(OmniIcon.of(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 7. 底部三 Tab：发现 / 常用 / 设置。 */
@Composable
fun OmniBottomBar(currentTab: Int, onTabSelected: (Int) -> Unit) {
    val items = listOf(
        Triple("grid_view", "发现", 0),
        Triple("star", "常用", 1),
        Triple("tune", "设置", 2)
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(OmniDimens.BottomBarHeight)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (icon, label, index) ->
                val active = currentTab == index
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(OmniShape.Pill)
                        .clickable { onTabSelected(index) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        shape = OmniShape.Pill
                    ) {
                        Icon(
                            OmniIcon.of(icon), contentDescription = label,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).size(22.dp),
                            tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(label, fontSize = 12.sp,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 通用小节标题（工具详情页复用）。 */
@Composable
fun OmniLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp))
}

/** 通用数值展示卡（指标类文案一律由真实计算结果传入）。 */
@Composable
fun OmniMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = OmniShape.CardSmall
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

/** 圆形图标按钮（工具页的复制 / 刷新 / 分享）。 */
@Composable
fun OmniIconAction(icon: String, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(OmniIcon.of(icon), contentDescription = contentDescription,
            modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
