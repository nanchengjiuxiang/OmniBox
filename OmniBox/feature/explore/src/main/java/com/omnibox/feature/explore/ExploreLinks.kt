package com.omnibox.feature.explore

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 外链跳转。
 *
 * 本应用不内置 WebView：页面渲染、Cookie、证书校验全部交给系统浏览器，
 * 我们只负责「用哪种方式把 URL 递出去」。这样既不用维护浏览器内核，
 * 也不会出现「应用内偷偷加载网页」的隐私争议。
 */
object ExploreLinks {

    /**
     * @param useCustomTabs true = Chrome Custom Tabs（保留浏览器的地址栏与安全提示）；
     *                      false = 系统默认浏览器的普通 VIEW。
     * @param incognito true = 不在本机留下访问足迹（NO_HISTORY），并跳过最近访问记录。
     * @return false 表示设备上没有可处理 http(s) 的应用。
     */
    fun open(context: Context, url: String, useCustomTabs: Boolean, incognito: Boolean): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false

        if (useCustomTabs) {
            val launched = runCatching {
                val customTab = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setUrlBarHidingEnabled(true)
                    .build()
                customTab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (incognito) customTab.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                customTab.launchUrl(context, uri)
                true
            }.getOrDefault(false)
            if (launched) return true
            // 没有支持 Custom Tabs 的浏览器时降级为标准 VIEW，而不是直接失败
        }

        val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (incognito) addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
        return runCatching { context.startActivity(fallback); true }.getOrDefault(false)
    }
}

/** 只做「现在能不能上网」的一次性判断，用于离线提示条，不做常驻轮询。 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isOnline(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
