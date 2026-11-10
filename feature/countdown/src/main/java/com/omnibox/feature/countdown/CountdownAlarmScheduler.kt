package com.omnibox.feature.countdown

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.omnibox.core.data.local.CountdownEventEntity

/**
 * 倒计时提醒的唯一调度入口。
 *
 * ViewModel 保存/删除事件、以及开机后 [CountdownBootReceiver] 重建提醒，都走这里，
 * 保证「闹钟的 PendingIntent 形状」只有一份定义——requestCode 用的是 eventId，
 * 三处各写一套很容易在重建时对不上，导致取消不掉或重复响铃。
 */
internal object CountdownAlarmScheduler {

    private fun reminderIntent(context: Context, eventId: Long, title: String): PendingIntent {
        val intent = Intent(context, CountdownReminderReceiver::class.java).apply {
            putExtra(CountdownReminderReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(CountdownReminderReceiver.EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            eventId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 按事件的真实目标时间（年度重复则取下一次周年）设置闹钟。
     * 时间已过去则不下单，返回 false，由调用方决定是否提示。
     */
    fun schedule(context: Context, entity: CountdownEventEntity): Boolean {
        CountdownReminderReceiver.ensureNotificationChannel(context)
        val now = System.currentTimeMillis()
        val trigger = CountdownEngine.nextOccurrence(
            entity.targetEpochMillis, entity.repeatYearly, now
        )
        if (trigger <= now) return false
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = reminderIntent(context, entity.id, entity.title)
        // Android 12+ 若未获精确闹钟特殊权限，退回系统允许的免耗电闹钟（仍是真实闹钟，只是可能延迟几分钟）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } else {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
        return true
    }

    fun cancel(context: Context, eventId: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(reminderIntent(context, eventId, ""))
    }
}
