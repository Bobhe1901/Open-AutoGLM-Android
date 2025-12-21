package com.example.open_autoglm_android

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.open_autoglm_android.service.AutoGLMAccessibilityService
import com.example.open_autoglm_android.ui.screen.ChatScreen
import com.example.open_autoglm_android.ui.theme.OpenAutoGLMAndroidTheme
import com.example.open_autoglm_android.ui.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenAutoGLMAndroidTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val chatViewModel: ChatViewModel = viewModel()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    
    // 检查无障碍服务是否启用
    LaunchedEffect(Unit) {
        val isServiceEnabled = isAccessibilityServiceEnabled(context)
        if (!isServiceEnabled) {
            showAccessibilityDialog = true
        }
    }
    
    // 跳转到无障碍设置
    fun navigateToAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        context.startActivity(intent)
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("烧饼") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            ChatScreen(chatViewModel = chatViewModel)
        }
    }
    
    // 无障碍服务未启用对话框
    if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            title = { Text("需要无障碍权限") },
            text = { Text("此应用需要无障碍权限才能正常工作。请前往系统设置开启无障碍服务。") },
            confirmButton = {
                Button(onClick = {
                    navigateToAccessibilitySettings()
                    showAccessibilityDialog = false
                }) {
                    Text("前往设置")
                }
            },
            dismissButton = {
                Button(onClick = { showAccessibilityDialog = false }) {
                    Text("暂不开启")
                }
            }
        )
    }
}

// 检查无障碍服务是否启用
private fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val serviceName = "${context.packageName}/${AutoGLMAccessibilityService::class.java.canonicalName}"
    val accessibilitySettings = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    return accessibilitySettings?.contains(serviceName) == true
}