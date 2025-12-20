package com.example.open_autoglm_android.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.open_autoglm_android.ui.viewmodel.SettingsViewModel
import com.example.open_autoglm_android.util.AuthHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToAdvancedAuth: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasWriteSecureSettings = remember { mutableStateOf(false) }
    
    // 检查 WRITE_SECURE_SETTINGS 权限状态
    LaunchedEffect(Unit) {
        hasWriteSecureSettings.value = AuthHelper.hasWriteSecureSettingsPermission(context)
    }
    
    // 当屏幕可见时检查服务状态
    LaunchedEffect(Unit) {
        viewModel.checkAccessibilityService()
    }
    
    // 使用 DisposableEffect 在每次进入设置页面时刷新状态
    DisposableEffect(Unit) {
        viewModel.checkAccessibilityService()
        viewModel.checkOverlayPermission()
        onDispose { }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // 无障碍服务状态
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.isAccessibilityEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "无障碍服务",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (uiState.isAccessibilityEnabled) {
                                "已启用"
                            } else {
                                "未启用 - 点击前往设置"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!uiState.isAccessibilityEnabled) {
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            }
                        ) {
                            Text("前往设置")
                        }
                    }
                }
            }
        }
        
        // 悬浮窗设置
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.floatingWindowEnabled && uiState.hasOverlayPermission) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "悬浮窗",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (!uiState.hasOverlayPermission) {
                                "需要悬浮窗权限"
                            } else if (uiState.floatingWindowEnabled) {
                                "已启用 - 显示任务状态"
                            } else {
                                "未启用"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    if (!uiState.hasOverlayPermission) {
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            }
                        ) {
                            Text("授权")
                        }
                    } else {
                        Switch(
                            checked = uiState.floatingWindowEnabled,
                            onCheckedChange = { viewModel.setFloatingWindowEnabled(it) }
                        )
                    }
                }
            }
        }

        Divider()

        // 高级授权与无感保活
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (hasWriteSecureSettings.value) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "高级授权与无感保活",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (hasWriteSecureSettings.value) {
                                "✓ WRITE_SECURE_SETTINGS 已授权 - 无感保活功能可用"
                            } else {
                                "✗ WRITE_SECURE_SETTINGS 未授权 - 点击进入授权页面"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    IconButton(onClick = onNavigateToAdvancedAuth) {
                        Icon(
                            Icons.Filled.ArrowForward,
                            contentDescription = "进入高级授权页面"
                        )
                    }
                }
            }
        }

        Divider()

        // API Key 设置
        OutlinedTextField(
            value = uiState.apiKey,
            onValueChange = { viewModel.updateApiKey(it) },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("输入你的 API Key") }
        )
        
        // Base URL 设置
        OutlinedTextField(
            value = uiState.baseUrl,
            onValueChange = { viewModel.updateBaseUrl(it) },
            label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("https://open.bigmodel.cn/api/paas/v4") }
        )
        
        // Model Name 设置
        OutlinedTextField(
            value = uiState.modelName,
            onValueChange = { viewModel.updateModelName(it) },
            label = { Text("Model Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("autoglm-phone") }
        )
        
        // 保存按钮
        Button(
            onClick = { viewModel.saveSettings() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("保存设置")
            }
        }
        
        // 成功/错误提示
        uiState.saveSuccess?.let {
            if (it) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "设置已保存",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        
        uiState.error?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("关闭")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 说明文字
        Text(
            text = "说明：\n" +
                    "1. 请先开启无障碍服务\n" +
                    "2. 在智谱平台申请 API Key\n" +
                    "3. 填写 API Key 和其他配置\n" +
                    "4. 保存设置后即可使用",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
