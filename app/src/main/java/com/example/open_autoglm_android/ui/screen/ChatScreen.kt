package com.example.open_autoglm_android.ui.screen

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.open_autoglm_android.data.TaskRecord
import com.example.open_autoglm_android.ui.viewmodel.ChatViewModel
import com.example.open_autoglm_android.ui.viewmodel.ChatMessage
import com.example.open_autoglm_android.ui.viewmodel.MessageRole
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatViewModel: ChatViewModel) {
    val uiState by chatViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    var inputText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var expandedMessageId by remember { mutableStateOf<String?>(null) }
    
    // 自动滚动到最新消息
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.scrollToItem(uiState.messages.size - 1)
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部工具栏
        TopAppBar(
            title = { Text("Open AutoGLM") },
            actions = {
                IconButton(onClick = {
                    if (selectedTab == 0) {
                        chatViewModel.clearMessages()
                    } else {
                        chatViewModel.clearTaskHistory()
                    }
                }) {
                    Icon(Icons.Default.Clear, contentDescription = "清空")
                }
            }
        )
        
        // 标签页
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("对话") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("任务历史") }
            )
        }
        
        // 错误提示
        if (uiState.error != null && selectedTab == 0) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { chatViewModel.clearError() }) {
                        Text("关闭")
                    }
                }
            ) {
                Text(uiState.error!!)
            }
        }
        
        // 任务完成提示
        if (uiState.taskCompletedMessage != null && selectedTab == 0) {
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
        
        // 内容区域
        when (selectedTab) {
            0 -> {
                // 对话界面
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    // 当前应用显示
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "当前应用:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Gray
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = uiState.currentApp ?: "未获取到应用信息",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { chatViewModel.refreshCurrentApp() }) {
                                    Text("刷新")
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 消息列表
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        state = listState,
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(uiState.messages) { message ->
                            ChatMessageItem(
                                message = message,
                                isExpanded = expandedMessageId == message.id,
                                onExpandToggle = {
                                    expandedMessageId = if (expandedMessageId == message.id) null else message.id
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
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
                    
                    // 输入框
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.OutlinedTextField(
                            value = inputText,
                            onValueChange = { value -> inputText = value },
                            placeholder = { Text("输入指令...") },
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 3,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    chatViewModel.sendMessage(inputText)
                                    inputText = ""
                                }
                            },
                            modifier = Modifier
                                .padding(8.dp)
                                .size(48.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "发送")
                        }
                    }
                }
            }
            
            1 -> {
                // 任务历史界面
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    if (uiState.taskHistory.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("暂无任务历史")
                            }
                        }
                    } else {
                        items(uiState.taskHistory.reversed()) { task ->
                            TaskHistoryItem(task = task)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage, isExpanded: Boolean, onExpandToggle: () -> Unit) {
    val isUserMessage = message.role == MessageRole.USER
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = if (isUserMessage) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.8f),
            colors = CardDefaults.cardColors(
                containerColor = if (isUserMessage) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
            ) {
                // 思考过程（仅助手消息）
                if (message.role == MessageRole.ASSISTANT && !message.thinking.isNullOrEmpty()) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "思考过程:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Gray
                            )
                            TextButton(onClick = onExpandToggle) {
                                Text(if (isExpanded) "收起" else "展开")
                            }
                        }
                        
                        if (isExpanded) {
                            Text(
                                message.thinking,
                                fontSize = 14.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                
                // 消息内容
                Text(
                    message.content,
                    fontSize = 16.sp
                )
                
                // 动作信息（仅助手消息）
                if (message.role == MessageRole.ASSISTANT && !message.action.isNullOrEmpty()) {
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
                
                // 时间戳
                Text(
                    text = android.text.format.DateFormat.format("HH:mm", message.timestamp).toString(),
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun TaskHistoryItem(
    task: TaskRecord
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable {
                // TODO: 点击任务历史项的处理逻辑
            },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
        ) {
            Text(
                text = task.taskContent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            if (task.isCompleted) MaterialTheme.colorScheme.tertiary 
                            else MaterialTheme.colorScheme.secondary
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (task.isCompleted) "已完成" else "未完成",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onTertiary
                    )
                }
                Text(
                    text = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", task.timestamp).toString(),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}