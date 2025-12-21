package com.example.open_autoglm_android.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.open_autoglm_android.data.MessageRepository
import com.example.open_autoglm_android.network.ModelClient
import com.example.open_autoglm_android.network.dto.ChatMessage as NetworkChatMessage
import com.example.open_autoglm_android.service.AutoGLMAccessibilityService
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
    val isLoading: Boolean = false,
    val error: String? = null,
    val taskId: String? = null,
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val taskCompletedMessage: String? = null,
    val userInput: String = ""
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private var modelClient: ModelClient? = null
    private var actionExecutor: com.example.open_autoglm_android.domain.ActionExecutor? = null
    private val messageRepository = MessageRepository(application)
    private val preferencesRepository = com.example.open_autoglm_android.data.PreferencesRepository(application)
    private val TAG = "ChatViewModel"
    
    // 维护对话上下文（消息历史）
    private val messageContext = mutableListOf<NetworkChatMessage>()
    
    private var currentTaskId: String? = null
    
    // 模型参数
    private var modelName: String = "autoglm-phone"
    private var apiKey: String = "2b55beee279d437ea8c7460e29bc12b0.X0JeFydsJjZjp4Rf"
    
    init {
        initializeComponents()
        loadSavedMessages()
    }
    
    private fun initializeComponents() {
        viewModelScope.launch {
            // 从PreferencesRepository获取配置
            val baseUrl = preferencesRepository.getBaseUrlSync()
            val apiKey = preferencesRepository.getApiKeySync()
            val modelName = preferencesRepository.getModelNameSync()
            val temperature = preferencesRepository.getTemperatureSync().toDouble()
            val topP = preferencesRepository.getTopPSync().toDouble()
            val frequencyPenalty = preferencesRepository.getFrequencyPenaltySync().toDouble()
            
            // 保存模型参数到类成员变量
            this@ChatViewModel.apiKey = apiKey
            this@ChatViewModel.modelName = modelName
            
            // 初始化 ModelClient
            modelClient = ModelClient(
                baseUrl = baseUrl,
                apiKey = apiKey
            )
            
            // 初始化 ActionExecutor
            AutoGLMAccessibilityService.getInstance()?.let { service ->
                actionExecutor = com.example.open_autoglm_android.domain.ActionExecutor(service)
            }
        }
    }
    
    private fun loadSavedMessages() {
        Log.d(TAG, "开始加载保存的消息")
        viewModelScope.launch {
            try {
                val savedMessages = messageRepository.loadMessages()
                if (savedMessages.isNotEmpty()) {
                    Log.d(TAG, "成功加载 ${savedMessages.size} 条保存的消息")
                    _uiState.value = _uiState.value.copy(messages = savedMessages)
                } else {
                    Log.d(TAG, "没有找到保存的消息")
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载保存的消息失败", e)
            }
        }
    }
    
    private suspend fun saveMessages(messages: List<ChatMessage>) {
        Log.d(TAG, "准备保存 ${messages.size} 条消息")
        try {
            messageRepository.saveMessages(messages)
            Log.d(TAG, "消息保存成功")
        } catch (e: Exception) {
            Log.e(TAG, "保存消息失败", e)
        }
    }
    
    fun sendMessage(messageContent: String) {
        if (messageContent.isBlank()) return
        
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = messageContent
        )
        
        val updatedMessages = _uiState.value.messages + userMessage
        _uiState.value = _uiState.value.copy(
            messages = updatedMessages,
            isLoading = true,
            error = null
        )
        
        // 保存消息到持久化存储
        viewModelScope.launch {
            saveMessages(updatedMessages)
        }
        
        // 生成任务ID用于跟踪
        val taskId = UUID.randomUUID().toString()
        currentTaskId = taskId
        
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
                
                // 重新初始化 ModelClient
                val baseUrl = preferencesRepository.getBaseUrlSync()
                val apiKey = preferencesRepository.getApiKeySync()
                val modelName = preferencesRepository.getModelNameSync()
                val temperature = preferencesRepository.getTemperatureSync().toDouble()
                val topP = preferencesRepository.getTopPSync().toDouble()
                val frequencyPenalty = preferencesRepository.getFrequencyPenaltySync().toDouble()
                
                // 保存模型参数到类成员变量
                this@ChatViewModel.apiKey = apiKey
                this@ChatViewModel.modelName = modelName
                
                // 初始化 ModelClient
                modelClient = ModelClient(
                    baseUrl = baseUrl,
                    apiKey = apiKey
                )
                
                // 初始化 ActionExecutor
                AutoGLMAccessibilityService.getInstance()?.let { service ->
                    actionExecutor = com.example.open_autoglm_android.domain.ActionExecutor(service)
                }
                
                // 清空消息上下文，开始新的任务
                messageContext.clear()
                
                // 执行任务循环
                executeTaskLoop(messageContent, taskId)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "发送失败：${e.message}"
                )
            }
        }
    }
    
    // 添加语音消息处理方法
    fun sendVoiceMessage(voiceText: String) {
        sendMessage(voiceText)
    }
    
    // 添加图片消息处理方法
    fun sendImageMessage(imageBytes: ByteArray) {
        val base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT)
        
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = "图片消息"
        )
        
        val updatedMessages = _uiState.value.messages + userMessage
        _uiState.value = _uiState.value.copy(
            messages = updatedMessages,
            isLoading = true,
            error = null
        )
        
        // 保存消息到持久化存储
        viewModelScope.launch {
            saveMessages(updatedMessages)
        }
        
        // 生成任务ID用于跟踪
        val taskId = UUID.randomUUID().toString()
        currentTaskId = taskId
        
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
                
                // 重新初始化 ModelClient
                val baseUrl = preferencesRepository.getBaseUrlSync()
                val apiKey = preferencesRepository.getApiKeySync()
                val modelName = preferencesRepository.getModelNameSync()
                val temperature = preferencesRepository.getTemperatureSync().toDouble()
                val topP = preferencesRepository.getTopPSync().toDouble()
                val frequencyPenalty = preferencesRepository.getFrequencyPenaltySync().toDouble()
                
                // 保存模型参数到类成员变量
                this@ChatViewModel.apiKey = apiKey
                this@ChatViewModel.modelName = modelName
                
                // 初始化 ModelClient
                modelClient = ModelClient(
                    baseUrl = baseUrl,
                    apiKey = apiKey
                )
                
                // 初始化 ActionExecutor
                AutoGLMAccessibilityService.getInstance()?.let { service ->
                    actionExecutor = com.example.open_autoglm_android.domain.ActionExecutor(service)
                }
                
                // 清空消息上下文，开始新的任务
                messageContext.clear()
                
                // 执行任务循环，传入图片
                executeImageTaskLoop(base64Image, taskId)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "发送失败：${e.message}"
                )
            }
        }
    }
    
    // 执行图片任务循环
    private suspend fun executeImageTaskLoop(base64Image: String, taskId: String) {
        var currentStep = 0
        val maxSteps = 50 // 防止无限循环
        
        try {
            val accessibilityService = AutoGLMAccessibilityService.getInstance()
            if (accessibilityService == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "无障碍服务未启用，请前往设置开启"
                )
                return
            }
            
            var currentApp = accessibilityService.getCurrentAppPackageName()
            
            while (currentStep < maxSteps) {
                currentStep++
                
                // 每次循环都创建一个新的临时消息列表，只包含当前对话所需的消息
                val currentMessages = mutableListOf<NetworkChatMessage>()
                
                // 创建系统消息（仅在第一步）
                if (currentStep == 1) {
                    val systemMessage = modelClient?.createSystemMessage()
                    if (systemMessage != null) {
                        currentMessages.add(systemMessage)
                    }
                }
                
                // 创建用户消息，包含图片
                val userMessage = modelClient?.createUserMessageWithImage(
                    userPrompt = if (currentStep == 1) "请分析这张图片" else "继续分析图片",
                    base64Image = base64Image,
                    currentApp = currentApp
                )
                if (userMessage != null) {
                    currentMessages.add(userMessage)
                }
                
                // 发送请求到模型
                Log.d(TAG, "发送API请求：消息数量=${currentMessages.size}")
                val response = modelClient?.request(
                    messages = currentMessages,
                    modelName = modelName,
                    apiKey = apiKey
                )
                
                if (response == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "模型返回为空，请检查网络连接"
                    )
                    return
                }
                
                // 提取思考和动作
                val thinking = response.thinking
                val action = response.action
                
                // 添加助手消息到UI
                val assistantMessage = ChatMessage(
                    id = "${System.currentTimeMillis()}_$currentStep",
                    role = MessageRole.ASSISTANT,
                    content = action,
                    thinking = thinking,
                    action = action
                )
                
                var updatedMessages = _uiState.value.messages + assistantMessage
                _uiState.value = _uiState.value.copy(
                    messages = updatedMessages
                )
                
                // 保存消息到持久化存储
                saveMessages(updatedMessages)
                
                // 如果模型返回的是 finish，则直接结束，不再执行动作
                val isFinishAction = action.contains("\"_metadata\":\"finish\"") ||
                    action.contains("\"_metadata\": \"finish\"") ||
                    action.lowercase().contains("finish(") ||
                    action.contains("_metadata") && action.contains("finish")
                if (isFinishAction) {
                    val completionMessage = extractFinishMessage(action) ?: resultMessageFallback(action)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        taskCompletedMessage = completionMessage
                    )
                    Log.d("ChatViewModel", "任务完成(无需执行动作): $completionMessage")
                    return
                }
                
                // 解析并执行动作
                val executor = actionExecutor ?: continue
                val result = executor.execute(
                    action,
                    1080, // 默认宽度
                    1920  // 默认高度
                )
                
                // 检查动作执行结果
                if (!result.success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "动作执行失败: ${result.message ?: "未知错误"}"
                    )
                    return
                }
                
                // 添加模型响应到上下文
                val assistantMessageForContext = modelClient?.createAssistantMessage(thinking, action)
                if (assistantMessageForContext != null) {
                    messageContext.add(assistantMessageForContext)
                }
                
                // 添加工具响应到上下文
                val toolMessageForContext = modelClient?.createToolMessage(result.message ?: "动作执行成功")
                if (toolMessageForContext != null) {
                    messageContext.add(toolMessageForContext)
                }
                
                // 限制消息上下文长度，避免内存占用过高
                limitMessageContextSize()
                
                // 添加工具消息到UI
                val toolMessage = ChatMessage(
                    id = "tool_${System.currentTimeMillis()}_$currentStep",
                    role = MessageRole.TOOL,
                    content = result.message ?: ""
                )
                
                updatedMessages = _uiState.value.messages + toolMessage
                _uiState.value = _uiState.value.copy(
                    messages = updatedMessages
                )
                
                // 保存消息到持久化存储
                saveMessages(updatedMessages)
                
                // 等待一段时间
                withContext(Dispatchers.IO) {
                    Thread.sleep(1000) // 等待1秒
                }
            }
            
            // 达到最大步骤数
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "任务执行步骤过多，请尝试简化任务"
            )
            
        } catch (e: Exception) {
            Log.e("ChatViewModel", "任务执行异常", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "任务执行异常: ${e.message}"
            )
        }
    }
    
    private suspend fun executeTaskLoop(userInput: String, taskId: String) {
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
                
                // 每次循环都创建一个新的临时消息列表，只包含当前对话所需的消息
                val currentMessages = mutableListOf<NetworkChatMessage>()
                
                // 创建系统消息（仅在第一步）
                if (currentStep == 1) {
                    val systemMessage = modelClient?.createSystemMessage()
                    if (systemMessage != null) {
                        currentMessages.add(systemMessage)
                    }
                }
                
                // 创建用户消息
                val userMessage = modelClient?.createUserMessage(
                    userPrompt = if (currentStep == 1) userInput else "继续执行任务",
                    screenshot = screenshot,
                    currentApp = currentApp
                )
                if (userMessage != null) {
                    currentMessages.add(userMessage)
                }
                
                // 发送请求到模型
                // 现在只发送当前消息，不包含历史对话
                Log.d(TAG, "发送API请求：消息数量=${currentMessages.size}")
                val response = modelClient?.request(
                    messages = currentMessages,
                    modelName = modelName,
                    apiKey = apiKey
                )
                
                if (response == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "模型返回为空，请检查网络连接"
                    )
                    return
                }
                
                // 提取思考和动作
                val thinking = response.thinking
                val action = response.action
                
                // 添加助手消息到UI
                val assistantMessage = ChatMessage(
                    id = "${System.currentTimeMillis()}_$currentStep",
                    role = MessageRole.ASSISTANT,
                    content = action,
                    thinking = thinking,
                    action = action
                )
                
                var updatedMessages = _uiState.value.messages + assistantMessage
                _uiState.value = _uiState.value.copy(
                    messages = updatedMessages
                )
                
                // 保存消息到持久化存储
                saveMessages(updatedMessages)
                
                // 如果模型返回的是 finish，则直接结束，不再执行动作
                val isFinishAction = action.contains("\"_metadata\":\"finish\"") ||
                    action.contains("\"_metadata\": \"finish\"") ||
                    action.lowercase().contains("finish(") ||
                    action.contains("_metadata") && action.contains("finish")
                if (isFinishAction) {
                    val completionMessage = extractFinishMessage(action) ?: resultMessageFallback(action)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        taskCompletedMessage = completionMessage
                    )
                    Log.d("ChatViewModel", "任务完成(无需执行动作): $completionMessage")
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
                    return
                }
                
                // 添加模型响应到上下文
                val assistantMessageForContext = modelClient?.createAssistantMessage(thinking, action)
                if (assistantMessageForContext != null) {
                    messageContext.add(assistantMessageForContext)
                }
                
                // 添加工具响应到上下文
                val toolMessageForContext = modelClient?.createToolMessage(result.message ?: "动作执行成功")
                if (toolMessageForContext != null) {
                    messageContext.add(toolMessageForContext)
                }
                
                // 限制消息上下文长度，避免内存占用过高
                limitMessageContextSize()
                
                // 添加工具消息到UI
                val toolMessage = ChatMessage(
                    id = "tool_${System.currentTimeMillis()}_$currentStep",
                    role = MessageRole.TOOL,
                    content = result.message ?: ""
                )
                
                updatedMessages = _uiState.value.messages + toolMessage
                _uiState.value = _uiState.value.copy(
                    messages = updatedMessages
                )
                
                // 保存消息到持久化存储
                saveMessages(updatedMessages)
                
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
            
        } catch (e: Exception) {
            Log.e("ChatViewModel", "任务执行异常", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "任务执行异常: ${e.message}"
            )
        }
    }
    
    private fun extractFinishMessage(action: String): String? {
        val finishPattern = "\"_metadata\"\\s*:\\s*\"finish\"".toRegex()
        if (finishPattern.containsMatchIn(action)) {
            // 尝试从JSON中提取message字段
            try {
                val jsonObject = com.google.gson.JsonParser.parseString(action).asJsonObject
                if (jsonObject.has("content")) {
                    return jsonObject.get("content").asString
                }
            } catch (e: Exception) {
                // JSON解析失败，尝试使用原始逻辑
                val messageStart = action.indexOf("\"message\"")
                if (messageStart != -1) {
                    val messageValueStart = action.indexOf(":", messageStart) + 1
                    val messageValueEnd = action.indexOf("}", messageValueStart)
                    if (messageValueStart != -1 && messageValueEnd != -1) {
                        return action.substring(messageValueStart, messageValueEnd).trim().removeSurrounding("\"")
                    }
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
    
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            error = null,
            taskCompletedMessage = null
        )
        messageContext.clear()
        
        // 清除保存的消息
        viewModelScope.launch {
            try {
                messageRepository.clearMessages()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "清除保存的消息失败", e)
            }
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
    
    /**
     * 限制消息上下文长度，避免令牌数量超过API限制
     */
    private fun limitMessageContext(context: List<NetworkChatMessage>): List<NetworkChatMessage> {
        // 保留系统消息（如果有）和最近的10条消息
        val systemMessage = context.find { it.role == "system" }
        val recentMessages = context.filter { it.role != "system" }.takeLast(10)
        
        val limitedContext = mutableListOf<NetworkChatMessage>()
        if (systemMessage != null) {
            limitedContext.add(systemMessage)
        }
        limitedContext.addAll(recentMessages)
        
        Log.d(TAG, "限制消息上下文：原始长度=${context.size}, 限制后长度=${limitedContext.size}")
        return limitedContext
    }
    
    /**
     * 限制消息上下文大小，避免内存占用过高
     */
    private fun limitMessageContextSize() {
        // 保留系统消息（如果有）和最近的20条消息
        val systemMessage = messageContext.find { it.role == "system" }
        val recentMessages = messageContext.filter { it.role != "system" }.takeLast(20)
        
        messageContext.clear()
        if (systemMessage != null) {
            messageContext.add(systemMessage)
        }
        messageContext.addAll(recentMessages)
        
        Log.d(TAG, "消息上下文大小限制：当前长度=${messageContext.size}")
    }
}