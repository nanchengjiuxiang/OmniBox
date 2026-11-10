package com.omnibox.feature.formatconverter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 批量转码的通知与前台服务。
 *
 * 存在的理由：一次选 9 张 4K 图转 WebP 可能跑几十秒，用户很可能切走。
 * 前台服务让系统知道「这个进程正在做用户可见的工作」，避免被后台回收；
 * 通知里的进度条是真实进度（来自 [TranscodeJobRunner.state]），不是装饰。
 */
@AndroidEntryPoint
class TranscodeService : Service() {

    @Inject
    lateinit var runner: TranscodeJobRunner

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        TranscodeNotifier.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 通知栏「取消」按钮走的是同一个 Service 的另一个 action
        if (intent?.action == ACTION_CANCEL) {
            runner.cancel()
            return START_NOT_STICKY
        }

        startForegroundSafely(TranscodeNotifier.build(this, TranscodeJobState(running = true)))

        if (observer == null) {
            observer = scope.launch {
                runner.state.collectLatest { state ->
                    if (!state.running) {
                        stopSelf()
                    } else {
                        TranscodeNotifier.publish(this@TranscodeService, state)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observer?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundSafely(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        // Android 14 起必须声明 type；没有 POST_NOTIFICATIONS 时通知不显示，但服务仍能继续跑
        runCatching { ServiceCompat.startForeground(this, TranscodeNotifier.NOTIFICATION_ID, notification, type) }
    }

    companion object {
        const val ACTION_CANCEL = "com.omnibox.feature.formatconverter.action.CANCEL"

        fun start(context: Context) {
            val intent = Intent(context, TranscodeService::class.java)
            runCatching { context.startForegroundService(intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, TranscodeService::class.java)) }
        }
    }
}

/** 通知的唯一出口：频道、进度条、取消动作、点击回到转码页。 */
object TranscodeNotifier {

    const val NOTIFICATION_ID = 4101
    private const val CHANNEL_ID = "omnibox_batch_transcode"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "批量转码",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示图片批量转码的真实进度"
                setShowBadge(false)
            }
        )
    }

    fun build(context: Context, state: TranscodeJobState): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(Intent.ACTION_VIEW, Uri.parse("omnibox://tool/format_converter"))
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getService(
            context,
            1,
            Intent(context, TranscodeService::class.java).setAction(TranscodeService.ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val total = state.total.coerceAtLeast(1)
        val processed = state.processed.coerceIn(0, total)
        val title = if (state.running) "正在转码 ${processed.coerceAtMost(total)}/$total" else "转码已结束"
        val text = when {
            state.running && state.currentName.isNotBlank() -> state.currentName
            state.running -> "准备中…"
            else -> "已完成 ${state.progress}%"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, state.progress.coerceIn(0, 100), false)
            .setOngoing(state.running)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .apply { if (state.running) addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", cancel) }
            .build()
    }

    fun publish(context: Context, state: TranscodeJobState) {
        // Android 13+ 无通知权限时会被系统静默丢弃，这里不因它中断转码
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, build(context, state))
        }
    }
}
