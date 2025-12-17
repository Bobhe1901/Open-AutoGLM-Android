package com.example.open_autoglm_android.ui.screen

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.open_autoglm_android.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    navController: NavController
) {
    val uiState = viewModel.uiState.collectAsState().value
    val context = LocalContext.current
    
    // 跳转到系统无障碍设置的函数
    val navigateToAccessibilitySettings = {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        context.startActivity(intent)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 无障碍服务状态卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            if (uiState.isAccessibilityEnabled) {
                                Text(
                                    text = "已启用",
                                    color = Color.Green,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                // 当无障碍服务未启用时，显示可点击的提示
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "未启用",
                                        color = Color.Red,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        textDecoration = TextDecoration.Underline,
                                        modifier = Modifier.clickable {
                                            navigateToAccessibilitySettings()
                                        }
                                    )
                                    Text(
                                        text = " (点击前往设置)",
                                        color = Color.Red,
                                        fontSize = 14.sp,
                                        modifier = Modifier.clickable {
                                            navigateToAccessibilitySettings()
                                        }
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = navigateToAccessibilitySettings
                        ) {
                            Text(text = "前往设置")
                        }
                    }
                }
            }
            
            // API Key 设置（移除）
            
            // Base URL 设置（移除）
            
            // Model Name 设置（移除）
            
            // 保存按钮（移除）
            
            // 错误提示
            if (uiState.error != null) {
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )
            }
            
            // 成功提示
            if (uiState.saveSuccess == true) {
                Text(
                    text = "保存成功",
                    color = Color.Green,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 操作说明
            Text(
                text = "操作说明：\n\n" +
                        "1. 请确保无障碍服务已启用\n" +
                        "2. 点击下方按钮返回对话界面\n" +
                        "3. 在对话界面输入指令，应用将自动执行",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            
            // 返回按钮
            Button(
                onClick = {
                    navController.navigateUp()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(text = "返回对话")
            }
        }
    }
}