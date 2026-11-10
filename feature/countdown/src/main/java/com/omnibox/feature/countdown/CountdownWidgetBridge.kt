package com.omnibox.feature.countdown

import android.content.Context
import android.content.Intent

/**
 * 倒计时数据变动 → 桌面小组件刷新。
 *
 * 小组件渲染在 app 模块（Glance），本模块不能反向依赖 app，
 * 因此用一条应用内广播解耦：feature 发，app 收。
 * **两端的 action 字符串必须一致**，改这里要同步改
 * `app/src/main/java/com/omnibox/app/widget/CountdownWidgetRefreshReceiver.kt`。
 */
private const val ACTION_COUNTDOWN_CHANGED = "com.omnibox.action.COUNTDOWN_CHANGED"

/** 广播只在本应用内传递（setPackage），不需要也不应该 exported。 */
fun notifyCountdownWidgetChanged(context: Context) {
    runCatching {
        context.sendBroadcast(Intent(ACTION_COUNTDOWN_CHANGED).setPackage(context.packageName))
    }
}
