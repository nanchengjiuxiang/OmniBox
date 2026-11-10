package com.omnibox.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 收到「倒计时数据已变更」的应用内广播后立即重绘小组件。
 * action 必须与 `feature/countdown/CountdownWidgetBridge.kt` 里的常量保持一致。
 */
class CountdownWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COUNTDOWN_CHANGED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                CountdownWidget().updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COUNTDOWN_CHANGED = "com.omnibox.action.COUNTDOWN_CHANGED"
    }
}
