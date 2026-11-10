package com.omnibox.feature.countdown

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.omnibox.core.data.local.CountdownDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机后重建倒计时提醒。
 *
 * AlarmManager 里的闹钟不跨重启存活：设备重启后 PendingIntent 全部失效。
 * 如果不重建，用户「设了提醒、重启后没响」且界面仍显示已开启提醒——这是假承诺。
 * 因此这里在 BOOT_COMPLETED / MY_PACKAGE_REPLACED 时从 Room 读回真实事件，
 * 对每个 notifyEnabled 的事件重新走一遍 [CountdownAlarmScheduler.schedule]。
 *
 * goAsync() 保证进程在协程查库期间不被回收；广播总量很小，不会触发 ANR。
 */
class CountdownBootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun countdownDao(): CountdownDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dao = EntryPointAccessors.fromApplication(
                    appContext, BootEntryPoint::class.java
                ).countdownDao()
                // 一次性读取：DAO 只暴露 Flow，这里取首个快照即可（开机时没有并发写入）
                val events = dao.observeAll().first()
                var restored = 0
                events.filter { it.notifyEnabled }.forEach { event ->
                    if (CountdownAlarmScheduler.schedule(appContext, event)) restored++
                }
                Log.i(TAG, "开机重建提醒：待恢复 ${events.count { it.notifyEnabled }} 条，实际下单 $restored 条")
            } catch (e: Exception) {
                // 开机广播失败不应让系统反复重启应用；记录后放弃，用户下次打开 App 保存事件时会重新下单
                Log.w(TAG, "开机重建提醒失败", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "CountdownBootReceiver"
    }
}
