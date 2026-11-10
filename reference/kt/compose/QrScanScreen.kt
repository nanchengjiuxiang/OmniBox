package com.omnibox.feature.qrcode

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGenerator: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QrScanViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()

    // 权限请求 Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val activity = context as? androidx.activity.ComponentActivity
        val shouldShowRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ?: false
        viewModel.handleEvent(QrScanUiEvent.OnPermissionResult(isGranted, shouldShowRationale))
    }

    // 系统安全图片选择器 (PhotoPicker - 无需任何存储权限)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.handleEvent(QrScanUiEvent.PickImageFromGallery(it)) }
    }

    // 初次进入生命周期检查权限状态
    LaunchedEffect(Unit) {
        val currentPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (currentPermission == PermissionChecker.PERMISSION_GRANTED) {
            viewModel.handleEvent(QrScanUiEvent.OnPermissionResult(true, false))
        } else {
            val activity = context as? androidx.activity.ComponentActivity
            val shouldShowRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ?: false
            viewModel.handleEvent(QrScanUiEvent.OnPermissionResult(false, shouldShowRationale))
        }
    }

    // 处理副作用 (Effect)
    LaunchedEffect(viewModel.uiEffect) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is QrScanUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is QrScanUiEffect.NavigateToGenerator -> onNavigateToGenerator(effect.prefillContent)
                is QrScanUiEffect.TriggerHapticFeedback -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                is QrScanUiEffect.CopyToClipboard -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ScanResult", effect.text))
                }
                is QrScanUiEffect.OpenSettingsScreen -> {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("二维码扫码", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.handleEvent(QrScanUiEvent.SwitchToGenerateMode) }) {
                        Icon(Icons.Outlined.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("离线生成")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            when (val state = uiState) {
                is QrScanUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is QrScanUiState.PermissionDenied -> {
                    PermissionRationaleView(
                        isPermanentlyDenied = state.isPermanentlyDenied,
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onOpenSettings = { viewModel.handleEvent(QrScanUiEvent.OpenAppSettings) },
                        onPickFromGallery = { photoPickerLauncher.launch("image/*") },
                        onSwitchToGenerator = { viewModel.handleEvent(QrScanUiEvent.SwitchToGenerateMode) }
                    )
                }

                is QrScanUiState.Scanning -> {
                    ScanningActiveView(
                        state = state,
                        onBarcodeDetected = { code, fmt ->
                            viewModel.handleEvent(QrScanUiEvent.OnBarcodeDetected(code, fmt))
                        },
                        onToggleTorch = { viewModel.handleEvent(QrScanUiEvent.ToggleTorch) },
                        onPickFromGallery = { photoPickerLauncher.launch("image/*") },
                        onCopyResult = { viewModel.handleEvent(QrScanUiEvent.CopyResult) },
                        onResume = { viewModel.handleEvent(QrScanUiEvent.ResumeScanning) }
                    )
                }
            }
        }
    }
}
