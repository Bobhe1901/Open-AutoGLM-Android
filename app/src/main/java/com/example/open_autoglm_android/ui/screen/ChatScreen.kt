package com.example.open_autoglm_android.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.open_autoglm_android.service.RecordingForegroundService
import com.example.open_autoglm_android.utils.VoiceRecognitionHelper
import com.example.open_autoglm_android.ui.viewmodel.ChatViewModel
import com.example.open_autoglm_android.ui.viewmodel.MessageRole
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatViewModel: ChatViewModel = viewModel()) {
    val uiState by chatViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var inputText by remember { mutableStateOf("") }
    var isVoiceMode by remember { mutableStateOf(true) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableIntStateOf(0) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    // 检查录音权限
    fun checkRecordPermission(): Boolean {
        val hasRecordPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        // Android 14及以上需要POST_NOTIFICATIONS权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            return hasRecordPermission && hasNotificationPermission
        }
        
        return hasRecordPermission
    }
    
    // 请求录音权限
    fun requestRecordPermission() {
        if (context is Activity) {
            val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
            
            // Android 14及以上添加POST_NOTIFICATIONS权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            
            ActivityCompat.requestPermissions(
                context as Activity,
                permissions.toTypedArray(),
                1001
            )
        }
    }
    
    // 显示提示信息
    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "关闭",
                duration = SnackbarDuration.Short
            )
        }
    }
    
    // 开始语音识别
    fun startVoiceRecognition() {
        if (!checkRecordPermission()) {
            showPermissionDialog = true
            return
        }
        
        // Android 14及以上启动前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RecordingForegroundService.start(context)
        }
        
        VoiceRecognitionHelper.startVoiceRecognition(
            context = context,
            onResult = { text ->
                // 停止前台服务
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    RecordingForegroundService.stop(context)
                }
                
                if (text.isNotEmpty()) {
                    chatViewModel.sendMessage(text)
                } else {
                    showMessage("未识别到语音")
                }
            },
            onError = { error ->
                // 停止前台服务
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    RecordingForegroundService.stop(context)
                }
                
                showMessage("语音识别失败: $error")
            },
            onListening = { isListening ->
                isRecording = isListening
            }
        )
    }
    
    // 停止语音识别
    fun stopVoiceRecognition() {
        VoiceRecognitionHelper.stopVoiceRecognition()
    }
    
    // 监听生命周期，在销毁时停止语音识别
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                VoiceRecognitionHelper.stopVoiceRecognition()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            VoiceRecognitionHelper.stopVoiceRecognition()
        }
    }
    
    // 自动滚动到最新消息
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.scrollToItem(uiState.messages.size - 1)
            }
        }
    }
    
    // 录音计时
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                delay(1000)
                recordingTime++
            }
        } else {
            recordingTime = 0
        }
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0) // 移除所有系统默认padding
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(top = paddingValues.calculateTopPadding()) // 只保留顶部padding
        ) {
            // 顶部联系人信息栏
            TopAppBar(
                title = { Text("大模型手机助手") },
                modifier = Modifier.background(Color.White)
            )
            
            // 任务完成提示
            if (uiState.taskCompletedMessage != null) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = Color.Green,
                    action = {
                        TextButton(onClick = { chatViewModel.clearTaskCompletedMessage() }) {
                            Text("关闭")
                        }
                    }
                ) {
                    Text(uiState.taskCompletedMessage!!)
                }
            }
            
            // 消息列表
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(12.dp)
            ) {
                items(uiState.messages) { message ->
                    ChatMessageItem(message = message)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                if (uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                // 录音时的提示
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .background(Color(0xFF4A90E2), RoundedCornerShape(24.dp))
                                .padding(24.dp)
                                .shadow(4.dp, RoundedCornerShape(24.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF4A90E2))
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "正在录音",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "松手发送，上移取消",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                // 简化的波形动画（实际项目中可以使用更复杂的动画）
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    for (i in 0..15) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height((8 + (i % 5) * 6).dp)
                                                .background(Color.White, RoundedCornerShape(2.dp))
                                                .padding(horizontal = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (isVoiceMode) {
                    // 语音输入模式（第三张图片）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF0F0F0), RoundedCornerShape(22.dp))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左侧按钮（切换到文字输入模式）
                        IconButton(
                            onClick = { 
                                isVoiceMode = false
                            },
                            modifier = Modifier
                                .size(40.dp)
                        ) {
                            Icon(Icons.Default.Keyboard, contentDescription = "文字输入", tint = Color.Gray)
                        }
                        
                        // 中间的按住说话按钮
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            // 开始语音识别
                                            startVoiceRecognition()
                                            try {
                                                awaitRelease()
                                            } finally {
                                                // 松开时停止语音识别
                                                stopVoiceRecognition()
                                            }
                                        },
                                        onTap = {
                                            // 移除点击切换到文字输入模式的逻辑
                                            // 语音模式现在会保持，除非用户手动切换
                                        }
                                    )
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "按住说话",
                                    color = Color(0xFF333333),
                                    fontSize = 16.sp
                                )
                            }
                        }
                        
                        // 右侧按钮（加号）
                        IconButton(
                            onClick = { 
                                // 显示"研发中，敬请期待"提示
                                showMessage("研发中，敬请期待")
                            },
                            modifier = Modifier
                                .size(40.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "更多", tint = Color.Gray)
                        }
                    }
                } else {
                    // 文字输入模式（第一张图片）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF0F0F0), RoundedCornerShape(22.dp))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左侧按钮（切换到语音输入模式）
                        IconButton(
                            onClick = { isVoiceMode = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "语音输入", tint = Color.Gray)
                        }
                        
                        // 文字输入框
                        TextField(
                            value = inputText,
                            onValueChange = { value -> inputText = value },
                            placeholder = { 
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "发消息或按住说话...",
                                        color = Color.Gray,
                                        fontSize = 14.sp
                                    )
                                }
                            },
                            shape = RoundedCornerShape(22.dp),
                            maxLines = 3,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp, max = 120.dp), // 允许输入框高度自适应
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color.Black
                            ),
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                color = Color.Black
                            ),
                            singleLine = false // 允许多行输入
                        )
                        
                        // 右侧按钮区域
                        if (inputText.isNotBlank()) {
                            // 有输入内容时显示发送按钮
                            IconButton(
                                onClick = {
                                    chatViewModel.sendMessage(inputText)
                                    inputText = ""
                                },
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(40.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", tint = Color(0xFF1976D2))
                            }
                        } else {
                            // 无输入内容时显示加号按钮
                            IconButton(
                                onClick = { 
                                    // 显示"研发中，敬请期待"提示
                                    showMessage("研发中，敬请期待")
                                },
                                modifier = Modifier
                                    .size(40.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "更多", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
    
    // 权限对话框（放在Scaffold外面，确保它在最上层）
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要录音权限") },
            text = { Text("此应用需要录音权限来进行语音识别") },
            confirmButton = {
                Button(onClick = {
                    requestRecordPermission()
                    showPermissionDialog = false
                }) {
                    Text("授予权限")
                }
            },
            dismissButton = {
                Button(onClick = { showPermissionDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun ChatMessageItem(message: com.example.open_autoglm_android.ui.viewmodel.ChatMessage) {
    val isUserMessage = message.role == MessageRole.USER
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = if (isUserMessage) Alignment.End else Alignment.Start
    ) {
        if (isUserMessage) {
            // 用户消息
            Box(
                modifier = Modifier
                    .background(Color(0xFF90CAF9), RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                    .padding(12.dp, 8.dp, 16.dp, 12.dp)
            ) {
                Text(
                    message.content,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = Color.Black
                )
            }
        } else {
            // AI消息
            Box(
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                    .padding(12.dp, 8.dp, 16.dp, 12.dp)
                    .shadow(1.dp, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
            ) {
                Column {
                    // 思考过程（仅助手消息）
                    if (!message.thinking.isNullOrEmpty()) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "思考过程:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Gray
                                )
                            }
                            
                            Text(
                                message.thinking,
                                fontSize = 14.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    
                    // 消息内容
                    Text(
                        message.content,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        color = Color.Black
                    )
                    
                    // 动作信息（仅助手消息）
                    if (!message.action.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF5F5F5))
                                .padding(8.dp)
                                .padding(top = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "执行动作: ${message.action.take(50)}${if (message.action.length > 50) "..." else ""}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
        
        // 时间戳
        Text(
            text = android.text.format.DateFormat.format("HH:mm", message.timestamp).toString(),
            fontSize = 10.sp,
            color = Color.Gray,
            modifier = Modifier
                .padding(top = 4.dp)
                .align(if (isUserMessage) Alignment.End else Alignment.Start)
        )
    }
}