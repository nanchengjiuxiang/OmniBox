package com.omnibox.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.omnibox.core.data.local.QrScanHistoryEntity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 二维码快捷小组件：显示最近一次识别/生成的内容与来源，点一下直达扫码页。
 * 内容全部来自 `qr_scan_history`，没有记录时明确说「暂无记录」，不造假数据。
 */
class QrWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val latest = runCatching {
            entryPoint.qrHistoryDao().observeAll().first().firstOrNull()
        }.getOrNull()

        provideContent { QrWidgetContent(latest) }
    }
}

@Composable
private fun QrWidgetContent(latest: QrScanHistoryEntity?) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WidgetColors.SURFACE))
            .padding(14.dp)
            .clickable(actionStartActivity(openToolIntent("qrcode")))
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "二维码",
                style = TextStyle(
                    color = ColorProvider(WidgetColors.PRIMARY),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "离线可用",
                style = TextStyle(color = ColorProvider(WidgetColors.ON_SURFACE_VARIANT), fontSize = 11.sp)
            )
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        if (latest == null) {
            Text(
                text = "暂无记录，点击进入扫码或生成",
                style = TextStyle(color = ColorProvider(WidgetColors.ON_SURFACE_VARIANT), fontSize = 12.sp),
                maxLines = 2
            )
        } else {
            Text(
                text = latest.content,
                style = TextStyle(
                    color = ColorProvider(WidgetColors.ON_SURFACE),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 2
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "${sourceLabel(latest.source)} · ${timeLabel(latest.createdAt)}",
                style = TextStyle(color = ColorProvider(WidgetColors.ON_SURFACE_VARIANT), fontSize = 11.sp),
                maxLines = 1
            )
        }
    }
}

private fun sourceLabel(source: String): String = when (source) {
    "camera" -> "相机识别"
    "image" -> "相册识别"
    "generated" -> "本地生成"
    else -> "历史记录"
}

private fun timeLabel(epochMillis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))

class QrWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QrWidget()
}
