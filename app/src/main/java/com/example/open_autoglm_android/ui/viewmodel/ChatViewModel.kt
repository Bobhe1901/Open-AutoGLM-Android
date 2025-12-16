package com.example.open_autoglm_android.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.open_autoglm_android.data.PreferencesRepository
import com.example.open_autoglm_android.data.TaskRecord
import com.example.open_autoglm_android.network.ModelClient
import com.example.open_autoglm_android.network.dto.ChatMessage as NetworkChatMessage
import com.example.open_autoglm_android.service.AutoGLMAccessibilityService
import com.example.open_autoglm_android.util.BitmapUtils
import com.example.open_autoglm_android.util.DeviceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val thinking: String? = null,
    val action: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageRole {
    USER, ASSISTANT, TOOL
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val taskHistory: List<TaskRecord> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentApp: String? = null,
    val appList: List<String> = emptyList(),
    val isAccessibilityServiceEnabled: Boolean = false,
    val taskCompletedMessage: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val preferencesRepository = PreferencesRepository(application)
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private var modelClient: ModelClient? = null
    private var actionExecutor: com.example.open_autoglm_android.domain.ActionExecutor? = null
    
    // 维护对话上下文（消息历史）
    private val messageContext = mutableListOf<NetworkChatMessage>()
    
    private var currentTaskId: String? = null
    
    init {
        loadData()
        initializeComponents()
        observeCurrentApp()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            // 加载任务历史
            val taskHistory = preferencesRepository.getTaskHistorySync()
            _uiState.value = _uiState.value.copy(
                taskHistory = taskHistory
            )
            
            // 监听任务历史变化
            preferencesRepository.taskHistory.collect { history ->
                _uiState.value = _uiState.value.copy(taskHistory = history)
            }
        }
    }
    
    private fun initializeComponents() {
        viewModelScope.launch {
            // 初始化 ModelClient
            val baseUrl = preferencesRepository.getBaseUrlSync()
            val apiKey = preferencesRepository.getApiKeySync() ?: "EMPTY"
            val modelName = preferencesRepository.getModelNameSync()
            
            // 移除API Key检查，因为已经硬编码
            modelClient = ModelClient(baseUrl, apiKey)
            
            // 初始化 ActionExecutor
            AutoGLMAccessibilityService.getInstance()?.let { service ->
                actionExecutor = com.example.open_autoglm_android.domain.ActionExecutor(service)
            }
        }
    }
    
    private fun observeCurrentApp() {
        viewModelScope.launch {
            AutoGLMAccessibilityService.getInstance()?.currentApp?.collect { app ->
                _uiState.value = _uiState.value.copy(currentApp = app)
            }
        }
    }
    
    fun sendMessage(messageContent: String) {
        if (messageContent.isBlank()) return
        
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = messageContent
        )
        
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isLoading = true,
            error = null
        )
        
        // 创建任务记录
        val taskId = UUID.randomUUID().toString()
        currentTaskId = taskId
        viewModelScope.launch {
            preferencesRepository.addTaskRecord(
                TaskRecord(
                    id = taskId,
                    taskContent = messageContent,
                    timestamp = System.currentTimeMillis(),
                    isCompleted = false
                )
            )
        }
        
        viewModelScope.launch {
            try {
                val accessibilityService = AutoGLMAccessibilityService.getInstance()
                if (accessibilityService == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "无障碍服务未启用，请前往设置开启"
                    )
                    return@launch
                }
                
                // 重新初始化 ModelClient（以防配置变化）
                val baseUrl = preferencesRepository.getBaseUrlSync()
                val apiKey = preferencesRepository.getApiKeySync() ?: "EMPTY"
                val modelName = preferencesRepository.getModelNameSync()
                
                // 移除API Key检查，因为已经硬编码
                modelClient = ModelClient(baseUrl, apiKey)
                actionExecutor = com.example.open_autoglm_android.domain.ActionExecutor(accessibilityService)
                
                // 清空消息上下文，开始新的任务
                messageContext.clear()
                
                // 执行任务循环
                executeTaskLoop(messageContent, modelName, apiKey, taskId)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "发送失败：${e.message}"
                )
                
                // 标记任务为未完成
                currentTaskId?.let {
                    updateTaskStatus(it, false)
                }
            }
        }
    }
    
    private suspend fun executeTaskLoop(userInput: String, modelName: String, apiKey: String, taskId: String) {
        var currentStep = 0
        val maxSteps = 50 // 防止无限循环
        
        try {
            // 获取初始截图
            val accessibilityService = AutoGLMAccessibilityService.getInstance()
            if (accessibilityService == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "无障碍服务未启用，请前往设置开启"
                )
                return
            }
            
            var screenshot = accessibilityService.takeScreenshot()
            var currentApp = accessibilityService.getCurrentAppPackageName()
            
            while (currentStep < maxSteps) {
                currentStep++
                
                // 创建系统消息（仅在第一步）
                if (currentStep == 1) {
                    val systemMessage = modelClient?.createSystemMessage()
                    if (systemMessage != null) {
                        messageContext.add(systemMessage)
                    }
                }
                
                // 创建用户消息
                val userMessage = modelClient?.createUserMessage(
                    userPrompt = if (currentStep == 1) userInput else "继续执行任务",
                    screenshot = screenshot,
                    currentApp = currentApp
                )
                if (userMessage != null) {
                    messageContext.add(userMessage)
                }
                
                // 发送请求到模型
                val response = modelClient?.chat(
                    messages = messageContext,
                    model = modelName,
                    apiKey = apiKey
                )
                
                if (response == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "模型返回为空，请检查网络连接"
                    )
                    return
                }
                
                // 解析模型响应
                val responseContent = response.choices.firstOrNull()?.message?.content
                if (responseContent == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "模型返回内容为空，请检查网络连接"
                    )
                    return
                }
                
                // 提取思考和动作
                val (thinking, action) = modelClient?.parseModelResponse(responseContent) ?: Pair("", "")
                
                // 添加助手消息到UI
                val assistantMessage = ChatMessage(
                    id = "${System.currentTimeMillis()}_$currentStep",
                    role = MessageRole.ASSISTANT,
                    content = action,
                    thinking = thinking,
                    action = action
                )
                
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + assistantMessage
                )
                
                // 如果模型返回的是 finish，则直接结束，不再执行动作
                val isFinishAction = action.contains("\"_metadata\":\"finish\"") ||
                    action.contains("\"_metadata\": \"finish\"") ||
                    action.lowercase().contains("finish(")
                if (isFinishAction) {
                    val completionMessage = extractFinishMessage(action) ?: resultMessageFallback(action)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        taskCompletedMessage = completionMessage
                    )
                    Log.d("ChatViewModel", "任务完成(无需执行动作): $completionMessage")
                    
                    // 更新任务状态为完成
                    updateTaskStatus(taskId, true)
                    return
                }
                
                // 解析并执行动作
                val executor = actionExecutor ?: continue
                val result = executor.execute(
                    action,
                    screenshot?.width ?: 1080,
                    screenshot?.height ?: 1920
                )
                
                // 检查动作执行结果
                if (!result.success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "动作执行失败: ${result.message ?: "未知错误"}"
                    )
                    // 标记任务为未完成
                    updateTaskStatus(taskId, false)
                    return
                }
                
                // 添加模型响应到上下文
                val assistantMessageForContext = modelClient?.createAssistantMessage(thinking, action)
                if (assistantMessageForContext != null) {
                    messageContext.add(assistantMessageForContext)
                }
                
                // 添加工具响应到上下文
                val toolMessageForContext = modelClient?.createToolMessage(result.message ?: "")
                if (toolMessageForContext != null) {
                    messageContext.add(toolMessageForContext)
                }
                
                // 添加工具消息到UI
                val toolMessage = ChatMessage(
                    id = "tool_${System.currentTimeMillis()}_$currentStep",
                    role = MessageRole.TOOL,
                    content = result.message ?: ""
                )
                
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + toolMessage
                )
                
                // 等待一段时间后获取新的截图和当前应用
                withContext(Dispatchers.IO) {
                    Thread.sleep(1000) // 等待1秒，确保界面更新
                }
                
                screenshot = accessibilityService.takeScreenshot()
                currentApp = accessibilityService.getCurrentAppPackageName()
            }
            
            // 达到最大步骤数
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "任务执行步骤过多，请尝试简化任务"
            )
            
            // 标记任务为未完成
            updateTaskStatus(taskId, false)
            
        } catch (e: Exception) {
            Log.e("ChatViewModel", "任务执行异常", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "任务执行异常: ${e.message}"
            )
            
            // 标记任务为未完成
            updateTaskStatus(taskId, false)
        }
    }
    
    private fun extractFinishMessage(action: String): String? {
        val finishPattern = "\"_metadata\"\\s*:\\s*\"finish\"".toRegex()
        if (finishPattern.containsMatchIn(action)) {
            val messageStart = action.indexOf("\"message\"")
            if (messageStart != -1) {
                val messageValueStart = action.indexOf(":", messageStart) + 1
                val messageValueEnd = action.indexOf("}", messageValueStart)
                if (messageValueStart != -1 && messageValueEnd != -1) {
                    return action.substring(messageValueStart, messageValueEnd).trim().removeSurrounding("\"")
                }
            }
        }
        return null
    }
    
    private fun resultMessageFallback(action: String): String {
        // 简单的回退逻辑，从动作中提取可能的完成消息
        return if (action.length > 50) {
            action.substring(0, 50) + "..."
        } else {
            action
        }
    }
    
    private fun updateTaskStatus(taskId: String, isCompleted: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateTaskStatus(taskId, isCompleted)
        }
    }
    
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            error = null,
            taskCompletedMessage = null
        )
        messageContext.clear()
    }
    
    fun clearTaskHistory() {
        viewModelScope.launch {
            preferencesRepository.clearTaskHistory()
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(
            error = null
        )
    }
    
    fun clearTaskCompletedMessage() {
        _uiState.value = _uiState.value.copy(
            taskCompletedMessage = null
        )
    }
    
    fun refreshCurrentApp() {
        viewModelScope.launch {
            val accessibilityService = AutoGLMAccessibilityService.getInstance()
            val currentApp = accessibilityService?.getCurrentAppPackageName()
            _uiState.value = _uiState.value.copy(
                currentApp = currentApp
            )
        }
    }
}