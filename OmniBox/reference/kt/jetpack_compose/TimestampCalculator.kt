package com.omnibox.feature.timestamp

import java.text.SimpleDateFormat
import java.util.*

object TimestampCalculator {
    fun formatEpochToDate(epochSeconds: Long, timeZoneId: String = "Asia/Shanghai"): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone(timeZoneId)
        return sdf.format(Date(epochSeconds * 1000L))
    }

    fun parseDateToEpoch(dateStr: String, timeZoneId: String = "Asia/Shanghai"): Long? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone(timeZoneId)
            sdf.parse(dateStr)?.time?.div(1000L)
        } catch (_: Exception) {
            null
        }
    }
}
