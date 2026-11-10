package com.omnibox.feature.imagecompress

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun CompareSplitView(
    sourceUri: Uri,
    compressedUri: Uri?,
    modifier: Modifier = Modifier
) {
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val widthPx = constraints.maxWidth.toFloat()

        // 1. 底层：优化后的图片 (右侧视窗)
        AsyncImage(
            model = compressedUri ?: sourceUri,
            contentDescription = "已优化图像",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 右上角浮标
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        ) {
            Text(
                text = "已优化 (WebP)",
                fontSize = 11.sp,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // 2. 顶层：原图 (左侧视窗，动态裁剪)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(LocalDensity.current) { (widthPx * splitFraction).toDp() })
                .clipToBounds()
        ) {
            AsyncImage(
                model = sourceUri,
                contentDescription = "原图",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(with(LocalDensity.current) { widthPx.toDp() })
                    .fillMaxHeight()
            )

            // 左上角浮标
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = "原图 (Original)",
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // 3. 分割拖拽把手中线
        val handleOffsetDp = with(LocalDensity.current) { (widthPx * splitFraction).toDp() }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .offset(x = handleOffsetDp - 1.dp)
                .width(2.dp)
                .background(Color.White)
        )

        // 拖拽圆钮
        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = handleOffsetDp - 18.dp)
                .size(36.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        val newFraction = (splitFraction + dragAmount / widthPx).coerceIn(0.05f, 0.95f)
                        splitFraction = newFraction
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "左右拖动对比",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
