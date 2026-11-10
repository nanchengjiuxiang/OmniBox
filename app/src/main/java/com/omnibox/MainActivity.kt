package com.omnibox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.omnibox.app.navigation.OmniBoxRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 主题在 OmniBoxRoot 内根据 DataStore 偏好即时切换
            OmniBoxRoot(startToolId = intent?.data?.lastPathSegment)
        }
    }
}
