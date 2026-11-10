package com.omnibox

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用入口。所有能力均为本地实现，不初始化任何网络 / 统计 / 广告 SDK。
 */
@HiltAndroidApp
class OmniBoxApplication : Application()
