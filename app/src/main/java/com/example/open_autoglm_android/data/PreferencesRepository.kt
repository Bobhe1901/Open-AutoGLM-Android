package com.example.open_autoglm_android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.*

// 定义数据存储的名称
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// 定义偏好键
private val API_KEY = stringPreferencesKey("api_key")
private val BASE_URL = stringPreferencesKey("base_url")
private val MODEL_NAME = stringPreferencesKey("model_name")
private val CHAT_HISTORY = stringPreferencesKey("chat_history")
private val TASK_HISTORY = stringPreferencesKey("task_history")

// 定义任务记录数据类
data class TaskRecord(
    val id: String,
    val taskContent: String,
    val timestamp: Long,
    val isCompleted: Boolean
)

// 定义消息数据类
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any>? = null
)

class PreferencesRepository(private val context: Context) {
    
    // 使用Gson进行JSON序列化和反序列化
    private val gson = Gson()
    
    // API Key
    val apiKey: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[API_KEY]
        }
    
    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = apiKey
        }
    }
    
    fun getApiKeySync(): String? {
        return runBlocking {
            context.dataStore.data.first()[API_KEY]
        }
    }
    
    // Base URL
    val baseUrl: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[BASE_URL] ?: "https://open.bigmodel.cn/api/paas/v4"
        }
    
    suspend fun saveBaseUrl(baseUrl: String) {
        context.dataStore.edit { preferences ->
            preferences[BASE_URL] = baseUrl
        }
    }
    
    fun getBaseUrlSync(): String {
        return runBlocking {
            context.dataStore.data.first()[BASE_URL] ?: "https://open.bigmodel.cn/api/paas/v4"
        }
    }
    
    // Model Name
    val modelName: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[MODEL_NAME] ?: "autoglm-phone"
        }
    
    suspend fun saveModelName(modelName: String) {
        context.dataStore.edit { preferences ->
            preferences[MODEL_NAME] = modelName
        }
    }
    
    fun getModelNameSync(): String {
        return runBlocking {
            context.dataStore.data.first()[MODEL_NAME] ?: "autoglm-phone"
        }
    }
    
    // 对话历史
    val chatHistory: Flow<List<Message>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[CHAT_HISTORY]
            if (json != null) {
                val type = object : TypeToken<List<Message>>() {}.type
                gson.fromJson(json, type)
            } else {
                emptyList()
            }
        }
    
    suspend fun saveChatHistory(messages: List<Message>) {
        // 只保存最近50条消息
        val recentMessages = messages.takeLast(50)
        val json = gson.toJson(recentMessages)
        context.dataStore.edit { preferences ->
            preferences[CHAT_HISTORY] = json
        }
    }
    
    fun getChatHistorySync(): List<Message> {
        val json = runBlocking {
            context.dataStore.data.first()[CHAT_HISTORY]
        }
        if (json != null) {
            val type = object : TypeToken<List<Message>>() {}.type
            return gson.fromJson(json, type)
        }
        return emptyList()
    }
    
    suspend fun clearChatHistory() {
        context.dataStore.edit { preferences ->
            preferences.remove(CHAT_HISTORY)
        }
    }
    
    // 任务历史
    val taskHistory: Flow<List<TaskRecord>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[TASK_HISTORY]
            if (json != null) {
                val type = object : TypeToken<List<TaskRecord>>() {}.type
                gson.fromJson(json, type)
            } else {
                emptyList()
            }
        }
    
    suspend fun addTaskRecord(taskRecord: TaskRecord) {
        val currentHistory = getTaskHistorySync()
        val updatedHistory = (currentHistory + taskRecord).takeLast(50) // 只保留最近50条
        val json = gson.toJson(updatedHistory)
        context.dataStore.edit { preferences ->
            preferences[TASK_HISTORY] = json
        }
    }
    
    suspend fun updateTaskStatus(taskId: String, isCompleted: Boolean) {
        val currentHistory = getTaskHistorySync()
        val updatedHistory = currentHistory.map {
            if (it.id == taskId) {
                it.copy(isCompleted = isCompleted)
            } else {
                it
            }
        }
        val json = gson.toJson(updatedHistory)
        context.dataStore.edit { preferences ->
            preferences[TASK_HISTORY] = json
        }
    }
    
    fun getTaskHistorySync(): List<TaskRecord> {
        val json = runBlocking {
            context.dataStore.data.first()[TASK_HISTORY]
        }
        if (json != null) {
            val type = object : TypeToken<List<TaskRecord>>() {}.type
            return gson.fromJson(json, type)
        }
        return emptyList()
    }
    
    suspend fun clearTaskHistory() {
        context.dataStore.edit { preferences ->
            preferences.remove(TASK_HISTORY)
        }
    }
}