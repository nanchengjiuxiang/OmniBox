package com.omnibox.feature.qrcode

import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

@Composable
fun ScanningActiveView(
    state: QrScanUiState.Scanning,
    onBarcodeDetected: (String, String) -> Unit,
    onToggleTorch: () -> Unit,
    onPickFromGallery: () -> Unit,
    onCopyResult: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var camera by remember { mutableStateOf<Camera?>(null) }

    // 联动手电筒
    LaunchedEffect(state.isTorchOn) {
        camera?.cameraControl?.enableTorch(state.isTorchOn)
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // CameraX 渲染视窗
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = Executors.newSingleThreadExecutor()
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    // 纯本地内存帧分析器 (ImageAnalysis)
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(executor) { imageProxy ->
                                if (state.isAnalyzing) {
                                    // 模拟 ZXing / ML Kit 本地解码流程
                                    // 解码成功回调: onBarcodeDetected("https://...", "QR_CODE")
                                    imageProxy.close()
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // 扫码取景框标线
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.Center)
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
        )

        // 底部快捷操作栏（手电筒与本地相册降级）
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(32.dp))
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleTorch) {
                Icon(
                    imageVector = if (state.isTorchOn) Icons.Filled.FlashOn else Icons.Outlined.FlashOff,
                    contentDescription = "补光灯",
                    tint = if (state.isTorchOn) MaterialTheme.colorScheme.primary else Color.White
                )
            }

            IconButton(onClick = onPickFromGallery) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = "相册导入",
                    tint = Color.White
                )
            }
        }

        // 识别结果浮层卡片
        state.scanResult?.let { result ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "识别结果 (${result.format})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = result.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onResume,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("继续扫描")
                        }
                        Button(
                            onClick = onCopyResult,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("复制内容")
                        }
                    }
                }
            }
        }
    }
}
