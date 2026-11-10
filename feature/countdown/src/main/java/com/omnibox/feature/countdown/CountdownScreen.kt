package com.omnibox.feature.countdown

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnibox.core.data.local.CountdownEventEntity
import com.omnibox.core.designsystem.OmniDimens
import com.omnibox.core.designsystem.OmniIcon
import com.omnibox.core.designsystem.OmniIconAction
import com.omnibox.core.designsystem.OmniLabel
import com.omnibox.core.designsystem.OmniPrimaryButton
import com.omnibox.core.designsystem.OmniShape
import com.omnibox.core.designsystem.OmniTopBar
import com.omnibox.core.designsystem.omniNavBarInset
import com.omnibox.core.designsystem.omniStatusBarInset
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CountdownColorOptions = listOf(
    "purple" to "原生紫",
    "mint" to "薄荷青",
    "rose" to "暖胭红",
    "night" to "深邃夜空"
)

@Composable
private fun countdownColor(token: String): Color = when (token) {
    "mint" -> MaterialTheme.colorScheme.tertiary
    "rose" -> MaterialTheme.colorScheme.error
    "night" -> MaterialTheme.colorScheme.inverseSurface
    else -> MaterialTheme.colorScheme.primary
}

private fun colorTokenLabel(token: String): String =
    CountdownColorOptions.firstOrNull { it.first == token }?.second ?: token

private fun formatShort(millis: Long): String =
    SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(millis))

private data class EventView(
    val entity: CountdownEventEntity,
    val effectiveTarget: Long,
    val past: Boolean,
    val remaining: CountdownEngine.Remaining,
    val passedDays: Long,
    val progress: Int
)

private fun eventView(entity: CountdownEventEntity, now: Long): EventView {
    val target = CountdownEngine.nextOccurrence(entity.targetEpochMillis, entity.repeatYearly, now)
    return EventView(
        entity = entity,
        effectiveTarget = target,
        past = target < now,
        remaining = CountdownEngine.remaining(now, target),
        passedDays = CountdownEngine.passedDays(now, target),
        progress = CountdownEngine.progressPercent(entity.createdAt, target, now)
    )
}

/** 公开入口。 */
@Composable
fun CountdownRoute(
    viewModel: CountdownViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    // 每秒真实刷新 System.currentTimeMillis()，剩余时间全部实时计算
    LaunchedEffect(Unit) {
        while (isActive) {
            viewModel.onEvent(CountdownEvent.Tick(System.currentTimeMillis()))
            delay(1000L)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onEvent(CountdownEvent.RefreshPermission(granted)) }

    val onRequestPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onEvent(CountdownEvent.RefreshPermission(true))
        }
    }

    when (state.screen) {
        CountdownScreenState.EMPTY ->
            CountdownEmptyScreen(state, viewModel::onEvent, onBack)
        CountdownScreenState.LIST ->
            CountdownListScreen(state, viewModel::onEvent, onBack)
        CountdownScreenState.EDITOR ->
            CountdownEditorScreen(state, viewModel::onEvent, onBack, onRequestPermission)
    }
}

@Composable
private fun MessageBanner(message: String?, onConsume: () -> Unit) {
    if (message == null) return
    LaunchedEffect(message) {
        delay(2600L)
        onConsume()
    }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = OmniShape.CardSmall,
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(OmniIcon.of("info"), contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f))
            TextButton(onClick = onConsume) { Text("知道了") }
        }
    }
}

/* ============================ 空态（countdown_empty） ============================ */

@Composable
private fun CountdownEmptyScreen(
    state: CountdownUiState,
    onEvent: (CountdownEvent) -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).omniStatusBarInset().omniNavBarInset()) {
        item {
            OmniTopBar(
                title = "倒计时 / 纪念日",
                subtitle = "本地零延时引擎 · 无需连网",
                navigationIcon = OmniIcon.of("arrow_back"),
                onNavigationClick = onBack
            )
        }
        item { MessageBanner(state.message) { onEvent(CountdownEvent.ConsumeMessage) } }
        item {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(OmniIcon.of("offline_bolt"), contentDescription = null,
                    modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(14.dp))
                Text("0 天", style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("还没有添加任何目标或纪念日", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "记录生活中的重要时刻，数据仅保存在本机 Room 数据库，" +
                        "支持每年重复与基于 AlarmManager 的离线精确闹钟提醒。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(Modifier.height(20.dp))
                OmniPrimaryButton("新建第一个纪念日",
                    onClick = { onEvent(CountdownEvent.CreateNew) }, icon = "add_circle")
            }
        }
        item {
            OmniStaticCard("auto_awesome", "灵感模板推荐", "点击极速创建（真实写入 Room）") {
                CountdownTemplate.entries.forEach { template ->
                    Row(
                        Modifier.fillMaxWidth().clip(OmniShape.Input)
                            .clickable { onEvent(CountdownEvent.ApplyTemplate(template)) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(OmniIcon.of("arrow_forward"), contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(template.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (template.repeatYearly) "次年今日 · 每年重复"
                                else "自今天起 ${template.daysFromNow} 天 · 单次",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("一键套用", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item {
            OmniStaticCard("verified_user", "本地原生架构保证", "零常驻后台进程 · 电量友好") {
                OmniInfoLine("database", "Room 持久化", "数据仅存本机")
                OmniInfoLine("alarm_on", "AlarmManager", "精确对齐免耗电")
                OmniInfoLine("check_circle", "零网络", "无任何 INTERNET 权限")
            }
        }
    }
}

/* ============================ 列表（countdown_list） ============================ */

@Composable
private fun CountdownListScreen(
    state: CountdownUiState,
    onEvent: (CountdownEvent) -> Unit,
    onBack: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    val views = state.events.map { eventView(it, state.nowMillis) }
    val upcoming = views.filter { !it.past }
    val filtered = when (state.filter) {
        CountdownFilter.ALL -> views
        CountdownFilter.UPCOMING -> upcoming
        CountdownFilter.PAST -> views.filter { it.past }
    }
    val featured = upcoming.minByOrNull { it.effectiveTarget }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除事件") },
            text = { Text("删除后将同时取消对应的系统闹钟，且不可恢复。") },
            confirmButton = {
                val id = pendingDelete
                TextButton(onClick = {
                    if (id != null) onEvent(CountdownEvent.Delete(id))
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).omniStatusBarInset().omniNavBarInset()) {
        item {
            OmniTopBar(
                title = "置顶目标 · 倒计时",
                subtitle = "共 ${views.size} 个事件 · 每秒实时刷新",
                navigationIcon = OmniIcon.of("arrow_back"),
                onNavigationClick = onBack
            ) {
                IconButton(onClick = { onEvent(CountdownEvent.CreateNew) }) {
                    Icon(OmniIcon.of("add"), contentDescription = "新建日程")
                }
            }
        }
        item { MessageBanner(state.message) { onEvent(CountdownEvent.ConsumeMessage) } }
        if (featured != null) {
            item {
                FeaturedCard(featured)
            }
        }
        item {
            OmniStaticCard("tune", "快速参数预设", "默认规则生效中") {
                OmniInfoLine("calendar_month", "历法制式", "公历")
                OmniInfoLine("update", "循环周期", "每年 / 单次")
                OmniInfoLine("view_quilt", "刷新方式", "Room 数据流 + 每秒 Tick")
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OmniMiniChip("全部 (${views.size})", state.filter == CountdownFilter.ALL) {
                    onEvent(CountdownEvent.SetFilter(CountdownFilter.ALL))
                }
                OmniMiniChip("即将到来 (${upcoming.size})", state.filter == CountdownFilter.UPCOMING) {
                    onEvent(CountdownEvent.SetFilter(CountdownFilter.UPCOMING))
                }
                OmniMiniChip("已过去 (${views.size - upcoming.size})", state.filter == CountdownFilter.PAST) {
                    onEvent(CountdownEvent.SetFilter(CountdownFilter.PAST))
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                Text("该筛选下暂无事件。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = OmniDimens.ScreenPadding, vertical = 16.dp))
            }
        }
        filtered.forEach { view ->
            item(key = view.entity.id) {
                EventCard(view, onEvent) { pendingDelete = view.entity.id }
            }
        }
        item {
            OmniPrimaryButton("新建日程", onClick = { onEvent(CountdownEvent.CreateNew) },
                icon = "add", modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = OmniDimens.ScreenPadding, vertical = 12.dp))
        }
        item {
            Text("基于本地 Room 数据库与 AlarmManager 唤醒机制，无常驻后台服务，离线自治安全。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(OmniDimens.ScreenPadding))
        }
    }
}

@Composable
private fun FeaturedCard(view: EventView) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = OmniShape.Card,
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(view.entity.title, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(4.dp))
            Text("目标：${CountdownEngine.format(view.effectiveTarget)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeUnit("天", view.remaining.days)
                TimeUnit("时", view.remaining.hours)
                TimeUnit("分", view.remaining.minutes)
                TimeUnit("秒", view.remaining.seconds)
            }
            Spacer(Modifier.height(12.dp))
            Text("筹备周期已推进 ${view.progress}% · 起始 ${formatShort(view.entity.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun RowScope.TimeUnit(label: String, value: Long) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = OmniShape.CardSmall,
        modifier = Modifier.weight(1f)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EventCard(
    view: EventView,
    onEvent: (CountdownEvent) -> Unit,
    onDelete: () -> Unit
) {
    val e = view.entity
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = OmniShape.CardSmall,
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(countdownColor(e.colorToken)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(
                        "${if (e.repeatYearly) "公历 · 每年" else "单次"} · " +
                            CountdownEngine.format(view.effectiveTarget),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OmniIconAction("edit", "编辑") { onEvent(CountdownEvent.Edit(e.id)) }
                Spacer(Modifier.width(6.dp))
                OmniIconAction("delete", "删除", onDelete)
            }
            Spacer(Modifier.height(12.dp))
            if (view.past) {
                Text("已过去 ${view.passedDays} 天",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("还有", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text("${view.remaining.days}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("天 ${view.remaining.hours} 时 ${view.remaining.minutes} 分 ${view.remaining.seconds} 秒",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("筹备周期已推进 ${view.progress}% · 起始 ${formatShort(e.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (e.notifyEnabled) {
                Spacer(Modifier.height(4.dp))
                OmniInfoLine("notifications", "离线提醒", "已写入系统 AlarmManager")
            }
        }
    }
}

/* ============================ 编辑器（countdown_editor） ============================ */

@Composable
private fun CountdownEditorScreen(
    state: CountdownUiState,
    onEvent: (CountdownEvent) -> Unit,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val d = state.draft
    val parsed = CountdownEngine.parse(d.dateText, d.timeText)
    val preview = if (parsed != null) CountdownEngine.remaining(state.nowMillis, parsed) else null

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).omniStatusBarInset().omniNavBarInset()) {
        item {
            OmniTopBar(
                title = if (d.id == 0L) "新建目标事件" else "编辑目标事件",
                subtitle = "本地离线加密存储",
                navigationIcon = OmniIcon.of("arrow_back"),
                onNavigationClick = onBack
            ) {
                TextButton(onClick = { onEvent(CountdownEvent.SaveEditor) }) {
                    Icon(OmniIcon.of("check"), contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("保存")
                }
            }
        }
        item { MessageBanner(state.message) { onEvent(CountdownEvent.ConsumeMessage) } }
        item {
            OmniStaticCard("event_note", "目标基本信息", "公历 · 本机时区") {
                OmniLabel("事件名称")
                OutlinedTextField(
                    value = d.title,
                    onValueChange = { onEvent(CountdownEvent.SetTitle(it)) },
                    placeholder = { Text("例如：考研冲刺倒计时") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OmniLabel("目标日期与时间")
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = d.dateText,
                        onValueChange = { onEvent(CountdownEvent.SetDate(it)) },
                        label = { Text("yyyy-MM-dd") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = d.timeText,
                        onValueChange = { onEvent(CountdownEvent.SetTime(it)) },
                        label = { Text("HH:mm") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("快捷预设：", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CountdownEngine.DatePreset.entries.forEach { preset ->
                        OmniMiniChip(preset.label, false) {
                            onEvent(CountdownEvent.ApplyDatePreset(preset))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (preview != null && parsed != null) {
                    Text("离目标还有：${preview.days} 天 ${preview.hours} 小时 ${preview.minutes} 分",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text(CountdownEngine.format(parsed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("日期或时间格式不正确，请使用 yyyy-MM-dd / HH:mm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            OmniStaticCard("palette", "主题色 (colorToken)", colorTokenLabel(d.colorToken)) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CountdownColorOptions.forEach { (token, label) ->
                        Row(
                            Modifier.clip(OmniShape.Pill)
                                .background(
                                    if (d.colorToken == token) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .clickable { onEvent(CountdownEvent.SetColorToken(token)) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(countdownColor(token)))
                            Spacer(Modifier.width(6.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item {
            OmniStaticCard("update", "周期循环与离线提醒", "AlarmManager 精确闹钟") {
                OmniSwitchLine(
                    "每年重复", "适合生辰、考研、周年日；自动计算下一个周年的真实时间戳",
                    d.repeatYearly
                ) { onEvent(CountdownEvent.SetRepeatYearly(it)) }
                OmniSwitchLine(
                    if (d.notifyEnabled) "离线提醒已开启" else "离线高精度闹钟唤醒",
                    "基于系统 AlarmManager 深度睡眠唤醒，零流量推送",
                    d.notifyEnabled
                ) { onEvent(CountdownEvent.SetNotifyEnabled(it)) }
                if (!state.notificationPermissionGranted) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = OmniShape.CardSmall,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Android 13+ 需要「通知」权限才能发出提醒。当前未授权，提醒不会被设置。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = onRequestPermission) {
                                Text("去授权通知权限")
                            }
                        }
                    }
                } else if (d.notifyEnabled) {
                    Spacer(Modifier.height(6.dp))
                    Text("保存后将在目标时间向系统注册真实精确闹钟。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = OmniDimens.ScreenPadding, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OmniPrimaryButton("保存事件", onClick = { onEvent(CountdownEvent.SaveEditor) },
                    icon = "save", modifier = Modifier.weight(1f))
                if (d.id != 0L) {
                    OmniPrimaryButton("删除", onClick = { onEvent(CountdownEvent.Delete(d.id)) },
                        icon = "delete", modifier = Modifier.weight(1f))
                }
            }
        }
        item {
            Text("本地沙盒环境存储 · 无后台常驻消耗 · 事件删除后空态自动出现。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(OmniDimens.ScreenPadding))
        }
    }
}

/* ---- 本模块复用的小组件（沿用 PasswordScreen 的稳定 API 模式） ---- */

@Composable
private fun OmniStaticCard(
    icon: String,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = OmniDimens.ScreenPadding, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = OmniShape.CardSmall
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(OmniIcon.of(icon), contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.weight(1f))
                    Text(subtitle, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun OmniInfoLine(icon: String, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(OmniIcon.of(icon), contentDescription = null, modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun OmniSwitchLine(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(hint, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun OmniMiniChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = OmniShape.Pill,
        modifier = Modifier.clip(OmniShape.Pill).clickable(onClick = onClick)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelSmall)
    }
}
