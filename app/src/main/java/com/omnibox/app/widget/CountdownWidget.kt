package com.omnibox.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.omnibox.core.data.local.CountdownEventEntity
import com.omnibox.feature.countdown.CountdownEngine
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 倒计时桌面小组件：显示最近的 3 个纪念日与真实剩余时间。
 *
 * 数据每次渲染都从 Room 现取，不在进程里缓存；刷新由
 * [CountdownWidgetTickReceiver] 的 15 分钟闹钟 + 系统 30 分钟周期共同驱动。
 * 剩余时间用 [CountdownEngine.nextOccurrence]，与 App 内算法完全同一份实现。
 */
class CountdownWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val now = System.currentTimeMillis()
        val events = runCatching { entryPoint.countdownDao().observeAll().first() }.getOrDefault(emptyList())
        val upcoming = events
            .map { event -> event to CountdownEngine.nextOccurrence(event.targetEpochMillis, event.repeatYearly, now) }
            .filter { it.second > now }
            .sortedBy { it.second }
            .take(3)
            .map { (event, target) -> CountdownRowModel(event.title, CountdownEngine.remaining(now, target)) }

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(WidgetColors.SURFACE))
                    .padding(14.dp)
                    .clickable(actionStartActivity(openToolIntent("countdown")))
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "倒计时",
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.PRIMARY),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Text(
                        text = "${events.size} 项",
                        style = TextStyle(color = ColorProvider(WidgetColors.ON_SURFACE_VARIANT), fontSize = 11.sp)
                    )
                }
                Spacer(modifier = GlanceModifier.height(8.dp))
                if (upcoming.isEmpty()) {
                    Text(
                        text = "还没有纪念日，点击创建第一个",
                        style = TextStyle(color = ColorProvider(WidgetColors.ON_SURFACE_VARIANT), fontSize = 12.sp),
                        maxLines = 2
                    )
                } else {
                    upcoming.forEach { row ->
                        CountdownLine(row)
                        Spacer(modifier = GlanceModifier.height(6.dp))
                    }
                }
            }
        }
    }
}

data class CountdownRowModel(val title: String, val remaining: CountdownEngine.Remaining)

@Composable
private fun CountdownLine(row: CountdownRowModel) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.title,
            style = TextStyle(
                color = ColorProvider(WidgetColors.ON_SURFACE),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
        Text(
            text = remainingLabel(row.remaining),
            style = TextStyle(
                color = ColorProvider(WidgetColors.PRIMARY),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

/** 天级为主，进入 1 天内才显示小时，避免桌面出现不停跳秒的假精确。 */
internal fun remainingLabel(remaining: CountdownEngine.Remaining): String = when {
    remaining.days >= 1 -> "${remaining.days} 天"
    remaining.hours >= 1 -> "${remaining.hours} 小时"
    remaining.minutes >= 1 -> "${remaining.minutes} 分钟"
    else -> "即将开始"
}

internal fun openToolIntent(toolId: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("omnibox://tool/$toolId")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

/** 15 分钟一次的自刷新，用不精确闹钟（不需要 SCHEDULE_EXACT_ALARM，也不会被系统拒绝）。 */
class CountdownWidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                CountdownWidget().updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}

class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleTick(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelTick(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // 系统在开机 / 恢复备份时也会走 onUpdate，顺手把闹钟补回来
        scheduleTick(context)
    }

    private fun scheduleTick(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + TICK_INTERVAL_MS,
                TICK_INTERVAL_MS,
                tickIntent(context)
            )
        }
    }

    private fun cancelTick(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { alarmManager.cancel(tickIntent(context)) }
    }

    private fun tickIntent(context: Context): PendingIntent {
        val intent = Intent(context, CountdownWidgetTickReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_TICK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val TICK_INTERVAL_MS = 15 * 60 * 1000L
        const val REQUEST_TICK = 7301
    }
}

/** 供 App 内修改数据后主动刷新小组件（当前无调用方也保留，避免以后重复实现）。 */
suspend fun refreshCountdownWidget(context: Context) {
    runCatching { CountdownWidget().updateAll(context) }
}

/** 小组件是否已被用户添加到桌面，用于设置页展示真实状态。 */
fun isCountdownWidgetPlaced(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context) ?: return false
    val ids = manager.getAppWidgetIds(ComponentName(context, CountdownWidgetReceiver::class.java))
    return ids.isNotEmpty()
}
