package com.omnibox.feature.battery

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.omnibox.core.data.preferences.PreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 电池守护前台服务。
 *
 * 为什么必须前台常驻：ACTION_BATTERY_CHANGED 是粘性广播，只能动态注册，
 * 而接收器随进程存活 —— 想在用户离开 App 后仍能「充到 80% 提醒」，只能靠前台服务撑住进程。
 * 三个提醒全部是本地通知 + 震动，不控制充电（免 Root 做不到，界面文案已如实说明）。
 */
@AndroidEntryPoint
class BatteryGuardService : Service() {

    @Inject lateinit var repository: BatteryRepository
    @Inject lateinit var preferences: PreferencesRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var targetAlert = false
    private var targetLevel = 80
    private var tempAlert = false
    private var tempThresholdC = 40
    private var fullVibrate = false

    // 会话级去抖：目标电量达到后只响一次，掉电 3% 或拔线才重置
    private var targetFired = false
    private var fullFired = false
    private var lastTempAlertAt = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val snapshot = repository.current()
            updateGuardNotification(snapshot)
            checkAlerts(snapshot)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        scope.launch {
            // 服务启动时同步一次最新设置（之后由页面修改时重启服务来应用）
            targetAlert = preferences.batteryTargetAlert.first()
            targetLevel = preferences.batteryTargetLevel.first()
            tempAlert = preferences.batteryTempAlert.first()
            tempThresholdC = preferences.batteryTempThreshold.first()
            fullVibrate = preferences.batteryFullVibrate.first()
        }
        startForeground(NOTIFICATION_ID, buildNotification(repository.current()))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkAlerts(snapshot: BatterySnapshot) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        // 1. 充电目标提醒
        if (targetAlert && snapshot.isCharging && snapshot.levelPercent >= targetLevel) {
            if (!targetFired && canNotify) {
                postAlert(
                    manager, ALERT_TARGET_ID,
                    "已达充电目标 ${targetLevel}%",
                    "当前 ${snapshot.levelPercent}% · ${formatCelsius(snapshot.temperatureC)}\n" +
                        "免 Root 应用无法切断充电，请手动拔线以保护电池"
                )
                vibrate()
                targetFired = true
            }
        } else if (snapshot.levelPercent < targetLevel - 3 || !snapshot.isCharging) {
            targetFired = false
        }

        // 2. 高温提醒（10 分钟冷却，避免持续高温刷屏）
        if (tempAlert && !snapshot.temperatureC.isNaN() && snapshot.temperatureC >= tempThresholdC) {
            val now = System.currentTimeMillis()
            if (now - lastTempAlertAt > 10 * 60_000L && canNotify) {
                postAlert(
                    manager, ALERT_TEMP_ID,
                    "电芯温度偏高",
                    "当前 ${formatCelsius(snapshot.temperatureC)}（阈值 ${tempThresholdC}°C）\n" +
                        "建议移除保护壳或暂停高负载任务"
                )
                lastTempAlertAt = now
            }
        }

        // 3. 充满提醒 + 震动
        if (fullVibrate && snapshot.status == BatteryStatus.FULL) {
            if (!fullFired) {
                vibrate()
                if (canNotify) {
                    postAlert(
                        manager, ALERT_FULL_ID,
                        "充电已完成",
                        "电池已充满（${snapshot.levelPercent}%），可拔掉充电器"
                    )
                }
                fullFired = true
            }
        } else if (snapshot.status != BatteryStatus.FULL) {
            fullFired = false
        }
    }

    private fun postAlert(manager: NotificationManager, id: Int, title: String, text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }

    private fun updateGuardNotification(snapshot: BatterySnapshot) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(snapshot))
    }

    private fun buildNotification(snapshot: BatterySnapshot) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("电池守护运行中")
        .setContentText(
            "${snapshot.levelPercent}% · ${formatCelsius(snapshot.temperatureC)} · " +
                if (snapshot.isCharging) formatWatts(snapshot.powerW) else snapshot.status.label
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(contentIntent())
        .build()

    /** 回到电池页：走 omnibox:// 深链（避免 feature 模块反向依赖 :app 的 Activity 类）。 */
    private fun contentIntent(): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("omnibox://tool/battery")).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun vibrate() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.takeIf { it.hasVibrator() }?.vibrate(
            VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID, "电池守护提醒", NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "充电目标 / 电芯温度 / 充满完成的本地提醒"
                    }
                )
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "omnibox_battery_guard"
        const val NOTIFICATION_ID = 4001
        const val ALERT_TARGET_ID = 4002
        const val ALERT_TEMP_ID = 4003
        const val ALERT_FULL_ID = 4004

        /** 任一提醒开启 → 启动守护服务；全部关闭 → 停掉。 */
        fun sync(context: Context, anyEnabled: Boolean) {
            if (anyEnabled) {
                ContextCompat.startForegroundService(
                    context, Intent(context, BatteryGuardService::class.java)
                )
            } else {
                context.stopService(Intent(context, BatteryGuardService::class.java))
            }
        }
    }
}
