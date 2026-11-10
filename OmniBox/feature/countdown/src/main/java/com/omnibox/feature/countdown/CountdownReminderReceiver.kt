package com.omnibox.feature.countdown

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * 由系统 AlarmManager 在目标时间唤醒，读取 Intent extra 后发出本地通知。
 * 注册由主工程在 app 的 AndroidManifest 里完成（本模块不改 Manifest）。
 */
class CountdownReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "倒计时提醒" }
        ensureNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("你设定的纪念日 / 倒计时时间到了")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(eventId.toInt(), notification)
    }

    companion object {
        const val CHANNEL_ID = "omnibox_countdown_reminder"
        const val EXTRA_EVENT_ID = "com.omnibox.feature.countdown.extra.EVENT_ID"
        const val EXTRA_TITLE = "com.omnibox.feature.countdown.extra.TITLE"

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "倒计时 / 纪念日提醒",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply { description = "OmniBox 本地精确闹钟提醒" }
                    manager.createNotificationChannel(channel)
                }
            }
        }
    }
}
